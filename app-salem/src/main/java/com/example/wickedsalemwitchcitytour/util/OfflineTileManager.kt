/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.locationmapapp.util.DebugLogger
import org.osmdroid.config.Configuration
import java.io.File

/**
 * Manages offline map tile archives for downtown Salem.
 *
 * The tile archive (`salem_tiles.sqlite`, ~200 MB with three providers:
 * Satellite + Mapnik + Witchy) is NOT bundled in the APK — it's too big for
 * GitHub (>100 MB) and for a Play Store AAB. It must arrive on the device
 * out-of-band:
 *   • Dev (adb):    adb push tools/tile-bake/dist/salem_tiles.sqlite \
 *                     /sdcard/Android/data/<pkg>/files/salem_tiles.sqlite
 *   • Prod (future): Play Asset Delivery on-install pack.
 *
 * osmdroid's MapTileFileArchiveProvider auto-discovers .sqlite files in the
 * base path configured here. No runtime copy step needed.
 */
object OfflineTileManager {

    private const val TAG = "OfflineTileManager"
    private const val ARCHIVE_NAME = "salem_tiles.sqlite"

    /**
     * Set osmdroid's base path to the app's external files directory via
     * SharedPreferences. Must be called BEFORE Configuration.getInstance().load()
     * so that osmdroid picks up the path. This ensures scoped storage compatibility
     * on Android 10+ and survives the activity's duplicate load() call.
     */
    fun configureStoragePath(context: Context) {
        val externalDir = context.getExternalFilesDir(null)
        val basePath = (externalDir ?: context.filesDir).absolutePath
        DebugLogger.d(TAG, "externalFilesDir=$externalDir, filesDir=${context.filesDir}")
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString("osmdroid.basePath", basePath)
            .apply()
        DebugLogger.i(TAG, "osmdroid base path configured: $basePath")
    }

    /**
     * Verify the tile archive is present at osmdroid's base path. If missing,
     * log a clear instruction for the dev adb-push command. No copy, no
     * extraction — the archive MUST arrive on the device out-of-band.
     */
    fun extractArchiveIfNeeded(context: Context) {
        val basePath = Configuration.getInstance().osmdroidBasePath
        basePath.mkdirs()
        val archive = File(basePath, ARCHIVE_NAME)

        if (!archive.exists()) {
            DebugLogger.w(TAG,
                "Missing $ARCHIVE_NAME at $basePath — push it manually:\n" +
                "  adb push tools/tile-bake/dist/$ARCHIVE_NAME " +
                "${basePath.absolutePath}/$ARCHIVE_NAME"
            )
            return
        }

        val sizeMb = archive.length() / 1024.0 / 1024.0
        DebugLogger.i(TAG, "Tile archive present: ${"%.1f".format(sizeMb)} MB at $archive")
        verifyArchive(archive)
    }

    /**
     * Open the extracted SQLite archive and log tile counts per provider.
     * This verifies the file is a valid database and the schema is correct.
     */
    private fun verifyArchive(file: File) {
        try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            val cursor = db.rawQuery(
                "SELECT provider, COUNT(*) as cnt FROM tiles GROUP BY provider", null
            )
            val counts = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val provider = cursor.getString(0)
                val count = cursor.getInt(1)
                counts.add("$provider=$count")
            }
            cursor.close()

            // Get zoom range
            val zoomCursor = db.rawQuery(
                "SELECT MIN(key), MAX(key) FROM tiles", null
            )
            zoomCursor.moveToFirst()
            zoomCursor.close()

            db.close()
            DebugLogger.i(TAG, "Archive verified: ${counts.joinToString(", ")} (${file.length() / 1024}KB)")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Archive verification failed: ${e.message}", e)
        }
    }
}
