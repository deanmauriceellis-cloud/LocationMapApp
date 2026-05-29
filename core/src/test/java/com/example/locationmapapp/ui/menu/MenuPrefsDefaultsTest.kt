/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.locationmapapp.ui.menu

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * S304 tech-debt Phase 0d — characterization test pinning the S206 "mirror"
 * Layers-default contract in [MenuPrefs]. This is the exact behaviour S301
 * root-caused: a fresh paid install starting a tour mirrors the explore-mode
 * default (ON) for an unwritten tour-mode pref, so the full Historical-Buildings
 * layer shows in tour mode. The test locks current behaviour so later cleanup
 * phases can't silently change it.
 *
 * Pure JVM — the default helpers take plain args (+ an optional
 * SharedPreferences), so no Robolectric is needed; the prefs path is a Mockito
 * stub of the interface.
 */
class MenuPrefsDefaultsTest {

    // ── Pref-key selection by mode ─────────────────────────────────────────

    @Test fun histLandmarkKey_picksModeSpecificKey() {
        assertEquals(MenuPrefs.PREF_POI_HIST_LANDMARK, MenuPrefs.histLandmarkPrefKey(false))
        assertEquals(MenuPrefs.PREF_POI_HIST_LANDMARK_TOUR, MenuPrefs.histLandmarkPrefKey(true))
    }

    @Test fun civicKey_picksModeSpecificKey() {
        assertEquals(MenuPrefs.PREF_POI_CIVIC, MenuPrefs.civicPrefKey(false))
        assertEquals(MenuPrefs.PREF_POI_CIVIC_TOUR, MenuPrefs.civicPrefKey(true))
    }

    // ── Constant contract: explore defaults ON, tour defaults OFF ──────────

    @Test fun exploreDefaultsOn_tourDefaultsOff() {
        assertTrue(MenuPrefs.PREF_POI_HIST_LANDMARK_DEFAULT)
        assertTrue(MenuPrefs.PREF_POI_CIVIC_DEFAULT)
        assertFalse(MenuPrefs.PREF_POI_HIST_LANDMARK_TOUR_DEFAULT)
        assertFalse(MenuPrefs.PREF_POI_CIVIC_TOUR_DEFAULT)
    }

    // ── Explore mode: always the explore default (ON); prefs are ignored ───

    @Test fun histLandmark_exploreMode_isOn_ignoringPrefs() {
        assertTrue(MenuPrefs.histLandmarkPrefDefault(tourActive = false))
        // Even with prefs present, explore mode short-circuits to the const.
        assertTrue(
            MenuPrefs.histLandmarkPrefDefault(
                tourActive = false,
                prefs = Mockito.mock(SharedPreferences::class.java)
            )
        )
    }

    @Test fun civic_exploreMode_isOn() {
        assertTrue(MenuPrefs.civicPrefDefault(tourActive = false))
    }

    // ── Tour mode, NO prefs supplied: hardcoded TOUR default (OFF) ──────────

    @Test fun histLandmark_tourMode_noPrefs_isOff() {
        assertFalse(MenuPrefs.histLandmarkPrefDefault(tourActive = true, prefs = null))
    }

    @Test fun civic_tourMode_noPrefs_isOff() {
        assertFalse(MenuPrefs.civicPrefDefault(tourActive = true, prefs = null))
    }

    // ── Tour mode WITH prefs: the S206 mirror — the helper returns whatever
    //    the explore-mode pref reads back (default ON when never written). ───

    @Test fun histLandmark_tourMode_mirrorsExploreOn() {
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(
            prefs.getBoolean(MenuPrefs.PREF_POI_HIST_LANDMARK, MenuPrefs.PREF_POI_HIST_LANDMARK_DEFAULT)
        ).thenReturn(true)
        assertTrue(MenuPrefs.histLandmarkPrefDefault(tourActive = true, prefs = prefs))
    }

    @Test fun histLandmark_tourMode_mirrorsExploreOff() {
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(
            prefs.getBoolean(MenuPrefs.PREF_POI_HIST_LANDMARK, MenuPrefs.PREF_POI_HIST_LANDMARK_DEFAULT)
        ).thenReturn(false)
        assertFalse(MenuPrefs.histLandmarkPrefDefault(tourActive = true, prefs = prefs))
    }

    @Test fun civic_tourMode_mirrorsExploreOff() {
        val prefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(
            prefs.getBoolean(MenuPrefs.PREF_POI_CIVIC, MenuPrefs.PREF_POI_CIVIC_DEFAULT)
        ).thenReturn(false)
        assertFalse(MenuPrefs.civicPrefDefault(tourActive = true, prefs = prefs))
    }
}
