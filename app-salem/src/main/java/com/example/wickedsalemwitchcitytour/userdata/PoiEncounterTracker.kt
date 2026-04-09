/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata

import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.tour.NearbyPoint
import com.example.wickedsalemwitchcitytour.userdata.dao.PoiEncounterDao
import com.example.wickedsalemwitchcitytour.userdata.db.PoiEncounter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks POI proximity encounters: every time the user comes within
 * [PROXIMITY_RADIUS_M] of a narration POI, a row is opened in
 * [com.example.wickedsalemwitchcitytour.userdata.db.UserDataDatabase] and
 * updated on every GPS fix until the user leaves the proximity radius.
 *
 * **What gets recorded per encounter:**
 *  - POI identity (id, name, lat, lng — denormalized so review queries
 *    don't need to join against the bundled content DB)
 *  - First/last seen timestamps + total duration
 *  - Closest approach (`min_distance_m`)
 *  - Farthest distance reached while still inside the proximity radius
 *  - Fix count (how many GPS samples contributed to the encounter)
 *
 * **Review path (S110 initial):** the operator pulls the SQLite file via
 *   adb pull /sdcard/Android/data/com.example.wickedsalemwitchcitytour/databases/user_data.db
 * and inspects with `sqlite3` / DB Browser. A future session will add an
 * in-app review screen.
 *
 * **Why this lives next to [GpsTrackRecorder]:** both are user-mutable data
 * generated while the activity is alive, both want to survive process
 * recreate (so they're @Singleton), and both target [UserDataDatabase].
 * The two are independent — GpsTrackRecorder records every fix the
 * activity sees regardless of POIs; this tracker only fires when a POI is
 * within range. Together they answer "where did I go" and "what did I get
 * close to".
 *
 * **Threading:** [recordObservation] is called from the activity's GPS
 * observer on the main thread. The in-memory `active` map is touched only
 * from there. DB writes are dispatched to the IO scope and never touch
 * `active`. No locking required.
 *
 * S110 — POI proximity tracking.
 */
@Singleton
class PoiEncounterTracker @Inject constructor(
    private val dao: PoiEncounterDao
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * In-memory state of currently-active encounters. Key is the POI id.
     * The value holds the in-flight running statistics. The DB row mirrors
     * this on every fix via INSERT OR REPLACE on the composite primary key.
     *
     * Touched only from [recordObservation], which is called from the main
     * thread (the GPS observer in [com.example.wickedsalemwitchcitytour.ui.SalemMainActivity]).
     */
    private val active = mutableMapOf<String, PoiEncounter>()

    @Volatile
    private var lastPruneMs: Long = 0L

    /**
     * Called from the activity's GPS observer with the manager's nearby list
     * (already filtered to within [com.example.wickedsalemwitchcitytour.tour.NarrationGeofenceManager.NEARBY_RADIUS_M]
     * = 300 m). The tracker further filters to within [PROXIMITY_RADIUS_M].
     *
     * For each POI inside the proximity radius:
     *   - Open a new encounter (if no active row exists for this POI), or
     *   - Update the existing encounter's running statistics
     * For each previously-active encounter that is no longer inside the
     * proximity radius, finalize it (remove from in-memory state; the DB row
     * already has the final values from the last update).
     *
     * Fire-and-forget DB writes — the caller never blocks.
     */
    fun recordObservation(nearby: List<NearbyPoint>, nowMs: Long) {
        // Filter to "actually close" — narrower than the manager's 300m nearby list
        val inProximity = nearby.filter { it.distanceM <= PROXIMITY_RADIUS_M }
        val seenIds = HashSet<String>(inProximity.size).apply {
            inProximity.forEach { add(it.point.id) }
        }

        val toUpsert = mutableListOf<PoiEncounter>()

        // 1. Open or update encounters for in-proximity POIs
        for (np in inProximity) {
            val poi = np.point
            val existing = active[poi.id]
            val updated = if (existing == null) {
                // New encounter — create row, log open event
                val newRow = PoiEncounter(
                    id = "${poi.id}|$nowMs",
                    poiId = poi.id,
                    poiName = poi.name,
                    poiLat = poi.lat,
                    poiLng = poi.lng,
                    firstSeenAtMs = nowMs,
                    lastSeenAtMs = nowMs,
                    durationMs = 0L,
                    minDistanceM = np.distanceM,
                    maxDistanceM = np.distanceM,
                    fixCount = 1
                )
                DebugLogger.i(
                    TAG,
                    "ENC OPEN: ${poi.name} (${poi.id}) dist=${np.distanceM.toInt()}m"
                )
                newRow
            } else {
                // Update existing encounter — running min/max/duration/fixCount
                existing.copy(
                    lastSeenAtMs = nowMs,
                    durationMs = nowMs - existing.firstSeenAtMs,
                    minDistanceM = if (np.distanceM < existing.minDistanceM) np.distanceM else existing.minDistanceM,
                    maxDistanceM = if (np.distanceM > existing.maxDistanceM) np.distanceM else existing.maxDistanceM,
                    fixCount = existing.fixCount + 1
                )
            }
            active[poi.id] = updated
            toUpsert.add(updated)
        }

        // 2. Finalize encounters that are no longer in proximity. The DB row
        //    already holds the final values from the last upsert, so we just
        //    drop the in-memory entry.
        val toFinalizeIds = active.keys.filter { it !in seenIds }
        for (poiId in toFinalizeIds) {
            val finalized = active.remove(poiId) ?: continue
            DebugLogger.i(
                TAG,
                "ENC CLOSE: ${finalized.poiName} (${finalized.poiId}) " +
                "duration=${finalized.durationMs / 1000}s " +
                "minDist=${finalized.minDistanceM.toInt()}m " +
                "maxDist=${finalized.maxDistanceM.toInt()}m " +
                "fixes=${finalized.fixCount}"
            )
        }

        // 3. Async DB write — single batch per fix
        if (toUpsert.isNotEmpty()) {
            scope.launch {
                for (enc in toUpsert) {
                    try {
                        dao.upsert(enc)
                    } catch (e: Exception) {
                        DebugLogger.e(TAG, "upsert encounter failed: ${e.message}")
                    }
                }
            }
        }

        // 4. Periodic prune — piggyback on the next fix after the interval
        if (nowMs - lastPruneMs >= PRUNE_INTERVAL_MS) {
            lastPruneMs = nowMs
            scope.launch { runPrune(nowMs) }
        }
    }

    /**
     * Drop all in-memory active encounters without persisting them. The DB
     * rows already hold their last-known values. Called from
     * [com.example.wickedsalemwitchcitytour.ui.SalemMainActivity.onDestroy]
     * for diagnostic logging only.
     */
    fun flushAll() {
        if (active.isEmpty()) return
        DebugLogger.i(TAG, "flushAll: ${active.size} active encounter(s) at activity destroy:")
        for (enc in active.values) {
            DebugLogger.i(
                TAG,
                "  · ${enc.poiName} duration=${enc.durationMs / 1000}s " +
                "minDist=${enc.minDistanceM.toInt()}m fixes=${enc.fixCount}"
            )
        }
        active.clear()
    }

    /**
     * One-shot prune at app start to clean up rows older than [RETENTION_MS].
     * Mirror of [GpsTrackRecorder.pruneStaleAtStartup].
     */
    fun pruneStaleAtStartup() {
        val nowMs = System.currentTimeMillis()
        lastPruneMs = nowMs
        scope.launch { runPrune(nowMs) }
    }

    /**
     * Synchronous review query — most recent N encounters across all time
     * (subject to retention). Suspending so it can be called from a
     * coroutine in any future review UI.
     */
    suspend fun getMostRecent(limit: Int = 100): List<PoiEncounter> {
        return try {
            dao.getMostRecent(limit)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "getMostRecent failed: ${e.message}")
            emptyList()
        }
    }

    /** Encounters touched in the last [windowMs] milliseconds, most recent first. */
    suspend fun getRecent(windowMs: Long): List<PoiEncounter> {
        val sinceMs = System.currentTimeMillis() - windowMs
        return try {
            dao.getRecent(sinceMs)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "getRecent failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun runPrune(nowMs: Long) {
        try {
            val cutoffMs = nowMs - RETENTION_MS
            val deleted = dao.deleteOlderThan(cutoffMs)
            if (deleted > 0) {
                DebugLogger.i(TAG, "Pruned $deleted encounters older than ${RETENTION_MS / (24 * 60 * 60 * 1000L)}d")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Prune failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PoiEncounter"

        /**
         * "Close to" definition. Wider than the typical 30-50 m geofence entry
         * radius (which gates narration triggers) and narrower than the manager's
         * 300 m "nearby" list (which can include several blocks worth of POIs in
         * dense areas like downtown Salem). 100 m means the user has to actually
         * pass by the POI for it to register, not just be on the same street.
         */
        const val PROXIMITY_RADIUS_M: Double = 100.0

        /** Retention window: 7 days. */
        private const val RETENTION_MS = 7L * 24L * 60L * 60L * 1000L

        /** How often we attempt the rolling prune (one hour). */
        private const val PRUNE_INTERVAL_MS = 60L * 60L * 1000L
    }
}
