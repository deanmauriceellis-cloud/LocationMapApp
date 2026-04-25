# MASTER SESSION REFERENCE — LocationMapApp v1.5

> **Purpose.** Topic-indexed lookup so future Claude sessions can find prior work without loading every session log. **Goal: keep context small and directed** — read this file (an index) + the ONE specific live log it points to, and skip everything else.
>
> **How operator uses it.** Operator says things like:
> - *"read MASTER_SESSION_REFERENCE about the work we did for animating feature graphics for when we make POIs kick fun things off"* → look up sprite system → read S170 live log only
> - *"read MSR about the dwell rewrite"* → S169 live log only
> - *"read MSR about the verifier we're blocked on"* → S162 live log + the TigerLine block note
>
> **The flow:** grep this file for the topic → find the marker → read the linked live log + paths it lists → answer the operator. Do not load anything else.

---

## Phrasing index — what the operator might say → canonical topic

| If the operator says… | Look for the topic… |
|---|---|
| "sprite stuff", "creatures", "POIs kicking fun things off", "fun graphics that appear", "the witch+cat+skeleton work", "overlay critters", "animated feature graphics" | **Sprite & overlay-creature system** |
| "the dwell", "walker dwell", "TTS-gated dwell", "walker pause" | **Walker (POI dwell, narration gating)** |
| "the splash bug", "launcher tap goes straight to map", "Katrina intro skipped" | **Splash + launcher behavior** |
| "the verifier", "TigerLine block", "POI location verifier", "lat_proposed/lng_proposed" | **POI location verification** |
| "L3 parcels", "MassGIS parcels", "Essex assessor data" | **POI location verification → S157 L3 ingest** |
| "MHC enrichment", "hidden POIs", "SI delivery for buildings" | **Hidden historical POIs** |
| "witchy tiles", "the new map skin", "the third basemap" | **Map base layer & tiles → Witchy** |
| "OSM stays", "the pivot from MapLibre", "we kept osmdroid" | **Map base layer & tiles → S157 pivot** |
| "the APK size fix", "icons trimmed", "tiles in APK" | **APK size + asset bundling** |
| "the content strip", "merchant tier 0", "hide commercials" | **V1 content-strip policy** |
| "Lenovo install rule", "rebake deploy", "install -r warning" | **Standing project facts → DB rebake deploy rule** |

If the topic isn't here, fall back to grepping the section headings below.

---

## How to use this file

When operator references prior work by topic, grep this file for the topic, follow the pointer to the live log, and read only what's needed. The live logs in `docs/session-logs/session-NNN-YYYY-MM-DD.md` are the canonical record of what happened; this file just helps you find them.

If a marker says "abandoned", do not retry the same approach without first reading why it failed.

**Do NOT use this file as a source of truth for current code.** Code can change after a marker is written. Always grep / read the actual file before quoting a function name or path.

---

## Sprite & overlay-creature system (witches, cats, skeletons, etc.)

### S170 — Map sprite pipeline (rotation-only baseline shipped, walking deferred)
- **Live log:** `docs/session-logs/session-170-2026-04-24.md`
- **What shipped:** 7 cartoon characters (witch on broom, owl, black-cat, katrina-kitty, skeleton, mouse, rat) as 16-angle rotation frames, packed as 7 animated WebPs at q=85, **992 KB total**, ready for APK delivery.
- **Pipeline (working):** SDXL still → TripoSR mesh → headless 16-angle render via `render-angles.py` → animated WebP. ~30 s per character end-to-end, fully unattended.
- **Pipeline (deferred — walking):** Real skeletal rigging is required (Mixamo for humanoids, Quaternius CC0 for animals, or Auto-Rig Pro paid). All AI-only shortcuts were tried and failed (see "abandoned" notes below).
- **Artifacts:**
  - `~/AI-Studio/map-sprites/meshes/{name}.glb` — 7 TripoSR meshes, ~12 MB total
  - `~/AI-Studio/map-sprites/frames/{name}/{name}-angle-NN.png` — per-frame 256×256 PNGs
  - `~/AI-Studio/map-sprites/frames/{name}.webp` — animated WebP per character (the APK deliverable)
  - `~/AI-Studio/map-sprites/demo.html` — live behavior demo (transit / popup / wander per character)
  - `~/AI-Studio/map-sprites/viewer3d.html?char=NAME` — generic 3D viewer with orientation/lighting knobs and bake button
- **Scripts (all `~/AI-Studio/3d-pipeline/`):**
  - `render-angles.py` — **the keeper.** Headless pyrender + EGL, fast, scales to batches.
  - `bake-character.py` — full SD+IPAdapter+TripoSR+render+webp pipeline (for fresh characters from prompt)
  - `blender-walk-render.py` — abandoned procedural walk attempt; do not reuse
  - `generate-turntable.py` / `generate-angles.py` — abandoned AnimateDiff/IPAdapter angle generators; do not reuse
