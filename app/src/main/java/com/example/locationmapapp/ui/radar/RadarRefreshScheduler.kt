package com.example.locationmapapp.ui.radar

import android.os.Handler
import android.os.Looper
import com.example.locationmapapp.util.DebugLogger

/**
 * RadarRefreshScheduler
 *
 * Drives periodic radar tile refresh using a simple Handler-based loop.
 * Chosen over WorkManager because this is a foreground, screen-visible feature —
 * the overhead of a JobScheduler/WorkManager round-trip is unnecessary.
 *
 * Thread: main thread only. The callback itself must be fast (just post a tile
 * source swap or timestamp change on the ViewModel).
 *
 * Usage:
 *   scheduler.start(intervalMinutes = 5) { viewModel.refreshRadar() }
 *   scheduler.stop()                           // called from onStop / radar-off toggle
 *   scheduler.setInterval(minutes)             // live update without stop/start
 */
class RadarRefreshScheduler {

    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var onRefresh: (() -> Unit)? = null
    private var intervalMs: Long = DEFAULT_INTERVAL_MS

    val isRunning get() = refreshRunnable != null

    // ── Public API ─────────────────────────────────────────────────────────────

    fun start(intervalMinutes: Int, onRefresh: () -> Unit) {
        stop()
        this.onRefresh    = onRefresh
        this.intervalMs   = intervalMinutes.minutesToMs()
        scheduleNext()
        DebugLogger.i(TAG, "Radar refresh started: every $intervalMinutes min")
    }

    fun stop() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
        DebugLogger.i(TAG, "Radar refresh stopped")
    }

    /** Change interval while running — next tick picks up the new period. */
    fun setInterval(intervalMinutes: Int) {
        intervalMs = intervalMinutes.minutesToMs()
        DebugLogger.i(TAG, "Radar refresh interval changed to $intervalMinutes min")
        if (isRunning) {
            refreshRunnable?.let { handler.removeCallbacks(it) }
            scheduleNext()
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private fun scheduleNext() {
        val runnable = Runnable {
            DebugLogger.d(TAG, "Radar auto-refresh tick")
            onRefresh?.invoke()
            scheduleNext()          // re-arm
        }
        refreshRunnable = runnable
        handler.postDelayed(runnable, intervalMs)
    }

    private fun Int.minutesToMs() = this.toLong() * 60_000L

    companion object {
        private const val TAG = "RadarRefreshScheduler"
        val DEFAULT_INTERVAL_MS = 5 * 60_000L   // 5 minutes per requirement
    }
}
