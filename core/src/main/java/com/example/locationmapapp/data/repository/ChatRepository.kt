/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.ChatMessage
import com.example.locationmapapp.data.model.ChatRoom
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.network.LocalServerCircuitBreakerInterceptor
import com.example.locationmapapp.util.network.OfflineModeInterceptor
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module ChatRepository.kt"

@Singleton
class ChatRepository @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val TAG = "ChatRepo"
    private val BASE = "http://10.0.0.229:4300"
    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(OfflineModeInterceptor())
        .addInterceptor(LocalServerCircuitBreakerInterceptor())
        .build()

    // ── Realtime chat (Socket.IO) — gutted in S211 for V1 no-network rule ───
    // Library dep removed; methods retained as no-op stubs because
    // SocialViewModel still references them (gated upstream by
    // FeatureFlags.V1_OFFLINE_ONLY in showChatDialog so they never fire).

    suspend fun connect() {
        // no-op: V1 ships zero-network. See feedback_v1_no_external_contact.
    }

    fun disconnect() {
        // no-op
    }

    fun isConnected(): Boolean = false

    fun setOnMessageListener(callback: (ChatMessage) -> Unit) {
        // no-op
    }

    fun setOnTypingListener(callback: (String) -> Unit) {
        // no-op
    }

    fun joinRoom(roomId: String) {
        // no-op
    }

    fun leaveRoom(roomId: String) {
        // no-op
    }

    fun sendMessage(roomId: String, content: String, replyToId: Long? = null) {
        // no-op
    }

    fun sendTyping(roomId: String) {
        // no-op
    }

    // ── REST API ─────────────────────────────────────────────────────────────

    suspend fun fetchRooms(): List<ChatRoom> = withContext(Dispatchers.IO) {
        val request = authRepository.authenticatedRequest("$BASE/chat/rooms").build()
        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "[]"
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "Fetch rooms failed: ${response.code}")
                return@withContext emptyList()
            }
            val arr = JsonParser.parseString(bodyStr).asJsonArray
            arr.map { el ->
                val o = el.asJsonObject
                ChatRoom(
                    id = o["id"]?.asString ?: "",
                    roomType = o["roomType"]?.asString ?: "public",
                    name = o["name"]?.asString ?: "",
                    description = if (o["description"]?.isJsonNull == false) o["description"]?.asString else null,
                    memberCount = o["memberCount"]?.asInt ?: 0,
                    lastMessageAt = if (o["lastMessageAt"]?.isJsonNull == false) o["lastMessageAt"]?.asString else null,
                    createdAt = o["createdAt"]?.asString ?: "",
                    isMember = o["isMember"]?.asBoolean ?: false,
                    memberRole = if (o["memberRole"]?.isJsonNull == false) o["memberRole"]?.asString else null
                )
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Fetch rooms error: ${e.message}")
            emptyList()
        }
    }

    suspend fun createRoom(name: String, description: String?): String? = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("name", name)
            if (description != null) put("description", description)
        }.toString()
        val request = authRepository.authenticatedRequest("$BASE/chat/rooms")
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext null
            JsonParser.parseString(bodyStr).asJsonObject["id"]?.asString
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Create room error: ${e.message}")
            null
        }
    }

    suspend fun fetchMessages(roomId: String, beforeId: Long? = null, limit: Int = 50): List<ChatMessage> = withContext(Dispatchers.IO) {
        var url = "$BASE/chat/rooms/$roomId/messages?limit=$limit"
        if (beforeId != null) url += "&before=$beforeId"
        val request = authRepository.authenticatedRequest(url).build()
        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: "[]"
            if (!response.isSuccessful) return@withContext emptyList()
            val arr = JsonParser.parseString(bodyStr).asJsonArray
            arr.map { el ->
                val o = el.asJsonObject
                ChatMessage(
                    id = o["id"]?.asLong ?: 0,
                    roomId = o["roomId"]?.asString ?: roomId,
                    userId = o["userId"]?.asString ?: "",
                    authorName = o["authorName"]?.asString ?: "Unknown",
                    content = o["content"]?.asString ?: "",
                    replyToId = if (o["replyToId"]?.isJsonNull == false) o["replyToId"]?.asLong else null,
                    isDeleted = o["isDeleted"]?.asBoolean ?: false,
                    sentAt = o["sentAt"]?.asString ?: ""
                )
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Fetch messages error: ${e.message}")
            emptyList()
        }
    }
}
