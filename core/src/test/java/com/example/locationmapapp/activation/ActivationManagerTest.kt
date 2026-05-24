/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.locationmapapp.activation

import com.example.locationmapapp.activation.ActivationManager.Companion.INSTALLER_PLAY
import com.example.locationmapapp.activation.ActivationManager.Companion.buildReceipt
import com.example.locationmapapp.activation.ActivationManager.Companion.decideHandshake
import com.example.locationmapapp.activation.ActivationManager.Companion.decideLocal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OMEN-025 Phase 1 (Session 6) — the activation state machine's pure decision
 * core: [decideLocal] (offline tripwires) + [decideHandshake] (Worker-result
 * mapping) + [buildReceipt]. Drives the companion functions directly — no
 * Android, no live Worker — matching [ContentManifestVerifierTest] style.
 */
class ActivationManagerTest {

    private val manifest = "978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28"

    private fun snapshot(
        fp: String? = "FP-device-1",
        androidId: String = "aidhash-1",
        installer: String? = INSTALLER_PLAY,
        cert: String = "CERT-1",
        sec: String = "TEE",
    ) = DeviceSnapshot(fp, androidId, installer, cert, sec)

    private fun receipt(snap: DeviceSnapshot = snapshot(), hash: String = manifest) = ActivationReceipt(
        activatedAtEpoch = 1L,
        deviceKeyFingerprint = snap.deviceKeyFingerprint.orEmpty(),
        deviceSecurityLevel = snap.deviceSecurityLevel,
        androidIdHash = snap.androidIdHash,
        installerPackage = snap.installerPackage,
        appSigningCertSha256 = snap.appSigningCertSha256,
        contentManifestHash = hash,
        verdict = "LICENSED",
        bigAssetsVerified = true,
    )

    // ── decideLocal ────────────────────────────────────────────────────────

