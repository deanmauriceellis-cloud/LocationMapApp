/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
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
import com.example.wickedsalemwitchcitytour.content.dao.PoiPassportDao
import com.example.wickedsalemwitchcitytour.content.model.Tour
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.content.model.TourStop
import com.example.wickedsalemwitchcitytour.userdata.dao.PassportVisitDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module TourEngine.kt"

/**
 * Tour Engine — manages the lifecycle of a guided walking tour.
 *
 * Responsibilities:
 *   - Load tour + baked legs + passport POIs from SalemContentRepository
 *   - Track elapsed time + cumulative distance
 *   - Pause / resume / detour with SharedPreferences persistence
 *   - Fire [TourState.Completed] when every POI in the tour's passport has
 *     been heard (S269 — visit-count vs passport-count comparison; one-shot
 *     latch per session)
 *   - Emit TourState flow for UI observation
 *
 * S269 — stops-progress machinery removed. The four leftover stops-based
 * UIs (top HUD banner, Active Tour dialog, completion-stats body, numbered
 * polyline pins) and their backing state (`currentStopIndex`,
 * `completedStops`, `skippedStops`, advance/skip/add/remove/reorder) are
 * gone. Tour membership now flows through `poi_passport` (built from
 * operator-authored filter in the web admin); visit state flows through
 * `passport_visit` (POI-keyed lifetime log).
 */
