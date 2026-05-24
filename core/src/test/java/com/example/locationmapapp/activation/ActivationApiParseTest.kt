/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.locationmapapp.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OMEN-025 Phase 1 (Session 5) — the (httpCode, body) → [ActivationResult] matrix.
 * Drives the pure [ActivationApi.parseActivateResponse], so the full success/deny/
 * retry mapping is covered without a live Worker or MockWebServer. Bodies mirror
 * `worker-activate/CONTRACT.md` and the `worker.test.js` fixtures.
 */
class ActivationApiParseTest {

    private val manifest = "978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28"

    @Test
    fun `200 ok maps to Activated with verdict`() {
        val body = """
            {"ok":true,"issuedAt":1716523200500,"manifestHash":"$manifest",
             "verdict":{"licensing":"LICENSED","appRecognition":"PLAY_RECOGNIZED","device":["MEETS_DEVICE_INTEGRITY"]}}
        """.trimIndent()
        val r = ActivationApi.parseActivateResponse(200, body)
        assertTrue(r is ActivationResult.Activated)
        r as ActivationResult.Activated
        assertEquals(manifest, r.manifestHash)
        assertEquals(1716523200500L, r.issuedAt)
        assertEquals("LICENSED", r.verdict.licensing)
        assertEquals(listOf("MEETS_DEVICE_INTEGRITY"), r.verdict.device)
        assertEquals(null, r.assertion)
    }

    @Test
    fun `200 ok with assertion preserves it`() {
        val body = """{"ok":true,"issuedAt":1,"manifestHash":"$manifest","assertion":"abc123"}"""
        val r = ActivationApi.parseActivateResponse(200, body) as ActivationResult.Activated
        assertEquals("abc123", r.assertion)
    }

    @Test
    fun `403 UNLICENSED maps to Denied`() {
        val r = ActivationApi.parseActivateResponse(403, """{"ok":false,"reason":"UNLICENSED"}""")
        assertTrue(r is ActivationResult.Denied)
        r as ActivationResult.Denied
        assertEquals("UNLICENSED", r.reason)
        assertEquals(403, r.httpStatus)
    }

    @Test
    fun `400 BAD_REQUEST maps to Denied`() {
        val r = ActivationApi.parseActivateResponse(400, """{"ok":false,"reason":"BAD_REQUEST"}""")
        assertTrue(r is ActivationResult.Denied)
        assertEquals("BAD_REQUEST", (r as ActivationResult.Denied).reason)
    }

    @Test
    fun `502 DECODE_FAILED maps to Retryable`() {
        val r = ActivationApi.parseActivateResponse(502, """{"ok":false,"reason":"DECODE_FAILED"}""")
        assertTrue(r is ActivationResult.Retryable)
        assertEquals("DECODE_FAILED", (r as ActivationResult.Retryable).reason)
    }

    @Test
    fun `500 with no body still Retryable`() {
        val r = ActivationApi.parseActivateResponse(500, "")
        assertTrue(r is ActivationResult.Retryable)
        assertEquals("HTTP_500", (r as ActivationResult.Retryable).reason)
    }

    @Test
    fun `200 but ok-false is treated as Retryable not silent-pass`() {
        val r = ActivationApi.parseActivateResponse(200, """{"ok":false}""")
        assertTrue(r is ActivationResult.Retryable)
    }

    @Test
    fun `200 with garbage body is Retryable`() {
        val r = ActivationApi.parseActivateResponse(200, "not json at all")
        assertTrue(r is ActivationResult.Retryable)
    }

    @Test
    fun `403 with garbage body still Denied with fallback reason`() {
        val r = ActivationApi.parseActivateResponse(403, "<<html error>>")
        assertTrue(r is ActivationResult.Denied)
        assertEquals("DENIED", (r as ActivationResult.Denied).reason)
    }
}
