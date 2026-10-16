package com.ammiratv.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a playlist source.
 * Supports M3U, Xtream Codes API, and Stalker/MAG portals.
 */
@Serializable
data class Playlist(
    val id: Long = 0,
    val name: String,
    val url: String,
    val type: PlaylistType = PlaylistType.M3U,
    val username: String? = null,
    val password: String? = null,
    val lastUpdated: Long = 0L,
    val expirationDate: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int = 0,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    /** Extra servers (DNS / mirrors) of the same provider; the best one is picked automatically. */
    val alternateUrls: List<String> = emptyList()
)

/** Splits what the user typed into server addresses: spaces, commas, semicolons, "|" or new lines. */
object ServerList {
    private val SEPARATORS = Regex("[\\s,;|]+")

    fun parse(text: String): List<String> =
        text.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Scheme + host + port of an address, e.g. "http://srv.example:8080" (no path), or null. */
    fun baseOf(url: String): String? {
        val match = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
        return match.groupValues[1]
    }
}

@Serializable
enum class PlaylistType {
    M3U, XTREAM, STALKER
}

private const val DAY_MS = 86_400_000L

/**
 * Whole days left before the subscription expires.
 * Returns null when the provider reports no expiry (unlimited / unknown),
 * and a negative number when the subscription is already expired.
 */
fun Playlist.daysUntilExpiry(nowMs: Long = System.currentTimeMillis()): Int? {
    val expiry = expirationDate?.takeIf { it > 0 } ?: return null
    return Math.floorDiv(expiry - nowMs, DAY_MS).toInt()
}
