/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.tour

import com.example.wickedsalemwitchcitytour.content.model.Tour
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.content.model.TourStop

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module TourModels.kt"

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

/** Progress tracking for an active tour. */
data class TourProgress(
    val tourId: String,
    val currentStopIndex: Int = 0,
    val completedStops: Set<Int> = emptySet(),
    val skippedStops: Set<Int> = emptySet(),
    val startTimeMs: Long = System.currentTimeMillis(),
    val totalDistanceWalkedM: Double = 0.0,
    val elapsedMs: Long = 0L
) {
    val isComplete: Boolean
        get() = completedStops.size + skippedStops.size >= totalStopCount

    var totalStopCount: Int = 0
        internal set

    val completionPercent: Int
        get() = if (totalStopCount == 0) 0
                else (completedStops.size * 100) / totalStopCount
}

/** A fully-loaded tour with its stops, POIs, and live progress. */
data class ActiveTour(
    val tour: Tour,
    val stops: List<TourStop>,
    val pois: List<TourPoi>,
    val progress: TourProgress
) {
    val currentStop: TourStop?
        get() = stops.getOrNull(progress.currentStopIndex)

    val currentPoi: TourPoi?
        get() {
            val stop = currentStop ?: return null
            return pois.firstOrNull { it.id == stop.poiId }
        }

    val nextStop: TourStop?
        get() = stops.getOrNull(progress.currentStopIndex + 1)

    val nextPoi: TourPoi?
        get() {
            val stop = nextStop ?: return null
            return pois.firstOrNull { it.id == stop.poiId }
        }

    val remainingStops: Int
        get() = stops.size - (progress.completedStops.size + progress.skippedStops.size)
}

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

    /** Tour has been completed. */
    data class Completed(val activeTour: ActiveTour, val summaryStats: TourSummary) : TourState()

    /** Error loading or running tour. */
    data class Error(val message: String) : TourState()
}

/** Summary stats shown at tour completion. */
data class TourSummary(
    val tourName: String,
    val totalStops: Int,
    val completedStops: Int,
    val skippedStops: Int,
    val totalTimeMs: Long,
    val totalDistanceM: Double
) {
    val completionPercent: Int
        get() = if (totalStops == 0) 0 else (completedStops * 100) / totalStops

    val totalTimeMinutes: Int
        get() = (totalTimeMs / 60_000).toInt()
}
