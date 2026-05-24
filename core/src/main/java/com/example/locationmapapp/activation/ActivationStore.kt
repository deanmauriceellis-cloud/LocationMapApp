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
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.Gson

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module ActivationStore.kt"

/**
 * OMEN-025 Phase 1 — persistence for the [ActivationReceipt].
 *
 * Two layers of protection, deliberately redundant:
 *  1. **EncryptedSharedPreferences** (AES-256) — at-rest confidentiality.
 *  2. **Detached device-key signature** ([DeviceKeyManager]) over the receipt
 *     JSON — tamper-evidence + device-binding. This is the real guarantee:
 *     [load] returns the receipt ONLY if the signature verifies against the
 *     on-device Keystore key, so a receipt copied from another device (or an
 *     edited one) is rejected even though it decrypts fine.
 *
 * `androidx.security.crypto` is in maintenance (deprecated 2024) but fine on
 * minSdk 26; the signature — not the prefs encryption — is what we lean on, so
 * a later migration off this library is low-risk.
 */
class ActivationStore(context: Context) {

    private val gson = Gson()

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Persist [receipt], signing its canonical JSON with the device key. */
    fun save(receipt: ActivationReceipt) {
        val json = gson.toJson(receipt)
        val sig = Base64.encodeToString(DeviceKeyManager.sign(json.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        prefs.edit().putString(KEY_RECEIPT, json).putString(KEY_SIG, sig).apply()
        DebugLogger.i(TAG, "activation receipt saved (verdict=${receipt.verdict})")
    }

    /**
     * Load the receipt, returning null unless BOTH the stored JSON and a valid
     * device-key signature over it are present. A signature mismatch (tampered
     * or copied-from-another-device receipt) returns null — the caller treats
     * that as "not activated / hard violation" and forces re-activation.
     */
    fun load(): ActivationReceipt? {
        val json = prefs.getString(KEY_RECEIPT, null) ?: return null
        val sig = prefs.getString(KEY_SIG, null) ?: return null
        val sigBytes = try {
            Base64.decode(sig, Base64.NO_WRAP)
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "receipt signature not decodable — rejecting")
            return null
        }
        if (!DeviceKeyManager.verify(json.toByteArray(Charsets.UTF_8), sigBytes)) {
            DebugLogger.w(TAG, "receipt signature does NOT verify against device key — rejecting")
            return null
        }
        return try {
            gson.fromJson(json, ActivationReceipt::class.java)
        } catch (t: Throwable) {
            DebugLogger.w(TAG, "receipt JSON parse failed — rejecting: ${t.message}")
            null
        }
    }

    /** Wipe the receipt (e.g. on hard violation before re-activation). */
    fun clear() {
        prefs.edit().remove(KEY_RECEIPT).remove(KEY_SIG).apply()
        DebugLogger.i(TAG, "activation receipt cleared")
    }

    companion object {
        private const val TAG = "ActivationStore"
        private const val PREFS_NAME = "omen025_activation"
        private const val KEY_RECEIPT = "receipt_json"
        private const val KEY_SIG = "receipt_sig"
    }
}
