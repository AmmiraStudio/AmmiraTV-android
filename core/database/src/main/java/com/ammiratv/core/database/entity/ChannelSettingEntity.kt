package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-channel playback preferences ("profili per canale"): aspect ratio, preferred tracks.
 * Keyed by stream URL so they survive a playlist re-import.
 */
@Entity(tableName = "channel_setting")
data class ChannelSettingEntity(
    @PrimaryKey val streamUrl: String,
    val aspectRatio: String? = null,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val audioDelayMs: Int = 0
)
