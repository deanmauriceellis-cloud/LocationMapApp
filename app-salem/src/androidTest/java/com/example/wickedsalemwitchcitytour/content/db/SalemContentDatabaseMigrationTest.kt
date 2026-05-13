/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.content.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S256 smoke gate for the bundled `salem_content.db` asset.
 *
 * Catches the recurring failure mode where a publish-chain rebake silently
 * leaves the asset on an old Room identity_hash, which triggers
 * `fallbackToDestructiveMigration` on first launch and wipes the asset.
 *
 * What this test asserts on the asset that ships in the AAB:
 *   1. Room opens it without falling back to destructive migration.
 *   2. `identity_hash` row in `room_master_table` matches the v19 hash that
 *      ships in `app-salem/schemas/.../19.json`.
 *   3. All 12 Room-managed tables exist and have non-zero rows
 *      (`tour_stops` is the one allowed zero — declared but populated
 *      lazily; everything else has authored content).
 *
 * Wire into CI before any v20 schema bump. If the asset diverges from
 * the JSON schema this test will be the first to scream — before the app
 * ships to Play and silently wipes its DB on user devices.
 *
 * Run:
 *   ./gradlew :app-salem:connectedDebugAndroidTest -PskipPublishChain
 */
@RunWith(AndroidJUnit4::class)
class SalemContentDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun bundledAssetMatchesV19IdentityHash() {
        val db = Room.databaseBuilder(context, SalemContentDatabase::class.java, "salem_content_test.db")
            .createFromAsset("salem_content.db")
            .build()
        try {
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            )
            cursor.use {
                assertTrue("room_master_table is empty — asset is not Room-managed", it.moveToFirst())
                val hash = it.getString(0)
                assertEquals(
                    "Asset identity_hash drifted — re-run publish chain " +
                        "(publish-salem-pois.js → publish-tours.js → publish-tour-legs.js → align-asset-schema-to-room.js)",
                    EXPECTED_IDENTITY_HASH_V19,
                    hash
                )
            }
        } finally {
            db.close()
            context.deleteDatabase("salem_content_test.db")
        }
    }

    @Test
    fun bundledAssetHasRequiredRowCounts() {
        val db = Room.databaseBuilder(context, SalemContentDatabase::class.java, "salem_content_test.db")
            .createFromAsset("salem_content.db")
            .build()
        try {
            val helper = db.openHelper.readableDatabase
            REQUIRED_TABLES.forEach { (table, minRows) ->
                val cursor = helper.query("SELECT COUNT(*) FROM $table")
                cursor.use {
                    assertTrue("$table: query returned no row", it.moveToFirst())
                    val n = it.getLong(0)
                    assertTrue(
                        "$table has $n rows, expected >= $minRows — publish chain incomplete or asset stale",
                        n >= minRows
                    )
                }
            }
        } finally {
            db.close()
            context.deleteDatabase("salem_content_test.db")
        }
    }

    private companion object {
        // Mirror of ROOM_IDENTITY_HASH_V19 in cache-proxy/scripts/verify-bundled-assets.js
        // and the value in app-salem/schemas/.../19.json. Bump these in lockstep when
        // @Database(version = N) changes.
        const val EXPECTED_IDENTITY_HASH_V19 = "745afa3eb4ce04bd7873671ea297b6e0"

        // Minimum row counts. `tour_stops` is allowed zero (declared but
        // populated lazily — current bundle has 0 rows, see ASSETS-MANIFEST.md).
        // Other floors track the canonical content baked in S224+ and trimmed
        // through S241/S253.
        val REQUIRED_TABLES = mapOf(
            "salem_pois" to 1500L,
            "historical_figures" to 40L,
            "tours" to 1L,
            "tour_legs" to 60L,
            "tour_stops" to 0L,
            "timeline_events" to 20L,
            "primary_sources" to 100L,
            "historical_facts" to 400L,
            "events_calendar" to 10L,
            "salem_witch_trials_articles" to 10L,
            "salem_witch_trials_newspapers" to 100L,
            "salem_witch_trials_npc_bios" to 40L
        )
    }
}
