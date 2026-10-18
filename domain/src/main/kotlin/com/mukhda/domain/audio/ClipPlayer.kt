package com.mukhda.domain.audio

import com.mukhda.domain.DataError
import com.mukhda.domain.EmptyOutcome

/** A tier's iTunes preview and where in it the clips start. */
data class Preview(
    val url: String,
    /**
     * Where clips start, when curation picked a point (say, the start of the mukhda). At 0 the
     * player starts clips where the preview's music starts, skipping any silent lead-in.
     */
    val startOffsetMs: Long = 0,
)

/**
 * Plays exact windows of previews. Clips never stream: [cache] downloads whole previews first, so
 * a 100 ms clip can't stall halfway.
 */
interface ClipPlayer {
    /** Downloads every preview not cached yet. Retries a failed download before giving up. */
    suspend fun cache(previews: Collection<Preview>): EmptyOutcome<DataError>

    /** Loads the clip [play] is likely to be asked for next, so the tap only has to start it. */
    suspend fun preload(preview: Preview, lengthMs: Long?)

    /**
     * Plays [lengthMs] of [preview] from its clip start (see [Preview.startOffsetMs]), or to the
     * end when [lengthMs] is null, and returns when the clip has finished. Cancelling the call
     * stops the clip.
     */
    suspend fun play(preview: Preview, lengthMs: Long?): EmptyOutcome<DataError>

    /**
     * Plays a moment of silence to wake the phone's speaker path, which sleeps 3 s after the last
     * sound and loses the start of a clip while it wakes. Call when a play is likely soon; a play
     * within a couple of seconds then starts at once. Doesn't take audio focus.
     */
    suspend fun wakeSpeaker()

    /** True when media volume is low enough that a 0.1 s clip could go unheard. */
    fun isVolumeLow(): Boolean

    /** Frees the player. The next [play] creates a new one. */
    fun release()
}
