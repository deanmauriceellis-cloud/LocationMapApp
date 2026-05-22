# LocationMapApp v1.5 — Claude Code Context

> **LocationMapApp v1.5** — A multi-module Android/Web mapping platform. The primary product
> is **Katrina's Mystic Visitors Guide - Salem** (the V1 paid product, formerly
> "WickedSalemWitchCityTour" — see Naming below), a GPS-guided Salem, MA tourist app with
> historical content, geofence-triggered narration, and walking directions.

---

## Naming (read this before any `adb` / package work)

The same product wears four different names depending on which layer you're touching. They are intentionally not aligned:

| Layer | Name | Notes |
|---|---|---|
| **User-facing brand** | **Katrina's Mystic Visitors Guide - Salem** | `app-salem/src/main/res/values/strings.xml:app_name`. Public brand, post-S245 LLC rebrand. |
| **Android `applicationId`** | `com.destructiveaigurus.katrinasmysticvisitorsguide` | What `adb` / `bundletool` / Play Store sees. Use this for `pm list packages`, uninstall, etc. |
| **Kotlin / Java namespace** | `com.example.wickedsalemwitchcitytour` | Code package + Room schema dir (`app-salem/schemas/com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase/`). Intentionally NOT renamed — refactor risk is high; touched everywhere. |
| **Gradle module** | `:app-salem` | What `./gradlew` targets. |
| **Codebase / repo slug** | `WickedSalemWitchCityTour` / `LocationMapApp_v1.5` | Legacy names; appear in older docs, plans, memory files. |

**Common footgun:** `adb -s <serial> uninstall com.example.wickedsalemwitchcitytour` will return `DELETE_FAILED_INTERNAL_ERROR` because that package doesn't exist on the device — the real installed package is `com.destructiveaigurus.katrinasmysticvisitorsguide`.

---

## Key Paths (canonical references — refresh on schema/pipeline changes)

