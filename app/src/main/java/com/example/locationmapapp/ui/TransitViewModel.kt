/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.model.MbtaPrediction
import com.example.locationmapapp.data.model.MbtaStop
import com.example.locationmapapp.data.model.MbtaTripScheduleEntry
import com.example.locationmapapp.data.model.MbtaVehicle
import com.example.locationmapapp.data.repository.MbtaRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TransitViewModel.kt"

@HiltViewModel
class TransitViewModel @Inject constructor(
    private val mbtaRepository: MbtaRepository
) : ViewModel() {

    private val TAG = "TransitVM"

    private val _mbtaTrains = MutableLiveData<List<MbtaVehicle>>()
    val mbtaTrains: LiveData<List<MbtaVehicle>> = _mbtaTrains

    private val _mbtaSubway = MutableLiveData<List<MbtaVehicle>>()
    val mbtaSubway: LiveData<List<MbtaVehicle>> = _mbtaSubway

    private val _mbtaBuses = MutableLiveData<List<MbtaVehicle>>()
    val mbtaBuses: LiveData<List<MbtaVehicle>> = _mbtaBuses

    private val _mbtaStations = MutableLiveData<List<MbtaStop>>()
    val mbtaStations: LiveData<List<MbtaStop>> = _mbtaStations

    private val _mbtaBusStops = MutableLiveData<List<MbtaStop>>()
    val mbtaBusStops: LiveData<List<MbtaStop>> = _mbtaBusStops

    fun fetchMbtaTrains() {
        DebugLogger.i(TAG, "fetchMbtaTrains() — fetching commuter rail vehicles")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { mbtaRepository.fetchCommuterRailVehicles() } }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA trains success — ${it.size} vehicles")
                    _mbtaTrains.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA trains FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun clearMbtaTrains() {
        _mbtaTrains.value = emptyList()
        DebugLogger.i(TAG, "MBTA trains cleared")
    }

    fun fetchMbtaSubway() {
        DebugLogger.i(TAG, "fetchMbtaSubway() — fetching subway vehicles")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { mbtaRepository.fetchSubwayVehicles() } }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA subway success — ${it.size} vehicles")
                    _mbtaSubway.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA subway FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun clearMbtaSubway() {
        _mbtaSubway.value = emptyList()
        DebugLogger.i(TAG, "MBTA subway cleared")
    }

    fun fetchMbtaBuses() {
        DebugLogger.i(TAG, "fetchMbtaBuses() — fetching bus vehicles")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { mbtaRepository.fetchBusVehicles() } }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA buses success — ${it.size} vehicles")
                    _mbtaBuses.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA buses FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun clearMbtaBuses() {
        _mbtaBuses.value = emptyList()
        DebugLogger.i(TAG, "MBTA buses cleared")
    }

    fun fetchMbtaStations() {
        DebugLogger.i(TAG, "fetchMbtaStations() — fetching subway + CR stations")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { mbtaRepository.fetchStations() } }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA stations success — ${it.size} stations")
                    _mbtaStations.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA stations FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun clearMbtaStations() {
        _mbtaStations.value = emptyList()
        DebugLogger.i(TAG, "MBTA stations cleared")
    }

    fun fetchMbtaBusStops() {
        DebugLogger.i(TAG, "fetchMbtaBusStops() — fetching all bus stops")
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { mbtaRepository.fetchBusStops() } }
                .onSuccess {
                    DebugLogger.i(TAG, "MBTA bus stops success — ${it.size} stops")
                    _mbtaBusStops.value = it
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "MBTA bus stops FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun clearMbtaBusStops() {
        _mbtaBusStops.value = emptyList()
        DebugLogger.i(TAG, "MBTA bus stops cleared")
    }

    /** Suspend call — returns predictions directly for dialogs. */
    suspend fun fetchPredictionsDirectly(stopId: String): List<MbtaPrediction> {
        return try {
            mbtaRepository.fetchPredictions(stopId)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchPredictionsDirectly FAILED: ${e.message}", e)
            emptyList()
        }
    }

    /** Suspend call — returns trip schedule directly for dialogs. */
    suspend fun fetchTripScheduleDirectly(tripId: String): List<MbtaTripScheduleEntry> {
        return try {
            mbtaRepository.fetchTripSchedule(tripId)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchTripScheduleDirectly FAILED: ${e.message}", e)
            emptyList()
        }
    }
}
