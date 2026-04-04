/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.tour

import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.content.model.TourStop
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.osmdroid.util.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TourGeofenceManager.kt"

/**
 * Lightweight geofence manager for tour stops.
 *
 * Uses simple haversine distance checks against each stop's geofence radius.
 * Emits [TourGeofenceEvent] when the user approaches, arrives at, or leaves a stop.
 * Designed for walking-pace GPS updates (~10-60s intervals).
 */
@Singleton
class TourGeofenceManager @Inject constructor() {

    private val TAG = "TourGeofence"

    /** Multiplier for approach detection (e.g., 2.0 × geofenceRadiusM). */
    private val APPROACH_MULTIPLIER = 2.0

    /** Minimum time between repeated events for the same stop (ms). */
    private val COOLDOWN_MS = 60_000L

    private var stops: List<TourStop> = emptyList()
    private var pois: Map<String, TourPoi> = emptyMap()
    private var enabled = false

    /** Stop IDs currently in APPROACH zone. */
    private val approachedStops = mutableSetOf<String>()

    /** Stop IDs currently in ENTRY zone (inside geofence radius). */
    private val enteredStops = mutableSetOf<String>()

    /** Cooldown tracking: poiId → last event timestamp. */
    private val approachCooldowns = mutableMapOf<String, Long>()
    private val entryCooldowns = mutableMapOf<String, Long>()

    private val _events = MutableSharedFlow<TourGeofenceEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<TourGeofenceEvent> = _events.asSharedFlow()

    /**
     * Load tour stops for geofence monitoring.
     * Call when a tour starts or stops change.
     */
    fun loadStops(tourStops: List<TourStop>, tourPois: List<TourPoi>) {
        stops = tourStops
        pois = tourPois.associateBy { it.id }
        approachedStops.clear()
        enteredStops.clear()
        approachCooldowns.clear()
        entryCooldowns.clear()
        enabled = true
        DebugLogger.i(TAG, "Loaded ${tourStops.size} geofence zones")
    }

    /** Clear all zones and stop monitoring. */
    fun clear() {
        stops = emptyList()
        pois = emptyMap()
        approachedStops.clear()
        enteredStops.clear()
        enabled = false
        DebugLogger.i(TAG, "Cleared geofence zones")
    }

    /**
     * Check user position against all tour stop geofences.
     * Call on each GPS update.
     *
     * @param lat user latitude
     * @param lng user longitude
     * @return list of new geofence events (may be empty)
     */
    fun checkPosition(lat: Double, lng: Double): List<TourGeofenceEvent> {
        if (!enabled || stops.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val newEvents = mutableListOf<TourGeofenceEvent>()

        for (stop in stops) {
            val poi = pois[stop.poiId] ?: continue
            val distance = TourEngine.haversineM(lat, lng, poi.lat, poi.lng)
            val radiusM = poi.geofenceRadiusM.toDouble()
            val approachRadius = radiusM * APPROACH_MULTIPLIER

            when {
                // ENTRY — inside geofence radius
                distance <= radiusM -> {
                    if (stop.poiId !in enteredStops) {
                        if (!isOnCooldown(entryCooldowns, stop.poiId, now)) {
                            val event = TourGeofenceEvent(
                                type = GeofenceEventType.ENTRY,
                                poiId = stop.poiId,
                                poiName = poi.name,
                                distanceM = distance,
                                stopIndex = stops.indexOf(stop),
                                poi = poi,
                                stop = stop
                            )
                            newEvents.add(event)
                            _events.tryEmit(event)
                            entryCooldowns[stop.poiId] = now
                            DebugLogger.i(TAG, "ENTRY: ${poi.name} (${distance.toInt()}m)")
                        }
                        enteredStops.add(stop.poiId)
                        approachedStops.add(stop.poiId) // also mark approached
                    }
                }

                // APPROACH — between geofence radius and approach radius
                distance <= approachRadius -> {
                    // Check for EXIT first (was inside, now only approaching)
                    if (stop.poiId in enteredStops) {
                        enteredStops.remove(stop.poiId)
                        val event = TourGeofenceEvent(
                            type = GeofenceEventType.EXIT,
                            poiId = stop.poiId,
                            poiName = poi.name,
                            distanceM = distance,
                            stopIndex = stops.indexOf(stop),
                            poi = poi,
                            stop = stop
                        )
                        newEvents.add(event)
                        _events.tryEmit(event)
                        DebugLogger.i(TAG, "EXIT: ${poi.name} (${distance.toInt()}m)")
                    }

                    // APPROACH event
                    if (stop.poiId !in approachedStops) {
                        if (!isOnCooldown(approachCooldowns, stop.poiId, now)) {
                            val event = TourGeofenceEvent(
                                type = GeofenceEventType.APPROACH,
                                poiId = stop.poiId,
                                poiName = poi.name,
                                distanceM = distance,
                                stopIndex = stops.indexOf(stop),
                                poi = poi,
                                stop = stop
                            )
                            newEvents.add(event)
                            _events.tryEmit(event)
                            approachCooldowns[stop.poiId] = now
                            DebugLogger.i(TAG, "APPROACH: ${poi.name} (${distance.toInt()}m)")
                        }
                        approachedStops.add(stop.poiId)
                    }
                }

                // OUT OF RANGE — clear approach/entry state
                else -> {
                    if (stop.poiId in enteredStops) {
                        enteredStops.remove(stop.poiId)
                        val event = TourGeofenceEvent(
                            type = GeofenceEventType.EXIT,
                            poiId = stop.poiId,
                            poiName = poi.name,
                            distanceM = distance,
                            stopIndex = stops.indexOf(stop),
                            poi = poi,
                            stop = stop
                        )
                        newEvents.add(event)
                        _events.tryEmit(event)
                        DebugLogger.i(TAG, "EXIT: ${poi.name} (${distance.toInt()}m)")
                    }
                    approachedStops.remove(stop.poiId)
                }
            }
        }

        return newEvents
    }

    private fun isOnCooldown(cooldowns: Map<String, Long>, poiId: String, now: Long): Boolean {
        val lastTime = cooldowns[poiId] ?: return false
        return (now - lastTime) < COOLDOWN_MS
    }
}

/** Types of geofence events for tour stops. */
enum class GeofenceEventType {
    /** User is approaching — within 2× geofence radius. */
    APPROACH,
    /** User has arrived — within geofence radius. */
    ENTRY,
    /** User has left — moved outside geofence radius. */
    EXIT
}

/** A geofence event for a tour stop. */
data class TourGeofenceEvent(
    val type: GeofenceEventType,
    val poiId: String,
    val poiName: String,
    val distanceM: Double,
    val stopIndex: Int,
    val poi: TourPoi,
    val stop: TourStop
)
