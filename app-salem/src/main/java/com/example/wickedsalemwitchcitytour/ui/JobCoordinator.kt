/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * JobCoordinator — S304 tech-debt Phase 1a.
 *
 * Owns the Activity's named refresh/one-shot coroutine Jobs in one place,
 * replacing the ~24 hand-managed `xxxJob: Job?` fields scattered across the
 * SalemMainActivity partial classes. Previously each was launched in
 * lifecycleScope and cancelled by hand, with only gpsObsHeartbeatJob explicitly
 * torn down in onDestroy — a leak risk the moment a cancel site is missed.
 *
 * Jobs are launched in the SUPPLIED scope (the Activity passes its
 * lifecycleScope), so lifecycleScope auto-cancellation stays the safety net.
 * This class adds: named lookup, automatic cancel-before-relaunch (the debounce
 * pattern ~8 jobs hand-rolled), self-eviction on completion, and a single
 * [cancelAll] for onDestroy.
 *
 * Not thread-safe by design — every call site is the Activity's main thread,
 * matching the existing `lifecycleScope.launch { }` usage it replaces.
 */
class JobCoordinator(private val scope: CoroutineScope) {

    private val jobs = HashMap<String, Job>()

    /**
     * Launch [block] under [name], first cancelling any prior job registered
     * under the same name (the cancel-before-relaunch / debounce pattern).
     * Returns the new Job.
     */
    fun launch(name: String, block: suspend CoroutineScope.() -> Unit): Job {
        jobs.remove(name)?.cancel()
        val job = scope.launch(block = block)
        jobs[name] = job
        // Self-evict on completion so the map / activeCount don't retain dead
        // jobs. Guard against a newer same-name job having already replaced this
        // entry (e.g. a rapid relaunch).
        job.invokeOnCompletion { if (jobs[name] === job) jobs.remove(name) }
        return job
    }

    /** Cancel and forget the job registered under [name] (no-op if absent). */
    fun cancel(name: String) {
        jobs.remove(name)?.cancel()
    }

    /** True if a job under [name] is registered and still active. */
    fun isActive(name: String): Boolean = jobs[name]?.isActive == true

    /** Count of currently-active tracked jobs (diagnostics / tests). */
    fun activeCount(): Int = jobs.values.count { it.isActive }

    /**
     * Cancel every tracked job. Call once from onDestroy. Snapshots first so a
     * synchronous completion callback (Unconfined/immediate dispatch) can't
     * mutate the map mid-iteration.
     */
    fun cancelAll() {
        if (jobs.isEmpty()) return
        DebugLogger.d("JobCoordinator", "cancelAll() — cancelling ${jobs.size} job(s): ${jobs.keys}")
        val snapshot = jobs.values.toList()
        jobs.clear()
        snapshot.forEach { it.cancel() }
    }
}
