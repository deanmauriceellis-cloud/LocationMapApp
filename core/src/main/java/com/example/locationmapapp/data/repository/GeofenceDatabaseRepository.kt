/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.example.locationmapapp.data.model.GeofenceDatabaseInfo
import com.example.locationmapapp.data.model.TfrShape
import com.example.locationmapapp.data.model.TfrZone
import com.example.locationmapapp.data.model.ZoneType
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.network.LocalServerCircuitBreakerInterceptor
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module GeofenceDatabaseRepository.kt"

@Singleton
class GeofenceDatabaseRepository @Inject constructor(
    private val context: Context
) {
    private val TAG = "GeofenceDbRepo"
    private val BASE = "http://10.0.0.229:4300"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(LocalServerCircuitBreakerInterceptor())
        .build()

    private val dbDir: File
        get() = File(context.filesDir, "geofence_databases").also { it.mkdirs() }

    // ── Catalog ──────────────────────────────────────────────────────────────

    suspend fun fetchCatalog(): List<GeofenceDatabaseInfo> = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val request = Request.Builder().url("$BASE/geofences/catalog").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "Catalog fetch failed: HTTP ${response.code}")
                return@withContext emptyList()
            }
            val body = response.body?.string().orEmpty()
            DebugLogger.i(TAG, "Catalog fetched in ${System.currentTimeMillis() - t0}ms")
            val type = object : TypeToken<List<GeofenceDatabaseInfo>>() {}.type
            val catalog: List<GeofenceDatabaseInfo> = gson.fromJson(body, type)
            val installed = getInstalledDatabases()
            catalog.map { info ->
                if (info.id in installed) {
                    val localMeta = getDatabaseMeta(info.id)
                    val localVersion = localMeta?.get("version")?.toIntOrNull() ?: 0
                    info.copy(installed = true, installedVersion = localVersion)
                } else info
            }
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Catalog fetch error: ${e.message}", e)
            emptyList()
        }
    }

    // ── Download ─────────────────────────────────────────────────────────────

    suspend fun downloadDatabase(id: String, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        try {
            val request = Request.Builder()
                .url("$BASE/geofences/database/$id/download")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                DebugLogger.e(TAG, "Download $id failed: HTTP ${response.code}")
                return@withContext false
            }
            val body = response.body ?: return@withContext false
            val totalBytes = body.contentLength()
            val file = File(dbDir, "$id.db")
            val tmpFile = File(dbDir, "$id.db.tmp")

            body.byteStream().use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Long = 0
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                        bytesRead += len
                        if (totalBytes > 0) {
                            onProgress(((bytesRead * 100) / totalBytes).toInt())
                        }
                    }
                }
            }
            tmpFile.renameTo(file)
            DebugLogger.i(TAG, "Downloaded $id (${file.length()} bytes) in ${System.currentTimeMillis() - t0}ms")
            true
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Download $id error: ${e.message}", e)
            File(dbDir, "$id.db.tmp").delete()
            false
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────

    fun deleteDatabase(id: String): Boolean {
        val file = File(dbDir, "$id.db")
        val deleted = file.delete()
        DebugLogger.i(TAG, "Delete $id: $deleted")
        return deleted
    }

    // ── Installed ────────────────────────────────────────────────────────────

    fun getInstalledDatabases(): Set<String> {
        return dbDir.listFiles()
            ?.filter { it.extension == "db" }
            ?.map { it.nameWithoutExtension }
            ?.toSet()
            ?: emptySet()
    }

    // ── Meta ─────────────────────────────────────────────────────────────────

    fun getDatabaseMeta(id: String): Map<String, String>? {
        val file = File(dbDir, "$id.db")
        if (!file.exists()) return null
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val meta = mutableMapOf<String, String>()
            val cursor = db.rawQuery("SELECT key, value FROM db_meta", null)
            cursor.use {
                while (it.moveToNext()) {
                    meta[it.getString(0)] = it.getString(1)
                }
            }
            meta
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Meta read error for $id: ${e.message}", e)
            null
        } finally {
            db?.close()
        }
    }

    // ── Load zones by bbox ───────────────────────────────────────────────────

    suspend fun loadZonesFromDatabaseInBbox(
        id: String, s: Double, w: Double, n: Double, e: Double
    ): List<TfrZone> = withContext(Dispatchers.IO) {
        val file = File(dbDir, "$id.db")
        if (!file.exists()) return@withContext emptyList()
        val t0 = System.currentTimeMillis()
        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            val cursor = db.rawQuery(
                """SELECT zone_id, name, description, zone_type, severity,
                          geometry_type, geometry, center_lat, center_lon, radius_m,
                          floor_alt_ft, ceil_alt_ft, speed_limit, metadata
                   FROM zones
                   WHERE lat_max >= ? AND lat_min <= ? AND lon_max >= ? AND lon_min <= ?""",
                arrayOf(s.toString(), n.toString(), w.toString(), e.toString())
            )
            val zones = mutableListOf<TfrZone>()
            cursor.use {
                while (it.moveToNext()) {
                    val zoneId = it.getString(0)
                    val name = it.getString(1)
                    val desc = it.getString(2) ?: ""
                    val zoneTypeStr = it.getString(3) ?: "CUSTOM"
                    val geomType = it.getString(5) ?: "polygon"
                    val geomJson = it.getString(6)
                    val centerLat = if (it.isNull(7)) null else it.getDouble(7)
                    val centerLon = if (it.isNull(8)) null else it.getDouble(8)
                    val radiusM = if (it.isNull(9)) null else it.getDouble(9)
                    val floorAlt = if (it.isNull(10)) 0 else it.getInt(10)
                    val ceilAlt = if (it.isNull(11)) 99999 else it.getInt(11)
                    val metadataJson = it.getString(13) ?: "{}"

                    val zoneType = try { ZoneType.valueOf(zoneTypeStr) } catch (_: Exception) { ZoneType.CUSTOM }

                    val shapes = when (geomType) {
                        "circle" -> {
                            if (centerLat != null && centerLon != null && radiusM != null) {
                                listOf(GeofenceRepository.generateCircleShape(centerLat, centerLon, radiusM))
                            } else continue
                        }
                        else -> {
                            if (geomJson != null) {
                                parsePolygonGeometry(geomJson, floorAlt, ceilAlt)
                            } else continue
                        }
                    }
                    if (shapes.isEmpty()) continue

                    val meta: Map<String, String> = try {
                        val parsed: Map<String, String> = gson.fromJson(
                            metadataJson, object : TypeToken<Map<String, String>>() {}.type
                        )
                        parsed
                    } catch (_: Exception) { emptyMap() }

                    zones.add(TfrZone(
                        id = zoneId, notam = name, type = zoneTypeStr,
                        description = desc, effectiveDate = "", expireDate = "",
                        shapes = shapes, facility = "", state = "",
                        zoneType = zoneType, metadata = meta
                    ))
                }
            }
            DebugLogger.i(TAG, "Loaded ${zones.size} zones from $id in ${System.currentTimeMillis() - t0}ms")
            zones
        } catch (ex: Exception) {
            DebugLogger.e(TAG, "Load zones error for $id: ${ex.message}", ex)
            emptyList()
        } finally {
            db?.close()
        }
    }

    // ── Validate ───────────────────────────────────────────────────────────

    fun validateDatabase(dbPath: String): String? {
        var db: SQLiteDatabase? = null
        return try {
            db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            // Check tables exist
            val tables = mutableSetOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) tables.add(c.getString(0))
            }
            if ("db_meta" !in tables) return "Missing db_meta table"
            if ("zones" !in tables) return "Missing zones table"

            // Check zones has required columns
            val requiredCols = setOf("zone_id", "name", "zone_type", "geometry_type",
                "lat_min", "lat_max", "lon_min", "lon_max")
            val actualCols = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(zones)", null).use { c ->
                while (c.moveToNext()) actualCols.add(c.getString(1))
            }
            val missing = requiredCols - actualCols
            if (missing.isNotEmpty()) return "Missing columns: ${missing.joinToString()}"

            // Check db_meta has id
            var hasId = false
            db.rawQuery("SELECT value FROM db_meta WHERE key='id'", null).use { c ->
                if (c.moveToFirst()) hasId = c.getString(0).isNotBlank()
            }
            if (!hasId) return "Missing 'id' in db_meta"

            // Check zone count > 0
            var count = 0L
            db.rawQuery("SELECT COUNT(*) FROM zones", null).use { c ->
                if (c.moveToFirst()) count = c.getLong(0)
            }
            if (count == 0L) return "Database has no zones"

            null // valid
        } catch (e: Exception) {
            "Not a valid SQLite database: ${e.message}"
        } finally {
            db?.close()
        }
    }

    // ── Import SQLite ─────────────────────────────────────────────────────

    suspend fun importSqliteDatabase(
        contentResolver: ContentResolver, uri: Uri, overwriteId: String? = null
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val tmpFile = File(dbDir, "_import_tmp.db")
        try {
            // Copy content URI to temp file
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmpFile).use { output -> input.copyTo(output) }
            } ?: return@withContext Pair(false, "Could not open file")

            // Validate
            val error = validateDatabase(tmpFile.absolutePath)
            if (error != null) {
                tmpFile.delete()
                return@withContext Pair(false, error)
            }

            // Read id and name from db_meta
            var dbId = ""
            var dbName = ""
            SQLiteDatabase.openDatabase(tmpFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT key, value FROM db_meta WHERE key IN ('id','name')", null).use { c ->
                    while (c.moveToNext()) {
                        when (c.getString(0)) {
                            "id" -> dbId = c.getString(1)
                            "name" -> dbName = c.getString(1)
                        }
                    }
                }
            }
            if (dbId.isBlank()) {
                tmpFile.delete()
                return@withContext Pair(false, "No database ID in metadata")
            }

            // Check for duplicate
            val targetFile = File(dbDir, "$dbId.db")
            if (targetFile.exists() && overwriteId != dbId) {
                tmpFile.delete()
                return@withContext Pair(false, "DUPLICATE:$dbId:$dbName")
            }

            // Install
            tmpFile.renameTo(targetFile)
            DebugLogger.i(TAG, "Imported SQLite database: $dbId ($dbName), ${targetFile.length()} bytes")
            Pair(true, "Imported \"$dbName\" ($dbId)")
        } catch (e: Exception) {
            tmpFile.delete()
            DebugLogger.e(TAG, "Import SQLite error: ${e.message}", e)
            Pair(false, "Import failed: ${e.message}")
        }
    }

    // ── Import CSV ────────────────────────────────────────────────────────

    suspend fun importCsvAsDatabase(
        contentResolver: ContentResolver, uri: Uri,
        databaseId: String, databaseName: String,
        zoneType: ZoneType, defaultRadius: Double
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // Read all lines
            val lines = mutableListOf<String>()
            contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    reader.forEachLine { lines.add(it) }
                }
            } ?: return@withContext Pair(false, "Could not open file")

            if (lines.size < 2) return@withContext Pair(false, "CSV has no data rows")

            // Parse header
            val header = parseCsvLine(lines[0]).map { it.lowercase().trim() }
            val colAliases = mapOf(
                "lat" to setOf("lat", "latitude"),
                "lon" to setOf("lon", "lng", "longitude"),
                "name" to setOf("name", "title", "label"),
                "type" to setOf("type", "zone_type", "category"),
                "radius" to setOf("radius", "radius_m"),
                "description" to setOf("description", "desc", "notes")
            )
            val colIndex = mutableMapOf<String, Int>()
            for ((key, aliases) in colAliases) {
                val idx = header.indexOfFirst { it in aliases }
                if (idx >= 0) colIndex[key] = idx
            }
            if ("lat" !in colIndex || "lon" !in colIndex) {
                return@withContext Pair(false, "CSV must have lat and lon columns (found: ${header.joinToString()})")
            }

            // Create SQLite database
            val targetFile = File(dbDir, "$databaseId.db")
            val tmpFile = File(dbDir, "${databaseId}_csv_tmp.db")
            tmpFile.delete()

            val db = SQLiteDatabase.openOrCreateDatabase(tmpFile, null)
            db.execSQL("CREATE TABLE db_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("""CREATE TABLE zones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                zone_id TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                description TEXT DEFAULT '',
                zone_type TEXT NOT NULL,
                severity INTEGER DEFAULT 1,
                geometry_type TEXT NOT NULL,
                geometry TEXT,
                center_lat REAL, center_lon REAL, radius_m REAL,
                floor_alt_ft INTEGER DEFAULT 0,
                ceil_alt_ft INTEGER DEFAULT 99999,
                speed_limit INTEGER,
                time_restrict TEXT,
                metadata TEXT DEFAULT '{}',
                lat_min REAL, lat_max REAL, lon_min REAL, lon_max REAL
            )""")
            db.execSQL("CREATE INDEX idx_zones_bbox ON zones (lat_min, lat_max, lon_min, lon_max)")
            db.execSQL("CREATE INDEX idx_zones_zone_id ON zones (zone_id)")

            var inserted = 0
            var skipped = 0

            db.beginTransaction()
            try {
                val stmt = db.compileStatement(
                    """INSERT INTO zones (zone_id, name, description, zone_type, severity,
                       geometry_type, center_lat, center_lon, radius_m,
                       metadata, lat_min, lat_max, lon_min, lon_max)
                       VALUES (?, ?, ?, ?, 1, 'circle', ?, ?, ?, '{}', ?, ?, ?, ?)"""
                )

                for (i in 1 until lines.size) {
                    val fields = parseCsvLine(lines[i])
                    if (fields.size <= maxOf(colIndex["lat"]!!, colIndex["lon"]!!)) {
                        skipped++; continue
                    }
                    val lat = fields.getOrNull(colIndex["lat"]!!)?.toDoubleOrNull()
                    val lon = fields.getOrNull(colIndex["lon"]!!)?.toDoubleOrNull()
                    if (lat == null || lon == null || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                        skipped++; continue
                    }
                    val name = fields.getOrNull(colIndex["name"] ?: -1)?.takeIf { it.isNotBlank() }
                        ?: "Zone ${inserted + 1}"
                    val type = fields.getOrNull(colIndex["type"] ?: -1)?.takeIf { it.isNotBlank() }
                        ?: zoneType.name
                    val radius = fields.getOrNull(colIndex["radius"] ?: -1)?.toDoubleOrNull()
                        ?: defaultRadius
                    val desc = fields.getOrNull(colIndex["description"] ?: -1) ?: ""

                    // Compute bbox from center + radius
                    val latDelta = radius / 111320.0
                    val lonDelta = radius / (111320.0 * Math.cos(Math.toRadians(lat)))

                    stmt.clearBindings()
                    stmt.bindString(1, "$databaseId-$inserted")  // zone_id
                    stmt.bindString(2, name)
                    stmt.bindString(3, desc)
                    stmt.bindString(4, type)
                    stmt.bindDouble(5, lat)
                    stmt.bindDouble(6, lon)
                    stmt.bindDouble(7, radius)
                    stmt.bindDouble(8, lat - latDelta)   // lat_min
                    stmt.bindDouble(9, lat + latDelta)   // lat_max
                    stmt.bindDouble(10, lon - lonDelta)  // lon_min
                    stmt.bindDouble(11, lon + lonDelta)  // lon_max
                    stmt.executeInsert()
                    inserted++
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            if (inserted == 0) {
                db.close()
                tmpFile.delete()
                return@withContext Pair(false, "No valid rows in CSV (skipped $skipped)")
            }

            // Write metadata
            val metaStmt = db.compileStatement("INSERT INTO db_meta (key, value) VALUES (?, ?)")
            val now = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val metaEntries = mapOf(
                "id" to databaseId,
                "name" to databaseName,
                "description" to "Imported from CSV ($inserted zones)",
                "version" to "1",
                "zone_type" to zoneType.name,
                "zone_count" to inserted.toString(),
                "updated_at" to now,
                "source" to "CSV import",
                "license" to "User data"
            )
            for ((k, v) in metaEntries) {
                metaStmt.clearBindings()
                metaStmt.bindString(1, k)
                metaStmt.bindString(2, v)
                metaStmt.executeInsert()
            }
            db.close()

            // Install
            if (targetFile.exists()) targetFile.delete()
            tmpFile.renameTo(targetFile)
            val msg = "Imported $inserted zones" + if (skipped > 0) " ($skipped rows skipped)" else ""
            DebugLogger.i(TAG, "CSV import: $databaseId → $msg")
            Pair(true, msg)
        } catch (e: Exception) {
            File(dbDir, "${databaseId}_csv_tmp.db").delete()
            DebugLogger.e(TAG, "CSV import error: ${e.message}", e)
            Pair(false, "CSV import failed: ${e.message}")
        }
    }

    fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++ // escaped quote
                    } else {
                        inQuotes = false
                    }
                }
                c == ',' && !inQuotes -> { fields.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        fields.add(sb.toString())
        return fields
    }

    // ── Export ─────────────────────────────────────────────────────────────

    fun getDatabaseFile(id: String): File? {
        val file = File(dbDir, "$id.db")
        return if (file.exists()) file else null
    }

    // ── Local-only databases ──────────────────────────────────────────────

    fun getLocalOnlyDatabaseInfos(): List<GeofenceDatabaseInfo> {
        val results = mutableListOf<GeofenceDatabaseInfo>()
        val files = dbDir.listFiles()?.filter { it.extension == "db" } ?: return results
        for (file in files) {
            val id = file.nameWithoutExtension
            val meta = getDatabaseMeta(id) ?: continue
            results.add(GeofenceDatabaseInfo(
                id = meta["id"] ?: id,
                name = meta["name"] ?: id,
                description = meta["description"] ?: "",
                version = meta["version"]?.toIntOrNull() ?: 1,
                zoneType = meta["zone_type"] ?: "CUSTOM",
                zoneCount = meta["zone_count"]?.toIntOrNull() ?: 0,
                fileSize = file.length(),
                updatedAt = meta["updated_at"] ?: "",
                source = meta["source"] ?: "Local",
                license = meta["license"] ?: "",
                installed = true,
                installedVersion = meta["version"]?.toIntOrNull() ?: 1
            ))
        }
        return results
    }

    private fun parsePolygonGeometry(json: String, floorAlt: Int, ceilAlt: Int): List<TfrShape> {
        return try {
            val arr = JsonParser.parseString(json).asJsonArray
            val points = arr.map { p ->
                val pair = p.asJsonArray
                listOf(pair[0].asDouble, pair[1].asDouble)
            }
            if (points.size >= 3) {
                listOf(TfrShape("polygon", points, floorAlt, ceilAlt, null))
            } else emptyList()
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Polygon parse error: ${e.message}", e)
            emptyList()
        }
    }
}
