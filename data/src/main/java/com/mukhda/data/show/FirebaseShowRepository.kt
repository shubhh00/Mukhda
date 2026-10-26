package com.mukhda.data.show

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.mukhda.data.di.IoDispatcher
import com.mukhda.data.firebase.decodeCallable
import com.mukhda.data.firebase.networkCause
import com.mukhda.data.firebase.safeFunctionCall
import com.mukhda.data.round.RoundDao
import com.mukhda.data.round.RoundEntity
import com.mukhda.data.round.TryDao
import com.mukhda.data.round.TryEntity
import com.mukhda.data.round.TryState
import com.mukhda.domain.DataError
import com.mukhda.domain.Outcome
import com.mukhda.domain.model.RoundStatus
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.TryRecord
import com.mukhda.domain.show.Show
import com.mukhda.domain.show.ShowRepository
import com.mukhda.domain.show.TryError
import com.mukhda.domain.show.isTransient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Today's show from the Cloud Functions (AGENTS.md §7), signed in anonymously and silently.
 *
 * Every try is saved in Room as PENDING before it's sent and keeps its requestId until the
 * server confirms it (AGENTS.md §8): a timeout is retried with the same id, and a try that still
 * hasn't gone through (no signal, or the app was killed) is resent, with that id, before the next
 * try of its round and before the next show is loaded. The server never counts one request twice,
 * so none of this can use a second try. Rounds are also kept in Room for the streak; the server's
 * copy always wins.
 */
