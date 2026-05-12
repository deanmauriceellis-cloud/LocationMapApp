/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * S249 — debug-only "SuperAdmin mode" runtime override that lets the operator
 * temporarily bypass the V1_OFFLINE_ONLY gate so the parked external services
 * (weather / MBTA / aircraft / METAR / webcams / radar) become reachable for
 * post-V1 dev + verification on the Lenovo. Hard-gated by [setAvailable]
 * which is only ever called with `true` in debug (app-salem hardcodes
 * `BuildDefaults.SUPER_ADMIN_ENABLED = BuildConfig.RECON_DEFAULTS`); release
 * builds R8-strip the call site, leaving [allowNetwork] permanently false.
 *
 * The OkHttp [com.example.locationmapapp.util.network.OfflineModeInterceptor]
 * and every V1-gated ViewModel call site checks
 * `if (FeatureFlags.V1_OFFLINE_ONLY && !SuperAdminMode.allowNetwork) ...`
 * — preserving the V1 ship-gate semantics in release while letting debug
 * flip the second predicate at runtime.
 *
 * Also requires the debug-only AndroidManifest at
 * `app-salem/src/debug/AndroidManifest.xml` to re-add INTERNET +
 * ACCESS_NETWORK_STATE; without those, the OS denies network at the syscall
 * layer regardless of this flag.
 *
 * Session-scoped — resets to `false` on every cold start. No SharedPreferences
 * persistence by design (mirrors field-edit's safer-default posture).
 */

package com.example.locationmapapp.util

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module SuperAdminMode.kt"

object SuperAdminMode {

    @Volatile private var available: Boolean = false
    @Volatile private var enabledFlag: Boolean = false

    /**
     * `true` iff SuperAdmin is BOTH compile-time available (debug build) AND
     * the operator has toggled it ON. Checked at every V1 gate site.
     */
    val allowNetwork: Boolean
        get() = available && enabledFlag

    /** Whether the SuperAdmin toggle is currently ON. Always false if not [available]. */
    fun isEnabled(): Boolean = enabledFlag

    /**
     * Called once at app startup by the host app (app-salem) with the
     * compile-time `BuildDefaults.SUPER_ADMIN_ENABLED` value. In release
     * R8 inlines this to `setAvailable(false)` and may DCE the entire
     * runtime-toggle path.
     */
    fun setAvailable(value: Boolean) {
        available = value
        if (!value) enabledFlag = false  // belt-and-braces
    }

    /**
     * Toggle SuperAdmin ON or OFF. No-op when [available] is false (release
     * builds), so even if a stray reference survived R8 it cannot enable
     * network access in retail.
     */
    fun setEnabled(value: Boolean) {
        if (available) enabledFlag = value
    }
}
