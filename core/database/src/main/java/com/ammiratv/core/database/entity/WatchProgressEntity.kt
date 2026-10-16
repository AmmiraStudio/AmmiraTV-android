package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * Where a profile stopped in a movie or an episode ("Continua a guardare" + watched marks).
 *
 * [contentKey] is stable across playlist refreshes: "movie:<playlistId>:<streamId>" or
 * "episode:<playlistId>:<episodeId>" (ids come from the provider, not from our database).
 */
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["profileId", "contentKey"],
    indices = [Index("profileId", "updatedAt")]
)
data class WatchProgressEntity(
    val profileId: Long,
    val contentKey: String,
    val kind: String, // MOVIE, EPISODE
    val playlistId: Long,
    val title: String,
    val subtitle: String? = null, // e.g. "S2 E5 · Pilot"
    val posterUrl: String? = null,
    val streamUrl: String,
    val seriesId: Long? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isWatched: Boolean = false,
    val updatedAt: Long = 0
)
