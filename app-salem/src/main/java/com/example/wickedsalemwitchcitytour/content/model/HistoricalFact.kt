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
 * Facts from the Salem knowledge base — ~500 selected tourist-relevant facts.
 */
@Entity(tableName = "historical_facts")
data class HistoricalFact(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val date: String? = null,
    /** exact|month|year|approximate */
    @ColumnInfo(name = "date_precision") val datePrecision: String? = null,
    val category: String? = null,
    val subcategory: String? = null,
    /** FK to tour_pois.id */
    @ColumnInfo(name = "poi_id") val poiId: String? = null,
    /** FK to historical_figures.id */
    @ColumnInfo(name = "figure_id") val figureId: String? = null,
    @ColumnInfo(name = "source_citation") val sourceCitation: String? = null,
    @ColumnInfo(name = "narration_script") val narrationScript: String? = null,
    /** public|semi_private */
    val confidentiality: String = "public",
    /** JSON array of tag strings */
    val tags: String? = null,

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
