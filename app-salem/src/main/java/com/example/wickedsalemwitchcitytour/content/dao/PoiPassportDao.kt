/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.wickedsalemwitchcitytour.content.model.PoiPassport

/**
 * S268 — read-only DAO over the baked `poi_passport` table.
 *
 * The runtime never writes to this table — it's populated at bake time by
 * `cache-proxy/scripts/publish-poi-passport.js`. Visit state (whether the
 * user has heard each POI) lives separately in
 * [com.example.wickedsalemwitchcitytour.userdata.dao.PassportVisitDao].
 *
 * Used by the new PassportSheet UI (lands in S3) — list passports, render
 * a passport's POIs grouped by category, switch between global and per-tour
 * passports via the header dropdown.
 */
@Dao
interface PoiPassportDao {

    /**
     * One row per distinct passport, with denormalized name + total POI count.
     * Drives the passport-picker dropdown in the PassportSheet header.
     */
    data class PassportSummary(
        val passport_id: String,
        val passport_name: String,
        val tour_id: String?,
        val poi_count: Int
    )

    @Query(
        """
        SELECT passport_id, passport_name, tour_id, COUNT(*) AS poi_count
          FROM poi_passport
      GROUP BY passport_id, passport_name, tour_id
      ORDER BY (tour_id IS NULL) DESC, passport_name ASC
        """
    )
    suspend fun listPassports(): List<PassportSummary>

    /** All POIs in a passport, in display_order. */
    @Query("SELECT * FROM poi_passport WHERE passport_id = :passportId ORDER BY display_order ASC")
    suspend fun findByPassport(passportId: String): List<PoiPassport>

    /** All passports a given POI appears in — used by visit-tracking when a POI is heard. */
    @Query("SELECT DISTINCT passport_id FROM poi_passport WHERE poi_id = :poiId")
    suspend fun findPassportsForPoi(poiId: String): List<String>

    /** Total POI count for a passport (used for "X / Y collected" header math). */
    @Query("SELECT COUNT(*) FROM poi_passport WHERE passport_id = :passportId")
    suspend fun countForPassport(passportId: String): Int

    /** Per-tour passport id lookup. Returns null if the tour has no passport authored. */
    @Query("SELECT DISTINCT passport_id FROM poi_passport WHERE tour_id = :tourId LIMIT 1")
    suspend fun findPassportIdForTour(tourId: String): String?

    @Query("SELECT COUNT(*) FROM poi_passport")
    suspend fun count(): Int
}
