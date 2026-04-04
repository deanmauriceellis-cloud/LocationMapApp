/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.locationmapapp.data.model.AircraftState
import com.example.locationmapapp.data.model.MbtaStop
import com.example.locationmapapp.data.model.MbtaVehicle
import com.example.locationmapapp.data.model.Webcam
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.util.DebugLogger
import android.content.Context
import org.osmdroid.views.overlay.Marker

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MainActivityDebug.kt"

// =========================================================================
// DEBUG HTTP SERVER — accessor methods
// =========================================================================

/** Returns the map view for direct control from DebugEndpoints. */
internal fun SalemMainActivity.debugMapView() = binding.mapView

/** Returns a snapshot of current app state for /state and /map endpoints. */
internal fun SalemMainActivity.debugState(): Map<String, Any?> {
    val map = binding.mapView
    val bb = map.boundingBox
    return mapOf(
        "center" to mapOf("lat" to map.mapCenter.latitude, "lon" to map.mapCenter.longitude),
        "zoom" to map.zoomLevelDouble,
        "bounds" to mapOf(
            "north" to bb.latNorth, "south" to bb.latSouth,
            "east" to bb.lonEast, "west" to bb.lonWest
        ),
        "markers" to mapOf(
            "poi" to poiMarkers.values.sumOf { it.size },
            "stations" to stationMarkers.size,
            "busStops" to busStopMarkers.size,
            "busStopsTotal" to allBusStops.size,
            "trains" to trainMarkers.size,
            "subway" to subwayMarkers.size,
            "buses" to busMarkers.size,
            "aircraft" to aircraftMarkers.size,
            "webcams" to webcamMarkers.size,
            "metar" to metarMarkers.size,
            "gps" to if (gpsMarker != null) 1 else 0,
            "flightTrailPoints" to flightTrailPoints.size,
            "flightTrailSegments" to flightTrailOverlays.size
        ),
        "followedVehicle" to followedVehicleId,
        "followedAircraft" to followedAircraftIcao,
        "findFilter" to mapOf(
            "active" to findFilterActive,
            "label" to findFilterLabel,
            "tags" to findFilterTags
        ),
        "filterAndMap" to mapOf(
            "active" to filterAndMapActive,
            "label" to filterAndMapLabel,
            "resultCount" to filterAndMapResults.size
        ),
        "silentFill" to (silentFillJob?.isActive == true),
        "idlePopulate" to (idlePopulateJob?.isActive == true),
        "idleTimeSec" to ((System.currentTimeMillis() - lastSignificantMoveTime) / 1000),
        "populate" to (populateJob?.isActive == true),
        "statusLine" to mapOf(
            "text" to statusLineManager.currentText(),
            "priority" to statusLineManager.currentPriority()?.name
        ),
        "gpsSpeedMph" to lastGpsSpeedMph,
        "gpsIntervalMs" to currentGpsIntervalMs,
        "lastPoiFetchDistanceM" to lastPoiFetchPoint?.let { from ->
            lastGpsPoint?.let { to -> distanceBetween(from, to).toInt() }
        },
        "overlays" to map.overlays.size,
        "weather" to weatherViewModel.weatherData.value?.let { w ->
            mapOf(
                "location" to "${w.location.city}, ${w.location.state}",
                "station" to w.location.station,
                "temperature" to w.current?.temperature,
                "description" to (w.current?.description ?: ""),
                "iconCode" to (w.current?.iconCode ?: ""),
                "hourlyCount" to w.hourly.size,
                "dailyCount" to w.daily.size,
                "alertCount" to w.alerts.size,
                "alerts" to w.alerts.map { it.event },
                "fetchedAt" to w.fetchedAt
            )
        },
        "geofences" to mapOf(
            "tfrCount" to (geofenceViewModel.tfrZones.value?.size ?: 0),
            "cameraCount" to (geofenceViewModel.cameraZones.value?.size ?: 0),
            "schoolCount" to (geofenceViewModel.schoolZones.value?.size ?: 0),
            "floodCount" to (geofenceViewModel.floodZones.value?.size ?: 0),
            "crossingCount" to (geofenceViewModel.crossingZones.value?.size ?: 0),
            "databaseCount" to (geofenceViewModel.databaseZones.value?.size ?: 0),
            "overlays" to mapOf(
                "tfr" to tfrOverlays.size,
                "camera" to cameraOverlays.size,
                "school" to schoolOverlays.size,
                "flood" to floodOverlays.size,
                "crossing" to crossingOverlays.size,
                "database" to databaseOverlays.size
            ),
            "loadedZoneShapes" to geofenceViewModel.geofenceEngine.getLoadedZoneCount(),
            "zoneCountByType" to geofenceViewModel.geofenceEngine.getZoneCountByType().map { (k, v) -> k.name to v }.toMap(),
            "activeAlerts" to (geofenceViewModel.geofenceAlerts.value?.size ?: 0),
            "alertSeverity" to (geofenceViewModel.geofenceAlerts.value
                ?.maxByOrNull { it.severity.level }?.severity?.name ?: "NONE"),
            "activeZones" to geofenceViewModel.geofenceEngine.getActiveZones()
        )
    )
}

