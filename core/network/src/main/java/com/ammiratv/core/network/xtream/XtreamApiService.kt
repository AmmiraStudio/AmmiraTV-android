package com.ammiratv.core.network.xtream

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface XtreamApiService {

    @GET
    suspend fun authenticate(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamAuthResponse

    @GET
    suspend fun getLiveCategories(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<XtreamCategory>

    @GET
    suspend fun getLiveStreams(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams",
        @Query("category_id") categoryId: String? = null
    ): List<XtreamLiveStream>

    /**
     * Generic call for the VOD/series actions. They return JsonElement because providers mix
     * types (see [XtreamVodParser]); `action` is one of get_vod_categories, get_vod_streams,
     * get_vod_info, get_series_categories, get_series, get_series_info.
     */
    /**
     * Same endpoint as [call] but returned as a raw stream. The full movie/series catalogue can
     * be tens of MB: decoding it into one JsonElement tree ran the app out of memory, so those
     * two actions are read element by element from this stream instead.
     */
    @retrofit2.http.Streaming
    @GET
    suspend fun callStreaming(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): okhttp3.ResponseBody

    @GET
    suspend fun call(
        @Url url: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String,
        @Query("vod_id") vodId: Long? = null,
        @Query("series_id") seriesId: Long? = null
    ): JsonElement
}
