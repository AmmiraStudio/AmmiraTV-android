package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg_program",
    foreignKeys = [
        ForeignKey(
            entity = EpgSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["epgSourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("epgSourceId"),
        Index(value = ["channelTvgId", "startTime", "endTime"]),
        Index("startTime")
    ]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val epgSourceId: Long,
    val channelTvgId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long,
    val category: String? = null,
    val posterUrl: String? = null
)
