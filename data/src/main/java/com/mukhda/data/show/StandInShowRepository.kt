package com.mukhda.data.show

import com.mukhda.data.search.SearchIndexDao
import com.mukhda.domain.DataError
import com.mukhda.domain.GameError
import com.mukhda.domain.Outcome
import com.mukhda.domain.RoundError
import com.mukhda.domain.audio.Preview
import com.mukhda.domain.model.Tier
import com.mukhda.domain.model.TryResult
import com.mukhda.domain.model.TryType
import com.mukhda.domain.round.RoundState
import com.mukhda.domain.round.ShowCalendar
import com.mukhda.domain.round.SongReveal
import com.mukhda.domain.round.TryRecord
import com.mukhda.domain.search.SongIndexDataSource
import com.mukhda.domain.show.Show
import com.mukhda.domain.show.ShowRepository
import com.mukhda.domain.show.TryError
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Placeholder until the Cloud Functions exist: lets the game screen be played end to end without
 * a server. Each day it plays five songs picked at random from the bundled search index (seeded by
 * the date, so they hold for the day), with previews from Apple's lookup API. It never compares a
 * guess with them, since answers may not be judged on the client: every guess is WRONG and every
 * skip SKIPPED. Like the server, it reveals the song once a round is over. Rounds live in memory only, so a restart gives a fresh show, which keeps testing
 * simple. The show date comes from the phone here only because there's no server yet; the real
 * repository takes it from `getShow()`.
 */
@Singleton
class StandInShowRepository @Inject constructor(
    private val songIndex: SongIndexDataSource,
    private val searchIndexDao: SearchIndexDao,
    private val trackLookup: ItunesTrackLookup,
) : ShowRepository {

    private var date: LocalDate = LocalDate.now(ShowCalendar.IST)
    private val rounds = Tier.entries.associateWith { RoundState(it) }.toMutableMap()
    /** Today's song per tier, picked on first load. */
    private var picks: Map<Tier, Pick>? = null

    private data class Pick(val songId: String, val track: ItunesTrack)

    override suspend fun todaysShow(): Outcome<Show, DataError> {
        startNewDayIfMidnightPassed()
        val picks = picks ?: when (val picked = pickSongs()) {
            is Outcome.Error -> return picked
            is Outcome.Success -> picked.data.also { picks = it }
        }
        return Outcome.Success(
            Show(
                date = date,
                showNo = 1,
                endsAt = date.plusDays(1).atTime(LocalTime.MIDNIGHT).atZone(ShowCalendar.IST).toInstant(),
                rounds = rounds.toMap(),
                previews = picks.mapValues { (_, pick) -> Preview(pick.track.previewUrl) },
            )
        )
    }

    /**
     * The app can stay open past midnight IST: a new day gets fresh rounds and new songs, and a try
     * still carrying yesterday's date is then refused as ROUND_CLOSED, which makes the game reload.
     */
    private fun startNewDayIfMidnightPassed() {
        val today = LocalDate.now(ShowCalendar.IST)
        if (today == date) return
        date = today
        Tier.entries.forEach { rounds[it] = RoundState(it) }
        picks = null
    }

    private suspend fun pickSongs(): Outcome<Map<Tier, Pick>, DataError> {
        if (songIndex.prepare() is Outcome.Error) return Outcome.Error(DataError.Local.UNKNOWN)
        val count = searchIndexDao.count()
        if (count == 0) return Outcome.Error(DataError.Local.NOT_FOUND)
        val random = Random(date.toEpochDay())
        // A few spares, in case some tracks no longer have a preview.
        val trackIds = List(Tier.entries.size * 2) { random.nextInt(count) }
            .distinct()
            .mapNotNull { searchIndexDao.songIdAt(it)?.removePrefix(SONG_ID_PREFIX)?.toLongOrNull() }
        return when (val lookup = trackLookup.tracks(trackIds)) {
            is Outcome.Error -> lookup
            is Outcome.Success -> {
                val found = trackIds.mapNotNull { id -> lookup.data[id]?.let { Pick(SONG_ID_PREFIX + id, it) } }
                if (found.size < Tier.entries.size) {
                    Outcome.Error(DataError.Network.NOT_FOUND)
                } else {
                    Outcome.Success(Tier.entries.zip(found).toMap())
                }
            }
        }
    }

    override suspend fun submitTry(
        date: LocalDate,
        tier: Tier,
        requestId: String,
        type: TryType,
        songId: String?,
    ): Outcome<RoundState, TryError> {
        startNewDayIfMidnightPassed()
        if (date != this.date) return Outcome.Error(TryError.Rule(GameError.ROUND_CLOSED))
        val result = if (type == TryType.SKIP) TryResult.SKIPPED else TryResult.WRONG
        val round = rounds.getValue(tier)
        return when (val applied = round.apply(TryRecord(requestId, type, songId, result))) {
            is Outcome.Success -> {
                val judged = if (applied.data.status.isFinished) applied.data.copy(reveal = reveal(tier)) else applied.data
                rounds[tier] = judged
                Outcome.Success(judged)
            }
            is Outcome.Error -> Outcome.Error(
                when (applied.error) {
                    RoundError.ALREADY_FINISHED -> TryError.Rule(GameError.ALREADY_FINISHED)
                    RoundError.TRIES_EXHAUSTED -> TryError.Rule(GameError.TRIES_EXHAUSTED)
                    RoundError.INCONSISTENT_TRY -> TryError.Data(DataError.Network.BAD_REQUEST)
                }
            )
        }
    }

    /** The tier's song, as the server reveals it when a round ends. */
    private suspend fun reveal(tier: Tier): SongReveal? {
        val pick = picks?.get(tier) ?: return null
        val song = searchIndexDao.byIds(listOf(pick.songId)).firstOrNull() ?: return null
        return SongReveal(
            songId = song.songId,
            title = song.title,
            filmTitle = song.filmTitle,
            year = song.year,
            artworkUrl = pick.track.artworkUrl,
            appleMusicUrl = pick.track.appleMusicUrl,
        )
    }

    private companion object {
        /** Song ids are `s_<iTunes track id>`. */
        const val SONG_ID_PREFIX = "s_"
    }
}
