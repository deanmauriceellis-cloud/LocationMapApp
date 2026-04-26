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
import com.example.wickedsalemwitchcitytour.routing.DirectionsSession
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

    fun onLocationUpdate(point: GeoPoint) {
        tourEngine.onLocationUpdate(point)
        handleActiveDirectionsLocation(point)
    }

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

    /** S146 #27 — replay a NarrationHistory entry (used by the hero-banner tap). */
    fun replayNarrationHistory(entry: com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Entry) =
        narrationManager.replayHistoryEntry(entry)

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

    /**
     * Active directions session (S176 P3b) — tracks drift off the polyline
     * and arrival at the destination. Null whenever no point-to-point route
     * is shown (multi-stop tour previews don't get a session). Replaced
     * wholesale on reroute.
     */
    private var directionsSession: DirectionsSession? = null

    /** Mutex flag — true while a reroute is in flight. Drift is muted in this window. */
    private var isRerouting: Boolean = false

    /** Get walking directions from user's current location to a POI. */
    fun getDirectionsTo(destination: GeoPoint) {
        val from = tourEngine.lastLocation ?: return
        viewModelScope.launch { computeAndApplyRoute(from, destination) }
    }

    /** Get walking directions to the current tour stop. */
    fun getDirectionsToCurrentStop() {
        val stopLocation = tourEngine.getCurrentPoi()?.let { GeoPoint(it.lat, it.lng) } ?: return
        getDirectionsTo(stopLocation)
    }

    /** Get multi-stop route preview for the entire active tour. */
    fun getFullTourRoute() {
        val tourId = (tourEngine.tourState.value as? TourState.Active)?.activeTour?.tour?.id
        val points = tourEngine.getAllStopLocations()
        if (points.size < 2) return
        viewModelScope.launch {
            // Prefer the pre-computed bundled polyline for the active tour —
            // straight-line fallback if the asset is missing or no active tour.
            val bundled = tourId?.let { walkingDirections.getBundledTourRoute(it) }
            _walkingRoute.value = bundled ?: walkingDirections.getMultiStopRoute(points)
            // Multi-stop preview has no single destination → no live session.
            directionsSession = null
        }
    }

    /** Clear the walking route display and any active directions session. */
    fun clearDirections() {
        _walkingRoute.value = null
        directionsSession = null
        isRerouting = false
    }

    /**
     * S176 P3b — feed each GPS fix into the active directions session and
     * react to drift / arrival. Multi-stop previews and tour-wide overlays
     * skip this path because they leave [directionsSession] null.
     */
    private fun handleActiveDirectionsLocation(point: GeoPoint) {
        if (isRerouting) return
        val session = directionsSession ?: return
        when (val action = session.onLocation(point)) {
            DirectionsSession.SessionAction.OnPath -> Unit
            DirectionsSession.SessionAction.Arrived -> {
                DebugLogger.i(TAG, "Directions: arrived at destination, clearing route")
                clearDirections()
            }
            is DirectionsSession.SessionAction.Reroute -> {
                DebugLogger.i(
                    TAG,
                    "Directions: drift %.1fm > threshold for ${DirectionsSession.DRIFT_FIXES} fixes — recomputing".format(action.driftM)
                )
                val dest = session.destination
                isRerouting = true
                viewModelScope.launch {
                    try {
                        computeAndApplyRoute(action.from, dest)
                    } finally {
                        isRerouting = false
                    }
                }
            }
        }
    }

    /**
     * Compute a route, emit it on [_walkingRoute], and replace
     * [directionsSession]. Returns true on success.
     */
    private suspend fun computeAndApplyRoute(from: GeoPoint, destination: GeoPoint): Boolean {
        val route = walkingDirections.getRoute(from, destination)
        _walkingRoute.value = route
        directionsSession = if (route != null && route.polyline.isNotEmpty()) {
            DirectionsSession(destination, route.polyline)
        } else {
            null
        }
        return route != null
    }

    /**
     * S176 P3c — compute a walk route between two arbitrary points. Returns
     * the polyline (ArrayList<GeoPoint>) or null if no route can be built.
     * Does **not** publish the route on [_walkingRoute] or arm a session —
     * that's the caller's job via [publishWalkRoute] once they're ready to
     * commit. This split lets the activity decide whether to display + walk
     * or just inspect.
     */
    suspend fun computeWalkRoute(from: GeoPoint, to: GeoPoint): java.util.ArrayList<GeoPoint>? {
        val route = walkingDirections.getRoute(from, to) ?: return null
        return if (route.polyline.isEmpty()) null else route.polyline
    }

    /**
     * S176 P3c — display a polyline as the active walking route AND arm a
     * P3b directions session against it. Used by the out-of-Salem simulated
     * walk path so the same arrival/drift logic that drives real-GPS routes
     * also applies to simulated ones.
     */
    fun publishWalkRoute(destination: GeoPoint, polyline: java.util.ArrayList<GeoPoint>) {
        if (polyline.isEmpty()) {
            clearDirections()
            return
        }
        // Build a minimal WalkingRoute so observeWalkingRoute → drawWalkingRoute fires.
        val route = WalkingRoute(
            polyline = polyline,
            distanceKm = polylineLengthM(polyline) / 1000.0,
            durationMinutes = (polylineLengthM(polyline) / 1.4 / 60.0).toInt(),
            instructions = emptyList(),
            road = null,
        )
        _walkingRoute.value = route
        directionsSession = DirectionsSession(destination, polyline)
        isRerouting = false
    }

    /** Sum of consecutive haversine segment lengths in meters. */
    private fun polylineLengthM(poly: List<GeoPoint>): Double {
        if (poly.size < 2) return 0.0
        val R = 6_371_000.0
        var total = 0.0
        for (i in 1 until poly.size) {
            val a = poly[i - 1]; val b = poly[i]
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val h = kotlin.math.sin(dLat / 2).let { it * it } +
                    kotlin.math.cos(Math.toRadians(a.latitude)) * kotlin.math.cos(Math.toRadians(b.latitude)) *
                    kotlin.math.sin(dLon / 2).let { it * it }
            total += R * 2 * kotlin.math.atan2(kotlin.math.sqrt(h), kotlin.math.sqrt(1 - h))
        }
        return total
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

    // ── Phase 9U: Unified POIs ─────────────────────────────────────────

    /** Load all narrated POIs from the unified salem_pois table */
    suspend fun loadNarrationPoints(): List<com.example.wickedsalemwitchcitytour.content.model.SalemPoi> {
        return repository.getNarratedPois()
    }

    /** Load narrated POIs within a bounding box (viewport-filtered) */
    suspend fun loadNarrationPointsInBbox(
        latMin: Double, latMax: Double, lngMin: Double, lngMax: Double
    ): List<com.example.wickedsalemwitchcitytour.content.model.SalemPoi> {
        return repository.getNarratedPoisInBbox(latMin, latMax, lngMin, lngMax)
    }

    /** Speak arbitrary text via the narration TTS engine */
    fun speakNarration(text: String, label: String, voiceId: String? = null) {
        narrationManager.speakHint(text, label, voiceId)
    }

    /**
     * Short orientation / hint utterance (e.g. "You are at Derby House.").
     * Tagged for later cancellation via [cancelSegmentsWithTag]. Emitted as
     * `SegmentType.HINT`.
     */
    fun speakTaggedHint(tag: String, text: String, label: String, voiceId: String? = null, category: String? = null) {
        narrationManager.speakTaggedHint(tag, text, label, voiceId, category)
    }

    /**
     * Full-body narration (historical content). Tagged for later cancellation
     * via [cancelSegmentsWithTag]. Emitted as `SegmentType.LONG_NARRATION` so
     * the history panel / nav cluster can distinguish body from hint.
     *
     * S150: previously delegated to [speakTaggedHint], which caused every
     * narration segment to be tagged HINT. Now routes to the real narration
     * path so kind inference, replay classification, and any future
     * body-vs-hint UI gating work as intended.
     */
    fun speakTaggedNarration(tag: String, text: String, label: String, voiceId: String? = null, category: String? = null) {
        narrationManager.speakTaggedNarration(tag, text, label, voiceId, category)
    }

    /** Cancel any queued or currently-playing segment whose id starts with [tag]. */
    fun cancelSegmentsWithTag(tag: String) {
        narrationManager.cancelSegmentsWithTag(tag)
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

    // ── S145 Must-Have #45 — nav cluster wrappers ──────────────────────
    /** Replay the oldest entry in the rolling narration history. */
    fun navFirst(): com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Entry? {
        val e = com.example.wickedsalemwitchcitytour.audio.NarrationHistory.first() ?: return null
        narrationManager.replayHistoryEntry(e); return e
    }
    /** Step back one entry in the rolling narration history and replay it. */
    fun navPrev(): com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Entry? {
        val e = com.example.wickedsalemwitchcitytour.audio.NarrationHistory.prev() ?: return null
        narrationManager.replayHistoryEntry(e); return e
    }
    /** Skip to the next entry (if any) and replay it. If none, skip the current queue item. */
    fun navNext(): com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Entry? {
        val e = com.example.wickedsalemwitchcitytour.audio.NarrationHistory.next()
        if (e != null) {
            narrationManager.replayHistoryEntry(e)
        } else {
            narrationManager.skip()
        }
        return e
    }
    /** Pause/resume the current TTS utterance. */
    fun navPauseToggle() {
        val state = narrationManager.state.value
        if (state is com.example.wickedsalemwitchcitytour.tour.NarrationState.Paused) {
            narrationManager.resume()
        } else {
            narrationManager.pause()
        }
    }
    /** The entry currently selected by the nav cluster (for Jump routing). */
    fun currentNavEntry(): com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Entry? =
        com.example.wickedsalemwitchcitytour.audio.NarrationHistory.current()

    override fun onCleared() {
        super.onCleared()
        narrationManager.shutdown()
    }
}
