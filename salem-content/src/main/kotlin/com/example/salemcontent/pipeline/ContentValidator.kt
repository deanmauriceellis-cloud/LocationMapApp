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

        // Basic counts
        if (output.tourPois.isEmpty()) errors.add("No tour POIs generated")
        if (output.figures.isEmpty()) errors.add("No historical figures generated")
        if (output.facts.isEmpty()) warnings.add("No historical facts generated")
        if (output.timelineEvents.isEmpty()) warnings.add("No timeline events generated")
        if (output.primarySources.isEmpty()) warnings.add("No primary sources generated")

        return ValidationResult(errors, warnings)
    }
}
