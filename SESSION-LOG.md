# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 105-114. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 115: 2026-04-10 — Android debug + tour polish: 18 fixes, heading-up smoothness deferred

Android debug pass on the Lenovo TB305FU surfaced a cascade of related issues and shipped 18 fixes across 7 modified files + 2 new classes (`DeviceOrientationTracker`, `MotionTracker`). Highlights: GPS prune 1h→5min; near-zone escape hatch for POIs inside the behind-user filter; dwell expansion ladder (20→35→60→100m, cap 6) for stopping at the Witch House; new heading-up `N↑` FAB with hybrid heading source (GPS bearing → rotation vector sensor → stale GPS); walk sim auto-dwell at each POI (15m trigger, 3s "look at POI" pause via new `setMovementBearing` setter); `android:configChanges` on the Salem activity to survive rotation without destroying the walk sim coroutine — which **also kills the S110 lifecycle churn** that was on the carry-forward list for 6+ sessions; magnetic declination correction via `GeomagneticField` for true-north rotation; stationary GPS freeze via `TYPE_SIGNIFICANT_MOTION`; adaptive smoothing with static-mode detection and SNAP-on-rotation-complete; compass accuracy toasts. Heading-up rotation smoothness is not yet solved — root cause identified as 100 Hz sensor delivery + main-thread saturation from per-sample log writes, plan documented for S116.

Full session detail (sensor inventory findings via `dumpsys`, 5 test/iteration cycles, three separate walk sim behavior iterations, three separate rotation-responsiveness iterations, and the final discovery that the Lenovo fires sensor updates at 100 Hz via SENSOR_DELAY_UI): `docs/session-logs/session-115-2026-04-10.md`.

---

## Session 114: 2026-04-10 — OMEN NOTEs cleared + POI taxonomy foundation (Step 1 of 4-step arc)

Cleared the outstanding OMEN NOTE backlog (L013 VoiceAuditionActivity deleted, L014 Privacy Policy drafted for OMEN-008 Salem stream pending review, L015 confirmed no-op for the 9th consecutive session) and then pivoted mid-session from a planned hero-image regen into an architecture conversation that surfaced seven parallel POI taxonomies across the codebase. Shipped Step 1 of a new 1→4 taxonomy-alignment arc: two PG lookup tables (`salem_poi_categories` 22 rows, `salem_poi_subcategories` 175 rows) seeded from `web/src/config/poiCategories.ts` via a new idempotent Node script, new nullable `category`/`subcategory` columns on `salem_businesses`, three FK constraints enforced (narration_points.subcategory, businesses.category, businesses.subcategory), and a multi-session plan doc (`docs/poi-taxonomy-plan.md`) with explicit S115/S116 handoff notes and hard scope decisions (`salem_tour_pois` excluded — tour-chapter themes, different concept; no table merge per operator direction).

Full session detail (architecture conversation, seven-taxonomy mapping, two bugs caught and fixed during seed-script testing, decisions locked in, deferred items, hero regen still blocked on SalemIntelligence): `docs/session-logs/session-114-2026-04-10.md`. Commit: `e32a4b7`.

---

## Session 113: 2026-04-09 — POI Detail Sheet: full-screen dialog with hero + descriptions + TTS read-through

Built a new POI detail window that opens on tap of a dock icon, map marker, or narration banner — hero image strip (20% of screen, fitXY stretch from hash-pinned `poi-icons/` pool or red "ASSIGN HERO" placeholder), overview row, Visit Website through in-app WebView, short/about/story narrative sections, and render-time-synthesized action buttons. Three iterations in one session: initial BottomSheetDialogFragment build, full-screen DialogFragment conversion + TTS trim (drops action button narration, reads only name/address/phone/website-ack + descriptions), then highlight-ring tap-interception fix (polygons moved to overlay index 0 with null titles and non-consuming click listeners) plus narration banner click handlers plus verbose click-path logging. End-of-session hero regen pass deferred — operator escalated it into a new sibling product at `~/Development/SalemIntelligence` (knowledgebase + LLM for per-business research-driven prompts) that takes a day to build.

Full session detail (3 iterations, 2 operator bug triage rounds, debug log reconstruction, S112 highlight-ring tap-interception diagnosis): `docs/session-logs/session-113-2026-04-09.md`. Commit: `2826145`.

---

## Session 112: 2026-04-09 — Major narration overhaul: tiered queue, density cascade, ahead-only filter, glowing highlight ring, walk-sim 3x removal, fresh-start reset, POI button tier filter, home-stops-walksim

Operator field test surfaced three distinct narration bugs (APPROACH-cooldown, walk-sim 3x starvation, sequencing) plus the GPS journey line backgrounding issue (diagnosed not fixed). Shipped 5 commits ending with a 3-level density cascade (40m/30m/20m discard tiers) + bearing-based ahead-only filter that picks the closest in-front POI within the highest non-empty tier, plus a glowing pulsing highlight ring on the active POI and a debug overlay showing the discard radii as outline circles around the walking icon. Operator confirmed "working much better. Success!" on the final walk.

Full session detail (all 5 commits, 7 design questions, debug log analysis, tier classifier, bearing math): `docs/session-logs/session-112-2026-04-09.md`. Commits: `7ca0602`, `95f25f2`, `dded0e6`, `1f63113`, `8a94a06`.

---

## Session 111: 2026-04-09 — Protocol optimization (compress STATE.md, archive S001-S101, rewrite session protocols, thin OMEN report template)

Compressed STATE.md from 784 → 112 lines (snapshot-only), archived sessions S001-S101 to SESSION-LOG-ARCHIVE.md (rolling-window protocol now active), and rewrote CLAUDE.md session-start protocol for parallel + head-only reads and session-end protocol for write-once-in-live-log with a new ~80-line thin OMEN report template. Operator-queued from S110 wrap-up; net per-session read cost drops ~70% at session start. **First session using the new protocols** — this entry itself is the new thin format.

Full session detail (decisions, file diffs, line-count tables, verification): `docs/session-logs/session-111-2026-04-09.md`. Per the new protocol, the SESSION-LOG.md entry is a 2-3 sentence pointer; the live log is the canonical record.

---

## Session 110: 2026-04-09 — Five-pivot side quest: layout fix + runaway-loop fix + verbose persistent logging + cache-proxy circuit breaker + POI proximity encounter tracker

### Summary
**Five operator pivots, all unplanned, all driven by real bug reports from a field test on the Lenovo TB305FU.** Zero progress on the carry-over 9P.11 admin tool task — fourth session in a row that's been side-quested by field-test bugs (S107 → S108 → S109 → S110). Major progress on app robustness instead.

