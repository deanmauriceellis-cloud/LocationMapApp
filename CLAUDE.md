# LocationMapApp v1.5 — Claude Code Context

> **LocationMapApp v1.5** — A multi-module Android/Web mapping platform. The primary product
> is **WickedSalemWitchCityTour**, a GPS-guided Salem, MA tourist app with historical content,
> geofence-triggered narration, and walking directions.

---

## ⚠️  IMMEDIATE PRIORITY — Pinned for next session start (set S185 close, 2026-04-26)

> **Operator override of lean-startup rule.** Read this block at session start *before* the lean greeting. Pinned here because CLAUDE.md auto-loads and these items are time-sensitive or load-bearing for V1 ship. Once an item is done, remove it from this block and move it to STATE.md.

### State as of S186 close

- **Tour Mode narration gate is now real.** Pre-S186 a tour silently narrated everything in proximity (no gate existed). Now: during a tour, only POIs flagged `is_tour_poi=true` narrate by default (216 POIs after S186 bulk-flag); two Layers checkboxes opt-in additional classes. Implementation: `NarrationGeofenceManager.setTourMode(active, allowHistLandmarks, allowCivic)` short-circuits `isHistoricalQualified`. `TourEngine.startTour` reads tour-mode prefs and calls setTourMode.
- **Layers checkboxes have separate explore vs tour state.** Existing **POIs Hist. Landmark** + new **POIs Civic**. Explore-mode prefs (`PREF_POI_HIST_LANDMARK`, `PREF_POI_CIVIC`) default ON — "Explore Salem" is permissive, civic + hist landmark visible AND audible by default. Tour-mode prefs (`PREF_POI_HIST_LANDMARK_TOUR`, `PREF_POI_CIVIC_TOUR`) default OFF — tour is restrictive, only is_tour_poi narrates until user opts back in mid-tour. Each mode remembers its own state. The Layers menu reads `MenuEventListener.isTourActive()` to pick which pref to show.
- **`is_tour_poi=true` is the force-visible AND force-audible override.** Layers checkboxes only add or remove the *additional* class they unsilence; they never hide or silence the tour-flagged baseline. Marker filter and narration gate both short-circuit on `if (poi.isTourPoi) return true`.
- **`is_civic_poi` is a new column on `salem_pois` (Room v11).** Bulk-true for all 157 CIVIC-categorized POIs. Operator can later un-flag specific modern items in the admin tool. Web admin: PoiEditDialog "Civic POI" checkbox alongside "Tour POI" in Operational section.
- **1-hour narration dedup persists across walk-sim restarts** within a process. Was being wiped by `narrationGeofenceManager.resetSession()` on every walk-sim start (line ~1660 in SalemMainActivity). That call is now removed. Dedup only clears on a true cold start (onCreate with savedInstanceState=null at line ~674) or after the 1-hour window naturally expires. Persistent SharedPreferences mirror (S125) survives PID forks but gets wiped by uninstall/install.
- **Asset DB schema-alignment is load-bearing** (S185 rule still applies). Room v10 → v11 in S186. Identity hash `55b35822253369a8af9f91e8bdbf8656`. Any future bump requires `kspDebugKotlin` to regen `<version>.json`, update `verify-bundled-assets.js` constant, then run `align-asset-schema-to-room.js` last in publish chain. Skipping triggers `fallbackToDestructiveMigration` and wipes the asset on first launch.
- **Operator-confirmed working post-S186 walk:** First Church Meetinghouse 1692 Site visible during tour with Hist Landmark checkbox OFF (force-visible via is_tour_poi); same POI does not re-fire after walk-sim stop+start. Operator close: "good enough."
- **Upload signing key** — one off-machine copy on USB; second-medium copy still owed before first Play Console upload.

### S187 next steps — operator direction at S186 close: "we continue next session with refinement"

**Claude-side technical work:**

1. **Backfill `year_established` on ENTERTAINMENT + LODGING.** With strict pre-1860 filter, the Hist Landmark override only narratable set is currently 79 POIs (74 HIST_BLDG + 0 ENTERTAINMENT + 5 LODGING — most ENTERTAINMENT/LODGING rows have NULL year). Operator should backfill via admin one-by-one or via a research pass for the historic core (Hawthorne Hotel, etc.).

