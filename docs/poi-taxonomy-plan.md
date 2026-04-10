# POI Taxonomy Alignment — Multi-Session Plan

**Opened:** 2026-04-10 (Session 114)
**Owner:** LocationMapApp v1.5 / WickedSalemWitchCityTour
**Trigger:** S114 architecture conversation — operator asked "Help me understand the confusion between the application and the database structures" after discovering that only ~29 coarse POI types existed in the bundled DB where the image generator assumed 185 subtypes.

---

## The problem in one sentence

The project has seven parallel POI taxonomies (`PoiCategories.kt`, its TS mirror, PG `salem_narration_points`, PG `salem_tour_pois`, PG `salem_businesses`, the bundled SQLite, and the `generate-poi-icons.py` script), and none of them enforce each other, so the admin tool writes richer data than the phone can read and the image generator targets a shape that never existed.

## Goal

One canonical POI taxonomy (`PoiCategories.kt` + its TS mirror) enforced as a FK constraint across every PG POI table that carries it, propagated into the bundled SQLite by a real publish loop, read natively by the Android Room entities, and joined on by `PoiHeroResolver` as a dumb `subcategory → {category}/{subcategory}.png` lookup.

## Hard scope decisions (made Session 114)

### IN scope for this arc
- `salem_narration_points` (814 rows) — already has `category` + `subcategory` columns, needs backfill + FK on `category`.
- `salem_businesses` (861 rows) — getting new `category` + `subcategory` columns this session; needs backfill in S115.
- `PoiCategories.kt` / `poiCategories.ts` as the single source of truth for the taxonomy.

### OUT of scope for this arc

1. **Merging `salem_businesses` and `salem_narration_points` into one table.** Operator direction S114: "two distinct things." They share a spine (category/subcategory) but keep separate feature-specific fields.

2. **`salem_tour_pois` (45 curated rows).** Its `category` column holds tour-chapter themes (`witch_trials`, `maritime`, `literary`, `landmark`, `museum`, `park`, `visitor_services`) — a different concept from the PoiCategories.kt layer taxonomy. Shoehorning it into the POI taxonomy creates more confusion than it solves. Tour POIs stay on their own path. If they later need hero images, that's a separate one-off (45 rows, manual assignment).

3. **Image regeneration.** Blocked by SalemIntelligence (separate sibling project the operator is building — supplies per-subcategory LLM-generated SD prompts). The data plumbing in this arc does NOT need images to proceed; hero assets regen slots in on top of a coherent taxonomy whenever SalemIntelligence ships. Until then the existing hash-pinned pool + red placeholder is the correct intermediate state.

4. **Touching the image generator `tools/generate-poi-icons.py`.** Will be aligned to the 176-subtype vocabulary as part of the SalemIntelligence-driven regen pass, not in this arc.

---

## Four-step arc (3-4 sessions total)

### Step 1 — Taxonomy foundation (SESSION 114, this session)

**Goal:** schema + seed script only. No data changes on the POI rows themselves.

Deliverables:
- `cache-proxy/salem-schema.sql` — added `salem_poi_categories` and `salem_poi_subcategories` lookup tables. Added `category` + `subcategory` columns on `salem_businesses` (nullable). Added FK constraints on the three safe columns (`salem_narration_points.subcategory`, `salem_businesses.category`, `salem_businesses.subcategory`).
- `cache-proxy/scripts/sync-poi-taxonomy.js` — parses `web/src/config/poiCategories.ts`, upserts `salem_poi_categories` (22 rows) and `salem_poi_subcategories` (~176 rows). Idempotent: re-run whenever `PoiCategories.kt` changes. Prunes stale rows.
- `docs/poi-taxonomy-plan.md` — this file.

