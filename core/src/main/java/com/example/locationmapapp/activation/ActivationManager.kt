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
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module ActivationManager.kt"

/**
 * OMEN-025 Phase 1 (Session 6) — the activation state machine + orchestration.
 *
 * Two paths:
 *  - [evaluateLocal] runs EVERY launch, fully offline (few ms): load the
 *    device-key-signed receipt, [verifyCheap] the content manifest, and check
 *    the tripwires. An already-activated install resolves to [GateState.ACTIVATED]
 *    here without a single socket — the offline-forever promise (decision D3).
 *  - [runHandshake] runs ONLY when there is no valid receipt (first run) or a
 *    hard violation forced re-validation: fetch a nonce, mint a Play Integrity
 *    token bound to `requestHash = SHA256(nonce‖manifestHash)`, POST to the
 *    Worker, and on a passing verdict persist a fresh receipt.
 *
 * **Decoupled + testable.** All Android-touching work is injected
 * ([verifyCheap]/[verifyFull] from [ContentManifestVerifier], [snapshot] from the
 * app layer, [store]/[integrity]/[api]) and the bypass/log-only switches are
 * passed in (the `BuildDefaults.ACTIVATION_*` consts live in `:app-salem`, so
 * `:core` never references them — same boundary as `ContentManifestVerifier.forAndroidAssets`).
 * The decision logic is the PURE companion ([decideLocal]/[decideHandshake]/
 * [buildReceipt]), driven directly by `ActivationManagerTest` — no Android, no
 * Robolectric, no live Worker.
 */
