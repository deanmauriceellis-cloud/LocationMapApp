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
import com.example.wickedsalemwitchcitytour.userdata.dao.GpsTrackPointDao
import com.example.wickedsalemwitchcitytour.userdata.db.GpsTrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records every GPS fix the activity sees into [com.example.wickedsalemwitchcitytour.userdata.db.UserDataDatabase]
 * for the user's GPS Journey feature (Phase 9T+, Session 109).
 *
 * ## Semantics (Option B from S109 design discussion)
 *
 * - **Recording is ALWAYS ON** while the activity is alive. There is no privacy
 *   gate at this layer; if the operator wants to pause tracking, the manual
 *   on/off control will live in the utility/settings menu (deferred to a future
 *   session).
 * - The bottom-left "GPS" FAB in [com.example.wickedsalemwitchcitytour.ui.SalemMainActivity]
 *   controls **display only** — toggling it does not affect what gets recorded.
 * - Recording stops when the activity is destroyed (no foreground service, no
 *   ACCESS_BACKGROUND_LOCATION).
 * - Retention: rolling 24h. Old points are pruned at startup and once per hour
 *   while the app is running. The pruning happens in the recorder's own scope so
 *   it survives lightweight UI churn.
 *
 * ## Why no batching
 *
 * GPS arrives at 5-60s intervals depending on adaptive mode. Room insert
 * overhead at that rate is negligible (<5ms per insert on the test tablet),
 * and avoiding batching means there's no "lost the last 5 fixes when the
 * process was killed" failure mode. Re-evaluate batching only if profiling
 * shows it matters.
 *
 * ## Storage cost
 *
 * Worst case: 5s GPS rate × 24h × ~50 bytes/row ≈ 800 KB. Real case is much
 * smaller because the rate drops to 60s when narration is inactive and
 * stationary, and to 10s while driving. Negligible.
 */
@Singleton
class GpsTrackRecorder @Inject constructor(
    private val dao: GpsTrackPointDao
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Wall-clock millis of the most recent prune. */
    @Volatile
    private var lastPruneMs: Long = 0L

    /**
     * Single-fix entry point. Called from the Salem activity's GPS observer
     * on every fix, BEFORE the proportional dead zone gate, so the journey
     * log captures continuous movement even when the visual marker is parked.
     *
     * Fire-and-forget — runs on the recorder's IO scope, returns immediately.
     */
    fun recordFix(
        lat: Double,
        lng: Double,
        accuracyM: Float,
        speedMps: Float?,
        bearingDeg: Float?
    ) {
        val nowMs = System.currentTimeMillis()
        val point = GpsTrackPoint(
            tsMs = nowMs,
            lat = lat,
            lng = lng,
            accuracyM = accuracyM,
            speedMps = speedMps,
            bearingDeg = bearingDeg
        )
        scope.launch {
            try {
                dao.insert(point)
            } catch (e: Exception) {
                DebugLogger.e(TAG, "recordFix insert failed: ${e.message}")
            }
        }

        // Hourly prune piggy-backs on the next fix after the interval elapses,
        // so we don't need a separate timer that has to be torn down.
        if (nowMs - lastPruneMs >= PRUNE_INTERVAL_MS) {
            lastPruneMs = nowMs
            scope.launch { runPrune(nowMs) }
        }
    }

    /**
     * Returns the last 24h of recorded fixes, oldest → newest. Used by the
     * activity to seed the polyline overlay at startup.
     */
    suspend fun getRecent24h(): List<GpsTrackPoint> {
        val sinceMs = System.currentTimeMillis() - RETENTION_MS
        return try {
            dao.getRecent(sinceMs)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "getRecent24h failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * One-shot prune called on app start to clean up stale data carried over
     * from a prior session. Synchronous from the caller's perspective in the
     * sense that it dispatches immediately on the recorder's scope; the
     * activity does not wait for it before loading the polyline overlay
     * (the overlay query uses the same 24h cutoff so anything still being
     * pruned is also outside the visible window).
     */
    fun pruneStaleAtStartup() {
        val nowMs = System.currentTimeMillis()
        lastPruneMs = nowMs
        scope.launch { runPrune(nowMs) }
    }

    private suspend fun runPrune(nowMs: Long) {
        try {
            val cutoffMs = nowMs - RETENTION_MS
            val deleted = dao.deleteOlderThan(cutoffMs)
            if (deleted > 0) {
                DebugLogger.i(TAG, "Pruned $deleted GPS points older than 24h")
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Prune failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "GpsTrackRecorder"

        /** Rolling retention window for the journey log. */
        private const val RETENTION_MS = 24 * 60 * 60 * 1000L

        /**
         * How often we attempt the rolling prune.
         *
         * S115: Tightened from 1 hour → 5 minutes. The previous cadence meant
         * the DB constantly held up to 60 minutes of aged-out rows, which
         * surfaced in field testing as "GPS not being deleted after 24 hours"
         * when the operator queried user_data.db directly and saw 500+ stale
         * rows. The drawn polyline was always correct (getRecent24h uses a
         * strict now-24h cutoff at query time), but the DB ground truth drifted.
         * 5-minute cadence drops the drift to ~5 minutes. Prune is a single
         * indexed DELETE; the overhead is negligible.
         */
        private const val PRUNE_INTERVAL_MS = 5 * 60 * 1000L
    }
}
