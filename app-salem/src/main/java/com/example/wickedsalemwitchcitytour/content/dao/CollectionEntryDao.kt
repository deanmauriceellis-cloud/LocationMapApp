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
import com.example.wickedsalemwitchcitytour.content.model.CollectionEntry

/**
 * S274 — read-only DAO over the baked `collection_entry` table. Renamed
 * from PoiPassportDao (S268) for the Katrina's Collection rebrand.
 *
 * The runtime never writes to this table — it's populated at bake time by
 * `cache-proxy/scripts/publish-poi-collection.js`. Visit state (whether the
 * user has heard each POI) lives separately in
 * [com.example.wickedsalemwitchcitytour.userdata.dao.PoiVisitDao].
 *
 * Used by the CollectionSheet UI — list collections, render a collection's
 * POIs grouped by category, switch between global and per-tour collections
 * via the header dropdown.
 */
@Dao
interface CollectionEntryDao {

    /**
     * One row per distinct collection, with denormalized name + total POI
     * count. Drives the collection-picker dropdown in the CollectionSheet
     * header.
     */
    data class CollectionSummary(
        val collection_id: String,
        val collection_name: String,
        val tour_id: String?,
        val poi_count: Int
    )

    @Query(
        """
        SELECT collection_id, collection_name, tour_id, COUNT(*) AS poi_count
          FROM collection_entry
      GROUP BY collection_id, collection_name, tour_id
      ORDER BY (tour_id IS NULL) DESC, collection_name ASC
        """
    )
    suspend fun listCollections(): List<CollectionSummary>

    /** All POIs in a collection, in display_order. */
    @Query("SELECT * FROM collection_entry WHERE collection_id = :collectionId ORDER BY display_order ASC")
    suspend fun findByCollection(collectionId: String): List<CollectionEntry>

    /** All collections a given POI appears in — used by visit-tracking when a POI is heard. */
    @Query("SELECT DISTINCT collection_id FROM collection_entry WHERE poi_id = :poiId")
    suspend fun findCollectionsForPoi(poiId: String): List<String>

    /** Total POI count for a collection (used for "X / Y collected" header math). */
    @Query("SELECT COUNT(*) FROM collection_entry WHERE collection_id = :collectionId")
    suspend fun countForCollection(collectionId: String): Int

    /** Per-tour collection id lookup. Returns null if the tour has no collection authored. */
    @Query("SELECT DISTINCT collection_id FROM collection_entry WHERE tour_id = :tourId LIMIT 1")
    suspend fun findCollectionIdForTour(tourId: String): String?

    @Query("SELECT COUNT(*) FROM collection_entry")
    suspend fun count(): Int
}
