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
    val name: String?,
    val lat: Double,
    val lon: Double,
    val rawMetar: String,
    val tempC: Double?,
    val dewpointC: Double?,
    val windDirDeg: Int?,
    val windSpeedKt: Int?,
    val windGustKt: Int?,
    val visibilityMiles: Double?,
    val altimeterInHg: Double?,
    val slpMb: Double?,
    val flightCategory: String?,   // VFR / MVFR / IFR / LIFR
    val skyCover: String?,         // CLR / FEW / SCT / BKN / OVC
    val wxString: String?,         // weather phenomena: RA, SN, FG, etc.
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

// ── Aircraft (OpenSky Network) ───────────────────────────────────────────────

data class AircraftState(
    val icao24: String,
    val callsign: String?,
    val originCountry: String,
    val timePosition: Long?,     // unix epoch of last position update
    val lastContact: Long?,      // unix epoch of last message received
    val lat: Double,
    val lon: Double,
    val baroAltitude: Double?,   // meters
    val onGround: Boolean,
    val velocity: Double?,       // m/s
    val track: Double?,          // true track in degrees (0=north)
    val verticalRate: Double?,   // m/s
    val geoAltitude: Double?,    // meters
    val squawk: String?,
    val spi: Boolean,            // special purpose indicator (emergency/ident)
    val positionSource: Int,     // 0=ADS-B, 1=ASTERIX, 2=MLAT, 3=FLARM
    val category: Int            // aircraft category code
) {
    fun toGeoPoint() = GeoPoint(lat, lon)
}

// ── Webcam (Windy Webcams API) ──────────────────────────────────────────────

data class Webcam(
    val id: Long,
    val title: String,
    val lat: Double,
    val lon: Double,
    val categories: List<String>,
    val previewUrl: String,
    val thumbnailUrl: String,
    val playerUrl: String,
    val detailUrl: String,
    val status: String,
    val lastUpdated: String?
) {
    fun toGeoPoint() = GeoPoint(lat, lon)
}

// ── Populate POIs scanner ────────────────────────────────────────────────────

data class PopulateSearchResult(
    val results: List<PlaceResult>,
    val cacheHit: Boolean,
    val gridKey: String,
    val radiusM: Int = 3000,
    val capped: Boolean = false   // true when Overpass returned exactly the limit (200)
)

enum class MbtaVehicleStatus(val display: String) {
    INCOMING_AT("Arriving"),
    STOPPED_AT("Stopped at"),
    IN_TRANSIT_TO("En route to"),
    UNKNOWN("—")
}
