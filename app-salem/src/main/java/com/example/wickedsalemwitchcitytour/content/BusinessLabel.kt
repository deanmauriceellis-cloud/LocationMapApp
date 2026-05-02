/*
 * WickedSalemWitchCityTour v1.5 — Session 217
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content

/**
 * BusinessLabel — turns a (category, subcategory) pair into a singular,
 * speakable English noun phrase used in stripped commercial-POI announcements.
 *
 * Decisions locked S217:
 *  - Curated map per (category, subcategory). When the subcategory's namespace
 *    repeats the category (TOUR_COMPANIES + TOUR_COMPANIES__walking_tours),
 *    the curated phrase ("walking tour company") absorbs both — never emit
 *    "walking tour tour company".
 *  - When subcategory is null/blank/unknown, use the category fallback noun.
 *  - "the" article: included by default, skipped when name starts with "The "
 *    or carries an apostrophe-s possessive (Red's, McDonald's).
 */
object BusinessLabel {

    /** Curated singular noun phrase per category when subcategory is null/unknown. */
    private val CATEGORY_NOUN: Map<String, String> = mapOf(
        "FOOD_DRINK"     to "restaurant",
        "SHOPPING"       to "shop",
        "LODGING"        to "lodging",
        "HEALTHCARE"     to "healthcare provider",
        "ENTERTAINMENT"  to "venue",
        "AUTO_SERVICES"  to "auto service",
        "OFFICES"        to "office",
        "TOUR_COMPANIES" to "tour company",
        "PSYCHIC"        to "psychic shop",
        "FINANCE"        to "financial business",
        "FUEL_CHARGING"  to "gas station",
        "TRANSIT"        to "transit stop",
        "PARKING"        to "parking lot",
        "EMERGENCY"      to "emergency facility",
        "WITCH_SHOP"     to "witch shop",
        "SERVICES"       to "service",
    )

    /** Curated singular noun phrase per fully-qualified subcategory. */
    private val SUBCATEGORY_NOUN: Map<String, String> = mapOf(
        // AUTO_SERVICES
        "AUTO_SERVICES__car_washes"    to "car wash",
        "AUTO_SERVICES__dealerships"   to "dealership",
        "AUTO_SERVICES__parts_stores"  to "auto parts store",
        "AUTO_SERVICES__repair_shops"  to "auto repair shop",
        "AUTO_SERVICES__tire_shops"    to "tire shop",

        // ENTERTAINMENT
        "ENTERTAINMENT__arts_centres"   to "arts centre",
        "ENTERTAINMENT__arts"           to "arts venue",
        "ENTERTAINMENT__event_venues"   to "event venue",
        "ENTERTAINMENT__fitness"        to "fitness venue",
        "ENTERTAINMENT__tour_operators" to "tour operator",

        // FOOD_DRINK
        "FOOD_DRINK__bars"        to "bar",
        "FOOD_DRINK__cafes"       to "cafe",
        "FOOD_DRINK__fast_food"   to "fast-food spot",
        "FOOD_DRINK__restaurants" to "restaurant",

        // HEALTHCARE
        "HEALTHCARE__clinics"    to "clinic",
        "HEALTHCARE__dentists"   to "dentist's office",
        "HEALTHCARE__doctors"    to "doctor's office",
        "HEALTHCARE__hospitals"  to "hospital",
        "HEALTHCARE__pharmacies" to "pharmacy",
        "HEALTHCARE__veterinary" to "veterinary clinic",

        // LODGING
        "LODGING__guest_houses" to "guest house",
        "LODGING__hotels"       to "hotel",

        // OFFICES
        "OFFICES__companies"    to "company office",
        "OFFICES__insurance"    to "insurance office",
        "OFFICES__law_offices"  to "law office",
        "OFFICES__real_estate"  to "real estate office",
        "OFFICES__tax_advisors" to "tax advisor's office",

        // SHOPPING
        "SHOPPING__antiques"            to "antique shop",
        "SHOPPING__beauty_spa"          to "beauty spa",
        "SHOPPING__bicycle_shops"       to "bicycle shop",
        "SHOPPING__bookstores"          to "bookstore",
        "SHOPPING__cannabis"            to "cannabis shop",
        "SHOPPING__clothing"            to "clothing store",
        "SHOPPING__convenience_stores"  to "convenience store",
        "SHOPPING__department_stores"   to "department store",
        "SHOPPING__electronics"         to "electronics store",
        "SHOPPING__florists"            to "florist",
        "SHOPPING__garden_centers"      to "garden center",
        "SHOPPING__gift_shops"          to "gift shop",
        "SHOPPING__hardware_stores"     to "hardware store",
        "SHOPPING__jewelry"             to "jewelry store",
        "SHOPPING__laundromats"         to "laundromat",
        "SHOPPING__malls"               to "mall",
        "SHOPPING__pet_stores"          to "pet store",
        "SHOPPING__phone_stores"        to "phone store",
        "SHOPPING__shoe_stores"         to "shoe store",
        "SHOPPING__storage_rentals"     to "storage rental",
        "SHOPPING__supermarkets"        to "supermarket",
        "SHOPPING__tattoo_shops"        to "tattoo shop",
        "SHOPPING__thrift_stores"       to "thrift store",
        "SHOPPING__tobacco_shops"       to "tobacco shop",
        "SHOPPING__vape_shops"          to "vape shop",

        // TOUR_COMPANIES — subcategory absorbs category ("walking tour company"
        // not "walking tour tour company").
        "TOUR_COMPANIES__ghost_tours"   to "ghost tour company",
        "TOUR_COMPANIES__walking_tours" to "walking tour company",

        // WITCH_SHOP
        "WITCH_SHOP__crystal_shops"     to "crystal shop",
        "WITCH_SHOP__herb_shops"        to "herb shop",
        "WITCH_SHOP__witchcraft_shops"  to "witchcraft shop",
    )

