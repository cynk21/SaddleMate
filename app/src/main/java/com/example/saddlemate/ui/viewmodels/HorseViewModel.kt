package com.example.saddlemate.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saddlemate.data.repository.RideRepository
import com.example.saddlemate.domain.Horse
import com.example.saddlemate.domain.RiderProfile
import com.example.saddlemate.store.RideSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HorseUiState(
    val horses: List<Horse> = emptyList(),
    val activeHorseId: String? = null,
    val riderProfile: RiderProfile = RiderProfile(),
    val emergencyContact: Pair<String, String> = Pair("", ""),
)

@HiltViewModel
class HorseViewModel @Inject constructor(
    private val repository: RideRepository,
    private val settings: RideSettings,
) : ViewModel() {

    val uiState: StateFlow<HorseUiState> = combine(
        repository.observeHorses(),
        settings.activeHorseId,
        settings.riderProfile,
        settings.emergencyContact,
    ) { horses, active, rider, sos -> HorseUiState(horses, active, rider, sos) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HorseUiState())

    fun select(id: String) = settings.selectHorse(id)

    fun saveRiderProfile(profile: RiderProfile) = settings.saveRiderProfile(profile)

    fun saveEmergencyContact(name: String, phone: String) = settings.saveEmergencyContact(name, phone)

    /** Saves an edited horse. Name and age must be valid; the caller checks that. */
    fun save(horse: Horse) {
        viewModelScope.launch { repository.saveHorse(horse) }
    }

    /** Deletes a horse with its rides. Resets the active selection if it was this horse. */
    fun delete(horseId: String) {
        viewModelScope.launch {
            repository.deleteHorse(horseId)
            if (settings.activeHorseId.value == horseId) settings.selectHorse(null)
        }
    }
}
