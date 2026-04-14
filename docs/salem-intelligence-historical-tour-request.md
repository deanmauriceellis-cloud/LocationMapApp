# SalemIntelligence — Historical Tour Mode Content Request

**Date:** 2026-04-13 (LMA Session 123)
**From:** LocationMapApp (consumer of SalemIntelligence KB)
**To:** SalemIntelligence (with copy to Salem and OMEN for awareness)
**Purpose:** Specify the content shape LMA needs from SalemIntelligence's already-in-progress historical content work for LMA's upcoming Phase 9R "Historical Tour Mode"
**Status:** Coordination spec. SalemIntelligence is already working on this content stream — this document just tells them the exact field shapes and endpoints LMA will consume so the two sides line up. Not gated on OMEN approval; OMEN gets a copy for awareness and so the cross-project record exists.

---

## Product Context (why we're asking)

LMA is building **Historical Tour Mode** as a top-level mode of the consumer app. When the user toggles it ON, the app becomes immersive and "oblivious to the world":

- The map shows ONLY historical POIs (modern businesses, chains, and services hidden by default)
- Geofence-triggered narration fires ONLY on historical POIs
- Narration uses a **tour-guide voice** (different from the existing per-POI `short_narration` / `long_narration` which serve the modern map view)
- District boundaries trigger district-level narration ("You're now entering the merchant district…")
- An "Essentials" escape valve lets the user opt-in to surface a single nearby modern POI on demand (bathroom, parking, coffee) without leaving Historical Tour Mode

This means the historical content is its own narrative track, parallel to the existing modern track. The two never share text — they serve different user experiences.

The existing `short_narration` / `long_narration` / `description` fields stay as-is for the modern map view. This request adds NEW fields/endpoints for the historical track.

---

## Current State (LMA inventory)

LMA has **2,080 active POIs** in `salem_pois` (post-S123 dedup). Of those:
- **1,607** are linked to SalemIntelligence via `intel_entity_id`
- **126** have `year_established` populated (only 23 confirmed >100 years old)
- **38** have `historical_period` populated
- **3** have `historical_note` populated
- **1,135** have a `district` field populated, across 9 distinct districts

LMA's current historical-content coverage is essentially zero. We need SalemIntelligence to populate it.

SalemIntelligence inventory (per `/api/intel/status` 2026-04-13):
- **9,981** `historic_building` entities
- **290** `civic_entity`
- **146** `attraction`
- **232** Mode C entries (with architect / built_year / ownership chains / heritage designations)
- **199** Mode C with confirmed `built_year`
- **10** districts in `/api/intel/districts` (some with thin / placeholder descriptions)

The 9,981 historic buildings vastly outnumber what LMA currently imports. LMA Phase 9Q ("Salem Domain Content Bridge") will translate the qualifying subset into LMA POIs — but that work is blocked until SalemIntelligence (a) tags which buildings are tour-grade candidates and (b) generates per-building tour-guide narrative.

---

## What we need from SalemIntelligence

### Task 1 — New synthesizer pass: `historical_note` (per-entity tour-guide narrative)

A new Phase 2 synthesis pass that generates the tour-guide voice for each qualifying historical entity.

