package com.mukhda.data.firebase

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.mukhda.domain.DataError
import com.mukhda.domain.Outcome
import com.mukhda.domain.show.TryError
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutionException

class SafeFunctionCallTest {

    @Test
    fun `an offline token refresh is no connection, not a crash`() = runTest {
        // What the SDK threw on the phone with the laptop unreachable.
        val offline = ExecutionException(
            "1 out of 2 underlying tasks failed",
            RuntimeException("An internal error has occurred. [ Failed to connect to /127.0.0.1:9099 ]"),
        )
        val result = safeFunctionCall<Unit> { throw offline }
        assertThat(result).isEqualTo(Outcome.Error(TryError.Data(DataError.Network.NO_INTERNET)))
    }

    @Test
    fun `a sign-in the server doesn't know is signed out, so it can be renewed`() = runTest {
        // What the SDK threw on the phone still signed in from the emulator.
        val stale = ExecutionException(
            "1 out of 2 underlying tasks failed",
            RuntimeException("An internal error has occurred. [ INVALID_REFRESH_TOKEN ]"),
        )
        val result = safeFunctionCall<Unit> { throw stale }
        assertThat(result).isEqualTo(Outcome.Error(TryError.Data(DataError.Network.UNAUTHORIZED)))
    }

    @Test
    fun `causes underneath decide the error`() {
        assertThat(ExecutionException(RuntimeException(IOException("reset"))).networkCause()).isEqualTo(DataError.Network.NO_INTERNET)
        assertThat(RuntimeException(SocketTimeoutException()).networkCause()).isEqualTo(DataError.Network.REQUEST_TIMEOUT)
        assertThat(IllegalStateException("something else").networkCause()).isEqualTo(DataError.Network.UNKNOWN)
    }
}
