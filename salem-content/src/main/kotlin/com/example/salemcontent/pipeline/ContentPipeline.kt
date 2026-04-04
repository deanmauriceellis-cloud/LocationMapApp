/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.pipeline

import com.example.salemcontent.mapper.CoordinateMapper
import com.example.salemcontent.model.*
import com.example.salemcontent.narration.NarrationGenerator
import com.example.salemcontent.reader.*
import com.google.gson.Gson
import java.io.File

/**
 * Orchestrates the full Salem content pipeline:
 * 1. Read all JSON sources from ~/Development/Salem
 * 2. Filter to tourist-relevant content
 * 3. Map coordinates (grid → GPS for village locations)
 * 4. Generate narration scripts
 * 5. Output as SQL insert statements
 */
class ContentPipeline(private val salemRoot: File) {

    private val gson = Gson()

    fun run(): PipelineOutput {
        println("=== Salem Content Pipeline ===")
        println("Source: ${salemRoot.absolutePath}")

        // 1. Read sources
        println("\n[1/5] Reading JSON sources...")
        val buildings = BuildingReader.read(salemRoot)
        println("  Buildings: ${buildings.size}")
        val npcs = NpcReader.read(salemRoot) // Tier 1+2 only
        println("  NPCs (Tier 1+2): ${npcs.size}")
        val facts = FactReader.read(salemRoot)
        println("  Facts (public/semi_private): ${facts.size}")
        val events = EventReader.read(salemRoot)
        println("  Events: ${events.size}")
        val sources = PrimarySourceReader.read(salemRoot)
        println("  Primary sources (top 200): ${sources.size}")
        val coords = CoordinateReader.read(salemRoot)
        println("  Building coordinates: ${coords.size}")

        // 2. Build coordinate lookup
        println("\n[2/5] Mapping coordinates...")
        val coordMap = coords.associateBy { it.id }

        // 3. Transform NPCs → HistoricalFigures
        println("\n[3/5] Transforming content...")
        val figures = npcs.map { npc -> transformNpc(npc, coordMap) }
        println("  Historical figures: ${figures.size}")

        // 4. Transform events → TimelineEvents
        val timelineEvents = events.map { transformEvent(it) }
        println("  Timeline events: ${timelineEvents.size}")

        // 5. Transform facts
        val outputFacts = facts.take(500).map { transformFact(it, npcs) }
        println("  Facts (capped at 500): ${outputFacts.size}")

        // 6. Transform primary sources
        val outputSources = sources.map { transformPrimarySource(it, npcs) }
        println("  Primary sources: ${outputSources.size}")

        // Note: Tour POIs, businesses, tours, tour stops, and events calendar
        // will be populated in Phase 5 (manually curated GPS-accurate data).
        // The pipeline generates placeholder structures for now.

        println("\n[4/5] Generating narration scripts...")
        // Narration is embedded in figure/source transforms above

        println("\n[5/5] Assembling output...")
        val output = PipelineOutput(
            tourPois = emptyList(),      // Phase 5: manually curated
            businesses = emptyList(),    // Phase 5: manually curated
            figures = figures,
            facts = outputFacts,
            timelineEvents = timelineEvents,
            primarySources = outputSources,
            tours = emptyList(),         // Phase 6: tour engine
            tourStops = emptyList(),     // Phase 6: tour engine
            events = emptyList()         // Phase 9: Haunted Happenings
        )

        // Validate
        val validation = ContentValidator.validate(output)
        println("\n=== Validation ===")
        println("Errors: ${validation.errors.size}")
        validation.errors.forEach { println("  ERROR: $it") }
        println("Warnings: ${validation.warnings.size}")
        validation.warnings.forEach { println("  WARN: $it") }

        return output
    }

