/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.data

import com.example.salemcontent.pipeline.OutputBusiness
import com.example.salemcontent.pipeline.Provenance

/**
 * Manually curated Salem businesses — Phase 5.
 * GPS coordinates verified against Google Maps / OpenStreetMap.
 * Covers occult shops, restaurants, bars, cafes, and lodging.
 */
object SalemBusinesses {

    private val now = System.currentTimeMillis()
    private val curated = Provenance("manual_curated", 0.9f, "2026-04-03", now, now,
        now + 180L * 24 * 60 * 60 * 1000) // stale after 180 days (businesses change)

    fun all(): List<OutputBusiness> = occultShops + restaurants + bars + cafes + lodging

    // ═══════════════════════════════════════════════════════════════════
    // Witch & Occult Shops
    // ═══════════════════════════════════════════════════════════════════

    val occultShops = listOf(
        OutputBusiness(
            id = "crow_haven_corner", name = "Crow Haven Corner",
            lat = 42.5211, lng = -70.8897,
            address = "125 Essex St, Salem, MA 01970",
            businessType = "shop_occult",
            description = "Salem's oldest witch shop, opened in 1970 by Laurie Cabot, the Official Witch of Salem. Tarot readings, spells, crystals, and witchcraft supplies.",
            historicalNote = "Founded by Laurie Cabot, declared Official Witch of Salem by Governor Dukakis in 1977.",
            tags = """["tarot","crystals","spells","witchcraft"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "hex", name = "HEX: Old World Witchery",
            lat = 42.5205, lng = -70.8903,
            address = "246 Essex St, Salem, MA 01970",
            businessType = "shop_occult",
            description = "Owned by Christian Day, one of Salem's most visible modern witches. Witchcraft supplies, readings, and occult artifacts.",
            tags = """["witchcraft","readings","occult"]""",
            website = "https://www.hexwitch.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "omen", name = "OMEN",
            lat = 42.5199, lng = -70.8907,
            address = "184 Essex St, Salem, MA 01970",
            businessType = "shop_occult",
            description = "Psychic parlor and witch shop. Tarot, palm reading, astrology, and metaphysical supplies.",
            tags = """["psychic","tarot","palm_reading","astrology"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "artemisia_botanicals", name = "Artemisia Botanicals",
            lat = 42.5196, lng = -70.8908,
            address = "168 Essex St, Salem, MA 01970",
            businessType = "shop_occult",
            description = "Herbal apothecary and botanical shop. Herbs, essential oils, teas, tinctures, and natural remedies.",
            tags = """["herbs","apothecary","essential_oils","natural"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "coven_corner", name = "The Coven's Cottage",
            lat = 42.5215, lng = -70.8894,
            address = "135 Essex St, Salem, MA 01970",
            businessType = "shop_occult",
            description = "Witchcraft supplies, handmade candles, ritual tools, and local witch-crafted items.",
            tags = """["candles","ritual_tools","handmade"]""",
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Restaurants
    // ═══════════════════════════════════════════════════════════════════

    val restaurants = listOf(
        OutputBusiness(
            id = "turners_seafood", name = "Turner's Seafood",
            lat = 42.5203, lng = -70.8876,
            address = "43 Church St, Salem, MA 01970",
            businessType = "restaurant",
            cuisineType = "seafood",
            priceRange = "$$$",
            description = "Upscale seafood restaurant in the historic Lyceum Hall, where Alexander Graham Bell made the first public demonstration of the telephone in 1877.",
            historicalNote = "Building is the Lyceum Hall where Bell first publicly demonstrated the telephone in 1877.",
            tags = """["seafood","fine_dining","historic_building"]""",
            phone = "(978) 745-7665",
            website = "https://www.turnersseafood.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "sea_level_oyster", name = "Sea Level Oyster Bar",
            lat = 42.5184, lng = -70.8856,
            address = "94 Wharf St, Salem, MA 01970",
            businessType = "restaurant",
            cuisineType = "seafood",
            priceRange = "$$$",
            description = "Waterfront raw bar and restaurant at Pickering Wharf. Fresh oysters, seafood platters, and harbor views.",
            tags = """["seafood","oysters","waterfront","harbor_views"]""",
            phone = "(978) 741-0555",
            website = "https://www.sealeveloysterbar.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "finz_seafood", name = "Finz Seafood & Grill",
            lat = 42.5178, lng = -70.8854,
            address = "76 Wharf St, Salem, MA 01970",
            businessType = "restaurant",
            cuisineType = "seafood",
            priceRange = "$$",
            description = "Casual waterfront dining at Pickering Wharf. Seafood, steaks, and craft cocktails with harbor views.",
            tags = """["seafood","waterfront","casual"]""",
            phone = "(978) 744-8485",
            website = "https://www.hipfinz.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "flying_saucer_pizza", name = "Flying Saucer Pizza Company",
            lat = 42.5203, lng = -70.8916,
            address = "118 Washington St, Salem, MA 01970",
            businessType = "restaurant",
            cuisineType = "pizza",
            priceRange = "$",
            description = "Beloved local pizza spot. Creative pies and slices in a casual, quirky atmosphere. A Salem institution.",
            tags = """["pizza","casual","local_favorite"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "rockafellas", name = "Rockafellas",
            lat = 42.5200, lng = -70.8919,
            address = "231 Essex St, Salem, MA 01970",
            businessType = "restaurant",
            cuisineType = "american",
            priceRange = "$$",
            description = "American gastropub in a historic building. Creative comfort food, craft beers, and live music on weekends.",
            tags = """["gastropub","live_music","craft_beer"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "ledger_restaurant", name = "Ledger",
            lat = 42.5168, lng = -70.8916,
            address = "125 Washington St, Salem, MA 01970",
            businessType = "restaurant",
            cuisineType = "american",
            priceRange = "$$$",
            description = "Upscale American dining in a restored bank building. Craft cocktails and creative New England cuisine.",
            tags = """["fine_dining","cocktails","historic_building"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "opus_underground", name = "Opus Underground",
            lat = 42.5177, lng = -70.8923,
            address = "87 Washington St, Salem, MA 01970",
            businessType = "restaurant",
            cuisineType = "american",
            priceRange = "$$",
            description = "Underground cocktail bar and restaurant with creative American fare and craft drinks.",
            tags = """["cocktails","underground","creative"]""",
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Bars
    // ═══════════════════════════════════════════════════════════════════

    val bars = listOf(
        OutputBusiness(
            id = "mercy_tavern", name = "Mercy Tavern",
            lat = 42.5192, lng = -70.8910,
            address = "148 Derby St, Salem, MA 01970",
            businessType = "bar",
            description = "Neighborhood tavern with craft beers, cocktails, and pub fare. Named for the Salem theme with a playful twist.",
            tags = """["craft_beer","cocktails","pub"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "notch_brewing", name = "Notch Brewing Salem Tap Room",
            lat = 42.5231, lng = -70.8970,
            address = "283 Derby St, Salem, MA 01970",
            businessType = "bar",
            cuisineType = "brewery",
            description = "Local session beer brewery with a tap room. Known for low-ABV, flavorful session ales and lagers.",
            tags = """["brewery","craft_beer","session_ales"]""",
            website = "https://www.notchbrewing.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "bit_bar", name = "Bit Bar",
            lat = 42.5210, lng = -70.8868,
            address = "1 Pleasant St, Salem, MA 01970",
            businessType = "bar",
            description = "Retro arcade bar with classic and modern video games, pinball machines, craft cocktails, and local beers.",
            tags = """["arcade","retro_games","cocktails","fun"]""",
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Cafes
    // ═══════════════════════════════════════════════════════════════════

    val cafes = listOf(
        OutputBusiness(
            id = "gulu_gulu_cafe", name = "Gulu-Gulu Cafe",
            lat = 42.5207, lng = -70.8907,
            address = "247 Essex St, Salem, MA 01970",
            businessType = "cafe",
            cuisineType = "cafe",
            priceRange = "$$",
            description = "Bohemian cafe and bar. Coffee by day, cocktails and live music by night. Art on the walls and a cozy, eclectic atmosphere.",
            tags = """["coffee","cocktails","live_music","bohemian"]""",
            website = "https://www.gulugulucafe.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "jaho_coffee", name = "Jaho Coffee & Tea",
            lat = 42.5188, lng = -70.8876,
            address = "197 Derby St, Salem, MA 01970",
            businessType = "cafe",
            cuisineType = "coffee",
            priceRange = "$",
            description = "Specialty coffee roaster with a Derby Street location. Single-origin coffees, pour-overs, espresso, and pastries.",
            tags = """["coffee","specialty","roaster","pastries"]""",
            provenance = curated
        ),
        OutputBusiness(
            id = "brew_box", name = "Brew Box",
            lat = 42.5192, lng = -70.8917,
            address = "1 Washington St, Salem, MA 01970",
            businessType = "cafe",
            cuisineType = "coffee",
            priceRange = "$",
            description = "Coffee and beer in a compact corner spot. Espresso drinks, cold brew, and a rotating selection of local craft beers.",
            tags = """["coffee","craft_beer","casual"]""",
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Lodging
    // ═══════════════════════════════════════════════════════════════════

    val lodging = listOf(
        OutputBusiness(
            id = "hawthorne_hotel_lodging", name = "Hawthorne Hotel",
            lat = 42.5207, lng = -70.8936,
            address = "18 Washington Square W, Salem, MA 01970",
            businessType = "lodging",
            priceRange = "$$$",
            description = "Historic 1925 hotel on Salem Common. 89 rooms, restaurant, and ballroom. Reportedly one of the most haunted hotels in America.",
            historicalNote = "Built in 1925, this Federal-style hotel overlooks Salem Common and has hosted presidents and celebrities.",
            tags = """["historic","haunted","restaurant","downtown"]""",
            phone = "(978) 744-4080",
            website = "https://www.hawthornehotel.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "salem_waterfront_hotel", name = "Salem Waterfront Hotel & Suites",
            lat = 42.5173, lng = -70.8830,
            address = "225 Derby St, Salem, MA 01970",
            businessType = "lodging",
            priceRange = "$$",
            description = "Modern waterfront hotel at Pickering Wharf. Harbor views, indoor pool, and walking distance to all Salem attractions.",
            tags = """["waterfront","modern","pool","harbor_views"]""",
            phone = "(978) 740-8788",
            website = "https://www.salemwaterfronthotel.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "salem_inn", name = "The Salem Inn",
            lat = 42.5175, lng = -70.8913,
            address = "7 Summer St, Salem, MA 01970",
            businessType = "lodging",
            priceRange = "$$$",
            description = "Boutique inn across three historic buildings. Period furnishings, fireplaces, and garden courtyard in the heart of downtown.",
            historicalNote = "Comprises three historic buildings: the West House (1834), the Curwen House (1854), and the Peabody House (1874).",
            tags = """["boutique","historic","garden","downtown"]""",
            phone = "(978) 741-0680",
            website = "https://www.saleminnma.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "coach_house_inn", name = "Coach House Inn",
            lat = 42.5157, lng = -70.8942,
            address = "284 Lafayette St, Salem, MA 01970",
            businessType = "lodging",
            priceRange = "$$",
            description = "Victorian bed and breakfast in a restored 1879 mansion. Period antiques, gardens, and a welcoming atmosphere.",
            tags = """["b_and_b","victorian","garden","antiques"]""",
            phone = "(978) 744-4092",
            website = "https://www.coachhouseinn.com",
            provenance = curated
        ),
        OutputBusiness(
            id = "morning_glory_bb", name = "Morning Glory Bed & Breakfast",
            lat = 42.5183, lng = -70.8976,
            address = "22 Hardy St, Salem, MA 01970",
            businessType = "lodging",
            priceRange = "$$",
            description = "Charming B&B in a quiet residential neighborhood. Walk to all downtown attractions. Full breakfast included.",
            tags = """["b_and_b","quiet","breakfast_included","residential"]""",
            provenance = curated
        )
    )
}
