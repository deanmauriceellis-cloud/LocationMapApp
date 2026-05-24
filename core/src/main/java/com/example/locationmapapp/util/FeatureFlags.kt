/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module FeatureFlags.kt"

/**
 * Compile-time feature flags for the V1 commercial launch.
 *
 * **V1 posture (locked S138, 2026-04-16):** $19.99 flat paid, fully offline,
 * no ads, no LLM, no tiers, IARC Teen / PG-13. Everything that requires an
 * outbound network connection is disabled for V1 and returns in V2.
 *
 * Flip [V1_OFFLINE_ONLY] to `false` for V2 builds — no other code changes
 * required. The [com.example.locationmapapp.util.network.OfflineModeInterceptor]
 * enforces this at the OkHttp layer so nothing slips past, and every ViewModel
 * fetch method early-returns when the flag is on so the coroutines don't even
 * spin up.
 *
 * Values are `const val`, so the Kotlin compiler inlines them at every call
 * site and ProGuard/R8 eliminates the disabled code branches in release builds.
 */
object FeatureFlags {

    /**
     * When `true`, every outbound HTTP/HTTPS request is blocked and every
     * network-backed feature (MBTA, Weather, METAR, Webcams, TFRs, Aircraft,
     * Radar, Cache-proxy POIs, OSRM live routing, online tile sources) is
     * hidden or no-op. V1 ships with this ON.
     */
    const val V1_OFFLINE_ONLY: Boolean = true

    /**
     * OMEN-025 Phase 1 — master switch for the one-time first-run activation
     * handshake (Play Integrity + signed-manifest licensing check). When `true`,
     * `SplashActivity` routes through `ActivationActivity` before `Main`; an
     * already-activated install verifies its local receipt offline (few ms) and
     * forwards immediately, so the offline-forever promise is preserved
     * ([V1_OFFLINE_ONLY] is unchanged and the 14 gated clients stay blocked).
     *
     * **Ships OFF until the activation Worker is deployed and the flow is
     * device-validated against the internal Play track** (plan Sessions 6–7,
     * operator-gated). Flipping it on without a reachable Worker would hard-block
     * every launch (operator decision: no grace window). Flip to `true` only in
     * the same change that wires + verifies the gate end-to-end.
     */
    const val ACTIVATION_HANDSHAKE_ENABLED: Boolean = false
}
