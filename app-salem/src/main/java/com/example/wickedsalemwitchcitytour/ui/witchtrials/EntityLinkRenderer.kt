/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X.7 (Session 133)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Cross-linking renderer for Witch Trials body text. Scans for known NPC
 * names and explicit [[npc:id]] / [[newspaper:YYYY-MM-DD]] markup, converts
 * them to tappable ClickableSpans that navigate to the appropriate detail
 * dialog.
 */

package com.example.wickedsalemwitchcitytour.ui.witchtrials

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNpcBio

private const val LINK_COLOR = "#C9A84C" // SALEM_GOLD

/**
 * Identifies an entity link target. The UI layer maps these to navigation
 * actions (open bio dialog, open newspaper detail, etc.).
 */
sealed class EntityLink {
    data class Npc(val id: String, val bio: WitchTrialsNpcBio) : EntityLink()
    data class NewspaperDate(val date: String) : EntityLink()
}

/**
 * Builds a SpannableStringBuilder from [text] with tappable entity links.
 *
 * Two linking modes:
 * 1. **Explicit markup:** `[[npc:id]]` → resolves id against [bioIndex],
 *    displays the NPC's display name. `[[newspaper:YYYY-MM-DD]]` → displays
 *    the date.
 * 2. **Auto-detection:** scans for known NPC names (longest-first greedy).
 *    The NPC whose bio is currently displayed ([excludeNpcId]) is excluded
 *    to avoid self-linking.
 *
 * @param text           The raw body text (may contain `## Headers` etc.)
 * @param bioIndex       Map of NPC id → bio, used for name resolution.
 * @param nameIndex      Map of NPC display name → bio, longest-first sorted.
 * @param excludeNpcId   Optional NPC id to exclude (the currently viewed bio).
 * @param onEntityTap    Callback when a link is tapped.
 */