/** Returns serializable marker info for /markers endpoint. */
internal fun SalemMainActivity.debugMarkers(type: String?): List<Map<String, Any?>> {
    val result = mutableListOf<Map<String, Any?>>()
    fun add(markers: List<Marker>, markerType: String) {
        markers.forEachIndexed { i, m ->
            val info = mutableMapOf<String, Any?>(
                "type" to markerType,
                "index" to i,
                "lat" to m.position.latitude,
                "lon" to m.position.longitude,
                "title" to (m.title ?: ""),
                "snippet" to (m.snippet ?: "")
            )
            // Include key fields from relatedObject when present
            when (val obj = m.relatedObject) {
                is com.example.locationmapapp.data.model.MbtaVehicle -> {
                    info["vehicleId"] = obj.id
                    info["label"] = obj.label
                    info["routeId"] = obj.routeId
                    info["routeName"] = obj.routeName
                    info["tripId"] = obj.tripId
                    info["headsign"] = obj.headsign
                    info["stopId"] = obj.stopId
                    info["stopName"] = obj.stopName
                    info["bearing"] = obj.bearing
                    info["speedMph"] = obj.speedMph
                    info["currentStatus"] = obj.currentStatus.display
                    info["updatedAt"] = obj.updatedAt
                    info["routeType"] = obj.routeType
                }
                is com.example.locationmapapp.data.model.MbtaStop -> {
                    info["stopId"] = obj.id
                    info["stopName"] = obj.name
                    info["routeIds"] = obj.routeIds
                }
                is com.example.locationmapapp.data.model.AircraftState -> {
                    info["icao24"] = obj.icao24
                    info["callsign"] = obj.callsign
                    info["altitude_ft"] = obj.baroAltitude?.let { it * 3.28084 }
                    info["velocity_kt"] = obj.velocity?.let { it * 1.94384 }
                    info["track"] = obj.track
                    info["onGround"] = obj.onGround
                }
                is com.example.locationmapapp.data.model.Webcam -> {
                    info["webcamId"] = obj.id
                    info["categories"] = obj.categories
                    info["status"] = obj.status
                }
            }
            result.add(info)
        }
    }
    when (type) {
        "poi"       -> poiMarkers.values.flatten().let { add(it, "poi") }
        "stations"  -> add(stationMarkers, "stations")
        "bus_stops" -> add(busStopMarkers, "bus_stops")
        "trains"    -> add(trainMarkers, "trains")
        "subway"    -> add(subwayMarkers, "subway")
        "buses"     -> add(busMarkers, "buses")
        "aircraft"  -> add(aircraftMarkers, "aircraft")
        "webcams"   -> add(webcamMarkers, "webcams")
        "metar"     -> add(metarMarkers, "metar")
        "gps"       -> gpsMarker?.let { add(listOf(it), "gps") }
        null -> {
            poiMarkers.values.flatten().let { add(it, "poi") }
            add(stationMarkers, "stations")
            add(busStopMarkers, "bus_stops")
            add(trainMarkers, "trains")
            add(subwayMarkers, "subway")
            add(busMarkers, "buses")
            add(aircraftMarkers, "aircraft")
            add(webcamMarkers, "webcams")
            add(metarMarkers, "metar")
            gpsMarker?.let { add(listOf(it), "gps") }
        }
        else -> {} // unknown type — empty list
    }
    return result
}

/** Triggers the click handler on a marker, as if the user tapped it. */
internal fun SalemMainActivity.debugTapMarker(marker: Marker) {
    // Invoke the marker's custom OnMarkerClickListener if set, otherwise default
    val listener = try {
        val field = Marker::class.java.getDeclaredField("mOnMarkerClickListener")
        field.isAccessible = true
        field.get(marker) as? Marker.OnMarkerClickListener
    } catch (_: Exception) { null }
    if (listener != null) {
        listener.onMarkerClick(marker, binding.mapView)
    } else {
        // No custom listener — show info window (default behavior)
        marker.showInfoWindow()
    }
}

/** Returns raw Marker objects for /markers/tap endpoint. */
internal fun SalemMainActivity.debugRawMarkers(type: String): List<Marker> {
    return when (type) {
        "poi"       -> poiMarkers.values.flatten()
        "stations"  -> stationMarkers
        "bus_stops" -> busStopMarkers
        "trains"    -> trainMarkers
        "subway"    -> subwayMarkers
        "buses"     -> busMarkers
        "aircraft"  -> aircraftMarkers
        "webcams"   -> webcamMarkers
        "metar"     -> metarMarkers
        "gps"       -> listOfNotNull(gpsMarker)
        else        -> emptyList()
    }
}

