package com.mukhda.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.SilenceMediaSource
import com.mukhda.data.di.IoDispatcher
import com.mukhda.domain.DataError
import com.mukhda.domain.EmptyOutcome
import com.mukhda.domain.Outcome
import com.mukhda.domain.audio.ClipPlayer
import com.mukhda.domain.audio.ClipProgress
import com.mukhda.domain.audio.Preview
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * ExoPlayer over a disk cache of whole previews. A clip is a [MediaItem.ClippingConfiguration]
 * window, so it ends where the clip ends rather than when a timer fires; that keeps short clips
 * exact on slow phones too. The player lives on the main thread, as ExoPlayer requires.
 */
@OptIn(UnstableApi::class)
@Singleton
class Media3ClipPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ClipPlayer {

    private val cache by lazy {
        SimpleCache(
            File(context.cacheDir, CACHE_DIR),
            LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }
    private val dataSourceFactory by lazy {
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setConnectTimeoutMs(TIMEOUT_MS)
                    .setReadTimeoutMs(TIMEOUT_MS),
            )
    }

    private val mediaSourceFactory by lazy { DefaultMediaSourceFactory(dataSourceFactory) }

    /**
     * Speaker path state, from our own sounds: when it last woke, and when our last sound ends.
     * Android puts the path to sleep 3 s after the last sound.
     */
    @Volatile private var awakeSince = 0L
    @Volatile private var lastSoundAt = 0L

    /** Main thread only, like everything below that touches it. */
    private var player: ExoPlayer? = null
    /** The clip the player holds (url, start, length), so a replay or a preloaded clip skips loading. */
    private var loaded: Triple<String, Long, Long?>? = null
    /**
     * Lead-in silence per preview url. Measuring takes a few hundred ms per preview, so it's done
     * once per preview and kept on disk; a preview's audio doesn't change.
     */
    private val leadIns = ConcurrentHashMap<String, Long>()
    private val leadInStore by lazy { context.getSharedPreferences(LEAD_IN_PREFS, Context.MODE_PRIVATE) }

    override suspend fun cache(previews: Collection<Preview>): EmptyOutcome<DataError> = withContext(io) {
        for (preview in previews) {
            val result = cacheWithRetries(preview)
            if (result is Outcome.Error) return@withContext result
        }
        Outcome.Success(Unit)
    }

    override suspend fun preload(preview: Preview, lengthMs: Long?) {
        withContext(Dispatchers.Main.immediate) { load(preview, lengthMs) }
    }

    override suspend fun play(preview: Preview, lengthMs: Long?): EmptyOutcome<DataError> {
        // Never start a clip that isn't fully on disk: a stalled half-second clip is a broken round.
        val cached = cache(listOf(preview))
        if (cached is Outcome.Error) return cached

        return withContext(Dispatchers.Main.immediate) {
            val player = load(preview, lengthMs)
            var listener: Player.Listener? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    fun finish(result: EmptyOutcome<DataError>) {
                        if (continuation.isActive) continuation.resume(result)
                    }
                    listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) finish(Outcome.Success(Unit))
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            loaded = null
                            finish(Outcome.Error(error.toDataError()))
                        }

