package com.example.saddlemate.ui.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saddlemate.data.local.RideEntity
import com.example.saddlemate.data.local.RideEventEntity
import com.example.saddlemate.data.repository.RideRepository
import com.example.saddlemate.domain.GeoPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** All stored rides, newest first. Used by the "Meine Ritte" list. */
@HiltViewModel
class MyRidesViewModel @Inject constructor(
    private val repository: RideRepository,
) : ViewModel() {
    val rides: Flow<List<RideEntity>> = repository.observeRides()

    /** Renames a ride and sets its note. */
    fun rename(rideId: String, name: String, note: String) {
        viewModelScope.launch { repository.renameRide(rideId, name, note) }
    }

    /** Deletes a ride with its track and events. */
    fun delete(rideId: String) {
        viewModelScope.launch { repository.deleteRide(rideId) }
    }
}

data class RideDetailState(
    val ride: RideEntity? = null,
    val track: List<GeoPoint> = emptyList(),
    val events: List<RideEventEntity> = emptyList(),
    val selectedEventId: Long? = null,
    /** Track index picked on the map; new events are placed here. */
    val pickedIndex: Int? = null,
)

/**
 * One stored ride: its track and events, with editing of events.
 * Events can be added at a track point, edited (type, label, note) and deleted.
 */
@HiltViewModel
class RideDetailViewModel @Inject constructor(
    private val repository: RideRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val rideId: String = checkNotNull(savedStateHandle["rideId"])

    private val _state = MutableStateFlow(RideDetailState())
    val state: StateFlow<RideDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.value = RideDetailState(
            ride = repository.ride(rideId),
            track = repository.track(rideId),
            events = repository.events(rideId),
        )
    }

    /** Adds a manual event at the track point with the given index. */
    fun addEventAt(trackIndex: Int, type: String, label: String, note: String) {
        val p = _state.value.track.getOrNull(trackIndex) ?: return
        viewModelScope.launch {
            repository.addEvent(
                RideEventEntity(
                    rideId = rideId,
                    lat = p.lat,
                    lon = p.lon,
                    timeMillis = p.timeMillis,
                    type = type,
                    label = label,
                    note = note,
                    source = "MANUAL",
                ),
            )
            load()
        }
    }

    /** Track index picked on the map for a new event, or null if none picked. */
    fun pickTrackIndex(index: Int?) {
        _state.value = _state.value.copy(pickedIndex = index)
    }

    fun selectEvent(id: Long?) {
        _state.value = _state.value.copy(selectedEventId = id)
    }

    fun updateEvent(event: RideEventEntity, type: String, label: String, note: String) {
        viewModelScope.launch {
            repository.updateEvent(event.copy(type = type, label = label, note = note))
            load()
        }
    }

    fun deleteEvent(event: RideEventEntity) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            _state.value = _state.value.copy(selectedEventId = null)
            load()
        }
    }

    /** Renames the ride and sets its note, then reloads it. */
    fun rename(name: String, note: String) {
        viewModelScope.launch {
            repository.renameRide(rideId, name, note)
            load()
        }
    }

    fun setVisibility(visibility: String) {
        viewModelScope.launch {
            repository.setVisibility(rideId, visibility)
            load()
        }
    }

    fun deleteRide(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRide(rideId)
            onDone()
        }
    }
}
