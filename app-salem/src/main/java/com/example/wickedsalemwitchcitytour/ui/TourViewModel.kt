/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.SalemContentRepository
import com.example.wickedsalemwitchcitytour.content.model.Tour
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.tour.NarrationManager
import com.example.wickedsalemwitchcitytour.tour.NarrationState
import com.example.wickedsalemwitchcitytour.tour.TourEngine
import com.example.wickedsalemwitchcitytour.tour.TourGeofenceManager
import com.example.wickedsalemwitchcitytour.tour.TourGeofenceEvent
import com.example.wickedsalemwitchcitytour.tour.TourState
import com.example.wickedsalemwitchcitytour.tour.WalkingDirections
import com.example.wickedsalemwitchcitytour.tour.WalkingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TourViewModel.kt"

/**
 * ViewModel bridging TourEngine to the SalemMainActivity UI.
 * Exposes tour list, selection state, active tour state, geofence events, and narration.
 */
@HiltViewModel
class TourViewModel @Inject constructor(
    private val tourEngine: TourEngine,
    private val repository: SalemContentRepository,
    private val geofenceManager: TourGeofenceManager,
    private val narrationManager: NarrationManager,
    private val walkingDirections: WalkingDirections
) : ViewModel() {

    private val TAG = "TourVM"

    /** All available tours, loaded once. */
    private val _availableTours = MutableStateFlow<List<Tour>>(emptyList())
    val availableTours: StateFlow<List<Tour>> = _availableTours.asStateFlow()

    /** All tour POIs (for browsing / custom tour builder). */
    private val _allPois = MutableStateFlow<List<TourPoi>>(emptyList())
    val allPois: StateFlow<List<TourPoi>> = _allPois.asStateFlow()

    /** Tour engine state — observe for active tour HUD. */
    val tourState: StateFlow<TourState> = tourEngine.tourState

    /** Geofence events — observe for approach/entry/exit notifications. */
    val geofenceEvents: SharedFlow<TourGeofenceEvent> = geofenceManager.events

    /** Narration state — observe for playback controls. */
    val narrationState: StateFlow<NarrationState> = narrationManager.state

    init {
        loadTours()
        restoreSavedTour()
        narrationManager.initialize()
        loadAmbientPois()
    }

    private fun loadTours() {
        viewModelScope.launch {
            try {
                _availableTours.value = repository.getAllTours()
                _allPois.value = repository.getAllTourPois()
                DebugLogger.i(TAG, "Loaded ${_availableTours.value.size} tours, ${_allPois.value.size} POIs")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to load tours: ${e.message}", e)
            }
        }
    }

    private fun restoreSavedTour() {
        viewModelScope.launch {
            if (tourEngine.restoreIfSaved()) {
                DebugLogger.i(TAG, "Restored saved tour from SharedPreferences")
            }
        }
    }

    // ── Tour actions (delegate to engine) ────────────────────────────────

    fun startTour(tourId: String) {
        viewModelScope.launch { tourEngine.startTour(tourId) }
    }

    fun advanceToNextStop() = tourEngine.advanceToNextStop()

    fun skipStop() = tourEngine.skipStop()

    fun pauseTour() = tourEngine.pauseTour()

    fun resumeTour() = tourEngine.resumeTour()

    fun endTour() = tourEngine.endTour()

    fun dismissCompletion() = tourEngine.dismissCompletion()

    fun addStop(poiId: String) {
        viewModelScope.launch { tourEngine.addStop(poiId) }
    }

    fun removeStop(poiId: String) = tourEngine.removeStop(poiId)

    fun onLocationUpdate(point: GeoPoint) = tourEngine.onLocationUpdate(point)

    // ── Custom tour builder ──────────────────────────────────────────────

    fun startCustomTour(selectedPois: List<TourPoi>, name: String = "Custom Tour") {
        tourEngine.startCustomTour(selectedPois, name)
    }

    fun buildTimeBudgetTour(budgetMinutes: Int, startPoint: GeoPoint? = null): List<TourPoi> {
        return tourEngine.buildTimeBudgetTour(budgetMinutes, _allPois.value, startPoint)
    }

    fun optimizeRoute(pois: List<TourPoi>): List<TourPoi> = tourEngine.optimizeRoute(pois)

    fun totalRouteDistanceKm(pois: List<TourPoi>): Float = tourEngine.totalRouteDistanceKm(pois)

    fun estimateWalkingMinutes(distanceKm: Float): Int = tourEngine.estimateWalkingMinutes(distanceKm)

    fun estimateTourMinutes(pois: List<TourPoi>): Int {
        val distKm = tourEngine.totalRouteDistanceKm(pois)
        return tourEngine.estimateWalkingMinutes(distKm) + (pois.size * 5) // +5 min per stop
    }

    // ── Query helpers ────────────────────────────────────────────────────

    fun getAllStopLocations(): List<GeoPoint> = tourEngine.getAllStopLocations()

    fun getStopLocation(index: Int): GeoPoint? = tourEngine.getStopLocation(index)

    fun distanceToStop(index: Int): Double? = tourEngine.distanceToStop(index)

    fun isAtCurrentStop(): Boolean = tourEngine.isAtCurrentStop()

    fun getCurrentPoi(): TourPoi? = tourEngine.getCurrentPoi()

    // ── Narration controls ──────────────────────────────────────────────

    fun pauseNarration() = narrationManager.pause()
    fun resumeNarration() = narrationManager.resume()
    fun stopNarration() = narrationManager.stop()
    fun skipNarration() = narrationManager.skip()
    fun cycleNarrationSpeed(): Float = narrationManager.cycleSpeed()

    fun isNarrating(): Boolean = narrationManager.isSpeaking()

    var autoNarrationEnabled: Boolean
        get() = narrationManager.autoNarrationEnabled
        set(value) { narrationManager.autoNarrationEnabled = value }

    /** Manually trigger narration for the current stop. */
    fun narrateCurrentStop() {
        val poi = tourEngine.getCurrentPoi() ?: return
        narrationManager.speakLongNarration(poi)
    }

    /** Speak a specific POI's narration on demand. */
    fun narratePoi(poi: TourPoi) {
        narrationManager.speakLongNarration(poi)
    }

    /** Ambient mode: speak a hint about a nearby non-tour POI. */
    fun speakAmbientHint(poi: TourPoi) {
        val hint = poi.shortNarration ?: "You're near ${poi.name}."
        narrationManager.speakHint(hint, poi.name)
    }

    // ── Walking directions ────────────────────────────────────────────────

    /** Active walking route (null = no directions shown). */
    private val _walkingRoute = MutableStateFlow<WalkingRoute?>(null)
    val walkingRoute: StateFlow<WalkingRoute?> = _walkingRoute.asStateFlow()

    /** Get walking directions from user's current location to a POI. */
    fun getDirectionsTo(destination: GeoPoint) {
        val from = tourEngine.lastLocation ?: return
        viewModelScope.launch {
            _walkingRoute.value = walkingDirections.getRoute(from, destination)
        }
    }

    /** Get walking directions to the current tour stop. */
    fun getDirectionsToCurrentStop() {
        val stopLocation = tourEngine.getCurrentPoi()?.let { GeoPoint(it.lat, it.lng) } ?: return
        getDirectionsTo(stopLocation)
    }

    /** Get multi-stop route preview for the entire active tour. */
    fun getFullTourRoute() {
        val points = tourEngine.getAllStopLocations()
        if (points.size < 2) return
        viewModelScope.launch {
            _walkingRoute.value = walkingDirections.getMultiStopRoute(points)
        }
    }

    /** Clear the walking route display. */
    fun clearDirections() {
        _walkingRoute.value = null
    }

    // ── Ambient mode ─────────────────────────────────────────────────────

    var ambientModeEnabled: Boolean
        get() = tourEngine.ambientModeEnabled
        set(value) { tourEngine.ambientModeEnabled = value }

    private fun loadAmbientPois() {
        viewModelScope.launch {
            tourEngine.loadAmbientPois()
        }
    }

    // ── Phase 9T: Narration points ─────────────────────────────────────

    /** Load all narration points from the database */
    suspend fun loadNarrationPoints(): List<com.example.wickedsalemwitchcitytour.content.model.NarrationPoint> {
        return repository.getAllNarrationPoints()
    }

    /** S118: Load narration points within a bounding box (viewport-filtered) */
    suspend fun loadNarrationPointsInBbox(
        latMin: Double, latMax: Double, lngMin: Double, lngMax: Double
    ): List<com.example.wickedsalemwitchcitytour.content.model.NarrationPoint> {
        return repository.getNarrationPointsInBbox(latMin, latMax, lngMin, lngMax)
    }

    /** Speak arbitrary text via the narration TTS engine */
    fun speakNarration(text: String, label: String, voiceId: String? = null) {
        narrationManager.speakHint(text, label, voiceId)
    }

    /**
     * S113 — Enqueue a POI-detail-sheet section for TTS read-through, tagged
     * so the sheet can cancel only its own segments on user click or dismiss.
     * The tag is typically `"sheet_${poi.id}"`.
     */
    fun speakSheetSection(tag: String, text: String, label: String, voiceId: String? = null) {
        narrationManager.speakTaggedHint(tag, text, label, voiceId)
    }

    /** S113 — Cancel every queued sheet-read segment matching this tag. */
    fun cancelSheetReading(tag: String) {
        narrationManager.cancelSegmentsWithTag(tag)
    }

    override fun onCleared() {
        super.onCleared()
        narrationManager.shutdown()
    }
}
