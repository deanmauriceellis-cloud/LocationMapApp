/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
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
 * One "I got close to this POI" encounter, recorded by
 * [com.example.wickedsalemwitchcitytour.userdata.PoiEncounterTracker].
 *
 * **What this represents:** every time the user comes within
 * [com.example.wickedsalemwitchcitytour.userdata.PoiEncounterTracker.PROXIMITY_RADIUS_M]
 * of a narration POI, a new row is opened. While the user remains within
 * the proximity radius, the row is updated on every GPS fix to track:
 *   - first/last seen timestamps
 *   - total duration in proximity
 *   - closest approach (`min_distance_m`)
 *   - farthest point reached while still in proximity (`max_distance_m`)
 *   - number of GPS fixes that contributed to the encounter
 *
 * When the user leaves the proximity radius, the row is finalized — no
 * further updates — but stays in the table for review.
 *
 * **Primary key:** composite `poiId|firstSeenAtMs`. This lets the tracker
 * use `INSERT OR REPLACE` semantics on every fix during the encounter
 * without juggling Room's auto-generated row IDs across coroutine
 * boundaries. If the user comes back to the same POI later, that's a
 * brand-new encounter with a different `firstSeenAtMs` and therefore a
 * different `id`.
 *
 * **Process death:** because the row is INSERT-OR-REPLACED on every fix
 * during the encounter, the DB always has the latest snapshot. If the
 * process dies mid-encounter, the row is left as-is and reflects the
 * state at the last fix before death. The tracker does not look for
 * "orphaned" encounters at startup — they're already correct.
 *
 * **Retention:** rolling 7 days, pruned hourly by the tracker.
 *
 * Lives in [UserDataDatabase] alongside [GpsTrackPoint]. Schema version
 * bumped 1 → 2 to add this table; uses `fallbackToDestructiveMigration`
 * because the DB has no production data yet (S109 GPS journey is the
 * only existing user-data table and it's already destroy-on-mismatch).
 */
@Entity(
    tableName = "poi_encounters",
    indices = [
        Index(value = ["first_seen_at_ms"]),
        Index(value = ["poi_id"])
    ]
)
data class PoiEncounter(
    /**
     * Composite key — "${poiId}|${firstSeenAtMs}". Lets us use
     * `INSERT OR REPLACE` from the tracker without round-tripping the
     * auto-generated row id back to the in-memory state.
     */
    @PrimaryKey val id: String,

    @ColumnInfo(name = "poi_id") val poiId: String,
    @ColumnInfo(name = "poi_name") val poiName: String,
    @ColumnInfo(name = "poi_lat") val poiLat: Double,
    @ColumnInfo(name = "poi_lng") val poiLng: Double,

    /** Wall-clock millis at the moment the user first crossed inside the proximity radius. */
    @ColumnInfo(name = "first_seen_at_ms") val firstSeenAtMs: Long,

    /** Wall-clock millis at the moment of the most recent in-proximity GPS fix. */
    @ColumnInfo(name = "last_seen_at_ms") val lastSeenAtMs: Long,

    /** `lastSeenAtMs - firstSeenAtMs`, denormalized so review queries don't have to compute it. */
    @ColumnInfo(name = "duration_ms") val durationMs: Long,

    /** Closest approach to the POI in meters across the encounter. */
    @ColumnInfo(name = "min_distance_m") val minDistanceM: Double,

    /** Farthest distance from the POI while still inside the proximity radius. */
    @ColumnInfo(name = "max_distance_m") val maxDistanceM: Double,

    /** Number of GPS fixes that landed inside the proximity radius for this encounter. */
    @ColumnInfo(name = "fix_count") val fixCount: Int
)
