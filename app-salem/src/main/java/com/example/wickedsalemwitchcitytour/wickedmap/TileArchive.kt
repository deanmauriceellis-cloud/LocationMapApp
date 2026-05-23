/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/**
 * Reads tiles from a salem_tiles.sqlite archive (osmdroid SqliteArchive format).
 * Schema: tiles(key INTEGER, provider TEXT, tile BLOB, PRIMARY KEY (key, provider))
 * Thread-safe: SQLiteDatabase supports concurrent reads.
 */
class TileArchive(archiveFile: File, private val provider: String = "Salem-Custom") {

    private val db: SQLiteDatabase = SQLiteDatabase.openDatabase(
        archiveFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY
    )

    // Byte-sized cache: 1/8 of the runtime heap, capped at Int.MAX_VALUE.
    // sizeOf returns the bitmap's actual allocation in bytes (API 19+).
    private val cache = object : LruCache<Long, Bitmap>(
        ((Runtime.getRuntime().maxMemory() / 8L).coerceAtMost(Int.MAX_VALUE.toLong())).toInt()
    ) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.allocationByteCount
    }

    private val missing = HashSet<Long>()

    // S243 — periodic-summary verbose stats. Per-call logging would flood the
    // collector (30+ tile lookups per frame); we aggregate and emit once per
    // 5s instead. Debug-gated.
    private var hits = 0L
    private var decoded = 0L
    private var decodedBytes = 0L
    private var nullDecodes = 0L
    private var missingHits = 0L
    private var lastSummaryAtMs = 0L

    fun tile(z: Int, x: Int, y: Int): Bitmap? {
        val key = MercatorMath.osmdroidKey(z, x, y)
        cache.get(key)?.let {
            hits++
            maybeLogSummary()
            return it
        }
        synchronized(missing) {
            if (missing.contains(key)) {
                missingHits++
                maybeLogSummary()
                return null
            }
        }

        val cursor = db.rawQuery(
            "SELECT tile, length(tile) FROM tiles WHERE key = ? AND provider = ? LIMIT 1",
            arrayOf(key.toString(), provider)
        )
        try {
            if (cursor.moveToFirst()) {
                val blob = cursor.getBlob(0)
                val blobLen = cursor.getInt(1)
                val bmp = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                if (bmp != null) {
                    cache.put(key, bmp)
                    decoded++
                    decodedBytes += bmp.allocationByteCount.toLong()
                    // S289 verbose: log first decode per (z,x,y) so we can correlate
                    // which tiles in which area are being served.
                    if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
                        com.example.locationmapapp.util.DebugLogger.d(
                            "TileArchive",
                            "DECODE $provider z=$z x=$x y=$y blob=$blobLen B",
                        )
                    }
                    maybeLogSummary()
                    return bmp
                } else {
                    nullDecodes++
                    if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
                        com.example.locationmapapp.util.DebugLogger.w(
                            "TileArchive",
                            "decode returned null z=$z x=$x y=$y blobSize=${blob.size}",
                        )
                    }
                }
            }
        } finally {
            cursor.close()
        }
        // S289 verbose: log first MISS per (z,x,y) so we see exactly which tile keys
        // are being requested by the overlay/basemap path but don't exist in the DB.
        val firstMiss: Boolean
        synchronized(missing) {
            firstMiss = missing.add(key)
        }
        if (firstMiss && com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
            com.example.locationmapapp.util.DebugLogger.d(
                "TileArchive",
                "MISS $provider z=$z x=$x y=$y (no row)",
            )
        }
        maybeLogSummary()
        return null
    }

    private fun maybeLogSummary() {
        if (!com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) return
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastSummaryAtMs < 5_000L) return
        lastSummaryAtMs = now
        com.example.locationmapapp.util.DebugLogger.d(
            "TileArchive",
            "cache hits=$hits decoded=$decoded ($decodedBytes B) nullDecodes=$nullDecodes " +
                "missingHits=$missingHits cacheSize=${cache.size()}/${cache.maxSize()} " +
                "missingKeys=${synchronized(missing) { missing.size }}",
        )
    }

    fun close() {
        cache.evictAll()
        db.close()
    }
}