@Singleton
class FirebaseShowRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val functions: FirebaseFunctions,
    private val roundDao: RoundDao,
    private val tryDao: TryDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ShowRepository {

    /** One call at a time, so a resend and a new try never overtake each other. */
    private val mutex = Mutex()

    /** The show as last heard from the server; a confirmed try is applied to its round. */
    private var current: Show? = null

    override suspend fun todaysShow(): Outcome<Show, DataError> = withContext(io) {
        mutex.withLock {
            signIn()?.let { return@withLock Outcome.Error(it) }
            // Tries left over from an earlier session go first. A try for a day that's over comes
            // back ROUND_CLOSED and is dropped. One that still can't get through fails the load
            // with its error: a show without it would look as if every try were in, and the
            // screen would stop showing it as sending and stop trying.
            for (pending in tryDao.pending()) {
                val failed = ((send(pending) as? Outcome.Error)?.error as? TryError.Data)
                if (failed != null && failed.isTransient) return@withLock Outcome.Error(failed.error)
            }
            fetchShow()
        }
    }

    override suspend fun submitTry(
        date: LocalDate,
        tier: Tier,
        requestId: String,
        type: TryType,
        songId: String?,
    ): Outcome<RoundState, TryError> = withContext(io) {
        mutex.withLock {
            signIn()?.let { return@withLock Outcome.Error(TryError.Data(it)) }
            // An earlier try of this round that never got through goes first, with its own id.
            // Until it does, no new one is made, so the player can't use two tries for one tap.
            for (earlier in tryDao.pendingFor(date.toString(), tier.name)) {
                val sent = send(earlier)
                if (sent is Outcome.Error && sent.error.isTransient) return@withLock sent
            }
            val attempt = TryEntity(
                requestId = requestId,
                date = date.toString(),
                tier = tier.name,
                type = type.name,
                guessedSongId = songId,
                result = null,
                state = TryState.PENDING.name,
                createdAt = System.currentTimeMillis(),
            )
            tryDao.insert(attempt)
            send(attempt)
        }
    }

    private suspend fun signIn(): DataError? {
        if (auth.currentUser != null) return null
        return try {
            auth.signInAnonymously().await()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No connection, or else anonymous sign-in is switched off in the console or Firebase
            // is down for us.
            e.networkCause().takeIf { it != DataError.Network.UNKNOWN } ?: DataError.Network.SERVICE_UNAVAILABLE
        }
    }

    /**
     * A call the server turns away as signed out (an expired session, or a sign-in it no longer
     * knows) is retried once: first with a fresh token, and only if the sign-in itself is rejected,
     * with a new anonymous one (AGENTS.md §8). A refresh that fails for want of a connection keeps
     * the sign-in: a new anonymous user would lose the player's rounds on the server.
     */
    private suspend fun <T> callSignedIn(call: suspend () -> T): Outcome<T, TryError> {
        val first = safeFunctionCall(call)
        if (first !is Outcome.Error || first.error != TryError.Data(DataError.Network.UNAUTHORIZED)) return first
        val refreshError = auth.currentUser?.let { user ->
            try {
                user.getIdToken(true).await()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.networkCause()
            }
        } ?: DataError.Network.UNAUTHORIZED
        when (refreshError) {
            null -> Unit
            DataError.Network.UNAUTHORIZED -> {
                auth.signOut()
                signIn()?.let { return Outcome.Error(TryError.Data(it)) }
            }
            else -> return Outcome.Error(TryError.Data(refreshError))
        }
        return safeFunctionCall(call)
    }

    private suspend fun fetchShow(): Outcome<Show, DataError> {
        val fetched = callSignedIn {
            decodeCallable<ShowDto>(functions.getHttpsCallable(GET_SHOW).call().await().getData()).toShow()
        }
        return when (fetched) {
            is Outcome.Error -> Outcome.Error((fetched.error as? TryError.Data)?.error ?: DataError.Network.UNKNOWN)
            is Outcome.Success -> {
                current = fetched.data
                // Rounds of earlier days still open on the phone closed at their midnight: unfinished.
                roundDao.closeOpenBefore(fetched.data.date.toString(), RoundStatus.UNFINISHED.name, System.currentTimeMillis())
                fetched.data.rounds.values.filter { it.isStarted }.forEach { saveRound(fetched.data.date, it) }
                fetched
            }
        }
    }

    /**
     * Sends one saved try, retrying a transient failure with the same requestId. A try the server
     * judged is CONFIRMED; one it refused is FAILED; one that never got through stays PENDING.
     */
    private suspend fun send(attempt: TryEntity): Outcome<RoundState, TryError> {
        var retries = 0
        while (true) {
            val sent = callSignedIn {
                decodeCallable<SubmitTryDto>(functions.getHttpsCallable(SUBMIT_TRY).call(attempt.toRequest()).await().getData())
            }
            when {
                sent is Outcome.Success -> return confirm(attempt, sent.data)
                sent is Outcome.Error && sent.error.isTransient && retries < RETRY_DELAYS_MS.size ->
                    delay(RETRY_DELAYS_MS[retries++])
                sent is Outcome.Error && sent.error.isTransient -> return sent
                sent is Outcome.Error -> {
                    tryDao.updateState(attempt.requestId, TryState.FAILED.name, null)
                    return sent
                }
            }
        }
    }

    /**
     * Applies a confirmed try to the round from the last show. If the two disagree (another phone
     * played too, say), the server's show is fetched again and wins.
     */
    private suspend fun confirm(attempt: TryEntity, judged: SubmitTryDto): Outcome<RoundState, TryError> {
        // A result this app doesn't know (from a newer server) can't be shown: the try is done
        // with, and the show reloaded later decides what the round looks like.
        val result = TryResult.entries.firstOrNull { it.name == judged.result } ?: run {
            tryDao.updateState(attempt.requestId, TryState.FAILED.name, null)
            return Outcome.Error(TryError.Data(DataError.Network.SERIALIZATION))
        }
        tryDao.updateState(attempt.requestId, TryState.CONFIRMED.name, judged.result)
        val tier = Tier.valueOf(attempt.tier)
        val date = LocalDate.parse(attempt.date)
        val show = current?.takeIf { it.date == date }
        val record = TryRecord(attempt.requestId, TryType.valueOf(attempt.type), attempt.guessedSongId, result)
        val applied = ((show?.rounds?.get(tier) ?: RoundState(tier)).apply(record) as? Outcome.Success)?.data
            ?.takeIf { it.triesUsed == judged.triesUsed && it.status.name == judged.status }
        val round = applied?.copy(reveal = judged.reveal?.toSongReveal())
            ?: when (val refetched = fetchShow()) {
                is Outcome.Success -> refetched.data.rounds[tier]
                is Outcome.Error -> null
            }
            ?: return Outcome.Error(TryError.Data(DataError.Network.CONFLICT))
        if (show != null) current = show.copy(rounds = show.rounds + (tier to round))
        saveRound(date, round)
        return Outcome.Success(round)
    }

    /** The local copy behind the streak and stats: when the round started, and how it stands. */
    private suspend fun saveRound(date: LocalDate, round: RoundState) {
        val now = System.currentTimeMillis()
        val saved = roundDao.round(date.toString(), round.tier.name)
        roundDao.upsert(
            RoundEntity(
                date = date.toString(),
                tier = round.tier.name,
                // The song only once it's revealed: never stored for an open round.
                songId = round.reveal?.songId,
                status = round.status.name,
                triesUsed = round.triesUsed,
                solvedAtStep = round.solvedAtStep,
                startedAt = saved?.startedAt ?: now,
                endedAt = saved?.endedAt ?: now.takeIf { round.status != RoundStatus.OPEN },
            )
        )
        round.tries.forEachIndexed { i, record ->
            tryDao.insert(
                TryEntity(
                    requestId = record.requestId,
                    date = date.toString(),
                    tier = round.tier.name,
                    type = record.type.name,
                    guessedSongId = record.guessedSongId,
                    result = record.result.name,
                    state = TryState.CONFIRMED.name,
                    createdAt = (saved?.startedAt ?: now) + i,
                )
            )
            tryDao.updateState(record.requestId, TryState.CONFIRMED.name, record.result.name)
        }
    }

    private fun TryEntity.toRequest(): Map<String, Any> = buildMap {
        put("date", date)
        put("tier", tier)
        put("requestId", requestId)
        put("type", type)
        guessedSongId?.let { put("songId", it) }
    }

    private companion object {
        const val GET_SHOW = "getShow"
        const val SUBMIT_TRY = "submitTry"

        /** Waits between resends of a try that didn't get through (AGENTS.md §8: retry ×3). */
        val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000)
    }
}
