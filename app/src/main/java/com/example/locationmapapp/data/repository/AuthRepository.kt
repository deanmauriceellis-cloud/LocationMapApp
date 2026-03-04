/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.repository

import android.content.Context
import com.example.locationmapapp.data.model.AuthResponse
import com.example.locationmapapp.data.model.AuthUser
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module AuthRepository.kt"

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "AuthRepo"
    private val BASE = "http://10.0.0.4:3000"
    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    // ── Token storage ────────────────────────────────────────────────────────

    private fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    private fun saveUser(user: AuthUser) {
        prefs.edit()
            .putString("user_id", user.id)
            .putString("user_name", user.displayName)
            .putString("user_role", user.role)
            .apply()
    }

    fun clearAuth() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = prefs.getString("access_token", null) != null

    fun currentUser(): AuthUser? {
        val id = prefs.getString("user_id", null) ?: return null
        val name = prefs.getString("user_name", null) ?: return null
        val role = prefs.getString("user_role", "user") ?: "user"
        return AuthUser(id, name, role)
    }

    private fun getAccessToken(): String? = prefs.getString("access_token", null)
    private fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    // ── Public API ───────────────────────────────────────────────────────────

    suspend fun register(displayName: String, email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val json = """{"displayName":"${displayName.escapeJson()}","email":"${email.escapeJson()}","password":"${password.escapeJson()}"}"""
        val request = Request.Builder()
            .url("$BASE/auth/register")
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            val errMsg = try { JsonParser.parseString(bodyStr).asJsonObject["error"]?.asString } catch (_: Exception) { null }
            throw RuntimeException(errMsg ?: "Registration failed (${response.code})")
        }
        val parsed = parseAuthResponse(bodyStr)
        saveTokens(parsed.accessToken, parsed.refreshToken)
        saveUser(parsed.user)
        DebugLogger.i(TAG, "Registered as ${parsed.user.displayName}")
        parsed
    }

    suspend fun login(email: String, password: String): AuthResponse = withContext(Dispatchers.IO) {
        val json = """{"email":"${email.escapeJson()}","password":"${password.escapeJson()}"}"""
        val request = Request.Builder()
            .url("$BASE/auth/login")
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            val errMsg = try { JsonParser.parseString(bodyStr).asJsonObject["error"]?.asString } catch (_: Exception) { null }
            throw RuntimeException(errMsg ?: "Login failed (${response.code})")
        }
        val parsed = parseAuthResponse(bodyStr)
        saveTokens(parsed.accessToken, parsed.refreshToken)
        saveUser(parsed.user)
        DebugLogger.i(TAG, "Logged in as ${parsed.user.displayName}")
        parsed
    }

    suspend fun refreshTokens(): Boolean = withContext(Dispatchers.IO) {
        val rt = getRefreshToken() ?: return@withContext false
        val json = """{"refreshToken":"$rt"}"""
        val request = Request.Builder()
            .url("$BASE/auth/refresh")
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                DebugLogger.w(TAG, "Token refresh failed: ${response.code}")
                if (response.code == 401) clearAuth()
                return@withContext false
            }
            val root = JsonParser.parseString(bodyStr).asJsonObject
            val newAccess = root["accessToken"]?.asString ?: return@withContext false
            val newRefresh = root["refreshToken"]?.asString ?: return@withContext false
            saveTokens(newAccess, newRefresh)
            DebugLogger.d(TAG, "Tokens refreshed")
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Token refresh error: ${e.message}")
            false
        }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val at = getAccessToken()
        val rt = getRefreshToken()
        if (at != null && rt != null) {
            try {
                val json = """{"refreshToken":"$rt"}"""
                val request = Request.Builder()
                    .url("$BASE/auth/logout")
                    .post(json.toRequestBody(JSON_TYPE))
                    .header("Authorization", "Bearer $at")
                    .build()
                client.newCall(request).execute().close()
            } catch (e: Exception) {
                DebugLogger.w(TAG, "Logout request failed: ${e.message}")
            }
        }
        clearAuth()
        DebugLogger.i(TAG, "Logged out")
    }

    suspend fun fetchProfile(): AuthUser? = withContext(Dispatchers.IO) {
        val at = getValidAccessToken() ?: return@withContext null
        val request = Request.Builder()
            .url("$BASE/auth/me")
            .header("Authorization", "Bearer $at")
            .build()
        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext null
            val root = JsonParser.parseString(bodyStr).asJsonObject
            val user = AuthUser(
                id = root["id"]?.asString ?: "",
                displayName = root["displayName"]?.asString ?: "",
                role = root["role"]?.asString ?: "user",
                createdAt = root["createdAt"]?.asString
            )
            saveUser(user)
            user
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Fetch profile error: ${e.message}")
            null
        }
    }

    /**
     * Returns a valid access token, auto-refreshing if the current one is expired.
     * Other repositories should call this for authenticated requests.
     */
    suspend fun getValidAccessToken(): String? {
        val at = getAccessToken() ?: return null
        // Try to decode and check expiry
        try {
            val parts = at.split(".")
            if (parts.size == 3) {
                val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
                val exp = JsonParser.parseString(payload).asJsonObject["exp"]?.asLong ?: 0
                if (System.currentTimeMillis() / 1000 < exp - 60) return at // Still valid (60s buffer)
            }
        } catch (_: Exception) {}
        // Token expired or unparseable — try refresh
        return if (refreshTokens()) getAccessToken() else null
    }

    /**
     * Build an authenticated request. Use this from other repositories.
     */
    suspend fun authenticatedRequest(url: String): Request.Builder {
        val token = getValidAccessToken()
        val builder = Request.Builder().url(url)
        if (token != null) builder.header("Authorization", "Bearer $token")
        return builder
    }

    // ── Parsing ──────────────────────────────────────────────────────────────

    private fun parseAuthResponse(bodyStr: String): AuthResponse {
        val root = JsonParser.parseString(bodyStr).asJsonObject
        val userObj = root["user"]?.asJsonObject ?: throw RuntimeException("Missing user in response")
        return AuthResponse(
            user = AuthUser(
                id = userObj["id"]?.asString ?: "",
                displayName = userObj["displayName"]?.asString ?: "",
                role = userObj["role"]?.asString ?: "user",
                createdAt = userObj["createdAt"]?.asString
            ),
            accessToken = root["accessToken"]?.asString ?: throw RuntimeException("Missing accessToken"),
            refreshToken = root["refreshToken"]?.asString ?: throw RuntimeException("Missing refreshToken"),
            expiresAt = root["expiresAt"]?.asString ?: ""
        )
    }

    private fun String.escapeJson(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