- **Tools installed (no sudo, all under `~/AI-Studio/`):**
  - Blender 4.2.5 LTS at `~/AI-Studio/blender/` (CUDA + OPTIX confirmed working)
  - TripoSR at `~/AI-Studio/3d-pipeline/TripoSR/` (own venv, torch+cu124, torchmcubes compiled, ~3 GB model on first run)
  - ComfyUI custom nodes added: AnimateDiff motion module v3 (1.67 GB at `~/AI-Studio/ComfyUI/models/animatediff_models/v3_sd15_mm.ckpt`), IPAdapter SD1.5 plus model (94 MB), CLIP vision ViT-H (already present)
- **Abandoned approaches (do not retry without reason):**
  - **AnimateDiff for turntables.** Its motion module animates motion-in-time, not rotation-in-angle. There is no "rotate" motion LoRA — only Pan/Tilt/Zoom/Roll. Wrong tool for character rotation.
  - **AI 2-pose walk-cycle generation.** IPAdapter weight is in tension with pose differentiation: high weight preserves identity but kills pose change; low weight breaks identity. Neither extreme produces visible walking.
  - **Procedural mesh deformation in Blender.** TripoSR meshes are continuous blobs without semantic body regions; vertex displacement crumples instead of producing leg motion.
- **Open follow-ups (V2 scope):**
  - Walking via real skeletal rigging — choose Mixamo (manual web clicks per humanoid, ~10 min/char) or Quaternius stock (loses AI character identity, fully automatable)
  - Generate the 25 new characters operator listed (spider, seagull, ravens, bluejays, red fox, MBTA train, bat, butterfly, tombstone, ghost, evil demon, warlock, pirate, old sea sailor, old sea captain, 1692 judge, 1692 sailing ship, horse, chicken, cow, goat, sheep, turkey, squirrel, chipmunk, pigeon)
  - Demo UI rework (character checklist + spawn-rate sliders + manual trigger buttons)
  - Map-triggered spawning (cemetery → skeleton, wharf → sailor, etc.) — needs in-app GPS integration

---

## Walker (POI dwell, narration gating, GPS handling)

### S169 — Walk FAB dwell rewritten as TTS-gated (CPA removed)
- **Live log:** `docs/session-logs/session-169-2026-04-23.md`
- **Commit:** `53a2b7b`
- **What shipped:** Walker now pauses at whatever step it's on whenever a POI is speaking or queued, resumes on `idle + queueEmpty`. Newspapers (segment.id `newspaper_1692_*`) stay ambient and don't pin the walker. Real GPS strategy untouched.
- **Net delta:** -154 lines on `SalemMainActivity.kt`.
- **Field-test owed:** still TOP PRIORITY for next session — confirm walker dwells on every narrating POI, exits on idle+empty, manual skip auto-advances.

### S168 — Install-default sweep + one narration per visit + zoom 14-20 lock + toolbar rework
- **Live log:** `docs/session-logs/session-168-2026-04-23.md`
- **Commit:** `d289efc`

### S167 — APK-size pre-Play-Store audit RESOLVED
- **Live log:** `docs/session-logs/session-167-2026-04-23.md`
- `poi-icons/` 544 MB → 3.4 MB (witchcraft-only WebP). `poi-circle-icons/` 36 MB → 1.2 MB. `salem_tiles.sqlite` 207 MB → 30 MB (Witchy-only, Salem city bbox, z16-19), now bundled in APK assets with auto-extract. **Debug APK: 79 MB.**

### S154 — V1 content-strip policy
- **Live log:** `docs/session-logs/session-154-*.md`
- `PoiContentPolicy.shouldStripContent(poi)` gates every render surface. Strip = BUSINESSES group AND `merchant_tier == 0`. ~1,832 strip / 459 keep.
- Hero architecture: `PoiHeroResolver.forceCategoryFallback` + subcategory-prefix filtering. Commercial hero backup at `/tmp/commercial-heroes-backup-S154-2026-04-20.tar.gz`.
- GPS cursor freeze fix: derived-speed escape hatch in `MotionTracker`.

---

## Splash + launcher behavior

### Open follow-up — `singleInstance` fix
- **Live log:** flagged in `docs/session-logs/session-168-2026-04-23.md`
- Symptom: launcher tap shows "quick witchykitty picture then jumped to GPS map" — bypasses Katrina intro + TTS welcome.
- Root cause: `SplashActivity` finishes itself after launching `SalemMainActivity`; task root becomes SalemMainActivity; next launcher tap brings that task to front on affinity match, bypassing the LAUNCHER intent filter.
- Proposed fix: `android:launchMode="singleInstance"` on SplashActivity in `app-salem/src/main/AndroidManifest.xml` (or `taskAffinity=""` + `singleTask`).
- Status: operator wants to decide before applying.

---

## POI location verification (TigerLine + L3 parcels)

