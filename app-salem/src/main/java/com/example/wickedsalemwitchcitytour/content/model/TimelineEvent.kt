/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Chronological events of 1692 — 40 anchor + ~40 minor events.
 */
@Entity(tableName = "timeline_events")
data class TimelineEvent(
    @PrimaryKey val id: String,
    val name: String,
    val date: String,
    /** pre_crisis|accusations|examinations|trials|executions|aftermath|resolution */
    @ColumnInfo(name = "crisis_phase") val crisisPhase: String? = null,
    val description: String,
    /** FK to tour_pois.id */
    @ColumnInfo(name = "poi_id") val poiId: String? = null,
    /** JSON array of figure IDs */
    @ColumnInfo(name = "figures_involved") val figuresInvolved: String? = null,
    @ColumnInfo(name = "narration_script") val narrationScript: String? = null,
    @ColumnInfo(name = "is_anchor") val isAnchor: Boolean = false
)
