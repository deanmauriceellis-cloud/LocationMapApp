/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
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

    private val cache = object : LruCache<Long, Bitmap>(256) {
        override fun sizeOf(key: Long, value: Bitmap): Int = 1
    }

    private val missing = HashSet<Long>()

    fun tile(z: Int, x: Int, y: Int): Bitmap? {
        val key = MercatorMath.osmdroidKey(z, x, y)
        cache.get(key)?.let { return it }
        synchronized(missing) { if (missing.contains(key)) return null }

        val cursor = db.rawQuery(
            "SELECT tile FROM tiles WHERE key = ? AND provider = ? LIMIT 1",
            arrayOf(key.toString(), provider)
        )
        try {
            if (cursor.moveToFirst()) {
                val blob = cursor.getBlob(0)
                val bmp = BitmapFactory.decodeByteArray(blob, 0, blob.size)
                if (bmp != null) {
                    cache.put(key, bmp)
                    return bmp
                }
            }
        } finally {
            cursor.close()
        }
        synchronized(missing) { missing.add(key) }
        return null
    }

    fun close() {
        cache.evictAll()
        db.close()
    }
}
