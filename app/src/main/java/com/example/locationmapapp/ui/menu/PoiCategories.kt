package com.example.locationmapapp.ui.menu

import android.graphics.Color

/**
 * Central config for all 16 POI categories.
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
                           "amenity=bar", "amenity=pub", "amenity=ice_cream"),
            subtypes = listOf(
                PoiSubtype("Restaurants",  listOf("amenity=restaurant")),
                PoiSubtype("Fast Food",    listOf("amenity=fast_food")),
                PoiSubtype("Cafes",        listOf("amenity=cafe")),
                PoiSubtype("Bars",         listOf("amenity=bar")),
                PoiSubtype("Pubs",         listOf("amenity=pub")),
                PoiSubtype("Ice Cream",    listOf("amenity=ice_cream"))
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
            tags = listOf("public_transport=station", "railway=station", "amenity=bus_station"),
            subtypes = null,
            color = Color.parseColor("#0277BD")
        ),

        // 4 — Civic & Gov
        PoiCategory(
            id = PoiLayerId.CIVIC,
            label = "Civic & Gov",
            prefKey = "poi_civic_on",
            tags = listOf("amenity=townhall", "amenity=courthouse", "amenity=post_office", "office=government"),
            subtypes = listOf(
                PoiSubtype("Town Halls",    listOf("amenity=townhall")),
                PoiSubtype("Courthouses",   listOf("amenity=courthouse")),
                PoiSubtype("Post Offices",  listOf("amenity=post_office")),
                PoiSubtype("Gov Offices",   listOf("office=government"))
            ),
            color = Color.parseColor("#1A237E")
        ),

        // 5 — Parks & Rec
        PoiCategory(
            id = PoiLayerId.PARKS_REC,
            label = "Parks & Rec",
            prefKey = "poi_parks_rec_on",
            tags = listOf("leisure=park", "leisure=nature_reserve", "leisure=playground",
                           "leisure=pitch", "leisure=swimming_pool"),
            subtypes = listOf(
                PoiSubtype("Parks",          listOf("leisure=park")),
                PoiSubtype("Nature Reserves", listOf("leisure=nature_reserve")),
                PoiSubtype("Playgrounds",    listOf("leisure=playground")),
                PoiSubtype("Sports Fields",  listOf("leisure=pitch")),
                PoiSubtype("Pools",          listOf("leisure=swimming_pool"))
            ),
            color = Color.parseColor("#2E7D32")
        ),

        // 6 — Shopping
        PoiCategory(
            id = PoiLayerId.SHOPPING,
            label = "Shopping",
            prefKey = "poi_shopping_on",
            tags = listOf("shop=supermarket", "shop=convenience", "shop=mall",
                           "shop=department_store", "shop=clothes"),
            subtypes = listOf(
                PoiSubtype("Supermarkets",       listOf("shop=supermarket")),
                PoiSubtype("Convenience Stores",  listOf("shop=convenience")),
                PoiSubtype("Malls",              listOf("shop=mall")),
                PoiSubtype("Department Stores",   listOf("shop=department_store")),
                PoiSubtype("Clothing",           listOf("shop=clothes"))
            ),
            color = Color.parseColor("#F57F17")
        ),

        // 7 — Healthcare
        PoiCategory(
            id = PoiLayerId.HEALTHCARE,
            label = "Healthcare",
            prefKey = "poi_healthcare_on",
            tags = listOf("amenity=hospital", "amenity=pharmacy", "amenity=clinic",
                           "amenity=dentist", "amenity=doctors", "amenity=veterinary"),
            subtypes = listOf(
                PoiSubtype("Hospitals",   listOf("amenity=hospital")),
                PoiSubtype("Pharmacies",  listOf("amenity=pharmacy")),
                PoiSubtype("Clinics",     listOf("amenity=clinic")),
                PoiSubtype("Dentists",    listOf("amenity=dentist")),
                PoiSubtype("Doctors",     listOf("amenity=doctors")),
                PoiSubtype("Veterinary",  listOf("amenity=veterinary"))
            ),
            color = Color.parseColor("#D32F2F")
        ),

        // 8 — Education
        PoiCategory(
            id = PoiLayerId.EDUCATION,
            label = "Education",
            prefKey = "poi_education_on",
            tags = listOf("amenity=school", "amenity=library", "amenity=college", "amenity=university"),
            subtypes = listOf(
                PoiSubtype("Schools",       listOf("amenity=school")),
                PoiSubtype("Libraries",     listOf("amenity=library")),
                PoiSubtype("Colleges",      listOf("amenity=college")),
                PoiSubtype("Universities",  listOf("amenity=university"))
            ),
            color = Color.parseColor("#5D4037")
        ),

        // 9 — Lodging
        PoiCategory(
            id = PoiLayerId.LODGING,
            label = "Lodging",
            prefKey = "poi_lodging_on",
            tags = listOf("tourism=hotel", "tourism=motel", "tourism=hostel"),
            subtypes = null,
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
                           "historic=memorial", "historic=monument"),
            subtypes = listOf(
                PoiSubtype("Museums",     listOf("tourism=museum")),
                PoiSubtype("Attractions", listOf("tourism=attraction")),
                PoiSubtype("Viewpoints",  listOf("tourism=viewpoint")),
                PoiSubtype("Memorials",   listOf("historic=memorial")),
                PoiSubtype("Monuments",   listOf("historic=monument"))
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
            tags = listOf("shop=car_repair", "amenity=car_wash", "amenity=car_rental", "shop=tyres"),
            subtypes = null,
            color = Color.parseColor("#37474F")
        ),

        // 16 — Entertainment
        PoiCategory(
            id = PoiLayerId.ENTERTAINMENT,
            label = "Entertainment",
            prefKey = "poi_entertainment_on",
            tags = listOf("leisure=fitness_centre", "leisure=sports_centre", "leisure=golf_course",
                           "leisure=marina", "leisure=stadium"),
            subtypes = listOf(
                PoiSubtype("Fitness",       listOf("leisure=fitness_centre")),
                PoiSubtype("Sports Centres", listOf("leisure=sports_centre")),
                PoiSubtype("Golf Courses",  listOf("leisure=golf_course")),
                PoiSubtype("Marinas",       listOf("leisure=marina")),
                PoiSubtype("Stadiums",      listOf("leisure=stadium"))
            ),
            color = Color.parseColor("#00838F")
        )
    )

    /** Look up a category by its layer ID, or null. */
    fun find(id: String): PoiCategory? = ALL.find { it.id == id }
}
