package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channel",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("playlistId"),
        Index("categoryId"),
        Index("isFavorite"),
        Index("tvgId"),
        Index("cleanName"),
        Index("channelNumber")
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val categoryId: Long,
    val name: String,
    val cleanName: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val streamType: Int, // 0=LIVE, 1=VOD, 2=SERIES
    val tvgId: String? = null,
    val tvgName: String? = null,
    val groupTitle: String? = null,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val reliability: Int = 0,
    val lastWatched: Long? = null,
    val watchPosition: Long? = null,
    /** TV-style number computed by the numbering engine; null when the channel has none. */
    val channelNumber: Int? = null
)
