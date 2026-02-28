package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.Webcam
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebcamRepository @Inject constructor() {

    private val TAG = "WebcamRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWebcams(
        south: Double, west: Double, north: Double, east: Double,
        categories: String = "traffic"
    ): List<Webcam> = withContext(Dispatchers.IO) {
        val url = "http://10.0.0.4:3000/webcams?s=$south&w=$west&n=$north&e=$east&categories=$categories"
        DebugLogger.d(TAG, "Fetching webcams categories=$categories bbox=$south,$west,$north,$east")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "Webcams response code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "(empty)"
            DebugLogger.e(TAG, "Webcams HTTP ${response.code} body: $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        val bodyStr = response.body?.string().orEmpty()
        DebugLogger.d(TAG, "Webcams body length=${bodyStr.length}")
        if (bodyStr.isBlank()) emptyList() else parseWebcamsJson(bodyStr)
    }

    private fun parseWebcamsJson(json: String): List<Webcam> {
        val results = mutableListOf<Webcam>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val cats = mutableListOf<String>()
                obj["categories"]?.asJsonArray?.forEach { c -> cats.add(c.asString) }
                results.add(Webcam(
                    id           = obj["id"]?.asLong ?: 0,
                    title        = obj["title"]?.asString ?: "",
                    lat          = obj["lat"]?.asDouble ?: 0.0,
                    lon          = obj["lon"]?.asDouble ?: 0.0,
                    categories   = cats,
                    previewUrl   = obj["previewUrl"]?.asString ?: "",
                    thumbnailUrl = obj["thumbnailUrl"]?.asString ?: "",
                    playerUrl    = obj["playerUrl"]?.let { if (it.isJsonNull) "" else it.asString } ?: "",
                    detailUrl    = obj["detailUrl"]?.let { if (it.isJsonNull) "" else it.asString } ?: "",
                    status       = obj["status"]?.asString ?: "active",
                    lastUpdated  = obj["lastUpdated"]?.let { if (it.isJsonNull) null else it.asString }
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Webcam parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} webcams")
        return results
    }
}