2. **Author narration text for the 8 newly tour-flagged HIST_BLDG museums.** They have `is_tour_poi=true` after the S186 bulk-flag but `has_announce_narration=false` — tour-eligible but silent. Pull from SalemIntelligence or curate manually.

3. **De-hardcode the Room identity_hash in `cache-proxy/scripts/publish-tour-legs.js`.** Currently stamps `dad6c01b8e5f8fed0ae9ff6f8ef7432d` (v10). The align script overwrites it but if anyone ran the publish chain without the align trailing, the asset would carry a stale hash. Read latest from `app-salem/schemas/<DB>/<v>.json` instead.

4. **Onboarding-to-nearest-point on tour start** (S185 carry-forward, still owed). When user picks a tour and isn't on the polyline, route them to the nearest point on it before walk mode begins. Path: `TourViewModel.startTour()` → fetch `tour_legs` → flatten polyline → find nearest segment → `walkingDirections.getRoute(userLoc, nearestPoint)` → publish as active directions session.

5. **GPS-OBS heartbeat investigation** (S185 carry-forward). Lenovo TB305FU quirk: system GPS delivers fixes but app's tour observer reports them stale. Same family as broken-motion-sensor issue. Investigate why fresh fixes aren't reaching `lastFixAtMs`.

6. **Tier 2 — admin → build pipeline auto-bake** (S180/S185 carry-forward, escalated). Wrap `publish-salem-pois.js` + `publish-tours.js` + `publish-tour-legs.js` + `align-asset-schema-to-room.js` as a `preBuild`-dependent Gradle task with stale-bake warning. Critical since the publish chain has 4 scripts now (S186 added is_civic_poi to publish-salem-pois.js too) and getting any out of order leaves a destructive-migration footgun.

7. **Pre-AAB hard-delete dedup losers** (S185 carry-forward). S123 soft-deleted 110 duplicate POIs marked in `data_source`. Before first Play Store upload: `DELETE FROM salem_pois WHERE data_source LIKE '%dedup-2026-04-13-loser%' OR data_source LIKE '%address-dedup-2026-04-13-loser%';` (verify zero FK refs first).

8. **Re-author the 5 deleted Kotlin tours in PG** (S185 carry-forward). Polyline-only tours via web admin. Each: insert `salem_tours` row, drop free waypoints, click Compute Route, eyeball-verify, re-run publish chain.

9. **Continue eyes-on smoke test on Lenovo** (S180/S181/S182 carry-forward): POI detail Visit Website ACTION_VIEW handoff; Find dialog Reviews/Comments hidden; Find Directions on-device router; toolbar gating; webcam dialog "View Live" hidden.

10. **Tier 3 — outlier POI coordinate fixes.** `salem_common_2` 600m off; `salem_willows` mid-parking-lot. Fix via `UPDATE salem_pois`, re-run publish chain, rebuild.

11. **Deferred from S179** (lower priority): Option 2 runtime mid-edge projection in `:routing-jvm` Router; walk-sim + DebugEndpoints `TourRouteLoader` cleanup → full retirement of S178 P6 dead data; water animation visual tuning; admin vertex hand-editing for `salem_tour_legs`.

**Operator-side (Claude cannot do these for you):**

9. **CRITICAL — second-medium copy of upload signing key.** USB copy on `/media/witchdoctor/writable/wickedsalem-upload-key-backup-2026-04-26/` exists. Recommend encrypted cloud or second USB stored elsewhere. Single-medium backups fail.
10. **Form TX copyright registration** — lawyer handling, hard deadline **2026-05-20** (statutory damages window).
11. **Google Play Developer Account + identity verification.** $25 one-time. Multi-week lead time.
12. **Privacy Policy public hosting + in-app link.** Waiting on lawyer approval. V1-minimal draft at `docs/PRIVACY-POLICY-V1.md`.
13. **Merchant / payments profile** in Play Console.

### Key paths to remember

