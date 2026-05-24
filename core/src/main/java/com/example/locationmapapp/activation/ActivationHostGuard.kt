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
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module ActivationHostGuard.kt"

/**
 * OMEN-025 Phase 1 (Session 5) — fails-closed transport guard for the activation
 * handshake. The activation client is the ONE OkHttpClient in the app that does
 * NOT carry [com.example.locationmapapp.util.network.OfflineModeInterceptor]
 * (decision D1) — so it needs its own perimeter:
 *
 *  - **exact host** — the request URL host must equal [expectedHost] verbatim
 *    (no subdomain wildcards, no IP literals). A misconfigured/redirected request
 *    to any other host is rejected before the socket.
 *  - **HTTPS only** — `http://` (cleartext) is rejected. Belt to the release
 *    `network_security_config` (Session 7) which is HTTPS-only anyway.
 *  - **no redirects** — the client sets `followRedirects(false)`; this guard
 *    additionally rejects any 3xx that slips through, so a malicious 302 can
 *    never bounce the integrity token off-host.
 *
 * **Cert pinning is intentionally absent** (NOTE-L023 #1, D-pin DROPPED): plain
 * HTTPS + standard cert validation. The stale-pin-bricks-every-new-buyer risk
 * outweighed the low-value MITM threat against a stateless verdict endpoint.
 *
 * JVM-testable: the policy is the pure [assertAllowed]; [intercept] is the thin
 * OkHttp adapter. Tests drive [assertAllowed] directly with `HttpUrl`s — no
 * `Interceptor.Chain` mock, matching the injected-input style of
 * [ContentManifestVerifier].
 */
class ActivationHostGuard(private val expectedHost: String) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        assertAllowed(chain.request().url)
        val response = chain.proceed(chain.request())
        if (response.isRedirect) {
            response.close()
            throw ActivationHostException("redirect not permitted on activation host (${response.code})")
        }
        return response
    }

    /**
     * Throws [ActivationHostException] if [url] violates the activation perimeter.
     * Returns normally when the URL is allowed. Pure — no I/O, no socket.
     */
    fun assertAllowed(url: HttpUrl) {
        if (!url.isHttps) {
            throw ActivationHostException("cleartext rejected: ${url.scheme} (HTTPS required)")
        }
        if (!url.host.equals(expectedHost, ignoreCase = true)) {
            throw ActivationHostException("unexpected host: ${url.host} (expected $expectedHost)")
        }
    }
}

/** Thrown when a request would leave the activation perimeter. Fails closed. */
class ActivationHostException(message: String) : IOException(message)