- **Upload keystore:** `~/keys/wickedsalem-upload.jks`; signing properties at `~/.gradle/gradle.properties` (mode 0600).
- **AAB / APK artifacts:** `app-salem/build/outputs/bundle/release/app-salem-release.aab` / `apk/release/app-salem-release.apk`.
- **Build commands:** `./gradlew :app-salem:assembleDebug` (debug APK) / `:app-salem:bundleRelease` (signed AAB) / `:app-salem:assembleRelease` (signed APK).
- **Android publish chain (must run in order before any debug/release build):**
  1. `node cache-proxy/scripts/publish-salem-pois.js`
  2. `node cache-proxy/scripts/publish-tours.js`
  3. `node cache-proxy/scripts/publish-tour-legs.js`
  4. `node cache-proxy/scripts/publish-poi-collection.js` (S268; renamed S274 from publish-poi-passport.js — Katrina's Collection rebrand)
  5. `node cache-proxy/scripts/align-asset-schema-to-room.js` (MUST run last — auto-discovers latest `app-salem/schemas/<DB>/<v>.json`, stamps Room `identity_hash` + `PRAGMA user_version`, rewrites every Room-managed table via canonical `createSql`. Skipping triggers `fallbackToDestructiveMigration` on first launch and wipes the asset.)
  Auxiliary scripts (run on-demand for specific content updates, not every bake): `publish-1692-newspapers.js`, `publish-witch-trials.js`, `publish-witch-trials-to-sqlite.js`, `publish-splash-tree.js`.
- **Historical-map bake pipeline** (`tools/historical-maps/`, S286-S288, on-demand per-map): (1) source JPG → `mask_to_frame.sh in.jpg out.png x0 y0 x1 y1` to alpha-mask everything outside the drawn frame; (2) operator GIMP pass for any in-frame insets/overlays that overlap content (e.g. 1692 City of Salem inset); (3) GCP picking via `tools/historical-maps/<year>/gcp_picker.html` (single-file browser tool, 12 pre-loaded Salem Village anchors with stable `#1..#12`, Leaflet+CARTO modern map, exports QGIS-format `.points` CSV); (4) `points_to_gdal_translate.sh <png> <points> translated.tif` parses + embeds GCPs; (5) `build_cutline.py` writes a Shapely convex-hull + 500m buffer GeoJSON; (6) `gdalwarp -tps -t_srs EPSG:3857 -r bilinear -dstalpha -cutline cutline.geojson -crop_to_cutline translated.tif warped_3857.tif`; (7) `gdal2tiles.py -z 14-19 -r bilinear --xyz -q warped_3857.tif tiles_v2` (SINGLE-THREADED — `--processes=N` skips overview tiles silently, S287 lesson); (8) `python3 xyz_to_mbtiles.py tiles_v2 <year>_v2.mbtiles`; (9) `node merge-historical-into-bundle.js --year <year> --source-mbtiles ./<year>_v2.mbtiles --bundle tools/tile-bake/dist/salem_tiles.sqlite` (DELETE-then-INSERT under `provider='Historical-<year>'`); (10) `python3 webp_compress_historical.py --provider Historical-<year>` (or no `--provider` for all). Sync to `app-salem-tiles-pack/src/main/assets/salem_tiles.sqlite`. The `verify-bundled-assets.js` regex (`Historical-(1[6-9]\d{2}|20\d{2})`) admits 16xx-20xx provider names — extend if a new century is added. Per-map working dirs `tools/historical-maps/<year>/` are gitignored (intermediates only).
- **Room schema export:** `app-salem/schemas/com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase/<version>.json`. Regenerate via `./gradlew :app-salem:kspDebugKotlin -x verifyBundledAssets` after any `@Database(version = N)` bump. Current: **v22**, identity_hash `07f63ead9c5c3c05d87dcb6d54f64ba8` (S274 — PoiPassport → CollectionEntry rename for Katrina's Collection rebrand).
- **Install workflow (asset packs, S256):** standalone `adb install` does NOT include `salem_tiles.sqlite`. Use:
  ```
  bundletool build-apks --bundle=<aab> --connected-device --device-id=<serial>
  bundletool install-apks --apks=<apks> --device-id=<serial>
  ```
  `adb uninstall` first (per `feedback_adb_install_after_db_rebake.md`); never `install -r`. bundletool jar at `~/.local/bin/bundletool.jar` (1.17.2).
- **Test devices:** Lenovo TB305FU (`HNY0CY0W`, V1 min-spec floor) / Pixel 8 (`41231FDJH0018J`, primary post-S263).
- **Admin walking-route endpoints:** `cache-proxy/lib/admin-tours.js`. Web admin route panel: `web/src/admin/{TourTree,AdminMap}.tsx`.
- **OMEN credential audit:** `~/Development/OMEN/docs/credential-audit-2026-04-05.md` (Amendment 2026-04-26).
- **Archived (do-not-execute) OSM merge plan:** `docs/archive/S182-osm-pedestrian-routing-merge_archived_2026-04-26-misdiagnosis.md`.

---

## Session Start Protocol — lean+state (2026-05-04)

Operator rule: **startup reads must be kept minimal to conserve tokens, but `STATE.md` and the most recent session log are now mandatory** so the session opens with current phase + last-session context already in mind. Originally lean-only (S161, 2026-04-23); amended 2026-05-04 (S224) — operator: "we always read state.md and the last session log in session start."

Do ONLY this at session start:

0. **Sync with remote** — `git fetch origin`, compare HEAD vs `origin/master`. Pull if behind. Alert if diverged. Never work on stale state.
1. **Read `STATE.md`** — current phase, TOP PRIORITY, carry-forwards. Mandatory.
2. **Read the most recent `docs/session-logs/session-NNN-*.md`** — last session's live log, for continuity. Mandatory.
3. **Open live conversation log** — Create `docs/session-logs/session-NNN-YYYY-MM-DD.md` with a minimal header (date, branch, HEAD, recovery state). This is the canonical record of the session and the crash-recovery mechanism — append significant actions, decisions, and code changes as they happen.
4. **One-line greeting** — Brief the operator: "Session N opened. What's on the docket?" Optionally include one tight sentence of carry-forward context if STATE.md or the last log surfaced something the operator likely needs reminded of. Do NOT dump phase status, carry-forwards, or OMEN items.
5. **Await direction.** Never execute work before the operator confirms scope.

`CLAUDE.md` and `MEMORY.md` load automatically; `STATE.md` + last session log are read manually as part of the protocol. That is the complete startup read set.

### Everything else is ON-DEMAND

Read these files only when a specific question requires them — never at session start:

- `SESSION-LOG.md` — when reconstructing recent work beyond the last session.
- `SESSION-LOG-ARCHIVE.md` — only for historical investigation.
- Older `docs/session-logs/session-NNN-*.md` (anything but the most recent) — read when the operator asks about a specific past session.
- `~/Development/OMEN/notes/locationmapapp.md` — when addressing or acknowledging an OMEN note.
- `~/Development/OMEN/directives/ACTIVE.md` — when an OMEN directive is relevant to current work.
- Reference docs (`GOVERNANCE.md`, `IP.md`, `COMMERCIALIZATION.md`, `DESIGN-REVIEW.md`) — on demand only. (`CURRENT_TESTING.md` archived 2026-04-29 as pre-pivot — lint tab + session logs serve the testing-checklist role now.)

### No master plan

As of 2026-04-23 (S161), the master plan was deleted and its pre-park snapshot moved to `docs/archive/`. There is no plan-of-record. If a multi-phase plan is needed, the operator declares one explicitly or we rebuild it in a Plan.

### Recovery from a killed session

The most recent live log is now read every session, so killed-session recovery is automatic.

## Live Conversation Log

**Every session maintains a live log at `docs/session-logs/session-NNN-YYYY-MM-DD.md`.**

This is the crash-recovery mechanism. If a session is killed (context overflow, disconnection, etc.), this file survives on disk and the next session can reconstruct full context from it.

**What to log (append as it happens):**
- Session start context (status, phase, blockers)
- Every significant decision and reasoning
- Every file created, modified, or deleted (with summary of changes)
- Build/test results
- User direction and confirmations
- Errors encountered and how they were resolved
- Open questions and their resolutions

**Format:** Timestamped entries, append-only. Never rewrite earlier entries.

**On session start:** Read the most recent log in `docs/session-logs/` if recovering from a killed session.

## Session End Protocol

**The session story is written ONCE — in the live log at `docs/session-logs/session-NNN-YYYY-MM-DD.md`. Everywhere else gets a thin pointer.** This is the load-bearing rule of the optimized protocol. Do not duplicate the story across STATE.md, SESSION-LOG.md, and the OMEN report — that wastes context budget and creates four places to keep in sync.

Execute in order:

1. **Finalize the live log** — Append a closing session summary and final status to `docs/session-logs/session-NNN-YYYY-MM-DD.md`. This is the canonical record. Everything else in this protocol references it.
2. **Append to SESSION-LOG.md** — Add ONE entry with the session header line + a 2-3 sentence summary + a pointer to the live log. **Do not duplicate decisions, file lists, or detailed reasoning** — those live in the live log and `git log -p`. Example:
   ```
   ## Session NNN: YYYY-MM-DD — One-line title

   Two-or-three sentence summary of what shipped.

   Full session detail: `docs/session-logs/session-NNN-YYYY-MM-DD.md`. Commit: `<sha>`.
   ```
3. **Roll the SESSION-LOG.md window** — If `SESSION-LOG.md` now contains more than 10 sessions, move the oldest entries to the top of `SESSION-LOG-ARCHIVE.md` (newest archived first). Target: keep the 10 most recent sessions in `SESSION-LOG.md`.
4. **Update STATE.md only if it changed** — STATE.md is a snapshot, not a changelog. Only update it if (a) the TOP PRIORITY changed, (b) a phase status changed, (c) a carry-forward item was resolved or added, or (d) a new OMEN item appeared. **Do not append session summaries to STATE.md.** Keep it under 200 lines. If the file ever drifts above 200 lines, stop and compress it on the spot.
5. **Update CLAUDE.md only if structure or protocols changed** — CLAUDE.md should be stable. Touch it ONLY when (a) project structure changes (new module, new key reference file), (b) the session start/end protocol itself changes, or (c) the tech stack changes. Do NOT update CLAUDE.md every session.
6. **Master plan — skip** (no master plan as of 2026-04-23). If the operator asks for a multi-phase plan, it is declared explicitly rather than written back to a master plan file.
7. **Archive removed content** — Anything removed from any tracked .md doc goes to `docs/archive/` with format `FILENAME_removed_YYYY-MM-DD.md`. Never silently delete.
8. **Commit and push** — Stage all changes, commit with a descriptive message, push to remote. Local and remote must never drift apart.
9. **Write the OMEN report** — Use the **thin template** below. **Do not duplicate Files Changed or Decisions Made** from the live log — those are already in `git log -p` and in the live log. The OMEN report carries only OMEN-specific content. Write to `~/Development/OMEN/reports/locationmapapp/session-NNN-YYYY-MM-DD.md`.

### Thin OMEN report template (~80 lines max)

```markdown
# LocationMapApp Session NNN — YYYY-MM-DD

**Branch:** master | **Commit:** <sha> | **Live log:** `~/Development/LocationMapApp_v1.5/docs/session-logs/session-NNN-YYYY-MM-DD.md`

## Summary
Two or three sentences. What shipped, what was deferred, what blocked.

## OMEN Note Acknowledgments
- NOTE-LXXX — status (acknowledged / acted on / still pending / surfaced again — Nth session)
- (only notes touched or relevant this session; skip the standing ones unless status changed)

## OMEN Directive Compliance
- OMEN-XXX — status against this session's work (compliant / no-op / conflict)
- (only directives that apply to what shipped; skip the inapplicable ones)

## Shared Engine Impact
- (any change that affects core/, the cache-proxy, or anything other projects depend on; "none" is a valid answer)

## Cross-project Notes
- (anything OMEN should relay to a sibling project — Salem, GeoInbox, RadioIntelligence, etc.; "none" is a valid answer)

## Open Items for OMEN
- (only things requiring OMEN action — operator credential rotations, directive amendments, cutovers, sign-offs)

## Reference
- Full session detail (decisions, file changes, reasoning, build results): see live log path above.
- Commit detail: `git show <sha>` in the LocationMapApp repo.
```

**Skip these sections from the old report format** (they live in the live log + git):
- Files Changed table
- Decisions Made list
- Build/test results detail
- Reasoning narrative
- Code snippets

If OMEN needs any of that, it can read the live log directly — the path is in the report header.

**A session without a live log + a SESSION-LOG.md pointer + a thin OMEN report is an incomplete session.**

---

## Project Status

> **Current state — phase, version, session count, top priority — lives in `STATE.md`.** This section is intentionally minimal so CLAUDE.md does not need to be touched every session. If you need to know what is in flight right now, read `STATE.md`.

**Critical timing:** Salem 400+ quadricentennial is 2026. **Internal ship target: August 1, 2026** (operator-confirmed S201 V1 triage). Salem 400+ peak attendance is October 2026 (~1M visitors). Sept 1 was the prior public-facing anchor; Aug 1 is the live internal goal driving session pacing.

---

## Non-Negotiable Design Principles

- Reference `GOVERNANCE.md` for full legal/compliance framework (56KB, lawyer-ready)
- Reference `DESIGN-REVIEW.md` for UX/architecture decisions
- Reference `IP.md` for V1 IP register (copyright + Form TX status + V1-novel patentable-angle backlog — pre-pivot 14-innovation register archived 2026-04-29)
- Reference `COMMERCIALIZATION.md` for monetization strategy
- **COPPA compliance required** before Play Store submission (age gate)
- **No hardcoded credentials** in APK or tracked files
- **Offline-first:** Free/cheap tiers must work without network connectivity
- **Data provenance** on all entities: data_source, confidence, verified_date, stale_after

---

## Tech Stack

- **Android app** — Kotlin, Hilt DI, OkHttp, osmdroid, Room SQLite, targeting API 34
- **Web app** — React 19, TypeScript, Vite, Leaflet, Tailwind CSS (`web/`)
- **Cache proxy** — Node.js/Express on port 4300 (`cache-proxy/`)
- **PostgreSQL** — `locationmapapp` DB, 25 `salem_*` tables (live; includes legacy `*_legacy` + production collection/witch-trials/poi/tour tables)
- **Multi-module monorepo** — `:core` (shared), `:app-salem` (WickedSalemWitchCityTour), `:routing-jvm` (shared Dijkstra + parity tests). (S242: pre-pivot `:app` and `:salem-content` modules deleted as dead code.)

---

## Architecture

```
LocationMapApp_v1.5/
├── app-salem/             — WickedSalemWitchCityTour module (the V1 product)
├── core/                  — Shared library module
├── routing-jvm/           — Shared Dijkstra walking router + parity tests
├── cache-proxy/           — Node.js caching proxy (port 4300)
├── web/                   — React 19 + TS admin tool (operator-only)
├── docs/session-logs/     — Live conversation logs (crash-recovery)
├── docs/                  — Archive, other docs
├── STATE.md               — Current project state
├── SESSION-LOG.md         — Session history
├── GOVERNANCE.md          — Legal/compliance framework
├── IP.md                  — Intellectual property register
├── COMMERCIALIZATION.md   — Monetization strategy
└── DESIGN-REVIEW.md       — UX/architecture decisions
```

---

## Cross-Project Dependencies

- **Salem JSON data** — historical-content source for narrations and POI authoring lives at `~/Development/Salem/data/json/` (49 figures, 500 facts, 80 events, 200 sources). Pulled into PG via cache-proxy admin tooling; the pre-pivot `:salem-content` JVM pipeline that used to consume this directly was deleted S242 — PG + cache-proxy scripts are now the only consumer.
- **GeoInbox** — Future photo source for POI imagery (see `~/Development/OMEN/architecture/geoinbox-concept.md`)
- **Phase 19 (LLM Integration)** — Blocked by OMEN-005 (Unified Server Engine Assessment). Must use shared conversation engine, not build parallel.

---

## Infrastructure

- **Repository**: github.com/deanmauriceellis-cloud/LocationMapApp_v1.5 (private, SSH)
- **Branch**: master
- **V1 pricing (locked S138):** $19.99 flat paid, fully offline, no ads, no LLM, IARC Teen. Tiered pricing deferred to V2.

---

## Key Reference Files

Under the amended startup rule (2026-05-04, S224): `CLAUDE.md` + `MEMORY.md` auto-load, AND `STATE.md` + the most recent `docs/session-logs/session-NNN-*.md` are read manually as part of session start. Every other file listed here is **on-demand only** and should be read only when a specific question requires it.

| File | Purpose | Read when |
|------|---------|-----------|
| `CLAUDE.md` | This file | Auto-loaded |
| `MEMORY.md` (user-config) | Persistent feedback, project, reference, user memories | Auto-loaded |
| `STATE.md` | Current snapshot — TOP PRIORITY, phase status, carry-forwards | **Read every session start** (S224 rule) |
| `docs/session-logs/session-NNN-*.md` (most recent) | Last session's live log | **Read every session start** (S224 rule) — older logs are on-demand |
| `MASTER_SESSION_REFERENCE.md` | Topic-indexed lookup of prior work — phrasing index + per-topic markers + paths | Operator says "read MASTER_SESSION_REFERENCE about [topic]" — read THIS file + only the one live log it points to (keeps context small and directed) |
| `SESSION-LOG.md` | Rolling 10-session summary | Reconstructing recent work beyond the last session |
| `SESSION-LOG-ARCHIVE.md` | Older session summaries | Historical investigation |
| `~/Development/OMEN/notes/locationmapapp.md` | OMEN action items | Addressing or acknowledging an OMEN note |
| `~/Development/OMEN/directives/ACTIVE.md` | OMEN directives | An OMEN directive is relevant to current work |
| `GOVERNANCE.md` | Legal / compliance (56KB) | Work touches legal, privacy, or compliance |
| `IP.md` | V1 IP register (copyright, Form TX status, V1-novel patentable-angle backlog) | Work touches copyright, trademark, or patent posture — full pre-pivot 14-innovation register archived at `docs/archive/IP_removed_2026-04-29.md` |
| `COMMERCIALIZATION.md` | V1 commercial posture (pricing, channels, merchant tier, open commercial items) | Work touches monetization, pricing, distribution channels, or merchant tier infrastructure — pre-pivot freemium/social vision archived at `docs/archive/COMMERCIALIZATION_removed_2026-04-29.md` |
| `DESIGN-REVIEW.md` | Architecture decisions | Work touches a design previously reviewed |

**No master plan** — deleted 2026-04-23 (S161); pre-park snapshot lives at `docs/archive/Parked_WickedSalemWitchCityTour_MASTER_PLAN_removed_2026-04-23.md`. Do not treat it as a plan-of-record.

_Document Version: 0.4.0 — Naming-disambiguation block added (S272 lint, 2026-05-16). Lean-startup protocol introduced 2026-04-23 (S161). Rolling-window rolled up by Session 111 (2026-04-09). Original by OMEN Session 003 (2026-04-04)._
