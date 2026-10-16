package com.ammiratv.domain.usecase

import com.ammiratv.core.common.ApplicationScope
import com.ammiratv.core.common.PinHasher
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Profile
import com.ammiratv.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/** The profile currently in use; falls back to the first one if the stored id no longer exists. */
class ObserveActiveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferences: AmmiraTvPreferences
) {
    operator fun invoke(): Flow<Profile?> =
        combine(profileRepository.getProfiles(), preferences.activeProfileId) { profiles, activeId ->
            profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
        }
}

class GetProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    operator fun invoke(): Flow<List<Profile>> = profileRepository.getProfiles()
}

class ManageProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val preferences: AmmiraTvPreferences
) {
    suspend fun ensureDefault() = profileRepository.ensureDefaultProfile()

    suspend fun add(name: String, isChild: Boolean, dailyLimitMinutes: Int?): Long =
        profileRepository.addProfile(
            Profile(name = name.trim().ifEmpty { "Profilo" }, isChild = isChild, dailyLimitMinutes = dailyLimitMinutes)
        )

    suspend fun select(profileId: Long) = preferences.setActiveProfileId(profileId)

    /** The last profile can't be deleted (favourites need an owner). Returns false in that case. */
    suspend fun delete(profileId: Long, remaining: List<Profile>): Boolean {
        val others = remaining.filter { it.id != profileId }
        if (others.isEmpty()) return false
        profileRepository.deleteProfile(profileId)
        return true
    }
}

/**
 * Global parental PIN. The PIN is hashed; the unlocked state is not persisted
 * (leaving a child profile always asks again).
 */
@Singleton
class ParentalGuard @Inject constructor(
    private val preferences: AmmiraTvPreferences,
    @ApplicationScope scope: CoroutineScope
) {
    private val pinHash: StateFlow<String> = preferences.parentalPin
        .stateIn(scope, SharingStarted.Eagerly, "")

    val hasPin: Flow<Boolean> = pinHash.map { it.isNotEmpty() }

    fun hasPinNow(): Boolean = pinHash.value.isNotEmpty()

    fun verify(pin: String): Boolean = PinHasher.matches(pin, pinHash.value)

    suspend fun setPin(pin: String) = preferences.setParentalPin(PinHasher.hash(pin))

    suspend fun clearPin() = preferences.setParentalPin("")
}
