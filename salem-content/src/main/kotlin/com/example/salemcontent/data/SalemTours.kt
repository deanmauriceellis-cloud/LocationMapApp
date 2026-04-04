/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.data

import com.example.salemcontent.pipeline.OutputTour
import com.example.salemcontent.pipeline.OutputTourStop
import com.example.salemcontent.pipeline.Provenance

/**
 * Three custom circular walking tours of Salem — Phase 5 continuation.
 *
 * Each tour is a LOOP designed to:
 * - Start and end at the same point (NPS Visitor Center canonical start;
 *   the app's TourEngine rotates to the user's nearest stop at runtime)
 * - Minimize retracing the same streets
 * - Include TTS-optimized transition narrations between every stop
 * - Carry geofence-ready coordinates via referenced TourPoi entries
 *
 * Tour 1: Salem Essentials — compact 14-stop downtown loop (~90 min)
 * Tour 2: Salem Explorer   — extended 20-stop figure-8 loop (~2 hr 15 min)
 * Tour 3: Grand Salem Tour — comprehensive 26-stop city-wide loop (~3.5 hr)
 */
object SalemTours {

    private val now = System.currentTimeMillis()
    private val curated = Provenance("manual_curated", 1.0f, "2026-04-03", now, now, 0L)

    fun allTours(): List<OutputTour> = listOf(essentialsTour, explorerTour, grandTour)

    fun allStops(): List<OutputTourStop> = essentialsStops + explorerStops + grandStops

    // ═══════════════════════════════════════════════════════════════════
    // Tour 1: Salem Essentials — 14 stops, ~90 min, ~2.2 km, easy
    // Counter-clockwise: NPS → west on Essex → north to Common →
    //   south through trial sites → east to waterfront → return
    // ═══════════════════════════════════════════════════════════════════

    private val essentialsTour = OutputTour(
        id = "tour_essentials",
        name = "Salem Essentials",
        theme = "essential_highlights",
        description = "The essential Salem experience in a compact downtown loop. " +
            "Visit the must-see witch trials sites, world-class museums, and literary " +
            "landmarks in under ninety minutes. Perfect for day-trippers and first-time visitors.",
        estimatedMinutes = 90,
        distanceKm = 2.2f,
        stopCount = 14,
        difficulty = "easy",
        sortOrder = 1,
        provenance = curated
    )

