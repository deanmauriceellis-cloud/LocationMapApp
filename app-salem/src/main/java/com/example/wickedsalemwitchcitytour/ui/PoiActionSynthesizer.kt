/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import com.example.wickedsalemwitchcitytour.content.model.NarrationPoint
import org.json.JSONArray
import org.json.JSONException

/**
 * Builds the "Things You Can Do" action list for a POI displayed in
 * [PoiDetailSheet] (Session 113).
 *
 * Resolution order:
 *
 *   1. **[NarrationPoint.actionButtons] JSON override** — if populated, parse
 *      and return the explicit action list. This is the admin tool hook for
 *      custom buttons (book tour, Instagram, menu, etc.) that land in a
 *      future session. As of Session 113 this field is populated on **0 of
 *      817** NarrationPoints, so the override path is effectively dormant.
 *   2. **Render-time synthesis** — builds actions from fields that already
 *      exist on the POI: `website` → Visit Website, `phone` → Call,
 *      `lat/lng` → Directions, `hours` → Hours chip. No schema migration.
 *
 * The override schema (when populated in the future) is an array of objects:
 * ```
 * [
 *   { "type": "visit",      "label": "Visit Website", "url":  "https://..." },
 *   { "type": "call",       "label": "Call",          "phone": "978-..."    },
 *   { "type": "directions", "label": "Directions",    "lat": 42.5, "lng": -70.8 },
 *   { "type": "hours",      "label": "Hours",         "text": "Mon-Fri 9-5" }
 * ]
 * ```
 * Unknown `type` values render as disabled chips with the provided label.
 */
object PoiActionSynthesizer {

    sealed class Action {
        /** Open URL in the in-app full-screen WebView. */
        data class VisitWebsite(val url: String, val label: String = "Visit Website") : Action()
        /** Dial a phone number via ACTION_DIAL intent. */
        data class Call(val phone: String, val label: String = "Call") : Action()
        /** Launch Google Maps / default maps app to a lat/lng. */
        data class Directions(val lat: Double, val lng: Double, val label: String = "Directions") : Action()
        /** Display operating hours inline (non-interactive). */
        data class Hours(val hours: String, val label: String = "Hours") : Action()
        /** Unknown admin action type — displayed as a disabled chip. */
        data class Unknown(val label: String) : Action()
    }

    fun buildActions(poi: NarrationPoint): List<Action> {
        parseOverride(poi.actionButtons)?.let { return it }
        return buildList {
            poi.website?.takeIf { it.isNotBlank() }?.let { add(Action.VisitWebsite(it)) }
            poi.phone?.takeIf { it.isNotBlank() }?.let { add(Action.Call(it)) }
            add(Action.Directions(poi.lat, poi.lng))
            poi.hours?.takeIf { it.isNotBlank() }?.let { add(Action.Hours(it)) }
        }
    }

    private fun parseOverride(json: String?): List<Action>? {
        if (json.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(json)
            if (arr.length() == 0) return null
            val list = buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val type = obj.optString("type")
                    val label = obj.optString("label").ifBlank {
                        type.ifBlank { "More" }
                    }
                    val action: Action = when (type) {
                        "visit", "website", "url" -> {
                            val url = obj.optString("url")
                            if (url.isNotBlank()) Action.VisitWebsite(url, label) else Action.Unknown(label)
                        }
                        "call", "tel", "phone" -> {
                            val phone = obj.optString("phone").ifBlank { obj.optString("value") }
                            if (phone.isNotBlank()) Action.Call(phone, label) else Action.Unknown(label)
                        }
                        "directions", "navigate" -> {
                            val lat = obj.optDouble("lat", Double.NaN)
                            val lng = obj.optDouble("lng", Double.NaN)
                            if (!lat.isNaN() && !lng.isNaN()) {
                                Action.Directions(lat, lng, label)
                            } else Action.Unknown(label)
                        }
                        "hours" -> {
                            val text = obj.optString("text").ifBlank { obj.optString("hours") }
                            if (text.isNotBlank()) Action.Hours(text, label) else Action.Unknown(label)
                        }
                        else -> Action.Unknown(label)
                    }
                    add(action)
                }
            }
            list.takeIf { it.isNotEmpty() }
        } catch (_: JSONException) {
            null  // malformed admin override → fall through to synthesis
        }
    }
}
