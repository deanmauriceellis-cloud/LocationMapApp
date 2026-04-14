/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Phase 9T: Salem Walking Tour Restructure — Narration system wiring
 */

package com.example.wickedsalemwitchcitytour.ui

import android.graphics.Color
import android.view.View
import android.widget.TextView
import android.widget.Toast
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polygon
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import com.example.wickedsalemwitchcitytour.tour.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/** Narration queue — auto-advances through nearby POIs.
 *
 *  S112: This queue is now SOURCE-OF-TRUTH for both the audio play order and
 *  the proximity dock display. Insertion is order-agnostic (`addLast`); selection
 *  happens at dequeue time, where the queue is filtered by distance to the
 *  current user position (>50m → discarded) and sorted by tier (paid → historic
 *  → attraction → rest), then closest within tier wins. The dock renders the
 *  same filtered+sorted snapshot so dock order = play order. */
internal val narrationQueue = ArrayDeque<SalemPoi>()
private var currentNarration: SalemPoi? = null
private var narrationAutoPlay = true

/**
 * S112: file-scope cache of the most recent user GPS position, fed by
 * SalemMainActivityTour.updateTourLocation(). Used by the dequeue logic to
 * compute distance for the 50m discard filter and the within-tier closest-wins
 * selection. (0.0, 0.0) means "no fix yet" — dequeue falls back to FIFO until
 * the first real fix arrives.
 */
private var lastUserLat: Double = 0.0
private var lastUserLng: Double = 0.0

/**
 * S112+: Three-level discard radius cascade. The dequeue starts at the
 * standard radius and tightens stepwise as density increases:
 *   - STANDARD (40m): sparse mode, broad reach
 *   - HIGH     (30m): 3+ POIs within 40m → tighten focus
 *   - SUPER    (20m): 3+ POIs within 30m → very tight, "right in front of you"
 *
 * Each level drops POIs farther than the radius before tier evaluation.
 */
private const val STANDARD_DISCARD_RADIUS_M = 40.0
private const val HIGH_DENSITY_DISCARD_RADIUS_M = 30.0
private const val SUPER_HIGH_DENSITY_DISCARD_RADIUS_M = 20.0

/** S112+: POI count within standard radius that triggers HD mode. */
private const val HIGH_DENSITY_THRESHOLD = 3

/** S112+: POI count within HD radius that triggers SUPER-HD mode. */
private const val SUPER_HIGH_DENSITY_THRESHOLD = 3

/**
 * S112+: Bearing-based "ahead" filter — track the user's movement direction
 * computed from the prev → current GPS positions. POIs whose bearing from
 * the user is more than this many degrees off the movement direction are
 * dropped at dequeue (they're behind the user). 90° half-angle = entire
 * front half-circle, which eliminates only POIs strictly behind.
 */
private const val AHEAD_CONE_HALF_ANGLE_DEG = 90.0

/**
 * S115: Below this distance, the "ahead" filter is bypassed. At short range
 * the user is essentially AT the POI and directional gating driven by noisy
 * GPS bearings silently drops POIs the user is standing next to. See
 * isPoiAhead for the failure mode and the field-test evidence that motivated
 * this constant (Session 115 live log).
 */
private const val NEAR_ZONE_OVERRIDE_M = 15.0

/** S112+: Minimum movement (meters) before we update the bearing. Below this
 *  the GPS jitter is too large to compute a reliable direction. */
private const val BEARING_UPDATE_MIN_MOVE_M = 1.0

/** S112+: Cached movement bearing in degrees (0=N, 90=E, 180=S, 270=W).
 *  Null until the first meaningful movement is observed. */
private var lastMovementBearing: Double? = null

/** S115: Wall-clock timestamp of the last [lastMovementBearing] update.
 *  Used by the heading-up rotation to decide when to fall back from GPS
 *  movement bearing to device azimuth (stationary → stale → sensor). */
private var lastMovementBearingUpdateMs: Long = 0L

/**
 * S115: Cross-file accessor for [lastMovementBearing]. The heading-up map
 * rotation in SalemMainActivityTour.kt reads this on every fix. Kept as a
 * function (not a public var) so the narration file retains ownership of the
 * write path — only updateNarrationUserPosition mutates the bearing.
 */
internal fun SalemMainActivity.getLastMovementBearing(): Double? = lastMovementBearing

/**
 * S115: Milliseconds since the last meaningful movement bearing update.
 * Used by the hybrid heading source to fall back on the device orientation
 * sensor when the GPS-derived bearing has gone stale (user is stationary).
 * Returns Long.MAX_VALUE if no bearing has ever been set.
 */
internal fun SalemMainActivity.getLastMovementBearingAgeMs(): Long {
    if (lastMovementBearingUpdateMs == 0L) return Long.MAX_VALUE
    return System.currentTimeMillis() - lastMovementBearingUpdateMs
}

/**
 * S115: Explicit bearing override, used by the walk sim to "face" a POI
 * during its short dwell at each anchor. Without this, heading-up locks on
 * the last real movement bearing (walker's direction of travel) and the
 * POI the sim is pausing at may be sideways or behind. Setting the bearing
 * to (walker → POI) lets the map rotate so the POI is directly ahead for
 * the duration of the pause. Pass null to clear the override — normally
 * the next real step through updateNarrationUserPosition re-establishes a
 * natural bearing on its own.
 */
internal fun SalemMainActivity.setMovementBearing(degrees: Double?) {
    lastMovementBearing = degrees
}

/** S112+: Previous user position used to compute movement bearing. */
private var prevUserLat: Double = 0.0
private var prevUserLng: Double = 0.0

// ── S115: Dwell expansion for graduated reach-out at destinations ─────────
//
// Problem the operator saw at the Witch House: 112 POIs nearby, only the
// handful within per-POI entry radii fired, then silence. The HD density
// squeeze actively TIGHTENS the radius in dense clusters, which is wrong for
// dwell scenarios (stand still at a destination → should hear the block).
//
// Fix: when the user stops and the reach-out at DWELL_RADIUS_TIGHT_M returns
// nothing, progressively expand the reach-out radius up to DWELL_RADIUS_MAX_M.
// Reset on meaningful movement away from the dwell anchor. Cap the number of
// expanded reach-outs per dwell session so a dense-cluster stop doesn't run on
// forever.
//
// This affects ONLY the silence reach-out path. Normal ENTRY events still
// fire on their per-POI geofence radius — dwell expansion never replaces
// the walk-through narration; it only fills the silence when the user
// stops inside a cluster.

/** S115/S121: Starting reach-out radius for a fresh dwell. */
private const val DWELL_RADIUS_TIGHT_M = 20.0

/** S121: First expansion step. Triggered when tight returns nothing. */
private const val DWELL_RADIUS_MEDIUM_M = 35.0

/** S121: Final expansion ceiling (was 100m, tightened to 50m). Beyond this the system goes silent until the user moves. */
private const val DWELL_RADIUS_MAX_M = 50.0

/** S115: Distance from the dwell anchor that resets the expansion ladder. */
private const val DWELL_RESET_DISTANCE_M = 15.0

/** S115: Max POIs narrated via dwell expansion per single dwell session. */
private const val DWELL_MAX_POIS_PER_SESSION = 6