**Pivot 1 — bottom-left layout fix.** Operator reported the GPS journey toggle FAB from S109 was hidden behind the narration sheet. Root cause: the FAB and the existing tool stack (POI/Walk/magnify) were both bottom-anchored at margins (100dp / 56dp) less than the narration sheet's worst-case content height. Moved the GPS toggle INTO the existing bottom-left tool LinearLayout as a 4th button (matching POI/Walk/magnify TextView style with `zoom_toggle_bg` background), bumped the LinearLayout's marginBottom from 56dp → 168dp so all 4 buttons clear the sheet. End layout (top → bottom of left column): GPS · POI · Walk · x1 (magnify). `setupGpsToggleButton()` switched from FAB-specific `imageTintList = ColorStateList.valueOf(...)` to TextView-friendly `setTextColor(...)` for the on/off state visualization.

**Pivot 2 — runaway narration reach-out loop diagnosis + fix.** Operator reported: "took a drive and it stopped working correctly, hung up for 15 minutes, now reporting locations in salem as I speak and I am home now!" Logcat reconstruction revealed the bug: at 16:38 the activity process died mid-drive (PID 18124 → 24586), one POI fired post-restart (Carly Cat Little Free Library at 15m — a real geofence entry), then 14 minutes of cache-proxy connection failures (laptop unreachable from drive). GPS chip stopped emitting fresh fixes during the drive home. At 16:53 an orientation change at home triggered an activity recreate; the narration manager's check counter reset to #1 because `NarrationGeofenceManager` was instance-state per-activity. The reach-out logic at `SalemMainActivityNarration.kt:117` then started cycling: `findNearestUnnarrated()` against the 15-minute-stale Salem position returned Benjamin Punchard, Shoreman (145m) → Salem Athenaeum (115m) → Magika (125m) → Salem Public Library (113m), every 10 seconds. **Four root causes identified and fixed:** (1) GPS staleness blindness — `currentLocation.observe` callbacks didn't carry a fix age. Fix: `lastFixAtMs: Long` field stamped on every observer call + `GPS-OBS` per-fix log + 10s heartbeat coroutine. (2) Reach-out doesn't check GPS freshness — fix: staleness gate at the silence reach-out site bails with `REACH-OUT SUPPRESSED: GPS stale` if `lastFixAtMs > 30s` old. (3) Reach-out distance too generous (150m real / 200m walk-sim) — fix: `REACH_RADIUS_M = 15.0` (real GPS) and `REACH_RADIUS_WALKSIM_M = 25.0` per operator direction "Salem has a lot of POIs clustered together." (4) Activity recreate wipes narration dedup — fix: `NarrationGeofenceManager` promoted to Hilt `@Singleton` with `@Inject constructor(@ApplicationContext context: Context)`. `narratedAt` and `cooldowns` survive activity recreate. `loadPoints()` no longer clears cooldowns. Activity field changed from `var ... = null` → `@Inject lateinit var`. Eight call sites converted from `?.foo()` to `.foo()`.

**Pivot 3 — verbose persistent logging.** Operator: "Any kind of verbose logging we can add to the application so we have a good log for capturing issues?" The pre-S110 `DebugLogger` was an in-memory ring buffer that lost everything on process death — exactly the gap that broke today's bug investigation. Added: (a) `DebugLogger.FileLogSink` — daily-rotated file at `/sdcard/Android/data/com.example.wickedsalemwitchcitytour/files/logs/debug-YYYYMMDD.log`, single background thread + `BufferedWriter` with `flush()` after every line, 7-day retention. (b) `GPS-OBS` per-fix telemetry with explicit `gap=` measurement. (c) `GPS-OBS` heartbeat coroutine with periodic OK every 60s + state-transition logging + throttled stale logging. (d) `LIFECYCLE` observer with `changingConfig` and `finishing` flags. (e) PID prefix `[N|PID]` in every log line so multiple process lifetimes appended to the same file are unambiguous. (f) `════════ process started PID=N ════════` banner as the first entry of every fresh process. (g) `bearing=?` format fix (was `bearing=?°` with stray degree sign on null bearings).

**Pivot 4 — cache-proxy circuit breaker (option A + B).** Operator: "If we can't connect locally to our server, it should not crash or affect the app." Audit revealed every consumer (WeatherViewModel / MainViewModel / GeofenceViewModel / etc.) was independently hitting 15-second TCP connect timeouts and logging 16+ line stack traces when the cache-proxy was unreachable. New `core/util/network/` package: `LocalServerHealth` singleton circuit breaker (60s OPEN window, host-keyed against `LOCAL_HOSTS = setOf("10.0.0.229", "localhost", "127.0.0.1")`), `CircuitOpenException` sentinel `IOException`, `LocalServerCircuitBreakerInterceptor` OkHttp interceptor that fast-fails to-local-host requests when the circuit is OPEN and otherwise records success/failure on response/exception. Wired into 13 OkHttpClient.Builder sites (12 repository files + the inline geocode client in SalemMainActivityFind.kt). `DebugLogger.e()` updated to detect `ConnectException`, `SocketTimeoutException`, `UnknownHostException`, `CircuitOpenException` and log a single compact WARN line instead of an ERROR + stack trace. Real exceptions still get the full stack so unexpected bugs aren't hidden.

**Pivot 5 — POI proximity encounter tracker (NEW feature).** Operator: "When we get close to POI's — I want us to track how close for how long (max/min distance and time) so I can review that later." New `PoiEncounter` Room entity with composite string PK `${poiId}|${firstSeenAtMs}` for `INSERT OR REPLACE` semantics. Fields: poi_id, poi_name, poi_lat, poi_lng, first_seen_at_ms, last_seen_at_ms, duration_ms, min_distance_m, max_distance_m, fix_count. New `PoiEncounterDao` with upsert/getRecent/getMostRecent/getClosestApproachForPoi/deleteOlderThan/count/deleteAll. New `PoiEncounterTracker` Hilt `@Singleton` with in-memory `Map<poiId, PoiEncounter>` of active encounters, `recordObservation(nearby, nowMs)` filters by `PROXIMITY_RADIUS_M = 100m`, opens/updates/closes encounters, fire-and-forget DB writes via IO scope. `UserDataDatabase` schema bumped 1 → 2 with `fallbackToDestructiveMigration` (rolling 24h GPS journey is acceptable to lose). Activity wiring: `@Inject` the tracker, `pruneStaleAtStartup()` in `onCreate`, `flushAll()` in `onDestroy`, `recordObservation()` call in `SalemMainActivityTour.kt#updateTourLocation()`. **Verified live** via in-app walk simulator: 9 encounters recorded over a 30-second simulated walk through downtown Salem. `Levesque Funeral Home` row example: `min_dist=9m`, `max_dist=83m`, `duration=40.3s`, `fix_count=35`. Schema confirmed via `sqlite3 .schema poi_encounters` — all 11 columns + 2 indexes present.

