package com.ammiratv.core.network.xtream

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XtreamAuthResponse(
    @SerialName("user_info") val userInfo: XtreamUserInfo? = null,
    @SerialName("server_info") val serverInfo: XtreamServerInfo? = null
)

@Serializable
data class XtreamUserInfo(
    @SerialName("username") val username: String = "",
    @SerialName("status") val status: String = "",
    @SerialName("exp_date") val expDate: String? = null,
    @SerialName("is_trial") val isTrial: String? = null,
    @SerialName("active_cons") val activeCons: String? = "0",
    @SerialName("max_connections") val maxConnections: String? = null
) {
    val activeConnectionsCount: Int
        get() = activeCons?.toIntOrNull() ?: 0

    val maxConnectionsCount: Int?
        get() = maxConnections?.toIntOrNull()

    val expirationTimestamp: Long?
        get() = expDate?.toLongOrNull()?.let { it * 1000 }
}

@Serializable
data class XtreamServerInfo(
    @SerialName("url") val url: String? = null,
    @SerialName("port") val port: String? = null,
    @SerialName("server_protocol") val serverProtocol: String? = null,
    @SerialName("timezone") val timezone: String? = null
)

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val categoryId: String,
    @SerialName("category_name") val categoryName: String,
    @SerialName("parent_id") val parentId: Int = 0
)

@Serializable
data class XtreamLiveStream(
    @SerialName("num") val num: Int = 0,
    @SerialName("name") val name: String,
    @SerialName("stream_type") val streamType: String? = "live",
    @SerialName("stream_id") val streamId: Int,
    @SerialName("stream_icon") val streamIcon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String = "0"
)