/**
 * S118: Consecutive narration pacing. After every PACING_BURST_SIZE narrations
 * played back-to-back, impose a PACING_COOLDOWN_MS breather before the next one.
 * Prevents the "machine gun" effect in dense downtown clusters where the system
 * would narrate non-stop with only 500ms gaps. Reset on user movement (dwell reset).
 */
private const val PACING_BURST_SIZE = 3
private const val PACING_COOLDOWN_MS = 30_000L
private var consecutiveNarrations: Int = 0

/**
 * S125: Maximum silence between 1692 newspaper dispatches while in Historical
 * Mode. A per-activity heartbeat coroutine wakes up every interval and, if no
 * newspaper has fired within that window, cancels any in-flight POI narration
 * and forces the next dispatch. Safety net for the starvation case where
 * back-to-back POI cancel-interrupts keep the TTS state oscillating so the
 * Idle silence branch never runs. See SalemMainActivity.lastNewspaperFiredMs.
 */
private const val NEWSPAPER_HEARTBEAT_INTERVAL_MS = 5 * 60_000L

/** S115: Lat/lng of the anchor point where the current dwell session began. 0.0 = no active dwell. */
private var dwellAnchorLat: Double = 0.0
private var dwellAnchorLng: Double = 0.0

/** S115: Current reach-out radius for the active dwell session. Starts tight, steps up. */
private var dwellCurrentRadiusM: Double = DWELL_RADIUS_TIGHT_M

/** S115: POIs narrated via dwell expansion in the current session. Resets on movement. */
private var dwellPoisPlayed: Int = 0

/**
 * S112: Glowing highlight ring drawn around the currently-narrated POI on the
 * map. The inner Polygon is the bright ring; the outer Polygon pulses around
 * it. Both are sized to hug the POI icon visually rather than mark a geofence.
 */
private var narrationHighlightInner: Polygon? = null
private var narrationHighlightOuter: Polygon? = null
private var narrationHighlightAnimJob: Job? = null

/** S112+: visual radius of the inner highlight ring — small, hugs the POI icon. */
private const val HIGHLIGHT_INNER_RADIUS_M = 5.0

/** S112+: visual radius of the outer (pulsing) highlight ring — small halo. */
private const val HIGHLIGHT_OUTER_RADIUS_M = 9.0

/**
 * Initialize the Phase 9T narration system:
 * - Load all narration points from Room DB
 * - Set up NarrationGeofenceManager with auto-queuing
 * - Set up CorridorGeofenceManager
 * - Set up ProximityDock (positioned above controls)
 * - Observe geofence events for narration dialog triggers
 */
internal fun SalemMainActivity.initNarrationSystem() {
    DebugLogger.i("SalemMainActivity", "initNarrationSystem() — Phase 9T setup")

    // narrationGeofenceManager is Hilt-injected (S110) — already constructed.
    // Don't replace it here; the singleton lift is what preserves dedup state
    // across Activity recreation.
    corridorManager = CorridorGeofenceManager()

    // Initialize proximity dock
    //
    // S113: Dock tap now opens the POI Detail Sheet instead of enqueueing
    // the narration. Ambient narration continues in the background; the
    // sheet will queue its own read-through via a dedicated TTS tag so it
    // can interrupt only its own speech on dismiss/click.
    proximityDock = ProximityDock(this, binding.root).apply {
        onPoiTapped = { point ->
            DebugLogger.i(
                "SalemMainActivity",
                "DOCK TAP id=${point.id} name=${point.name} → showing PoiDetailSheet"
            )
            PoiDetailSheet.show(point, supportFragmentManager)
        }
    }

    // Load corridors
    corridorManager?.loadCorridors(SalemCorridors.all())

    // Load narration points from database — ALWAYS ON, no tour selection needed
    lifecycleScope.launch {
        try {
            val points = tourViewModel.loadNarrationPoints()
            if (points.isNotEmpty()) {
                narrationGeofenceManager.loadPoints(points)
                // Show proximity dock immediately
                proximityDock?.show()
                // Also load narration point markers on the map
                loadNarrationPointMarkers()
                DebugLogger.i("SalemMainActivity", "Ambient narration ACTIVE: ${points.size} points, ${SalemCorridors.all().size} corridors — no tour selection required")
            } else {
                DebugLogger.w("SalemMainActivity", "No narration points in database")
            }
        } catch (e: Exception) {
            DebugLogger.e("SalemMainActivity", "Failed to load narration points", e)
        }
    }

    // Observe narration geofence events — auto-queue on entry
    lifecycleScope.launch {
        narrationGeofenceManager.events.collectLatest { event ->
            when (event.type) {
                NarrationEventType.APPROACH -> {
                    DebugLogger.i("SalemMainActivity",
                        "APPROACH: ${event.point.name} (${event.distanceM.toInt()}m)")
                }
                NarrationEventType.ENTRY -> {
                    DebugLogger.i("SalemMainActivity",
                        "ENTRY: ${event.point.name} (${event.distanceM.toInt()}m)")
                    enqueueNarration(event.point, jumpToFront = false)
                }
            }
        }
    }

    // Observe TTS completion to auto-advance queue, with silence reach-out
    lifecycleScope.launch {
        tourViewModel.narrationState.collectLatest { state ->
            DebugLogger.i("NARR-STATE", "narrationState → $state (autoPlay=$narrationAutoPlay, queueSize=${narrationQueue.size})")
            if (state is com.example.wickedsalemwitchcitytour.tour.NarrationState.Idle && narrationAutoPlay) {
                // Clear currentNarration — TTS is done, allow next entry to play immediately
                currentNarration = null
                DebugLogger.i("NARR-STATE", "  Idle → currentNarration cleared, queueSize=${narrationQueue.size}")

                // S118: Pacing — after every 3rd consecutive narration, cool down 30s
                consecutiveNarrations++
                if (consecutiveNarrations >= PACING_BURST_SIZE) {
                    DebugLogger.i("NARR-STATE",
                        "  PACING COOLDOWN: $consecutiveNarrations consecutive narrations → " +
                        "breathing ${PACING_COOLDOWN_MS / 1000}s before next")
                    consecutiveNarrations = 0
                    clearNarrationHighlight()
                    kotlinx.coroutines.delay(PACING_COOLDOWN_MS)
                    // After cooldown, check if anything queued during the break
                    if (narrationQueue.isNotEmpty()) {
                        playNextNarration()
                    }
                    // Otherwise fall through to silence/reach-out below
                    if (narrationQueue.isEmpty()) {
                        return@collectLatest
                    }
                } else if (narrationQueue.isNotEmpty()) {
                    // TTS finished — play next in queue after brief pause
                    val gapMs = if (walkSimRunning) 500L else 2000L
                    DebugLogger.i("NARR-STATE", "  Queue has ${narrationQueue.size} → advancing after ${gapMs}ms")
                    kotlinx.coroutines.delay(gapMs)
                    playNextNarration()
                } else {
                    // Queue empty — wait, then either play queued items or reach out
                    val silenceMs = if (walkSimRunning) 500L else 5000L
                    kotlinx.coroutines.delay(silenceMs)
                    if (narrationQueue.isNotEmpty()) {
                        // Items queued during the silence delay — play them
                        DebugLogger.i("NARR-STATE", "  Queue filled during silence (${narrationQueue.size}) → playing")
                        playNextNarration()
                    } else {
                        // S125: silence-fill body lifted into a helper so the
                        // queue-empty-after-filter path in playNextNarration
                        // can also reach it (overnight test 2026-04-14
                        // showed ~5-minute dead-end silences when the queue
                        // held a single POI that the bearing filter dropped).
                        runSilenceFill()
                    }
                }
            }
        }
    }

    // S125: Newspaper heartbeat — guarantees a 1692 dispatch at least every
    // NEWSPAPER_HEARTBEAT_INTERVAL_MS while in Historical Mode. Overnight
    // 2026-04-14 saw ~35 unique dispatches in 7.5 h against a 202-article
    // corpus because constant POI cancel-interrupts kept TTS state
    // oscillating Speaking↔Speaking so the silence branch never fired.
    // This is a safety net, not the primary dispatch path.
    lifecycleScope.launch {
        DebugLogger.i(
            "NARR-HEARTBEAT",
            "START — interval=${NEWSPAPER_HEARTBEAT_INTERVAL_MS / 1000}s"
        )
        while (isActive) {
            delay(NEWSPAPER_HEARTBEAT_INTERVAL_MS)
            if (!narrationGeofenceManager.isHistoricalMode()) continue
            val now = System.currentTimeMillis()
            val sinceLast = if (lastNewspaperFiredMs == 0L) Long.MAX_VALUE else now - lastNewspaperFiredMs
            if (sinceLast < NEWSPAPER_HEARTBEAT_INTERVAL_MS) {
                DebugLogger.d(
                    "NARR-HEARTBEAT",
                    "SKIP — newspaper fired ${sinceLast / 1000}s ago (< ${NEWSPAPER_HEARTBEAT_INTERVAL_MS / 1000}s)"
                )
                continue
            }
            val h = historicalHeadlineQueue.pollNext()
            if (h == null) {
                DebugLogger.d("NARR-HEARTBEAT", "SKIP — headline queue empty")
                continue
            }
            DebugLogger.i(
                "NARR-HEARTBEAT",
                "FORCE — ${if (sinceLast == Long.MAX_VALUE) "never" else "${sinceLast / 1000}s"} since last newspaper, " +
                    "cancelling POI for ${h.date} ${h.name}"
            )
            tourViewModel.cancelSegmentsWithTag("poi_narration")
            clearNarrationHighlight()
            tourViewModel.speakTaggedNarration(
                "newspaper_1692",
                h.text,
                "Salem 1692 — ${h.date}",
                "en-au-x-auc-local"
            )
            historicalHeadlineQueue.advance()
            lastNewspaperFiredMs = System.currentTimeMillis()
        }
    }

    DebugLogger.i("SalemMainActivity", "initNarrationSystem() complete")
}

