package com.mukhda.data.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Finds where the music starts in a preview. Apple's previews often open with a second or two of
 * near silence; a clip cut from there is silence and hiss, which sounds like static.
 */
internal object LeadInDetector {
    private const val WINDOW_MS = 25
    /**
     * A clipped start can land up to one decoded frame (about 46 ms for these HE-AAC previews)
     * before the time asked for, so clips start this much after the measured onset.
     */
    private const val ONSET_MARGIN_MS = 50L
    private const val SCAN_MS = 8_000L
    /** A window this loud, relative to the preview's loud parts, counts as the music starting. */
    private const val ONSET_FRACTION = 0.1
    /** Below this (of 32,767) the whole scan is quiet: leave the start alone. */
    private const val QUIET_PREVIEW_RMS = 300.0
    private const val TIMEOUT_US = 10_000L

    /** Milliseconds of lead-in at the start of [file], or 0 when there's none or it can't be read. */
    fun leadInMs(file: File): Long = runCatching { windowLevels(file) }.getOrNull()?.let(::leadInMs) ?: 0

    /** Lead-in for per-window loudness [levels], 25 ms apart. */
    fun leadInMs(levels: List<Double>): Long {
        if (levels.isEmpty()) return 0
        val reference = levels.sorted()[(levels.size * 0.9).toInt().coerceAtMost(levels.size - 1)]
        if (reference < QUIET_PREVIEW_RMS) return 0
        val onset = levels.indexOfFirst { it >= reference * ONSET_FRACTION }
        // Starting a little late only trims the first note's attack; starting early can leave
        // the first clip mostly silent.
        return if (onset <= 0) 0 else onset.toLong() * WINDOW_MS + ONSET_MARGIN_MS
    }

    /** Loudness (RMS of the first channel) of each 25 ms window in the first 8 s. */
    private fun windowLevels(file: File): List<Double> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return emptyList()
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
                configure(format, null, null, 0)
                start()
            }

            val levels = mutableListOf<Double>()
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var sum = 0.0
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (true) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
                        if (size < 0 || extractor.sampleTime > SCAN_MS * 1000) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    out >= 0 -> {
                        val windowSamples = sampleRate * WINDOW_MS / 1000
                        val pcm = codec.getOutputBuffer(out)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        var i = info.offset / 2
                        val end = (info.offset + info.size) / 2
                        while (i < end) {
                            val sample = pcm.get(i).toDouble()
                            sum += sample * sample
                            if (++count == windowSamples) {
                                levels += sqrt(sum / count)
                                sum = 0.0
                                count = 0
                            }
                            i += channels
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return levels
                    }
                }
            }
        } finally {
            codec?.stop()
            codec?.release()
            extractor.release()
        }
    }
}
