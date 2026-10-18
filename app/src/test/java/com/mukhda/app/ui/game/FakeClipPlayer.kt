package com.mukhda.app.ui.game

import com.mukhda.domain.DataError
import com.mukhda.domain.EmptyOutcome
import com.mukhda.domain.Outcome
import com.mukhda.domain.audio.ClipPlayer
import com.mukhda.domain.audio.Preview
import kotlinx.coroutines.delay

/** Plays nothing: a clip "lasts" its length in virtual time. */
class FakeClipPlayer : ClipPlayer {
    val cached = mutableListOf<Preview>()
    val played = mutableListOf<Pair<Preview, Long?>>()
    val preloaded = mutableListOf<Pair<Preview, Long?>>()
    var playError: DataError? = null
    var volumeLow = false
    var released = false
        private set
    var speakerWakes = 0
        private set

    override suspend fun cache(previews: Collection<Preview>): EmptyOutcome<DataError> {
        cached += previews
        return Outcome.Success(Unit)
    }

    override suspend fun preload(preview: Preview, lengthMs: Long?) {
        preloaded += preview to lengthMs
    }

    override suspend fun play(preview: Preview, lengthMs: Long?): EmptyOutcome<DataError> {
        played += preview to lengthMs
        playError?.let { return Outcome.Error(it) }
        delay(lengthMs ?: FULL_PREVIEW_MS)
        return Outcome.Success(Unit)
    }

    override suspend fun wakeSpeaker() {
        speakerWakes++
    }

    override fun isVolumeLow(): Boolean = volumeLow

    override fun release() {
        released = true
    }

    private companion object {
        const val FULL_PREVIEW_MS = 30_000L
    }
}
