/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Auto-generated from merged-salem-pois.json by generate_businesses_expanded.py
 * Generated: 2026-04-05 23:14:56
 * Source POIs: 848
 *
 * Data attribution:
 *   - Destination Salem (salem.org)
 *   - Haunted Happenings (hauntedhappenings.org)
 *   - OpenStreetMap contributors (ODbL 1.0)
 */

package com.example.salemcontent.data

import com.example.salemcontent.pipeline.OutputBusiness
import com.example.salemcontent.pipeline.Provenance

/**
 * Expanded Salem business directory — 848 POIs from merged data sources.
 * Auto-generated. Do not edit manually; re-run generate_businesses_expanded.py.
 *
 * Sources: Destination Salem, Haunted Happenings, OpenStreetMap
 * OpenStreetMap data (c) OpenStreetMap contributors, ODbL 1.0
 */
object SalemBusinessesExpanded {

    private val now = System.currentTimeMillis()

    fun all(): List<OutputBusiness> = shopOccult + shopPsychic + museum + historic + attraction + attractionHaunted + tourGhost + tour + restaurant + cafe + bar + shopRetail + lodging + venue + park + public + medical + services + other

    // ===================================================================
    // Witch & Occult Shops (18)
    // ===================================================================

    val shopOccult = listOf(
        OutputBusiness(
            id = "artemisia_botanicals", name = "Artemisia Botanicals",
            lat = 42.5222364, lng = -70.8900826,
            address = "3 Hawthorne Boulevard, Salem, MA, 01970",
            businessType = "shop_occult",
            hours = "Friday\t10 AM–6 PM<br />\nSaturday\t10 AM–6 PM<br />\nSunday\t10 AM–6 PM<br />\nMonday\t10 AM–6 PM<br />\nTuesday\t10 AM–6 PM<br />\nWednesday\t10 AM–6 PM<br />\nThursday\t10 AM–6 PM",
            phone = "978-745-0065",
            website = "https://www.artemisiabotanicals.com/",
            description = "Step into a world where magick meets the scentual. An apothecary filled with over 400 herbs, 100 teas, essential oils, soaps, candles and other handmade products for health, beauty, and magick. While you're here, enjoy a tarot or tea leaf reading with our amazing readers. For more information checkout our website artemisiabotanicals.com or call 978/745-0065.",
            tags = """["ds_subcat_18", "hh_venue", "osm_shop=herbalist"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "black_veil_shoppe_for_the_grim_hearted", name = "Black Veil Shoppe for the Grim Hearted",
            lat = 42.5215227, lng = -70.8984862,
            address = "304 Essex St, Salem, MA, 01970",
            businessType = "shop_occult",
            website = "https://www.blackveilstudio.com/",
            description = "Our sister store location neighboring the infamous Salem Witch House. A fine art, apparel & home decor gift shoppe inspired by the unrevealed mysteries of life, death, and the beauty that lies within the process of nature.",
            tags = """["ds_subcat_17", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "black_veil_shoppe_of_drear_and_wonder", name = "Black Veil Shoppe of Drear & Wonder",
            lat = 42.5215399, lng = -70.9147514,
            address = "137 Boston St, Salem, MA, 01970",
            businessType = "shop_occult",
            website = "https://www.blackveilstudio.com/",
            description = "Wander throughout our foyer and amongst a vast variety of beautifully grim artists, clothing designers, antiques, oddities, jewelry, and crafted goods. Photograph candidly amongst sprawling gardens where shadows never rest.",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "botanica_of_salem", name = "Botanica of Salem",
            lat = 42.5215196, lng = -70.8973541,
            address = "272 Essex St, Salem, MA, 01970",
            businessType = "shop_occult",
            website = "https://botanicaofsalem.com/",
            description = "At the Botanica of Salem, we are committed to preserving the sacred knowledge of Mystery Traditions. Our mission is to provide a welcoming space for all who seek to learn and explore these ancient teachings. The Botanica & Hermetic Arts of Salem offers a variety of Occult Science supplies; altar tools, magickal art, books, candles, cords, crystals, herbal preparations, incense, magickal jewelry, posters, powders, potions, statuary, and more.",
            tags = """["ds_subcat_18", "hh_venue", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "crow_haven_corner", name = "Crow Haven Corner",
            lat = 42.5223339, lng = -70.8908167,
            address = "125 Essex St, Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "978-745-8763",
            website = "https://www.crowhavencorner.com/",
            description = "Welcome to Crow Haven Corner, Salem's Oldest Witch Shop. Owned by Lorelei, The Famous Love Witch featured on The Bachelorette, The Wall Street Journal, BostonGlobe.com and much more. Lorelei invites you to stop by and book an In Person or Phone Spiritual Consultation with her very Gifted Tarot Readers: Candace, Nina, Kimberly, Dianne, Nickolas, and Jesse who specialize in Tarot Cards, Mediumship, Palmistry and much more. You can also find Candles, Books, Spells, Perfumes, Incenses, Crystals, Tarot Cards and so much more. Don't forget to visit our Salem Saves Room!!! Where all proceeds go to our Non-Profit Salem Saves Animals",
            tags = """["ds_subcat_18", "hh_venue", "osm_shop=esoteric"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "enchanted", name = "Enchanted",
            lat = 42.5203927, lng = -70.8880226,
            address = "98 Wharf Street",
            businessType = "shop_occult",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gallows_hill_witchery_s_downtown_sanctuary", name = "Gallows Hill Witchery’s Downtown Sanctuary",
            lat = 42.5224882, lng = -70.8962787,
            address = "6 Lynde Street, Salem, MA, 01970",
            businessType = "shop_occult",
            website = "https://www.salem.org/venue/gallows-hill-witcherys-downtown-sanctuary/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "haus_witch", name = "Haus Witch",
            lat = 42.5205561, lng = -70.8957129,
            address = "144 Washington Street",
            businessType = "shop_occult",
            website = "https://hauswitch.com",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hauswitch_home_healing", name = "HausWitch Home + Healing",
            lat = 42.5205886, lng = -70.8958373,
            address = "144 Washington Street, Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "978-594-8950",
            website = "https://hauswitchstore.com/",
            description = "HausWitch Home + Healing is a modern metaphysical lifestyle brand and shop, providing Salem locals and visitors with a selection of witchy and handmade products from independent makers from around New England and the US! HausWitch combines the principles of earth magic, meditation, herbalism, and interior decorating to bring magic and healing into everyday spaces. HausWitch will always be an inclusive space for all genders, sexualities, ethnicities, abilities, and anyone who feels like they are in need of a truly supportive and safe environment in this ever-changing world. We also offer aura photography readings.",
            tags = """["ds_subcat_18", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_cauldron_black", name = "The Cauldron Black",
            lat = 42.5198875, lng = -70.888405,
            address = "65 Wharf Street, Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "978-744-2492",
            website = "http://www.thecauldronblack.com",
            tags = """["hh_venue", "osm_shop=occult_goods"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_good_witch_of_salem", name = "The Good Witch of Salem",
            lat = 42.521658, lng = -70.89855279999999,
            address = "2 North St, Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "978-594-4026",
            website = "http://goodwitchofsalem.com/",
            description = "Formerly our children's shop, now a magical space for adults and teens. Explore crystals, jewelry, and treasures that inspire self-expression, and nurture your Inner Child Magic.",
            tags = """["ds_subcat_16", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_lost_library_a_peculiar_gift_shop_at_the_witch_village", name = "The Lost Library: A Peculiar Gift Shop at the Witch Village",
            lat = 42.5204357, lng = -70.8914193,
            address = "282 Derby St, Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "978-740-2992",
            website = "https://www.LostLibrarySalem.com",
            description = "Experience a witch’s cottage, curiosity corner, and Salem’s largest glowing pumpkin. You don’t just shop—you explore!",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_ossuary", name = "The Ossuary",
            lat = 42.5202991, lng = -70.8883588,
            address = "77 Wharf St, Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "978-224-2706",
            website = "https://theossuarysalem.com/",
            description = "Salem's gothic boutique for one-of-a-kind evening gowns, wedding attire, cocktail outfits, corsetry, & gifts. Handmade & small batch fashion that is stunning and unforgettable! Follow us on Instagram: @salemossuary",
            tags = """["ds_subcat_17", "osm_shop=clothes"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_witchery_studio_space", name = "The Witchery Studio Space",
            lat = 42.519974, lng = -70.8884433,
            address = "61 Wharf St., Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "978-914-8858",
            website = "https://thewitcherysalem.com/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_and_fairy_emporium", name = "Witch & Fairy Emporium",
            lat = 42.5218339, lng = -70.8931874,
            address = "178 Essex Street",
            businessType = "shop_occult",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_broom_company", name = "Witch City Broom Company",
            lat = 42.5215931, lng = -70.8962975,
            address = "246 Essex St, Salem, MA, 01970",
            businessType = "shop_occult",
            phone = "781-608-6986",
            website = "https://www.WitchCityBroomCompany.com",
            description = "Step into our brand new enchanting retail haven, where magic meets artistry! Our curated collection features one-of-a-kind gifts and artisanal treasures from local and regional artisans. Discover our signature witch brooms and DIY broom kits, meticulously handcrafted to bring a peice of Salem home. Our shelves are adorned with an array of delights, including: Handcrafted candles that evoke the essence of the season. Stylish apparel, books & stationery that will ignite your inner witch. Seasonal decor that will cast a spell of delight on your home with our unique gifts and more! Come and indulge in the magic of the Witch City Broom Co., where every item is carefully selected to bring a touch of enchantment to your life.",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_dr", name = "Witch Dr.",
            lat = 42.5182198, lng = -70.8929829,
            address = "109 Lafayette Street, Salem, MA, 01970",
            businessType = "shop_occult",
            tags = """["hh_venue", "osm_shop=cannabis"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_way_gifts", name = "Witch Way Gifts",
            lat = 42.5217491, lng = -70.8860655,
            address = "Salem, MA",
            businessType = "shop_occult",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Psychic & Spiritual Services (13)
    // ===================================================================

    val shopPsychic = listOf(
        OutputBusiness(
            id = "angelique_renard_at_hex", name = "Angelique Renard at Hex",
            lat = 42.5215722, lng = -70.89356959999999,
            address = "184 Essex St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-666-0765",
            website = "https://www.HexWitch.com",
            description = "Angelique Renard is a Salem Witch, High Priestess of the Salem Coven, and a gifted psychic medium, guiding seekers with insight and prophecy since childhood. She specializes in Tarot, palmistry, and spirit mediumship.",
            tags = """["ds_subcat_7"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "celestial_navigation_astrology_and_wellness", name = "Celestial Navigation Astrology & Wellness",
            lat = 42.5216991, lng = -70.8964584,
            address = "254 Essex St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-414-5022",
            website = "https://www.CelestialNavigationAstrology.com",
            description = "A quiet healing space for astrology, reiki, sound baths, tarot, workshops, private tours and more. Blending intuitive insight and energy work for clarity and renewal. By appointment, book online.",
            tags = """["ds_subcat_7"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fatima_s_psychic_studio", name = "Fatima’s Psychic Studio",
            lat = 42.5220818, lng = -70.8906004,
            address = "Salem, MA",
            businessType = "shop_psychic",
            tags = """["osm_shop=psychic"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hex_old_world_witchery", name = "Hex: Old World Witchery",
            lat = 42.52158019999999, lng = -70.8962545,
            address = "246 Essex St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "(978) 666-0765",
            description = "What is it you seek? To find Love or strengthen it? To achieve success? To bring healing, a better job, or justice to one who deserves it? To peer into the future? To contact your beloved dead? Start with the purest oils, richest incense, candles, and soaps, potent charms and gris-gris handmade by true practitioners. Christian Day, Brian Cain, and the Witches of Hex honor the old gods, speak to spirits, handcraft spells, conjure changes, make waves, live every day surrounded by magic. And they welcome you to join them.",
            tags = """["ds_subcat_7", "ds_subcat_18"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "live_spellcasting_within_the_witching_hour", name = "Live Spellcasting: Within the Witching Hour",
            lat = 42.520318, lng = -70.891368,
            address = "282 Derby Street, Salem, MA, 01970",
            businessType = "shop_psychic",
            hours = "Open Summer and October",
            phone = "978-740-2992",
            website = "http://buryingpointproductions.com",
            description = "Join a practicing witch in a live ritual and cast your spell inside a black box theater. This interactive and immersive experience is great for all ages.",
            tags = """["ds_subcat_7"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "omen_psychic_parlor_and_witchcraft_emporium", name = "Omen: Psychic Parlor & Witchcraft Emporium",
            lat = 42.5215722, lng = -70.8935696,
            address = "184 Essex Street, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-666-0763",
            website = "http://www.omensalem.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "personal_rainbow_aura_photography", name = "Personal Rainbow Aura Photography",
            lat = 42.5206921, lng = -70.8958516,
            address = "144 Washington St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-594-8950",
            website = "https://www.HausWitch.com",
            description = "Step into the custom-built aura photo booth at HausWitch Home + Healing to see your true colors! Each aura photo session includes a 15 minute interpretation + keepsake Fuji Instax Wide film photo. Room for two, or just you!",
            tags = """["ds_subcat_7"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "psychic_spa", name = "Psychic Spa",
            lat = 42.5217857, lng = -70.89340059999999,
            address = "184 Essex St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-666-0765",
            website = "https://www.ThePsychicSpa.com",
            description = "Beyond divination lies transformation. Recline, release, and receive as your psychic guides you through breath, energy, and trance—to restore balance, recall past lives, commune with ancestors, or raise shields of power.",
            tags = """["ds_subcat_7"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_center_for_past_life_regression", name = "Salem Center for Past Life Regression",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "shop_psychic",
            phone = "978-578-9222",
            website = "https://www.pastliferegressionsalem.com/",
            description = "Susan is an experienced Past Life Regression and Ancestral Healing Therapist, earning her Certificate from Woolger Training International in 2012. She participated in workshops, training and conferences in the US, Canada, South America, and Europe. Susan worked with world famous, master-level regression therapy trainers from the UK, Portugal, Brazil, & United States, which enhanced her spiritual practices. Susan’s approach integrates humor with a profound understanding of metaphysics, psi phenomena, and holistic health. Death is a part of life; it is just a lot less scary and painful than the rest of it. Contact: pastliferegressionsalem@gmail.com",
            tags = """["ds_subcat_7", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_s_ance", name = "Salem Séance",
            lat = 42.5218449, lng = -70.8934487,
            address = "184 Essex St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-666-0763",
            description = "Step into the Spirit Parlor of Hex and experience an authentic Salem Seance. Our gifted psychic mediums can connect with your loved ones on the other side and have helped thousands reunite with souls who have crossed over!",
            tags = """["ds_subcat_7"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sea_wych_salem", name = "Sea Wych Salem",
            lat = 42.5214071, lng = -70.89451369999999,
            address = "1 Derby Square, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "(508) 319-1885",
            website = "https://www.theseawych.com/",
            description = "Awaken your inner sea witch with The Sea Wych Salem, Salem's only shop dedicated to sea and water magic, located in the heart of downtown. Whether you're looking to unleash the mermaid within, divine your destiny, or discover a calm and tranquil lagoon, The Sea Wych Salem is a must visit. Featuring handcrafted magic and ritual tools, beautifully curated items for bath, body, mind, and soul, a drop-in sea spell jar bar, sea witch ball workshops, and tarot & oracle readings, your time at The Sea Wych Salem will be an unforgettable experience.",
            tags = """["ds_subcat_7", "ds_subcat_18", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tarot_cove_reading_room_and_gathering_space", name = "TAROT COVE: Reading Room & Gathering Space",
            lat = 42.519974, lng = -70.88844329999999,
            address = "61 Wharf St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-914-8858",
            website = "https://www.TarotCoveSalem.com",
            description = "Tarot Cove is The Witchery's new divination parlor and gathering space! Drop in for a private, couples or group reading. Bring your wedding party or friends for a girls' night out! Immerse yourself in live, ritual theatre!",
            tags = """["ds_subcat_7"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "yulia_applewood_at_hex_old_world_witchery", name = "Yulia Applewood at Hex: Old World Witchery",
            lat = 42.5218034, lng = -70.8933757,
            address = "184 Essex St, Salem, MA, 01970",
            businessType = "shop_psychic",
            phone = "978-666-0765",
            website = "https://www.yuliaapplewood.com/",
            description = "As a natural born psychic medium, with 20+yrs of experience, Yulia Applewood offers clients an exclusive look into their daily lives, future, and potentials with profound empathy. Yulia employs a variety of techniques to enhance your experience and focus her innate intuition specifically to your needs. Techniques include tarot, clairvoyance, palmistry, runes, bone throwing, dowsing, and crystal ball gazing, which validate intuited psychic information and increase accuracy. Through mediumship, Yulia connects with the spirits of loved ones who have passed on, providing clients with validating information and messages directly from the spirit world with compassion and clarity.",
            tags = """["ds_subcat_7", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Museums (19)
    // ===================================================================

    val museum = listOf(
        OutputBusiness(
            id = "custom_house", name = "Custom House",
            lat = 42.5216533, lng = -70.8872189,
            address = "164;174;178 Derby Street, Salem, MA, 01970",
            businessType = "museum",
            description = "Exhibits on the tools of the Custom Service, the work of the Customs inspectors, and the office of Nathaniel Hawthorne, whose three-year-long stint in the Salem Custom House inspired his classic novel, The Scarlet Letter.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "derby_house", name = "Derby House",
            lat = 42.5218573, lng = -70.8866771,
            address = "Derby Street, Salem, MA, 01970",
            businessType = "museum",
            description = "Built in 1762 as a wedding present, the Derby House was the home of Elias Hasket Derby (1739-1799) and Elizabeth Crowninshield Derby (1727-1799) for the first twenty years of their marriage.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_institute_museum_building", name = "Essex Institute Museum Building",
            lat = 42.5226451, lng = -70.8913845,
            address = "MA",
            businessType = "museum",
            tags = """["osm_tourism=museum"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "halloween_museum", name = "Halloween Museum",
            lat = 42.5221168, lng = -70.8915226,
            address = "131 Essex Street",
            businessType = "museum",
            tags = """["osm_tourism=museum"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hawkes_house", name = "Hawkes House",
            lat = 42.5217687, lng = -70.8869256,
            address = "Derby Street, Salem, MA, 01970",
            businessType = "museum",
            description = "Designed by Samuel McIntire (1751-1811), one of the earliest and most influential architects in the United States, the Hawkes House had been commissioned but never lived in by Elias Hasket and Elizabeth Derby.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "narbonne_house", name = "Narbonne House",
            lat = 42.5226613, lng = -70.8873634,
            address = "71 Essex Street, Salem, MA, 01970",
            businessType = "museum",
            description = "The Narbonne House was built in 1675 for butcher Thomas Ives. Named after Sarah Narbonne, whose grandfather Jonathan Andrews purchased the house in 1780. Sarah was born in the Narbonne house, and lived there for her entire life.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pedrick_store_house", name = "Pedrick Store House",
            lat = 42.5205359, lng = -70.886565,
            address = "1 Derby Wharf, Salem, MA, 01970",
            businessType = "museum",
            description = "Moved from Marblehead, originally built by Thomas Pedrick in 1770. During the Revolution, Pedrick commissioned privateers to capture British merchant vessels, and probably stored captured cargo in his store house.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pioneer_village_1", name = "Pioneer Village",
            lat = 42.5079565, lng = -70.8857988,
            address = "Salem, MA",
            businessType = "museum",
            tags = """["osm_tourism=museum"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "public_stores", name = "Public Stores",
            lat = 42.5217192, lng = -70.8872529,
            address = "Derby Street, Salem, MA, 01970",
            businessType = "museum",
            description = "It was used by the U.S. Customs Service to hold cargo for merchants until they were able to pay the duties on their goods. Cargo came into Salem in barrels, crates, bags, and chests.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "scale_house", name = "Scale House",
            lat = 42.5219201, lng = -70.8874625,
            address = "Derby Street, Salem, MA, 01970",
            businessType = "museum",
            description = "used as a storage facility for scales and other equipment required to weigh cargo goods on ships. The scales were never used inside the building, but were carted and moved to the wharf and assembled alongside a vessel that had just returned.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "st_joseph_hall", name = "St. Joseph Hall",
            lat = 42.5219078, lng = -70.88631,
            address = "160 Derby Street, Salem, MA, 01970",
            businessType = "museum",
            description = "By the 20th century, the western end of Derby Street had become the heart of the Polish Community in Salem. The St. Joseph Society, a fraternal society, built this headquarters in 1909.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "west_india_goods_store", name = "West India Goods Store",
            lat = 42.5218044, lng = -70.8864769,
            address = "Derby Street, Salem, MA, 01970",
            businessType = "museum",
            description = "The term “West India Goods Store” was used in Salem as a generic term for a retail shop selling items from all over the world, not just the Caribbean. Captain Henry Prince (1764-1846) built this structure in the early 1800s.",
            tags = """["osm_tourism=museum", "osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "1692_before_and_after_llc", name = "1692 Before and After, LLC",
            lat = 42.52161409999999, lng = -70.897401,
            address = "272 Essex St, Salem, MA, 01970",
            businessType = "museum",
            phone = "978-998-5220",
            website = "https://1692beforeandafter.com/",
            description = "Come unearth the secrets of Salem with 1692 Before and After. This two-hour walking tour is the most comprehensive and highest-rated historical tour of the Witch Trials in Salem.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_witch_board_museum", name = "Salem Witch Board Museum",
            lat = 42.5222105, lng = -70.8909128,
            address = "127 Essex St, Salem, MA, 01970",
            businessType = "museum",
            phone = "781-552-0096",
            website = "https://www.SalemWitchBoardMuseum.com",
            description = "The world's only museum dedicated to the history and mystery of the Ouija Board. Learn it's true history, it's impact on pop culture, haunted boards, as well as stories of murder, suicide and mysteries surrounding the board. The world’s only museum dedicated to the history and mystery of the Ouija Board. Learn it’s true history, it’s impact on pop culture, haunted boards, as well as stories of murder, suicide and mysteries surrounding the board.",
            tags = """["ds_subcat_5", "ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_witch_museum", name = "Salem Witch Museum",
            lat = 42.5237102, lng = -70.891125,
            address = "19 1/2 North Washington Square, Salem, MA, 01970",
            businessType = "museum",
            hours = "Open daily 10 am-5 pm year-round. 10 am-7 pm in July and August. Extended hours in October.",
            phone = "978-744-1692",
            website = "https://salemwitchmuseum.com/",
            description = "The Salem Witch Museum, founded in 1972, offers two historical presentations. The first tells the tragic story of the 1692 witch trials and takes place in a large auditorium with life-sized stage sets which are illuminated and dramatically narrated to immerse visitors in the world of 17th-century Salem. The second exhibit, Witches: Evolving Perceptions, examines the European witchcraft trials, the evolving image of the witch, and the larger issues of persecution and scapegoating in American history. Visit salemwitchmuseum.com for information on purchasing tickets, upcoming virtual events, educational materials, and the online museum store.",
            tags = """["ds_subcat_5", "ds_subcat_16", "hh_venue", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_witch_trials_memorial", name = "Salem Witch Trials Memorial",
            lat = 42.5208444, lng = -70.8918965,
            address = "Liberty Street, Salem, MA, 01970",
            businessType = "museum",
            website = "https://salemwitchmuseum.com/locations/witch-trials-memorial/",
            description = "Striking in its simplicity, the Memorial is surrounded on three sides by a handcrafted granite wall. Inscribed in the rough stone threshold entering the Memorial are the victims’ protests of innocence. These protests are interrupted mid-sentence by the wall, symbolizing society’s indifference to oppression. Six locust trees, the last to flower and the first to lose their leaves, represent the injustice of the Salem witch trials. The Symbolism at The Salem Witch Trials Memorial At the memorial’s entrance, the clipped quotations represent the silenced voices of the accused during the Salem witch trials. Their statements of innocence are cut off mid-sentence, symbolizing how their pleas were ignored and dismissed. The unfinished words serve as a powerful reminder of the injustice they faced and the lasting impact of a society that failed to listen. Benches within the Memorial perimeter bear the names and the execution dates of each of the 20 victims, creating a quiet, contemplative environment in which to evoke the spirit and strength of those people who chose to die rather than compromise their personal truths. Visiting The Salem Witch Trials Memorial The Witch Trials Memorial is located on Liberty Street between Charter Street and Derby Street and is open from dawn till dusk. It is handicapped accessible and is appropriate for all ages. When visiting the Salem Witch Trials Memorial it’s important to treat it with honor and respect. Visit the Charter Street Cemetery to learn more about the do’s and don’ts when visiting the memorial. Voices Against Injustice can be reached at: info@voicesagainstinjustice.org Frequently Asked Questions Where is the Salem Witch Trials Memorial located? A: The memorial is located at Liberty Street, next to the Charter Street Old Burying Point Cemetery in downtown Salem. Is the Salem Witch Trials Memorial free to visit? A: Yes, the Salem Witch Trials Memorial has free public access to enter and visit site. What are the visiting hours? A: The memorial is typically accessible to visit during daylight hours. Seasonal closures for maintenance can occur. Is the Salem Witch Trials Memorial accessible? A: All pathways are flat and wheelchair accessible. Am I allowed to take pictures of the Salem Witch Trials Memorial? A: Yes, you are able to take all photos of the memorial, but please respect the established path through the cemetery. Am I allowed to leave flowers or light candles? A: No, guests are aren't allowed to leave flowers, light or leave candles. Can I bring my pets to the memorial? A: No, pets are not allowed into the cemetery.",
            tags = """["ds_subcat_5", "ds_subcat_8", "osm_historic=memorial"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_witch_house", name = "The Witch House",
            lat = 42.5215486, lng = -70.8988988,
            address = "310 Essex St, Salem, MA, 01970",
            businessType = "museum",
            hours = "Open daily from mid-March through November<br />\n10am - 5pm last entry 4:45pm",
            phone = "978-744-8815",
            website = "https://www.thewitchhouse.org/",
            description = "Salem's only building with direct ties to the witch trials, the 17th century home of Judge Jonathan Corwin. Open daily from mid-March through November. Call for winter hours. Open 10-5 (Last entry into the House is 4:45) Self-guided tours for 4 adults, 2 children is current occupancy every 20 minutes. Staff positioned throughout house to chat and answer questions.",
            tags = """["ds_subcat_5", "hh_venue", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_dungeon_museum", name = "Witch Dungeon Museum",
            lat = 42.5225674, lng = -70.8971921,
            address = "16 Lynde Street, Salem, MA, 01970",
            businessType = "museum",
            hours = "Daily 10 am–5 pm, April 1–Nov 30",
            phone = "978-741-3570",
            website = "http://WitchDungeon.com",
            description = "Salem’s most exciting award winning reenactment of a 1692 Witch Trial and guided tour of the dungeon. Daily 10 am–5 pm, April 1–Nov 30. Extended hours in October. Visa/MC/Dis.",
            tags = """["ds_subcat_5", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_history_museum", name = "Witch History Museum",
            lat = 42.521486, lng = -70.8940628,
            address = "197 Essex Street, Salem, MA, 01970",
            businessType = "museum",
            hours = "Daily 10 am–5 pm, April 1–Nov 30.",
            phone = "978-741-7770",
            website = "http://WitchHistoryMuseum.com",
            description = "Guided tour featuring the history of the 1692 Witch Trials through life-sized scenes depicting Salem’s untold stories. Daily 10 am–5 pm, April 1–Nov 30. Extended hours in October. Visa/MC/Dis.",
            tags = """["ds_subcat_5", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Historic Sites (39)
    // ===================================================================

    val historic = listOf(
        OutputBusiness(
            id = "broad_street_cemetery", name = "Broad Street Cemetery",
            lat = 42.5182432, lng = -70.8991016,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "charter_street_cemetery", name = "Charter Street Cemetery",
            lat = 42.5204578, lng = -70.8922572,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "friends_burial_ground_quaker_cemetary", name = "Friends Burial Ground Quaker Cemetary",
            lat = 42.5189033, lng = -70.9049293,
            address = "396 1/2 Essex Street, Salem, MA, 01970",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "howard_street_cemetery", name = "Howard Street Cemetery",
            lat = 42.5245667, lng = -70.8925194,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "monumental_cemetery", name = "Monumental Cemetery",
            lat = 42.528329, lng = -70.9231854,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "old_south_trask_cemetery", name = "Old South Trask Cemetery",
            lat = 42.5230095, lng = -70.918223,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "prescott_memorial_cemetery", name = "Prescott Memorial Cemetery",
            lat = 42.5303252, lng = -70.9261805,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_marys_cemetery", name = "Saint Marys Cemetery",
            lat = 42.5349548, lng = -70.9119114,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "waterside_cemetery", name = "Waterside Cemetery",
            lat = 42.5115876, lng = -70.8661209,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wilson_cemetery", name = "Wilson Cemetery",
            lat = 42.5372948, lng = -70.9262367,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=cemetery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "andrew_safford_house", name = "Andrew Safford House",
            lat = 42.5230254, lng = -70.8909076,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=manor"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "benjamin_punchard_shoreman", name = "Benjamin Punchard, Shoreman",
            lat = 42.5218054, lng = -70.9011966,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=memorial"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bewitched_sculpture_samantha_statue", name = "Bewitched Sculpture – Samantha Statue",
            lat = 42.5213319, lng = -70.8958518,
            address = "237-245 Essex St, Salem, MA, 01970",
            businessType = "historic",
            description = "Now a landmark of the city of Salem, this six-foot-tall statue of Elizabeth Montgomery pays tribute to the well-known 1960s TV sitcom ‘Bewitched.’ The series filmed several episodes in Salem for its seventh season in 1970 after a fire shut down its Hollywood set. In 2005, the nostalgia cable channel TV Land gifted this bronze beauty to Salem in honor of the show’s 40th anniversary, and it has quickly become a staple in our magical city. Take selfies with it casting spells, twitching your nose, and having a good time in Salem. Photo: Ben Rekemeyer",
            tags = """["ds_subcat_8", "osm_tourism=artwork"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "central_wharf", name = "Central Wharf",
            lat = 42.519575, lng = -70.8867606,
            address = "Derby Street, Salem, MA, 01970",
            businessType = "historic",
            tags = """["osm_historic=maritime", "osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "charlotte_forten_park", name = "Charlotte Forten Park",
            lat = 42.5197342, lng = -70.8917046,
            address = "289 Derby Street, Salem, MA, 01970",
            businessType = "historic",
            website = "https://www.salem.org/venue/charlotte-forten-park/",
            description = "Salem's newest green space honors the legacy of Charlotte Forten, an abolitionist, women’s right’s activist, and Salem State University’s first African-American graduate. The roughly 25,000 square foot Charlotte Forten Park includes a plaza for programs and performances, a harbor-walk around the South River, swing seating facing the water, built-in percussion features, and green space. The park can connect to Downtown Salem, the Point neighborhood and Salem’s historic waterfront while paying tribute to Forten’s legacy and celebrating all that she achieved for generations to come. Charlotte Forten was an educator, writer, poet, abolitionist, and women’s rights activist. Forten was originally from Philadelphia where she was brought up in a prominent abolitionist family and her desire for an equal education led her to Salem, Massachusetts, where she was the first African-American to graduate from the Salem Normal School, now Salem State University, with the class of 1856. Throughout her life, Forten faced inequality due to her race and gender; however, that did not deter her. She used her pen to express her frustrations and advocate for solutions. Her life’s work as a poet and translator showcased her as a leading social justice activist. Forten, who was an advocate for equality for women and people of color, education for all, and for the end of slavery, was also the first northern African-American teacher to go south to teach former slaves.",
            tags = """["ds_subcat_8", "hh_venue", "osm_leisure=park"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "charter_street_historic_district", name = "Charter Street Historic District",
            lat = 42.5209289, lng = -70.892271,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=district"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "crowninshield_bentley_house", name = "Crowninshield-Bentley House",
            lat = 42.5227014, lng = -70.890822,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=yes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dead_horse_beach", name = "Dead Horse Beach",
            lat = 42.5344854, lng = -70.8701578,
            address = "158 Fort Avenue, Salem, MA, 01970",
            businessType = "historic",
            description = "Located at the Salem Willows, Dead Horse Beach faces the Beverly harbor. It is one of the larger sandy beaches at The Willows and can be accessed by the public year-round. A great place to start a kayaking trip. Free parking.",
            tags = """["ds_subcat_8"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "derby_powderhouse_wharf", name = "Derby Powderhouse Wharf",
            lat = 42.5261424, lng = -70.8716779,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "derby_wharf_light_station", name = "Derby Wharf Light Station",
            lat = 42.5213163, lng = -70.8866429,
            address = "160 Derby Street, Salem, MA, 01970",
            businessType = "historic",
            phone = "(978) 740-1650",
            website = "https://www.nps.gov/sama/index.htm",
            description = "The Derby Wharf Light Station was constructed in 1871 to better assist ships entering Salem Harbor. The lighthouse features a unique square design, and is only about 20 feet tall. The National Park Service gained ownership of the lighthouse in 1977, and today NPS rangers at the Salem Maritime National Historic Site are available to provide additional information about the light station. The interior of the lighthouse is not open to the public, however the exterior is accessible via a scenic walk to the end of Derby Wharf.",
            tags = """["ds_subcat_8"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "forest_river_park", name = "Forest River Park",
            lat = 42.5063131, lng = -70.884693,
            address = "32-38 Clifton Avenue, Salem, MA, 01970",
            businessType = "historic",
            description = "With lots of trees, a dazzling view of the harbor, bike paths, picnic areas, two beaches, a playground, swimming pool, a baseball field, and even a concrete slide you can slide down with cardboard; Forest River Park is a great way to spend an afternoon. The park is also home to Pioneer Village, a recreation of a 17th Century fishing village. Established in 1930, this is one of America's first living history museums. Parking is available at Forest River Park Mon-Sun (8am – 8pm) 4 hours max, \$0.50/hr, April 1 – October 31 Pay by plate number with the Passport app; Free with resident permit Parking is metered through pay by plate number with the Passport app; Free with recreation permit 2025 Dates Coming Soon!",
            tags = """["ds_subcat_8", "ds_parking", "osm_leisure=park"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gardner_pingree_house", name = "Gardner-Pingree House",
            lat = 42.5225717, lng = -70.8910202,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=manor"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "harmony_grove_cemetery", name = "Harmony Grove Cemetery",
            lat = 42.5258618, lng = -70.9144783,
            address = "30 Grove St, Salem, MA, 01970",
            businessType = "historic",
            website = "https://www.harmonygrovesalem.org/",
            description = "Harmony Grove Cemetery is a nonprofit cemetery and crematory organization devoted to maintaining its grounds in perpetuity. Now spanning nearly 57 acres, Harmony Grove remains a serene and historically rich resting place, honoring Salem’s past while serving future generations with care and dignity.",
            tags = """["ds_subcat_8", "osm_historic=cemetery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hathaway_house", name = "Hathaway House",
            lat = 42.5214953, lng = -70.8837087,
            address = "MA",
            businessType = "historic",
            tags = """["osm_historic=yes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "historic_beverly", name = "Historic Beverly",
            lat = 42.5458491, lng = -70.8798849,
            address = "117 Cabot St, Beverly, MA, 01915",
            businessType = "historic",
            phone = "978-922-1186",
            website = "https://historicbeverly.net/",
            description = "Join Historic Beverly to explore Beverly's connection to the Salem witchcraft era at the historic 1694 home of Rev. John Hale, located in downtown Beverly near restaurants and shopping. Free parking. Special programming throughout October includes Witches and Bitches: The Badass Women of Beverly walking tour on October 9 and 19th; Tales and Ales spooky tours with gentile Brewing Company on October 10: Hale Whodunit? October 17th; Spooky Tales at Hale on October 24th, Creature Double Feature: Spooky Tales and Ancient Burial Ground Walking Tour on October 31st. Reservations required for all programs. For more information visit our website: HistoricBeverly.net/events",
            tags = """["ds_subcat_8", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lady_of_salem_maritime_public_art_celebration", name = "Lady of Salem Maritime Public Art Celebration",
            lat = 42.5227268, lng = -70.8919824,
            address = "2 New Liberty Street, Salem, MA, 01970",
            businessType = "historic",
            website = "https://www.facebook.com/Lady-of-Salem-Maritime-Arts-Festival-102578296479721/",
            description = "Lady of Salem” Maritime Public Art Exhibition is on display June-October, celebrating the “Golden Age of Sail “ in New Englands original port of culture, Salem Massachusetts. The Salem Beautification Committee sponsored project began in 2012. Lady of Salem features over 17 figureheads created by local artists. The “Ladies” can be found adorning lampposts along the Essex St. pedestrian mall. The Lady of Salem” exhibition is a public-private collaboration which pairs local artists with school, civic organization or business sponsors. Artists paint and embellish a 33 inch fiber molded form. Each figurehead embodies various aspects of Salem’s Golden Age of Sail. Figureheads are ornamental carvings which graced the bowsprit of sailing ships and helped to identify the vessel. During the late 1790’s Salem was the richest city in America with over 50 wharves. Spices, textiles and other riches were brought to Salem from Asia and the Caribbean. Locally crafted products and commodities were traded around the world. Salem’s City motto, “To the farthest ports of the rich East” celebrates this illustrious period in our history! The “Ladies” pay homage to our maritime glory.",
            tags = """["ds_subcat_8"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "nathaniel_bowditch_house", name = "Nathaniel Bowditch House",
            lat = 42.5217564, lng = -70.8988732,
            address = "9 North Street, Salem, MA, 01970",
            businessType = "historic",
            description = "Nathaniel Bowditch was a self taught mathematician. He discovered over 8,000 errors in Englishman John Hamilton Moore’s, The Practical Navigator. In 1802 he published his own American Practical Navigator, which remains the world standard.",
            tags = """["osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "roger_conant_statue", name = "Roger Conant Statue",
            lat = 42.5234417, lng = -70.8908657,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=memorial"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_common", name = "Salem Common",
            lat = 42.5241664, lng = -70.8891093,
            address = "North Washington Square, Salem, MA, 01970",
            businessType = "historic",
            website = "https://www.salem.org/venue/salem-common/",
            description = "Right in the heart of downtown Salem, this grassy green 8-acre public park has been a common area since the 17th Century. Great for picnics, walks, and photos.",
            tags = """["ds_subcat_8", "hh_venue", "osm_leisure=park"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_harbor", name = "Salem Harbor",
            lat = 42.519662, lng = -70.889544,
            address = "23 Congress Street, Salem, MA, 01970",
            businessType = "historic",
            description = "One of the sites of the major international ports in the colonies, the Salem Harbor spans both north and south of Salem. During the late 18th and early 19th centuries, international trade was conducted in Salem from the Atlantic coast, importing ceramics, furniture, decorative arts, artificial flowers, spices, and dyes. The harbor is known for its beautiful sunsets and views, which can be best seen on Pickering Wharf.",
            tags = """["ds_subcat_8"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_harbormaster", name = "Salem Harbormaster",
            lat = 42.5261273, lng = -70.8711621,
            address = "51 Winter Island Road, Salem, MA, 01970",
            businessType = "historic",
            website = "https://www.salem.com/harbormaster",
            description = "Air station facilities consisted of a single hangar, a paved 250 ft parking apron, and two seaplane ramps. Barracks, administrative and dining facilities, communications and motor pool buildings were also part of the complex.Air station facilities consist",
            tags = """["osm_historic=maritime", "osm_office=harbour_master"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_maritime_national_historical_park", name = "Salem Maritime National Historical Park",
            lat = 42.5190578, lng = -70.885693,
            address = "160 Derby Street, Salem, 01970",
            businessType = "historic",
            phone = "978-740-1650",
            website = "https://www.nps.gov/sama/",
            tags = """["osm_historic=maritime", "osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_woods", name = "Salem Woods",
            lat = 42.5065716, lng = -70.9069887,
            address = "Willson Street, Salem, MA, 01970",
            businessType = "historic",
            description = "Recognized by the Massachusetts Historical Commission and once used as a \"common land\" for pasturage of livestock in colonial times, the Salem Woods is now used by families for picnics, hikers, naturalists, birders, the Boy Scouts, schools groups, bikers, and many more. It has served Salem in many ways for many years, and is a great place to visit while here.",
            tags = """["ds_subcat_8"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_first_muster", name = "The First Muster",
            lat = 42.5240339, lng = -70.8894076,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=monument"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "u_s_coast_guard_sea_and_rescue_administration", name = "U. S. Coast Guard Sea and Rescue Administration",
            lat = 42.5268368, lng = -70.8705509,
            address = "50 Winter Island Road, Salem, MA, 01970",
            businessType = "historic",
            website = "https://cgaviationhistory.org/1935-coast-guard-air-station-salem-established/",
            tags = """["osm_historic=maritime"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "washington_arch", name = "Washington Arch",
            lat = 42.5246419, lng = -70.8895257,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=memorial"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "winter_island_light", name = "Winter Island Light",
            lat = 42.5267382, lng = -70.8691769,
            address = "50 Winter Island Road, Salem, MA, 01970",
            businessType = "historic",
            phone = "978-745-9430",
            website = "https://coastalbyway.org/communities/salem/winter-island-light-fort-pickering-light-1871/",
            description = "Winter Island Light, or the Fort Pickering Lighthouse was established in 1871 as part of the joint effort with the Derby Wharf Light Station and Hospital Point Light Station in Beverly to safely direct ships into Salem harbor regardless of the times of day they were coming in. Now primarily used as a campsite and recreational area, guests are welcome to visit the exterior of Winter Island Light, which is accessible at 50 Winter Island Road.",
            tags = """["ds_subcat_8", "ds_subcat_24"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "world_war_ii_memorial", name = "World War II Memorial",
            lat = 42.523345, lng = -70.890176,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=memorial"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "yin_yu_tang", name = "Yin Yu Tang",
            lat = 42.5211985, lng = -70.8921326,
            address = "Salem, MA",
            businessType = "historic",
            tags = """["osm_historic=manor"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Attractions (86)
    // ===================================================================

    val attraction = listOf(
        OutputBusiness(
            id = "9th_realm_gallery", name = "9th Realm Gallery",
            lat = 42.51530349999999, lng = -70.8930092,
            address = "172 Lafayette St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-594-1488",
            website = "https://www.9thRealmGallery.com",
            description = "Our studio offers a refined, private, and immersive tattoo experience- where creativity, craftsmanship, and personal expression take center stage, and our artists are some of the best in the industry.",
            tags = """["ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "artists_row", name = "Artists’ Row",
            lat = 42.5197422, lng = -70.8944557,
            address = "24 New Derby Street, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-619-5685",
            website = "https://www.salemma.gov/artists-row",
            description = "Artists' Row offers uniquely handcrafted products by local artisans in maker/gallery spaces. Classes, demonstrations, workshops. May - November. Originally built as a marketplace, hosts creative public events for all ages.",
            tags = """["ds_subcat_5", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bell", name = "Bell",
            lat = 42.5221866, lng = -70.8919611,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=attraction"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ben_rekemeyer_photography", name = "Ben Rekemeyer Photography",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://www.BenRekemeyer.com",
            description = "Capturing your time in Salem with a walking photoshoot that visits your choice of the city’s most famous sites including Hocus Pocus movie locations, the Witch House, Chestnut Street, and Salem Common.",
            tags = """["ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bit_bar", name = "Bit Bar",
            lat = 42.520621, lng = -70.8910543,
            address = "278 Derby St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-594-4838",
            website = "https://bit.bar/",
            description = "50+ classic arcade and pinball games. Local craft beer and specialty cocktails. Now on Derby Street with a large patio and new comfort food menu. 21+ after 8 pm.",
            tags = """["ds_subcat_5", "ds_subcat_13", "osm_amenity=bar", "osm_leisure=amusement_arcade"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "charter_street_cemetery_old_burying_point", name = "Charter Street Cemetery/Old Burying Point",
            lat = 42.5211008, lng = -70.8917438,
            address = "Charter Street, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://www.charterstreetcemetery.com/",
            description = "Founded in 1637, Salem’s oldest cemetery is the final resting place of several notable Salem residents. The Charter Street Cemetery Welcome Center is now open in the 17th-century Pickman House next to the memorial.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "craft_witch_diy_crafting_studio", name = "Craft Witch: DIY Crafting Studio",
            lat = 42.5204705, lng = -70.8880673,
            address = "102 Wharf St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "351-235-6610",
            website = "https://www.TheWitcherySalem.com/CraftWitch",
            description = "Get your craft on, Witches! Stop by to make some wands, coffin boxes, runes, wreaths, witch dolls, tarot boxes, charm-casting sets, travel journals, oracle collage cards, pendulum & Ouija boards, witch balls and more! Groups welcome!",
            tags = """["ds_subcat_5", "osm_shop=craft"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "derby_waterfront_district", name = "Derby Waterfront District",
            lat = 42.5217294, lng = -70.8852563,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=attraction"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "first_church_in_salem", name = "First Church in Salem",
            lat = 42.5215981, lng = -70.8993964,
            address = "316 Essex St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-744-1551",
            website = "https://www.FirstChurchinSalem.org",
            description = "Explore the connections of The First Church of Salem, its parishioners, and the Salem Witch Trials of 1692! Tours are available on weekends from August to the first weekend of November! Visit our website for tickets and details. Gathered by the Puritans of Massachusetts Bay Colony in August of 1629, First Church is one of the oldest Protestant churches founded in North America and the first to be governed by congregational polity.",
            tags = """["ds_subcat_5", "hh_venue", "osm_amenity=place_of_worship"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "frankenstein_s_castle", name = "Frankenstein’s Castle",
            lat = 42.520415, lng = -70.8916573,
            address = "288 Derby Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Daily Noon-4pm",
            phone = "978-740-2929",
            website = "https://salemwaxmuseum.com/collections/october-tickets",
            description = "Voted Salem's best haunted house. Step into the wax museum's dark dungeon and witness the horrors of Dr. Frankenstein. The live actors and animatronics aim to scare. Open June-October. Combination, AAA, group tickets available.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "friendship_of_salem", name = "Friendship of Salem",
            lat = 42.5201197, lng = -70.8865678,
            address = "160 Derby Street, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-740-1650",
            website = "https://www.nps.gov/sama/index.htm",
            description = "The National Park Service owns and operates this replica tall ship. Friendship commemorates Salem's role as a center of global trade in post-Colonial America. Tours and activities; seasonal. The Friendship is undergoing restoration work but is open Saturdays and Sundays from 12 pm - 3 pm. see website for updates.",
            tags = """["ds_subcat_5", "osm_tourism=museum", "osm_historic=ship"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "haunt_blackcraft", name = "Haunt Blackcraft",
            lat = 42.5211827, lng = -70.896633,
            address = "259 Essex St, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Monday-Thursday 12-6, Friday & Saturday 12-7, Sunday 12-6",
            phone = "978-943-6066",
            website = "https://hauntblackcraft.com/",
            description = "Haunt Blackcraft is an immersive Haunt experience located inside of Blackcraft Salem. Out of season the Haunt operates as a walkthrough experience allowing you to soak in all the beautiful sets and photo ops our Haunt has to offer. Same day tickets for the walkthrough can be purchased inside of Blackcraft Salem. See a sales associate for details. In season (select dates August-November) the Haunt features Salem's best scare actors complete with movie quality costumes and performances. More information, FAQ's and all pre-sale tickets for dates with scare actors are available at: https://hauntblackcraft.com/",
            tags = """["ds_subcat_5", "osm_tourism=attraction"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "haunted_neighborhood", name = "Haunted Neighborhood",
            lat = 42.5207198, lng = -70.8915831,
            address = "Liberty St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-740-2929",
            website = "https://www.SalemWaxMuseum.com",
            description = "Five iconic Salem attractions, one spirited street. Discover the Salem Wax, Witch Village, Frankenstein’s Castle, and more—plus shops, photo ops, and seasonal events all along historic Derby and Liberty Streets.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "historic_new_england_s_phillips_house", name = "Historic New England’s Phillips House",
            lat = 42.5192657, lng = -70.9025857,
            address = "34 Chestnut St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "(978) 744-0440",
            website = "https://www.HistoricNewEngland.org",
            description = "Visit Historic New England's Phillips House on historic Chestnut Street and learn about the Phillips family and their domestic staff in the early 1900s. Antique carriages and cars. Admission charged.",
            tags = """["ds_subcat_5", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "history_alive_cry_innocent", name = "History Alive / Cry Innocent",
            lat = 42.5209707, lng = -70.894617,
            address = "32 Derby Square, Salem, MA, 01970",
            businessType = "attraction",
            phone = "508-423-4823",
            website = "https://www.historyalivesalem.com/current",
            description = "Critically acclaimed live re-enactment of the witchcraft examination of Bridget Bishop where YOU are the jury. Featured on the Travel Channel, NPR, TLC and more. Multiyear Tripadvisor Certificate of Excellence.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "holmes_haus_photography", name = "Holmes Haus Photography",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "attraction",
            phone = "978-219-6613",
            description = "Dark fine art photography for lovers of the macabre.' Specializing in b&w portrait photography, available for shoots on location or in their North Salem studio. Digitals available/inquire about alternative process options.",
            tags = """["ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "monsters_of_salem", name = "Monsters of Salem",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://monstersofsalem.com/",
            description = "Capture unforgettable moments here in Witch City with one of Monster of Salem’s local photographers. We specialize in artistic cosplay portraits, light painting, romantic proposals, elopements, and unique photo booth experiences. Join us for our Ghost Photo Workshop every Sunday in October or book a magical session today.",
            tags = """["ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "new_england_pirate_museum", name = "New England Pirate Museum",
            lat = 42.5207178, lng = -70.8907862,
            address = "274 Derby Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Mo-Su 10:00-17:00",
            phone = "978-741-2800",
            website = "http://piratemuseum.com/",
            description = "Guided tour featuring adventures of New England’s sea robbers through a recreated dockside village and pirate ship. Daily 10 am–5 pm, May 1–October 31. Extended hours in October. Visa/MC/Dis accepted in-person.",
            tags = """["ds_subcat_5", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_shore_glass_school", name = "North Shore Glass School",
            lat = 42.5182886, lng = -70.9105726,
            address = "16 Proctor St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-306-6981",
            website = "https://www.northshoreglassschool.com",
            description = "NSGS teaches glass blowing, fused glass, sandblasting, stained glass, and flameworking. Visit us in our new Artist's Row location at 24 New Derby Street or at our glassblowing location on Gallows Hill at 16 Proctor Street.",
            tags = """["ds_subcat_5", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_shore_pilates_and_fitness_studio", name = "North Shore Pilates & Fitness Studio",
            lat = 42.5122777, lng = -70.90006509999999,
            address = "63 Jefferson Ave, Salem, MA, 01970",
            businessType = "attraction",
            phone = "323-972-1363",
            website = "https://www.northshorepilatesandfitnessstudio.com",
            description = "Take care of your mind, body and soul while in Salem. Join us for a Reformer, Mat or Springboard class, a once a month Full Moon Meditation session or our weekly Kid's Rave.",
            tags = """["ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_street_tattoo", name = "North Street Tattoo",
            lat = 42.5268034, lng = -70.9010903,
            address = "116 North St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-594-0120",
            website = "https://www.NorthStreetSalem.com",
            description = "We host Ink Master stars Jimmy Snaz and Kyle MacKenzie, alongside a cast of professional artists, and rotating guests, guaranteeing a world class environment. We supply expert tattooing in all styles. Open 7 days a week.",
            tags = """["ds_parent_4", "osm_shop=tattoo"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "off_cabot", name = "Off Cabot",
            lat = 42.5481285, lng = -70.8794388,
            address = "9 Wallis St, Beverly, MA, 01915",
            businessType = "attraction",
            website = "https://offcabot.org/",
            description = "Off Cabot Comedy & Events, presented in partnership with The Cabot and John Tobin Presents, is an electrifying escape from reality. Located just blocks away from The Cabot at 9 Wallis Street in downtown Beverly, this 150-seat venue hosts some of the hottest comedians on the scene today, plus live music, open mics, eclectic variety programming, and more!",
            tags = """["ds_parent_4", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "old_town_hall", name = "Old Town Hall",
            lat = 42.5208842, lng = -70.89463,
            address = "32 Derby Square, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://www.salemma.gov/old-town-hall",
            description = "Salem's Old Town Hall is a Federal style building, erected in 1816 and namesake of the Old Town Hall National Historic District. This space is no longer used as a space for municipal operations but is instead utilized as a cultural and rental facility in service to the community of Salem, MA. Old Town Hall is a beautiful federal style building built in 1816, located in the historic heart of downtown Salem. The two-story brick building features Palladian windows, carved wood details, antique chandeliers, decorative columns, hardwood floors. A do-it-yourself venue that gives you the palette to create your special day. Old Town Hall has an elevator and is handicapped accessible. The building is not air conditioned. The first floor space the “Colonnade” presents wooden floors, large windows, and two rows of white pillars with lots of old world charm. The Main floor can accommodate cocktail parties and receptions for up to 100 people. The second story of Old Town Hall, the “Great Hall” is a breathtaking room with large Palladian arached windows. The Great Hall is the perfect location for ceremonies, dinners, receptions and special events hosting up to 100 people.",
            tags = """["ds_subcat_5", "ds_subcat_741", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_essex_museum", name = "Peabody Essex Museum",
            lat = 42.5215405, lng = -70.892209,
            address = "161 Essex Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Th-Mo 10:00-17:00",
            phone = "866-745-1876",
            website = "https://www.pem.org/",
            description = "Founded in 1799 by Salem sea captains, the Peabody Essex Museum (PEM) shares a global array of art, science, history and culture, including a 200-year old Chinese home. Host your corporate event, banquet, or wedding in an unforgettable artistic setting with the Peabody Essex Museum’s variety of indoor and outdoor spaces.",
            tags = """["ds_subcat_5", "hh_venue", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pickering_wharf", name = "Pickering Wharf",
            lat = 42.519787, lng = -70.8876099,
            address = "86 Wharf St, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://www.SalemWaterfrontHotel.com",
            description = "Pickering Wharf is the premier waterfront shopping, dining and lodging destination in Salem. Located directly on Salem Harbor, it is home to both the Salem Waterfront Hotel & Suites and the Pickering Wharf Marina.",
            tags = """["ds_subcat_5", "ds_subcat_8", "osm_tourism=attraction"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pioneer_village", name = "Pioneer Village",
            lat = 42.505735, lng = -70.888912,
            address = "West Avenue, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-744-8815",
            website = "https://www.pioneervillagesalem.org/",
            description = "Pioneer Village is the oldest living history museum in America and is designed to represent Salem in the year 1630. Open June–September, Saturdays and Sundays. Tickets can be purchased at the gate or online.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "professor_spindlewink_s_world_of_wizardry", name = "Professor Spindlewink’s World of Wizardry",
            lat = 42.52168, lng = -70.8940764,
            address = "194 Essex St, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Open daily April through November",
            phone = "978-224-2312",
            website = "http://worldofwizardrysalem.com/",
            description = "Journey into magic realms and discover an enchanted forest, a crystal cave, a room of magic beasts and even an Ice dragon. A must see for lovers of fantasy magic. Family Friendly!",
            tags = """["ds_subcat_5", "hh_venue", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "punto_urban_art_museum", name = "Punto Urban Art Museum",
            lat = 42.5185089, lng = -70.8907546,
            address = "91-1 Peabody St, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://puntourbanartmuseum.org/",
            description = "Located in the Point Neighborhood, Punto Urban Art Museum is a beautiful open-air museum that features 75 large-scale murals by 30 world-renowned artists and 25 local artists, all within a three-block radius. Only a five-minute walk from downtown and free to explore, this museum believes that public art can strengthen communities and help break down invisible socio-economic barriers. Special Exhibitions Punto Urban Art Museum also offers an assortment of special exhibitions that you should check out. A Dream Called Home - Inspired by award-winning, Mexican-American author Reyna Grande’s memoir, artists have painted murals in Salem to share their stories that reflect and/or connect with the life/home that communities of color continue to pursue in this country. Muñecas Sin Rostro - The Faceless Dolls Project is a creative and educational arts initiative inspired by the traditional Faceless Dolls made by Liliana Mera Lime. You’re invited to create a unique, faceless doll to share an untold story about you or your family, honor women of the past and present, and celebrate BIPOC stories and culture. The American Dream - Inspired by the ethos of the United States of America, this exhibit aims to provoke deeper conversations about the constant quest for a better life. As the meaning and attainability of this dream has been called into question in recent years, New England-based artists have been called on to use their creativity to develop murals based on their interpretation of “The American Dream.” The Casa De Abuela Project The latest art project set up by Punto Urban Art Museum, Casa de Abuela (Grandma’s House) is a replica of a typical Dominican country house. Within the four simple walls and sheltered by a tin roof, the main space within Casa de Abuela will reveal a realistic living room scene like a humble Dominican grandmother would reside in. Casa de Abuela is meant to offer innovative opportunities to help engage the community, activate a community parcel, and create economic development. Connected to the house will be an area reserved for pop-up cultural experiences, like a convenience store similar to what you would find in the Dominican Republic, and other elements will be added to the program over time. As a whole, Casa de Abuela acts as a physical space for visitors and residents to celebrate their culture, remember their roots through storytelling, and have opportunities to share various cultural foods.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "re_max_beacon", name = "RE/MAX Beacon",
            lat = 42.5183923, lng = -70.895087,
            address = "225 Washington St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-219-0011",
            website = "https://www.REMAXBeaconMA.com",
            description = "RE/MAX Beacon is a community-centric real estate brokerage with four offices on the North Shore MA, offering expert guidance, cutting-edge technology, and a service-first philosophy to help buyers, sellers, and landlords.",
            tags = """["ds_parent_4", "osm_office=estate_agent"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "real_pirates_salem", name = "Real Pirates Salem",
            lat = 42.5196361, lng = -70.8913107,
            address = "285 Derby St, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Winter Hours: Open Thurs thru Sun in January, February and March; Available 7 days per week for groups of 10 or more by appointment via info@realpiratessalem.com or chat at www.realpiratessalem.com",
            phone = "978-259-1717 x 104",
            website = "https://realpiratessalem.com/",
            description = "Dead men tell no tales, but we do! Experience history through real treasure and real stories... discover pirate artifacts recovered from the shipwrecked Whydah, lost for nearly 300 years. Host your wedding or vows renewal at Real Pirates Salem amid the artifacts of the Whydah Gally. Packages for up to 20 guests can include costumes, photos, refreshments and more! Email info@realpiratessalem.com or call (978) 259-1717 ext. 104.",
            tags = """["ds_subcat_5", "ds_subcat_741", "ds_subcat_16", "osm_tourism=attraction"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_armory_regional_visitor_center", name = "Salem Armory Regional Visitor Center",
            lat = 42.5227268, lng = -70.8919824,
            address = "2 New Liberty Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Open Wednesday - Sunday from 9:30am until 4:30 pm",
            phone = "978-740-1650",
            website = "https://www.nps.gov/sama/index.htm",
            description = "Located at 2 New Liberty Street across from the Peabody Essex Museum and the Museum Place Garage, a central location to all the museums, restaurants, and attractions, and information available, the Visitor Center is the perfect place to begin your tour of Salem. Portable toilets are currently available next to the Visitor Center, and indoor restrooms are accessible during operating hours. History and Culture The Salem Visitor Center, originally known as the Salem Armory, was the headquarters and training facility for the Second Corps of Cadets, tracing back roots to the 18th century. When fire destroyed the building in 1982, the remaining drill shed was converted into the Salem Armory Visitor Center in 1994. At the center, National Park staff and volunteers share information on Essex County and the area’s special historic sites.",
            tags = """["ds_subcat_5", "ds_restroom", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_arts_association", name = "Salem Arts Association",
            lat = 42.5198083, lng = -70.8881949,
            address = "88 Wharf St, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Open weekends 12-6PM<br />",
            phone = "978-745-4850",
            website = "http://www.salemarts.org",
            description = "Salem Arts Association is a nonprofit community arts organization located in Salem’s historic waterfront, nestled among the shops and restaurants of Pickering Wharf. Our galleries and gift shop offer affordable locally created fine art, crafts, jewelry, photography, unique gifts and more. Our mission is to bring art in all its forms to the community and to welcome the community in all its diversity to the arts in Salem, Massachusetts. We host regular exhibitions, workshops, and community events that celebrate local talent and foster creative community engagement, ensuring that everyone has an opportunity to enjoy our vibrant local arts scene. Founded in 2007, we serve as an inclusive cultural hub for artists and art enthusiasts alike. Visit us weekends 12pm - 6pm",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_athenaeum", name = "Salem Athenaeum",
            lat = 42.5204377, lng = -70.9004821,
            address = "337 Essex Street, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-744-2540",
            website = "http://SalemAthenaeum.net",
            description = "Salem’s original library established in 1760, the Salem Athenaeum welcomes you to visit our exhibition, browse the collection, attend an event or workshop, and have a quiet moment in the reading rooms and garden.",
            tags = """["ds_subcat_5", "hh_venue", "osm_amenity=library"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_city_hall", name = "Salem City Hall",
            lat = 42.5218476, lng = -70.8954081,
            address = "93 Washington St, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://www.salemma.gov/",
            description = "Salem City Hall has stood at the heart of civic life since 1837. Designed in the Greek Revival style, the building has hosted generations of local government, public gatherings, and historic moments - making it both a working city hall and a lasting symbol of Salem’s evolving story.",
            tags = """["ds_parent_4", "osm_amenity=townhall"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_heritage_trail", name = "Salem Heritage Trail",
            lat = 42.5215037, lng = -70.8949406,
            address = "Essex Street, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://www.SalemHeritageTrail.org",
            description = "In the 1980’s, the Salem Heritage Trail was born to provide visitors a self-guided walking tour of Salem’s historic sites. The trail is meant to bridge connections between the earliest settlers in Salem, local Indigenous Peoples, Colonial Salem and the Witch Trials, Industrial Heritage, Abolitionism and African American stories, Immigrant Experiences, and more. You’ll find a mix of historical sites and commercial and tourist attractions when walking the trail. In 2020, Salem revised the Salem Heritage Trail, removing its references to its original offensive “Red Line” history to promote more inclusivity within the downtown area and beyond. Learn more at SalemHeritageTrail.org",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_horror_fest_2026", name = "Salem Horror Fest 2026",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://salemhorror.com/",
            description = "Salem Horror Fest is a celebration of diverse voices and innovative filmmaking in a genre that best reflects our cultural demons and anxieties. We currently in are in our 9th year and can’t wait to scratch the surface of what our programming has to offer. Screening venues include: PEM, SATV, DWYBO, Hawthorne Hotel, Notch.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_maritime_national_historic_park", name = "Salem Maritime National Historic Park",
            lat = 42.5218822, lng = -70.8863392,
            address = "160 Derby Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "The visitor center is open Wednesday-Sunday, 9:30 am - 4:30 pm.",
            phone = "978-740-1650",
            website = "http://NPS.gov/Sama",
            description = "Please note programming and interior access to buildings is currently unavailable. The grounds of Derby Wharf are accessible. This National Park preserves one of America's most influential ports, Historic buildings, wharves, and the vessel Friendship; describe the traders and sailors who brought the riches of the Far East to America. See website or contact the Salem Visitor Center at 978-740-1650 for the most up to date tour schedules and movie times: Derby House Tour The 1762 Derby House was the first home of Elias Hasket and Elizabeth Crowninshield Derby. It is a beautiful example of a merchant’s Georgian home and is furnished to reflect the Derbys’ 20 year-long residence in the house. Guided tours led by National Park Rangers are free, limited to 8 persons and require reservations. Call (978) 740-1650 or stop by the Salem Visitor Center between at 2 New Liberty Street on the day of your visit to make your reservation. Tours begin at Waite and Peirce, 193 Derby Street. Narbonne House Self-Guided Tour Built in 1675, this free self-guided tour takes visitors through nearly 300 years of history as a home of successful businessmen and their families. Staffed by National Park Rangers or Volunteers, the Narbonne House is unfurnished, and contains displays of some of the nearly 150,000 archaeological artifacts, which were excavated from the back yard. US Custom House, Public Stores, and Scale House Self-Guided Tours Visit the U.S. Custom House, Public Stores, and Scale House for free self-guided tours and talk with National Park Rangers or Volunteers. Built in 1819 to house the offices of the U.S. Customs Service, Salem collected millions of dollars of taxes on incoming cargo, providing vital financial support for the new United States government. The building was also the workplace of the famous author Nathaniel Hawthorne. The Public Stores and Scale House have exhibits showcasing global trade in Salem. \"Where Past is Present\" A 27-minute film on the history of Essex County featuring early settlement, maritime and industrial history. \"Salem Witch Hunt: Examine the Evidence\" A 35-minute documentary style film on the history of the Salem Witch Trials of 1692.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_museum_of_torture", name = "Salem Museum of Torture",
            lat = 42.52327529999999, lng = -70.8956769,
            address = "30 Federal St, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Wednesday-Monday 10:00 A.M. - 6:00 P.M. <br />\nOctober 7 days 10:00 A.M. - 6:00 P.M.",
            website = "https://salemtorture.com/",
            description = "Step inside a historical museum showcasing authentic instruments of torture and life-size displays of criminal punishments. This eerie exhibit will make you appreciate modern humanity, if you’re brave enough to enter.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_state_center_for_creative_and_performing_arts", name = "Salem State Center for Creative and Performing Arts",
            lat = 42.5027347, lng = -70.8907073,
            address = "352 Lafayette Street, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-542-7890",
            website = "http://SalemState.edu/Arts",
            description = "Experience world-class guest artists and award-winning student and faculty performances in theatre, dance, music, creative writing, and visual art.",
            tags = """["ds_subcat_5", "ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_wax_a_halloween_experience", name = "Salem Wax: A Halloween Experience",
            lat = 42.520415, lng = -70.8916573,
            address = "288 Derby Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Daily 10am-8pm",
            phone = "978-740-2929",
            website = "https://salemwaxmuseum.com/",
            description = "Where Salem’s past and Halloween meet. Explore lifelike exhibits, cobblestone streets & hands-on fun. Part of Halloween Pass & Wicked Special. Discounts available.",
            tags = """["ds_subcat_5", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_willows", name = "Salem Willows",
            lat = 42.5347557, lng = -70.8693627,
            address = "167 Fort Avenue, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-745-0251",
            website = "https://willowsarcade.com",
            description = "The Salem Willows is a scenic, seaside public park. One of Salem's Treasures, Salem Willows is a great way to spend the day. With a video arcade, the best popcorn in New England, a picnic area, and a seaside promenade, you won't want to leave. The Salem Willows is open year-round to the public. The amusement portion of the park operates seasonally and consists of various privately owned businesses, where hours of operation may vary.",
            tags = """["ds_subcat_5", "ds_subcat_8", "ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_witch_hunt_myths_and_misconceptions", name = "Salem Witch Hunt: Myths & Misconceptions",
            lat = 42.5227268, lng = -70.8919824,
            address = "2 New Liberty Street, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-224-2036",
            website = "https://www.essexheritagetours.org/",
            description = "Discover the true story of the Witch Trials in this 36-minute documentary. Featuring authentic dialogue and scholarly commentary, the film uncovers new insights into one of Salem’s darkest chapters. Tours Available Seasonally",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "spirits_of_salem_museum", name = "Spirits of Salem Museum",
            lat = 42.521582, lng = -70.895843,
            address = "234 Essex St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "(978) 412-2245",
            website = "https://www.spiritsofsalem.com/",
            description = "Salem’s newest, must-see immersive experience! An eerie, enchanting, family-friendly journey through Salem’s true history, told by the Spirits themselves in their own words alongside genuine relics of Salem’s bewitching past!",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "st_peter_s_church_historic_presentations_and_tours", name = "St. Peter’s Church Historic Presentations and Tours",
            lat = 42.5229684, lng = -70.8928811,
            address = "24 St Peter St, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Service Times<br />\nSundays<br />\n9:00am in English<br />\n12:30pm in Spanish<br />\nLast Sunday of each month one bilingual service at 10:30am<br />\nWednesdays<br />\n7:00pm online Bible Study<br />\nFirst Thursdays<br />\n7:00pm Taizé Service",
            phone = "(978) 745-2291",
            website = "https://www.stpeters-sanpedro.org/",
            description = "From our play The Making of a Witch, the true account of our Anglican founder (and accused witch), to our Revolutionary War stories and beyond, learn about 300 years of Salem history at beautiful, historic St. Peter’s.",
            tags = """["ds_subcat_5", "hh_venue", "osm_amenity=place_of_worship"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "stained_hourglass_escapes", name = "Stained Hourglass Escapes",
            lat = 42.5190807, lng = -70.8949307,
            address = "207 Washington St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-607-4273",
            website = "https://www.StainedHourglass.com",
            description = "Find clues, solve puzzles, and uncover secret passages: you are the heroes in your own fantasy adventure! Embark on a private one-hour quest in downtown Salem’s award-winning family-friendly escape rooms.",
            tags = """["ds_subcat_5", "osm_leisure=escape_game"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_good_witch_of_salem_spell_studio", name = "The Good Witch of Salem Spell Studio",
            lat = 42.5212607, lng = -70.8974297,
            address = "281 Essex St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-594-4026",
            website = "https://www.goodwitchofsalem.com",
            description = "Step into the Good Witch of Salem Spell Studio, an immersive storybook experience where families can brew potions, decorate brooms and hats, or transform into a magical witch or wizard. Just a block from our original shop, the Spell Studio also offers crystals, retail treasures, and family-friendly fun. Rooted in our mission of helping children discover their inner magic, every activity supports social and emotional learning (SEL) through creativity, play, and imagination.",
            tags = """["ds_parent_4", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_pickering_house", name = "The Pickering House",
            lat = 42.5186359, lng = -70.9002651,
            address = "18 Broad St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-744-4777",
            website = "https://PickeringHouse.org",
            description = "Step into America's oldest home, where 365 years of history unfold. Built in 1660, this evolving landmark tells the story of generations who shaped Salem, from patriots to statesmen. Discover timeless charm & rich heritage.",
            tags = """["ds_subcat_5", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_satanic_temple_salem_art_gallery", name = "The Satanic Temple | Salem Art Gallery",
            lat = 42.5313838, lng = -70.8891105,
            address = "64 Bridge Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Tuesday–Saturday, 11:00am–5:00pm<br />\nSunday, 12:00–5:00pm<br />\nMonday, CLOSED",
            phone = "857-523-2200",
            website = "https://www.salemartgallery.com/",
            description = "Once a funeral home, The Satanic Temple’s headquarters hosts the Salem Art Gallery, replete with museum-quality artwork, artifacts, a library, and the breathtaking 8 1⁄2 ft tall bronze statue of Baphomet.",
            tags = """["ds_subcat_5", "osm_tourism=gallery", "osm_amenity=place_of_worship"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_witchery", name = "The Witchery",
            lat = 42.5196253, lng = -70.887944,
            address = "86 Wharf St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-914-8858",
            website = "https://thewitcherysalem.com/",
            description = "The Witchery is a hands-on creative space offering broom making, bookbinding, witchy, DIY crafting, live performances and tarot readings!",
            tags = """["ds_subcat_5", "hh_venue", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "thistle_piercing_and_fine_jewelry", name = "Thistle Piercing & Fine Jewelry",
            lat = 42.517286, lng = -70.8952396,
            address = "17 Canal St, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-740-4619",
            website = "https://www.ThistlePiercing.com",
            description = "Thistle is Salem’s first luxury piercing-only studio, offering curated piercings and fine jewelry in titanium and gold. Inclusive and design-forward, with a goth aesthetic rooted in Salem’s spirit of transformation.",
            tags = """["ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wicked_new_england_halfway_to_halloween_festival", name = "Wicked New England Halfway to Halloween Festival",
            lat = 42.5209343, lng = -70.894629,
            address = "32 Derby Square, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://www.wickednewengland.info/",
            description = "For those who can’t wait for spooky season, “Wicked New England” is bringing its Halfway to Halloween Festival back to “The Witch City”, Salem, Massachusetts. The event will once again pack both floors of Old Town Hall, and will be creeping out into Derby Square for the first time. The hall will be filled with a curated vendor market featuring spooky vendors from every New England state and beyond, stage presentations by New England Halloween businesses and experiences, photo ops with iconic characters, monsters lurking and more. This second-year event will occur on Sunday, April 19, 2026, from 12:00pm-7:00pm. The Marketplace features vendors from every New England state and beyond with their spooky, scary, macabre and Halloween-themed merchandise for all to enjoy. From apparel to candles, jewelry, decorations, artwork, pet accessories, bath goods, apothecary items and more, every Halloween-loving guest is sure to find something to take home. Heirloom Hallowe’en will be debuting their traveling museum showcasing the rich history of Halloween traditions and decorations. They will be decorating Old Town Hall’s stage with artifacts from their diverse collection, featuring antique jack-o’-lanterns, paper decorations, party games, postcards, and other historic treasures from Halloweens past. They will share the history of select artifacts as part of a Stage Presentation. Additional Stage Presentations include discussions with Halloween New England as they share family-friendly Halloween offerings in New England, a “do it yourself” demonstration from beloved haunted house attraction Barrett’s Haunted Mansion, an offering from Witch City Broom Co., tales of real-life ghostly encounters from Wicked Ghost Hunters, a mass wedding vow renewal with BORAH! from Salem’s Black Hat Society where anyone in attendance can take part, a peek behind the scenes with “Hocus Pocus Live: An All New Parody Musical” presented by Summer Orlando Productions, and more! Salem’s own BORAH! The Witch, as seen on “America’s Got Talent”, and the cast of “Hocus Pocus Live: An All New Parody Musical” will be in attendance for the entirety of the event to meet with fans at their photo ops. The latter are especially fitting as Old Town Hall was used for exterior shooting in the Disney movie “Hocus Pocus”. New this year is the opportunity for children in attendance to trick-or-treat from participating vendors. This is a free offering for any child enjoying the event. They’ll be able to pick up a trick-or-treat bag at the event entrance. Children four and under attend free with a paid adult. Tickets are now on sale for this one-day event. General Admission is only \$11 and can be purchased on our site. This event is sponsored by Witch City Walking Tours and Witch City Broom Co.",
            tags = """["ds_parent_4"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_broom_making_workshop", name = "Witch City Broom Making Workshop",
            lat = 42.5210083, lng = -70.8945602,
            address = "32 Derby Square, Salem, MA, 01970",
            businessType = "attraction",
            phone = "781-608-6986",
            website = "https://WitchCityWalkingTours.com",
            description = "Sign up for an exclusive Broom Making Workshop on our website! Gather Witches and Warlocks! Choose unique colors, charms, and ribbons to craft a magical broom keepsake for your home or altar. Add protective elements or good luck symbols and take a bit of Salem home with you!",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_comedy", name = "Witch City Comedy",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "attraction",
            phone = "631-827-2222",
            website = "https://www.Instagram.com/WitchCityComedy",
            description = "Experience the fast growing comedy scene in Salem, Witch City Comedy! Founded by Allie Del Franco, 250 comedy events a year happen across 20 local iconic Salem venues including Satanic Temple, Hallowed Ground, and Bit Bar.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_pix", name = "Witch Pix",
            lat = 42.5219858, lng = -70.8931084,
            address = "172 Essex Street, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Thursday - Sunday 11am - 5pm<br />",
            phone = "978-745-2021",
            website = "http://www.witchpix.com",
            description = "Create memories in Salem’s award-winning witch photo studio! Choose from 400+ costumes for a guided shoot on 1 of 4 sets with expert stylists and photographers. Book online for a unique journey into fun, role play, and portraiture.",
            tags = """["ds_subcat_5", "hh_venue", "osm_craft=photographer"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ye_olde_pepper_candy_companie", name = "Ye Olde Pepper Candy Companie",
            lat = 42.5224812, lng = -70.8848117,
            address = "122 Derby St, Salem, MA, 01970",
            businessType = "attraction",
            hours = "Monday-Saturday: 10:00 a.m. - 5:00 p.m. <br />\nSundays: 10:00 a.m. - 5:00 p.m.",
            phone = "978-745-2744",
            website = "http://www.oldepeppercandy.com/",
            description = "Got a sweet tooth? Visit America's oldest candy company with a little history in every bite!",
            tags = """["ds_subcat_5", "osm_shop=confectionery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cat_witch", name = "Cat Witch",
            lat = 42.5173501, lng = -70.8897112,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://puntourbanartmuseum.org/mural/64-1-2-harbor-st/",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "communion_with_us", name = "Communion with Us",
            lat = 42.5185629, lng = -70.8910618,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://puntourbanartmuseum.org/mural/communion-with-us/",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "crabs", name = "Crabs",
            lat = 42.5187667, lng = -70.8941932,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "equality", name = "Equality",
            lat = 42.5183119, lng = -70.8934821,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "garden_boy", name = "Garden Boy",
            lat = 42.5182951, lng = -70.8922229,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://puntourbanartmuseum.org/mural/garden-boy/",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "goldfish", name = "Goldfish",
            lat = 42.518994, lng = -70.8945298,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "life_is_sweet", name = "Life is Sweet",
            lat = 42.5183003, lng = -70.8935974,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "love_child", name = "Love Child",
            lat = 42.5184906, lng = -70.8917793,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://puntourbanartmuseum.org/mural/24-peabody-st/",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "migrar", name = "Migrar",
            lat = 42.5181353, lng = -70.8934284,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "our_lady_guadelupe", name = "Our Lady Guadelupe",
            lat = 42.5181362, lng = -70.8932971,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pride_identity", name = "Pride/Identity",
            lat = 42.51824, lng = -70.8938851,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "super_dali", name = "Super Dali",
            lat = 42.5184376, lng = -70.8928059,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://puntourbanartmuseum.org/mural/6-peabody-st/",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "untitled", name = "Untitled",
            lat = 42.5183002, lng = -70.8937241,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "verduras", name = "Verduras",
            lat = 42.518884, lng = -70.8938946,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "viking_statue", name = "Viking Statue",
            lat = 42.5053822, lng = -70.8945831,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "villa_alegra", name = "Villa Alegra",
            lat = 42.5180898, lng = -70.8932902,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://puntourbanartmuseum.org/mural/villa-alegria/",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "window_phone", name = "Window Phone",
            lat = 42.5183255, lng = -70.8933478,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_tourism=artwork"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "alumni_field", name = "Alumni Field",
            lat = 42.5046243, lng = -70.8940385,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=pitch"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "batting_cage", name = "Batting Cage",
            lat = 42.5071339, lng = -70.8858046,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=pitch"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gasset_fitness_center", name = "Gasset FItness Center",
            lat = 42.5051666, lng = -70.8946078,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=fitness_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hawthorne_cove_marina", name = "Hawthorne Cove Marina",
            lat = 42.5214988, lng = -70.8819634,
            address = "Salem, MA",
            businessType = "attraction",
            website = "https://shmarinas.com/locations/safe-harbor-hawthorne-cove/",
            tags = """["osm_leisure=marina"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jubilee_yacht_club", name = "Jubilee Yacht Club",
            lat = 42.5400286, lng = -70.878934,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=marina"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "kernwood_country_club", name = "Kernwood Country Club",
            lat = 42.5397258, lng = -70.9038705,
            address = "1 Kernwood Street, Salem, 01970",
            businessType = "attraction",
            phone = "978 745 1210",
            website = "https://www.kernwoodcc.org/",
            tags = """["osm_leisure=golf_course"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "oconnor_a", name = "O'Connor A",
            lat = 42.5162939, lng = -70.9293611,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=pitch"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "oconnor_b", name = "O'Connor B",
            lat = 42.5171134, lng = -70.9294812,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=pitch"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "okeefe_sports_complex", name = "O'Keefe Sports Complex",
            lat = 42.5050185, lng = -70.8950741,
            address = "225 Canal Street, Salem, MA, 01970",
            businessType = "attraction",
            website = "https://www.salemstatevikings.com/information/okeefe-complex",
            tags = """["osm_leisure=sports_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "olde_salem_greens", name = "Olde Salem Greens",
            lat = 42.5015297, lng = -70.9084613,
            address = "75 Willson Street, Salem, MA, 01970",
            businessType = "attraction",
            phone = "978-744-2149",
            website = "http://oldesalemgreens.com/",
            tags = """["osm_leisure=golf_course"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "palmer_cove_yacht_club", name = "Palmer Cove Yacht Club",
            lat = 42.5139373, lng = -70.8870776,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=marina"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "planet_fitness", name = "Planet Fitness",
            lat = 42.5024033, lng = -70.9204264,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=fitness_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_ymca", name = "Salem YMCA",
            lat = 42.5218528, lng = -70.8979953,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=fitness_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tennis_courts", name = "Tennis Courts",
            lat = 42.5340609, lng = -70.8718784,
            address = "Salem, MA",
            businessType = "attraction",
            tags = """["osm_leisure=pitch"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Haunted Attractions (5)
    // ===================================================================

    val attractionHaunted = listOf(
        OutputBusiness(
            id = "chambers_of_terror", name = "Chambers of Terror",
            lat = 42.5204835, lng = -70.8885617,
            address = "57 Wharf St, Salem, MA, 01970",
            businessType = "attraction_haunted",
            phone = "973-876-2789",
            website = "https://www.chambersofterror.net",
            description = "Scariest Haunt in Salem, undisputed. Live monsters around every corner bring the Terror. Nothing will touch you. Daily Sept. & Oct. After hours Fireside Ghost Stories available. Get tickets on website or call for show times.",
            tags = """["ds_subcat_5", "osm_tourism=attraction"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "count_orlok_s_nightmare_gallery", name = "Count Orlok’s Nightmare Gallery",
            lat = 42.521452, lng = -70.8947819,
            address = "217 Essex Street, Salem, MA, 01970",
            businessType = "attraction_haunted",
            phone = "978-740-0500",
            website = "https://www.nightmaregallery.com/",
            description = "Bigger, better and ever-expanding, Salem’s TOP RATED and ONLY Horror Museum, filled with life-sized creatures and characters from 100+ years by Hollywood SPFX artists! A unique must-stop for all genre fans! Gift shop on-site!",
            tags = """["ds_subcat_5", "hh_venue", "osm_tourism=attraction", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gallows_hill_museum_theatre", name = "Gallows Hill Museum/Theatre",
            lat = 42.5221296, lng = -70.8966569,
            address = "7 Lynde Street, Salem, MA, 01970",
            businessType = "attraction_haunted",
            phone = "978-825-0222",
            website = "http://www.gallowshillsalem.com",
            tags = """["hh_venue", "osm_tourism=museum"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "haunted_witch_village_live_haunt_weekends", name = "Haunted Witch Village: Live Haunt Weekends",
            lat = 42.520558, lng = -70.89114,
            address = "278-282 Derby St, Salem, MA, 01970",
            businessType = "attraction_haunted",
            hours = "Daily 10am-8pm",
            phone = "978-740-2929",
            website = "https://www.WitchVillageSalem.com",
            description = "Salem's longest-running haunted house brings the Witch Village to life on the weekends! Expect live frights and fun! Part of Halloween Pass & Wicked Special. Discounts available.",
            tags = """["ds_subcat_5"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_mansion_haunted_house", name = "Witch Mansion Haunted House",
            lat = 42.5217944, lng = -70.89348,
            address = "186 Essex Street",
            businessType = "attraction_haunted",
            tags = """["osm_tourism=museum"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Ghost Tours (4)
    // ===================================================================

    val tourGhost = listOf(
        OutputBusiness(
            id = "candlelit_ghostly_walking_tours", name = "Candlelit Ghostly Walking Tours",
            lat = 42.520415, lng = -70.8916573,
            address = "288 Derby Street, Salem, MA, 01970",
            businessType = "tour_ghost",
            hours = "Daily at 7pm (No tour July 4th)",
            phone = "978-740-2929",
            website = "https://salemwaxmuseum.com/products/candlelit-ghostly-walking-tour",
            description = "AAA, military, group discounts. Nightly summer tour and extended tours in October. On this hour-long stroll you will light your own path, lantern in hand, and listen to tales of Salem's famous hauntings and histories.One of the longest running walking tours in Salem, we celebrated our 30th anniversary in 2023. This tour runs Monday-Thursday at 6pm and 7pm and Friday- Sunday starting at 5pm every half hour with the last tour departing at 8pm. Meets in the back parking lot of the Salem Wax Museum located at 288 Derby street in Salem. 45 to 60 minutes long this walking tour takes you to approximately 7 different documented haunted historical sites such as the Joshua Ward House, Charter Street Cemetery and the site of Bridge Bishop's apple orchard, telling you both the history and the ghost story that took place. Purchasing tickets in advance is strongly encouraged, but we will also be selling tickets in person Visit website to view full tour schedule and purchase tickets. AAA, military, group discounts. Nightly summer tour and extended tours in October. Adjacent to Old Burying Point",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "specters_and_apparitions_a_ghost_hunting_tour", name = "Specters & Apparitions: A Ghost Hunting Tour",
            lat = 42.5210083, lng = -70.8945602,
            address = "32 Derby Square, Salem, MA, 01970",
            businessType = "tour_ghost",
            phone = "781-608-6986",
            website = "https://www.WitchCityWalkingTours.com",
            description = "Join us for a spine-tingling ghost hunting tour to explore Salem's dark history and haunted hot spots. Using state-of-the-art ghost hunting equipment, come face to face with the supernatural for an unforgettable experience.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "vampire_ghost_adventures", name = "Vampire Ghost Adventures",
            lat = 42.521959, lng = -70.890258,
            address = "20 Hawthorne Blvd, Salem, MA, 01970",
            businessType = "tour_ghost",
            website = "https://www.chambersofterror.net/vampireghostadventures",
            description = "Unlike other tours in Salem! WE take you places and tell you tales others don't! Ghosts, Witches, Vampires, and Poltergeists! Come here the ghostly secrets of Salem you wont hear anywhere else. Did we mention your experienced guide is a vampire? The only walking tour in Salem where you actually enter a building and see haunted objects! Meeting place is the Nathaniel Hawthorne Statue, 20 Hawthorne Blvd. Don't see available tickets? Call us now to schedule an excursion tonight!",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_walking_tours", name = "Witch City Walking Tours",
            lat = 42.5210083, lng = -70.8945602,
            address = "32 Derby Square, Salem, MA, 01970",
            businessType = "tour_ghost",
            phone = "781-608-6986",
            website = "https://www.witchcitywalkingtours.com",
            description = "Salem’s most LOVED tour! Certificate of Excellence multiple years on TripAdvisor. Rated the “Number One Cultural & Historical Tour in the United States” -TripAdvisor 2023 & 2024. Call/text 781-608-6986 for reservations. \"Ranked No. 1 in the U.S. and No. 2 in the world for cultural and historic tours.\" - Trip Advisor 2024",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Tours & Experiences (18)
    // ===================================================================

    val tour = listOf(
        OutputBusiness(
            id = "alijen_charters", name = "AliJen Charters",
            lat = 42.5225143, lng = -70.8827165,
            address = "10 White St, Salem, MA, 01970",
            businessType = "tour",
            phone = "(781)-910-3776",
            website = "https://alijencharters.com/",
            description = "CPT. Dan Grimes USCG 100-Ton (OUPV) First Aid & CPR Certified Growing up on the water with my dad I've been passionate about boating and fishing since I was 6 years old. I worked alongside him on his commercial fishing boat gaining invaluable knowledge and experience out on the ocean. In 2014 I turned my focus to rod and reel fishing, specializing in catching cod, haddock, stripers, and tuna off the coast of Mass. With decades of experience on the water I'm dedicated to proving unforgettable fishing trips for anglers of all levels. It's not about the catch; it's about the adventure!",
            tags = """["ds_subcat_250"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bewitched_historical_tours", name = "Bewitched Historical Tours",
            lat = 42.5220679, lng = -70.8913786,
            address = "131 Essex St, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-498-4061",
            website = "https://www.bewitchedtours.com/",
            description = "“Best Tour in Salem!” Fun and informative tours led by local historians. Learn what really happened in 1692 and visit 12+ sites. Advance purchase is recommended. Tours leave from the Halloween Museum. Advance purchase is recommended via BewitchedTours.com but if tickets are still available they can be purchased at the Halloween Museum.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "black_cat_tours", name = "Black Cat Tours",
            lat = 42.521533, lng = -70.895853,
            address = "234 Essex St, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-239-9145",
            website = "https://www.blackcatsalem.com/",
            description = "Experience captivating true tales of Salem with our daytime historical and ghostly night tours! Voted one of the 10 best ghost tours in the US four years in a row! Tickets are available at the Black Cat Curiosity Shoppe located at 234 Essex Street, and online at www.blackcatsalem.com",
            tags = """["ds_subcat_6", "ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "coast_to_coast_paddle", name = "Coast to Coast Paddle",
            lat = 42.5347557, lng = -70.8693627,
            address = "167 Fort Ave, Salem, MA, 01970",
            businessType = "tour",
            hours = "HOURS<br />\n10am - 6pm<br />\nMemorial Day through Labor Day",
            phone = "978-969-0151",
            website = "https://www.coasttocoastpaddle.com/",
            description = "Discover a different side of Salem with our Salem Paddle Tour! Beyond its famous Witch Trials, Salem boasts a stunning oceanfront perfect for exploration. Glide along its historic coastline, once the world's richest port, and immerse yourself in breathtaking natural beauty and maritime heritage. Ideal for beginners and seasoned paddlers alike, our tours offer a unique adventure through Salem's sheltered coves under the guidance of passionate experts. Join us for an unforgettable journey and make your visit to Salem truly memorable! Coast to Coast Paddle also offers kayak & paddle board lessons, rentals & tours 7 days/week.",
            tags = """["ds_subcat_250"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "forest_lore_tour", name = "Forest Lore Tour",
            lat = 42.5050196, lng = -70.9066127,
            address = "75 Willson St, Salem, MA, 01970",
            businessType = "tour",
            phone = "844-757-5657",
            website = "http://walkwithawitch.com/",
            description = "Explore the crossroads of science, history & folklore on Salem's most unique tour! Experience magical Salem Woods with The Mushroom Witch on this guided, 1-mile trek. Wear hiking shoes & bring your curiosity! Tours meet meet at the (newly restored) trail sign at the back of the parking lot of Olde Salem Greens municipal golf course (75 Willson St). The Forest Lore Tour requires tickets in advance. Tickets can be found through witchwoodtours.com",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lighthouse_and_harbor_tours", name = "Lighthouse & Harbor Tours",
            lat = 42.5218593, lng = -70.8804521,
            address = "10 Blaney St, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-224-2036",
            website = "https://www.bakersislandlight.org/visit.html",
            description = "Explore the treasures of Salem’s islands and coastline! From June-September trips to Bakers Island Light, Misery Island, and specialty tours are available. Overnight stays and camping also available at Bakers Island Light.",
            tags = """["ds_subcat_250"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mahi_harbor_cruises_and_private_events", name = "Mahi Harbor Cruises & Private Events",
            lat = 42.5197365, lng = -70.8900847,
            address = "24 Congress Street, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-825-0001",
            website = "https://mahicruises.com/",
            description = "Cruising daily from downtown Salem, May–October! Mahi offers harbor tours, private events, custom outings, & unique transport options. Groups & buses welcome. Enjoy boat drinks & grillable favorites on every cruise! Mahi Harbor Cruises & Private Events Whether you’re looking for an intimate rehearsal dinner, unique transportation options to get your guests from point A to B, or a casual ceremony and reception at sea, nothing is off limits! Mahi Cruises is able to accommodate events for up to 150 passengers, provide customizable catering, live entertainment, three fully stocked bars and unbeatable views to make your wedding an unforgettable and one-of-a-kind experience. Whether you’re planning for 10 or 150 Mahi has what you need. The Finback is a 50-foot sightseeing boat that provides a fun and intimate setting. Or celebrate in style on the spacious Hannah Glover with a fully heated main deck and an open-air top deck. All events hourly rates are based on cruises departing from Pickering Wharf and include exclusive rights to the boat, captain, crew, bar set up, bar tenders, preparation of the boat set-up, clean-up and assistance loading and unloading. In the rare case that weather hinders your cruise, and a cancellation is necessary, all cruise costs are waived. Contact Annie, Events and Operations, at 978-825-0001 or events@mahicruises.com to discuss pricing and availability.",
            tags = """["ds_subcat_250"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_food_tours", name = "Salem Food Tours",
            lat = 42.5216186, lng = -70.88625789999999,
            address = "159 Derby St, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-594-8811",
            website = "https://www.salemfoodtours.com/",
            description = "“Astounding experience!” “Wickedly fun and delicious!” Since 2012, Salem’s award-winning food and cultural walking tours. Enjoy delicious tastings at local shops and restaurants. Learn about Salem’s illustrious spice trade history, as well as modern day Salem’s thriving modern culinary scene. Tours run year-round. We welcome groups. Featured on Chronicle; multi-year Trip Advisor Certificate of Excellence. 3-hour private excursions for bridal party, wedding party, families, etc. A fun and popular way to celebrate! Our Bridal Tours are perfect for bachelorette and bachelor parties, wedding parties, family experience before or after nuptials, etc. Memorable and bonding, we’ll walk through Salem and touch on our local history as well as the history of food and wedding customs through the ages. This tour includes a cheese, wine, bubbly, chocolate and spice tasting, along with a savory tasting, a second sweet surprise and local artisanal bread. Please email us for availability and pricing, including a bit about your party (bachelorette, family, etc.) estimate on headcount, date(s) choices, and wedding date, info@salemfoodtours.com.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_ghosts_tours_and_haunted_pub_crawls", name = "Salem Ghosts Tours and Haunted Pub Crawls",
            lat = 42.5216076, lng = -70.8946993,
            address = "210 Essex St, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-219-2380",
            website = "https://www.SalemGhosts.com",
            description = "Salem Ghosts is Google’s top-rated and TripAdvisor’s Award-winning tour company, offering unforgettable haunted, historic, and witchcraft experiences in the heart of Salem. Veteran-owned and proudly women- and minority-led, our expert local guides bring Witch City’s most infamous stories to life through immersive storytelling and carefully researched history. Explore Salem’s layered past on our daytime walking tours, from the Secrets of Salem Hidden History Experience to The Witches & Witchcraft of Salem tours, where you’ll uncover hidden history, folklore, and the real events behind the 1692 Witch Trials. Visit iconic locations including the Salem Witch House, the Old Burying Point Cemetery, and the legendary Ropes Mansion—famously featured in Hocus Pocus. When the sun sets, the shadows deepen, as you step straight into haunted history and walk the streets of Salem’s past when you join our signature Ghosts, Witches & Hauntings of Salem Ghost Tour to discover chilling true accounts of tragedy and the unexplained. For those seeking something darker, step into the Echoes of Twilight Dead of Night Ultimate Tour, extended experiences that venture deeper into Salem’s most haunted corners with additional stories and exclusive stops. Looking for spirits of a different kind? Sip spirits, share scares on Salem’s hottest adults-only tour experience. Enjoy boo-sy drinks & uncover ghostly tales at colonial hotspots on the Salem Boos and Booze Haunted Pub Crawl. Fun, racy, more than a li’l spooky—the ultimate boos & booze bash combines haunted history with Salem’s historic taverns and local watering holes. Sip, socialize, and uncover scandalous tales from centuries past in a fun and unforgettable nightlife experience (21+; drinks sold separately). We proudly host: Student and educational groups Bachelorette parties Corporate and VIP private tours Family groups and reunions Custom experiences upon request Planning a New England getaway? Save more when you bundle your experience with tours in Boston, Providence, or at the historic Lizzie Borden House. Whether you’re visiting for the history, the hauntings, or the witches, Salem Ghosts delivers an experience that is engaging, entertaining, and rooted in real stories and real places. Tours depart daily, year-round, from 210 Essex Street in Salem. Call or text (978) 219-2380, or visit salemghosts.com for details and reservations. Book now and experience the history, mystery, and hauntings of Witch City like never before.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_historical_tours", name = "Salem Historical Tours",
            lat = 42.5213427, lng = -70.8937554,
            address = "8 Central Street, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-745-0666",
            website = "https://www.salemhistoricaltours.com/",
            description = "We are Salem's oldest, most respected & recommended tour company, celebrating over 25 years. Local, certified guides provide history tours on witchcraft, maritime and revolutionary history (morning & afternoon) as well ghost/paranormal tours (afternoon & evening). Take our history tour and explore four centuries of maritime and revolutionary history, inventors, pop culture and more. Take our 1692 Witchcraft Walk. Examine the demographics, theories and personal stories comprising the dire events of the witchcraft hysteria that put Salem on the map. Look for our purple sign.",
            tags = """["ds_subcat_6", "hh_venue", "osm_office=guide"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_kids_tours", name = "Salem Kids Tours",
            lat = 42.5215394, lng = -70.8994602,
            address = "316 Essex Street, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-766-1103",
            website = "https://salemkidstours.com/",
            description = "Looking for things to do with kids? We are Salem’s Wicked Walking Tour for children and families. Kids discover history, mystery, and a little magic on the only tour that gives a kids’ eye view of Salem’s captivating past. All of our tour guides are certified Massachusetts educators. Ages 5+. True historical tales that fascinate all ages Interactive storytelling that keeps children engaged Kid-appropriate content you can trust A memorable family experience in Salem Stories you'll be talking about long after the tour ends Find out why parents call us “the best thing we did in Salem” Visit our website to book your tour today, or email Alicia at alicia@salemkidstours.com",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_night_tour", name = "Salem Night Tour",
            lat = 42.5222241, lng = -70.890891,
            address = "127 Essex Street, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-741-1170",
            website = "https://www.salemghosttours.com/",
            description = "Salem's nightly \"Haunt and History Tour.\" Join the adventure every night at 8:00 pm, our licensed guides lead you through legends, history, and the infamous hysteria of 1692. Click here to purchase tickets.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_trolley", name = "Salem Trolley",
            lat = 42.5214701, lng = -70.893872,
            address = "8 Central Street, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-744-5469",
            website = "http://salemtrolley.com/",
            description = "Ride the Red Trolley! Salem’s original trolley tour. Experience nearly 400 years of diverse history in our one-hour narrated tour. Special tours are available. Tales & Tombstones night tours (July, August & October): Dusk transforms the city of Salem. As the city of renowned seafarers and distinguished architecture fades into the shadows, a new Salem of the occult and paranormal emerges. Visit scenes of grisly murders and ghastly executions, hear tales of ghosts, both mischievous and malevolent, of Salem’s haunted hotels and restaurants, of long forgotten underground passageways, ancient curses, and haunted islands. Salem Trolley – Unforgettable and Safe Transportation The Salem Trolley can provide transportation aboard one of their replica turn-of-the-century trolleys. Let them help make your event memorable, safe, and stress-free. Salem Trolley has provided exceptional event transportation for over a quarter of a century. Count on the Salem Trolley to transport your guests in style, comfort and grace. When you book an event, you may decorate the trolley ahead of time in accordance with your particular theme, it’s all possible with the Salem Trolley and your imagination. Visit SalemTrolley.com or call (978)-744-5469 for more information.",
            tags = """["ds_subcat_6", "ds_subcat_741", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_uncovered_tours", name = "Salem Uncovered Tours",
            lat = 42.52049002416123, lng = -70.89423086441803,
            address = "1 Houdini Way, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-791-2131",
            website = "https://www.salemuncovered.com/",
            description = "Join Salem's expert storytellers on a premier walking tour. Learn about the tragic history of the Witch Trials in the afternoon and true tales of Dark History in the evenings. Book your tours direct through Salem Uncovered! https://www.salemuncovered.com/ Tours meet at One Houdini Way.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "schooner_fame_of_salem", name = "Schooner Fame of Salem",
            lat = 42.51971677594507, lng = -70.88763129325407,
            address = "86 Wharf St, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-729-7600",
            website = "https://schoonerfame.com/",
            description = "Sail on the Schooner Fame! Sail on the top-rated boat tour in Salem! Our replica of the 1812 privateer FAME sails 3-4 times daily from Pickering Wharf, right downtown among the shops and restaurants. A hands-on adventure where you can raise the sails, hear the roar of the cannon, and learn about Salem's epic maritime history! A great trip for families and kids, provided they are at least five years old. Drinks and snacks (including beer & wine) sold on board. Book in advance for our popular sunset trips! You haven't seen Salem until you’ve seen it from the water. Click here to view the sailing schedule, and to purchase tickets.",
            tags = """["ds_subcat_250", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "spellbound_tours", name = "Spellbound Tours",
            lat = 42.5215136, lng = -70.8947729,
            address = "213 Essex Street, Salem, MA, 01970",
            businessType = "tour",
            phone = "978-740-1876",
            website = "https://spellboundtours.com/",
            description = "Salem’s original supernatural experience. Top-rated on TripAdvisor! Frightening, historically accurate tours by professional paranormal investigators. Explore the Witch Trials, vampire folklore, and voodoo! Tour meets at Armory Park Bell. Click here to purchase tickets.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sunset_sail_salem", name = "Sunset Sail Salem",
            lat = 42.5218517, lng = -70.8804743,
            address = "10 Blaney St, Salem, MA, 01970",
            businessType = "tour",
            phone = "(305) 697-1024",
            website = "https://www.sailwhenandif.com/",
            description = "Experience luxury, romance and breathtaking views aboard a stunning 1930 schooner yacht with Salem’s truly authentic North Shore sailing experience. Cash bar with RTD Craft Cocktails on board. Sailing all day every day.",
            tags = """["ds_subcat_250"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tipples_and_mash_tours", name = "Tipples and Mash Tours",
            lat = 42.52158, lng = -70.893562,
            address = "185 Essex St, Salem, MA, 01970",
            businessType = "tour",
            website = "https://www.tipplesandmash.com",
            description = "Salem's only walking tour of its breweries, cideries and distilleries. Learn about Salem's brewing past, while sampling beverages from the establishments that are making new history everyday.",
            tags = """["ds_subcat_6"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Restaurants (109)
    // ===================================================================

    val restaurant = listOf(
        OutputBusiness(
            id = "adriatic_restaurant_and_bar", name = "Adriatic Restaurant & Bar",
            lat = 42.5203408, lng = -70.8951733,
            address = "155 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-594-1832",
            website = "https://www.adriaticrestaurantandbar.com/",
            description = "Located in Downtown Salem in the historic Salem News Building, Adriatic is a modern European restaurant featuring fresh fish, seasonal vegetables, homemade pastas, and bread. Outdoor seating and friendly service. Vegan and vegetarian-friendly, gluten-free items are available.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "amazing_pizza", name = "Amazing Pizza",
            lat = 42.520307, lng = -70.8887334,
            address = "62 Wharf St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-594-4102",
            website = "https://www.AmazingPizzaMA.com",
            description = "We are an artisan pizzeria with gourmet pizza, pasta entrees, sandwiches, and fresh salads. Our appetizers are home made along with our desserts. We’re passionate serving high quality food.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "antique_table", name = "Antique Table",
            lat = 42.5197612, lng = -70.8901561,
            address = "26 Congress Street, Salem, MA, 01970",
            businessType = "restaurant",
            website = "https://www.antiquetablerestaurants.com/salem",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "back_alley_bacon", name = "Back Alley Bacon",
            lat = 42.5205199, lng = -70.8914434,
            address = "24 Liberty Street",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bagel_world_ii", name = "Bagel World II",
            lat = 42.5066982, lng = -70.8957504,
            address = "203 Canal Street, Salem, 01970",
            businessType = "restaurant",
            cuisineType = "bakery",
            hours = "Mo-Fr 06:00-15:30; Sa 06:00-15:00; Su 06:00-14:00",
            phone = "978-741-5225",
            website = "https://www.bagelworld.net/",
            tags = """["osm_shop=bakery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bambolina", name = "Bambolina",
            lat = 42.5203705, lng = -70.8916416,
            address = "288 Derby Street",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "barrio_tacos", name = "Barrio Tacos",
            lat = 42.520175, lng = -70.8933323,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bella_verona", name = "Bella Verona",
            lat = 42.5224933, lng = -70.8899756,
            address = "107 Essex",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bernadette", name = "Bernadette",
            lat = 42.5225569, lng = -70.8957337,
            address = "65 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-224-2976",
            website = "https://www.BernadetteSalem.com",
            description = "Bernadette is a French-inspired, market-driven restaurant in downtown Salem, offering classic French dishes, seasonal ingredients, and a warm, elegant atmosphere from husband-and-wife team Aaron and Shanna Chambers.",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bill_and_bobs_roast_beef", name = "Bill & Bob's Roast Beef",
            lat = 42.5357711, lng = -70.8866451,
            address = "9 Bridge Street, Salem, MA",
            businessType = "restaurant",
            hours = "Mo-Su 10:00-02:00",
            website = "https://billandbobs.com/salem-menu/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bill_and_bobs_roast_beef_1", name = "Bill and Bob's Roast Beef",
            lat = 42.5274129, lng = -70.9279126,
            address = "2 Central Street, Peabody, MA, 01960",
            businessType = "restaurant",
            hours = "Mo-Th 10:00-02:00, Fr-Sa 10:00-02:30, Su 10:00-01:30",
            phone = "978-531-9605",
            website = "http://billandbobs.com/peabody.html",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "blue_fez_moroccan_cuisine", name = "Blue Fez Moroccan Cuisine",
            lat = 42.5211555, lng = -70.8961334,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "boston_burger_company", name = "Boston Burger Company",
            lat = 42.520724, lng = -70.8952929,
            address = "133 Washington Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Su-We 11:00-22:00; Th-Sa 11:00-23:00",
            phone = "978-414-5910",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "burger_king", name = "Burger King",
            lat = 42.5038405, lng = -70.9218485,
            address = "259 Highland Avenue, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "06:00-22:00",
            phone = "978-744-9467",
            website = "https://www.bk.com/store-locator/store/restaurant_1980",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "caramel", name = "Caramel",
            lat = 42.5213548, lng = -70.8975213,
            address = "281 Essex Street",
            businessType = "restaurant",
            cuisineType = "bakery",
            tags = """["osm_shop=pastry"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "casa_tequila", name = "Casa Tequila",
            lat = 42.520027, lng = -70.8926392,
            address = "300 Derby Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Su-Th 11:00-22:00; Fr-Sa 11:00-23:00",
            phone = "978-224-2298",
            website = "http://casatequilasalemma.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "chezcasa", name = "ChezCasa",
            lat = 42.5292859, lng = -70.8896952,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "christinas_famous_roast_beef", name = "Christina's Famous Roast Beef",
            lat = 42.5113727, lng = -70.8916053,
            address = "239 Lafayette Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Tu-Su 11:00-20:50",
            phone = "978-744-0700",
            website = "https://www.christinaspizzamenu.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "counter", name = "Counter",
            lat = 42.5215046, lng = -70.8942455,
            address = "209 Essex St, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Thurs: 12pm to 8pm<br />\nFriday: 12pm to 9pm<br />\nSaturday: 10am to 9pm Sunday: 10am to 4pm, bar until 6 pm <br />\nMonday 12 - 8pm",
            phone = "(978) 451-4818",
            website = "https://www.countersalem.com/",
            description = "Located inside The Hotel Salem, Counter is a modern take on a lunch counter, and currently home to our seasonal pop-up, Crust@Counter, serving gourmet flat breads, sandwiches, and more.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "crave", name = "Crave",
            lat = 42.5183935, lng = -70.8930015,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "curly_girl_candy_shop", name = "Curly Girl Candy Shop",
            lat = 42.5207684, lng = -70.8959428,
            address = "140 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            hours = "Open 7 days a week<br />\nMonday - Thursday 11am - 7pm<br />\nFriday & Saturday 11am - 9pm<br />\nSunday 11am - 6pm",
            phone = "978-594-1553",
            website = "http://curlygirlcandy.com/",
            description = "Come & feel like a kid in a candy store! Specializing in “Best Day Ever!” experiences, Curly Girl Candy Shop is a candy heaven in the heart of downtown Salem. With over 120 candy options to choose from, you can fill a bag to fulfill every craving. Find new favorites or be transported back to childhood with the extensive nostalgic candy options. With our wide variety of bulk candy including gummy, sour, chocolate and everything in between, along with a huge selection of jellybeans, bubblegum and gift items, Curly Girl Candy Shop is a truly a one-stop shop for FUN!",
            tags = """["ds_subcat_11", "osm_shop=confectionery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dire_wolf_tavern", name = "Dire Wolf Tavern",
            lat = 42.5220072, lng = -70.8956205,
            address = "87 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Mo-Th 17:00-22:00; Fr 17:00-23:00; Sa 11:30-23:00; Su 11:30-22:00",
            phone = "978-744-9600",
            website = "https://www.direwolfsalem.com/",
            description = "Guests can enjoy tavern favorites with a twist, paired with creative, award-winning sushi and refreshing cocktails. This warm, inclusive tavern serves a fast, casual lunch and dinner nightly.",
            tags = """["ds_subcat_13", "hh_venue", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dotty_and_rays", name = "Dotty and Ray's",
            lat = 42.5267124, lng = -70.9009013,
            address = "112 North Street, Salem, 01970",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dunkin", name = "Dunkin'",
            lat = 42.5033092, lng = -70.8898366,
            address = "352 Lafayette Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "Mo-Fr 07:00-16:00; Sa-Su 00:00-24:00",
            phone = "978-745-1859",
            website = "https://locations.dunkindonuts.com/en/ma/salem/352-lafayette-st/342543",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dunkin_1", name = "Dunkin'",
            lat = 42.5070235, lng = -70.8957412,
            address = "201 Canal Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "05:00-20:00",
            phone = "978-744-6935",
            website = "https://locations.dunkindonuts.com/en/ma/salem/201-canal-st/307633",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dunkin_2", name = "Dunkin'",
            lat = 42.5194994, lng = -70.909869,
            address = "68 Boston Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "Mo-Sa 05:00-19:00; Su 06:00-18:00",
            phone = "+19787418658",
            website = "https://locations.dunkindonuts.com/en/ma/salem/68-boston-st/304216",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dunkin_3", name = "Dunkin'",
            lat = 42.5045213, lng = -70.922465,
            address = "248 Highland Avenue, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "05:00-19:00",
            phone = "978-745-6110",
            website = "https://locations.dunkindonuts.com/en/ma/salem/248-highland-ave/340126",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dunkin_4", name = "Dunkin'",
            lat = 42.5202903, lng = -70.8959026,
            address = "152;154 Washington Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "05:00-19:00",
            phone = "978-744-9888",
            website = "https://locations.dunkindonuts.com/en/ma/salem/152-washington-st/300353",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "engine_house_pizza", name = "Engine House Pizza",
            lat = 42.5196992, lng = -70.8932407,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "finz", name = "Finz",
            lat = 42.5195842, lng = -70.8876087,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "finz_seafood_and_grill", name = "Finz Seafood & Grill",
            lat = 42.5195197, lng = -70.8877558,
            address = "86 Wharf Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-744-8485",
            website = "https://hipfinz.com/",
            description = "Waterfront dining with a seasonal patio. Featuring fresh seafood brought in daily, raw and sushi bars, as well as private function rooms for groups 10-100pp. Overlooking the Salem harbor, Finz is the perfect spot to host your next event from weddings, rehearsal dinners, baby & bridal showers, social gatherings, birthday parties & corporate events. Open seven days a week, 11:30 am–midnight. Reservations accepted.",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "flying_saucer_pizza_company", name = "Flying Saucer Pizza Company",
            lat = 42.5211986, lng = -70.8962507,
            address = "118 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "11:00-23:00",
            phone = "978-594-8189",
            website = "http://flyingsaucerpizzacompany.com/",
            description = "Hand-stretched, fresh dough made in-house daily! Unique pizza topping combinations paired with craft beers in funny digs sporting sci-fi decor. Traditional, vegetarian, vegan, and gluten-free options. Live Long and Pizza!",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fountain_place", name = "Fountain Place",
            lat = 42.5216597, lng = -70.8954645,
            address = "232 Essex Street, Salem, MA, 01970",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "funhouse_donuts", name = "Funhouse Donuts",
            lat = 42.52037139999999, lng = -70.8911054,
            address = "282 Derby St, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            phone = "(978) 893-8497",
            website = "https://www.FunhouseDonuts.com",
            description = "Whimsy meets wonder at Funhouse Donuts! Enjoy wildly creative, ever-changing themed donuts that surprise and delight. No two visits are the same—come back again and again for sweet new magic!",
            tags = """["ds_subcat_11"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ginger_cajun_seafood_and_bar", name = "Ginger Cajun Seafood & Bar",
            lat = 42.5211609, lng = -70.8958029,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ginger_seafood_and_asian_fusion", name = "Ginger Seafood & Asian Fusion",
            lat = 42.5210573, lng = -70.8959684,
            address = "118 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-594-5558",
            website = "https://www.gingerseafood.com/",
            description = "Best Chinese Food and Japanese sushi and ramen. Oysters \$1.25. Tasty Cajun seafood will make your dining experience unforgettable. Karaoke. See website for details.",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "good_night_fatty", name = "Good Night, Fatty",
            lat = 42.5232231, lng = -70.8909928,
            address = "1 Washington Square West, Salem, MA, 01970-4012",
            businessType = "restaurant",
            cuisineType = "dessert",
            hours = "Mo 08:00-12:00; Th 16:00-22:00; Fr-Sa 08:00-23:00; Su 08:00-22:00",
            website = "https://goodnightfatty.com/",
            tags = """["osm_amenity=ice_cream"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gulu_gulu_cafe", name = "Gulu-Gulu Cafe",
            lat = 42.521323, lng = -70.8962821,
            address = "247 Essex St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-740-8882",
            website = "https://www.gulugulucafe.com/",
            description = "Full-service restaurant with a relaxed, bohemian cafe feel in the heart of Salem! Breakfast, gourmet sandwiches, light fare, appetizers, espresso, award-winning craft beer menu, wine, and desserts. Live music. Stay a while.",
            tags = """["ds_subcat_13", "hh_venue", "osm_amenity=cafe"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hallowed_ground", name = "Hallowed Ground",
            lat = 42.52201540000001, lng = -70.8955251,
            address = "87 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-744-9600",
            website = "https://www.HallowedGroundSalem.com",
            description = "Escape underground to a speakeasy with old-school lounge vibes. Enjoy elevated cocktails, a curated beverage program, and a late-night menu that’s perfect for unwinding or indulging in a unique, intimate atmosphere.",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "harbor_sweets", name = "Harbor Sweets",
            lat = 42.5153649, lng = -70.8878952,
            address = "85 Leavitt Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            phone = "978-745-7648",
            website = "https://www.harborsweets.com/",
            description = "Always handmade, our chocolates are the perfect New England gift. Visit our store, watch chocolate being made, enjoy a sample, and shop for something sweet!",
            tags = """["ds_subcat_11", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "holy_cow_ice_cream", name = "Holy Cow Ice Cream",
            lat = 42.5352079, lng = -70.8690842,
            address = "181 Fort Ave, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            website = "https://www.holycowicecreamcafe.com/",
            description = "National award-winning, homemade, and super premium ice cream. Offering creative flavor combinations, house-made mix-ins, and nostalgic treats. Home of the 2022 North American Ice Cream Association’s “Flavor of the Year”.",
            tags = """["ds_subcat_11"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "howling_wolf_taqueria", name = "Howling Wolf Taqueria",
            lat = 42.5193196, lng = -70.8936855,
            address = "76 Lafayette Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Su-Tu 11:30-23:00; We-Sa 11:30-00:00",
            phone = "978-744-9653",
            website = "https://www.howlingwolftaqueria.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ihop", name = "IHOP",
            lat = 42.50225, lng = -70.9240836,
            address = "2 Trader's Way, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Mo-Su 07:00-22:00",
            phone = "+19788259020",
            website = "https://www.ihop.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jamie_s_roast_beef", name = "Jamie’s Roast Beef",
            lat = 42.5356246, lng = -70.9171578,
            address = "44 Margin Street, Peabody, MA, 01960",
            businessType = "restaurant",
            hours = "Tu-Su 11:00-21:00",
            phone = "978 531-9942",
            website = "https://www.jamiesroastbeef.com",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "kakawa_chocolate_house", name = "Kakawa Chocolate House",
            lat = 42.5215937, lng = -70.8931873,
            address = "173 Essex Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            hours = "Th-Su 11:00-18:00",
            phone = "617-548-4567",
            website = "https://kakawachocolates.com/",
            description = "Love chocolate? We are Salem's most unique, delicious chocolate experience offering historic drinking chocolate elixirs (come try a sample!), amazing artisan chocolates, organic ice creams, baked goods and more, all handmade on the premises for you to see. Cafe seating for dine in or takeout available. Many DF, V options are also available. Take a stroll on historic Essex Street. We are directly adjacent to the Peabody Essex Museum. Come by for a Sweet Treat at Kakawa Chocolate House! Wi-Fi.",
            tags = """["ds_subcat_11", "osm_amenity=cafe"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "kings_roast_beef", name = "King's Roast Beef",
            lat = 42.5288022, lng = -70.9041108,
            address = "145 North Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Mo-Su 11:00-21:30",
            phone = "978-745-7779",
            website = "https://www.kingsroastbeef.com",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "kismet_kafe", name = "Kismet Kafe",
            lat = 42.5295662, lng = -70.9270398,
            address = "47 Central Street, Peabody, 01960",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "koto_asian_fusion", name = "Koto Asian Fusion",
            lat = 42.5220211, lng = -70.8959266,
            address = "90 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-594-8681",
            website = "https://www.KotoAsianFusion.com/",
            description = "Best sushi on the North Shore, lunch and dinner, full bar, takeout, delivery, live music, Ramen soup, and fresh sushi.",
            tags = """["ds_subcat_13", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "koto_grill_and_sushi", name = "Koto Grill & Sushi",
            lat = 42.5219626, lng = -70.8959064,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "la_delicia", name = "La Delicia",
            lat = 42.5167964, lng = -70.8891785,
            address = "75 Congress Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "07:00-22:00",
            phone = "978 306-6660",
            website = "https://ladeliciasalem.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ledger", name = "Ledger",
            lat = 42.5210828, lng = -70.8953299,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ledger_restaurant_and_bar", name = "Ledger Restaurant & Bar",
            lat = 42.5210168, lng = -70.8953537,
            address = "125 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-594-1908",
            website = "https://www.ledgersalem.com/",
            description = "Ledger Restaurant is located in America’s second oldest savings bank that showcases the original ceilings, safety deposit boxes and vault doors. The menu is modern New England cuisine with an emphasis on wood fire cooking, local raw bar selections and housemade desserts led by James Beard Nominated Chef, Daniel Gursha. Ledger offers dinner five days a week (Wednesday-Sunday) and brunch on Sunday mornings. Weddings at Ledger Restaurant & Bar There are so many benefits to having your wedding at a restaurant like Ledger. The restaurant provides everything you need in house (chairs, napkins, plates, water decanters etc – no rentals are required! Ledger has an in house sound system and uses Spotify if you can’t afford to hire a DJ. Candles, lanterns, and vintage décor add to the unique space. Bringing your own floral arrangements to add your own personal touch. They have amazing craft cocktails that are always available and a huge inventory of bar selections instead of just your basic titos/soda. And of course, they have delicious food! Ledger offers stationary displays, passed appetizers, full sit down meals PLUS they make cakes, donuts & sweet treats all in house too!",
            tags = """["ds_subcat_13", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "life_alive_urban_oasis_and_organic_cafe", name = "Life Alive Urban Oasis & Organic Cafe",
            lat = 42.5213737, lng = -70.8974001,
            address = "281 Essex Street, Salem, 01970",
            businessType = "restaurant",
            hours = "Mo-Sa 10:00-20:00; Su 11:00-20:00",
            phone = "978 594 4644",
            website = "https://www.lifealive.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lil_devils_deli", name = "Lil' Devil's Deli",
            lat = 42.5179001, lng = -70.8895536,
            address = "48 Congress Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "Mo 12:00-19:30; Tu-Th 12:00-20:30; Fr 12:00-21:30; Su 12:00-20:30",
            phone = "978-570-8982",
            website = "https://lildevilsdeli.com/",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "londis_roast_beef_and_pizza", name = "Londi's Roast Beef & Pizza",
            lat = 42.5031632, lng = -70.9229021,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "longboards_restaurant_and_bar", name = "Longboards Restaurant & Bar",
            lat = 42.519545, lng = -70.8886422,
            address = "72 Wharf Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-745-6659",
            website = "https://www.longboardsbar.com/",
            description = "Dine where the locals do with sandwiches, award-winning lobster, steak tips, and pizzas on our oceanfront deck and bar with something for everyone! Keno on the wharf. Join us for the best sunset in Salem!",
            tags = """["ds_subcat_13", "hh_venue", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mainstay_social", name = "Mainstay Social",
            lat = 42.520175, lng = -70.8891957,
            address = "225 Derby Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-740-8788",
            website = "https://salemwaterfronthotel.com/dining/",
            description = "New restaurant in the Salem Waterfront Hotel & Suite serving breakfast, lunch, cocktails and dinner every day offering a classic and creative menu. Outdoor patio is the perfect spot for specialty cocktails and spirits.",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "maitland_farm", name = "Maitland Farm",
            lat = 42.5232779, lng = -70.88306,
            address = "84 Derby Street",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "maria_s_sweet_somethings", name = "Maria’s Sweet Somethings",
            lat = 42.5206785, lng = -70.8944334,
            address = "26 Front Street",
            businessType = "restaurant",
            cuisineType = "bakery",
            hours = "Mo-Su 11:00-20:00",
            tags = """["osm_shop=bakery"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "marsh_dining_hall", name = "Marsh Dining Hall",
            lat = 42.5001517, lng = -70.8934973,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mcdonalds", name = "McDonald's",
            lat = 42.5232449, lng = -70.9216515,
            address = "133 Main Street, Peabody, MA, 01960",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "Mo-Su 06:00-24:00",
            phone = "978-531-9513",
            website = "https://www.mcdonalds.com/us/en-us/location/1474.html",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mcdonalds_1", name = "McDonald's",
            lat = 42.5074203, lng = -70.8964264,
            address = "150 Canal Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "05:00-22:00",
            phone = "978-745-2727",
            website = "https://www.mcdonalds.com/us/en-us/location/3138.html",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mcdonalds_2", name = "McDonald's",
            lat = 42.5027937, lng = -70.9234283,
            address = "1 Traders Way, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "05:30-23:00",
            phone = "978-744-9631",
            website = "https://www.mcdonalds.com/us/en-us/location/14500.html",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mei_lee_express", name = "Mei Lee Express",
            lat = 42.5097178, lng = -70.8949506,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "melt", name = "Melt",
            lat = 42.5224204, lng = -70.8959712,
            address = "Salem, MA",
            businessType = "restaurant",
            cuisineType = "dessert",
            tags = """["osm_amenity=ice_cream"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "melt_ice_cream", name = "Melt Ice Cream",
            lat = 42.5224231, lng = -70.8959552,
            address = "60 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            website = "https://meltsalem.com/",
            description = "Homemade small-batch ice cream made onsite with REAL ingredients. We are allergy friendly and always have vegan and gluten free options available. Stop in to try some of our rotating seasonal flavors such as our famous honey cornbread or buttered popcorn.",
            tags = """["ds_subcat_11"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mercy_tavern", name = "Mercy Tavern",
            lat = 42.5220411, lng = -70.885843,
            address = "148 Derby Street",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ministry_of_donuts", name = "Ministry Of Donuts",
            lat = 42.5204131, lng = -70.888771,
            address = "Salem, MA",
            businessType = "restaurant",
            cuisineType = "fast_food",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "nats", name = "Nat's",
            lat = 42.5226444, lng = -70.890087,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ninety_nine", name = "Ninety Nine",
            lat = 42.5350385, lng = -70.8864737,
            address = "15 Bridge Street, Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant", "osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_dining_commons", name = "North Dining Commons",
            lat = 42.5035209, lng = -70.89051,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_house_of_pizza", name = "North House of Pizza",
            lat = 42.5277877, lng = -70.9022022,
            address = "134 North Street, Salem, 01970",
            businessType = "restaurant",
            hours = "11:00-22:00",
            phone = "978-740-0033",
            website = "https://www.grabull.com/restaurant/north-house-of-pizza-134-north-street-salem-ma",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "olde_main_street_pub", name = "Olde Main Street Pub",
            lat = 42.5223344, lng = -70.8907336,
            address = "121 Essex St, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Monday - Thursday 5-1am<br />\nFriday - Sunday; 12pm to 1am. Lunch & Dinner",
            phone = "(978)-594-8188",
            website = "https://www.oldemainstpub.com/",
            description = "Step into the heart of Salem, MA, and discover this enchanting Irish pub and restaurant. We proudly boast the best quality food at a reasonable price, a beer, wine, and cocktail list that will delight any palate. Patrons can indulge in made from scratch top quality dishes by two of the top young chefs on the North Shore. Soak up the city’s rich history from our many large windows or while on our outside patio. Cherish its warm hospitality, great service, and the authentic Irish experience it offers.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "o_neill_s", name = "O’Neill’s",
            lat = 42.5209444, lng = -70.8957419,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pza_grille", name = "PZA Grille",
            lat = 42.5055212, lng = -70.8905856,
            address = "331 Lafayette Street, 01970",
            businessType = "restaurant",
            hours = "Su-We 11:00-20:00; Th 11:00-21:00; Fr-Sa 11:00-22:00",
            phone = "978-594-0490",
            website = "https://pzagrille.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "passage_to_india", name = "Passage to India",
            lat = 42.5202004, lng = -70.8951316,
            address = "157 Washington Street, 01970",
            businessType = "restaurant",
            phone = "978-832-2200",
            website = "https://www.passagetoindiasalem.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "piccolo_piatti", name = "Piccolo piatti",
            lat = 42.5277732, lng = -70.8882528,
            address = "102 Webb Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Mo-Su 11:00-20:30",
            phone = "+19789717918",
            website = "https://www.toasttab.com/piccolo-piatti-102-webb-st",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "poblano", name = "Poblano",
            lat = 42.5183371, lng = -70.8930027,
            address = "105 Lafayette Street",
            businessType = "restaurant",
            hours = "11:00-22:00",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "popped", name = "Popped!",
            lat = 42.5196226, lng = -70.8880012,
            address = "84 Wharf Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            phone = "978-594-0609",
            website = "https://www.poppedstores.com/",
            description = "Handcrafted, gourmet popcorn! Watch the chef create over 100 flavors! Ice cream, candy, old fashioned root beer floats! Buy 3 Get 1 FREE! Nationwide shipping.",
            tags = """["ds_subcat_11", "osm_shop=confectionery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rayadea_s", name = "Rayadea’s",
            lat = 42.5186177, lng = -70.8934306,
            address = "90 Lafayette Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Sa-Th 11:00-16:00; Mo 11:00-15:00; Fr 11:00-13:30",
            phone = "978-594-4271",
            website = "https://adeasmk.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "red_s_sandwich_shop", name = "Red’s Sandwich Shop",
            lat = 42.5212332, lng = -70.8933635,
            address = "15 Central Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-745-3257",
            website = "https://www.redsinsalem.com/",
            description = "Salem’s most popular restaurant. Serving award-winning breakfast and home-cooked meals for 80 years. Daily lunch special. Group tours welcome. Visit our online store. Mon-Sat. 7 am-3 pm and Sundays 7 am to 1 pm.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rockafellas", name = "Rockafellas",
            lat = 42.5213097, lng = -70.8953387,
            address = "231 Essex Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-745-2411",
            website = "https://rockafellasofsalem.com/",
            description = "In the heart of historic Salem this casual chic restaurant is the perfect spot for a delicious meal. In the warmer months enjoy your appetizer and a cool drink on our brick patio. Colonial Hall at Rockafellas Colonial Hall at Rockafellas is not your typical function venue, customizing everything from menus and table decor, to the flow of the evening. Their experienced catering team is here to guide you through the planning process. Click here for complete details. To learn more or to request a private tour with their event planning team, call: 978-745-5415 or email sales@colonialhallatrockafellas.com.",
            tags = """["ds_subcat_13", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rockefellas", name = "Rockefellas",
            lat = 42.5213481, lng = -70.895279,
            address = "231 Essex Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Su-Th 11:30-22:00; Fr-Sa 11:30-01:00",
            phone = "978-745-2411",
            website = "https://rockafellasofsalem.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_tipico", name = "Salem Tipico",
            lat = 42.5162978, lng = -70.8895488,
            address = "88 Congress Street, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "07:00-21:00",
            phone = "978-678-8423; 978-594-4575",
            website = "https://salemtipicorestaurantma.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salems_retreat", name = "Salem's Retreat",
            lat = 42.5261809, lng = -70.9002976,
            address = "01970-3961",
            businessType = "restaurant",
            hours = "We-Mo 07:00-14:30",
            phone = "978-290-0958",
            website = "https://www.salemsretreat.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sea_level_oyster_bar", name = "Sea Level Oyster Bar",
            lat = 42.5202554, lng = -70.8878481,
            address = "94 Wharf Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-741-0555",
            website = "https://sealeveloysterbar.com/",
            description = "Dine with views of Salem Harbor offering waterfront dining on our seasonal patio. Featuring fresh seafood, raw bar, pizzas, and sandwiches. Twenty craft beers and craft cocktails. Let Sea Level plan your next wedding, rehearsal dinner, baby & bridal shower, social gathering, birthday party & corporate event in our private event space. Our event space can seat 30-65pp. Open seven days, 11:30 am–midnight. Reservations accepted.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "settler", name = "Settler",
            lat = 42.5224233, lng = -70.8961752,
            address = "2 Lynde St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-744-2094",
            website = "https://www.SettlerSalem.com",
            description = "Settler is a Mediterranean-inspired restaurant by Chef Aaron Chambers. Hidden on Lynde Street in historic Salem, it’s a cozy, chic spot where Aaron and Shanna Chambers bring their love of good food, wine and warm hospitality.",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "subway", name = "Subway",
            lat = 42.5101726, lng = -70.8952589,
            address = "119-125 Canal Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "Mo-Su 09:00-23:00",
            phone = "978-744-7827",
            website = "https://restaurants.subway.com/united-states/ma/salem/119-125-canal-st",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "super_slice_pizza", name = "Super Slice Pizza",
            lat = 42.5196505, lng = -70.8939161,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "taco_bell", name = "Taco Bell",
            lat = 42.5035817, lng = -70.9224627,
            address = "267 Highland Avenue, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "08:00-24:00",
            phone = "978-740-9933",
            website = "https://locations.tacobell.com/ma/salem/267-highland-ave.html",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tavern_on_the_green", name = "Tavern on the Green",
            lat = 42.5228833, lng = -70.8903406,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "thai_cuisine_and_seafood", name = "Thai Cuisine & Seafood",
            lat = 42.522298, lng = -70.8933497,
            address = "1 Church Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-741-8008",
            website = "https://www.thaiplacesalem.net/",
            description = "#1 Thai in 2022, 2021, 2020 and 2019. Open since 1990, 7 days a week. Try our Special MaiTai. Lunch, dinner, takeout & delivery. Groups welcome. Lunch specials 9.95, Children’s specials \$3.95.",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_babe", name = "The Babe",
            lat = 42.5174107, lng = -70.8938835,
            address = "268 Washington St, Salem, MA, 01970",
            businessType = "restaurant",
            hours = "Monday 11am - 10pm<br />\nTuesday 11am - 10pm<br />\nWednesday 11am - 10pm<br />\nThursday 11am - 10pm<br />\nFriday 11am - 12am<br />\nSaturday 11am - 12am<br />\nSunday 11am - 10pm",
            phone = "(978) 219-2210",
            website = "https://www.thebabesalem.com/",
            description = "The Babe is a friendly neighborhood bar and sandwich shop in The Point are of Salem featuring a wide range of classics including wings, salads, burgers, cheese steaks, meatball subs and falafel all made from scratch. With creative cocktail menu focused on fresh seasonal ingredients, as well as live music and programming. The Babe is Salem's new destination spot for inspired drinks, delicious food and entertainment. We can't wait to meet you!",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_chocolate_pantry", name = "The Chocolate Pantry",
            lat = 42.5220974, lng = -70.885599,
            address = "140 Derby St, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "dessert",
            phone = "978-744-7000",
            website = "https://www.shopthechocolatepantry.com/",
            description = "This is a global experience! Our pantry is filled with imported, handcrafted chocolate, with over 50 flavors. We also have specialty items VG, GF, and SF. Customized event favors available.",
            tags = """["ds_subcat_11", "osm_shop=chocolate"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_juicery", name = "The Juicery",
            lat = 42.5221092, lng = -70.8956448,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_little_depot_diner", name = "The Little Depot Diner",
            lat = 42.5270345, lng = -70.9275672,
            address = "1 Railroad Avenue, Peabody, MA, 01960",
            businessType = "restaurant",
            hours = "Mo-Su 07:00-13:00",
            phone = "978-977-7775",
            website = "https://thelittledepotdiner.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_lobster_shanty", name = "The Lobster Shanty",
            lat = 42.5202229, lng = -70.8946189,
            address = "25 Front St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-745-5449",
            website = "https://www.LobsterShantySalem.com",
            description = "The Lobster Shanty is a beloved Salem institution offering great food, signature lobster rolls, a covered deck, and our famous lobster martini—a cozy, welcoming spot that’s been serving the community for over 40 years.",
            tags = """["ds_subcat_13", "hh_venue", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_nexmex_thing", name = "The NexMex Thing",
            lat = 42.5244221, lng = -70.9242639,
            address = "77 Main Street, Peabody, MA, 01960",
            businessType = "restaurant",
            hours = "Mo-Th 11:00-21:00; Fr-Sa 11:00-22:00; Su 12:00-21:00",
            phone = "978-839-3931",
            website = "https://nexmexthing.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_roof_at_hotel_salem", name = "The Roof at Hotel Salem",
            lat = 42.5214601, lng = -70.8942723,
            address = "209 Essex St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "(978) 451-4814",
            website = "https://www.theroofsalem.com/?utm_source=gmb&utm_medium=yext",
            description = "Located at the top of The Hotel Salem, The Roof offers Salem's only open-air rooftop bar and restaurant serving the necessities-strong drinks, simple food, and expansive city views.",
            tags = """["ds_subcat_13", "ds_subcat_21", "hh_venue", "osm_tourism=hotel"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tipsy_cowboy", name = "Tipsy Cowboy",
            lat = 42.5187488, lng = -70.8949521,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "trade_house", name = "Trade House",
            lat = 42.5219327, lng = -70.8860568,
            address = "156 Derby St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-306-7718",
            website = "https://tradehousesalem.com/",
            description = "A cozy neighborhood pub on historic Derby Street serving premium steak, hearty local fare, festive cocktails, and weekly specials—all with an inviting, affordable vibe in one of Salem’s most spirited spots.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "treadwells", name = "Treadwell's",
            lat = 42.5356953, lng = -70.9173284,
            address = "46 Margin Street, Peabody, MA, 01960",
            businessType = "restaurant",
            cuisineType = "dessert",
            hours = "Mo-Sa 10:00-22:00; Su 11:00-22:00",
            phone = "978 531 7010",
            website = "https://www.mytreadwells.com/",
            tags = """["osm_amenity=ice_cream"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "turner_s_seafood_at_lyceum_hall", name = "Turner’s Seafood at Lyceum Hall",
            lat = 42.522426, lng = -70.8952659,
            address = "43 Church Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-745-7665",
            website = "https://www.turners-seafood.com/",
            description = "About Winner of the 2025 Best Seafood Restaurant from North Shore Magazine, and Yelp’s 2025 Best Seafood, we look forward to serving you the freshest seafood in New England. The Turner Family has been in the seafood business for four generations; we buy fresh from the local docks, process the fish at our plant in Gloucester and then deliver it to our restaurants. Come experience authentic New England seafood with Turner’s Seafood, located in Salem’s historic Lyceum Hall. Interested in American History Beyond Salem? Come dine with us at our historic Publick House in Melrose, MA, the Rising Eagle. Open daily for lunch and dinner, including a brunch menu on the weekends.",
            tags = """["ds_subcat_13", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ugly_mug_diner", name = "Ugly Mug Diner",
            lat = 42.5210011, lng = -70.8957534,
            address = "Salem, MA",
            businessType = "restaurant",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "village_tavern", name = "Village Tavern",
            lat = 42.5222386, lng = -70.8927344,
            address = "168 Essex Street, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-744-2858",
            website = "https://www.villagetavernsalem.com/",
            description = "An upbeat American Tavern, located opposite the Peabody-Essex Museum, once again receiving the people’s Vote for “Salem’s Number One Burger” - Now 8 Years in a Row! Amazing Wraps, Hand Tossed Pizza, Wings, plus tasty appetizers; the 75 varieties of domestic and craft beers round out the perfect place to enjoy Lunch or Dinner in Salem. The Venue also features 3 Bars, a fun Game Room, and Nightly Entertainment that compliments an amazingly fun staff. There are many quality restaurants in Salem; but for quality, value, and for an overall excellent experience, be sure the Village is on your list.",
            tags = """["ds_subcat_13", "hh_venue", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wendys", name = "Wendy's",
            lat = 42.5186397, lng = -70.8929445,
            address = "91 Lafayette Street, Salem, MA, 01970",
            businessType = "restaurant",
            cuisineType = "fast_food",
            hours = "Su-Th 06:30-26:00; Fr-Sa 06:30-27:00",
            phone = "978-745-9545",
            website = "https://locations.wendys.com/united-states/ma/salem/91-lafayette-st",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_vfw", name = "Witch City VFW",
            lat = 42.522976, lng = -70.88317409999999,
            address = "95 Derby St, Salem, MA, 01970",
            businessType = "restaurant",
            phone = "978-745-0010",
            website = "https://www.facebook.com/Post1524",
            description = "Support our local veterans at the Witch City VFW! You will enjoy the best food, the best drinks, and at the best prices around. Serving only the finest clientele!",
            tags = """["ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witchside_tavern", name = "WitchSide Tavern",
            lat = 42.5201511, lng = -70.8909815,
            address = "283 Derby Street, 01970",
            businessType = "restaurant",
            website = "https://witchsidetavern.com/",
            tags = """["osm_amenity=restaurant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ziggy_and_sons_donuts", name = "Ziggy & Sons Donuts",
            lat = 42.5257887, lng = -70.8844607,
            address = "Salem, MA",
            businessType = "restaurant",
            cuisineType = "fast_food",
            tags = """["osm_amenity=fast_food"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Cafes & Coffee (27)
    // ===================================================================

    val cafe = listOf(
        OutputBusiness(
            id = "aandj_king_artisan_bakers", name = "A&J King Artisan Bakers",
            lat = 42.5200903, lng = -70.8930354,
            address = "48 Central St, Salem, MA, 01970",
            businessType = "cafe",
            hours = "Open 7 Days, 7am - 3pm",
            phone = "(978) 744-4881",
            website = "https://ajkingbakery.com/",
            description = "Since 2006, A&J King has led the artisan food movement, offering pastries, bread, espresso, and more. Partnering with local farms, they serve fresh, locally-made products. Stop by for a quick bite or sit down and enjoy!",
            tags = """["ds_subcat_12", "osm_shop=bakery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "au_gratin", name = "Au Gratin",
            lat = 42.5225356, lng = -70.8959841,
            address = "Salem, MA",
            businessType = "cafe",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "blackcraft_coffee", name = "Blackcraft Coffee",
            lat = 42.5212608, lng = -70.8964933,
            address = "253 Essex St, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-943-6066",
            website = "https://blackcraftsalem.com/pages/coffee",
            description = "Tucked in the back right corner of Blackcraft Salem you will find Blackcraft Coffee, which was built to resemble a witch's cottage. Our cafe features speciality drinks and treats inspired by the Witch City!",
            tags = """["ds_subcat_12"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "breaking_grounds", name = "Breaking Grounds",
            lat = 42.5245154, lng = -70.9244774,
            address = "67 Main Street, Peabody, MA, 01960",
            businessType = "cafe",
            hours = "Mo-Fr 07:00-14:00; Sa-Su 08:00-14:00",
            phone = "978-854-5465",
            website = "https://breakinggroundscafe.com/",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "brew_box", name = "Brew Box",
            lat = 42.5221553, lng = -70.8913968,
            address = "131 Essex Street",
            businessType = "cafe",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "camillas_cafe_and_breakfast", name = "Camilla's Cafe & Breakfast",
            lat = 42.5088876, lng = -70.8965578,
            address = "140 Canal Street, Salem, MA, 01970",
            businessType = "cafe",
            hours = "Mo-Sa 05:00-18:00",
            phone = "978-306-7281",
            website = "https://camillascafebreakfast.com/",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "coffee_time_bake_shop", name = "Coffee Time Bake Shop",
            lat = 42.5293901, lng = -70.8899879,
            address = "96 Bridge Street, Salem, MA, 01970",
            businessType = "cafe",
            hours = "05:00-23:00",
            phone = "978-744-0995",
            website = "https://coffeetimebakeshop.net/",
            description = "Baker-owned shop famous for Real Cream Bismarks, Fresh Egg Éclairs, Strawberry Shortcake, Breakfast Sandwiches, Hand-Cut Donuts, Paczki, Great Coffee, and Specialty Beverages. Open 5 a.m. to 11 p.m., 7 days.",
            tags = """["ds_subcat_12", "osm_amenity=cafe"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "eat_drink_explore_provisions", name = "Eat Drink Explore Provisions",
            lat = 42.5252185, lng = -70.9253988,
            address = "50 Main Street, Peabody, MA, 01960",
            businessType = "cafe",
            hours = "Sa-Su 10:00-14:00",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_s_ny_pizza_and_deli", name = "Essex’s NY Pizza & Deli",
            lat = 42.5221276, lng = -70.8931071,
            address = "1 Church Street, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-741-1383",
            website = "https://essexsnypizza.com/",
            description = "Tossing New York style pizza and serving homemade chicken tenders, big salads and amazing subs. Pizza by the pie or the slice. Cheese Steaks and chicken sandwiches are legendary. Who wants a quarter pound chocolate chip cookie? Cold beer, amazing music and old school American pizza shop culture. Did we mention Arcade Games? Quick service restaurant for dine in with 100 seats as well as take out or delivery. Get your food fast! Large groups welcome. Located in the Witch City Mall downtown. Look out for the all seeing eye of pizza. It is watching you!",
            tags = """["ds_subcat_12", "ds_subcat_13"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "front_street_coffeehouse", name = "Front Street Coffeehouse",
            lat = 42.5206251, lng = -70.8942857,
            address = "20 Front St, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-740-6697",
            website = "https://www.FSCSalem.com",
            description = "Since 1996, Front Street Coffeehouse has been “Salem’s Living Room,” serving organic coffee, espresso, teas, sandwiches & smoothies—a cozy downtown gathering place where everyone is welcome.",
            tags = """["ds_subcat_12", "ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "honey_dew", name = "Honey Dew",
            lat = 42.521721, lng = -70.8955833,
            address = "Salem, MA",
            businessType = "cafe",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jaho_cafe", name = "Jaho Cafe",
            lat = 42.5208149, lng = -70.8881829,
            address = "197 Derby Street, Salem, MA, 01970",
            businessType = "cafe",
            hours = "Mo-Fr 07:00-23:00; Sa 07:30-23:00; Su 07:30-22:00",
            phone = "978-744-4300",
            website = "https://www.jaho.com/pages/salem-locations",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jaho_coffee", name = "Jaho Coffee",
            lat = 42.5022776, lng = -70.8936474,
            address = "57 Loring Avenue, Salem, MA, 01970",
            businessType = "cafe",
            hours = "Mo-Su 07:00-17:00",
            phone = "978-594-4743",
            website = "https://www.jaho.com/pages/salem-locations",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jaho_coffee_roaster_and_bakery_canal_street", name = "Jaho Coffee Roaster & Bakery – Canal Street",
            lat = 42.5099816, lng = -70.8964829,
            address = "130 Canal St, Salem, MA, 01970",
            businessType = "cafe",
            hours = "Mo-Fr 06:30-23:00; Sa-Su 07:00-23:00",
            phone = "978-594-1485",
            website = "https://www.Jaho.com",
            description = "Come visit Salem’s first and oldest coffee roaster. Try our our award-winning coffee and the region’s most famous Pumpkin Spice Latte—crafted to haunt your tastebuds.",
            tags = """["ds_subcat_12", "osm_amenity=cafe"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jaho_coffee_roaster_and_bakery_derby_street", name = "Jaho Coffee Roaster & Bakery – Derby Street",
            lat = 42.5208111, lng = -70.8882306,
            address = "197 Derby St, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-744-4300",
            website = "https://www.Jaho.com",
            description = "Warm your haunted heart with the perfect brew at Salem’s first and favorite coffee roaster for the past 20 years. Try our award-winning coffee and iconic Pumpkin Spice Latte!",
            tags = """["ds_subcat_12"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jolie_tea_company", name = "Jolie Tea Company",
            lat = 42.5198403, lng = -70.8928433,
            address = "316 Derby St, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-745-5654",
            website = "https://www.JolieTeaCo.com",
            description = "Discover the Beauty & History of Tea at Jolie! Visit our Tea Salon & Patisserie on Derby Street in Salem. Enjoy our beautiful setting for High Tea or Pastry in this historical port City, where tea was one of many imports to traverse the oceans as cargo aboard the massive Clipper Ships. We have a Loose Leaf Tea Bar with over 300 teas that can be purchased by the ounce. Let our Tea Counter guides help you find the perfect flavor profile of tea for your enjoyment in our cafe or to take with you. Reservations for High Tea may be made online, however, Walk-Ins are always welcome! Check out our additional location in Beverly, at 192 Cabot Street.",
            tags = """["ds_subcat_12", "osm_shop=tea"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lulu_s_bakery_and_pantry", name = "Lulu’s Bakery & Pantry",
            lat = 42.5200502, lng = -70.891356,
            address = "285 Derby St, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-594-4531",
            website = "http://lulusbakeryandpantry.com/",
            description = "An American bakery and cafe featuring an array of pastries, artisanal chocolates, coffee, soup, salads and sandwiches, all made with down-home charm. Special occasion cakes and seasonal treats are also available.",
            tags = """["ds_subcat_12", "osm_amenity=cafe"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "new_england_soup_factory", name = "New England Soup Factory",
            lat = 42.5206858, lng = -70.8956984,
            address = "Salem, MA",
            businessType = "cafe",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "odd_meter", name = "Odd Meter",
            lat = 42.5225698, lng = -70.8959955,
            address = "60 Washington Street, Salem, MA, 01970",
            businessType = "cafe",
            hours = "We-Mo 08:00-16:00",
            website = "https://www.oddmetercoffee.com/",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "polonus_deli", name = "Polonus Deli",
            lat = 42.5218411, lng = -70.8931232,
            address = "176 Essex Street",
            businessType = "cafe",
            website = "http://www.polonusdeli.com/",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "red_line_cafe", name = "Red Line Cafe",
            lat = 42.5217542, lng = -70.8937042,
            address = "Salem, MA",
            businessType = "cafe",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "roseadella_s", name = "Roseadella’s",
            lat = 42.5225598, lng = -70.8935298,
            address = "Salem, MA",
            businessType = "cafe",
            hours = "Su 07:00-18:00; Mo-Sa 07:00-18:30",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "starbucks", name = "Starbucks",
            lat = 42.5189609, lng = -70.8949609,
            address = "211 Washington Street, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-825-0824",
            website = "https://www.starbucks.com/store-locator/store/15999/salem-dodge-street-211-washington-street-salem-ma-019703607-us",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "starbucks_1", name = "Starbucks",
            lat = 42.5007051, lng = -70.8949873,
            address = "71 Loring Avenue, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-542-2543",
            website = "https://www.starbucks.com/store-locator/store/1012909/salem-state-university-viking-71-loring-avenue-salem-ma-01970-us",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "starbucks_2", name = "Starbucks",
            lat = 42.507991, lng = -70.8962995,
            address = "144 Canal Street, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-595-0025",
            website = "https://www.starbucks.com/store-locator/store/1031793/salem-canal-street-144-canal-street-salem-ma-019704650-us",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_peabody_diner", name = "The Peabody Diner",
            lat = 42.5326775, lng = -70.9138754,
            address = "10 Margin Street, Peabody, MA",
            businessType = "cafe",
            hours = "Mo-Fr 6:00-14:00; Sa,Su 6:00-15:00",
            phone = "978-854-5800",
            tags = """["osm_amenity=cafe"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wolf_next_door_coffee", name = "Wolf Next Door Coffee",
            lat = 42.5220963, lng = -70.8856828,
            address = "142 Derby St, Salem, MA, 01970",
            businessType = "cafe",
            phone = "978-594-1540",
            website = "https://www.WolfNextDoorCoffee.com",
            description = "A welcoming local spot for organic coffee, espresso, teas & fresh pastries—Wolf Next Door Coffee brings warmth & community to Salem’s Historic Derby Street Neighborhood.",
            tags = """["ds_subcat_12", "osm_amenity=cafe"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Bars & Breweries (10)
    // ===================================================================

    val bar = listOf(
        OutputBusiness(
            id = "all_souls_lounge", name = "All Souls Lounge",
            lat = 42.5204367, lng = -70.8911065,
            address = "Salem, MA",
            businessType = "bar",
            tags = """["osm_amenity=bar"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "granite_coast_brewery", name = "Granite Coast Brewery",
            lat = 42.5243933, lng = -70.9241996,
            address = "77 Main Street, Peabody, MA, 01960",
            businessType = "bar",
            hours = "Fr 16:00-20:00; Sa 14:00-20:00; Su 14:00-18:00",
            phone = "978-595-2775",
            website = "https://www.granitecoastbrewing.com",
            tags = """["osm_amenity=pub"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "regatta_pub", name = "Regatta Pub",
            lat = 42.5202226, lng = -70.8893497,
            address = "225 Derby Street, Salem, MA, 01970",
            businessType = "bar",
            tags = """["osm_amenity=pub"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sidelines_sports_bar_and_grill", name = "Sidelines Sports Bar & Grill",
            lat = 42.5103735, lng = -70.8959613,
            address = "105 Canal Street",
            businessType = "bar",
            tags = """["osm_amenity=bar"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_roof", name = "The Roof",
            lat = 42.5212432, lng = -70.8942647,
            address = "209 Essex Street, Salem, MA, 01970",
            businessType = "bar",
            hours = "Mo-Th 15:00-22:00; Fr-Sa 12:00-23:00; Su 12:00-22:00",
            phone = "978-451-4950",
            website = "https://www.theroofsalem.com/",
            tags = """["osm_amenity=bar"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "vfw", name = "VFW",
            lat = 42.523224, lng = -70.8827419,
            address = "85 Derby Street",
            businessType = "bar",
            tags = """["osm_amenity=bar"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "couch_dog_brewing_co", name = "Couch Dog Brewing Co.",
            lat = 42.5193052, lng = -70.893556,
            address = "76 Lafayette St, Salem, MA, 01970",
            businessType = "bar",
            hours = "Monday: 4PM - 10PM<br />\n<br />\nThursday: 4PM - 10PM<br />\n<br />\nFriday: 12PM - 10PM<br />\n<br />\nSaturday: 12PM - 10PM <br />\n<br />\nSunday: 12PM - 8PM",
            website = "https://www.couchdogbrewing.com/",
            description = "Microbrewery and taproom based in downtown Salem with a focus on fun, aromatic, and Asian-inspired ales in a relaxed environment.",
            tags = """["ds_subcat_14", "osm_amenity=pub"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "east_regiment_beer_company", name = "East Regiment Beer Company",
            lat = 42.5226391, lng = -70.8950008,
            address = "30 Church St, Salem, MA, 01970",
            businessType = "bar",
            hours = "TAPROOM HOURS <br />\nMonday & Tuesday 4-9:30pm<br />\nSaturday 12-10 PM<br />\n Sunday 12-8 PM <br />\nClosed Monday/Tuesday",
            website = "https://eastregimentbeercompany.com/",
            description = "Craft brewery in the heart of downtown Salem. Featuring a rotating menu of IPAs, sours, lagers, ales, seltzer and experimental beer. Featuring chicken sandwiches & burgers from our kitchen partners, Crazy Good Kitchen.",
            tags = """["ds_subcat_14", "hh_venue", "osm_craft=brewery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "far_from_the_tree", name = "Far From The Tree",
            lat = 42.51479982798013, lng = -70.89970813680536,
            address = "108 Jackson St, Salem, MA, 01970",
            businessType = "bar",
            phone = "978-224-2904",
            website = "https://www.farfromthetreecider.com/",
            description = "Far From The Tree Cider is a craft cider house specializing in unique, high-quality hard cider made from local apples and all-natural ingredients. Most apples don’t fall far from the tree, and our ciders are no exception. Far From The Tree respects tradition by controlling the entire production process from apple pressing straight through to canning. Check out our selection of flagships, seasonals, and other unique offerings both on draft and to-go. Enjoy yourself in our taproom or on our dog friendly patio. Rooted in tradition, unique to the core.",
            tags = """["ds_subcat_14", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "notch_brewing", name = "Notch Brewing",
            lat = 42.5196686, lng = -70.8907968,
            address = "283r Derby Street, Salem, MA, 01970",
            businessType = "bar",
            phone = "978-238-9060",
            website = "https://www.notchbrewing.com/",
            description = "Brewery, Tap Room, and Beer Garden overlooking the South River, Salem, MA. German and Czech-focused beer and food.",
            tags = """["ds_subcat_14", "hh_venue", "osm_craft=brewery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Shopping & Retail (122)
    // ===================================================================

    val shopRetail = listOf(
        OutputBusiness(
            id = "amp_cannabis_dispensary", name = "AMP Cannabis Dispensary",
            lat = 42.5017922, lng = -70.924032,
            address = "297 Highland Ave, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://www.ampma.org/",
            description = "AMP Cannabis Dispensary in Salem offers the lowest prices in the city since we grow our own award-winning flower. We also offer a great selection of vapes, gummies, chocolates, drinks and more! Come visit us today!",
            tags = """["ds_subcat_733", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "seagrass_dispensary", name = "Seagrass Dispensary",
            lat = 42.5187709, lng = -70.89386,
            address = "3 Dodge St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Monday: 10 AM – 8 PM<br />\nTuesday: 10 AM – 8 PM<br />\nWednesday: 10 AM – 8 PM<br />\nThursday: 10 AM – 9 PM<br />\nFriday: 10 AM – 9 PM<br />\nSaturday: 10 AM – 9 PM<br />\nSunday: 11 AM – 7 PM",
            phone = "978-498-4183",
            website = "https://seagrasssalem.com/",
            description = "Downtown adult-use dispensary offering the best cannabis products in a welcoming environment for all with friendly and knowledgeable staff to help both the new and seasoned customers. Overview of Adult-Use Marijuana in Massachusetts",
            tags = """["ds_subcat_733", "osm_shop=cannabis"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "starbird_dispensary", name = "Starbird Dispensary",
            lat = 42.5365583, lng = -70.8870727,
            address = "2 Bridge St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Su 09:00-22:00",
            phone = "978-662-0191",
            website = "https://www.StarbirdSalem.com",
            description = "Starbird Cannabis is locally owned and located next to Bill & Bob’s Famous Roast Beef and the Beverly–Salem Bridge. Open 7 days a week from 9am–10pm, we offer free parking, a welcoming atmosphere, and knowledgeable staff.",
            tags = """["ds_subcat_733", "osm_shop=cannabis"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "100_derby_country_store", name = "100 Derby Country Store",
            lat = 42.522984, lng = -70.8836912,
            address = "100 Derby Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "1925_at_the_hawthorne", name = "1925 At The Hawthorne",
            lat = 42.5226047, lng = -70.8903369,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ascend", name = "ASCEND",
            lat = 42.5216959, lng = -70.8939574,
            address = "192 Essex St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://ascendgetlifted.com/",
            description = "Shop high-quality, hand-selected crystals and accessories from around the globe that are filled with intention. Our branded collection is created and infused with reiki here in historic Salem, MA.",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "backyard_store", name = "Backyard Store",
            lat = 42.5043555, lng = -70.9228036,
            address = "250 Highland Avenue",
            businessType = "shop_retail",
            tags = """["osm_shop=doityourself"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bewitched_in_salem", name = "Bewitched in Salem",
            lat = 42.5218455, lng = -70.8932377,
            address = "180 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-744-9904",
            website = "https://www.facebook.com/Bewitchedinsalem",
            description = "Salem's largest collection of statuary: Americana, Angelic, Art Deco, Artistic Nudes, Catholic, Egyptian, Greek, Hindu, Mermaid, Military, Norse, Roman, Service People, Steampunk, and more. Leather journals for your darkest secrets!",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "big_apple_food_shops", name = "Big Apple Food Shops",
            lat = 42.5006125, lng = -70.8965133,
            address = "72 Loring Avenue, Salem, MA, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=supermarket"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "blackcraftcult", name = "BlackCraftCult",
            lat = 42.521389, lng = -70.8965292,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=clothes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "blackcraft_salem", name = "Blackcraft Salem",
            lat = 42.5212608, lng = -70.8964933,
            address = "253 Essex St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Sunday 10-7, Monday-Thursday 11-7, Friday 10-8, Saturday 10-8",
            phone = "978-943-6066",
            website = "https://blackcraftsalem.com/",
            description = "Step inside Blackcraft Salem, the original Merchants National Bank of Salem over 110 years old. Here you will find Salem's largest witch, Nydia, hanging from our beautiful dome ceiling. Our store features a variety of apparel, accessories and footwear for adults, children and pets! Plus, a variety of home goods, books, collectibles, plush and so much more. NEW for 2024, tucked in the back right corner of our store you will find Blackcraft Coffee built to resemble a witch's cottage. Our cafe features specialty drinks and treats inspired by the Witch City!",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "brick_box", name = "Brick Box",
            lat = 42.5219815, lng = -70.8918596,
            address = "135 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bung_hole_liquor_store", name = "Bung Hole Liquor Store",
            lat = 42.5210021, lng = -70.8885172,
            address = "204 Derby Street",
            businessType = "shop_retail",
            tags = """["osm_shop=alcohol"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "busa_wine_and_spirits", name = "Busa Wine & Spirits",
            lat = 42.5026151, lng = -70.9202922,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=alcohol"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "carpe_diem_boutique", name = "Carpe Diem Boutique",
            lat = 42.5255773, lng = -70.9262981,
            address = "24 Main Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            tags = """["osm_shop=clothes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cheese_shop_of_salem", name = "Cheese Shop of Salem",
            lat = 42.5200264, lng = -70.8932471,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=cheese"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "circle_k", name = "Circle K",
            lat = 42.5001948, lng = -70.9263946,
            address = "323 Highland Avenue",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "circle_of_stitches", name = "Circle of Stitches",
            lat = 42.5196369, lng = -70.8884513,
            address = "78 Wharf St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mon: Noon to 6 pm | Tues: 1 pm to 5 pm | Wed: CLOSED | Thurs: Noon to 6 pm | Fri: 11 am to 6 pm | Sat: 11 am to 5 pm | Sun: 1 pm to 5 pm",
            phone = "978-745-9276",
            website = "http://circleofstitches.com/",
            description = "Putting the CRAFT in Witchcraft! Visit us for your stitching and witching needs. We have unique gifts, tarot decks, crystals, witchy goods, fine yarns, tarot readings, and lots of workshops!",
            tags = """["ds_subcat_16", "hh_venue", "osm_shop=fabric"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "city_smoke_shop", name = "City Smoke Shop",
            lat = 42.5203322, lng = -70.8932424,
            address = "1;41 Lafayette Street",
            businessType = "shop_retail",
            tags = """["osm_shop=tobacco"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "clam_shack", name = "Clam Shack",
            lat = 42.5351065, lng = -70.8707498,
            address = "200 Fort Avenue, Salem, MA, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=seafood"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "coven", name = "Coven",
            lat = 42.5198634, lng = -70.8884307,
            address = "63 Wharf St, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-594-1166",
            website = "https://www.covenboutique.com/",
            description = "Located on the beautiful Pickering Wharf, Coven offers a modern twist of Salem's charm, apparel, accessories, cosmetics, SFX makeup, ritual tools, gifts, and more! Shop in-store or online.",
            tags = """["ds_subcat_17", "osm_shop=clothes"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "create_and_escape", name = "Create & Escape",
            lat = 42.5244802, lng = -70.9243932,
            address = "71 Main Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            phone = "978-826-4741",
            website = "https://www.createandescapediy.com",
            tags = """["osm_shop=craft"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "crosbys_marketplace", name = "Crosby's Marketplace",
            lat = 42.5099462, lng = -70.894859,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=supermarket"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dave_eng_s_flowers", name = "Dave Eng’s Flowers",
            lat = 42.522133, lng = -70.8855454,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=florist"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "destination_salem", name = "Destination Salem",
            lat = 42.5205328, lng = -70.8902584,
            address = "245 Derby St, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-741-3252",
            website = "https://www.salem.org/",
            description = "Looking for Salem’s best-kept secrets? Start at Destination Salem’s Information Center, where our friendly specialists offer personalized tips to explore the city’s art, culture, unique shops, and dining for a trip to remember! Explore a shop filled with local charm, featuring Salem-inspired merchandise, souvenirs, and keepsakes. From apparel to books and exclusive treasures, it’s the perfect place to bring home a piece of your visit and celebrate the city’s rich history.",
            tags = """["ds_subcat_16", "ds_subcat_5", "osm_tourism=information"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "die_with_your_boots_on", name = "Die With Your Boots On",
            lat = 42.5225184, lng = -70.8938009,
            address = "9 Church St, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-594-8085",
            website = "http://diewithyourbootson.com/",
            description = "Dressing Salem in fearless fashion since 2018! Family owned and operated and curated with your favorite small and traditional goth brands + local and small batch vendors! Follow us on TikTok and Instagram: @diewithbootson",
            tags = """["ds_subcat_17", "hh_venue", "osm_shop=clothes"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "diehl_marcus_and_company", name = "Diehl Marcus & Company",
            lat = 42.5212497, lng = -70.893511,
            address = "11 Central St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://diehlmarcus.com/",
            description = "Step back in time and experience the charm of Salem’s historic merchant trade at this unique shop, selling exquisite teas, soaps, scented candles, and antiques. Nestled within the gorgeous 11 Central Street building.",
            tags = """["ds_subcat_16", "osm_shop=tea"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "district_trading_company", name = "District Trading Company",
            lat = 42.5221675, lng = -70.8913407,
            address = "131 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "SHOP HOURS<br />\n<br />\nSunday: 10 am to 6 pm<br />\nMonday: 10 am - 6 pm<br />\nTuesday: 10 am - 6 pm<br />\nWednesday: 10 am - 6pm<br />\nThursday: 10 am - 6pm<br />\nFriday: 10 am - 6 pm (7pm summer-fall)<br />\nSaturday: 11 am to 6 pm (7pm summer-fall)<br />",
            phone = "978-594-8186",
            website = "https://www.districttrading.com",
            description = "Salem-inspired apparel and lifestyle shop featuring original line of high-quality graphic t-shirts, hoodies, hats, greeting cards, home décor, souvenirs, stickers, and more. (5% of sales to art education.)",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "district_trading_company_store", name = "District Trading Company Store",
            lat = 42.5215434, lng = -70.8962392,
            address = "244 Essex St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://www.DistrictTrading.com",
            description = "New England and Salem inspired clothing boutique featuring District original apparel and a curated collection of mens and womens attire, housewares and gifts.",
            tags = """["ds_subcat_17"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "emporium_32", name = "Emporium 32",
            lat = 42.521384, lng = -70.8938135,
            address = "6 Central St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "http://emporium32.com/",
            description = "A vintage-inspired boutique offering unique, artist-made gifts, accessories, and décor. Featuring beautiful barware, beard/mustache care, eclectic art, homegoods, heirloom-style jewelry and 250+ styles of hats from around the globe!",
            tags = """["ds_subcat_17", "osm_shop=fashion_accessories"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_street_mall", name = "Essex Street Mall",
            lat = 42.5216971, lng = -70.8937528,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=mall"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "family_dollar", name = "Family Dollar",
            lat = 42.5090698, lng = -70.8966116,
            address = "138;140 Canal Street",
            businessType = "shop_retail",
            tags = """["osm_shop=variety_store"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "flying_monkey", name = "Flying Monkey",
            lat = 42.5216721, lng = -70.8941594,
            address = "196 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=clothes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "friendly_mini_mart", name = "Friendly Mini Mart",
            lat = 42.5114491, lng = -70.8916999,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "game_zone", name = "Game Zone",
            lat = 42.5215301, lng = -70.8973139,
            address = "270 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=video_games"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gardner_park_variety", name = "Gardner Park Variety",
            lat = 42.535566, lng = -70.9170931,
            address = "44 Margin Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            hours = "Mo-Su 08:00-21:00",
            phone = "978-531-0038",
            website = "https://www.instagram.com/gardner_park_vaerity/",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "georgia_s_little_salem_shop", name = "Georgia’s Little Salem Shop",
            lat = 42.5206547, lng = -70.8941123,
            address = "12 Front St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://www.littlesalemshop.com",
            description = "At Georgia’s Little Salem Shop, you can find the Witch City's most unique gifts and apparel for people of all ages, created by 14-year-old resident Georgia Wrenn, who started her brand when she was just six years old!",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "habanero_bicycles", name = "Habanero Bicycles",
            lat = 42.51754, lng = -70.8941262,
            address = "264 Washington Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "480-525-2721",
            website = "https://www.habcycles.com/",
            tags = """["osm_shop=bicycle"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "harrisons_comics", name = "Harrison's Comics",
            lat = 42.5215638, lng = -70.8965151,
            address = "252 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Su 10:00-20:00",
            phone = "978-741-0786",
            description = "Books = comics",
            tags = """["osm_shop=books"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "home_decor_group", name = "Home Decor Group",
            lat = 42.5202915, lng = -70.8907362,
            address = "281 Derby Street, Salem, MA, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=paint"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "house_of_boo", name = "House Of Boo",
            lat = 42.5202491, lng = -70.8883391,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jenni_stuart_fine_jewelry", name = "Jenni Stuart Fine Jewelry",
            lat = 42.5204621, lng = -70.8943237,
            address = "24 Front St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Thursday\t11 AM–6 PM<br />\nFriday\t11 AM–6 PM<br />\nSaturday\t11 AM–6 PM<br />\nSunday\tClosed<br />\nMonday\tClosed<br />\nTuesday\tClosed<br />\nWednesday\t11 AM–6 PM",
            phone = "978-594-2460",
            website = "https://jennistuart.com/",
            description = "Fine jewelry made right in downtown Salem! See our exclusive charm designs, get zapped with a permanent bracelet, and shop our curated collection of gifts & personal accessories. There's a little bit of Salem in every piece!",
            tags = """["ds_subcat_16", "ds_subcat_741"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "joe_s_fish_prints", name = "Joe’s Fish Prints",
            lat = 42.5196147, lng = -70.887792,
            address = "88 Wharf Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "king_lewis_engraving_and_gifts", name = "King Lewis Engraving & Gifts",
            lat = 42.5223927, lng = -70.8849376,
            address = "124 Derby Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "landry_and_arcari", name = "Landry & Arcari",
            lat = 42.5207583, lng = -70.9078103,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=carpet"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lifebridge_thrift_store", name = "Lifebridge Thrift Store",
            lat = 42.5167341, lng = -70.8954444,
            address = "01970",
            businessType = "shop_retail",
            hours = "Tu-Sa 10:00-17:00",
            phone = "978-745-2459",
            website = "https://lifebridgethriftshop.org/",
            tags = """["osm_shop=second_hand"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "loring_liquors", name = "Loring Liquors",
            lat = 42.5013601, lng = -70.8957602,
            address = "70 Loring Avenue, Salem, MA, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=alcohol"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "los_amigos", name = "Los Amigos",
            lat = 42.517775, lng = -70.8932784,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "l_appel_du_vide", name = "L’appel Du Vide",
            lat = 42.5208681, lng = -70.8887177,
            address = "208 Derby Street",
            businessType = "shop_retail",
            tags = """["osm_shop=clothes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "marc_s_market", name = "Marc’s Market",
            lat = 42.5177447, lng = -70.8907389,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "market_basket", name = "Market Basket",
            lat = 42.504649, lng = -70.9199725,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=supermarket"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "metro_by_t_mobile", name = "Metro by T-Mobile",
            lat = 42.5244559, lng = -70.9243373,
            address = "75 Main Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            hours = "Mo-Fr 09:00-19:00; Sa 10:00-18:00; Su 10:00-14:00",
            phone = "978-854-5740",
            website = "https://www.metrobyt-mobile.com/stores/bd/metro-by-t-mobile-peabody-ma-10202750/",
            tags = """["osm_shop=mobile_phone"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "metro_by_t_mobile_1", name = "Metro by T-Mobile",
            lat = 42.5177369, lng = -70.8943537,
            address = "258 Washington Street",
            businessType = "shop_retail",
            tags = """["osm_shop=mobile_phone"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "miramar_printmakers", name = "MiraMar Printmakers",
            lat = 42.5197332, lng = -70.89446269999999,
            address = "24 New Derby St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://www.miramarprintmakers.com",
            description = "MiraMar Printmakers is an art studio focused on the art of printmaking. Founded in 2019 by artist duo, Sammia Atoui & Adrian Rodriguez, the studio creates limited edition original relief prints; woodblock and linocut. MiraMar, which translates to Seaview, annually creates suites of prints in the themes of Marina, Monsters, and Mythos. In addition to creating seasonal collections of art, the studio offers classes and workshops to people of all levels and experience in printmaking, drawing, and watercolor.",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "modern_millie", name = "Modern Millie",
            lat = 42.5215078, lng = -70.8936522,
            address = "3 Central Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-745-0231",
            website = "http://www.modernmillieshop.com",
            description = "Millie's is a women's dress shop specializing in vintage-inspired clothing. Millie's racks are filled with loved brands such as Hell Bunny, Retrolicious, and Collectif! Open Monday- Saturday 11-6, and Sunday 12-6. Shop online at modernmillieshop.com.",
            tags = """["ds_subcat_17", "osm_shop=clothes"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "moody_s_home_and_gifts", name = "Moody’s Home & Gifts",
            lat = 42.5224741, lng = -70.8901115,
            address = "109 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-414-5390",
            website = "https://www.moodysgifts.com",
            description = "Moody's offers a variety of artisan handmade gifts, crafted by local and international fair trade makers. Carrying a unique mix of home decor, housewares, seasonal decor, Salem-inspired souvenirs, jewelry, accessories and gifts.",
            tags = """["ds_subcat_16", "hh_venue", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "moons_gift_shop", name = "Moons Gift Shop",
            lat = 42.5215462, lng = -70.8953593,
            address = "226 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Su 10:00-19:00",
            phone = "978-744-5884",
            website = "https://moonsgiftshop.com/",
            description = "Come dance under our disco ball and feel the magic of Moons Gift Shop. Handcrafted spell kits, apothecary herbs, metaphysical supplies, Salem souvenirs & apparel, statuary, crystals, one of the largest selections of tarot and oracle decks in the city, and so much more. Open Daily.",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "new_england_dog_biscuit", name = "New England Dog Biscuit",
            lat = 42.5214187, lng = -70.8936161,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=pet"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "new_england_magic", name = "New England Magic",
            lat = 42.5221232, lng = -70.8914933,
            address = "131 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "new_market", name = "New Market",
            lat = 42.5288231, lng = -70.8898691,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "nocturne", name = "Nocturne",
            lat = 42.5207005, lng = -70.8942371,
            address = "18 Front St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://www.nocturnesalem.com/",
            description = "Nocturne is an immersive boutique experience celebrating the beauty of the night. Discover indie artists, a gothic library, handcrafted jewelry, alluring perfumes, and unique gifts in a dark romantic dreamscape.",
            tags = """["ds_subcat_16", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_east_trains", name = "North East Trains",
            lat = 42.5255375, lng = -70.926188,
            address = "18 Main Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            hours = "Tu-Fr 10:00-17:00; Sa 09:00-17:00; Dec 1-23 Mo 12:00-18:00; Dec 1-23 Su 12:00-16:00",
            phone = "978-532-1615",
            website = "https://netrains.com/",
            tags = """["osm_shop=model"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "oak_moss", name = "Oak + Moss",
            lat = 42.5205874, lng = -70.8952963,
            address = "143 Washington Street",
            businessType = "shop_retail",
            hours = "Mo-Su 10:00-18:00",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pamplemousse", name = "Pamplemousse",
            lat = 42.5215848, lng = -70.8935645,
            address = "185 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Sa 10:00-19:00; Su 10:00-19:00",
            phone = "978-745-2900",
            website = "http://pmousse.com",
            description = "European-inspired boutique featuring fine wines, craft beer, spirits, kitchen gadgets, gourmet food, and gifts! Specialty gift baskets available. Free tastings every Saturday 2 pm-4 pm.",
            tags = """["ds_subcat_16", "osm_shop=convenience"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "partridge_in_a_bear_tree", name = "Partridge in a Bear Tree",
            lat = 42.5196371, lng = -70.8881067,
            address = "82 Wharf Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-744-0548",
            website = "https://www.loc8nearme.com/massachusetts/salem/partridge-in-a-bear-tree/5274044/",
            description = "Salem’s favorite shop for holiday décor, candles, souvenirs, and unique gifts. From Christmas to Halloween to Nautical, we have it all!",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_music_box", name = "Peabody Music Box",
            lat = 42.5249579, lng = -70.9247663,
            address = "80 Main Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            phone = "978-532-3394",
            website = "https://www.facebook.com/Peabody-Music-Box-1434161596795914/",
            tags = """["osm_shop=musical_instrument"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_vapors", name = "Peabody Vapors",
            lat = 42.5252025, lng = -70.9253753,
            address = "50 Main Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            hours = "Mo-Sa 11:00-20:00; Su 12:00-17:00",
            phone = "978-548-9619",
            website = "https://www.facebook.com/Peabodyvapors",
            tags = """["osm_shop=e-cigarette"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "petsmart", name = "PetSmart",
            lat = 42.501387, lng = -70.9227724,
            address = "10 Traders Way, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Sa 09:00-21:00; Su 10:00-19:00",
            phone = "978-745-2112",
            website = "https://www.petsmart.com/stores/us/ma/salem-store1197.html",
            tags = """["osm_shop=pet"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pub_and_cloth", name = "Pub and Cloth",
            lat = 42.5223717, lng = -70.8937341,
            address = "2 East India Square Mall, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "888-405-7498",
            website = "https://www.PubandCloth.com",
            description = "At Pub & Cloth, quality comes first. Apparel & giftware are curated for all body types, ages, & genders, with unique, one-of-a-kind designs. Mix-and-match pieces coordinate easily to build versatile outfits & show your style.",
            tags = """["ds_subcat_17"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pyramid_books", name = "Pyramid Books",
            lat = 42.5208468, lng = -70.8890411,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=books"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "quick_pick_convenience", name = "Quick Pick Convenience",
            lat = 42.5308874, lng = -70.9093763,
            address = "207 North Street, Salem, MA",
            businessType = "shop_retail",
            hours = "Mo-Sa 06:00-20:00; Su 06:00-13:00",
            phone = "978-910-0475",
            website = "http://www.variety09.comcastbiz.net",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "re_find", name = "Re-Find",
            lat = 42.5220753, lng = -70.8959335,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=second_hand"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "record_exchange", name = "Record Exchange",
            lat = 42.5177698, lng = -70.8944236,
            address = "256 Washington Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-745-0777",
            website = "https://www.facebook.com/The-Record-Exchange-1405959716325639/",
            description = "The Record Exchange, a Salem institution since 1974, is a truly independent music shop buying and selling quality used, new & collectible records, CDs, DVD's Blu-rays and more! Discover a 50 year old Salem institution-buying & selling NEW & USED records, CDs, cassettes & DVDs. Since 1974, people, music & vinyl have been our passion! Record Exchange Regular 2025 Hours are: Wed-Fri: 11 to 5 Saturday: 10 to 5 Please check: Record Exchange, Salem, MA Facebook & Google Business pages for ALL updates, news, changes in hours & vacation closures! These updates have the LATEST information and hours. We look forward to seeing you & thanks for checking in! We purchase MEDIA items (excellent condition records, cassettes, CDs & DVDs) by APPOINTMENT only. Call 978-745-0777, message us on FB or email, and we’ll get back to you!",
            tags = """["ds_subcat_16", "osm_shop=music"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "red_lion_smokeshop", name = "Red Lion Smokeshop",
            lat = 42.5218592, lng = -70.8958576,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=variety_store"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "remember_salem_gifts", name = "Remember Salem Gifts",
            lat = 42.5223127, lng = -70.8908867,
            address = "127 Essex St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "http://www.salemghosttours.com/Shawn.html",
            description = "Discover a unique shop offering enchanting gifts, magical memorabilia, and themed treasures inspired by beloved stories. Perfect for fans, collectors, and anyone seeking a touch of wonder in every carefully curated item.",
            tags = """["ds_subcat_16", "hh_venue", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "richdale_convenience_store", name = "Richdale Convenience Store",
            lat = 42.515319, lng = -70.8928974,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_7", name = "Salem 7",
            lat = 42.5274556, lng = -70.9014887,
            address = "126 North Street, Salem, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_art", name = "Salem Art",
            lat = 42.5206088, lng = -70.8883784,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=art", "osm_shop=tattoo"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_bespoke_boutique", name = "Salem Bespoke Boutique",
            lat = 42.5222806, lng = -70.8909871,
            address = "127 Essex St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://SalemBespoke.com",
            description = "Visit our shop to find your very own unique witch or steampunk hat, all created in our in-house design studio! Also featuring gifts made by local artists and other women-owned small businesses from across the USA. Choose from a curated collection of art prints, crystals, mystery boxes, pottery, jewelry, candles, soaps, books, stickers and enamel pins, smudging supplies, zodiac specific gifts, and other bits and baubles. We also offer bespoke costume and bridal design services by local designer Savor Designs.",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_farmers_market", name = "Salem Farmer's Market",
            lat = 42.5207843, lng = -70.8946022,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_amenity=marketplace"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_liquors", name = "Salem Liquors",
            lat = 42.5275189, lng = -70.9017006,
            address = "128 North Street, Salem, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=alcohol"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_liquors_1", name = "Salem Liquors",
            lat = 42.5101843, lng = -70.8950416,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=alcohol"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_supermarket", name = "Salem Supermarket",
            lat = 42.5161213, lng = -70.8890871,
            address = "95 Congress Street, Salem, MA, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=supermarket"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "shaws", name = "Shaw's",
            lat = 42.5018553, lng = -70.9215171,
            address = "Salem, MA",
            businessType = "shop_retail",
            website = "https://local.shaws.com/ma/salem/11-traders-way.html",
            tags = """["osm_shop=supermarket"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "shell", name = "Shell",
            lat = 42.5065533, lng = -70.8963953,
            address = "200;208 Canal Street",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience", "osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sherwin_williams", name = "Sherwin-Williams",
            lat = 42.5032852, lng = -70.9227257,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=paint"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "speedway", name = "Speedway",
            lat = 42.5259526, lng = -70.8996138,
            address = "86 North Street, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://www.speedway.com/locations/MA/Salem/90-North-St",
            tags = """["osm_shop=convenience", "osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "speedway_1", name = "Speedway",
            lat = 42.5195617, lng = -70.8919458,
            address = "295 Derby Street, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://www.speedway.com/locations/MA/Salem/295-DERBY-ST",
            tags = """["osm_shop=convenience", "osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "spirit_market", name = "Spirit Market",
            lat = 42.5207254, lng = -70.89151129999999,
            address = "1 Liberty St, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-740-2929",
            website = "https://www.SalemWaxMuseum.com",
            description = "Discover an open-air market filled with spooky makers, seasonal treats, art, oddities, and handcrafted Salem treasures. Shop, snack, and explore right in the heart of the Haunted Neighborhood.",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sports_collectibles", name = "Sports Collectibles",
            lat = 42.5259823, lng = -70.9268035,
            address = "2 Main Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            hours = "Mo-Fr 10:00-18:00; Sa 10:00-17:00; Su 12:30-17:00",
            phone = "978-532-4686",
            website = "https://www.facebook.com/Sports-Collectibles-156510094388639/",
            tags = """["osm_shop=sports"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "spruce", name = "Spruce",
            lat = 42.5206508, lng = -70.8953001,
            address = "139 Washington Street",
            businessType = "shop_retail",
            hours = "Mo-Su 10:00-18:00",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "stardust", name = "Stardust",
            lat = 42.5186624, lng = -70.8891579,
            address = "29 Congress Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "steves_quality_market", name = "Steve's Quality Market",
            lat = 42.5191648, lng = -70.8963351,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "stop_and_shop", name = "Stop & Shop",
            lat = 42.5234027, lng = -70.9171396,
            address = "19 Howley Street, Peabody, MA, 01960",
            businessType = "shop_retail",
            hours = "Mo-Sa 07:00-22:00; Su 07:00-21:00",
            phone = "978-977-3900",
            website = "https://stores.stopandshop.com/ma/peabody/19-howley-street",
            tags = """["osm_shop=supermarket"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sunoco", name = "Sunoco",
            lat = 42.5081541, lng = -70.8957944,
            address = "145 Canal Street",
            businessType = "shop_retail",
            phone = "978-741-2577",
            website = "https://www.sunoco.com/locations/store/145-canal-st-salem-ma-0447906903",
            tags = """["osm_shop=convenience", "osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tj_maxx", name = "TJ Maxx",
            lat = 42.5022018, lng = -70.9208224,
            address = "11;17;21;29 Traders Way",
            businessType = "shop_retail",
            tags = """["osm_shop=department_store"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "target", name = "Target",
            lat = 42.503268, lng = -70.9191919,
            address = "227 Highland Avenue, Salem, MA, 01970-1830",
            businessType = "shop_retail",
            hours = "08:00-22:00",
            phone = "978-224-4000",
            website = "https://www.target.com/sl/salem-highland-ave/1803",
            tags = """["osm_shop=department_store"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_art_corner_custom_picture_frame_shop", name = "The Art Corner Custom Picture Frame Shop",
            lat = 42.5094369, lng = -70.900482,
            address = "10 Colonial Rd, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-745-9524",
            description = "The Art Corner, purveyors of fine custom picture framing since 1977. Specializing in thoughtful expert design and friendly service.",
            tags = """["ds_subcat_16"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_coven_s_cottage", name = "The Coven’s Cottage",
            lat = 42.5217164, lng = -70.8938573,
            address = "190 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_house_of_the_seven_gables", name = "The House of the Seven Gables",
            lat = 42.5218158, lng = -70.8838139,
            address = "115 Derby Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "OPEN DAILY 10 A.M. – 6 P.M.",
            phone = "978-744-0991",
            website = "https://7gables.org/",
            description = "Discover a dynamic national landmark dedicated to history, culture and community. Enjoy public programs and events, embark on tours of The House of the Seven Gables, and relax on our seaside lawn and gardens.",
            tags = """["ds_subcat_16", "ds_subcat_5", "hh_venue", "osm_tourism=museum", "osm_historic=yes"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_magic_parlor", name = "The Magic Parlor",
            lat = 42.5214831, lng = -70.894519,
            address = "215 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_trolley_depot", name = "The Trolley Depot",
            lat = 42.5215872, lng = -70.8939023,
            address = "191 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "978-745-3003 | 800-821-8179",
            website = "http://TrolleyDepot.com",
            description = "Your first stop for EVERYTHING in Salem! Trolley tickets and visitor info. Salem’s BEST tees, souvenirs, books, jewelry, great gifts, and collectibles. Celebrating our 32nd Anniversary! Open daily 9:30 am-8:00 pm.",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "timeless_life_treasures_by_kae_louise", name = "Timeless Life Treasures by Kae Louise",
            lat = 42.5199043, lng = -70.8886667,
            address = "68 Wharf St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "We-Th,Su 10:00-17:00; Fr-Sa 10:00-18:00",
            phone = "978-745-8563",
            website = "http://timelesslifetreasures.com/",
            description = "Begin your treasured memories in life with us! Browse through a wide selection of custom made fine, fashion, and estate jewelry, handmade giftware, & memorable Salem themed keepsakes. With our distinctive and exceptional selection, you’ll be the talk of the town and make a bold statement.",
            tags = """["ds_subcat_16", "osm_shop=jewelry"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tobie_s_jewelers", name = "Tobie’s Jewelers",
            lat = 42.5206172, lng = -70.8957015,
            address = "142 Washington Street",
            businessType = "shop_retail",
            hours = "We-Sa 10:00-17:00",
            tags = """["osm_shop=jewelry"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tomb", name = "Tomb",
            lat = 42.5215229, lng = -70.897406,
            address = "274 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tomo_s_tackle", name = "Tomo’s Tackle",
            lat = 42.5207243, lng = -70.8881945,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=fishing"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "trident_electronics_recycling", name = "Trident Electronics Recycling",
            lat = 42.5181983, lng = -70.9104352,
            address = "16 Proctor Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Fr 09:00-16:00",
            phone = "781-990-3333",
            website = "https://www.tridentboston.com/",
            tags = """["osm_shop=electronics"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tropicana_market", name = "Tropicana Market",
            lat = 42.5155709, lng = -70.8910545,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "twilight_house", name = "Twilight House",
            lat = 42.5214291, lng = -70.894982,
            address = "221 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=gift"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "velvet_and_vein", name = "Velvet & Vein",
            lat = 42.5215488, lng = -70.8963829,
            address = "248 Essex Street, Salem, MA",
            businessType = "shop_retail",
            website = "https://velvetandvein.com",
            tags = """["osm_shop=jewelry"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "verizon", name = "Verizon",
            lat = 42.5177782, lng = -70.8950046,
            address = "1 Canal Street",
            businessType = "shop_retail",
            hours = "Mo-Sa 10:00-20:00; Su 11:00-17:00",
            phone = "+19787400016",
            tags = """["osm_shop=mobile_phone"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "village_silversmith", name = "Village Silversmith",
            lat = 42.5217766, lng = -70.8935794,
            address = "186 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=jewelry"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "walyo_s_variety", name = "Walyo’s Variety",
            lat = 42.5246255, lng = -70.8849379,
            address = "20 Essex Street",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "whimsy_s_sweet_life", name = "Whimsy’s Sweet Life",
            lat = 42.5207241, lng = -70.8943488,
            address = "26 Front Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Sunday-Thursday 11am to 7pm<br />\n Friday-Saturday 11am to 8pm",
            phone = "978-825-9111",
            website = "https://whimsyssweetlife.com/",
            description = "Welcome to the sweet Life! Whimsy’s is a whimsical, magical ice cream, chocolate, and gift shop nestled in Downtown Salem. Indulge in 16 rotating flavors of local ice cream, over 20 varieties of fine chocolates, and freshly baked cookies. Sip on craft soda, hot chocolate, local tea, or cider, served hot or cold to match the season. Discover unique, ethically sourced treasures created by women and LGBTQ+ artisans, including hand-poured candles, energetically charged gemstones, all-natural body care, and beautifully crafted jewelry. Whimsy’s is your destination for treats, treasures, and a touch of magic!",
            tags = """["ds_subcat_16", "ds_subcat_11"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "white_dove_market_and_grill", name = "White Dove Market & Grill",
            lat = 42.5021368, lng = -70.8938574,
            address = "59 Loring Avenue, Salem, MA, 01970",
            businessType = "shop_retail",
            tags = """["osm_shop=convenience"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wicked_good_books_and_silly_bunny_toys", name = "Wicked Good Books and Silly Bunny Toys",
            lat = 42.5215376, lng = -70.8968851,
            address = "260 Essex St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Open Daily",
            phone = "978-594-1938",
            website = "https://www.wickedgoodbookstore.com",
            description = "Wicked Good Books and Silly Bunny Toys are a mom & pop book & toy store. Wicked Good Books is Salem's independent bookstore, serving Boston's north shore for a decade. WE curate a selection of books including local and witch hysteria history; sci-fi/fantasy and horror; feminist; LGBTQ and BIPOC selections, among other categories. Together with Silly Bunny Toys, we also have a solid selection of kids' books and toys, including LEGO, Squishables, Mattel and some fine European toy makers that you will surely love. We've got kids' costumes, puppets, dolls, classic books and games and more. Find us at our new location at 260 Essex St., on the way to the Witch House and to Allison's house from Hocus Pocus. Open daily. Wheelchair and stroller accessible.",
            tags = """["ds_subcat_16", "hh_venue", "osm_shop=books"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wicked_wardrobe", name = "Wicked Wardrobe",
            lat = 42.5206795, lng = -70.8884073,
            address = "Salem, MA",
            businessType = "shop_retail",
            tags = """["osm_shop=clothes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "winer_brothers_hardware", name = "Winer Brothers Hardware",
            lat = 42.5187931, lng = -70.8934436,
            address = "86 Lafayette Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Fr 07:30-17:30; Sa 08:00-17:00",
            phone = "978-744-0780",
            website = "https://winerbros.com",
            tags = """["osm_shop=doityourself"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_mall_1", name = "Witch City Mall",
            lat = 42.5222387, lng = -70.8931433,
            address = "1 Church Street, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mo-Su 08:00-21:00",
            phone = "978-744-9910",
            tags = """["osm_shop=mall"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_wicks", name = "Witch City Wicks",
            lat = 42.5215983, lng = -70.8984361,
            address = "302 Essex Street, Salem, MA, 01970",
            businessType = "shop_retail",
            phone = "617-838-7189",
            website = "http://www.witchcitywicks.com",
            description = "Experience our handcrafted, artisan soy candles made exclusively in Salem. We also offer a curated selection of books, bath products, jewelry, art prints, greeting cards, and home décor.",
            tags = """["ds_subcat_16", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wynott_s_wands", name = "Wynott’s Wands",
            lat = 42.522294, lng = -70.8909428,
            address = "127 Essex St, Salem, MA, 01970",
            businessType = "shop_retail",
            website = "https://salemmagicshop.com/",
            description = "Unlock your magical potential with Wynott's Wands, the premier destination for exquisite wands crafted for aspiring witches and wizards.",
            tags = """["ds_subcat_16", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "zitelle", name = "Zitelle",
            lat = 42.52229799999999, lng = -70.8933497,
            address = "1 Church St, Salem, MA, 01970",
            businessType = "shop_retail",
            hours = "Mon - Sat 11am to 6pm <br />\nSun 11am to 5pm",
            phone = "978-594-8405",
            website = "https://zitelle.com/",
            description = "Zitelle, a local luxury beanie company, with a twist. Come in and design your own hat, along with other customizable accessories. Shop high-quality merino wool toques, hats, beanies, bags, and gifts.",
            tags = """["ds_subcat_17"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Lodging (15)
    // ===================================================================

    val lodging = listOf(
        OutputBusiness(
            id = "hampton_inn_salem", name = "Hampton Inn Salem",
            lat = 42.5184215, lng = -70.8947147,
            address = "11 Dodge Street, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-414-3100",
            website = "https://www.hilton.com/en/hotels/bossahx-hampton-inn-salem-boston/",
            description = "The brand new Hampton Inn Salem is located in downtown Salem. Walk to all restaurants and attractions. Enjoy complimentary breakfast, an indoor pool, a large fitness center, and attached heated garage with valet parking.",
            tags = """["ds_subcat_21", "ds_parking", "osm_tourism=hotel"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hawthorne_hotel", name = "Hawthorne Hotel",
            lat = 42.5227666, lng = -70.8901546,
            address = "18 Washington Square West, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-744-4080",
            website = "https://www.hawthornehotel.com/",
            description = "The Hawthorne Hotel has been a landmark in Salem since opening its doors in 1925. This beautifully restored Federal-style historic hotel is named for celebrated author Nathaniel Hawthorne, who grew up in Salem, served as Surveyor of the Port at the Salem Custom House, and wrote The Scarlet Letter while living nearby. Ideally located in the heart of downtown Salem, the hotel overlooks historic Salem Common and is directly across from the Salem Witch Museum. Guests are just steps from the Peabody Essex Museum, The House of the Seven Gables, Pickering Wharf, and Front Street’s shops and restaurants—making the Hawthorne Hotel an ideal home base for exploring Salem and the North Shore of Massachusetts. The hotel’s 89 guest rooms blend historic character with modern comfort. Many rooms feature views of Salem Common or the surrounding historic district, while updated furnishings, refined gold accents, and comfortable amenities provide a welcoming stay. The Hawthorne is a popular choice for wedding guest accommodations, corporate travelers, and weekend getaways in Salem, MA. In 2025, the Hawthorne celebrated its 100th anniversary and was named Best Historic Hotel by Historic Hotels of America. A proud member of Historic Hotels of America and New England Inns & Resorts, the hotel is also a certified Age-Friendly Salem business. Amenities include complimentary on-site parking, full accessibility, and a welcoming atmosphere for all visitors. Meetings & Corporate Events in Downtown Salem Planning a meeting or corporate event in Salem? The Hawthorne Hotel offers over 9,000 square feet of flexible meeting and event space, including the Grand Ballroom and multiple breakout rooms. Ideal for off-site meetings, corporate retreats, trade shows, retirement celebrations, and holiday parties, the hotel combines professional amenities with historic New England character. With 89 guest rooms, a central downtown location, and discounted meeting packages, the Hawthorne provides more than just meeting space—it delivers a full-service corporate event experience. Suggested corporate itineraries and curated amenities make planning easy for businesses hosting events in Salem, MA. Special Events & Weddings in Salem, MA Located in the heart of downtown Salem overlooking the historic Salem Common, the Hawthorne Hotel is a premier Salem MA wedding venue and special event destination. The hotel features timeless event spaces, including the elegant Grand Ballroom with dramatic Palladian windows facing the Common, along with multiple smaller function rooms ideal for rehearsal dinners, bridal showers, engagement parties, and intimate celebrations. The Hawthorne accommodates weddings and celebrations from 10 to 200 guests and offers a variety of inclusive wedding packages designed for a seamless planning experience. Couples work directly with an experienced on-site events team who assist with ceremony coordination, reception details, rehearsal dinners, farewell breakfasts, overnight room blocks, and trusted local vendor referrals. With historic charm, walkable access to Salem’s attractions, and comprehensive planning support, the Hawthorne Hotel offers a complete destination wedding experience in Salem, Massachusetts.",
            tags = """["ds_subcat_21", "ds_subcat_741", "ds_subcat_13", "hh_venue", "osm_tourism=hotel"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lafeyette_hotel", name = "Lafeyette Hotel",
            lat = 42.5179242, lng = -70.8933348,
            address = "Salem, MA",
            businessType = "lodging",
            tags = """["osm_tourism=hotel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_waterfront_hotel_and_suites", name = "Salem Waterfront Hotel & Suites",
            lat = 42.5203269, lng = -70.889265,
            address = "225 Derby Street, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-740-8788",
            website = "http://SalemWaterfrontHotel.com",
            description = "The newly renovated Salem Waterfront Hotel & Suites is conveniently located on Pickering Wharf and is within walking distance of everything Salem has to offer. Free parking, indoor heated pool, and the new Mainstay Social restaurant. Salem Waterfront Hotel Winter Wedding Packages Celebrate Winter Weddings at the Salem Waterfront Hotel in January, February, and March 2023 with a complimentary room rental! Click here to learn more.",
            tags = """["ds_subcat_21", "ds_subcat_741", "hh_venue", "osm_tourism=hotel"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_coach_house", name = "The Coach House",
            lat = 42.5084224, lng = -70.891594,
            address = "284 Lafayette St, Salem, MA, 01970",
            businessType = "lodging",
            website = "https://www.coachhousesalem.com/",
            description = "The Coach House is a boutique hotel brimming with charm and luxury. Built by a sea captain in 1879, it honors Salem’s history with beautiful surroundings and intentional design, creating a magical, relaxing, and unforgettable stay.",
            tags = """["ds_subcat_21", "osm_tourism=hotel"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_cove_at_salem", name = "The Cove at Salem",
            lat = 42.5338122, lng = -70.88926529999999,
            address = "40 Bridge St, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-955-7795",
            website = "https://coveatsalem.com",
            description = "The Cove at Salem, a modern boutique hotel with a twist, immersed in Salem's rich local culture. Our 57-room hotel is a captivating blend of history & hospitality, and features themed-rooms inspired by Salem's iconic past.",
            tags = """["ds_subcat_21"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_merchant", name = "The Merchant",
            lat = 42.5204244, lng = -70.8959257,
            address = "148 Washington Street, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-745-8100",
            website = "http://TheMerchantSalem.com",
            description = "Located in the heart of the city yet privately tucked away, The Merchant is rooted in Salem’s history, offers luxurious guest rooms, high-end amenities, and the service to match. Wi-Fi. The Merchant – Gorgeous Salem Micro Weddings The Merchant offers the perfect downtown Salem venue for your intimate micro wedding or other event for 25 or fewer guests. An exquisite boutique hotel stay in the heart of Salem, The Merchant’s 11 historic (but gorgeously renovated) large, luxurious guest rooms and common spaces feature a thoughtful blend of stylish fabrics, modern furnishings, eclectic accents, and sophisticated amenities. Rent the entire inn for your wedding or special event starting at \$6,800. You’ll have the place to yourself, including all 11 guest rooms and indoor and outdoor common spaces. Hold a ceremony in our lounge, private back deck, or front lawn. We can recommend and coordinate with your chosen caterer to create the perfect, unique cocktail reception or small buffet. For more information or booking, contact (978) 745-8100 or stay@themerchantsalem.com. Visit https://www.themerchantsalem.com/salem-ma-micro-wedding-and-events/ to learn more.",
            tags = """["ds_subcat_21", "osm_tourism=hotel"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "amelia_payson_house", name = "Amelia Payson House",
            lat = 42.5258808, lng = -70.8906079,
            address = "16 Winter St, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-744-8304",
            website = "https://www.ameliapaysonhouse.com/",
            description = "Located in the heart of downtown Salem, The Amelia Payson House offers well appointed guest rooms with private baths, and all the amenities expected.",
            tags = """["ds_subcat_23", "osm_tourism=guest_house"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fidelia_bridges_guest_house", name = "Fidelia Bridges Guest House",
            lat = 42.5226475, lng = -70.8889537,
            address = "98 Essex Street",
            businessType = "lodging",
            tags = """["osm_tourism=guest_house"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "morning_glory_bed_and_breakfast", name = "Morning Glory Bed & Breakfast",
            lat = 42.5219071, lng = -70.8846091,
            address = "22 Hardy Street, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-741-1703",
            website = "http://MorningGloryBB.com",
            description = "“#1 B&B in Salem” by TripAdvisor. Steps away from Salem Harbor, Salem Ferry, and The House of the Seven Gables. Private baths, parking, hot breakfast, AC, roof deck with ocean views. Smoke-free. Wi-Fi.",
            tags = """["ds_subcat_23"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "northey_street_house_bed_and_breakfast", name = "Northey Street House Bed & Breakfast",
            lat = 42.5277561, lng = -70.892241,
            address = "30 Northey Street, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-397-1582",
            website = "http://NortheyStreetHouse.com",
            description = "Charming historic B&B built in 1809. 5 stars on Tripadvisor. Walk to attractions, train, and waterfront. Private baths, parking, breakfast, AC, TV, garden deck. Smoke-free, Wi-Fi. Check availability and book directly on NortheyStreetHouse.com For more information, to check availability, or to book directly online, Visit NortheyStreetHouse.com. Book direct and save on the commission fees that are added on by other online booking platforms.",
            tags = """["ds_subcat_23"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rv_field", name = "RV Field",
            lat = 42.5281987, lng = -70.8698148,
            address = "Salem, MA",
            businessType = "lodging",
            tags = """["osm_tourism=caravan_site"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "silsbee_s_by_the_daniels_house", name = "Silsbee’s by the Daniels House",
            lat = 42.5231242, lng = -70.8863044,
            address = "53 Essex St, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-594-8757",
            website = "https://www.danielshousesalem.com/",
            description = "Operated by the Daniels House Inn, Silsbee’s offers upscale, luxurious Victorian guest rooms, c1843, featuring wide pine floors, exposed beams, brass fixtures, Chinoiserie wallpaper and antique furniture.",
            tags = """["ds_subcat_23", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "stepping_stone_inn", name = "Stepping Stone Inn",
            lat = 42.523486, lng = -70.891155,
            address = "19 North Washington Square, Salem, MA, 01970",
            businessType = "lodging",
            phone = "978-741-8900",
            website = "http://TheSteppingStoneInn.com",
            description = "Simply the best location! On the Common, next door to the Salem Witch Museum. Six elegantly decorated rooms with queen beds, private baths, owner Matt’s famous coffee, parking and Wi-Fi. Walk everywhere!",
            tags = """["ds_subcat_23"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_warlock_house", name = "The Warlock House",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "lodging",
            website = "https://www.WarlockHouse.com",
            description = "The Warlock House beckons those who love Salem with a vacation rental where it’s Halloween every day of the year. This bubbling cauldron blends the whimsical festivities of Halloween with the mysteries of Salem Witchcraft!",
            tags = """["ds_subcat_23"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Event Venues (68)
    // ===================================================================

    val venue = listOf(
        OutputBusiness(
            id = "252_bridge_street", name = "252 Bridge Street",
            lat = 42.5245895, lng = -70.8963483,
            address = "252 Bridge Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "260_lafayette_street", name = "260 Lafayette Street",
            lat = 42.5097718, lng = -70.8919419,
            address = "260 Lafayette Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "abbott_street_cemetery", name = "Abbott Street Cemetery",
            lat = 42.5472186, lng = -70.8765169,
            address = "Abbott Street, Beverly, Massachusetts, 01915",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ames_memorial_hall", name = "Ames Memorial Hall",
            lat = 42.5215591, lng = -70.8979286,
            address = "290 Essex Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "(978) 740-9622",
            website = "http://www.northshoreymca.org",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bit_bar_1", name = "Bit Bar",
            lat = 42.5244826, lng = -70.8932191,
            address = "278 Derby St, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-594-4838",
            website = "https://bit.bar/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "blackcraft_ceremonies", name = "Blackcraft Ceremonies",
            lat = 42.5212608, lng = -70.8964933,
            address = "253 Essex St, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-943-6066",
            website = "https://www.blackcraftsalem.com",
            description = "Blackcraft Salem proudly introduces Blackcraft Ceremonies–a hauntingly beautiful venue in the heart of downtown Salem, MA. Designed for elopements, proposals, and vow renewals, this intimate space blends gothic romance with eerie elegance, offering couples a truly unforgettable setting to say “til death do us part.”",
            tags = """["ds_subcat_741"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "burying_point_productions", name = "Burying Point Productions",
            lat = 42.5204357, lng = -70.8914193,
            address = "282 Derby St, Salem, MA, 01970",
            businessType = "venue",
            phone = "9787402929",
            website = "https://buryingpointproductions.com/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cinemasalem", name = "CinemaSalem",
            lat = 42.5225961, lng = -70.8927733,
            address = "1 East India Square Mall, Salem, MA",
            businessType = "venue",
            phone = "978-744-1400",
            website = "http://www.CinemaSalem.com",
            tags = """["hh_venue", "osm_amenity=cinema"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "collins_cove", name = "Collins Cove",
            lat = 42.529481, lng = -70.880372,
            address = "Settlers Way, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "colonial_hall", name = "Colonial Hall",
            lat = 42.5212998, lng = -70.8952278,
            address = "227 Essex St, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-745-2411",
            website = "https://www.colonialhallatrockafellas.com/",
            description = "Weddings and events are offered at Colonial Hall year-round and range in size from 40-300 guests. Each event is customized and includes catering, service, and equipment. Colonial Hall is located above Rockafellas restaurant inside of the historic Daniel Low and Co. Building. It was formerly a church, a bank and once the largest jewelry store in the country. It is truly a cornerstone of Salem history. To learn more or to request a private tour with our event planning team, call 978-745-5415 or email sales@colonialhallatrockafellas.com Weddings and events are offered at Colonial Hall year round and range in size from 40 guests to 300 guests. Each event is customized and includes catering, service and equipment. Colonial Hall is located above Rockafellas restaurant inside of the historic Daniel Low and Co Building. It was formerly a church, a bank and once the largest jewelry store in the country. It is truly a cornerstone of Salem history. Our grand ballroom with vaulted ceilings, gleaming hardwood floors and gracious balconies is the perfect setting for your next special occasion. Let Colonial Hall bring simple elegance to your next wedding or event.",
            tags = """["ds_subcat_741", "hh_venue", "osm_amenity=events_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "deacon_giles_distillery", name = "Deacon Giles Distillery",
            lat = 42.513299, lng = -70.8961532,
            address = "Canal Street, Salem, Massachusetts, 01970",
            businessType = "venue",
            hours = "Mo 06:00-22:00; Th 17:00-21:00; Fr 14:00-23:00; Sa 12:00-23:00; Su 12:00-20:00",
            phone = "978-306-6675",
            website = "https://www.deacongiles.com/",
            tags = """["hh_venue", "osm_craft=brewery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "derby_square", name = "Derby Square",
            lat = 42.5210083, lng = -70.8945602,
            address = "32 Derby Square, Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.salem.org/venue/derby-square/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "derby_wharf", name = "Derby Wharf",
            lat = 42.5179574, lng = -70.8848036,
            address = "Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.hauntedhappenings.org/venue/derby-wharf/",
            description = "Begun in 1762 and completed in 1806, it extends 2,045 feet into the Salem Harbor. At one time, it was home to nearly twenty structures, During the Revolution privateers used the wharf probably more than any other port facility in the colonies.",
            tags = """["hh_venue", "osm_historic=maritime", "osm_leisure=park"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "downtown_salem", name = "Downtown Salem",
            lat = 42.5235097, lng = -70.8952337,
            address = "Essex St, Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.salem.org/venue/downtown-salem/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "east_india_fountain", name = "East India Fountain",
            lat = 42.5219546, lng = -70.8927912,
            address = "158 Essex Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue", "osm_amenity=fountain"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "emerson_hall_theater", name = "Emerson Hall Theater",
            lat = 42.5228158, lng = -70.896197,
            address = "50 Washington Street, Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.salem.org/venue/emerson-hall-theater/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_heritage_imprisoned_1692", name = "Essex Heritage – Imprisoned! 1692",
            lat = 42.5234534, lng = -70.8934746,
            address = "35 St. Peter Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-740-0444",
            website = "http://www.essexheritage.org",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_lodge_of_freemasons", name = "Essex Lodge of Freemasons",
            lat = 42.5221515, lng = -70.8959943,
            address = "70 Washington Street, 5th Floor, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "flatbread_company", name = "Flatbread Company",
            lat = 42.5194918, lng = -70.8923076,
            address = "311 Derby Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-744-2829",
            website = "http://www.flatbreadcompany.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fountain_stage", name = "Fountain Stage",
            lat = 42.5214907, lng = -70.8951268,
            address = "Essex Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "frank_l_wiggin_auditorium", name = "Frank L. Wiggin Auditorium",
            lat = 42.5260068, lng = -70.9288125,
            address = "24 Lowell Street, Peabody, MA, 01960",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "grace_and_diggs", name = "Grace & Diggs",
            lat = 42.5197422, lng = -70.8944557,
            address = "24 New Derby Street, Salem, MA, 01970",
            businessType = "venue",
            website = "http://www.graceanddiggs.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "grace_episcopal_church", name = "Grace Episcopal Church",
            lat = 42.5194218, lng = -70.9032184,
            address = "385 Essex Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-744-2796",
            website = "http://www.gracechurchsalem.org",
            tags = """["hh_venue", "osm_amenity=place_of_worship"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "greenlawn_cemetery", name = "Greenlawn Cemetery",
            lat = 42.533198, lng = -70.9028054,
            address = "57 Orne St, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue", "osm_historic=cemetery"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hale_farm", name = "Hale Farm",
            lat = 42.5490748, lng = -70.874263,
            address = "39 Hale Street, Beverly, 01915",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hamilton_hall", name = "Hamilton Hall",
            lat = 42.5196459, lng = -70.8991675,
            address = "9 Chestnut Street, Salem, 01970",
            businessType = "venue",
            phone = "978-744-0805",
            website = "https://www.hamiltonhall.org/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "harborpoint_at_root", name = "Harborpoint at Root",
            lat = 42.5177088, lng = -70.8875162,
            address = "Shetland Park, 35 Congress Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "historic_salem_inc", name = "Historic Salem, Inc.",
            lat = 42.5217541, lng = -70.8988352,
            address = "9 North Street, Salem, MA, 01970",
            businessType = "venue",
            website = "http://www.HistoricSalem.org",
            tags = """["hh_venue", "osm_office=yes"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hive_and_forge", name = "Hive & Forge",
            lat = 42.5226332, lng = -70.8951149,
            address = "30 Church Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-809-7414",
            website = "http://www.hiveandforge.com",
            tags = """["hh_venue", "osm_shop=esoteric"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hollowed_harvest", name = "Hollowed Harvest",
            lat = 42.5212415, lng = -70.8983811,
            address = "301 Essex Street, Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.salem.org/venue/hollowed-harvest/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jean_a_levesque_community_life_center", name = "Jean A. Levesque Community Life Center",
            lat = 42.5203513, lng = -70.9071248,
            address = "401 Bridge Street, Salem, Massachusetts, 01970",
            businessType = "venue",
            tags = """["hh_venue", "osm_amenity=community_centre"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "john_cabot_house_and_visitor_center", name = "John Cabot House and Visitor Center",
            lat = 42.5458772, lng = -70.879895,
            address = "Cabot Street, Beverly, Massachusetts, 01915",
            businessType = "venue",
            phone = "(978) 922-1186",
            website = "https://historicbeverly.net/visit/our-locations/cabot-house/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jolie_tea_company_1", name = "Jolie Tea Company",
            lat = 42.522424, lng = -70.889903,
            address = "105 Essex Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-745-5654",
            website = "http://www.jolieteaco.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "larcom_theatre", name = "Larcom Theatre",
            lat = 42.5481592, lng = -70.879767,
            address = "13 Wallis St, Beverly, MA, 01915",
            businessType = "venue",
            phone = "(978) 922-6313",
            website = "https://www.thelarcom.org/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mack_park", name = "Mack Park",
            lat = 42.524295, lng = -70.9113025,
            address = "31 Grove Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "maison_vampyre", name = "Maison Vampyre",
            lat = 42.5211769, lng = -70.8946171,
            address = "One Derby Square, Salem, MA, 01970",
            businessType = "venue",
            phone = "351-209-5851",
            website = "https://www.vampirehouse.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "marine_hall_mercantile", name = "Marine Hall Mercantile",
            lat = 42.5216621, lng = -70.8933287,
            address = "Essex Street, Salem, Massachusetts, 01970",
            businessType = "venue",
            website = "https://shop.pem.org/",
            tags = """["hh_venue", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_shore_community_development_coalition", name = "North Shore Community Development Coalition",
            lat = 42.5183887, lng = -70.8935505,
            address = "96 Lafayette Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-825-4004",
            website = "http://northshorecdc.org/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "nu_aeon_home_of_white_light_pentacles_inc_and_the_cosmic_connection", name = "Nu Aeon: Home of White Light Pentacles, Inc. & The Cosmic Connection",
            lat = 42.5200547, lng = -70.8880876,
            address = "Pickering Wharf, 88 Wharf Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-744-0202",
            website = "http://www.nuaeon.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ocean_chic_boutique_and_waterbar", name = "Ocean Chic Boutique & Waterbar",
            lat = 42.5203654, lng = -70.8879416,
            address = "96 Wharf Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-594-4560",
            website = "http://www.shopoceanchic.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "off_cabot_comedy_and_events", name = "Off Cabot Comedy & Events",
            lat = 42.5481785, lng = -70.8822265,
            address = "Wallis Street, Beverly, MA, 01915",
            businessType = "venue",
            website = "https://offcabot.org/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "opus_underground", name = "Opus Underground",
            lat = 42.522002, lng = -70.8955716,
            address = "87 Washington Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-744-9600",
            website = "http://www.salemopus.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pedestrian_mall", name = "Pedestrian Mall",
            lat = 42.5214907, lng = -70.8951268,
            address = "Essex Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "punto_urban_art_museum_1", name = "Punto Urban Art Museum",
            lat = 42.5235097, lng = -70.8952337,
            address = "Salem, MA, 01970",
            businessType = "venue",
            website = "http://www.puntourbanartmuseum.org",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "remix_church", name = "Remix Church",
            lat = 42.5225153, lng = -70.8938001,
            address = "9 Church Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-745-1812",
            website = "http://www.remixsalem.org",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ropes_mansion", name = "Ropes Mansion",
            lat = 42.5212118, lng = -70.8998011,
            address = "318 Essex Street, Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.pem.org",
            tags = """["hh_venue", "osm_historic=manor"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "satv", name = "SATV",
            lat = 42.51954, lng = -70.8967155,
            address = "Salem, MA",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_arts_association_1", name = "Salem Arts Association",
            lat = 42.5238603, lng = -70.8949085,
            address = "159 Derby Street, Salem, MA, 01970",
            businessType = "venue",
            website = "http://www.salemarts.org",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_haunted_magic_show", name = "Salem Haunted Magic Show",
            lat = 42.5228158, lng = -70.896197,
            address = "50 Washington Street, Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.TheSalemMagicShow.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_moose_lodge", name = "Salem Moose Lodge",
            lat = 42.5224162, lng = -70.9109784,
            address = "50 Grove Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_state_university", name = "Salem State University",
            lat = 42.5041326, lng = -70.8902384,
            address = "352 Lafayette Street, Salem, MA, 01970",
            businessType = "venue",
            website = "salemstate.edu",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_state_university_recital_hall", name = "Salem State University Recital Hall",
            lat = 42.5005418, lng = -70.8948584,
            address = "71 Loring Ave, Salem, MA, 01970",
            businessType = "venue",
            website = "https://www.salem.org/venue/salem-state-university-recital-hall/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_state_university_callan_studio_theater", name = "Salem State University – Callan Studio Theater",
            lat = 42.5034893, lng = -70.889835,
            address = "356 Lafayette Street, Salem, MA, 01970",
            businessType = "venue",
            website = "http://www.salemstatetickets.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_state_university_sophia_gordon_center", name = "Salem State University – Sophia Gordon Center",
            lat = 42.503587, lng = -70.8900554,
            address = "356 Lafayette Street, Salem, MA, 01970",
            businessType = "venue",
            website = "http://www.salemstatetickets.com",
            tags = """["hh_venue", "osm_amenity=theatre"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_willows_1", name = "Salem Willows",
            lat = 42.5293609, lng = -70.8758678,
            address = "Fort Avenue, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_wine_imports", name = "Salem Wine Imports",
            lat = 42.5226992, lng = -70.8954659,
            address = "32 Church Street, Salem, MA",
            businessType = "venue",
            phone = "978-741-9463",
            website = "http://www.SalemWineImports.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_in_1630_pioneer_village", name = "Salem in 1630: Pioneer Village",
            lat = 42.5070492, lng = -70.8855729,
            address = "Forest River Park, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-744-8815",
            website = "http://www.thewitchhouse.org",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sperling_interactive", name = "Sperling Interactive",
            lat = 42.520976, lng = -70.8949085,
            address = "10 Derby Sq., Salem, MA, 01970",
            businessType = "venue",
            phone = "555-555-5555",
            website = "https://sperlinginteractive.com",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "st_peter_s_church", name = "St. Peter’s Church",
            lat = 42.5235097, lng = -70.8952337,
            address = "24 St. Peter's Street, Salem, 01970",
            businessType = "venue",
            phone = "978-745-2291",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "st_peter_s_san_pedro_episcopal_church", name = "St. Peter’s-San Pedro Episcopal Church",
            lat = 42.5229161, lng = -70.8928649,
            address = "24 St Peter Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "(978) 745-2291",
            website = "https://www.stpeters-sanpedro.org/",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "tabernacle_church", name = "Tabernacle Church",
            lat = 42.5228131, lng = -70.8961056,
            address = "50 Washington Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_bridge_at_211", name = "The Bridge at 211",
            lat = 42.5239116, lng = -70.8948838,
            address = "211 Bridge Street, Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue", "osm_amenity=community_centre"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_derby", name = "The Derby",
            lat = 42.5197989, lng = -70.8949683,
            address = "189 Washington Street, Salem, 01970",
            businessType = "venue",
            phone = "978-740-2337",
            website = "https://www.thederbysalem.com/",
            tags = """["hh_venue", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "vampfangs", name = "Vampfangs",
            lat = 42.5215434, lng = -70.8962389,
            address = "244 Essex Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-594-8163",
            website = "http://www.vampfangs.com",
            tags = """["hh_venue", "osm_shop=clothes"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "waite_and_peirce", name = "Waite and Peirce",
            lat = 42.5208838, lng = -70.8878759,
            address = "193 Derby Street, Salem, MA, 01970",
            businessType = "venue",
            phone = "978-744-4319",
            website = "http://www.waiteandpeirce.com",
            tags = """["hh_venue", "osm_shop=gift"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_hibachi", name = "Witch City Hibachi",
            lat = 42.5184043, lng = -70.8933754,
            address = "Lafayette Street, Salem, MA, 01970",
            businessType = "venue",
            hours = "Mo-Th 11:30-22:00; Fr-Sa 11:30-23:00; Su 12:00-22:00",
            phone = "978-594-0832",
            website = "https://www.witchcityhibachi.com/",
            tags = """["hh_venue", "osm_amenity=restaurant"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_mall", name = "Witch City Mall",
            lat = 42.5235097, lng = -70.8952337,
            address = "Salem, MA, 01970",
            businessType = "venue",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_trials_memorial", name = "Witch Trials Memorial",
            lat = 42.5206175, lng = -70.892236,
            address = "Charter/Liberty Streets, Salem, MA, 01970",
            businessType = "venue",
            website = "http://www.salemaward.org",
            tags = """["hh_venue"]""",
            provenance = Provenance("api_sync", 0.7f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Parks & Open Space (51)
    // ===================================================================

    val park = listOf(
        OutputBusiness(
            id = "armory_park", name = "Armory Park",
            lat = 42.5223091, lng = -70.891887,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "broad_street_park", name = "Broad Street Park",
            lat = 42.514864, lng = -70.9039077,
            address = "29 Highland Avenue, Salem, MA",
            businessType = "park",
            website = "http://media.wix.com/ugd/2294a8_3c10e61137c243beb7bdf82bf1b064b7.pdf",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "buckley_field", name = "Buckley Field",
            lat = 42.5315698, lng = -70.9252465,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cabot_farm_playground", name = "Cabot Farm Playground",
            lat = 42.5348945, lng = -70.8991781,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=playground"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "castle_hill_park", name = "Castle Hill Park",
            lat = 42.5048504, lng = -70.9044228,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "collins_cove_park", name = "Collins Cove Park",
            lat = 42.5298101, lng = -70.8868183,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "connelly_park", name = "Connelly Park",
            lat = 42.5321419, lng = -70.919674,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "conservation_land", name = "Conservation Land",
            lat = 42.5020845, lng = -70.914272,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=nature_reserve"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "conservation_land_1", name = "Conservation Land",
            lat = 42.5001948, lng = -70.9176762,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=nature_reserve"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "conservation_land_2", name = "Conservation Land",
            lat = 42.5075144, lng = -70.9073881,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=nature_reserve"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "conservation_land_3", name = "Conservation Land",
            lat = 42.5014447, lng = -70.9012487,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=nature_reserve"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "conservation_land_4", name = "Conservation Land",
            lat = 42.5009101, lng = -70.9279293,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=nature_reserve"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cottage_street_playground", name = "Cottage Street Playground",
            lat = 42.5183216, lng = -70.9239813,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=playground"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "curtis_park", name = "Curtis Park",
            lat = 42.5327758, lng = -70.8897995,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "david_j_beattie_park", name = "David J. Beattie Park",
            lat = 42.5254808, lng = -70.8818145,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dibiase_park", name = "Dibiase Park",
            lat = 42.5111859, lng = -70.9201317,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "east_end_veterans_memorial_park", name = "East End Veterans Memorial Park",
            lat = 42.5261292, lng = -70.9237447,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fort_lee", name = "Fort Lee",
            lat = 42.531542, lng = -70.8744645,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "furlong_park", name = "Furlong Park",
            lat = 42.5283749, lng = -70.8975598,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gallows_hill_park", name = "Gallows Hill Park",
            lat = 42.5158335, lng = -70.9123422,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gonyea_park", name = "Gonyea Park",
            lat = 42.5277394, lng = -70.8931345,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hatches_wharf", name = "Hatches Wharf",
            lat = 42.5208922, lng = -70.8871476,
            address = "Derby Street, Salem, MA, 01970",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hawthorn_pond_conservation_area", name = "Hawthorn Pond Conservation Area",
            lat = 42.500413, lng = -70.8697958,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=nature_reserve"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "high_street_park", name = "High Street Park",
            lat = 42.5183407, lng = -70.8969952,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "irzyk_park", name = "Irzyk Park",
            lat = 42.5269954, lng = -70.8809596,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jeggle_island", name = "Jeggle Island",
            lat = 42.5020112, lng = -70.8854683,
            address = "Salem, MA",
            businessType = "park",
            description = "A tidal island that can be reached by boat. At low tide it is connected to the land by mud flats. The mud flats lead to private property, so you must use a boat to reach it. There is no place to tie a boat at the island.",
            tags = """["osm_leisure=nature_reserve"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lafayette_park", name = "Lafayette Park",
            lat = 42.517055, lng = -70.8934216,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "leslies_retreat_park", name = "Leslie's Retreat Park",
            lat = 42.5228628, lng = -70.9027774,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mack_park_1", name = "Mack Park",
            lat = 42.5238841, lng = -70.908536,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mansell_playground", name = "Mansell Playground",
            lat = 42.5165362, lng = -70.9104031,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=playground"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "marrs_park", name = "Marrs Park",
            lat = 42.5160869, lng = -70.9268929,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mary_jane_lee_park", name = "Mary Jane Lee Park",
            lat = 42.5160923, lng = -70.8901091,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mccabe_park", name = "McCabe Park",
            lat = 42.5397738, lng = -70.899264,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mcglew_park", name = "McGlew Park",
            lat = 42.5301791, lng = -70.9093018,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mcgrath_park", name = "McGrath Park",
            lat = 42.5085438, lng = -70.9227964,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "o_connor_park", name = "O\"Connor Park",
            lat = 42.5163542, lng = -70.9293117,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "old_slide", name = "Old Slide",
            lat = 42.5059606, lng = -70.8835172,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=playground"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "palmer_cove_park", name = "Palmer Cove Park",
            lat = 42.5140429, lng = -70.8893159,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "patten_park", name = "Patten Park",
            lat = 42.527992, lng = -70.9048034,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_street_park", name = "Peabody Street Park",
            lat = 42.5188645, lng = -70.8921074,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pierpont_park", name = "Pierpont Park",
            lat = 42.521526, lng = -70.9218637,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rainbow_terrace_playground", name = "Rainbow Terrace Playground",
            lat = 42.5024286, lng = -70.8915962,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=playground"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "remond_park", name = "Remond Park",
            lat = 42.5374483, lng = -70.8868935,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "riley_plaza", name = "Riley Plaza",
            lat = 42.519544, lng = -70.8958869,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_community_gardens", name = "Salem Community Gardens",
            lat = 42.5139154, lng = -70.8883751,
            address = "Salem, MA",
            businessType = "park",
            website = "https://www.salemcommunitygardens.org/",
            tags = """["osm_leisure=garden"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "splaine_park", name = "Splaine Park",
            lat = 42.5171638, lng = -70.9070928,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sullivan_front_lawn", name = "Sullivan Front Lawn",
            lat = 42.5047512, lng = -70.8908229,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "swiniuch_park", name = "Swiniuch Park",
            lat = 42.5223749, lng = -70.8851459,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "upper_quad", name = "Upper Quad",
            lat = 42.5037881, lng = -70.8910224,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "winter_island_maritime_park", name = "Winter Island Maritime Park",
            lat = 42.5272656, lng = -70.8690662,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witchcraft_heights_playground", name = "Witchcraft Heights Playground",
            lat = 42.5151955, lng = -70.9206355,
            address = "Salem, MA",
            businessType = "park",
            tags = """["osm_leisure=playground"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Public Services (62)
    // ===================================================================

    val public = listOf(
        OutputBusiness(
            id = "aspire_developmental_services", name = "Aspire Developmental Services",
            lat = 42.5091673, lng = -70.8906087,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=social_facility"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "columbus_society_of_salem", name = "Columbus Society of Salem",
            lat = 42.5244522, lng = -70.9012619,
            address = "18 Commercial Street, Salem, MA, 01970",
            businessType = "public",
            phone = "978-745-6536",
            tags = """["osm_amenity=social_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "espacio_el_punto", name = "Espacio El Punto",
            lat = 42.5156482, lng = -70.8891594,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=community_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "polish_league_of_american_veterans", name = "Polish League of American Veterans",
            lat = 42.5226733, lng = -70.886334,
            address = "9 Daniels Street",
            businessType = "public",
            tags = """["osm_amenity=community_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_pantry", name = "Salem Pantry",
            lat = 42.5150849, lng = -70.8894934,
            address = "47 Leavitt Street, Salem, MA, 01970",
            businessType = "public",
            hours = "Tu,Th,Fr 10:00-12:00,13:00-16:30; We 16:00-20:00; Sa 10:00-14:00",
            phone = "978-552-3954",
            website = "https://www.thesalempantry.org/",
            tags = """["osm_amenity=social_facility", "osm_shop=charity"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_salvation_army_north_shore_community_center_and_church", name = "The Salvation Army North Shore Community Center & Church",
            lat = 42.5258441, lng = -70.9004692,
            address = "93 North Street, Salem, MA, 01970",
            businessType = "public",
            tags = """["osm_amenity=community_centre", "osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_county_court_buildings", name = "Essex County Court Buildings",
            lat = 42.523241, lng = -70.8972542,
            address = "MA",
            businessType = "public",
            tags = """["osm_amenity=courthouse"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_city_hall", name = "Peabody City Hall",
            lat = 42.5260009, lng = -70.9288339,
            address = "24 Lowell Street, Peabody, MA, 01960",
            businessType = "public",
            tags = """["osm_amenity=townhall"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_district_court", name = "Peabody District Court",
            lat = 42.5265884, lng = -70.9276587,
            address = "1 Lowell Street",
            businessType = "public",
            tags = """["osm_amenity=courthouse"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_fire_department", name = "Peabody Fire Department",
            lat = 42.5296162, lng = -70.9176186,
            address = "96 Tremont Street, Peabody, MA, 01960",
            businessType = "public",
            tags = """["osm_amenity=fire_station", "osm_office=Engine 4"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_fire_department_1", name = "Peabody Fire Department",
            lat = 42.5267767, lng = -70.928994,
            address = "47 Lowell Street, Peabody, MA, 01960",
            businessType = "public",
            tags = """["osm_amenity=fire_station"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_city_fire_department", name = "Salem City Fire Department",
            lat = 42.5269631, lng = -70.8795356,
            address = "MA",
            businessType = "public",
            tags = """["osm_amenity=fire_station"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_district_court", name = "Salem District Court",
            lat = 42.5232663, lng = -70.897887,
            address = "56 Federal Street, Salem, MA, 01970",
            businessType = "public",
            hours = "Mo-Fr 08:30-16:30",
            website = "https://www.mass.gov/locations/salem-district-court",
            tags = """["osm_amenity=courthouse"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_fire_department", name = "Salem Fire Department",
            lat = 42.5283551, lng = -70.9028455,
            address = "142 North Street, Salem, MA, 01970",
            businessType = "public",
            tags = """["osm_amenity=fire_station", "osm_office=Station 2"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_fire_department_1", name = "Salem Fire Department",
            lat = 42.5171763, lng = -70.9057424,
            address = "415 Essex Street, Salem, MA, 01970",
            businessType = "public",
            tags = """["osm_amenity=fire_station", "osm_office=Station 4"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_fire_department_2", name = "Salem Fire Department",
            lat = 42.5197971, lng = -70.8936981,
            address = "48 Lafayette Street, Salem, MA, 01970",
            businessType = "public",
            tags = """["osm_amenity=fire_station", "osm_office=S1 Headquarters"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_fire_department_station_5", name = "Salem Fire Department - Station 5",
            lat = 42.5027018, lng = -70.8940713,
            address = "64 Loring Avenue, Salem, MA, 01970",
            businessType = "public",
            tags = """["osm_amenity=fire_station"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_police_department", name = "Salem Police Department",
            lat = 42.5157421, lng = -70.8966075,
            address = "95 Margin Street, Salem, MA, 01970",
            businessType = "public",
            tags = """["osm_amenity=police"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "university_police", name = "University Police",
            lat = 42.5006068, lng = -70.8937915,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=police"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_law_library", name = "Essex Law Library",
            lat = 42.52306, lng = -70.89611,
            address = "34 Federal Street, Salem, MA, 01970",
            businessType = "public",
            hours = "Mo-Fr 09:00-12:00,13:00-16:00",
            phone = "978-741-0674",
            website = "https://www.mass.gov/locations/essex-law-library",
            tags = """["osm_amenity=library", "osm_office=Massachusetts Trial Court Law Libraries"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "frederick_e_berry_library", name = "Frederick E. Berry Library",
            lat = 42.5024654, lng = -70.8907225,
            address = "4 College Drive, Salem, MA, 01970",
            businessType = "public",
            phone = "978-542-6230",
            website = "https://libguides.salemstate.edu/home",
            tags = """["osm_amenity=library"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "health_sciences_library", name = "Health Sciences Library",
            lat = 42.5115442, lng = -70.9067745,
            address = "81 Highland Avenue, Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=library", "osm_office=Salem Hospital"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_institute_library", name = "Peabody Institute Library",
            lat = 42.5250046, lng = -70.9242028,
            address = "82 Main Street, Peabody, MA, 01960",
            businessType = "public",
            hours = "Mo-Th 09:00-21:00, Fr 09:00-13:00, Sa 09:00-17:00, Su 13:00-17:00",
            website = "https://peabodylibrary.org/",
            tags = """["osm_amenity=library"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "stephen_phillips_archives", name = "Stephen Phillips Archives",
            lat = 42.52239, lng = -70.89574,
            address = "81 Washington Street, Salem, MA, 01970",
            businessType = "public",
            tags = """["osm_amenity=library", "osm_office=Stephen Phillips Trust House"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "first_baptist_church_of_salem", name = "First Baptist Church of Salem",
            lat = 42.507841, lng = -70.891654,
            address = "292;296 Lafayette Street",
            businessType = "public",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "first_united_methodist_church", name = "First United Methodist Church",
            lat = 42.523151, lng = -70.9239384,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "immaculate_conception_church", name = "Immaculate Conception Church",
            lat = 42.5213768, lng = -70.8896216,
            address = "15 Hawthorne Boulevard",
            businessType = "public",
            phone = "+1978-745-9060",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "magika", name = "Magika",
            lat = 42.5216634, lng = -70.9013822,
            address = "107 Federal Street, Salem, MA, 01970",
            businessType = "public",
            description = "Magika is a traditional witchcraft store in Salem, Massachusetts — and the long-time vision of Lori Bruno, Sicilian Strega and high priestess of the Old Craft.",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ministerios_casa_de_oraci_n", name = "Ministerios Casa De Oración",
            lat = 42.5167657, lng = -70.8913825,
            address = "21 Salem Street",
            businessType = "public",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_nicholas_orthodox_church", name = "Saint Nicholas Orthodox Church",
            lat = 42.5258615, lng = -70.8847238,
            address = "64 Forrester Street, Salem, MA, 01970",
            businessType = "public",
            phone = "978-744-5869",
            website = "https://www.orthodoxsalem.com/",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_vasilios_church", name = "Saint Vasilios Church",
            lat = 42.5268124, lng = -70.9211085,
            address = "5;7 Paleologos Street, Peabody, MA",
            businessType = "public",
            phone = "978-531-0777",
            website = "https://www.stvasilios.org/",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "st_john_the_baptist_ukrainian_church", name = "St. John The Baptist Ukrainian Church",
            lat = 42.5273977, lng = -70.8908478,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wesley_united_methodist_church", name = "Wesley United Methodist Church",
            lat = 42.5221271, lng = -70.8982522,
            address = "8;18 North Street",
            businessType = "public",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "world_international_ministries", name = "World International Ministries",
            lat = 42.5161485, lng = -70.905909,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=place_of_worship"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bates_elementary_school", name = "Bates Elementary School",
            lat = 42.535517, lng = -70.9053028,
            address = "53 Liberty Hill Avenue, Salem, 01970",
            businessType = "public",
            phone = "978 740 1250",
            website = "https://spsbates.salemk12.org/",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bentley_academy_charter_school", name = "Bentley Academy Charter School",
            lat = 42.527635, lng = -70.8811477,
            address = "Salem, MA",
            businessType = "public",
            phone = "978-740-1260",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bishop_fenwick_high_school", name = "Bishop Fenwick High School",
            lat = 42.5387908, lng = -70.9154052,
            address = "99 Margin Street, Peabody, MA, 01960",
            businessType = "public",
            phone = "978-587-8300",
            website = "https://www.fenwick.org/",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "carlton_school", name = "Carlton School",
            lat = 42.5317624, lng = -70.8902618,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "collins_middle_school", name = "Collins Middle School",
            lat = 42.5143389, lng = -70.9049364,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "endicott_school", name = "Endicott School",
            lat = 42.5214844, lng = -70.9125493,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "farnsworth_school", name = "Farnsworth School",
            lat = 42.5334286, lng = -70.9278276,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "phoenix", name = "Phoenix",
            lat = 42.5207565, lng = -70.9100997,
            address = "28 Goodhue Street, Salem, MA, 01970",
            businessType = "public",
            phone = "978-741-0870",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pickering_school", name = "Pickering School",
            lat = 42.5289843, lng = -70.9069937,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_anne_school", name = "Saint Anne School",
            lat = 42.5028736, lng = -70.9017153,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_james_school", name = "Saint James School",
            lat = 42.5203733, lng = -70.9058824,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_josephs_school", name = "Saint Josephs School",
            lat = 42.5167623, lng = -70.8922709,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_josephs_school_1", name = "Saint Josephs School",
            lat = 42.5350952, lng = -70.9272721,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_marys_school", name = "Saint Marys School",
            lat = 42.5214845, lng = -70.8900487,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saint_thomas_school", name = "Saint Thomas School",
            lat = 42.5334286, lng = -70.9136606,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_academy_charter_school", name = "Salem Academy Charter School",
            lat = 42.5178125, lng = -70.8875068,
            address = "45 Congress Street, Salem, MA, 01970",
            businessType = "public",
            phone = "978 744 2105",
            website = "https://www.salemacademycs.org/",
            description = "Middle and High Charter School serving students in Salem, MA and surrounding towns.",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_community_child_care", name = "Salem Community Child Care",
            lat = 42.516169, lng = -70.8895457,
            address = "90 Congress Street, Salem, MA, 01970",
            businessType = "public",
            phone = "978 745-6500",
            tags = """["osm_amenity=childcare"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_early_childhood_center", name = "Salem Early Childhood Center",
            lat = 42.5280828, lng = -70.8804558,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_high_school", name = "Salem High School",
            lat = 42.5057506, lng = -70.9114774,
            address = "77 Willson Street, Salem, MA, 01970",
            businessType = "public",
            phone = "978-740-1123",
            website = "https://spssalemhs.salemk12.org/",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "saltonstall_school", name = "Saltonstall School",
            lat = 42.5135128, lng = -70.8912684,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "seeglitz_junior_high_school", name = "Seeglitz Junior High School",
            lat = 42.5314842, lng = -70.9272721,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "thomas_carroll_school", name = "Thomas Carroll School",
            lat = 42.5324938, lng = -70.9181428,
            address = "60 Northend Street, Peabody, 01960",
            businessType = "public",
            phone = "978 536 4200",
            website = "http://www.peabody.k12.ma.us/carroll",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witchcraft_heights_elementary_school", name = "Witchcraft Heights Elementary School",
            lat = 42.5164997, lng = -70.9205482,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=school"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "northeastern_massachusetts_aquaculture_center", name = "Northeastern Massachusetts Aquaculture Center",
            lat = 42.5301722, lng = -70.871977,
            address = "Fort Avenue, Salem, MA",
            businessType = "public",
            website = "https://salemstate.instructure.com/courses/1166744/pages/northeastern-massachusetts-aquaculture-center",
            tags = """["osm_amenity=university"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_state_university_north_campus", name = "Salem State University North Campus",
            lat = 42.5023797, lng = -70.8911021,
            address = "352 Lafayette Street, Salem, 01970",
            businessType = "public",
            website = "https://www.salemstate.edu/",
            tags = """["osm_amenity=university"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_state_university_okeefe_campus", name = "Salem State University O'Keefe Campus",
            lat = 42.5052712, lng = -70.8945299,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_amenity=university"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "national_park_service_visitor_center", name = "National Park Service Visitor Center",
            lat = 42.5226012, lng = -70.8919858,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_tourism=information"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_willows_park_sign", name = "Salem Willows Park sign",
            lat = 42.5346015, lng = -70.8701225,
            address = "Salem, MA",
            businessType = "public",
            tags = """["osm_tourism=information"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Medical (15)
    // ===================================================================

    val medical = listOf(
        OutputBusiness(
            id = "massgeneral_for_children_at_north_shore_medical_center", name = "MassGeneral for Children at North Shore Medical Center",
            lat = 42.5118445, lng = -70.9064467,
            address = "57 Highland Avenue, Salem, MA, 01970",
            businessType = "medical",
            hours = "24/7",
            phone = "978 745 2100",
            website = "https://nsmc.partners.org/locations/nsmc_north_shore_childrens",
            tags = """["osm_amenity=hospital"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_shore_medical_center_salem_hospital", name = "North Shore Medical Center - Salem Hospital",
            lat = 42.5111964, lng = -70.9051843,
            address = "81 Highland Avenue, Salem, 01970",
            businessType = "medical",
            hours = "24/7",
            phone = "978 741 1200",
            website = "https://nsmc.partners.org/locations/salem_hospital",
            tags = """["osm_amenity=hospital"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "aleris_salem_dental_center", name = "Aleris Salem Dental Center",
            lat = 42.5185262, lng = -70.8934163,
            address = "Salem, MA",
            businessType = "medical",
            tags = """["osm_amenity=dentist"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "burba_dental_partners", name = "Burba Dental Partners",
            lat = 42.508734, lng = -70.9112487,
            address = "129 Highland Avenue, Salem, MA, 01970",
            businessType = "medical",
            hours = "Mo,Fr 08:00-16:00; Tu-Th 08:00-17:00",
            phone = "978-744-7575",
            website = "https://www.burbadental.com/",
            tags = """["osm_amenity=dentist"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dr_stan_garber_dmd", name = "Dr. Stan Garber DMD",
            lat = 42.5105593, lng = -70.8915219,
            address = "Salem, MA",
            businessType = "medical",
            tags = """["osm_amenity=dentist"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_street_dental_medicine", name = "Essex Street Dental Medicine",
            lat = 42.5186164, lng = -70.9048665,
            address = "398 Essex Street, Salem, MA, 01970",
            businessType = "medical",
            hours = "Mo, We 08:00-17:00; Tu 12:00-18:00; Th 08:00-16:00",
            phone = "978-744-7904",
            website = "https://www.yoursalemdentist.com",
            tags = """["osm_amenity=dentist"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mass_bay_dental", name = "Mass Bay Dental",
            lat = 42.5215601, lng = -70.8952786,
            address = "Salem, MA",
            businessType = "medical",
            tags = """["osm_amenity=dentist"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_family_health_center", name = "Salem Family Health Center",
            lat = 42.5176636, lng = -70.8891332,
            address = "Salem, MA",
            businessType = "medical",
            tags = """["osm_amenity=doctors"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cvs_pharmacy", name = "CVS Pharmacy",
            lat = 42.5166978, lng = -70.9062175,
            address = "426b Essex Street, Salem, MA, 01970",
            businessType = "medical",
            hours = "Mo-Su 08:00-22:00",
            website = "https://www.cvs.com/store-locator/salem-ma-pharmacies/426b-essex-st-salem-ma-01970/storeid=457",
            tags = """["osm_amenity=pharmacy"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cvs_pharmacy_1", name = "CVS Pharmacy",
            lat = 42.5217092, lng = -70.8943849,
            address = "200 Essex Street, Salem, 01970",
            businessType = "medical",
            website = "https://www.cvs.com/store-locator/salem-ma-pharmacies/200-essex-street-salem-ma-01970/storeid=261",
            tags = """["osm_amenity=pharmacy"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cvs_pharmacy_2", name = "CVS Pharmacy",
            lat = 42.5233633, lng = -70.9201066,
            address = "174 Main Street, Peabody, MA, 01960",
            businessType = "medical",
            hours = "Mo-Su 07:00-23:00",
            website = "https://www.cvs.com/store-locator/peabody-ma-pharmacies/174-main-st-peabody-ma-01960/storeid=4073",
            tags = """["osm_amenity=pharmacy"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cvs_pharmacy_3", name = "CVS Pharmacy",
            lat = 42.501036, lng = -70.8965935,
            address = "300 Canal Street, Salem, MA, 01970",
            businessType = "medical",
            website = "https://www.cvs.com/store-locator/salem-ma-pharmacies/300-canal-street-salem-ma-01970/storeid=7109",
            tags = """["osm_amenity=pharmacy"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "walgreens", name = "Walgreens",
            lat = 42.5194317, lng = -70.8944844,
            address = "Salem, MA",
            businessType = "medical",
            tags = """["osm_amenity=pharmacy"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "walgreens_1", name = "Walgreens",
            lat = 42.5187179, lng = -70.9093427,
            address = "59 Boston Street, Salem, MA, 01970",
            businessType = "medical",
            hours = "Mo-Su 07:00-23:00",
            tags = """["osm_amenity=pharmacy"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "walgreens_2", name = "Walgreens",
            lat = 42.5250226, lng = -70.9261283,
            address = "35 Main Street, Peabody, MA, 01960",
            businessType = "medical",
            hours = "Mo-Su 08:00-23:00",
            tags = """["osm_amenity=pharmacy"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Services (104)
    // ===================================================================

    val services = listOf(
        OutputBusiness(
            id = "century_bank", name = "Century Bank",
            lat = 42.5262023, lng = -70.9267655,
            address = "12 Peabody Square, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-We 08:30-16:00; Th-Fr 08:30-17:00; Sa 09:00-13:00",
            phone = "978-977-4900",
            website = "https://www.centurybank.com/location/peabody",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "eastern_bank", name = "Eastern Bank",
            lat = 42.5206467, lng = -70.8930955,
            address = "37 Central Street, Salem, MA, 01970",
            businessType = "services",
            hours = "Mo-We 09:00-16:00; Th-Fr 09:00-17:00; Sa 09:00-12:00",
            phone = "978-740-6900",
            website = "https://www.easternbank.com/locations/eastern-bank-salem-ma-central-street",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "eastern_bank_1", name = "Eastern Bank",
            lat = 42.5200939, lng = -70.8897276,
            address = "19 Congress Street, Salem, MA, 01970",
            businessType = "services",
            hours = "Mo-We 09:00-16:00; Th-Fr 09:00-17:00; Sa 09:00-12:00",
            phone = "978-740-6382",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "eastern_bank_2", name = "Eastern Bank",
            lat = 42.5019045, lng = -70.9235179,
            address = "6 Traders Way, Salem, MA, 01970",
            businessType = "services",
            hours = "Mo-We 09:00-17:00; Th-Fr 09:00-18:00; Sa 09:00-15:00; Su 11:00-15:00",
            phone = "800-327-8376",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mandt_bank", name = "M&T Bank",
            lat = 42.5030785, lng = -70.9230232,
            address = "Salem, MA",
            businessType = "services",
            website = "https://locations.mtb.com/ma/salem/bank-branches-and-atms-salem-ma-8396.html",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "marblehead_bank", name = "Marblehead Bank",
            lat = 42.5101583, lng = -70.8954048,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "metro_credit_union", name = "Metro Credit Union",
            lat = 42.5250555, lng = -70.925007,
            address = "68 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Fr 08:00-16:30; Sa 09:00-12:30",
            website = "https://www.metrocu.org/about/locations/peabody",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rockland_trust", name = "Rockland Trust",
            lat = 42.5194859, lng = -70.8949235,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_five_bank", name = "Salem Five Bank",
            lat = 42.5216001, lng = -70.8947786,
            address = "210 Essex Street",
            businessType = "services",
            website = "https://www.salemfive.com/",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "st_jeans_credit_union", name = "St. Jean's Credit Union",
            lat = 42.5053736, lng = -70.8910881,
            address = "336 Lafayette Street",
            businessType = "services",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "td_bank", name = "TD Bank",
            lat = 42.5256661, lng = -70.9265048,
            address = "10 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Fr 09:30-16:00; Sa 09:30-13:00",
            phone = "978-538-3309",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "trunorth_bank", name = "TruNorth Bank",
            lat = 42.5193818, lng = -70.893041,
            address = "Salem, MA",
            businessType = "services",
            website = "https://trunorthbank.com/",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "trunorth_bank_1", name = "TruNorth Bank",
            lat = 42.5254001, lng = -70.925843,
            address = "32 Main Street, Peabody, MA, 01960",
            businessType = "services",
            website = "https://trunorthbank.com/",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "trunorth_bank_2", name = "TruNorth Bank",
            lat = 42.5003118, lng = -70.926221,
            address = "319 Highland Avenue",
            businessType = "services",
            website = "https://trunorthbank.com/",
            tags = """["osm_amenity=bank"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_business_center", name = "Salem Business Center",
            lat = 42.5177808, lng = -70.8935526,
            address = "3 Harbor Street",
            businessType = "services",
            tags = """["osm_amenity=money_transfer"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_five_financial", name = "Salem Five Financial",
            lat = 42.52231, lng = -70.8949244,
            address = "1 Salem Green",
            businessType = "services",
            tags = """["osm_office=financial"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "exxon", name = "Exxon",
            lat = 42.5298399, lng = -70.8898248,
            address = "94 Bridge Street, Salem, MA, 01970",
            businessType = "services",
            hours = "Sa-Su 07:00-23:00; Mo-Fr 06:00-23:00",
            tags = """["osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "irving_oil", name = "Irving Oil",
            lat = 42.5004636, lng = -70.9264723,
            address = "323 Highland Avenue",
            businessType = "services",
            tags = """["osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "prime_gas_station", name = "Prime Gas Station",
            lat = 42.515333, lng = -70.8923463,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "university_fuel", name = "University Fuel",
            lat = 42.5017319, lng = -70.8961712,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=fuel"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "merchant_consulting_group", name = "Merchant Consulting Group",
            lat = 42.519485, lng = -70.8949602,
            address = "201 Washington Street, 01970",
            businessType = "services",
            tags = """["osm_office=yes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "administration_lot", name = "Administration Lot",
            lat = 42.5038487, lng = -70.8906021,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking", "osm_office=yes"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "broadway_lot", name = "Broadway Lot",
            lat = 42.5040564, lng = -70.8953688,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "canal_street_lot", name = "Canal Street Lot",
            lat = 42.5021675, lng = -70.8954379,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ellison_campus_center_lot", name = "Ellison Campus Center Lot",
            lat = 42.5030204, lng = -70.8917934,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "library_lot", name = "Library Lot",
            lat = 42.5018389, lng = -70.889758,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_campus_faculty_lot", name = "North Campus Faculty Lot",
            lat = 42.5002181, lng = -70.8913526,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_campus_parking_garage", name = "North Campus Parking Garage",
            lat = 42.5008142, lng = -70.890823,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "okeefe_back_lot", name = "O'Keefe Back Lot",
            lat = 42.5044914, lng = -70.8958295,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "okeefe_lot", name = "O'Keefe Lot",
            lat = 42.5059633, lng = -70.8947733,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "public_parking", name = "Public Parking",
            lat = 42.5188095, lng = -70.8959111,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "stanley_lot", name = "Stanley Lot",
            lat = 42.5029745, lng = -70.8954428,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=parking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_post_office", name = "Salem Post Office",
            lat = 42.5196108, lng = -70.8965902,
            address = "2;4 Margin Street, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_amenity=post_office"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_ups_store", name = "The UPS Store",
            lat = 42.5191141, lng = -70.894932,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_amenity=post_office", "osm_shop=copyshop"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "10_perfect_nails", name = "10 Perfect Nails",
            lat = 42.5194697, lng = -70.8947418,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "4_seasons_cleaners", name = "4 Seasons Cleaners",
            lat = 42.5349617, lng = -70.9157831,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=dry_cleaning"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "adams_auto_repair", name = "Adam's Auto Repair",
            lat = 42.5076753, lng = -70.8956024,
            address = "97 Ocean Avenue",
            businessType = "services",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "advance_auto_parts", name = "Advance Auto Parts",
            lat = 42.5164694, lng = -70.9062385,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=car_parts"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "advantage_self_storage", name = "Advantage Self Storage",
            lat = 42.515564, lng = -70.898237,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=storage_rental"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "alex_cuts_barber_shop", name = "Alex Cuts Barber Shop",
            lat = 42.529027, lng = -70.9038554,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "andrew_michaels_salon_and_spa", name = "Andrew Michaels Salon & Spa",
            lat = 42.5081104, lng = -70.8916634,
            address = "47 Ocean Avenue",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "autozone", name = "AutoZone",
            lat = 42.5014616, lng = -70.8963858,
            address = "292 Canal Street, Salem, MA, 01970",
            businessType = "services",
            hours = "Mo-Sa 07:30-21:00; Su 07:30-20:00",
            website = "https://www.autozone.com/locations/ma/salem/292-canal-st.html",
            tags = """["osm_shop=car_parts"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bada_bing_barbershop_and_shave_parlor", name = "Bada-Bing Barbershop & Shave Parlor",
            lat = 42.5217996, lng = -70.8959027,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "banville_optical", name = "Banville Optical",
            lat = 42.5186788, lng = -70.8934406,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=optician"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "beijing_herbal_foot_spa", name = "Beijing Herbal Foot Spa",
            lat = 42.5193688, lng = -70.89496,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=massage"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "berube_and_sons_funeral_home", name = "Berube & Sons Funeral Home",
            lat = 42.5146505, lng = -70.8923429,
            address = "191 Lafayette Street, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_shop=funeral_directors"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "canal_auto_shop", name = "Canal Auto Shop",
            lat = 42.5014031, lng = -70.8970608,
            address = "296 Canal Street, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "canal_street_cycles", name = "Canal Street Cycles",
            lat = 42.5016463, lng = -70.8969444,
            address = "296 Canal Street, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_shop=motorcycle_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "china_massage_and_reflexology_spa", name = "China Massage & Reflexology Spa",
            lat = 42.5255052, lng = -70.9260846,
            address = "30 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Su 10:00-21:00",
            phone = "781 888 1068",
            tags = """["osm_shop=massage"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "corporate_nails_and_spa", name = "Corporate Nails and Spa",
            lat = 42.5355236, lng = -70.9170486,
            address = "44 Margin Street, Peabody, MA, 01960",
            businessType = "services",
            phone = "978-532-5959",
            website = "http://www.corporatenailsandspa.com/",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "creedons_transmissions", name = "Creedon's Transmissions",
            lat = 42.512853, lng = -70.8962089,
            address = "75 Canal Street, Salem, MA, 01970",
            businessType = "services",
            hours = "Mo-Fr 08:00-17:00",
            phone = "978-744-0647",
            website = "https://www.creedonstransmissions.com/",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dandt_nails", name = "D&T Nails",
            lat = 42.5202052, lng = -70.9115982,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "derby_street_laundry", name = "Derby Street Laundry",
            lat = 42.5233543, lng = -70.8828955,
            address = "82 Derby Street",
            businessType = "services",
            tags = """["osm_shop=laundry"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "dotties", name = "Dottie's",
            lat = 42.5227329, lng = -70.9199905,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_county_craftsmen_inc", name = "Essex County Craftsmen Inc",
            lat = 42.5181783, lng = -70.8901374,
            address = "60 Ward Street, Salem, MA, 01970",
            businessType = "services",
            phone = "+19787458028",
            website = "https://www.ecchvac.net/",
            tags = """["osm_craft=hvac"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_vacuum", name = "Essex Vacuum",
            lat = 42.5268619, lng = -70.9011452,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=vacuum_cleaner"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "exclusive_hair_design", name = "Exclusive Hair Design",
            lat = 42.5251271, lng = -70.9252036,
            address = "60 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Tu-We 10:00-18:00; Th-Fr 10:00-20:00; Sa 09:00-18:00",
            phone = "978-531-9888",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fix_master", name = "Fix Master",
            lat = 42.5304197, lng = -70.9072458,
            address = "190 North Street, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_shop=repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fresh_express_laundry_center", name = "Fresh Express Laundry Center",
            lat = 42.5160496, lng = -70.8890896,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=laundry"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "gomes_travel", name = "Gomes Travel",
            lat = 42.5250829, lng = -70.9250832,
            address = "64 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Fr 09:00-17:00; Feb 1-Apr 23 Sa 09:00-13:00",
            phone = "978-532-5435",
            tags = """["osm_shop=travel_agency"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "handh_propeller_shop", name = "H&H Propeller Shop",
            lat = 42.5261051, lng = -70.884516,
            address = "0 Essex Street, Salem, MA, 01970",
            businessType = "services",
            phone = "800-325-0117",
            website = "https://www.hhprop.com/",
            tags = """["osm_craft=boatbuilder"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hemsey_judge_p_c", name = "Hemsey Judge, P. C.",
            lat = 42.5229136, lng = -70.8974051,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_office=lawyer"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "impressions_hair_salon", name = "Impressions Hair Salon",
            lat = 42.5258234, lng = -70.9267701,
            address = "2 Main Street, Peabody, MA, 01960",
            businessType = "services",
            phone = "978-532-3811",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "inkperial", name = "InkPerial",
            lat = 42.5177441, lng = -70.8897043,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=tattoo"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jm_constructions", name = "JM Constructions",
            lat = 42.5195824, lng = -70.9238649,
            address = "18 Beckett Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Su 06:00-18:00",
            phone = "978-979-4473",
            website = "https://www.jmplumbingcompany.com",
            tags = """["osm_craft=plumber"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jia_the_nail_place", name = "Jia The Nail Place",
            lat = 42.5153179, lng = -70.8930253,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "jims_collision", name = "Jim's Collision",
            lat = 42.5074623, lng = -70.8951869,
            address = "22 Hersey Street",
            businessType = "services",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "kissable_paws", name = "Kissable Paws",
            lat = 42.5025486, lng = -70.8943817,
            address = "122 Broadway, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_shop=pet_grooming"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "laura_lanes_skin_care", name = "Laura Lanes Skin Care",
            lat = 42.5215532, lng = -70.8961353,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "law_offices_of_paul_r_moraski", name = "Law Offices of Paul R. Moraski",
            lat = 42.522538, lng = -70.8943261,
            address = "15 Church Street, Salem, 01970",
            businessType = "services",
            phone = "978 397 0011",
            tags = """["osm_office=lawyer"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "levesque_funeral_home", name = "Levesque Funeral Home",
            lat = 42.5160149, lng = -70.8925298,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=funeral_directors"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "louis_restoration_and_repair", name = "Louis Restoration & Repair",
            lat = 42.5148085, lng = -70.888663,
            address = "62 Leavitt Street, Salem, MA, 01970",
            businessType = "services",
            phone = "+19787452111",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "louise_michaud_photographer", name = "Louise Michaud Photographer",
            lat = 42.5208387, lng = -70.8891692,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_craft=photographer"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mal_n_barbershop", name = "Malón Barbershop",
            lat = 42.5177604, lng = -70.8896227,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "merk_ink_studio", name = "Merk Ink Studio",
            lat = 42.5215354, lng = -70.8972003,
            address = "266 Essex Street",
            businessType = "services",
            tags = """["osm_shop=tattoo"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mindys_nails", name = "Mindy's Nails",
            lat = 42.5096286, lng = -70.8949263,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "minuteman_press", name = "Minuteman Press",
            lat = 42.5250305, lng = -70.9249428,
            address = "70 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Fr 08:30-17:00",
            phone = "978-531-0081",
            website = "https://www.peabody.minutemanpress.com/",
            tags = """["osm_shop=copyshop"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "moon_baby", name = "Moon Baby",
            lat = 42.5215334, lng = -70.8983917,
            address = "302B Essex Street, 01970",
            businessType = "services",
            hours = "Tu-Fr 10:00-20:00; Sa 10:00-18:00",
            phone = "978-594-4858",
            website = "https://www.moonbabysalem.com/",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "nails_by_andria", name = "Nails by Andria",
            lat = 42.5246621, lng = -70.9240226,
            address = "86 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Fr 09:30-20:00, Sa 09:30-18:00",
            website = "http://www.nailsbyandria.com/",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "north_shore_auto_clinic", name = "North Shore Auto Clinic",
            lat = 42.5235973, lng = -70.8856432,
            address = "43 Essex Street",
            businessType = "services",
            phone = "978-745-0106",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "paxton_parts_and_service", name = "Paxton Parts & Service",
            lat = 42.5216415, lng = -70.8955785,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_shoe_repair_and_tailoring", name = "Peabody Shoe Repair & Tailoring",
            lat = 42.5252366, lng = -70.9254252,
            address = "46 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Fr 09:00-18:00; Sa 09:00-15:00",
            phone = "978-826-5971",
            website = "https://www.facebook.com/Peabody-Shoe-Repair-Tailoring-611781905531447/",
            tags = """["osm_shop=tailor"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pequot_filling_station_inc", name = "Pequot Filling Station Inc",
            lat = 42.5164726, lng = -70.8897257,
            address = "84 Congress Street",
            businessType = "services",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "porcello_law_offices", name = "Porcello Law Offices",
            lat = 42.5093211, lng = -70.8912241,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_office=lawyer"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "pringles_barber_shop", name = "Pringle's Barber Shop",
            lat = 42.5030518, lng = -70.8949785,
            address = "108 Broadway, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "quality_cuts", name = "Quality Cuts",
            lat = 42.5032392, lng = -70.9227924,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ronan_segal_and_harrington", name = "Ronan, Segal & Harrington",
            lat = 42.5171705, lng = -70.8891781,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_office=lawyer"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "royal_blendz", name = "Royal Blendz",
            lat = 42.5169307, lng = -70.8934805,
            address = "282 Washington Street",
            businessType = "services",
            phone = "+19785944923",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "royal_cleaners", name = "Royal Cleaners",
            lat = 42.5095319, lng = -70.894915,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=dry_cleaning"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "royale_salon", name = "Royale Salon",
            lat = 42.5250023, lng = -70.9248825,
            address = "72 Main Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Tu-Th 10:00-19:00; Fr-Sa 09:00-19:00",
            phone = "978-531-0414",
            website = "https://www.facebook.com/RoyaleSalao/",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_laundry_co", name = "Salem Laundry Co.",
            lat = 42.510137, lng = -70.8957294,
            address = "Salem, MA",
            businessType = "services",
            tags = """["osm_shop=laundry"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_nail_bar", name = "Salem Nail Bar",
            lat = 42.5031987, lng = -70.9228501,
            address = "3;5;7 Traders Way",
            businessType = "services",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_overhead_door", name = "Salem Overhead Door",
            lat = 42.5217962, lng = -70.9039606,
            address = "337 Bridge Street, Salem, MA, 01970",
            businessType = "services",
            tags = """["osm_shop=doors"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "santisis_garage", name = "Santisi's Garage",
            lat = 42.5149162, lng = -70.8890051,
            address = "62 Leavitt Street, Salem, MA, 01970",
            businessType = "services",
            hours = "Tu-Fr 09:00-17:00",
            phone = "978 744-9664",
            website = "https://santisisgarage.com/",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sarai_by_day", name = "Sarai By Day",
            lat = 42.528946, lng = -70.9043849,
            address = "149 North Street, Salem, MA, 01970",
            businessType = "services",
            hours = "We-Th 09:00-18:00; Fr-Su 09:00-17:00",
            phone = "978-631-6750",
            website = "http://www.saraibyday.com/",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "shepard_law_offices", name = "Shepard Law Offices",
            lat = 42.5252405, lng = -70.8846885,
            address = "8 Essex Street",
            businessType = "services",
            tags = """["osm_office=lawyer"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "signal_motorsports", name = "Signal Motorsports",
            lat = 42.5036801, lng = -70.8958364,
            address = "259 Canal Street",
            businessType = "services",
            tags = """["osm_shop=car_repair"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "spiro_s_auto", name = "Spiro’s Auto",
            lat = 42.5399326, lng = -70.9191573,
            address = "90 Margin Street, Peabody, MA, 01960",
            businessType = "services",
            hours = "Mo-Th 10:00-18:00; Fr,Sa 10:00-17:00",
            phone = "978-817-2949",
            website = "https://spirosauto.com/",
            tags = """["osm_shop=car"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_threading_soma", name = "The Threading Soma",
            lat = 42.5251553, lng = -70.9252553,
            address = "58 Main Street, Peabody, MA, 01960",
            businessType = "services",
            phone = "617-820-4488",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "today_nails_and_spa_salem", name = "Today Nails and Spa Salem",
            lat = 42.5196268, lng = -70.888369,
            address = "80 Wharf Street, Salem, MA, 01970",
            businessType = "services",
            hours = "Mo-Sa 09:30-19:00; Su 10:00-17:00",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "vanished_house_of_laser", name = "Vanished House of Laser",
            lat = 42.517666, lng = -70.8942141,
            address = "262 Washington Street",
            businessType = "services",
            tags = """["osm_shop=beauty"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_auto_sales", name = "Witch City Auto Sales",
            lat = 42.5073939, lng = -70.8957429,
            address = "151 Canal Street",
            businessType = "services",
            tags = """["osm_shop=car"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "witch_city_ink", name = "Witch City Ink",
            lat = 42.5217608, lng = -70.8936529,
            address = "186 Essex Street",
            businessType = "services",
            tags = """["osm_shop=tattoo"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "xiomaras_salon", name = "Xiomara's Salon",
            lat = 42.5176947, lng = -70.8942743,
            address = "260 Washington Street",
            businessType = "services",
            tags = """["osm_shop=hairdresser"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

    // ===================================================================
    // Other (63)
    // ===================================================================

    val other = listOf(
        OutputBusiness(
            id = "salem_arts", name = "Salem Arts",
            lat = 42.5216133, lng = -70.8863158,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_office=association"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ahmed_insurance_agency", name = "Ahmed Insurance Agency",
            lat = 42.5265841, lng = -70.9006542,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_office=insurance"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "all_creatures_veterinary_hospital", name = "All Creatures Veterinary Hospital",
            lat = 42.5242245, lng = -70.9018985,
            address = "20 Commercial Street, Salem, MA, 01970",
            businessType = "other",
            hours = "Mo 08:00-18:00; Tu-Th 08:00-19:00; Fr 08:00-18:00; Sa 09:00-13:00",
            phone = "978-740-0290",
            website = "https://www.creaturehealth.com/",
            tags = """["osm_amenity=veterinary"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "apex_production", name = "Apex Production",
            lat = 42.5294694, lng = -70.9061068,
            address = "173 North Street, Salem, MA, 01970",
            businessType = "other",
            phone = "978 740 0195",
            website = "https://www.apexproduction.com/",
            tags = """["osm_office=company"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bertram_field", name = "Bertram Field",
            lat = 42.5127229, lng = -70.904793,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_leisure=stadium"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bluebikes", name = "Bluebikes",
            lat = 42.5235097, lng = -70.8952337,
            address = "Salem, MA, 01970",
            businessType = "other",
            website = "https://www.bluebikes.com",
            description = "Bluebikes Salem is an extension of Metro Boston's public bike share system. Download the Bluebikes app to easily purchase a monthly membership for \$29 or single pass for a 30-minute trips for \$4.00.",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bluebikes_1", name = "Bluebikes",
            lat = 42.5113064, lng = -70.8917706,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_amenity=bicycle_rental"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "bluebikes_2", name = "Bluebikes",
            lat = 42.5179724, lng = -70.8956645,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_amenity=bicycle_rental"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "cabot_wealth_management", name = "Cabot Wealth Management",
            lat = 42.5215679, lng = -70.8950616,
            address = "216 Essex Street",
            businessType = "other",
            tags = """["osm_office=financial_advisor"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "carly_cat_little_free_library", name = "Carly Cat Little Free Library",
            lat = 42.5212583, lng = -70.9036623,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_amenity=public_bookcase"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "catholic_charities", name = "Catholic Charities",
            lat = 42.5170299, lng = -70.893569,
            address = "280 Washington Street",
            businessType = "other",
            tags = """["osm_office=charity"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "church_street_east_lot", name = "Church Street East Lot",
            lat = 42.523082, lng = -70.8935537,
            address = "72 Church St, Salem, MA, 01970",
            businessType = "other",
            description = "Pay with the Passport Parking app upon arrival.",
            tags = """["ds_parking", "osm_amenity=parking"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "church_street_west_lot", name = "Church Street West Lot",
            lat = 42.5229601, lng = -70.8943609,
            address = "15 Federal St, Salem, MA, 01970",
            businessType = "other",
            description = "All-day parking. Pay with the Passport Parking app upon arrival. Electric Vehicle charging available.",
            tags = """["ds_parking", "osm_amenity=parking"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "creative_collective", name = "Creative Collective",
            lat = 42.5212384, lng = -70.89700739999999,
            address = "265 Essex St, Salem, MA, 01970",
            businessType = "other",
            website = "https://creativecollectivema.com/",
            description = "Real Business Support. Real Community Building. Creative Collective delivers comprehensive business services - marketing promotion, technical assistance, business consulting, and advocacy at all levels - for Essex County entrepreneurs, creators, and innovators. We support the whole person, not just the business, through a collaborative community where members learn from each other, not just from us, because we know thriving businesses are built by thriving humans supporting each other.",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "crombie_street_lot", name = "Crombie Street Lot",
            lat = 42.5210724, lng = -70.8977686,
            address = "283-287 Essex St, Salem, MA, 01970",
            businessType = "other",
            description = "Pay with the Passport Parking app upon arrival. Electric Vehicle charging available.",
            tags = """["ds_parking", "osm_amenity=place_of_worship", "osm_amenity=parking"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "d_johnny_multi_services", name = "D’Johnny Multi Services",
            lat = 42.5177537, lng = -70.8906429,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_office=consulting"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "essex_heritage", name = "Essex Heritage",
            lat = 42.5227268, lng = -70.8919824,
            address = "2 New Liberty St, Salem, MA, 01970",
            businessType = "other",
            website = "https://essexheritage.org/",
            description = "Essex Heritage Essex National Heritage Commission (Essex Heritage) is a non-profit organization that has been federally authorized to manage and oversee the unique heritage resources of the Essex National Heritage Area. Mission: To preserve and enhance the historic, cultural, and natural resources of the Essex National Heritage Area located in Essex County, Massachusetts. Vision: The historic, cultural and natural resources (and their associated history, organizations, and people) are recognized as critically important to maintaining the quality of life, community vitality, and economic sustainability of Essex County, Massachusetts. Essex National Heritage Area The Essex National Heritage Area was created as an act of Congress in 1996 and encompasses the 34 cities and towns of Essex County, Massachusetts. Within this NHA there are two National Parks (Salem Maritime National Historic Site and Saugus Ironworks National Historic Site) and the US Parker River National Wildlife Refuge. There are also several state parks, thousands of acres of conservation land, and numerous museums, historic houses and heritage sites that are open to the public and owned/managed by non-profit organizations and municipal governments. Land Acknowledgement: The Essex National Heritage Area encompasses land on which Native communities have thrived for millennia. The Pawtucket band of the Massachuset Tribe inhabited the land that is now known as Essex County. As advocates for the appropriate interpretation and stewardship of our many natural, historic, and cultural resources, Essex Heritage has a responsibility to acknowledge all stories, past and present.",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "feline_hospital", name = "Feline Hospital",
            lat = 42.5267274, lng = -70.8866333,
            address = "81 Webb Street",
            businessType = "other",
            tags = """["osm_amenity=veterinary"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "fort_ave_free_parking", name = "Fort Ave - Free Parking",
            lat = 42.53050683172669, lng = -70.87355358399199,
            address = "81 Fort Ave, Salem, MA, 01970",
            businessType = "other",
            description = "Fort Ave free parking near Salem State’s Cat Cove Marine Laboratory.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "handr_block", name = "H&R Block",
            lat = 42.5192484, lng = -70.894953,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_office=tax_advisor"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "hive_workspace", name = "Hive Workspace",
            lat = 42.5182981, lng = -70.8950184,
            address = "227 Washington Street, Salem, MA, 01970",
            businessType = "other",
            tags = """["osm_office=coworking"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "john_f_dolan_cpa", name = "John F. Dolan, CPA",
            lat = 42.5251739, lng = -70.9253069,
            address = "54 Main Street, Peabody, MA, 01960",
            businessType = "other",
            phone = "978-531-1981",
            tags = """["osm_office=accountant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "klopp_alley_lot", name = "Klopp Alley Lot",
            lat = 42.5200153, lng = -70.8940078,
            address = "44 Lafayette St, Salem, MA, 01970",
            businessType = "other",
            description = "4 hour limit.",
            tags = """["ds_parking", "osm_amenity=parking"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "knights_of_columbus", name = "Knights of Columbus",
            lat = 42.5247671, lng = -70.9237912,
            address = "96 Main Street, Peabody, MA, 01960",
            businessType = "other",
            website = "https://peabodykofc.org",
            tags = """["osm_amenity=events_venue"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "laer_realty_partners", name = "LAER Realty Partners",
            lat = 42.5245422, lng = -70.9245476,
            address = "65 Main Street, Peabody, MA, 01960",
            businessType = "other",
            phone = "978-777-5509",
            website = "https://www.laerrealty.com/offices/966-peabody-ma",
            tags = """["osm_office=estate_agent"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "lappin_park_little_free_library", name = "Lappin Park Little Free Library",
            lat = 42.521321, lng = -70.8959624,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_amenity=public_bookcase", "osm_leisure=park"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "leos_metro_bowl", name = "Leo's Metro Bowl",
            lat = 42.5240328, lng = -70.9294801,
            address = "Salem, MA",
            businessType = "other",
            phone = "978-531-0500",
            website = "https://leosmetrobowl.com/",
            tags = """["osm_leisure=bowling_alley"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "liberty_tax", name = "Liberty Tax",
            lat = 42.5031331, lng = -70.9229439,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_office=tax_advisor"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "little_free_library", name = "Little Free Library",
            lat = 42.5128816, lng = -70.8920424,
            address = "215 Lafayette Street, Salem, MA, 01970",
            businessType = "other",
            tags = """["osm_amenity=public_bookcase"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mbta_station_garage", name = "MBTA Station Garage",
            lat = 42.52479750000001, lng = -70.896068,
            address = "252 Bridge St, Salem, MA, 01970",
            businessType = "other",
            description = "All-day parking. The MBTA Commuter Rail Station is accessible and conveniently located at 252 Bridge Street. Bike cage available.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mbta_train_and_bus_service", name = "MBTA Train & Bus Service",
            lat = 42.51954, lng = -70.8967155,
            address = "Salem, MA",
            businessType = "other",
            phone = "800-392-6100",
            website = "https://www.mbta.com/schedules/CR-Newburyport/timetable",
            description = "MBTA Commuter Rail connects Salem to Boston's North Station via both the Newburyport and Rockport Lines. Walk from Salem Depot to downtown shopping, dining, attractions, and waterfront. Bus #450/450W from Haymarket/Wonderland, Bus #455 from Logan Airport. Bus route #459 (from Logan) has been eliminated, please see current bus routes to Salem below- #435 or #456 Salem to Lynn #450 Salem to Wonderland or Haymarket in Boston #451 Salem to North Beverly #455 Salem to Revere Beach",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "masshire_north_shore_career_center", name = "MassHire North Shore Career Center",
            lat = 42.5222592, lng = -70.8959743,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_office=employment_agency"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "merryfox_realty", name = "MerryFox Realty",
            lat = 42.5208364, lng = -70.889295,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_office=estate_agent"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "mobility2rent", name = "Mobility2Rent",
            lat = 42.5197473, lng = -70.8954626,
            address = "Salem, MA",
            businessType = "other",
            phone = "6173375133",
            website = "https://www.mobility2rent.com",
            description = "Navigating the world of mobility equipment rentals can often feel overwhelming. Whether you require temporary assistance due to an injury, are planning a vacation, or simply need additional support for daily activities, finding the right provider is crucial. Mobility2rent.com stands out as a trusted name in the field, offering a seamless experience for those in need of mobility equipment rental. Mobility2rent.com provides a comprehensive range of equipment to suit various mobility needs: Wheelchairs: Both manual and electric wheelchairs are available, catering to short-term and long-term needs. Scooters: Ideal for enhancing mobility during travel or day-to-day activities, scooters come in a variety of models. Walkers and Rollators: For those seeking additional support, walkers and rollators offer stability and ease of movement. Hospital Beds: Providing comfort and functionality, these beds are perfect for home care settings. Our warehouse is located to Salem and we provide free delivery.",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "museum_place_garage", name = "Museum Place Garage",
            lat = 42.5224815, lng = -70.8925131,
            address = "1 New Liberty St, Salem, MA, 01970",
            businessType = "other",
            description = "All day parking. Cash and credit are accepted in the payment kiosks. (Height clearance for the garage is 6 ft. 6 in.) Electric Vehicle charging available.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "nta_numbers_tax_accounting", name = "NTA - Numbers Tax Accounting",
            lat = 42.5257278, lng = -70.9266772,
            address = "2 Main Street, Peabody, MA, 01960",
            businessType = "other",
            phone = "978 587 2710",
            website = "https://ntaaccounting.com/",
            tags = """["osm_office=accountant"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "old_salem_jail_lot", name = "Old Salem Jail Lot",
            lat = 42.5251525, lng = -70.8928231,
            address = "161 Bridge St, Salem, MA, 01970",
            businessType = "other",
            description = "4 hour limit.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "p_j_woods_insurance_agency_inc", name = "P.J. Woods Insurance Agency Inc.",
            lat = 42.5252744, lng = -70.9255307,
            address = "40 Main Street, Peabody, MA, 01960",
            businessType = "other",
            hours = "Mo-Fr 09:00-17:00",
            phone = "978-531-2777",
            tags = """["osm_office=insurance"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "peabody_insurance_agency", name = "Peabody Insurance Agency",
            lat = 42.5258738, lng = -70.9267824,
            address = "2 Main Street, Peabody, MA, 01960",
            businessType = "other",
            hours = "Mo-Fr 09:00-17:00",
            phone = "978-531-9863",
            website = "https://www.peabodyinsuranceagency.com/",
            tags = """["osm_office=insurance"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "punto_urban_art_museum_2", name = "Punto Urban Art Museum",
            lat = 42.5182345, lng = -70.8933311,
            address = "96 Lafayette Street, Salem, MA, 01970",
            businessType = "other",
            tags = """["osm_amenity=arts_centre"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rv_parking", name = "RV Parking",
            lat = 42.526877, lng = -70.8691305,
            address = "50 Winter Island Rd, Salem, MA, 01970",
            businessType = "other",
            description = "From downtown Salem, driving northeast, Derby Street turns into Fort Avenue. RV camping is available seasonally (May 20th – November 1st) at Winter Island Maritime Park.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "regional_parking_mbta_garage", name = "Regional Parking: MBTA Garage",
            lat = 42.547591, lng = -70.8853058,
            address = "10 Park St, Beverly, MA, 01915",
            businessType = "other",
            description = "Trains run hourly between Beverly and Salem.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rockett_arena", name = "Rockett Arena",
            lat = 42.5053591, lng = -70.8952864,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_leisure=ice_rink"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "ropes_garden_little_free_library", name = "Ropes Garden Little Free Library",
            lat = 42.5213452, lng = -70.8997159,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_amenity=public_bookcase", "osm_leisure=garden"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "rose_insurance_agency_inc", name = "Rose Insurance Agency Inc.",
            lat = 42.5024617, lng = -70.8942909,
            address = "66 Loring Avenue, Salem, MA, 01970",
            businessType = "other",
            tags = """["osm_office=insurance"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_chamber_of_commerce", name = "Salem Chamber of Commerce",
            lat = 42.5213815, lng = -70.8969667,
            address = "265 Essex Street, Salem, MA, 01970",
            businessType = "other",
            phone = "978-744-0004",
            website = "http://www.salem-chamber.org/",
            description = "The mission of the Salem Chamber of Commerce is to make Salem a better place to live, work, and do business. The Salem Chamber serves as the voice for member businesses, representing, advocating and working to enhance the business and civic environment. With 525 members, we are the largest business organization in Salem. The Chamber connects its members to people asking for special products or services, and it encourages consumers to frequent member businesses. Chamber membership gives customers the assurance that they are frequenting a reputable business. For visitors to Salem, the Salem Chamber office is located in the heart of the downtown, at 265 Essex Street, and we encourage all to step in and learn about Salem’s (business) community.",
            tags = """["osm_office=association"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_green_lot", name = "Salem Green Lot",
            lat = 42.52252859999999, lng = -70.8948113,
            address = "35 Church St, Salem, MA, 01970",
            businessType = "other",
            description = "The Salem Green lot offers short term parking. Pay with the Passport Parking app upon arrival. Electric Vehicle charging available.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_main_streets", name = "Salem Main Streets",
            lat = 42.52126210000001, lng = -70.8969403,
            address = "265 Essex St, Salem, MA, 01970",
            businessType = "other",
            website = "https://salemmainstreets.org/",
            description = "Salem Main Streets' mission is the continued revitalization of downtown Salem as a vibrant, year-round, retail, dining and cultural destination through business retention, recruitment, and promotion of the downtown district. Downtown Salem is a Year-Round Destination From tasting ice cream to dancing in the streets, Salem Main Streets is involved with bringing events into the downtown year-round. Salem Main Streets Events Include: Salem’s So Sweet Salem Arts Festival Salem Farmers’ Market Salem Ice Scream Bowl Howl-o-ween Parade Salem Winter Market Holiday Tree Lighting Santa’s Arrival at the Hawthorne Holiday Window Contest New Year’s Eve Salem",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_maritime_national_historic_park_central_wharf", name = "Salem Maritime National Historic Park / Central Wharf",
            lat = 42.5207256, lng = -70.8877791,
            address = "193 Derby St, Salem, MA, 01970",
            businessType = "other",
            hours = "Mon 5:30 AM-12:30 PM; Tue 5:30 AM-12:30 PM; Wed 5:30 AM-12:30 PM; Thu 5:30 AM-12:30 PM; Fri 5:30 AM-12:30 PM; Sat 5:30 AM-12:30 PM; Sun 5:30 AM-12:30 PM",
            phone = "978-740-1650",
            website = "http://www.nps.gov/sama",
            tags = """["ds_restroom", "hh_venue"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_public_library", name = "Salem Public Library",
            lat = 42.5202683, lng = -70.9031916,
            address = "370 Essex Street, Salem, MA, 01970",
            businessType = "other",
            hours = "Mo-Th 09:00-21:00; Sa,Su 09:00-17:00; Su 13:00-17:00",
            phone = "978-744-0860",
            website = "http://www.salempl.org",
            description = "The Salem Public Library welcomes visitors of all ages to explore, learn, and be entertained. The historic building is open seven days a week. Call 978-744-0860 or visit salempl.org for more information.",
            tags = """["osm_amenity=library", "osm_office=Salem"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "salem_skipper", name = "Salem Skipper",
            lat = 42.51954, lng = -70.8967155,
            address = "Salem, MA",
            businessType = "other",
            phone = "(844) 983-1842",
            website = "https://www.salemma.gov/mobility-services/pages/salem-skipper",
            description = "A ridesharing service for residents, commuters, and visitors in Salem. Book a ride in the mobile app and be matched with other passengers heading in the same direction.",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "sewall_street_lot", name = "Sewall Street Lot",
            lat = 42.521882, lng = -70.897142,
            address = "8 Sewall St, Salem, MA, 01970",
            businessType = "other",
            description = "4 hour limit. Electric Vehicle charging available.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "south_harbor_garage", name = "South Harbor Garage",
            lat = 42.5202423, lng = -70.8904338,
            address = "10 Congress St, Salem, MA, 01970",
            businessType = "other",
            hours = "Mon 6:00 AM-12:00 PM; Tue 6:00 AM-12:00 PM; Wed 6:00 AM-12:00 PM; Thu 6:00 AM-12:00 PM; Fri 6:00 AM-12:00 PM; Sat 6:00 AM-12:00 PM; Sun 6:00 AM-12:00 PM",
            description = "All-day parking. Park on upper floors for stays longer than 4 hours. Metered parking on first floor, time limits may vary. If you are going to be exploring the waterfront, Pickering Wharf and Derby Street, you may want to park in the South Harbor Garage. Electric Vehicle charging available.",
            tags = """["ds_parking", "ds_restroom", "osm_amenity=parking"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_salem_ferry", name = "The Salem Ferry",
            lat = 42.5218575, lng = -70.8804597,
            address = "10 Blaney St, Salem, MA, 01970",
            businessType = "other",
            phone = "1-877-733-9425",
            website = "https://www.cityexperiences.com/boston/city-cruises/boston-harbor/salem-ferry/",
            description = "How Long is the Salem Ferry Ride? The Salem Ferry Ride from Boston's Long Wharf to Salem is between 50 to 60 minutes. You’ll be able to sightsee, museum hop, sampling the specialties of local chefs, and shopping in Salem’s many quaint boutiques and quirky shops upon arriving in Salem! Salem Ferry Drop Off Upon arriving from Boston, the Salem Ferry drops you off at the Salem Wharf Ferry Terminal on Blaney Street. Taking the ferry to Boston, passengers are dropped off at Boston's Long Wharf at 66 Long Wharf, Boston, MA 02110. Click here for schedules and more information. RESIDENT AND COMMUTER RATES Salem Resident Round Trip Rates: Child (3-11) - \$11, Child (Under 3) - Free (needs a ticket) North Shore Resident Round Trip: Child (3-11) - \$19, Child (Under 3) - Free (needs a ticket) Commuter Round Trip Rates (Mon - Fri rush hour trips only): Child (3-11) - \$8, Child (under 3) - Free (needs a ticket) *North Shore Resident rates are applicable to the following locations Beverly, Danvers, Lynn, Marblehead, Peabody, Swampscott",
            tags = """["ds_parking", "hh_venue", "osm_amenity=ferry_terminal"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_salem_pantry", name = "The Salem Pantry",
            lat = 42.5187351, lng = -70.88770939999999,
            address = "27 Congress St, Salem, MA, 01970",
            businessType = "other",
            phone = "978-552-3954",
            website = "https://thesalempantry.org/",
            description = "The Salem Pantry provides the North Shore with convenient access to fresh, free, healthy food. Our innovative public pop up pantries, home deliveries, and The Market offer an abundant selection of produce, proteins, spices, and dairy products. We partner with local farmers and health organizations to nourish our community from the inside out.",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_salem_willows_free_parking", name = "The Salem Willows - Free Parking",
            lat = 42.5309582, lng = -70.8728929,
            address = "92 Fort Ave, Salem, MA, 01970",
            businessType = "other",
            description = "Dirt parking lot free, unmetered, within walking distance to Salem Willows Recreation Area, restaurant row, and beach.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "the_salem_willows_rec_area", name = "The Salem Willows - Rec Area",
            lat = 42.5346066, lng = -70.8715142,
            address = "9 Restaurant Row, Salem, MA, 01970",
            businessType = "other",
            tags = """["ds_parking", "osm_leisure=park"]""",
            provenance = Provenance("api_sync", 0.9f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "todays_insurance", name = "Today's Insurance",
            lat = 42.5251074, lng = -70.9251387,
            address = "64 Main Street, Peabody, MA, 01960",
            businessType = "other",
            hours = "Mo-Fr 09:00-17:00",
            phone = "978-532-3555",
            website = "https://www.todaysins.com/",
            tags = """["osm_office=insurance"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "twohig_gymnasium", name = "Twohig Gymnasium",
            lat = 42.5047075, lng = -70.8954581,
            address = "Salem, MA",
            businessType = "other",
            tags = """["osm_leisure=sports_hall"]""",
            provenance = Provenance("overpass_import", 0.75f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "universal_steel_lot", name = "Universal Steel Lot",
            lat = 42.523071, lng = -70.9008704,
            address = "297 Bridge St, Salem, MA, 01970",
            businessType = "other",
            description = "All-day parking.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "voices_against_injustice", name = "Voices Against Injustice",
            lat = 42.51954, lng = -70.8967155,
            address = "Salem, MA",
            businessType = "other",
            description = "Voices Against Injustice is a Salem-based non-profit. We REMEMBER the lessons from the events of 1692 by maintaining the Salem Witch Trials Memorial, we HONOR current champions of social justice with the Salem Award for Human Rights and Social Justice, and we ACT by inspiring members of our worldwide community to raise their voices and confront social injustice with courage. To learn more, get involved and help us raise voices, visit us at https://voicesagainstinjustice.org/.",
            tags = """[]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "washington_street_lot", name = "Washington Street Lot",
            lat = 42.5193117, lng = -70.8949085,
            address = "201 Washington St, Salem, MA, 01970",
            businessType = "other",
            description = "4 hour limit.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        ),
        OutputBusiness(
            id = "wharf_street", name = "Wharf Street",
            lat = 42.519932148324266, lng = -70.88819735015389,
            address = "199 Derby St, Salem, MA, 01970",
            businessType = "other",
            description = "2-hour parking to visit businesses on Wharf Street.",
            tags = """["ds_parking"]""",
            provenance = Provenance("api_sync", 0.85f, "2026-04-05", now, now,
                now + 180L * 24 * 60 * 60 * 1000)
        )
    )

}
