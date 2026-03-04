/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.data.model

import org.osmdroid.util.GeoPoint

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module Models.kt"

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
    val urgency: String = "",
    val instruction: String = "",
    val effective: String,
    val expires: String,
    val areaDesc: String
)

// ── Weather Composite Models ────────────────────────────────────────────────

data class WeatherLocation(
    val city: String,
    val state: String,
    val station: String
)

data class CurrentConditions(
    val temperature: Int?,
    val temperatureUnit: String,
    val humidity: Int?,
    val windSpeed: Int?,
    val windDirection: String?,
    val windChill: Int?,
    val heatIndex: Int?,
    val dewpoint: Int?,
    val description: String,
    val iconCode: String,
    val isDaytime: Boolean,
    val visibility: Double?,
    val barometer: Double?
)

data class HourlyForecast(
    val time: String,
    val temperature: Int,
    val windSpeed: String,
    val windDirection: String,
    val precipProbability: Int,
    val shortForecast: String,
    val iconCode: String,
    val isDaytime: Boolean
)

data class DailyForecast(
    val name: String,
    val isDaytime: Boolean,
    val temperature: Int,
    val windSpeed: String,
    val shortForecast: String,
    val detailedForecast: String,
    val iconCode: String,
    val precipProbability: Int
)

data class WeatherData(
    val location: WeatherLocation,
    val current: CurrentConditions?,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
    val alerts: List<WeatherAlert>,
    val fetchedAt: String
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
    val headsign: String?,       // Trip destination e.g. "Harvard" — from included trip object
    val stopId: String?,         // Current/next stop ID
    val stopName: String?,       // Resolved stop name
    val lat: Double,
    val lon: Double,
    val bearing: Int,            // 0-359 degrees
    val speedMps: Double?,       // Speed in m/s, null if not moving/unknown
    val currentStatus: MbtaVehicleStatus,
    val updatedAt: String,
    val routeType: Int,          // 0=Light Rail, 1=Heavy Rail, 2=Commuter Rail, 3=Bus
    val nextStopMinutes: Int? = null  // Minutes until arrival at next stop (from predictions)
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

data class FlightPathPoint(
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Double?,
    val timestamp: Long  // epoch millis
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

// ── MBTA Station / Prediction / Schedule Models ─────────────────────────────

data class MbtaStop(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val routeIds: List<String>
) {
    fun toGeoPoint() = GeoPoint(lat, lon)
}

data class MbtaPrediction(
    val id: String,
    val routeId: String,
    val routeName: String,
    val tripId: String?,
    val headsign: String?,
    val arrivalTime: String?,
    val departureTime: String?,
    val directionId: Int,
    val status: String?,
    val vehicleId: String?
)

data class MbtaTripScheduleEntry(
    val stopId: String,
    val stopName: String,
    val stopSequence: Int,
    val arrivalTime: String?,
    val departureTime: String?,
    val platformCode: String?
)

// ── Populate POIs scanner ────────────────────────────────────────────────────

data class PopulateSearchResult(
    val results: List<PlaceResult>,
    val cacheHit: Boolean,
    val gridKey: String,
    val radiusM: Int = 3000,
    val capped: Boolean = false,
    val poiNew: Int = 0,
    val poiKnown: Int = 0,
    val coverageStatus: String = ""
)

// ── Find Feature Models ─────────────────────────────────────────────────────

data class FindResult(
    val id: Long,
    val type: String,
    val name: String?,
    val lat: Double,
    val lon: Double,
    val category: String,
    val distanceM: Int,
    val tags: Map<String, String>,
    val address: String? = null,
    val cuisine: String? = null,
    val phone: String? = null,
    val openingHours: String? = null
) {
    /** Extracts value after "=" (e.g. "amenity=cafe" → "cafe") */
    val typeValue: String get() = category.substringAfter("=", category)

    /** First non-null detail from cuisine/denomination/sport (semicolons → commas) */
    val detail: String? get() {
        val raw = cuisine
            ?: tags["denomination"]
            ?: tags["sport"]
            ?: tags["brand"]
        return raw?.replace(";", ", ")
    }

    fun toGeoPoint() = GeoPoint(lat, lon)

    fun toPlaceResult() = PlaceResult(
        id = "$type:$id",
        name = name ?: typeValue.replaceFirstChar { it.uppercase() },
        lat = lat,
        lon = lon,
        category = category,
        address = address,
        phone = phone,
        openingHours = openingHours
    )
}

data class FavoriteEntry(
    val osmType: String,
    val osmId: Long,
    val name: String?,
    val lat: Double,
    val lon: Double,
    val category: String,
    val address: String? = null,
    val phone: String? = null,
    val openingHours: String? = null,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toFindResult(fromLat: Double, fromLon: Double): FindResult {
        val R = 6371000.0
        val dLat = Math.toRadians(lat - fromLat)
        val dLon = Math.toRadians(lon - fromLon)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(lat)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val dist = (R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toInt()
        return FindResult(
            id = osmId,
            type = osmType,
            name = name,
            lat = lat,
            lon = lon,
            category = category,
            distanceM = dist,
            tags = emptyMap(),
            address = address,
            phone = phone,
            openingHours = openingHours
        )
    }
}

data class FindCounts(
    val counts: Map<String, Int>,
    val total: Int
)

data class FindResponse(
    val results: List<FindResult>,
    val totalInRange: Int,
    val scopeM: Int
)

data class SearchResponse(
    val results: List<FindResult>,
    val totalCount: Int,
    val scopeM: Int,
    val categoryHint: String?
)

data class PoiWebsite(
    val url: String?,
    val source: String,   // "osm", "wikidata", "search", "cached", "none"
    val phone: String?,
    val hours: String?,
    val address: String?
)

data class GeocodeSuggestion(
    val lat: Double,
    val lon: Double,
    val display_name: String,
    val type: String?,
    val city: String?,
    val state: String?
)

enum class MbtaVehicleStatus(val display: String) {
    INCOMING_AT("Arriving"),
    STOPPED_AT("Stopped at"),
    IN_TRANSIT_TO("En route to"),
    UNKNOWN("—")
}

// ── Geofence / TFR Models ──────────────────────────────────────────────────

enum class ZoneType { TFR, SPEED_CAMERA, SCHOOL_ZONE, FLOOD_ZONE, RAILROAD_CROSSING, MILITARY_BASE, NO_FLY_ZONE, CUSTOM }

data class GeofenceDatabaseInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: Int,
    val zoneType: String,
    val zoneCount: Int,
    val fileSize: Long,
    val updatedAt: String,
    val source: String,
    val license: String,
    val installed: Boolean = false,
    val installedVersion: Int = 0
)

enum class AlertSeverity(val level: Int) {
    INFO(0), WARNING(1), CRITICAL(2), EMERGENCY(3)
}

data class TfrShape(
    val type: String,           // "circle", "polygon", "polyarc"
    val points: List<List<Double>>,  // [[lon, lat], ...] — GeoJSON convention
    val floorAltFt: Int,
    val ceilingAltFt: Int,
    val radiusNm: Double?       // only for circle type
)

data class TfrZone(
    val id: String,
    val notam: String,
    val type: String,
    val description: String,
    val effectiveDate: String,
    val expireDate: String,
    val shapes: List<TfrShape>,
    val facility: String,
    val state: String,
    val zoneType: ZoneType = ZoneType.TFR,
    val metadata: Map<String, String> = emptyMap()
)

data class GeofenceAlert(
    val zoneId: String,
    val zoneName: String,
    val alertType: String,      // "entry", "proximity", "exit"
    val severity: AlertSeverity,
    val distanceNm: Double?,
    val timestamp: Long,
    val description: String,
    val zoneType: ZoneType = ZoneType.TFR
)

// ── Social / Auth Models ────────────────────────────────────────────────────

data class AuthUser(
    val id: String,
    val displayName: String,
    val role: String,
    val createdAt: String? = null
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String
)

data class AuthResponse(
    val user: AuthUser,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String
)

// ── POI Comments Models ─────────────────────────────────────────────────────

data class PoiComment(
    val id: Long,
    val osmType: String,
    val osmId: Long,
    val userId: String,
    val parentId: Long?,
    val content: String,
    val rating: Int?,
    val upvotes: Int,
    val downvotes: Int,
    val isDeleted: Boolean,
    val createdAt: String,
    val authorName: String,
    val viewerVote: Int = 0
)

data class CommentsResponse(
    val comments: List<PoiComment>,
    val total: Int
)

// ── Chat Models ─────────────────────────────────────────────────────────────

data class ChatRoom(
    val id: String,
    val roomType: String,
    val name: String,
    val description: String?,
    val memberCount: Int,
    val lastMessageAt: String?,
    val createdAt: String,
    val isMember: Boolean = false,
    val memberRole: String? = null
)

data class ChatMessage(
    val id: Long,
    val roomId: String,
    val userId: String,
    val authorName: String,
    val content: String,
    val replyToId: Long?,
    val isDeleted: Boolean = false,
    val sentAt: String
)
