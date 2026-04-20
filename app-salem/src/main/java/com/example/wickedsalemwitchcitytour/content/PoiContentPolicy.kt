/*
 * WickedSalemWitchCityTour v1.5 — Session 154
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.content

import com.example.wickedsalemwitchcitytour.audio.AudioControl
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi

/**
 * PoiContentPolicy — PG-13 / legal-safety content gate introduced S154.
 *
 * Non-licensed commercial POIs (category in BUSINESSES group AND merchant_tier == 0)
 * must not expose any LLM-synthesized descriptive content. They render as
 * name + address + plain-text URL + phone + hours + category graphic only.
 * Historic / civic / parks / worship / public-entity POIs keep their curated
 * narration. Merchants buy in via `merchant_tier >= 1` to unlock full content.
 *
 * Render-time gate only — no DB deletion (operator direction S154: "no data
 * destruction yet"). SI-generated narrations remain in Room for merchant
 * unlocks and future cleanup passes.
 */
object PoiContentPolicy {

    /**
     * True if this POI should be stripped to name + address + category-graphic.
     * False if it keeps its full narration + historical_note + description.
     */
    fun shouldStripContent(poi: SalemPoi): Boolean =
        shouldStripByCategoryAndTier(poi.category, poi.merchantTier)

    /**
     * Lower-level strip test for projections that don't carry the full SalemPoi
     * (TourPoi, FindResult, etc). Safe for V1 where no merchant has bought in
     * yet — pass `merchantTier = 0` to get category-only stripping. Re-audit
     * call sites when merchant_tier is threaded through each projection.
     */
    fun shouldStripByCategoryAndTier(categoryRaw: String?, merchantTier: Int): Boolean =
        AudioControl.groupForCategory(categoryRaw) == AudioControl.Group.BUSINESSES &&
            merchantTier == 0

    /**
     * Category-only strip test. Equivalent to
     * [shouldStripByCategoryAndTier] with `merchantTier = 0`. For V1 where
     * no merchants are licensed, this is a safe approximation wherever the
     * caller doesn't have access to the `merchant_tier` column.
     */
    fun shouldStripByCategory(categoryRaw: String?): Boolean =
        shouldStripByCategoryAndTier(categoryRaw, 0)

    /** Short TTS line used when a stripped POI enters its geofence. */
    fun strippedAnnouncement(poi: SalemPoi): String {
        val name = poi.name
        val addr = poi.address?.takeIf { it.isNotBlank() }
        return if (addr != null) "You are near $name, at $addr." else "You are near $name."
    }
}
