/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.locationmapapp.activation

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * OMEN-025 Phase 1 (Session 5) — fails-closed perimeter for the activation host.
 * Drives the pure [ActivationHostGuard.assertAllowed] directly (no Chain mock),
 * matching the injected-input style of [ContentManifestVerifierTest].
 */
class ActivationHostGuardTest {

    private val host = "activate-salem.example.workers.dev"
    private val guard = ActivationHostGuard(host)

    @Test
    fun `exact host over https is allowed`() {
        guard.assertAllowed("https://$host/nonce".toHttpUrl())
        guard.assertAllowed("https://$host/activate".toHttpUrl())
    }

    @Test
    fun `host match is case-insensitive`() {
        guard.assertAllowed("https://ACTIVATE-SALEM.EXAMPLE.WORKERS.DEV/nonce".toHttpUrl())
    }

    @Test
    fun `cleartext http is rejected`() {
        assertThrows(ActivationHostException::class.java) {
            guard.assertAllowed("http://$host/nonce".toHttpUrl())
        }
    }

    @Test
    fun `wrong host is rejected`() {
        assertThrows(ActivationHostException::class.java) {
            guard.assertAllowed("https://evil.example.com/nonce".toHttpUrl())
        }
    }

    @Test
    fun `subdomain of expected host is rejected (no wildcard)`() {
        assertThrows(ActivationHostException::class.java) {
            guard.assertAllowed("https://attacker.$host/nonce".toHttpUrl())
        }
    }

    @Test
    fun `lookalike host is rejected`() {
        assertThrows(ActivationHostException::class.java) {
            guard.assertAllowed("https://activate-salem.example.workers.dev.evil.com/nonce".toHttpUrl())
        }
    }
}
