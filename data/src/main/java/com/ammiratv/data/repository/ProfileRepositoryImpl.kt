package com.ammiratv.data.repository

import com.ammiratv.core.database.dao.ProfileDao
import com.ammiratv.core.database.entity.ProfileEntity
import com.ammiratv.core.model.Profile
import com.ammiratv.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun getProfiles(): Flow<List<Profile>> =
        profileDao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProfile(id: Long): Profile? = profileDao.getById(id)?.toDomain()

    override suspend fun addProfile(profile: Profile): Long = profileDao.insert(profile.toEntity())

    override suspend fun updateProfile(profile: Profile) = profileDao.update(profile.toEntity())

    override suspend fun deleteProfile(id: Long) = profileDao.deleteById(id)

    override suspend fun ensureDefaultProfile() {
        if (profileDao.count() == 0) {
            profileDao.insert(ProfileEntity(id = DEFAULT_PROFILE_ID, name = "Principale"))
        }
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = 1L
    }
}

private fun ProfileEntity.toDomain() = Profile(
    id = id,
    name = name,
    avatarUrl = avatarUrl,
    isChild = isChild,
    pin = pin,
    dailyLimitMinutes = dailyLimitMinutes
)

private fun Profile.toEntity() = ProfileEntity(
    id = id,
    name = name,
    avatarUrl = avatarUrl,
    isChild = isChild,
    pin = pin,
    dailyLimitMinutes = dailyLimitMinutes
)
