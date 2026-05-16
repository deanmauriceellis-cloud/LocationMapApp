/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.tour

import com.example.wickedsalemwitchcitytour.content.model.Tour
import com.example.wickedsalemwitchcitytour.content.model.TourLeg
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.content.model.TourStop

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module TourModels.kt"

/** Themed tour categories — maps to the `theme` field in the tours table. */
enum class TourTheme(val displayName: String) {
    WITCH_TRIALS("Witch Trials"),
    MARITIME("Maritime Heritage"),
    LITERARY("Literary Salem"),
    ARCHITECTURE("Architecture"),
    PARKS("Parks & Outdoors"),
    FOOD_DRINK("Food & Drink"),
    COMPLETE("Complete Salem"),
    OCTOBER_SPECIAL("October Special"),
    HERITAGE_TRAIL("Heritage Trail"),
    CUSTOM("Custom Tour");

    companion object {
        fun fromString(value: String): TourTheme =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CUSTOM
    }
}

/**
 * S269 — runtime progress carried across an active tour.
 *
 * Pre-S269 this carried `currentStopIndex` + `completedStops` + `skippedStops`
 * to drive the stops-based HUD / Active-Tour dialog / completion stats. All
 * four of those surfaces were ripped out in S269 in favour of the POI
 * Passport (see `docs/plans/poi-passport-replaces-tour-stops.md`); the only
 * progress the runtime still tracks is time + cumulative distance, both
 * trivially derivable from start-time and accumulated GPS deltas.
 */
data class TourProgress(
    val tourId: String,
    val startTimeMs: Long = System.currentTimeMillis(),
    val totalDistanceWalkedM: Double = 0.0,
    val elapsedMs: Long = 0L,
)

/**
 * A fully-loaded tour with its baked legs, POIs, and live progress.
 *
 * Post-S190 every authored tour is polyline-only — [stops] is preserved as a
 * field for parity with [TourGeofenceManager.loadStops] but every entry has
 * `poiId == null`, so consumers must never derive tour-membership by joining
 * on stops (see memory rule `feedback_tour_stops_not_poi_anchored.md`).
 * [legs] is the canonical geometry source; [passportPoiIds] is the canonical
 * tour-membership source.
 */
data class ActiveTour(
    val tour: Tour,
    val stops: List<TourStop>,
    val pois: List<TourPoi>,
    /** Pre-baked polyline geometry. Used at runtime by the detour anchor
     *  helper and by the route overlay. */
    val legs: List<TourLeg>,
    val progress: TourProgress,
    /** `salem_passport_filters.id` for this tour's passport, or null when no
     *  passport is authored. */
    val passportId: String?,
    /** POIs in the tour's passport, in display order. Drives the visit-count
     *  vs total comparison that fires [TourState.Completed] in S269. */
    val passportPoiIds: Set<String>,
)

/** Observable state emitted by TourEngine. */
sealed class TourState {
    /** No tour is active. */
    data object Idle : TourState()

    /** Tour is loading from the database. */
    data object Loading : TourState()

    /** Tour is actively running. */
    data class Active(val activeTour: ActiveTour) : TourState()

    /** Tour is paused (user-initiated). */
    data class Paused(val activeTour: ActiveTour) : TourState()

    /**
     * S221 — Tour is paused while the user takes a *detour* to another POI
     * (typically opened from a subtopic adjacency card). The narration gate
     * is fully relaxed during a detour so any nearby property speaks; the
     * persistent floating banner offers Return-to-tour or Stop-tour-continue-later.
     * Distinct from [Paused] (user-initiated) so the UI can pick the right
     * surface — a detour shows the banner; a plain pause does not.
     */
    data class Detour(
        val activeTour: ActiveTour,
        val detourPoiId: String,
        val detourPoiName: String,
    ) : TourState()

    /** Tour has been completed. */
    data class Completed(val activeTour: ActiveTour, val summaryStats: TourSummary) : TourState()

    /** Error loading or running tour. */
    data class Error(val message: String) : TourState()
}

/**
 * S269 — summary shown at tour completion. Passport-aware: replaces the
 * pre-S269 stops-based counters with `passportHeard / passportTotal` so the
 * completion dialog reflects how many of the tour's curated POIs the walker
 * actually heard. [passportId] is non-null whenever the just-ended tour had
 * a passport bound to it, and lets the completion dialog open the
 * PassportSheet pre-filtered to that passport.
 */
data class TourSummary(
    val tourName: String,
    val passportId: String?,
    val passportTotal: Int,
    val passportHeard: Int,
    val totalTimeMs: Long,
    val totalDistanceM: Double,
) {
    val completionPercent: Int
        get() = if (passportTotal == 0) 0 else (passportHeard * 100) / passportTotal

    val totalTimeMinutes: Int
        get() = (totalTimeMs / 60_000).toInt()
}
