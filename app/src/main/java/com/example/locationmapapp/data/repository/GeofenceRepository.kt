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
import com.example.locationmapapp.data.model.ZoneType
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module GeofenceRepository.kt"

@Singleton
class GeofenceRepository @Inject constructor() {

    private val TAG = "GeofenceRepository"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val BASE = "http://10.0.0.4:3000"

    // ── Speed / Red-Light Cameras ────────────────────────────────────────────

    suspend fun fetchCameras(s: Double, w: Double, n: Double, e: Double): List<TfrZone> = withContext(Dispatchers.IO) {
        val url = "$BASE/cameras?bbox=$s,$w,$n,$e"
        DebugLogger.d(TAG, "fetchCameras bbox=$s,$w,$n,$e")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        DebugLogger.i(TAG, "Cameras response=${response.code} in ${System.currentTimeMillis() - t0}ms")
        if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) return@withContext emptyList()
        parseCamerasJson(body)
    }

    private fun parseCamerasJson(json: String): List<TfrZone> {
        val results = mutableListOf<TfrZone>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val lat = obj["lat"]?.asDouble ?: continue
                val lon = obj["lon"]?.asDouble ?: continue
                val id = obj["id"]?.asString ?: "camera_${results.size}"
                val name = obj["name"]?.let { if (it.isJsonNull) null else it.asString }
                    ?: obj["cameraType"]?.asString ?: "Speed Camera"
                val meta = mutableMapOf<String, String>()
                obj["maxspeed"]?.let { if (!it.isJsonNull) meta["maxspeed"] = it.asString }
                obj["direction"]?.let { if (!it.isJsonNull) meta["direction"] = it.asString }
                obj["operator"]?.let { if (!it.isJsonNull) meta["operator"] = it.asString }
                obj["cameraType"]?.let { if (!it.isJsonNull) meta["cameraType"] = it.asString }
                results.add(TfrZone(
                    id = id, notam = name, type = "Speed Camera",
                    description = buildCameraDescription(meta),
                    effectiveDate = "", expireDate = "",
                    shapes = listOf(generateCircleShape(lat, lon, 200.0)),
                    facility = "", state = "",
                    zoneType = ZoneType.SPEED_CAMERA, metadata = meta
                ))
            }
        } catch (e: Exception) { DebugLogger.e(TAG, "Camera parse error", e) }
        DebugLogger.i(TAG, "Parsed ${results.size} cameras")
        return results
    }

    private fun buildCameraDescription(meta: Map<String, String>): String {
        val parts = mutableListOf<String>()
        meta["cameraType"]?.let { parts.add(it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() }) }
        meta["maxspeed"]?.let { parts.add("Speed limit: $it") }
        meta["direction"]?.let { parts.add("Direction: $it") }
        meta["operator"]?.let { parts.add("Operator: $it") }
        return parts.joinToString(" · ")
    }

    // ── School Zones ─────────────────────────────────────────────────────────

    suspend fun fetchSchools(s: Double, w: Double, n: Double, e: Double): List<TfrZone> = withContext(Dispatchers.IO) {
        val url = "$BASE/schools?bbox=$s,$w,$n,$e"
        DebugLogger.d(TAG, "fetchSchools bbox=$s,$w,$n,$e")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        DebugLogger.i(TAG, "Schools response=${response.code} in ${System.currentTimeMillis() - t0}ms")
        if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) return@withContext emptyList()
        parseSchoolsJson(body)
    }

    private fun parseSchoolsJson(json: String): List<TfrZone> {
        val results = mutableListOf<TfrZone>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val lat = obj["lat"]?.asDouble ?: continue
                val lon = obj["lon"]?.asDouble ?: continue
                val id = obj["id"]?.asString ?: "school_${results.size}"
                val name = obj["name"]?.let { if (it.isJsonNull) null else it.asString } ?: "School"
                val isPolygon = obj["isPolygon"]?.asBoolean == true
                val meta = mutableMapOf<String, String>()
                obj["grades"]?.let { if (!it.isJsonNull) meta["grades"] = it.asString }
                obj["operator"]?.let { if (!it.isJsonNull) meta["operator"] = it.asString }

                val shapes = if (isPolygon && obj.has("points") && !obj["points"].isJsonNull) {
                    val pointsArr = obj.getAsJsonArray("points")
                    val points = pointsArr.map { p ->
                        val pair = p.asJsonArray
                        listOf(pair[0].asDouble, pair[1].asDouble)
                    }
                    if (points.size >= 3) {
                        listOf(TfrShape("polygon", points, 0, 99999, null))
                    } else {
                        listOf(generateCircleShape(lat, lon, 300.0))
                    }
                } else {
                    listOf(generateCircleShape(lat, lon, 300.0))
                }

                results.add(TfrZone(
                    id = id, notam = name, type = "School Zone",
                    description = buildSchoolDescription(name, meta),
                    effectiveDate = "", expireDate = "",
                    shapes = shapes, facility = "", state = "",
                    zoneType = ZoneType.SCHOOL_ZONE, metadata = meta
                ))
            }
        } catch (e: Exception) { DebugLogger.e(TAG, "School parse error", e) }
        DebugLogger.i(TAG, "Parsed ${results.size} schools")
        return results
    }

    private fun buildSchoolDescription(name: String, meta: Map<String, String>): String {
        val parts = mutableListOf(name)
        meta["grades"]?.let { parts.add("Grades: $it") }
        meta["operator"]?.let { parts.add("Operator: $it") }
        return parts.joinToString(" · ")
    }

    // ── Flood Zones ──────────────────────────────────────────────────────────

    suspend fun fetchFloodZones(s: Double, w: Double, n: Double, e: Double): List<TfrZone> = withContext(Dispatchers.IO) {
        val url = "$BASE/flood-zones?bbox=$s,$w,$n,$e"
        DebugLogger.d(TAG, "fetchFloodZones bbox=$s,$w,$n,$e")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        DebugLogger.i(TAG, "FloodZones response=${response.code} in ${System.currentTimeMillis() - t0}ms")
        if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) return@withContext emptyList()
        parseFloodJson(body)
    }

    private fun parseFloodJson(json: String): List<TfrZone> {
        val results = mutableListOf<TfrZone>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val id = obj["id"]?.asString ?: "flood_${results.size}"
                val zoneCode = obj["zoneCode"]?.asString ?: ""
                val zoneSubtype = obj["zoneSubtype"]?.asString ?: ""
                val bfe = obj["bfe"]?.let { if (it.isJsonNull) null else it.asString }
                val meta = mutableMapOf("zoneCode" to zoneCode)
                if (zoneSubtype.isNotBlank()) meta["zoneSubtype"] = zoneSubtype
                if (bfe != null) meta["bfe"] = bfe

                // rings is array of rings, each ring is array of [lon, lat] pairs
                val ringsArr = obj.getAsJsonArray("rings") ?: continue
                val shapes = mutableListOf<TfrShape>()
                for (ringEl in ringsArr) {
                    val ring = ringEl.asJsonArray
                    val points = ring.map { p ->
                        val pair = p.asJsonArray
                        listOf(pair[0].asDouble, pair[1].asDouble)
                    }
                    if (points.size >= 3) {
                        shapes.add(TfrShape("polygon", points, 0, 99999, null))
                    }
                }
                if (shapes.isEmpty()) continue

                results.add(TfrZone(
                    id = id, notam = "Flood Zone $zoneCode", type = "Flood Zone",
                    description = buildFloodDescription(zoneCode, zoneSubtype, bfe),
                    effectiveDate = "", expireDate = "",
                    shapes = shapes, facility = "", state = "",
                    zoneType = ZoneType.FLOOD_ZONE, metadata = meta
                ))
            }
        } catch (e: Exception) { DebugLogger.e(TAG, "Flood parse error", e) }
        DebugLogger.i(TAG, "Parsed ${results.size} flood zones")
        return results
    }

    private fun buildFloodDescription(code: String, subtype: String, bfe: String?): String {
        val parts = mutableListOf("FEMA Zone $code")
        if (subtype.isNotBlank()) parts.add("Subtype: $subtype")
        if (bfe != null) parts.add("BFE: $bfe ft")
        return parts.joinToString(" · ")
    }

    // ── Railroad Crossings ───────────────────────────────────────────────────

    suspend fun fetchCrossings(s: Double, w: Double, n: Double, e: Double): List<TfrZone> = withContext(Dispatchers.IO) {
        val url = "$BASE/crossings?bbox=$s,$w,$n,$e"
        DebugLogger.d(TAG, "fetchCrossings bbox=$s,$w,$n,$e")
        val t0 = System.currentTimeMillis()
        val response = client.newCall(Request.Builder().url(url).build()).execute()
        DebugLogger.i(TAG, "Crossings response=${response.code} in ${System.currentTimeMillis() - t0}ms")
        if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) return@withContext emptyList()
        parseCrossingsJson(body)
    }

    private fun parseCrossingsJson(json: String): List<TfrZone> {
        val results = mutableListOf<TfrZone>()
        try {
            val arr = JsonParser.parseString(json).asJsonArray
            for (el in arr) {
                val obj = el.asJsonObject
                val lat = obj["lat"]?.asDouble ?: continue
                val lon = obj["lon"]?.asDouble ?: continue
                val id = obj["id"]?.asString ?: "crossing_${results.size}"
                val railroad = obj["railroad"]?.let { if (it.isJsonNull) "" else it.asString } ?: ""
                val street = obj["street"]?.let { if (it.isJsonNull) "" else it.asString } ?: ""
                val meta = mutableMapOf<String, String>()
                obj["crossingId"]?.let { if (!it.isJsonNull) meta["crossingId"] = it.asString }
                obj["railroad"]?.let { if (!it.isJsonNull) meta["railroad"] = it.asString }
                obj["street"]?.let { if (!it.isJsonNull) meta["street"] = it.asString }
                obj["warningDevices"]?.let { if (!it.isJsonNull) meta["warningDevices"] = it.asString }
                obj["crossingType"]?.let { if (!it.isJsonNull) meta["crossingType"] = it.asString }
                val name = if (street.isNotBlank() && railroad.isNotBlank()) "$railroad @ $street"
                    else if (street.isNotBlank()) street
                    else if (railroad.isNotBlank()) railroad
                    else "Railroad Crossing"
                results.add(TfrZone(
                    id = id, notam = name, type = "Railroad Crossing",
                    description = buildCrossingDescription(meta),
                    effectiveDate = "", expireDate = "",
                    shapes = listOf(generateCircleShape(lat, lon, 100.0)),
                    facility = "", state = "",
                    zoneType = ZoneType.RAILROAD_CROSSING, metadata = meta
                ))
            }
        } catch (e: Exception) { DebugLogger.e(TAG, "Crossing parse error", e) }
        DebugLogger.i(TAG, "Parsed ${results.size} crossings")
        return results
    }

    private fun buildCrossingDescription(meta: Map<String, String>): String {
        val parts = mutableListOf<String>()
        meta["railroad"]?.let { parts.add("Railroad: $it") }
        meta["street"]?.let { parts.add("Street: $it") }
        meta["warningDevices"]?.let { parts.add("Warning: $it") }
        meta["crossingType"]?.let { parts.add("Type: $it") }
        return parts.joinToString(" · ")
    }

    // ── Circle geometry generator ────────────────────────────────────────────

    companion object {
        /** Generate a circular polygon shape around a point. */
        fun generateCircleShape(lat: Double, lon: Double, radiusM: Double, numPoints: Int = 36): TfrShape {
            val points = mutableListOf<List<Double>>()
            val radiusDeg = radiusM / 111_320.0  // approximate meters → degrees latitude
            for (i in 0..numPoints) {
                val angle = Math.toRadians((360.0 / numPoints) * i)
                val dLat = radiusDeg * cos(angle)
                val dLon = radiusDeg * Math.sin(angle) / cos(Math.toRadians(lat))
                points.add(listOf(lon + dLon, lat + dLat))
            }
            return TfrShape("polygon", points, 0, 99999, null)
        }
    }
}