@Singleton
class TourEngine @Inject constructor(
    private val repository: SalemContentRepository,
    private val geofenceManager: TourGeofenceManager,
    private val narrationManager: NarrationManager,
    private val narrationGeofenceManager: NarrationGeofenceManager,
    private val poiPassportDao: PoiPassportDao,
    private val passportVisitDao: PassportVisitDao,
    @ApplicationContext private val context: Context
) {
    private val TAG = "TourEngine"
    private val PREFS_NAME = "tour_engine_prefs"
    private val KEY_ACTIVE_TOUR_ID = "active_tour_id"
    private val KEY_START_TIME = "start_time"
    private val KEY_DISTANCE_WALKED = "distance_walked"
    private val KEY_ELAPSED_BEFORE_PAUSE = "elapsed_before_pause"
    // S221 — detour persistence keys. Presence of KEY_DETOUR_POI_ID under an
    // already-paused tour means we should restore as TourState.Detour rather
    // than TourState.Paused, and the activity should re-render the floating
    // detour banner on relaunch.
    private val KEY_DETOUR_POI_ID = "detour_poi_id"
    private val KEY_DETOUR_POI_NAME = "detour_poi_name"
    private val KEY_DETOUR_PREV_HIST_LANDMARK = "detour_prev_hist_landmark_tour"
    private val KEY_DETOUR_PREV_CIVIC = "detour_prev_civic_tour"

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    /**
     * S269 — one-shot latch. Flipped to true the first time
     * [maybeCompleteFromPassport] observes a full sweep; flipped back to
     * false in [startTour] / [endTour] so a subsequent tour can complete
     * again. Lives on the engine (not on [ActiveTour]) so it isn't part of
     * the immutable state snapshot consumed by the UI.
     */
    private var completionFiredThisSession: Boolean = false

    // ── Tour Lifecycle ─────────────────────────────────────────────────────

    /**
     * Start a pre-defined tour by ID.
     * Loads tour metadata, baked legs, and the passport POI list.
     */
    suspend fun startTour(tourId: String) {
        DebugLogger.i(TAG, "startTour($tourId)")
        _tourState.value = TourState.Loading
        completionFiredThisSession = false

        try {
            val tour = repository.getTourById(tourId)
            if (tour == null) {
                _tourState.value = TourState.Error("Tour not found: $tourId")
                return
            }

            // S185: every authored tour is polyline-only — `stops` rows in the
            // asset carry poi_id NULL, so we keep the list for parity with
            // TourGeofenceManager.loadStops but never derive tour-membership
            // from it. See memory rule `feedback_tour_stops_not_poi_anchored.md`.
            val stops = repository.getTourStops(tourId)
            val pois = repository.getTourPois(tourId)
            val legs = try {
                repository.getTourLegs(tourId)
            } catch (e: Exception) {
                DebugLogger.w(TAG, "startTour: failed to read tour_legs for $tourId: ${e.message}")
                emptyList()
            }

            // S269 — passport binding. Tour-membership for V1 lives here.
            val passportId = try {
                poiPassportDao.findPassportIdForTour(tourId)
            } catch (e: Exception) {
                DebugLogger.w(TAG, "startTour: passport lookup failed for $tourId: ${e.message}")
                null
            }
            val passportPoiIds: Set<String> = if (passportId != null) {
                try {
                    poiPassportDao.findByPassport(passportId).map { it.poiId }.toSet()
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "startTour: passport POI load failed for $passportId: ${e.message}")
                    emptySet()
                }
            } else {
                emptySet()
            }
            if (passportId == null) {
                DebugLogger.i(TAG, "Tour '$tourId' has no passport authored — completion trigger disabled")
            } else {
                DebugLogger.i(TAG, "Tour '$tourId' bound to passport '$passportId' (${passportPoiIds.size} POIs)")
            }

            val progress = TourProgress(
                tourId = tourId,
                startTimeMs = System.currentTimeMillis()
            )

            val activeTour = ActiveTour(
                tour = tour,
                stops = stops,
                pois = pois,
                legs = legs,
                progress = progress,
                passportId = passportId,
                passportPoiIds = passportPoiIds,
            )
            _tourState.value = TourState.Active(activeTour)
            persistProgress(activeTour)
            geofenceManager.loadStops(stops, pois)

            // S217 — Tour Mode is owned by SalemMainActivity.refreshHistoricalModeForActiveTour,
            // which observes tourState and the FAB. Calling setTourMode here used
            // to race with the Activity's observer when the "Show All POIs" FAB
            // was already on at tour-start: the observer would correctly disable
            // tour-mode (FAB-on rule), then this line re-enabled it, leaving the
            // user's FAB-on intent silently overridden. The Activity now reads
            // its own Layers prefs and pushes setTourMode + the show-all
            // override; we just emit the historical-narration-mode flag below.

            // S193 — Historical Narration Mode. When the tour is flagged
            // is_historical_tour=true, NarrationGeofenceManager returns
            // historical_narration (strict pre-1860) instead of short/long.
            // POIs without historical_narration stay silent on a historical
            // tour by design.
            narrationGeofenceManager.setHistoricalNarrationMode(tour.isHistoricalTour)
            if (tour.isHistoricalTour) {
                DebugLogger.i(TAG, "Historical Narration Mode ENABLED for '${tour.name}'")
            }

            DebugLogger.i(TAG, "Tour started: ${tour.name} — ${legs.size} legs, ${passportPoiIds.size} passport POIs")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "startTour failed: ${e.message}", e)
            _tourState.value = TourState.Error("Failed to start tour: ${e.message}")
        }
    }

    /** Pause the tour. Preserves elapsed time so it doesn't count pause time. */
    fun pauseTour() {
        val current = activeOrNull() ?: return
        val elapsed = System.currentTimeMillis() - current.progress.startTimeMs + current.progress.elapsedMs
        val pausedProgress = current.progress.copy(elapsedMs = elapsed)
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
        )

        val updated = activeTour.copy(progress = newProgress)
        _tourState.value = TourState.Active(updated)
        persistProgress(updated)
        DebugLogger.i(TAG, "Tour resumed: ${activeTour.tour.name}")
    }

    // ── Detour Lifecycle (S221) ───────────────────────────────────────────

    /**
     * Start a detour from the active tour. Pauses the underlying tour
     * (reusing [pauseTour]'s persistence), captures the current Layer-toggle
     * gate flags so they can be re-applied on return, fully relaxes the
     * narration gate (so other POIs encountered en-route to the detour
     * target actually speak), and emits [TourState.Detour].
     *
     * No-op unless a tour is currently Active.
     */
    fun startDetour(detourPoiId: String, detourPoiName: String) {
        val current = activeOrNull() ?: run {
            DebugLogger.w(TAG, "startDetour called with no Active tour — ignored")
            return
        }
        // Snapshot the current Layer-toggle pref state directly from the
        // gate manager so we don't depend on an Activity-owned SharedPreferences.
        val prevHistLandmark = narrationGeofenceManager.currentTourAllowHistLandmarks()
        val prevCivic = narrationGeofenceManager.currentTourAllowCivic()

        // Reuse pauseTour's persistence path — it writes the underlying
        // active-tour keys (start time, distance, elapsed) and emits
        // TourState.Paused. We then overwrite with TourState.Detour.
        pauseTour()
        prefs.edit()
            .putString(KEY_DETOUR_POI_ID, detourPoiId)
            .putString(KEY_DETOUR_POI_NAME, detourPoiName)
            .putBoolean(KEY_DETOUR_PREV_HIST_LANDMARK, prevHistLandmark)
            .putBoolean(KEY_DETOUR_PREV_CIVIC, prevCivic)
            .apply()

        // Relax the gate fully — anything eligible narrates while detoured.
        narrationGeofenceManager.setTourMode(active = false)

        _tourState.value = TourState.Detour(
            activeTour = current.copy(
                progress = current.progress.copy(
                    elapsedMs = System.currentTimeMillis() - current.progress.startTimeMs + current.progress.elapsedMs
                )
            ),
            detourPoiId = detourPoiId,
            detourPoiName = detourPoiName,
        )
        DebugLogger.i(TAG, "Detour started → $detourPoiName ($detourPoiId)")
    }

    /**
     * End the current detour.
     *
     * @param rejoin true → re-apply the saved Layer-toggle prefs and
     *   resume the underlying tour ([TourState.Active]). The caller is
     *   responsible for routing the user to either the nearest tour
     *   polyline point or the last stop (see TourViewModel.ReturnTarget).
     *   false → drop detour metadata but leave the tour in [TourState.Paused]
     *   so the existing [restoreIfSaved] path will offer to resume it on
     *   the next session ("continue later").
     *
     * No-op unless current state is [TourState.Detour].
     */
    fun endDetour(rejoin: Boolean) {
        val state = _tourState.value
        if (state !is TourState.Detour) {
            DebugLogger.w(TAG, "endDetour called with state=$state — ignored")
            return
        }
        val prevHistLandmark = prefs.getBoolean(KEY_DETOUR_PREV_HIST_LANDMARK, false)
        val prevCivic = prefs.getBoolean(KEY_DETOUR_PREV_CIVIC, false)

        prefs.edit()
            .remove(KEY_DETOUR_POI_ID)
            .remove(KEY_DETOUR_POI_NAME)
            .remove(KEY_DETOUR_PREV_HIST_LANDMARK)
            .remove(KEY_DETOUR_PREV_CIVIC)
            .apply()

        if (rejoin) {
            // Re-apply the gate prefs that were live before the detour, then
            // resume the underlying tour. The Active state is the trigger
            // for the Activity to drop the detour banner + polylines.
            narrationGeofenceManager.setTourMode(
                active = true,
                allowHistLandmarks = prevHistLandmark,
                allowCivic = prevCivic,
            )
            // Stage the underlying tour as Paused so resumeTour's contract
            // (state must be Paused) is satisfied, then resume.
            _tourState.value = TourState.Paused(state.activeTour)
            resumeTour()
            DebugLogger.i(TAG, "Detour ended → REJOIN (allowHist=$prevHistLandmark, allowCivic=$prevCivic)")
        } else {
            // "Stop tour, continue later" — leave the underlying tour in
            // Paused. Persisted progress already lives in the prefs from
            // the original pauseTour() call, so restoreIfSaved() on next
            // launch will offer to resume.
            _tourState.value = TourState.Paused(state.activeTour)
            DebugLogger.i(TAG, "Detour ended → tour PAUSED for next session")
        }
    }

    /** True iff a detour is currently active. */
    fun isDetourActive(): Boolean = _tourState.value is TourState.Detour

    // ── Detour anchor (S269) ──────────────────────────────────────────────

    /**
     * S269 — return the leg-end vertex on the active tour's polyline that's
     * geographically closest to the supplied user location. Used by the
     * "Back to last stop" rejoin path in [SalemMainActivityDetour] in place
     * of the deleted `currentStopIndex`-driven anchor.
     *
     * Walks every leg's anchor pair: the leg-end (`to_lat`/`to_lng`) anchors
     * are the canonical "stops" of a polyline-only tour, since they're the
     * vertices the operator hand-curated to land at meaningful destinations.
     *
     * Returns null when there's no active tour, no baked legs, or every leg
     * is missing both from/to anchors.
     */
    fun currentLegEndForDetourAnchor(userLat: Double, userLng: Double): GeoPoint? {
        val current = activeOrPausedOrNull() ?: return null
        var best: GeoPoint? = null
        var bestD = Double.MAX_VALUE
        for (leg in current.legs) {
            val candidates = listOfNotNull(
                leg.toLat?.let { lat -> leg.toLng?.let { lng -> GeoPoint(lat, lng) } },
                leg.fromLat?.let { lat -> leg.fromLng?.let { lng -> GeoPoint(lat, lng) } },
            )
            for (p in candidates) {
                val d = haversineM(userLat, userLng, p.latitude, p.longitude)
                if (d < bestD) {
                    bestD = d
                    best = p
                }
            }
        }
        return best
    }

    // ── Passport completion trigger (S269) ────────────────────────────────

    /**
     * S269 — called from the narration ENTRY path in [SalemMainActivityNarration]
     * after a [passportVisitDao.recordHeard] UPSERT lands. If every POI in
     * the active tour's passport has now been heard, transitions
     * [TourState.Active] → [TourState.Completed] exactly once per session.
     *
     * No-op when there's no active tour, the tour has no passport bound,
     * completion already fired this session, or the count is incomplete.
     */
    suspend fun maybeCompleteFromPassport() {
        if (completionFiredThisSession) return
        val current = activeOrNull() ?: return
        val ids = current.passportPoiIds
        if (ids.isEmpty()) return
        val heard = try {
            passportVisitDao.countHeardAmong(ids.toList())
        } catch (e: Exception) {
            DebugLogger.w(TAG, "maybeCompleteFromPassport: count failed: ${e.message}")
            return
        }
        if (heard < ids.size) return
        DebugLogger.i(TAG, "Passport completion: ${heard}/${ids.size} heard — firing TourState.Completed")
        completionFiredThisSession = true
        endTour()
    }

    /** End the tour and emit completion summary. */
    suspend fun endTour() {
        val current = activeOrPausedOrNull() ?: return
        val elapsed = when (_tourState.value) {
            is TourState.Active -> System.currentTimeMillis() - current.progress.startTimeMs + current.progress.elapsedMs
            is TourState.Paused -> current.progress.elapsedMs
            else -> 0L
        }

        val passportHeard = if (current.passportPoiIds.isEmpty()) 0
                            else try {
                                passportVisitDao.countHeardAmong(current.passportPoiIds.toList())
                            } catch (e: Exception) {
                                DebugLogger.w(TAG, "endTour: heard-count failed: ${e.message}")
                                0
                            }

        val summary = TourSummary(
            tourName = current.tour.name,
            passportId = current.passportId,
            passportTotal = current.passportPoiIds.size,
            passportHeard = passportHeard,
            totalTimeMs = elapsed,
            totalDistanceM = current.progress.totalDistanceWalkedM
        )

        _tourState.value = TourState.Completed(current, summary)
        clearPersistedProgress()
        geofenceManager.clear()
        narrationManager.stop()

        // S186: clear Tour Mode (and any leftover Historical Mode from older code paths).
        narrationGeofenceManager.setTourMode(false)
        if (narrationGeofenceManager.isHistoricalMode()) {
            narrationGeofenceManager.setHistoricalMode(false)
        }
        // S193: clear Historical Narration Mode if it was enabled for this tour.
        if (narrationGeofenceManager.isHistoricalNarrationMode()) {
            narrationGeofenceManager.setHistoricalNarrationMode(false)
        }
        DebugLogger.i(TAG, "Tour Mode DISABLED (tour ended)")

        DebugLogger.i(TAG, "Tour ended: ${summary.tourName} — " +
                "${summary.passportHeard}/${summary.passportTotal} stamps, ${summary.totalTimeMinutes} min")
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
                // S154: stripped commercial POIs get the category-aware line,
                // never the SI-generated shortNarration.
                // S217: line shape now matches PoiContentPolicy.strippedAnnouncement —
                // "You are near [the ]Name, a <noun-phrase>." (no address).
                val hint = if (PoiContentPolicy.shouldStripByCategory(poi.category)) {
                    com.example.wickedsalemwitchcitytour.content.BusinessLabel
                        .strippedSentence(poi.name, poi.category, poi.subcategories)
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

    /**
     * React to a geofence event with auto-narration.
     *
     * S269 — the maybeAdvanceOnEntry(...) call from the ENTRY branch is gone
     * along with the rest of the stops-progress shed. Tour-completion is now
     * driven by [maybeCompleteFromPassport], called from the narration
     * ENTRY hook in [SalemMainActivityNarration] after each passport visit
     * is recorded.
     */
    private fun handleGeofenceEvent(event: TourGeofenceEvent) {
        // S125: Historical Mode routes tour-stop narration through
        // NarrationGeofenceManager instead — it honors the whitelist,
        // reads `historical_note` via getNarrationForPass, and respects
        // the 1692-newspaper interleave. Skipping the TourEngine's direct
        // speakShort/Long here prevents the overnight-test bug where tour
        // stops like "Salem Common" fired a SHORT_NARRATION using the
        // legacy short text and bypassed the Historical Mode gate.
        // S193: same reasoning for Historical Narration Mode — direct
        // speakShort/Long would read the modern short_narration field,
        // bypassing the historical_narration substitution in
        // NarrationGeofenceManager.getNarrationForPass.
        val skipDirectNarration =
            narrationGeofenceManager.isHistoricalMode() ||
            narrationGeofenceManager.isHistoricalNarrationMode() ||
            !narrationManager.autoNarrationEnabled

        when (event.type) {
            GeofenceEventType.APPROACH -> {
                if (!skipDirectNarration) narrationManager.speakShortNarration(event.poi)
            }
            GeofenceEventType.ENTRY -> {
                if (!skipDirectNarration) narrationManager.speakLongNarration(event.poi)
            }
            GeofenceEventType.EXIT -> {
                if (!skipDirectNarration) {
                    event.stop.transitionNarration?.let { narrationManager.speakTransition(it) }
                }
            }
        }
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
        completionFiredThisSession = false

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
            startTimeMs = System.currentTimeMillis()
        )

        // Custom tours derive their "passport" implicitly from the selected
        // POI ids — visit-driven completion fires when every selected POI
        // has been heard. No author-side filter row in salem_passport_filters
        // is required.
        val passportPoiIds = optimized.map { it.id }.toSet()

        val activeTour = ActiveTour(
            tour = tour,
            stops = stops,
            pois = optimized,
            legs = emptyList(),
            progress = progress,
            passportId = null,
            passportPoiIds = passportPoiIds,
        )
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
            val stops = repository.getTourStops(savedTourId)
            val pois = repository.getTourPois(savedTourId)
            val legs = try {
                repository.getTourLegs(savedTourId)
            } catch (e: Exception) {
                DebugLogger.w(TAG, "restoreIfSaved: failed to read tour_legs: ${e.message}")
                emptyList()
            }
            val passportId = try {
                poiPassportDao.findPassportIdForTour(savedTourId)
            } catch (e: Exception) {
                DebugLogger.w(TAG, "restoreIfSaved: passport lookup failed: ${e.message}")
                null
            }
            val passportPoiIds: Set<String> = if (passportId != null) {
                try {
                    poiPassportDao.findByPassport(passportId).map { it.poiId }.toSet()
                } catch (e: Exception) {
                    emptySet()
                }
            } else {
                emptySet()
            }

            val progress = TourProgress(
                tourId = savedTourId,
                startTimeMs = System.currentTimeMillis(),
                totalDistanceWalkedM = prefs.getFloat(KEY_DISTANCE_WALKED, 0f).toDouble(),
                elapsedMs = prefs.getLong(KEY_ELAPSED_BEFORE_PAUSE, 0L)
            )

            val activeTour = ActiveTour(
                tour = tour,
                stops = stops,
                pois = pois,
                legs = legs,
                progress = progress,
                passportId = passportId,
                passportPoiIds = passportPoiIds,
            )
            geofenceManager.loadStops(stops, pois)
            // Restoring is a fresh session — let the completion latch arm again.
            completionFiredThisSession = false

            // S221 — if a detour was in progress, restore as TourState.Detour
            // so the floating banner re-renders. Otherwise, plain Paused.
            val detourPoiId = prefs.getString(KEY_DETOUR_POI_ID, null)
            val detourPoiName = prefs.getString(KEY_DETOUR_POI_NAME, null)
            _tourState.value = if (detourPoiId != null && detourPoiName != null) {
                // Gate stays relaxed across the relaunch — caller can re-apply
                // by tapping Return-to-tour, which calls endDetour(rejoin=true).
                narrationGeofenceManager.setTourMode(active = false)
                DebugLogger.i(TAG, "Restored MID-DETOUR → $detourPoiName")
                TourState.Detour(activeTour, detourPoiId, detourPoiName)
            } else {
                TourState.Paused(activeTour)
            }

            DebugLogger.i(TAG, "Restored tour: ${tour.name} (${legs.size} legs, ${passportPoiIds.size} passport POIs)")
            return true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to restore tour: ${e.message}", e)
            clearPersistedProgress()
            return false
        }
    }

    // ── Query helpers ────────────────────────────────────────────────────

    /** Get all stop GeoPoints for drawing the route polyline. Post-S190
     *  most tours have polyline-only stops (all-NULL poi_id) so this returns
     *  empty for them — callers fall back to the baked-legs path. */
    fun getAllStopLocations(): List<GeoPoint> {
        val current = activeOrPausedOrNull() ?: return emptyList()
        return current.stops.mapNotNull { stop ->
            current.pois.firstOrNull { it.id == stop.poiId }?.let { GeoPoint(it.lat, it.lng) }
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun activeOrNull(): ActiveTour? =
        (_tourState.value as? TourState.Active)?.activeTour

    private fun activeOrPausedOrNull(): ActiveTour? = when (val state = _tourState.value) {
        is TourState.Active -> state.activeTour
        is TourState.Paused -> state.activeTour
        // S221 — Detour keeps the underlying tour query-able (polyline,
        // current stop, etc.) so the activity can render the tour overlay
        // and compute return-to-tour routes against it.
        is TourState.Detour -> state.activeTour
        else -> null
    }

    private fun persistProgress(activeTour: ActiveTour) {
        prefs.edit()
            .putString(KEY_ACTIVE_TOUR_ID, activeTour.tour.id)
            .putLong(KEY_START_TIME, activeTour.progress.startTimeMs)
            .putFloat(KEY_DISTANCE_WALKED, activeTour.progress.totalDistanceWalkedM.toFloat())
            .putLong(KEY_ELAPSED_BEFORE_PAUSE, activeTour.progress.elapsedMs)
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
