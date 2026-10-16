package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A number the user chose for a channel. Keyed by channel identity (tvg-id or cleaned name, see
 * DuplicateMerger.keyOf) rather than by row id, so it survives a playlist refresh, which
 * recreates the channel rows.
 */
@Entity(tableName = "manual_channel_number")
data class ManualChannelNumberEntity(
    @PrimaryKey val key: String,
    val number: Int
)
