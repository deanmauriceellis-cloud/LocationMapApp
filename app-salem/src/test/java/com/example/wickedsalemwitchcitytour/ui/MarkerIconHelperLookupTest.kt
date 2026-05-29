/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S304 tech-debt Phase 0d — characterization test pinning the category →
 * circle-icon asset-path resolution in [MarkerIconHelper.circleIconAssetPath].
 * Locks the S216 design decision (HISTORICAL_BUILDINGS and HISTORICAL_LANDMARKS
 * deliberately share tourism_history/historic_building) and the S301 worship
 * glyph path, so a later cleanup phase can't silently re-point them.
 *
 * Pure JVM lookup — no Context/Bitmap touched. (Loading the object initialises
 * the sibling CATEGORY_MAP, whose Color.parseColor calls return 0 under
 * testOptions.unitTests.returnDefaultValues — harmless, not under test here.)
 */
class MarkerIconHelperLookupTest {

    @Test fun historicalBuildings_resolvesToHistoricBuilding() {
        assertEquals(
            "tourism_history/historic_building",
            MarkerIconHelper.circleIconAssetPath("historical_buildings")
        )
    }

    @Test fun historicalLandmarks_shareHistoricBuildingIcon_S216() {
        // S216 split — HISTORICAL_LANDMARKS intentionally reuses the
        // HISTORICAL_BUILDINGS circle icon; only gating + label differ.
        assertEquals(
            MarkerIconHelper.circleIconAssetPath("historical_buildings"),
            MarkerIconHelper.circleIconAssetPath("historical_landmarks")
        )
        assertEquals(
            "tourism_history/historic_building",
            MarkerIconHelper.circleIconAssetPath("historical_landmarks")
        )
    }

    @Test fun worship_resolvesToPlaceOfWorshipGlyph() {
        assertEquals(
            "worship/place_of_worship",
            MarkerIconHelper.circleIconAssetPath("worship")
        )
        assertEquals(
            "worship/place_of_worship",
            MarkerIconHelper.circleIconAssetPath("place_of_worship")
        )
    }

    @Test fun lookupIsCaseInsensitive() {
        assertEquals(
            "tourism_history/historic_building",
            MarkerIconHelper.circleIconAssetPath("HISTORICAL_BUILDINGS")
        )
        assertEquals(
            "worship/place_of_worship",
            MarkerIconHelper.circleIconAssetPath("Worship")
        )
    }

    @Test fun unknownCategory_resolvesToNull() {
        assertNull(MarkerIconHelper.circleIconAssetPath("not_a_real_category_xyz"))
    }
}
