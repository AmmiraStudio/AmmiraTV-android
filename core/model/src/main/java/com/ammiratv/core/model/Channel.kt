package com.ammiratv.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a TV channel or stream.
 * This is a pure Kotlin data class with no Android dependencies.
 */
@Serializable
data class Channel(
    val id: Long = 0,
    val playlistId: Long = 0,
    val categoryId: Long = 0,
    val name: String,
    val cleanName: String = name,
    val streamUrl: String,
    val logoUrl: String? = null,
    val streamType: StreamType = StreamType.LIVE,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val groupTitle: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val reliability: Int = 0,
    val lastWatched: Long? = null,
    val watchPosition: Long? = null,
    /** TV-style number (e.g. Rai 1 = 1); null when the channel has none. Live Italian channels only. */
    val channelNumber: Int? = null
)

@Serializable
enum class StreamType {
    LIVE, VOD, SERIES
}
