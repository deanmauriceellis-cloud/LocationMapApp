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
 * S268 — POI-keyed visit log for the POI Passport feature.
 *
 * **Single row per POI, lifetime.** When the user hears a POI's narration —
 * either by geofence ENTRY ([com.example.wickedsalemwitchcitytour.narration.NarrationGeofenceManager])
 * or by tapping the marker and triggering narration inside PoiDetailSheet
 * — the tracker UPSERTs this row: increments [heardCount], updates
 * [lastHeardAtMs], and sets [firstHeardAtMs] only on first encounter.
 *
 * **Why no `passport_id` column** (operator decision, S268): a visit is a
 * fact about a POI, period. Per-tour Passports are filtered POI-id lists
 * (see [com.example.wickedsalemwitchcitytour.content.model.PoiPassport])
 * that render against this one global table. If a POI is in multiple
 * passports, hearing it once stamps all of them. This keeps the schema
 * trivial and lets Business POIs plug into the same log later without a
 * v4 migration.
 *
 * **Persistence:** survives launches and force-stops. `adb uninstall` wipes
 * it (app-private storage by design). For real users, persists indefinitely
 * — Settings → "Reset Passport" in PassportSheet's overflow menu is the
 * only way to clear it deliberately.
 *
 * Lives in [UserDataDatabase] alongside [GpsTrackPoint] and [PoiEncounter].
 * Added in v3 via the hand-written [com.example.wickedsalemwitchcitytour.userdata.db.MIGRATION_2_3]
 * — paid-user history lockdown forbids `fallbackToDestructiveMigration`.
 */
@Entity(
    tableName = "passport_visit",
    indices = [
        Index(value = ["last_heard_at_ms"])
    ]
)
data class PassportVisit(
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