                        // Another app took the audio for good, or the headphones came out: the
                        // clip is over.
                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            if (!playWhenReady && reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
                                finish(Outcome.Success(Unit))
                            }
                        }

                        // A call takes the audio only for a while: Media3 holds the clip and would
                        // resume it halfway through afterwards. End it instead.
                        override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                            if (playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                                finish(Outcome.Success(Unit))
                            }
                        }
                    }.also(player::addListener)
                    // Android powers the speaker path down after 3 s of silence, and waking it eats
                    // the first ~100 ms of sound, a fifth of the first clip. From cold, the warm-up silence
                    // plays first; warm, the clip starts at once.
                    val now = SystemClock.elapsedRealtime()
                    val cold = !isSpeakerAwake(now)
                    if (cold) awakeSince = now
                    val from = if (cold) WARM_UP_INDEX else CLIP_INDEX
                    if (player.currentMediaItemIndex != from || player.currentPosition > 0) player.seekTo(from, 0)
                    player.play()
                }
            } finally {
                listener?.let(player::removeListener)
                lastSoundAt = SystemClock.elapsedRealtime()
                // Stopped early or finished: rewind now, while nobody is waiting.
                player.pause()
                park(player)
            }
        }
    }

    /**
     * Main thread. Loads the clip, behind a short warm-up silence, unless it's already loaded, and
     * parks it (see [park]).
     */
    private fun load(preview: Preview, lengthMs: Long?): ExoPlayer {
        val player = player ?: createPlayer().also { player = it }
        val start = clipStartMs(preview)
        val key = Triple(preview.url, start, lengthMs)
        if (loaded != key || player.playbackState == Player.STATE_IDLE) {
            val clip = mediaSourceFactory.createMediaSource(
                MediaItem.Builder()
                    .setUri(preview.url)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(start)
                            .setEndPositionMs(lengthMs?.let { start + it } ?: C.TIME_END_OF_SOURCE)
                            .build(),
                    )
                    .build(),
            )
            player.setMediaSources(listOf(SilenceMediaSource(WARM_UP_US), clip), WARM_UP_INDEX, 0)
            player.prepare()
            park(player)
            loaded = key
        }
        return player
    }

    override suspend fun wakeSpeaker() {
        val now = SystemClock.elapsedRealtime()
        if (!isSpeakerAwake(now)) awakeSince = now
        lastSoundAt = now + WARM_UP_MS
        // A silent track of its own, without audio focus, so music in another app keeps playing.
        val bytes = (WARM_UP_SAMPLE_RATE * WARM_UP_MS / 1000).toInt() * BYTES_PER_STEREO_FRAME
        withContext(Dispatchers.Main.immediate) { player?.takeIf { !it.isPlaying }?.let(::park) }
        // Built, played and released inside one block, so a failed write or play, or a cancelled
        // caller, still releases it: a process can only hold a few AudioTracks.
        withContext(io) {
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(WARM_UP_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build(),
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(bytes)
                    .build()
            }.getOrNull() ?: return@withContext
            try {
                track.write(ByteArray(bytes), 0, bytes)
                track.play()
                delay(WARM_UP_MS + RELEASE_MARGIN_MS)
            } catch (e: IllegalStateException) {
                // The track didn't initialise: the clip's own warm-up covers this tap.
            } finally {
                track.release()
            }
        }
    }

    /**
     * Main thread. Parks the player where the next tap will start, so the tap only has to press
     * play: at the clip while the speaker is awake or waking, at the warm-up once it has gone back
     * to sleep (and moves it there when that happens).
     */
    private fun park(player: ExoPlayer) {
        mainHandler.removeCallbacks(parkForColdSpeaker)
        val awakeFor = lastSoundAt + SPEAKER_COLD_AFTER_MS - SystemClock.elapsedRealtime()
        val target = if (awakeFor > 0) CLIP_INDEX else WARM_UP_INDEX
        if (player.currentMediaItemIndex != target || player.currentPosition > 0) player.seekTo(target, 0)
        if (awakeFor > 0) mainHandler.postDelayed(parkForColdSpeaker, awakeFor)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val parkForColdSpeaker = Runnable { player?.takeIf { !it.isPlaying }?.let(::park) }

    /** Woken long enough ago to be fully on, and not yet back in standby. */
    private fun isSpeakerAwake(now: Long): Boolean =
        now - awakeSince >= SPEAKER_WAKE_MS && now - lastSoundAt <= SPEAKER_COLD_AFTER_MS

    override fun progress(): ClipProgress? {
        val player = player?.takeIf { it.isPlaying } ?: return null
        // During the warm-up silence the clip hasn't started.
        if (player.currentMediaItemIndex != CLIP_INDEX) return ClipProgress(playedMs = 0, lengthMs = null)
        return ClipProgress(playedMs = player.currentPosition, lengthMs = player.duration.takeIf { it != C.TIME_UNSET })
    }

    override fun isVolumeLow(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return max > 0 && audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max < LOW_VOLUME
    }

    override fun release() {
        mainHandler.removeCallbacks(parkForColdSpeaker)
        player?.release()
        player = null
        loaded = null
    }

    private fun createPlayer(): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

    /** The server's offset when it set one; otherwise where the music starts after the lead-in. */
    private fun clipStartMs(preview: Preview): Long =
        preview.startOffsetMs.takeIf { it > 0 } ?: leadIn(preview.url) ?: 0

    private fun leadIn(url: String): Long? =
        leadIns[url] ?: leadInStore.takeIf { it.contains(url) }?.getLong(url, 0)?.also { leadIns[url] = it }

    /** Reads the cached preview into a temp file and measures its lead-in. IO thread. */
    private fun measureLeadIn(url: String) {
        if (leadIn(url) != null) return
        var file: File? = null
        try {
            // Inside the try: on a full disk this throws too, and that mustn't escape play().
            file = File.createTempFile("preview", ".m4a", context.cacheDir)
            val source = dataSourceFactory.createDataSource()
            source.open(DataSpec(url.toUri()))
            try {
                file.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer, 0, buffer.size)
                        if (read == C.RESULT_END_OF_INPUT) break
                        out.write(buffer, 0, read)
                    }
                }
            } finally {
                source.close()
            }
            val measured = LeadInDetector.leadInMs(file)
            leadIns[url] = measured
            leadInStore.edit().putLong(url, measured).apply()
        } catch (e: IOException) {
            // Unreadable: clips start at 0, as they would without the check.
            leadIns[url] = 0
        } finally {
            file?.delete()
        }
    }

    private suspend fun cacheWithRetries(preview: Preview): EmptyOutcome<DataError> {
        val result = downloadWithRetries(preview)
        if (result is Outcome.Success) measureLeadIn(preview.url)
        return result
    }

    private suspend fun downloadWithRetries(preview: Preview): EmptyOutcome<DataError> {
        if (isFullyCached(preview.url)) return Outcome.Success(Unit)
        var lastError: DataError = DataError.Network.UNKNOWN
        repeat(DOWNLOAD_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_BASE_MS shl (attempt - 1))
            try {
                CacheWriter(dataSourceFactory.createDataSource(), DataSpec(preview.url.toUri()), null, null).cache()
                return Outcome.Success(Unit)
            } catch (e: IOException) {
                lastError = e.toDataError()
                // Nothing to gain from retrying a missing preview, a full disk, or no connection
                // (the game sends the downloads again once the phone is back online).
                if (lastError in NO_RETRY) return Outcome.Error(lastError)
            }
        }
        return Outcome.Error(lastError)
    }

    private fun isFullyCached(url: String): Boolean {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(url))
        return length != C.LENGTH_UNSET.toLong() && cache.isCached(url, 0, length)
    }

    private companion object {
        const val CACHE_DIR = "clips"
        // A day's five previews are about 1 MB each; this keeps a few days for a quick replay.
        const val CACHE_BYTES = 50L * 1024 * 1024
        const val TIMEOUT_MS = 10_000
        const val DOWNLOAD_ATTEMPTS = 3
        const val RETRY_BASE_MS = 1_000L
        val NO_RETRY = setOf(DataError.Network.NOT_FOUND, DataError.Local.DISK_FULL, DataError.Network.NO_INTERNET)
        const val LOW_VOLUME = 0.3f
        const val BUFFER_BYTES = 64 * 1024
        const val LEAD_IN_PREFS = "clip_lead_ins"
        /** Android's output standby is 3 s; a little under that, the path may already be asleep. */
        const val SPEAKER_COLD_AFTER_MS = 2_500L
        /** How long the path takes to come up; measured at 50-107 ms on a OnePlus 7. */
        const val SPEAKER_WAKE_MS = 150L
        /** Comfortably longer than the wake-up. */
        const val WARM_UP_MS = 300L
        const val WARM_UP_US = WARM_UP_MS * 1000
        const val WARM_UP_SAMPLE_RATE = 44_100
        const val BYTES_PER_STEREO_FRAME = 4
        const val RELEASE_MARGIN_MS = 100L
        const val WARM_UP_INDEX = 0
        const val CLIP_INDEX = 1
    }
}

private fun Throwable.toDataError(): DataError {
    var cause: Throwable? = this
    while (cause != null) {
        when (cause) {
            is UnknownHostException -> return DataError.Network.NO_INTERNET
            is SocketTimeoutException -> return DataError.Network.REQUEST_TIMEOUT
            is HttpDataSource.InvalidResponseCodeException -> return when (cause.responseCode) {
                404, 410 -> DataError.Network.NOT_FOUND
                429 -> DataError.Network.TOO_MANY_REQUESTS
                in 500..599 -> DataError.Network.SERVER_ERROR
                else -> DataError.Network.UNKNOWN
            }
        }
        if (cause.message?.contains("ENOSPC") == true) return DataError.Local.DISK_FULL
        cause = cause.cause
    }
    return DataError.Network.UNKNOWN
}
