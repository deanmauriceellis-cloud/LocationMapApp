# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-05 — **Session 225 (web admin can now create POIs).** Operator-driven sprint while SI provenance reformulation continues. New `POST /admin/salem/pois` in `cache-proxy/lib/admin-pois.js` (auto-slug id from name, JSONB whitelist serialization, FK-violation 400, dup-id 409, stamps `data_source='admin_create'` + `admin_dirty=TRUE`). New `web/src/admin/PoiCreateDialog.tsx` (4-field minimal form: Name + Category w/ inline `+ Add new` + optional Subcategory/Address). `AdminMap.tsx` + `AdminLayout.tsx` wired with `addPoiMode` mirroring the existing tour-stop add-mode pattern: emerald **+ New POI** header button (POIs view) → cursor crosshair + emerald banner → click map → create modal opens with lat/lng pre-filled → on save the existing full PoiEditDialog opens on the new row for narration / flags / hours / images. All five validation paths smoke-tested. `npx tsc --noEmit` clean; Vite HMR clean; cache-proxy restarted via plain `npm start` (S223 dotenv-autoload live). No Android build, no Room bump (admin-only feature; reaches device next publish-chain run). Find-menu-refinement docket queued at S224 close deferred — operator pivoted to POI creation; docket carries forward for later refinement passes. **Session 224 (prior — walk-sim queue stall fix + Find/PoiDetailSheet V1-dead code purge + lean+state session-start protocol). Sessions 217-223 (prior — category-aware FAB narration + Show-All FAB override; per-POI TigerLine/Google Validate workflow; storytelling-with-subtopics framework Room v17 + subtopic UI + 497-POI auto-gen; tour detour feature; cache-proxy dotenv autoload; web-admin restart; lean+state protocol amendment).** **Operator-stated hard pause from S214 still in effect: Salem Oracle (:8088) + SalemIntelligence (:8089) are undergoing major reformulation to attach full per-claim provenance; without source attribution V1 content cannot ship legally — do not propose work consuming them or backfilling content from them until the operator signals the pause is lifted (memory `project_oracle_intelligence_paused_provenance.md`).** **Cross-repo Salem commit owed (still).** Per-session detail in `docs/session-logs/session-{217..225}-*.md` and `SESSION-LOG.md`.

> Older "Previous" entries (S180 / S183-S189 trimmed S201; S195 trimmed S202; S196-S199 trimmed S204; S213-S216 prose trimmed S224; S217-S224 prose collapsed S225) — moved out to keep STATE.md under the 200-line cap. Detail lives in `SESSION-LOG.md` + `SESSION-LOG-ARCHIVE.md` + per-session live logs at `docs/session-logs/session-NNN-*.md`.

---

## TOP PRIORITY — V1 launch triage (operator-confirmed 2026-04-29; revised S203 close 2026-04-30)

> **Internal ship target: 2026-08-01** (89 days). One month tighter than the prior Sept 1 anchor in CLAUDE.md / strategic docs. Salem 400+ peak attendance is October 2026.

### Operator-side legal / business (in flight)

- **Form TX copyright** — counsel handling. Hard deadline **2026-05-20** (16 days). Confirm with lawyer this week that filing happens.
- **2026-04-20 lawyer meeting** — happened, outcome captured by operator. **Operator owes me four privacy-policy fields** (operating entity name+type, jurisdiction, contact email, mailing address) so I can patch `docs/PRIVACY-POLICY-V1.md`.
- **Play Console developer account** — operator starts this week. Multi-week ID-verification clock starts immediately.
- **Upload keystore second-medium backup** — covered (operator confirmed multiple secure methods).
- **TTS quality monitoring** — operator does end-to-end Lenovo listen-tests routinely as part of QC.

### Field test (carry-forward to next session)

- Operator drove ~50 min with wife on 2026-04-30 evening on the S206 first-build APK (pre-fixes). Logs reconstructed the missing-POI experience as queue-starvation + tour-mode default-OFF + `is_tour_poi` half-honored. Lenovo now carries the post-fix S206 APK; next drive should validate (a) Lappin Park / Curtis Park / Carlton School render in tour mode regardless of layer toggles, (b) starting a tour with explore-checked layer boxes no longer silently silences markers + narration. Operator's wife's next Salem walk also still owes #25 / #29 Fix 1 / #3 full POI-narration loop validation from S204.

### Hard gate (S214 close, 2026-05-01)

