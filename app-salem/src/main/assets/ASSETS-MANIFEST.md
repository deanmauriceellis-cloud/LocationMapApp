# Bundled Assets Manifest

Everything in `app-salem/src/main/assets/` that the APK depends on at runtime. **Every required asset must be verified present before an APK build ships.** The verification script at `cache-proxy/scripts/verify-bundled-assets.js` enforces this; run it before any `assembleDebug` / `bundleRelease`.

## History of missing-asset production crashes

Bundled-asset regressions keep shipping because the cache-proxy rebuild pipeline has multiple scripts writing to different DBs, and some of them silently clobber each other. This manifest + the verify script exist so the next regression fails the build instead of the demo.

| When | What went missing | Root cause | Fix |
|---|---|---|---|
| S149 (2026-04-17) | `salem_witch_trials_newspapers` table | bundle script wrote only to assets DB; `publish-salem-pois.js` rebuilds source → copies over assets, silently wiping | S150: write to source DB, `CREATE TABLE IF NOT EXISTS`, mirror source → assets |
| S153 (2026-04-20) | `salem_witch_trials_npc_bios` + `salem_witch_trials_articles` tables (demo crash) | same bug, other two tables never got the S150 treatment | S153: apply S150 pattern to both bundle scripts |

## Required assets

### `salem_content.db` — Room database (~9 MB)

Backing store for `SalemContentDatabase`. Room validates schema + identity_hash on open; any table missing or schema-mismatched crashes the app with `SQLiteException`.

Required tables (must all exist, with `CREATE TABLE` matching the `@Entity` declarations in `app-salem/.../content/model/`):

| Table | Min rows | Source script | Room entity |
|---|---:|---|---|
| `salem_pois` | 1800 | `publish-salem-pois.js` | `SalemPoi.kt` |
| `tours` | 4 | `publish-tours.js` | `Tour.kt` |
| `tour_stops` | 0 | same (Room-declared but unpopulated in current bundle) | `TourStop.kt` |
| `tour_legs` | 70 | `publish-tour-legs.js` | `TourLeg.kt` |
| `events_calendar` | 20 | `publish-events-to-sqlite.js` | `EventsCalendar.kt` |
| `historical_facts` | 500 | (legacy; bundled at last `:salem-content` run pre-S242, table preserved by Room schema) | `HistoricalFact.kt` |
| `historical_figures` | 49 | same | `HistoricalFigure.kt` |
| `primary_sources` | 200 | same | `PrimarySource.kt` |
| `timeline_events` | 40 | same | `TimelineEvent.kt` |
| `salem_witch_trials_newspapers` | 200 | `bundle-witch-trials-newspapers-into-db.js` | `WitchTrialsNewspaper.kt` |
| `salem_witch_trials_npc_bios` | 49 | `bundle-witch-trials-npc-bios-into-db.js` | `WitchTrialsNpcBio.kt` |
| `salem_witch_trials_articles` | 16 | `bundle-witch-trials-into-db.js` | `WitchTrialsArticle.kt` |
| `room_master_table` | 1 | identity-hash housekeeping | (Room internal) |

Required Room `identity_hash` at v19: **`745afa3eb4ce04bd7873671ea297b6e0`**. Bumping this requires regenerating the schema JSON (`./gradlew :app-salem:kspDebugKotlin`) and re-running `align-asset-schema-to-room.js`.

**S255 — dead tables dropped from publish chain:** `salem_businesses` (861 rows), `narration_points` (817), `tour_pois` (45). Not in `@Database(entities=…)` since the pre-S242 module deletion; never read at runtime. `publish-salem-pois.js` now DROPs them on every bake. ~1 MB asset reclaim.

### `salem_tiles.sqlite` — Offline map tiles (262 MB) — **lives in `app-salem-tiles-pack/` as of S256**

osmdroid-format tile cache. Single `tiles` table, schema: `(key INTEGER, provider TEXT, tile BLOB, PRIMARY KEY(key, provider))`. Required minimum: **5,000 tile rows** covering downtown Salem zoom 16-19 (per `feedback_tile_zoom_levels.md`).

Source: `cache-proxy/scripts/bundle-offline-tiles.js` (or equivalent per operator). If this file is absent or empty, the map renders blank.

**S256 — moved to install-time Asset Pack.** Path is now `app-salem-tiles-pack/src/main/assets/salem_tiles.sqlite`, not `app-salem/src/main/assets/`. Reason: V1 AAB exceeded Google Play's 200 MB compressed download ceiling. install-time Asset Packs (a) keep the base AAB under the ceiling (Play allows the pack as a separate up-to-2GB component) and (b) preserve the offline-from-install UX — pack is downloaded with the base APK at install time, no first-launch "downloading map" screen. At runtime install-time pack assets are merged into the app's `AssetManager`, so `OfflineTileManager` continues to use `context.assets.open("salem_tiles.sqlite")` with no code change. Verify script + pack module: `cache-proxy/scripts/verify-bundled-assets.js` (path updated), `app-salem-tiles-pack/build.gradle`, `app-salem-tiles-pack/src/main/AndroidManifest.xml`.

**Not tracked in git (S256).** The 262 MB blob exceeds GitHub's 100 MB file-size limit and is a generated build artifact regardless. `.gitignore` excludes both the asset pack path and the `tools/tile-bake/dist/` staging path. **To regenerate on a fresh clone:**

```
cd tools/tile-bake
# Regenerate salem-custom.mbtiles from source (Mapnik / Planetiler — see bake-parallel.js)
node merge-into-bundle.js              # writes tools/tile-bake/dist/salem_tiles.sqlite
cp dist/salem_tiles.sqlite ../../app-salem-tiles-pack/src/main/assets/
```

