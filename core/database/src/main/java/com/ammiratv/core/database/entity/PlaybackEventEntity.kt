package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One observation of how a stream behaved. Feeds the provider report card, the diagnostics
 * verdict and the learning buffer. [type]: START_OK, START_FAILED, REBUFFER, STALL.
 */
@Entity(
    tableName = "playback_event",
    indices = [Index("playlistId", "timestamp"), Index("timestamp")]
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val channelId: Long,
    val timestamp: Long,
    val type: String,
    /** START_OK: time to first frame. REBUFFER: how long playback was stalled. */
    val durationMs: Long = 0
)
