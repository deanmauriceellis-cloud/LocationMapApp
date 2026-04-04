/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.pipeline

import com.example.salemcontent.data.SalemBusinesses
import com.example.salemcontent.data.SalemPois
import com.example.salemcontent.data.SalemTours
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

        // Phase 5: Load curated Salem POIs and businesses
        println("\n[4/7] Loading curated POIs and businesses...")
        val tourPois = SalemPois.all()
        println("  Tour POIs: ${tourPois.size}")
        val businesses = SalemBusinesses.all()
        println("  Businesses: ${businesses.size}")

        println("\n[5/7] Loading tour definitions...")
        val tours = SalemTours.allTours()
        println("  Tours: ${tours.size}")
        val tourStops = SalemTours.allStops()
        println("  Tour stops: ${tourStops.size}")

        println("\n[6/7] Generating narration scripts...")
        // Narration is embedded in figure/source transforms above
        // POI narrations are included in the curated data
        // Tour transition narrations are included in the tour stop data

        println("\n[7/7] Assembling output...")
        val output = PipelineOutput(
            tourPois = tourPois,
            businesses = businesses,
            figures = figures,
            facts = outputFacts,
            timelineEvents = timelineEvents,
            primarySources = outputSources,
            tours = tours,
            tourStops = tourStops,
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
                w.write("""INSERT OR REPLACE INTO historical_figures (id, name, first_name, surname, born, died, age_in_1692, role, faction, short_bio, full_bio, narration_script, appearance_description, role_in_crisis, historical_outcome, key_quotes, family_connections, primary_poi_id, data_source, confidence, verified_date, created_at, updated_at, stale_after) VALUES (${esc(f.id)}, ${esc(f.name)}, ${esc(f.firstName)}, ${esc(f.surname)}, ${esc(f.born)}, ${esc(f.died)}, ${f.ageIn1692 ?: "NULL"}, ${esc(f.role)}, ${esc(f.faction)}, ${esc(f.shortBio)}, ${esc(f.fullBio)}, ${esc(f.narrationScript)}, ${esc(f.appearanceDescription)}, ${esc(f.roleInCrisis)}, ${esc(f.historicalOutcome)}, ${esc(f.keyQuotes)}, ${esc(f.familyConnections)}, ${esc(f.primaryPoiId)}, ${esc(f.provenance.dataSource)}, ${f.provenance.confidence}, ${esc(f.provenance.verifiedDate)}, ${f.provenance.createdAt}, ${f.provenance.updatedAt}, ${f.provenance.staleAfter});""")
                w.newLine()
            }

            // Timeline events
            w.write("\n-- Timeline Events (${output.timelineEvents.size})\n")
            for (e in output.timelineEvents) {
                w.write("""INSERT OR REPLACE INTO timeline_events (id, name, date, crisis_phase, description, poi_id, figures_involved, narration_script, is_anchor, data_source, confidence, verified_date, created_at, updated_at, stale_after) VALUES (${esc(e.id)}, ${esc(e.name)}, ${esc(e.date)}, ${esc(e.crisisPhase)}, ${esc(e.description)}, ${esc(e.poiId)}, ${esc(e.figuresInvolved)}, ${esc(e.narrationScript)}, ${if (e.isAnchor) 1 else 0}, ${esc(e.provenance.dataSource)}, ${e.provenance.confidence}, ${esc(e.provenance.verifiedDate)}, ${e.provenance.createdAt}, ${e.provenance.updatedAt}, ${e.provenance.staleAfter});""")
                w.newLine()
            }

            // Facts
            w.write("\n-- Historical Facts (${output.facts.size})\n")
            for (f in output.facts) {
                w.write("""INSERT OR REPLACE INTO historical_facts (id, title, description, date, date_precision, category, subcategory, poi_id, figure_id, source_citation, narration_script, confidentiality, tags, data_source, confidence, verified_date, created_at, updated_at, stale_after) VALUES (${esc(f.id)}, ${esc(f.title)}, ${esc(f.description)}, ${esc(f.date)}, ${esc(f.datePrecision)}, ${esc(f.category)}, ${esc(f.subcategory)}, ${esc(f.poiId)}, ${esc(f.figureId)}, ${esc(f.sourceCitation)}, ${esc(f.narrationScript)}, ${esc(f.confidentiality)}, ${esc(f.tags)}, ${esc(f.provenance.dataSource)}, ${f.provenance.confidence}, ${esc(f.provenance.verifiedDate)}, ${f.provenance.createdAt}, ${f.provenance.updatedAt}, ${f.provenance.staleAfter});""")
                w.newLine()
            }

            // Primary sources
            w.write("\n-- Primary Sources (${output.primarySources.size})\n")
            for (s in output.primarySources) {
                w.write("""INSERT OR REPLACE INTO primary_sources (id, title, source_type, author, date, full_text, excerpt, figure_id, poi_id, narration_script, citation, data_source, confidence, verified_date, created_at, updated_at, stale_after) VALUES (${esc(s.id)}, ${esc(s.title)}, ${esc(s.sourceType)}, ${esc(s.author)}, ${esc(s.date)}, ${esc(s.fullText)}, ${esc(s.excerpt)}, ${esc(s.figureId)}, ${esc(s.poiId)}, ${esc(s.narrationScript)}, ${esc(s.citation)}, ${esc(s.provenance.dataSource)}, ${s.provenance.confidence}, ${esc(s.provenance.verifiedDate)}, ${s.provenance.createdAt}, ${s.provenance.updatedAt}, ${s.provenance.staleAfter});""")
                w.newLine()
            }

            // Tour POIs
            w.write("\n-- Tour POIs (${output.tourPois.size})\n")
            for (p in output.tourPois) {
                w.write("""INSERT OR REPLACE INTO tour_pois (id, name, lat, lng, address, category, subcategories, short_narration, long_narration, description, historical_period, admission_info, hours, phone, website, image_asset, geofence_radius_m, requires_transportation, wheelchair_accessible, seasonal, priority, data_source, confidence, verified_date, created_at, updated_at, stale_after) VALUES (${esc(p.id)}, ${esc(p.name)}, ${p.lat}, ${p.lng}, ${esc(p.address)}, ${esc(p.category)}, ${esc(p.subcategories)}, ${esc(p.shortNarration)}, ${esc(p.longNarration)}, ${esc(p.description)}, ${esc(p.historicalPeriod)}, ${esc(p.admissionInfo)}, ${esc(p.hours)}, ${esc(p.phone)}, ${esc(p.website)}, ${esc(p.imageAsset)}, ${p.geofenceRadiusM}, ${if (p.requiresTransportation) 1 else 0}, ${if (p.wheelchairAccessible) 1 else 0}, ${if (p.seasonal) 1 else 0}, ${p.priority}, ${esc(p.provenance.dataSource)}, ${p.provenance.confidence}, ${esc(p.provenance.verifiedDate)}, ${p.provenance.createdAt}, ${p.provenance.updatedAt}, ${p.provenance.staleAfter});""")
                w.newLine()
            }

            // Businesses
            w.write("\n-- Salem Businesses (${output.businesses.size})\n")
            for (b in output.businesses) {
                w.write("""INSERT OR REPLACE INTO salem_businesses (id, name, lat, lng, address, business_type, cuisine_type, price_range, hours, phone, website, description, historical_note, tags, rating, image_asset, data_source, confidence, verified_date, created_at, updated_at, stale_after) VALUES (${esc(b.id)}, ${esc(b.name)}, ${b.lat}, ${b.lng}, ${esc(b.address)}, ${esc(b.businessType)}, ${esc(b.cuisineType)}, ${esc(b.priceRange)}, ${esc(b.hours)}, ${esc(b.phone)}, ${esc(b.website)}, ${esc(b.description)}, ${esc(b.historicalNote)}, ${esc(b.tags)}, ${b.rating ?: "NULL"}, ${esc(b.imageAsset)}, ${esc(b.provenance.dataSource)}, ${b.provenance.confidence}, ${esc(b.provenance.verifiedDate)}, ${b.provenance.createdAt}, ${b.provenance.updatedAt}, ${b.provenance.staleAfter});""")
                w.newLine()
            }

            // Tours
            w.write("\n-- Tours (${output.tours.size})\n")
            for (t in output.tours) {
                w.write("""INSERT OR REPLACE INTO tours (id, name, theme, description, estimated_minutes, distance_km, stop_count, difficulty, seasonal, icon_asset, sort_order, data_source, confidence, verified_date, created_at, updated_at, stale_after) VALUES (${esc(t.id)}, ${esc(t.name)}, ${esc(t.theme)}, ${esc(t.description)}, ${t.estimatedMinutes}, ${t.distanceKm}, ${t.stopCount}, ${esc(t.difficulty)}, ${if (t.seasonal) 1 else 0}, ${esc(t.iconAsset)}, ${t.sortOrder}, ${esc(t.provenance.dataSource)}, ${t.provenance.confidence}, ${esc(t.provenance.verifiedDate)}, ${t.provenance.createdAt}, ${t.provenance.updatedAt}, ${t.provenance.staleAfter});""")
                w.newLine()
            }

            // Tour Stops
            w.write("\n-- Tour Stops (${output.tourStops.size})\n")
            for (s in output.tourStops) {
                w.write("""INSERT OR REPLACE INTO tour_stops (tour_id, poi_id, stop_order, transition_narration, walking_minutes_from_prev, distance_m_from_prev, data_source, confidence, created_at, updated_at, stale_after) VALUES (${esc(s.tourId)}, ${esc(s.poiId)}, ${s.stopOrder}, ${esc(s.transitionNarration)}, ${s.walkingMinutesFromPrev ?: "NULL"}, ${s.distanceMFromPrev ?: "NULL"}, ${esc(s.provenance.dataSource)}, ${s.provenance.confidence}, ${s.provenance.createdAt}, ${s.provenance.updatedAt}, ${s.provenance.staleAfter});""")
                w.newLine()
            }
        }
        println("SQL written: ${outFile.length() / 1024} KB")
    }

    private fun esc(value: String?): String {
        if (value == null) return "NULL"
        return "'" + value.replace("'", "''") + "'"
    }
}
