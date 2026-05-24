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
import java.io.InputStream
import java.security.DigestInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module ContentManifestVerifier.kt"

/**
 * OMEN-025 Phase 1 — runtime verification of the signed content manifest
 * (Layer 2, content integrity). Counterpart to `sign-content-manifest.js`.
 *
 * The build emits two committed assets:
 *  - `content-manifest.json` — SHA-256 of each in-scope content asset, plus a
 *    canonical [ContentManifest.manifestHash] and a presence/size record of the
 *    bulk assets ([ContentManifest.unverifiedLarge], D4).
 *  - `content-manifest.sig`  — detached base64 RSASSA-PKCS1-v1_5/SHA-256
 *    signature over the EXACT manifest bytes, made with the operator's offline
 *    RSA-2048 private key.
 *
 * The app embeds the matching public key (`res/raw/content_manifest_pubkey.der`,
 * SPKI DER). A bare hash in the DB is patchable; a *signed* manifest is not — a
 * tamperer cannot forge fresh valid hashes without the offline private key. So
 * the chain of trust is: embedded pubkey → verifies the manifest signature →
 * the manifest's per-file hashes → recomputed against the bytes actually on disk.
 *
 * **Tiered hashing (D4):**
 *  - [verifyCheap] runs EVERY launch. Signature verify + recompute the in-scope
 *    file hashes (`salem_content.db` is the high-value tamper anchor, ~5 MB →
 *    sub-second even on the Lenovo min-spec floor) + recompute [manifestHash].
 *  - [verifyFull] runs at activation only. Adds a presence + gross-size check of
 *    the bulk assets (354 MB tile DB + image dirs). Per-file content hashing of
 *    those is deferred to Phase 2 (the manifest records size only, by design),
 *    so [verifyFull] confirms presence/size, not byte-exact content, for them.
 *
 * **JVM-testable by construction.** All inputs are injected lambdas/bytes — no
 * Android `Context`, `AssetManager`, or `android.util.Log` on the verified path
 * (logging is an optional callback). The production wiring lives in
 * [forAndroidAssets]; unit tests drive the primary constructor directly.
 */
