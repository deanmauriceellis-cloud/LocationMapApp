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
 * Court records, petitions, letters, diary entries — ~200 key excerpts.
 * Primary source material from the Salem Witch Trials.
 */
@Entity(tableName = "primary_sources")
data class PrimarySource(
    @PrimaryKey val id: String,
    val title: String,
    /** examination|petition|letter|diary|sermon|court_record */
    @ColumnInfo(name = "source_type") val sourceType: String,
    val author: String? = null,
    val date: String? = null,
    @ColumnInfo(name = "full_text") val fullText: String? = null,
    /** Key passage excerpt */
    val excerpt: String? = null,
    /** FK to historical_figures.id */
    @ColumnInfo(name = "figure_id") val figureId: String? = null,
    /** FK to tour_pois.id */
    @ColumnInfo(name = "poi_id") val poiId: String? = null,
    @ColumnInfo(name = "narration_script") val narrationScript: String? = null,
    val citation: String? = null,

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
