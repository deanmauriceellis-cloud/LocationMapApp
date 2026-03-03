package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.ChatMessage
import com.example.locationmapapp.data.model.ChatRoom
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val TAG = "ChatRepo"
    private val BASE = "http://10.0.0.4:3000"
    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var socket: Socket? = null
    private var onMessageCallback: ((ChatMessage) -> Unit)? = null
    private var onTypingCallback: ((String) -> Unit)? = null

    // ── Socket.IO Lifecycle ──────────────────────────────────────────────────

    suspend fun connect() {
        val token = authRepository.getValidAccessToken() ?: return
        withContext(Dispatchers.IO) {
            try {
                if (socket?.connected() == true) return@withContext

                val opts = IO.Options().apply {
                    auth = mapOf("token" to token)
                    forceNew = true
                    reconnection = true
                    reconnectionDelay = 2000
                    reconnectionAttempts = 10
                }
                socket = IO.socket(BASE, opts)

                socket?.on("new_message") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val obj = args[0] as JSONObject
                            val msg = ChatMessage(
                                id = obj.getLong("id"),
                                roomId = obj.getString("roomId"),
                                userId = obj.getString("userId"),
                                authorName = obj.getString("authorName"),
                                content = obj.getString("content"),
                                replyToId = if (obj.isNull("replyToId")) null else obj.getLong("replyToId"),
                                sentAt = obj.getString("sentAt")
                            )
                            onMessageCallback?.invoke(msg)
                        } catch (e: Exception) {
                            DebugLogger.e(TAG, "Parse new_message error: ${e.message}")
                        }
                    }
                }

                socket?.on("user_typing") { args ->
                    if (args.isNotEmpty()) {
                        try {
                            val obj = args[0] as JSONObject
                            onTypingCallback?.invoke(obj.getString("displayName"))
                        } catch (_: Exception) {}
                    }
                }

                socket?.on(Socket.EVENT_CONNECT) {
                    DebugLogger.i(TAG, "Socket.IO connected")
                }

                socket?.on(Socket.EVENT_DISCONNECT) { args ->
                    val reason = if (args.isNotEmpty()) args[0].toString() else "unknown"
                    DebugLogger.i(TAG, "Socket.IO disconnected: $reason")
                }

                socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val err = if (args.isNotEmpty()) args[0].toString() else "unknown"
                    DebugLogger.e(TAG, "Socket.IO connect error: $err")
                }

                socket?.connect()
                DebugLogger.i(TAG, "Socket.IO connecting to $BASE")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Socket.IO init error: ${e.message}")
            }
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        DebugLogger.i(TAG, "Socket.IO disconnected")
    }

    fun isConnected(): Boolean = socket?.connected() == true

    fun setOnMessageListener(callback: (ChatMessage) -> Unit) {
        onMessageCallback = callback
    }

    fun setOnTypingListener(callback: (String) -> Unit) {
        onTypingCallback = callback
    }

    // ── Socket.IO Actions ────────────────────────────────────────────────────

    fun joinRoom(roomId: String) {
        socket?.emit("join_room", roomId)
        DebugLogger.d(TAG, "Joining room $roomId")
    }

    fun leaveRoom(roomId: String) {
        socket?.emit("leave_room", roomId)
    }

    fun sendMessage(roomId: String, content: String, replyToId: Long? = null) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("content", content)
            if (replyToId != null) put("replyToId", replyToId)
        }
        socket?.emit("send_message", data)
    }

    fun sendTyping(roomId: String) {
        socket?.emit("typing", roomId)
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
