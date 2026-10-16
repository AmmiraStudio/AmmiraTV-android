package com.ammiratv.domain.repository

import com.ammiratv.core.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun getProfiles(): Flow<List<Profile>>
    suspend fun getProfile(id: Long): Profile?
    suspend fun addProfile(profile: Profile): Long
    suspend fun updateProfile(profile: Profile)
    suspend fun deleteProfile(id: Long)

    /** Creates the default "Principale" profile (id 1) on first run, so favourites always have an owner. */
    suspend fun ensureDefaultProfile()
}
