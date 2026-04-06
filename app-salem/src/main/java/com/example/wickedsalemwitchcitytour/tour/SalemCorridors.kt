/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

/**
 * Pre-defined street corridor geofences for historically significant Salem streets.
 * Each corridor triggers narration once per walk session when the user walks along it.
 *
 * Coordinates trace the street centerline within the downtown boundary.
 * Buffer = 20m each side (40m total corridor width, typical street width).
 *
 * Phase 9T Step 5: Street Corridor Geofences
 */
object SalemCorridors {

    fun all(): List<CorridorGeofence> = listOf(
        // Essex Street — Salem's main commercial artery, pedestrian mall at center
        CorridorGeofence(
            id = "corridor_essex_st",
            name = "Essex Street",
            narrationPointId = "corridor_essex_street",
            bufferM = 25.0,
            points = listOf(
                42.5225 to -70.8967,  // Essex at Washington
                42.5218 to -70.8935,  // Essex pedestrian mall west
                42.5212 to -70.8905,  // Essex at Central
                42.5207 to -70.8880,  // Essex at Hawthorne Blvd
                42.5200 to -70.8850,  // Essex east end
            )
        ),

        // Derby Street — waterfront, maritime history, House of Seven Gables
        CorridorGeofence(
            id = "corridor_derby_st",
            name = "Derby Street",
            narrationPointId = "corridor_derby_street",
            bufferM = 20.0,
            points = listOf(
                42.5195 to -70.8920,  // Derby at Congress
                42.5188 to -70.8890,  // Derby at Hawthorne Blvd
                42.5180 to -70.8855,  // Derby wharf area
                42.5175 to -70.8830,  // Derby at Turner
                42.5168 to -70.8800,  // Derby east (Seven Gables)
            )
        ),

        // Charter Street — oldest cemetery, atmospheric historic lane
        CorridorGeofence(
            id = "corridor_charter_st",
            name = "Charter Street",
            narrationPointId = "corridor_charter_street",
            bufferM = 15.0,
            points = listOf(
                42.5215 to -70.8912,  // Charter at Central
                42.5205 to -70.8905,  // Charter at Liberty
                42.5195 to -70.8898,  // Charter Street Cemetery
            )
        ),

        // Federal Street — Federal-period mansions, McIntire architecture
        CorridorGeofence(
            id = "corridor_federal_st",
            name = "Federal Street",
            narrationPointId = "corridor_federal_street",
            bufferM = 20.0,
            points = listOf(
                42.5240 to -70.8910,  // Federal at North
                42.5230 to -70.8900,  // Federal mid
                42.5220 to -70.8895,  // Federal at Essex
                42.5210 to -70.8888,  // Federal south
            )
        ),

        // Chestnut Street — most beautiful street in America (Samuel McIntire)
        CorridorGeofence(
            id = "corridor_chestnut_st",
            name = "Chestnut Street",
            narrationPointId = "corridor_chestnut_street",
            bufferM = 15.0,
            points = listOf(
                42.5230 to -70.8935,  // Chestnut at Cambridge
                42.5225 to -70.8915,  // Chestnut mid
                42.5220 to -70.8895,  // Chestnut at Federal
                42.5215 to -70.8880,  // Chestnut east
            )
        ),

        // Washington Street — connects train station to downtown, shops, witch attractions
        CorridorGeofence(
            id = "corridor_washington_st",
            name = "Washington Street",
            narrationPointId = "corridor_washington_street",
            bufferM = 20.0,
            points = listOf(
                42.5260 to -70.8980,  // Washington at Bridge
                42.5250 to -70.8975,  // Washington at Church
                42.5240 to -70.8970,  // Washington at Federal
                42.5225 to -70.8965,  // Washington at Essex
                42.5210 to -70.8955,  // Washington south
            )
        ),

        // Hawthorne Boulevard — connects Essex St to waterfront, divides downtown
        CorridorGeofence(
            id = "corridor_hawthorne_blvd",
            name = "Hawthorne Boulevard",
            narrationPointId = "corridor_hawthorne_blvd",
            bufferM = 25.0,
            points = listOf(
                42.5240 to -70.8880,  // Hawthorne at Common
                42.5225 to -70.8878,  // Hawthorne mid
                42.5210 to -70.8876,  // Hawthorne at Essex
                42.5195 to -70.8875,  // Hawthorne at Charter
                42.5185 to -70.8873,  // Hawthorne at Derby
            )
        ),

        // Liberty Street — witch-era jail site, Gallows Hill access
        CorridorGeofence(
            id = "corridor_liberty_st",
            name = "Liberty Street",
            narrationPointId = "corridor_liberty_street",
            bufferM = 20.0,
            points = listOf(
                42.5230 to -70.8930,  // Liberty at Federal
                42.5220 to -70.8920,  // Liberty mid
                42.5210 to -70.8910,  // Liberty at Charter
                42.5200 to -70.8905,  // Liberty south
            )
        ),

        // Turner Street — House of Seven Gables, Nathaniel Hawthorne's inspiration
        CorridorGeofence(
            id = "corridor_turner_st",
            name = "Turner Street",
            narrationPointId = "corridor_turner_street",
            bufferM = 15.0,
            points = listOf(
                42.5200 to -70.8835,  // Turner at Derby
                42.5190 to -70.8830,  // Turner mid
                42.5180 to -70.8825,  // Turner at Hardy
                42.5170 to -70.8815,  // Turner at House of Seven Gables
            )
        ),

        // Church Street — First Church, witch trial connections
        CorridorGeofence(
            id = "corridor_church_st",
            name = "Church Street",
            narrationPointId = "corridor_church_street",
            bufferM = 15.0,
            points = listOf(
                42.5255 to -70.8965,  // Church at Bridge
                42.5245 to -70.8960,  // Church mid
                42.5235 to -70.8955,  // Church at Essex
            )
        ),
    )
}
