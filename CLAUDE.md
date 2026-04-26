# LocationMapApp v1.5 — Claude Code Context

> **LocationMapApp v1.5** — A multi-module Android/Web mapping platform. The primary product
> is **WickedSalemWitchCityTour**, a GPS-guided Salem, MA tourist app with historical content,
> geofence-triggered narration, and walking directions.

---

## ⚠️  IMMEDIATE PRIORITY — Pinned for next session start (set S185 close, 2026-04-26)

> **Operator override of lean-startup rule.** Read this block at session start *before* the lean greeting. Pinned here because CLAUDE.md auto-loads and these items are time-sensitive or load-bearing for V1 ship. Once an item is done, remove it from this block and move it to STATE.md.

### State as of S185 close

- **S183/S184 walking-route work is now in the AAB on the Lenovo.** Operator-curated `tour_WD1` (1 tour + 14 legs) flows PG → publish chain → asset `salem_content.db` (Room v10) → app via new `TourLeg` entity. `TourViewModel.computeTourPolyline()` reads baked legs and assembles them with the same anchor + clip rendering policy as the admin's `TourLegsLayer.drawLeg`. Walk-sim consumes the baked legs path. The 5 historical Kotlin tours are dropped from the asset; operator will re-author them in PG via admin when wanted.
- **V1 tour model is polyline-only.** A tour is just a line for the user to follow; POI geofences govern their own narration via proximity, fully independent of any tour. Tour stops with NULL `poi_id` are internal authoring waypoints (admin compute-route uses them) — they're filtered out of the asset's `tour_stops` table. `TourEngine.startTour` accepts empty stops; Historical Mode is auto-skipped when the active tour has zero user-facing stops (was building an empty whitelist that silenced every POI on the route).
- **Asset DB schema-alignment is now load-bearing.** Two latent landmines flushed in S185: (1) tables had `DEFAULT` clauses from `salem-content/create_db.sql` while Room codegen has none → `TableInfo.equals` mismatch → `fallbackToDestructiveMigration` wiped the asset on every fresh install; (2) `PRAGMA user_version` lagged the `@Database(version=N)` value → upgrade migration ran → fallback destructive. New `cache-proxy/scripts/align-asset-schema-to-room.js` is the canonical bridge — must run last in any publish chain. Any future Room version bump requires regenerating `app-salem/schemas/<DB>/<v>.json` via `kspDebugKotlin` then re-running the align script.
- **Admin tool labels at zoom ≥17.** POI markers show humanized category above + name below (mirrors Android `MarkerIconHelper.labeledDot`); cluster threshold lowered to 17 to match.
- **Upload signing key — one off-machine copy on USB**, second-medium copy still owed before first Play Console upload.
- **Operator-confirmed working in walk-sim:** The Witch House at Salem, Bewitched Sculpture (Samantha Statue), Witch House at #310 Essex. Ames Memorial Hall fix landed at session end — re-verify on next walk.

### S186 next steps — operator stated direction at S185 close: "we continue to refine"

**Claude-side technical work:**

1. **Onboarding-to-nearest-point on tour start.** Operator request from S185: when user picks a tour and isn't on the polyline, route them to the nearest point on it before walk mode begins. Path: `TourViewModel.startTour()` → fetch `tour_legs` → flatten polyline → find nearest segment to user's GPS → call `walkingDirections.getRoute(userLoc, nearestPoint)` → publish that as the active directions session. When user reaches it, switch to "tour-walking" mode.

2. **GPS-OBS heartbeat investigation.** Pre-existing Lenovo TB305FU quirk surfaced during S185 walk-sim debug: system GPS is delivering fixes (visible at `LocationManagerService` level) but the app's tour observer reports them as stale (`HEARTBEAT STALE — last fix 33s ago, narration reach-out suppressed`). Same family as the broken-motion-sensor issue noted in `feedback_lenovo_motion_sensor_broken.md`. Investigate why fresh fixes aren't reaching `lastFixAtMs` on this device.

3. **Tier 2 — admin → build pipeline auto-bake** (S180 carry-forward, escalated by S185). Wrap `publish-tours.js` + `publish-tour-legs.js` + `align-asset-schema-to-room.js` as a `preBuild`-dependent Gradle task with a "stale bake" warning when last-bake-mtime < last-admin-edit-mtime in PG. ~30-min job. Now critical since the publish chain has 3 scripts and getting any of them out of order leaves a destructive-migration footgun.

4. **Pre-AAB hard-delete dedup losers.** S123 soft-deleted 110 duplicate POIs marked in `data_source`. Before first Play Store upload: `DELETE FROM salem_pois WHERE data_source LIKE '%dedup-2026-04-13-loser%' OR data_source LIKE '%address-dedup-2026-04-13-loser%';` (verify zero FK refs first). Surfaced again in S185: 5 "Ledger" rows in PG, dedup leftovers — likely contributing to confused geofence registration.

5. **Re-author the 5 deleted Kotlin tours in PG** (S185 carry-forward). Operator wants them back as polyline-only tours via web admin. Each tour: insert `salem_tours` row, drop free waypoints onto the route in admin map, click Compute Route, eyeball-verify, re-run publish chain.

6. **Continue eyes-on smoke test on Lenovo** (S180/S181/S182 carry-forward):
   - POI detail Visit Website button → ACTION_VIEW external browser handoff.
   - Find dialog → POI detail: Reviews/Comments NOT rendered.
   - Find dialog → Directions: on-device walking router (green/dark-green polyline), NOT Google Maps.
   - Toolbar: NO Weather/Transit/CAMs/Aircraft/Radar buttons.
   - Webcam dialog: "View Live" button NOT rendered.
   - **Re-verify Ames Memorial Hall narration** after the S185 Historical Mode fix.

7. **Tier 3 — outlier POI coordinate fixes.** `salem_common_2` is 600m off (sits at 42.5203,-70.8816 instead of ~42.5232,-70.8908); `salem_willows` is mid-parking-lot at 42.535,-70.86945. Fix via `UPDATE salem_pois`, re-run publish chain, rebuild.

8. **Deferred from S179** (lower priority): Option 2 runtime mid-edge projection in `:routing-jvm` Router; walk-sim + DebugEndpoints `TourRouteLoader` cleanup → full retirement of S178 P6 dead data; water animation visual tuning; admin vertex hand-editing for `salem_tour_legs` (drag-to-edit polyline → PATCH `/legs/:n` with `manual_edits`).

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
- **Publish chain (must run in order):** `node cache-proxy/scripts/publish-tours.js` → `node cache-proxy/scripts/publish-tour-legs.js` → `node cache-proxy/scripts/align-asset-schema-to-room.js` → `./gradlew :app-salem:assembleDebug`. The align script stamps the Room `identity_hash` + `PRAGMA user_version` and rewrites every Room-managed table using the canonical schema from `app-salem/schemas/<DB>/<v>.json`. Skipping it triggers `fallbackToDestructiveMigration` on first launch and wipes the asset.
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