**Salem Oracle (:8088) and SalemIntelligence (:8089) are paused for major provenance reformulation.** Until the operator signals completion, do NOT propose: new admin-tool integrations against `oracleClient.ts` / port 8088, new consumers of `/api/intel/*` at :8089, narration regeneration sourced from these services, hero-prompt regeneration from these services, POI description backfills from these services. Manual / curated content (operator-authored, MassGIS MHC, TigerLine-derived, hand-edited Wikipedia with operator citations) is unaffected. Existing `salem_pois` / `salem_tour_stops` data sourced from these services is grandfathered for now but may need re-attribution before AAB upload — the operator will direct this when the reformulation lands.

### S225 outcome — Web admin can create POIs (shipped 2026-05-05)

`+ New POI` header button in the admin POIs view enters place-mode; clicking the map opens `PoiCreateDialog` (Name + Category + optional Subcategory/Address). `POST /admin/salem/pois` creates the row with auto-slug id, `data_source='admin_create'`, `admin_dirty=TRUE`. The full `PoiEditDialog` then opens on the new row for narration / flags / hours / images. Inline `+ Add new category` available inside both the create dialog and the edit dialog so the operator can build out new taxonomy as they go. Find admin-created rows via `SELECT * FROM salem_pois WHERE data_source = 'admin_create'`.

### S226+ next-session docket — deferred from S224 (Find menu refinement)

While SI provenance reformulation continues, the Find-menu-refinement docket from S224 close is still queued. Now also relevant whenever the operator decides to give the new categories created in S225+ their own Find-menu tiles.

- **Find category grid review.** 16 visible tiles (4 cols × 4 rows + Favorites cell). `FIND_HIDE_IN_V1` currently excludes `FUEL_CHARGING`, `TRANSIT`, `PARKING`, `EMERGENCY` — revisit. `FIND_SALEM_LABEL` map (e.g. "Taverns & Cafés", "Apothecaries & Clinics") and `FIND_TILE_ASSET` map (`find_tile_<id>.jpg` in `res/drawable-nodpi/`) define the labels + artwork — walk tile-by-tile.
- **Subcategory grid review.** `showFindSubtypeGrid` at `SalemMainActivityFind.kt:606` — drills from each top-level category into `PoiCategories.ALL` subcategories. Verify naming, ordering, and which subcategories surface for each parent.
- **Hero picture review.** Per-tile illustration assets in `res/drawable-nodpi/find_tile_<id>.jpg` plus per-POI hero artwork resolved via `PoiHeroResolver`. Operator wants to review which hero shows for which category/subcategory and whether HISTORICAL_LANDMARKS still aliases to the `historic_house` set (S216 carry).
- **POI detail click-through review.** Two parallel surfaces: `showPoiDetailDialog` (Find dialog flow) and `PoiDetailSheet` (map flow). Walk through what each shows, in what order, and whether the post-S224-cleanup layout (no Reviews, no Comments, no in-app website) is coherent.

### Other carry-forwards (lower priority)

- **Operator field-validation of S221 detour feature** on Lenovo HNY0CY0W (Plan steps 2–12). Polish anticipated post-field-test: distance/time banner labels, out-and-back preview, rejoin-fade timing.
- **Refine subtopic content quality** (S220 carry): figure-card body trimming, adjacency teasers, distance phrasing.
- **`is_tour_poi=true` Lint cleanup** (S220 carry): spurious `young_world_*` rows. Add a Lint check flagging `is_tour_poi=true` AND `category IN (EDUCATION, OFFICES, HEALTHCARE, AUTO_SERVICES, FINANCE, APARTMENT_BUILDING, FOOD_DRINK, LODGING)`.
- **Borderline-historical commercial opt-in audit** (S220 carry): Hawthorne Hotel etc. — operator-side flip `is_historical_property=true`.
- **Tier 3 disk reclaim** (~4.4 GB): `tools/poi-icons/`, `tools/hero-triptych/output-full/`, `tools/tile-bake/data/sources/`, `cache-proxy/out/l3-essex/`, `docs/archive/poi-icons-unused-styles_*/`.
- **`.gitignore` audit**: `overnight-runs/`, `cache-proxy/poi-cache.json`, `cache-proxy/cache-data.json`, `web/dist`, `web/tsconfig.tsbuildinfo`, `docs/bake-tests/`.
- **Field listen-test of S217 BusinessLabel fallbacks** + TTS pronunciation of "cafe" / "centre".
- **Field validation of S217 FAB override** — every nearby POI announces with FAB ON.
- **John Ward House tour-leg fix** (S216): operator-side re-author or relocate.
- **HISTORICAL_LANDMARKS icon + hero regeneration** (S216).
- **Content rebuild for `description` post-`historical_note`-merge** (S216).
- **Dequeue 40 m staleness drop** (S216) — align with `geofence_radius_m`?
- **S215 suspicious-leg graph investigation** — Dr. K 13/14, WD1 3/6/10/11.
- **PoiDetailSheet/Find further cleanup** (S224 carry): `enterFilterAndMapMode`/`exitFilterAndMapMode` clearXXX/stopXXX no-ops still in source.
- **CLAUDE.md "Pinned" block cleanup** — references Room v11 / S186-S188 state; actual schema is v17.

