package com.example.locationmapapp.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.locationmapapp.data.model.GeofenceDatabaseInfo
import com.example.locationmapapp.data.model.TfrShape
import com.example.locationmapapp.data.model.TfrZone
import com.example.locationmapapp.data.model.ZoneType
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofenceDatabaseRepository @Inject constructor(
    private val context: Context
) {
    private val TAG = "GeofenceDbRepo"
    private val BASE = "http://10.0.0.4:3000"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
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
