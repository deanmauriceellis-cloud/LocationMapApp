/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.CommentsResponse
import com.example.locationmapapp.data.model.PoiComment
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
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
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module CommentRepository.kt"

@Singleton
class CommentRepository @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val TAG = "CommentRepo"
    private val BASE = "http://10.0.0.4:4300"
    private val JSON_TYPE = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchComments(osmType: String, osmId: Long): CommentsResponse = withContext(Dispatchers.IO) {
        val request = authRepository.authenticatedRequest("$BASE/comments/$osmType/$osmId")
            .build()
        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: ""
        if (!response.isSuccessful) {
            DebugLogger.e(TAG, "Fetch comments failed: ${response.code}")
            return@withContext CommentsResponse(emptyList(), 0)
        }
        parseCommentsResponse(bodyStr)
    }

    suspend fun postComment(
        osmType: String,
        osmId: Long,
        content: String,
        rating: Int? = null,
        parentId: Long? = null
    ): Long? = withContext(Dispatchers.IO) {
        val ratingStr = if (rating != null) ""","rating":$rating""" else ""
        val parentStr = if (parentId != null) ""","parentId":$parentId""" else ""
        val json = """{"osmType":"$osmType","osmId":$osmId,"content":"${content.escapeJson()}"$ratingStr$parentStr}"""
        val request = authRepository.authenticatedRequest("$BASE/comments")
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errMsg = try { JsonParser.parseString(bodyStr).asJsonObject["error"]?.asString } catch (_: Exception) { null }
                DebugLogger.e(TAG, "Post comment failed: ${response.code} $errMsg")
                return@withContext null
            }
            val root = JsonParser.parseString(bodyStr).asJsonObject
            DebugLogger.i(TAG, "Comment posted: id=${root["id"]?.asLong}")
            root["id"]?.asLong
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Post comment error: ${e.message}")
            null
        }
    }

    suspend fun voteOnComment(commentId: Long, vote: Int): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val json = """{"vote":$vote}"""
        val request = authRepository.authenticatedRequest("$BASE/comments/$commentId/vote")
            .post(json.toRequestBody(JSON_TYPE))
            .build()
        try {
            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext null
            val root = JsonParser.parseString(bodyStr).asJsonObject
            Pair(root["upvotes"]?.asInt ?: 0, root["downvotes"]?.asInt ?: 0)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Vote error: ${e.message}")
            null
        }
    }

    suspend fun deleteComment(commentId: Long): Boolean = withContext(Dispatchers.IO) {
        val request = authRepository.authenticatedRequest("$BASE/comments/$commentId")
            .delete()
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Delete error: ${e.message}")
            false
        }
    }

    private fun parseCommentsResponse(bodyStr: String): CommentsResponse {
        val root = JsonParser.parseString(bodyStr).asJsonObject
        val arr = root["comments"]?.asJsonArray ?: return CommentsResponse(emptyList(), 0)
        val comments = arr.map { el ->
            val o = el.asJsonObject
            PoiComment(
                id = o["id"]?.asLong ?: 0,
                osmType = o["osmType"]?.asString ?: "",
                osmId = o["osmId"]?.asLong ?: 0,
                userId = o["userId"]?.asString ?: "",
                parentId = if (o["parentId"]?.isJsonNull == false) o["parentId"]?.asLong else null,
                content = o["content"]?.asString ?: "",
                rating = if (o["rating"]?.isJsonNull == false) o["rating"]?.asInt else null,
                upvotes = o["upvotes"]?.asInt ?: 0,
                downvotes = o["downvotes"]?.asInt ?: 0,
                isDeleted = o["isDeleted"]?.asBoolean ?: false,
                createdAt = o["createdAt"]?.asString ?: "",
                authorName = o["authorName"]?.asString ?: "Unknown",
                viewerVote = o["viewerVote"]?.asInt ?: 0
            )
        }
        return CommentsResponse(comments, root["total"]?.asInt ?: comments.size)
    }

    private fun String.escapeJson(): String = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
