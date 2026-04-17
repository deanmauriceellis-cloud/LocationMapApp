/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util.network

import com.example.locationmapapp.util.FeatureFlags
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module OfflineModeInterceptor.kt"

/**
 * OkHttp interceptor that enforces the V1 offline-only posture at the transport
 * layer. When [FeatureFlags.V1_OFFLINE_ONLY] is `true`, every outbound request
 * is short-circuited with [OfflineModeException] before it hits the socket.
 *
 * Install this interceptor **first** in the chain (before the circuit breaker
 * and any logging interceptor) so V1 builds never touch the network, never
 * log a connect attempt, and never update circuit-breaker state.
 *
 * For V2 builds [FeatureFlags.V1_OFFLINE_ONLY] flips to `false` and this
 * interceptor becomes a pass-through — no code changes needed downstream.
 */
class OfflineModeInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        if (FeatureFlags.V1_OFFLINE_ONLY) {
            throw OfflineModeException(chain.request().url.host)
        }
        return chain.proceed(chain.request())
    }
}

/**
 * Extension helper — one-line addition for every repository OkHttpClient builder.
 * Should be installed **before** [withLocalServerCircuitBreaker] so V1 builds
 * never even record a circuit-breaker failure.
 *
 * Usage:
 * ```
 * private val client = OkHttpClient.Builder()
 *     .connectTimeout(15, TimeUnit.SECONDS)
 *     .withOfflineModeGate()
 *     .withLocalServerCircuitBreaker()
 *     .build()
 * ```
 */
fun OkHttpClient.Builder.withOfflineModeGate(): OkHttpClient.Builder =
    addInterceptor(OfflineModeInterceptor())