/**
 * S125: Silence-fill logic extracted from the narrationState observer so the
 * "queue empty after filter" branch in [playNextNarration] can reach it too.
 *
 * Runs the GPS staleness gate → dwell-cap branch → 2:1 newspaper/POI
 * interleave → progressive dwell-radius reach-out. Every newspaper path
 * stamps [SalemMainActivity.lastNewspaperFiredMs] so the heartbeat knows the
 * queue is alive.
 *
 * Preconditions: caller has already confirmed the narration queue is empty.
 * Callers: narrationState Idle observer (after its silence delay) and
 * playNextNarration() when pickNextFromQueue() returns null.
 */
internal suspend fun SalemMainActivity.runSilenceFill() {
    // ── S110 staleness gate ──
    //    Suppress reach-out entirely when GPS is stale. Without
    //    this, the reach-out logic happily searches for the
    //    "nearest unnarrated POI" against a frozen position
    //    and cycles through every POI in the cluster — the
    //    bug that ran the user through downtown Salem while
    //    they were sitting at home.
    val now = System.currentTimeMillis()
    val ageMs = if (lastFixAtMs == 0L) Long.MAX_VALUE else (now - lastFixAtMs)
    if (ageMs > GPS_STALE_THRESHOLD_MS) {
        DebugLogger.w("NARR-STATE",
            "  REACH-OUT SUPPRESSED: GPS stale " +
            "(age=${if (ageMs == Long.MAX_VALUE) "never" else "${ageMs / 1000}s"}, " +
            "threshold=${GPS_STALE_THRESHOLD_MS / 1000}s)")
        clearNarrationHighlight()  // S112+: nothing more coming → no ring
        return
    }

    // S115: Dwell expansion — if the dwell cap for this
    // session is reached, stop expanding and go quiet
    // until the user walks out of the anchor zone.
    //
    // Phase 9R.0: in Historical Mode, use the silence that
    // would normally follow the dwell cap as a cue to play
    // the next 1692 headline. The cap protects against POI
    // reach-out spamming the same cluster — it should NOT
    // silence the filler queue.
    if (dwellPoisPlayed >= DWELL_MAX_POIS_PER_SESSION) {
        if (narrationGeofenceManager.isHistoricalMode()) {
            val h = historicalHeadlineQueue.pollNext()
            if (h != null) {
                DebugLogger.i(
                    "NARR-HEADLINE",
                    "SILENCE FILL (dwell cap): 1692 headline ${h.index + 1}/${h.total} — ${h.date} ${h.name}"
                )
                clearNarrationHighlight()
                tourViewModel.speakTaggedNarration("newspaper_1692", h.text, "Salem 1692 — ${h.date}", "en-au-x-auc-local")
                historicalHeadlineQueue.advance()
                lastNewspaperFiredMs = System.currentTimeMillis()
                return
            }
        }
        DebugLogger.i(
            "NARR-STATE",
            "  REACH-OUT HELD: dwell cap reached " +
                "($dwellPoisPlayed/$DWELL_MAX_POIS_PER_SESSION POIs this stop) — " +
                "waiting for user to move"
        )
        clearNarrationHighlight()
        return
    }

    // Phase 9R.0: 2:1 POI-reach-out:newspaper ratio. Fire newspaper
    // only every 3rd silence slot; the other two get POI reach-out
    // attempts first (fall through below).
    val fireNewspaperThisSlot =
        narrationGeofenceManager.isHistoricalMode() &&
        newspaperSilenceSlotCounter >= 2
    if (fireNewspaperThisSlot) {
        val h = historicalHeadlineQueue.pollNext()
        if (h != null) {
            newspaperSilenceSlotCounter = 0
            DebugLogger.i(
                "NARR-HEADLINE",
                "SILENCE FILL (2:1 interleave): 1692 headline ${h.index + 1}/${h.total} — ${h.date} ${h.name}"
            )
            clearNarrationHighlight()
            tourViewModel.speakTaggedNarration("newspaper_1692", h.text, "Salem 1692 — ${h.date}", "en-au-x-auc-local")
            historicalHeadlineQueue.advance()
            lastNewspaperFiredMs = System.currentTimeMillis()
            return
        }
    }
    newspaperSilenceSlotCounter++

    // S115: Try the current dwell radius first; if nothing,
    // step the radius up the DWELL ladder and retry until
    // we either find a POI or hit DWELL_RADIUS_MAX_M.
    var attemptRadius = dwellCurrentRadiusM
    var nearest = narrationGeofenceManager.findNearestUnnarrated(attemptRadius)
    while (nearest == null && attemptRadius < DWELL_RADIUS_MAX_M) {
        val nextRadius = when {
            attemptRadius < DWELL_RADIUS_MEDIUM_M -> DWELL_RADIUS_MEDIUM_M
            else -> DWELL_RADIUS_MAX_M
        }
        DebugLogger.i(
            "NARR-DWELL",
            "EXPAND: ${attemptRadius.toInt()}m empty → trying ${nextRadius.toInt()}m"
        )
        attemptRadius = nextRadius
        nearest = narrationGeofenceManager.findNearestUnnarrated(attemptRadius)
    }

    if (nearest != null) {
        dwellCurrentRadiusM = attemptRadius
        dwellPoisPlayed++
        DebugLogger.i(
            "NARR-STATE",
            "  REACH-OUT: ${nearest.point.name} " +
                "(${nearest.distanceM.toInt()}m) " +
                "dwellRadius=${attemptRadius.toInt()}m " +
                "dwellPlayed=$dwellPoisPlayed/$DWELL_MAX_POIS_PER_SESSION " +
                "gpsAge=${ageMs}ms"
        )
        narrationGeofenceManager.triggerReachOut(nearest)
        return
    }

    // Reach-out found nothing — in Historical Mode, use the silence
    // as a cue for a 1692 headline. Runs only when reach-out also
    // found nothing, so any nearby POI still wins.
    val headlineFired = if (narrationGeofenceManager.isHistoricalMode()) {
        val h = historicalHeadlineQueue.pollNext()
        if (h != null) {
            DebugLogger.i(
                "NARR-HEADLINE",
                "SILENCE FILL: 1692 headline ${h.index + 1}/${h.total} — ${h.date} ${h.name}"
            )
            clearNarrationHighlight()
            tourViewModel.speakTaggedNarration(
                "newspaper_1692",
                h.text,
                "Salem 1692 — ${h.date}",
                "en-au-x-auc-local"
            )
            historicalHeadlineQueue.advance()
            lastNewspaperFiredMs = System.currentTimeMillis()
            true
        } else {
            false
        }
    } else {
        false
    }

    if (!headlineFired) {
        DebugLogger.i(
            "NARR-STATE",
            "  Nothing within ${DWELL_RADIUS_MAX_M.toInt()}m reach-out radius " +
                "(gpsAge=${ageMs}ms, dwellPlayed=$dwellPoisPlayed, histMode=${narrationGeofenceManager.isHistoricalMode()})"
        )
        clearNarrationHighlight()  // S112+: nothing more coming → no ring
    }
}