### Decisions
1. **`narrationGeofenceManager` lifted to Hilt singleton** rather than restored on every activity creation. Process death still loses state, but activity recreate (orientation change, configuration change) now preserves the dedup map. This is the structural fix for the runaway loop — without it, even the staleness gate + 15m radius cap wouldn't have been enough on their own to prevent the bug from ever recurring.
2. **Reach-out radius hardcoded to 15m / 25m** rather than configurable via SharedPreferences. Operator was explicit: "let's try 15m." If we need to tune later we can promote to a constant or pref.
3. **Staleness threshold = 30 seconds.** Long enough to absorb the 5-10s adaptive GPS interval + a missed fix or two; short enough to catch the "GPS chip lost lock and is replaying cached fixes" failure mode. Could be configurable later if 30s proves wrong.
4. **File log location: external files dir, NOT internal.** External dir at `/sdcard/Android/data/<pkg>/files/logs/` is accessible via `adb pull` without root. Internal `filesDir` would require `run-as` to read. The operator's primary review path is `adb pull`, so external is the right call. Falls back to `filesDir` only if external is unavailable (rare on modern Android).
5. **PID prefix format `[N|PID]` not `[N]@PID` or any other variant.** Pipe is grep-friendly (`grep '|2315]'` filters to one process), the bracket-and-pipe format is visually distinct from other timestamp formats, and the seq number stays at the front so existing log readers that just look at the first bracket still work.
6. **Heartbeat logs once per 60s when healthy, not silent.** Initial design only logged on transitions to keep noise low, but the result was 0 heartbeat lines in a multi-hour file — making the coroutine indistinguishable from "crashed and never started." Reversed: always log a single line per minute so the heartbeat is provably alive.
7. **Compact network logging detects by exception class, NOT by message string.** Walks the cause chain 5 levels deep. Exception classes are stable; message strings are not (OkHttp's "Failed to connect to /10.0.0.229:4300" message could change between OkHttp versions).
8. **Circuit breaker is host-keyed, NOT host:port-keyed.** When the laptop is offline, BOTH the cache-proxy (port 4300) and Nominatim (port 3000) are offline. Tracking by host means both fast-fail together. Edge case: if cache-proxy is down but Nominatim is up, the breaker over-blocks Nominatim. Acceptable trade-off.
9. **Circuit breaker `LOCAL_HOSTS` is a hardcoded set, NOT a config.** When the IP changes, this set is the single point of update. Same hardcoded `10.0.0.229` lives in 16+ repository files that build URLs — promoting them all to a config is a separate cleanup not in S110 scope.
10. **`recordSuccess()` is called on ANY returned response, even 4xx / 5xx.** The server is reachable even if it's unhappy — the circuit breaker only cares about transport-level failures, not application-level errors. A 401 / 503 response means the cache-proxy is alive and answering; we should not pretend the circuit is open.
11. **Compact network logging demoted from ERROR to WARNING level.** These are expected degraded conditions, not bugs. ERROR level is reserved for unexpected exceptions that warrant investigation. The user can grep `E/` to find real errors.
12. **POI encounter tracker uses 100m proximity radius.** Wider than the 30-50m geofence entry radius (which gates narration triggers) and narrower than the manager's 300m nearby radius. 100m means the user has to actually pass by the POI for it to register, not just be on the same street.
13. **POI encounter composite PK is `${poiId}|${firstSeenAtMs}` (string), NOT auto-generated `Long`.** Lets the tracker use `INSERT OR REPLACE` semantics on every fix during the encounter without round-tripping the auto-generated row id back to the in-memory state across coroutine boundaries. If the user comes back to the same POI later, that's a brand-new encounter with a different `firstSeenAtMs` and therefore a different PK.
14. **No "ended" or "active" boolean column on PoiEncounter.** A row is naturally "finalized" when its `last_seen_at_ms` is more than ~30s old (no new fix in 30s = encounter is over). Querying for "encounters from today where last_seen_at_ms < now - 30s" gives you the finalized ones. Simpler schema.
15. **POI encounter retention = 7 days, GPS journey retention = 24 hours.** Different windows because the use cases differ — GPS journey is "where am I right now-ish" (display polyline), encounters are "where did I walk this week" (review queries). 7 days is long enough to compare yesterday's walk to last Sunday's; 24 hours is long enough for a single-day sightseeing session.
16. **DB schema migration uses `fallbackToDestructiveMigration` instead of writing migration code.** The only existing user-data table is `gps_track_points`, which is a 24h rolling window — losing it on schema upgrade re-seeds within the next fix. No production data to preserve. Writing real migration code would be over-engineering for this point in the project lifecycle.
17. **Two commits, not three or four.** Originally proposed splitting the S110 work into 3 commits (layout / bug fix + logging / circuit breaker) and later 4 commits adding the encounter tracker. In practice the changes are heavily entangled across `SalemMainActivity.kt`, `SalemMainActivityTour.kt`, and `DebugLogger.kt` — splitting would require interactive `git add -p` which is hard to drive non-interactively. Settled on two commits: (1) all bug fixes + logging + circuit breaker + layout fix, (2) POI encounter tracker as a separable new-feature commit.

### Files Changed
**Commit 1 — S110 narration loop fix + verbose logging + cache-proxy circuit breaker + bottom-left layout:**
- New: `core/src/main/java/com/example/locationmapapp/util/network/LocalServerHealth.kt`
- New: `core/src/main/java/com/example/locationmapapp/util/network/CircuitOpenException.kt`
- New: `core/src/main/java/com/example/locationmapapp/util/network/LocalServerCircuitBreakerInterceptor.kt`
- `core/src/main/java/com/example/locationmapapp/util/DebugLogger.kt` — `FileLogSink` + `initFileSink(context)` + PID prefix in line format + compact network exception logging in `e()` (walks cause chain 5 levels for `ConnectException` / `SocketTimeoutException` / `UnknownHostException` / `CircuitOpenException`)
- 12 × `core/src/main/java/com/example/locationmapapp/data/repository/*Repository.kt` — `withLocalServerCircuitBreaker()` extension on each `OkHttpClient.Builder` + import (Aircraft, Auth, Chat, Comment, Find, Geofence, GeofenceDatabase, Mbta, Places, Tfr, Weather, Webcam)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/WickedSalemApp.kt` — `DebugLogger.initFileSink(applicationContext)` first thing in `onCreate()`
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/NarrationGeofenceManager.kt` — Hilt `@Singleton` with `@Inject constructor(@ApplicationContext context: Context)`; `REACH_RADIUS_M` 150 → 15, `REACH_RADIUS_WALKSIM_M` added at 25; `loadPoints()` no longer clears cooldowns
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt` — Hilt `@Inject lateinit var narrationGeofenceManager`; new `lastFixAtMs` + `GPS_STALE_THRESHOLD_MS` fields; per-fix `GPS-OBS` log; 10s heartbeat coroutine in `observeViewModel`; `LIFECYCLE` observer in `onCreate`; `setupGpsToggleButton` switched from FAB `imageTintList` to TextView `setTextColor`; `narrationActive` check changed from null-check to hardcoded true (singleton structurally guaranteed)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityNarration.kt` — Removed `narrationGeofenceManager = NarrationGeofenceManager().apply { init(...) }` instance creation; staleness gate added at silence reach-out site (`if (ageMs > GPS_STALE_THRESHOLD_MS) ... return@collectLatest`); converted all `narrationGeofenceManager?.foo()` to `narrationGeofenceManager.foo()`
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityTour.kt` — Converted `narrationGeofenceManager?.let { mgr -> ... }` to direct usage (Hilt singleton)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityFind.kt` — Added `withLocalServerCircuitBreaker()` to inline geocode `OkHttpClient.Builder`
- `app-salem/src/main/res/layout/activity_main.xml` — Moved GPS toggle into bottom-left tool stack as TextView; bumped LinearLayout `marginBottom` 56dp → 168dp; deleted standalone btnGpsToggle FAB + btnGpsToggleLabel TextView

**Commit 2 — S110 POI proximity encounter tracker:**
- New: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/db/PoiEncounter.kt` (Room entity with composite string PK)
- New: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/dao/PoiEncounterDao.kt`
- New: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/PoiEncounterTracker.kt` (Hilt singleton with in-memory active encounter map)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/db/UserDataDatabase.kt` — Schema v1 → v2; add `PoiEncounter` entity; add `poiEncounterDao()` abstract method
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/di/UserDataModule.kt` — Provide `PoiEncounterDao`
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt` — `@Inject lateinit var poiEncounterTracker`; `pruneStaleAtStartup()` call in `onCreate` next to GPS recorder; `flushAll()` call in `onDestroy`
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityTour.kt` — `poiEncounterTracker.recordObservation(nearby, System.currentTimeMillis())` after `narrationGeofenceManager.checkPosition()` in `updateTourLocation()`

### Verification
- `./gradlew :app-salem:assembleDebug` → BUILD SUCCESSFUL ~10s on each rebuild during the session. Only the pre-existing `setBuiltInZoomControls` deprecation warning at SalemMainActivity.kt:633.
- Installed and verified live on Lenovo TB305FU (HNY0CY0W) across 5+ PIDs throughout the session
- File sink confirmed writing to `/sdcard/Android/data/com.example.wickedsalemwitchcitytour/files/logs/debug-20260409.log`, 5,255+ lines by session end across 6 distinct process lifetimes
- GPS-OBS lines flowing every ~5 seconds, heartbeat ticking every 60s, lifecycle transitions clearly visible
- Circuit breaker tripping cleanly when cache-proxy unreachable, fast-failing subsequent calls in milliseconds, transition logged exactly once per OPEN/CLOSED change
- POI encounter tracker verified via walk-sim test: 9 encounters recorded with correct min/max/duration/fix_count values; database schema confirmed via `sqlite3 .schema poi_encounters`
- The runaway narration loop is dead — verified the new PID has zero `Salem Public Library` style cycling at the home (stale GPS) position

### Open items carried into S111
- **Phase 9P.B Step 9P.11 Highlight Duplicates wiring** — STILL the stated TOP PRIORITY, four sessions in a row without progress
- **`DWELL_MS = 3_000L` dead code** in `NarrationGeofenceManager`
- **Narration queue stale-pop check** — in dense POI clusters all 5+ in-range POIs queue and play back-to-back
- **Pre-existing GPS log redundancy** — 4 lines per fix from 4 layers
- **POI encounter review screen** — current path is `adb pull` + `sqlite3` or grep the persistent log; future session can add an in-app review screen
- **Cache-proxy unreachability** is a SECONDARY observation, NOT a code issue — operator's next field-test setup may want to verify the laptop is reachable from the tablet IP
- **9P.10a Linked Historical Content tab** — still blocked on Phase 9Q

---

## Session 109: 2026-04-09 — Side quest: column drift + GPS observer + GPS Journey recorder

### Summary
Side-quest session triggered by two operator pivots away from the carry-over 9P.11 task. **First pivot:** "the admin tool doesn't have all the POIs the app sees" — investigation confirmed `salem_tour_pois` (45) + `salem_businesses` (861) + `salem_narration_points` (814) = 1,720 POIs are all surfaced in the admin, the apparent "missing" POIs were either generic Overpass POIs (deliberate scope split, never in admin) or the operator misremembering. BUT the dig surfaced that **PG `salem_narration_points` had been carrying 814 NULL-narration rows since Phase 9P.2** — the importer was reading `salem-content/salem_content.sql` but that file's narration_points INSERTs all have `short_narration / long_narration / narration_pass_2 / narration_pass_3 = NULL`. The actual content lives in separate UPDATE files (`narration_content.sql`, `wave4_narration_content.sql`, `teaser_narrations.sql`, `promote_short_to_long.sql`) that the salem-content build pipeline applies AFTER the main INSERT to produce the populated `.db`. Switched the importer to read `salem_content.db` directly via better-sqlite3, removed the ~80-line SQL text parser, and incidentally fixed a column-name drift between PG (`pass1_narration / pass2_narration / pass3_narration`) and the bundled DB / Room entity (`narration_pass_2 / narration_pass_3`) by dropping the orphan `pass1_narration` (it was conceptually duplicate of `short_narration` per `NarrationGeofenceManager.kt:98`) and renaming pass2/3 PG-side. PG narration_points now has **817 fully populated rows** (short=817, long=817, narration_pass_2=12, narration_pass_3=12). Three "stale overpass" rows with `created_at = 0` triggered a separate fix via a `toTimestampOrNow()` helper. **Second pivot:** "GPS was terrible during my Salem test" on the Lenovo TB305FU — `adb dumpsys location` showed the chip is fine (lifetime mean TTFF 6.49s, accuracy 3.09m, 4 constellations: GPS+GLONASS+BeiDou+Galileo, 0% failure rate, last fix in Salem with hAcc=2.3m + 21 satellites tracked). The bug was `SalemMainActivity.kt:1272`'s `100m` static dead zone — at slow Salem traffic speeds (~6 m/s), 100m takes exactly 15 seconds, mathematically matching the operator's "10-15 sec GPS updates" report. Replaced with `(update.accuracy * 2f).coerceIn(5f, 100f)`, self-adapting across walking, slow driving, and L1-only multipath without mode-specific tuning. Two more fixes shipped: idlePopulate trigger gated to tour mode only (was wasting bandwidth in Explore mode pulling in POIs far outside the visible viewport), and a NEW "GPS Journey" feature — always-on Room recorder in a separate `UserDataDatabase`, 24h rolling retention, bottom-left FAB toggles polyline visibility (recording independent of toggle, manual on/off deferred to utility menu). Built debug APK and installed on the Lenovo for operator's next Salem test walk. **NO progress on 9P.11 this session** — Phase 9P.B is still 6/8 done. Also surfaced but not fixed: `DWELL_MS` in `NarrationGeofenceManager` is dead code (declared, never referenced — single fix glimpses can fire false-positive narration triggers in dense POI areas), and the narration queue has no stale-pop check (queues 5+ POIs in dense clusters and commits the operator to ~3-4 minutes of audio even after they walk away).

### Decisions
1. **Conform PG to bundled, not bundled to PG, for the narration column rename.** PG side has fewer call sites (cache-proxy + admin UI = 5 files) and is purely admin-tool consumed. Renaming bundled would require Room schema migration v3→v4 + salem-content build pipeline change + Android side regression risk. Chose the lower blast radius. PG is canonical for the *data*; column names are just labels.
2. **Dropped `pass1_narration` entirely.** Per `tools/salem-data/generate_multipass_narrations.py:5` and `NarrationGeofenceManager.kt:98`, "Pass 1: Standard narration (already exists in short_narration)" — the PG schema's separate `pass1_narration` column was an orphan that was never populated, never referenced by any read path, never going to be. Schema was carrying dead weight.
3. **Importer now reads from `.db`, not `.sql`.** This is a meaningful semantic shift but it's the only way to actually populate PG with the narration text. The .sql file is the unpopulated source; the .db is the post-build artifact with all UPDATE files applied. Per the pre-existing salem-content build pipeline, the .db is the source of truth for narration content.
4. **Defaulted `created_at` / `updated_at` to `now()` for the 3 stale overpass rows.** These had `created_at = 0` in the bundled DB, which `toTimestamp(0)` converts to `null`, which violates PG NOT NULL. Could have skipped those rows entirely or marked them with an "unknown timestamp" sentinel. Chose `now()` because it's the least-bad approximation and lets all 817 rows successfully import. The data quality issue is pre-existing in the bundled DB and out of scope.
5. **GPS proportional dead zone keys off `update.accuracy`, not `loc.accuracy`.** The activity's location flow goes through `LocationUpdate` (a UI-layer data class), not the raw `android.location.Location`. The accuracy field is on `update`, not on `point` (which is a `GeoPoint` from osmdroid with no accuracy field). First commit attempt used `point.accuracy` and failed compile — fixed by switching to `update.accuracy`.
6. **idlePopulate gate uses `is Active || is Paused`.** Active alone would skip the case where the operator paused at a tour stop to take a photo — the spiral is still useful in that case. Idle / Loading / Completed / Error all skip.
7. **Three separate commits for column drift / dead zone / idlePopulate gate**, achieved via Edit-revert-reapply on `SalemMainActivity.kt` since the dead zone fix and the idlePopulate gate touch the same file in adjacent regions. Clean bisect targets at the cost of one extra Edit cycle. The user's stated preference was "I'd recommend three separate commits so each is its own bisect target" from earlier in the conversation.
8. **GPS Journey feature is Option B** (recording always on, toggle controls display only), not Option A (toggle controls both). User explicit choice.
9. **GPS Journey storage in a NEW `UserDataDatabase`**, not the existing `SalemContentDatabase`. The latter is meant to be regenerated by the Phase 9P.C publish loop; mixing user-mutable journey data into it would create a regeneration hazard. Separation is non-negotiable for the architecture's long-term health.
10. **No batching in the GPS recorder.** GPS arrives at 5-60s intervals. Room insert overhead at that rate is negligible (<5ms per insert on the Lenovo). Avoiding batching means there's no "lost the last 5 fixes when the process was killed" failure mode. Re-evaluate only if profiling shows it matters.
11. **Polyline race-safety: empty-first then async-seed.** The polyline is created on the Main thread immediately (so the GPS observer's `addPoint` calls can never see a null reference), then the historical 24h is loaded asynchronously and merged with any in-flight points before `setPoints` is called. No mutex needed because both branches run on Main.
12. **DWELL_MS dead-code discovery is documented but not fixed.** Operator asked the right question ("does dwell make sense at 5s sampling?"), investigation showed the constant exists but is unwired. Recommended fix is a per-POI `firstSeenInside: Long?` map with "require 2 consecutive fixes" semantics, ~10 lines. Not applied because operator's stated scope was "fix the dead zone" + "fix the idlePopulate gate" + "build the GPS Journey feature" — adding the dwell fix would be scope creep without explicit authorization.
13. **APK built and installed on the Lenovo TB305FU before push.** Operator's next action is a Salem test walk; they need the binary on the device. Used `./gradlew :app-salem:assembleDebug` and `adb -s HNY0CY0W install -r`. Build verified clean.

### Files Changed
**Commit 1 — column drift cleanup (08d431e):**
- `cache-proxy/salem-schema.sql` — drop `pass1_narration`, rename `pass2_narration / pass3_narration`, rewrite multipass comment block to document tier vs pass distinction
- `cache-proxy/lib/admin-pois.js` — `NARRATION_FIELDS` whitelist updated
- `cache-proxy/scripts/import-narration-points.js` — source switched from `.sql` → `.db`, removed `extractNarrationPointsSql()` and `updateStringState()` helpers (~80 lines deleted), UPSERT column rename, `toTimestampOrNow()` helper for the 3 stale rows
- `web/src/admin/poiAdminFields.ts` — `NARRATION_FIELDS` whitelist (line-aligned with cache-proxy mirror)
- `web/src/admin/PoiEditDialog.tsx` — drop `pass1_narration` FieldRow block, rename `pass2_narration / pass3_narration` IDs / `reg()` / `has()` calls, hint text on Pass 2 explaining "Pass 1 is short_narration above"
- `docs/session-logs/session-109-2026-04-09.md` — NEW (live conversation log, started at session start)

**Commit 2 — GPS proportional dead zone (d229498):**
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt` — replace `< 100f` with `< deadZoneMeters` where `deadZoneMeters = (update.accuracy * 2f).coerceIn(5f, 100f)`, rewrite the dead zone comment block to explain the math, update stale "GPS moved >100m" log message to "GPS moved beyond dead zone"

**Commit 3 — idlePopulate tour gate (524a26e):**
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt` — add `tourActive` check (`tourState is TourState.Active || tourState is TourState.Paused`) to the idlePopulate trigger, rewrite the comment block, fix stale "5 min" → "10 min" comment

**Commit 4 — GPS Journey recorder + visibility toggle (5428795):**
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/db/GpsTrackPoint.kt` — NEW Room entity (id, ts_ms, lat, lng, accuracy_m, speed_mps, bearing_deg)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/dao/GpsTrackPointDao.kt` — NEW DAO (insert, getRecent, deleteOlderThan, count, deleteAll)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/db/UserDataDatabase.kt` — NEW Room v1, separate from `SalemContentDatabase`, lives in app data dir (NOT in assets)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/di/UserDataModule.kt` — NEW Hilt `@Provides @Singleton` for DB + DAO
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/GpsTrackRecorder.kt` — NEW singleton, fire-and-forget `recordFix()`, hourly piggyback prune, `getRecent24h()`, `pruneStaleAtStartup()`
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt` — `@Inject` recorder, `gpsTrackOverlay: Polyline?` field, `initGpsTrackOverlay()` (race-safe empty-first-then-async-seed), `setupGpsToggleButton()`, `isGpsTrackVisible() / setGpsTrackVisible()` SharedPreferences helpers, `recordFix()` call at the very TOP of the GPS observer (BEFORE the dead zone gate)
- `app-salem/src/main/res/layout/activity_main.xml` — new `btnGpsToggle` FloatingActionButton at `bottom|start` with `marginBottom=100dp` mirroring fabMain on the right, "GPS" TextView label below

**Commit 5 — session end docs (this commit):**
- `STATE.md` — header bumped to S109, new "S109 fixes summary" section with all 4 commits + 2 known-but-not-fixed items
- `CLAUDE.md` — version bumped to v1.5.70, session count 108 → 109 with full S109 summary
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-109-2026-04-09.md` — finalized with session-end section
- `~/Development/OMEN/reports/locationmapapp/session-109-2026-04-09.md` — NEW (OMEN session report)

### Status
- Phase 9P.B: still **6/8 done** (9P.6, 9P.7, 9P.8, 9P.9, 9P.10, 9P.10b) — no progress on 9P.11 this session
- Phase 9T+ added: GPS Journey recorder shipped (new feature, not previously in master plan)
- Next step: **Phase 9P.B Step 9P.11 — Highlight Duplicates wiring** (carried over from S107 → S108 → S109 → S110)
- 9P.10a still deferred (blocked on Phase 9Q)
- All open OMEN items still tracked: NOTE-L013 (debug Activity cleanup), NOTE-L014 / OMEN-008 (Privacy Policy), NOTE-L015 (stale, surfaced again), OMEN-002 (history rotation), OMEN-004 (first Kotlin unit test, deadline 2026-04-30 = ~3 weeks)
- New tech debt: `DWELL_MS` dead code in `NarrationGeofenceManager`, narration queue has no stale-pop check
- APK built and installed on Lenovo TB305FU (HNY0CY0W) for operator's next Salem test walk
- Working tree expected to be clean at S110 start

---

## Session 108: 2026-04-09 — Admin UI polish (tab wrap + backdrop opacity + legend z-index)

### Summary
Short polish session sandwiched between S107's 9P.10b finalization commit and the still-pending 9P.11 work. Operator pivoted from 9P.11 to ask for a live demo of the admin interface and a graphics-overlap check on the latest screenshot. Started cache-proxy on `:4300` (PID 49332) via `bash bin/restart-proxy.sh` and Vite on `:4302` (PID 49368) via `cd web && npm run dev` in background; delivered the URL `http://localhost:4302/admin` and Basic Auth creds (`admin` / `salem-tour-9P3-test`) to the operator. Read the latest screenshot (`~/Pictures/Screenshots/Screenshot from 2026-04-09 02-57-28.png`, 1360×670, POI edit dialog open on a "Custom House" tour POI). Identified two real graphics issues at max browser zoom and one out-of-scope crowding issue (Mullvad VPN browser-extension badge bumping into the header right-side cluster). Made three single-line edits across two files: `PoiEditDialog.tsx` `TabList` `flex gap-1 overflow-x-auto` → `flex flex-wrap gap-1` (so all 7 tabs wrap to a second row instead of clipping behind a horizontal scroll at narrow viewports), `PoiEditDialog.tsx` backdrop `bg-black/40` → `bg-black/65` (so the legend / map content is properly suppressed when the modal is open instead of bleeding through at 60% opacity), and `AdminMap.tsx` Legend `z-[400]` → `z-[100]` (so any modal stacking context cleanly stacks above it; side benefit: the move-confirm modal at `AdminMap.tsx:294` `z-[500]` now correctly suppresses the legend too). `cd web && npx tsc --noEmit` clean (exit 0). Vite HMR fired three updates and the operator confirmed visually after refresh. NO progress on 9P.11 this session — Phase 9P.B is still 6/8 done. NOTE-L015 still stale (surfaced again in this session's OMEN report).