- Upload keystore: `~/keys/wickedsalem-upload.jks` (off-machine USB backup, currently disconnected)
- Signing properties: `~/.gradle/gradle.properties` (mode 0600)
- AAB / APK artifacts: `app-salem/build/outputs/bundle/release/app-salem-release.aab` / `apk/release/app-salem-release.apk`
- **Publish chain (must run in order):** `node cache-proxy/scripts/publish-salem-pois.js` → `node cache-proxy/scripts/publish-tours.js` → `node cache-proxy/scripts/publish-tour-legs.js` → `node cache-proxy/scripts/align-asset-schema-to-room.js` → `./gradlew :app-salem:assembleDebug`. The align script auto-discovers the latest `app-salem/schemas/<DB>/<v>.json`, stamps Room `identity_hash` + `PRAGMA user_version`, and rewrites every Room-managed table using the canonical `createSql`. Skipping it triggers `fallbackToDestructiveMigration` on first launch and wipes the asset.
- Lenovo install: `adb -s HNY0CY0W uninstall com.destructiveaigurus.katrinasmysticvisitorsguide && adb -s HNY0CY0W install <apk>` (NEVER `install -r`).
- Room schema export: `app-salem/schemas/com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase/<version>.json` (regenerate via `./gradlew :app-salem:kspDebugKotlin -x verifyBundledAssets` after any `@Database(version = N)` bump).
- Admin walking-route endpoints: `cache-proxy/lib/admin-tours.js`. Web admin route panel: `web/src/admin/{TourTree,AdminMap}.tsx`.
- Archived (do-not-execute) OSM merge plan: `docs/archive/S182-osm-pedestrian-routing-merge_archived_2026-04-26-misdiagnosis.md`
- OMEN credential audit: `~/Development/OMEN/docs/credential-audit-2026-04-05.md` (Amendment 2026-04-26)
- Build commands: `./gradlew :app-salem:assembleDebug` (debug APK) / `:app-salem:bundleRelease` (signed AAB) / `:app-salem:assembleRelease` (signed APK)

---

## Session Start Protocol — lean (2026-04-23)

Operator rule: **startup reads must be kept minimal to conserve tokens.** At session start, rely on what the harness auto-loads (`CLAUDE.md` + `MEMORY.md`). Everything else is deferred until a specific question requires it.

Do ONLY this at session start:

0. **Sync with remote** — `git fetch origin`, compare HEAD vs `origin/master`. Pull if behind. Alert if diverged. Never work on stale state.
1. **Open live conversation log** — Create `docs/session-logs/session-NNN-YYYY-MM-DD.md` with a minimal header (date, branch, HEAD, recovery state). This is the canonical record of the session and the crash-recovery mechanism — append significant actions, decisions, and code changes as they happen.
2. **One-line greeting** — Brief the operator: "Session N opened. What's on the docket?" Do NOT pre-emptively summarize `STATE.md`, phase status, carry-forwards, the most recent session log, or OMEN items.
3. **Await direction.** Never execute work before the operator confirms scope.

`CLAUDE.md` and `MEMORY.md` load automatically. That is the complete startup read set.

### Everything else is ON-DEMAND

Read these files only when a specific question requires them — never at session start:

- `STATE.md` — when the operator asks for status, current phase, TOP PRIORITY, or carry-forward items.
- `SESSION-LOG.md` — when reconstructing recent work. **Never** at session start.
- `SESSION-LOG-ARCHIVE.md` — only for historical investigation.
- `docs/session-logs/session-NNN-*.md` — read a prior live log only when recovering from a killed session OR when the operator asks about a specific past session.
- `~/Development/OMEN/notes/locationmapapp.md` — when addressing or acknowledging an OMEN note.
- `~/Development/OMEN/directives/ACTIVE.md` — when an OMEN directive is relevant to current work.
- Reference docs (`GOVERNANCE.md`, `IP.md`, `COMMERCIALIZATION.md`, `DESIGN-REVIEW.md`, `CURRENT_TESTING.md`) — on demand only.

### No master plan

As of 2026-04-23 (S161), the master plan was deleted and its pre-park snapshot moved to `docs/archive/`. There is no plan-of-record. If a multi-phase plan is needed, the operator declares one explicitly or we rebuild it in a Plan.

### Recovery from a killed session

If the prior session was killed (context overflow, disconnection), read the most recent `docs/session-logs/session-NNN-YYYY-MM-DD.md` before proceeding. Otherwise the normal lean startup applies.

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

**Critical timing:** Salem 400+ quadricentennial is 2026. App must be in Play Store by **September 1, 2026** to capture October's 1M+ visitors. This is the only date that constrains every other planning decision.

