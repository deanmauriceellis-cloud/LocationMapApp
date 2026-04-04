/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui.menu

import android.graphics.Color
import com.example.locationmapapp.ui.menu.PoiLayerId

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module PoiCategories.kt"

/**
 * Central config for all 17 POI categories.
 * Menu items, toggles, restore, Overpass queries, and marker colors are all driven from this list.
 */
data class PoiCategory(
    val id: String,
    val label: String,
    val prefKey: String,
    val tags: List<String>,
    val subtypes: List<PoiSubtype>?,
    val color: Int
)

data class PoiSubtype(val label: String, val tags: List<String>)

object PoiCategories {

    val ALL: List<PoiCategory> = listOf(

        // 1 — Food & Drink
        PoiCategory(
            id = PoiLayerId.FOOD_DRINK,
            label = "Food & Drink",
            prefKey = "poi_food_drink_on",
            tags = listOf("amenity=restaurant", "amenity=fast_food", "amenity=cafe",
                           "amenity=bar", "amenity=pub", "amenity=ice_cream",
                           "shop=bakery", "shop=alcohol", "shop=deli",
                           "shop=pastry", "shop=confectionery", "amenity=marketplace",
                           "craft=brewery", "craft=winery", "craft=distillery",
                           "shop=wine", "shop=butcher", "shop=seafood"),
            subtypes = listOf(
                PoiSubtype("Restaurants",  listOf("amenity=restaurant")),
                PoiSubtype("Fast Food",    listOf("amenity=fast_food")),
                PoiSubtype("Cafes",        listOf("amenity=cafe")),
                PoiSubtype("Bars",         listOf("amenity=bar")),
                PoiSubtype("Pubs",         listOf("amenity=pub")),
                PoiSubtype("Ice Cream",    listOf("amenity=ice_cream")),
                PoiSubtype("Bakeries",     listOf("shop=bakery")),
                PoiSubtype("Pastry Shops", listOf("shop=pastry")),
                PoiSubtype("Candy Stores", listOf("shop=confectionery")),
                PoiSubtype("Liquor Stores", listOf("shop=alcohol")),
                PoiSubtype("Wine Shops",   listOf("shop=wine")),
                PoiSubtype("Delis",        listOf("shop=deli")),
                PoiSubtype("Butcher Shops", listOf("shop=butcher")),
                PoiSubtype("Seafood Markets", listOf("shop=seafood")),
                PoiSubtype("Marketplaces", listOf("amenity=marketplace")),
                PoiSubtype("Breweries",    listOf("craft=brewery")),
                PoiSubtype("Wineries",     listOf("craft=winery")),
                PoiSubtype("Distilleries", listOf("craft=distillery"))
            ),
            color = Color.parseColor("#BF360C")
        ),

        // 2 — Fuel & Charging
        PoiCategory(
            id = PoiLayerId.FUEL_CHARGING,
            label = "Fuel & Charging",
            prefKey = "poi_fuel_charging_on",
            tags = listOf("amenity=fuel", "amenity=charging_station"),
            subtypes = listOf(
                PoiSubtype("Gas Stations",      listOf("amenity=fuel")),
                PoiSubtype("Charging Stations",  listOf("amenity=charging_station"))
            ),
            color = Color.parseColor("#E65100")
        ),

        // 3 — Transit
        PoiCategory(
            id = PoiLayerId.TRANSIT,
            label = "Transit",
            prefKey = "poi_transit_on",
            tags = listOf("public_transport=station", "railway=station", "amenity=bus_station",
                           "amenity=bicycle_rental", "amenity=ferry_terminal",
                           "aeroway=aerodrome", "amenity=taxi"),
            subtypes = listOf(
                PoiSubtype("Train Stations", listOf("public_transport=station", "railway=station")),
                PoiSubtype("Bus Stations",   listOf("amenity=bus_station")),
                PoiSubtype("Airports",       listOf("aeroway=aerodrome")),
                PoiSubtype("Bike Rentals",   listOf("amenity=bicycle_rental")),
                PoiSubtype("Ferry Terminals", listOf("amenity=ferry_terminal")),
                PoiSubtype("Taxi Stands",    listOf("amenity=taxi"))
            ),
            color = Color.parseColor("#0277BD")
        ),

        // 4 — Civic & Gov
        PoiCategory(
            id = PoiLayerId.CIVIC,
            label = "Civic & Gov",
            prefKey = "poi_civic_on",
            tags = listOf("amenity=townhall", "amenity=courthouse", "amenity=post_office", "office=government",
                           "amenity=community_centre", "amenity=social_facility", "amenity=post_box",
                           "amenity=recycling", "office=diplomatic"),
            subtypes = listOf(
                PoiSubtype("Town Halls",        listOf("amenity=townhall")),
                PoiSubtype("Courthouses",       listOf("amenity=courthouse")),
                PoiSubtype("Post Offices",      listOf("amenity=post_office")),
                PoiSubtype("Post Boxes",        listOf("amenity=post_box")),
                PoiSubtype("Gov Offices",       listOf("office=government")),
                PoiSubtype("Community Centres", listOf("amenity=community_centre")),
                PoiSubtype("Social Services",   listOf("amenity=social_facility")),
                PoiSubtype("Recycling",         listOf("amenity=recycling")),
                PoiSubtype("Embassies",         listOf("office=diplomatic"))
            ),
            color = Color.parseColor("#1A237E")
        ),

        // 5 — Parks & Rec
        PoiCategory(
            id = PoiLayerId.PARKS_REC,
            label = "Parks & Rec",
            prefKey = "poi_parks_rec_on",
            tags = listOf("leisure=park", "leisure=nature_reserve", "leisure=playground",
                           "leisure=pitch", "leisure=swimming_pool",
                           "leisure=garden", "tourism=picnic_site",
                           "amenity=drinking_water", "amenity=toilets",
                           "amenity=shelter", "amenity=fountain", "leisure=dog_park",
                           "leisure=track", "leisure=recreation_ground",
                           "leisure=beach_resort", "leisure=slipway", "leisure=skatepark"),
            subtypes = listOf(
                PoiSubtype("Parks",          listOf("leisure=park")),
                PoiSubtype("Nature Reserves", listOf("leisure=nature_reserve")),
                PoiSubtype("Playgrounds",    listOf("leisure=playground")),
                PoiSubtype("Sports Fields",  listOf("leisure=pitch")),
                PoiSubtype("Tracks",         listOf("leisure=track")),
                PoiSubtype("Rec Grounds",    listOf("leisure=recreation_ground")),
                PoiSubtype("Pools",          listOf("leisure=swimming_pool")),
                PoiSubtype("Dog Parks",      listOf("leisure=dog_park")),
                PoiSubtype("Gardens",        listOf("leisure=garden")),
                PoiSubtype("Boat Ramps",     listOf("leisure=slipway")),
                PoiSubtype("Skateparks",     listOf("leisure=skatepark")),
                PoiSubtype("Picnic Sites",   listOf("tourism=picnic_site")),
                PoiSubtype("Shelters",       listOf("amenity=shelter")),
                PoiSubtype("Fountains",      listOf("amenity=fountain")),
                PoiSubtype("Drinking Water", listOf("amenity=drinking_water")),
                PoiSubtype("Restrooms",      listOf("amenity=toilets")),
                PoiSubtype("Beaches",        listOf("leisure=beach_resort"))
            ),
            color = Color.parseColor("#2E7D32")
        ),

        // 6 — Shopping
        PoiCategory(
            id = PoiLayerId.SHOPPING,
            label = "Shopping",
            prefKey = "poi_shopping_on",
            tags = listOf("shop=supermarket", "shop=convenience", "shop=mall",
                           "shop=department_store", "shop=clothes",
                           "shop=hairdresser", "shop=beauty", "shop=massage",
                           "shop=gift", "shop=laundry", "shop=variety_store",
                           "shop=mobile_phone", "shop=dry_cleaning", "shop=books",
                           "shop=furniture", "shop=jewelry", "shop=optician",
                           "shop=florist", "shop=chemist", "shop=storage_rental",
                           "shop=shoes", "shop=tobacco", "shop=hardware",
                           "shop=pet", "shop=electronics", "shop=bicycle", "shop=garden_centre",
                           "shop=tattoo", "shop=barber", "shop=second_hand",
                           "shop=e-cigarette", "shop=cannabis"),
            subtypes = listOf(
                PoiSubtype("Supermarkets",       listOf("shop=supermarket")),
                PoiSubtype("Convenience Stores",  listOf("shop=convenience")),
                PoiSubtype("Malls",              listOf("shop=mall")),
                PoiSubtype("Department Stores",   listOf("shop=department_store")),
                PoiSubtype("Clothing",           listOf("shop=clothes")),
                PoiSubtype("Shoe Stores",        listOf("shop=shoes")),
                PoiSubtype("Jewelry",            listOf("shop=jewelry")),
                PoiSubtype("Hair Salons",        listOf("shop=hairdresser")),
                PoiSubtype("Barber Shops",       listOf("shop=barber")),
                PoiSubtype("Beauty & Spa",       listOf("shop=beauty")),
                PoiSubtype("Massage",            listOf("shop=massage")),
                PoiSubtype("Tattoo Shops",       listOf("shop=tattoo")),
                PoiSubtype("Bookstores",         listOf("shop=books")),
                PoiSubtype("Gift Shops",         listOf("shop=gift")),
                PoiSubtype("Florists",           listOf("shop=florist")),
                PoiSubtype("Furniture",          listOf("shop=furniture")),
                PoiSubtype("Hardware Stores",    listOf("shop=hardware")),
                PoiSubtype("Phone Stores",       listOf("shop=mobile_phone")),
                PoiSubtype("Opticians",          listOf("shop=optician")),
                PoiSubtype("Drug Stores",        listOf("shop=chemist")),
                PoiSubtype("Laundromats",        listOf("shop=laundry")),
                PoiSubtype("Dry Cleaners",       listOf("shop=dry_cleaning")),
                PoiSubtype("Variety Stores",     listOf("shop=variety_store")),
                PoiSubtype("Tobacco Shops",      listOf("shop=tobacco")),
                PoiSubtype("Vape Shops",         listOf("shop=e-cigarette")),
                PoiSubtype("Cannabis",           listOf("shop=cannabis")),
                PoiSubtype("Thrift Stores",      listOf("shop=second_hand")),
                PoiSubtype("Storage Rentals",    listOf("shop=storage_rental")),
                PoiSubtype("Pet Stores",         listOf("shop=pet")),
                PoiSubtype("Electronics",        listOf("shop=electronics")),
                PoiSubtype("Bicycle Shops",      listOf("shop=bicycle")),
                PoiSubtype("Garden Centers",     listOf("shop=garden_centre"))
            ),
            color = Color.parseColor("#F57F17")
        ),

        // 7 — Healthcare
        PoiCategory(
            id = PoiLayerId.HEALTHCARE,
            label = "Healthcare",
            prefKey = "poi_healthcare_on",
            tags = listOf("amenity=hospital", "amenity=pharmacy", "amenity=clinic",
                           "amenity=dentist", "amenity=doctors", "amenity=veterinary",
                           "amenity=nursing_home"),
            subtypes = listOf(
                PoiSubtype("Hospitals",     listOf("amenity=hospital")),
                PoiSubtype("Pharmacies",    listOf("amenity=pharmacy")),
                PoiSubtype("Clinics",       listOf("amenity=clinic")),
                PoiSubtype("Dentists",      listOf("amenity=dentist")),
                PoiSubtype("Doctors",       listOf("amenity=doctors")),
                PoiSubtype("Veterinary",    listOf("amenity=veterinary")),
                PoiSubtype("Nursing Homes", listOf("amenity=nursing_home"))
            ),
            color = Color.parseColor("#D32F2F")
        ),

        // 8 — Education
        PoiCategory(
            id = PoiLayerId.EDUCATION,
            label = "Education",
            prefKey = "poi_education_on",
            tags = listOf("amenity=school", "amenity=library", "amenity=college", "amenity=university",
                           "amenity=childcare", "amenity=kindergarten"),
            subtypes = listOf(
                PoiSubtype("Schools",        listOf("amenity=school")),
                PoiSubtype("Libraries",      listOf("amenity=library")),
                PoiSubtype("Colleges",       listOf("amenity=college")),
                PoiSubtype("Universities",   listOf("amenity=university")),
                PoiSubtype("Childcare",      listOf("amenity=childcare")),
                PoiSubtype("Kindergartens",  listOf("amenity=kindergarten"))
            ),
            color = Color.parseColor("#5D4037")
        ),

        // 9 — Lodging
        PoiCategory(
            id = PoiLayerId.LODGING,
            label = "Lodging",
            prefKey = "poi_lodging_on",
            tags = listOf("tourism=hotel", "tourism=motel", "tourism=hostel",
                           "tourism=camp_site", "tourism=guest_house", "tourism=caravan_site"),
            subtypes = listOf(
                PoiSubtype("Hotels",       listOf("tourism=hotel")),
                PoiSubtype("Motels",       listOf("tourism=motel")),
                PoiSubtype("Hostels",      listOf("tourism=hostel")),
                PoiSubtype("Campgrounds",  listOf("tourism=camp_site")),
                PoiSubtype("Guest Houses", listOf("tourism=guest_house")),
                PoiSubtype("RV Parks",     listOf("tourism=caravan_site"))
            ),
            color = Color.parseColor("#7B1FA2")
        ),

        // 10 — Parking
        PoiCategory(
            id = PoiLayerId.PARKING,
            label = "Parking",
            prefKey = "poi_parking_on",
            tags = listOf("amenity=parking"),
            subtypes = null,
            color = Color.parseColor("#455A64")
        ),

        // 11 — Finance
        PoiCategory(
            id = PoiLayerId.FINANCE,
            label = "Finance",
            prefKey = "poi_finance_on",
            tags = listOf("amenity=bank", "amenity=atm"),
            subtypes = listOf(
                PoiSubtype("Banks", listOf("amenity=bank")),
                PoiSubtype("ATMs",  listOf("amenity=atm"))
            ),
            color = Color.parseColor("#00695C")
        ),

        // 12 — Places of Worship
        PoiCategory(
            id = PoiLayerId.WORSHIP,
            label = "Places of Worship",
            prefKey = "poi_worship_on",
            tags = listOf("amenity=place_of_worship"),
            subtypes = null,
            color = Color.parseColor("#4E342E")
        ),

        // 13 — Tourism & History
        PoiCategory(
            id = PoiLayerId.TOURISM_HISTORY,
            label = "Tourism & History",
            prefKey = "poi_tourism_history_on",
            tags = listOf("tourism=museum", "tourism=attraction", "tourism=viewpoint",
                           "historic=memorial", "historic=monument",
                           "tourism=artwork", "tourism=gallery", "tourism=information",
                           "historic=cemetery", "historic=building",
                           "historic=ruins", "historic=maritime",
                           "tourism=zoo", "tourism=aquarium", "tourism=theme_park"),
            subtypes = listOf(
                PoiSubtype("Museums",     listOf("tourism=museum")),
                PoiSubtype("Attractions", listOf("tourism=attraction")),
                PoiSubtype("Viewpoints",  listOf("tourism=viewpoint")),
                PoiSubtype("Memorials",   listOf("historic=memorial")),
                PoiSubtype("Monuments",   listOf("historic=monument")),
                PoiSubtype("Public Art",  listOf("tourism=artwork")),
                PoiSubtype("Galleries",   listOf("tourism=gallery")),
                PoiSubtype("Info Points", listOf("tourism=information")),
                PoiSubtype("Cemeteries",  listOf("historic=cemetery")),
                PoiSubtype("Historic Bldgs", listOf("historic=building")),
                PoiSubtype("Ruins",       listOf("historic=ruins")),
                PoiSubtype("Maritime",    listOf("historic=maritime")),
                PoiSubtype("Zoos",        listOf("tourism=zoo")),
                PoiSubtype("Aquariums",   listOf("tourism=aquarium")),
                PoiSubtype("Theme Parks", listOf("tourism=theme_park"))
            ),
            color = Color.parseColor("#FF6F00")
        ),

        // 14 — Emergency Svc
        PoiCategory(
            id = PoiLayerId.EMERGENCY,
            label = "Emergency Svc",
            prefKey = "poi_emergency_on",
            tags = listOf("amenity=police", "amenity=fire_station"),
            subtypes = listOf(
                PoiSubtype("Police",        listOf("amenity=police")),
                PoiSubtype("Fire Stations", listOf("amenity=fire_station"))
            ),
            color = Color.parseColor("#B71C1C")
        ),

        // 15 — Auto Services
        PoiCategory(
            id = PoiLayerId.AUTO_SERVICES,
            label = "Auto Services",
            prefKey = "poi_auto_services_on",
            tags = listOf("shop=car_repair", "amenity=car_wash", "amenity=car_rental", "shop=tyres",
                           "shop=car", "shop=car_parts"),
            subtypes = listOf(
                PoiSubtype("Repair Shops",  listOf("shop=car_repair")),
                PoiSubtype("Car Washes",    listOf("amenity=car_wash")),
                PoiSubtype("Rentals",       listOf("amenity=car_rental")),
                PoiSubtype("Tire Shops",    listOf("shop=tyres")),
                PoiSubtype("Dealerships",   listOf("shop=car")),
                PoiSubtype("Parts Stores",  listOf("shop=car_parts"))
            ),
            color = Color.parseColor("#37474F")
        ),

        // 16 — Entertainment
        PoiCategory(
            id = PoiLayerId.ENTERTAINMENT,
            label = "Entertainment",
            prefKey = "poi_entertainment_on",
            tags = listOf("leisure=fitness_centre", "leisure=sports_centre", "leisure=golf_course",
                           "leisure=marina", "leisure=stadium", "leisure=disc_golf_course",
                           "amenity=theatre", "amenity=cinema", "amenity=nightclub",
                           "amenity=events_venue", "amenity=arts_centre",
                           "amenity=studio", "leisure=dance", "leisure=amusement_arcade",
                           "leisure=ice_rink", "leisure=bowling_alley",
                           "leisure=water_park", "leisure=miniature_golf", "leisure=escape_game"),
            subtypes = listOf(
                PoiSubtype("Fitness",       listOf("leisure=fitness_centre")),
                PoiSubtype("Sports Centres", listOf("leisure=sports_centre")),
                PoiSubtype("Golf Courses",  listOf("leisure=golf_course")),
                PoiSubtype("Disc Golf",     listOf("leisure=disc_golf_course")),
                PoiSubtype("Marinas",       listOf("leisure=marina")),
                PoiSubtype("Stadiums",      listOf("leisure=stadium")),
                PoiSubtype("Theatres",      listOf("amenity=theatre")),
                PoiSubtype("Cinemas",       listOf("amenity=cinema")),
                PoiSubtype("Nightclubs",    listOf("amenity=nightclub")),
                PoiSubtype("Event Venues",  listOf("amenity=events_venue")),
                PoiSubtype("Arts Centres",  listOf("amenity=arts_centre")),
                PoiSubtype("Studios",       listOf("amenity=studio")),
                PoiSubtype("Dance Studios", listOf("leisure=dance")),
                PoiSubtype("Arcades",       listOf("leisure=amusement_arcade")),
                PoiSubtype("Ice Rinks",     listOf("leisure=ice_rink")),
                PoiSubtype("Bowling",       listOf("leisure=bowling_alley")),
                PoiSubtype("Water Parks",   listOf("leisure=water_park")),
                PoiSubtype("Mini Golf",     listOf("leisure=miniature_golf")),
                PoiSubtype("Escape Rooms",  listOf("leisure=escape_game"))
            ),
            color = Color.parseColor("#00838F")
        ),

        // 17 — Offices & Services
        PoiCategory(
            id = PoiLayerId.OFFICES,
            label = "Offices & Services",
            prefKey = "poi_offices_on",
            tags = listOf("office=company", "office=estate_agent", "office=lawyer",
                           "office=insurance", "office=tax_advisor"),
            subtypes = listOf(
                PoiSubtype("Companies",   listOf("office=company")),
                PoiSubtype("Real Estate", listOf("office=estate_agent")),
                PoiSubtype("Law Offices", listOf("office=lawyer")),
                PoiSubtype("Insurance",   listOf("office=insurance")),
                PoiSubtype("Tax Advisors", listOf("office=tax_advisor"))
            ),
            color = Color.parseColor("#546E7A")
        )
    )

    /** Look up a category by its layer ID, or null. */
    fun find(id: String): PoiCategory? = ALL.find { it.id == id }
}