### Decisions
1. **Treat the polish as a standalone commit, not as part of the upcoming 9P.11 commit.** The fixes are independent of the duplicates wiring; folding them into a 9P.11 commit would muddy the diff and make bisecting harder. A standalone "admin UI polish" commit on top of S107 is the cleanest "save context" action when the user calls session end mid-task.
2. **Tab row wrap (`flex flex-wrap`) instead of shrink-to-fit or horizontal scroll.** Wrap is the simplest CSS change, doesn't require touching `tabClass`, and works at any viewport width. Trade-off: when there's space for one row, the tabs still take one row; when there isn't, they wrap to two. No conditional layout logic.
3. **Backdrop opacity bump 40% → 65%, not 100%.** 65% still shows enough context for the operator to know what POI they're editing without the underlying map / legend competing for attention. 100% would be a hard cut and feel modal-disruptive.
4. **Lowered legend z-index, did NOT add a "hide when dialog open" context flag.** Lower z-index is one character; passing an `editOpen` flag down to AdminMap would have required threading state through props. Same end result.
5. **Did NOT bump the move-confirm modal's `bg-black/30` backdrop.** Flagged it for the operator as a "say the word" follow-up. They didn't ask for it.
6. **Did NOT fix the header right-side cluster crowding.** That's the Mullvad VPN browser extension badge bumping into the admin tool's header; not fixable in app code without artificial right-padding. Logged in the live log as out-of-scope.
7. **Stopped both background servers at session end.** Salem Oracle on `:8088` left running (not owned by this project). The cache-proxy and Vite restart commands are documented in the memory file so S109 can bring them back up cheaply.