/**
 * S112: Push the latest GPS position into the narration queue subsystem.
 * Called from SalemMainActivityTour.updateTourLocation() on every GPS fix.
 *
 * S112+: Also computes the movement bearing from the prev → current
 * positions when the move is large enough to be meaningful (>= 1m). The
 * bearing drives the "ahead-only" filter at dequeue time.
 *
 * S115: Also maintains the dwell expansion anchor. If the user moves further
 * than DWELL_RESET_DISTANCE_M from the current anchor, the anchor is reset
 * to the new position and the reach-out radius / POI counter drop back to
 * their tight defaults. A cold start (lastUserLat == 0.0) seeds the anchor
 * at the first fix.
 */
internal fun SalemMainActivity.updateNarrationUserPosition(lat: Double, lng: Double) {
    // S115: Stationary freeze — if the motion tracker says the device is
    // physically still, skip the bearing update entirely. GPS jitter that
    // moves the reading 1-5m would otherwise thrash lastMovementBearing
    // between random values and make heading-up rotation spin while the
    // tablet sits on a desk. By not updating here, the bearing goes stale
    // (age > 3s), and the heading-up logic's hybrid source selector falls
    // back to the device orientation sensor — which DOES know the tablet
    // is still and reports a constant azimuth.
    val stationary = motionTracker?.isStationary() == true

    // Update movement bearing if we have a meaningful step AND we're not
    // in stationary freeze mode.
    if (!stationary && (lastUserLat != 0.0 || lastUserLng != 0.0)) {
        val moved = haversineM(lastUserLat, lastUserLng, lat, lng)
        if (moved >= BEARING_UPDATE_MIN_MOVE_M) {
            val prevBearing = lastMovementBearing
            val newBearing = bearingDeg(lastUserLat, lastUserLng, lat, lng)
            lastMovementBearing = newBearing
            lastMovementBearingUpdateMs = System.currentTimeMillis()
            prevUserLat = lastUserLat
            prevUserLng = lastUserLng
            DebugLogger.d(
                "BEARING",
                "moved=${"%.1f".format(moved)}m " +
                    "bearing=${newBearing.toInt()}° " +
                    (if (prevBearing != null) "(prev=${prevBearing.toInt()}°, Δ=${((newBearing - prevBearing + 540) % 360 - 180).toInt()}°)" else "(first)")
            )
        } else {
            DebugLogger.d("BEARING", "still — moved=${"%.2f".format(moved)}m (under ${BEARING_UPDATE_MIN_MOVE_M}m threshold)")
        }
    } else if (stationary) {
        DebugLogger.d(
            "BEARING",
            "frozen — motion tracker says stationary, bearing update suppressed"
        )
    }

    // S115: Dwell anchor maintenance. First fix seeds the anchor. Subsequent
    // fixes reset it if the user has walked beyond DWELL_RESET_DISTANCE_M.
    if (dwellAnchorLat == 0.0 && dwellAnchorLng == 0.0) {
        dwellAnchorLat = lat
        dwellAnchorLng = lng
        dwellCurrentRadiusM = DWELL_RADIUS_TIGHT_M
        dwellPoisPlayed = 0
        DebugLogger.i(
            "NARR-DWELL",
            "ANCHOR SEED — first fix at ${"%.5f".format(lat)},${"%.5f".format(lng)}"
        )
    } else {
        val distFromAnchor = haversineM(dwellAnchorLat, dwellAnchorLng, lat, lng)
        if (distFromAnchor >= DWELL_RESET_DISTANCE_M) {
            if (dwellPoisPlayed > 0 || dwellCurrentRadiusM > DWELL_RADIUS_TIGHT_M) {
                DebugLogger.i(
                    "NARR-DWELL",
                    "RESET: user moved ${distFromAnchor.toInt()}m from anchor — " +
                        "reset radius ${dwellCurrentRadiusM.toInt()}m → ${DWELL_RADIUS_TIGHT_M.toInt()}m, " +
                        "played=$dwellPoisPlayed → 0"
                )
            } else {
                DebugLogger.d(
                    "NARR-DWELL",
                    "anchor advance — moved ${distFromAnchor.toInt()}m, no state to reset"
                )
            }
            dwellAnchorLat = lat
            dwellAnchorLng = lng
            dwellCurrentRadiusM = DWELL_RADIUS_TIGHT_M
            dwellPoisPlayed = 0
            consecutiveNarrations = 0  // S118: reset pacing on movement
        }
    }

    lastUserLat = lat
    lastUserLng = lng
    refreshProximityDockFromQueue()
}

/**
 * S112+: Compute compass bearing from (lat1,lng1) → (lat2,lng2) in degrees.
 * 0 = North, 90 = East, 180 = South, 270 = West. Result normalized to [0, 360).
 */
private fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLng = Math.toRadians(lng2 - lng1)
    val y = kotlin.math.sin(dLng) * kotlin.math.cos(phi2)
    val x = kotlin.math.cos(phi1) * kotlin.math.sin(phi2) -
            kotlin.math.sin(phi1) * kotlin.math.cos(phi2) * kotlin.math.cos(dLng)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

/**
 * S112+: Smallest absolute difference between two compass bearings, in degrees.
 * Result is in [0, 180]. Used to test "is POI ahead of me" — if angleDiff
 * (bearingToPoi, movementBearing) <= AHEAD_CONE_HALF_ANGLE_DEG, the POI is
 * within the front half-circle.
 */
private fun angleDiffDeg(a: Double, b: Double): Double {
    var diff = (a - b + 540.0) % 360.0 - 180.0
    if (diff < 0) diff = -diff
    return diff
}

/**
 * S112+: Returns true if the POI is "ahead" of the user — meaning the bearing
 * from user → POI is within the AHEAD_CONE_HALF_ANGLE_DEG cone of the user's
 * current movement direction. If we don't have a movement bearing yet (cold
 * start, stationary forever), all POIs pass.
 *
 * S115: Near-zone escape hatch. If the POI is within NEAR_ZONE_OVERRIDE_M of
 * the user, the ahead filter is skipped. At short distance the user is
 * effectively AT the POI and directional gating becomes noise driven by GPS
 * jitter when the user slows or stops. Field testing showed ChezCasa at 9m,
 * Historic Salem, Inc. at 18m, and several others dropped as "behind user"
 * when the user was standing right next to them.
 */
private fun isPoiAhead(poiLat: Double, poiLng: Double): Boolean {
    val distM = haversineM(lastUserLat, lastUserLng, poiLat, poiLng)
    if (distM <= NEAR_ZONE_OVERRIDE_M) return true
    val moveBearing = lastMovementBearing ?: return true
    val poiBearing = bearingDeg(lastUserLat, lastUserLng, poiLat, poiLng)
    return angleDiffDeg(poiBearing, moveBearing) <= AHEAD_CONE_HALF_ANGLE_DEG
}

/**
 * Add a narration point to the queue.
 * @param jumpToFront if true (user tapped), interrupt current and play immediately
 *
 * S112: Insertion is now order-agnostic — selection happens at dequeue time
 * where the queue is filtered by distance to the user (>50m → drop) and sorted
 * by tier (paid > historic > attraction > rest), with closest-within-tier as
 * the tiebreaker. The previous adPriority/priority insertion sort is removed
 * because the new tier system supersedes it.
 *
 * jumpToFront still works: user tap on the dock plays immediately, bypassing
 * the tier filter (explicit user intent overrides auto-prioritization).
 */
internal fun SalemMainActivity.enqueueNarration(point: SalemPoi, jumpToFront: Boolean) {
    val tier = NarrationTierClassifier.classify(point)
    // Phase 9R.0: A new POI interrupts whatever is currently playing:
    //   - Newspaper dispatches (tag "newspaper_1692") — ALWAYS interrupt
    //   - Prior-POI narration (tag "poi_narration") — only interrupt after
    //     a 10 s MIN HOLD on the current POI. Without this hold, clustered
    //     ENTRY events at walk start (several POIs within 40m of the first
    //     GPS fix all fire simultaneously) cascade-cancel each other and
    //     no audio ever survives long enough to reach the speaker.
    val now = System.currentTimeMillis()
    val priorPoiStartedAgoMs = if (lastPoiNarrationStartMs > 0L) now - lastPoiNarrationStartMs else Long.MAX_VALUE
    val priorPoiMinHoldMs = 10_000L
    val wasInterruptingPriorPoi = currentNarration != null && currentNarration?.id != point.id

    // Newspapers always yield to POIs.
    tourViewModel.cancelSegmentsWithTag("newspaper_1692")

    if (wasInterruptingPriorPoi && priorPoiStartedAgoMs < priorPoiMinHoldMs) {
        DebugLogger.i("NARR-QUEUE",
            "DROP ENTRY for ${point.name}: prior POI '${currentNarration?.name}' still in min-hold " +
                "(${priorPoiStartedAgoMs}ms / ${priorPoiMinHoldMs}ms) — queue instead")
        // Fall through to the append path below — the new POI becomes a
        // candidate for the next silence/idle cycle but does NOT kill the
        // just-started current POI.
    } else {
        // Either no prior POI playing, or min-hold elapsed — interrupt cleanly.
        tourViewModel.cancelSegmentsWithTag("poi_narration")
        if (wasInterruptingPriorPoi) {
            DebugLogger.i("NARR-QUEUE",
                "Interrupting prior POI '${currentNarration?.name}' for new POI '${point.name}' " +
                    "(prior held for ${priorPoiStartedAgoMs}ms)")
            currentNarration = null
        }
    }
    // Stamp the start so future POIs know when min-hold expires.
    lastPoiNarrationStartMs = now
    DebugLogger.i("NARR-QUEUE", "enqueueNarration: ${point.name} tier=$tier ad=${point.adPriority} jumpToFront=$jumpToFront currentNarration=${currentNarration?.name} queueSize=${narrationQueue.size}")
    // Don't add duplicates
    if (point.id == currentNarration?.id) {
        DebugLogger.i("NARR-QUEUE", "SKIP duplicate (current): ${point.name}")
        return
    }
    if (narrationQueue.any { it.id == point.id }) {
        DebugLogger.i("NARR-QUEUE", "SKIP duplicate (queued): ${point.name}")
        return
    }

    if (jumpToFront || currentNarration == null) {
        // Play immediately (user tapped or nothing playing).
        // S118: Play the point DIRECTLY — do not route through
        // pickNextFromQueue() which applies bearing/distance filters
        // that can drop a just-triggered ENTRY POI the walk-sim has
        // already moved past. The queue filter is for competitive
        // selection among multiple candidates, not for blocking the
        // only candidate that just fired a geofence event.
        DebugLogger.i("NARR-QUEUE", "PLAY DIRECT: ${point.name} (jumpToFront=$jumpToFront, currentNull=${currentNarration == null})")
        currentNarration = point
        showNarrationSheet(point)
        showNarrationHighlight(point)
        val rawText = narrationGeofenceManager.getNarrationForPass(point)
            ?: point.shortNarration ?: point.description
        val voiceId = com.example.wickedsalemwitchcitytour.tour.CategoryVoiceMap.voiceForCategory(point.category)
        if (rawText != null) {
            narrationGeofenceManager.recordVisit(point.id)
            DebugLogger.i("NARR-PLAY", "DIRECT PLAY: ${point.name} voice=$voiceId")
            // Phase 9R.0: chapter break via two separate TTS segments —
            // "You are at {POI}." plays first, the body plays second. Tagged
            // "poi_narration" so a subsequent POI ENTRY can interrupt the
            // prior POI's read once the walker has moved on.
            tourViewModel.speakTaggedNarration("poi_narration", "You are at ${point.name}.", point.name, voiceId)
            tourViewModel.speakTaggedNarration("poi_narration", rawText, point.name, voiceId)
        } else {
            DebugLogger.w("NARR-PLAY", "DIRECT PLAY: ${point.name} — no narration text, skipping")
        }
        updateQueueIndicator()
        refreshProximityDockFromQueue()
    } else {
        // Just append. The dequeue logic will pick the correct order at play time.
        narrationQueue.addLast(point)
        updateQueueIndicator()
        refreshProximityDockFromQueue()
        DebugLogger.i("SalemMainActivity", "Queued: ${point.name} [tier=$tier ad=${point.adPriority}] (${narrationQueue.size} in queue)")
    }
}

