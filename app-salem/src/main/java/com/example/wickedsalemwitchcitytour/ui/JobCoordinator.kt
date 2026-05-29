/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
 * Call sites are the Activity's main thread (matching the `lifecycleScope.launch`
 * usage it replaces), but the [invokeOnCompletion] self-evict runs on whatever
 * thread a job completes on — which is Main today (all jobs are Main-dispatched
 * via lifecycleScope) but need not be once a future cluster completes inside
 * `withContext(IO)`. The backing map is therefore a [ConcurrentHashMap] and the
 * self-evict uses the atomic two-arg `remove(key, value)` so an off-Main
 * completion can never corrupt the map or race a same-name relaunch (S304
 * self-review finding).
 */
class JobCoordinator(private val scope: CoroutineScope) {

    private val jobs = ConcurrentHashMap<String, Job>()

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
        // jobs. The atomic two-arg remove drops the entry ONLY if this exact job
        // is still mapped (Job uses identity equals), so a newer same-name job
        // from a rapid relaunch is never clobbered — and it's safe off-Main.
        job.invokeOnCompletion { jobs.remove(name, job) }
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
