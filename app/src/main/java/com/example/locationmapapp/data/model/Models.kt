package com.example.locationmapapp.data.model

import org.osmdroid.util.GeoPoint

data class PlaceResult(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String,
    val address: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val openingHours: String? = null
) {
    fun toGeoPoint() = GeoPoint(lat, lon)
}

data class WeatherAlert(
    val id: String,
    val event: String,
    val headline: String,
    val description: String,
    val severity: String,
    val effective: String,
    val expires: String,
    val areaDesc: String
)

data class MetarStation(
    val stationId: String,
    val lat: Double,
    val lon: Double,
    val rawMetar: String,
    val tempC: Double?,
    val dewpointC: Double?,
    val windDirDeg: Int?,
    val windSpeedKt: Int?,
    val visibilityMiles: Double?,
    val altimeterInHg: Double?,
    val flightCategory: String?,   // VFR / MVFR / IFR / LIFR
    val observationTime: String?
)

data class EarthquakeEvent(
    val id: String,
    val magnitude: Double,
    val place: String,
    val lat: Double,
    val lon: Double,
    val depth: Double,
    val time: Long,
    val url: String
)

// ── MBTA Vehicle Models ───────────────────────────────────────────────────────

data class MbtaVehicle(
    val id: String,
    val label: String,           // Train number e.g. "1712"
    val routeId: String,         // e.g. "CR-Fitchburg"
    val routeName: String,       // e.g. "Fitchburg Line"
    val tripId: String?,
    val stopId: String?,         // Current/next stop ID
    val stopName: String?,       // Resolved stop name
    val lat: Double,
    val lon: Double,
    val bearing: Int,            // 0-359 degrees
    val speedMps: Double?,       // Speed in m/s, null if not moving/unknown
    val currentStatus: MbtaVehicleStatus,
    val updatedAt: String,
    val routeType: Int           // 0=Light Rail, 1=Heavy Rail, 2=Commuter Rail, 3=Bus
) {
    val speedMph: Double? get() = speedMps?.let { it * 2.237 }
    val speedDisplay: String get() = speedMph?.let { "%.0f mph".format(it) } ?: "—"
    fun toGeoPoint() = org.osmdroid.util.GeoPoint(lat, lon)
}

enum class MbtaVehicleStatus(val display: String) {
    INCOMING_AT("Arriving"),
    STOPPED_AT("Stopped at"),
    IN_TRANSIT_TO("En route to"),
    UNKNOWN("—")
}