/**
 * S112: Pick the next SalemPoi to play, applying the dequeue rules:
 *   1. Drop everything farther than 50m from the current user position.
 *   2. Find the highest non-empty tier (PAID → HISTORIC → ATTRACTION → REST).
 *   3. Within that tier, return the closest POI to the user.
 *
 * The picked entry is REMOVED from the queue. Returns null if the queue is
 * empty after filtering.
 *
 * Falls back to pure FIFO if no GPS fix has arrived yet (lat=lng=0.0) — this
 * preserves cold-start behavior where the queue might receive a jumpToFront
 * tap before any GPS is available.
 */
private fun SalemMainActivity.pickNextFromQueue(): SalemPoi? {
    if (narrationQueue.isEmpty()) return null

    // Cold start fallback — no GPS yet, return FIFO order
    if (lastUserLat == 0.0 && lastUserLng == 0.0) {
        return narrationQueue.pollFirst()  // pollFirst returns null if empty
    }

    // S112+: Three-level density cascade. Count POIs within each radius and
    // pick the tightest level whose threshold is exceeded.
    val nearbyStandard = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= STANDARD_DISCARD_RADIUS_M
    }
    val nearbyHd = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= HIGH_DENSITY_DISCARD_RADIUS_M
    }
    val discardRadius = when {
        nearbyHd >= SUPER_HIGH_DENSITY_THRESHOLD -> SUPER_HIGH_DENSITY_DISCARD_RADIUS_M
        nearbyStandard >= HIGH_DENSITY_THRESHOLD -> HIGH_DENSITY_DISCARD_RADIUS_M
        else -> STANDARD_DISCARD_RADIUS_M
    }
    val mode = when (discardRadius) {
        SUPER_HIGH_DENSITY_DISCARD_RADIUS_M -> "SUPER-HD"
        HIGH_DENSITY_DISCARD_RADIUS_M -> "HD"
        else -> "STANDARD"
    }
    if (discardRadius < STANDARD_DISCARD_RADIUS_M) {
        DebugLogger.i("NARR-QUEUE",
            "$mode MODE: $nearbyStandard POIs in ${STANDARD_DISCARD_RADIUS_M.toInt()}m, $nearbyHd in ${HIGH_DENSITY_DISCARD_RADIUS_M.toInt()}m → discard=${discardRadius.toInt()}m")
    }

    // Step 1: drop anything > discardRadius (out of range) OR behind the user
    val survivors = mutableListOf<SalemPoi>()
    val droppedNames = mutableListOf<String>()
    val droppedBehind = mutableListOf<String>()
    for (point in narrationQueue) {
        val dist = haversineM(lastUserLat, lastUserLng, point.lat, point.lng)
        if (dist > discardRadius) {
            droppedNames.add("${point.name}(${dist.toInt()}m)")
            continue
        }
        if (!isPoiAhead(point.lat, point.lng)) {
            droppedBehind.add("${point.name}(${dist.toInt()}m)")
            continue
        }
        survivors.add(point)
    }
    if (droppedNames.isNotEmpty()) {
        DebugLogger.i("NARR-QUEUE",
            "Dequeue dropped ${droppedNames.size} stale (>${discardRadius.toInt()}m): " +
            droppedNames.take(5).joinToString(", ") +
            if (droppedNames.size > 5) " +${droppedNames.size - 5} more" else ""
        )
    }
    if (droppedBehind.isNotEmpty()) {
        DebugLogger.i("NARR-QUEUE",
            "Dequeue dropped ${droppedBehind.size} behind-user: " +
            droppedBehind.take(5).joinToString(", ") +
            if (droppedBehind.size > 5) " +${droppedBehind.size - 5} more" else ""
        )
    }

    // Rebuild the queue with only the survivors (keeps the queue clean for next iteration)
    narrationQueue.clear()
    narrationQueue.addAll(survivors)

    if (survivors.isEmpty()) return null

    // Step 2: find highest non-empty tier; Step 3: closest within that tier
    for (tier in NarrationTier.values()) {
        val candidates = survivors.filter { NarrationTierClassifier.classify(it) == tier }
        if (candidates.isEmpty()) continue
        val winner = candidates.minByOrNull { haversineM(lastUserLat, lastUserLng, it.lat, it.lng) }
            ?: continue
        narrationQueue.remove(winner)
        DebugLogger.i("NARR-QUEUE",
            "Picked ${winner.name} tier=$tier dist=${haversineM(lastUserLat, lastUserLng, winner.lat, winner.lng).toInt()}m " +
            "(${candidates.size} in tier, ${survivors.size} total survivors)")
        return winner
    }
    return null
}

/** Play the next narration point from the queue (S112: tier+distance dequeue). S125: suspend so the queue-filter dead-end can await runSilenceFill(). */
internal suspend fun SalemMainActivity.playNextNarration() {
    val point = pickNextFromQueue()
    if (point == null) {
        // S125: queue-filter dead-end. Before this change we returned
        // silently here, which meant a queue holding a single POI that
        // the bearing filter dropped as "behind user" would stall the
        // whole audio stream until a new ENTRY event arrived — seen as
        // multi-minute silence windows in the 2026-04-14 overnight test.
        // Fall through to the same silence-fill logic the Idle observer
        // uses so the reach-out/newspaper machinery still runs.
        DebugLogger.i("NARR-PLAY", "playNextNarration: queue empty after filter → silence-fill")
        clearNarrationHighlight()  // S112+: nothing playing → no ring
        refreshProximityDockFromQueue()
        runSilenceFill()
        return
    }
    currentNarration = point
    DebugLogger.i("NARR-PLAY", "playNextNarration: ${point.name} (${narrationQueue.size} remaining in queue)")
    showNarrationSheet(point)
    showNarrationHighlight(point)  // S112+: glowing ring on the POI being announced

    // Auto-play TTS — select narration pass based on lifetime visit count
    val rawText = narrationGeofenceManager.getNarrationForPass(point)
        ?: point.shortNarration ?: point.description

    // Resolve voice: POI override > category default
    val voiceId = com.example.wickedsalemwitchcitytour.tour.CategoryVoiceMap.voiceForCategory(point.category)
    DebugLogger.i("NARR-PLAY", "  text=${if (rawText != null) "${rawText.take(60)}..." else "NULL"} voice=$voiceId narrationAutoPlay=$narrationAutoPlay")
    if (rawText != null && narrationAutoPlay) {
        DebugLogger.i("NARR-PLAY", "  → calling tourViewModel.speakNarration() voice=$voiceId")
        // Phase 9R.0: chapter break via two separate TTS segments. Tagged
        // "poi_narration" so the next POI ENTRY can cancel-interrupt this
        // POI's read once the walker has moved past its CPA.
        tourViewModel.speakTaggedNarration("poi_narration", "You are at ${point.name}.", point.name, voiceId)
        tourViewModel.speakTaggedNarration("poi_narration", rawText, point.name, voiceId)
    } else {
        DebugLogger.w("NARR-PLAY", "  → NOT speaking: text=${rawText != null} autoPlay=$narrationAutoPlay")
    }
    // Record this visit for next-pass selection on future days
    narrationGeofenceManager.recordVisit(point.id)
    refreshProximityDockFromQueue()
}

