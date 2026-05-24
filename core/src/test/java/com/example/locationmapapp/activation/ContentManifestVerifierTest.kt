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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.util.Base64

/**
 * OMEN-025 Phase 1 / OMEN-004 #3 (part 1) — JVM unit tests for
 * [ContentManifestVerifier]. Pure JVM (no Android framework): the verifier
 * takes injected lambdas + raw key bytes, so a throwaway RSA-2048 keypair and
 * an in-memory asset map exercise the full trust chain without a device.
 *
 * Required cases (per the plan's S3 step 4): sig-valid / sig-invalid /
 * byte-flipped-asset / stale-manifest. Plus a cross-check that the Kotlin
 * canonical [ContentManifestVerifier.canonicalManifestHash] byte-matches the
 * real `sign-content-manifest.js` output, and a verifyFull bulk-asset path.
 */
class ContentManifestVerifierTest {

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    private val pubKeyDer: ByteArray = keyPair.public.encoded // SPKI DER, like res/raw

    /** Small stand-in content assets. */
    private val contentDb = "salem_content.db".toByteArray() + ByteArray(2048) { (it % 251).toByte() }
    private val splash = "{\"splash\":true}".toByteArray()

    private fun sha256Hex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val triples = listOf(
        Triple("salem_content.db", sha256Hex(contentDb), contentDb.size.toLong()),
        Triple("splash_tree_v1.json", sha256Hex(splash), splash.size.toLong()),
    )

    /** Build manifest JSON bytes (any valid JSON — the sig covers these exact bytes). */
    private fun manifestJson(
        files: List<Triple<String, String, Long>> = triples,
        manifestHash: String = ContentManifestVerifier.canonicalManifestHashOf(files),
        large: List<Map<String, Any>> = emptyList(),
    ): ByteArray {
        val m = linkedMapOf<String, Any>(
            "schemaVersion" to 1,
            "roomVersion" to 23,
            "roomIdentityHash" to "4db15f763c9dbd5a529d24b128cecada",
            "files" to files.map { mapOf("path" to it.first, "sha256" to it.second, "bytes" to it.third) },
            "manifestHash" to manifestHash,
            "unverifiedLarge" to large,
        )
        return Gson().toJson(m).toByteArray(Charsets.UTF_8)
    }

