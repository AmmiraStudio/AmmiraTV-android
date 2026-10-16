package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val url: String,
    val type: Int, // 0=M3U, 1=XTREAM, 2=STALKER
    val username: String? = null,
    val password: String? = null,
    val lastUpdated: Long = 0L,
    val expirationDate: Long? = null,
    val maxConnections: Int? = null,
    val activeConnections: Int = 0,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    /** Extra servers of the same provider, one per line; null when there are none. */
    val alternateUrls: String? = null
)
