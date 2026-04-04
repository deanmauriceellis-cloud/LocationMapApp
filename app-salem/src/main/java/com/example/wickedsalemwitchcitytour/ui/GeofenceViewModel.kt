/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.locationmapapp.data.model.GeofenceAlert
import com.example.locationmapapp.data.model.GeofenceDatabaseInfo
import com.example.locationmapapp.data.model.TfrZone
import com.example.locationmapapp.data.model.ZoneType
import com.example.locationmapapp.data.repository.GeofenceDatabaseRepository
import com.example.locationmapapp.data.repository.GeofenceRepository
import com.example.locationmapapp.data.repository.TfrRepository
import com.example.locationmapapp.util.GeofenceEngine
import com.example.locationmapapp.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module GeofenceViewModel.kt"

@HiltViewModel
class GeofenceViewModel @Inject constructor(
    private val tfrRepository: TfrRepository,
    private val geofenceRepository: GeofenceRepository,
    private val geofenceDatabaseRepository: GeofenceDatabaseRepository
) : ViewModel() {

    private val TAG = "GeofenceVM"

    private val _tfrZones = MutableLiveData<List<TfrZone>>()
    val tfrZones: LiveData<List<TfrZone>> = _tfrZones

    private val _cameraZones = MutableLiveData<List<TfrZone>>()
    val cameraZones: LiveData<List<TfrZone>> = _cameraZones

    private val _schoolZones = MutableLiveData<List<TfrZone>>()
    val schoolZones: LiveData<List<TfrZone>> = _schoolZones

    private val _floodZones = MutableLiveData<List<TfrZone>>()
    val floodZones: LiveData<List<TfrZone>> = _floodZones

    private val _crossingZones = MutableLiveData<List<TfrZone>>()
    val crossingZones: LiveData<List<TfrZone>> = _crossingZones

    private val _databaseZones = MutableLiveData<List<TfrZone>>()
    val databaseZones: LiveData<List<TfrZone>> = _databaseZones

    private val _geofenceCatalog = MutableLiveData<List<GeofenceDatabaseInfo>>()
    val geofenceCatalog: LiveData<List<GeofenceDatabaseInfo>> = _geofenceCatalog

    private val _databaseDownloadProgress = MutableLiveData<Pair<String, Int>?>()
    val databaseDownloadProgress: LiveData<Pair<String, Int>?> = _databaseDownloadProgress

    private val _geofenceAlerts = MutableLiveData<List<GeofenceAlert>>()
    val geofenceAlerts: LiveData<List<GeofenceAlert>> = _geofenceAlerts

    private val _importResult = MutableLiveData<Pair<Boolean, String>?>()
    val importResult: LiveData<Pair<Boolean, String>?> = _importResult

    val geofenceEngine = GeofenceEngine()

    fun loadTfrs(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadTfrs() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { tfrRepository.fetchTfrs(south, west, north, east) }
                .onSuccess {
                    DebugLogger.i(TAG, "TFRs success — ${it.size}")
                    _tfrZones.value = it
                    rebuildGeofenceIndex()
                }
                .onFailure { e ->
                    DebugLogger.e(TAG, "TFRs FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun loadCameras(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadCameras() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchCameras(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Cameras success — ${it.size}"); _cameraZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "Cameras FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun loadSchools(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadSchools() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchSchools(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Schools success — ${it.size}"); _schoolZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "Schools FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun loadFloodZones(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadFloodZones() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchFloodZones(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "FloodZones success — ${it.size}"); _floodZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "FloodZones FAILED: ${e.message}", e as? Exception) }
        }
    }

    fun loadCrossings(south: Double, west: Double, north: Double, east: Double) {
        DebugLogger.i(TAG, "loadCrossings() bbox=$south,$west,$north,$east")
        viewModelScope.launch {
            runCatching { geofenceRepository.fetchCrossings(south, west, north, east) }
                .onSuccess { DebugLogger.i(TAG, "Crossings success — ${it.size}"); _crossingZones.value = it; rebuildGeofenceIndex() }
                .onFailure { e -> DebugLogger.e(TAG, "Crossings FAILED: ${e.message}", e as? Exception) }
        }
    }

    /** Rebuild the spatial index from all zone type lists. */
    private fun rebuildGeofenceIndex() {
        val all = mutableListOf<TfrZone>()
        _tfrZones.value?.let { all.addAll(it) }
        _cameraZones.value?.let { all.addAll(it) }
        _schoolZones.value?.let { all.addAll(it) }
        _floodZones.value?.let { all.addAll(it) }
        _crossingZones.value?.let { all.addAll(it) }
        _databaseZones.value?.let { all.addAll(it) }
        geofenceEngine.loadZones(all)
    }

    /** Clear a specific zone type. */
    fun clearZoneType(zoneType: ZoneType) {
        when (zoneType) {
            ZoneType.TFR -> _tfrZones.value = emptyList()
            ZoneType.SPEED_CAMERA -> _cameraZones.value = emptyList()
            ZoneType.SCHOOL_ZONE -> _schoolZones.value = emptyList()
            ZoneType.FLOOD_ZONE -> _floodZones.value = emptyList()
            ZoneType.RAILROAD_CROSSING -> _crossingZones.value = emptyList()
            ZoneType.MILITARY_BASE, ZoneType.NO_FLY_ZONE, ZoneType.CUSTOM -> { /* database zones not per-type cleared */ }
        }
        rebuildGeofenceIndex()
        DebugLogger.i(TAG, "Cleared zone type $zoneType")
    }

    suspend fun fetchTfrsDirectly(south: Double, west: Double, north: Double, east: Double): List<TfrZone> {
        return try {
            tfrRepository.fetchTfrs(south, west, north, east)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "fetchTfrsDirectly FAILED: ${e.message}", e)
            emptyList()
        }
    }

    fun checkGeofences(lat: Double, lon: Double, altFt: Double?, bearing: Double?) {
        val alerts = geofenceEngine.checkPosition(lat, lon, altFt, bearing)
        if (alerts.isNotEmpty()) {
            DebugLogger.i(TAG, "Geofence alerts: ${alerts.size} — ${alerts.map { "${it.alertType}:${it.zoneName}" }}")
            _geofenceAlerts.value = alerts
        }
    }

    fun clearTfrs() {
        _tfrZones.value = emptyList()
        _geofenceAlerts.value = emptyList()
        geofenceEngine.clear()
        DebugLogger.i(TAG, "TFRs cleared")
    }

    fun clearAllGeofences() {
        _tfrZones.value = emptyList()
        _cameraZones.value = emptyList()
        _schoolZones.value = emptyList()
        _floodZones.value = emptyList()
        _crossingZones.value = emptyList()
        _databaseZones.value = emptyList()
        _geofenceAlerts.value = emptyList()
        geofenceEngine.clear()
        DebugLogger.i(TAG, "All geofences cleared")
    }

    // ── Geofence Databases ───────────────────────────────────────────────────

    fun fetchGeofenceCatalog() {
        viewModelScope.launch {
            runCatching { geofenceDatabaseRepository.fetchCatalog() }
                .onSuccess { catalog ->
                    // Merge local-only databases not in remote catalog
                    val remoteIds = catalog.map { it.id }.toSet()
                    val localOnly = geofenceDatabaseRepository.getLocalOnlyDatabaseInfos()
                        .filter { it.id !in remoteIds }
                    val merged = catalog + localOnly
                    _geofenceCatalog.value = merged
                    DebugLogger.i(TAG, "Catalog: ${catalog.size} remote + ${localOnly.size} local-only = ${merged.size}")
                }
                .onFailure { e ->
                    // Even if remote fails, show local-only databases
                    val localOnly = geofenceDatabaseRepository.getLocalOnlyDatabaseInfos()
                    if (localOnly.isNotEmpty()) {
                        _geofenceCatalog.value = localOnly
                        DebugLogger.i(TAG, "Catalog remote FAILED, showing ${localOnly.size} local-only databases")
                    }
                    DebugLogger.e(TAG, "Catalog FAILED: ${e.message}", e as? Exception)
                }
        }
    }

    fun importGeofenceDatabase(contentResolver: ContentResolver, uri: Uri, overwriteId: String? = null) {
        viewModelScope.launch {
            val result = geofenceDatabaseRepository.importSqliteDatabase(contentResolver, uri, overwriteId)
            _importResult.value = result
            if (result.first) fetchGeofenceCatalog()
        }
    }

    fun importCsvAsGeofenceDatabase(
        contentResolver: ContentResolver, uri: Uri,
        databaseId: String, databaseName: String,
        zoneType: ZoneType, defaultRadius: Double
    ) {
        viewModelScope.launch {
            val result = geofenceDatabaseRepository.importCsvAsDatabase(
                contentResolver, uri, databaseId, databaseName, zoneType, defaultRadius
            )
            _importResult.value = result
            if (result.first) fetchGeofenceCatalog()
        }
    }

    fun clearImportResult() { _importResult.value = null }

    fun getGeofenceDatabaseFile(id: String): java.io.File? =
        geofenceDatabaseRepository.getDatabaseFile(id)

    fun downloadGeofenceDatabase(id: String) {
        viewModelScope.launch {
            _databaseDownloadProgress.value = Pair(id, 0)
            val success = geofenceDatabaseRepository.downloadDatabase(id) { pct ->
                _databaseDownloadProgress.postValue(Pair(id, pct))
            }
            _databaseDownloadProgress.value = null
            if (success) {
                DebugLogger.i(TAG, "Database $id downloaded, refreshing catalog")
                fetchGeofenceCatalog()
            }
        }
    }

    fun deleteGeofenceDatabase(id: String) {
        geofenceDatabaseRepository.deleteDatabase(id)
        _databaseZones.value = _databaseZones.value?.filter {
            val meta = it.metadata
            meta["database_id"] != id
        } ?: emptyList()
        rebuildGeofenceIndex()
        fetchGeofenceCatalog()
        DebugLogger.i(TAG, "Database $id deleted")
    }

    fun loadDatabaseZonesForVisibleArea(s: Double, w: Double, n: Double, e: Double) {
        viewModelScope.launch {
            val installed = geofenceDatabaseRepository.getInstalledDatabases()
            if (installed.isEmpty()) return@launch
            val allZones = mutableListOf<TfrZone>()
            for (dbId in installed) {
                val zones = geofenceDatabaseRepository.loadZonesFromDatabaseInBbox(dbId, s, w, n, e)
                // Tag each zone with its source database
                allZones.addAll(zones.map { zone ->
                    zone.copy(metadata = zone.metadata + ("database_id" to dbId))
                })
            }
            _databaseZones.value = allZones
            rebuildGeofenceIndex()
            DebugLogger.i(TAG, "Database zones: ${allZones.size} from ${installed.size} databases")
        }
    }

    fun hasInstalledDatabases(): Boolean =
        geofenceDatabaseRepository.getInstalledDatabases().isNotEmpty()
}
