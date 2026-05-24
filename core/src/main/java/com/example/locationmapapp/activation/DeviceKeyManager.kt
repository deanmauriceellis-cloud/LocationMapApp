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
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.example.locationmapapp.util.DebugLogger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module DeviceKeyManager.kt"

/**
 * OMEN-025 Phase 1 — hardware-backed device-binding key.
 *
 * A non-exportable EC P-256 signing key generated inside the Android Keystore
 * (StrongBox secure element where available — API 28+ with
 * [PackageManager.FEATURE_STRONGBOX_KEYSTORE] — else the TEE, which minSdk 26
 * supports). The private key never leaves secure hardware.
 *
 * **Device-bound, NOT user-auth-bound** ([KeyGenParameterSpec.Builder.setUserAuthenticationRequired]
 * `= false`): the key survives reboots, biometric-enrollment changes, and OS
 * updates, and is destroyed only on factory reset / app uninstall / Keystore
 * clear / a different device. That is exactly the "this is the same paid device"
 * signal we want — copying app data to another phone leaves the receipt
 * unverifiable because the other secure element holds a different key.
 *
 * Phase-1 use: sign the local [ActivationReceipt] so it is tamper-evident and
 * device-bound. **Phase-2 hook (additive, do NOT overload this key):** a
 * *separate* `PURPOSE_ENCRYPT | PURPOSE_DECRYPT` wrapping key will wrap the
 * AES content key; keeping signing and wrapping keys distinct is the standard
 * hygiene and keeps Phase 2 a pure addition.
 */
object DeviceKeyManager {

    private const val TAG = "DeviceKeyManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "omen025_device_key"
    private const val SIG_ALGORITHM = "SHA256withECDSA"

    /** Where the key physically lives — surfaced for the receipt + logging. */
    enum class SecurityLevel { STRONGBOX, TEE, SOFTWARE, UNKNOWN }

    /**
     * Idempotently ensure the device key exists, generating it on first call.
     * Returns the [SecurityLevel] the key is backed by. Safe to call on every
     * launch — generation happens only once.
     */
    @Synchronized
    fun ensureKey(context: Context): SecurityLevel {
        val ks = keystore()
        if (!ks.containsAlias(ALIAS)) {
            generate(context)
        }
        return securityLevel()
    }

    /** Sign [data] with the device key. Throws if the key is absent. */
    fun sign(data: ByteArray): ByteArray {
        val entry = privateKey() ?: error("device key absent — call ensureKey() first")
        return Signature.getInstance(SIG_ALGORITHM).run {
            initSign(entry)
            update(data)
            sign()
        }
    }

    /**
     * Verify [signature] over [data] with the device key. Returns false (never
     * throws) on any failure — a missing key, malformed signature, or genuine
     * mismatch all mean "this receipt does not belong to this device's key".
     */
    fun verify(data: ByteArray, signature: ByteArray): Boolean = try {
        val pub = publicKey() ?: return false
        Signature.getInstance(SIG_ALGORITHM).run {
            initVerify(pub)
            update(data)
            verify(signature)
        }
    } catch (t: Throwable) {
        DebugLogger.w(TAG, "verify() failed: ${t.message}")
        false
    }

    /**
     * SHA-256 (hex) of the X.509-encoded public key. Stored in the receipt; a
     * change between launches means the Keystore key was regenerated (factory
     * reset / clear-data / different device) → hard violation → re-activate.
     * Returns null if the key is absent.
     */
    fun publicKeyFingerprint(): String? {
        val pub = publicKey() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(pub.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ── internals ──────────────────────────────────────────────────────────

    private fun keystore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun privateKey(): PrivateKey? =
        keystore().getKey(ALIAS, null) as? PrivateKey

    private fun publicKey(): PublicKey? =
        keystore().getCertificate(ALIAS)?.publicKey

    private fun generate(context: Context) {
        val strongBoxAvailable =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        // First attempt: StrongBox when the device advertises it. The feature
        // flag can still lie at keygen time, so we catch and retry on TEE.
        if (strongBoxAvailable) {
            try {
                generateInternal(strongBox = true)
                DebugLogger.i(TAG, "device key generated (StrongBox)")
                return
            } catch (e: StrongBoxUnavailableException) {
                DebugLogger.w(TAG, "StrongBox advertised but keygen failed — falling back to TEE: ${e.message}")
            }
        }
        generateInternal(strongBox = false)
        DebugLogger.i(TAG, "device key generated (TEE/software)")
    }

    private fun generateInternal(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            // Device-bound, not user-auth-bound — survives reboots/biometric
            // changes/OS updates; dies on factory reset / different device.
            .setUserAuthenticationRequired(false)
            .apply {
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setIsStrongBoxBacked(true)
                }
            }
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    /** Inspect where the existing key is backed (StrongBox / TEE / software). */
    private fun securityLevel(): SecurityLevel {
        val key = privateKey() ?: return SecurityLevel.UNKNOWN
        return try {
            val factory = KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
            val info = factory.getKeySpec(key, KeyInfo::class.java)
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> when (info.securityLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> SecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> SecurityLevel.TEE
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> SecurityLevel.SOFTWARE
                    else -> SecurityLevel.UNKNOWN
                }
                @Suppress("DEPRECATION")
                info.isInsideSecureHardware -> SecurityLevel.TEE
                else -> SecurityLevel.SOFTWARE
            }
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "securityLevel() probe failed: ${t.message}")
            SecurityLevel.UNKNOWN
        }
    }
}
