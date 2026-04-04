/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Curated Salem events — Haunted Happenings, festivals, museum programming.
 * Dates use YYYY-MM-DD format. Recurring events use recurrence_pattern.
 * Venues linked to tour_pois IDs where applicable.
 */

package com.example.salemcontent.data

import com.example.salemcontent.pipeline.OutputEvent
import com.example.salemcontent.pipeline.Provenance

object SalemEvents {

    fun all(): List<OutputEvent> = hauntedHappenings + yearRound + museumExhibits + ghostTours

    // ═════════════════════════════════════════════════════════════════════
    // HAUNTED HAPPENINGS (October)
    // ═════════════════════════════════════════════════════════════════════

    private val hauntedHappenings = listOf(
        OutputEvent(
            id = "evt_hh_grand_parade",
            name = "Haunted Happenings Grand Parade",
            venuePoiId = null,
            eventType = "parade",
            description = "Salem's signature Halloween event — thousands of costumed participants march through downtown. Route: New Derby Street to Washington Street to the Salem Common.",
            startDate = "2026-10-01",
            endDate = "2026-10-01",
            hours = "6:30 PM lineup, 7:00 PM step-off",
            admission = "Free",
            website = "https://www.hauntedhappenings.org",
            recurring = true,
            recurrencePattern = "annual_first_thursday_october",
            seasonalMonth = 10
        ),
        OutputEvent(
            id = "evt_hh_marketplace",
            name = "Haunted Happenings Artisan Marketplace",
            venuePoiId = "poi_salem_common",
            eventType = "market",
            description = "Weekend artisan marketplace on Salem Common featuring local crafts, food vendors, and seasonal goods throughout October.",
            startDate = "2026-10-03",
            endDate = "2026-10-31",
            hours = "Sat-Sun 10 AM - 6 PM",
            admission = "Free admission",
            recurring = true,
            recurrencePattern = "weekends_october",
            seasonalMonth = 10
        ),
        OutputEvent(
            id = "evt_hh_costume_contest",
            name = "Halloween Costume Contest",
            venuePoiId = "poi_salem_common",
            eventType = "special_event",
            description = "Annual costume contest on Salem Common. Categories include children, adults, groups, and pets. Prizes awarded by audience vote.",
            startDate = "2026-10-31",
            endDate = "2026-10-31",
            hours = "1:00 PM - 3:00 PM",
            admission = "Free",
            seasonalMonth = 10
        ),
        OutputEvent(
            id = "evt_hh_pumpkin_decorating",
            name = "Pumpkin Decorating at Riley Plaza",
            venuePoiId = null,
            eventType = "special_event",
            description = "Family-friendly pumpkin decorating event. Pumpkins and supplies provided while they last.",
            startDate = "2026-10-18",
            endDate = "2026-10-18",
            hours = "11:00 AM - 2:00 PM",
            admission = "Free",
            seasonalMonth = 10
        ),
        OutputEvent(
            id = "evt_hh_haunted_house_13ghosts",
            name = "Thirteen Ghosts of Salem",
            venuePoiId = null,
            eventType = "haunted_tour",
            description = "Live theatrical haunted experience through multiple rooms of terror. Salem's longest-running commercial haunted attraction.",
            startDate = "2026-10-01",
            endDate = "2026-10-31",
            hours = "Thu-Sun 7 PM - 11 PM, daily last 2 weeks",
            admission = "$18 adults, $12 children",
            website = "https://13ghostsofsalem.com",
            seasonalMonth = 10
        ),
        OutputEvent(
            id = "evt_hh_witch_museum_extended",
            name = "Salem Witch Museum — Extended October Hours",
            venuePoiId = "poi_witch_museum",
            eventType = "museum_exhibit",
            description = "Extended hours during October. The museum presents the Salem witch trials through staged presentations with life-size figures.",
            startDate = "2026-10-01",
            endDate = "2026-10-31",
            hours = "10 AM - 10 PM (extended from usual 5 PM close)",
            admission = "$15 adults, $12 seniors, $11 children 6-14",
            website = "https://salemwitchmuseum.com",
            seasonalMonth = 10
        ),
        OutputEvent(
            id = "evt_hh_psychic_fair",
            name = "Salem Psychic Fair",
            venuePoiId = null,
            eventType = "psychic_fair",
            description = "Tarot readings, palm reading, aura photography, and metaphysical vendors. Multiple psychic practitioners available throughout October.",
            startDate = "2026-10-01",
            endDate = "2026-10-31",
            hours = "Daily 10 AM - 9 PM",
            admission = "Readings start at $20",
            seasonalMonth = 10
        )
    )

