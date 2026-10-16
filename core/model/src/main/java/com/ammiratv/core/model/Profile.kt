package com.ammiratv.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model for user/family profiles.
 * Each profile has its own favorites, watch history, and optional parental controls.
 */
@Serializable
data class Profile(
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null,
    val isChild: Boolean = false,
    val pin: String? = null,
    val dailyLimitMinutes: Int? = null
)