### Files Changed
- `web/src/admin/PoiEditDialog.tsx` — modified (2 lines: TabList wrap class, backdrop opacity)
- `web/src/admin/AdminMap.tsx` — modified (1 line: Legend z-index)
- `STATE.md` — modified (header bumped to S108, top-priority blurb extended with the polish summary)
- `CLAUDE.md` — modified (session count 107 → 108)
- `SESSION-LOG.md` — modified (this entry)
- `docs/session-logs/session-108-2026-04-09.md` — NEW (live conversation log)
- `~/.claude/projects/.../memory/project_next_session_priority.md` — modified (dependency-state section bumped to "after Session 108", added a polish-landed bullet, added a server-restart hint, added the S108 live log to the reference list)
- `~/Development/OMEN/reports/locationmapapp/session-108-2026-04-09.md` — NEW (OMEN session report)

### Status
- Phase 9P.B: still **6/8 done** (9P.6, 9P.7, 9P.8, 9P.9, 9P.10, 9P.10b) — no progress on 9P.11 this session, polish only
- Next step: **Phase 9P.B Step 9P.11 — Highlight Duplicates wiring** (carried over from S107 → S109)
- 9P.10a still deferred (blocked on Phase 9Q)
- All open OMEN items still tracked
- Working tree expected to be clean at S109 start