    private fun transformNpc(npc: JsonNpc, coordMap: Map<String, BuildingCoordinate>): OutputFigure {
        val shortBio = buildShortBio(npc)
        val fullBio = buildFullBio(npc)

        // Try to find a primary POI from the NPC's home building
        val homeBuilding = npc.home?.buildingId
        val homePoi = homeBuilding?.let { coordMap[it] }

        return OutputFigure(
            id = npc.id,
            name = npc.name,
            firstName = npc.firstName ?: npc.name.substringBefore(" "),
            surname = npc.surname ?: npc.name.substringAfter(" ", ""),
            born = npc.bornYear,
            died = npc.diedYear,
            ageIn1692 = npc.age,
            role = mapNpcRole(npc),
            faction = npc.faction,
            shortBio = shortBio,
            fullBio = fullBio,
            narrationScript = NarrationGenerator.generateFigureLong(npc),
            appearanceDescription = npc.narrative?.appearance,
            roleInCrisis = npc.narrative?.roleInCrisis,
            historicalOutcome = npc.historicalOutcome,
            keyQuotes = npc.personality?.opinionOfCrisis?.let {
                gson.toJson(listOf(it))
            },
            familyConnections = npc.relationships?.let { rels ->
                gson.toJson(rels.associate { (it.targetName ?: "unknown") to (it.description ?: "") })
            },
            primaryPoiId = null // Will be linked in Phase 5
        )
    }

    private fun buildShortBio(npc: JsonNpc): String {
        return NarrationGenerator.generateFigureShort(npc)
    }

