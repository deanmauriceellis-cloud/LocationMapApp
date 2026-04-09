/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module LocalServerCircuitBreakerInterceptor.kt"

/**
 * OkHttp interceptor that wires every outgoing request through the
 * [LocalServerHealth] circuit breaker. Only acts on requests whose host is in
 * [LocalServerHealth.LOCAL_HOSTS]; everything else (MBTA, NWS, OpenSky,
 * Aviation Weather, OSRM, Wikipedia, etc.) passes through unchanged.
 *
 * **Behavior for local-host requests:**
 *
 * 1. If the circuit is currently OPEN (a recent failure has not yet aged out),
 *    throw [CircuitOpenException] immediately. No TCP connect, no waiting,
 *    no log spam.
 * 2. Otherwise, attempt the call.
 *    - On [ConnectException] / [SocketTimeoutException] / [UnknownHostException]
 *      from the chain, call [LocalServerHealth.recordFailure] (which flips the
 *      circuit to OPEN) and rethrow the original exception.
 *    - On any returned [Response] (even 4xx / 5xx — the server is reachable
 *      even if it's unhappy), call [LocalServerHealth.recordSuccess] (which
 *      resets the circuit to CLOSED) and return the response.
 *
 * Stateless and safe to share — the singleton instance lives in the companion
 * object and is the same object across all repository [OkHttpClient]s.
 */
class LocalServerCircuitBreakerInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host

        // Pass through anything that isn't a local server — no behavior change
        // for MBTA / NWS / OpenSky / public APIs.
        if (host !in LocalServerHealth.LOCAL_HOSTS) {
            return chain.proceed(request)
        }

        // Fail fast if the circuit is already OPEN. We do not even attempt the
        // TCP connect; the call site receives a CircuitOpenException which
        // DebugLogger.e recognizes and compact-logs.
        if (!LocalServerHealth.isUp()) {
            val ageMs = LocalServerHealth.lastFailureAgeMs()
            throw CircuitOpenException(
                "circuit-open: $host (last failure ${ageMs / 1000}s ago, " +
                "fast-failing for ~${(60_000 - ageMs).coerceAtLeast(0L) / 1000}s more)"
            )
        }

        // Attempt the call. Catch the exact exception types OkHttp uses for
        // TCP-level failures, record the failure, then rethrow.
        return try {
            val response = chain.proceed(request)
            // ANY response — even 4xx / 5xx — proves the server is reachable.
            // Record success so the circuit closes immediately if it had been
            // partially open.
            LocalServerHealth.recordSuccess(host)
            response
        } catch (e: ConnectException) {
            LocalServerHealth.recordFailure(host, "ConnectException: ${e.message}")
            throw e
        } catch (e: SocketTimeoutException) {
            LocalServerHealth.recordFailure(host, "SocketTimeoutException: ${e.message}")
            throw e
        } catch (e: UnknownHostException) {
            LocalServerHealth.recordFailure(host, "UnknownHostException: ${e.message}")
            throw e
        }
    }
}

/**
 * Extension helper — one-line addition for every repository OkHttpClient builder.
 * Usage:
 * ```
 * private val client = OkHttpClient.Builder()
 *     .connectTimeout(15, TimeUnit.SECONDS)
 *     .withLocalServerCircuitBreaker()
 *     .build()
 * ```
 */
fun OkHttpClient.Builder.withLocalServerCircuitBreaker(): OkHttpClient.Builder =
    addInterceptor(LocalServerCircuitBreakerInterceptor())
