/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.example.wickedsalemwitchcitytour.R
import com.example.locationmapapp.util.DebugLogger

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module MarkerIconHelper.kt"

/**
 * MarkerIconHelper
 *
 * Converts VectorDrawable resources into BitmapDrawable instances sized
 * and tinted for OSMDroid Marker use. Results are cached by (resId, sizeDp, colorInt)
 * to avoid repeated rasterisation on every marker add.
 *
 * Design intent: icons appear as clean, minimal glyphs directly on the map surface —
 * no filled circles, no drop shadows. The icon IS the marker.
 */
object MarkerIconHelper {

    private const val TAG = "MarkerIconHelper"
    private const val MAX_CACHE_SIZE = 2000

    // LRU cache: access-order LinkedHashMap evicts oldest entries beyond MAX_CACHE_SIZE
    private val cache = object : LinkedHashMap<String, BitmapDrawable>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BitmapDrawable>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    // ── Circle icon asset cache (witch-themed POI icons from Stable Diffusion) ──
    private val circleIconCache = mutableMapOf<String, Bitmap?>()

    /** OSM tag value → asset path under poi-circle-icons/ (without .png) */
    private val CIRCLE_ICON_MAP = mapOf(
        // Food & Drink
        "restaurant" to "food_drink/restaurant", "fast_food" to "food_drink/fast_food",
        "cafe" to "food_drink/cafe", "bar" to "food_drink/bar", "pub" to "food_drink/pub",
        "ice_cream" to "food_drink/ice_cream", "bakery" to "food_drink/bakery",
        "pastry" to "food_drink/pastry", "confectionery" to "food_drink/candy",
        "alcohol" to "food_drink/liquor", "wine" to "food_drink/wine", "deli" to "food_drink/deli",
        "butcher" to "food_drink/butcher", "seafood" to "food_drink/seafood",
        "marketplace" to "food_drink/marketplace", "brewery" to "food_drink/brewery",
        "winery" to "food_drink/winery", "distillery" to "food_drink/distillery",
        // Fuel & Charging
        "fuel" to "fuel_charging/gas_station", "gas_station" to "fuel_charging/gas_station",
        "charging_station" to "fuel_charging/charging_station",
        // Transit
        "station" to "transit/train_station", "train_station" to "transit/train_station",
        "bus_station" to "transit/bus_station", "aerodrome" to "transit/airport",
        "bicycle_rental" to "transit/bike_rental", "ferry_terminal" to "transit/ferry",
        "taxi" to "transit/taxi",
        // Civic & Gov
        "townhall" to "civic/town_hall", "courthouse" to "civic/courthouse",
        "post_office" to "civic/post_office", "government" to "civic/gov_office",
        "community_centre" to "civic/community_centre", "recycling" to "civic/recycling",
        "civic" to "civic/town_hall", "public" to "civic/community_centre",
        // Parks & Rec
        "park" to "parks_rec/park", "nature_reserve" to "parks_rec/nature_reserve",
        "playground" to "parks_rec/playground", "pitch" to "parks_rec/sports_field",
        "swimming_pool" to "parks_rec/pool", "dog_park" to "parks_rec/dog_park",
        "garden" to "parks_rec/garden", "slipway" to "parks_rec/boat_ramp",
        "fountain" to "parks_rec/fountain", "toilets" to "parks_rec/restroom",
        "beach_resort" to "parks_rec/beach",
        // Shopping
        "supermarket" to "shopping/supermarket", "convenience" to "shopping/convenience",
        "mall" to "shopping/mall", "department_store" to "shopping/mall",
        "clothes" to "shopping/clothing", "shoes" to "shopping/shoes",
        "jewelry" to "shopping/jewelry", "hairdresser" to "shopping/hair_salon",
        "barber" to "shopping/barber", "beauty" to "shopping/beauty_spa",
        "massage" to "shopping/beauty_spa", "tattoo" to "shopping/tattoo",
        "books" to "shopping/bookstore", "gift" to "shopping/gift_shop",
        "florist" to "shopping/florist", "hardware" to "shopping/hardware",
        "mobile_phone" to "shopping/phone_store", "pet" to "shopping/pet_store",
        "electronics" to "shopping/electronics", "bicycle" to "shopping/bicycle_shop",
        "second_hand" to "shopping/thrift", "cannabis" to "shopping/cannabis",
        // Healthcare
        "hospital" to "healthcare/hospital", "pharmacy" to "healthcare/pharmacy",
        "clinic" to "healthcare/clinic", "dentist" to "healthcare/dentist",
        "doctors" to "healthcare/doctor", "veterinary" to "healthcare/veterinary",
        "nursing_home" to "healthcare/nursing_home", "medical" to "healthcare/clinic",
        // Education
        "school" to "education/school", "library" to "education/library",
        "college" to "education/college", "university" to "education/university",
        "childcare" to "education/childcare", "kindergarten" to "education/childcare",
        // Lodging
        "hotel" to "lodging/hotel", "motel" to "lodging/motel", "hostel" to "lodging/hostel",
        "camp_site" to "lodging/campground", "guest_house" to "lodging/guest_house",
        "caravan_site" to "lodging/rv_park",
        // Parking
        "parking" to "parking/parking",
        // Finance
        "bank" to "finance/bank", "atm" to "finance/atm",
        // Worship
        "place_of_worship" to "worship/place_of_worship",
        // Tourism & History
        "museum" to "tourism_history/museum", "attraction" to "tourism_history/attraction",
        "viewpoint" to "tourism_history/viewpoint", "memorial" to "tourism_history/memorial",
        "monument" to "tourism_history/monument", "artwork" to "tourism_history/public_art",
        "gallery" to "tourism_history/gallery", "information" to "tourism_history/info_point",
        "cemetery" to "tourism_history/cemetery", "building" to "tourism_history/historic_building",
        "ruins" to "tourism_history/ruins", "maritime" to "tourism_history/maritime",
        "zoo" to "tourism_history/zoo", "aquarium" to "tourism_history/aquarium",
        "theme_park" to "tourism_history/theme_park", "historic" to "tourism_history/historic_building",
        // Emergency
        "police" to "emergency/police", "fire_station" to "emergency/fire_station",
        // Auto Services
        "car_repair" to "auto_services/repair_shop", "car_wash" to "auto_services/car_wash",
        "car_rental" to "auto_services/car_rental", "tyres" to "auto_services/tire_shop",
        "car" to "auto_services/dealership",
        // Entertainment
        "fitness_centre" to "entertainment/fitness", "sports_centre" to "entertainment/sports_centre",
        "golf_course" to "entertainment/golf", "marina" to "entertainment/marina",
        "stadium" to "entertainment/stadium", "theatre" to "entertainment/theatre",
        "cinema" to "entertainment/cinema", "nightclub" to "entertainment/nightclub",
        "events_venue" to "entertainment/event_venue", "arts_centre" to "entertainment/arts_centre",
        "amusement_arcade" to "entertainment/arcade", "ice_rink" to "entertainment/ice_rink",
        "bowling_alley" to "entertainment/bowling", "miniature_golf" to "entertainment/mini_golf",
        "escape_game" to "entertainment/escape_room",
        // Offices
        "company" to "offices/company", "estate_agent" to "offices/real_estate",
        "lawyer" to "offices/law_office", "insurance" to "offices/insurance",
        "tax_advisor" to "offices/tax_advisor", "services" to "offices/company",
        // Salem: Witch & Occult
        "esoteric" to "witch_shop/witchcraft_shop", "occult" to "witch_shop/occult_supplies",
        "metaphysical" to "witch_shop/metaphysical", "crystal" to "witch_shop/crystal_shop",
        "herbs" to "witch_shop/herb_shop", "witch_shop" to "witch_shop/witchcraft_shop",
        "shop_occult" to "witch_shop/occult_supplies",
        // Salem: Psychic & Tarot
        "psychic" to "psychic/psychic_reading", "tarot" to "psychic/tarot",
        "palmistry" to "psychic/palm_reading", "seance" to "psychic/seance",
        "spiritual" to "psychic/spiritual_healer", "shop_psychic" to "psychic/psychic_reading",
        // Salem: Ghost Tours
        "ghost_tour" to "ghost_tour/haunted_tour", "ghost_walk" to "ghost_tour/walking_tour",
        "haunted_tour" to "ghost_tour/haunted_tour", "night_tour" to "ghost_tour/night_tour",
        "historical_tour" to "ghost_tour/historical_tour", "tour_ghost" to "ghost_tour/haunted_tour",
        "tour" to "ghost_tour/walking_tour",
        // Salem: Haunted Attractions
        "haunted_attraction" to "haunted_attraction/haunted_house",
        "haunted_house" to "haunted_attraction/haunted_house",
        "attraction_haunted" to "haunted_attraction/haunted_house",
        "wax_museum" to "haunted_attraction/wax_museum",
        // Salem: Historic Houses
        "historic_house" to "historic_house/colonial_house",
        "colonial_house" to "historic_house/colonial_house",
        "witch_trial_house" to "historic_house/witch_trial_house",
        // ── SalemPoi unified categories (coarse, from Phase 9U) ──────────
        "food_drink"          to "food_drink/restaurant",
        "tourism_history"     to "tourism_history/historic_building",
        "entertainment"       to "entertainment/event_venue",
        "parks_rec"           to "parks_rec/park",
        "shopping"            to "shopping/gift_shop",
        "civic"               to "civic/gov_office",
        "lodging"             to "lodging/hotel",
        "healthcare"          to "healthcare/clinic",
        "education"           to "education/school",
        "offices"             to "offices/company",
        "worship"             to "worship/place_of_worship",
        "finance"             to "finance/bank",
        "auto_services"       to "auto_services/repair_shop",
        "parking"             to "parking/parking",
        "emergency"           to "emergency/police",
        "ghost_tour"          to "ghost_tour/haunted_tour",
        "witch_shop"          to "witch_shop/witchcraft_shop",
        "psychic"             to "psychic/psychic_reading",
        "haunted_attraction"  to "haunted_attraction/haunted_house",
        "historic_house"      to "historic_house/colonial_house",
        "historical_buildings" to "tourism_history/historic_building",
        // S216 split — HISTORICAL_LANDMARKS uses the same circle icon set as
        // HISTORICAL_BUILDINGS (both render historic-tag POIs); only the
        // gating + label differ.
        "historical_landmarks" to "tourism_history/historic_building",
        "tour_companies"      to "ghost_tour/walking_tour",
        // Venue / Other / catch-all narration_point types
        "venue" to "entertainment/event_venue",
        "shop" to "shopping/gift_shop",
        "shop_retail" to "shopping/gift_shop",
        "services" to "offices/company",
        "other" to "tourism_history/info_point",
        "historic_site" to "tourism_history/historic_building",
        "public_art" to "tourism_history/public_art",
        "lodging" to "lodging/guest_house",
        "hotel" to "lodging/hotel",
        "witch_museum" to "tourism_history/museum",
        "community_center" to "civic/community_centre",
        "visitor_info" to "tourism_history/info_point",
        "medical" to "healthcare/clinic",
        "public" to "civic/community_centre",
    )

