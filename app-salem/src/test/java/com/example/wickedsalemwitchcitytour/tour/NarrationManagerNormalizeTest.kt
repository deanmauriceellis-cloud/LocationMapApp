/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * S213 — unit tests for NarrationManager.normalizeForTts(). Verifies the
 * markdown-strip pass and the abbreviation-expansion heuristics against
 * real Salem narration patterns.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import com.example.wickedsalemwitchcitytour.content.SalemContentRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

class NarrationManagerNormalizeTest {

    private lateinit var manager: NarrationManager

    @Before
    fun setUp() {
        // Constructor takes Context + repository; neither is touched by
        // normalizeForTts, so plain mocks are sufficient.
        manager = NarrationManager(
            context = mock(Context::class.java),
            repository = mock(SalemContentRepository::class.java)
        )
    }

    private fun n(s: String) = manager.normalizeForTts(s)

    // ── Markdown stripping ─────────────────────────────────────────────────

    @Test fun stripsItalicBookTitle() {
        assertEquals(
            "Hawthorne wrote The Scarlet Letter in 1850.",
            n("Hawthorne wrote *The Scarlet Letter* in 1850.")
        )
    }

    @Test fun stripsBoldEmphasis() {
        assertEquals(
            "The trials were a dark chapter.",
            n("The trials were a **dark chapter**.")
        )
    }

    @Test fun stripsItalicForeignWord() {
        assertEquals("Japanese ningyô dolls", n("Japanese *ningyô* dolls"))
    }

    @Test fun preservesLiteralAsteriskInMath() {
        // Single asterisk surrounded by digits is not emphasis (negative lookbehind).
        assertEquals("3 * 4 = 12", n("3 * 4 = 12"))
    }

    @Test fun stripsItalicAcrossPossessive() {
        assertEquals(
            "from raison d’être to reality",
            n("from *raison d’être* to reality")
        )
    }

    // ── Title expansion (drop period) ──────────────────────────────────────

    @Test fun expandsCaptainTitle() {
        assertEquals(
            "Captain John Smith arrived in 1626.",
            n("Capt. John Smith arrived in 1626.")
        )
    }

    @Test fun expandsReverendTitle() {
        assertEquals("Reverend Samuel Parris", n("Rev. Samuel Parris"))
    }

    @Test fun expandsDeaconTitle() {
        assertEquals("Deacon Joseph Hawthorne", n("Dea. Joseph Hawthorne"))
    }

    @Test fun expandsMisterTitle() {
        assertEquals("Mister Hawthorne", n("Mr. Hawthorne"))
    }

    @Test fun expandsSergeantAndMajor() {
        assertEquals(
            "Sergeant Cole and Major Stoughton",
            n("Sgt. Cole and Maj. Stoughton")
        )
    }

    // ── State expansion (sentence-end aware) ───────────────────────────────

    @Test fun expandsStateAtEndOfText() {
        assertEquals(
            "Salem, Massachusetts.",
            n("Salem, Mass.")
        )
    }

    @Test fun expandsStateBeforeNextSentence() {
        assertEquals(
            "He lived in Massachusetts. Then he died.",
            n("He lived in Mass. Then he died.")
        )
    }

    @Test fun expandsStateMidSentence() {
        assertEquals(
            "Massachusetts courts ruled in favor.",
            n("Mass. courts ruled in favor.")
        )
    }

    // ── St. — saint vs street disambiguation ───────────────────────────────

    @Test fun stPlusSaintNameBecomesSaint() {
        assertEquals(
            "Saint Peter's Church",
            n("St. Peter's Church")
        )
    }

    @Test fun stPlusJoseph() {
        assertEquals("Saint Joseph Hall", n("St. Joseph Hall"))
    }

    @Test fun streetSuffixBecomesStreet() {
        assertEquals("Hardy Street is the oldest", n("Hardy St. is the oldest"))
    }

    @Test fun streetSuffixAtSentenceEnd() {
        assertEquals(
            "Built on Derby Street. Local establishments line the wharf.",
            n("Built on Derby St. Local establishments line the wharf.")
        )
    }

    @Test fun burySaintEdmunds() {
        assertEquals(
            "The Bury Saint Edmunds Witch Trial",
            n("The Bury St. Edmunds Witch Trial")
        )
    }

    // ── Postnominal Jr./Sr. ────────────────────────────────────────────────

    @Test fun juniorMidSentence() {
        // "Jr." is mid-sentence (House is a noun, not a sentence-starter pronoun);
        // "St." is at end of input → period preserved.
        assertEquals(
            "Joseph Junior House stands on Essex Street.",
            n("Joseph Jr. House stands on Essex St.")
        )
    }

    @Test fun juniorAtSentenceEnd() {
        assertEquals("his son Joseph Junior.", n("his son Joseph Jr."))
    }

    @Test fun juniorBeforePronoun() {
        assertEquals(
            "Ann Putnam Junior. He testified later.",
            n("Ann Putnam Jr. He testified later.")
        )
    }

    @Test fun juniorBeforeComma() {
        assertEquals(
            "Joseph Junior, a famous merchant",
            n("Joseph Jr., a famous merchant")
        )
    }

    // ── Streets, companies, era ────────────────────────────────────────────

    @Test fun avenueExpansion() {
        // Period preserved at end of input — sentence terminator survives.
        assertEquals("Lafayette Avenue.", n("Lafayette Ave."))
    }

    @Test fun avenueExpansionMidSentence() {
        assertEquals(
            "Lafayette Avenue runs north",
            n("Lafayette Ave. runs north")
        )
    }

    @Test fun roadExpansion() {
        assertEquals("Highland Road and other routes", n("Highland Rd. and other routes"))
    }

    @Test fun mountExpansion() {
        // Mt. precedes a proper noun (drop period); St. is at end of input
        // (preserve period as sentence terminator).
        assertEquals("Mount Vernon Street.", n("Mt. Vernon St."))
    }

    @Test fun brothersExpansion() {
        assertEquals("Smith Brothers.", n("Smith Bros."))
    }

    @Test fun incorporatedExpansion() {
        assertEquals("Acme Incorporated.", n("Acme Inc."))
    }

    @Test fun bcMarker() {
        assertEquals("around 600 BC.", n("around 600 B.C."))
    }

    @Test fun circaWithYear() {
        assertEquals("constructed circa 1675", n("constructed ca. 1675"))
    }

    @Test fun etcetera() {
        assertEquals(
            "ships, cargo, etcetera, were tracked",
            n("ships, cargo, etc., were tracked")
        )
    }

    // ── Combined: a representative Salem sentence ──────────────────────────

    @Test fun realSalemSentence() {
        val input = "Capt. John Smith and Rev. Samuel Parris built a meeting house on Essex St. in Salem, Mass. *The Scarlet Letter* would later memorialize this place."
        val expected = "Captain John Smith and Reverend Samuel Parris built a meeting house on Essex Street in Salem, Massachusetts. The Scarlet Letter would later memorialize this place."
        assertEquals(expected, n(input))
    }
}
