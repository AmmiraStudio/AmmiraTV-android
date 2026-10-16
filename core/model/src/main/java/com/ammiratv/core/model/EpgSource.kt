package com.ammiratv.core.model

/**
 * An XMLTV guide source (URL) that EPG programs are imported from.
 */
data class EpgSource(
    val id: Long = 0,
    val url: String,
    val name: String,
    val lastUpdated: Long = 0L
)
