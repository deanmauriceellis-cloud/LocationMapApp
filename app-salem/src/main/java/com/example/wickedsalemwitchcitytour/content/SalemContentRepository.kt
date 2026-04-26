/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content

import com.example.wickedsalemwitchcitytour.content.dao.*
import com.example.wickedsalemwitchcitytour.content.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified access to all Salem-specific content.
 * Wraps the individual DAOs and provides higher-level query methods.
 */
@Singleton
class SalemContentRepository @Inject constructor(
    private val tourPoiDao: TourPoiDao,
    private val historicalFigureDao: HistoricalFigureDao,
    private val historicalFactDao: HistoricalFactDao,
    private val timelineEventDao: TimelineEventDao,
    private val primarySourceDao: PrimarySourceDao,
    private val tourDao: TourDao,
    private val tourStopDao: TourStopDao,
    private val tourLegDao: TourLegDao,
    private val eventsCalendarDao: EventsCalendarDao,
    private val salemPoiDao: SalemPoiDao
) {

    // ── Tours ────────────────────────────────────────────────────────────

    suspend fun getAllTours(): List<Tour> = tourDao.findAll()

    suspend fun getTourById(id: String): Tour? = tourDao.findById(id)

    suspend fun getTourStops(tourId: String): List<TourStop> = tourStopDao.findByTour(tourId)

    /** Get the full TourPoi objects for each stop in a tour, ordered. */
    suspend fun getTourPois(tourId: String): List<TourPoi> = tourStopDao.findTourPoisByTour(tourId)

    /** S185 — Pre-baked walking legs for a tour, ordered by from_stop_order. */
    suspend fun getTourLegs(tourId: String): List<TourLeg> = tourLegDao.findByTour(tourId)

    // ── Tour POIs (project from salem_pois — Phase 9U) ───────────────────

    suspend fun getAllTourPois(): List<TourPoi> = tourPoiDao.findAll()

    suspend fun getTourPoiById(id: String): TourPoi? = tourPoiDao.findById(id)

    // ── Historical Figures ───────────────────────────────────────────────

    suspend fun getAllFigures(): List<HistoricalFigure> = historicalFigureDao.findAll()

    suspend fun getFigureById(id: String): HistoricalFigure? = historicalFigureDao.findById(id)

    suspend fun getFiguresByPoi(poiId: String): List<HistoricalFigure> =
        historicalFigureDao.findByPoi(poiId)

    // ── Historical Facts ─────────────────────────────────────────────────

    suspend fun getFactsByPoi(poiId: String): List<HistoricalFact> =
        historicalFactDao.findByPoi(poiId)

    suspend fun getFactsByFigure(figureId: String): List<HistoricalFact> =
        historicalFactDao.findByFigure(figureId)

    // ── Timeline ─────────────────────────────────────────────────────────

    suspend fun getTimeline(): List<TimelineEvent> = timelineEventDao.findAll()

    suspend fun getAnchorEvents(): List<TimelineEvent> = timelineEventDao.findAnchorEvents()

    suspend fun getTimelineByPhase(phase: String): List<TimelineEvent> =
        timelineEventDao.findByPhase(phase)

    /** Find timeline events that occurred on this month-day in 1692. */
    suspend fun getTimelineByMonthDay(monthDay: String): List<TimelineEvent> =
        timelineEventDao.findByMonthDay(monthDay)

    // ── Primary Sources ──────────────────────────────────────────────────

    suspend fun getSourcesByFigure(figureId: String): List<PrimarySource> =
        primarySourceDao.findByFigure(figureId)

    suspend fun getSourcesByPoi(poiId: String): List<PrimarySource> =
        primarySourceDao.findByPoi(poiId)

    // ── Events Calendar ──────────────────────────────────────────────────

    suspend fun getUpcomingEvents(today: String): List<EventsCalendar> =
        eventsCalendarDao.findUpcoming(today)

    suspend fun getEventsByMonth(month: Int): List<EventsCalendar> =
        eventsCalendarDao.findByMonth(month)

    suspend fun getActiveEvents(today: String): List<EventsCalendar> =
        eventsCalendarDao.findActive(today)

    suspend fun getEventsByType(type: String): List<EventsCalendar> =
        eventsCalendarDao.findByType(type)

    suspend fun getAllEvents(): List<EventsCalendar> =
        eventsCalendarDao.findAll()

    // ── Provenance & Staleness ─────────────────────────────────────────

    /** Find all stale events. */
    suspend fun getStaleEvents(): List<EventsCalendar> =
        eventsCalendarDao.findStale(System.currentTimeMillis())

    // ── Unified POIs (Phase 9U — salem_pois table) ───────────────────────

    suspend fun getAllPois(): List<SalemPoi> = salemPoiDao.findAll()

    suspend fun getVisiblePois(): List<SalemPoi> = salemPoiDao.findAllVisible()

    suspend fun getPoiById(id: String): SalemPoi? = salemPoiDao.findById(id)

    suspend fun getPoisByCategory(category: String): List<SalemPoi> =
        salemPoiDao.findByCategory(category)

    suspend fun getPoisBySubcategory(subcategory: String): List<SalemPoi> =
        salemPoiDao.findBySubcategory(subcategory)

    suspend fun getNarratedPois(): List<SalemPoi> = salemPoiDao.findNarrated()

    suspend fun getNarratedPoisByWave(wave: Int): List<SalemPoi> =
        salemPoiDao.findNarratedByWave(wave)

    suspend fun getNarratedPoisInBbox(
        latMin: Double, latMax: Double, lngMin: Double, lngMax: Double
    ): List<SalemPoi> = salemPoiDao.findNarratedInBbox(latMin, latMax, lngMin, lngMax)

    suspend fun getPoisNearby(lat: Double, lng: Double, radiusM: Double): List<SalemPoi> {
        val radiusDeg = radiusM / 111_000.0
        return salemPoiDao.findNearby(lat, lng, radiusDeg)
    }

    suspend fun getNarratedPoisNearby(lat: Double, lng: Double, radiusM: Double): List<SalemPoi> {
        val radiusDeg = radiusM / 111_000.0
        return salemPoiDao.findNarratedNearby(lat, lng, radiusDeg)
    }

    suspend fun getVisiblePoisNearby(lat: Double, lng: Double, radiusM: Double): List<SalemPoi> {
        val radiusDeg = radiusM / 111_000.0
        return salemPoiDao.findVisibleNearby(lat, lng, radiusDeg)
    }

    suspend fun searchPois(query: String): List<SalemPoi> = salemPoiDao.search(query)

    suspend fun getPoisByDistrict(district: String): List<SalemPoi> =
        salemPoiDao.findByDistrict(district)

    suspend fun getDistricts(): List<String> = salemPoiDao.getDistricts()

    suspend fun getPoiCount(): Int = salemPoiDao.count()

    suspend fun getVisiblePoiCount(): Int = salemPoiDao.countVisible()

    suspend fun insertPois(pois: List<SalemPoi>) = salemPoiDao.insertAll(pois)

    // ── Bulk Insert (for content pipeline) ───────────────────────────────

    suspend fun insertFigures(figures: List<HistoricalFigure>) = historicalFigureDao.insertAll(figures)
    suspend fun insertFacts(facts: List<HistoricalFact>) = historicalFactDao.insertAll(facts)
    suspend fun insertTimelineEvents(events: List<TimelineEvent>) = timelineEventDao.insertAll(events)
    suspend fun insertPrimarySources(sources: List<PrimarySource>) = primarySourceDao.insertAll(sources)
    suspend fun insertTours(tours: List<Tour>) = tourDao.insertAll(tours)
    suspend fun insertTourStops(stops: List<TourStop>) = tourStopDao.insertAll(stops)
    suspend fun insertEvents(events: List<EventsCalendar>) = eventsCalendarDao.insertAll(events)
}