---

## Non-Negotiable Design Principles

- Reference `GOVERNANCE.md` for full legal/compliance framework (56KB, lawyer-ready)
- Reference `DESIGN-REVIEW.md` for UX/architecture decisions
- Reference `IP.md` for intellectual property register (14 patentable innovations)
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
- **PostgreSQL** — `locationmapapp` DB, 9 `salem_*` tables
- **Multi-module monorepo** — `:core` (shared), `:app` (generic), `:app-salem` (WickedSalemWitchCityTour), `:salem-content` (JVM content pipeline)

---

## Architecture

```
LocationMapApp_v1.5/
├── app/                   — Generic LocationMapApp module
├── app-salem/             — WickedSalemWitchCityTour module
├── core/                  — Shared library module
├── salem-content/         — JVM content pipeline (imports from ~/Development/Salem/data/json/)
├── cache-proxy/           — Node.js caching proxy
├── web/                   — React web app
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

- **Salem JSON data** — `salem-content/` pipeline reads from `~/Development/Salem/data/json/` (49 figures, 500 facts, 80 events, 200 sources). Salem's JSON schema is a shared engine dependency tracked in OMEN.
- **GeoInbox** — Future photo source for POI imagery (see `~/Development/OMEN/architecture/geoinbox-concept.md`)
- **Phase 19 (LLM Integration)** — Blocked by OMEN-005 (Unified Server Engine Assessment). Must use shared conversation engine, not build parallel.

---

## Infrastructure

- **Repository**: github.com/deanmauriceellis-cloud/LocationMapApp_v1.5 (private, SSH)
- **Branch**: master
- **V1 pricing (locked S138):** $19.99 flat paid, fully offline, no ads, no LLM, IARC Teen. Tiered pricing deferred to V2.

---

## Key Reference Files

Under the lean-startup rule (2026-04-23), only `CLAUDE.md` and `MEMORY.md` are read at session start — both auto-load. Every other file listed here is **on-demand only** and should be read only when a specific question requires it.

| File | Purpose | Read when |
|------|---------|-----------|
| `CLAUDE.md` | This file | Auto-loaded |
| `MEMORY.md` (user-config) | Persistent feedback, project, reference, user memories | Auto-loaded |
| `MASTER_SESSION_REFERENCE.md` | Topic-indexed lookup of prior work — phrasing index + per-topic markers + paths | Operator says "read MASTER_SESSION_REFERENCE about [topic]" — read THIS file + only the one live log it points to (keeps context small and directed) |
| `STATE.md` | Current snapshot — TOP PRIORITY, phase status, carry-forwards | Operator asks for status, or current phase needs confirming |
| `docs/session-logs/session-NNN-*.md` | Per-session live log | Recovering from a killed session, or operator asks about a specific past session |
| `SESSION-LOG.md` | Rolling 10-session summary | Reconstructing recent work — NEVER at session start |
| `SESSION-LOG-ARCHIVE.md` | Older session summaries | Historical investigation |
| `~/Development/OMEN/notes/locationmapapp.md` | OMEN action items | Addressing or acknowledging an OMEN note |
| `~/Development/OMEN/directives/ACTIVE.md` | OMEN directives | An OMEN directive is relevant to current work |
| `GOVERNANCE.md` | Legal / compliance (56KB) | Work touches legal, privacy, or compliance |
| `IP.md` | Patent / copyright register | Work touches a patentable innovation |
| `COMMERCIALIZATION.md` | Business model (pre-S138 detail) | Work touches monetization — V1 posture is in this file's preamble, not in CLAUDE.md |
| `DESIGN-REVIEW.md` | Architecture decisions | Work touches a design previously reviewed |
| `CURRENT_TESTING.md` | Test checklist | Entering Phase 10 or running regression |

**No master plan** — deleted 2026-04-23 (S161); pre-park snapshot lives at `docs/archive/Parked_WickedSalemWitchCityTour_MASTER_PLAN_removed_2026-04-23.md`. Do not treat it as a plan-of-record.

_Document Version: 0.3.0 — Lean-startup protocol introduced 2026-04-23 (S161). Rolling-window rolled up by Session 111 (2026-04-09). Original by OMEN Session 003 (2026-04-04)._
