package com.ammiratv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ammiratv.core.datastore.AmmiraTvPreferences
import com.ammiratv.core.model.Profile
import com.ammiratv.domain.usecase.GetProfilesUseCase
import com.ammiratv.domain.usecase.ManageProfilesUseCase
import com.ammiratv.domain.usecase.ParentalGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    getProfilesUseCase: GetProfilesUseCase,
    private val manageProfiles: ManageProfilesUseCase,
    private val parentalGuard: ParentalGuard,
    preferences: AmmiraTvPreferences
) : ViewModel() {

    val profiles: StateFlow<List<Profile>> = getProfilesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileId: StateFlow<Long> = preferences.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1L)

    val hasPin: StateFlow<Boolean> = parentalGuard.hasPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun select(profileId: Long) {
        viewModelScope.launch { manageProfiles.select(profileId) }
    }

    fun add(name: String, isChild: Boolean, dailyLimitMinutes: Int?) {
        viewModelScope.launch { manageProfiles.add(name, isChild, dailyLimitMinutes) }
    }

    fun delete(profileId: Long) {
        viewModelScope.launch {
            val all = profiles.value
            val wasActive = activeProfileId.value == profileId
            if (manageProfiles.delete(profileId, all) && wasActive) {
                all.firstOrNull { it.id != profileId }?.let { manageProfiles.select(it.id) }
            }
        }
    }

    fun verifyPin(pin: String): Boolean = parentalGuard.verify(pin)

    fun setPin(pin: String) {
        viewModelScope.launch { parentalGuard.setPin(pin) }
    }

    fun clearPin() {
        viewModelScope.launch { parentalGuard.clearPin() }
    }
}