    /**
     * Load a circle icon PNG from assets, scaled to the given size in pixels.
     * Returns null if no matching icon exists for this category.
     */
    private fun loadCircleIcon(context: Context, category: String, sizePx: Int): Bitmap? {
        val assetPath = CIRCLE_ICON_MAP[category.lowercase()] ?: return null
        val fullPath = "poi-circle-icons/$assetPath.webp"
        val key = "$fullPath|$sizePx"

        // Check cache (null = tried and failed, don't retry)
        if (circleIconCache.containsKey(key)) return circleIconCache[key]

        return try {
            val input = context.assets.open(fullPath)
            val original = BitmapFactory.decodeStream(input)
            input.close()
            val scaled = Bitmap.createScaledBitmap(original, sizePx, sizePx, true)
            if (scaled !== original) original.recycle()
            circleIconCache[key] = scaled
            scaled
        } catch (e: Exception) {
            circleIconCache[key] = null  // cache the miss
            null
        }
    }

    // ── Category → (drawableRes, tintColor) ───────────────────────────────────
    private val CATEGORY_MAP = mapOf(
        // ── System markers ──────────────────────────────────────────────────
        "gps"            to Pair(R.drawable.ic_gps,           Color.parseColor("#37474F")),
        "metar"          to Pair(R.drawable.ic_metar,         Color.parseColor("#1B5E20")),
        "transit_rail"   to Pair(R.drawable.ic_transit_rail,  Color.parseColor("#0277BD")),
        "bus_stop"       to Pair(R.drawable.ic_bus,           Color.parseColor("#1565C0")),
        "bus_station"    to Pair(R.drawable.ic_bus,           Color.parseColor("#1565C0")),
        "train_station"  to Pair(R.drawable.ic_train_station,  Color.parseColor("#37474F")),
        "camera"         to Pair(R.drawable.ic_camera,        Color.parseColor("#455A64")),
        "search"         to Pair(R.drawable.ic_search,        Color.parseColor("#4A148C")),
        "weather_alert"  to Pair(R.drawable.ic_weather_alert, Color.parseColor("#006064")),
        "record_gps"     to Pair(R.drawable.ic_record_gps,    Color.parseColor("#C62828")),
        "aircraft"       to Pair(R.drawable.ic_aircraft,      Color.parseColor("#1565C0")),
        "crosshair"      to Pair(R.drawable.ic_crosshair,     Color.parseColor("#FF6D00")),

        // ── Food & Drink (#BF360C) ──────────────────────────────────────────
        "restaurant"     to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "fast_food"      to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "cafe"           to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "bar"            to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "pub"            to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "ice_cream"      to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),

