# LocationMapApp v1.5 — Project State

## Last Updated: 2026-04-09 Session 110 (side quest: runaway narration loop fix + verbose persistent logging + cache-proxy circuit breaker + POI proximity encounter tracker)

## TOP PRIORITY — Next Session (S111)
**1. (FIRST) Protocol optimization.** Operator queued this as the first task for S111 at the end of S110. Session start/end have gotten too slow — ~2,500 lines of context reads at start (with chunked re-reads), ~3,800 read + ~900 written at end, with the same session story duplicated across 4 places (live log, SESSION-LOG.md, STATE.md, OMEN report). Concrete scope:
- (A) Compress STATE.md to current-state-only (~200 lines max, no more session-by-session history)
- (B) Archive SESSION-LOG.md sessions older than ~S100 to `SESSION-LOG-ARCHIVE.md` (currently 2,190+ lines, needs to drop to ~500)
- (C) Make OMEN session report a thin pointer to the live log instead of duplicating content (~80 lines instead of 250)
- (D) Stop touching CLAUDE.md every session
- (E) Update CLAUDE.md session-start protocol for parallel + head-only reads
- (F) Update CLAUDE.md session-end protocol so the session story is written ONCE in the live log; everywhere else gets a 2-3 line pointer
- Net impact: ~540 lines saved per start, ~2,800 lines saved per end, ~80% latency cut on the read phase. See `project_next_session_priority.md` memory file for full detail.

**2. (SECOND) Phase 9P.B Step 9P.11 — Highlight Duplicates wiring.** Carried over from S107 → S108 → S109 → S110. Phase 9P.A complete; 9P.6/9P.7/9P.8/9P.9/9P.10/9P.10b all landed (S101→S106). S108 added admin-UI polish; **S109 AND S110 were side quests** triggered by field-test bug reports. Neither progressed 9P.11. **Four sessions in a row** have not advanced 9P.11 — once the protocol optimization lands, 9P.11 is the next concrete code task.

**Next step (9P.11):** Wire the existing `Highlight Duplicates` header button (currently a console.log stub at `AdminLayout.tsx:69-73`) to the existing `/api/admin/salem/pois/duplicates?radius=15` endpoint built in 9P.5 (S100). On click → fetch the duplicate clusters → draw red rings on the admin map at the cluster center coordinates → click a red ring opens a side panel listing the duplicate group with Compare / Pick Winner buttons (basic UX, full merge UI deferred to v1.5 per master plan §1393). Toggle off → rings clear. Reference: `WickedSalemWitchCityTour_MASTER_PLAN.md` Step 9P.11 (search the heading). After 9P.11: Phase 9P.C publish loop (9P.12-9P.14: rebuild `salem-content/salem_content.sql` from PG, wire the Publish button, end-to-end APK rebuild verification).

**Skip 9P.10a** (Linked Historical Content tab is still a stub showing "No links yet — see Phase 9Q") until Phase 9Q has run.

## S110 fixes summary (everything that actually shipped this five-pivot side-quest session)

**Bug:** mid-drive process death at 16:38 left the activity dead, but on relaunch the narration system entered a runaway reach-out loop at home — cycling through Salem POIs at 10-second intervals against a 15-minute-old GPS fix. Operator was sitting in their living room hearing the app announce Benjamin Punchard, Shoreman → Salem Athenaeum → Magika → Salem Public Library → repeat.

**Four root causes identified and fixed:**

1. **GPS staleness blindness** — `currentLocation.observe` callbacks didn't carry a fix age. A 15-minute-old fix looked identical to a fresh one. **Fix:** added `lastFixAtMs: Long` field on the activity, stamped on every observer call. New `GPS-OBS` per-fix log line + 10s heartbeat coroutine that logs `HEARTBEAT STALE / RECOVERED / ok` transitions.

2. **Reach-out doesn't check GPS freshness** — `SalemMainActivityNarration.kt:117` called `findNearestUnnarrated()` against whatever the most recent fix was, regardless of how stale. **Fix:** staleness gate at the silence reach-out site bails with `REACH-OUT SUPPRESSED: GPS stale` if `lastFixAtMs > GPS_STALE_THRESHOLD_MS (30s)` old.

3. **Reach-out distance too generous** — `findNearestUnnarrated()` returned POIs at 113-145m. Constants were `150m` real-GPS / `200m` walk-sim. **Fix:** `REACH_RADIUS_M = 15.0` (real GPS), `REACH_RADIUS_WALKSIM_M = 25.0` (walk sim). Operator direction: "Salem has a lot of POIs clustered together."

4. **Activity recreate wipes narration dedup state** — `narratedAt` map was instance-state on `NarrationGeofenceManager`, which was constructed fresh in `initNarrationSystem()` on every activity init. Orientation change → all 817 POIs eligible to fire again. **Fix:** `NarrationGeofenceManager` promoted to Hilt `@Singleton` with `@Inject constructor(@ApplicationContext context: Context)`. `narratedAt` and `cooldowns` survive activity recreate. `loadPoints()` no longer clears cooldowns. Activity field changed from `var ... = null` → `@Inject lateinit var`. Eight call sites converted from `?.foo()` to `.foo()`.

**Verbose persistent logging added** (operator: "Any kind of verbose logging we can add to the application so we have a good log for capturing issues?"):

