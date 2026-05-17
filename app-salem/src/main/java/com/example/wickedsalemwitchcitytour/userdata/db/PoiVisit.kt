/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * S274 — POI-keyed visit log for Katrina's Collection.
 *
 * Renamed from PassportVisit (S268) as part of the Katrina's Collection
 * rebrand. The table is POI-keyed (one row per POI), not collection-keyed,
 * because a visit is a fact about a POI; per-collection rendering applies
 * the global visit log against each collection's filtered POI list.
 *
 * **Single row per POI, lifetime.** When the user hears a POI's narration —
 * either by geofence ENTRY ([com.example.wickedsalemwitchcitytour.narration.NarrationGeofenceManager])
 * or by tapping the marker and triggering narration inside PoiDetailSheet
 * — the tracker UPSERTs this row: increments [heardCount], updates
 * [lastHeardAtMs], and sets [firstHeardAtMs] only on first encounter.
 *
 * **Why POI-keyed, not collection-keyed** (operator decision, S268,
 * preserved S274): a visit is a fact about a POI, period. Per-tour
 * collections are filtered POI-id lists (see
 * [com.example.wickedsalemwitchcitytour.content.model.CollectionEntry])
 * that render against this one global table. If a POI is in multiple
 * collections, hearing it once flags all of them. Business POIs plug into
 * the same log later without another migration.
 *
 * **Ghost subtype:** HIST_BLDG entries surface as "caught ghosts" in
 * CollectionSheet UI when visited. The visit table doesn't distinguish ghost
 * visits from other POI visits — the UI does that join against POI category
 * at render time.
 *
 * **Persistence:** survives launches and force-stops. `adb uninstall` wipes
 * it (app-private storage by design). For real users, persists indefinitely
 * — Settings → "Reset Collection" in CollectionSheet's overflow menu is the
 * only way to clear it deliberately.
 *
 * Lives in [UserDataDatabase] alongside [GpsTrackPoint] and [PoiEncounter].
 * Schema history: added v3 (S268, as passport_visit) via MIGRATION_2_3;
 * renamed v4 (S274, to poi_visit) via MIGRATION_3_4. Paid-user history
 * lockdown forbids `fallbackToDestructiveMigration` — see [UserDataMigrations].
 */
@Entity(
    tableName = "poi_visit",
    indices = [
        Index(value = ["last_heard_at_ms"])
    ]
)
data class PoiVisit(
    /** Matches `salem_pois.id`. One row per POI for the lifetime of the install. */
    @PrimaryKey
    @ColumnInfo(name = "poi_id")
    val poiId: String,

    /** Wall-clock millis at the first time the user heard this POI's narration. */
    @ColumnInfo(name = "first_heard_at_ms") val firstHeardAtMs: Long,

    /** Wall-clock millis at the most-recent hearing. */
    @ColumnInfo(name = "last_heard_at_ms") val lastHeardAtMs: Long,

    /** Total times the user has heard this POI's narration (each ENTRY or tap-fire). */
    @ColumnInfo(name = "heard_count") val heardCount: Int
)
