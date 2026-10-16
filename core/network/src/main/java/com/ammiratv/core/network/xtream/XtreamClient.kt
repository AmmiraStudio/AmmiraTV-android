package com.ammiratv.core.network.xtream

import com.ammiratv.core.model.SeriesDetails
import com.ammiratv.core.model.VodCategory
import com.ammiratv.core.model.VodDetails
import com.ammiratv.core.model.VodItem
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeToSequence
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamClient @Inject constructor(
    private val retrofit: Retrofit
) {
    private val api: XtreamApiService by lazy {
        retrofit.create(XtreamApiService::class.java)
    }

    private fun normalizeEndpoint(baseUrl: String): String {
        var url = baseUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        return "${url}player_api.php"
    }

    suspend fun authenticate(serverUrl: String, user: String, pass: String): XtreamAuthResponse {
        val endpoint = normalizeEndpoint(serverUrl)
        return api.authenticate(endpoint, user, pass)
    }

    suspend fun getLiveCategories(serverUrl: String, user: String, pass: String): List<XtreamCategory> {
        val endpoint = normalizeEndpoint(serverUrl)
        return api.getLiveCategories(endpoint, user, pass)
    }

    suspend fun getLiveStreams(
        serverUrl: String,
        user: String,
        pass: String,
        categoryId: String? = null
    ): List<XtreamLiveStream> {
        val endpoint = normalizeEndpoint(serverUrl)
        return api.getLiveStreams(endpoint, user, pass, categoryId = categoryId)
    }

    /**
     * Reads the live catalogue element by element and hands it over in chunks, so a 50k-channel
     * answer (tens of MB of JSON) never exists in memory as one string or one list.
     * Returns how many streams were read.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    suspend fun streamLiveStreams(
        serverUrl: String,
        user: String,
        pass: String,
        chunkSize: Int = 500,
        onChunk: suspend (List<XtreamLiveStream>) -> Unit
    ): Int {
        val body = api.callStreaming(normalizeEndpoint(serverUrl), user, pass, "get_live_streams")
        // No context switch here: the caller (a flow already running on IO) emits progress from
        // inside [onChunk], which must stay in the collector's own coroutine.
        return run {
            body.use { responseBody ->
                responseBody.byteStream().buffered(64 * 1024).use { stream ->
                    val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
                    var total = 0
                    val buffer = ArrayList<XtreamLiveStream>(chunkSize)
                    // Elements are decoded one by one; a malformed one is skipped, not fatal
                    val elements = json.decodeToSequence(stream, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
                    for (element in elements) {
                        val stream1 = runCatching {
                            json.decodeFromJsonElement(XtreamLiveStream.serializer(), element)
                        }.getOrNull() ?: continue
                        buffer += stream1
                        if (buffer.size >= chunkSize) {
                            onChunk(ArrayList(buffer))
                            total += buffer.size
                            buffer.clear()
                        }
                    }
                    if (buffer.isNotEmpty()) {
                        onChunk(ArrayList(buffer))
                        total += buffer.size
                    }
                    total
                }
            }
        }
    }

    // ── VOD & series ─────────────────────────────────────────────────────

    suspend fun getVodCategories(serverUrl: String, user: String, pass: String): List<VodCategory> =
        XtreamVodParser.parseCategories(call(serverUrl, user, pass, "get_vod_categories"))

    suspend fun getMovies(serverUrl: String, user: String, pass: String, playlistId: Long): List<VodItem> =
        streamItems(serverUrl, user, pass, "get_vod_streams") { el ->
            XtreamVodParser.parseMovies(JsonArray(listOf(el)), playlistId)
        }

    suspend fun getVodInfo(serverUrl: String, user: String, pass: String, vodId: Long): VodDetails =
        XtreamVodParser.parseVodInfo(call(serverUrl, user, pass, "get_vod_info", vodId = vodId))

    suspend fun getSeriesCategories(serverUrl: String, user: String, pass: String): List<VodCategory> =
        XtreamVodParser.parseCategories(call(serverUrl, user, pass, "get_series_categories"))

    suspend fun getSeries(serverUrl: String, user: String, pass: String, playlistId: Long): List<VodItem> =
        streamItems(serverUrl, user, pass, "get_series") { el ->
            XtreamVodParser.parseSeriesList(JsonArray(listOf(el)), playlistId)
        }

    /**
     * Reads a big JSON array one element at a time so memory stays bounded by the size of one
     * item, not of the whole catalogue. A provider that answers with something other than an
     * array (an error object, `[]`) yields an empty list.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private suspend fun streamItems(
        serverUrl: String,
        user: String,
        pass: String,
        action: String,
        parse: (JsonElement) -> List<VodItem>
    ): List<VodItem> {
        val body = api.callStreaming(normalizeEndpoint(serverUrl), user, pass, action)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            body.use { responseBody ->
                responseBody.byteStream().use { stream ->
                    try {
                        Json { ignoreUnknownKeys = true; isLenient = true }
                            .decodeToSequence(stream, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED)
                            .flatMap { parse(it).asSequence() }
                            .toList()
                    } catch (_: kotlinx.serialization.SerializationException) {
                        emptyList()
                    } catch (_: IllegalArgumentException) {
                        emptyList()
                    }
                }
            }
        }
    }

    suspend fun getSeriesInfo(serverUrl: String, user: String, pass: String, seriesId: Long): SeriesDetails =
        XtreamVodParser.parseSeriesInfo(call(serverUrl, user, pass, "get_series_info", seriesId = seriesId))

    private suspend fun call(
        serverUrl: String,
        user: String,
        pass: String,
        action: String,
        vodId: Long? = null,
        seriesId: Long? = null
    ) = api.call(normalizeEndpoint(serverUrl), user, pass, action, vodId, seriesId)

    fun buildMovieUrl(serverUrl: String, user: String, pass: String, streamId: Long, extension: String): String =
        "${baseOf(serverUrl)}/movie/$user/$pass/$streamId.$extension"

    fun buildEpisodeUrl(serverUrl: String, user: String, pass: String, episodeId: Long, extension: String): String =
        "${baseOf(serverUrl)}/series/$user/$pass/$episodeId.$extension"

    private fun baseOf(serverUrl: String): String {
        var base = serverUrl.trim()
        if (!base.startsWith("http://") && !base.startsWith("https://")) base = "http://$base"
        return base.trimEnd('/')
    }

    /** Standard Xtream XMLTV endpoint: `{server}/xmltv.php?username=..&password=..` */
    fun buildXmltvUrl(serverUrl: String, user: String, pass: String): String {
        var base = serverUrl.trim()
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }
        base = base.trimEnd('/')
        val u = java.net.URLEncoder.encode(user, "UTF-8")
        val p = java.net.URLEncoder.encode(pass, "UTF-8")
        return "$base/xmltv.php?username=$u&password=$p"
    }

    fun buildLiveStreamUrl(
        serverUrl: String,
        user: String,
        pass: String,
        streamId: Int,
        extension: String = "ts"
    ): String {
        var base = serverUrl.trim()
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://$base"
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length - 1)
        }
        return "$base/live/$user/$pass/$streamId.$extension"
    }
}