    /**
     * Build the spoken sentence for a stripped commercial POI.
     * Returns "You are near [the ]Name, a phrase." with a phrase chosen from
     * the (category, subcategory) curated map.
     *
     * Falls back gracefully when category is null or unknown:
     *   - Unknown category + null subcategory → "You are near [the ]Name, a business."
     *   - Unknown subcategory → use category noun (or "business" if category also unknown)
     */
    fun strippedSentence(name: String, category: String?, subcategory: String?): String {
        val phrase = noun(category, subcategory)
        val article = articleFor(name)
        val finalName = name.trim()
        return "You are near $article$finalName, a $phrase."
    }

    /** Resolve the noun phrase. Public for testing / future call sites. */
    fun noun(category: String?, subcategory: String?): String {
        val sub = subcategory?.trim()?.takeIf { it.isNotEmpty() }
        if (sub != null) {
            SUBCATEGORY_NOUN[sub]?.let { return it }
            // Defensive fallback: humanize the leaf token if the curated map
            // doesn't list it. e.g. UNKNOWN_CATEGORY__cool_things → "cool thing".
            humanizeLeaf(sub)?.let { return it }
        }
        val cat = category?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        if (cat != null) {
            CATEGORY_NOUN[cat]?.let { return it }
        }
        return "business"
    }

    /** Returns "the " or "" depending on whether the name should be articled. */
    private fun articleFor(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return ""
        // Already starts with "The " — don't double-article.
        if (trimmed.length >= 4 && trimmed.substring(0, 4).equals("The ", ignoreCase = true)) {
            return ""
        }
        // Possessive name (Red's, McDonald's, Murphy's) — "the McDonald's" sounds wrong.
        if (containsPossessive(trimmed)) return ""
        return "the "
    }

    /** True when the name carries an English possessive 's — e.g. "Red's" or "Murphy's Pub". */
    private fun containsPossessive(s: String): Boolean {
        // Trailing 's
        if (s.endsWith("'s") || s.endsWith("’s")) return true
        // 's followed by space (mid-name possessive)
        return s.contains("'s ") || s.contains("’s ")
    }

    /**
     * Last-ditch humanization for an unmapped subcategory: strip the
     * "CAT__" namespace prefix, replace underscores with spaces, lowercase,
     * and singularize the last token via simple rules.
     */
    private fun humanizeLeaf(subcategory: String): String? {
        val leaf = subcategory.substringAfter("__", subcategory)
        if (leaf.isBlank()) return null
        val tokens = leaf.lowercase().split('_').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        val last = tokens.last()
        val singularLast = singularize(last)
        return (tokens.dropLast(1) + singularLast).joinToString(" ")
    }

    private fun singularize(word: String): String = when {
        word.endsWith("ies") && word.length > 3 -> word.dropLast(3) + "y"
        word.endsWith("ses") && word.length > 3 -> word.dropLast(2)
        word.endsWith("ches") && word.length > 4 -> word.dropLast(2)
        word.endsWith("shes") && word.length > 4 -> word.dropLast(2)
        word.endsWith("s") && !word.endsWith("ss") && word.length > 1 -> word.dropLast(1)
        else -> word
    }
}