        // ── Fuel & Charging (#E65100) ───────────────────────────────────────
        "fuel"               to Pair(R.drawable.ic_gas_station, Color.parseColor("#E65100")),
        "gas_station"        to Pair(R.drawable.ic_gas_station, Color.parseColor("#E65100")),
        "charging_station"   to Pair(R.drawable.ic_gas_station, Color.parseColor("#E65100")),

        // ── Transit (#0277BD) — POI markers ─────────────────────────────────
        "station"        to Pair(R.drawable.ic_transit_rail,  Color.parseColor("#0277BD")),

        // ── Civic & Gov (#1A237E) ───────────────────────────────────────────
        "civic"          to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "government"     to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "townhall"       to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "courthouse"     to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "post_office"    to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),

        // ── Parks & Rec (#2E7D32) ───────────────────────────────────────────
        "park"               to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "nature_reserve"     to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "playground"         to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "pitch"              to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),
        "swimming_pool"      to Pair(R.drawable.ic_park,      Color.parseColor("#2E7D32")),

        // ── Shopping (#F57F17) ──────────────────────────────────────────────
        "supermarket"        to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "convenience"        to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "mall"               to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "department_store"   to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),
        "clothes"            to Pair(R.drawable.ic_poi,       Color.parseColor("#F57F17")),

        // ── Healthcare (#D32F2F) ────────────────────────────────────────────
        "hospital"       to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "pharmacy"       to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "clinic"         to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "dentist"        to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "doctors"        to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "veterinary"     to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),

        // ── Education (#5D4037) ─────────────────────────────────────────────
        "school"         to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),
        "library"        to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),
        "college"        to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),
        "university"     to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),

        // ── Lodging (#7B1FA2) ───────────────────────────────────────────────
        "hotel"          to Pair(R.drawable.ic_poi,           Color.parseColor("#7B1FA2")),
        "motel"          to Pair(R.drawable.ic_poi,           Color.parseColor("#7B1FA2")),
        "hostel"         to Pair(R.drawable.ic_poi,           Color.parseColor("#7B1FA2")),

        // ── Parking (#455A64) ───────────────────────────────────────────────
        "parking"        to Pair(R.drawable.ic_poi,           Color.parseColor("#455A64")),

        // ── Finance (#00695C) ───────────────────────────────────────────────
        "bank"           to Pair(R.drawable.ic_poi,           Color.parseColor("#00695C")),
        "atm"            to Pair(R.drawable.ic_poi,           Color.parseColor("#00695C")),

        // ── Places of Worship (#4E342E) ─────────────────────────────────────
        "place_of_worship" to Pair(R.drawable.ic_poi,         Color.parseColor("#4E342E")),

        // ── Tourism & History (#FF6F00) ─────────────────────────────────────
        "museum"         to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "attraction"     to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "viewpoint"      to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "memorial"       to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "monument"       to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),

        // ── Emergency Svc (#B71C1C) ─────────────────────────────────────────
        "police"         to Pair(R.drawable.ic_poi,           Color.parseColor("#B71C1C")),
        "fire_station"   to Pair(R.drawable.ic_poi,           Color.parseColor("#B71C1C")),

        // ── Auto Services (#37474F) ─────────────────────────────────────────
        "car_repair"     to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),
        "car_wash"       to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),
        "car_rental"     to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),
        "tyres"          to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),

        // ── Entertainment (#00838F) ─────────────────────────────────────────
        "fitness_centre" to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "sports_centre"  to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "golf_course"    to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "marina"         to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "stadium"        to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),

        // ── Salem: Witch & Occult (#6A1B9A) ─────────────────────────────────
        "shop_occult"    to Pair(R.drawable.ic_poi,           Color.parseColor("#6A1B9A")),
        "witch_shop"     to Pair(R.drawable.ic_poi,           Color.parseColor("#6A1B9A")),

        // ── Salem: Psychic & Tarot (#AB47BC) ────────────────────────────────
        "shop_psychic"   to Pair(R.drawable.ic_poi,           Color.parseColor("#AB47BC")),
        "psychic"        to Pair(R.drawable.ic_poi,           Color.parseColor("#AB47BC")),

        // ── Salem: Ghost Tours (#E040FB) ────────────────────────────────────
        "tour_ghost"     to Pair(R.drawable.ic_poi,           Color.parseColor("#E040FB")),
        "ghost_tour"     to Pair(R.drawable.ic_poi,           Color.parseColor("#E040FB")),
        "tour"           to Pair(R.drawable.ic_poi,           Color.parseColor("#E040FB")),

        // ── Salem: Haunted Attractions (#D500F9) ────────────────────────────
        "attraction_haunted" to Pair(R.drawable.ic_poi,       Color.parseColor("#D500F9")),
        "haunted_attraction" to Pair(R.drawable.ic_poi,       Color.parseColor("#D500F9")),

        // ── Salem: Historic Houses (#8D6E63) ────────────────────────────────
        "historic"       to Pair(R.drawable.ic_poi,           Color.parseColor("#8D6E63")),
        "historic_house" to Pair(R.drawable.ic_poi,           Color.parseColor("#8D6E63")),

        // ── Salem: Other expanded types ─────────────────────────────────────
        "venue"          to Pair(R.drawable.ic_poi,           Color.parseColor("#E91E63")),
        "shop_retail"    to Pair(R.drawable.ic_poi,           Color.parseColor("#F57F17")),
        "public"         to Pair(R.drawable.ic_civic,         Color.parseColor("#1A237E")),
        "medical"        to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "services"       to Pair(R.drawable.ic_poi,           Color.parseColor("#546E7A")),

        // ── SalemPoi unified categories (Phase 9U, lowercase) ──────────────
        "food_drink"          to Pair(R.drawable.ic_restaurant,    Color.parseColor("#BF360C")),
        "tourism_history"     to Pair(R.drawable.ic_poi,           Color.parseColor("#FF6F00")),
        "entertainment"       to Pair(R.drawable.ic_poi,           Color.parseColor("#00838F")),
        "parks_rec"           to Pair(R.drawable.ic_park,          Color.parseColor("#2E7D32")),
        "lodging"             to Pair(R.drawable.ic_poi,           Color.parseColor("#7B1FA2")),
        "shopping"            to Pair(R.drawable.ic_poi,           Color.parseColor("#F57F17")),
        "education"           to Pair(R.drawable.ic_poi,           Color.parseColor("#5D4037")),
        "healthcare"          to Pair(R.drawable.ic_poi,           Color.parseColor("#D32F2F")),
        "offices"             to Pair(R.drawable.ic_poi,           Color.parseColor("#546E7A")),
        "finance"             to Pair(R.drawable.ic_poi,           Color.parseColor("#00695C")),
        "auto_services"       to Pair(R.drawable.ic_poi,           Color.parseColor("#37474F")),
        "worship"             to Pair(R.drawable.ic_poi,           Color.parseColor("#4E342E")),
        // S143: two categories introduced by the S134 historic-sites reclass
        // that were missing from the icon map — fell through to DEFAULT and
        // rendered as a near-invisible generic dot. Brown for buildings
        // matches the existing `historic_house` palette; pink-purple for tour
        // companies groups them visually with `ghost_tour`.
        "historical_buildings" to Pair(R.drawable.ic_poi,          Color.parseColor("#8D6E63")),
        // S216 — same icon, lighter brown so the two layers read distinct on the map.
        "historical_landmarks" to Pair(R.drawable.ic_poi,          Color.parseColor("#A1887F")),
        "tour_companies"       to Pair(R.drawable.ic_poi,          Color.parseColor("#E040FB")),
    )

    // Default fallback
    private val DEFAULT = Pair(R.drawable.ic_poi, Color.parseColor("#6A1B9A"))

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Get a marker icon for a named category.
     * @param sizeDp  Icon size in density-independent pixels (default 28dp — visible but not obstructing)
     */
    fun forCategory(context: Context, category: String, sizeDp: Int = 28): BitmapDrawable {
        val (resId, color) = CATEGORY_MAP[category.lowercase()] ?: DEFAULT
        return get(context, resId, sizeDp, color)
    }

    /**
     * Get a marker icon directly from a drawable resource, with optional tint.
     */
    fun get(context: Context, resId: Int, sizeDp: Int = 28, tintColor: Int? = null): BitmapDrawable {
        val px = (sizeDp * context.resources.displayMetrics.density).toInt()
        val key = "$resId|$px|${tintColor ?: 0}"
        cache[key]?.let { return it }

        return try {
            val drawable: Drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
            tintColor?.let { DrawableCompat.setTint(drawable, it) }
            val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, px, px)
            drawable.draw(canvas)
            val result = BitmapDrawable(context.resources, bitmap)
            cache[key] = result
            result
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Failed to rasterize icon resId=$resId", e)
            // Return a 1x1 transparent fallback rather than crashing
            BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
        }
    }

    /**
     * POI marker icon: witch-themed circle icon from assets if available,
     * otherwise falls back to the tiny colored dot.
     * @param sizeDp icon size — use 12 for dense/low-zoom, 20 for medium, 28 for close-up
     */
    fun dot(context: Context, category: String, sizeDp: Int = 12): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        val key = "cdot|$category|$sizePx"
        cache[key]?.let { return it }

        // Try circle icon from assets first
        val circleIcon = loadCircleIcon(context, category, sizePx)
        if (circleIcon != null) {
            val result = BitmapDrawable(context.resources, circleIcon)
            cache[key] = result
            return result
        }

        // Fallback: colored dot
        val (_, color) = CATEGORY_MAP[category.lowercase()] ?: DEFAULT
        return dotFallback(context, color)
    }

    fun dot(context: Context, color: Int): BitmapDrawable {
        return dotFallback(context, color)
    }

    private fun dotFallback(context: Context, color: Int): BitmapDrawable {
        val key = "dot|$color"
        cache[key]?.let { return it }

        val sizePx = (5 * context.resources.displayMetrics.density).toInt().coerceAtLeast(5)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = 160
        canvas.drawCircle(cx, cy, cx, paint)
        paint.alpha = 255
        canvas.drawCircle(cx, cy, cx * 0.35f, paint)

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    /**
     * Cluster circle icon: filled circle with count text centered, sized by count threshold.
     * Matches web app's cluster visualization.
     */
    fun clusterIcon(context: Context, count: Int, color: Int): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val radiusDp = when {
            count <= 5   -> 16
            count <= 20  -> 20
            count <= 50  -> 24
            count <= 100 -> 28
            else         -> 32
        }
        val sizePx = (radiusDp * 2 * density).toInt()
        val key = "cluster|$count|$color|$sizePx"
        cache[key]?.let { return it }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = cx - density  // 1dp inset for border

        // Fill circle — 30% opacity
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
            alpha = 77  // ~30%
        }
        canvas.drawCircle(cx, cy, radius, fillPaint)

        // Border — 60% opacity
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color
            alpha = 153  // ~60%
            strokeWidth = density
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Count text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = (radiusDp * 0.7f) * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f * density, 0f, 0f, Color.argb(128, 0, 0, 0))
        }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(count.toString(), cx, textY, textPaint)

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    /** Resolve a cluster tag string (e.g. "amenity=restaurant") to a category color. */
    fun clusterTagColor(tag: String): Int {
        // Try direct match on value after "="
        val value = tag.substringAfter("=", tag)
        CATEGORY_MAP[value.lowercase()]?.let { return it.second }
        // Fallback: match the key
        val key = tag.substringBefore("=", tag)
        return when (key.lowercase()) {
            "amenity"    -> Color.parseColor("#BF360C")  // Food & Drink orange
            "shop"       -> Color.parseColor("#F57F17")  // Shopping yellow
            "tourism"    -> Color.parseColor("#FF6F00")   // Tourism amber
            "leisure"    -> Color.parseColor("#2E7D32")   // Parks green
            "historic"   -> Color.parseColor("#FF6F00")   // History amber
            "office"     -> Color.parseColor("#455A64")   // Offices gray
            "healthcare" -> Color.parseColor("#D32F2F")   // Healthcare red
            else         -> DEFAULT.second
        }
    }

    /**
     * Labeled POI marker for high zoom levels (≥18): category type above the dot, name below.
     * Layout (top to bottom): category label → dot → name label
     */
    fun labeledDot(context: Context, category: String, name: String): BitmapDrawable {
        val (_, color) = CATEGORY_MAP[category.lowercase()] ?: DEFAULT
        val density = context.resources.displayMetrics.density

        // Humanize category: "fast_food" → "Fast Food"
        val typeLabel = category.replace('_', ' ')
            .split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        val nameLabel = if (name.isNotBlank()) name else ""

        val key = "ldot|$color|$typeLabel|$nameLabel"
        cache[key]?.let { return it }

        val iconSizeDp = 32
        val iconSizePx = (iconSizeDp * density).toInt()

        val typePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = 10 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.DKGRAY
            textSize = 10 * density
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        val typeW = if (typeLabel.isNotEmpty()) typePaint.measureText(typeLabel).toInt() else 0
        val nameW = if (nameLabel.isNotEmpty()) namePaint.measureText(nameLabel).toInt() else 0
        val typeH = if (typeLabel.isNotEmpty()) (typePaint.textSize + 2 * density).toInt() else 0
        val nameH = if (nameLabel.isNotEmpty()) (namePaint.textSize + 2 * density).toInt() else 0
        val gap = (2 * density).toInt()

        val totalW = maxOf(iconSizePx, typeW, nameW) + (8 * density).toInt()
        val totalH = typeH + gap + iconSizePx + gap + nameH
        val cx = totalW / 2f

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Category type label (above icon)
        if (typeLabel.isNotEmpty()) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; alpha = 210; style = Paint.Style.FILL
            }
            val tw = typePaint.measureText(typeLabel)
            val padH = 3 * density
            val rect = RectF(cx - tw / 2 - padH, 0f, cx + tw / 2 + padH, typeH.toFloat())
            canvas.drawRoundRect(rect, 3 * density, 3 * density, bgPaint)
            canvas.drawText(typeLabel, cx, typeH - 4 * density, typePaint)
        }

        // Circle icon (or fallback dot)
        val iconTop = typeH + gap
        val circleIcon = loadCircleIcon(context, category, iconSizePx)
        if (circleIcon != null) {
            val iconLeft = ((totalW - iconSizePx) / 2f).toInt()
            canvas.drawBitmap(circleIcon, iconLeft.toFloat(), iconTop.toFloat(), null)
        } else {
            // Fallback dot
            val dotCy = iconTop + iconSizePx / 2f
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; this.color = color; alpha = 160
            }
            canvas.drawCircle(cx, dotCy, iconSizePx / 4f, dotPaint)
            dotPaint.alpha = 255
            canvas.drawCircle(cx, dotCy, iconSizePx * 0.1f, dotPaint)
        }

        // Name label (below dot)
        if (nameLabel.isNotEmpty()) {
            val nameTop = typeH + gap + iconSizePx + gap
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = Color.WHITE; alpha = 210; style = Paint.Style.FILL
            }
            val nw = namePaint.measureText(nameLabel)
            val padH = 3 * density
            val rect = RectF(cx - nw / 2 - padH, nameTop.toFloat(), cx + nw / 2 + padH, (nameTop + nameH).toFloat())
            canvas.drawRoundRect(rect, 3 * density, 3 * density, bgPaint)
            canvas.drawText(nameLabel, cx, nameTop + nameH - 4 * density, namePaint)
        }

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    /**
     * Icon with a small directional arrow on top, rotated to [bearingDeg].
     * The arrow sits just above the icon so direction of travel is visible at a glance.
     */
    fun withArrow(context: Context, resId: Int, sizeDp: Int, tintColor: Int, bearingDeg: Int): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val iconPx = (sizeDp * density).toInt()
        val arrowSize = (8 * density).toInt()          // arrow height in px
        val gap = (2 * density).toInt()                // space between arrow and icon
        val totalW = iconPx + arrowSize * 2            // wide enough for rotated arrow
        val totalH = iconPx + arrowSize + gap

        val key = "arrow|$resId|$iconPx|${tintColor}|$bearingDeg"
        cache[key]?.let { return it }

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw the base icon centered horizontally, at the bottom
        val iconLeft = (totalW - iconPx) / 2
        val iconTop = arrowSize + gap
        val drawable: Drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
        DrawableCompat.setTint(drawable, tintColor)
        drawable.setBounds(iconLeft, iconTop, iconLeft + iconPx, iconTop + iconPx)
        drawable.draw(canvas)

        // Draw rotated arrow above the icon
        val arrowCx = totalW / 2f
        val arrowCy = arrowSize.toFloat()
        canvas.save()
        canvas.rotate(bearingDeg.toFloat(), arrowCx, arrowCy)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(arrowCx, arrowCy - arrowSize * 0.7f)          // tip (points up = north = 0°)
            lineTo(arrowCx - arrowSize * 0.4f, arrowCy + arrowSize * 0.3f) // bottom-left
            lineTo(arrowCx + arrowSize * 0.4f, arrowCy + arrowSize * 0.3f) // bottom-right
            close()
        }
        canvas.drawPath(path, paint)
        canvas.restore()

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    /**
     * Labeled vehicle marker for high zoom levels (≥18): status pill above, arrow+icon center,
     * speed badge + route pill below.
     *
     * @return Pair(BitmapDrawable, anchorY) — anchorY points at the icon center (geographic position)
     */
    fun labeledVehicle(
        context: Context,
        resId: Int,
        sizeDp: Int,
        tintColor: Int,
        bearingDeg: Int,
        routeName: String,
        headsign: String?,
        stopName: String?,
        statusDisplay: String,
        speedDisplay: String,
        isStale: Boolean,
        nextStopMinutes: Int? = null
    ): Pair<BitmapDrawable, Float> {
        val density = context.resources.displayMetrics.density

        // ── Truncation helper ─────────────────────────────────────────────────
        fun trunc(s: String, max: Int = 18): String =
            if (s.length > max) s.take(max) + "…" else s

        // ── Prepare text ──────────────────────────────────────────────────────
        val statusPrefix = when {
            statusDisplay.startsWith("En route")  -> "→"
            statusDisplay.startsWith("Stopped")   -> "●"
            statusDisplay.startsWith("Arriving")  -> "↓"
            else -> ""
        }
        val topText = trunc(if (stopName != null) "$statusPrefix $stopName" else statusDisplay)
        val bottomText = trunc(if (headsign != null) "$routeName · To $headsign" else routeName)
        val speedText = if (speedDisplay != "—") speedDisplay else ""
        val etaText = nextStopMinutes?.let { "${it}m" } ?: ""

        val key = "lv|$resId|$sizeDp|$tintColor|$bearingDeg|$topText|$bottomText|$speedText|$isStale|$etaText"
        cache[key]?.let {
            // Recompute anchorY from cached bitmap dimensions
            val iconPx = (sizeDp * density).toInt()
            val arrowSz = (8 * density).toInt()
            val arrowGap = (2 * density).toInt()
            val pillH = (10 * density + 2 * density + 2 * 2 * density).toInt() // textSize + padding
            val gap = (2 * density).toInt()
            val iconCenterFromTop = pillH + gap + arrowSz + arrowGap + iconPx / 2f
            val anchorY = iconCenterFromTop / it.bitmap.height.toFloat()
            return Pair(it, anchorY)
        }

        val iconPx = (sizeDp * density).toInt()
        val arrowSz = (8 * density).toInt()
        val arrowGap = (2 * density).toInt()

        // ── Text paints ───────────────────────────────────────────────────────
        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            textSize = 10 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 10 * density
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val pillPadH = 3 * density
        val pillPadV = 2 * density
        val pillCorner = 3 * density

        // ── Measure pills ─────────────────────────────────────────────────────
        val topTextW = topPaint.measureText(topText)
        val topPillW = topTextW + 2 * pillPadH
        val topPillH = (topPaint.textSize + 2 * pillPadV).toInt()

        val bottomTextW = bottomPaint.measureText(bottomText)
        val bottomPillW = bottomTextW + 2 * pillPadH
        val bottomPillH = (bottomPaint.textSize + 2 * pillPadV).toInt()

        val speedW = if (speedText.isNotEmpty()) speedPaint.measureText(speedText) else 0f
        val speedPillW = if (speedText.isNotEmpty()) speedW + 2 * pillPadH else 0f
        val speedGap = if (speedText.isNotEmpty()) 2 * density else 0f

        // ── ETA pill measurement ────────────────────────────────────────────
        val etaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val etaW = if (etaText.isNotEmpty()) etaPaint.measureText(etaText) else 0f
        val etaPillW = if (etaText.isNotEmpty()) etaW + 2 * pillPadH else 0f
        val etaPillH = if (etaText.isNotEmpty()) (etaPaint.textSize + 2 * pillPadV).toInt() else 0
        val etaGap = if (etaText.isNotEmpty()) 3 * density else 0f  // gap between icon and eta

        // ── Total dimensions ──────────────────────────────────────────────────
        val gap = (2 * density).toInt()
        val arrowArea = iconPx + arrowSz * 2  // width for rotated arrow
        val arrowAreaWithEta = arrowArea + etaGap + etaPillW  // icon area + eta badge
        val bottomRowW = speedPillW + speedGap + bottomPillW

        val totalW = maxOf(topPillW, arrowAreaWithEta, bottomRowW).toInt() + (4 * density).toInt()
        val totalH = topPillH + gap + arrowSz + arrowGap + iconPx + gap + bottomPillH

        val workBitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(workBitmap)
        val cx = totalW / 2f

        // ── Top pill (status + stop name) ─────────────────────────────────────
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; alpha = 210; style = Paint.Style.FILL
        }
        val topRect = RectF(
            cx - topPillW / 2, 0f,
            cx + topPillW / 2, topPillH.toFloat()
        )
        canvas.drawRoundRect(topRect, pillCorner, pillCorner, bgPaint)
        canvas.drawText(topText, cx, topPillH - pillPadV - 1 * density, topPaint)

        // ── Arrow + icon (inlined from withArrow logic) ───────────────────────
        val iconLeft = (totalW - iconPx) / 2
        val iconTop = topPillH + gap + arrowSz + arrowGap
        val drawable: Drawable = ContextCompat.getDrawable(context, resId)!!.mutate()
        DrawableCompat.setTint(drawable, tintColor)
        drawable.setBounds(iconLeft, iconTop, iconLeft + iconPx, iconTop + iconPx)
        drawable.draw(canvas)

        // Rotated arrow above icon
        val arrowCx = totalW / 2f
        val arrowCy = (topPillH + gap + arrowSz).toFloat()
        canvas.save()
        canvas.rotate(bearingDeg.toFloat(), arrowCx, arrowCy)
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor; style = Paint.Style.FILL
        }
        val path = Path().apply {
            moveTo(arrowCx, arrowCy - arrowSz * 0.7f)
            lineTo(arrowCx - arrowSz * 0.4f, arrowCy + arrowSz * 0.3f)
            lineTo(arrowCx + arrowSz * 0.4f, arrowCy + arrowSz * 0.3f)
            close()
        }
        canvas.drawPath(path, arrowPaint)
        canvas.restore()

        // ── ETA badge (right of icon, vertically centered with icon) ────────
        if (etaText.isNotEmpty()) {
            val iconCenterY = iconTop + iconPx / 2f
            val etaLeft = iconLeft + iconPx + etaGap
            val etaTop = iconCenterY - etaPillH / 2f
            val etaRect = RectF(etaLeft, etaTop, etaLeft + etaPillW, etaTop + etaPillH)
            val etaBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tintColor; style = Paint.Style.FILL
            }
            canvas.drawRoundRect(etaRect, pillCorner, pillCorner, etaBgPaint)
            canvas.drawText(etaText, etaRect.centerX(), etaTop + etaPillH - pillPadV - 1 * density, etaPaint)
        }

        // ── Bottom row: speed badge (optional) + route pill ───────────────────
        val bottomY = (topPillH + gap + arrowSz + arrowGap + iconPx + gap).toFloat()
        val bottomRowCenter = cx + (speedPillW + speedGap) / 2f  // shift right when speed present

        // Route pill
        val bottomRect = RectF(
            cx + (speedPillW + speedGap) / 2f - bottomPillW / 2,
            bottomY,
            cx + (speedPillW + speedGap) / 2f + bottomPillW / 2,
            bottomY + bottomPillH
        )
        bgPaint.alpha = 210
        canvas.drawRoundRect(bottomRect, pillCorner, pillCorner, bgPaint)
        canvas.drawText(bottomText, bottomRect.centerX(), bottomY + bottomPillH - pillPadV - 1 * density, bottomPaint)

        // Speed badge (left of route pill)
        if (speedText.isNotEmpty()) {
            val speedRect = RectF(
                bottomRect.left - speedGap - speedPillW,
                bottomY,
                bottomRect.left - speedGap,
                bottomY + bottomPillH
            )
            val speedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tintColor; style = Paint.Style.FILL
            }
            canvas.drawRoundRect(speedRect, pillCorner, pillCorner, speedBgPaint)
            canvas.drawText(speedText, speedRect.centerX(), bottomY + bottomPillH - pillPadV - 1 * density, speedPaint)
        }

        // ── Staleness dimming ─────────────────────────────────────────────────
        val finalBitmap = if (isStale) {
            val out = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val dimPaint = Paint().apply { alpha = 128 }
            c.drawBitmap(workBitmap, 0f, 0f, dimPaint)
            workBitmap.recycle()
            out
        } else {
            workBitmap
        }

        val result = BitmapDrawable(context.resources, finalBitmap)
        cache[key] = result

        // anchorY: icon center relative to total height
        val iconCenterFromTop = topPillH + gap + arrowSz + arrowGap + iconPx / 2f
        val anchorY = iconCenterFromTop / totalH
        return Pair(result, anchorY)
    }

    /**
     * Render an aircraft marker: rotated airplane icon pointing to [headingDeg],
     * callsign label, vertical rate indicator (arrow up/down/dash), and an optional
     * thick red circle when [spi] (Special Purpose Indicator / emergency) is active.
     *
     * Layout (top to bottom):
     *   callsign + vert indicator text
     *   rotated airplane icon (with optional red ring)
     */
    fun aircraftMarker(
        context: Context,
        headingDeg: Int,
        tintColor: Int,
        callsign: String?,
        verticalRate: Double?,   // m/s — positive=climb, negative=descend
        spi: Boolean
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        val iconPx = (24 * density).toInt()
        val spiStroke = (3 * density).toInt()       // thick red ring
        val spiPad = if (spi) spiStroke + (2 * density).toInt() else 0
        val iconArea = iconPx + spiPad * 2           // icon + ring space

        // Vertical-rate indicator character
        val vertChar = when {
            verticalRate == null      -> ""
            verticalRate > 0.5        -> " \u2191"   // ↑ climbing
            verticalRate < -0.5       -> " \u2193"   // ↓ descending
            else                      -> " \u2014"   // — level
        }
        val label = (callsign?.ifBlank { null } ?: "") + vertChar

        // Measure text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 11 * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textWidth = if (label.isNotEmpty()) textPaint.measureText(label).toInt() + (4 * density).toInt() else 0
        val textHeight = if (label.isNotEmpty()) (textPaint.textSize + 4 * density).toInt() else 0
        val textGap = if (label.isNotEmpty()) (2 * density).toInt() else 0

        val totalW = maxOf(iconArea, textWidth)
        val totalH = textHeight + textGap + iconArea

        val key = "acm|$headingDeg|$tintColor|${label}|$spi"
        cache[key]?.let { return it }

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = totalW / 2f

        // ── Draw label (callsign + vert indicator) ──────────────────────────
        if (label.isNotEmpty()) {
            // Background pill
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 200
                style = Paint.Style.FILL
            }
            val tw = textPaint.measureText(label)
            val padH = 3 * density
            val padV = 1 * density
            val rect = RectF(cx - tw / 2 - padH, 0f, cx + tw / 2 + padH, textHeight.toFloat())
            canvas.drawRoundRect(rect, 3 * density, 3 * density, bgPaint)
            canvas.drawText(label, cx, textHeight - 4 * density, textPaint)
        }

        // ── Draw SPI emergency ring ─────────────────────────────────────────
        val iconCx = cx
        val iconCy = textHeight + textGap + iconArea / 2f
        if (spi) {
            val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.RED
                style = Paint.Style.STROKE
                strokeWidth = spiStroke.toFloat()
            }
            val ringRadius = iconArea / 2f - spiStroke / 2f
            canvas.drawCircle(iconCx, iconCy, ringRadius, ringPaint)
        }

        // ── Draw rotated airplane icon ──────────────────────────────────────
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_aircraft)!!.mutate()
        DrawableCompat.setTint(drawable, tintColor)

        canvas.save()
        canvas.rotate(headingDeg.toFloat(), iconCx, iconCy)
        val halfIcon = iconPx / 2
        drawable.setBounds(
            (iconCx - halfIcon).toInt(), (iconCy - halfIcon).toInt(),
            (iconCx + halfIcon).toInt(), (iconCy + halfIcon).toInt()
        )
        drawable.draw(canvas)
        canvas.restore()

        val result = BitmapDrawable(context.resources, bitmap)
        cache[key] = result
        return result
    }

    /**
     * Station icon: 26dp train station drawable tinted to [tintColor].
     */
    fun stationIcon(context: Context, tintColor: Int): BitmapDrawable {
        return get(context, R.drawable.ic_train_station, 26, tintColor)
    }

    /**
     * Bus stop icon: 20dp bus stop sign, smaller than 26dp station icons.
     */
    fun busStopIcon(context: Context, tintColor: Int): BitmapDrawable {
        return get(context, R.drawable.ic_bus_stop, 20, tintColor)
    }

    /**
     * Create a numbered circle marker for tour stops.
     * @param number Stop number (1-based)
     * @param color Fill color (green=completed, blue=current, gray=upcoming)
     */
    fun createNumberedCircle(context: Context, number: Int, color: Int): BitmapDrawable {
        val key = "tour_circle_${number}_${color}"
        cache[key]?.let { return it }

        val density = context.resources.displayMetrics.density
        val sizePx = (28 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = sizePx / 2f
        val cy = sizePx / 2f
        val radius = sizePx / 2f - 2 * density

        // Circle fill
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, radius, fillPaint)

        // White border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
        }
        canvas.drawCircle(cx, cy, radius, borderPaint)

        // Number text
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = 12 * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textBounds = Rect()
        val text = number.toString()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        canvas.drawText(text, cx, cy + textBounds.height() / 2f, textPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        cache[key] = drawable
        return drawable
    }

    fun clearCache() = cache.clear()
}
