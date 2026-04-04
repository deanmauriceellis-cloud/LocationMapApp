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
import com.example.locationmapapp.data.model.MetarStation
import com.example.locationmapapp.data.model.Webcam
import com.example.locationmapapp.data.model.WeatherAlert
import com.example.locationmapapp.data.model.WeatherData
import com.example.locationmapapp.data.repository.WebcamRepository
import com.example.locationmapapp.data.repository.WeatherRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module WeatherViewModel.kt"

@HiltViewModel
class WeatherViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val webcamRepository: WebcamRepository
) : ViewModel() {

    private val TAG = "WeatherVM"

    private val _weatherAlerts = MutableLiveData<List<WeatherAlert>>()
    val weatherAlerts: LiveData<List<WeatherAlert>> = _weatherAlerts

    private val _metars = MutableLiveData<List<MetarStation>>()
    val metars: LiveData<List<MetarStation>> = _metars

    private val _radarRefreshTick = MutableLiveData<Long>()
    val radarRefreshTick: LiveData<Long> = _radarRefreshTick

    private val _weatherData = MutableLiveData<WeatherData?>()
    val weatherData: LiveData<WeatherData?> = _weatherData

    private val _webcams = MutableLiveData<List<Webcam>>()
    val webcams: LiveData<List<Webcam>> = _webcams

    fun fetchWeatherAlerts() {
        DebugLogger.i(TAG, "fetchWeatherAlerts()")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchAlerts() }
                .onSuccess { DebugLogger.i(TAG, "Alerts success — ${it.size}"); _weatherAlerts.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Alerts FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun fetchWeather(lat: Double, lon: Double) {
        DebugLogger.i(TAG, "fetchWeather() lat=$lat lon=$lon")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchWeather(lat, lon) }
                .onSuccess { DebugLogger.i(TAG, "Weather success — ${it.location.city},${it.location.state}"); _weatherData.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Weather FAILED: ${e.message}", e as? Exception) }
        }
    }

    /** Suspend call — returns weather data directly for dialog. */
    suspend fun fetchWeatherDirectly(lat: Double, lon: Double): WeatherData? {
        return try {
            weatherRepository.fetchWeather(lat, lon)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchWeatherDirectly FAILED: ${e.message}", e)
            null
        }
    }

    fun loadMetars(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadMetars() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { weatherRepository.fetchMetars(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "METAR success — ${it.size}"); _metars.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "METAR FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun loadWebcams(south: Double, west: Double, north: Double, east: Double, categories: String) {
        DebugLogger.i(TAG, "loadWebcams() bbox=$south,$west,$north,$east categories=$categories")
        viewModelScope.launch {
            runCatching { webcamRepository.fetchWebcams(south, west, north, east, categories) }
                .onSuccess { DebugLogger.i(TAG, "Webcams success — ${it.size}"); _webcams.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "Webcams FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun clearWebcams() {
        _webcams.value = emptyList()
        DebugLogger.i(TAG, "Webcams cleared")
    }

    fun refreshRadar() {
        _radarRefreshTick.value = System.currentTimeMillis()
        DebugLogger.i(TAG, "Radar refresh tick")
    }
}