Deliberately deferred (NOT shipped in S114):
- FK constraint on `salem_narration_points.category` — existing 814 rows hold legacy lowercase values like `shop`, `restaurant`, `services`, `attraction` that do not match PoiLayerId form. Adding the FK now would reject every row. FK goes in after the S115 backfill.
- Backfill of `salem_businesses.category`/`subcategory` — new columns start NULL.
- Backfill of `salem_narration_points.subcategory` — NULL on all 814 rows today.
- Any Android Room, admin tool, or publish loop changes.

**Ordering constraint:** S115 depends on S114's lookup tables + FK columns being in place. Do not start S115 until S114 has been applied to PG.

### Step 2 — Backfill + admin worklist (SESSION 115)

**Goal:** every POI row has a subcategory FK'd to `salem_poi_subcategories(id)`. Ambiguous rows surface in the admin tool for manual curation.

Work:
1. **Normalization migration for `salem_narration_points.category`.** Map the 29 legacy lowercase values to PoiLayerId form:
   - `shop` → probably `SHOPPING` (unless tags say otherwise — some may be `WITCH_SHOP` or similar)
   - `restaurant`, `cafe`, `bar`, `brewery` → `FOOD_DRINK`
   - `services` → needs splitting — likely `SHOPPING`, `AUTO_SERVICES`, or `CIVIC` based on name/tags
   - `attraction` → probably `ENTERTAINMENT` or `TOURISM_HISTORY` based on context
   - `venue` → `ENTERTAINMENT` or `CIVIC`
   - `other` → MUST be resolved per-row; no safe default
   - `historic_site`, `witch_shop`, `psychic`, `ghost_tour`, etc. → direct mapping to PoiLayerId
2. **Heuristic subcategory assignment:** use POI name, tags, `narration_script` content, and any OSM tag fields to infer the right subcategory. The heuristic writes to `subcategory` only when confidence is high; leaves NULL when ambiguous.
3. **Admin tool:** add a "Needs Subcategory" worklist filter to the admin tree (`web/src/admin/AdminLayout.tsx` and `PoiEditDialog.tsx`). Operator clicks through and assigns subcategories to the ambiguous rows.
4. **Subcategory dropdown in `PoiEditDialog.tsx`:** reads from `salem_poi_subcategories` via a new `GET /api/admin/salem/poi-subcategories?category_id=XXX` endpoint in cache-proxy. Filters by the current POI's category.
5. **Same pass for `salem_businesses`:** heuristic category/subcategory assignment from `business_type` + `cuisine_type` + `name` + `tags`.
6. **Once `salem_narration_points.category` is clean**, add the deferred FK constraint:
   ```sql
   ALTER TABLE salem_narration_points
     ADD CONSTRAINT fk_salem_narration_category
     FOREIGN KEY (category) REFERENCES salem_poi_categories(id) ON DELETE RESTRICT;
   ```

Acceptance: every row in `salem_narration_points` and `salem_businesses` has a non-NULL `category` that is an FK-valid PoiLayerId. Subcategory may remain NULL on some rows — those become the "missing subcategory" worklist the operator finishes curating between sessions.

### Step 3 — Publish loop (Phase 9P.C) (SESSION 116)

**Goal:** the bundled APK's SQLite mirrors the current PG state, carrying the new columns. Android Room entities read `category` + `subcategory` natively.

Work:
1. **Build-time dump script.** Reads PG via `cache-proxy`'s existing connection, writes a fresh `salem-content/salem_content.db` with the new columns on `narration_points` and `salem_businesses`.
2. **Bump Room schema version** on `SalemContentDatabase`. The `type` column stays for backward compat during the transition but `subcategory` is added. Eventually `type` gets dropped in a later schema version.
3. **Real Room migration** (not `fallbackToDestructiveMigration`). One of the STATE.md carry-forwards is that the destructive fallback needs to go before Play Store — this is the session where that happens for the user_data.db too.
4. **Update Android entities:** `NarrationPoint.kt` (add `category: String`, `subcategory: String?`), `SalemBusinessEntity.kt` (same). DAOs updated to expose them.
5. **Update `salem-content/` content pipeline:** so a full rebuild from Salem JSON also populates the new columns.

