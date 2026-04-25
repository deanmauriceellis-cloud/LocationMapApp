/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads `assets/wickedmap/polygons.json` (a GeoJSON FeatureCollection) at app
 * start and indexes features by `properties.kind` ("water", "cemetery",
 * "park", etc.).
 *
 * Source data is extracted from local TigerLine + MassGIS Postgres ingests
 * by scripts under tools/wickedmap-polygons/. We never load OSM or planetiler
 * data here (memory: feedback_no_osm_use_local_geo).
 *
 * GeoJSON coordinates are [lon, lat] order. We flip to (lat, lon) on load to
 * match the rest of the WickedMap engine.
 */
object PolygonLibrary {

    private const val TAG = "WickedMap.Polygons"
    private const val ASSET_PATH = "wickedmap/polygons.json"

    private val byKind: MutableMap<String, MutableList<WickedPolygon>> = HashMap()
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        val started = System.currentTimeMillis()

        val raw = try {
            context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "failed to read $ASSET_PATH: ${e.message}", e)
            return
        }

        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            Log.e(TAG, "failed to parse polygons.json: ${e.message}", e)
            return
        }

        val features = root.optJSONArray("features") ?: JSONArray()
        var added = 0
        var skipped = 0

        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val props = feature.optJSONObject("properties") ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val kind = props.optString("kind", "")
            if (kind.isEmpty()) {
                skipped++
                continue
            }
            val polygons = parseGeometry(geometry)
            if (polygons.isEmpty()) {
                skipped++
                continue
            }
            for (rings in polygons) {
                val poly = WickedPolygon(
                    rings = rings,
                    name = props.optString("name", "").takeIf { it.isNotEmpty() },
                    subkind = props.optString("subkind", "").takeIf { it.isNotEmpty() },
                    properties = props,
                )
                byKind.getOrPut(kind) { mutableListOf() }.add(poly)
                added++
            }
        }

        loaded = true
        val elapsed = System.currentTimeMillis() - started
        val summary = byKind.entries.joinToString(", ") { "${it.key}=${it.value.size}" }
        Log.i(TAG, "loaded $added polygons in ${elapsed}ms ($summary), skipped=$skipped")
    }

    fun byKind(kind: String): List<WickedPolygon> = byKind[kind].orEmpty()

    /**
     * Parse a GeoJSON Polygon or MultiPolygon to a list of polygons (each a
     * list of rings; first ring is outer, rest are holes). Coordinates are
     * flipped from [lon, lat] to (lat, lon).
     */
    private fun parseGeometry(geometry: JSONObject): List<List<List<Pair<Double, Double>>>> {
        val type = geometry.optString("type")
        val coords = geometry.optJSONArray("coordinates") ?: return emptyList()
        return when (type) {
            "Polygon" -> listOf(parsePolygonRings(coords))
            "MultiPolygon" -> {
                val out = mutableListOf<List<List<Pair<Double, Double>>>>()
                for (i in 0 until coords.length()) {
                    val polyCoords = coords.optJSONArray(i) ?: continue
                    out.add(parsePolygonRings(polyCoords))
                }
                out
            }
            else -> emptyList()
        }
    }

    private fun parsePolygonRings(rings: JSONArray): List<List<Pair<Double, Double>>> {
        val out = mutableListOf<List<Pair<Double, Double>>>()
        for (i in 0 until rings.length()) {
            val ringArr = rings.optJSONArray(i) ?: continue
            val pts = mutableListOf<Pair<Double, Double>>()
            for (j in 0 until ringArr.length()) {
                val pt = ringArr.optJSONArray(j) ?: continue
                if (pt.length() < 2) continue
                val lon = pt.optDouble(0, Double.NaN)
                val lat = pt.optDouble(1, Double.NaN)
                if (lat.isFinite() && lon.isFinite()) {
                    pts.add(lat to lon)
                }
            }
            if (pts.size >= 3) out.add(pts)
        }
        return out
    }
}

data class WickedPolygon(
    /** Outer ring first, then holes. Each point is (lat, lon). */
    val rings: List<List<Pair<Double, Double>>>,
    val name: String?,
    val subkind: String?,
    val properties: JSONObject,
) {
    val outerRing: List<Pair<Double, Double>> get() = rings.firstOrNull().orEmpty()
}
