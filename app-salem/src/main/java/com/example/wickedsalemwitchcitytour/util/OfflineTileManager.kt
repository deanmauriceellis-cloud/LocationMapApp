/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
     * S305 (perf/stability review finding #1, CRITICAL): the ~370 MB archive
     * copy + verify used to run synchronously inside Application.onCreate — a
     * multi-second main-thread freeze (cold-start ANR) on first launch and after
     * every APK update, before the splash even appeared. It now runs on this
     * background IO scope; callers gate the map handoff via [awaitReady].
     */
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val ready = CompletableDeferred<Boolean>()
    @Volatile private var started = false

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
     * Kick off tile-archive extraction + verification on a background thread.
     * Idempotent — safe to call once from Application.onCreate. Copies the
     * bundled APK asset to osmdroid's base path on first launch (or when the
     * on-disk archive's size differs from the asset's — upgrade detection),
     * then verifies it. Callers MUST [awaitReady] before rendering the map so
     * tiles are present (the splash gates its handoff to the map on this).
     *
     * S305: replaces the old synchronous `extractArchiveIfNeeded` which blocked
     * Application.onCreate (the cold-start ANR — review finding #1).
     */
    fun startExtraction(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        ioScope.launch {
            val ok = try {
                extractAndVerifyBlocking(app)
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Tile extraction failed: ${e.message}", e)
                false
            }
            ready.complete(ok)
        }
    }

    /**
     * Suspend until [startExtraction]'s work has finished (success or failure),
     * returning whether the archive is present + valid. Returns immediately once
     * complete, so repeat callers and post-extraction launches don't block.
     * If extraction was never started (e.g. a unit test), returns true (no gate).
     */
    suspend fun awaitReady(): Boolean = if (started) ready.await() else true

    /** Non-suspending peek for UX (e.g. show a "preparing" splash hint). */
    fun isReady(): Boolean = ready.isCompleted

    /**
     * The actual blocking copy + verify. Runs on [ioScope] (Dispatchers.IO).
     * Returns true if the archive is present and verifies.
     */
    private fun extractAndVerifyBlocking(context: Context): Boolean {
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
                "Extracting $ARCHIVE_NAME from APK assets (on-disk=${archive.length()}, asset=$assetSize) — background"
            )
            val t0 = System.currentTimeMillis()
            try {
                context.assets.open(ARCHIVE_NAME).use { input ->
                    archive.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                    }
                }
                DebugLogger.i(TAG,
                    "Extracted ${archive.length() / 1024} KB in ${System.currentTimeMillis() - t0}ms to $archive")
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to extract $ARCHIVE_NAME: ${e.message}", e)
                return false
            }
        }

        val sizeMb = archive.length() / 1024.0 / 1024.0
        DebugLogger.i(TAG, "Tile archive present: ${"%.1f".format(sizeMb)} MB at $archive")
        // S305 (#30): the full per-provider GROUP BY scan over the 370 MB DB is
        // only worth it right after a fresh copy. On a repeat launch a cheap
        // open + single-row probe confirms the DB is valid without scanning.
        return if (needsCopy) verifyArchiveFull(archive) else verifyArchiveCheap(archive)
    }

    /**
     * Full verification after a fresh copy: open the archive and log tile counts
     * per provider. Confirms it's a valid DB with the expected `tiles` schema.
     */
    private fun verifyArchiveFull(file: File): Boolean {
        return try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                db.rawQuery(
                    "SELECT provider, COUNT(*) as cnt FROM tiles GROUP BY provider", null
                ).use { cursor ->
                    val counts = mutableListOf<String>()
                    while (cursor.moveToNext()) {
                        counts.add("${cursor.getString(0)}=${cursor.getInt(1)}")
                    }
                    DebugLogger.i(TAG, "Archive verified (full): ${counts.joinToString(", ")} (${file.length() / 1024}KB)")
                }
            }
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Archive full verification failed: ${e.message}", e)
            false
        }
    }

    /**
     * Cheap per-launch validity check: open the DB and confirm the `tiles`
     * table responds to a single-row probe (no full-table scan).
     */
    private fun verifyArchiveCheap(file: File): Boolean {
        return try {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            ).use { db ->
                db.rawQuery("SELECT 1 FROM tiles LIMIT 1", null).use { c ->
                    val ok = c.moveToFirst()
                    DebugLogger.i(TAG, "Archive present + valid (cheap probe ok=$ok, ${file.length() / 1024}KB)")
                    ok
                }
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Archive cheap verification failed: ${e.message}", e)
            false
        }
    }
}