---

## Session 107: 2026-04-09 — Phase 9P.B Step 9P.10b finalization (recovery + commit)

### Summary
Crash-recovery + commit session for the cross-midnight S106 work. S106 ran 2026-04-08 → 2026-04-09 and implemented Phase 9P.B Step 9P.10b (Salem Oracle integration) end-to-end including the live smoke test, but the session ended without committing — three artifacts were left dirty on disk: untracked `web/src/admin/oracleClient.ts` (~248 lines), modified `web/src/admin/AdminLayout.tsx` (+137/-8 with the Oracle status pill polling), and modified `web/src/admin/PoiEditDialog.tsx` (+440/-1 with the Generate sub-dialog modal). S107 verified the work was complete and clean: re-ran `cd web && npx tsc --noEmit` (exit 0), re-ran `npm run build` (clean, 539 modules, 802.45 kB / 234.29 kB gzip — identical to S106 final numbers, no drift), confirmed the live Salem Oracle was still up at `http://localhost:8088` (PID 8167, `available:true`, 3891 facts / 4950 primary sources / 63 POIs / 202 newspapers loaded, same Oracle session S106 hit), and committed the work as `a498562` titled "Session 106 → 107: Phase 9P.B Step 9P.10b — Salem Oracle integration". The commit included `STATE.md` and `WickedSalemWitchCityTour_MASTER_PLAN.md` (both already updated by S106), the new `oracleClient.ts`, the modified `AdminLayout.tsx` and `PoiEditDialog.tsx`, both session-106 live log files (the 04-08 stub and the 04-09 cross-midnight finalization log), and the new S107 live log. Master plan and STATE.md left as-is from S106 (no further changes needed). Updated `~/.claude/projects/.../memory/project_next_session_priority.md` to point at Phase 9P.B Step 9P.11 (Highlight Duplicates wiring) as the new top priority. **NOTE-L015 confirmed stale a second time** in S107: `~/Development/SalemCommercial/` still does not exist on this workstation; the OMEN-016d "grand cutover" has not landed here; the `salem-content/` hardcoded paths are still valid. Both S106 and S107 OMEN reports surface this for retraction or re-attempt. After S107 commit, this session-end follow-up updates `CLAUDE.md` (107 sessions, v1.5.69) and prepends this entry to `SESSION-LOG.md`.

