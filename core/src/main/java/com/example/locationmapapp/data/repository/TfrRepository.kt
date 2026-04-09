/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.repository

import com.example.locationmapapp.data.model.TfrShape
import com.example.locationmapapp.data.model.TfrZone
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.network.LocalServerCircuitBreakerInterceptor
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TfrRepository.kt"

@Singleton
class TfrRepository @Inject constructor() {

    private val TAG = "TfrRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(LocalServerCircuitBreakerInterceptor())
        .build()

    suspend fun fetchTfrs(south: Double, west: Double, north: Double, east: Double): List<TfrZone> = withContext(Dispatchers.IO) {
        val bbox = "$south,$west,$north,$east"
        val url = "http://10.0.0.229:4300/tfrs?bbox=$bbox"
        DebugLogger.d(TAG, "Fetching TFRs for bbox=$bbox")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        val elapsed = System.currentTimeMillis() - t0
        DebugLogger.i(TAG, "TFR response code=${response.code} in ${elapsed}ms")
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300) ?: "(empty)"
            DebugLogger.e(TAG, "TFR HTTP ${response.code} body: $errBody")
            throw RuntimeException("HTTP ${response.code}: $errBody")
        }
        val bodyStr = response.body?.string().orEmpty()
        DebugLogger.d(TAG, "TFR body length=${bodyStr.length}")
        if (bodyStr.isBlank()) emptyList() else parseTfrJson(bodyStr)
    }

    private fun parseTfrJson(json: String): List<TfrZone> {
        val results = mutableListOf<TfrZone>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val shapes = mutableListOf<TfrShape>()
                val shapesArr = obj.getAsJsonArray("shapes")
                if (shapesArr != null) {
                    for (sEl in shapesArr) {
                        val sObj = sEl.asJsonObject
                        val points = mutableListOf<List<Double>>()
                        val pointsArr = sObj.getAsJsonArray("points")
                        if (pointsArr != null) {
                            for (pEl in pointsArr) {
                                val pArr = pEl.asJsonArray
                                if (pArr.size() >= 2) {
                                    points.add(listOf(pArr[0].asDouble, pArr[1].asDouble))
                                }
                            }
                        }
                        shapes.add(TfrShape(
                            type = sObj["type"]?.asString ?: "polygon",
                            points = points,
                            floorAltFt = sObj["floorAltFt"]?.asInt ?: 0,
                            ceilingAltFt = sObj["ceilingAltFt"]?.asInt ?: 99999,
                            radiusNm = sObj["radiusNm"]?.let { if (it.isJsonNull) null else it.asDouble }
                        ))
                    }
                }
                results.add(TfrZone(
                    id = obj["id"]?.asString ?: "",
                    notam = obj["notam"]?.asString ?: "",
                    type = obj["type"]?.asString ?: "TFR",
                    description = obj["description"]?.asString ?: "",
                    effectiveDate = obj["effectiveDate"]?.asString ?: "",
                    expireDate = obj["expireDate"]?.asString ?: "",
                    shapes = shapes,
                    facility = obj["facility"]?.asString ?: "",
                    state = obj["state"]?.asString ?: ""
                ))
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "TFR parse error", e)
        }
        DebugLogger.i(TAG, "Parsed ${results.size} TFRs")
        return results
    }
}