    // ═════════════════════════════════════════════════════════════════════
    // YEAR-ROUND EVENTS
    // ═════════════════════════════════════════════════════════════════════

    private val yearRound = listOf(
        OutputEvent(
            id = "evt_salems_so_sweet",
            name = "Salem's So Sweet Chocolate & Ice Cream Festival",
            venuePoiId = null,
            eventType = "festival",
            description = "Valentine's season chocolate festival featuring tastings at downtown Salem restaurants and shops. Passport-style event with stamps at each location.",
            startDate = "2026-02-07",
            endDate = "2026-02-14",
            hours = "Varies by venue",
            admission = "$25 passport",
            recurring = true,
            recurrencePattern = "annual_february",
            seasonalMonth = 2
        ),
        OutputEvent(
            id = "evt_salem_film_fest",
            name = "Salem Film Fest",
            venuePoiId = null,
            eventType = "film_fest",
            description = "Annual documentary film festival screening independent documentaries from around the world. Multiple venues across downtown Salem.",
            startDate = "2026-03-05",
            endDate = "2026-03-15",
            hours = "Screenings daily 12 PM - 10 PM",
            admission = "$15 individual, $80 all-access pass",
            website = "https://salemfilmfest.com",
            recurring = true,
            recurrencePattern = "annual_march",
            seasonalMonth = 3
        ),
        OutputEvent(
            id = "evt_salem_arts_festival",
            name = "Salem Arts Festival",
            venuePoiId = null,
            eventType = "arts_festival",
            description = "Juried arts festival featuring visual arts, music, dance, and theater performances throughout downtown Salem. Open studios and gallery walks.",
            startDate = "2026-06-05",
            endDate = "2026-06-07",
            hours = "Fri 5-9 PM, Sat 10 AM - 7 PM, Sun 11 AM - 5 PM",
            admission = "Free",
            recurring = true,
            recurrencePattern = "annual_first_weekend_june",
            seasonalMonth = 6
        ),
        OutputEvent(
            id = "evt_heritage_days",
            name = "Salem Heritage Days",
            venuePoiId = "poi_maritime_nhs",
            eventType = "heritage",
            description = "Celebrate Salem's maritime and cultural heritage. Events include historical reenactments, craft demonstrations, boat tours, and music at Derby Wharf and Salem Maritime National Historic Site.",
            startDate = "2026-08-07",
            endDate = "2026-08-09",
            hours = "10 AM - 5 PM daily",
            admission = "Free",
            recurring = true,
            recurrencePattern = "annual_second_weekend_august",
            seasonalMonth = 8
        ),
        OutputEvent(
            id = "evt_salem_ferry_season",
            name = "Salem Ferry — Boston Harbor Cruises",
            venuePoiId = "poi_ferry_terminal",
            eventType = "special_event",
            description = "Seasonal high-speed catamaran service between Salem and Boston's Long Wharf. 50-minute scenic ride across the harbor.",
            startDate = "2026-05-22",
            endDate = "2026-10-31",
            hours = "Multiple departures daily, check schedule",
            admission = "$25 one-way, $47 round-trip",
            website = "https://www.bostonharborcruises.com/salem-ferry/",
            recurring = true,
            recurrencePattern = "annual_late_may_to_october",
            seasonalMonth = null
        )
    )

    // ═════════════════════════════════════════════════════════════════════
    // MUSEUM EXHIBITS & PROGRAMMING
    // ═════════════════════════════════════════════════════════════════════

