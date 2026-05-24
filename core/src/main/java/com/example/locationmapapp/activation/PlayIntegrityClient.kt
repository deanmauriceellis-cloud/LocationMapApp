/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.activation

import android.content.Context
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityToken
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.IntegrityManagerFactory
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module PlayIntegrityClient.kt"

/**
 * OMEN-025 Phase 1 (Session 6) — Play Integrity **Standard** request wrapper.
 *
 * Standard requests carry a `requestHash` (not a nonce field). We bind the
 * server-issued nonce + the content manifest hash into that requestHash so the
 * Worker can prove freshness without storing per-device state:
 *
 *   `requestHash = base64url( SHA-256( utf8(nonce) ‖ utf8(manifestHash) ) )`  (no padding)
 *
 * The Worker re-derives the identical value (`worker-activate/src/integrity.js`
 * `requestHash`) and checks the decoded token's `requestDetails.requestHash`
 * matches. [computeRequestHash] is the load-bearing contract — it MUST produce
 * the byte-identical string the Worker computes; a cross-check unit test pins it
 * to the real Worker output (mirrors the S295 manifestHash cross-check).
 *
 * The device-side token request needs Play services + the Google Cloud project
 * number (gated on Play Console setup), so [requestToken] is Android-only and
 * untested at the JVM tier; [computeRequestHash] is pure and unit-tested.
 */
class PlayIntegrityClient(
    private val context: Context,
    /**
     * Google Cloud project number linked to the Play Console app. **Wire the real
     * value here (or via `BuildConfig`) once the Play Integrity API is enabled in
     * the linked GCP project** (Session 0 prereq #2, operator-gated).
     */
    private val cloudProjectNumber: Long = CLOUD_PROJECT_NUMBER_PLACEHOLDER,
) {

    private var provider: StandardIntegrityTokenProvider? = null

    /** Warm up the token provider once (cheap to call repeatedly — caches). */
    suspend fun prepare() {
        if (provider != null) return
        val manager: StandardIntegrityManager = IntegrityManagerFactory.createStandard(context)
        val request = PrepareIntegrityTokenRequest.builder()
            .setCloudProjectNumber(cloudProjectNumber)
            .build()
        provider = manager.prepareIntegrityToken(request).await()
    }

    /**
     * Request a Standard integrity token bound to [nonce] + [manifestHash].
     * Returns the opaque token string to POST to the Worker `/activate`.
     */
    suspend fun requestToken(nonce: String, manifestHash: String): String {
        prepare()
        val p = provider ?: error("integrity token provider not prepared")
        val request = StandardIntegrityTokenRequest.builder()
            .setRequestHash(computeRequestHash(nonce, manifestHash))
            .build()
        val token: StandardIntegrityToken = p.request(request).await()
        return token.token()
    }

    companion object {
        /** Placeholder — replace with the real linked GCP project number at wiring time. */
        const val CLOUD_PROJECT_NUMBER_PLACEHOLDER: Long = 0L

        /**
         * `base64url( SHA-256( utf8(nonce) ‖ utf8(manifestHash) ) )`, no padding.
         * Byte-identical to the Worker's `requestHash` — pinned by cross-check test.
         */
        fun computeRequestHash(nonce: String, manifestHash: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest((nonce + manifestHash).toByteArray(Charsets.UTF_8))
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }
    }
}

/** Bridge a Play-services [com.google.android.gms.tasks.Task] to a coroutine. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
