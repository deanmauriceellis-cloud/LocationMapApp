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
import com.example.locationmapapp.data.model.FindCounts
import com.example.locationmapapp.data.model.FindResponse
import com.example.locationmapapp.data.model.PoiWebsite
import com.example.locationmapapp.data.model.SearchResponse
import com.example.locationmapapp.data.repository.FindRepository
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module FindViewModel.kt"

@HiltViewModel
class FindViewModel @Inject constructor(
    private val findRepository: FindRepository
) : ViewModel() {

    private val TAG = "FindVM"

    private val _findCounts = MutableLiveData<FindCounts?>()
    val findCounts: LiveData<FindCounts?> = _findCounts

    fun loadFindCounts(lat: Double, lon: Double) {
        DebugLogger.i(TAG, "loadFindCounts() at $lat,$lon")
        viewModelScope.launch {
            runCatching { findRepository.fetchCounts(lat, lon) }
                .onSuccess { _findCounts.value = it }
                .onFailure { e -> DebugLogger.e(TAG, "FindCounts FAILED: ${e.message}", e as? Exception) }
        }
    }

    /** Direct suspend call for Find dialog — returns results without LiveData. */
    suspend fun findNearbyDirectly(lat: Double, lon: Double, categories: List<String>, limit: Int = 50, offset: Int = 0): FindResponse? {
        return try {
            findRepository.findNearby(lat, lon, categories, limit, offset)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "findNearbyDirectly FAILED: ${e.message}", e)
            null
        }
    }

    suspend fun searchPoisByName(query: String, lat: Double, lon: Double, limit: Int = 200): SearchResponse? {
        return try {
            findRepository.searchByName(query, lat, lon, limit)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "searchPoisByName FAILED: ${e.message}", e)
            null
        }
    }

    suspend fun fetchPoiWebsiteDirectly(osmType: String, osmId: Long, name: String?, lat: Double, lon: Double): PoiWebsite? {
        return try {
            findRepository.fetchWebsite(osmType, osmId, name, lat, lon)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchPoiWebsiteDirectly FAILED: ${e.message}", e)
            null
        }
    }

    /** Returns total POI count within [radiusM] of (lat, lon), or -1 on error. */
    suspend fun fetchNearbyPoiCount(lat: Double, lon: Double, radiusM: Int = 10000): Int {
        return try {
            val counts = findRepository.fetchCounts(lat, lon, radiusM)
            counts?.total ?: -1
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchNearbyPoiCount FAILED: ${e.message}", e)
            -1
        }
    }
}
