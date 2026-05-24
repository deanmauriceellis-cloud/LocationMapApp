/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.locationmapapp.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OMEN-025 Phase 1 (Session 6) — the load-bearing cross-component contract:
 * [PlayIntegrityClient.computeRequestHash] MUST produce the byte-identical string
 * the activation Worker computes, or every genuine token is rejected with
 * REQUEST_HASH_MISMATCH. Mirrors the S295 manifestHash cross-check.
 *
 * The expected value below was produced by the real Worker code:
 *   cd worker-activate && node --input-type=module -e '
 *     import { requestHash } from "./src/integrity.js";
 *     console.log(await requestHash("TEST_NONCE_abc-123_xyz",
 *       "978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28"));'
 * If `src/integrity.js` requestHash changes, this test must be regenerated in lockstep.
 */
class PlayIntegrityRequestHashTest {

    private val nonce = "TEST_NONCE_abc-123_xyz"
    private val manifest = "978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28"

    @Test
    fun `requestHash byte-matches the Worker value`() {
        assertEquals(
            "8DORFCkZ9qldWh9J-ZslUCcVRmidwj66Hj2FY888g4Q",
            PlayIntegrityClient.computeRequestHash(nonce, manifest),
        )
    }

    @Test
    fun `requestHash is base64url without padding`() {
        val h = PlayIntegrityClient.computeRequestHash(nonce, manifest)
        assertTrue("no '+' or '/'", h.none { it == '+' || it == '/' })
        assertTrue("no '=' padding", !h.contains('='))
    }

    @Test
    fun `requestHash is deterministic and order-sensitive`() {
        assertEquals(
            PlayIntegrityClient.computeRequestHash(nonce, manifest),
            PlayIntegrityClient.computeRequestHash(nonce, manifest),
        )
        // nonce‖manifest != manifest‖nonce — concatenation order is part of the contract.
        assertTrue(
            PlayIntegrityClient.computeRequestHash(nonce, manifest) !=
                PlayIntegrityClient.computeRequestHash(manifest, nonce),
        )
    }
}
