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
 * users would lose `poi_encounters` history (and now `passport_visit` history)
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
     * The CREATE TABLE / CREATE INDEX SQL MUST match Room's generated output
     * for the [PassportVisit] entity at v3 byte-for-byte, including column
     * ordering, NOT NULL, and index names. If Room's identity-hash check
     * disagrees at runtime, the DB fails to open. To regenerate the expected
     * SQL, build with `./gradlew :app-salem:kspDebugKotlin` and inspect the
     * v3 schema JSON under `app-salem/schemas/.../UserDataDatabase/3.json`.
     *
     * Currently `exportSchema = false` on UserDataDatabase so the JSON is not
     * written — the SQL below was derived by hand from [PassportVisit] and is
     * the canonical source of truth for this table's shape on existing
     * installs migrated from v2. Fresh installs use Room's generated CREATE.
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
}
