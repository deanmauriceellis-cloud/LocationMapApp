/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.activation

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module ActivationReceipt.kt"

/**
 * OMEN-025 Phase 1 — the locally-persisted proof that this install activated
 * once online on this device. Written after a passing Play Integrity verdict,
 * verified on every launch (offline) so the app is offline-forever after the
 * single handshake.
 *
 * Stored in [ActivationStore] (EncryptedSharedPreferences) AND detached-signed
 * by [DeviceKeyManager] — so it is tamper-evident even if the prefs file is
 * lifted to another device (the other secure element can't reproduce the
 * signature, and [deviceKeyFingerprint] won't match).
 *
 * Field semantics (each is a violation tripwire checked by ActivationManager):
 *  - [deviceKeyFingerprint]   changes  → hard violation (key regenerated / new device)
 *  - [androidIdHash]          changes  → hard violation (data cloned / reset)
 *  - [installerPackage]       != Play  → soft tripwire (sideloaded)
 *  - [appSigningCertSha256]   changes  → soft tripwire (repackaged/re-signed)
 *  - [contentManifestHash]    drift     → integrity violation (content tampered)
 *  - [bigAssetsVerified]      D4 — large assets verified in full at activation;
 *                                       per-launch uses the cheap size/mtime proxy.
 *
 * Serialized with Gson by field name — keep names stable (R8 keep rule covers
 * the `activation.**` package).
 */
data class ActivationReceipt(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val activatedAtEpoch: Long,
    val deviceKeyFingerprint: String,
    val deviceSecurityLevel: String,
    val androidIdHash: String,
    val installerPackage: String?,
    val appSigningCertSha256: String,
    val contentManifestHash: String,
    val verdict: String,
    val bigAssetsVerified: Boolean = false,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