    private fun sign(bytes: ByteArray): String {
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update(bytes)
        }.sign()
        return Base64.getEncoder().encodeToString(sig)
    }

    /** Verifier wired to an in-memory asset map. */
    private fun verifier(
        manifest: ByteArray,
        sigB64: String,
        assets: Map<String, ByteArray> = mapOf(
            "salem_content.db" to contentDb,
            "splash_tree_v1.json" to splash,
        ),
        largeStat: (String) -> ContentManifestVerifier.LargeAssetStat? = { null },
    ): ContentManifestVerifier {
        val all = assets +
            mapOf(
                ContentManifestVerifier.MANIFEST_NAME to manifest,
                ContentManifestVerifier.SIG_NAME to (sigB64 + "\n").toByteArray(),
            )
        val opener: (String) -> InputStream = { name ->
            ByteArrayInputStream(all[name] ?: error("missing asset $name"))
        }
        return ContentManifestVerifier(pubKeyDer, opener, largeStat)
    }

    // ── Tests ───────────────────────────────────────────────────────────────

    @Test fun sigValid_verifyCheapPasses() {
        val manifest = manifestJson()
        val result = verifier(manifest, sign(manifest)).verifyCheap()
        assertTrue("expected Ok, got $result", result is ContentManifestVerifier.Result.Ok)
        result as ContentManifestVerifier.Result.Ok
        assertEquals(ContentManifestVerifier.canonicalManifestHashOf(triples), result.manifestHash)
        assertEquals(23, result.roomVersion)
    }

    @Test fun sigInvalid_isRejected() {
        val manifest = manifestJson()
        val good = Base64.getDecoder().decode(sign(manifest))
        good[good.size - 1] = (good.last().toInt() xor 0xFF).toByte() // corrupt last sig byte
        val result = verifier(manifest, Base64.getEncoder().encodeToString(good)).verifyCheap()
        assertTrue(result is ContentManifestVerifier.Result.Fail)
        assertTrue((result as ContentManifestVerifier.Result.Fail).reason.contains("signature"))
    }

    @Test fun byteFlippedAsset_isRejected() {
        val manifest = manifestJson()
        val sig = sign(manifest)
        // Flip one byte of salem_content.db AFTER the manifest was signed.
        val tampered = contentDb.copyOf().also { it[100] = (it[100].toInt() xor 0x01).toByte() }
        val result = verifier(
            manifest, sig,
            assets = mapOf("salem_content.db" to tampered, "splash_tree_v1.json" to splash),
        ).verifyCheap()
        assertTrue(result is ContentManifestVerifier.Result.Fail)
        assertTrue((result as ContentManifestVerifier.Result.Fail).reason.contains("tampered"))
    }

    @Test fun staleManifestHash_isRejected() {
        // manifestHash field is wrong, but the sig is valid over this (wrong) body —
        // step-4 self-consistency must still catch it.
        val manifest = manifestJson(manifestHash = "deadbeef".repeat(8))
        val result = verifier(manifest, sign(manifest)).verifyCheap()
        assertTrue(result is ContentManifestVerifier.Result.Fail)
        assertTrue((result as ContentManifestVerifier.Result.Fail).reason.contains("manifestHash mismatch"))
    }

    @Test fun tamperedManifestBody_failsSignature() {
        val manifest = manifestJson()
        val sig = sign(manifest)
        val tamperedBody = manifest.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
        val result = verifier(tamperedBody, sig).verifyCheap()
        assertTrue(result is ContentManifestVerifier.Result.Fail)
        assertTrue((result as ContentManifestVerifier.Result.Fail).reason.contains("signature"))
    }

    @Test fun emptySignature_isRejected() {
        val manifest = manifestJson()
        val result = verifier(manifest, "").verifyCheap()
        assertTrue(result is ContentManifestVerifier.Result.Fail)
        assertTrue((result as ContentManifestVerifier.Result.Fail).reason.contains("empty"))
    }

    @Test fun verifyFull_bulkSizeMatch_ok_butTileDbDeferred() {
        val large = listOf(
            mapOf("path" to "heroes", "fileCount" to 3, "bytes" to 900L),
            mapOf("path" to "salem_tiles.sqlite", "bytes" to 370483200L, "note" to "asset pack"),
        )
        val manifest = manifestJson(large = large)
        val result = verifier(manifest, sign(manifest), largeStat = { p ->
            when (p) {
                "heroes" -> ContentManifestVerifier.LargeAssetStat(fileCount = 3, bytes = 900L)
                else -> null // tile DB not reachable from the main APK assets
            }
        }).verifyFull()
        assertTrue("expected Ok, got $result", result is ContentManifestVerifier.Result.Ok)
        // tile DB unreachable → largeVerified false, but verification still passes.
        assertEquals(false, (result as ContentManifestVerifier.Result.Ok).largeVerified)
    }

    @Test fun verifyFull_bulkSizeDrift_isRejected() {
        val large = listOf(mapOf("path" to "heroes", "fileCount" to 3, "bytes" to 900L))
        val manifest = manifestJson(large = large)
        val result = verifier(manifest, sign(manifest), largeStat = {
            ContentManifestVerifier.LargeAssetStat(fileCount = 3, bytes = 901L) // off by one byte
        }).verifyFull()
        assertTrue(result is ContentManifestVerifier.Result.Fail)
        assertTrue((result as ContentManifestVerifier.Result.Fail).reason.contains("size drift"))
    }

    /**
     * Cross-check the Kotlin canonical hash against the REAL build value from
     * S293 (`sign-content-manifest.js` printed `manifestHash = 978937…`). This is
     * the contract that guarantees the on-device manifestHash equals the value
     * baked into the Worker's EXPECTED_MANIFEST_HASHES.
     */
    @Test fun canonicalHash_matchesRealBuildValue() {
        val realFiles = listOf(
            Triple("salem_content.db", "0f8952ea909e850a46c7f07268fe8490cfd607c893e9c53cc42af1378774ef54", 5480448L),
            Triple("splash_tree_v1.json", "ce64e0ab25f423348051950f80add1f5dfb67cc7c5ebb0edbf5b03fd0235779f", 1748L),
            Triple("us_places_v1.sqlite", "9b805adb6067525bcaf7318ce86fa8c04e68142a9c4af428b236c3d3fa0c2194", 2187264L),
        )
        assertEquals(
            "978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28",
            ContentManifestVerifier.canonicalManifestHashOf(realFiles),
        )
    }
}