### Decisions
1. **Treat the dirty disk state as recovery, not as parallel branching.** S106's work was substantively complete and matched the master plan §1332-1387 build task list exactly; the right move was to verify + commit, not rewrite or rebase.
2. **Re-ran tsc + build to confirm no drift** between S106's final state and the current disk. Identical outputs → green to commit.
3. **Skipped a redundant `/api/oracle/ask` smoke test** because S106 already did two real ones and the typed client matches the live API exactly.
4. **Did not touch master plan or STATE.md** — both were already correctly updated by S106.
5. **Did not touch `session-106-*.md` live logs** — both files (the 04-08 stub and the 04-09 cross-midnight finalization log) are S106's work product and should remain as-written for traceability.
6. **Bumped session count to 107 in CLAUDE.md** because S107 is the canonical "completion" session per the commit hash, even though S106 did the implementation work. The OMEN reports for both sessions remain separate to preserve accountability.

### Files Changed
- (committed by S107) `web/src/admin/oracleClient.ts`, `web/src/admin/AdminLayout.tsx`, `web/src/admin/PoiEditDialog.tsx`, `STATE.md`, `WickedSalemWitchCityTour_MASTER_PLAN.md`, `docs/session-logs/session-106-2026-04-09.md`, `docs/session-logs/session-107-2026-04-09.md` — all part of commit `a498562`
- `~/.claude/projects/.../memory/project_next_session_priority.md` — rewritten by S107 to point at 9P.11
- `CLAUDE.md` — modified (this session, post-commit follow-up: bumped session count 106 → 107)
- `SESSION-LOG.md` — modified (this session, this entry)
- `~/Development/OMEN/reports/locationmapapp/session-106-2026-04-09.md` — S106 OMEN report
- `~/Development/OMEN/reports/locationmapapp/session-107-2026-04-09.md` — S107 OMEN report (this session)

### Status
- Phase 9P.B: **6/8 done** (9P.6, 9P.7, 9P.8, 9P.9, 9P.10, 9P.10b)
- Next step: **Phase 9P.B Step 9P.11 — Highlight Duplicates wiring**
- 9P.10a still deferred (stub in dialog, blocked on Phase 9Q)
- Commit `a498562` lands the implementation; this session-end follow-up adds the doc-level wrap-up

---

## Session 106: 2026-04-09 — Phase 9P.B Step 9P.10b (Salem Oracle "Generate with AI" integration)