    private val essentialsStops = listOf(
        // 1. Start — orientation
        OutputTourStop(
            tourId = "tour_essentials", poiId = "nps_visitor_center", stopOrder = 1,
            transitionNarration = "Welcome to the Salem Essentials tour. We will loop through " +
                "downtown Salem, visiting the most important witch trials sites, museums, and " +
                "literary landmarks. Pick up a free map inside the visitor center, then head " +
                "west toward Essex Street.",
            walkingMinutesFromPrev = 0, distanceMFromPrev = 0,
            provenance = curated
        ),
        // 2. West on Essex — world-class museum
        OutputTourStop(
            tourId = "tour_essentials", poiId = "peabody_essex_museum", stopOrder = 2,
            transitionNarration = "Walk west along New Liberty Street to Essex Street. The " +
                "grand museum entrance is on your left. Even if you do not go inside today, " +
                "note the striking modern wing designed by architect Moshe Safdie.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 190,
            provenance = curated
        ),
        // 3. Continue west — Hocus Pocus house
        OutputTourStop(
            tourId = "tour_essentials", poiId = "ropes_mansion", stopOrder = 3,
            transitionNarration = "Continue west along Essex Street through the pedestrian " +
                "mall. You are passing Salem's witch shop district. Crow Haven Corner, the " +
                "oldest witch shop in Salem, is on your right. The Ropes Mansion ahead dates " +
                "to 1727. Film fans will recognize it as the Allison house from Hocus Pocus.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 150,
            provenance = curated
        ),
        // 4. South to Witch House — only 1692 structure
        OutputTourStop(
            tourId = "tour_essentials", poiId = "witch_house", stopOrder = 4,
            transitionNarration = "Walk south from Essex Street. The Witch House ahead is the " +
                "only structure still standing in Salem with direct ties to the 1692 witch trials. " +
                "Judge Jonathan Corwin lived here and conducted examinations of accused witches " +
                "inside these walls.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        ),
        // 5. North to Witch Dungeon — live reenactment
        OutputTourStop(
            tourId = "tour_essentials", poiId = "witch_dungeon_museum", stopOrder = 5,
            transitionNarration = "Walk north, back to Essex Street and continue to Lynde " +
                "Street. The Witch Dungeon Museum features live reenactments of a 1692 trial " +
                "performed by professional actors using actual court transcripts.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 280,
            provenance = curated
        ),
        // 6. West to Salem Common
        OutputTourStop(
            tourId = "tour_essentials", poiId = "salem_common", stopOrder = 6,
            transitionNarration = "Walk west toward Salem Common. This nine-acre green has " +
                "served as public land since the sixteen thirties, first as militia training " +
                "grounds and grazing pasture, now as the civic heart of the city.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        ),
        // 7. South edge — Hawthorne Hotel
        OutputTourStop(
            tourId = "tour_essentials", poiId = "hawthorne_hotel", stopOrder = 7,
            transitionNarration = "Walk south along the western edge of Salem Common. The " +
                "grand Federal-style building ahead is the Hawthorne Hotel, built in 1925. " +
                "Staff and guests have reported ghost sightings for decades, making it " +
                "reputedly one of America's most haunted hotels.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 80,
            provenance = curated
        ),
        // 8. Salem Witch Museum
        OutputTourStop(
            tourId = "tour_essentials", poiId = "salem_witch_museum", stopOrder = 8,
            transitionNarration = "Continue south toward the Gothic turrets at the southern " +
                "edge of Washington Square. The Salem Witch Museum is housed in a former " +
                "church built in 1846. Thirteen life-size stage sets inside retell the " +
                "terror of 1692.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        ),
        // 9. Roger Conant — founder photo op
        OutputTourStop(
            tourId = "tour_essentials", poiId = "roger_conant_statue", stopOrder = 9,
            transitionNarration = "Cross to the southeast corner of Washington Square. The " +
                "cloaked figure ahead is Roger Conant, who founded Salem in 1626. Visitors " +
                "often mistake his Puritan cloak for a witch's costume. It is one of " +
                "Salem's most photographed landmarks.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 120,
            provenance = curated
        ),
        // 10. South — Memorial
        OutputTourStop(
            tourId = "tour_essentials", poiId = "witch_trials_memorial", stopOrder = 10,
            transitionNarration = "Walk south on Liberty Street. You are heading toward " +
                "the Witch Trials Memorial. Nobel Laureate Elie Wiesel dedicated this " +
                "space in 1992. Twenty stone benches bear the names and execution dates " +
                "of the victims. Their protests of innocence are inscribed on the walls " +
                "but cut off mid-sentence by the stone.",
            walkingMinutesFromPrev = 5, distanceMFromPrev = 380,
            provenance = curated
        ),
        // 11. Adjacent — oldest cemetery
        OutputTourStop(
            tourId = "tour_essentials", poiId = "charter_street_cemetery", stopOrder = 11,
            transitionNarration = "The cemetery is directly adjacent to the memorial. " +
                "Charter Street Cemetery is one of the oldest burying grounds in " +
                "the United States, established in 1637. Look for the ornate winged " +
                "skulls carved on the ancient headstones. Judge Hathorne, the most " +
                "zealous witch trial magistrate, is buried here.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 70,
            provenance = curated
        ),
        // 12. East to waterfront — Custom House
        OutputTourStop(
            tourId = "tour_essentials", poiId = "custom_house", stopOrder = 12,
            transitionNarration = "Walk east on Charter Street toward the harbor. You are " +
                "crossing into Salem's waterfront district. The Federal-era Custom House " +
                "ahead is where Nathaniel Hawthorne worked as surveyor. He claimed to have " +
                "found the actual scarlet letter in this building's attic.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 300,
            provenance = curated
        ),
        // 13. Northeast — Seven Gables
        OutputTourStop(
            tourId = "tour_essentials", poiId = "house_seven_gables", stopOrder = 13,
            transitionNarration = "Walk north along the waterfront. The House of the Seven " +
                "Gables campus is ahead on Derby Street. Built in 1668, it is the oldest " +
                "surviving timber-frame mansion in New England and the inspiration for " +
                "Hawthorne's famous novel. The campus also houses his birthplace.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 160,
            provenance = curated
        ),
        // 14. Return west — back to start
        OutputTourStop(
            tourId = "tour_essentials", poiId = "hawthorne_statue", stopOrder = 14,
            transitionNarration = "Walk west along Derby Street back toward downtown. " +
                "The seated bronze figure of Nathaniel Hawthorne on Hawthorne Boulevard " +
                "marks the end of your loop. The NPS Visitor Center is just a short walk " +
                "north from here. You have completed the Salem Essentials tour.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 280,
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Tour 2: Salem Explorer — 20 stops, ~2 hr 15 min, ~3.3 km, moderate
    // Figure-8: NPS → east to waterfront → south through trial sites →
    //   northwest to Common → east on Essex → return
    // ═══════════════════════════════════════════════════════════════════

    private val explorerTour = OutputTour(
        id = "tour_explorer",
        name = "Salem Explorer",
        theme = "extended_exploration",
        description = "An extended walking tour through Salem's historic heart. Beyond the " +
            "must-see sites, discover literary landmarks, browse witch shops on Essex Street, " +
            "and pass by Salem's best restaurants and cafes along the way. Perfect for visitors " +
            "with a couple of hours to explore.",
        estimatedMinutes = 135,
        distanceKm = 3.3f,
        stopCount = 20,
        difficulty = "moderate",
        sortOrder = 2,
        provenance = curated
    )

    private val explorerStops = listOf(
        // 1. Start
        OutputTourStop(
            tourId = "tour_explorer", poiId = "nps_visitor_center", stopOrder = 1,
            transitionNarration = "Welcome to the Salem Explorer tour. Over the next two " +
                "hours, we will walk through three centuries of history, from the waterfront " +
                "where Salem's merchant ships once docked to the sites of the 1692 witch " +
                "trials. We begin by heading east toward the harbor.",
            walkingMinutesFromPrev = 0, distanceMFromPrev = 0,
            provenance = curated
        ),
        // 2. East — PEM
        OutputTourStop(
            tourId = "tour_explorer", poiId = "peabody_essex_museum", stopOrder = 2,
            transitionNarration = "Walk west along New Liberty Street to Essex Street. The " +
                "Peabody Essex Museum houses over 1.8 million works spanning art, maritime " +
                "history, and global culture. Its roots go back to the East India Marine " +
                "Society, founded by Salem sea captains in 1799.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 190,
            provenance = curated
        ),
        // 3. Literary — Hawthorne
        OutputTourStop(
            tourId = "tour_explorer", poiId = "hawthorne_statue", stopOrder = 3,
            transitionNarration = "Just south on Hawthorne Boulevard, the seated bronze " +
                "figure of Nathaniel Hawthorne gazes toward the harbor. He was born in " +
                "Salem in 1804 and immortalized the city in The Scarlet Letter and " +
                "The House of the Seven Gables.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 100,
            provenance = curated
        ),
        // 4. Pirate Museum
        OutputTourStop(
            tourId = "tour_explorer", poiId = "new_england_pirate_museum", stopOrder = 4,
            transitionNarration = "Walk south on Hawthorne Boulevard toward Derby Street. " +
                "Salem's maritime wealth attracted pirates as well as merchants. The Pirate " +
                "Museum on your left explores the Golden Age of Piracy and Salem's connections " +
                "to buccaneering.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 180,
            provenance = curated
        ),
        // 5. Maritime NHP
        OutputTourStop(
            tourId = "tour_explorer", poiId = "salem_maritime_nhp", stopOrder = 5,
            transitionNarration = "Continue east along Derby Street. You are entering the " +
                "Salem Maritime National Historical Park, the first National Historic Site " +
                "in the United States, established in 1938. Ships from Salem once sailed " +
                "to China, the East Indies, and Africa.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 230,
            provenance = curated
        ),
        // 6. Custom House
        OutputTourStop(
            tourId = "tour_explorer", poiId = "custom_house", stopOrder = 6,
            transitionNarration = "The Custom House is steps away. Nathaniel Hawthorne " +
                "worked here as surveyor from 1846 to 1849. He claimed to have found " +
                "the scarlet letter that inspired his famous novel in this building's attic.",
            walkingMinutesFromPrev = 0, distanceMFromPrev = 30,
            provenance = curated
        ),
        // 7. Derby Wharf
        OutputTourStop(
            tourId = "tour_explorer", poiId = "derby_wharf", stopOrder = 7,
            transitionNarration = "Walk southeast toward the water. Derby Wharf stretches " +
                "nearly half a mile into Salem Harbor. Built by the merchant Elias Hasket " +
                "Derby, once the richest man in America. The replica tall ship Friendship " +
                "is often moored here. Walk out as far as you like.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 160,
            provenance = curated
        ),
        // 8. Seven Gables
        OutputTourStop(
            tourId = "tour_explorer", poiId = "house_seven_gables", stopOrder = 8,
            transitionNarration = "Walk north along the waterfront path. The House of the " +
                "Seven Gables campus is ahead. Built in 1668, it is the oldest surviving " +
                "timber-frame mansion in New England. Hawthorne's cousin Susanna Ingersoll " +
                "lived here, and her stories inspired his famous novel.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 120,
            provenance = curated
        ),
        // 9. Hawthorne Birthplace (same campus)
        OutputTourStop(
            tourId = "tour_explorer", poiId = "hawthorne_birthplace", stopOrder = 9,
            transitionNarration = "Hawthorne's actual birthplace sits on the same campus. " +
                "He was born here on July 4, 1804. The house was originally on Union " +
                "Street and was moved to this site in 1958.",
            walkingMinutesFromPrev = 0, distanceMFromPrev = 0,
            provenance = curated
        ),
        // 10. West to Witch House
        OutputTourStop(
            tourId = "tour_explorer", poiId = "witch_house", stopOrder = 10,
            transitionNarration = "Walk west along Derby Street, then turn north toward " +
                "Essex Street. Along the way you will pass Jaho Coffee and Tea on Derby " +
                "Street, an excellent spot for specialty coffee. Continue north to the " +
                "Witch House, the only surviving building with direct ties to the 1692 trials.",
            walkingMinutesFromPrev = 7, distanceMFromPrev = 510,
            provenance = curated
        ),
        // 11. South — Jail site
        OutputTourStop(
            tourId = "tour_explorer", poiId = "salem_jail_site", stopOrder = 11,
            transitionNarration = "Walk south to Federal Street. Near this intersection " +
                "stood the old Salem jail where accused witches were held in horrifying " +
                "conditions. Several of the accused died in custody before ever reaching trial. " +
                "Sarah Osborne and Ann Foster perished here.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 310,
            provenance = curated
        ),
        // 12. Court House
        OutputTourStop(
            tourId = "tour_explorer", poiId = "court_house_site", stopOrder = 12,
            transitionNarration = "Continue south on Washington Street. Near here, the " +
                "Court of Oyer and Terminer convened in 1692. Chief Justice William " +
                "Stoughton presided over the proceedings that condemned nineteen people " +
                "to the gallows. The original building is long gone, but a marker " +
                "commemorates the site.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 190,
            provenance = curated
        ),
        // 13. Cemetery
        OutputTourStop(
            tourId = "tour_explorer", poiId = "charter_street_cemetery", stopOrder = 13,
            transitionNarration = "Walk east on Charter Street. On your right is one of the " +
                "oldest burying grounds in the United States, established in 1637. " +
                "Look for the ornate winged skulls carved on the ancient slate headstones. " +
                "Judge Hathorne, ancestor of Nathaniel Hawthorne, is buried here.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        ),
        // 14. Memorial
        OutputTourStop(
            tourId = "tour_explorer", poiId = "witch_trials_memorial", stopOrder = 14,
            transitionNarration = "The memorial is directly adjacent to the cemetery. " +
                "Twenty granite benches, one for each victim, line the quiet stone " +
                "walls. Their cries of innocence are inscribed but cut off mid-sentence " +
                "by the walls. Take a moment of reflection here.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 70,
            provenance = curated
        ),
        // 15. Northwest — Witch Museum
        OutputTourStop(
            tourId = "tour_explorer", poiId = "salem_witch_museum", stopOrder = 15,
            transitionNarration = "Walk north on Liberty Street to Washington Square. " +
                "The former Gothic Revival church ahead with its pointed turrets houses " +
                "the Salem Witch Museum. Thirteen life-size stage sets retell the " +
                "terror of 1692 through light and narration.",
            walkingMinutesFromPrev = 5, distanceMFromPrev = 380,
            provenance = curated
        ),
        // 16. Roger Conant
        OutputTourStop(
            tourId = "tour_explorer", poiId = "roger_conant_statue", stopOrder = 16,
            transitionNarration = "Cross to the southeast corner of Salem Common. Roger " +
                "Conant, Salem's founder in 1626, stands in his Puritan cloak. It is " +
                "so often mistaken for a witch's costume that it has become a running joke.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 120,
            provenance = curated
        ),
        // 17. Hawthorne Hotel
        OutputTourStop(
            tourId = "tour_explorer", poiId = "hawthorne_hotel", stopOrder = 17,
            transitionNarration = "Walk north along the edge of Salem Common to the " +
                "Hawthorne Hotel. Built in 1925, it is reputedly one of the most " +
                "haunted hotels in America. Even non-guests can walk through the " +
                "elegant lobby and admire the architecture.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 230,
            provenance = curated
        ),
        // 18. Salem Common
        OutputTourStop(
            tourId = "tour_explorer", poiId = "salem_common", stopOrder = 18,
            transitionNarration = "Step onto Salem Common. This nine-acre green has " +
                "served Salem since the sixteen thirties, first as grazing land, then " +
                "as militia training ground, now as the peaceful civic heart of the city.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 80,
            provenance = curated
        ),
        // 19. East — Ropes Mansion
        OutputTourStop(
            tourId = "tour_explorer", poiId = "ropes_mansion", stopOrder = 19,
            transitionNarration = "Walk east along Essex Street, back into the pedestrian " +
                "mall. The Ropes Mansion on your left dates to 1727. Its formal gardens " +
                "are free to visit. Hocus Pocus fans, this is the Allison house. As you " +
                "continue east, you will pass Gulu-Gulu Cafe, a beloved bohemian hangout " +
                "with coffee by day and cocktails by night.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 340,
            provenance = curated
        ),
        // 20. Witch Dungeon — final stop before return
        OutputTourStop(
            tourId = "tour_explorer", poiId = "witch_dungeon_museum", stopOrder = 20,
            transitionNarration = "Continue east on Essex Street past the witch shops. " +
                "Crow Haven Corner and HEX Old World Witchery are along this stretch. " +
                "The Witch Dungeon Museum on Lynde Street features live reenactments " +
                "using actual 1692 court transcripts. The NPS Visitor Center is just " +
                "a short walk east from here. Your Salem Explorer tour is complete.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Tour 3: Grand Salem Tour — 26 stops, ~3.5 hr, ~5.2 km, challenging
    // Clockwise: NPS → east to waterfront → south through trial core →
    //   west to Proctor's Ledge → north via MBTA → Common → east on
    //   Essex → return
    // ═══════════════════════════════════════════════════════════════════

    private val grandTour = OutputTour(
        id = "tour_grand",
        name = "Grand Salem Tour",
        theme = "comprehensive",
        description = "The definitive Salem experience. From the waterfront wharves where " +
            "merchant ships once docked, through the heart of the witch trials, to the " +
            "confirmed execution site at Proctor's Ledge and back. This comprehensive " +
            "tour covers every major historic site, museum, and landmark in Salem. " +
            "Bring comfortable shoes and water.",
        estimatedMinutes = 210,
        distanceKm = 5.2f,
        stopCount = 26,
        difficulty = "challenging",
        sortOrder = 3,
        provenance = curated
    )

    private val grandStops = listOf(
        // ── Leg 1: East / Waterfront ──────────────────────────────────
        // 1. Start
        OutputTourStop(
            tourId = "tour_grand", poiId = "nps_visitor_center", stopOrder = 1,
            transitionNarration = "Welcome to the Grand Salem Tour, the most comprehensive " +
                "walking tour of Salem's historic sites. Over the next three and a half hours, " +
                "you will visit every major landmark, from the waterfront wharves to Proctor's " +
                "Ledge, the confirmed site where nineteen innocent people were hanged in 1692. " +
                "Let us begin by heading toward the harbor.",
            walkingMinutesFromPrev = 0, distanceMFromPrev = 0,
            provenance = curated
        ),
        // 2. Seven Gables
        OutputTourStop(
            tourId = "tour_grand", poiId = "house_seven_gables", stopOrder = 2,
            transitionNarration = "Walk east along Derby Street toward the harbor. You are " +
                "heading into one of Salem's oldest neighborhoods. The House of the Seven " +
                "Gables campus is ahead at the eastern end of Derby Street, perched on the " +
                "waterfront. Built in 1668, it is the oldest surviving timber-frame mansion " +
                "in New England.",
            walkingMinutesFromPrev = 5, distanceMFromPrev = 420,
            provenance = curated
        ),
        // 3. Hawthorne Birthplace (same campus)
        OutputTourStop(
            tourId = "tour_grand", poiId = "hawthorne_birthplace", stopOrder = 3,
            transitionNarration = "Nathaniel Hawthorne's birthplace is on the same campus. " +
                "He was born here on July 4, 1804. The house was originally on Union Street " +
                "and moved to this site in 1958.",
            walkingMinutesFromPrev = 0, distanceMFromPrev = 0,
            provenance = curated
        ),
        // 4. Ferry Terminal
        OutputTourStop(
            tourId = "tour_grand", poiId = "salem_ferry_terminal", stopOrder = 4,
            transitionNarration = "Walk south along the waterfront. The Salem Ferry terminal " +
                "is ahead. In season, a high-speed catamaran connects Salem to Boston's Long " +
                "Wharf in about fifty minutes. The scenic harbor crossing is an attraction " +
                "in itself.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 340,
            provenance = curated
        ),
        // 5. Derby Wharf
        OutputTourStop(
            tourId = "tour_grand", poiId = "derby_wharf", stopOrder = 5,
            transitionNarration = "Walk west along the waterfront path. Derby Wharf stretches " +
                "half a mile into the harbor. Built by Elias Hasket Derby, once the richest " +
                "man in America, this wharf was the center of Salem's global trading empire. " +
                "The Derby Wharf Light Station stands at the far end.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 340,
            provenance = curated
        ),
        // 6. Custom House
        OutputTourStop(
            tourId = "tour_grand", poiId = "custom_house", stopOrder = 6,
            transitionNarration = "Walk to the base of the wharf. The Custom House stands " +
                "before you. Hawthorne worked here as surveyor and claimed to have found " +
                "the original scarlet letter in its upper stories.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 160,
            provenance = curated
        ),
        // 7. Salem Maritime NHP
        OutputTourStop(
            tourId = "tour_grand", poiId = "salem_maritime_nhp", stopOrder = 7,
            transitionNarration = "The Salem Maritime National Historical Park surrounds " +
                "you. Established in 1938, it was the first National Historic Site in the " +
                "United States. The park encompasses twelve historic structures along this " +
                "stretch of waterfront.",
            walkingMinutesFromPrev = 0, distanceMFromPrev = 30,
            provenance = curated
        ),
        // 8. Pirate Museum
        OutputTourStop(
            tourId = "tour_grand", poiId = "new_england_pirate_museum", stopOrder = 8,
            transitionNarration = "Walk west along Derby Street. You will pass several " +
                "restaurants along the waterfront. Sea Level Oyster Bar and Finz Seafood " +
                "are at Pickering Wharf to your left, excellent choices for a meal after " +
                "the tour. The Pirate Museum is ahead on your right.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 250,
            provenance = curated
        ),
        // ── Leg 2: South / Witch Trials Core ─────────────────────────
        // 9. Witch House
        OutputTourStop(
            tourId = "tour_grand", poiId = "witch_house", stopOrder = 9,
            transitionNarration = "Turn north and walk up Turner Street to Essex Street. " +
                "The Witch House is ahead. Judge Jonathan Corwin lived here and likely " +
                "conducted preliminary examinations of accused witches in this very building. " +
                "It is the only structure still standing with direct ties to 1692.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 230,
            provenance = curated
        ),
        // 10. Jail Site
        OutputTourStop(
            tourId = "tour_grand", poiId = "salem_jail_site", stopOrder = 10,
            transitionNarration = "Walk west along Essex Street, then south on North Street " +
                "to Federal Street. Near here stood the old Salem jail. Conditions were " +
                "appalling. Accused witches were chained in dark, filthy cells. Sarah " +
                "Osborne and Ann Foster died in custody before ever reaching trial.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 310,
            provenance = curated
        ),
        // 11. Court House
        OutputTourStop(
            tourId = "tour_grand", poiId = "court_house_site", stopOrder = 11,
            transitionNarration = "Continue south on Washington Street. The Court of Oyer " +
                "and Terminer met near this location. Here, Chief Justice William Stoughton " +
                "presided over the proceedings that sent nineteen people to the gallows and " +
                "pressed one man to death.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 190,
            provenance = curated
        ),
        // 12. Cemetery
        OutputTourStop(
            tourId = "tour_grand", poiId = "charter_street_cemetery", stopOrder = 12,
            transitionNarration = "Turn left onto Charter Street. The cemetery ahead dates " +
                "to 1637. Among its weathered headstones you will find the grave of Judge " +
                "John Hathorne, the most zealous of the witch trial magistrates. His " +
                "descendant Nathaniel added a W to the family name, reportedly to distance " +
                "himself from the shame.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        ),
        // 13. Memorial
        OutputTourStop(
            tourId = "tour_grand", poiId = "witch_trials_memorial", stopOrder = 13,
            transitionNarration = "The Witch Trials Memorial is directly adjacent. Nobel " +
                "Laureate Elie Wiesel dedicated this space in 1992. Twenty stone benches " +
                "bear the names, execution methods, and dates of each victim. Take a " +
                "moment of reflection.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 70,
            provenance = curated
        ),
        // 14. Witch Museum
        OutputTourStop(
            tourId = "tour_grand", poiId = "salem_witch_museum", stopOrder = 14,
            transitionNarration = "Walk north on Liberty Street toward Washington Square. " +
                "You are climbing gently uphill toward Salem Common. The Gothic turrets of " +
                "the Salem Witch Museum rise ahead. This is one of Salem's most visited " +
                "sites, with thirteen dramatic stage sets telling the story of 1692.",
            walkingMinutesFromPrev = 5, distanceMFromPrev = 380,
            provenance = curated
        ),
        // 15. Roger Conant
        OutputTourStop(
            tourId = "tour_grand", poiId = "roger_conant_statue", stopOrder = 15,
            transitionNarration = "Cross to the southeast corner of Salem Common. The " +
                "cloaked figure of Roger Conant, who founded Salem in 1626, is one of " +
                "the most photographed landmarks in the city.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 120,
            provenance = curated
        ),
        // ── Leg 3: West Excursion to Proctor's Ledge ─────────────────
        // 16. Chestnut Street
        OutputTourStop(
            tourId = "tour_grand", poiId = "chestnut_street", stopOrder = 16,
            transitionNarration = "Walk south toward Chestnut Street. You are entering one " +
                "of the most beautiful streets in America. Grand Federal-style mansions line " +
                "both sides, built by Salem's sea captains when the city was one of the " +
                "wealthiest ports in the nation. Architect Samuel McIntire designed many " +
                "of these homes.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        ),
        // 17. Proctor's Ledge — the hanging site
        OutputTourStop(
            tourId = "tour_grand", poiId = "proctors_ledge", stopOrder = 17,
            transitionNarration = "Continue west along Chestnut Street, then turn north " +
                "on Flint Street and west on Pope Street. You are walking to Proctor's " +
                "Ledge, the confirmed execution site of the Salem witch trials. For " +
                "centuries, the exact location was debated. In 2016, a research team " +
                "conclusively identified this rocky ledge. Bridget Bishop was the first, " +
                "hanged here on June tenth, 1692. Eighteen more followed over the next " +
                "four months.",
            walkingMinutesFromPrev = 7, distanceMFromPrev = 510,
            provenance = curated
        ),
        // ── Leg 4: Return via North ──────────────────────────────────
        // 18. MBTA Station
        OutputTourStop(
            tourId = "tour_grand", poiId = "salem_mbta_station", stopOrder = 18,
            transitionNarration = "Return east on Pope Street, then walk north on Margin " +
                "Street to Bridge Street. The MBTA commuter rail station is ahead. This " +
                "is where the train from Boston arrives, making Salem an easy day trip. " +
                "We are now looping back toward the center of the city.",
            walkingMinutesFromPrev = 6, distanceMFromPrev = 440,
            provenance = curated
        ),
        // 19. Salem Common
        OutputTourStop(
            tourId = "tour_grand", poiId = "salem_common", stopOrder = 19,
            transitionNarration = "Walk south on Washington Street toward Salem Common. " +
                "This nine-acre park has served as public green since the sixteen thirties. " +
                "The grand homes lining the square were built during Salem's maritime " +
                "golden age.",
            walkingMinutesFromPrev = 5, distanceMFromPrev = 350,
            provenance = curated
        ),
        // 20. Hawthorne Hotel
        OutputTourStop(
            tourId = "tour_grand", poiId = "hawthorne_hotel", stopOrder = 20,
            transitionNarration = "The Hawthorne Hotel stands on the west side of Salem " +
                "Common. Built in 1925, it has hosted presidents and celebrities. Staff " +
                "and guests report numerous ghost sightings, particularly on the sixth floor.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 80,
            provenance = curated
        ),
        // 21. Witch Dungeon
        OutputTourStop(
            tourId = "tour_grand", poiId = "witch_dungeon_museum", stopOrder = 21,
            transitionNarration = "Walk east along the north side of the common, then turn " +
                "south on Lynde Street. The Witch Dungeon Museum features live reenactments " +
                "of a 1692 trial, performed by professional actors using actual court transcripts.",
            walkingMinutesFromPrev = 4, distanceMFromPrev = 310,
            provenance = curated
        ),
        // 22. Ropes Mansion
        OutputTourStop(
            tourId = "tour_grand", poiId = "ropes_mansion", stopOrder = 22,
            transitionNarration = "Walk south to Essex Street and turn left. The Ropes " +
                "Mansion on your right dates to 1727. Its formal gardens are free to visit. " +
                "Film fans will recognize it as the Allison house from the 1993 movie " +
                "Hocus Pocus.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 190,
            provenance = curated
        ),
        // 23. Hawthorne Statue
        OutputTourStop(
            tourId = "tour_grand", poiId = "hawthorne_statue", stopOrder = 23,
            transitionNarration = "Continue east on Essex Street. You are walking through " +
                "Salem's witch shop district. Crow Haven Corner, HEX Old World Witchery, " +
                "and Omen are all along this block. The seated statue of Hawthorne is " +
                "just ahead.",
            walkingMinutesFromPrev = 2, distanceMFromPrev = 120,
            provenance = curated
        ),
        // 24. PEM
        OutputTourStop(
            tourId = "tour_grand", poiId = "peabody_essex_museum", stopOrder = 24,
            transitionNarration = "The Peabody Essex Museum is on your left. If you have " +
                "time, the museum is worth several hours. Highlights include Yin Yu Tang, " +
                "a two hundred year old Chinese house transported and reassembled inside " +
                "the museum.",
            walkingMinutesFromPrev = 1, distanceMFromPrev = 100,
            provenance = curated
        ),
        // 25. Salem Maritime (pass through on return)
        OutputTourStop(
            tourId = "tour_grand", poiId = "salem_jail_site", stopOrder = 25,
            transitionNarration = "Walk south on Washington Street. You are passing through " +
                "the commercial heart of Salem. Rockafellas gastropub and Flying Saucer " +
                "Pizza are along this stretch if you are ready for a well-deserved meal. " +
                "Turner's Seafood in the historic Lyceum Hall, where Alexander Graham Bell " +
                "first publicly demonstrated the telephone in 1877, is nearby on Church Street.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 200,
            provenance = curated
        ),
        // 26. Return — back to NPS
        OutputTourStop(
            tourId = "tour_grand", poiId = "nps_visitor_center", stopOrder = 26,
            transitionNarration = "Walk east back to the NPS Visitor Center. You have " +
                "completed the Grand Salem Tour. You have walked over five kilometers " +
                "through more than four centuries of Salem history, from the founding " +
                "in 1626 to the tragedy of 1692 to the vibrant city of today. " +
                "Thank you for exploring Salem with us.",
            walkingMinutesFromPrev = 3, distanceMFromPrev = 190,
            provenance = curated
        )
    )
}
