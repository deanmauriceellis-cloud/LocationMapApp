/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import android.content.SharedPreferences
import com.example.wickedsalemwitchcitytour.audio.AudioControl
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.*

/**
 * Manages proximity-based geofence checks for all narration points in downtown Salem.
 * Unlike TourGeofenceManager (linear tour stops), this checks ALL loaded narration points
 * on every GPS update — the city narrates itself as you walk.
 *
 * Emits NarrationGeofenceEvent when the user approaches or enters a narration point's zone.
 * Also provides a sorted list of nearby points for the proximity dock.
 *
 * Phase 9T: Salem Walking Tour Restructure.
 *
 * S110: Promoted to Hilt @Singleton so the in-memory dedup state (`narratedAt`,
 * `cooldowns`) survives Activity recreation (orientation change, configuration
 * change). Previously a fresh instance was constructed in `initNarrationSystem()`
 * on every recreate, which wiped the dedup set and caused already-narrated POIs
 * to retrigger. State still does not survive process death — that needs a
 * SharedPreferences mirror, deferred.
 */
@Singleton
class NarrationGeofenceManager @Inject constructor(
    @ApplicationContext context: Context
) {

    private var points: List<SalemPoi> = emptyList()

    /** Walk sim mode: expands entry radius and tightens reach-out for continuous narration */
    var walkSimMode: Boolean = false

    /**
     * Phase 9R.0 — Historical Mode.
     *
     * When ON, the manager pretends modern POIs don't exist:
     *   - `checkPosition()` only considers POIs that either
     *       (a) have their id in [historicalAllowedIds] (tour stops, always allowed), OR
     *       (b) have a populated `historicalNote` (SI-generated tour-guide narration), OR
     *       (c) have a populated `historicalPeriod` field
     *     Every other POI is skipped entirely — no ENTRY, no APPROACH, no nearby-dock entry.
     *   - [getNarrationForPass] prefers `historicalNote` over `shortNarration` so the
     *     tour-guide voice plays instead of the modern map-view blurb.
     *
     * Set via [setHistoricalMode]. Safe to flip mid-session; already-narrated POIs
     * stay in the dedup map so they don't re-trigger.
     */
    private var historicalMode: Boolean = false
    private var historicalAllowedIds: Set<String> = emptySet()

    /**
     * Enable/disable historical mode. Provide the tour-stop POI IDs so they
     * always narrate even if their per-POI `historicalNote`/`historicalPeriod`
     * fields happen to be null.
     */
    fun setHistoricalMode(enabled: Boolean, allowedIds: Set<String> = emptySet()) {
        historicalMode = enabled
        historicalAllowedIds = if (enabled) allowedIds else emptySet()
        com.example.locationmapapp.util.DebugLogger.i(
            "NARR-GEO",
            "historicalMode=$enabled (allowedIds=${allowedIds.size})"
        )
    }

    fun isHistoricalMode(): Boolean = historicalMode

    /**
     * Is this POI categorically historical? Single source of truth for the
     * Historical Mode visibility + narration gates — checks ONLY per-POI
     * category + metadata, never looks at `historical_note` content.
     *
     * The reason to ignore `historical_note` here: content is populated by a
     * bulk sync from SalemIntelligence that pipes SI's general-purpose
     * `medium_narration` into `historical_note` as a fallback. That means
     * modern shops, restaurants, and offices end up with `historical_note`
     * populated too (1,100+ POIs including 315 SHOPPING, 188 OFFICES, 174
     * FOOD_DRINK, 131 HEALTHCARE). Relying on "has historical_note" as the
     * filter signal would leak those modern categories straight into the
     * immersive Historical track — the exact opposite of the intent.
     *
     * A POI is categorically historical when ANY of:
     *   1. category = HISTORICAL_BUILDINGS (landmarks, statues, monuments,
     *      historic houses, museums, cemeteries, memorials)
     *   2. `historical_period` is populated (curated "this is historic" tag)
     *   3. category is amusement-like (ENTERTAINMENT / MUSEUM / PARKS_REC /
     *      WORSHIP / etc.) AND `year_established` ≤ 1860 — gives us
     *      Hamilton Hall 1805, Ropes Mansion 1727, historic churches, etc.
     *      The 1860 cutoff matches the S134 HISTORICAL_BUILDINGS reclassification
     *      boundary. Modern venues (Vampfangs 1993, etc.) are excluded.
     *      Modern BCS shops sit in SHOPPING/FOOD_DRINK so they miss this.
     */
    private fun isCategoricallyHistorical(point: SalemPoi): Boolean {
        val cat = point.category.uppercase()
        if (cat == "HISTORICAL_BUILDINGS") return true
        if (!point.historicalPeriod.isNullOrBlank()) return true
        val year = point.yearEstablished
        if (cat in AMUSEMENT_LIKE_CATEGORIES && year != null && year <= 1860) return true
        return false
    }

    /**
     * Does this POI qualify for NARRATION under historical mode?
     *
     * Strict rule:
     *   - Tour stops always narrate (whitelisted by TourEngine)
     *   - Otherwise, must be categorically historical AND have a note to read
     */
    private fun isHistoricalQualified(point: SalemPoi): Boolean {
        if (!historicalMode) return true
        if (point.id in historicalAllowedIds) return true
        if (point.historicalNote.isNullOrBlank()) return false
        return isCategoricallyHistorical(point)
    }

    /**
     * Does this POI qualify for MAP VISIBILITY under historical mode?
     *
     * Looser than narration — the map shows landmarks even while their
     * tour-guide content is still being generated. Tour stops always show.
     * Everything else must be categorically historical. Explicitly does NOT
     * gate on `historical_note` (see [isCategoricallyHistorical] for why).
     */
    fun isVisibleInHistoricalMode(point: SalemPoi): Boolean {
        if (!historicalMode) return true
        if (point.id in historicalAllowedIds) return true
        return isCategoricallyHistorical(point)
    }

    /**
     * POI ID → timestamp when narrated (expires after REPEAT_WINDOW_MS).
     *
     * S125: Mirrored to SharedPreferences so the dedup window survives
     * process death. Overnight test 2026-04-14 showed 18 PID forks across
     * ~7.5 h (APK installs, ANR restarts, etc.); each previously wiped
     * this map, so POIs re-narrated within the 1-hour window after every
     * restart. Writes are apply() (async, minimal I/O overhead) and the
     * map is loaded at construction, then pruned of expired entries.
     */
    private val narratedAt = mutableMapOf<String, Long>()

    /**
     * How long before a POI can repeat (ms).
     *
     * S112: Reduced from 12 hours → 1 hour per operator direction.
     * Rationale: 12h was too long for typical session use; reducing to 1h
     * lets POIs repeat on a return-loop drive or after a short break, which
     * is the common case for sightseeing.
     *
     * S125: Now persistent across process death via narratedAtPrefs.
     */
    private val REPEAT_WINDOW_MS = 1 * 60 * 60 * 1000L  // 1 hour

    /** Cooldown tracker: POI ID → last trigger timestamp */
    private val cooldowns = mutableMapOf<String, Long>()

    /** Persistent visit counts per POI (survives app restarts, drives multi-pass narration) */
    private val visitPrefs: SharedPreferences =
        context.getSharedPreferences("narration_visits", Context.MODE_PRIVATE)

    /** S125: Persistent mirror of [narratedAt] so the 1-hour dedup window survives PID forks. */
    private val narratedAtPrefs: SharedPreferences =
        context.getSharedPreferences("narration_narrated_at", Context.MODE_PRIVATE)

    init {
        // S125: Hydrate narratedAt from disk, dropping anything older than
        // REPEAT_WINDOW_MS. Cheap — typical walk narrates 10-100 POIs in an
        // hour so the stored map is small.
        val now = System.currentTimeMillis()
        var loaded = 0
        var dropped = 0
        for ((id, any) in narratedAtPrefs.all) {
            val ts = (any as? Long) ?: continue
            if (now - ts < REPEAT_WINDOW_MS) {
                narratedAt[id] = ts
                loaded++
            } else {
                dropped++
            }
        }
        if (dropped > 0) {
            // Purge expired keys from disk
            val editor = narratedAtPrefs.edit()
            for ((id, any) in narratedAtPrefs.all) {
                val ts = (any as? Long) ?: continue
                if (now - ts >= REPEAT_WINDOW_MS) editor.remove(id)
            }
            editor.apply()
        }
        com.example.locationmapapp.util.DebugLogger.i(
            "NARR-GEO",
            "narratedAt hydrated: loaded=$loaded, dropped=$dropped (window=${REPEAT_WINDOW_MS / 60000}m)"
        )
    }

    /** S125: Write one narratedAt entry to the persistent mirror. */
    private fun persistNarrated(pointId: String, ts: Long) {
        narratedAtPrefs.edit().putLong(pointId, ts).apply()
    }

    /** Events emitted when a narration point is approached or entered */
    private val _events = MutableSharedFlow<NarrationGeofenceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NarrationGeofenceEvent> = _events

    /** Current nearby points sorted by distance (for proximity dock) */
    private var _nearby: List<NearbyPoint> = emptyList()
    val nearby: List<NearbyPoint> get() = _nearby

    /** Timestamp of the last ENTRY event emission (normal or reach-out) */
    private var lastEntryEmitTime = 0L

    /** Last known user position (for reach-out queries from outside checkPosition) */
    private var lastLat = 0.0
    private var lastLng = 0.0

    companion object {
        /** Minimum time between repeated triggers for the same POI (ms) */
        private const val COOLDOWN_MS = 120_000L  // 2 minutes

        /** Approach multiplier: trigger APPROACH at this multiple of geofence radius */
        private const val APPROACH_MULTIPLIER = 2.0

        /** Maximum narration points to include in nearby list */
        private const val MAX_NEARBY = 15

        /** Maximum distance for nearby list (meters) */
        private const val NEARBY_RADIUS_M = 300.0

        /** Minimum time inside geofence before triggering (prevents GPS drift false positives) */
        private const val DWELL_MS = 3_000L

        /** Silence threshold — reach out to nearest POI after this much quiet (ms) */
        private const val SILENCE_THRESHOLD_MS = 5_000L

        /**
         * Maximum reach-out radius for the silence reach-out (meters).
         * S110: tightened from 150m → 15m. Salem has dense POI clusters; the
         * loose 150m radius let the reach-out grab POIs an entire block away
         * — and once GPS went stale, it cycled through the entire downtown
         * cluster as a runaway loop. 15m means the POI must be essentially
         * underfoot to trigger a reach-out narration.
         */
        private const val REACH_RADIUS_M = 15.0

        /** Same cap, but expanded under walk sim mode where the route is predictable. */
        private const val REACH_RADIUS_WALKSIM_M = 25.0

        /**
         * Categories that count as "amusement-like" for Historical Mode map
         * visibility. A POI in one of these categories shows on the map if it
         * also has `year_established` ≤ 1860 — a signal that the venue is
         * genuinely historical (museum, historic hall, old church, preserved
         * park). Used by [isVisibleInHistoricalMode]. Do NOT add SHOPPING,
         * FOOD_DRINK, OFFICES, HEALTHCARE, AUTO_SERVICES, or WITCH_SHOP here
         * — those would drag modern businesses into the immersive tour view.
         */
        private val AMUSEMENT_LIKE_CATEGORIES = setOf(
            "ENTERTAINMENT", "MUSEUM",
            "PARKS_REC", "WORSHIP"
        )
    }

    /** Load narration points to monitor.
     *
     *  S110: no longer clears cooldowns. Cooldowns are short-lived (2 minutes)
     *  and naturally expire — clearing them on every Activity recreate would
     *  defeat the purpose of the singleton lift, which is to prevent repeat
     *  triggers after orientation changes. Cooldowns now survive recreate
     *  along with `narratedAt`. */
    fun loadPoints(narrationPoints: List<SalemPoi>) {
        points = narrationPoints
        purgeExpired()
    }

    /**
     * Get the narration body text for a POI, honoring [AudioControl.detailLevel].
     *
     * S150 field-test fix: the ambient walk path previously hard-coded
     * `short_narration` regardless of the user's Detail setting, so `DEEP`
     * never pulled `long_narration`. Operators heard 5-20% of authored content.
     *
     * - `BRIEF`    → null (caller speaks only "You are at X")
     * - `STANDARD` → `short_narration`
     * - `DEEP`     → `long_narration` ?: `short_narration`
     * - Historical Mode (Phase 9R.0) still prefers `historical_note` when present,
     *   regardless of detail level.
     */
    fun getNarrationForPass(point: SalemPoi): String? {
        if (historicalMode) {
            val hn = point.historicalNote
            if (!hn.isNullOrBlank()) return hn
        }
        return when (AudioControl.detailLevel()) {
            AudioControl.DetailLevel.BRIEF    -> null
            AudioControl.DetailLevel.STANDARD -> point.shortNarration
            AudioControl.DetailLevel.DEEP     -> point.longNarration ?: point.shortNarration
        }
    }

    /**
     * Mode-independent "does this POI have any narration text at all?" check,
     * used by the enqueue no-narrative gate so BRIEF detail doesn't cause POIs
     * to be silently dropped from the queue (user may switch detail mid-walk).
     */
    fun hasAnyNarrationText(point: SalemPoi): Boolean {
        return !point.longNarration.isNullOrBlank() ||
               !point.shortNarration.isNullOrBlank() ||
               !point.description.isNullOrBlank() ||
               !point.historicalNote.isNullOrBlank()
    }

    /** Record that this POI was narrated today (increments persistent visit count) */
    fun recordVisit(pointId: String) {
        val prefs = visitPrefs ?: return
        val count = prefs.getInt(pointId, 0)
        prefs.edit().putInt(pointId, count + 1).apply()
    }

    /** Get lifetime visit count for a POI */
    fun getVisitCount(pointId: String): Int {
        return visitPrefs?.getInt(pointId, 0) ?: 0
    }

    /** GPS check counter for periodic summary logging */
    private var checkCount = 0L
    private var lastSummaryTime = 0L

    /** Call on every GPS update. Returns updated nearby list for the dock. */
    fun checkPosition(lat: Double, lng: Double): List<NearbyPoint> {
        val now = System.currentTimeMillis()
        purgeExpired()
        lastLat = lat
        lastLng = lng
        checkCount++
        val nearbyList = mutableListOf<NearbyPoint>()
        var entryCount = 0
        var approachCount = 0
        var skippedNarrated = 0
        var skippedCooldown = 0

        for (point in points) {
            // Phase 9R.0: historical mode strips modern POIs from consideration
            // entirely — they don't appear in the nearby dock, don't fire
            // APPROACH, and don't fire ENTRY. The user is walking the
            // Heritage Trail, not shopping for coffee.
            if (!isHistoricalQualified(point)) continue

            val distanceM = haversine(lat, lng, point.lat, point.lng)

            // Build nearby list (for proximity dock)
            if (distanceM <= NEARBY_RADIUS_M) {
                nearbyList.add(NearbyPoint(point, distanceM))
            }

            // Skip if already narrated within the repeat window (S112: 1 hour)
            if (isNarrated(point.id, now)) {
                if (distanceM <= point.geofenceRadiusM) skippedNarrated++
                continue
            }

            // S112+: walk-sim mode no longer inflates the entry radius. Walk-sim
            // and real GPS now use the same per-POI geofenceRadiusM. The 3x
            // inflation was a test-mode artifact that produced 75-120m entry zones,
            // which then conflicted with the queue's 50m discard threshold and
            // starved the dequeue. Walk-sim should behave identically to real
            // walking so the test reflects reality.
            val baseRadius = point.geofenceRadiusM.toDouble()
            val entryRadius = baseRadius
            val approachRadius = entryRadius * APPROACH_MULTIPLIER

            when {
                // ENTRY: within geofence radius
                distanceM <= entryRadius -> {
                    if (!isOnCooldown(point.id, now)) {
                        narratedAt[point.id] = now
                        persistNarrated(point.id, now)
                        cooldowns[point.id] = now
                        lastEntryEmitTime = now
                        entryCount++
                        com.example.locationmapapp.util.DebugLogger.i("NARR-GEO",
                            "ENTRY: ${point.name} dist=${distanceM.toInt()}m radius=${entryRadius.toInt()}m walkSim=$walkSimMode")
                        _events.tryEmit(NarrationGeofenceEvent(
                            type = NarrationEventType.ENTRY,
                            point = point,
                            distanceM = distanceM
                        ))
                    } else {
                        skippedCooldown++
                    }
                }
                // APPROACH: within 2x geofence radius
                distanceM <= approachRadius -> {
                    // S112 fix: APPROACH events do NOT consume the entry cooldown.
                    // Previously this branch set `cooldowns[point.id] = now`, which
                    // meant the very next fix (5-10 sec later) when the user was
                    // inside the entry radius would see `isOnCooldown == true` and
                    // skip the ENTRY event entirely. The dense-area starvation bug
                    // surfaced in S112 field tests was rooted here: ~30 POIs were
                    // silenced per drive because APPROACH burned the credit ENTRY
                    // would have used. APPROACH now emits its dock-update event
                    // without touching the cooldown.
                    if (!isOnCooldown(point.id, now)) {
                        approachCount++
                        _events.tryEmit(NarrationGeofenceEvent(
                            type = NarrationEventType.APPROACH,
                            point = point,
                            distanceM = distanceM
                        ))
                    }
                }
            }
        }

        // Periodic summary every 60 seconds
        if (now - lastSummaryTime >= 60_000L) {
            lastSummaryTime = now
            val activeNarrated = narratedAt.count { now - it.value < REPEAT_WINDOW_MS }
            com.example.locationmapapp.util.DebugLogger.i("NARR-GEO",
                "SUMMARY: check#$checkCount pos=${lat.format(5)},${lng.format(5)} " +
                "nearby=${nearbyList.size} narrated=$activeNarrated/${points.size} " +
                "entries=$entryCount approaches=$approachCount " +
                "skippedNarrated=$skippedNarrated skippedCooldown=$skippedCooldown " +
                "walkSim=$walkSimMode")
        }

        // Sort by distance, limit
        _nearby = nearbyList.sortedBy { it.distanceM }.take(MAX_NEARBY)
        return _nearby
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    /**
     * Find the best un-narrated point within reach-out radius.
     * Priority order: paying merchants first, then historical importance, then distance.
     * Called when silence exceeds threshold — keeps the narration flowing.
     * Returns null if nothing available nearby.
     *
     * S110: radius tightened to 15m (real GPS) / 25m (walk sim). See REACH_RADIUS_M
     * documentation. Caller must additionally gate this on GPS-fix freshness — the
     * manager has no notion of how stale `lastLat / lastLng` are.
     *
     * S115: Accepts an optional radius override from the caller. The dwell
     * expansion logic in SalemMainActivityNarration passes progressively wider
     * radii when the user is stationary and the tight-radius reach-out has
     * been exhausted. When `overrideRadiusM` is null the original S110
     * 15m/25m behavior applies.
     */
    fun findNearestUnnarrated(overrideRadiusM: Double? = null): NearbyPoint? {
        if (lastLat == 0.0 && lastLng == 0.0) return null

        val radius = overrideRadiusM ?: if (walkSimMode) REACH_RADIUS_WALKSIM_M else REACH_RADIUS_M
        val now = System.currentTimeMillis()

        val candidates = mutableListOf<NearbyPoint>()
        for (point in points) {
            if (isNarrated(point.id, now)) continue
            if (!isHistoricalQualified(point)) continue // 9R.0: silence modern POIs in reach-out too
            val dist = haversine(lastLat, lastLng, point.lat, point.lng)
            if (dist <= radius) {
                candidates.add(NearbyPoint(point, dist))
            }
        }
        if (candidates.isEmpty()) return null

        // Distance-first: narrate what the user is actually near, not some distant merchant.
        // Merchant priority only breaks ties at similar distance (within 50m of each other).
        return candidates.minWithOrNull(compareBy<NearbyPoint>(
            { (it.distanceM / 50).toInt() },  // bucket by ~50m bands — nearby always wins
            { -it.point.adPriority },          // within same band: merchants first
            { it.point.priority },              // then historical importance
            { it.distanceM }                    // exact distance tiebreaker
        ))
    }

    /**
     * Trigger a reach-out narration for the given point.
     * Marks it as narrated and emits an ENTRY event.
     */
    fun triggerReachOut(nearbyPoint: NearbyPoint) {
        val now = System.currentTimeMillis()
        narratedAt[nearbyPoint.point.id] = now
        persistNarrated(nearbyPoint.point.id, now)
        cooldowns[nearbyPoint.point.id] = now
        lastEntryEmitTime = now
        _events.tryEmit(NarrationGeofenceEvent(
            type = NarrationEventType.ENTRY,
            point = nearbyPoint.point,
            distanceM = nearbyPoint.distanceM
        ))
    }

    /** Time since last narration entry event (ms). Used by activity to detect silence. */
    fun msSinceLastEntry(): Long {
        if (lastEntryEmitTime == 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - lastEntryEmitTime
    }

    /** Reset tracking (e.g., when user starts a new walk sim). S125: also wipes persistent mirror. */
    fun resetSession() {
        narratedAt.clear()
        cooldowns.clear()
        narratedAtPrefs.edit().clear().apply()
    }

    /** Mark a specific point as narrated (e.g., user tapped it in dock) */
    fun markTriggered(pointId: String) {
        val now = System.currentTimeMillis()
        narratedAt[pointId] = now
        persistNarrated(pointId, now)
    }

    /** Check if a point has been narrated within the repeat window */
    fun isTriggered(pointId: String): Boolean = isNarrated(pointId, System.currentTimeMillis())

    /** Returns how many POIs have been narrated in the current window */
    fun narratedCount(): Int {
        val now = System.currentTimeMillis()
        return narratedAt.count { now - it.value < REPEAT_WINDOW_MS }
    }

    /** Check if a POI was narrated within the repeat window (S112: 1 hour) */
    private fun isNarrated(pointId: String, now: Long): Boolean {
        val ts = narratedAt[pointId] ?: return false
        return (now - ts) < REPEAT_WINDOW_MS
    }

    /** Remove expired entries older than the repeat window. S125: also prunes persistent mirror. */
    private fun purgeExpired() {
        val now = System.currentTimeMillis()
        val expired = narratedAt.entries.filter { now - it.value >= REPEAT_WINDOW_MS }.map { it.key }
        if (expired.isEmpty()) return
        for (id in expired) narratedAt.remove(id)
        val editor = narratedAtPrefs.edit()
        for (id in expired) editor.remove(id)
        editor.apply()
    }

    private fun isOnCooldown(pointId: String, now: Long): Boolean {
        val lastTrigger = cooldowns[pointId] ?: return false
        return (now - lastTrigger) < COOLDOWN_MS
    }

    private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}

/** Event types for narration geofence triggers */
enum class NarrationEventType {
    APPROACH,   // Within 2x geofence radius — show in dock, preview
    ENTRY       // Within geofence radius — trigger narration
}

/** Geofence event emitted when user is near a narration point */
data class NarrationGeofenceEvent(
    val type: NarrationEventType,
    val point: SalemPoi,
    val distanceM: Double
)

/** A narration point with its current distance from the user (for proximity dock) */
data class NearbyPoint(
    val point: SalemPoi,
    val distanceM: Double
)
