/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.narration

import com.example.salemcontent.model.JsonNpc
import com.example.salemcontent.model.JsonPrimarySource

/**
 * Converts historical descriptions to TTS-optimized narration scripts.
 *
 * Design principles:
 * - Shorter sentences (max ~20 words)
 * - Natural speech patterns, not academic prose
 * - Appropriate pauses (commas, periods)
 * - Pronunciation hints for 1692 names
 */
object NarrationGenerator {

    private const val SHORT_WORD_LIMIT = 100   // ~15-30 sec TTS
    private const val LONG_WORD_LIMIT = 400    // ~60-120 sec TTS

    /**
     * Generate a short narration (~50-100 words) from an NPC's data.
     * Suitable for TTS when approaching a POI.
     */
    fun generateFigureShort(npc: JsonNpc): String {
        val name = npc.name
        val role = npc.role ?: "resident"
        val age = npc.age?.let { ", age $it in 1692" } ?: ""
        val faction = npc.faction?.let { " Aligned with the ${cleanFaction(it)} faction." } ?: ""
        val bio = npc.narrative?.roleInCrisis
            ?: npc.narrative?.lifeBefore1692
            ?: npc.personality?.opinionOfCrisis
            ?: ""

        val intro = "$name. ${role.replaceFirstChar { it.uppercase() }}$age.$faction"
        val body = truncateToWords(cleanForTts(bio), SHORT_WORD_LIMIT - wordCount(intro))
        return "$intro $body".trim()
    }

    /**
     * Generate a long narration (~200-400 words) from an NPC's data.
     * Suitable for TTS when stopped at a POI.
     */
    fun generateFigureLong(npc: JsonNpc): String {
        val sections = mutableListOf<String>()

        // Opening
        val name = npc.name
        val role = npc.role ?: "resident"
        val born = npc.bornYear?.let { "Born $it." } ?: ""
        val died = npc.diedYear?.let { if (it != "?") "Died $it." else "" } ?: ""
        sections.add("$name. ${role.replaceFirstChar { it.uppercase() }}. $born $died".trim())

        // Life before
        npc.narrative?.lifeBefore1692?.let { sections.add(cleanForTts(it)) }

        // Role in crisis
        npc.narrative?.roleInCrisis?.let { sections.add(cleanForTts(it)) }

        // Historical outcome
        npc.historicalOutcome?.takeIf { it.isNotBlank() }?.let {
            sections.add(cleanForTts(it))
        }

        return truncateToWords(sections.joinToString(" "), LONG_WORD_LIMIT)
    }

    /**
     * Generate narration from a primary source excerpt.
     */
    fun generateSourceNarration(source: JsonPrimarySource): String {
        val intro = "From a ${source.docType.replace('_', ' ')}."
        val attribution = source.attribution?.let { " By $it." } ?: ""
        val date = source.date?.let { " Dated $it." } ?: ""
        val text = source.modernGloss
            ?: source.verbatimText?.let { truncateToWords(it, 150) }
            ?: ""
        return truncateToWords(
            "$intro$attribution$date ${cleanForTts(text)}".trim(),
            LONG_WORD_LIMIT
        )
    }

    /**
     * Clean text for TTS — remove markdown, academic formatting, etc.
     */
    fun cleanForTts(text: String): String {
        return text
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")     // remove bold markdown
            .replace(Regex("\\*(.+?)\\*"), "$1")            // remove italic markdown
            .replace(Regex("\\[(.+?)\\]"), "$1")            // remove bracket refs
            .replace(Regex("#+ "), "")                       // remove heading markers
            .replace(Regex("\\n+"), " ")                     // newlines to spaces
            .replace(Regex("\\s+"), " ")                     // collapse whitespace
            .trim()
    }

    private fun cleanFaction(faction: String): String {
        return faction
            .replace("Lean ", "")
            .replace("(through Parris household)", "")
            .trim()
    }

    private fun wordCount(text: String): Int = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size

    private fun truncateToWords(text: String, maxWords: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= maxWords) return text.trim()
        // Truncate at sentence boundary if possible
        val truncated = words.take(maxWords).joinToString(" ")
        val lastPeriod = truncated.lastIndexOf('.')
        return if (lastPeriod > truncated.length / 2) {
            truncated.substring(0, lastPeriod + 1)
        } else {
            "$truncated."
        }
    }
}