fun renderLinkedText(
    text: String,
    bioIndex: Map<String, WitchTrialsNpcBio>,
    nameIndex: List<Pair<String, WitchTrialsNpcBio>>,
    excludeNpcId: String? = null,
    onEntityTap: (EntityLink) -> Unit
): SpannableStringBuilder {
    // Phase 1: resolve explicit [[markup]] → replace with display text + record spans
    val (cleaned, explicitSpans) = resolveMarkup(text, bioIndex, excludeNpcId, onEntityTap)

    // Phase 2: auto-detect NPC names in the cleaned text
    val autoSpans = detectNpcNames(cleaned, nameIndex, excludeNpcId, explicitSpans, onEntityTap)

    // Build the spannable
    val ssb = SpannableStringBuilder(cleaned)
    for ((start, end, span) in explicitSpans + autoSpans) {
        ssb.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    return ssb
}

/**
 * Build the two indexes needed by [renderLinkedText] from a list of bios.
 * Call once when the data is loaded, cache in the ViewModel.
 */
fun buildLinkIndexes(bios: List<WitchTrialsNpcBio>): Pair<Map<String, WitchTrialsNpcBio>, List<Pair<String, WitchTrialsNpcBio>>> {
    val bioIndex = bios.associateBy { it.id }
    // Build name → bio pairs. Use displayName if set, else name.
    // Also add shortened forms (e.g. "Rev. Samuel Parris" → "Samuel Parris").
    val namePairs = mutableListOf<Pair<String, WitchTrialsNpcBio>>()
    for (bio in bios) {
        val primary = bio.displayName ?: bio.name
        namePairs.add(primary to bio)
        // Add shortened form without title prefix
        val stripped = stripTitle(primary)
        if (stripped != primary && stripped.length >= 8) {
            namePairs.add(stripped to bio)
        }
        // If displayName differs from name, also index the name
        if (bio.displayName != null && bio.name != bio.displayName) {
            namePairs.add(bio.name to bio)
            val strippedName = stripTitle(bio.name)
            if (strippedName != bio.name && strippedName.length >= 8) {
                namePairs.add(strippedName to bio)
            }
        }
    }
    // Sort longest-first so greedy matching prefers "Ann Putnam Jr." over "Ann Putnam"
    val sorted = namePairs.sortedByDescending { it.first.length }
    return bioIndex to sorted
}

// ── Internal helpers ──────────────────────────────────────────────────

private data class PendingSpan(val start: Int, val end: Int, val span: ClickableSpan)

/** Strip common title prefixes: "Rev. ", "Dr. ", "Gov. ", etc. */
private fun stripTitle(name: String): String {
    val prefixes = listOf(
        "Rev. ", "Dr. ", "Gov. ", "Governor ", "Chief Justice ",
        "Magistrate ", "Constable ", "Captain ", "Capt. ", "Judge "
    )
    for (p in prefixes) {
        if (name.startsWith(p)) return name.removePrefix(p)
    }
    return name
}

/**
 * Resolve `[[npc:id]]` and `[[newspaper:YYYY-MM-DD]]` markup.
 * Returns the cleaned text (markup replaced with display text) and pending spans.
 */
private fun resolveMarkup(
    text: String,
    bioIndex: Map<String, WitchTrialsNpcBio>,
    excludeNpcId: String?,
    onEntityTap: (EntityLink) -> Unit
): Pair<String, List<PendingSpan>> {
    val pattern = Regex("""\[\[(npc|newspaper|date|event):([^\]]+)\]\]""")
    val spans = mutableListOf<PendingSpan>()
    val sb = StringBuilder()
    var lastEnd = 0

    for (match in pattern.findAll(text)) {
        sb.append(text, lastEnd, match.range.first)
        val kind = match.groupValues[1]
        val value = match.groupValues[2]
        val insertStart = sb.length

        when (kind) {
            "npc" -> {
                val bio = bioIndex[value]
                if (bio != null && bio.id != excludeNpcId) {
                    val displayName = bio.displayName ?: bio.name
                    sb.append(displayName)
                    spans.add(PendingSpan(insertStart, sb.length,
                        entityClickableSpan { onEntityTap(EntityLink.Npc(bio.id, bio)) }))
                } else {
                    // Unknown NPC or self-link → insert raw id as plain text
                    val fallback = bio?.let { it.displayName ?: it.name } ?: value
                    sb.append(fallback)
                }
            }
            "newspaper", "date" -> {
                sb.append(value)
                spans.add(PendingSpan(insertStart, sb.length,
                    entityClickableSpan { onEntityTap(EntityLink.NewspaperDate(value)) }))
            }
            else -> {
                // event: or unknown — render the value as plain text for now
                sb.append(value)
            }
        }
        lastEnd = match.range.last + 1
    }
    sb.append(text, lastEnd, text.length)
    return sb.toString() to spans
}

/**
 * Auto-detect NPC names in [text]. Greedy longest-first matching.
 * Skips regions already covered by [existingSpans] (from explicit markup).
 */
private fun detectNpcNames(
    text: String,
    nameIndex: List<Pair<String, WitchTrialsNpcBio>>,
    excludeNpcId: String?,
    existingSpans: List<PendingSpan>,
    onEntityTap: (EntityLink) -> Unit
): List<PendingSpan> {
    val spans = mutableListOf<PendingSpan>()
    // Track covered regions to avoid overlapping spans
    val covered = mutableListOf<IntRange>()
    for (es in existingSpans) covered.add(es.start until es.end)

    for ((name, bio) in nameIndex) {
        if (bio.id == excludeNpcId) continue
        var searchFrom = 0
        while (searchFrom < text.length) {
            val idx = text.indexOf(name, searchFrom, ignoreCase = false)
            if (idx < 0) break
            val end = idx + name.length

            // Check word boundaries — the char before and after must not be a letter
            val validStart = idx == 0 || !text[idx - 1].isLetter()
            val validEnd = end >= text.length || !text[end].isLetter()

            if (validStart && validEnd && !isOverlapping(idx, end, covered)) {
                spans.add(PendingSpan(idx, end,
                    entityClickableSpan { onEntityTap(EntityLink.Npc(bio.id, bio)) }))
                covered.add(idx until end)
            }
            searchFrom = end
        }
    }
    return spans
}

private fun isOverlapping(start: Int, end: Int, covered: List<IntRange>): Boolean {
    return covered.any { range -> start < range.last + 1 && end > range.first }
}

private fun entityClickableSpan(onClick: () -> Unit): ClickableSpan {
    return object : ClickableSpan() {
        override fun onClick(widget: View) { onClick() }
        override fun updateDrawState(ds: TextPaint) {
            ds.color = Color.parseColor(LINK_COLOR)
            ds.isUnderlineText = true
        }
    }
}
