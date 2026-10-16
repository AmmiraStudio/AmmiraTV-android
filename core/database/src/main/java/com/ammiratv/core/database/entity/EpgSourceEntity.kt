package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "epg_source")
data class EpgSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val name: String,
    val lastUpdated: Long = 0L
)