class ContentManifestVerifier(
    /** SPKI DER bytes of the RSA-2048 public key (`res/raw/content_manifest_pubkey.der`). */
    private val publicKeyDer: ByteArray,
    /** Opens a bundled asset by name (manifest, sig, and each in-scope content file). */
    private val openAsset: (name: String) -> InputStream,
    /**
     * Optional stat of a bulk asset by manifest path, for [verifyFull]. Returns
     * file count (null for single files) + total bytes, or null if the asset is
     * not reachable from here (e.g. the tile DB lives in the install-time asset
     * pack). Default returns null for everything → bulk checks are skipped.
     */
    private val largeAssetStat: (path: String) -> LargeAssetStat? = { null },
    /** Optional debug log sink. Kept off the hot path so JVM tests need no Robolectric. */
    private val log: (String) -> Unit = {},
) {

    /** Recomputed presence/size of a bulk asset for [verifyFull]. */
    data class LargeAssetStat(val fileCount: Int?, val bytes: Long)

    sealed class Result {
        /**
         * Verification passed. [manifestHash] is the value to feed the activation
         * handshake (`requestHash = SHA256(nonce || manifestHash)`); [largeVerified]
         * is true only when [verifyFull] confirmed every reachable bulk asset.
         */
        data class Ok(
            val manifestHash: String,
            val roomVersion: Int,
            val roomIdentityHash: String,
            val largeVerified: Boolean,
        ) : Result()

        /** Verification failed. [reason] is a non-PII diagnostic, safe to log. */
        data class Fail(val reason: String) : Result()
    }

    // ── Manifest model (Gson by field name — keep names matching the JS emitter) ──
    private data class FileEntry(val path: String, val sha256: String, val bytes: Long)
    private data class LargeEntry(
        val path: String,
        val bytes: Long = 0,
        val fileCount: Int? = null,
        val note: String? = null,
    )
    private data class ContentManifest(
        val schemaVersion: Int = 0,
        val roomVersion: Int = 0,
        val roomIdentityHash: String = "",
        val files: List<FileEntry> = emptyList(),
        val manifestHash: String = "",
        val unverifiedLarge: List<LargeEntry> = emptyList(),
    )

    /** Signature verify + in-scope file hashes. Cheap; run on every launch. */
    fun verifyCheap(): Result = verify(full = false)

    /** [verifyCheap] plus presence/size of the bulk assets. Run at activation. */
    fun verifyFull(): Result = verify(full = true)

    private fun verify(full: Boolean): Result {
        // 1. Read the exact manifest + signature bytes (the sig covers these bytes verbatim).
        val manifestBytes = try {
            openAsset(MANIFEST_NAME).use { it.readBytes() }
        } catch (t: Throwable) {
            return fail("manifest asset unreadable: ${t.message}")
        }
        val sigB64 = try {
            openAsset(SIG_NAME).use { it.readBytes() }.toString(Charsets.UTF_8).trim()
        } catch (t: Throwable) {
            return fail("manifest signature asset unreadable: ${t.message}")
        }
        if (sigB64.isEmpty()) return fail("manifest signature is empty (unsigned manifest — CI bake never ships)")

        // 2. Verify the signature against the embedded public key.
        val sigBytes = try {
            Base64.getDecoder().decode(sigB64)
        } catch (t: Throwable) {
            return fail("manifest signature not valid base64")
        }
        val pubKey = try {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKeyDer))
        } catch (t: Throwable) {
            return fail("embedded public key unloadable: ${t.message}")
        }
        val sigOk = try {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(pubKey)
                update(manifestBytes)
                verify(sigBytes)
            }
        } catch (t: Throwable) {
            return fail("signature verify threw: ${t.message}")
        }
        if (!sigOk) return fail("manifest signature does NOT verify against embedded public key")

        // 3. Parse the (now-trusted) manifest.
        val manifest = try {
            Gson().fromJson(manifestBytes.toString(Charsets.UTF_8), ContentManifest::class.java)
        } catch (t: Throwable) {
            return fail("manifest JSON parse failed: ${t.message}")
        }
        if (manifest.files.isEmpty()) return fail("manifest has no in-scope files")

        // 4. Self-consistency: the embedded manifestHash must match a fresh canonical
        //    recompute of the files array (catches a stale/edited manifestHash field).
        val recomputed = canonicalManifestHash(manifest.files)
        if (!recomputed.equals(manifest.manifestHash, ignoreCase = true)) {
            return fail("manifestHash mismatch: embedded=${manifest.manifestHash} recomputed=$recomputed")
        }

        // 5. Recompute each in-scope file's SHA-256 from the bytes actually on disk.
        for (f in manifest.files) {
            val actual = try {
                openAsset(f.path).use { sha256HexStreaming(it) }
            } catch (t: Throwable) {
                return fail("in-scope asset unreadable: ${f.path}: ${t.message}")
            }
            if (!actual.equals(f.sha256, ignoreCase = true)) {
                return fail("content tampered: ${f.path} sha256 ${actual.take(16)}… != manifest ${f.sha256.take(16)}…")
            }
            log("verified ${f.path} (${f.bytes} bytes)")
        }

        // 6. verifyFull only — presence + gross-size of the bulk assets (D4). The
        //    manifest records size, not per-file hashes, for these; Phase 2 adds
        //    chunked content hashing. We confirm what is reachable from here.
        var largeVerified = true
        if (full) {
            for (e in manifest.unverifiedLarge) {
                val stat = largeAssetStat(e.path)
                if (stat == null) {
                    // Not reachable here (e.g. tile DB in the install-time asset pack).
                    log("bulk asset not reachable for full-verify, skipped: ${e.path}")
                    largeVerified = false
                    continue
                }
                if (stat.bytes != e.bytes) {
                    return fail("bulk asset size drift: ${e.path} ${stat.bytes} != manifest ${e.bytes}")
                }
                if (e.fileCount != null && stat.fileCount != null && stat.fileCount != e.fileCount) {
                    return fail("bulk asset file-count drift: ${e.path} ${stat.fileCount} != manifest ${e.fileCount}")
                }
                log("bulk asset present: ${e.path} (${e.bytes} bytes)")
            }
        }

        return Result.Ok(
            manifestHash = manifest.manifestHash,
            roomVersion = manifest.roomVersion,
            roomIdentityHash = manifest.roomIdentityHash,
            largeVerified = largeVerified,
        )
    }

    private fun fail(reason: String): Result.Fail {
        log("FAIL: $reason")
        return Result.Fail(reason)
    }

    companion object {
        private const val TAG = "ContentManifestVerifier"
        const val MANIFEST_NAME = "content-manifest.json"
        const val SIG_NAME = "content-manifest.sig"

        /**
         * Canonical manifestHash — MUST byte-match `sign-content-manifest.js`
         * `manifestHashOf()`: `JSON.stringify` of the files array sorted by path,
         * each object as exactly `{"path":…,"sha256":…,"bytes":N}` with no
         * whitespace, then SHA-256 (lowercase hex). Verified against the real
         * build value in unit tests.
         */
        private fun canonicalManifestHash(files: List<FileEntry>): String {
            val sorted = files.sortedWith(compareBy { it.path })
            val sb = StringBuilder("[")
            sorted.forEachIndexed { i, f ->
                if (i > 0) sb.append(',')
                sb.append("{\"path\":").append(jsonString(f.path))
                    .append(",\"sha256\":").append(jsonString(f.sha256))
                    .append(",\"bytes\":").append(f.bytes)
                    .append('}')
            }
            sb.append(']')
            return sha256Hex(sb.toString().toByteArray(Charsets.UTF_8))
        }

        /**
         * JSON string escaping matching JS JSON.stringify. Manifest paths are
         * plain filenames and sha256 fields are lowercase hex, so only `"` and
         * `\` can realistically occur; control chars get a defensive `\uXXXX`.
         */
        private fun jsonString(s: String): String {
            val sb = StringBuilder("\"")
            for (c in s) {
                when {
                    c == '"' -> sb.append("\\\"")
                    c == '\\' -> sb.append("\\\\")
                    c == '\n' -> sb.append("\\n")
                    c == '\r' -> sb.append("\\r")
                    c == '\t' -> sb.append("\\t")
                    c < ' ' -> sb.append("\\u%04x".format(c.code))
                    else -> sb.append(c)
                }
            }
            return sb.append('"').toString()
        }

        private fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        private fun sha256HexStreaming(input: InputStream): String {
            val md = MessageDigest.getInstance("SHA-256")
            val dis = DigestInputStream(input, md)
            val buf = ByteArray(64 * 1024)
            while (dis.read(buf) != -1) { /* drain through the digest */ }
            return md.digest().toHex()
        }

        private fun ByteArray.toHex(): String {
            val out = CharArray(size * 2)
            val hex = "0123456789abcdef"
            for (i in indices) {
                val v = this[i].toInt() and 0xFF
                out[i * 2] = hex[v ushr 4]
                out[i * 2 + 1] = hex[v and 0x0F]
            }
            return String(out)
        }

        /**
         * Test/diagnostic helper — canonical manifestHash from raw (path, sha256,
         * bytes) tuples without exposing the private [FileEntry] type.
         */
        internal fun canonicalManifestHashOf(triples: List<Triple<String, String, Long>>): String =
            canonicalManifestHash(triples.map { FileEntry(it.first, it.second, it.third) })

        /**
         * Production factory. Reads the embedded RSA public key from a raw
         * resource (`R.raw.content_manifest_pubkey`, SPKI DER — passed by the
         * app module since the resource lives in `:app-salem`, not `:core`) and
         * opens content assets through the app's `AssetManager`. Bulk-asset
         * full-verify is left at the default (deferred to Phase 2; the tile DB
         * lives in the install-time asset pack and image dirs are size-only).
         *
         * @param pubKeyRawResId e.g. `R.raw.content_manifest_pubkey`
         */
        fun forAndroidAssets(
            context: android.content.Context,
            pubKeyRawResId: Int,
            log: (String) -> Unit = {},
        ): ContentManifestVerifier {
            val der = context.resources.openRawResource(pubKeyRawResId).use { it.readBytes() }
            return ContentManifestVerifier(
                publicKeyDer = der,
                openAsset = { name -> context.assets.open(name) },
                log = log,
            )
        }
    }
}
