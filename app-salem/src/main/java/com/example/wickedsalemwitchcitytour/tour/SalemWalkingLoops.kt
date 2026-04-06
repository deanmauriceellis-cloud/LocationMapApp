/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

/**
 * Suggested walking loops through downtown Salem.
 * These are suggestions only — narration points trigger regardless of route.
 * Each loop is a list of waypoints that trace a walking path through the city.
 *
 * Phase 9T Step 7: Suggested Walking Loops
 */
object SalemWalkingLoops {

    data class WalkingLoop(
        val id: String,
        val name: String,
        val description: String,
        val estimatedMinutes: Int,
        val distanceKm: Double,
        val narrationPointCount: Int,
        /** Ordered list of [lat, lng] waypoints tracing the loop */
        val waypoints: List<Pair<Double, Double>>
    )

    /** Quick Loop: ~30 min, Essex St core + Charter St Cemetery + waterfront return */
    val quickLoop = WalkingLoop(
        id = "loop_quick",
        name = "Quick Salem Stroll",
        description = "The essential Salem experience in 30 minutes. Walk the pedestrian mall, visit Charter Street Cemetery, and loop back along the waterfront.",
        estimatedMinutes = 30,
        distanceKm = 1.5,
        narrationPointCount = 25,
        waypoints = listOf(
            42.5216 to -70.8869,  // Start: NPS Visitor Center
            42.5220 to -70.8895,  // Walk up to Essex St
            42.5218 to -70.8935,  // Essex St pedestrian mall
            42.5212 to -70.8905,  // Essex at Central
            42.5205 to -70.8905,  // Down to Charter St
            42.5195 to -70.8898,  // Charter Street Cemetery
            42.5188 to -70.8890,  // Down to Derby St
            42.5195 to -70.8920,  // Derby St west
            42.5210 to -70.8880,  // Up Hawthorne Blvd
            42.5216 to -70.8869,  // Return to Visitor Center
        )
    )

    /** Standard Loop: ~60 min, adds McIntire District + Chestnut St + Common */
    val standardLoop = WalkingLoop(
        id = "loop_standard",
        name = "Salem Explorer Walk",
        description = "A thorough downtown tour covering witch sites, maritime history, beautiful architecture, and Salem Common. Pass 50+ narrated locations.",
        estimatedMinutes = 60,
        distanceKm = 3.0,
        narrationPointCount = 50,
        waypoints = listOf(
            42.5216 to -70.8869,  // Start: NPS Visitor Center
            42.5220 to -70.8895,  // Essex St
            42.5225 to -70.8967,  // Essex west (Witch House area)
            42.5240 to -70.8970,  // Up Washington St
            42.5250 to -70.8975,  // Witch Museum area
            42.5240 to -70.8880,  // Across to Hawthorne Blvd
            42.5230 to -70.8935,  // Chestnut Street
            42.5220 to -70.8895,  // Federal Street
            42.5215 to -70.8912,  // Charter Street
            42.5195 to -70.8898,  // Charter St Cemetery
            42.5188 to -70.8890,  // Derby Street
            42.5175 to -70.8830,  // Derby wharf / House of Seven Gables
            42.5195 to -70.8850,  // Back up to Essex east
            42.5216 to -70.8869,  // Return to Visitor Center
        )
    )

    /** Grand Loop: ~90+ min, covers the full bounded area comprehensively */
    val grandLoop = WalkingLoop(
        id = "loop_grand",
        name = "Grand Salem Experience",
        description = "The complete downtown Salem experience. Every major street, every historic district, the waterfront, and the Common. Pass 80+ narrated locations.",
        estimatedMinutes = 90,
        distanceKm = 5.0,
        narrationPointCount = 80,
        waypoints = listOf(
            42.5216 to -70.8869,  // Start: NPS Visitor Center
            42.5220 to -70.8895,  // Essex St east
            42.5225 to -70.8967,  // Essex St west (full pedestrian mall)
            42.5240 to -70.8980,  // Washington St north
            42.5260 to -70.8980,  // Bridge St area
            42.5255 to -70.8965,  // Church Street
            42.5250 to -70.8975,  // Salem Witch Museum
            42.5240 to -70.8880,  // Salem Common / Hawthorne Blvd
            42.5230 to -70.8935,  // Chestnut Street (most beautiful street)
            42.5240 to -70.8910,  // Federal Street north
            42.5220 to -70.8895,  // Federal at Essex
            42.5230 to -70.8930,  // Liberty Street
            42.5215 to -70.8912,  // Charter Street
            42.5195 to -70.8898,  // Charter St Cemetery
            42.5188 to -70.8890,  // Derby Street west
            42.5175 to -70.8830,  // House of Seven Gables
            42.5170 to -70.8815,  // Turner Street
            42.5180 to -70.8855,  // Derby Wharf
            42.5195 to -70.8920,  // Congress St back up
            42.5210 to -70.8880,  // Hawthorne Blvd south
            42.5216 to -70.8869,  // Return to Visitor Center
        )
    )

    fun all(): List<WalkingLoop> = listOf(quickLoop, standardLoop, grandLoop)
}
