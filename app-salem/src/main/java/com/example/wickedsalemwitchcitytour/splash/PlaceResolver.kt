/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * S231 — runtime polygon resolver against the bundled us_places_v1.sqlite
 * asset. Layered point-in-polygon:
 *
 *   1. IN_TOWN_ADJACENT_TO_SALEM (layer 3)  ← Salem-area towns first
 *   2. IN_CITY                   (layer 1)  ← top-100 raw city polygons
 *   3. NEAR_CITY                 (layer 2)  ← top-100 cities + 30 mi buffer
 *   4. IN_COUNTY                 (layer 4)  ← universal fallback
 *
 * Each candidate is bbox-prefiltered via the SQLite R-tree, gunzipped from a
 * `wkb_gz` blob, and tested with ray-casting PIP. ~5 ms cold per lookup,
 * sub-millisecond after warmup.
 *
 * The asset ships read-only inside the APK; on first use we copy it to
 * application files dir so SQLite can open it via a normal file path. ~2 MB.
 */
package com.example.wickedsalemwitchcitytour.splash

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.locationmapapp.util.DebugLogger
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

private const val TAG = "PlaceResolver"
private const val ASSET_NAME = "us_places_v1.sqlite"

// Layer codes — must match tools/bake-place-polygons.js.
private const val LAYER_CITY    = 1
private const val LAYER_BUFFER  = 2
private const val LAYER_TOWN    = 3
private const val LAYER_COUNTY  = 4

// Resolution preference: TOWN_ADJACENT > CITY > BUFFER > COUNTY.
private val LAYER_PREFERENCE = intArrayOf(LAYER_TOWN, LAYER_CITY, LAYER_BUFFER, LAYER_COUNTY)

object PlaceResolver {

    @Volatile private var db: SQLiteDatabase? = null

    /**
     * Open the bundled asset (copy to internal storage on first call). Cheap
     * after first call. Safe to invoke from any thread.
     */
    fun ensureOpen(context: Context): SQLiteDatabase? {
        db?.let { return it }
        synchronized(this) {
            db?.let { return it }
            return try {
                val dbFile = File(context.filesDir, ASSET_NAME)
                val assetLen = context.assets.openFd(ASSET_NAME).use { it.length }
                val needCopy = !dbFile.exists() ||
                    dbFile.length() == 0L ||
                    dbFile.length() != assetLen
                if (needCopy) {
                    context.assets.open(ASSET_NAME).use { input ->
                        FileOutputStream(dbFile).use { out -> input.copyTo(out) }
                    }
                    DebugLogger.i(TAG, "copied $ASSET_NAME to filesDir (${dbFile.length()} bytes)")
                }
                val opened = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY,
                )
                db = opened
                opened
            } catch (t: Throwable) {
                DebugLogger.e(TAG, "open failed: ${t.message}")
                null
            }
        }
    }

    /**
     * Resolve a (lat, lon) to a [PlaceHit] using layered PIP. Returns null on
     * any error; returns OFFGRID PlaceHit when no polygon matches.
     */
    fun resolve(context: Context, lat: Double, lon: Double): PlaceHit {
        val database = ensureOpen(context)
            ?: return PlaceHit(PlaceKind.OFFGRID, name = "", admin = null)

        // Bbox prefilter via the composite index on (maxx, minx, maxy, miny).
        // Android SQLite ships without the R-Tree extension, so we use the
        // regular bbox columns stored on `places`. ~3,400 rows; the index
        // brings the scan to sub-millisecond.
        val cursor = database.rawQuery(
            "SELECT id, layer, name, admin_name FROM places " +
                "WHERE minx <= ? AND maxx >= ? AND miny <= ? AND maxy >= ?",
            arrayOf(lon.toString(), lon.toString(), lat.toString(), lat.toString()),
        )

        val candidates = HashMap<Int, MutableList<Cand>>()
        cursor.use {
            while (it.moveToNext()) {
                val cand = Cand(
                    id = it.getInt(0),
                    layer = it.getInt(1),
                    name = it.getString(2),
                    admin = it.getString(3),
                )
                candidates.getOrPut(cand.layer) { mutableListOf() }.add(cand)
            }
        }

        for (layer in LAYER_PREFERENCE) {
            val list = candidates[layer] ?: continue
            for (c in list) {
                if (pointInPolygon(database, c.id, lat, lon)) {
                    return PlaceHit(layer.toKind(), c.name, c.admin)
                }
            }
        }
        return PlaceHit(PlaceKind.OFFGRID, name = "", admin = null)
    }

    private fun Int.toKind(): PlaceKind = when (this) {
        LAYER_CITY   -> PlaceKind.IN_CITY
        LAYER_BUFFER -> PlaceKind.NEAR_CITY
        LAYER_TOWN   -> PlaceKind.IN_TOWN_ADJACENT_TO_SALEM
        LAYER_COUNTY -> PlaceKind.IN_COUNTY
        else         -> PlaceKind.OFFGRID
    }

    private fun pointInPolygon(database: SQLiteDatabase, id: Int, lat: Double, lon: Double): Boolean {
        val cursor = database.rawQuery(
            "SELECT wkb_gz FROM place_geom WHERE id = ?",
            arrayOf(id.toString()),
        )
        cursor.use {
            if (!it.moveToFirst()) return false
            val gz = it.getBlob(0) ?: return false
            val wkb = gunzip(gz) ?: return false
            return WkbPip.contains(wkb, lon, lat)   // WKB stores (x=lon, y=lat)
        }
    }
}