    private val museumExhibits = listOf(
        OutputEvent(
            id = "evt_pem_permanent",
            name = "Peabody Essex Museum — Permanent Collection",
            venuePoiId = "poi_peabody_essex",
            eventType = "museum_exhibit",
            description = "One of the oldest continuously operating museums in America. Collections spanning maritime art, Asian export art, Native American objects, and the Yin Yu Tang house — an entire Chinese home transported and reconstructed inside the museum.",
            hours = "Tue-Sun 10 AM - 5 PM (closed Mon except holidays)",
            admission = "$22 adults, $20 seniors, free under 16",
            website = "https://www.pem.org"
        ),
        OutputEvent(
            id = "evt_seven_gables_tours",
            name = "House of the Seven Gables — Guided Tours",
            venuePoiId = "poi_seven_gables",
            eventType = "museum_exhibit",
            description = "Tours of the 1668 mansion that inspired Hawthorne's novel. The campus includes Hawthorne's Birthplace, seaside gardens, and a museum store.",
            hours = "Daily 10 AM - 5 PM (extended hours in October)",
            admission = "$18 adults, $15 seniors, $12 children 5-12",
            website = "https://www.7gables.org"
        ),
        OutputEvent(
            id = "evt_witch_museum_permanent",
            name = "Salem Witch Museum — Witch Trials Presentation",
            venuePoiId = "poi_witch_museum",
            eventType = "museum_exhibit",
            description = "Stage presentations using life-size figures recreate the Salem witch trials of 1692. A second exhibit, 'Witches: Evolving Perceptions,' examines how witch imagery has changed over centuries.",
            hours = "Daily 10 AM - 5 PM (extended in October)",
            admission = "$15 adults, $12 seniors, $11 children 6-14",
            website = "https://salemwitchmuseum.com"
        ),
        OutputEvent(
            id = "evt_pioneer_village_season",
            name = "Pioneer Village — Living History Museum",
            venuePoiId = "poi_pioneer_village",
            eventType = "museum_exhibit",
            description = "Replica of Salem's 1630 settlement with costumed interpreters. Features dugout shelters, thatched-roof cottages, and demonstrations of 17th-century crafts and skills.",
            startDate = "2026-05-01",
            endDate = "2026-10-31",
            hours = "Sat-Sun 10 AM - 5 PM (daily in October)",
            admission = "$10 adults, $8 seniors, $7 children",
            recurring = true,
            recurrencePattern = "annual_may_to_october",
            seasonalMonth = null
        ),
        OutputEvent(
            id = "evt_witch_dungeon",
            name = "Witch Dungeon Museum — Live Reenactment",
            venuePoiId = "poi_witch_dungeon",
            eventType = "museum_exhibit",
            description = "Live reenactment of a witch trial based on actual 1692 court transcripts, followed by a guided tour of a recreated dungeon.",
            startDate = "2026-04-01",
            endDate = "2026-11-30",
            hours = "Daily 10 AM - 5 PM",
            admission = "$14 adults, $12 seniors, $10 children",
            seasonalMonth = null
        )
    )

    // ═════════════════════════════════════════════════════════════════════
    // GHOST TOURS
    // ═════════════════════════════════════════════════════════════════════

    private val ghostTours = listOf(
        OutputEvent(
            id = "evt_ghost_bewitched",
            name = "Bewitched After Dark Walking Tour",
            venuePoiId = null,
            eventType = "ghost_tour",
            description = "90-minute evening walking tour through Salem's most haunted locations. Guides share paranormal investigations and ghost stories alongside verified history.",
            hours = "Nightly 8:00 PM (additional 10 PM in October)",
            admission = "$18 adults, $12 children",
            recurring = true,
            recurrencePattern = "nightly_year_round"
        ),
        OutputEvent(
            id = "evt_ghost_candlelight",
            name = "Salem Night Tour — Candlelight Ghost Tour",
            venuePoiId = null,
            eventType = "ghost_tour",
            description = "Candlelit walking tour through Salem's dark streets. Visit burial grounds, former prison sites, and locations of documented hauntings. Lanterns provided.",
            hours = "Nightly 8:30 PM",
            admission = "$20 adults, $14 children under 12",
            recurring = true,
            recurrencePattern = "nightly_april_to_november"
        ),
        OutputEvent(
            id = "evt_ghost_history_alive",
            name = "Cry Innocent — History Alive!",
            venuePoiId = "poi_witch_house",
            eventType = "show",
            description = "Interactive theatrical experience where the audience participates as jurors in a witch trial. Based on the actual examination of Bridget Bishop, first person executed in 1692.",
            startDate = "2026-05-01",
            endDate = "2026-10-31",
            hours = "Shows at 12, 1:30, 3:00, and 4:30 PM",
            admission = "$16 adults, $12 children",
            website = "https://www.cryinnocent.com",
            recurring = true,
            recurrencePattern = "daily_may_to_october"
        )
    )
}