    private fun buildFullBio(npc: JsonNpc): String? {
        val parts = mutableListOf<String>()
        npc.narrative?.lifeBefore1692?.let { parts.add(NarrationGenerator.cleanForTts(it)) }
        npc.narrative?.roleInCrisis?.let { parts.add(NarrationGenerator.cleanForTts(it)) }
        npc.narrative?.emotionalArc?.let { parts.add(NarrationGenerator.cleanForTts(it)) }
        npc.historicalOutcome?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        return parts.joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    private fun mapNpcRole(npc: JsonNpc): String {
        val role = (npc.role ?: "other").lowercase()
        return when {
            "minister" in role || "reverend" in role -> "minister"
            "magistrate" in role || "judge" in role -> "magistrate"
            "accuser" in role || "afflicted" in role -> "accuser"
            "accused" in role -> "accused"
            "defender" in role -> "defender"
            "witness" in role -> "witness"
            else -> role
        }
    }

    private fun transformEvent(event: JsonEvent): OutputTimelineEvent {
        val figureIds = event.npcPlacements?.map { it.npcId }
        val phaseNames = mapOf(
            0 to "pre_crisis", 1 to "accusations", 2 to "examinations",
            3 to "trials", 4 to "executions", 5 to "aftermath"
        )
        return OutputTimelineEvent(
            id = event.id,
            name = event.name,
            date = event.gameDate,
            crisisPhase = event.crisisPhase?.let { phaseNames[it] ?: "phase_$it" },
            description = event.description,
            poiId = null, // Will be linked in Phase 5
            figuresInvolved = figureIds?.let { gson.toJson(it) },
            narrationScript = NarrationGenerator.cleanForTts(event.description),
            isAnchor = event.anchorNumber != null
        )
    }

    private fun transformFact(fact: JsonFact, npcs: List<JsonNpc>): OutputFact {
        val firstNpc = fact.npcsInvolved?.firstOrNull()
        return OutputFact(
            id = fact.id,
            title = fact.title,
            description = fact.description,
            date = fact.date,
            datePrecision = fact.datePrecision,
            category = fact.category,
            subcategory = fact.subcategory,
            poiId = null, // Will be linked in Phase 5
            figureId = firstNpc,
            sourceCitation = fact.source,
            narrationScript = NarrationGenerator.cleanForTts("${fact.title}. ${fact.description}"),
            confidentiality = fact.confidentiality ?: "public",
            tags = fact.tags?.let { gson.toJson(it) }
        )
    }

    private fun transformPrimarySource(source: JsonPrimarySource, npcs: List<JsonNpc>): OutputPrimarySource {
        val figureId = (source.npcsDirect ?: source.npcsAbout)?.firstOrNull()
        return OutputPrimarySource(
            id = source.id,
            title = source.title,
            sourceType = source.docType,
            author = source.attribution,
            date = source.date,
            fullText = source.verbatimText,
            excerpt = source.modernGloss ?: source.verbatimText?.take(500),
            figureId = figureId,
            poiId = null, // Will be linked in Phase 5
            narrationScript = NarrationGenerator.generateSourceNarration(source),
            citation = source.swpReference ?: source.sourceId
        )
    }

    /**
     * Write pipeline output as SQL INSERT statements.
     */
    fun writeSql(output: PipelineOutput, outFile: File) {
        println("\nWriting SQL to ${outFile.absolutePath}")
        outFile.bufferedWriter().use { w ->
            w.write("-- Salem Content Database — generated by ContentPipeline\n")
            w.write("-- Generated: ${java.time.LocalDateTime.now()}\n\n")

            // Historical figures
            w.write("-- Historical Figures (${output.figures.size})\n")
            for (f in output.figures) {
                w.write("""INSERT OR REPLACE INTO historical_figures (id, name, first_name, surname, born, died, age_in_1692, role, faction, short_bio, full_bio, narration_script, appearance_description, role_in_crisis, historical_outcome, key_quotes, family_connections, primary_poi_id) VALUES (${esc(f.id)}, ${esc(f.name)}, ${esc(f.firstName)}, ${esc(f.surname)}, ${esc(f.born)}, ${esc(f.died)}, ${f.ageIn1692 ?: "NULL"}, ${esc(f.role)}, ${esc(f.faction)}, ${esc(f.shortBio)}, ${esc(f.fullBio)}, ${esc(f.narrationScript)}, ${esc(f.appearanceDescription)}, ${esc(f.roleInCrisis)}, ${esc(f.historicalOutcome)}, ${esc(f.keyQuotes)}, ${esc(f.familyConnections)}, ${esc(f.primaryPoiId)});""")
                w.newLine()
            }

            // Timeline events
            w.write("\n-- Timeline Events (${output.timelineEvents.size})\n")
            for (e in output.timelineEvents) {
                w.write("""INSERT OR REPLACE INTO timeline_events (id, name, date, crisis_phase, description, poi_id, figures_involved, narration_script, is_anchor) VALUES (${esc(e.id)}, ${esc(e.name)}, ${esc(e.date)}, ${esc(e.crisisPhase)}, ${esc(e.description)}, ${esc(e.poiId)}, ${esc(e.figuresInvolved)}, ${esc(e.narrationScript)}, ${if (e.isAnchor) 1 else 0});""")
                w.newLine()
            }

            // Facts
            w.write("\n-- Historical Facts (${output.facts.size})\n")
            for (f in output.facts) {
                w.write("""INSERT OR REPLACE INTO historical_facts (id, title, description, date, date_precision, category, subcategory, poi_id, figure_id, source_citation, narration_script, confidentiality, tags) VALUES (${esc(f.id)}, ${esc(f.title)}, ${esc(f.description)}, ${esc(f.date)}, ${esc(f.datePrecision)}, ${esc(f.category)}, ${esc(f.subcategory)}, ${esc(f.poiId)}, ${esc(f.figureId)}, ${esc(f.sourceCitation)}, ${esc(f.narrationScript)}, ${esc(f.confidentiality)}, ${esc(f.tags)});""")
                w.newLine()
            }

            // Primary sources
            w.write("\n-- Primary Sources (${output.primarySources.size})\n")
            for (s in output.primarySources) {
                w.write("""INSERT OR REPLACE INTO primary_sources (id, title, source_type, author, date, full_text, excerpt, figure_id, poi_id, narration_script, citation) VALUES (${esc(s.id)}, ${esc(s.title)}, ${esc(s.sourceType)}, ${esc(s.author)}, ${esc(s.date)}, ${esc(s.fullText)}, ${esc(s.excerpt)}, ${esc(s.figureId)}, ${esc(s.poiId)}, ${esc(s.narrationScript)}, ${esc(s.citation)});""")
                w.newLine()
            }

            w.write("\n-- Tour POIs, businesses, tours, tour stops, events calendar\n")
            w.write("-- will be populated in Phase 5/6/9 with curated GPS data.\n")
        }
        println("SQL written: ${outFile.length() / 1024} KB")
    }

    private fun esc(value: String?): String {
        if (value == null) return "NULL"
        return "'" + value.replace("'", "''") + "'"
    }
}
