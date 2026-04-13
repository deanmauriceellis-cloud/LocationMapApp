# SalemIntelligence — LocationMapApp Narration Gap Fill Request

**Date:** 2026-04-13 (LMA Session 121)
**From:** LocationMapApp
**Purpose:** Fill all missing narrative text for LMA's 2,190 POIs

---

## Current State

LocationMapApp has **2,190 active POIs** in `salem_pois`. Of those:
- **1,618** are linked to SalemIntelligence via `intel_entity_id`
- **572** have never been seen by SalemIntelligence

After the initial narration sync (Session 121), coverage is:

| Field | Populated | Missing | Used For |
|---|---|---|---|
| `short_narration` | 853 | 1,337 | Geofence walk-by TTS announcement (~20-50 words) |
| `long_narration` | 1,556 | 634 | Detail view / linger content (~200-500 words) |
| `description` | 995 | 1,195 | POI card display (~60-120 words) |
| `short_description` | 558 | 1,632 | Compact UI displays |
| `origin_story` | 223 | 1,967 | Historical origin narrative |

---

## Task 1: Generate LLM narrations for ~1,212 linked entities

1,212 of the 1,618 linked entities return 404 on `GET /api/intel/entity/{id}/narration` — they have never had narrations generated.

**Action:**
```
POST /api/intel/mass/re-generate
Body: { "all_eligible": true }
```
Then poll `GET /api/intel/job/{job_id}` until complete.

**This populates per entity:**
- `short_narration` (~20-50 words) — LMA uses for geofence TTS
- `medium_narration` (~60-120 words) — LMA uses as `description`
- `long_narration` (~200-500 words) — LMA uses for detail view
- `entity_narration` (~80-150 words) — LMA uses as `long_narration` fallback when `long_narration` is null

---

## Task 2: Re-extract BCS descriptions for entities with missing fields

Many linked entities have thin BCS coverage:
- 1,060 missing `short_description`
- 1,033 missing `long_description` (maps to LMA `description` as fallback)
- 1,395 missing `origin_story`

**Action:**
```
POST /api/intel/mass/refresh-current
```
Re-crawl web sources to extract `short_description`, `long_description`, and `origin_story` for all entities with missing or stale values.

---

## Task 3: Discover and ingest 572 unlinked POIs

572 POIs exist in LMA's database but have no corresponding entity in SalemIntelligence. These need to be ingested into the KB so they can be linked and narrated.

**Export file:** `~/Development/LocationMapApp_v1.5/tools/salem-data/unlinked-pois-for-intel.json`

Each entry has: `lma_id`, `name`, `category`, `lat`, `lng`, `address`

**Breakdown by category:**

| Category | Count |
|---|---|
| SHOPPING | 189 |
| ENTERTAINMENT | 93 |
| TOURISM_HISTORY | 70 |
| FOOD_DRINK | 63 |
| PARKS_REC | 61 |
| CIVIC | 47 |
| WITCH_SHOP | 13 |
| PSYCHIC | 9 |
| LODGING | 8 |
| HEALTHCARE | 6 |
| HAUNTED_ATTRACTION | 4 |
| WORSHIP | 4 |
| GHOST_TOUR | 3 |
| EDUCATION | 2 |

**After ingesting**, run `mass/re-generate` on the new entities to produce their narrations.

---

## Execution Order

1. `POST /api/intel/mass/re-generate { "all_eligible": true }` — generate narrations for all 1,212+ entities missing them
2. `POST /api/intel/mass/refresh-current` — re-extract BCS fields from web sources
3. Ingest 572 new POIs from `unlinked-pois-for-intel.json` into the KB
4. Run `mass/re-generate` again for the newly ingested entities
5. Signal LocationMapApp when complete

---

## What LocationMapApp Does After

Once SalemIntelligence signals completion:

```bash
cd ~/Development/LocationMapApp_v1.5/cache-proxy
node scripts/import-bcs-pois.js          # link the 572 new entities
node scripts/sync-narrations-from-intel.js  # pull all fresh narrations
node scripts/publish-salem-pois.js        # republish SQLite bundle
```

Then rebuild and install the APK.

---

## Field Mapping Reference

How SalemIntelligence fields map to LMA's `salem_pois` table:

| SalemIntelligence Source | SalemIntelligence Field | LMA Column | Priority |
|---|---|---|---|
| Narration endpoint | `short_narration` | `short_narration` | primary |
| Narration endpoint | `long_narration` | `long_narration` | primary |
| Narration endpoint | `medium_narration` | `description` | primary |
| Narration endpoint | `entity_narration` | `long_narration` | fallback (when long_narration null) |
| BCS export | `short_description` | `short_description` | primary |
| BCS export | `long_description` | `description` | fallback (when medium_narration null) |
| BCS export | `origin_story` | `origin_story` | primary |