- **`DebugLogger.FileLogSink`** — daily-rotated file at `/sdcard/Android/data/com.example.wickedsalemwitchcitytour/files/logs/debug-YYYYMMDD.log`. Single background thread + `BufferedWriter` with `flush()` after every line. 7-day retention. Wired in `WickedSalemApp.onCreate()` BEFORE any other init logs. Captures everything that the in-memory ring buffer captures, plus survives process death (the gap that broke today's bug investigation).
- **`GPS-OBS` per-fix telemetry** — every fix gets `fix lat=X lng=Y acc=Wm speed=Vmps bearing=Z° gap=Tms` with explicit `gap=` measurement of the time between consecutive fix arrivals.
- **`GPS-OBS` heartbeat coroutine** — ticks every 10s, logs periodic `HEARTBEAT ok — last fix age=Xs` every 60s, immediate `HEARTBEAT STALE/RECOVERED` on state transitions, throttled `HEARTBEAT still-stale` once per 30s while held in stale state.
- **`LIFECYCLE` observer** — `onResume / onPause / onStop / onDestroy` with `changingConfig` and `finishing` flags so post-mortems can distinguish process death from configuration change from user-initiated background.
- **PID prefix in every log line** — `[N|PID]` format prevents sequence-number collisions when multiple process lifetimes get appended to the same daily file. Single grep `\|2315]` filters to one process.
- **Process-start banner** — `════════ process started PID=N ════════` as the first entry of every fresh process.
- **Compact network exception logging** — `DebugLogger.e()` detects `ConnectException`, `SocketTimeoutException`, `UnknownHostException`, `CircuitOpenException` (and walks the cause chain 5 levels deep) and logs a single compact WARN line instead of an ERROR + 16+ line stack trace. Real exceptions still get the full stack so unexpected bugs aren't hidden.

**Cache-proxy circuit breaker** (operator: "If we can't connect locally to our server, it should not crash or affect the app."):

- **`LocalServerHealth`** singleton in `core/util/network/`. State: single `lastFailureAtMs: AtomicLong`. Methods: `isUp()`, `recordFailure(host, cause)`, `recordSuccess(host)`. Constants: `OPEN_DURATION_MS = 60_000L`, `LOCAL_HOSTS = setOf("10.0.0.229", "localhost", "127.0.0.1")`. Logs OPEN/CLOSED transitions only.
- **`CircuitOpenException`** sentinel `IOException` thrown by the interceptor when the circuit is open. Recognized by `DebugLogger.e()` for compact logging.
- **`LocalServerCircuitBreakerInterceptor`** OkHttp interceptor. Only acts on requests whose host is in `LOCAL_HOSTS`; everything else passes through unchanged (MBTA / NWS / OpenSky / public APIs unaffected). On TCP failure → `recordFailure` + rethrow. On any returned response → `recordSuccess`. While circuit OPEN → throw `CircuitOpenException` immediately.
- **Wired into 13 OkHttpClient.Builder sites** — 12 repository files in `core/data/repository/` + the inline geocode client in `app-salem/.../SalemMainActivityFind.kt`. WeatherRepository and MbtaRepository have pre-existing per-API-key/User-Agent interceptors; the circuit breaker goes BEFORE those in the chain.

**POI proximity encounter tracker** (operator: "When we get close to POI's — I want us to track how close for how long (max/min distance and time) so I can review that later"):

- **`PoiEncounter`** Room entity with composite string PK `${poiId}|${firstSeenAtMs}` for `INSERT OR REPLACE` semantics without auto-id juggling. Fields: poi_id, poi_name, poi_lat, poi_lng, first_seen_at_ms, last_seen_at_ms, duration_ms, min_distance_m, max_distance_m, fix_count.
- **`PoiEncounterDao`** with `upsert`, `getRecent(sinceMs)`, `getMostRecent(limit)`, `getClosestApproachForPoi`, `deleteOlderThan`, `count`, `deleteAll`.
- **`PoiEncounterTracker`** Hilt `@Singleton`. In-memory `Map<poiId, PoiEncounter>` of currently-active encounters. `recordObservation(nearby, nowMs)` filters by `PROXIMITY_RADIUS_M = 100m`, opens new encounters / updates existing / closes ones that left the radius. Fire-and-forget DB writes via IO scope. `flushAll()` for diagnostic logging on activity destroy. `pruneStaleAtStartup()` matches `GpsTrackRecorder` pattern. 7-day retention pruned hourly.
- **`UserDataDatabase` schema bumped 1 → 2** with `fallbackToDestructiveMigration` (rolling 24h GPS journey is acceptable to lose on schema upgrade).
- **Activity wiring:** `@Inject lateinit var poiEncounterTracker: PoiEncounterTracker`, `pruneStaleAtStartup()` in `onCreate()`, `flushAll()` in `onDestroy()`, `recordObservation()` call in `SalemMainActivityTour.kt#updateTourLocation()` immediately after `narrationGeofenceManager.checkPosition()`.
- **Verified live** via the in-app walk simulator: 9 encounters recorded over a 30-second simulated walk through downtown Salem. `Levesque Funeral Home` row example: `min_dist=9m`, `max_dist=83m`, `duration=40.3s`, `fix_count=35`. Database schema confirmed via `sqlite3 .schema poi_encounters` — all 11 columns + 2 indexes (`first_seen_at_ms`, `poi_id`) present.

**Bottom-left layout fix** (the original first pivot of the session): the GPS journey toggle FAB from S109 was hidden behind the narration sheet because both were anchored to the bottom of the screen at incompatible margins. Moved the GPS toggle into the existing bottom-left tool stack as a 4th button (matching POI/Walk/magnify TextView style), bumped the stack's `marginBottom` from 56dp → 168dp so all 4 buttons clear the narration sheet's worst-case peek height. End layout (top → bottom of left column): GPS · POI · Walk · x1 (magnify).

## S110 known-but-not-fixed (still carry-forward candidates)

- **`DWELL_MS = 3_000L` in `NarrationGeofenceManager` is dead code.** Declared, never wired. Single jittery fixes can fire false-positive narration in dense areas like the Essex/Charter cluster. Recommended fix is a per-POI `firstSeenInside: Long?` map with "require 2 consecutive fixes inside the ring" semantics — express as fix-count, not milliseconds, since at 5s adaptive sampling the millisecond value is meaningless. ~10 lines in `NarrationGeofenceManager.checkPosition()`.
- **Narration queue stale-pop check** — in dense POI clusters all 5+ in-range POIs queue immediately on the first fix and play back-to-back even after the operator walks away. Stale-pop check ("verify still in `2× geofenceRadiusM` before playing") is the cheapest fix (~5 lines).
- **Pre-existing GPS log redundancy** — every fix logs 4 lines from 4 layers (`LocationManager → ViewModel → GPS-OBS → SalemMainActivity`). All at `D/` level, separable by tag. Pre-existing duplication, not a S110 regression. Worth a future cleanup but not blocking.
- **Narration sheet has no `setPeekHeight`** — the deeper architectural reason buttons get covered. Real fix would either set a peek height + collapsed state or lift the bottom-left tools to follow the sheet's slide. The 168dp marginBottom is a workaround that handles common case but not a fully expanded sheet.
- **POI encounter review screen** — current review path is `adb pull` + `sqlite3` or grep the persistent log. A future session can add an in-app review screen, probably in the operator's debug menu.

## SECONDARY ISSUE surfaced by the new logging (not a bug, observation only)
**Cache-proxy at `10.0.0.229:4300` is unreachable from the tablet.** Confirmed via repeated ECONNREFUSED to that address from the tablet's IP `10.0.0.159`. The laptop's cache-proxy may not be running, or its IP may have changed. The new circuit breaker handles this gracefully (fast-fails, single-line WARN log per failure, no app crash). MBTA / public APIs continue to work through the interceptor's host-only filter. The Salem narration data is bundled in the Room DB so the offline-core experience is fully functional. **No code action required** — surfaced for the operator's next field-test setup so they can `bash bin/restart-proxy.sh` if they want POI prefetch to work.

## Salem Oracle API integration (Phase 9P.10b — added Session 101)
The Salem sibling project exposes a dev-side LLM-backed API at `http://localhost:8088` that the admin tool can call to compose, revise, summarize, or expand POI descriptions. Reference: `~/Development/Salem/docs/oracle-api.md` (492 lines, owned by Salem). Key endpoints: `/api/oracle/status` (health), `/api/oracle/ask` (main composition with `current_poi_id` context pinning, 5-15s latency), `/api/oracle/pois`, `/api/oracle/poi?id=`, `/api/oracle/newspapers`. Salem corpus is read-only from this API. The Oracle backs gemma3:27b on the operator's RTX 3090. **Critical conceptual point:** Salem's 63 historical POIs (`salem_poi_*` IDs) are DISTINCT from LocationMapApp's 1,720 POIs — same geography, different datasets. The Oracle's `current_poi_id` field accepts any string for context pinning; pass the LocationMapApp POI id directly. Spec lives in master plan as Step 9P.10b.

**Also surface at session start:**
1. **OMEN-002 history rotation** still pending — needs operator action: `ALTER USER witchdoctor WITH PASSWORD '<new>';` + log into OpenSky portal to regenerate `OPENSKY_CLIENT_SECRET`, then update `cache-proxy/.env`. Test password `salem-tour-9P3-test` in `cache-proxy/.env` should also be replaced with a real strong value before relying on the admin auth in any non-test context.
2. **NOTE-L014 OMEN-008 Privacy Policy** still on the books, still blocking RadioIntelligence Salem ingest. Async writing work — can be drafted between admin-tool sessions.
3. **NOTE-L013** debug `VoiceAuditionActivity.kt` cleanup still pending.
4. **Phase 9T.9** walk simulator end-to-end verification still TODO.

## Phase 9P.A status (Backend Foundation) — COMPLETE
- ✓ **9P.1 DONE** (S98) — `salem_narration_points` table (42 cols, 7 indexes)
- ✓ **9P.2 DONE** (S98) — Importer landed 814 narration rows; PG canonical for narration
- ✓ **9P.3 DONE** (S99) — HTTP Basic Auth middleware; `/cache/clear` locked down
- ✓ **9P.4 DONE** (S99) — Admin POI write endpoints; six routes, all three POI kinds, soft-delete-aware
- ✓ **9P.4a DONE** (S100) — `historicTourDefault` field on `PoiCategory`, 6 categories opted into historic tour mode
- ✓ **9P.5 DONE** (S100) — Duplicates detection endpoint with cross-kind clustering
- ✓ **9P.A.3 DONE** (S101) — `salem_tour_pois` (45 rows) and `salem_businesses` (861 rows) migrated to PG via `cache-proxy/scripts/import-tour-pois-and-businesses.js`. **Admin tool now functional for ALL three POI kinds.** PG totals: tour=45, business=861, narration=814 → **1,720 active POIs canonical in PG**. Bundled `salem-content/salem_content.db` is now a downstream artifact and will need Phase 9P.C publish loop to regenerate it before APK builds pick up admin edits.

## Phase 9P.B status (Admin UI) — IN PROGRESS
- ✓ **9P.6 DONE** (S101) — TS port of 22-category POI taxonomy → `web/src/config/poiCategories.ts`
- ✓ **9P.7 DONE** (S102) — `/admin` route + `web/src/admin/AdminLayout.tsx` shell. Path-based dispatch in `main.tsx` (no `react-router-dom` dep). Header has Highlight Duplicates / Publish stubs, Oracle status pill placeholder (wires up in 9P.10b), Logout via XHR 401 trick. Three-pane layout: header / left tree pane (320px) / center map pane.
- ✓ **9P.8 DONE** (S103) — POI tree via `react-arborist` v3.4.3. `web/src/admin/PoiTree.tsx` (~370 lines) fetches all 1,720 active POIs (3 calls: tour sequential first to fire the Basic Auth prompt cleanly, then business + narration in parallel), groups them as **kind → category → POI rows** (3 levels, not 4 — narration `subcategory` is NULL across all 814 rows, tour `subcategories` is JSONB-list not a single column), renders with virtualization, supports name search (`searchTerm`/`searchMatch`), show-deleted toggle (client-side filter, no re-fetch), counts per kind/category, click → `onSelect({kind, poi})` event into AdminLayout. **S104 update:** added `onDataLoaded` callback + `externalByKind` prop so AdminLayout can hoist the dataset and feed both panes from one shared snapshot.
- ✓ **9P.9 DONE** (S104) — Admin map view via Leaflet + `leaflet.markercluster` v1.5.3. `web/src/admin/AdminMap.tsx` (~370 lines) renders the shared `byKind` snapshot as a single `L.markerClusterGroup` with kind-color SVG divIcons (tour=red `#dc2626`, business=blue `#2563eb`, narration=green `#059669`). Imperative cluster layer driven via `useMap()` (react-leaflet-markercluster wrapper is stuck on RL v4; we're on v5). Marker click → `onPoiSelect` lifts to AdminLayout. Tree-click sets `selectedPoi` → `<FlyToSelected>` calls `map.flyTo([lat,lng], max(currentZoom,17), {duration: 0.6})` and the matching marker gets a gold ring (icon recoloured in a separate effect — not a full cluster rebuild). Marker drag captures origin at `dragstart`, on `dragend` opens a confirm modal (overlay div, not a Leaflet popup) showing from/to coords + Haversine distance; Confirm POSTs to `/admin/salem/pois/:kind/:id/move`, calls `onPoiMoved` which patches the row in AdminLayout's shared `byKind` so the tree stays consistent; Cancel or HTTP failure reverts the marker via `marker.setLatLng(origin)`. Soft-deleted rows are filtered from the map (still visible in the tree behind the show-deleted toggle). `disableClusteringAtZoom: 18` so the operator can drag individual POIs at deepest zoom; `maxClusterRadius: 50`. Top-right legend shows kind colors + counts. Soft-deleted excluded from map but tree still toggles them. AdminLayout lifted state: `byKind` (populated by PoiTree's `onDataLoaded`), `selectedPoi` (driven by both tree and map clicks). PoiTree consumes the same snapshot via `externalByKind` after the initial load. `npx tsc --noEmit` clean. CSS reset for `.admin-poi-marker` strips leaflet's default white divIcon background. `leaflet.markercluster/dist/MarkerCluster{,.Default}.css` imported in `web/src/index.css`.
- ✓ **9P.10 DONE** (S105) — POI edit dialog (tabbed). `web/src/admin/PoiEditDialog.tsx` (~1200 lines) + `web/src/admin/poiAdminFields.ts` (~75 lines, TS mirror of cache-proxy whitelists). Headless UI v2 `<Dialog>` + `<TabGroup>` modal with kind-aware tabs: General · Location · Hours & Contact · Narration · Provenance · Linked Historical (9P.10a stub) · Danger Zone. Uses `react-hook-form` `formState.dirtyFields` for partial-update payloads against `PUT /api/admin/salem/pois/:kind/:id`. JSONB textareas pretty-print on load and `JSON.parse` on submit (parse failure surfaces inline error and aborts save). Numeric coercion, date YYYY-MM-DD slicing, boolean checkboxes for tour-only flags. Category field is free-text + `<datalist>` of observed values from the loaded snapshot (tree currently has 7 tour categories, 19 business_types, 29 narration categories). Save flow: PUT → `onSaved(kind, updated)` patches the row in AdminLayout's shared `byKind` (mirrors `handlePoiMoved`). Soft-delete in Danger Zone: `DELETE /api/admin/salem/pois/:kind/:id` → `onDeleted` marks row deleted in `byKind`, tree ghosts it, map drops it. **Open trigger split:** marker click (`handleMapSelect`) opens the dialog AND selects; tree click (`handleTreeSelect`) only selects + flies the map. Cancel with dirty fields → `window.confirm("Discard and close?")`. Footer shows dirty-field count or "No changes". `npx tsc --noEmit` clean, `npm run build` clean.
- ⏳ **9P.10a** — Linked Historical Content tab (currently a stub) — blocked on Phase 9Q
- ✓ **9P.10b DONE** (S106) — Salem Oracle "Generate with AI" integration. New file `web/src/admin/oracleClient.ts` (~225 lines) — typed wrapper for the Salem Oracle API at `http://localhost:8088` (configurable via `VITE_SALEM_ORACLE_URL`). Exports `getStatus`, `ask`, `listPois`, `isAskOk` plus typed shapes (`OracleStatus`, `OracleAskOk`/`OracleAskErr`, `OraclePrimarySource`, `OraclePoiSummary`). 5s timeout on status/catalog endpoints, 120s default on `ask` (60s minimum per spec). `OracleNetworkError` distinguishes network failures from successful HTTP responses with `error` envelopes. **Direct browser → Oracle calls** (permissive CORS — no Vite proxy needed, per master plan §1341). `AdminLayout.tsx` updated: new `oraclePill` state machine (loading/ready/unavailable) polled at mount + every 30s via `refreshOracleStatus`; click pill to force re-poll. New `<OraclePill>` component renders the three states with rich tooltips (ready: corpus counts + history turn count; down: start command + reason). State mirrored to `PoiEditDialog` via new `oracleAvailable` prop + `onOracleRefresh` callback. `PoiEditDialog.tsx` updated: new `OracleLauncher` component (banner variant on Narration tab, compact variant under General tab description), nested `<Dialog>` (z-1100) sub-dialog with prompt textarea + "Reset history" checkbox (auto-flips OFF after first generation so iterate works) + Generate button → `oracleClient.ask({ question, current_poi_id: poi.id, reset })`, spinner panel during call, returned `text` rendered in slate panel with `whitespace-pre-wrap`, "Insert into short_narration / long_narration / description" buttons filtered through `has(field)`, "Iterate" button forces `reset:false`, primary_sources rendered in collapsible `<details>` with score+attribution+verbatim+gloss in amber callouts. Insert flow: `setValue(field, text, { shouldDirty: true, shouldTouch: true })` → existing react-hook-form dirty-tracking → existing PUT pipeline picks it up automatically. **Audit log:** `localStorage[salem-oracle-audit]` capped at 500 entries FIFO; logged on **insert** (not on generate — generate-but-discard is just thinking out loud) with `{ ts, poi_id, poi_kind, field, question, text, primary_sources, history_turn_count }`. Concurrent calls serialized client-side per master plan §1383. **End-to-end smoke test passed S106:** live `POST /api/oracle/ask` returned all 9 expected `OracleAskOk` fields, 8 `primary_sources` populated correctly, `current_poi_id="lma_smoke_test_poi_id_not_a_real_one"` accepted (confirms arbitrary string contract), `route: "ORACLE_LLM_RICH"`, `used_external_context: false` when `current_poi_id` omitted. `npx tsc --noEmit` clean, `npm run build` clean (539 modules, 802 kB / 234 kB gz, +12 kB / +3 kB gz over S105).
- ⏳ **9P.11** — Highlight Duplicates wiring — NEXT
- ⏳ **9P.13** — Publish wiring (Phase 9P.C publish loop)

## POI Inventory PDF (S101)
- `tools/generate-poi-inventory-pdf.py` — Python + reportlab Platypus, sources from bundled `salem-content/salem_content.db`
- Output: `docs/poi-inventory-2026-04-08.pdf` (gitignored), 1,163 pages, 2.5 MB, all 1,723 POIs with every field
- venv at `tools/.poi-pdf-venv/` (gitignored, reportlab 4.4.10)
- Re-run: `tools/.poi-pdf-venv/bin/python tools/generate-poi-inventory-pdf.py`

## OMEN-002 status
- ✓ **File-side cleanup DONE** (S99) — `bin/restart-proxy.sh` no longer contains hardcoded credentials. Sources `cache-proxy/.env` (gitignored) via `set -a; source ...; set +a`. `cache-proxy/.env.example` committed as a template. Both old credential strings (`fuckers123`, `6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR`) confirmed absent from tracked file.
- ⏳ **History rotation pending** — Both credentials still exist in git history. Full closure requires operator action: rotate DB password, rotate OpenSky client secret via portal. See TOP PRIORITY above.

**Three-phase expanded vision (Session 97 planning):**
- **Phase 9P** — POI Admin Tool. ~4-5 sessions. Operator-facing CMS for POI metadata. Two new steps added in S97: 9P.4a (per-mode category visibility schema) and 9P.10a (read-only "Linked Historical Content" tab in edit dialog).
- **Phase 9Q** — Salem Domain Content Bridge. ~2-3 sessions. Builds the `building_id → poi_id` translation layer that Salem JSON requires (Salem uses `building_id`, LocationMapApp uses POI; the bridge lives on the LocationMapApp side). Imports 425 buildings + the new 202-article newspaper corpus (Salem session 044, ~13.5 hours TTS-ready content via `tts_full_text` field). Backfills historical FKs by graph traversal: `building_id → POI` populates `salem_historical_figures.primary_poi_id`, `salem_historical_facts.poi_id`, `salem_timeline_events.poi_id`, `salem_primary_sources.poi_id` (all currently NULL).
- **Phase 9R** — Historic Tour Mode (App-Side). ~4-6 sessions. Opt-in tour mode that suppresses ambient POI narration, plays curated 1692 historical content as user walks. **Hybrid model:** user picks a chapter, chapter is a curated route through 4-8 POIs in chronological story order, content fires by GPS proximity but in guaranteed-narrative order. 7 chapters drafted: Strange Behavior in the Parris Household → First Accusations → Examinations → Court of Oyer and Terminer → Bridget Bishop & First Execution → Summer of Death → Aftermath. Newspaper articles play as long-form readings (~4 min each, one per chapter). Per-mode category visibility (free-roam keeps current toggles, historic tour gets restrictive defaults).

**Total runway for full vision:** 9-12 sessions across 9P + 9Q + 9R. Salem 400+ launch deadline is September 1, 2026 — ship 9P first to get the operator-facing tool in hand, assess 9Q+9R against runway after.

**Cross-project dependency (Salem):** Phase 9Q consumes the Salem Oracle newspaper corpus (NEW in Salem session 044). Schema at `~/Development/Salem/data/json/newspapers/README.md`. Each article has `events_referenced` JSONB field — the load-bearing join key for the bridge chain `newspaper → event → building → POI`.

**Deferred (not abandoned):** OMEN-008 Privacy Policy drafting (NOTE-L014). Still on the books, still blocking RadioIntelligence Salem ingest, but no longer top of stack. Can be drafted asynchronously (it's writing, not coding) between admin-tool sessions if appetite allows.

## Current Direction
- **Multi-module platform refactor** — `:core`, `:app`, `:app-salem`, `:salem-content`
- **WickedSalemWitchCityTour** (`app-salem/`) — GPS-guided Salem, MA tourist app
- **Tiered pricing**: Free (map+POIs+1 tour preview+ads) / $4.99 Explorer / $9.99 Premium / $49.99-mo (Salem LLM)
- Master plan: `WickedSalemWitchCityTour_MASTER_PLAN.md` (16 phases + 4 future phases + 5 UX transformation phases, supersedes all other plans)
- **Phases 1-9 complete** (core development done)
- **Phase 9A COMPLETE** — Splash screen, Esri satellite tiles (zoom 19), cinematic zoom, tile source picker, welcome dialog, tour selection, tour JSON files
- **Phase 9A+ IN PROGRESS — Tour Hardening & Offline Foundation** (HIGHEST PRIORITY):
  - ~~Offline map tiles~~ **DONE** — 3,295 tiles (Esri zoom 16-19 + OSM zoom 16-18), 40MB SQLite archive, verified offline
  - ~~Salem Witch Trials tour~~ **DONE** — 19-stop tour seeded from Kate data, 8 new POIs, 4 tours total
  - ~~Pre-computed walking route geometry~~ **DONE** — OSRM routes for all 19 segments + loop-back, 471 points, 8.6km loop
  - ~~GPS walk simulator~~ **DONE** — in-app Walk button + debug HTTP endpoint, interpolates route at walking pace
  - ~~Map follows GPS~~ **DONE** — continuous GPS follow (real + simulated), 100m dead zone bypassed in manual mode
  - ~~POI layer defaults~~ **DONE** — only Tourism/History, Civic, Entertainment ON by default; bbox filter respects prefs
  - ~~Splash screen~~ **DONE** — WitchKitty.png + Creepster font "Wicked Salem Witch Tours"
  - ~~Map magnify~~ **DONE** — x1-x5 scale toggle (1.0-3.0x) without changing zoom level
  - End-to-end tour walk test, tour UX polish, geocode verify (deprioritized — superseded by 9T)
- **Phase 9T IN PROGRESS — Salem Walking Tour Restructure** (HIGHEST PRIORITY):
  - ~~9T.1 Boundary audit~~ **DONE** — 539 downtown POIs, 307 narration priority, 113 Wave 1
  - ~~9T.2 Schema~~ **DONE** — NarrationPoint entity (34 fields), DAO, Room DB v3
  - ~~9T.3 Dialog UI~~ **DONE** — Proximity dock (bottom POI icons) + narration bottom sheet
  - ~~9T.4 Geofence system~~ **DONE** — NarrationGeofenceManager, 2min cooldown, session tracking
  - ~~9T.5 Corridors~~ **DONE** — CorridorGeofence + 10 Salem streets
  - ~~9T.6 Narration content~~ **DONE** — All 814 POIs narrated (W1:112 + W2:85 + W3:109 + W4:508)
  - ~~9T.7 Walking loops~~ **DONE** — Quick (30min), Standard (60min), Grand (90min)
  - ~~9T.8 Integration~~ **DONE** — Wired GPS→geofence→dock+sheet in activity
  - 9T.9 Verification — TODO (walk simulator end-to-end test)
- **NEW: Salem data scraped** — 848 POIs from Destination Salem + Haunted Happenings + OSM, merged/deduped
- **NEW: 5 Salem-specific POI categories** — witch_shop, psychic, ghost_tour, haunted_attraction, historic_house (17→22 total)
- **NEW: 1,416 POI icons generated** — 8 flavors (evil/cute/devil/psycho/undead/demon/zombie/witchcraft) × 177 subtypes
- **NEW: Bark TTS installed** — ~/AI-Studio/bark/, RTX 3090 CUDA, voice clip generation ready
- **NEW: Splash audio generated** — warlock + witch cackle with sox post-processing (phaser/reverb/echo)
- **NEW: Tour 1 renamed** — "Salem Essentials" → "Walking Through Salem" (flexible downtown discovery)
- **NEW: Downtown street route** — 352-point OSRM route through Salem streets (PEM→Essex→Common→Liberty→Charter→Derby→back), walk simulator follows real roads
- **NEW: 861 businesses in Room DB** — up from 23 (23 curated + 848 scraped)
- **NEW: 817 narration points in Room DB** — Wave 1 (112), Wave 2 (85), Wave 3 (109), Wave 4 (511) — 100% Overpass POI coverage
- **NEW: Walk sim narration mode** — 3x entry geofence radius, 200m distance-first reach-out, 500ms gaps, session reset on start
- **NEW: Walk sim interaction fixes** — tap/long-press stops walk sim, interpolation residual bug fixed
- **NEW: Silence reach-out** — 5s silence → auto-narrates nearest valuable POI within 500m (200m in walk sim), never dead air
- **NEW: Priority narration queue** — adPriority DESC (merchants first), priority ASC (historical value), distance tiebreaker; walk sim uses distance-first
- **Vision: Salem = Disneyland** — downtown is offline entertainment park, 10-mile radius over internet
- **Monetization: merchant tiers** — every POI controls its own content (icons, voice, narration, ads) based on payment
- **Phase 9B-9D after**: Feature tier matrix, user settings, contextual alerts
- **Phase 10**: Production readiness — Firebase, photos, emulator verification, DB hardening
- **Phase 11**: Branding, ASO & Play Store launch — target **September 1, 2026**
- **Phases 12-14**: Social media, fieldwork, community engagement — **NO CODE, can start NOW**
- **Critical timing**: 2026 is Salem's 400th anniversary (Salem 400+) — once-in-a-generation marketing window
- **NEW: Test device confirmed** — Lenovo TB305FU tablet (Android 15, ARM64, Magisk root, Frida 17.9.1, SELinux Permissive, Google TTS installed). Serial: HNY0CY0W
- **Offline-first VERIFIED** — Map tiles bundled (40MB), tour engine + geofences + TTS all offline-ready. Remaining gap: walking directions (OSRM)
- **NEW: Satellite tiles upgraded** — USGS (zoom 16 max) → Esri World Imagery (zoom 19, street-level)
- **Live conversation logs** — `docs/session-logs/` for crash recovery (append-only, written during session)
- **UI direction** — Tour guide view is the primary screen; LocationMapApp map view becomes a utility
- **Branding work started** — AI Art Studio installed locally (`~/AI-Studio/`), splash screen concepts generated ("WitchKitty"), image morph transitions prototyped
- **GeoInbox initialized** — `github.com/deanmauriceellis-cloud/GeoInbox`, Gmail API authenticated (`omenpicturefeed@gmail.com`)
- **Proxy IP updated** — all source files changed from `10.0.0.4` to `10.0.0.229`
- **Tour engine complete**: 3 pre-defined tours + custom tour builder + time-budget tours + route optimization
- **GPS geofence triggers**: approach/entry/exit detection with auto-narration via Android TTS
- **Walking directions**: OSRM integration via OSMBonusPack, turn-by-turn, "Walk Here" from any POI
- **Events calendar**: 20 curated Salem events + "On this date in 1692"
- **Offline-first architecture**: bundled Room DB + offline map tiles (zoom 12-15, ~60MB) + API sync when online
- Content pipeline: 49 figures, 500 facts, 40 timeline events, 200 sources, 37 POIs, 23 businesses, 3 tours, 60 stops, 20 calendar events
- **Data provenance & staleness** on all entities (local Room + remote PostgreSQL)
- **Revenue streams**: Google Ads (free tier), merchant partnerships, loyalty program, LLM subscription
- **New in Session 76**: Firebase Crashlytics/Analytics, POI photos (Wikimedia/NPS/field), social media strategy, community engagement plan, competitive landscape analysis, iOS/PWA roadmap, gamification/virality features
- **Direct competitors identified**: Action Tour Guide ($9.99, multi-language), Salem On Your Own (25+ years)
- **Key partners identified**: Destination Salem (Bridie O'Connell), Salem Chamber ($360/yr), NPS VECE Division, Salem 400+

## Architecture
- **Android app** (Kotlin, Hilt DI, OkHttp, osmdroid) targeting API 34
- **Web app** (React 19, TypeScript, Vite, Leaflet/react-leaflet, Tailwind CSS) — cross-platform frontend at `web/`
- **Cache proxy** (Node.js/Express on port 4300) — transparent caching layer, CORS-enabled for web app
- **PostgreSQL** (`locationmapapp` DB) — permanent storage for POIs, aircraft sightings, and Salem content (9 new `salem_*` tables)
- **Split**: App/Web → Cache Proxy → External APIs (Overpass, NWS, Aviation Weather, MBTA, OpenSky, Windy Webcams)
- **Multi-module monorepo**: `:core` (shared library), `:app` (generic LocationMapApp), `:app-salem` (WickedSalemWitchCityTour), `:salem-content` (JVM content pipeline)
- **Dual database**: Local Room SQLite (offline-first, bundled asset) + remote PostgreSQL (API sync via `/salem/*` endpoints)
- **Provenance on all data**: data_source, confidence, verified_date, created_at, updated_at, stale_after

## What's Working
- Map display (osmdroid), GPS tracking with manual override (long-press), custom zoom slider
- **17 POI categories, 153 subtypes** with submenu refinement (central config in `PoiCategories.kt`)
  - 5dp colored dots; zoom ≥ 16: labeled icons with type + name
  - Layer-aware LiveData `Pair<String, List>`, viewport-only display via `/pois/bbox`
  - All layers default ON except aircraft (OFF)
- **Weather dialog** (v1.5.34): current conditions, 48-hour hourly, 7-day outlook, expandable alerts
  - Proxy `/weather?lat=&lon=` (5 NWS calls), 22 vector icons, auto-fetch every 30min
  - Toolbar icon shows current conditions; red border when alerts active
- **METAR** — rich text markers (temp, wind, sky), bbox passthrough, deferred load, human-readable tap info
- **NWS NEXRAD radar** tiles (Iowa State Mesonet) + animated radar (7-frame 35-min loop), default 35% opacity
- **Dark mode** (v1.5.44): toolbar moon icon toggles between MAPNIK and CartoDB Dark Matter tiles, persisted
- **Share POI** (v1.5.44): 5th action button in POI detail dialog, shares name/address/phone/hours + Google Maps link
- **Favorites** (v1.5.44, web v1.5.68): star icon in POI detail dialog, SharedPreferences+JSON storage (Android) / localStorage (web), dedicated Favorites cell in Find dialog/panel
- **Smart fuzzy search** (v1.5.51): pg_trgm similarity + keyword→category hints in Find dialog search bar
  - Typo-tolerant: "Starbcks" finds Starbucks, "Dunkin Donts" finds Dunkin' Donuts
  - ~110 keyword→category mappings: "historic" → Tourism & History, "gas" → Fuel & Charging, "tattoo" → Shopping, etc.
  - **Cuisine-aware search** (v1.5.57): "pizza" matches name OR `cuisine=pizza` tag (~12.8k cuisine-tagged POIs)
  - Combined queries: "food italian" → Food & Drink category + fuzzy "italian" name + cuisine match
  - Distance expansion: 50km → 100km → 100mi/160,934m (stops at ≥50 results)
  - **Results sorted by distance** (v1.5.56): nearest first, regardless of fuzzy match score
  - Rich result rows: bold name, detail line (cuisine/brand), category label in category color
  - Header hint bar: count + category + "refine to narrow" in title bar next to "Find" (200 result limit)
- **MBTA transit** — live vehicles (buses, CR, subway) with directional arrows + staleness detection
  - Commuter rail: next-stop ETA badge on labeled markers (batch predictions API)
  - ~270 train stations: tap → arrival board (30s auto-refresh) → trip schedule
  - ~7,900 bus stops: viewport-filtered, zoom ≥ 15, tap → arrival board
  - Vehicle detail dialog: info + Follow/View Route/Arrivals buttons
- **Aircraft tracking** (OpenSky) — rotated airplane icons, callsign, altitude-colored, SPI emergency
  - 18 state vector fields, configurable refresh (30s–5min), zoom ≥ 10 guard
  - Dedicated **Air** menu (toggle, frequency, auto-follow)
- **Slim toolbar + status line + grid dropdown** (v1.5.40): replaced 2×5 icon grid with compact toolbar bar
  - **Toolbar** (40dp): Weather | Home | spacer | Dark Mode | Alerts | Grid | About (v1.5.55)
  - **Status line** (24dp): priority-based info bar — GPS coords+weather when idle, follow/scan/alert info when active
    - `StatusLineManager.kt`: 8 priority levels (GPS_IDLE → GEOFENCE_ALERT), set/clear/updateIdle API
    - All banner functions migrated from dynamic TextView creation to StatusLineManager
    - Geofence alerts show zone-type-colored background on status line
  - **Grid dropdown**: PopupWindow with 8 labeled buttons (icon+text) in 2×4 grid
    - Row 1: Transit, Webcams, Aircraft, Radar | Row 2: POI, Utility, Find, Go To
  - `setupSlimToolbar()` + `showGridDropdown()` in AppBarMenuManager; `fitsSystemWindows` on AppBarLayout
  - **Home icon** (v1.5.55): house icon centers map on GPS at zoom 18 with 800ms animation
  - **About icon** (v1.5.55): info circle shows version/copyright/contact dialog (DestructiveAIGurus.com)
  - Weather icon dynamically updates to show current conditions; red border when alerts active
  - Alerts icon dynamically colored by severity: gray (none), blue (INFO), yellow (WARNING), red (CRITICAL), pulsing red (EMERGENCY)
  - Debug `/state` includes `statusLine` field with text + priority name
- **Go to Location** (v1.5.32): Photon geocoder autocomplete via proxy `/geocode`, US-only, 500ms debounce
- **Aircraft follow mode**: global icao24 tracking, 3-strike tolerance, flight path trail (altitude-colored polyline, DB history + live, 1000-point cap)
- **Auto-follow aircraft (POI Builder)**: ≥10k ft, 20-min rotation, smart switching (altitude/POI/US bounds)
- **Webcam layer** (Windy API): 18 categories, preview + live player, 0.5° min bbox, 10-min cache
- **Social layer** (v1.5.45) — auth, POI comments, real-time chat
  - **Auth**: device-bonded registration (register once, no login/logout), Argon2id hashing, JWT access tokens (15min) + refresh tokens (365d)
    - Family-based refresh token rotation with reuse revocation
    - `AuthRepository.kt`: auto-refresh expired tokens, SharedPreferences storage
    - Profile dialog: avatar initial, display name, role badge (no logout — device-bonded)
    - Register-only dialog from grid dropdown "Social" button (no login toggle)
  - **POI Comments**: star ratings (1-5), upvote/downvote, threaded (parent_id), soft delete
    - `CommentRepository.kt`: fetch/post/vote/delete with auth
    - Comments section in POI detail dialog: author, relative time, rating stars, vote arrows
    - "Add Comment" sub-dialog with star rating selector
  - **Chat**: Socket.IO real-time messaging, REST room list/create/history
    - `ChatRepository.kt`: Socket.IO connect/disconnect, room join/leave, send/receive
    - Auto-creates "Global" room on proxy startup
    - Room list dialog: name, member count, description, tap to enter
    - Chat room dialog: message bubbles (own=blue, others=gray), author name, send bar, fitsSystemWindows
    - Socket.IO connect-before-join: suspendCancellableCoroutine waits for actual connection
    - Create room dialog: name + optional description
  - **Grid dropdown**: expanded to 3×4 (12 buttons) — Row 3: Social, Chat, Profile, Legend
  - **Proxy deps**: argon2, jsonwebtoken, socket.io; `http.createServer` + Socket.IO attach
  - **Android dep**: `io.socket:socket.io-client:2.1.0`
  - **8 DB tables**: users, auth_lookup, refresh_tokens, poi_comments, comment_votes, chat_rooms, room_memberships, messages
  - **Security hardening** (v1.5.47): server-side sanitization + validation + rate limiting + login lockout
    - `sanitizeText(str, maxLen)` / `sanitizeMultiline(str, maxLen)`: strip HTML, control chars, enforce caps
    - Rate limiting: auth 10/15min (IP), comments 10/1min (user), rooms 5/1hr (user), Socket.IO 30/60s (socket)
    - Login lockout: 5 fails → 15min IP lock, auto-cleanup 30min interval
    - Validation: displayName 2-50 chars unicode whitelist, password max 128, email format, osmType/roomType enums, rating integer
    - JSON body limit 16kb; `/auth/debug/users` requires auth + owner/support role
    - Client-side: `InputFilter.LengthFilter` on all social EditTexts, min-length checks, email format, comment char counter
    - Proxy dep: express-rate-limit ^7.5.1
  - **Skipped for now**: COPPA age gate, email encryption, content moderation, OAuth, monthly partitioning
- **Cap detection & retry-to-fit**: halves radius on 500-element cap, 20km fuzzy hints, MIN_RADIUS 100m
- **Populate POIs v2** (grid scanner): probe-calibrate-spiral with recursive 3×3 subdivision, 10km initial probe, narrative banner
- **10km Probe Populate** (v1.5.42): expanding spiral of 10km wide probes for low-density POI discovery
  - Crosshairs pan (no zoom), 30s countdown, status line with last probe count + recommended fill radius
  - `estimateFillRadius()`: targets ~200 POIs per search area, scales by density
  - Fill Probe Populate: stub for future implementation
- **Vehicle follow**: tap → track, staleness detection (>2 min), POI prefetch along route
- **Find dialog** (v1.5.26, overhauled v1.5.50): category grid → subtype grid → distance-sorted results, long-press filter mode
  - Counts scoped to 10km radius around map center; auto-fit cell heights (36dp min, 120dp max cap)
  - ScrollView wrapping on both grids — large categories (26 Shopping, 18 Entertainment) scroll instead of clipping
  - Dialog height 85% of screen; search bar fixed above scrollable grid
  - Unlimited distance on `/db/pois/find`: scope expands 50km → 200km → 1000km → global (no bbox)
  - Smart fuzzy search bar (v1.5.51): pg_trgm fuzzy + keyword hints, 1000ms debounce, rich 3-line result rows
  - Favorites cell (v1.5.44): gold star, first in grid, shows count badge, tap for sorted favorites list
  - **Filter and Map** (v1.5.53, improved v1.5.56): teal button at top of search results → exclusive map view
    - Clears all other layers (transit, aircraft, webcams, METAR, geofences, radar), stops background jobs
    - Force-labels all result markers at any zoom; keeps current position, adaptive zoom (18→out until results visible)
    - Status line "Showing N label — tap to clear" with FIND_FILTER priority; tap exits and restores layers
    - Scroll/zoom handlers guarded — no layer reloads while active
- **POI Detail Dialog** (v1.5.27): info rows, website (3-tier waterfall), action buttons (Directions/Call/Reviews/Map/Share)
  - Tap any POI marker on map → opens detail dialog directly (v1.5.46)
  - Star icon in header (v1.5.44): tap to add/remove from favorites, filled/outline toggle
- **Legend dialog** (v1.5.25): 7 sections, Utility menu, driven from `PoiCategories.ALL`
- Transit zoom guard (zoom ≤ 10 hides markers), POI display (zoom ≥ 10 + max 5000 markers), adaptive radius hints
- **Idle auto-populate** (v1.5.33): 10-min GPS stationarity → full scanner, 45s delays, GPS-centered
  - **POI density guard** (v1.5.48): checks `/db/pois/counts` 10km radius before starting; skips if ≥100 POIs nearby
  - Touch-to-stop: any map tap cancels idle populate, resets 10-min idle timer
  - Any UI activity (grid dropdown, dialogs, toolbar buttons) also resets idle timer
  - State preservation: stopped idle scanner resumes from last ring/point (not from scratch)
  - State cleared on: long-press, GPS move >100m, goToLocation, manual populate start
- **Overpass queue**: serialized upstream, 10s min gap, per-client fair queue, covering cache, content hash delta, **retry with 3-endpoint rotation + exponential backoff** (proxy + app-side)
  - **Queue cancel** (`POST /overpass/cancel`): flushes all queued requests for a client; called on stop follow, stop populate, GPS move
- **Proxy heap reduction** (v1.5.60): eliminated poiCache Map (268k entries, ~90MB), LRU cap on main cache (7,214 → 2,000 entries)
  - All POI endpoints (`/pois/*`, `/poi/:type/:id`) query PostgreSQL directly; `collectPoisInRadius()` uses PostgreSQL
  - Import buffer: lightweight array drains every 15min; `MAX_CACHE_ENTRIES` env var (default 2000)
  - Heap: 643MB → 208MB; `poi-cache.json` eliminated; `cache-data.json` 320MB → 57MB
- **Scan cell coverage** (v1.5.59): ~1.1km grid cells track when areas were last scanned
  - Decision flow: exact cache → covering cache → cache-only merge → **scan cell FRESH → serve from PostgreSQL** → upstream Overpass
  - `X-Cache: CELL` + `X-Coverage: FRESH/STALE/EMPTY` headers on all `/overpass` responses
  - Configurable: `SCAN_FRESHNESS_MS` (default 24h), `MIN_COVERAGE_POIS` (default 10)
  - Persisted to `scan-cells.json`; debug endpoint `GET /scan-cells`
  - Idle/manual populate: FRESH cells **skipped entirely** (no search, no countdown, no subdivision)
  - Silent fill: FRESH cells show brief "Coverage fresh" banner (1.5s vs 3s)
- **Bbox snapping**: METAR/webcams 0.01°, aircraft 0.1° for cache hit rate
- **Silent fill** (v1.5.28): single Overpass search on startup/restore/long-press (3-4s delay)
- **Geofence Alert System** (v1.5.35-36) — multi-zone spatial alerting with 5 zone types
  - **GeofenceEngine** (`util/GeofenceEngine.kt`): JTS R-tree spatial index, point-in-polygon, proximity detection
    - `loadZones()` builds JTS polygons, inserts into STRtree; `checkPosition()` returns `List<GeofenceAlert>`
    - Entry detection (CRITICAL for TFR, WARNING for others), proximity within 5nm + bearing ±60°, exit (INFO)
    - Cooldowns: 5min proximity, 10min entry; configurable proximity threshold
    - `ZoneType` enum: TFR, SPEED_CAMERA, SCHOOL_ZONE, FLOOD_ZONE, RAILROAD_CROSSING
    - Severity mapping per zone type; school-zone time filter (weekdays 7-9 AM, 2-4 PM)
    - `getZoneCountByType()` for per-type counts in debug
  - **5 zone types** with map overlays (all default OFF except TFR):
    - **TFR** (v1.5.35): red fill/outline, FAA TFR data via `tfr.faa.gov` scraping
    - **Speed cameras** (v1.5.36): orange fill/outline, Overpass `highway=speed_camera`, 200m alert radius
    - **School zones** (v1.5.36): amber fill/outline, Overpass `amenity=school`, polygon or 300m circle
    - **Flood zones** (v1.5.36): blue fill/outline (darker for A/V codes), FEMA NFHL ArcGIS Layer 28
    - **Railroad crossings** (v1.5.36): dark fill/yellow outline, Overpass `railway=level_crossing|crossing`, 100m radius
  - **Zone-type-aware UI**: detail dialog adapts color bar + metadata by zone type; alert banner color per type
  - **Proxy endpoints**: `/tfrs`, `/cameras`, `/schools`, `/flood-zones`, `/crossings` (all bbox-based)
  - **GeofenceRepository** (`data/repository/GeofenceRepository.kt`): consolidated fetch for 4 non-TFR zone types
    - `generateCircleShape()` companion helper for point → polygon conversion
  - **Viewport loading**: `scheduleGeofenceReload()` loads all enabled zone types with zoom guards
    - Cameras: zoom ≥ 10; Schools/Flood/Crossings: zoom ≥ 12
  - **GPS integration**: user GPS checks geofence (no altitude); followed aircraft checks with baroAltitude
  - **Alerts menu**: TFR Overlay (ON), Speed Camera/School Zone/Flood Zone/Railroad Crossing toggles (OFF), Alert Sound, Alert Distance
  - Debug: `/geofences`, `/geofences/alerts` endpoints; `geofences` field in `/state` with per-type counts
- **Downloadable Geofence Databases** (v1.5.37-38) — offline SQLite databases for pre-built zone data
  - **Database Manager dialog**: Alerts menu → "Zone Databases", shows catalog with download/delete per database
  - **`GeofenceDatabaseRepository.kt`**: catalog fetch, file download with progress callback, SQLite zone loading
  - **Proxy endpoints**: `GET /geofences/catalog` (enriched with file sizes), `GET /geofences/database/:id/download`
  - **4 databases** (220,657 zones total):
    - `military-bases.db`: 1,944 US military installation polygon boundaries (MILITARY_BASE, severity 2)
    - `excam-cameras.db`: 109,500 worldwide speed/red-light cameras (SPEED_CAMERA, severity 1, 200m radius)
    - `nces-schools.db`: 101,390 US K-12 public schools (SCHOOL_ZONE, severity 1, 300m radius)
    - `dji-nofly.db`: 7,823 DJI drone no-fly zones (NO_FLY_ZONE, severity 2, variable radius)
  - **Build scripts**: `build-military.js` (ArcGIS polygon), `build-excam.js` (XZ/NDJSON), `build-nces.js` (ArcGIS point), `build-dji-nofly.js` (CSV)
  - Auto-updates `catalog.json` with actual zone counts and file sizes after each build
  - `lzma-native` npm dependency for XZ decompression (ExCam cameras)
- **Database Import & Export** (v1.5.39) — import custom zone databases, export installed databases
  - **Import SQLite .db**: SAF file picker → schema validation (db_meta + zones tables, required columns, zone count) → install to geofence_databases
  - **Import CSV**: SAF file picker → config dialog (name, zone type, default radius) → parsed with column aliases → converted to SQLite with full schema + bbox indexes
  - **Export**: installed databases shareable via Android share intent (FileProvider)
  - **Duplicate handling**: detects existing database ID, shows overwrite confirmation dialog
  - **Local-only databases**: catalog merges locally-imported databases not in remote catalog; works offline
  - **Database Manager UI**: "IMPORT .DB" / "IMPORT CSV" buttons at top; "EXPORT" button on installed cards
- **MODULE_ID** (v1.5.55): searchable copyright+module constant in every source file (131 files)
  - Kotlin: `private const val MODULE_ID` after imports; JS: `const MODULE_ID`; Shell: `MODULE_ID=`; SQL: `-- MODULE_ID:`; XML: `Module:` in copyright comment
- **Startup**: loads cached bbox only (no per-category Overpass queries); 504/429 don't shrink radius hints
- **Debug HTTP server** (v1.5.18): port 8085, 24 endpoints, `adb forward` + `curl`
  - `DebugHttpServer.kt` (singleton) + `DebugEndpoints.kt`; lifecycle-aware, double-start guard
  - `relatedObject` on all markers; reflection-based marker tap; `runOnMain` helper
  - Test suite: `test-app.sh` (30+ tests), `overnight-test.sh`, `morning-transit-test.sh`

## External API Routing
| API | App Endpoint | Proxy Route | TTL |
|-----|-------------|-------------|-----|
| Overpass POI | `http://10.0.0.4:3000/overpass` | POST /overpass | 365 days |
| NWS Alerts | `http://10.0.0.4:3000/nws-alerts` | GET /nws-alerts | 1 hour |
| NWS Weather | `http://10.0.0.4:3000/weather?lat=&lon=` | GET /weather (composite) | 5min–24h per section |
| METAR | `http://10.0.0.4:3000/metar?bbox=...` | GET /metar?bbox=s,w,n,e | 1 hour (per bbox) |
| Radius Hints | `http://10.0.0.4:3000/radius-hint` | GET+POST /radius-hint | persistent |
| POI Query | `http://10.0.0.4:3000/pois/...` | GET /pois/stats, /pois/export, /pois/bbox, /poi/:type/:id | live (PostgreSQL) |
| Aircraft | `http://10.0.0.4:3000/aircraft?...` | GET /aircraft?bbox=s,w,n,e or ?icao24=hex | 15 seconds |
| Webcams | `http://10.0.0.4:3000/webcams?...` | GET /webcams?s=&w=&n=&e=&categories= | 10 minutes |
| DB Import | `http://10.0.0.4:3000/db/import` | POST /db/import (manual), GET /db/import/status | live |
| DB POI Query | `http://10.0.0.4:3000/db/pois/...` | GET /db/pois/search, /nearby, /stats, /categories, /coverage | live |
| DB POI Find | `http://10.0.0.4:3000/db/pois/...` | GET /db/pois/counts (10-min cache), /db/pois/find | 10 min / live |
| POI Website | `http://10.0.0.4:3000/pois/website` | GET /pois/website?osm_type=&osm_id=&name=&lat=&lon= | permanent (DB) |
| DB POI Lookup | `http://10.0.0.4:3000/db/poi/...` | GET /db/poi/:type/:id | live |
| DB Aircraft | `http://10.0.0.4:3000/db/aircraft/...` | GET /db/aircraft/search, /recent, /stats, /:icao24 | live |
| FAA TFRs | `http://10.0.0.4:3000/tfrs?bbox=...` | GET /tfrs?bbox=s,w,n,e | 5min list / 10min detail |
| Speed Cameras | `http://10.0.0.4:3000/cameras?bbox=...` | GET /cameras?bbox=s,w,n,e | 24 hours |
| Schools | `http://10.0.0.4:3000/schools?bbox=...` | GET /schools?bbox=s,w,n,e | 24 hours |
| Flood Zones | `http://10.0.0.4:3000/flood-zones?bbox=...` | GET /flood-zones?bbox=s,w,n,e | 30 days |
| Railroad Crossings | `http://10.0.0.4:3000/crossings?bbox=...` | GET /crossings?bbox=s,w,n,e | 7 days |
| Geofence Catalog | `http://10.0.0.4:3000/geofences/catalog` | GET /geofences/catalog | live (from catalog.json) |
| Geofence DB Download | `http://10.0.0.4:3000/geofences/database/:id/download` | GET /geofences/database/:id/download | static file |
| Geocode | `http://10.0.0.4:3000/geocode?q=...` | GET /geocode?q=&limit= | 24 hours |
| Auth | `http://10.0.0.4:3000/auth/*` | POST /auth/register, /login, /refresh, /logout; GET /auth/me | — |
| Comments | `http://10.0.0.4:3000/comments/*` | GET /comments/:type/:id; POST /comments, /comments/:id/vote; DELETE /comments/:id | live |
| Chat Rooms | `http://10.0.0.4:3000/chat/*` | GET /chat/rooms, /chat/rooms/:id/messages; POST /chat/rooms | live |
| Chat Socket.IO | `ws://10.0.0.4:3000` | join_room, leave_room, send_message, typing; new_message, user_typing | real-time |
| MBTA Bus Stops | `http://10.0.0.4:3000/mbta/bus-stops` | GET /mbta/bus-stops | 24 hours |
| MBTA Vehicles | direct (api-v3.mbta.com) | not proxied | — |
| MBTA Stations | direct (api-v3.mbta.com/stops) | not proxied | — |
| MBTA Predictions | direct (api-v3.mbta.com/predictions) | not proxied | — |
| MBTA Schedules | direct (api-v3.mbta.com/schedules) | not proxied | — |
| Radar tiles | direct (tilecache.rainviewer.com) | not proxied | — |

## Map Interaction Model
- **Tap POI marker**: opens POI detail dialog directly (info, comments, actions)
- **Single tap on map** (empty area): stops all following (vehicle/aircraft) and all population tasks (populate, 10km probe, idle populate, silent fill)
- **Long press (~2s)**: enter manual mode, center map (auto-zoom to 18 if <18), search POIs at location, fetch weather + alerts
- **Scroll/pan**: displays cached POIs for visible area via proxy `/pois/bbox`
- **Tap vehicle marker**: vehicle detail dialog (route, status, speed) with Follow / View Route / Arrivals buttons
- **Tap station marker**: arrival board dialog (real-time predictions), tap train → trip schedule dialog
- **Tap bus stop marker**: arrival board dialog (real-time bus predictions)
- **Tap aircraft marker**: follow mode (map tracks globally via icao24, banner shows flight info)
- **Tap follow/populate banner**: stop following or stop populate scan
- **Tap find filter banner**: exit filter mode, restore normal POI display
- **Tap filter-and-map status line**: exit exclusive filter view, restore all layers
- **Find toolbar icon**: search bar + favorites cell + category grid → subtype grid → distance-sorted results → tap to open POI detail dialog
- **Find results "Filter and Map" button**: enter exclusive map view showing only those results with forced labels
- **Go to Location toolbar icon**: geocoder dialog → type address → pick result → map navigates + POI search
- **POI detail dialog**: info rows + star (favorite) + Load Website button + action buttons (Directions, Call, Reviews, Map, Share)
- **Find long-press**: filter map to show only that category's POIs
- **Utility → Populate POIs**: systematic grid scanner spirals from map center

## Key Files

### MainActivity Decomposition (13 extension-function files)
- `app/src/main/java/.../ui/MainActivity.kt` — main map activity, lifecycle, setup, observers (1,996 lines)
- `app/src/main/java/.../ui/MainActivityFind.kt` — Find dialog, fuzzy search, Filter and Map (2,161 lines)
- `app/src/main/java/.../ui/MainActivityGeofences.kt` — geofence overlays, zone loading, alerts (1,011 lines)
- `app/src/main/java/.../ui/MainActivityTransit.kt` — MBTA vehicles, stations, bus stops (977 lines)
- `app/src/main/java/.../ui/MainActivitySocial.kt` — auth, comments, chat UI (860 lines)
- `app/src/main/java/.../ui/MainActivityPopulate.kt` — POI populate scanner, idle populate (782 lines)
- `app/src/main/java/.../ui/MainActivityWeather.kt` — weather dialog, alerts dialog (607 lines)
- `app/src/main/java/.../ui/MainActivityAircraft.kt` — aircraft overlays, follow mode, flight trail (534 lines)
- `app/src/main/java/.../ui/MainActivityDebug.kt` — debug HTTP server accessor methods (332 lines)
- `app/src/main/java/.../ui/MainActivityDialogs.kt` — POI detail, vehicle detail dialogs (269 lines)
- `app/src/main/java/.../ui/MainActivityRadar.kt` — radar tile overlay, animation (211 lines)
- `app/src/main/java/.../ui/MainActivityMetar.kt` — METAR markers, info display (208 lines)
- `app/src/main/java/.../ui/MainActivityHelpers.kt` — small utility functions (39 lines)

### ViewModel Decomposition (7 ViewModels)
- `app/src/main/java/.../ui/MainViewModel.kt` — Location + POI viewport only (215 lines)
- `app/src/main/java/.../ui/SocialViewModel.kt` — auth, comments, chat (200 lines)
- `app/src/main/java/.../ui/TransitViewModel.kt` — MBTA trains, subway, buses, stations (165 lines)
- `app/src/main/java/.../ui/GeofenceViewModel.kt` — TFR, cameras, schools, flood, crossings, databases (286 lines)
- `app/src/main/java/.../ui/WeatherViewModel.kt` — weather, METAR, webcams, radar (108 lines)
- `app/src/main/java/.../ui/FindViewModel.kt` — Find dialog DB queries, text search (86 lines)
- `app/src/main/java/.../ui/AircraftViewModel.kt` — aircraft tracking, flight history (79 lines)

### Menu & UI (app-specific)
- `app/src/main/java/.../ui/menu/AppBarMenuManager.kt` — toolbar menus, grid dropdown (812 lines)
- `app/src/main/java/.../ui/menu/PoiCategories.kt` — central config for all 17 POI categories (153 subtypes)
- (MenuPrefs.kt and MenuEventListener.kt moved to `:core`)
- `app/src/main/java/.../ui/MarkerIconHelper.kt` — icon/dot rendering with cache
- `app/src/main/java/.../ui/WeatherIconHelper.kt` — NWS icon code → drawable mapping
- `app/src/main/java/.../ui/StatusLineManager.kt` — priority-based toolbar status line manager

### Core Module (`:core` — shared library)
- `core/src/main/java/.../data/model/Models.kt` — 50+ data classes (PlaceResult, MbtaVehicle, AircraftState, etc.)
- `core/src/main/java/.../core/AppException.kt` — sealed exception hierarchy
- `core/src/main/java/.../data/repository/PlacesRepository.kt` — Overpass POI search
- `core/src/main/java/.../data/repository/WeatherRepository.kt` — NWS + METAR
- `core/src/main/java/.../data/repository/AircraftRepository.kt` — OpenSky aircraft
- `core/src/main/java/.../data/repository/MbtaRepository.kt` — MBTA vehicles
- `core/src/main/java/.../data/repository/FindRepository.kt` — Find dialog DB queries + text search
- `core/src/main/java/.../data/repository/WebcamRepository.kt` — Windy webcams
- `core/src/main/java/.../data/repository/TfrRepository.kt` — FAA TFR fetch via proxy
- `core/src/main/java/.../data/repository/GeofenceRepository.kt` — speed cameras, schools, flood zones, crossings
- `core/src/main/java/.../data/repository/GeofenceDatabaseRepository.kt` — downloadable geofence DB catalog/download/import/export
- `core/src/main/java/.../data/repository/AuthRepository.kt` — JWT auth, token storage, auto-refresh
- `core/src/main/java/.../data/repository/CommentRepository.kt` — POI comments CRUD
- `core/src/main/java/.../data/repository/ChatRepository.kt` — Socket.IO chat + REST rooms/messages
- `core/src/main/java/.../data/location/LocationManager.kt` — GPS location management
- `core/src/main/java/.../util/GeofenceEngine.kt` — JTS R-tree spatial index + multi-zone geofence alerting
- `core/src/main/java/.../util/FavoritesManager.kt` — SharedPreferences+JSON favorites CRUD
- `core/src/main/java/.../util/DebugLogger.kt` — tagged debug logging
- `core/src/main/java/.../di/CoreModule.kt` — Hilt DI module (repositories + location + geofence)
- `core/src/main/java/.../ui/menu/MenuPrefs.kt` — preference key constants
- `core/src/main/java/.../ui/menu/MenuEventListener.kt` — menu event interface

### App-specific Utilities (remain in `:app`)
- `app/src/main/java/.../util/DebugHttpServer.kt` — embedded HTTP server (port 8085)
- `app/src/main/java/.../util/DebugEndpoints.kt` — debug endpoint handlers (takes 6 ViewModel params)

### Web App (Phases 1-6 — Map + Find + Weather + Aircraft + Transit + Social + Favorites/URL)
- `web/package.json` — React 19, react-leaflet, Leaflet, Tailwind CSS, Vite, socket.io-client
- `web/vite.config.ts` — dev server binds `0.0.0.0:5173` (LAN-accessible), proxy `/api` → `localhost:3000`, `@/` path alias
- `web/src/main.tsx` — React root entry point
- `web/src/App.tsx` — top-level orchestration: Find/Detail/Weather/Aircraft/Transit/Chat panels, layer toggles, auth state, detail panel mutual exclusion, vehicle/aircraft follow, favorites, URL routing
- `web/src/config/api.ts` — typed `apiFetch<T>()` + `authFetch<T>()` wrappers, token storage helpers, singleton refresh, `VITE_API_URL` env var
- `web/src/config/categories.ts` — 17 POI categories, `classifyPoi()`, `resolveCategory()`, `getCategoryByTag/Tags/SubtypeTags()`
- `web/src/config/weatherIcons.ts` — NWS icon code → inline SVG mapping (~25 codes, day/night variants)
- `web/src/config/aircraft.ts` — altitude color mapping, unit converters, `aircraftIconHtml()` DivIcon factory, emergency squawk detection
- `web/src/config/transit.ts` — MBTA route colors, `getRouteColor()`, `routeTypeLabel()`, `vehicleStatusLabel()`
- `web/src/lib/types.ts` — POI, FavoriteEntry, FindResult, WeatherData, MetarStation, AircraftState, AircraftHistory, MbtaVehicle, MbtaStop, MbtaPrediction, AuthUser, AuthResponse, PoiComment, CommentsResponse, ChatRoom, ChatMessage
- `web/src/lib/distance.ts` — `haversineM()` + `formatDistance()` (ft/mi formatting)
- `web/src/lib/timeFormat.ts` — `relativeTime()` ("just now" / "5m ago" / "2h ago" / "3d ago" / date)
- `web/src/hooks/useGeolocation.ts` — browser Geolocation API with Boston fallback, persistent home location (localStorage), Promise-based locate
- `web/src/hooks/usePois.ts` — debounced (300ms) `/pois/bbox` fetch + `/pois/stats` total count + server-side cluster support
- `web/src/hooks/useDarkMode.ts` — localStorage-persisted dark mode toggle
- `web/src/hooks/useFind.ts` — search (1s debounce), findByCategory, loadCounts, fetchWebsite, fetchPoiDetail
- `web/src/hooks/useWeather.ts` — weather/METAR fetch, radar/metar toggles, 5-min auto-refresh
- `web/src/hooks/useAircraft.ts` — aircraft state, 15s auto-refresh, OpenSky state vector parsing, select/follow/history
- `web/src/hooks/useTransit.ts` — trains/subway/buses/stations/busStops, per-type refresh timers, vehicle follow, trip predictions
- `web/src/hooks/useAuth.ts` — auth state, register, login, logout, token validation on mount via `/auth/me`
- `web/src/hooks/useComments.ts` — POI comment CRUD with unauthenticated fallback for viewing
- `web/src/hooks/useChat.ts` — Socket.IO chat rooms, real-time messaging, typing indicator
- `web/src/hooks/useFavorites.ts` — localStorage-backed favorites CRUD (toggle, isFavorite, count)
- `web/src/hooks/useUrlState.ts` — URL search params (lat/lon/z/poi): parse on load, debounced replaceState, POI deep linking
- `web/src/components/Map/MapView.tsx` — react-leaflet MapContainer + all marker layers + radar + flight trail + long-press handler
- `web/src/components/Map/PoiMarkerLayer.tsx` — category-colored CircleMarkers, server-side cluster rendering (translucent), filter mode
- `web/src/components/Map/RadarLayer.tsx` — RainViewer radar tiles + 7-frame animation via Leaflet API
- `web/src/components/Map/MetarMarkerLayer.tsx` — flight-category colored CircleMarkers + monospace labels
- `web/src/components/Map/AircraftMarkerLayer.tsx` — DivIcon markers with rotated airplane SVG, altitude-colored
- `web/src/components/Map/FlightTrailLayer.tsx` — altitude-colored polyline segments from flight path history
- `web/src/components/Map/TransitMarkerLayer.tsx` — route-colored CircleMarkers for vehicles, station dots, bus stop dots, selected vehicle highlighting
- `web/src/components/Map/MapControls.tsx` — zoom +/- buttons + geolocation button + home location indicator
- `web/src/components/Layout/Toolbar.tsx` — top bar: app name + Find + Weather + Layers (with count badge) + Chat + Profile + dark mode
- `web/src/components/Layout/LayersDropdown.tsx` — dropdown toggle switches for Aircraft/Trains/Subway/Buses with count badges
- `web/src/components/Layout/StatusBar.tsx` — bottom bar: coords + POI/aircraft/transit counts, teal filter bar, red alert banner
- `web/src/components/Weather/WeatherPanel.tsx` — slide-in panel: Current/Hourly/Daily tabs, alert banners, layer controls
- `web/src/components/Aircraft/AircraftDetailPanel.tsx` — aircraft info panel: altitude/speed/heading/squawk, follow button, sighting history
- `web/src/components/Transit/VehicleDetailPanel.tsx` — vehicle info panel: route-colored header, status, next 5 stops with arrival times, follow button
- `web/src/components/Transit/ArrivalBoardPanel.tsx` — station arrival/departure board: dark-themed, route-colored prediction rows, DEP/ARR labels, service ended message
- `web/src/components/Find/FindPanel.tsx` — slide-in panel: search bar, favorites cell, 4-col category grid with count badges, subtype grid, results, Filter and Map
- `web/src/components/Find/ResultsList.tsx` — shared result rows: distance + color dot + name + detail + category label
- `web/src/components/Find/PoiDetailPanel.tsx` — detail panel: color bar, info rows, website resolution, action buttons (Directions/Call/Map/Share), comments section, star favorite toggle
- `web/src/components/Social/AuthDialog.tsx` — register/login modal with client-side validation, two modes (toggle)
- `web/src/components/Social/ProfileDropdown.tsx` — profile info dropdown with sign-in/sign-out, home location set/clear, click-outside-to-close
- `web/src/components/Social/CommentsSection.tsx` — comment list, add form, star rating, voting, delete
- `web/src/components/Social/StarRating.tsx` — reusable star rating display + interactive selector
- `web/src/components/Social/ChatPanel.tsx` — room list + chat room with real-time messaging, typing indicator

### Cache Proxy (decomposed into 19 modules)
- `cache-proxy/server.js` — Express bootstrap, middleware, CORS, module loader (167 lines)
- `cache-proxy/lib/config.js` — environment vars, constants
- `cache-proxy/lib/cache.js` — file-based cache engine with LRU eviction + import buffer
- `cache-proxy/lib/opensky.js` — OpenSky OAuth2 token management
- `cache-proxy/lib/scan-cells.js` — scan cell coverage tracking (~1.1km grid, persistence, debug endpoint)
- `cache-proxy/lib/overpass.js` — POST /overpass (queue, cache, scan cells, content hash, cancel)
- `cache-proxy/lib/db-pois.js` — 8 /db/pois/* routes + /pois/website
- `cache-proxy/lib/db-aircraft.js` — 4 /db/aircraft/* routes
- `cache-proxy/lib/aircraft.js` — /aircraft endpoint
- `cache-proxy/lib/weather.js` — /weather composite NWS
- `cache-proxy/lib/tfr.js` — /tfrs FAA scraping
- `cache-proxy/lib/zones.js` — /cameras, /schools, /flood-zones, /crossings
- `cache-proxy/lib/auth.js` — 5 /auth/* routes + rate limiting
- `cache-proxy/lib/comments.js` — 4 /comments/* routes
- `cache-proxy/lib/chat.js` — 3 /chat/* routes + Socket.IO
- `cache-proxy/lib/pois.js` — POI endpoints (PostgreSQL-backed: stats, export, bbox, single lookup)
- `cache-proxy/lib/import.js`, `metar.js`, `webcams.js`, `mbta.js`, `geocode.js`, `geofences.js`, `admin.js`, `proxy-get.js`
- `cache-proxy/geofence-databases/catalog.json` — geofence database catalog (4 databases)
- `cache-proxy/geofence-databases/build-military.js` — military base polygon builder (ArcGIS NTAD)
- `cache-proxy/geofence-databases/build-excam.js` — speed/red-light camera builder (WzSabre XZ/NDJSON)
- `cache-proxy/geofence-databases/build-nces.js` — US public school builder (NCES ArcGIS)
- `cache-proxy/geofence-databases/build-dji-nofly.js` — DJI no-fly zone builder (GitHub CSV)
- `cache-proxy/cache-data.json` — persistent Overpass cache, LRU-capped at 2000 entries (gitignored)
- `cache-proxy/radius-hints.json` — adaptive radius hints per grid cell (gitignored)
- `cache-proxy/scan-cells.json` — scan cell coverage timestamps + POI counts (gitignored)
- `cache-proxy/schema.sql` — PostgreSQL schema for permanent POI storage
- `app/src/main/java/.../util/DebugHttpServer.kt` — embedded HTTP server (port 8085)
- `app/src/main/java/.../util/DebugEndpoints.kt` — all debug endpoint handlers
- `cache-proxy/import-pois.js` — standalone script to import POIs from proxy into PostgreSQL

## PostgreSQL Database
- Database: `locationmapapp`, user: `witchdoctor`
- **`pois` table**: Composite PK `(osm_type, osm_id)`, JSONB tags (GIN index), promoted name/category columns
  - Indexes: category, name (partial), tags (GIN), lat+lon (compound), **name trigram (GIN pg_trgm)** for fuzzy search
  - 268,291 POIs as of 2026-03-04 (auto-imported, ~12.8k with cuisine tags)
- **`aircraft_sightings` table**: Serial PK, tracks each continuous observation as a separate row
  - Columns: icao24, callsign, origin_country, first/last seen, first/last lat/lon/altitude/heading, velocity, vertical_rate, squawk, on_ground
  - 5-minute gap between observations = new sighting row (enables flight history analysis)
  - Indexes: icao24, callsign, first_seen, last_seen, last_lat+lon
  - 501+ sightings as of 2026-03-01, 195 unique aircraft (accumulates in real-time)
  - Real-time: proxy writes to DB on every aircraft API response (cache hits and misses)
- **Automated POI import** (v1.5.49, updated v1.5.60): buffer-drain upsert every 15min
  - Overpass responses buffered in lightweight import buffer; drained and deduped on each import cycle
  - Batches of 500, per-batch transactions, mutex against overlap
  - `POST /db/import` (manual trigger), `GET /db/import/status` (read-only status)
  - Stats in `/cache/stats` → `dbImport` object
- **DB query API** (`/db/*` prefix): 14 endpoints — 8 POI + 4 aircraft + 2 import
  - `GET /db/pois/search` — smart fuzzy search (pg_trgm similarity + ILIKE), keyword→category hints, **cuisine-aware** (tags->>'cuisine'), distance expansion, composite scoring
  - `GET /db/pois/nearby` — Haversine distance sort with bbox pre-filter
  - `GET /db/poi/:type/:id` — single POI with timestamps
  - `GET /db/pois/stats` — totals, named count, top categories, bounds, time range
  - `GET /db/pois/categories` — category breakdown with key/value split
  - `GET /db/pois/coverage` — geographic grid with configurable resolution
  - `GET /db/pois/counts` — category counts with 10-min server cache (Find dialog)
  - `GET /db/pois/find` — distance-sorted POIs by category, Haversine sort, auto-expand (Find dialog)
  - `GET /db/aircraft/search` — filter by callsign, icao24, country, bbox, time range
  - `GET /db/aircraft/stats` — totals, unique aircraft, top countries/callsigns, altitude distribution
  - `GET /db/aircraft/recent` — most recently seen aircraft, deduplicated by icao24
  - `GET /db/aircraft/:icao24` — full sighting history + flight path for one aircraft
- **Social tables** (v1.5.45): `users`, `auth_lookup`, `refresh_tokens`, `poi_comments`, `comment_votes`, `chat_rooms`, `room_memberships`, `messages`
  - All granted to `witchdoctor` user; DDL runs as `sudo -u postgres`
- Import: automated every 15min when `DATABASE_URL` set (manual: `node import-pois.js` or `POST /db/import`)
  - **Quick-drain** (v1.5.69): buffered Overpass results auto-imported to PostgreSQL within 2 seconds, so POIs appear on map immediately after long-press search
- Proxy startup: `DATABASE_URL=... JWT_SECRET=... OPENSKY_CLIENT_ID=... OPENSKY_CLIENT_SECRET=... node server.js`
  - Without DATABASE_URL: proxy starts normally, `/db/*` and social endpoints return 503, auto-import disabled
  - Without JWT_SECRET: random secret generated (tokens don't survive restarts)
  - Without OPENSKY_*: aircraft requests use anonymous access (100 req/day)

## GPS Centering
- Auto-centers on **first** GPS fix only (`initialCenterDone` flag) at **zoom 18**; follow mode still pans

## OpenSky OAuth2
- Registered account: `DeanMauriceEllis`
- Client ID: `deanmauriceellis-api-client`
- Client credentials flow: `OPENSKY_CLIENT_ID` + `OPENSKY_CLIENT_SECRET` env vars
- Token endpoint: `auth.opensky-network.org/.../token` (30-min expiry, auto-refresh with 5-min buffer)
- Authenticated: 4,000 req/day (vs 100 anonymous)

## OpenSky Rate Limiter (v1.5.11)
- **Proxy-level** — all aircraft requests throttled at the single proxy choke point
- Daily quota: 90% of limit (3,600 of 4,000 authenticated, 90 of 100 anonymous)
- Minimum interval: `86400000 / effective_limit` ms between upstream requests (~24s authenticated)
- **Exponential backoff** on 429: 10s → 20s → 40s → 80s → 160s → 300s cap
- **Stale cache fallback**: returns expired cached data when throttled (app doesn't see errors)
- `Retry-After` header sent when no cache available
- Rate stats exposed in `/cache/stats` → `opensky` object (requestsLast24h, remaining, backoff state)
- Resets on successful response after 429 series

## Build Environment
- **Java**: JBR (JetBrains Runtime) 21.0.9 bundled with Android Studio
  - Path: `/home/witchdoctor/AndroidStudio/android-studio/jbr`
  - Build command: `JAVA_HOME=/home/witchdoctor/AndroidStudio/android-studio/jbr ./gradlew assembleDebug`
  - System Java 17 is NOT sufficient — Gradle daemon requires Java 21 (`gradle/gradle-daemon-jvm.properties`)

## Development Environment — Server & Emulator Setup

### Ports
| Port | Service | Notes |
|------|---------|-------|
| 4300 | Cache proxy (Node.js/Express) | Main backend API |
| 4301 | TCP log streamer | Android app sends debug logs here |
| 4302 | Vite web dev server | React web app |
| 4303 | Debug HTTP server | On-device, access via `adb forward tcp:4303 tcp:4303` |
| 5432 | PostgreSQL | Database: `locationmapapp` |

### Server Startup (all three required for full functionality)
```bash
# 1. Cache proxy — use bin/restart-proxy.sh or:
cd cache-proxy && DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
  JWT_SECRET=$(openssl rand -hex 32) \
  OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
  OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR node server.js

# 2. Web dev server
cd web && npx vite --host 0.0.0.0 --port 4302

# 3. PostgreSQL must be running on port 5432
#    Without DATABASE_URL: /db/* POI endpoints return 503, POIs won't appear on map
```

### Emulator Setup (Pixel_8a_API_34)
AVD config at `~/.android/avd/Pixel_8a_API_34.avd/config.ini` — critical settings:
```ini
hw.initialOrientation=landscape
showDeviceFrame=no
skin.name=1080x2400
skin.path=1080x2400
```
- **Must use API 34** (not 36) — API 36 has immersive mode restrictions
- **showDeviceFrame=no** — removes phone bezel; required for correct touch coordinate mapping
- **skin.name/path=1080x2400** — do NOT use `pixel_8a` skin (causes bezel to reappear)
- AVD RAM: 8192 MB (`hw.ramSize=8192`) — prevents ANR on startup with all layers active
- Start: `emulator -avd Pixel_8a_API_34 -no-boot-anim -no-snapshot`
- After boot, apply immersive mode: `adb shell settings put global policy_control "immersive.full=com.example.locationmapapp"`
- Resize the emulator window by **dragging the corner** — do NOT use `wmctrl -b add,fullscreen` (crashes emulator) or `wmctrl -e` to force size (breaks touch coordinates)
- Install app: `./gradlew installDebug`
- Desktop screen resolution: 1280x720

## POI Marker Memory Management
- Viewport-only display: `replaceAllPoiMarkers()` on bbox refresh, ~100-400 markers (was 22k)
- Category toggles control *searching*, not *display* — display always from `/pois/bbox`
- LRU icon cache capped at 500 entries

## Known Issues
- MBTA API key hardcoded in MbtaRepository.kt (should be in BuildConfig/secrets)
- Windy Webcams API key hardcoded in server.js (free tier)
- 10.0.0.4 proxy IP hardcoded (works on local network only)
- OpenSky state vector: category field (index 17) not always present — guarded with size check
- **Overpass intermittent 504s** (observed 2026-03-04): FIXED — proxy now retries with 3-endpoint rotation + exponential backoff; app PlacesRepository retries up to 3 times with 5s/10s delays
- **Web app POI limitation**: only shows POIs already in PostgreSQL (from prior Android app scans); navigating to unscanned areas shows nothing — needs Overpass trigger mechanism

## Debug HTTP Server (v1.5.18)
| Endpoint | Description |
|---|---|
| `GET /` | List all endpoints |
| `GET /state` | Map center, zoom, bounds, marker counts, follow state |
| `GET /logs?tail=N&filter=X&level=E` | Debug log entries (filtered, tailed) |
| `GET /logs/clear` | Clear log buffer |
| `GET /map?lat=X&lon=Y&zoom=Z` | Set/read map position (animates) |
| `GET /markers?type=X&limit=N` | List markers by type |
| `GET /markers/tap?type=X&index=N` | Trigger marker click handler |
| `GET /markers/nearest?lat=X&lon=Y&type=X` | Find nearest marker(s) |
| `GET /markers/search?q=X&type=X` | Search markers by title/snippet |
| `GET /vehicles?type=X&limit=N&index=N` | Raw vehicle data (headsign, tripId, stopName, etc.) |
| `GET /stations?limit=N&q=X` | Raw station data with name search |
| `GET /bus-stops?limit=N&q=X` | All cached bus stops with name search |
| `GET /screenshot` | PNG of root view |
| `GET /livedata` | All ViewModel LiveData values |
| `GET /prefs` | Dump SharedPreferences |
| `GET /toggle?pref=X&value=true` | Toggle layer pref + fire handler |
| `GET /search?lat=X&lon=Y` | Trigger POI search at point |
| `GET /refresh?layer=X` | Force refresh a layer |
| `GET /follow?type=aircraft&icao=X` | Follow aircraft/vehicle |
| `GET /stop-follow` | Stop following |
| `GET /perf` | Memory, threads, uptime |
| `GET /overlays` | List all map overlays |
| `GET /geofences` | Loaded geofence zones, active zones, proximity threshold |
| `GET /geofences/alerts` | Active geofence alerts with severity/type/distance |

Marker types: `poi`, `stations`, `trains`, `subway`, `buses`, `aircraft`, `webcams`, `metar`, `gps`

## Automated Testing

### Test Scripts (project root)
| Script | Lines | Purpose |
|--------|-------|---------|
| `test-app.sh` | ~650 | Quick endpoint tests (30+ checks, ~1 min) |
| `overnight-test.sh` | ~1,850 | Full overnight harness (6-8 hrs unattended) |
| `morning-transit-test.sh` | ~500 | Deep transit validation (active service hours) |
| `run-full-test-suite.sh` | ~100 | Master chain: overnight → morning |

### Running Tests
```bash
adb forward tcp:8085 tcp:8085        # Required first
./test-app.sh                        # Quick smoke test
./overnight-test.sh --duration 30    # 30-min trial run
./overnight-test.sh                  # Full 8-hour overnight run
./run-full-test-suite.sh --quick     # 30-min overnight + 30-min morning
./run-full-test-suite.sh             # Full suite (8hr + 1hr)
```

### Test Output
```
overnight-runs/YYYY-MM-DD_HHMM/
  events.log          — timestamped PASS/FAIL/WARN/INFO stream
  time-series.csv     — 30-column CSV (memory, markers, cache, OpenSky, errors)
  report.md           — final summary with recommendations
  baseline/           — initial state snapshots + init-timing.json
  snapshots/          — periodic JSON state every 5 min
  screenshots/        — PNG captures every 15 min
  logs/               — error snapshots + final log dump
```

### Full Suite Results (2026-03-01): 103 PASS, 0 FAIL
- Overnight 5.5hrs + Morning 1hr — no leaks, no failures, memory 9-27MB steady
- See `overnight-runs/` for detailed reports

## Completed Plans
- **Geofence Alert System** (v1.5.35–v1.5.39): Phases 1-4 done. See `PLAN-ARCHIVE.md`.
- **Social Layer** Phases A-C (v1.5.45–v1.5.47): Auth + Comments + Chat done. Phase D (mod tools) not started. See `GOVERNANCE.md`.
- **Web App Social** Phase 5 (v1.5.66): Auth + Comments + Chat ported to web app. See `WEB-APP-PLAN.md`.
- **Web App Favorites + URL** Phase 6 (v1.5.68): localStorage favorites + shareable URL routing. See `WEB-APP-PLAN.md`.
- **Code Decomposition** (2026-03-04): 3-phase refactoring complete. See `REFACTORING-REPORT.txt`.
  - Phase 1: server.js (3,925 → 156 lines + 18 modules in lib/)
  - Phase 2: MainViewModel.kt (958 → 215 lines + 6 domain ViewModels)
  - Phase 3: AppBarMenuManager.kt (879 → 812 lines + MenuPrefs.kt)
  - Prior: MainActivity.kt (9,577 → 1,996 lines + 13 extension-function files)

## Commercialization
- **`COMMERCIALIZATION.md`** v2.0 — lawyer-ready edition (1,897 lines, 27 sections, created 2026-03-04)
  - **Part A** (§1–4): Product description, freemium revenue model (free+ads / paid tier), data flow diagram, executive risk summary with probability scoring
  - **Part B** (§5–17): Finding an attorney, business entity (Wyoming LLC), insurance, IP protection, user content liability (Section 230, DMCA, Take It Down Act), privacy (CCPA + 21 states + GDPR/UK/international roadmap), API licensing (**OpenSky BLOCKING**), dependency inventory (22 Android + 12 Node.js libs), ad network compliance (AdMob), Play Billing, tax considerations, social media integration, safety disclaimers, competitor analysis (8 comparable apps)
  - **Part C** (§18–27): Content moderation, legal documents, Play Store requirements, account management, APK protection, cloud deployment, cost summary ($4,803–$11,480 Year 1), risk matrix (14 risks scored by probability×impact), 17 prioritized attorney questions, master checklist (10 phases, ~70 action items)

## Next Steps
- **Web app Phase 5 testing**: Auth register/login, comments CRUD, chat room messaging, token refresh, dark mode
- **Web app Phase 7-8**: PWA, monetization
- **Commercialization blockers**: Find attorney (see §5), OpenSky commercial license, LLC formation, insurance, attorney review of ToS/Privacy Policy
- **Monetization**: AdMob integration, Google Play Billing for subscriptions, freemium tier gating
- Social: Phase D (room management), content moderation system (reporting, flagging, moderation queue)
- Cloud migration: move proxy + PostgreSQL to cloud, replace hardcoded 10.0.0.4 with domain
- Find dialog: pagination, recent searches, search history
- Privacy: user data export endpoint, account deletion/anonymization, age gate
- Google Play: R8 obfuscation, Data Safety section, prominent location disclosure