Acceptance: a clean build of the Salem APK ships a Room DB with `narration_points.subcategory` populated. `NarrationPoint.subcategory` is readable from Kotlin.

### Step 4 — `PoiHeroResolver` rewrite (SESSION 116, tacks onto Step 3)

**Goal:** dumb lookup, no hardcoded maps, no hash-pinning, no pool math.

```kotlin
object PoiHeroResolver {
    fun resolve(context: Context, poi: NarrationPoint): HeroResult {
        val subcategory = poi.subcategory ?: return HeroResult.RedPlaceholder
        val category = poi.category.lowercase()
        val slug = subcategory.substringAfter("__")
        val path = "poi-heroes/$category/$slug.png"
        return if (assetExists(context, path)) HeroResult.AssetImage(path)
        else HeroResult.RedPlaceholder
    }
}
```

~30 lines. The existing `typeToFolder` map, the `folderListingCache`, the hash-pinning math — all deleted. Red placeholder when the subcategory is NULL or the asset file doesn't exist (which becomes the visible worklist flag for both "data not curated" and "image not generated" — operator sees the same red rectangle for both problems and resolves through the admin tool + SalemIntelligence-driven image regen).

Acceptance: every POI with a valid subcategory renders its assigned hero. Every POI with a NULL subcategory OR a missing asset file renders the red "ASSIGN HERO" placeholder.

---

## Session-sized handoff notes

### For S115 (backfill)
- S114's lookup tables are seeded from `poiCategories.ts`. Re-run `sync-poi-taxonomy.js` at the start of S115 if the TS file has moved on since S114.
- Start with a local dry-run of the normalization migration — generate a `(poi_id, current_category, proposed_category, confidence)` CSV before executing any `UPDATE`.
- The `other` (60 rows) and `services` (101 rows) buckets in `salem_narration_points` are the hard cases — these need name/tag inspection per row, not a simple lowercase→uppercase mapping.
- Do not drop `salem_businesses.business_type` or `cuisine_type` in S115. Those stay as secondary metadata until a later session proves nothing reads them.
- FK on `salem_narration_points.category` is the acceptance checkpoint — if it fails to add, backfill is not done.

### For S116 (publish loop + resolver)
- Do NOT start before S115's backfill is verified and the narration_points category FK is in place. The publish loop copies PG→SQLite; if PG isn't clean, neither is the shipped DB.
- Coordinate with STATE.md's DB-wipe carry-forward: the Room schema bump on `SalemContentDatabase` should be paired with the Room migration work on `UserDataDatabase` to replace `fallbackToDestructiveMigration`.
- Hero asset files (`poi-heroes/{category}/{subcategory}.png`) will not exist yet — SalemIntelligence-driven regen comes later. S116's resolver rewrite ships, but every POI will render the red placeholder until assets land. That's expected. Operator validates by seeing the placeholder on-device for POIs where they previously saw the hash-pinned pool image.

### For the eventual SalemIntelligence-driven image regen
- Target path: `app-salem/src/main/assets/poi-heroes/{category_lowercase}/{subcategory_slug}.png`.
- Dimensions: TBD (S114 operator mentioned 384x256; revisit when SalemIntelligence ships).
- Input to the prompt: the POI's `salem_poi_subcategories.label`, `category_id`, and `tags`, plus any SalemIntelligence-supplied per-subcategory context.
- Generator script (`tools/generate-poi-icons.py`) rewrites to query `salem_poi_subcategories` directly instead of carrying its own fictional 185-slug list.

---

## Status tracker (update as sessions ship)

| Step | Session | Status |
|---|---|---|
| 1. Schema + seed + plan doc | S114 | shipping this session |
| 2. Backfill + admin worklist | S115 | not started |
| 3. Publish loop + Room migration | S116 | not started |
| 4. PoiHeroResolver rewrite | S116 | not started |
| Hero asset regen | deferred | blocked on SalemIntelligence |
