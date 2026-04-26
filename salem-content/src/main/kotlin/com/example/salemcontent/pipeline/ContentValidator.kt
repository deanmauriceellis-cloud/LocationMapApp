/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.pipeline

/**
 * Validates pipeline output for data integrity.
 */
object ContentValidator {

    data class ValidationResult(
        val errors: List<String>,
        val warnings: List<String>
    ) {
        val isValid get() = errors.isEmpty()
    }

    fun validate(output: PipelineOutput): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Tour POIs must have valid GPS
        output.tourPois.forEach { poi ->
            if (poi.lat == 0.0 && poi.lng == 0.0) {
                errors.add("TourPoi '${poi.id}' has zero coordinates")
            }
            if (poi.lat < 42.0 || poi.lat > 43.0 || poi.lng < -71.5 || poi.lng > -70.0) {
                warnings.add("TourPoi '${poi.id}' coordinates outside Salem area: ${poi.lat}, ${poi.lng}")
            }
        }

        // Narration length checks
        output.figures.forEach { fig ->
            fig.narrationScript?.let { script ->
                val words = script.split(Regex("\\s+")).size
                if (words > 500) warnings.add("Figure '${fig.id}' narration too long: $words words")
            }
        }

        // Figure-POI link checks
        val poiIds = output.tourPois.map { it.id }.toSet()
        output.figures.forEach { fig ->
            fig.primaryPoiId?.let { poiId ->
                if (poiId !in poiIds) {
                    warnings.add("Figure '${fig.id}' references non-existent POI: $poiId")
                }
            }
        }

        // Tour stop references
        val tourIds = output.tours.map { it.id }.toSet()
        output.tourStops.forEach { stop ->
            if (stop.tourId !in tourIds) {
                errors.add("TourStop references non-existent tour: ${stop.tourId}")
            }
            if (stop.poiId !in poiIds) {
                errors.add("TourStop references non-existent POI: ${stop.poiId}")
            }
        }

        // Tour-specific validation
        output.tours.forEach { tour ->
            val stops = output.tourStops.filter { it.tourId == tour.id }
            if (stops.isEmpty()) {
                errors.add("Tour '${tour.id}' has no stops")
            } else {
                // Check stop_order is sequential starting from 1
                val orders = stops.map { it.stopOrder }.sorted()
                val expected = (1..stops.size).toList()
                if (orders != expected) {
                    errors.add("Tour '${tour.id}' has non-sequential stop_order: $orders (expected $expected)")
                }
                // Check stop count matches declared count
                if (stops.size != tour.stopCount) {
                    warnings.add("Tour '${tour.id}' declares ${tour.stopCount} stops but has ${stops.size}")
                }
                // Check distances are non-negative
                stops.forEach { stop ->
                    stop.distanceMFromPrev?.let { d ->
                        if (d < 0) errors.add("Tour '${tour.id}' stop ${stop.stopOrder}: negative distance $d")
                    }
                    stop.walkingMinutesFromPrev?.let { m ->
                        if (m < 0) errors.add("Tour '${tour.id}' stop ${stop.stopOrder}: negative walking time $m")
                    }
                }
                // Check first stop has 0 distance (starting point)
                val firstStop = stops.minByOrNull { it.stopOrder }
                if (firstStop != null && (firstStop.distanceMFromPrev ?: 0) != 0) {
                    warnings.add("Tour '${tour.id}' first stop has non-zero distance from prev: ${firstStop.distanceMFromPrev}")
                }
            }
        }

        // Tour leg validation: each leg must reference a real tour and the
        // (from, to) stop_orders must both exist for that tour and be
        // consecutive.
        val stopsByTour = output.tourStops.groupBy { it.tourId }
            .mapValues { (_, ss) -> ss.map { it.stopOrder }.toSet() }
        output.tourLegs.forEach { leg ->
            if (leg.tourId !in tourIds) {
                errors.add("TourLeg references non-existent tour: ${leg.tourId}")
                return@forEach
            }
            val orders = stopsByTour[leg.tourId] ?: emptySet()
            if (leg.fromStopOrder !in orders || leg.toStopOrder !in orders) {
                errors.add(
                    "TourLeg ${leg.tourId} ${leg.fromStopOrder}->${leg.toStopOrder}: " +
                        "stop_order(s) not found in tour"
                )
            }
            if (leg.toStopOrder != leg.fromStopOrder + 1) {
                warnings.add(
                    "TourLeg ${leg.tourId} ${leg.fromStopOrder}->${leg.toStopOrder}: " +
                        "non-consecutive stop_orders"
                )
            }
            if (leg.distanceM <= 0.0) {
                errors.add("TourLeg ${leg.tourId} ${leg.fromStopOrder}: non-positive distance ${leg.distanceM}")
            }
            if (leg.geometry.isBlank()) {
                errors.add("TourLeg ${leg.tourId} ${leg.fromStopOrder}: empty geometry")
            }
        }

        // Basic counts
        if (output.tourPois.isEmpty()) errors.add("No tour POIs generated")
        if (output.figures.isEmpty()) errors.add("No historical figures generated")
        if (output.tours.isEmpty()) warnings.add("No tours generated")
        if (output.facts.isEmpty()) warnings.add("No historical facts generated")
        if (output.timelineEvents.isEmpty()) warnings.add("No timeline events generated")
        if (output.primarySources.isEmpty()) warnings.add("No primary sources generated")

        return ValidationResult(errors, warnings)
    }
}
