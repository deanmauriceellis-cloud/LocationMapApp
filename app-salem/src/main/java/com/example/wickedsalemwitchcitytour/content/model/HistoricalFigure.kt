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
 * People of the Salem Witch Trials — ~50 key historical figures.
 * Sourced from ~/Development/Salem NPC data (Tier 1 + Tier 2).
 */
@Entity(tableName = "historical_figures")
data class HistoricalFigure(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "first_name") val firstName: String,
    val surname: String,
    val born: String? = null,
    val died: String? = null,
    @ColumnInfo(name = "age_in_1692") val ageIn1692: Int? = null,
    /** accused|accuser|magistrate|minister|defender|witness|victim|other */
    val role: String,
    /** pro_trials|anti_trials|neutral|shifting */
    val faction: String? = null,
    /** 2-3 sentences */
    @ColumnInfo(name = "short_bio") val shortBio: String,
    /** Complete narrative */
    @ColumnInfo(name = "full_bio") val fullBio: String? = null,
    /** TTS-optimized narration */
    @ColumnInfo(name = "narration_script") val narrationScript: String? = null,
    @ColumnInfo(name = "appearance_description") val appearanceDescription: String? = null,
    @ColumnInfo(name = "role_in_crisis") val roleInCrisis: String? = null,
    @ColumnInfo(name = "historical_outcome") val historicalOutcome: String? = null,
    /** JSON array of quote strings */
    @ColumnInfo(name = "key_quotes") val keyQuotes: String? = null,
    /** JSON object of family relationships */
    @ColumnInfo(name = "family_connections") val familyConnections: String? = null,
    /** FK to tour_pois.id — the primary POI associated with this figure */
    @ColumnInfo(name = "primary_poi_id") val primaryPoiId: String? = null
)
