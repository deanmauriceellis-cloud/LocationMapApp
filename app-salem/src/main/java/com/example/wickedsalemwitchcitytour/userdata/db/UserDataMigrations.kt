/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Hand-written Room migrations for [UserDataDatabase].
 *
 * S180 lockdown rule: every version bump (v3+) MUST register a real Migration
 * here. `fallbackToDestructiveMigration` was removed in S180 — paid Play Store
 * users would lose `poi_encounters` history (and now `poi_visit` history)
 * on every app update without these. Forgetting to register = ship-blocker.
 *
 * Register in [com.example.wickedsalemwitchcitytour.userdata.di.UserDataModule]
 * via `.addMigrations(...)` when building the database.
 */
object UserDataMigrations {

    /**
     * S268 — v2 → v3 adds the `passport_visit` table for the POI Passport
     * feature (POI-keyed lifetime visit log).
     *
     * S274: the table is renamed to `poi_visit` in MIGRATION_3_4 as part of
     * the Katrina's Collection rebrand. The v2 → v3 step here still creates
     * the table under the old name so that any device upgrading from v2
     * passes through the historical schema before MIGRATION_3_4 renames it.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `passport_visit` (
                    `poi_id` TEXT NOT NULL,
                    `first_heard_at_ms` INTEGER NOT NULL,
                    `last_heard_at_ms` INTEGER NOT NULL,
                    `heard_count` INTEGER NOT NULL,
                    PRIMARY KEY(`poi_id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_passport_visit_last_heard_at_ms`
                    ON `passport_visit` (`last_heard_at_ms`)
                """.trimIndent()
            )
        }
    }

    /**
     * S274 — v3 → v4 renames `passport_visit` → `poi_visit` (and the
     * matching index) as part of the Katrina's Collection rebrand.
     *
     * Schema/row data is preserved verbatim — only the table name and index
     * name change. Operator decision: a visit is a fact about a POI, not
     * about a collection, so the POI-centric name is more honest.
     *
     * Only invoked on v3 → v4 upgrade path, so `passport_visit` is
     * guaranteed to exist. New installs land at v4 directly via Room's
     * generated CREATE TABLE for `poi_visit`, skipping this migration.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // SQLite has no IF EXISTS on ALTER TABLE RENAME, but the v3
            // schema guarantees passport_visit is present when this fires.
            db.execSQL("ALTER TABLE `passport_visit` RENAME TO `poi_visit`")
            // Index rename — SQLite ALTER INDEX is unavailable, so drop + recreate.
            db.execSQL("DROP INDEX IF EXISTS `index_passport_visit_last_heard_at_ms`")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_poi_visit_last_heard_at_ms`
                    ON `poi_visit` (`last_heard_at_ms`)
                """.trimIndent()
            )
        }
    }
}