private data class Cand(val id: Int, val layer: Int, val name: String, val admin: String?)

private fun gunzip(input: ByteArray): ByteArray? = try {
    GZIPInputStream(ByteArrayInputStream(input)).use { it.readBytes() }
} catch (_: Throwable) { null }

// ──────────────────────────────────────────────────────────────────────────
// WKB → point-in-polygon (Polygon + MultiPolygon, EPSG:4269 lat/lon)
// ──────────────────────────────────────────────────────────────────────────

private object WkbPip {

    private const val WKB_TYPE_POLYGON      = 3
    private const val WKB_TYPE_MULTIPOLYGON = 6

    /** True if (x, y) is inside the polygon/multipolygon encoded in WKB. */
    fun contains(wkb: ByteArray, x: Double, y: Double): Boolean {
        val buf = ByteBuffer.wrap(wkb)
        return readGeom(buf, x, y)
    }

    private fun readGeom(buf: ByteBuffer, x: Double, y: Double): Boolean {
        val byteOrder = buf.get().toInt() and 0xFF
        buf.order(if (byteOrder == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        val type = buf.int and 0xFFFF   // strip Z/M flags
        return when (type) {
            WKB_TYPE_POLYGON      -> polygonContains(buf, x, y)
            WKB_TYPE_MULTIPOLYGON -> multiPolygonContains(buf, x, y)
            else -> false
        }
    }

    private fun multiPolygonContains(buf: ByteBuffer, x: Double, y: Double): Boolean {
        val numPolygons = buf.int
        var hit = false
        for (i in 0 until numPolygons) {
            // Each sub-polygon has its own byte-order + type prefix.
            val byteOrder = buf.get().toInt() and 0xFF
            buf.order(if (byteOrder == 1) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
            val type = buf.int and 0xFFFF
            if (type != WKB_TYPE_POLYGON) {
                // Malformed — bail; rest of buffer may be corrupted but we stop.
                return false
            }
            if (!hit && polygonContains(buf, x, y)) {
                hit = true
                // Don't break — must keep buf position consistent in case caller
                // wants to re-read; but since we only test once per WKB, we can.
                return true
            } else {
                skipPolygon(buf)
            }
        }
        return hit
    }

    private fun polygonContains(buf: ByteBuffer, x: Double, y: Double): Boolean {
        val numRings = buf.int
        var inOuter = false
        for (r in 0 until numRings) {
            val numPoints = buf.int
            val ring = DoubleArray(numPoints * 2)
            for (i in 0 until numPoints) {
                ring[2 * i]     = buf.double
                ring[2 * i + 1] = buf.double
            }
            val inThisRing = ringContains(ring, x, y)
            if (r == 0) {
                if (!inThisRing) {
                    // Skip remaining rings in this polygon — point isn't even in
                    // the outer ring.
                    val remaining = numRings - 1 - r
                    for (k in 0 until remaining) {
                        val n = buf.int
                        buf.position(buf.position() + n * 16)
                    }
                    return false
                }
                inOuter = true
            } else if (inThisRing) {
                // Point is inside a hole — not contained.
                val remaining = numRings - 1 - r
                for (k in 0 until remaining) {
                    val n = buf.int
                    buf.position(buf.position() + n * 16)
                }
                return false
            }
        }
        return inOuter
    }

    /** Skip a single Polygon body (numRings already at buffer position). */
    private fun skipPolygon(buf: ByteBuffer) {
        val numRings = buf.int
        for (r in 0 until numRings) {
            val numPoints = buf.int
            buf.position(buf.position() + numPoints * 16)
        }
    }

    /** Standard ray-casting PIP for a single linear ring. */
    private fun ringContains(ring: DoubleArray, x: Double, y: Double): Boolean {
        val n = ring.size / 2
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = ring[2 * i];     val yi = ring[2 * i + 1]
            val xj = ring[2 * j];     val yj = ring[2 * j + 1]
            val intersects = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
