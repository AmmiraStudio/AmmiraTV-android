package com.ammiratv.core.network.m3u

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads M3U playlist streams via OkHttp.
 */
@Singleton
class M3uDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    /**
     * Follows [url] (redirects included) and returns the final address when it is a live HLS
     * playlist, or null when it is not reachable or leads to something else (for example the short
     * "not available in your country" video that Rai serves outside Italy).
     */
    fun resolveLiveHls(url: String, timeoutMs: Long): String? = try {
        val client = okHttpClient.newBuilder().callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val type = response.header("Content-Type").orEmpty().lowercase()
            val isHls = type.contains("mpegurl") || finalUrl.substringBefore('?').endsWith(".m3u8")
            val isVodClip = finalUrl.contains("/podcastcdn/") || finalUrl.substringBefore('?').contains(".mp4")
            if (response.isSuccessful && isHls && !isVodClip) finalUrl else null
        }
    } catch (_: Exception) {
        null
    }

    /** True when [url] answers within [timeoutMs] with something that looks like an M3U playlist. */
    fun looksLikePlaylist(url: String, timeoutMs: Long): Boolean = try {
        val client = okHttpClient.newBuilder().callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS).build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val head = response.body?.byteStream()?.let { s ->
                val buf = ByteArray(512)
                val n = s.read(buf)
                if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
            }.orEmpty()
            head.trimStart().startsWith("#EXTM3U") || head.contains("#EXTINF")
        }
    } catch (_: Exception) {
        false
    }

    @Throws(IOException::class)
    fun downloadStream(url: String): InputStream {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP error ${response.code}: ${response.message}")
        }
        val body = response.body ?: throw IOException("Empty response body")
        return body.byteStream()
    }
}
