/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.activation

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module ActivationApi.kt"

/**
 * OMEN-025 Phase 1 (Session 5) — thin transport over the activation Worker.
 * Mirrors `worker-activate/CONTRACT.md`:
 *   - `GET /nonce`     → [NonceResponse]
 *   - `POST /activate` → [ActivationResult]
 *
 * Orchestration (fetch nonce → mint Play Integrity token bound to
 * `requestHash = SHA256(nonce‖manifestHash)` → POST) lives in [PlayIntegrityClient]
 * + ActivationManager (Session 6). This class is pure transport — it never
 * computes the requestHash or touches the verdict logic, so it stays trivially
 * unit-testable via the pure [parseActivateResponse] / [parseNonceResponse]
 * functions (no MockWebServer needed; matches [ContentManifestVerifier] style).
 *
 * Error mapping (operator: no grace window → hard-block, Retry only):
 *   - HTTP 200 ok:true       → [ActivationResult.Activated]
 *   - HTTP 400 / 403         → [ActivationResult.Denied] (hard; UNLICENSED/LOCKED)
 *   - HTTP 5xx / unparseable → [ActivationResult.Retryable] (BLOCKED_NO_INTERNET)
 *   - IOException (no signal)→ caller maps to [ActivationResult.Retryable]
 */
class ActivationApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl,
    private val gson: Gson = Gson(),
) {

    /** GET /nonce. Throws [IOException] on transport failure (caller → Retryable). */
    @Throws(IOException::class)
    fun fetchNonce(): NonceResponse {
        val req = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("nonce").build())
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("nonce HTTP ${resp.code}")
            return parseNonceResponse(body)
                ?: throw IOException("nonce: unparseable body")
        }
    }

    /** POST /activate. Transport failures surface as [ActivationResult.Retryable]. */
    fun activate(integrityToken: String, contentManifestHash: String, nonce: String): ActivationResult {
        val payload = gson.toJson(
            ActivateRequest(
                integrityToken = integrityToken,
                contentManifestHash = contentManifestHash,
                nonce = nonce,
            ),
        )
        val req = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegment("activate").build())
            .post(payload.toRequestBody(JSON_MEDIA))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                parseActivateResponse(resp.code, resp.body?.string().orEmpty(), gson)
            }
        } catch (e: IOException) {
            ActivationResult.Retryable("NETWORK_ERROR")
        }
    }

    private fun parseNonceResponse(body: String): NonceResponse? = try {
        gson.fromJson(body, NonceResponse::class.java)?.takeIf { !it.nonce.isNullOrEmpty() }
    } catch (e: Exception) {
        null
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * Pure response parser — maps (httpCode, body) to an [ActivationResult].
         * Extracted so the full success/deny/retry matrix is JVM-testable without
         * a live server or MockWebServer.
         */
        fun parseActivateResponse(httpCode: Int, body: String, gson: Gson = Gson()): ActivationResult {
            return when {
                httpCode == 200 -> {
                    val ok = runCatching { gson.fromJson(body, ActivateResponse::class.java) }.getOrNull()
                    if (ok?.ok == true && !ok.manifestHash.isNullOrEmpty()) {
                        ActivationResult.Activated(
                            issuedAt = ok.issuedAt ?: 0L,
                            manifestHash = ok.manifestHash,
                            verdict = ok.verdict ?: Verdict(),
                            assertion = ok.assertion,
                        )
                    } else {
                        // 200 but malformed/ok:false — treat as retryable, not a hard deny.
                        ActivationResult.Retryable("MALFORMED_OK")
                    }
                }
                httpCode == 400 || httpCode == 403 -> {
                    val reason = runCatching { gson.fromJson(body, DenyResponse::class.java)?.reason }
                        .getOrNull() ?: "DENIED"
                    ActivationResult.Denied(reason, httpCode)
                }
                else -> {
                    // 5xx (incl. 502 DECODE_FAILED), 404, anything else → retryable.
                    val reason = runCatching { gson.fromJson(body, DenyResponse::class.java)?.reason }
                        .getOrNull() ?: "HTTP_$httpCode"
                    ActivationResult.Retryable(reason)
                }
            }
        }
    }
}

// ── Wire models (Gson by field name; R8 keep covers activation.**) ───────────

private data class ActivateRequest(
    @SerializedName("integrity_token") val integrityToken: String,
    @SerializedName("content_manifest_hash") val contentManifestHash: String,
    @SerializedName("nonce") val nonce: String,
)

data class NonceResponse(
    @SerializedName("nonce") val nonce: String? = null,
    @SerializedName("ttlMs") val ttlMs: Long = 0,
    @SerializedName("expiresAt") val expiresAt: Long = 0,
)

data class Verdict(
    @SerializedName("licensing") val licensing: String? = null,
    @SerializedName("appRecognition") val appRecognition: String? = null,
    @SerializedName("device") val device: List<String> = emptyList(),
)

private data class ActivateResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("issuedAt") val issuedAt: Long? = null,
    @SerializedName("manifestHash") val manifestHash: String? = null,
    @SerializedName("verdict") val verdict: Verdict? = null,
    @SerializedName("assertion") val assertion: String? = null,
)

private data class DenyResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("reason") val reason: String? = null,
)

/** Outcome of `POST /activate`. */
sealed class ActivationResult {
    /** Verdict passed — write the receipt and forward to Main. */
    data class Activated(
        val issuedAt: Long,
        val manifestHash: String,
        val verdict: Verdict,
        val assertion: String?,
    ) : ActivationResult()

    /** Hard denial (400/403) — UNLICENSED / LOCKED. [reason] from the contract table. */
    data class Denied(val reason: String, val httpStatus: Int) : ActivationResult()

    /** Transient (5xx / no signal) — maps to BLOCKED_NO_INTERNET, Retry only. */
    data class Retryable(val reason: String) : ActivationResult()
}
