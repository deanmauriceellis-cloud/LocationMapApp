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
    @ColumnInfo(name = "is_anchor") val isAnchor: Boolean = false,

    // --- Provenance & Staleness ---
    /** manual_curated|salem_project|overpass_import|api_sync|user_report */
    @ColumnInfo(name = "data_source") val dataSource: String = "salem_project",
    /** 0.0–1.0 trust score */
    val confidence: Float = 1.0f,
    /** ISO date of last human/automated verification */
    @ColumnInfo(name = "verified_date") val verifiedDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
    /** Epoch millis when this record becomes stale (0 = never) */
    @ColumnInfo(name = "stale_after") val staleAfter: Long = 0L
)
