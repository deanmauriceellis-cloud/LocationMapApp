/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import org.json.JSONArray

/**
 * Subtopic — one storytelling card inside the narration_subtopics JSONB column.
 * Schema is `[{header, body, source_kind?, source_ref?}]`. `source_kind` and
 * `source_ref` (S221) carry provenance for auto-gen cards: e.g. an
 * adjacency card has `source_kind="adjacent_poi"` and `source_ref` set to the
 * linked POI's id, which surfaces as a tappable "Open <linked POI>" button on
 * the card body.
 */
data class Subtopic(
    val header: String,
    val body: String,
    val sourceKind: String? = null,
    val sourceRef: String? = null,
)

/**
 * Tolerant parser. Bad JSON or missing fields → empty list, never throws.
 */
fun parseSubtopics(json: String?, logTag: String = "SubtopicRenderer", logId: String = ""): List<Subtopic> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val header = o.optString("header", "").trim()
            val body = o.optString("body", "").trim()
            if (header.isEmpty() || body.isEmpty()) return@mapNotNull null
            val sourceKind = o.optString("source_kind", "").trim().takeIf { it.isNotEmpty() }
            val sourceRef = o.optString("source_ref", "").trim().takeIf { it.isNotEmpty() }
            Subtopic(header, body, sourceKind, sourceRef)
        }
    }.getOrElse {
        DebugLogger.i(logTag, "subtopics parse failed id=$logId: ${it.message}")
        emptyList()
    }
}

/**
 * Render `items` into the supplied label / chip strip / body card stack.
 * Hides the whole block if `items` is empty.
 *
 * Tap behavior:
 *  - Tap chip → toggles its body card (and smooth-scrolls the strip so the
 *    chip is in view).
 *  - Tap card header row → toggles its body card (no strip scroll).
 *  - Tap body → invokes `onSpeakBody(text)`.
 *
 * Caller supplies all five view references and the speak handler so this
 * util can be reused on the POI Detail Sheet, the narration banner, and any
 * future surface without duplicating the wiring.
 */
fun renderSubtopics(
    label: TextView?,
    chipScroll: View?,
    chipRow: LinearLayout?,
    bodyStack: LinearLayout?,
    divider: View?,
    items: List<Subtopic>,
    onSpeakBody: (Subtopic) -> Unit,
    onOpenLinkedPoi: ((poiId: String) -> Unit)? = null,
    resolveLinkedPoiName: ((poiId: String) -> String?)? = null,
    logTag: String = "SubtopicRenderer",
    logId: String = "",
) {
    if (label == null || chipScroll == null || chipRow == null || bodyStack == null || divider == null) {
        return
    }
    if (items.isEmpty()) {
        label.visibility = View.GONE
        chipScroll.visibility = View.GONE
        bodyStack.visibility = View.GONE
        divider.visibility = View.GONE
        chipRow.removeAllViews()
        bodyStack.removeAllViews()
        return
    }

    label.visibility = View.VISIBLE
    chipScroll.visibility = View.VISIBLE
    bodyStack.visibility = View.VISIBLE
    divider.visibility = View.VISIBLE
    chipRow.removeAllViews()
    bodyStack.removeAllViews()

    val inflater = LayoutInflater.from(chipRow.context)
    val chips = mutableListOf<TextView>()
    val cards = mutableListOf<View>()

    items.forEach { sub ->
        val chip = inflater.inflate(R.layout.subtopic_chip, chipRow, false) as TextView
        chip.text = sub.header
        chipRow.addView(chip)
        chips += chip

        val card = inflater.inflate(R.layout.subtopic_body_card, bodyStack, false)
        card.findViewById<TextView>(R.id.subtopicCardHeader).text = sub.header
        card.findViewById<TextView>(R.id.subtopicCardBody).text = sub.body
        bodyStack.addView(card)
        cards += card
    }

    fun toggle(index: Int, expand: Boolean) {
        chips[index].isSelected = expand
        val card = cards[index]
        val body = card.findViewById<TextView>(R.id.subtopicCardBody)
        val chevron = card.findViewById<TextView>(R.id.subtopicCardChevron)
        val openLink = card.findViewById<TextView>(R.id.subtopicCardOpenLink)
        body.visibility = if (expand) View.VISIBLE else View.GONE
        chevron.text = if (expand) "▴" else "▾"
        // The Open-linked-POI link is conceptually part of the expanded card;
        // hide it when collapsed and only show it when the card is expanded
        // AND it's a wired adjacent_poi card (text != "").
        openLink.visibility = if (expand && openLink.text.isNotBlank()) View.VISIBLE else View.GONE
    }

    items.forEachIndexed { index, sub ->
        val chip = chips[index]
        val card = cards[index]
        val headerRow = card.findViewById<View>(R.id.subtopicCardHeaderRow)
        val body = card.findViewById<TextView>(R.id.subtopicCardBody)
        val openLink = card.findViewById<TextView>(R.id.subtopicCardOpenLink)

        // S221 — adjacent_poi cards get a tappable "Open <linked POI>" link
        // under the body. Only rendered when caller supplied an
        // onOpenLinkedPoi handler AND the card carries source_kind +
        // source_ref + the linked POI is resolvable.
        val openHandler = onOpenLinkedPoi
        val linkedPoiId = sub.sourceRef?.takeIf {
            sub.sourceKind == "adjacent_poi" && openHandler != null
        }
        if (linkedPoiId != null && openHandler != null) {
            val resolvedName = resolveLinkedPoiName?.invoke(linkedPoiId)
            val linkLabel = if (!resolvedName.isNullOrBlank()) "▸ Open $resolvedName" else "▸ Open linked property"
            openLink.text = linkLabel
            openLink.setOnClickListener {
                DebugLogger.i(logTag, "tap-open-linked subtopic#$index id=$logId target=$linkedPoiId")
                openHandler.invoke(linkedPoiId)
            }
        } else {
            openLink.text = ""
            openLink.setOnClickListener(null)
        }
        // Visibility is governed by toggle() — link only appears when the
        // card is expanded AND has a non-blank label.
        openLink.visibility = View.GONE

        chip.setOnClickListener {
            val nowExpanded = chip.isSelected.not()
            toggle(index, nowExpanded)
            if (nowExpanded) {
                DebugLogger.i(logTag, "tap-chip subtopic#$index id=$logId header=${sub.header}")
                chipScroll.post {
                    val left = chip.left - 32
                    (chipScroll as? HorizontalScrollView)?.smoothScrollTo(left.coerceAtLeast(0), 0)
                }
            }
        }
        headerRow.setOnClickListener {
            val nowExpanded = chip.isSelected.not()
            toggle(index, nowExpanded)
        }
        body.setOnClickListener {
            DebugLogger.i(logTag, "tap-to-speak subtopic#$index id=$logId header=${sub.header}")
            onSpeakBody(sub)
        }
    }
}

/**
 * Hide the whole block — chip strip, body stack, label, divider — and clear
 * any inflated children. Used on screens that share the narration banner
 * with non-POI content (e.g. the witch-trials newspaper sheet).
 */
fun hideSubtopics(
    label: TextView?,
    chipScroll: View?,
    chipRow: LinearLayout?,
    bodyStack: LinearLayout?,
    divider: View?,
) {
    label?.visibility = View.GONE
    chipScroll?.visibility = View.GONE
    bodyStack?.visibility = View.GONE
    divider?.visibility = View.GONE
    chipRow?.removeAllViews()
    bodyStack?.removeAllViews()
}
