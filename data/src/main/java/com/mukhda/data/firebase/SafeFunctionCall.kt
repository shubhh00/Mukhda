package com.mukhda.data.firebase

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.FirebaseFunctionsException.Code
import com.mukhda.domain.DataError
import com.mukhda.domain.GameError
import com.mukhda.domain.Outcome
import com.mukhda.domain.show.TryError
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a callable function and maps what can go wrong (AGENTS.md §2a): game rule refusals,
 * which the server sends as `failed-precondition` with the reason in `details.reason`, become
 * [TryError.Rule]; everything else becomes a [DataError.Network]. Cancellation is never swallowed.
 */
suspend fun <T> safeFunctionCall(call: suspend () -> T): Outcome<T, TryError> = try {
    Outcome.Success(call())
} catch (e: CancellationException) {
    throw e
} catch (e: FirebaseFunctionsException) {
    Outcome.Error(e.toTryError())
} catch (e: SerializationException) {
    Outcome.Error(TryError.Data(DataError.Network.SERIALIZATION))
} catch (e: IllegalArgumentException) {
    // A response the DTOs can't read (a missing field, an unknown enum value).
    Outcome.Error(TryError.Data(DataError.Network.SERIALIZATION))
} catch (e: Exception) {
    // Before each call the SDK refreshes the sign-in token, and offline that fails with its own
    // exceptions (an ExecutionException around a FirebaseException around an IOException), not
    // a FirebaseFunctionsException. Whatever it throws, the game shows an error, never a crash.
    Outcome.Error(TryError.Data(e.networkCause()))
}

/** What went wrong underneath [this], from its chain of causes. */
fun Throwable.networkCause(): DataError.Network {
    generateSequence(this) { it.cause.takeIf { cause -> cause !== it } }.forEach { cause ->
        when (cause) {
            is InterruptedIOException -> return DataError.Network.REQUEST_TIMEOUT
            is IOException, is FirebaseNetworkException -> return DataError.Network.NO_INTERNET
        }
    }
    // The SDK words a refused connection as "Failed to connect to …" without an IOException.
    if (generateSequence(this) { it.cause }.any { it.message?.contains("Failed to connect", ignoreCase = true) == true }) {
        return DataError.Network.NO_INTERNET
    }
    return DataError.Network.UNKNOWN
}

fun FirebaseFunctionsException.toTryError(): TryError {
    gameError()?.let { return TryError.Rule(it) }
    // The SDK reports a dropped or missing connection as INTERNAL/UNAVAILABLE with an I/O cause.
    when (cause) {
        is InterruptedIOException -> return TryError.Data(DataError.Network.REQUEST_TIMEOUT)
        is IOException -> return TryError.Data(DataError.Network.NO_INTERNET)
    }
    return TryError.Data(
        when (code) {
            Code.DEADLINE_EXCEEDED -> DataError.Network.REQUEST_TIMEOUT
            Code.UNAVAILABLE -> DataError.Network.SERVICE_UNAVAILABLE
            Code.UNAUTHENTICATED -> DataError.Network.UNAUTHORIZED
            Code.PERMISSION_DENIED -> DataError.Network.FORBIDDEN
            Code.RESOURCE_EXHAUSTED -> DataError.Network.TOO_MANY_REQUESTS
            Code.INVALID_ARGUMENT -> DataError.Network.BAD_REQUEST
            Code.NOT_FOUND -> DataError.Network.NOT_FOUND
            Code.ALREADY_EXISTS, Code.ABORTED -> DataError.Network.CONFLICT
            Code.INTERNAL, Code.DATA_LOSS, Code.UNKNOWN -> DataError.Network.SERVER_ERROR
            else -> DataError.Network.UNKNOWN
        }
    )
}

/** The game rule behind a `failed-precondition`, from the error's `details.reason`. */
private fun FirebaseFunctionsException.gameError(): GameError? {
    if (code != Code.FAILED_PRECONDITION) return null
    val reason = (details as? Map<*, *>)?.get("reason") as? String ?: return null
    return GameError.entries.firstOrNull { it.name == reason }
}
