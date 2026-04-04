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
    private val salemBusinessDao: SalemBusinessDao,
    private val historicalFigureDao: HistoricalFigureDao,
    private val historicalFactDao: HistoricalFactDao,
    private val timelineEventDao: TimelineEventDao,
    private val primarySourceDao: PrimarySourceDao,
    private val tourDao: TourDao,
    private val tourStopDao: TourStopDao,
    private val eventsCalendarDao: EventsCalendarDao
) {

    // ── Tours ────────────────────────────────────────────────────────────

    suspend fun getAllTours(): List<Tour> = tourDao.findAll()

    suspend fun getTourById(id: String): Tour? = tourDao.findById(id)

    suspend fun getTourStops(tourId: String): List<TourStop> = tourStopDao.findByTour(tourId)

    /** Get the full TourPoi objects for each stop in a tour, ordered. */
    suspend fun getTourPois(tourId: String): List<TourPoi> = tourStopDao.findTourPoisByTour(tourId)

    // ── Tour POIs ────────────────────────────────────────────────────────

    suspend fun getAllTourPois(): List<TourPoi> = tourPoiDao.findAll()

    suspend fun getTourPoiById(id: String): TourPoi? = tourPoiDao.findById(id)

    suspend fun getTourPoisByCategory(category: String): List<TourPoi> =
        tourPoiDao.findByCategory(category)

    /**
     * Find tour POIs within [radiusM] meters of the given point.
     * Converts meters to approximate degrees for the bounding-box pre-filter.
     */
    suspend fun getTourPoisNearby(lat: Double, lng: Double, radiusM: Double): List<TourPoi> {
        val radiusDeg = radiusM / 111_000.0
        return tourPoiDao.findNearby(lat, lng, radiusDeg)
    }

    suspend fun searchTourPois(query: String): List<TourPoi> = tourPoiDao.search(query)

    // ── Businesses ───────────────────────────────────────────────────────

    suspend fun getBusinessesByType(type: String): List<SalemBusiness> =
        salemBusinessDao.findByType(type)

    suspend fun getBusinessesNearby(lat: Double, lng: Double, radiusM: Double): List<SalemBusiness> {
        val radiusDeg = radiusM / 111_000.0
        return salemBusinessDao.findNearby(lat, lng, radiusDeg)
    }

    suspend fun searchBusinesses(query: String): List<SalemBusiness> =
        salemBusinessDao.search(query)

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

    // ── Provenance & Staleness ─────────────────────────────────────────

    /** Find all stale tour POIs (stale_after > 0 and expired). */
    suspend fun getStaleTourPois(): List<TourPoi> =
        tourPoiDao.findStale(System.currentTimeMillis())

    /** Find all stale businesses. */
    suspend fun getStaleBusinesses(): List<SalemBusiness> =
        salemBusinessDao.findStale(System.currentTimeMillis())

    /** Find all stale events. */
    suspend fun getStaleEvents(): List<EventsCalendar> =
        eventsCalendarDao.findStale(System.currentTimeMillis())

    /** Find tour POIs by data source. */
    suspend fun getTourPoisBySource(source: String): List<TourPoi> =
        tourPoiDao.findBySource(source)

    /** Find businesses by data source. */
    suspend fun getBusinessesBySource(source: String): List<SalemBusiness> =
        salemBusinessDao.findBySource(source)

    /** Touch the updated_at timestamp for a tour POI. */
    suspend fun markTourPoiUpdated(id: String) =
        tourPoiDao.markUpdated(id, System.currentTimeMillis())

    /** Touch the updated_at timestamp for a business. */
    suspend fun markBusinessUpdated(id: String) =
        salemBusinessDao.markUpdated(id, System.currentTimeMillis())

    // ── Bulk Insert (for content pipeline) ───────────────────────────────

    suspend fun insertTourPois(pois: List<TourPoi>) = tourPoiDao.insertAll(pois)
    suspend fun insertBusinesses(businesses: List<SalemBusiness>) = salemBusinessDao.insertAll(businesses)
    suspend fun insertFigures(figures: List<HistoricalFigure>) = historicalFigureDao.insertAll(figures)
    suspend fun insertFacts(facts: List<HistoricalFact>) = historicalFactDao.insertAll(facts)
    suspend fun insertTimelineEvents(events: List<TimelineEvent>) = timelineEventDao.insertAll(events)
    suspend fun insertPrimarySources(sources: List<PrimarySource>) = primarySourceDao.insertAll(sources)
    suspend fun insertTours(tours: List<Tour>) = tourDao.insertAll(tours)
    suspend fun insertTourStops(stops: List<TourStop>) = tourStopDao.insertAll(stops)
    suspend fun insertEvents(events: List<EventsCalendar>) = eventsCalendarDao.insertAll(events)
}
