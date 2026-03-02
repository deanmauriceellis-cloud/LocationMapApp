package com.example.locationmapapp.util

import com.example.locationmapapp.data.model.AlertSeverity
import com.example.locationmapapp.data.model.GeofenceAlert
import com.example.locationmapapp.data.model.TfrZone
import com.example.locationmapapp.data.model.ZoneType
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.index.strtree.STRtree
import java.util.Calendar

/**
 * GeofenceEngine — spatial geofence checking using JTS R-tree.
 *
 * Designed to accept any zone type (TFRs, speed cameras, school zones, flood zones, railroad crossings).
 * Uses STRtree for fast spatial indexing and JTS Polygon for point-in-polygon tests.
 */
class GeofenceEngine {

    private val TAG = "GeofenceEngine"
    private val factory = GeometryFactory()

    private var tree = STRtree()
    private var treeBuilt = false

    /** Zone ID → JTS Polygon + metadata. */
    private data class IndexedZone(
        val zoneId: String,
        val zoneName: String,
        val description: String,
        val polygon: Polygon,
        val floorAltFt: Int,
        val ceilingAltFt: Int,
        val zoneType: ZoneType = ZoneType.TFR
    )

    private val zones = mutableListOf<IndexedZone>()

    /** Zone IDs the user/aircraft is currently inside. */
    private val activeZones = mutableSetOf<String>()

    /** Cooldown tracking: zone ID → last alert timestamp. */
    private val proximityCooldowns = mutableMapOf<String, Long>()
    private val entryCooldowns = mutableMapOf<String, Long>()

    /** How close (in NM) before a proximity warning fires. */
    var proximityThresholdNm: Double = 5.0

    private companion object {
        const val PROXIMITY_COOLDOWN_MS = 5 * 60 * 1000L   // 5 min
        const val ENTRY_COOLDOWN_MS = 10 * 60 * 1000L      // 10 min
        const val BEARING_WINDOW_DEG = 60.0                 // ±60° toward zone
    }

    /**
     * Load all zone types into the spatial index. Clears any previous data.
     */
    fun loadZones(allZones: List<TfrZone>) {
        clear()
        tree = STRtree()
        treeBuilt = false

        for (tfr in allZones) {
            for (shape in tfr.shapes) {
                if (shape.points.size < 3) continue
                try {
                    val coords = shape.points.map { Coordinate(it[0], it[1]) }.toMutableList()
                    // Ensure ring is closed
                    if (coords.first().x != coords.last().x || coords.first().y != coords.last().y) {
                        coords.add(Coordinate(coords.first().x, coords.first().y))
                    }
                    if (coords.size < 4) continue  // JTS needs at least 4 coords (3 + close)
                    val ring = factory.createLinearRing(coords.toTypedArray())
                    val polygon = factory.createPolygon(ring)
                    if (!polygon.isValid) continue

                    val indexed = IndexedZone(
                        zoneId = tfr.id,
                        zoneName = tfr.notam,
                        description = tfr.description,
                        polygon = polygon,
                        floorAltFt = shape.floorAltFt,
                        ceilingAltFt = shape.ceilingAltFt,
                        zoneType = tfr.zoneType
                    )
                    zones.add(indexed)
                    tree.insert(polygon.envelopeInternal, indexed)
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Skipping invalid zone shape in ${tfr.id}: ${e.message}")
                }
            }
        }
        tree.build()
        treeBuilt = true
        DebugLogger.i(TAG, "Loaded ${zones.size} zone shapes from ${allZones.size} zones (${getZoneCountByType()})")
    }

    /** Alias — backward compatibility. */
    fun loadTfrs(tfrs: List<TfrZone>) = loadZones(tfrs)