**Inputs to the prompt (per entity):**
- Mode A entity description and history
- Mode C details (architect, built_year, owner chain, heritage designations) when present
- Related entity_relations (who lived/worked there, what events happened)
- Related sources / primary source excerpts when present
- District context (which district + the district's historical character)

**Output:**
- 100-200 words
- Tour-guide voice — second-person, as if pointing at the building while standing across the street
- Lead with the hook (why does a tourist care?), then 1-2 concrete historical details (year, architect, person, event), then a sensory or human anchor
- Avoid restating the modern address or modern business operating there
- Do NOT duplicate `short_narration` / `medium_narration` / `entity_narration` text — this is a parallel narrative voice

**Storage:**
- New column on `entities`: `historical_note TEXT`
- Track prompt SHA: `historical_note_prompt_sha`
- Track generation timestamp: `historical_note_at`
- Use the existing job-control pattern (start / stop / status / restart) like the narration pass

**Scope:**
- Initial run: all entities of type `historic_building`, `attraction`, `civic_entity` that are inside Salem city limits AND have either Mode C details OR ≥1 related primary source (proxy for "we know enough to write something real, not hallucinate")
- Estimate: ~600-1,500 entities in this filtered set
- Future expansion: drop the gating filter once Mode C coverage broadens

---

### Task 2 — Tag historical-tour candidates

Add a derived flag on each entity so LMA knows which entities to import into `salem_pois` during Phase 9Q.

**Add column:** `is_historical_tour_candidate BOOLEAN`

**Compute as TRUE when:**
- entity_type IN (`historic_building`, `attraction`, `civic_entity`)
- AND inside Salem bbox (or any of LMA's 9 districts)
- AND has at least one of:
  - Mode C row with `built_year IS NOT NULL`
  - ≥1 related primary source
  - Heritage designation (NRHP, National Historic Landmark, etc.)
- AND `historical_note IS NOT NULL` (Task 1 output)

This single boolean is the gating filter LMA uses to decide which entities deserve a POI row. It replaces the open-ended question "is this building historically significant enough to tour?" with a deterministic data check.

---

### Task 3 — Expand district narratives

The existing `/api/intel/districts` endpoint returns one-paragraph descriptions, some of which are placeholder text from archive metadata (e.g. `"Title from negative.\nEssex Street, Salem, Massachusetts\nChamberlain, Samuel..."`). These are useful as labels but inadequate as tour content.

We need TWO narratives per district:

| Field | Length | Used for |
|---|---|---|
| `district_short_narrative` | 30-60 words | Played when user enters district geofence ("You've crossed into Derby Street wharf country, where Salem's salt-merchant princes built America's first fortunes…") |
| `district_long_narrative` | 200-400 words | Available in detail view if user taps the district label, or surfaces during a longer pause in narration |

**Quality bar:** real historical narrative, not a list of "businesses currently operating here." The current `description` text is fine for the modern map view but doesn't serve tour mode.

**Districts to cover (LMA's 9):**
1. Essex Street
2. The Point
3. Highland Avenue
4. Derby Street & Derby Wharf
5. Downtown Salem
6. Canal Street
7. North Salem
8. Bridge Street Neck
9. Salem Willows

(SalemIntelligence currently has overlapping districts including duplicates — e.g. two "Essex Street" entries, one with placeholder text. Please consolidate.)

---

### Task 4 — District boundary polygons (not centroids)

The current `/api/intel/districts` returns a single `lat`/`lng` per district. LMA needs to detect "user has entered district X" via geofence — a single point doesn't give us that.

**Need:** GeoJSON polygon (or MultiPolygon) for each of the 9 districts above.

**Source options (in your judgment):**
- City of Salem GIS open data (if available)
- Manually drawn from OSM neighborhood boundaries
- Convex hull of the BCS businesses already assigned to each district
- Hand-drawn against the OSM base map

**Format:** standard GeoJSON Feature with `properties.district_name` and `geometry: Polygon | MultiPolygon`.

**Endpoint:** add `geojson` field to `/api/intel/districts` response, OR add a new `GET /api/intel/district/{name}/geometry`.

---

### Task 5 — New API endpoints for LMA consumption

Mirror the existing narration sync pattern. LMA already has `cache-proxy/scripts/sync-narrations-from-intel.js` — we'll add a parallel `sync-historical-from-intel.js` that consumes these.

| Method | Endpoint | Returns |
|---|---|---|
| `GET` | `/api/intel/entity/{id}/historical_note` | `{ entity_id, historical_note, historical_note_at, historical_note_prompt_sha }` |
| `GET` | `/api/intel/historical-tour-candidates` | List of all entities with `is_historical_tour_candidate = true`, paginated. Includes `entity_id`, `name`, `lat`, `lng`, `category`/`type`, `district`, `historical_note`, `built_year`, `architect`, `heritage_designations`. This is what Phase 9Q's bridge will consume. |
| `GET` | `/api/intel/district/{name}/full` | Both narratives + GeoJSON polygon + historical metadata in a single payload. |

Existing endpoints stay unchanged. The above are additions.

---

## Acceptance Criteria

### Task 1 (historical_note)
- [ ] New synthesizer pass exists with start/stop/status/restart job control
- [ ] Sample of 20 generated notes reviewed by operator (we'll review before full run)
- [ ] Coverage: ≥80% of the gated entity set has a non-null `historical_note` after the run
- [ ] Notes pass a "tour-guide voice" smell test — second-person, hook-led, sensory, not Wikipedia-style

### Task 2 (candidate flag)
- [ ] `is_historical_tour_candidate` column populated
- [ ] Final count surfaced in `/api/intel/status` so LMA can sanity-check expected import volume

### Task 3 (district narratives)
- [ ] All 9 districts have BOTH `district_short_narrative` and `district_long_narrative`
- [ ] No placeholder/archive-metadata text — real historical content
- [ ] Duplicate district entries consolidated

### Task 4 (district polygons)
- [ ] All 9 districts have GeoJSON polygon
- [ ] Polygons tile reasonably (don't overlap implausibly; Essex Street and Derby Street should not enclose each other)

### Task 5 (endpoints)
- [ ] All 3 endpoints return valid JSON with documented fields
- [ ] Listed in your `USAGE.md` API table

---

## Sequencing

LMA can start prep work in parallel:

1. **LMA, immediate:** Surface `historical_note` field unconditionally in admin POI editor so operator can hand-author samples and refine the tour-guide voice you'll codify into the prompt
2. **LMA, immediate:** Create local `salem_districts` table to receive the eventual sync output
3. **SalemIntelligence, Task 3 + 4 first** (smaller scope, high leverage — district narratives and polygons unlock the "you've entered…" feature even before per-POI notes exist)
4. **SalemIntelligence, Task 1 small batch** (~20 entities) for prompt iteration with operator review
5. **SalemIntelligence, Tasks 1 + 2 full run** once prompt is locked
6. **LMA Phase 9Q:** import qualifying candidates into `salem_pois` via the bridge
7. **LMA Phase 9R:** wire historical mode toggle in consumer app, narration source switching, district geofences

---

## Coordination Notes

- **Sister-project routing:** LMA, SalemIntelligence, and Salem (the 1692 game / corpus source) are sister projects under the same operator umbrella, all consuming/producing Salem-domain content. Direct technical coordination on shared schema is fine; OMEN gets visibility via the LMA session report and a parallel note in `~/Development/OMEN/notes/salemintelligence.md`. No formal OMEN approval blocking this work.
- **Timeline:** No hard deadline. Phase 9R is post-9Q. The LMA Play Store target is 2026-09-01 for Salem 400+, but Historical Tour Mode is not gating that release. Quality > speed.
- **Volume warning:** The 9,981 historic_building entities are NOT all in scope. The candidate filter (Task 2) is what constrains the work to a manageable set (~600-1,500 estimated). Confirm the filtered count before kicking off the full Task 1 run.
- **No duplication:** Do not regenerate any of the existing narration fields (`short_narration`, `long_narration`, `medium_narration`, `entity_narration`). Those serve a different user surface.
- **Salem (the 1692 game) overlap:** Salem's corpus already imported into SalemIntelligence (219 person entities, 40 events, 1,217 sources, 16,767 relations, 232 NPC profiles per `salemint salem-1692-import`). When the historical_note prompt has access to those `entity_relations` for a building, please use them — the connection between a building and a 1692 figure who lived/worked there is the exact tour-guide hook this product needs. No new request to Salem; just reuse the import that's already on the SalemIntelligence side.

---

## Open Questions for SalemIntelligence

1. Does SalemIntelligence have access to City of Salem GIS data for district boundaries, or do we need to hand-draw?
2. Do you have a preferred prompt structure for tour-guide voice generation, or want LMA operator to draft a v0 prompt for you to refine?
3. The 9,981 historic_building count — what's the realistic Salem-bbox subset count?
4. Mode C coverage of the candidate set — what's the current % with built_year + architect? If low, should we expand sources before running Task 1?

---

**End of spec.** Reply with any pushback on scope, schema-shape changes, or open-question answers. Iterate as needed.