### Summary
Built **Phase 9P.B Step 9P.10b** end-to-end against the live Salem Oracle. New `web/src/admin/oracleClient.ts` (~225 lines) is a typed wrapper for the Salem testapp's HTTP API at `http://localhost:8088/api/oracle/*` (override via `VITE_SALEM_ORACLE_URL`). It exports `getStatus`, `ask`, `listPois`, `isAskOk` plus typed shapes (`OracleStatus`, `OracleAskOk`/`OracleAskErr`, `OraclePrimarySource`, `OraclePoiSummary`); a custom `OracleNetworkError` distinguishes connection failures from successful HTTP responses with `error` envelopes; `fetchWithTimeout` uses `AbortController` with 5s timeouts on status/catalog calls and 120s on `ask` (60s minimum per `oracle-api.md`, 120s for cold-cache headroom). `AdminLayout.tsx` grew a polled state machine (`loading`/`ready`/`unavailable`) that hits `/api/oracle/status` at mount and every 30s; click-to-recheck supplements the timer; the new `OraclePill` component renders three visual states with rich tooltips (ready: corpus counts + history turn count; down: start command + reason). The pill's `oracleAvailable` is mirrored down to `PoiEditDialog` via two new props plus `onOracleRefresh`. `PoiEditDialog.tsx` grew an `OracleLauncher` component with two variants — a banner at the top of the Narration tab and a compact button under the General tab description textarea — both opening the same nested Headless UI `<Dialog>` (z-1100, layered above the main edit dialog at z-1000). The sub-dialog has a prompt textarea + Reset history checkbox (default ON, auto-flips OFF after first generation so iterate works without re-checking) + Generate button → `oracleClient.ask({ question, current_poi_id: poi.id, reset })`; a violet spinner panel during the call; the returned `text` rendered in a slate panel with `whitespace-pre-wrap` and turn count; "Insert into short_narration / long_narration / description" buttons filtered through `has(field)` (so tour POIs without those fields don't see no-op buttons); an Iterate button that forces `reset:false` regardless of checkbox state; up to 8 `primary_sources` rendered in a collapsible `<details>` block with attribution + score + verbatim text + modern_gloss in amber callouts. Insert flow: `setValue(field, text, { shouldDirty: true, shouldTouch: true })` so the existing react-hook-form dirty-tracking → PUT pipeline picks it up automatically. **Audit log:** `localStorage[salem-oracle-audit]` capped at 500 entries FIFO, logged on **insert** events only (not on generate — generate-but-discard is just thinking out loud), with `{ ts, poi_id, poi_kind, field, question, text, primary_sources, history_turn_count }`. Concurrent calls serialized client-side per master plan §1383. **End-to-end smoke test passed against live Oracle** (PID 8167, started by operator mid-session): two real `POST /api/oracle/ask` calls verified all 9 expected `OracleAskOk` fields present, 8 `primary_sources` populated correctly, `current_poi_id="lma_smoke_test_poi_id_not_a_real_one"` accepted as arbitrary string per master plan §1352. `npx tsc --noEmit` clean, `npm run build` clean (539 modules, 802 kB / 234 kB gz, +12 kB / +3 kB gz over S105). **NOTE-L015 flagged stale to OMEN** in this session's report — `~/Development/SalemCommercial/` does not exist on this workstation, the cutover never landed here, the `salem-content/` pipeline is healthy.

### Decisions
1. **Direct browser → Oracle calls, no Vite proxy.** Per master plan §1341 and `oracle-api.md`, the Oracle serves permissive CORS. The existing `/api/*` Vite proxy strips `/api` and forwards to the cache-proxy at `http://10.0.0.229:4300`, which has no `/oracle/*` routes — routing through it would not work. Direct call avoids the indirection and matches the contract.
2. **Configurable base URL via `VITE_SALEM_ORACLE_URL`.** Default `http://localhost:8088`. Useful when the operator runs the admin tool from a LAN host while the Salem testapp runs on the dev box; the testapp binds to `*:8088` so the LAN reach works as long as the base URL is set.
3. **Status polling at mount + every 30s.** Cheap (sub-millisecond on the Salem side, 5s client timeout). Click-to-recheck supplements the timer so the operator never has to wait for the next tick after starting the testapp.
4. **Three-state pill machine** (`loading`/`ready`/`unavailable`) instead of a binary boolean. The loading state matters because the first poll has 5s of latency on cold start, and rendering the pill as "down" during that window would be misleading.
5. **Sub-dialog as nested Headless UI `<Dialog>` at z-1100.** Pros: focus trap, ESC handling, backdrop click, visual layering all handled by Headless UI; con: nested dialogs aren't a documented happy path in HUI v2 but tested cleanly here.
6. **Reset checkbox auto-flips OFF after first successful generation.** Spec says first call should reset history (cross-POI bleed protection) but iterate calls should not. Auto-flipping is the natural UX.
7. **Two launcher placements, one shared sub-dialog state.** Narration tab gets a banner-style `OracleLauncher`, General tab gets a compact variant under the description textarea — both open the same sub-dialog. Insert buttons filtered through `has(field)` so POI kinds without `short_narration`/`long_narration` don't see no-op buttons.
8. **Audit log on INSERT, not GENERATE.** Generate-but-discard is just thinking out loud and shouldn't pollute the log. Insert is the moment of acceptance.
9. **Insert buttons replace, not append.** Operator sees the result before clicking, so it's an intentional commit. `setValue(field, text, { shouldDirty: true, shouldTouch: true })` — the existing react-hook-form dirty-tracking + PUT pipeline picks it up automatically.
10. **NOTE-L015 surfaced as stale at session start.** Filesystem check confirmed `~/Development/SalemCommercial/` does not exist; LMA's `salem-content/` hardcoded paths still resolve. No code changes — would have been a no-op breaking change against valid paths. Flagged in the OMEN session report for retraction or re-attempt.

### Files Changed
- `web/src/admin/oracleClient.ts` — NEW (~225 lines, typed Salem Oracle client)
- `web/src/admin/AdminLayout.tsx` — modified (+126 lines: state machine, polling, `OraclePill` component, prop wiring)
- `web/src/admin/PoiEditDialog.tsx` — modified (+440 lines: Oracle sub-dialog, two `OracleLauncher` placements, audit log helper, new `OracleLauncher` component)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — modified (Step 9P.10b heading + 7 build tasks marked DONE with implementation notes)
- `STATE.md` — modified (header bumped to S106; TOP PRIORITY → 9P.11; 9P.10b row marked DONE)
- `SESSION-LOG.md` — modified (this entry)
- `docs/session-logs/session-106-2026-04-09.md` — NEW (live conversation log)

### Status
- Phase 9P.B: **6/8 done** (9P.6, 9P.7, 9P.8, 9P.9, 9P.10, **9P.10b**)
- Next step: **Phase 9P.B Step 9P.11 — Highlight Duplicates wiring** (the existing `/api/admin/salem/pois/duplicates?radius=15` endpoint built in 9P.5 needs UI on the admin map)
- 9P.10a still deferred (stub in dialog, blocked on Phase 9Q)
- Salem Oracle is now a documented cross-project dev surface (consumption only — read-only, dev-only, browser-direct, no APK/production impact)

---