class ActivationManager(
    private val store: ActivationStore,
    private val integrity: PlayIntegrityClient,
    private val api: ActivationApi,
    /** Cheap per-launch manifest check (sig + in-scope hashes). */
    private val verifyCheap: () -> ContentManifestVerifier.Result,
    /** Full activation-time manifest check (adds bulk-asset presence/size). */
    private val verifyFull: () -> ContentManifestVerifier.Result,
    /** Current device fingerprint material, gathered by the app layer. */
    private val snapshot: () -> DeviceSnapshot,
    /** `BuildDefaults.ACTIVATION_BYPASS` — debug-only, const-folds out in release. */
    private val bypass: Boolean,
    /** `BuildDefaults.ACTIVATION_TRIPWIRES_LOG_ONLY` — debug-only. */
    private val tripwiresLogOnly: Boolean,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /** Offline launch check. No network. */
    fun evaluateLocal(): GateDecision {
        val cheap = verifyCheap()
        val receipt = store.load()
        return decideLocal(
            receipt = receipt,
            current = snapshot(),
            manifestOk = cheap is ContentManifestVerifier.Result.Ok,
            bypass = bypass,
            tripwiresLogOnly = tripwiresLogOnly,
        )
    }

    /** One-time online handshake. Persists a receipt on success; clears on hard denial. */
    suspend fun runHandshake(): GateDecision {
        // Content must verify first — its hash binds the integrity requestHash.
        val full = verifyFull()
        if (full !is ContentManifestVerifier.Result.Ok) {
            return GateDecision(GateState.LOCKED_REVALIDATE, "CONTENT_TAMPERED", hard = true)
        }
        val manifestHash = full.manifestHash

        val nonce = try {
            api.fetchNonce().nonce
        } catch (e: Exception) {
            return GateDecision(GateState.BLOCKED_NO_INTERNET, "NONCE_FETCH_FAILED")
        }
        if (nonce.isNullOrEmpty()) return GateDecision(GateState.BLOCKED_NO_INTERNET, "NONCE_EMPTY")

        val token = try {
            integrity.requestToken(nonce, manifestHash)
        } catch (e: Exception) {
            // Play Integrity warmup/request can fail with no/poor signal → retryable.
            return GateDecision(GateState.BLOCKED_NO_INTERNET, "INTEGRITY_TOKEN_FAILED")
        }

        val result = api.activate(token, manifestHash, nonce)
        val decision = decideHandshake(result, manifestHash)

        when {
            decision.state == GateState.SUCCESS && result is ActivationResult.Activated -> {
                store.save(
                    buildReceipt(
                        current = snapshot(),
                        manifestHash = manifestHash,
                        verdict = result.verdict,
                        bigAssetsVerified = true, // verifyFull passed above
                        now = now(),
                    ),
                )
            }
            decision.hard -> store.clear() // hard denial → force a clean re-handshake next time
        }
        return decision
    }

    companion object {
        /** The only legitimate installer for a paid Play distribution. */
        const val INSTALLER_PLAY = "com.android.vending"

        /**
         * PURE offline decision. Hard violations (no/changed device key, changed
         * ANDROID_ID, tampered content) → LOCKED_REVALIDATE(hard). Soft tripwires
         * (sideloaded installer, re-signed cert) → LOCKED_REVALIDATE(soft) unless
         * [tripwiresLogOnly]. [bypass] short-circuits to ACTIVATED (debug only).
         */
        fun decideLocal(
            receipt: ActivationReceipt?,
            current: DeviceSnapshot,
            manifestOk: Boolean,
            bypass: Boolean,
            tripwiresLogOnly: Boolean,
        ): GateDecision {
            if (bypass) return GateDecision(GateState.ACTIVATED, "BYPASS")
            if (receipt == null) return GateDecision(GateState.NEEDS_ACTIVATION)
            if (!manifestOk) return GateDecision(GateState.LOCKED_REVALIDATE, "CONTENT_TAMPERED", hard = true)

            // hard tripwires — device binding
            if (current.deviceKeyFingerprint.isNullOrEmpty() ||
                receipt.deviceKeyFingerprint != current.deviceKeyFingerprint
            ) {
                return GateDecision(GateState.LOCKED_REVALIDATE, "DEVICE_KEY_MISMATCH", hard = true)
            }
            if (receipt.androidIdHash != current.androidIdHash) {
                return GateDecision(GateState.LOCKED_REVALIDATE, "ANDROID_ID_MISMATCH", hard = true)
            }

            // soft tripwires — sideload / repackage heuristics
            val softReason = when {
                receipt.appSigningCertSha256 != current.appSigningCertSha256 -> "SIGNING_CERT_MISMATCH"
                current.installerPackage != INSTALLER_PLAY -> "INSTALLER_NOT_PLAY"
                else -> null
            }
            if (softReason != null && !tripwiresLogOnly) {
                return GateDecision(GateState.LOCKED_REVALIDATE, softReason, hard = false)
            }
            return GateDecision(GateState.ACTIVATED, softReason?.let { "SOFT_LOGGED:$it" })
        }

        /**
         * PURE mapping of a Worker [ActivationResult] to a gate decision.
         * The 200-ok manifestHash MUST echo the value we sent (the Worker already
         * checked it against EXPECTED_MANIFEST_HASHES; a mismatch here means a
         * MITM/confused response → hard lock).
         */
        fun decideHandshake(result: ActivationResult, expectedManifestHash: String): GateDecision =
            when (result) {
                is ActivationResult.Activated ->
                    if (result.manifestHash == expectedManifestHash) {
                        GateDecision(GateState.SUCCESS)
                    } else {
                        GateDecision(GateState.LOCKED_REVALIDATE, "MANIFEST_ECHO_MISMATCH", hard = true)
                    }
                is ActivationResult.Denied -> when (result.reason) {
                    "UNLICENSED", "UNRECOGNIZED" -> GateDecision(GateState.UNLICENSED, result.reason)
                    else -> GateDecision(GateState.LOCKED_REVALIDATE, result.reason, hard = true)
                }
                is ActivationResult.Retryable -> GateDecision(GateState.BLOCKED_NO_INTERNET, result.reason)
            }

        /** PURE — assemble the receipt to persist after a passing handshake. */
        fun buildReceipt(
            current: DeviceSnapshot,
            manifestHash: String,
            verdict: Verdict,
            bigAssetsVerified: Boolean,
            now: Long,
        ): ActivationReceipt = ActivationReceipt(
            activatedAtEpoch = now,
            deviceKeyFingerprint = current.deviceKeyFingerprint.orEmpty(),
            deviceSecurityLevel = current.deviceSecurityLevel,
            androidIdHash = current.androidIdHash,
            installerPackage = current.installerPackage,
            appSigningCertSha256 = current.appSigningCertSha256,
            contentManifestHash = manifestHash,
            verdict = verdict.licensing ?: "UNKNOWN",
            bigAssetsVerified = bigAssetsVerified,
        )
    }
}

/** The five UI-facing gate states (+ the two internal transients). */
enum class GateState {
    /** Local receipt valid (or bypass) → forward to Main. */
    ACTIVATED,
    /** No receipt — run the one-time handshake. */
    NEEDS_ACTIVATION,
    /** Handshake just passed — write receipt, forward to Main. */
    SUCCESS,
    /** No/poor signal or transient server error — hard-block, Retry only. */
    BLOCKED_NO_INTERNET,
    /** Verdict says not a genuine Play purchase. Terminal-ish. */
    UNLICENSED,
    /** Local hard violation or non-license denial — re-handshake to recover. */
    LOCKED_REVALIDATE,
}

/**
 * Device fingerprint material the app layer gathers and feeds to [ActivationManager].
 * Kept as a plain data class so [ActivationManager.decideLocal] stays pure/JVM-testable.
 */
data class DeviceSnapshot(
    val deviceKeyFingerprint: String?,
    val androidIdHash: String,
    val installerPackage: String?,
    val appSigningCertSha256: String,
    val deviceSecurityLevel: String,
)

/** A gate outcome + the reason (for the UI + DebugLogger) + whether it was a hard violation. */
data class GateDecision(val state: GateState, val reason: String? = null, val hard: Boolean = false)
