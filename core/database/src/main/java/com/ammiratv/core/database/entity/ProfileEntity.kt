package com.ammiratv.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null,
    val isChild: Boolean = false,
    val pin: String? = null,
    val dailyLimitMinutes: Int? = null
)
