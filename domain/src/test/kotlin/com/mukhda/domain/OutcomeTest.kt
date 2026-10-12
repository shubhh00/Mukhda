package com.mukhda.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test

class OutcomeTest {

    private val success: Outcome<Int, DataError> = Outcome.Success(2)
    private val failure: Outcome<Int, DataError> = Outcome.Error(DataError.Network.NO_INTERNET)

    @Test
    fun `map transforms success and passes errors through`() {
        assertThat(success.map { it * 10 }).isEqualTo(Outcome.Success(20))
        assertThat(failure.map { it * 10 }).isEqualTo(Outcome.Error(DataError.Network.NO_INTERNET))
    }

    @Test
    fun `onSuccess and onFailure run only on their branch`() {
        var seen: Any? = null
        failure.onSuccess { seen = it }
        assertThat(seen).isNull()
        failure.onFailure { seen = it }
        assertThat(seen).isEqualTo(DataError.Network.NO_INTERNET)
    }

    @Test
    fun `asEmptyOutcome drops the value`() {
        assertThat(success.asEmptyOutcome()).isEqualTo(Outcome.Success(Unit))
    }
}
