/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.model.AircraftState
import com.example.locationmapapp.data.model.FlightPathPoint
import com.example.locationmapapp.data.repository.AircraftRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module AircraftViewModel.kt"

@HiltViewModel
class AircraftViewModel @Inject constructor(
    private val aircraftRepository: AircraftRepository
) : ViewModel() {

    private val TAG = "AircraftVM"

    private val _aircraft = MutableLiveData<List<AircraftState>>()
    val aircraft: LiveData<List<AircraftState>> = _aircraft

    private val _followedAircraft = MutableLiveData<AircraftState?>()
    val followedAircraft: LiveData<AircraftState?> = _followedAircraft

    fun loadAircraft(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadAircraft() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { aircraftRepository.fetchAircraft(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Aircraft success — ${it.size}"); _aircraft.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Aircraft FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun loadFollowedAircraft(icao24: String) {
        DebugLogger.i(TAG, "loadFollowedAircraft() icao24=$icao24")
        viewModelScope.launch {
            runCatching { aircraftRepository.fetchAircraftByIcao(icao24) }
                .onSuccess { state ->
                    DebugLogger.i(TAG, "Followed aircraft ${if (state != null) "found at ${state.lat},${state.lon}" else "NOT found"}")
                    _followedAircraft.value = state
                }
                .onFailure { e -> DebugLogger.e(TAG, "Followed aircraft FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun clearFollowedAircraft() {
        _followedAircraft.value = null
    }

    /** Suspend call — returns flight history path points directly for trail drawing. */
    suspend fun fetchFlightHistoryDirectly(icao24: String): List<FlightPathPoint> {
        return try {
            aircraftRepository.fetchFlightHistory(icao24)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchFlightHistoryDirectly FAILED: ${e.message}", e)
            emptyList()
        }
    }

    fun clearAircraft() {
        _aircraft.value = emptyList()
        DebugLogger.i(TAG, "Aircraft cleared")
    }
}
