package com.ammiratv.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a channel category/group.
 */
@Serializable
data class Category(
    val id: Long = 0,
    val playlistId: Long = 0,
    val name: String,
    val type: StreamType = StreamType.LIVE,
    val sortOrder: Int = 0,
    val isHidden: Boolean = false
)