### S162 — Verifier schema + admin filter shipped, runtime BLOCKED on TigerLine ingest
- **Live log:** `docs/session-logs/session-162-*.md`
- Schema columns added to `salem_pois`: `lat_proposed`, `lng_proposed`, `location_status`, `location_truth_of_record`, `location_drift_m`, `location_geocoder_rating`, `location_verified_at` + 3 indexes. Backend `accept-proposed-location` endpoint shipped. Admin filter+panel shipped. Android layers-popup POI-source group shipped.
- **Block:** `tiger.addr/addrfeat/edges/featnames` are empty for `statefp='25'`. Need TigerLine project (or `~/Development/OMEN/notes/tigerline.md`) to confirm MA tables are populated before writing the verifier.
- **L3 fallback available:** `~/Development/OMEN/architecture/` — L3 assessor path is already used for MHC year_built join (S163), so L3-centroid fallback for parcel-matched rows is viable in parallel.

### S157 — MassGIS L3 Parcels + Assess for Essex County INGESTED
- **Live log:** `docs/session-logs/session-157-*.md`
- `massgis.l3_parcels_essex` = 366,884 polygons with geom + LOC_ID + map_par_id (262 MB).
- `massgis.l3_assess_essex` = 429,803 tabular rows joined by LOC_ID (450 MB).
- Ingest script: `cache-proxy/scripts/ingest-l3-parcels-essex.py` (idempotent, checkpoints in `cache-proxy/out/l3-essex/`).
- Validated against 111 Essex St Beverly (year_built 1676, SFR use code 101, owner match).

---

## Hidden historical POIs (MHC enrichment)

### S163 — MHC hidden POI plumbing shipped, awaiting SI enrichment
- **Live log:** `docs/session-logs/session-163-*.md`
- SI got a research request this session. Awaiting JSON delivery keyed by `lma_id` with Tier A fields (`historical_note` / `mhc_style` / `origin_story` / `owners`).
- When SI delivers: write `tools/historical-buildings/import-si-enrichment.py` mirroring `import-to-salem-pois.py` — UPDATE `salem_pois` in place, honor `admin_dirty=TRUE` (don't clobber operator edits).

---

## Map base layer & tiles

### S158 — Witchy tile bake (third offline basemap shipped)
- **Live log:** `docs/session-logs/session-158-*.md`
- Pipeline at `tools/tile-bake/` (planetiler + tippecanoe + MapLibre-GL-Native).
- Tiered-purple historic buildings, parcel lines, no commercial POI labels.
- Memory marker: `project_witchy_tile_bake.md`.

### S157 — OSM stays as V1 basemap (architectural pivot)
- **Live log:** `docs/session-logs/session-157-*.md`
- Reversal of S156 plan. OSM + osmdroid stay as V1 base map; MassGIS/TigerLine become **overlays only**. MapLibre port, osmdroid ripout, TigerLine tile bundling are CANCELLED.
- Memory marker: `project_osm_stays_as_basemap.md`.

---

## Cross-project dependencies (off-repo)

- **SalemIntelligence** — Phase 1 KB live at `:8089`. 1,724 BCS POIs, 116K entities, 238 buildings, 5.67M relations. Memory: `project_salem_intelligence.md`.
- **Salem JSON data** — `salem-content/` pipeline reads from `~/Development/Salem/data/json/` (49 figures, 500 facts, 80 events, 200 sources).
- **GeoInbox** — future photo source for POI imagery; `omenpicturefeed@gmail.com`. Memory: `reference_geoinbox.md`.
- **RadioIntelligence** — LMA Salem is a hashed-observation contributor only, NOT a peer of WWWatcher/RadioLogger threat tools. Memory: `project_radiointelligence_collector_role.md`.
- **OMEN** — `~/Development/OMEN/notes/locationmapapp.md` for actionable items, `~/Development/OMEN/directives/ACTIVE.md` for active directives, `~/Development/OMEN/reports/locationmapapp/` for session-end reports.

---

## Standing project facts

- **Ship date:** Play Store by **2026-09-01** (Salem 400+ quadricentennial October 2026).
- **V1 commercial posture:** $19.99 flat paid, fully offline, no ads, no LLM, IARC Teen. Locked S138.
- **Test devices:** Lenovo TB305FU (HNY0CY0W) preferred. Pixel_8a_API_34 emulator only if Lenovo unavailable.
- **DB rebake deploy rule:** `adb uninstall && adb install` — NEVER `install -r` (SIGKILL during replace corrupts Room WAL recovery, false "no such table" crashes). Memory: `feedback_adb_install_after_db_rebake.md`.
- **Server ports:** cache-proxy 4300, admin 4301, oracle 4302/4303. Memory: `project_servers.md`.

---

## Out-of-repo work directories (for sprite/AI work)

- `~/AI-Studio/` — all AI tooling (ComfyUI, Forge, Bark, AudioCraft, models, Blender, TripoSR)
- `~/AI-Studio/map-sprites/` — sprite pipeline outputs + demo HTML pages
- `~/AI-Studio/3d-pipeline/` — TripoSR + render scripts + Blender scripts
- `~/AI-Studio/ComfyUI/` — ComfyUI install + custom nodes + shared model symlinks
- `~/AI-Studio/USAGE.md` — operator's run notes for each AI tool

---

_Document version: 1.0 — Created S170 close (2026-04-24). Maintain by appending marker blocks at session end whenever shipped work might be referenced later._
