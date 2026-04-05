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
 * On first launch, copies the bundled tile archive from APK assets to osmdroid's
 * base path. osmdroid's MapTileFileArchiveProvider auto-discovers .sqlite files
 * and serves tiles from them — no custom tile provider needed.
 *
 * The archive contains pre-downloaded tiles for the Salem walking tour area
 * (zoom 14-18, satellite + street) so the app works fully offline.
 */
object OfflineTileManager {

    private const val TAG = "OfflineTileManager"
    private const val ARCHIVE_ASSET = "salem_tiles.sqlite"
    private const val PREFS_KEY = "offline_tiles_version"
    private const val CURRENT_VERSION = 1

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
     * Copy the bundled tile archive from assets to osmdroid's base path.
     * Call AFTER Configuration.getInstance().load() so paths are resolved.
     * Uses a version number in SharedPreferences to avoid re-extracting
     * on every launch and to allow updating the archive in future releases.
     */
    fun extractArchiveIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("offline_tiles", Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(PREFS_KEY, 0)

        if (installedVersion >= CURRENT_VERSION) {
            DebugLogger.i(TAG, "Offline tiles v$CURRENT_VERSION already installed")
            return
        }

        val basePath = Configuration.getInstance().osmdroidBasePath
        DebugLogger.d(TAG, "osmdroidBasePath resolved to: $basePath")
        DebugLogger.d(TAG, "osmdroidTileCache resolved to: ${Configuration.getInstance().osmdroidTileCache}")
        basePath.mkdirs()

        val destFile = File(basePath, ARCHIVE_ASSET)
        DebugLogger.d(TAG, "Archive destination: $destFile (exists=${destFile.exists()})")

        // List existing files in base path for debugging
        val existingFiles = basePath.listFiles()
        DebugLogger.d(TAG, "Files in basePath: ${existingFiles?.map { "${it.name} (${it.length()} bytes)" } ?: "null"}")

        try {
            val assetList = context.assets.list("") ?: emptyArray()
            DebugLogger.d(TAG, "Assets root contains: ${assetList.filter { it.contains("tile") || it.contains("sqlite") }.joinToString()}")
            if (ARCHIVE_ASSET !in assetList) {
                DebugLogger.w(TAG, "No bundled tile archive '$ARCHIVE_ASSET' in assets — asset list: ${assetList.take(20).joinToString()}")
                return
            }

            DebugLogger.i(TAG, "Extracting offline tiles from assets → $destFile")
            val startTime = System.currentTimeMillis()

            context.assets.open(ARCHIVE_ASSET).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            val elapsed = System.currentTimeMillis() - startTime
            val sizeMb = destFile.length() / 1024.0 / 1024.0
            DebugLogger.i(TAG, "Extracted ${"%.1f".format(sizeMb)} MB in ${elapsed}ms → $destFile")

            // Verify the extracted database
            verifyArchive(destFile)

            prefs.edit().putInt(PREFS_KEY, CURRENT_VERSION).apply()
            DebugLogger.i(TAG, "Offline tiles v$CURRENT_VERSION installed successfully")
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to extract offline tiles: ${e.message}", e)
            destFile.delete()
        }
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
