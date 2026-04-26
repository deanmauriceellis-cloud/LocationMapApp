/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import android.content.SharedPreferences
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.PoiContentPolicy
import com.example.wickedsalemwitchcitytour.content.SalemContentRepository
import com.example.wickedsalemwitchcitytour.content.model.Tour
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.content.model.TourStop
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TourEngine.kt"

/**
 * Tour Engine — manages the lifecycle of a guided walking tour.
 *
 * Responsibilities:
 *   - Load tour + stops + POIs from SalemContentRepository
 *   - Track progress (current stop, completed/skipped, distance)
 *   - Support advance, skip, reorder, add/remove stops
 *   - Pause/resume with SharedPreferences persistence
 *   - Emit TourState flow for UI observation
 */
@Singleton
class TourEngine @Inject constructor(
    private val repository: SalemContentRepository,
    private val geofenceManager: TourGeofenceManager,
    private val narrationManager: NarrationManager,
    private val narrationGeofenceManager: NarrationGeofenceManager,
    @ApplicationContext private val context: Context
) {
    private val TAG = "TourEngine"
    private val PREFS_NAME = "tour_engine_prefs"
    private val KEY_ACTIVE_TOUR_ID = "active_tour_id"
    private val KEY_CURRENT_STOP_INDEX = "current_stop_index"
    private val KEY_COMPLETED_STOPS = "completed_stops"
    private val KEY_SKIPPED_STOPS = "skipped_stops"
    private val KEY_START_TIME = "start_time"
    private val KEY_DISTANCE_WALKED = "distance_walked"
    private val KEY_ELAPSED_BEFORE_PAUSE = "elapsed_before_pause"
    private val KEY_CUSTOM_STOP_ORDER = "custom_stop_order"

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _tourState = MutableStateFlow<TourState>(TourState.Idle)
    val tourState: StateFlow<TourState> = _tourState.asStateFlow()

    /** Last known GPS location — used for distance calcs and route origins. */
    var lastLocation: GeoPoint? = null
        private set

    /** Ambient mode — passive narration when no tour is active. */
    var ambientModeEnabled: Boolean = false

    /** Distance threshold for ambient hints (meters). */
    private val AMBIENT_RADIUS_M = 100.0

    /** POI IDs that have already triggered ambient hints this session. */
    private val ambientHintedPois = mutableSetOf<String>()

    /** Cached all-POIs list for ambient mode. */
    private var allPoisCache: List<TourPoi>? = null

    // ── Tour Lifecycle ─────────────────────────────────────────────────────

    /**
     * Start a pre-defined tour by ID.
     * Loads tour metadata, stops (ordered), and full TourPoi objects.
     */
    suspend fun startTour(tourId: String) {
        DebugLogger.i(TAG, "startTour($tourId)")
        _tourState.value = TourState.Loading

        try {
            val tour = repository.getTourById(tourId)
            if (tour == null) {
                _tourState.value = TourState.Error("Tour not found: $tourId")
                return
            }

            // S185: a tour can be polyline-only — its stops table holds
            // internal authoring waypoints (poi_id NULL) that we deliberately
            // exclude from the asset DB. Such tours render their `tour_legs`
            // overlay; narration is driven by POI geofences independently.
            // Don't error on empty stops — let the player run as a
            // line-on-the-ground.
            val stops = repository.getTourStops(tourId)
            val pois = repository.getTourPois(tourId)

            val progress = TourProgress(
                tourId = tourId,
                currentStopIndex = 0,
                startTimeMs = System.currentTimeMillis()
            ).also { it.totalStopCount = stops.size }

            val activeTour = ActiveTour(tour, stops, pois, progress)
            _tourState.value = TourState.Active(activeTour)
            persistProgress(activeTour)
            geofenceManager.loadStops(stops, pois)

            // Phase 9R.0: auto-enable Historical Mode for heritage-trail themed
            // tours. The NarrationGeofenceManager then silences modern POIs and
            // plays `historical_note` (tour-guide voice) instead of the default
            // short_narration. Disables cleanly in endTour().
            if (tour.theme.equals("HERITAGE_TRAIL", ignoreCase = true)) {
                val allowedIds = stops.map { it.poiId }.toSet()
                narrationGeofenceManager.setHistoricalMode(true, allowedIds)
                DebugLogger.i(TAG, "Historical Mode ENABLED for '${tour.name}' — ${allowedIds.size} tour stops whitelisted")
            }

            DebugLogger.i(TAG, "Tour started: ${tour.name} — ${stops.size} stops")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "startTour failed: ${e.message}", e)
            _tourState.value = TourState.Error("Failed to start tour: ${e.message}")
        }
    }

    /** Advance to the next stop, marking the current one as completed. */
    fun advanceToNextStop() {
        val current = activeOrNull() ?: return
        val progress = current.progress
        val nextIndex = progress.currentStopIndex + 1

        if (nextIndex >= current.stops.size) {
            endTour()
            return
        }

        val distanceDelta = calcDistanceBetweenStops(current, progress.currentStopIndex, nextIndex)
        val newProgress = progress.copy(
            currentStopIndex = nextIndex,
            completedStops = progress.completedStops + progress.currentStopIndex,
            totalDistanceWalkedM = progress.totalDistanceWalkedM + distanceDelta
        ).also { it.totalStopCount = current.stops.size }

        val updated = current.copy(progress = newProgress)
        _tourState.value = TourState.Active(updated)
        persistProgress(updated)

        DebugLogger.i(TAG, "Advanced to stop $nextIndex/${current.stops.size}: ${updated.currentPoi?.name}")
    }

    /** Skip the current stop without marking it completed. */
    fun skipStop() {
        val current = activeOrNull() ?: return
        val progress = current.progress
        val nextIndex = progress.currentStopIndex + 1

        if (nextIndex >= current.stops.size) {
            endTour()
            return
        }

        val newProgress = progress.copy(
            currentStopIndex = nextIndex,
            skippedStops = progress.skippedStops + progress.currentStopIndex
        ).also { it.totalStopCount = current.stops.size }

        val updated = current.copy(progress = newProgress)
        _tourState.value = TourState.Active(updated)
        persistProgress(updated)

        DebugLogger.i(TAG, "Skipped stop ${progress.currentStopIndex}, now at $nextIndex: ${updated.currentPoi?.name}")
    }

    /** Reorder the remaining stops (does not affect completed/skipped). */
    fun reorderStops(newStopOrder: List<TourStop>) {
        val current = activeOrNull() ?: return

        // Keep completed/skipped stops in their original positions, replace the rest
        val updated = current.copy(stops = newStopOrder)
        _tourState.value = TourState.Active(updated)
        persistProgress(updated)
        persistCustomStopOrder(newStopOrder.map { it.poiId })

        DebugLogger.i(TAG, "Stops reordered — ${newStopOrder.size} stops")
    }

    /** Add a POI as a new stop after the current position. */
    suspend fun addStop(poiId: String) {
        val current = activeOrNull() ?: return
        val poi = repository.getTourPoiById(poiId) ?: run {
            DebugLogger.w(TAG, "addStop: POI not found: $poiId")
            return
        }

        // Create a synthetic TourStop
        val newStop = TourStop(
            tourId = current.tour.id,
            poiId = poiId,
            stopOrder = current.progress.currentStopIndex + 1,
            transitionNarration = "Next, we'll visit ${poi.name}.",
            walkingMinutesFromPrev = null,
            distanceMFromPrev = null
        )

        val mutableStops = current.stops.toMutableList()
        val mutablePois = current.pois.toMutableList()
        mutableStops.add(current.progress.currentStopIndex + 1, newStop)
        if (mutablePois.none { it.id == poiId }) mutablePois.add(poi)

        val newProgress = current.progress.also { it.totalStopCount = mutableStops.size }
        val updated = current.copy(stops = mutableStops, pois = mutablePois, progress = newProgress)
        _tourState.value = TourState.Active(updated)
        persistProgress(updated)

        DebugLogger.i(TAG, "Added stop: ${poi.name} at position ${current.progress.currentStopIndex + 1}")
    }

    /** Remove a POI from the tour (cannot remove the current stop). */
    fun removeStop(poiId: String) {
        val current = activeOrNull() ?: return
        val stopIndex = current.stops.indexOfFirst { it.poiId == poiId }
        if (stopIndex < 0 || stopIndex == current.progress.currentStopIndex) {
            DebugLogger.w(TAG, "removeStop: cannot remove current or missing stop: $poiId")
            return
        }

        val mutableStops = current.stops.toMutableList()
        mutableStops.removeAt(stopIndex)

        // Adjust currentStopIndex if we removed a stop before it
        val adjustedIndex = if (stopIndex < current.progress.currentStopIndex)
            current.progress.currentStopIndex - 1 else current.progress.currentStopIndex

        val newProgress = current.progress.copy(currentStopIndex = adjustedIndex)
            .also { it.totalStopCount = mutableStops.size }

        val updated = current.copy(stops = mutableStops, progress = newProgress)
        _tourState.value = TourState.Active(updated)
        persistProgress(updated)

        DebugLogger.i(TAG, "Removed stop: $poiId — ${mutableStops.size} stops remain")
    }

    /** Pause the tour. Preserves elapsed time so it doesn't count pause time. */
    fun pauseTour() {
        val current = activeOrNull() ?: return
        val elapsed = System.currentTimeMillis() - current.progress.startTimeMs + current.progress.elapsedMs
        val pausedProgress = current.progress.copy(elapsedMs = elapsed)
            .also { it.totalStopCount = current.stops.size }
        val updated = current.copy(progress = pausedProgress)
        _tourState.value = TourState.Paused(updated)
        persistProgress(updated)
        DebugLogger.i(TAG, "Tour paused: ${current.tour.name}")
    }

    /** Resume a paused tour. */
    fun resumeTour() {
        val state = _tourState.value
        if (state !is TourState.Paused) return

        val activeTour = state.activeTour
        val newProgress = activeTour.progress.copy(
            startTimeMs = System.currentTimeMillis()
        ).also { it.totalStopCount = activeTour.stops.size }

        val updated = activeTour.copy(progress = newProgress)
        _tourState.value = TourState.Active(updated)
        persistProgress(updated)
        DebugLogger.i(TAG, "Tour resumed: ${activeTour.tour.name}")
    }

    /** End the tour and emit completion summary. */
    fun endTour() {
        val current = activeOrPausedOrNull() ?: return
        val elapsed = when (val state = _tourState.value) {
            is TourState.Active -> System.currentTimeMillis() - current.progress.startTimeMs + current.progress.elapsedMs
            is TourState.Paused -> current.progress.elapsedMs
            else -> 0L
        }

        val summary = TourSummary(
            tourName = current.tour.name,
            totalStops = current.stops.size,
            completedStops = current.progress.completedStops.size,
            skippedStops = current.progress.skippedStops.size,
            totalTimeMs = elapsed,
            totalDistanceM = current.progress.totalDistanceWalkedM
        )

        _tourState.value = TourState.Completed(current, summary)
        clearPersistedProgress()
        geofenceManager.clear()
        narrationManager.stop()

        // Phase 9R.0: always clear Historical Mode when the tour ends, even if
        // the tour wasn't a HERITAGE_TRAIL one. Cheap idempotent reset.
        if (narrationGeofenceManager.isHistoricalMode()) {
            narrationGeofenceManager.setHistoricalMode(false)
            DebugLogger.i(TAG, "Historical Mode DISABLED (tour ended)")
        }

        DebugLogger.i(TAG, "Tour ended: ${summary.tourName} — " +
                "${summary.completedStops}/${summary.totalStops} stops, ${summary.totalTimeMinutes} min")
    }

    /** Dismiss the completed state and return to idle. */
    fun dismissCompletion() {
        _tourState.value = TourState.Idle
    }

    /** Update the user's current GPS location (for distance calculations + geofence checks). */
    fun onLocationUpdate(point: GeoPoint) {
        lastLocation = point

        // Check geofences and auto-trigger narration
        if (_tourState.value is TourState.Active) {
            val events = geofenceManager.checkPosition(point.latitude, point.longitude)
            for (event in events) {
                handleGeofenceEvent(event)
            }
        } else if (ambientModeEnabled && _tourState.value is TourState.Idle) {
            checkAmbientProximity(point)
        }
    }

    /** Ambient mode: check if user is near any Salem POI and speak a hint. */
    private fun checkAmbientProximity(point: GeoPoint) {
        val pois = allPoisCache ?: return
        for (poi in pois) {
            if (poi.id in ambientHintedPois) continue
            val dist = haversineM(point.latitude, point.longitude, poi.lat, poi.lng)
            if (dist <= AMBIENT_RADIUS_M) {
                ambientHintedPois.add(poi.id)
                // S154: stripped commercial POIs get the name+address line,
                // never the SI-generated shortNarration.
                val hint = if (PoiContentPolicy.shouldStripByCategory(poi.category)) {
                    val addr = poi.address.takeIf { it.isNotBlank() }
                    if (addr != null) "You are near ${poi.name}, at $addr." else "You are near ${poi.name}."
                } else {
                    poi.shortNarration ?: "You're near ${poi.name}."
                }
                narrationManager.speakHint(hint, poi.name)
                DebugLogger.i(TAG, "Ambient hint: ${poi.name} (${dist.toInt()}m)")
                break // One hint at a time
            }
        }
    }

    /** Load POI cache for ambient mode. Call from ViewModel init. */
    suspend fun loadAmbientPois() {
        allPoisCache = repository.getAllTourPois()
        DebugLogger.i(TAG, "Ambient POI cache loaded: ${allPoisCache?.size ?: 0} POIs")
    }

    /** Clear ambient hint history (e.g., on new session). */
    fun resetAmbientHints() {
        ambientHintedPois.clear()
    }

    /** React to a geofence event with auto-narration. */
    private fun handleGeofenceEvent(event: TourGeofenceEvent) {
        // S125: Historical Mode routes tour-stop narration through
        // NarrationGeofenceManager instead — it honors the whitelist,
        // reads `historical_note` via getNarrationForPass, and respects
        // the 1692-newspaper interleave. Skipping the TourEngine's direct
        // speakShort/Long here prevents the overnight-test bug where tour
        // stops like "Salem Common" fired a SHORT_NARRATION using the
        // legacy short text and bypassed the Historical Mode gate.
        val skipDirectNarration =
            narrationGeofenceManager.isHistoricalMode() || !narrationManager.autoNarrationEnabled

        when (event.type) {
            GeofenceEventType.APPROACH -> {
                if (!skipDirectNarration) narrationManager.speakShortNarration(event.poi)
            }
            GeofenceEventType.ENTRY -> {
                if (!skipDirectNarration) narrationManager.speakLongNarration(event.poi)
                // S125: Auto-advance the tour HUD when the walker reaches
                // the current stop's POI. Previously the index only moved
                // on an explicit Next tap, so the overnight walker completed
                // the Heritage Trail with the HUD stuck at "Tour: 1/10 —
                // Unknown" for the full 41 minutes.
                maybeAdvanceOnEntry(event.poi.id)
            }
            GeofenceEventType.EXIT -> {
                if (!skipDirectNarration) {
                    event.stop.transitionNarration?.let { narrationManager.speakTransition(it) }
                }
            }
        }
    }

    /**
     * S125: If the entered POI matches the current stop's POI, advance.
     * No-op when no tour is active or the match fails (walker may be
     * passing a prior/later stop out of sequence — we never jump the
     * index non-monotonically from a geofence event).
     */
    private fun maybeAdvanceOnEntry(poiId: String) {
        val current = activeOrNull() ?: return
        val idx = current.progress.currentStopIndex
        val expected = current.stops.getOrNull(idx)?.poiId ?: return
        if (expected != poiId) return
        DebugLogger.i(TAG, "Auto-advance on ENTRY: stop $idx/${current.stops.size} poi=$poiId")
        advanceToNextStop()
    }

    // ── Custom Tour Builder ──────────────────────────────────────────────

    /**
     * Start a custom tour from a user-selected list of POIs.
     * POIs are route-optimized via nearest-neighbor before starting.
     * @param selectedPois POIs the user picked (any order)
     * @param tourName display name for the custom tour
     */
    fun startCustomTour(selectedPois: List<TourPoi>, tourName: String = "Custom Tour") {
        if (selectedPois.isEmpty()) {
            _tourState.value = TourState.Error("Select at least one stop")
            return
        }
        _tourState.value = TourState.Loading

        val optimized = optimizeRoute(selectedPois)
        val totalDistKm = totalRouteDistanceKm(optimized)
        val estMinutes = estimateWalkingMinutes(totalDistKm)

        val tour = Tour(
            id = "custom_${System.currentTimeMillis()}",
            name = tourName,
            theme = "CUSTOM",
            description = "Your custom walking tour with ${optimized.size} stops",
            estimatedMinutes = estMinutes,
            distanceKm = totalDistKm,
            stopCount = optimized.size,
            difficulty = when {
                totalDistKm > 4.0 -> "challenging"
                totalDistKm > 2.0 -> "moderate"
                else -> "easy"
            }
        )

        val stops = optimized.mapIndexed { i, poi ->
            val prevPoi = if (i > 0) optimized[i - 1] else null
            val distFromPrev = prevPoi?.let {
                haversineM(it.lat, it.lng, poi.lat, poi.lng).toInt()
            }
            TourStop(
                tourId = tour.id,
                poiId = poi.id,
                stopOrder = i,
                transitionNarration = if (i == 0) "Let's begin! Our first stop is ${poi.name}."
                                      else "Next, we'll head to ${poi.name}.",
                walkingMinutesFromPrev = distFromPrev?.let { (it / 80.0).toInt().coerceAtLeast(1) },
                distanceMFromPrev = distFromPrev
            )
        }

        val progress = TourProgress(
            tourId = tour.id,
            currentStopIndex = 0,
            startTimeMs = System.currentTimeMillis()
        ).also { it.totalStopCount = stops.size }

        val activeTour = ActiveTour(tour, stops, optimized, progress)
        _tourState.value = TourState.Active(activeTour)
        persistProgress(activeTour)
        geofenceManager.loadStops(stops, optimized)

        DebugLogger.i(TAG, "Custom tour started: ${optimized.size} stops, " +
                "%.1f km, ~${estMinutes} min".format(totalDistKm))
    }

    // ── Time-Budget Tour Builder ────────────────────────────────────────

    /**
     * Build a tour that fits within the given time budget.
     * Selects highest-priority POIs, then optimizes the route.
     * @param budgetMinutes how long the user has (30, 60, 90, 180+)
     * @param allPois the full POI catalog to pick from
     * @param startPoint optional user's current location for route start
     */
    fun buildTimeBudgetTour(budgetMinutes: Int, allPois: List<TourPoi>, startPoint: GeoPoint? = null): List<TourPoi> {
        if (allPois.isEmpty()) return emptyList()

        // Filter out POIs requiring transportation unless budget is generous
        val candidates = if (budgetMinutes < 120)
            allPois.filter { !it.requiresTransportation }
        else allPois

        // Sort by priority (1=must-see, 5=minor)
        val sorted = candidates.sortedBy { it.priority }

        // Estimate: avg 5 min per stop + walking time between stops
        // For Salem's compact walkable core (~1 sq mi), avg inter-stop walk ~4 min
        val avgMinutesPerStop = 9  // 5 min at stop + 4 min walking

        val maxStops = (budgetMinutes / avgMinutesPerStop).coerceAtLeast(2)
        val selected = sorted.take(maxStops)

        // Optimize route order
        val optimized = if (startPoint != null) {
            optimizeRouteFromStart(selected, startPoint)
        } else {
            optimizeRoute(selected)
        }

        DebugLogger.i(TAG, "Time budget: ${budgetMinutes}min → ${optimized.size} stops " +
                "(from ${candidates.size} candidates, max=$maxStops)")
        return optimized
    }

    // ── Route Optimization ──────────────────────────────────────────────

    /**
     * Nearest-neighbor TSP heuristic: start from the first POI, always
     * visit the closest unvisited POI next. O(n²), fine for <50 stops.
     */
    fun optimizeRoute(pois: List<TourPoi>): List<TourPoi> {
        if (pois.size <= 2) return pois

        val remaining = pois.toMutableList()
        val route = mutableListOf(remaining.removeAt(0))

        while (remaining.isNotEmpty()) {
            val last = route.last()
            val nearest = remaining.minBy { haversineM(last.lat, last.lng, it.lat, it.lng) }
            remaining.remove(nearest)
            route.add(nearest)
        }
        return route
    }

    /**
     * Nearest-neighbor starting from the POI closest to [startPoint].
     */
    fun optimizeRouteFromStart(pois: List<TourPoi>, startPoint: GeoPoint): List<TourPoi> {
        if (pois.isEmpty()) return pois
        val sorted = pois.sortedBy { haversineM(startPoint.latitude, startPoint.longitude, it.lat, it.lng) }
        val reordered = mutableListOf(sorted.first())
        val remaining = sorted.drop(1).toMutableList()

        while (remaining.isNotEmpty()) {
            val last = reordered.last()
            val nearest = remaining.minBy { haversineM(last.lat, last.lng, it.lat, it.lng) }
            remaining.remove(nearest)
            reordered.add(nearest)
        }
        return reordered
    }

    /** Total route distance in km for a sequence of POIs. */
    fun totalRouteDistanceKm(pois: List<TourPoi>): Float {
        if (pois.size < 2) return 0f
        var total = 0.0
        for (i in 0 until pois.size - 1) {
            total += haversineM(pois[i].lat, pois[i].lng, pois[i + 1].lat, pois[i + 1].lng)
        }
        return (total / 1000.0).toFloat()
    }

    /** Estimate walking minutes: ~80m/min (comfortable tourist pace). */
    fun estimateWalkingMinutes(distanceKm: Float): Int {
        val walkMin = (distanceKm * 1000 / 80.0).toInt()
        // Add ~5 min per stop for viewing/reading/photos
        return walkMin
    }

    // ── Restore from SharedPreferences ────────────────────────────────────

    /** Check for a previously persisted tour and restore it. Called on app startup. */
    suspend fun restoreIfSaved(): Boolean {
        val savedTourId = prefs.getString(KEY_ACTIVE_TOUR_ID, null) ?: return false
        DebugLogger.i(TAG, "Restoring saved tour: $savedTourId")

        try {
            val tour = repository.getTourById(savedTourId) ?: return false
            var stops = repository.getTourStops(savedTourId)
            val pois = repository.getTourPois(savedTourId)

            // Restore custom stop order if it was modified
            val customOrderJson = prefs.getString(KEY_CUSTOM_STOP_ORDER, null)
            if (customOrderJson != null) {
                val customOrder: List<String> = gson.fromJson(
                    customOrderJson, object : TypeToken<List<String>>() {}.type
                )
                stops = customOrder.mapNotNull { poiId ->
                    stops.firstOrNull { it.poiId == poiId }
                }
            }

            val completedStops: Set<Int> = prefs.getStringSet(KEY_COMPLETED_STOPS, emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

            val skippedStops: Set<Int> = prefs.getStringSet(KEY_SKIPPED_STOPS, emptySet())
                ?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

            val progress = TourProgress(
                tourId = savedTourId,
                currentStopIndex = prefs.getInt(KEY_CURRENT_STOP_INDEX, 0),
                completedStops = completedStops,
                skippedStops = skippedStops,
                startTimeMs = System.currentTimeMillis(),
                totalDistanceWalkedM = prefs.getFloat(KEY_DISTANCE_WALKED, 0f).toDouble(),
                elapsedMs = prefs.getLong(KEY_ELAPSED_BEFORE_PAUSE, 0L)
            ).also { it.totalStopCount = stops.size }

            val activeTour = ActiveTour(tour, stops, pois, progress)
            geofenceManager.loadStops(stops, pois)
            _tourState.value = TourState.Paused(activeTour)

            DebugLogger.i(TAG, "Restored tour: ${tour.name} at stop ${progress.currentStopIndex}/${stops.size}")
            return true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to restore tour: ${e.message}", e)
            clearPersistedProgress()
            return false
        }
    }

    // ── Query helpers ────────────────────────────────────────────────────

    /** Get the current stop's TourPoi, or null if no tour is active. */
    fun getCurrentPoi(): TourPoi? = activeOrPausedOrNull()?.currentPoi

    /** Get the GeoPoint for a stop index. */
    fun getStopLocation(stopIndex: Int): GeoPoint? {
        val current = activeOrPausedOrNull() ?: return null
        val stop = current.stops.getOrNull(stopIndex) ?: return null
        val poi = current.pois.firstOrNull { it.id == stop.poiId } ?: return null
        return GeoPoint(poi.lat, poi.lng)
    }

    /** Get all stop GeoPoints for drawing the route polyline. */
    fun getAllStopLocations(): List<GeoPoint> {
        val current = activeOrPausedOrNull() ?: return emptyList()
        return current.stops.mapNotNull { stop ->
            current.pois.firstOrNull { it.id == stop.poiId }?.let { GeoPoint(it.lat, it.lng) }
        }
    }

    /** Distance in meters from the user's current GPS to a specific stop. */
    fun distanceToStop(stopIndex: Int): Double? {
        val location = lastLocation ?: return null
        val stopPoint = getStopLocation(stopIndex) ?: return null
        return haversineM(location.latitude, location.longitude, stopPoint.latitude, stopPoint.longitude)
    }

    /** True if the user is within the geofence radius of the given stop. */
    fun isWithinGeofence(stopIndex: Int): Boolean {
        val current = activeOrPausedOrNull() ?: return false
        val distance = distanceToStop(stopIndex) ?: return false
        val stop = current.stops.getOrNull(stopIndex) ?: return false
        val poi = current.pois.firstOrNull { it.id == stop.poiId } ?: return false
        return distance <= poi.geofenceRadiusM
    }

    /** Check if user is within geofence of the current stop. */
    fun isAtCurrentStop(): Boolean {
        val current = activeOrNull() ?: return false
        return isWithinGeofence(current.progress.currentStopIndex)
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun activeOrNull(): ActiveTour? =
        (_tourState.value as? TourState.Active)?.activeTour

    private fun activeOrPausedOrNull(): ActiveTour? = when (val state = _tourState.value) {
        is TourState.Active -> state.activeTour
        is TourState.Paused -> state.activeTour
        else -> null
    }

    private fun calcDistanceBetweenStops(tour: ActiveTour, fromIndex: Int, toIndex: Int): Double {
        val fromStop = tour.stops.getOrNull(fromIndex) ?: return 0.0
        val toStop = tour.stops.getOrNull(toIndex) ?: return 0.0

        // Use pre-computed distance if available
        toStop.distanceMFromPrev?.let { return it.toDouble() }

        // Otherwise calculate from GPS coordinates
        val fromPoi = tour.pois.firstOrNull { it.id == fromStop.poiId } ?: return 0.0
        val toPoi = tour.pois.firstOrNull { it.id == toStop.poiId } ?: return 0.0
        return haversineM(fromPoi.lat, fromPoi.lng, toPoi.lat, toPoi.lng)
    }

    private fun persistProgress(activeTour: ActiveTour) {
        prefs.edit()
            .putString(KEY_ACTIVE_TOUR_ID, activeTour.tour.id)
            .putInt(KEY_CURRENT_STOP_INDEX, activeTour.progress.currentStopIndex)
            .putStringSet(KEY_COMPLETED_STOPS, activeTour.progress.completedStops.map { it.toString() }.toSet())
            .putStringSet(KEY_SKIPPED_STOPS, activeTour.progress.skippedStops.map { it.toString() }.toSet())
            .putLong(KEY_START_TIME, activeTour.progress.startTimeMs)
            .putFloat(KEY_DISTANCE_WALKED, activeTour.progress.totalDistanceWalkedM.toFloat())
            .putLong(KEY_ELAPSED_BEFORE_PAUSE, activeTour.progress.elapsedMs)
            .apply()
    }

    private fun persistCustomStopOrder(poiIds: List<String>) {
        prefs.edit()
            .putString(KEY_CUSTOM_STOP_ORDER, gson.toJson(poiIds))
            .apply()
    }

    private fun clearPersistedProgress() {
        prefs.edit().clear().apply()
        DebugLogger.i(TAG, "Cleared persisted tour progress")
    }

    companion object {
        /** Haversine distance in meters between two lat/lng points. */
        fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val R = 6_371_000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return R * c
        }
    }
}
