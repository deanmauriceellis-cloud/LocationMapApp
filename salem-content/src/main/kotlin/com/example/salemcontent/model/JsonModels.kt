/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Gson deserialization models matching ~/Development/Salem JSON schemas.
 */

package com.example.salemcontent.model

import com.google.gson.annotations.SerializedName

// ── Buildings ────────────────────────────────────────────────────────────

data class JsonBuilding(
    val id: String,
    val name: String,
    val type: String,
    val zone: String,
    @SerializedName("scene_type") val sceneType: String? = null,
    val rooms: List<JsonRoom>? = null,
    val atmosphere: JsonAtmosphere? = null
)

data class JsonRoom(
    val id: String,
    val name: String,
    val description: String? = null,
    val furnishings: List<String>? = null,
    val lighting: String? = null
)

data class JsonAtmosphere(
    val lighting: String? = null,
    val sounds: List<String>? = null,
    val mood: String? = null,
    val smells: List<String>? = null,
    val temperature: String? = null
)

// ── NPCs ─────────────────────────────────────────────────────────────────

data class JsonNpc(
    val id: String,
    val name: String,
    @SerializedName("first_name") val firstName: String? = null,
    val surname: String? = null,
    @SerializedName("born_year") val bornYear: String? = null,
    @SerializedName("died_year") val diedYear: String? = null,
    val age: Int? = null,
    val role: String? = null,
    val faction: String? = null,
    @SerializedName("historical_outcome") val historicalOutcome: String? = null,
    val tier: Int = 4,
    val personality: JsonPersonality? = null,
    val narrative: JsonNarrative? = null,
    val home: JsonHome? = null,
    val relationships: List<JsonRelationship>? = null
)

data class JsonPersonality(
    val intelligence: String? = null,
    val temperament: String? = null,
    val sociability: String? = null,
    val wit: String? = null,
    @SerializedName("piety_style") val pietyStyle: String? = null,
    val quirks: List<String>? = null,
    val fears: List<String>? = null,
    val motivations: List<String>? = null,
    @SerializedName("opinion_of_crisis") val opinionOfCrisis: String? = null
)

data class JsonNarrative(
    val appearance: String? = null,
    @SerializedName("emotional_arc") val emotionalArc: String? = null,
    @SerializedName("emotional_landscape") val emotionalLandscape: String? = null,
    @SerializedName("how_they_see_world") val howTheySeeWorld: String? = null,
    @SerializedName("life_before_1692") val lifeBefore1692: String? = null,
    @SerializedName("role_in_crisis") val roleInCrisis: String? = null,
    @SerializedName("sermon_influence") val sermonInfluence: String? = null
)

data class JsonHome(
    @SerializedName("building_id") val buildingId: String? = null,
    @SerializedName("household_role") val householdRole: String? = null,
    @SerializedName("household_number") val householdNumber: Int? = null
)

data class JsonRelationship(
    @SerializedName("target_name") val targetName: String? = null,
    val description: String? = null,
    val trust: Double? = null
)

// ── Facts ─────────────────────────────────────────────────────────────────

data class JsonFact(
    val id: String,
    val category: String? = null,
    val subcategory: String? = null,
    val date: String? = null,
    @SerializedName("date_precision") val datePrecision: String? = null,
    val title: String,
    val description: String,
    val location: String? = null,
    @SerializedName("location_id") val locationId: String? = null,
    @SerializedName("npcs_involved") val npcsInvolved: List<String>? = null,
    val tags: List<String>? = null,
    val source: String? = null,
    val confidentiality: String? = "public"
)

// ── Events ────────────────────────────────────────────────────────────────

data class JsonEvent(
    val id: String,
    @SerializedName("anchor_number") val anchorNumber: Int? = null,
    val name: String,
    @SerializedName("game_date") val gameDate: String,
    @SerializedName("crisis_phase") val crisisPhase: Int? = null,
    val description: String,
    val mandatory: Boolean? = null,
    @SerializedName("npc_placements") val npcPlacements: List<JsonNpcPlacement>? = null,
    @SerializedName("player_note") val playerNote: String? = null,
    @SerializedName("scene_id") val sceneId: String? = null,
    @SerializedName("title_card") val titleCard: String? = null
)

data class JsonNpcPlacement(
    @SerializedName("npc_id") val npcId: String,
    @SerializedName("scene_id") val sceneId: String? = null,
    val room: String? = null,
    val activity: String? = null
)

// ── Primary Sources ──────────────────────────────────────────────────────

data class JsonPrimarySource(
    val id: String,
    @SerializedName("source_id") val sourceId: String? = null,
    @SerializedName("doc_type") val docType: String,
    val title: String,
    @SerializedName("verbatim_text") val verbatimText: String? = null,
    @SerializedName("modern_gloss") val modernGloss: String? = null,
    val attribution: String? = null,
    val date: String? = null,
    @SerializedName("date_precision") val datePrecision: String? = null,
    @SerializedName("crisis_phase") val crisisPhase: Int? = null,
    val location: String? = null,
    @SerializedName("location_id") val locationId: String? = null,
    @SerializedName("npcs_direct") val npcsDirect: List<String>? = null,
    @SerializedName("npcs_witness") val npcsWitness: List<String>? = null,
    @SerializedName("npcs_about") val npcsAbout: List<String>? = null,
    val tags: List<String>? = null,
    val emotion: String? = null,
    @SerializedName("token_count") val tokenCount: Int? = null,
    @SerializedName("swp_reference") val swpReference: String? = null
)

// ── Building Coordinates ─────────────────────────────────────────────────

data class BuildingCoordinate(
    val id: String,
    val name: String,
    val x: Int,
    val y: Int,
    val zone: String,
    val type: String
)
