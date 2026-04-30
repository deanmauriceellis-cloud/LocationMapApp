/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * S203 — OMEN-004 phase-1 unit-test seed. First real Kotlin unit test in
 * :app-salem; closes the OMEN-004 deadline (2026-08-30) for "first test
 * exists and runs in CI".
 *
 * Covers the [NarrationGeofenceManager.setTourMode] contract:
 *   1. Default (post-construction) state is non-tour.
 *   2. setTourMode(true) activates tour mode.
 *   3. setTourMode(false) deactivates and resets the allow flags, even
 *      when allowHist/allowCivic are passed true (since the impl gates
 *      them on `active`).
 *   4. setTourMode(true, allowHist=true, allowCivic=true) propagates both flags.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class NarrationGeofenceManagerTourModeTest {

    private lateinit var manager: NarrationGeofenceManager

    @Before
    fun setUp() {
        val mockPrefs = mock(SharedPreferences::class.java)
        val mockContext = mock(Context::class.java)
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        manager = NarrationGeofenceManager(mockContext)
    }

    @Test
    fun `default state is non-tour`() {
        assertFalse("Manager should boot with tourMode=false", manager.isTourMode())
        assertFalse(manager.tourAllowsHistLandmarksForTest())
        assertFalse(manager.tourAllowsCivicForTest())
    }

    @Test
    fun `setTourMode true activates tour mode without allow flags`() {
        manager.setTourMode(active = true)
        assertTrue(manager.isTourMode())
        assertFalse(manager.tourAllowsHistLandmarksForTest())
        assertFalse(manager.tourAllowsCivicForTest())
    }

    @Test
    fun `setTourMode true with allow flags propagates them`() {
        manager.setTourMode(active = true, allowHistLandmarks = true, allowCivic = true)
        assertTrue(manager.isTourMode())
        assertTrue(manager.tourAllowsHistLandmarksForTest())
        assertTrue(manager.tourAllowsCivicForTest())
    }

    @Test
    fun `setTourMode false resets allow flags even when caller passes true`() {
        // Confirm the documented contract: when active=false, allow flags MUST
        // be false regardless of caller intent (impl gates them on `active`).
        manager.setTourMode(active = false, allowHistLandmarks = true, allowCivic = true)
        assertFalse(manager.isTourMode())
        assertFalse(manager.tourAllowsHistLandmarksForTest())
        assertFalse(manager.tourAllowsCivicForTest())
    }

    @Test
    fun `setTourMode false after active true clears all state`() {
        manager.setTourMode(active = true, allowHistLandmarks = true, allowCivic = true)
        manager.setTourMode(active = false)
        assertFalse(manager.isTourMode())
        assertFalse(manager.tourAllowsHistLandmarksForTest())
        assertFalse(manager.tourAllowsCivicForTest())
    }
}
