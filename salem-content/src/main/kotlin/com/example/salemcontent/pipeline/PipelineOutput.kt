/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.pipeline

/**
 * Intermediate output matching the Room entity schemas.
 * Field names match the Room @ColumnInfo names for direct SQL generation.
 */
data class PipelineOutput(
    val tourPois: List<OutputTourPoi>,
    val businesses: List<OutputBusiness>,
    val figures: List<OutputFigure>,
    val facts: List<OutputFact>,
    val timelineEvents: List<OutputTimelineEvent>,
    val primarySources: List<OutputPrimarySource>,
    val tours: List<OutputTour>,
    val tourStops: List<OutputTourStop>,
    val tourLegs: List<OutputTourLeg>,
    val events: List<OutputEvent>
)

/** Provenance metadata shared by all output types. */
data class Provenance(
    val dataSource: String = "manual_curated",
    val confidence: Float = 1.0f,
    val verifiedDate: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val staleAfter: Long = 0L
)

data class OutputTourPoi(
    val id: String, val name: String, val lat: Double, val lng: Double,
    val address: String, val category: String, val subcategories: String? = null,
    val shortNarration: String? = null, val longNarration: String? = null,
    val description: String? = null, val historicalPeriod: String? = null,
    val admissionInfo: String? = null, val hours: String? = null,
    val phone: String? = null, val website: String? = null,
    val imageAsset: String? = null, val geofenceRadiusM: Int = 50,
    val requiresTransportation: Boolean = false,
    val wheelchairAccessible: Boolean = true,
    val seasonal: Boolean = false, val priority: Int = 3,
    val provenance: Provenance = Provenance(dataSource = "manual_curated")
)

data class OutputBusiness(
    val id: String, val name: String, val lat: Double, val lng: Double,
    val address: String, val businessType: String,
    val cuisineType: String? = null, val priceRange: String? = null,
    val hours: String? = null, val phone: String? = null,
    val website: String? = null, val description: String? = null,
    val historicalNote: String? = null, val tags: String? = null,
    val rating: Float? = null, val imageAsset: String? = null,
    val provenance: Provenance = Provenance(dataSource = "manual_curated")
)

data class OutputFigure(
    val id: String, val name: String, val firstName: String, val surname: String,
    val born: String? = null, val died: String? = null, val ageIn1692: Int? = null,
    val role: String, val faction: String? = null,
    val shortBio: String, val fullBio: String? = null,
    val narrationScript: String? = null, val appearanceDescription: String? = null,
    val roleInCrisis: String? = null, val historicalOutcome: String? = null,
    val keyQuotes: String? = null, val familyConnections: String? = null,
    val primaryPoiId: String? = null,
    val provenance: Provenance = Provenance(dataSource = "salem_project")
)

data class OutputFact(
    val id: String, val title: String, val description: String,
    val date: String? = null, val datePrecision: String? = null,
    val category: String? = null, val subcategory: String? = null,
    val poiId: String? = null, val figureId: String? = null,
    val sourceCitation: String? = null, val narrationScript: String? = null,
    val confidentiality: String = "public", val tags: String? = null,
    val provenance: Provenance = Provenance(dataSource = "salem_project")
)

data class OutputTimelineEvent(
    val id: String, val name: String, val date: String,
    val crisisPhase: String? = null, val description: String,
    val poiId: String? = null, val figuresInvolved: String? = null,
    val narrationScript: String? = null, val isAnchor: Boolean = false,
    val provenance: Provenance = Provenance(dataSource = "salem_project")
)

data class OutputPrimarySource(
    val id: String, val title: String, val sourceType: String,
    val author: String? = null, val date: String? = null,
    val fullText: String? = null, val excerpt: String? = null,
    val figureId: String? = null, val poiId: String? = null,
    val narrationScript: String? = null, val citation: String? = null,
    val provenance: Provenance = Provenance(dataSource = "salem_project")
)

data class OutputTour(
    val id: String, val name: String, val theme: String,
    val description: String, val estimatedMinutes: Int,
    val distanceKm: Float, val stopCount: Int,
    val difficulty: String = "moderate", val seasonal: Boolean = false,
    val iconAsset: String? = null, val sortOrder: Int = 0,
    val provenance: Provenance = Provenance(dataSource = "manual_curated")
)

data class OutputTourStop(
    val tourId: String, val poiId: String, val stopOrder: Int,
    val transitionNarration: String? = null,
    val walkingMinutesFromPrev: Int? = null,
    val distanceMFromPrev: Int? = null,
    val provenance: Provenance = Provenance(dataSource = "manual_curated")
)

/**
 * Pre-computed walking leg between two consecutive stops in a tour, baked
 * from the same `salem-routing-graph.sqlite` bundle that the runtime Router
 * uses. Lets the tour player render the polyline + distance/duration without
 * loading the routing bundle or running Dijkstra at runtime.
 *
 * Geometry is encoded as `"lat,lng;lat,lng;..."` to match the bundle's
 * `edges.geom_polyline` text format and the Android router's parsing
 * expectations.
 */
data class OutputTourLeg(
    val tourId: String,
    val fromStopOrder: Int,
    val toStopOrder: Int,
    val fromPoiId: String,
    val toPoiId: String,
    val distanceM: Double,
    val durationS: Double,
    val edgeCount: Int,
    val geometry: String,
    val provenance: Provenance = Provenance(dataSource = "router_bake_v1")
)

data class OutputEvent(
    val id: String, val name: String, val venuePoiId: String? = null,
    val eventType: String, val description: String? = null,
    val startDate: String? = null, val endDate: String? = null,
    val hours: String? = null, val admission: String? = null,
    val website: String? = null, val recurring: Boolean = false,
    val recurrencePattern: String? = null, val seasonalMonth: Int? = null,
    val provenance: Provenance = Provenance(dataSource = "manual_curated")
)