### Open backlog (older items, still pending)

- **Speed-aware queue cap** (S206 carry) — at >15 mph, replace queued narration items rather than appending; alternative: switch to SHORT tier at high speed. Pick + ship.
- **DensityTour 0-stop design question** (S206) — auto-fall-back to Explore at tour start, or refuse to start a 0-stop tour. Decide + ship.
- **Cross-repo Salem commit owed** (S204→S205→S206) — `Salem/cmd/genbiographies/main.go` + 3 hand-edited bio JSONs need committing in the Salem repo. Operator-driven.
- **Rebuild signed AAB + 30-min Lenovo smoke** — first signed AAB since S180. V1 gating checklist (Visit Website handoff, Find Reviews/Comments hidden, toolbar gating, webcam "View Live" hidden, recon camera DEBUG-only — verify BuildDefaults retail posture in release).
- **Operator field-walk validation** (S204) — GPS-OBS heartbeat + Witch Trials bios + full TTS-gated dwell on real POI traversal during wife's Salem walks.
- **Drive regression in airplane mode** (#20) — validate offline posture under real GPS load.
- **#9 Re-author 5 polyline tours via web admin** (S185, operator-driven).
- **#6 McIntire content drain** (S200, operator hand-author).

### S205+ — V1 feature additions (priority order, KEPT for V1)

1. **Halloween / October seasonal layer** — date-checked content overlay (different POI markers, ghost-tour POIs unlocked October 1, photo-spot pins, October-only event narration). Zero network. Reuse existing geofence + narration paths. Scope tight, ~1 week.
2. **Time-slider basemap, 1906 only** for V1 (Sanborn 1906) — full 4-year slate (1890/1906/1950/1957) deferred to V1.0.1+. ~1 week for one year.
3. **Tour-completion certificate (PDF on-device)** — end-of-tour souvenir with route + triggered POIs + date + Salem-themed border. Zero network. ~2-3 days.
4. **Salem Heritage Trail polyline tour — YELLOW, not red** (the trail was repainted yellow). Hand-trace via admin or import official KML if available. ~1 day. See memory `reference_salem_heritage_trail_yellow.md`.

### V1 features deferred / killed (S201 decisions)

- **Bark hero voice clips** — SKIPPED. Ship Android TTS only (operator does ongoing QC listen-tests).
- **Hotel B2B partnerships** — post-launch only.
- **Pre-launch beta** — small, 2–3 friendly-eyes testers (not the 5–10 program).
- **iOS port** — scope effort/timing now, decide later.

### Marketing (operator-led, content-only sessions before Aug 1)

- **Channel:** Salem Chamber of Commerce + local-first.
- **Asset packet** — drafted in a content-only session before Aug 1: 1-page sell sheet, feature graphic, 3-5 hero screenshots, 2-paragraph press blurb. Operator provides photos + final approval; I draft text + screenshot frames.

### Architectural housekeeping (S201) — done

`COMMERCIALIZATION.md` / `IP.md` replaced with V1-truthful stubs (pre-pivot originals archived 2026-04-29). `CURRENT_TESTING.md` archived. Ship anchor updated Sept 1 → Aug 1.

---

## Backlog from S185–S200 (TRIAGED S203, ✅ items pruned)

Open items only: **V1.0.1 (S196)** multi-admin auth + actor-from-session wiring; **V1.0.1 (S196)** PoiEditDialog inline history panel; **V1 (S200)** operator hand-authors McIntire content; **V1.0.1 (S192)** backfill `year_established` on 500+ HIST_BLDG rows; **V2 (S195)** driving mode (V1 walking-only); **V1 (S185)** re-author 5 deleted Kotlin tours in PG (operator-driven); **V2 (S179)** routing-jvm Option 2 + walk-sim cleanup; **V1.0.1 (open)** DirectionsSession JVM tests.

---

### Older carry-forwards (S159–S172) — TRIAGED S203

Open items: V1.0.1 water animation tuning (S172); V2 osmdroid → WickedMapView migration; V1.0.1 POI location verifier batch-run (S162); V2 MHC hidden-POI companion importer (S163); V1 GPS + V1-offline drive regression (S161, operator); V1.0.1 Witchy tile-bake bbox extension for operator's Beverly home (S159).

---

## Architectural pivots & rule changes (recent → older)

- **2026-05-01 (S216):** **`HISTORICAL_LANDMARKS` split off from `HISTORICAL_BUILDINGS`.** MASSGIS-only (`data_source = 'massgis_mhc'`) POIs reclassify to a new `HISTORICAL_LANDMARKS` category that's gated by the existing "POIs Hist. Landmark" Layers toggle (default off). Curated `HISTORICAL_BUILDINGS` (BCS / OSM / manual / api_sync) always narrate in tour mode — no Layers gate. `NarrationGeofenceManager.TOUR_HIST_LANDMARK_CATEGORIES` swapped from `BUILDINGS, ENT, LODGING` to `LANDMARKS, ENT, LODGING`.
- **2026-05-01 (S216):** **`historical_note` column dropped (Room v15 → v16).** Pre-existing rule: `historical_narration` is the strict pre-1860 audio script. `historical_note` was a duplicate-purpose blurb field used for display + search + legacy fallbacks; consolidated into `description` (no gaps), then column dropped end-to-end across PG, Kotlin (5 files), cache-proxy + web admin (5 files), Room schema bump, KSP regen.
- **2026-05-01 (S216):** **Auto-bake publish chain into Gradle preBuild** (S180/S185 carry-forward done) — every `:app-salem` build now runs `publishSalemContent` (4 scripts) + `verifyBundledAssets` so admin-tool edits in PG reach the device automatically. Escape hatch `-PskipPublishChain`.
- **2026-05-01 (S216):** **Walk-sim onboarding owns the directions session** — `TourViewModel.startTour(skipOnboarding: Boolean = false)` flag; both walk-sim entrypoints opt out of the parallel `onboardToNearestPolylinePoint` path so the drift-reroute loop can't paint a green directions polyline on top of the per-leg overlays.
- **2026-05-04 (S224):** **Session-start protocol amended lean → lean+state.** STATE.md and the most recent live log are now mandatory at session start (in addition to auto-loaded CLAUDE.md + MEMORY.md). Older logs / SESSION-LOG.md / OMEN files stay on-demand. Memory `feedback_session_protocol.md` rewritten.
- **2026-05-04 (S224):** **`enqueueNarration` cancel-stamp scoped to direct-play.** Walk-sim queue stall (Lappin Park → Rockafellas) was caused by unconditional `narrationCancelForPoiAtMs` bump in `enqueueNarration` colliding with the S146 cancel-for-POI Idle-suppression guard within 500ms of a previously-playing POI's natural Idle. Stamp + newspaper-cancel now only fire on the direct-play branch.
- **2026-04-26 (S185):** **Tours are polyline-only paths; POIs govern their own narration.** PG is the only source of truth for tours/stops/legs; the 5 historical Kotlin-curated tours dropped from the asset.
- **2026-04-26 (S185):** **Asset schema must be Room-canonical, not legacy SQL.** `align-asset-schema-to-room.js` rewrites every Room-managed table using the exact `createSql` from `app-salem/schemas/<DB>/<v>.json` and stamps `identity_hash` + `user_version`. Must run last in any publish chain.
- **2026-04-26 (S183/S184):** Tour walking polylines are authored content (`salem_tour_legs` PG table); admin walking-route rendering uses anchor + clip for marker continuity.
- **2026-04-26 (S182):** Tour route fidelity declared content-authoring, not engineering — `feedback_tour_routing_is_content_not_engineering.md`. S181 OSM-policy reversal retracted; `feedback_no_osm_use_local_geo.md` restored to S178 surgical-only constraint.
- **2026-04-26 (S180):** V1 manifest stripped of network permissions; V1 feature gates R8-stripped from binary. App network-incapable at OS level. Rule: `feedback_v1_no_external_contact.md` (zero outside contact except GPS).
- **2026-04-23 to 04-25 (S157/S158/S171/S175/S178):** MassGIS L3 ingest, Witchy basemap, custom WickedMap engine (parallel system), on-device router shipped (TIGER bake + Directions UI), surgical wharf-walkway osm_id allowlist. Detail in archive.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A / 9P.B / 9U / 9X** | COMPLETE | Admin UI, unified POI table, Witch Trials feature |
| **9Y** MassGIS+TigerLine Overlay Integration | PARTIAL | L3 parcels + assess ingested (S157). Schema extended (S156). v9 cascade (S159). Outstanding: 9Y.3 enrichment script, 9Y.9 polygon geofence runtime, 9Y.Overlay osmdroid dynamic overlays. |
| **9Z** Historical Maps Time-Slider | PLANNED | V1 year slate: 1890, 1906, 1950, 1957 Sanborn. |
| **9Q** Salem Domain Content Bridge | DEFERRED — consumes 9Y data | building→POI translation. |
| **9R** Historic Tour Mode | DEFERRED — consumes 9Y data | opt-in chapter-based 1692 tour. |
| **10** Production readiness | First signed AAB built (S180); commercial chores remain | Firebase, photos, DB hardening, emulator verification. |
| **11** Branding, ASO, Play Store | Quality over schedule per operator | Salem 400+ Oct 2026 aspirational, not hard. |
| **Cross-project** TigerLine | Phase 2 stalled (2026-04-21) | LMA no longer blocked on tile delivery. |
| **Cross-project** SalemIntelligence | Phase 1 KB live at :8089 | 1,830 POIs / 1,770 narrated. |

**Sessions completed:** 225. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,081 live (S216 dedup: 220 dead hard-deleted + 43 live cluster losers hard-deleted + 1 Gardner-Pingree dup, all merged before delete; further trim through S221+). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description` (143 POIs touched, 0 gaps).
- **Room DB:** at `app-salem/src/main/assets/salem_content.db`, **v17 schema** (S219), identity_hash `d44caaec8754fbf94080c82e0abd1ebe`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). 4 tours / 73 legs (S224). v11 (S186) `is_civic_poi`. v12 (S192) `historical_narration`. v13 (S193) `is_historical_tour`. v14 (S195) `is_historical_property`. v15 (S198) dropped `body_points` from WitchTrialsNewspaper. v16 (S216) dropped `historical_note` from SalemPoi. v17 (S219) added `narration_subtopics` JSONB to SalemPoi.
- **APK:** ~739 MB debug; `poi-icons/` at 544 MB is the pre-Play-Store audit target.
- **Assets manifest:** `app-salem/src/main/assets/ASSETS-MANIFEST.md` + `cache-proxy/scripts/verify-bundled-assets.js`.

---

## Standing rules + decisions

- **V1 commercial posture (S138):** $19.99 flat paid, fully offline, no ads, no LLM, IARC Teen.
- **PG-13 standing content rule** (NOTE-L018, accepted by OMEN S023).
- **Device-verify preference:** Lenovo TB305FU (HNY0CY0W) over emulator.
- **V1 offline-mode enforcement** (S141 + S180): const + R8 strip + manifest-level network-incapable at OS level.
- **adb install discipline** (S156): after `publish-salem-pois.js` rebake, deploy via `adb uninstall && adb install`, NEVER `install -r`.
- **No master plan** (S161): if multi-phase plan needed, declared explicitly in `docs/plans/`.

---

## OMEN Open Items

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — V1-minimal Posture A shipped S145 at `docs/PRIVACY-POLICY-V1.md`. Full OMEN-008 draft pending OMEN review.
2. **NOTE-L015 — SalemCommercial cutover** — PARKED POST-V1 per operator S145.
3. **Cross-project: SalemIntelligence anomaly relay** — `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` (ANOM-001, ANOM-002).
4. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline.
5. **9-dot menu witchy backgrounds** — PARKED S146.

---

## Pointers to detail

| What | Where |
|---|---|
| **Topic-indexed lookup of prior work** | **`MASTER_SESSION_REFERENCE.md`** |
| Recent session summaries (rolling window) | `SESSION-LOG.md` |
| Older session summaries | `SESSION-LOG-ARCHIVE.md` |
| Live conversation logs | `docs/session-logs/session-NNN-YYYY-MM-DD.md` |
| Active multi-phase plans | `docs/plans/` |
| SalemIntelligence integration | `~/Development/SalemIntelligence/docs/lma-*.md` |
| Architecture, tech stack | `CLAUDE.md` and the codebase itself |
| Legal/compliance | `GOVERNANCE.md` |
| Patentable innovations | `IP.md` |
| Monetization strategy | `COMMERCIALIZATION.md` |
| UX/architecture decisions | `DESIGN-REVIEW.md` |
| Older STATE.md content | `docs/archive/STATE_removed_2026-04-09.md` (and `_removed_2026-04-21.md`) |