/**
 * S112: Refresh the proximity dock from the current queue snapshot.
 * Computes the same filter+sort the dequeue logic uses, then hands the result
 * to the dock so the visible dock order matches what the user is about to hear.
 *
 * Called from: every enqueue, every dequeue, every GPS position update.
 */
internal fun SalemMainActivity.refreshProximityDockFromQueue() {
    val dock = proximityDock ?: return

    if (narrationQueue.isEmpty()) {
        dock.updateFromQueue(emptyList(), lastUserLat, lastUserLng)
        return
    }

    if (lastUserLat == 0.0 && lastUserLng == 0.0) {
        // No GPS yet — show queue in insertion order, no filter
        dock.updateFromQueue(narrationQueue.toList(), 0.0, 0.0)
        return
    }

    // S112+: Same three-level density cascade the dequeue uses, plus the
    // bearing-based "ahead" filter, so the dock shows exactly the set of POIs
    // the dequeue is actually choosing from.
    val nearbyStandard = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= STANDARD_DISCARD_RADIUS_M
    }
    val nearbyHd = narrationQueue.count {
        haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= HIGH_DENSITY_DISCARD_RADIUS_M
    }
    val discardRadius = when {
        nearbyHd >= SUPER_HIGH_DENSITY_THRESHOLD -> SUPER_HIGH_DENSITY_DISCARD_RADIUS_M
        nearbyStandard >= HIGH_DENSITY_THRESHOLD -> HIGH_DENSITY_DISCARD_RADIUS_M
        else -> STANDARD_DISCARD_RADIUS_M
    }

    // Same filter+sort the dequeue would apply: in-range, ahead, tier order, closest-in-tier
    val sorted = narrationQueue
        .filter { haversineM(lastUserLat, lastUserLng, it.lat, it.lng) <= discardRadius }
        .filter { isPoiAhead(it.lat, it.lng) }
        .sortedWith(compareBy(
            { NarrationTierClassifier.classify(it).ordinal },
            { haversineM(lastUserLat, lastUserLng, it.lat, it.lng) }
        ))

    dock.updateFromQueue(sorted, lastUserLat, lastUserLng)
}

/**
 * Show the narration bottom sheet for a narration point.
 */
internal fun SalemMainActivity.showNarrationSheet(point: SalemPoi) {
    val sheet = binding.root.findViewById<View>(R.id.narrationSheet) ?: return

    // Populate content
    sheet.findViewById<TextView>(R.id.narrationTitle)?.text = point.name
    sheet.findViewById<TextView>(R.id.categoryLabel)?.text =
        point.category.replace('_', ' ').split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    val narrationText = narrationGeofenceManager.getNarrationForPass(point)
        ?: point.shortNarration ?: point.description ?: "Narration coming soon."
    sheet.findViewById<TextView>(R.id.narrationText)?.text = narrationText

    // Distance
    val loc = viewModel.currentLocation.value?.point
    if (loc != null) {
        val dist = haversineM(loc.latitude, loc.longitude, point.lat, point.lng)
        sheet.findViewById<TextView>(R.id.distanceLabel)?.text = formatDistanceM(dist)
    }

    // Queue indicator
    updateQueueIndicator()

    // Show the sheet
    sheet.visibility = View.VISIBLE

    // S113 iter-3: Tapping anywhere on the bottom banner (except the
    // Skip/Listen/More/Walk/Fence button row which has its own
    // handlers) opens the full POI Detail Sheet for the currently-
    // narrated point. Without this, the banner was dead-clickable —
    // operator tapped "the first announcement" and nothing happened.
    //
    // We attach the listener to the sheet root; individual buttons
    // further down already have their own setOnClickListener calls
    // which will consume touches on those button regions before the
    // root sees them, so this only fires on empty-area taps.
    sheet.setOnClickListener {
        DebugLogger.i(
            "SalemMainActivity",
            "BANNER TAP id=${point.id} name=${point.name} → showing PoiDetailSheet"
        )
        PoiDetailSheet.show(point, supportFragmentManager)
    }
    // Also make the title and narration text regions explicitly
    // clickable and route to the same handler — these are the most
    // likely tap targets and may not receive the root's click on
    // certain Android versions when they have their own clickable
    // parent chain.
    sheet.findViewById<TextView>(R.id.narrationTitle)?.apply {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            DebugLogger.i(
                "SalemMainActivity",
                "BANNER TITLE TAP id=${point.id} name=${point.name} → showing PoiDetailSheet"
            )
            PoiDetailSheet.show(point, supportFragmentManager)
        }
    }
    sheet.findViewById<TextView>(R.id.narrationText)?.apply {
        isClickable = true
        isFocusable = true
        setOnClickListener {
            DebugLogger.i(
                "SalemMainActivity",
                "BANNER TEXT TAP id=${point.id} name=${point.name} → showing PoiDetailSheet"
            )
            PoiDetailSheet.show(point, supportFragmentManager)
        }
    }

    // Action buttons
    sheet.findViewById<View>(R.id.btnSkip)?.setOnClickListener {
        DebugLogger.i("NARR-SKIP", "Skip tapped — stopping TTS, clearing current narration")
        tourViewModel.stopNarration()
        currentNarration = null
        consecutiveNarrations = 0  // reset pacing so next POI plays promptly
        clearNarrationHighlight()
        sheet.visibility = View.GONE
        // Force map redraw after sheet hides — the layout change can confuse overlay positions
        binding.mapView.postDelayed({ binding.mapView.invalidate() }, 100)
    }
    sheet.findViewById<View>(R.id.btnPlayPause)?.setOnClickListener {
        val text = point.longNarration ?: point.shortNarration ?: point.description
        if (text != null) {
            tourViewModel.speakNarration(text, point.name)
        }
    }
    sheet.findViewById<View>(R.id.btnMoreInfo)?.setOnClickListener {
        val longText = point.longNarration ?: point.description ?: "No additional information."
        sheet.findViewById<TextView>(R.id.narrationText)?.text = longText
    }
    sheet.findViewById<View>(R.id.btnDirections)?.setOnClickListener {
        Toast.makeText(this, "Directions to ${point.name}", Toast.LENGTH_SHORT).show()
    }
    sheet.findViewById<View>(R.id.btnGeofence)?.setOnClickListener {
        toggleGeofenceOverlay(point)
    }
}

private fun SalemMainActivity.updateQueueIndicator() {
    val indicator = binding.root.findViewById<TextView>(R.id.queueIndicator)
    if (narrationQueue.isNotEmpty()) {
        indicator?.text = "${narrationQueue.size} more nearby"
        indicator?.visibility = View.VISIBLE
    } else {
        indicator?.visibility = View.GONE
    }
}

private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2).let { it * it }
    return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
}

private fun formatDistanceM(meters: Double): String = when {
    meters < 100 -> "${meters.toInt()}m"
    meters < 1000 -> "${(meters / 10).toInt() * 10}m"
    else -> "${"%.1f".format(meters / 1000)}km"
}

