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
 * Manages offline map tile archives for Salem city.
 *
 * S167: `salem_tiles.sqlite` was shrunk from 207 MB to ~30 MB (Witchy-only,
 * Salem city bbox, z16-19) and is now bundled in the APK at
 * `assets/salem_tiles.sqlite`. On first launch (or whenever the on-disk
 * archive is missing or differs in size from the asset) the archive is
 * copied to the app's external files dir where osmdroid's
 * `MapTileFileArchiveProvider` auto-discovers it.
 *
 * Asset stays uncompressed in the APK (`noCompress "sqlite"` in
 * `app-salem/build.gradle`) so `AssetFileDescriptor.getLength()` returns
 * the true archive size — enabling a cheap first-launch size-based
 * version check without reading the file.
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
     * Ensure the tile archive is present at osmdroid's base path. Copies
     * from the bundled APK asset on first launch, or when the on-disk
     * archive's size differs from the asset's size (upgrade detection).
     */
    fun extractArchiveIfNeeded(context: Context) {
        val basePath = Configuration.getInstance().osmdroidBasePath
        basePath.mkdirs()
        val archive = File(basePath, ARCHIVE_NAME)

        val assetSize: Long = try {
            context.assets.openFd(ARCHIVE_NAME).use { it.length }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to stat asset $ARCHIVE_NAME: ${e.message}", e)
            -1L
        }

        val needsCopy = !archive.exists() ||
            (assetSize > 0 && archive.length() != assetSize)

        if (needsCopy) {
            DebugLogger.i(TAG,
                "Extracting $ARCHIVE_NAME from APK assets (on-disk=${archive.length()}, asset=$assetSize)"
            )
            try {
                context.assets.open(ARCHIVE_NAME).use { input ->
                    archive.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                DebugLogger.i(TAG, "Extracted ${archive.length() / 1024} KB to $archive")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to extract $ARCHIVE_NAME: ${e.message}", e)
                return
            }
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