The bake takes ~30 minutes wall-clock on a recent machine; intermediate `salem-custom.mbtiles` is the slow part. Operator typically keeps the bundle warm and rebakes only when the tile source / bbox / zoom range changes (last bake S234, expanded to +10 mi q=60 at 261.7 MB). Verify post-copy with `node cache-proxy/scripts/verify-bundled-assets.js` (asserts 5000+ rows + provider=`Salem-Custom`).

**Historical-map providers in `salem_tiles.sqlite` (S288 close):** beyond the modern `Salem-Custom` provider, the same DB also carries one row-set per historical year, accessed at runtime via the picker FAB → `OsmdroidHistoricalOverlay`:

| Provider | Tiles | Notes |
|---|---|---|
| `Historical-1692` | 15,364 | Upham 1866 reconstruction of Salem Village 1692. S288 precision pass: 6 GCPs (Parris parsonage, Salem Village Meeting House, Rebecca Nurse Homestead, John Proctor house, Endicott Pear / Orchard Farm, Rev. John Hale house — corrected anchor coords). TPS warp + cutline clip to GCP convex hull + 500m buffer. |
| `Historical-1700` | 28,747 | Phillips/Perley downtown Salem reconstruction. S287 coarse 4-corner. Precision pass owed. |
| `Historical-1851` | 12,450 | McIntyre city map. S285 v2 process: 4 Washington Square corner GCPs + 4 outer anchors. Operator-confirmed alignment good. |
| `Historical-1911` | 31,791 | Walker atlas plate 05. S287 coarse 4-corner. Precision pass owed. |
| `Salem-Custom` | 715,841 | Modern basemap (default). |

Picker entry in `HistoricalMapsBottomSheet.YEARS` must be updated to add a new year + tile thumbnail must be generated under `app-salem/src/main/assets/historical-thumbs/thumb_<year>.webp`. Full per-map bake pipeline lives in `tools/historical-maps/` — see CLAUDE.md "Historical-map bake pipeline" subsection under Key Paths.

### `tours/*.json` — Pre-computed OSRM walking routes (~400 KB)

Required files (one per `tours.id` in the DB):
- `tour_essentials.json`
- `tour_explorer.json`
- `tour_grand.json`
- `tour_salem_heritage_trail.json`
- `tour_witch_trials.json`

Extra legacy/test files tolerated (e.g., `downtown_salem_*.json`) — not enforced. Source: `cache-proxy/scripts/backfill-tour-routes.js` (S125).

### `portraits/*.webp` — Historical-figure portraits (~2.5 MB)

One file per `historical_figures.id`. Minimum count: **49** (matches `historical_figures` row count). If count drops below 49, some figure profile views will render a placeholder.

### `poi-icons/<category>/*.webp` and `poi-circle-icons/<category>/*.webp` — POI marker art (~4.6 MB combined, post-S167)

22 category folders. Per `feedback_poi_icon_architecture.md`, Salem supplies its own icon mapping; these files are the map markers. Loss of a category folder degrades markers to a generic pin. Not individually enforced in the verify script (too many files) — the top-level directories must exist and be non-empty.

**S167 shrink (2026-04-24):**
- `poi-icons/` — 544 MB PNG → 3.4 MB WebP. Only the `witchcraft` style variant survives (177 files, 512×512 q=80). The other 7 style variants (cute/demon/devil/evil/psycho/undead/zombie) are archived at `docs/archive/poi-icons-unused-styles_archived_2026-04-24/` for possible V2 tier-based monetization. 512 dimension preserved because `PoiDetailSheet.bindStrippedHero` renders these at 55% of screen height.
- `poi-circle-icons/` — 36 MB PNG → 1.2 MB WebP. 150 files at 128×128 q=80 (they render as ~40 dp map markers).
- Loaders updated to look for `.webp` suffix: `ProximityDock.kt:188`, `PoiHeroResolver.kt:158`, `MarkerIconHelper.kt:208`. If you ever add more PNG files, either reconvert them or update the filters back.

### `hero/*.webp` and `heroes/*.webp` — POI hero images

`hero/` = UUID-named assets from the SalemIntelligence KB. `heroes/` = POI-id-named assets (the canonical names used by `HeroAssetLoader`). Both trees must exist and be non-empty.

### `us_places_v1.sqlite` — Splash place resolver (~2.1 MB)

Read-only SQLite consumed by `PlaceResolver.kt:33` (S231 splash polygon resolver) — maps the user's current GPS fix to a US place name (city/town) for the splash-screen "Welcome to <PLACE>" heading when outside Salem. Single bundled file; not a Room database. If absent, the splash falls back to a generic greeting; no crash.

### `witch_trials/portraits/*` — NPC portraits (currently empty)

Directory exists but is empty as of S153. The 49 npc_bio rows reference portraits via `portrait_asset`, but the actual WebP files aren't bundled yet. Not a crash risk (portrait_asset is nullable) — the UI falls back to a silhouette. Regenerating these portraits is a content-art task deferred post-V1.

### `salem_1692_newspapers.json` — Legacy JSON (deprecated after S130)

Loaded by `WitchTrialsNewspaper.kt` at some paths. Since S130, the authoritative source is the `salem_witch_trials_newspapers` table. This file can be removed once the legacy code paths are deleted — **do not delete until a grep of `salem_1692_newspapers.json` shows zero code references.**

## How to verify before an APK build

```bash
cd cache-proxy
node scripts/verify-bundled-assets.js
```

Script exits non-zero on any failure. Integrate into your build harness (gradle preBuild task or CI check) to make the check mandatory. Until that wiring lands, **run it manually every time the assets DB is re-published.**
