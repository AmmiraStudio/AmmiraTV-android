package com.ammiratv.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model for an EPG (Electronic Program Guide) entry.
 */
@Serializable
data class EpgProgram(
    val id: Long = 0,
    val epgSourceId: Long = 0,
    val channelTvgId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
    val posterUrl: String? = null
)