    @Test
    fun `valid receipt + matching device + good manifest = ACTIVATED`() {
        val d = decideLocal(receipt(), snapshot(), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals(GateState.ACTIVATED, d.state)
        assertFalse(d.hard)
    }

    @Test
    fun `no receipt = NEEDS_ACTIVATION`() {
        val d = decideLocal(null, snapshot(), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals(GateState.NEEDS_ACTIVATION, d.state)
    }

    @Test
    fun `bypass short-circuits to ACTIVATED even with no receipt`() {
        val d = decideLocal(null, snapshot(), manifestOk = false, bypass = true, tripwiresLogOnly = false)
        assertEquals(GateState.ACTIVATED, d.state)
        assertEquals("BYPASS", d.reason)
    }

    @Test
    fun `tampered content = hard LOCKED`() {
        val d = decideLocal(receipt(), snapshot(), manifestOk = false, bypass = false, tripwiresLogOnly = false)
        assertEquals(GateState.LOCKED_REVALIDATE, d.state)
        assertEquals("CONTENT_TAMPERED", d.reason)
        assertTrue(d.hard)
    }

    @Test
    fun `device key changed = hard LOCKED`() {
        val d = decideLocal(receipt(), snapshot(fp = "FP-device-2"), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals(GateState.LOCKED_REVALIDATE, d.state)
        assertEquals("DEVICE_KEY_MISMATCH", d.reason)
        assertTrue(d.hard)
    }

    @Test
    fun `missing device key = hard LOCKED`() {
        val d = decideLocal(receipt(), snapshot(fp = null), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals("DEVICE_KEY_MISMATCH", d.reason)
        assertTrue(d.hard)
    }

    @Test
    fun `androidId changed (cloned data) = hard LOCKED`() {
        val d = decideLocal(receipt(), snapshot(androidId = "aidhash-2"), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals("ANDROID_ID_MISMATCH", d.reason)
        assertTrue(d.hard)
    }

    @Test
    fun `sideloaded installer = soft LOCKED when enforced`() {
        val d = decideLocal(receipt(), snapshot(installer = "com.android.shell"), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals(GateState.LOCKED_REVALIDATE, d.state)
        assertEquals("INSTALLER_NOT_PLAY", d.reason)
        assertFalse(d.hard)
    }

    @Test
    fun `sideloaded installer = ACTIVATED (logged) when log-only`() {
        val d = decideLocal(receipt(), snapshot(installer = null), manifestOk = true, bypass = false, tripwiresLogOnly = true)
        assertEquals(GateState.ACTIVATED, d.state)
        assertEquals("SOFT_LOGGED:INSTALLER_NOT_PLAY", d.reason)
    }

    @Test
    fun `re-signed cert = soft LOCKED when enforced`() {
        val d = decideLocal(receipt(), snapshot(cert = "CERT-2"), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals("SIGNING_CERT_MISMATCH", d.reason)
        assertFalse(d.hard)
    }

    @Test
    fun `hard violation beats soft when both present`() {
        // cert changed (soft) AND androidId changed (hard) → hard wins
        val d = decideLocal(receipt(), snapshot(cert = "CERT-2", androidId = "x"), manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals("ANDROID_ID_MISMATCH", d.reason)
        assertTrue(d.hard)
    }

    // ── decideHandshake ──────────────────────────────────────────────────────

    private fun verdict() = Verdict("LICENSED", "PLAY_RECOGNIZED", listOf("MEETS_DEVICE_INTEGRITY"))

    @Test
    fun `Activated with matching hash = SUCCESS`() {
        val d = decideHandshake(ActivationResult.Activated(1L, manifest, verdict(), null), manifest)
        assertEquals(GateState.SUCCESS, d.state)
    }

    @Test
    fun `Activated with mismatched hash echo = hard LOCKED`() {
        val d = decideHandshake(ActivationResult.Activated(1L, "deadbeef", verdict(), null), manifest)
        assertEquals(GateState.LOCKED_REVALIDATE, d.state)
        assertEquals("MANIFEST_ECHO_MISMATCH", d.reason)
        assertTrue(d.hard)
    }

    @Test
    fun `Denied UNLICENSED = UNLICENSED state`() {
        val d = decideHandshake(ActivationResult.Denied("UNLICENSED", 403), manifest)
        assertEquals(GateState.UNLICENSED, d.state)
    }

    @Test
    fun `Denied UNRECOGNIZED = UNLICENSED state`() {
        val d = decideHandshake(ActivationResult.Denied("UNRECOGNIZED", 403), manifest)
        assertEquals(GateState.UNLICENSED, d.state)
    }

    @Test
    fun `Denied DEVICE_INTEGRITY = hard LOCKED`() {
        val d = decideHandshake(ActivationResult.Denied("DEVICE_INTEGRITY", 403), manifest)
        assertEquals(GateState.LOCKED_REVALIDATE, d.state)
        assertTrue(d.hard)
    }

    @Test
    fun `Retryable = BLOCKED_NO_INTERNET`() {
        val d = decideHandshake(ActivationResult.Retryable("NETWORK_ERROR"), manifest)
        assertEquals(GateState.BLOCKED_NO_INTERNET, d.state)
        assertEquals("NETWORK_ERROR", d.reason)
    }

    // ── buildReceipt ──────────────────────────────────────────────────────────

    @Test
    fun `buildReceipt captures the snapshot + verdict`() {
        val snap = snapshot(fp = "FP-x", androidId = "aid-x", installer = INSTALLER_PLAY, cert = "C-x", sec = "STRONGBOX")
        val r = buildReceipt(snap, manifest, verdict(), bigAssetsVerified = true, now = 12345L)
        assertEquals("FP-x", r.deviceKeyFingerprint)
        assertEquals("aid-x", r.androidIdHash)
        assertEquals("STRONGBOX", r.deviceSecurityLevel)
        assertEquals(manifest, r.contentManifestHash)
        assertEquals("LICENSED", r.verdict)
        assertEquals(12345L, r.activatedAtEpoch)
        assertTrue(r.bigAssetsVerified)
    }

    @Test
    fun `receipt built then re-evaluated locally on same device = ACTIVATED`() {
        // round-trip: a receipt minted from a snapshot must pass decideLocal on that same snapshot.
        val snap = snapshot()
        val r = buildReceipt(snap, manifest, verdict(), bigAssetsVerified = true, now = 1L)
        val d = decideLocal(r, snap, manifestOk = true, bypass = false, tripwiresLogOnly = false)
        assertEquals(GateState.ACTIVATED, d.state)
    }
}