// ── Geofence overlay ────────────────────────────────────────────────────

/** Currently displayed geofence overlay (null = hidden) */
private var geofenceOverlay: Polygon? = null
private var geofenceOverlayPointId: String? = null

/**
 * Toggle the geofence circle for a narration point on the map.
 * Shows entry radius (solid) and approach radius (dashed outline).
 * Tapping again or tapping for a different POI replaces it.
 */
internal fun SalemMainActivity.toggleGeofenceOverlay(point: SalemPoi) {
    // Remove existing overlay
    geofenceOverlay?.let { binding.mapView.overlays.remove(it) }

    // If same POI tapped again, just hide
    if (geofenceOverlayPointId == point.id) {
        geofenceOverlay = null
        geofenceOverlayPointId = null
        binding.mapView.invalidate()
        Toast.makeText(this, "Geofence hidden", Toast.LENGTH_SHORT).show()
        return
    }

    val center = GeoPoint(point.lat, point.lng)
    val baseRadius = point.geofenceRadiusM.toDouble()
    // S112+: walk-sim no longer inflates the radius — same numbers as real GPS.
    val entryRadius = baseRadius
    val approachRadius = entryRadius * 2.0

    // Entry zone — filled circle
    val entryCircle = Polygon().apply {
        points = Polygon.pointsAsCircle(center, entryRadius)
        fillPaint.color = Color.argb(50, 76, 175, 80)   // green fill 20%
        outlinePaint.color = Color.argb(180, 76, 175, 80) // green outline
        outlinePaint.strokeWidth = 3f
        title = "${point.name} — entry ${entryRadius.toInt()}m"
    }

    // Approach zone — outline only
    val approachCircle = Polygon().apply {
        points = Polygon.pointsAsCircle(center, approachRadius)
        fillPaint.color = Color.argb(20, 255, 193, 7)    // amber fill 8%
        outlinePaint.color = Color.argb(120, 255, 193, 7) // amber outline
        outlinePaint.strokeWidth = 2f
        title = "${point.name} — approach ${approachRadius.toInt()}m"
    }

    // Add approach first (behind), then entry (on top)
    binding.mapView.overlays.add(approachCircle)
    binding.mapView.overlays.add(entryCircle)

    // Track for removal — store entry circle as the "main" overlay, remove both later
    geofenceOverlay = entryCircle
    geofenceOverlayPointId = point.id

    // Center map on the POI
    binding.mapView.controller.animateTo(center)

    binding.mapView.invalidate()
    Toast.makeText(this, "Geofence: entry=${entryRadius.toInt()}m approach=${approachRadius.toInt()}m", Toast.LENGTH_SHORT).show()

    // Store approach circle for cleanup too
    entryCircle.relatedObject = approachCircle
}

/** Remove any active geofence overlay (call when narration sheet hides) */
internal fun SalemMainActivity.clearGeofenceOverlay() {
    geofenceOverlay?.let { entry ->
        binding.mapView.overlays.remove(entry)
        (entry.relatedObject as? Polygon)?.let { binding.mapView.overlays.remove(it) }
    }
    geofenceOverlay = null
    geofenceOverlayPointId = null
    binding.mapView.invalidate()
}

// ─── S112+: Glowing highlight ring around the currently-narrated POI ─────────

/**
 * Draw a glowing gold ring at the POI being narrated. Two concentric circles
 * (inner solid, outer faded) plus a coroutine that pulses the outer ring's
 * alpha so it visibly "breathes" while the narration plays.
 *
 * Auto-replaces any prior highlight (so successive narrations move the ring
 * cleanly from one POI to the next without manual cleanup at the call site).
 */
internal fun SalemMainActivity.showNarrationHighlight(point: SalemPoi) {
    // Tear down any existing highlight first
    clearNarrationHighlight()

    val center = GeoPoint(point.lat, point.lng)

    // Inner ring — bright solid gold, marks the POI center.
    //
    // S113 iter-3 fix: The ring must NOT intercept taps meant for the POI
    // marker underneath. osmdroid's default Polygon.onSingleTapConfirmed
    // will consume the tap if title is set (opens its info window). We:
    //   (1) leave title null so there is no info window to open
    //   (2) install a no-op click listener that returns false, so even if
    //       a future osmdroid version adds default tap behavior, the tap
    //       falls through to the marker
    //   (3) insert at overlay index 0 (bottom), below the markers, so
    //       osmdroid's top-down tap dispatch hits the marker first
    val inner = Polygon().apply {
        points = Polygon.pointsAsCircle(center, HIGHLIGHT_INNER_RADIUS_M)
        fillPaint.color = Color.argb(80, 255, 215, 0)     // gold @ ~31% alpha
        outlinePaint.color = Color.argb(255, 255, 215, 0) // gold solid stroke
        outlinePaint.strokeWidth = 6f
        // title intentionally unset — do not intercept taps
        setOnClickListener { _, _, _ -> false }
    }

    // Outer ring — faded gold, will pulse via the animation job
    val outer = Polygon().apply {
        points = Polygon.pointsAsCircle(center, HIGHLIGHT_OUTER_RADIUS_M)
        fillPaint.color = Color.argb(40, 255, 215, 0)     // gold @ ~16% alpha (animated)
        outlinePaint.color = Color.argb(180, 255, 215, 0) // gold faded stroke
        outlinePaint.strokeWidth = 3f
        // title intentionally unset — do not intercept taps
        setOnClickListener { _, _, _ -> false }
    }

    // Add at index 0 (bottom of overlay stack) so markers are drawn and
    // tap-dispatched on top. Visual result: ring halo shows around the
    // marker icon, marker remains tappable.
    binding.mapView.overlays.add(0, outer)
    binding.mapView.overlays.add(1, inner)
    narrationHighlightInner = inner
    narrationHighlightOuter = outer
    binding.mapView.invalidate()
    DebugLogger.i("SalemMainActivity",
        "HIGHLIGHT drawn at index 0-1 for ${point.name} (below markers)")

    // Pulse the outer ring's alpha while narration is active.
    // 6-step cycle = 1.8 sec per pulse, alpha cycles 30 → 110 → 30.
    narrationHighlightAnimJob = lifecycleScope.launch {
        var phase = 0
        while (isActive) {
            val targetAlpha = when (phase) {
                0 -> 30
                1 -> 55
                2 -> 80
                3 -> 110
                4 -> 80
                else -> 55  // phase 5
            }
            val outerNow = narrationHighlightOuter ?: break
            outerNow.fillPaint.alpha = targetAlpha
            binding.mapView.postInvalidate()
            delay(300L)
            phase = (phase + 1) % 6
        }
    }
}

/**
 * Remove the narration highlight ring (both circles + cancel pulse animation).
 * Safe to call when no highlight is active.
 */
internal fun SalemMainActivity.clearNarrationHighlight() {
    narrationHighlightAnimJob?.cancel()
    narrationHighlightAnimJob = null
    narrationHighlightInner?.let { binding.mapView.overlays.remove(it) }
    narrationHighlightOuter?.let { binding.mapView.overlays.remove(it) }
    narrationHighlightInner = null
    narrationHighlightOuter = null
    binding.mapView.invalidate()
}
