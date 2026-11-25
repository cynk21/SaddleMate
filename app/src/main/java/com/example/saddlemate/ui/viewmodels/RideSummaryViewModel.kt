package com.example.saddlemate.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.saddlemate.data.local.RideEntity
import com.example.saddlemate.data.repository.RideRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RideSummaryUiState(
    val ride: RideEntity? = null,
    val warnings: List<String> = emptyList(),
)

@HiltViewModel
class RideSummaryViewModel @Inject constructor(
    private val repository: RideRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rideId: String = checkNotNull(savedStateHandle["rideId"])

    private val _state = MutableStateFlow(RideSummaryUiState())
    val state: StateFlow<RideSummaryUiState> = _state.asStateFlow()

    suspend fun load() {
        val ride = repository.ride(rideId)
        _state.value = RideSummaryUiState(
            ride = ride,
            warnings = ride?.warnings?.lines()?.filter { it.isNotBlank() } ?: emptyList(),
        )
    }
}
