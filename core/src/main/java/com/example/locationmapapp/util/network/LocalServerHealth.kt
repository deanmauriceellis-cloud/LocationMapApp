/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util.network

import com.example.locationmapapp.util.DebugLogger
import java.util.concurrent.atomic.AtomicLong

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module LocalServerHealth.kt"

/**
 * Process-wide circuit breaker for the local development server (cache-proxy) at
 * `10.0.0.229:4300` (and `localhost` / `127.0.0.1` aliases).
 *
 * **Why this exists:**
 * S110 — when the laptop's `cache-proxy` is offline (sleeping, restarted, on a
 * different network), every consumer (`WeatherViewModel`, `MainViewModel`,
 * `GeofenceViewModel`, `Aircraft / Webcam / TFR / etc.` repositories)
 * independently hits a 15-second TCP connect timeout, logs a stack trace, then
 * retries on the next foreground / scroll / refresh. The result is dozens of
 * pointless 15-second hangs and noisy log spam during a perfectly normal
 * "I'm-not-on-my-laptop's-LAN" condition.
 *
 * **What this does:**
 * - Tracks a single boolean: "is the local server up right now?"
 * - On a TCP-connect failure (recorded by `LocalServerCircuitBreakerInterceptor`),
 *   the circuit flips to OPEN for [OPEN_DURATION_MS] (60 seconds by default).
 * - While the circuit is OPEN, subsequent requests to local hosts fail
 *   immediately with [CircuitOpenException] — no TCP, no waiting.
 * - On any successful response from a local host, the circuit is reset to
 *   CLOSED immediately (the server came back).
 * - State is process-wide and intentionally NOT persisted: every fresh process
 *   start gets one chance to discover the server.
 *
 * **What this is NOT:**
 * - Not a retry policy. If the call site wants retries, it can still implement
 *   them — they'll just fail fast for the first 60 seconds after a confirmed
 *   outage.
 * - Not a UI signal. There's no toast / banner / icon — the goal is silent
 *   degradation, not user-facing alerts.
 * - Not a health check. We do not actively probe the server; we react to
 *   real call results.
 */
object LocalServerHealth {

    private const val TAG = "LocalServerHealth"

    /** How long the circuit stays OPEN after the last observed failure. */
    private const val OPEN_DURATION_MS = 60_000L

    /**
     * Hostnames that this circuit breaker applies to. Any OkHttp request whose
     * URL host matches one of these is subject to the breaker. Other hosts pass
     * through the interceptor unchanged so MBTA / NWS / OpenSky / Aviation
     * Weather / etc. continue to work normally even if the cache-proxy is down.
     *
     * The cache-proxy IP is hardcoded across the codebase as `10.0.0.229` (see
     * `Repository#PROXY_BASE` constants). When that IP changes, this set is the
     * place to update.
     */
    val LOCAL_HOSTS: Set<String> = setOf(
        "10.0.0.229",
        "localhost",
        "127.0.0.1"
    )

    /**
     * Wall-clock timestamp of the most recent failure that opened the circuit,
     * or 0 if the circuit has never tripped this process.
     */
    private val lastFailureAtMs = AtomicLong(0L)

    /**
     * Returns `true` if the local server is currently considered healthy
     * (the circuit is CLOSED). Returns `false` if a recent failure has put
     * the circuit in OPEN state and we're still inside the cooldown window.
     */
    fun isUp(): Boolean {
        val lastFailure = lastFailureAtMs.get()
        if (lastFailure == 0L) return true
        val ageMs = System.currentTimeMillis() - lastFailure
        return ageMs >= OPEN_DURATION_MS
    }

    /**
     * Returns the age of the most recent failure in milliseconds, or
     * `Long.MAX_VALUE` if there has been no failure yet. Used by the
     * interceptor to format the [CircuitOpenException] message.
     */
    fun lastFailureAgeMs(): Long {
        val lastFailure = lastFailureAtMs.get()
        if (lastFailure == 0L) return Long.MAX_VALUE
        return System.currentTimeMillis() - lastFailure
    }

    /**
     * Mark the local server as DOWN. Called by the interceptor when a TCP
     * connect / read fails for one of the [LOCAL_HOSTS]. Idempotent — multiple
     * concurrent failures all reset the timer to "now."
     */
    fun recordFailure(host: String, cause: String) {
        val wasUp = isUp()
        lastFailureAtMs.set(System.currentTimeMillis())
        if (wasUp) {
            // Only log the transition, not every retry, so the log stays quiet
            // while the circuit is held open.
            DebugLogger.w(
                TAG,
                "circuit OPEN — local server $host is down ($cause). Future requests to $host will fail fast for ${OPEN_DURATION_MS / 1000}s."
            )
        }
    }

    /**
     * Mark the local server as UP. Called by the interceptor on a successful
     * (non-network-error) response from one of the [LOCAL_HOSTS], regardless
     * of HTTP status — a 4xx / 5xx still proves the server is reachable.
     */
    fun recordSuccess(host: String) {
        val wasDown = !isUp()
        lastFailureAtMs.set(0L)
        if (wasDown) {
            DebugLogger.i(TAG, "circuit CLOSED — local server $host recovered")
        }
    }
}
