/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.activation

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module ActivationHttpClientFactory.kt"

/**
 * OMEN-025 Phase 1 (Session 5) — builds the ONE OkHttpClient permitted to touch
 * the network in a V1 release (decision D1). It deliberately does NOT install
 * [com.example.locationmapapp.util.network.OfflineModeInterceptor], so the
 * default-deny posture of the other 14 clients is untouched — the offline gate
 * stays a hard wall everywhere except this single activation path.
 *
 * Perimeter (all fail-closed): [ActivationHostGuard] (exact host + HTTPS-only +
 * no-redirect), `followRedirects(false)` / `followSslRedirects(false)`. **No
 * `CertificatePinner`** (NOTE-L023 #1) — plain HTTPS + standard cert validation.
 *
 * Used only inside [ActivationActivity]'s one-time handshake (Session 6). Normal
 * activated launches do zero network (decision D3), so this client is never even
 * constructed on the offline-forever path.
 */
object ActivationHttpClientFactory {

    /**
     * Placeholder activation host. **Wire the real `*.workers.dev` host here (or via
     * `BuildConfig`) once the Worker is deployed** (Session 4 step 7, operator-gated
     * on the Cloudflare token). Until then this compiles and unit-tests the path
     * without a live endpoint. The host is also passed to [ActivationHostGuard], so
     * a wrong value fails closed rather than leaking the token off-host.
     */
    const val WORKER_HOST: String = "activate-salem.example.workers.dev"
    const val WORKER_BASE_URL: String = "https://$WORKER_HOST"

    fun baseUrl(): HttpUrl = WORKER_BASE_URL.toHttpUrl()

    fun create(host: String = WORKER_HOST): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(ActivationHostGuard(host))
            // NOTE: no withOfflineModeGate() — by design (D1). No CertificatePinner (NOTE-L023 #1).
            .build()
}