    /**
     * Check a GPS position against all loaded zones.
     *
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @param altitudeFt Altitude in feet (null = skip altitude check)
     * @param bearing Current bearing in degrees (null = skip bearing-toward check)
     * @return List of alerts (entry, proximity, exit)
     */
    fun checkPosition(lat: Double, lon: Double, altitudeFt: Double?, bearing: Double?): List<GeofenceAlert> {
        if (!treeBuilt || zones.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val alerts = mutableListOf<GeofenceAlert>()
        val point = factory.createPoint(Coordinate(lon, lat))

        // Expand search envelope by proximity threshold (degrees)
        val proximityDeg = nmToDeg(proximityThresholdNm)
        val searchEnvelope = Envelope(
            lon - proximityDeg, lon + proximityDeg,
            lat - proximityDeg, lat + proximityDeg
        )

        @Suppress("UNCHECKED_CAST")
        val candidates = tree.query(searchEnvelope) as List<IndexedZone>
        val currentlyInside = mutableSetOf<String>()

        for (zone in candidates) {
            // School zone time filter — only alert during weekday school hours
            if (zone.zoneType == ZoneType.SCHOOL_ZONE && !isSchoolHours()) continue

            // Altitude check — skip if we know altitude and it's outside the zone's vertical range
            if (altitudeFt != null && (altitudeFt < zone.floorAltFt || altitudeFt > zone.ceilingAltFt)) {
                continue
            }

            val inside = zone.polygon.contains(point)
            val severity = severityForZoneType(zone.zoneType, if (inside) "entry" else "proximity")

            if (inside) {
                currentlyInside.add(zone.zoneId)

                // Entry detection — wasn't in activeZones before
                if (!activeZones.contains(zone.zoneId)) {
                    if (!isOnCooldown(entryCooldowns, zone.zoneId, now, ENTRY_COOLDOWN_MS)) {
                        alerts.add(GeofenceAlert(
                            zoneId = zone.zoneId,
                            zoneName = zone.zoneName,
                            alertType = "entry",
                            severity = severity,
                            distanceNm = 0.0,
                            timestamp = now,
                            description = zone.description,
                            zoneType = zone.zoneType
                        ))
                        entryCooldowns[zone.zoneId] = now
                    }
                }
            } else {
                // Proximity detection
                val distanceDeg = zone.polygon.distance(point)
                val distanceNm = degToNm(distanceDeg)

                if (distanceNm <= proximityThresholdNm) {
                    // Check if we're heading toward the zone
                    val headingToward = if (bearing != null) {
                        val zoneCentroid = zone.polygon.centroid
                        val bearingToZone = bearingTo(lat, lon, zoneCentroid.y, zoneCentroid.x)
                        isWithinBearingWindow(bearing, bearingToZone, BEARING_WINDOW_DEG)
                    } else {
                        true // No bearing info → always warn
                    }

                    if (headingToward && !isOnCooldown(proximityCooldowns, zone.zoneId, now, PROXIMITY_COOLDOWN_MS)) {
                        alerts.add(GeofenceAlert(
                            zoneId = zone.zoneId,
                            zoneName = zone.zoneName,
                            alertType = "proximity",
                            severity = severity,
                            distanceNm = distanceNm,
                            timestamp = now,
                            description = zone.description,
                            zoneType = zone.zoneType
                        ))
                        proximityCooldowns[zone.zoneId] = now
                    }
                }
            }
        }

        // Exit detection — was in activeZones, now outside
        val exited = activeZones - currentlyInside
        for (zoneId in exited) {
            val zone = zones.find { it.zoneId == zoneId }
            if (zone != null) {
                alerts.add(GeofenceAlert(
                    zoneId = zone.zoneId,
                    zoneName = zone.zoneName,
                    alertType = "exit",
                    severity = AlertSeverity.INFO,
                    distanceNm = null,
                    timestamp = now,
                    description = "Exited ${zone.zoneName}",
                    zoneType = zone.zoneType
                ))
            }
        }

        activeZones.clear()
        activeZones.addAll(currentlyInside)

        return alerts
    }

    fun clear() {
        zones.clear()
        activeZones.clear()
        proximityCooldowns.clear()
        entryCooldowns.clear()
        tree = STRtree()
        treeBuilt = false
    }

    fun getLoadedZoneCount(): Int = zones.size

    fun getActiveZones(): Set<String> = activeZones.toSet()

    /** Returns count of loaded zone shapes grouped by ZoneType. */
    fun getZoneCountByType(): Map<ZoneType, Int> =
        zones.groupBy { it.zoneType }.mapValues { it.value.size }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Map zone type + alert type → severity level. */
    private fun severityForZoneType(zoneType: ZoneType, alertType: String): AlertSeverity {
        if (alertType == "exit") return AlertSeverity.INFO
        return when (zoneType) {
            ZoneType.TFR -> if (alertType == "entry") AlertSeverity.CRITICAL else AlertSeverity.WARNING
            ZoneType.SPEED_CAMERA -> AlertSeverity.WARNING
            ZoneType.SCHOOL_ZONE -> AlertSeverity.WARNING
            ZoneType.FLOOD_ZONE -> if (alertType == "entry") AlertSeverity.WARNING else AlertSeverity.INFO
            ZoneType.RAILROAD_CROSSING -> AlertSeverity.WARNING
        }
    }

    /** Returns true during weekday school hours (Mon–Fri, 7–9 AM or 2–4 PM). */
    private fun isSchoolHours(): Boolean {
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return (hour in 7..8) || (hour in 14..15)
    }

    private fun nmToDeg(nm: Double): Double = nm / 60.0

    private fun degToNm(deg: Double): Double = deg * 60.0

    private fun bearingTo(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = Math.sin(dLon) * Math.cos(lat2R)
        val x = Math.cos(lat1R) * Math.sin(lat2R) - Math.sin(lat1R) * Math.cos(lat2R) * Math.cos(dLon)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    private fun isWithinBearingWindow(currentBearing: Double, targetBearing: Double, windowDeg: Double): Boolean {
        val diff = Math.abs(((currentBearing - targetBearing) + 540) % 360 - 180)
        return diff <= windowDeg
    }

    private fun isOnCooldown(map: Map<String, Long>, key: String, now: Long, cooldownMs: Long): Boolean {
        val last = map[key] ?: return false
        return (now - last) < cooldownMs
    }
}
