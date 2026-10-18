package com.mukhda.data.show

import com.mukhda.data.di.IoDispatcher
import com.mukhda.domain.DataError
import com.mukhda.domain.Outcome
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Preview URLs for iTunes track ids, from Apple's public lookup API. Only the stand-in show uses
 * this; the real show's previews come from `getShow()`.
 */
class ItunesPreviewLookup @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun previewUrls(trackIds: List<Long>): Outcome<Map<Long, String>, DataError> = withContext(io) {
        val url = URL("https://itunes.apple.com/lookup?country=IN&entity=song&id=${trackIds.joinToString(",")}")
        try {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext Outcome.Error(DataError.Network.SERVER_ERROR)
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val results = json.decodeFromString<LookupResponse>(body).results
                Outcome.Success(results.mapNotNull { r -> r.previewUrl?.let { r.trackId to it } }.toMap())
            } finally {
                connection.disconnect()
            }
        } catch (e: UnknownHostException) {
            Outcome.Error(DataError.Network.NO_INTERNET)
        } catch (e: SocketTimeoutException) {
            Outcome.Error(DataError.Network.REQUEST_TIMEOUT)
        } catch (e: SerializationException) {
            Outcome.Error(DataError.Network.SERIALIZATION)
        } catch (e: IOException) {
            Outcome.Error(DataError.Network.UNKNOWN)
        }
    }

    @Serializable
    private data class LookupResponse(val results: List<LookupResult> = emptyList())

    @Serializable
    private data class LookupResult(val trackId: Long = 0, val previewUrl: String? = null)

    private companion object {
        const val TIMEOUT_MS = 10_000
    }
}
