package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A scheduled, running or finished recording. [status]: SCHEDULED, RECORDING, DONE, FAILED, CANCELLED. */
@Entity(tableName = "recording", indices = [Index("startTime"), Index("status")])
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val channelName: String,
    val title: String,
    val streamUrl: String,
    val startTime: Long,
    val endTime: Long,
    val status: String,
    val filePath: String? = null,
    val sizeBytes: Long = 0,
    val errorMessage: String? = null
)