/** Toggle a preference and fire the corresponding layer handler. */
internal fun SalemMainActivity.debugTogglePref(pref: String, value: Boolean) {
    val prefs = getSharedPreferences("app_bar_menu_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean(pref, value).apply()
    val toggleMap = mapOf<String, (Boolean) -> Unit>(
        MenuPrefs.PREF_MBTA_STATIONS   to { menuEventListenerImpl.onMbtaStationsToggled(it) },
        MenuPrefs.PREF_MBTA_BUS_STOPS  to { menuEventListenerImpl.onMbtaBusStopsToggled(it) },
        MenuPrefs.PREF_MBTA_TRAINS     to { menuEventListenerImpl.onMbtaTrainsToggled(it) },
        MenuPrefs.PREF_MBTA_SUBWAY     to { menuEventListenerImpl.onMbtaSubwayToggled(it) },
        MenuPrefs.PREF_MBTA_BUSES      to { menuEventListenerImpl.onMbtaBusesToggled(it) },
        MenuPrefs.PREF_RADAR_ON         to { menuEventListenerImpl.onRadarToggled(it) },
        MenuPrefs.PREF_METAR_DISPLAY    to { menuEventListenerImpl.onMetarDisplayToggled(it) },
        MenuPrefs.PREF_AIRCRAFT_DISPLAY to { menuEventListenerImpl.onAircraftDisplayToggled(it) },
        MenuPrefs.PREF_WEBCAMS_ON       to { menuEventListenerImpl.onWebcamToggled(it) },
    )
    toggleMap[pref]?.invoke(value)
        ?: DebugLogger.w("DebugHttp", "debugTogglePref: unknown pref '$pref'")
}

/** Force refresh a specific data layer. */
internal fun SalemMainActivity.debugRefreshLayer(layer: String) {
    when (layer) {
        "trains"   -> transitViewModel.fetchMbtaTrains()
        "subway"   -> transitViewModel.fetchMbtaSubway()
        "buses"    -> transitViewModel.fetchMbtaBuses()
        "stations"  -> transitViewModel.fetchMbtaStations()
        "bus_stops" -> transitViewModel.fetchMbtaBusStops()
        "aircraft"  -> loadAircraftForVisibleArea()
        "metar"    -> loadMetarsForVisibleArea()
        "webcams"  -> loadWebcamsForVisibleArea()
        "pois"     -> loadCachedPoisForVisibleArea()
        "radar"    -> weatherViewModel.refreshRadar()
        else -> DebugLogger.w("DebugHttp", "debugRefreshLayer: unknown layer '$layer'")
    }
}

/** Follow an aircraft by icao24. */
internal fun SalemMainActivity.debugFollowAircraft(icao24: String) {
    // Start following directly — no dialog
    followedVehicleId = null
    followedAircraftIcao = icao24
    followedAircraftFailCount = 0
    clearFlightTrail()
    loadFlightTrailHistory(icao24)
    aircraftViewModel.loadFollowedAircraft(icao24)
    startFollowedAircraftRefresh(icao24)
}

/** Follow a vehicle by type and marker index — starts follow directly (bypasses detail dialog). */
internal fun SalemMainActivity.debugFollowVehicleByIndex(type: String, index: Int): Map<String, Any?> {
    val markers = debugRawMarkers(type)
    if (index < 0 || index >= markers.size) {
        return mapOf("error" to "Index $index out of range (0..${markers.size - 1})")
    }
    val marker = markers[index]
    // Get the vehicle from relatedObject and start following directly
    val vehicle = marker.relatedObject as? com.example.locationmapapp.data.model.MbtaVehicle
    if (vehicle != null) {
        startFollowing(vehicle)
    } else {
        // Fallback: tap the marker (opens detail dialog)
        debugTapMarker(marker)
    }
    return mapOf(
        "status" to "ok",
        "following" to type,
        "index" to index,
        "title" to (marker.title ?: ""),
        "vehicleId" to (vehicle?.id ?: ""),
        "position" to mapOf("lat" to marker.position.latitude, "lon" to marker.position.longitude)
    )
}

/** Stop following any vehicle or aircraft. */
internal fun SalemMainActivity.debugStopFollow() {
    stopFollowing()
}

/** Returns raw MbtaVehicle list from LiveData for /vehicles endpoint. */
internal fun SalemMainActivity.debugVehicles(type: String): List<com.example.locationmapapp.data.model.MbtaVehicle> {
    return when (type) {
        "trains" -> transitViewModel.mbtaTrains.value ?: emptyList()
        "subway" -> transitViewModel.mbtaSubway.value ?: emptyList()
        "buses"  -> transitViewModel.mbtaBuses.value ?: emptyList()
        else     -> emptyList()
    }
}

/** Returns raw MbtaStop list from LiveData for /stations endpoint. */
internal fun SalemMainActivity.debugStations(): List<com.example.locationmapapp.data.model.MbtaStop> {
    return transitViewModel.mbtaStations.value ?: emptyList()
}

/** Returns all cached bus stops for /bus-stops endpoint. */
internal fun SalemMainActivity.debugBusStops(): List<com.example.locationmapapp.data.model.MbtaStop> {
    return allBusStops
}

