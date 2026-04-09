# LocationMapApp — Session Log

> Sessions prior to v1.5.51 archived in `SESSION-LOG-ARCHIVE.md`.

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

## Session 105: 2026-04-08 — Phase 9P.B Step 9P.10 (POI edit dialog — tabbed Headless UI modal)

### Summary
Built `web/src/admin/PoiEditDialog.tsx` (~1,193 lines) and `web/src/admin/poiAdminFields.ts` (~75 lines, TS mirror of `cache-proxy/lib/admin-pois.js` field whitelists). The dialog is a Headless UI v2 `<Dialog>` + `<TabGroup>` modal with kind-aware tabs (General · Location · Hours & Contact · Narration · Provenance · Linked Historical (9P.10a stub) · Danger Zone) covering every column in `TOUR_FIELDS`/`BUSINESS_FIELDS`/`NARRATION_FIELDS`. Form state via `react-hook-form`; only `formState.dirtyFields` are sent to `PUT /api/admin/salem/pois/:kind/:id` so the partial-update endpoint never gets a clobbering full row. JSONB textareas pretty-print on load (`JSON.stringify(value, null, 2)`) and `JSON.parse` on submit — parse failures surface inline and abort save. Numeric fields coerce empty→null and abort on non-numeric strings. Date fields slice to YYYY-MM-DD. Tour-only boolean flags render as checkboxes. The category field is a free-text input + `<datalist>` of observed values from the loaded snapshot (computed in AdminLayout via a new `knownCategories` memo: 7 categories for tour, 19 business_types, 29 categories for narration). Soft-delete in the Danger Zone tab uses an inline confirm prompt (no nested dialog) → `DELETE /api/admin/salem/pois/:kind/:id` → `onDeleted` callback patches the row's `deleted_at` in the shared `byKind` snapshot so the tree ghosts it and the map drops it. Save flow: PUT → `onSaved(kind, updated)` patches the row in `byKind` + resets the form to the new baseline + closes. Cancel with dirty fields → `window.confirm("Discard and close?")`. Footer shows dirty-field count "N field(s) modified" or "No changes". **Open-trigger split:** marker click in `AdminMap` opens the dialog AND selects (per master plan §1296); tree click only selects + flies the map. Implemented via two new AdminLayout callbacks (`handleTreeSelect` and `handleMapSelect`) replacing the single `handlePoiSelect` from S104. `react-hook-form@7.72.1` and `@headlessui/react@2.2.10` installed (20 packages added, no new audit warnings). `npx tsc --noEmit` clean. `npm run build` clean (538 modules, 790KB bundle, no new warnings beyond pre-existing chunk-size note).

### Decisions
1. **Single dialog component, kind-aware via prop, conditional fields with `has(field)` guards.** Three separate dialog components (one per kind) would have ~70% duplicated JSX for the shared fields (name, lat, lng, address, phone, website, hours, image_asset, provenance) and would force the maintainer to remember to mirror changes across three files. One file with `has('subcategories') && (...)` blocks is uglier per-line but objectively easier to keep aligned with the cache-proxy whitelist.
2. **TS mirror of cache-proxy whitelists in `poiAdminFields.ts`** rather than fetching the schema or re-deriving from PG metadata. The cache-proxy already enforces the whitelist server-side; the client mirror just keeps the form scoped to writable fields. The two files must stay aligned when columns are added — comment at the top of `poiAdminFields.ts` calls this out explicitly.
3. **`react-hook-form` `formState.dirtyFields` for partial-update payloads.** The PUT endpoint is partial-update friendly. Sending only dirty fields means: no accidental clobbering of server-only fields, no needless `updated_at` bumps, no full-row payloads for one-field changes, and the dialog footer can show an honest "N field(s) modified" count.
4. **JSONB editor as raw textarea + JSON.parse on submit.** A real array editor would have been nicer for `tags`, `subcategories`, `related_*_ids`, etc., but the JSONB columns store heterogeneous shapes (some are string arrays, some are object arrays like `action_buttons`) so a generic editor is the only honest option for now. Parse-on-submit with inline error surfacing is the simplest correct path; a proper structured editor can land in a later step if the operator pushes back.
5. **Category field is free-text + `<datalist>`, NOT a fixed dropdown from `poiCategories.ts`.** The Android app's 22-category taxonomy doesn't match what's actually in the DB (7 tour categories, 19 business_types, 29 narration categories). Forcing a dropdown would lock the operator out of new values. Free text + datalist of observed values gives autocomplete without the lockout. Computed in AdminLayout via a `knownCategories` memo so the dialog doesn't need access to the full dataset.
6. **Open trigger split: marker click opens, tree click does not.** The master plan §1296 says "click marker → opens edit dialog (placeholder until 9P.10)" — opening the modal on every tree click would launch a dialog while the operator is just navigating. Two callbacks: `handleTreeSelect` (select-only) and `handleMapSelect` (select + flip `editOpen=true`). The single `handlePoiSelect` from S104 was split.
7. **9P.10a Linked Historical Content tab is a stub** ("No links yet — see Phase 9Q") since Phase 9Q hasn't run. The tab exists in the dialog so the muscle memory is right; the real implementation lands after the building→POI bridge is built.
8. **9P.10b Salem Oracle integration deferred to its own step** — the master plan calls for a "Generate with Salem Oracle" button on the Narration tab that opens its own sub-dialog and POSTs to `http://localhost:8088/api/oracle/ask`. Marked with a TODO comment in the file at the slot where the button will land. Keeping S105 scoped to the form itself.
9. **Soft-delete confirm is an inline prompt inside the Danger Zone tab, not a nested dialog.** Nested Headless UI dialogs would fight for focus management and add a dependency on `Disclosure` or another nesting primitive. An inline `confirmDelete` boolean state with cancel/confirm buttons is simpler and stays within the tab.
10. **Did NOT add an Oracle button to the General tab's `description` field even though master plan §1355 says to.** That's part of 9P.10b. Added a TODO comment at the top of the Narration tab pointing at the slot.

### Files Changed
- `web/src/admin/PoiEditDialog.tsx` — NEW (~1,193 lines)
- `web/src/admin/poiAdminFields.ts` — NEW (~75 lines, TS mirror of cache-proxy whitelists)
- `web/src/admin/AdminLayout.tsx` — modified (split `handlePoiSelect` into `handleTreeSelect`/`handleMapSelect`; added `editOpen` state, `handlePoiSaved`, `handlePoiDeleted`, `handleEditClose` callbacks, `knownCategories` memo; rendered `<PoiEditDialog>` at the layout root; doc comment block refreshed)
- `web/package.json` + `web/package-lock.json` — `react-hook-form@^7.72.1` + `@headlessui/react@^2.2.10` added (20 packages, no new audit warnings)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.10 marked DONE with full implementation checklist
- `STATE.md` — Last Updated → S105; TOP PRIORITY → Step 9P.10b; Phase 9P.B status row for 9P.10
- `CLAUDE.md` — session count 104 → 105; Phase 9P.B status 3/8 → 5/8; "next" pointer → 9P.10b
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-105-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/project_next_session_priority.md` — refreshed to point at Step 9P.10b (Salem Oracle)

### Verification
- `cd web && npx tsc --noEmit` → exit 0, zero diagnostics
- `cd web && npm run build` → success, 538 modules transformed, dist/assets/index-*.js 790.08 kB / gzip 231.06 kB, no new warnings
- NOT browser-tested by Claude — the save/delete flows and the JSONB parse-on-submit handling are unverified end-to-end. First operator smoke test happens next session.

---

## Session 104: 2026-04-08 — Phase 9P.B Step 9P.9 (Admin map view via Leaflet + markercluster) + assessment delta review

### Summary
Built `web/src/admin/AdminMap.tsx` (~370 lines) and wired it into the AdminLayout center pane, replacing the 9P.9 placeholder. Map renders all ~1,720 active POIs as a single `L.markerClusterGroup` driven imperatively via `useMap()` (rather than the `react-leaflet-markercluster` wrapper, which is stuck on react-leaflet v4 while we're on v5). Markers are color-coded by kind (tour=red `#dc2626`, business=blue `#2563eb`, narration=green `#059669`) using cached `L.divIcon` SVG circles, with a separate single-marker recolor effect for selection highlighting (gold ring) so selection clicks don't trigger full cluster rebuilds. Tree-click → AdminLayout `selectedPoi` state → `<FlyToSelected>` calls `map.flyTo([lat,lng], max(currentZoom,17), {duration: 0.6})`. Marker drag captures origin at `dragstart`, fires confirm modal at `dragend` with kind/name/id/from/to/Haversine distance, Confirm POSTs to `/api/admin/salem/pois/:kind/:id/move`, AdminLayout's `onPoiMoved` callback patches the matching row in the shared `byKind` snapshot so PoiTree (consuming the same data via the new `externalByKind` prop) stays consistent. Cancel and HTTP failure both call `marker.setLatLng(origin)` to revert. Soft-deleted rows are filtered from the map (still visible in the tree behind its existing toggle). Top-right legend overlay shows kind colors + visible counts. Cluster config: `disableClusteringAtZoom: 18`, `maxClusterRadius: 50`, `spiderfyOnMaxZoom: true`. PoiTree gained two optional props: `onDataLoaded` (fires once after initial fetch) and `externalByKind` (snapshot override) — lets AdminLayout hoist the dataset and feed both panes from one shared fetch with one Basic Auth prompt. `leaflet.markercluster@1.5.3` + `@types/leaflet.markercluster` installed (2 packages added, NO new audit warnings — the 3 pre-existing high-severity transitives from S103 are unchanged). MarkerCluster CSS imported in `web/src/index.css`; `.admin-poi-marker` reset added to strip leaflet's default white divIcon background. `npx tsc --noEmit` clean. Side-task before code work: per user request, did a delta review of all OMEN notes (L001–L014) and directives (OMEN-002 through OMEN-011) against current project state — see live log for full table. Live demo via `bin/restart-proxy.sh` + `bin/start-web.sh` confirmed end-to-end: cache-proxy 4300 + vite dev 4302 + Basic Auth + 1,720 POI counts.

### Decisions
1. **Imperative cluster layer over `react-leaflet-markercluster`.** The wrapper is built for react-leaflet v4. We're on v5, which introduced internal API changes the wrapper hasn't caught up to. The official escape hatch is to use `useMap()` and drive the cluster group imperatively. ~30 lines of imperative code beats another upstream-lagging dependency, especially given the project already has 3 pre-existing high-severity audit warnings.
2. **Color by KIND, not category.** The 22-category taxonomy belongs to the tree's grouping. On the map the operator needs at-a-glance "what kind is this" — and there are too many categories to color-distinguish meaningfully. Per-category coloring can be revisited in 9P.11 (highlight duplicates) if needed.
3. **Single shared `byKind` dataset hoisted to AdminLayout.** Both the tree and the map need the same 1,720-row snapshot. Two fetches would mean two Basic Auth prompts (or a race) and double the network. Lifted state via PoiTree's new `onDataLoaded` callback + `externalByKind` prop. Drag-to-move patches the snapshot in place via `onPoiMoved`, and the tree re-renders off the new state without a re-fetch. This is the same pattern the edit dialog (9P.10) will use for save flows.
4. **Selection highlight via single-marker recolor, not full cluster rebuild.** `markersByKeyRef` is a `Map<"kind:id", {marker, kind}>` that lets a separate `useEffect` touch only the previously- and currently-selected marker icons on selection change. Full cluster rebuild on every selection click would be ~1,700 marker allocations + cluster reflow per click.
5. **Confirm modal is an overlay div, not a Leaflet popup.** Z-index 500, plain JSX. Easier to style with the rest of the admin tool's Tailwind palette, easier to show error messages inline, easier to manage focus and busy state. Leaflet popups would have fought all three.
6. **Soft-deleted rows filtered from the map but kept in the tree.** Tree is the operator's hierarchical inventory (tombstones useful for restore decisions); map is the spatial workspace (tombstones add clutter).
7. **`disableClusteringAtZoom: 18`** so the operator can drag individual POIs at deepest zoom without fighting cluster aggregation. **`maxClusterRadius: 50`** — half the leaflet default (80); at 1,720 POIs in downtown Salem the default produces too-large meta-clusters that hide merchant clumps.
8. **Did NOT install `react-hook-form` or `@headlessui/react`** despite the master plan listing them — those are 9P.10 dependencies, not 9P.9 dependencies. Keeping the install scoped to what this step actually needs.
9. **Defer click→edit dialog to 9P.10.** User asked at end of demo whether marker click could open the dialog. Currently the click sets `selectedPoi` but no dialog component exists. Offered a 5-10min read-only `PoiInspector` stub vs full Step 9P.10 work. User chose to defer entirely to next session as proper 9P.10. The selection-lifting hook is already in place — 9P.10 just renders against `selectedPoi`.

### Files Changed
- `web/src/admin/AdminMap.tsx` — NEW (~370 lines)
- `web/src/admin/AdminLayout.tsx` — modified (lifted `byKind` + `selectedPoi` state, new `handleDataLoaded` and `handlePoiMoved` callbacks, replaced center-pane placeholder with `<AdminMap />`, header diagram comment block updated)
- `web/src/admin/PoiTree.tsx` — modified (new optional props `onDataLoaded` + `externalByKind`, internal `effectiveByKind` threaded through render path, no behavior change for standalone usage)
- `web/src/index.css` — added MarkerCluster CSS imports + `.admin-poi-marker` reset
- `web/package.json` + `web/package-lock.json` — `leaflet.markercluster@^1.5.3` + types added
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.9 marked DONE with full notes; Stack additions section refreshed
- `STATE.md` — Last Updated → S104; Phase 9P.B status row for 9P.9; TOP PRIORITY → Step 9P.10
- `CLAUDE.md` — Project Status section refreshed: session count 76 → 104, "Phase 10 next" replaced with current 9P/9Q/9R structure
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-104-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/project_next_session_priority.md` — refreshed to point at Step 9P.10

### Verification
- `cd web && npx tsc --noEmit` → exit 0, zero diagnostics
- Live demo confirmed end-to-end: counts = 45 + 861 + 814 = 1,720 (matches PG canonical from S101 9P.A.3); 401 challenge confirmed with `WWW-Authenticate: Basic realm="LocationMapApp Admin"`; user opened the page in browser
- NOT browser-tested by Claude — drag-to-move and fly-to flows are unverified end-to-end. Type system says they're correct; first user smoke test of those flows happens next session

### Side-task — OMEN assessment delta review
User requested a review of all old OMEN assessments since "a lot has changed". Findings (full table in live log):
- **Notes:** L001 done; L002 ⚠️ partial (history rotation pending); L003 deprioritized; **L004 ⚠️ unmet (deadline 2026-04-30)**; L005 stale premise (Phase 10 deferred behind 9P/9Q/9R); L006 evolving (PG canonical for POIs, Salem path stable); L007/L008/L010/L011 unchanged; L009 mostly met; L012 done; **L013 scope expanded** (VoiceTestActivity.kt also tracked now); L014 still owed
- **Directives:** OMEN-002 partial; **OMEN-004 unmet (deadline 2026-04-30)**; OMEN-006/007 done; OMEN-008 Privacy Policy still owed; **OMEN-011 confirmed Salem stayed in `~/Development/Salem/`** so cross-project path is unchanged
- Memory file `project_next_session_priority.md` was stale (still 9P.8); refreshed mid-session to 9P.10

### What's next
Phase 9P.B Step 9P.10 — POI edit dialog (tabbed modal). `react-hook-form` + `@headlessui/react` install, six tabs (General · Location · Hours & Contact · Narration · Provenance · Danger Zone), per-kind field whitelists copied from `cache-proxy/lib/admin-pois.js`, JSONB editors, PUT to `/admin/salem/pois/:kind/:id`, optimistic update via the same `byKind` patch pattern AdminLayout's `handlePoiMoved` already uses, dirty-tracking with warn-before-close. Memory pointer is current; AdminLayout's `selectedPoi` state is the open trigger.

---

## Session 103: 2026-04-08 — Phase 9P.B Step 9P.8 (POI tree via react-arborist)

### Summary
Built `web/src/admin/PoiTree.tsx` (~330 lines) and wired it into the AdminLayout left pane, replacing the 9P.7 placeholder. Tree fetches all 1,720 active POIs across the three kinds (`tour` 45 sequential first to fire the Basic Auth prompt cleanly, then `business` 861 + `narration` 814 in parallel reusing cached credentials) via `/api/admin/salem/pois?kind=...&include_deleted=true&limit=2000`, groups them as **kind → category → POI rows**, renders with react-arborist's virtualized list, supports client-side name search via `searchTerm`/`searchMatch`, soft-deleted toggle (client-side filter on the cached rows — no re-fetch, no re-prompt), counts per kind/category in the row labels and a totals strip in the toolbar, and emits `onSelect({kind, poi})` events into AdminLayout (currently `console.log`'d — the map pan/zoom in 9P.9 and edit dialog in 9P.10 will consume them). `useElementSize` ResizeObserver hook supplies the explicit pixel `width`/`height` that react-arborist needs for its virtualized list. `react-arborist@^3.4.3` added to `web/package.json` (16 packages, peer requirement is open-ended `react: >= 16.14` so React 19 is fine). Three pre-existing high-severity transitive audit warnings (`vite`, `picomatch`, `socket.io-parser`) surfaced but NOT introduced by this install — flagged for a separate cleanup pass. `npx tsc --noEmit` clean.

### Decisions
1. **Three-level tree, not four.** The master plan and the next-session memory both said "kind → category → subcategory → POI rows" (4 levels). Recon at session start queried PG and found: tour uses 7 distinct `category` values, business uses 19 distinct `business_type` values, narration uses 29 distinct `category` values — and `salem_narration_points.subcategory` is **NULL for all 814 rows**. `salem_tour_pois.subcategories` is a JSONB list of refinements (not a single column suitable for grouping). The fourth level isn't physically present in the data, so the tree collapses to three. Documented the deviation in the `PoiTree.tsx` header so a future session can re-add the fourth level when narration's subcategory column gets backfilled (or tour's JSONB list gets promoted) by editing `buildTree()` only.
2. **Sequential `tour` first, then parallel `business`+`narration`.** The first call to `/api/admin/salem/pois` triggers the browser's native HTTP Basic Auth dialog. Firing 3 simultaneous 401-eligible requests races the dialog and can cause some browsers to show the prompt multiple times. Awaiting `tour` (the smallest, 45 rows) sequentially first ensures the user sees exactly one prompt; once the browser caches credentials, the other two kinds fetch in parallel reusing them. Universal modern browser behavior — if a future session sees auth prompts firing 3x, the fix is to also serialize business+narration.
3. **Fetch with `include_deleted=true` once, filter client-side.** The "Show soft-deleted POIs" toggle rebuilds the tree from the cached rows. Flipping it is instant and never re-prompts for auth. Deleted rows render greyed-out italic with a "(deleted)" suffix.
4. **No taxonomy lookup at grouping time.** Categories in the DB are domain-specific OSM-style strings (`witch_trials`, `shop_retail`, `historic_site`, etc.), NOT layer IDs from `web/src/config/poiCategories.ts` (which uses `FOOD_DRINK`, `SHOPPING`, etc.). Decided not to attempt a lookup — just `groupBy` on the raw field. Each kind has its own vocabulary; overlapping names (e.g. `restaurant` in both business and narration) are scoped under their kind so no collision. The taxonomy file remains the source of truth for the *edit dialog* (9P.10) when the operator picks a category from a dropdown, but it's not needed for the tree's grouping shape.
5. **`useElementSize` ResizeObserver hook.** react-arborist requires explicit pixel `width`/`height` for its virtualized list. The hook handles measurement and reflow on browser resize so the tree fills the left pane. Future drag-handle for the left pane gets resize-responsiveness for free.
6. **`disableMultiSelection`/`disableEdit`/`disableDrag`/`disableDrop` all true.** The tree is read-only; mutations happen through the edit dialog (9P.10) and the admin map (9P.9). Locking down the unused react-arborist features is one less surface area to reason about.
7. **Defensive `parseFloat` on lat/lng.** node-postgres returns DOUBLE PRECISION as JS numbers in the current driver version, but the coerce is cheap insurance for the 9P.9 map view in case a future serializer ever string-ifies them.
8. **Pre-existing audit warnings deferred.** `npm audit` after the react-arborist install reported 3 high-severity transitive deps (`picomatch`, `socket.io-parser`, `vite`) — none introduced by react-arborist. Verified they live under `node_modules/{vite,tinyglobby,socket.io}/...`, all pre-existing. Tracked for a separate cleanup pass; not blocking 9P.8.

### Files Changed
- `web/src/admin/PoiTree.tsx` — NEW (~330 lines)
- `web/src/admin/AdminLayout.tsx` — modified (import `PoiTree`/`PoiSelection`, add `handlePoiSelect` callback, replace left-pane placeholder, update header comment)
- `web/package.json` + `web/package-lock.json` — `react-arborist@^3.4.3` added
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.8 marked DONE with full implementation notes including the 3-vs-4-level tree decision
- `STATE.md` — Last Updated → S103; Phase 9P.B status row for 9P.8; TOP PRIORITY → Step 9P.9 (Admin map view)
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-103-2026-04-08.md` — live conversation log

### Verification
- `cd web && npx tsc --noEmit` → clean exit, zero errors

---

## Session 102: 2026-04-08 — Phase 9P.B Step 9P.7 (Admin route + AdminLayout shell)

### Summary
First step of Phase 9P.B Admin UI build-out. Added a `/admin` route to the web app via path-based dispatch in `web/src/main.tsx` — chose this over installing `react-router-dom` because the public web app has no other routing needs and the admin tool is a single screen, so the dep was unjustified weight. Created `web/src/admin/AdminLayout.tsx` (~140 lines) with the full three-pane shell: header bar (slate-800) carrying **[Highlight Duplicates]** and **[Publish]** stubs (wired up in 9P.11 and 9P.13), an **Oracle: —** placeholder pill (wired up in 9P.10b), and a **[Logout]** button that uses the synchronous XMLHttpRequest 401 trick (sync XHR to `/api/admin/salem/pois` with deliberately wrong `logout:wrongpassword` credentials in `xhr.open()`, then `window.location.reload()` — overwrites the browser's cached Basic Auth credentials so the next admin call re-prompts). Left tree pane (white, 320px wide) and center map pane (slate-200) are placeholders with TODO comments pointing at the steps that fill them in (9P.8 and 9P.9). ASCII layout diagram and inline rationale for every design decision are included in the file header. Auth model: no in-page login form — the first admin API call from the page (the tree fetch in 9P.8) triggers the browser's native Basic Auth dialog because the cache-proxy admin endpoints (Phase 9P.3 middleware) return 401 with `WWW-Authenticate: Basic`. Subsequent requests reuse the browser's cached credentials automatically (same-origin via the vite `/api` proxy). `npx tsc --noEmit` clean. Also: refreshed the stale `project_next_session_priority.md` memory entry that still pointed at Privacy Policy drafting from S94 — now points at Phase 9P.B Step 9P.8 with concrete next-session steps and explicitly demotes NOTE-L014 to secondary.

### Decisions
1. **Path-based dispatch over `react-router-dom`.** The public web app currently has zero routing logic. The admin tool is a single screen. Adding a router dep is unjustified weight. `window.location.pathname.startsWith('/admin')` in `main.tsx` is one line and produces the same UX. Vite's dev server falls back to `index.html` for unknown paths so `/admin` works in `vite dev` and `vite preview`. Production hosting must mirror that fallback if `/admin` is ever exposed beyond dev — documented in the master plan and inline.
2. **No in-page login form.** Browser-native Basic Auth is the right tool when the auth surface is exactly one operator and the credentials live in `cache-proxy/.env`. The browser handles the prompt automatically when an admin endpoint returns 401, caches the credentials for the origin, and includes them on subsequent requests transparently (same-origin via the vite `/api` proxy). The first call to `/api/admin/salem/pois` (which lands in 9P.8) is the trigger.
3. **Logout via XMLHttpRequest 401 trick.** HTTP Basic Auth has no formal logout. The widely-used workaround is to fire a synchronous XHR with deliberately wrong credentials at a URL the server will reject; the browser caches the new (wrong) credentials, replacing the good ones. We point at `/api/admin/salem/pois` with `logout:wrongpassword` in `xhr.open()`, then `window.location.reload()` to force a fresh prompt on the next admin call. Wrapped in try/catch because some browsers throw on deprecated sync XHR. Documented inline as a known footgun, not a blocker.
4. **Oracle pill is a static placeholder for now.** Phase 9P.10b will wire it to poll `/api/oracle/status` (Salem Oracle on port 8088). The pill's `title` attribute documents this so a future session sees the contract without re-reading the master plan.
5. **Light mode only for the admin tool.** No spec for dark mode in 9P.7. Public web app's dark mode toggle is irrelevant here — admin tool is a different consumer and a different audience (operator-only, dev-side).
6. **Tailwind for styling.** Consistent with the rest of the web app. No new CSS deps.
7. **Stale memory must be refreshed at session start, not session end.** The `project_next_session_priority.md` entry was written after S94 and still listed Privacy Policy drafting as the top priority — but S101 reprioritized in STATE.md and the memory was never updated. Session 102 caught the stale state in the start protocol and refreshed it. Lesson: when STATE.md and a memory entry conflict, STATE.md is more recent and authoritative.

### Files Changed
- `web/src/admin/AdminLayout.tsx` — NEW (~140 lines)
- `web/src/main.tsx` — modified (path-based dispatch)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.7 marked DONE with full notes
- `STATE.md` — Last Updated → S102; new Phase 9P.B status section; TOP PRIORITY → Step 9P.8
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-102-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/project_next_session_priority.md` — stale entry refreshed

### Verification
- `cd web && npx tsc --noEmit` → clean exit, zero errors across the project

### What's next
- **Phase 9P.B Step 9P.8** — POI tree (`react-arborist`). `npm install react-arborist`, create `web/src/admin/PoiTree.tsx`, fetch all 1,720 POIs once via `/api/admin/salem/pois`, group by kind → category → subcategory, render in the left pane (replacing the current placeholder), search bar at top filters by name client-side, soft-deleted POIs hidden by default. The first admin API call from this is when the browser's Basic Auth prompt actually fires.
- After 9P.8: 9P.9 (admin map), 9P.10 (edit dialog), 9P.10b (Salem Oracle integration), 9P.11 (Highlight Duplicates wiring), 9P.13 (Publish wiring), Phase 9P.C (publish loop regenerating bundled `.db` from PG).

---

## Session 101: 2026-04-08 — POI Inventory PDF + Phase 9P.A.3 Migration + Phase 9P.6 TypeScript Port + Salem Oracle API Spec (Phase 9P.10b)

### Summary
Four pieces of work in one session, all converging on making the POI Admin Tool actually buildable. **(1) POI inventory PDF** — `tools/generate-poi-inventory-pdf.py` produces a 1,163-page PDF (`docs/poi-inventory-2026-04-08.pdf`, 2.5 MB, gitignored) listing all 1,723 POIs from the bundled `salem-content/salem_content.db` (45 tour + 861 business + 817 narration) with every field of every POI fully written out, organized by category → subcategory. Implementation: Python + reportlab Platypus, 2-column key/value tables, JSON pretty-printing, NULL → '—'. New isolated venv at `tools/.poi-pdf-venv/` (gitignored, fully reversible). **(2) Phase 9P.A.3 — Migrate `tour_pois` and `salem_businesses` to PostgreSQL** — closes the architectural gap that made the admin tool non-functional for two-thirds of POI kinds. Before this commit, `salem_tour_pois` and `salem_businesses` PG tables were empty, so admin PUT/DELETE/move on those kinds returned 404. New `cache-proxy/scripts/import-tour-pois-and-businesses.js` (~270 lines) reads directly from the bundled SQLite via `better-sqlite3` and UPSERTs into PG (UPSERT, not TRUNCATE+INSERT, because six tables FK-reference `salem_tour_pois` for the future Phase 9Q backfill). Type conversions: JSON TEXT → JSONB, INTEGER 0/1 → BOOLEAN, Unix-millis → TIMESTAMPTZ. After migration: tour=45, business=861, narration=814 — 1,720 active POIs all canonical in PG. 9/9 smoke tests pass. The duplicates endpoint jumped from 101 clusters to 550 (top cluster 27 → 54 members) — cross-kind duplicate detection now active. **(3) Phase 9P.6 — TypeScript port of POI category taxonomy** — new `web/src/config/poiCategories.ts` (~535 lines) faithfully mirrors `app-salem/.../ui/menu/PoiCategories.kt` with all 22 categories (17 standard + 5 Salem-specific), all 175+ subtypes, all per-mode visibility flags including `historicTourDefault` from 9P.4a, plus helper functions `freeRoamPrefKey`, `tourPrefKey`, `enabledTagValues`, `findPoiCategory`, and the `PoiLayerId` constant. Cross-reference comments added to BOTH files pointing at each other. The existing `web/src/config/categories.ts` (17 categories, different shape) is left untouched — the two coexist for now, TODO Phase 9C+ unification. `npx tsc --noEmit` clean. **(4) Salem Oracle API integration spec (Phase 9P.10b)** — user surfaced the cross-project capability at session end: the Salem sibling project exposes a dev-side LLM-backed API at `http://localhost:8088` (gemma3:27b on the operator's RTX 3090, backed by 3,891 facts + 4,950 primary source chunks + 63 historical POIs + 202 newspaper articles) that the admin tool can call to compose, revise, summarize, or expand POI descriptions. Read the full Oracle contract at `~/Development/Salem/docs/oracle-api.md` (492 lines, owned by Salem). Added a new Step 9P.10b to the master plan specifying the integration: typed `oracleClient.ts`, "Generate with Salem Oracle" button in the Narration and General tabs of the edit dialog, sub-dialog with prompt + reset + generate, 5-15s spinner, primary source citations surfaced in UI, insert / iterate workflow per the Oracle's 6-turn rolling context, client-side audit log per accepted generation. Critical conceptual point: Salem's 63 historical POIs are DISTINCT from LocationMapApp's 1,720 POIs — same geography, different datasets — so the Oracle is editorial brain only (read from Salem, write to LocationMapApp PG). New memory entry `reference_salem_oracle_api.md` so future sessions don't have to re-read the Salem doc.

### Decisions
1. **PDF source = bundled SQLite, not PG.** The bundled `salem-content/salem_content.db` is the most inclusive single source — narration_points has 817 rows there vs 814 in PG (3-row delta from S98 dedup, the bundled DB is more inclusive). Sourcing from one place is also simpler than mixing PG + bundled.
2. **Skip psycopg2.** Don't need PG access from the PDF script if I source from bundled SQLite. sqlite3 is built-in to Python; only reportlab needs to be installed.
3. **Isolated venv, not system pip install.** Python 3.12 on Ubuntu has PEP 668; venv is the right pattern. Created `tools/.poi-pdf-venv/`, gitignored, fully reversible.
4. **Use bundled SQLite directly via `better-sqlite3` for the migration**, not the SQL file parsing approach S98 used. Cleaner, less error-prone, and `better-sqlite3` was already a cache-proxy dep.
5. **UPSERT, not TRUNCATE+INSERT, for the migration.** Six tables FK-reference `salem_tour_pois` (`salem_historical_figures`, `salem_historical_facts`, `salem_timeline_events`, `salem_primary_sources`, `salem_tour_stops`, `salem_events_calendar`) — even though those references are currently NULL, PG refuses TRUNCATE without CASCADE. UPSERT avoids the issue entirely and is fully idempotent.
6. **Public read endpoints in `lib/salem.js` were already updated in S99** to filter `deleted_at IS NULL` for tour_pois and businesses — so the migration didn't need any further read-side change.
7. **For Phase 9P.6, keep existing `categories.ts` untouched and create a new `poiCategories.ts`.** Two different shapes (`tagMatches` grouped by key vs flat `tags: string[]`), two different consumers (public web app vs Salem admin tool). Don't risk a behavior change in the public web app. TODO Phase 9C+ unification via codegen or shared JSON config.
8. **Salem Oracle API integration is editorial-only.** Salem corpus is read-only from this API; admin tool only writes to LocationMapApp's PG. Explicitly do NOT try to write to Salem from the admin tool; that's against the API contract and Salem's editorial workflow.
9. **The Oracle's `current_poi_id` field accepts any string.** When calling from the LocationMapApp admin tool, pass the LocationMapApp POI id directly (not a Salem catalog id) — the Oracle uses it for context pinning regardless of source.
10. **5-15s latency is unavoidable.** The Oracle runs on a single RTX 3090 with one model loaded; concurrent requests serialize naturally. Don't fight it — show a spinner of at least 5s expected duration, set client timeout ≥60s, and queue follow-ups instead of firing parallel.

### Files Changed
- `tools/generate-poi-inventory-pdf.py` — NEW (~280 lines, Python + reportlab)
- `tools/.poi-pdf-venv/` — NEW (gitignored, isolated venv with reportlab 4.4.10)
- `docs/poi-inventory-2026-04-08.pdf` — NEW (gitignored, 1,163 pages, 2.5 MB)
- `cache-proxy/scripts/import-tour-pois-and-businesses.js` — NEW (~270 lines)
- `web/src/config/poiCategories.ts` — NEW (~535 lines)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/menu/PoiCategories.kt` — added cross-reference comment to the data class doc block
- `.gitignore` — added `tools/.poi-pdf-venv/`, `docs/poi-inventory*.pdf`
- `STATE.md` — TOP PRIORITY rewritten for Phase 9P.B Step 9P.7; added Salem Oracle API integration section
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.A.3, Step 9P.6, Step 9P.10b all added/marked DONE
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-101-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/reference_salem_oracle_api.md` — NEW memory entry (cross-session persistence)
- `~/.claude/.../memory/MEMORY.md` — index updated

### Verification
- **PDF:** `pdfinfo` confirms 1,163 pages, PDF v1.4, title set, valid metadata. `pdftotext` spot-checks at page 1 (cover, "1723 total") and page 100 (mid-doc, "The Peabody Diner" cafe in Peabody) render cleanly.
- **9P.A.3 migration:** PG row counts verified (tour=45, business=861, narration=814 → 1,720 active). Sample rows (`witch_trials_memorial`, `hex`) verified with JSONB arrays parsed cleanly. 9/9 smoke tests against admin endpoints all pass — list, GET single, PUT update (was 404 before), DELETE/restore round-trip, duplicates endpoint surfaces 550 clusters (up from 101).
- **9P.6 TypeScript port:** `cd web && npx tsc --noEmit` → no errors across the entire web project. The new `poiCategories.ts` compiles cleanly with the existing types and doesn't break any existing imports.
- **Oracle API spec:** read the full 492-line Salem doc; Phase 9P.10b spec covers all 6 endpoints used by the admin tool plus the constraints, latency, conceptual gotchas, and operator UI affordances.

### Build & Deploy
- PDF generation: `tools/.poi-pdf-venv/bin/python tools/generate-poi-inventory-pdf.py` (re-run anytime, idempotent)
- Migration: `node cache-proxy/scripts/import-tour-pois-and-businesses.js` (idempotent UPSERT)
- TypeScript verification: `cd web && npx tsc --noEmit`
- Three commits pushed: `8271e6e` (PDF), `7f81b0d` (9P.A.3), `9e25c61` (9P.6)

### Open Items for Session 102
1. **Phase 9P.B Step 9P.7** — `/admin` route in `web/src/App.tsx` + `web/src/admin/AdminLayout.tsx` shell with header (Highlight Duplicates / Publish / Logout / **Oracle status pill**), 2-pane layout
2. **Step 9P.8** — POI tree via `react-arborist` (the user's left-side tree list requirement); needs `npm install react-arborist`
3. **Step 9P.9** — map view with `react-leaflet` (already a dep)
4. **Step 9P.10** — edit dialog with tabbed attribute groups (`react-hook-form`, `@headlessui/react`)
5. **Step 9P.10b — Salem Oracle "Generate with AI" integration** — typed client + UI buttons + sub-dialog (per master plan spec)
6. **Phase 9P.C — publish loop** (regenerate `salem-content/salem_content.db` from PG so APK builds pick up admin edits)
7. **OMEN-002 history rotation** still pending operator action (rotate DB password via `ALTER USER`, regenerate `OPENSKY_CLIENT_SECRET` via portal, set real `ADMIN_PASS` in `cache-proxy/.env`)
8. **Long-deferred carryover:** NOTE-L013 voice debug cleanup, 9T.9 walk simulator verification, NOTE-L014 OMEN-008 Privacy Policy drafting

---

## Session 100: 2026-04-08 — Phase 9P.A COMPLETE (9P.4a Per-Mode Visibility Schema + 9P.5 Duplicates Detection)

### Summary
Two small steps that finish Phase 9P.A (Backend Foundation). **Phase 9P.4a (Per-mode category visibility schema)** added a `historicTourDefault: Boolean = false` field to the `PoiCategory` data class in `app-salem/.../ui/menu/PoiCategories.kt`, plus helper getters `freeRoamPrefKey` and `tourPrefKey` codifying the two-prefKey naming convention (`<existing prefKey>` for free-roam, `<existing prefKey>_tour` for tour mode). Six categories opted in to historic tour mode with inline justification: CIVIC (1692 courthouses), WORSHIP (Salem Village Church central to narrative — deliberate split where free-roam keeps it OFF as clutter but tour mode turns it ON), TOURISM_HISTORY (heart of historic content), WITCH_SHOP (modern witch shops fit the atmosphere), GHOST_TOUR (historic-tour-adjacent), and HISTORIC_HOUSE (witch trial houses). Three categories go the opposite way (free-roam ON, tour OFF) — ENTERTAINMENT, PSYCHIC, HAUNTED_ATTRACTION — because their modern flavor distracts from the 1692 narrative. The actual SharedPreferences migration code is deferred to Phase 9R when historic tour mode ships; 9P.4a only ships the schema. The migration plan is documented in the master plan as a Phase 9R rollout note. `./gradlew :app-salem:compileDebugKotlin` BUILD SUCCESSFUL. **Phase 9P.5 (Duplicates detection endpoint)** added `GET /admin/salem/pois/duplicates?radius=15` to `cache-proxy/lib/admin-pois.js` (~140 new lines). Architecture: bbox-prefiltered self-join across all three POI tables via a `WITH all_pois AS (...UNION ALL...)` CTE; Haversine distance computed in pure SQL (no PostGIS); JS-side cluster grouping via union-find with path compression; transitive clustering (chained POIs collapse into one cluster — correct semantics for duplicate detection). Default radius 15m, max 100m (silently capped above 100), `radius=0` and `radius=banana` rejected with 400. Excludes soft-deleted rows. 7/7 smoke tests pass on the live ~1,696-POI Salem dataset in ~500ms. Notable real-data findings: top 27-member cluster at 15m radius chains POIs around the corner of Essex Street in downtown Salem (Pamplemousse, Tipples and Mash Tours, Witch City Ink, and 24 more — a known dense block where Destination Salem and OSM imports both saw the same shops); a 3-POI cluster at exact identical coordinates (42.5235097, -70.8952337) shows "Bluebikes", "St. Peter's Church", and "Downtown Salem" sharing one location, a clear import default that the admin UI should help the operator fix. **Phase 9P.A — Backend Foundation — COMPLETE.** Phase 9P.B (Admin UI in `web/`) starts next session.

### Decisions
1. **Schema-only for 9P.4a, no migration code yet.** The master plan says "schema". Adding the data shape (the new field + helpers + doc comment) is what 9P.4a delivers; the actual SharedPreferences migration to populate `<prefKey>_tour` keys and the mode-aware reads are Phase 9R deliverables. This keeps 9P.4a small (~30 lines of Kotlin total) and doesn't ship dead code.
2. **`historicTourDefault = true` only on 6 categories, implicit false on the other 16.** Matches the existing project house style where `defaultEnabled = true` only appears on categories that need it. Adding `historicTourDefault = false` to 16 categories would be verbose churn that obscures intent.
3. **WORSHIP gets a deliberate free-roam/tour split.** Free-roam keeps it OFF (clutter — there are many places of worship in any city, not all 1692-relevant). Tour mode turns it ON (Salem Village Church, First Church of Salem, etc. are central to the witch trials narrative). This is exactly the kind of category where the per-mode flag earns its keep.
4. **Bbox prefilter on the duplicates self-join.** Without it, the join is O(N²) ≈ 2.9M Haversine evaluations on the ~1,696-POI dataset. The lat prefilter (`ABS(a.lat - b.lat) <= radius/111000`) is exact since 1° latitude is uniformly ~111 km. The lng prefilter (`ABS(a.lng - b.lng) <= radius/60000`) uses 60000 m/degree as a conservative lower bound that overshoots in Salem (~82000 m/degree at 42.5°N) — overshoots without affecting correctness, just doesn't prune as much. Net result: ~500ms query latency.
5. **Transitive clustering via union-find, not strict pairwise.** When POI A is within 15m of B, B within 15m of C, and C within 15m of D, all four collapse into one cluster of 4 — even if A and D are 60m apart. This is the correct semantics for duplicate detection in the context of this admin tool: chained dense POIs in one tight area are likely the same physical thing fragmented across imports. Strict pairwise would miss the chain and fragment the cluster into pairs.
6. **Members within each cluster sorted by distance from centroid asc; clusters sorted by member_count desc.** Biggest dupes first in the response so the admin UI can lead with the worst offenders. Members within a cluster surface the centroid-closest first so the operator sees the "anchor" POI before the outliers.
7. **One commit shipping Phase 9P.A complete.** User chose "one commit" at session start. 9P.4a + 9P.5 are conceptually distinct but they're the last two steps of a coherent unit (Backend Foundation). Cleaner history to land them together as "Phase 9P.A complete" than to split.

### Files Changed
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/menu/PoiCategories.kt` — added `historicTourDefault` field + helper getters + doc comment block; opted 6 categories into historic tour mode with inline justification comments
- `cache-proxy/lib/admin-pois.js` — added `GET /admin/salem/pois/duplicates` endpoint (~140 new lines)
- `cache-proxy/server.js` — banner updated to advertise the new route
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — 9P.4a + 9P.5 marked DONE with implementation notes; Phase 9P.A marked COMPLETE
- `STATE.md` — TOP PRIORITY rewritten for Session 101 (Phase 9P.B Admin UI in `web/`)
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-100-2026-04-08.md` — live conversation log

### Verification
- **9P.4a:** `./gradlew :app-salem:compileDebugKotlin` → BUILD SUCCESSFUL in 24s. Only an unrelated pre-existing deprecation warning on `setBuiltInZoomControls` in `SalemMainActivity.kt:611`. `grep historicTourDefault` confirms 6 `historicTourDefault = true` lines on the right categories plus the data class default at line 48.
- **9P.5 smoke tests** (7/7 pass): no auth → 401; default radius=15m → 200, 101 clusters with top cluster of 27 members near downtown Essex Street; radius=30m → 84 clusters; radius=100m → 53 clusters; radius=200 silently capped at 100; radius=0 → 400; radius=banana → 400
- **Real-data spot check:** the duplicates endpoint surfaced a real cleanup target — three POIs at exact identical coordinates (Bluebikes / St. Peter's Church / Downtown Salem) that were clearly imported with default coordinates and need operator attention. This is exactly the use case the duplicates endpoint exists for.
- **Performance:** full query against 1,696 POIs returned in ~500ms with the bbox prefilter. Without prefilter would be O(N²) ≈ 2.9M Haversine evaluations.

### Build & Deploy
- Kotlin compile: `./gradlew :app-salem:compileDebugKotlin` (no APK rebuild needed for this session — schema-only Kotlin change)
- Proxy restart: `bin/restart-proxy.sh` (now sources `cache-proxy/.env` from S99 cleanup; admin auth still uses smoke-test password `salem-tour-9P3-test`)
- Banner now shows: `AdminPOI: GET /admin/salem/pois?kind=tour|business|narration, GET /admin/salem/pois/duplicates?radius=, GET/PUT/DELETE /admin/salem/pois/:kind/:id, POST .../move, POST .../restore (Basic Auth)`

### Open Items for Session 101
1. **Phase 9P.B starts** — Phase 9P.A (Backend Foundation) is complete; Admin UI work begins in `web/`
2. **Step 9P.6** (next concrete code task) — hand-port the 22-category POI taxonomy from `PoiCategories.kt` to `web/src/config/poiCategories.ts` with the same shape (including the new `historicTourDefault` field from 9P.4a)
3. **Step 9P.7** — `/admin` route in `web/src/App.tsx`, `web/src/admin/AdminLayout.tsx` shell with header/tree/map panes
4. **OMEN-002 history rotation** still pending operator action (rotate DB password + OpenSky secret via portal)
5. **Real ADMIN_PASS** still needs to be set in `cache-proxy/.env` (currently smoke-test value)
6. **Long-deferred carryover:** NOTE-L013 voice debug cleanup, 9T.9 walk simulator verification, NOTE-L014 OMEN-008 Privacy Policy drafting

---

## Session 99: 2026-04-08 — Phase 9P.3 + 9P.4 DONE (Admin Auth + Admin POI Endpoints) + OMEN-002 File-Side Cleanup

### Summary
Three things in one session, all on a single architectural arc: harden the cache-proxy admin surface, then build the first write endpoints on top of it. **Phase 9P.3** added `cache-proxy/lib/admin-auth.js` — a `requireBasicAuth` middleware with constant-time comparison via `crypto.timingSafeEqual` (length-padded so even length comparison can't leak info), reading `ADMIN_USER`/`ADMIN_PASS` from env at request time, returning `401 + WWW-Authenticate: Basic realm="LocationMapApp Admin"` on failure and `503` if env vars are unset. Mounted globally on `/admin/*` in `server.js` and applied per-route to `POST /cache/clear` to fix the latent S96 unauthenticated-admin-route bug. 7/7 smoke tests pass. **Phase 9P.4** built `cache-proxy/lib/admin-pois.js` (~290 lines): six endpoints under `/admin/salem/pois/*` covering all three POI kinds (`tour`, `business`, `narration`) with kind-specific field whitelists, JSONB-aware update clause builder, soft-delete semantics (PUT and move refuse against deleted rows; DELETE/restore distinguish 404 vs 409), and lat/lng range validation. Required an in-scope schema migration: only `salem_narration_points` had `deleted_at` going in, so added the column + active partial indexes to `salem_tour_pois` and `salem_businesses`, applied the ALTER TABLE live, updated `salem-schema.sql` with `IF NOT EXISTS` clauses for reproducibility, and updated `lib/salem.js` public reads (`/salem/pois`, `/salem/pois/:id`, `/salem/businesses`) to filter `deleted_at IS NULL` so soft delete is functional end-to-end (otherwise it would be theatrical — admin "deletes" a POI but the public app still shows it). 24/24 smoke tests pass. **OMEN-002 file-side cleanup** done as a precondition: lifted `DATABASE_URL`, `OPENSKY_CLIENT_ID`, `OPENSKY_CLIENT_SECRET` out of `bin/restart-proxy.sh` into a new gitignored `cache-proxy/.env`; rewrote the script to source `.env` via `set -a; source ...; set +a`; created tracked `cache-proxy/.env.example` template; added `cache-proxy/.env`, `.env`, `.env.local` to `.gitignore`. Both old credential strings confirmed absent from the tracked file via grep. Credentials are still in git history — full closure requires operator-side rotation flagged for Session 100.

### Decisions
1. **OMEN-002 cleanup before 9P.3, not after.** User chose option (A): clean up the existing leaked credentials before introducing the new `ADMIN_USER`/`ADMIN_PASS` env vars, so the new vars don't perpetuate the bad pattern. File-side migration was straightforward (move secrets, gitignore, script sources `.env`); credential rotation is left for the operator because OpenSky requires a portal login and DB password rotation is a one-line `ALTER USER` the operator should run themselves.
2. **Constant-time comparison with length padding.** `crypto.timingSafeEqual` throws on length mismatch, which would leak length information about the expected credentials. Padded both sides to `max(len)` with zero-fill, then verified equal lengths separately. Standard pattern, but worth doing right.
3. **Read env vars at request time, not module load.** Means the operator can change `ADMIN_USER`/`ADMIN_PASS` in `cache-proxy/.env`, restart, and the new values take effect without any module-cache concerns. Also enables future runtime rotation if needed.
4. **`/cache/stats` left public, only `/cache/clear` gated.** Stats is read-only telemetry; clear is destructive. Different blast radius, different access policy.
5. **Schema migration for `deleted_at` was in-scope, not scope creep.** The 9P.4 master plan calls for soft delete on all three kinds. Only `salem_narration_points` had the column. Adding it to the other two (with `IF NOT EXISTS` for idempotency) is a precondition for the master plan task, not a tangent.
6. **Public read endpoints in `lib/salem.js` had to filter `deleted_at IS NULL`.** Without this, soft delete is theatrical — admin "deletes" something but the user-facing app still shows it. Three-line change, behavior is what users would expect from soft delete. Flagged in the session log as a small scope expansion beyond the literal master plan task.
7. **Whitelisted partial updates, not PUT-replace semantics.** "Full update" in the master plan is interpreted as "update any field you provide, leave others alone". Safer for an admin tool — accidental missing field doesn't clobber existing data. Whitelist enforced per kind so non-existent or system-managed columns (`created_at`, `updated_at`, `deleted_at`) cannot be written via the API.
8. **JSONB-aware update clause builder.** Seven JSONB columns across the three tables (`subcategories`, `tags`, `related_figure_ids`, etc.). Auto-stringify and cast via `$N::jsonb`. Centralized in `buildUpdateClause` so each endpoint stays small.
9. **Soft delete semantics: PUT/move refuse against deleted rows.** Forces a deliberate "restore first" workflow rather than letting an admin update a tombstoned POI by accident. DELETE returns 409 (not 404) when the row exists but is already deleted, distinguishing it from "does not exist".
10. **GET single still returns deleted rows; GET list excludes them by default.** Admin workflows often need to inspect a tombstone before deciding to restore. List view defaults to "active only" but supports `?include_deleted=true` for cleanup workflows.

### Files Changed
- `.gitignore` — added `cache-proxy/.env`, `.env`, `.env.local`
- `bin/restart-proxy.sh` — sources `cache-proxy/.env`, no hardcoded credentials
- `cache-proxy/.env.example` — NEW (placeholder template)
- `cache-proxy/lib/admin-auth.js` — NEW (~140 lines, Basic Auth middleware)
- `cache-proxy/lib/admin.js` — `/cache/clear` gated by `requireBasicAuth`
- `cache-proxy/lib/admin-pois.js` — NEW (~290 lines, six endpoints)
- `cache-proxy/lib/salem.js` — public reads filter `deleted_at IS NULL`
- `cache-proxy/salem-schema.sql` — `deleted_at` added to tour_pois and businesses with active indexes
- `cache-proxy/server.js` — admin auth wired in, `admin-pois` module registered, banner updated
- `STATE.md` — TOP PRIORITY rewritten for Session 100 (9P.4a + 9P.5)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — 9P.3 + 9P.4 marked DONE with bonus notes
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-099-2026-04-08.md` — live conversation log

### Verification
- **9P.3 smoke tests** (7/7 pass): `GET /admin/ping` no auth → 401 + WWW-Authenticate; wrong pass → 401; wrong user → 401; correct creds → 200; `POST /cache/clear` no auth → 401 (was 200 before — latent bug fixed); correct creds → 200; `GET /cache/stats` no auth → 200 (intentionally public)
- **9P.4 smoke tests** (24/24 pass): list w/o kind → 400; list narration limit=3 → 200 with 3 rows; list no auth → 401; GET single → 200; GET nonexistent → 404; GET bad kind → 400; PUT description+priority → 200, updated_at refreshed; PUT lat=999 → 400; PUT empty body → 400; PUT non-whitelisted-only → 400 (whitelist works); POST move → 200 with from/to JSON; POST move bad lng → 400; DELETE → 200; DELETE again → 409; PUT against deleted → 409; GET single still returns deleted row; list include_deleted=false → count=0; list include_deleted=true → count=1; restore → 200; restore again → 409; move back to original coords → 200; PUT priority back → 200; PUT description=null (clean restore) → 200, NULL in DB
- **Test row** (`andrew_safford_house`) verified back to bundled-SQL state via direct PG query: `description=NULL, priority=2, wave=1, lat=42.5230254, lng=-70.8909076`
- **Live PG migration** applied: both new `deleted_at` columns and active indexes confirmed via `information_schema.columns`
- **Both old credential strings** (`fuckers123`, `6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR`) confirmed absent from `bin/restart-proxy.sh` via grep

### Build & Deploy
- Schema migration: `psql -U witchdoctor -d locationmapapp -h localhost -c "ALTER TABLE salem_tour_pois ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ; ..."` (idempotent)
- Proxy startup: `bin/restart-proxy.sh` (now sources `cache-proxy/.env`). New banner lines: `Admin: GET /cache/stats, POST /cache/clear (Basic Auth), GET /admin/ping (Basic Auth)` and `AdminPOI: GET /admin/salem/pois?kind=..., GET/PUT/DELETE /admin/salem/pois/:kind/:id, POST .../move, POST .../restore (Basic Auth)` and `Admin auth: configured`

### Open Items for Session 100
1. **OMEN-002 history rotation** — needs operator action: rotate DB password via `ALTER USER witchdoctor WITH PASSWORD '<new>';`, rotate `OPENSKY_CLIENT_SECRET` via OpenSky web portal, then update `cache-proxy/.env`
2. **Set real `ADMIN_PASS`** in `cache-proxy/.env` (currently the smoke-test value `salem-tour-9P3-test`)
3. **Phase 9P.4a** — Per-mode category visibility schema (small, schema-only)
4. **Phase 9P.5** — Duplicates detection endpoint (one endpoint, ~50 lines)
5. **Carryover** — NOTE-L013 cleanup, 9T.9 walk simulator verification, NOTE-L014 OMEN-008 Privacy Policy drafting (still on the books, still blocking RadioIntelligence Salem ingest)

---

## Session 98: 2026-04-08 — Phase 9P.A.1 + 9P.A.2 DONE (Schema + Importer)

### Summary
First code session of the Phase 9P series. Added the new `salem_narration_points` PostgreSQL table (42 columns, 7 indexes — mirroring the bundled SQL's full column set including merchant_tier and ad_priority for the future advertising business) and wrote a one-shot importer at `cache-proxy/scripts/import-narration-points.js` that brings 814 narration points out of the bundled `salem_content.sql` and into PostgreSQL. PostgreSQL is now the canonical source of truth for editable Salem narration points, exactly per the Session 96 architectural decision. Wave breakdown verified: W1=112, W2=85, W3=109, W4=508 — exact match to the bundled SQL (3 fewer than STATE.md's stale 817; bundled SQL is the truth). Three honest investigation iterations were needed to land cleanly: (1) the schema initially missed 12 columns from the bundled SQL including the merchant fields, fixed by dropping the empty table and re-running; (2) the line-based extractor was breaking on multi-line INSERTs where ~7 rows have literal newlines inside the `hours` field, fixed by accumulating until reaching `;` outside a string literal with proper escape-pair handling; (3) the importer's idempotency was verified by TRUNCATE + re-run. Stopped at the 9P.A.1+9P.A.2 milestone per the conservative recommendation rather than racing through 9P.3-9P.5 in the same session.

### OMEN-002 finding (predates Session 98, flagged for cleanup before 9P.3)
While hunting for the DATABASE_URL, found `bin/restart-proxy.sh:19` contains the PostgreSQL password as a hardcoded credential, and line 22 contains `OPENSKY_CLIENT_SECRET`. Both files are tracked in git. This is an OMEN-002 violation that predates this session. Recommended cleanup: rotate both credentials (since they're in git history), move to a gitignored `cache-proxy/.env`, have the script `source` it. Should be done **before Phase 9P.3** (which adds `ADMIN_USER` / `ADMIN_PASS` to the env model) so the new vars don't perpetuate the bad pattern. Surfaced to user, awaiting decision on rotation timing.

### Decisions
1. **Schema mirrors bundled SQL exactly.** Initial schema was missing 12 columns including `merchant_tier` and `ad_priority` (foundation fields for the future advertising business). Caught the gap during data inspection, dropped the empty table, expanded the schema, re-ran the migration. Net result: PostgreSQL preserves 100% of bundled-SQL data fidelity. 42 columns total. The `merchant_tier`/`ad_priority` fields ship NULL/0 today and become editable in Phase 17 — schema is ready ahead of time.
2. **Importer reads from bundled SQL, not JSON.** The bundled SQL is the most complete source — JSON files have raw POIs without narrations. better-sqlite3 (already a cache-proxy dep) loads extracted INSERTs into in-memory SQLite, Node reads rows, UPSERTs to PostgreSQL. No fragile regex parsing of SQL.
3. **Multi-line INSERT extractor.** ~7 rows have literal newlines in `hours` (Satanic Temple Salem Art Gallery, etc). The extractor now accumulates lines until it sees `;` at end-of-line outside a string literal. Single-quote escape pairs (`''`) are properly skipped.
4. **Stopped at the 9P.A.1+9P.A.2 milestone.** Could have pushed through to 9P.5 but the schema migration + canonical-source establishment is the load-bearing piece of Phase 9P.A. Better to commit + push that cleanly than to race through three more endpoints in the same session.
5. **STATE.md was stale by 3 rows.** STATE.md said 817 narration points; bundled SQL has 814. Bundled SQL is the truth. Updated STATE.md.

### Files Changed
- `cache-proxy/salem-schema.sql` — added `salem_narration_points` table (42 cols, 7 indexes) between `salem_businesses` and `salem_historical_figures`
- `cache-proxy/scripts/import-narration-points.js` — NEW (~290 lines, idempotent UPSERT importer with multi-line SQL parsing)
- STATE.md — updated for 9P.A.1+9P.A.2 done, next priority is 9P.3
- SESSION-LOG.md — this entry
- `docs/session-logs/session-098-2026-04-08.md` — live conversation log

### Verification
- `\d salem_narration_points` shows all 42 columns and 7 indexes
- `SELECT count(*) FROM salem_narration_points` = 814
- Wave breakdown: W1=112, W2=85, W3=109, W4=508 (matches bundled SQL exactly)
- 29 distinct categories, 10 distinct data_source values (preserves multi-source dedup provenance)
- Spot-check: Salem Witch Museum, 1692 Before and After both present with expected attributes
- 675 (83%) have descriptions, 324 (40%) have phones, 393 (48%) have websites
- Importer is idempotent: re-run after TRUNCATE produced identical results

### Build & Deploy
- Schema migration: `psql -U witchdoctor -d locationmapapp -h localhost -f cache-proxy/salem-schema.sql` (idempotent, only added new table)
- Importer: `DATABASE_URL=postgres://... node cache-proxy/scripts/import-narration-points.js` (run twice — first run to surface schema gaps, second run after fix to land cleanly)

### Open Items for Session 99
1. **OMEN-002 cleanup decision** — rotate DATABASE_URL password + OPENSKY_CLIENT_SECRET, move to gitignored `.env`, BEFORE adding ADMIN_USER/ADMIN_PASS in 9P.3. Or accept the existing exposure and only fix the new vars. User decision needed.
2. **Phase 9P.3** — HTTP Basic Auth middleware in `cache-proxy/lib/admin-auth.js`, mount on `/admin/*`, lock down `/cache/clear`
3. **Phase 9P.4** — Admin POI write endpoints (GET/PUT/DELETE/MOVE under `/admin/salem/pois/*`)
4. **Phase 9P.4a** — Per-mode category visibility schema (two prefKeys per category, free-roam vs tour-mode defaults)
5. **Phase 9P.5** — Duplicates detection endpoint
6. **Carryover** — NOTE-L013 cleanup, 9T.9 walk simulator verification, OMEN-008 Privacy Policy drafting (deferred)

---

## Session 97: 2026-04-08 — Phase 9Q + 9R Added; Hybrid Historic Tour Model Committed

### Summary
Planning session, no code. Started with the goal of kicking off Phase 9P.1 (`salem_narration_points` table), but a clarifying question from the user about the multipass narration columns surfaced a much larger architectural picture that needed to be designed before code touched the schema. User clarified: Pass1/2/3 are POI engagement passes (a separate concept), and the historical narrative content for the historic tour mode comes from a separate stream (`~/Development/Salem`). A spawned Explore agent confirmed the survey from Session 96 plus added new findings: Salem domain entities use `building_id` (not POI ID), historical FK columns in PostgreSQL are all currently NULL, and Salem's session 044 added a 202-article daily newspaper corpus with TTS-ready `tts_full_text` and `events_referenced` join fields. User confirmed: the building→POI translation layer must live on the LocationMapApp side (in `salem-content/`), Salem has no knowledge of POIs by design. Drafted a three-phase architecture (9P additions + new 9Q + new 9R), got user approval for the hybrid historic tour model, and inserted everything into the master plan. Phase 9P.1 still queued as the immediate next code action — Session 97 ends without any code, Session 98 picks up there.

### Architectural Insights (Session 97)
1. **`building_id` is Salem's spatial unit, not POI.** Salem JSON entities (NPCs, facts, events, primary sources, newspapers) reference historical 1692 buildings. They have no POI field by design. The translation to LocationMapApp POIs must happen on the LocationMapApp side via a curated `building_id → poi_id` lookup table inside `salem-content/`.
2. **Historical FK columns are still NULL.** `salem_historical_figures.primary_poi_id`, `salem_historical_facts.poi_id`, `salem_timeline_events.poi_id`, `salem_primary_sources.poi_id` — none of them have data. The schema is ready, the bridge has just never been built.
3. **NEW content stream: Salem Oracle newspapers.** 202 daily articles, Nov 1 1691 → May 9 1693, ~13.5 hours of TTS-ready content. Schema at `~/Development/Salem/data/json/newspapers/README.md`. **Each article has `events_referenced` (list of anchor/minor event IDs)** — this is the join key. POI ↔ newspaper join chain: POI → event → newspaper article (transitive 2-hop).
4. **Pass1/2/3 are POI engagement passes, NOT historical curriculum.** Multipass columns in `salem_narration_points` are for user-engagement layering ("first visit gets quick teaser, third visit gets primary sources"). They are unrelated to the historical narrative content stream.

### Decisions
1. **Phase 9P stays focused on POI management** — admin tool, not bloated with bridge work or newspaper ingest. Two small additions only: 9P.4a (per-mode category visibility schema) and 9P.10a (read-only "Linked Historical Content" tab in edit dialog).
2. **NEW Phase 9Q — Salem Domain Content Bridge.** 7 steps. Imports 425 buildings, builds the `salem_building_poi_map` join table (many-to-many, with link types: exact / memorial / representative / nearby), drafts auto-mappings via name + geo proximity (operator manually curates ~50-80 expected mappings out of 425 buildings), imports 202 newspaper articles, backfills historical FKs by graph traversal, adds a Building Map admin panel as a new view in the Phase 9P admin tool.
3. **NEW Phase 9R — Historic Tour Mode (App-Side).** 7 steps. Hybrid tour model: user picks a chapter (5-7 chapters covering the 1692 timeline), each chapter is a curated route through 4-8 POIs in chronological story order, content fires by GPS proximity but in guaranteed-narrative order. Newspaper articles play as long-form TTS readings (~4 min each, one per chapter, not all 202). Per-mode category visibility (free-roam keeps current toggles, historic tour gets restrictive defaults). Ambient narration fully suppressed in tour mode. Pre-rendered audio deferred to v1.1 (use on-device TTS for v1).
4. **Hybrid model selected over spatial-only or temporal-only.** User confirmed. Pure spatial breaks storytelling; pure temporal feels like a forced march; hybrid keeps walking agency while locking narrative coherence at the chapter level.
5. **Phase 9P.1 + 9P.2 are unaffected by all of this.** They migrate `narration_points` to PostgreSQL, which is the prerequisite to everything else. Session 98 starts there.
6. **Total runway for full vision:** ~9-12 sessions across 9P + 9Q + 9R. Salem 400+ launch deadline is September 1, 2026. Plan: ship 9P first (operator gets the tool), assess 9Q+9R against runway after.

### Concerns Surfaced
- **Scope is growing fast.** What started as "build a POI editor" is now POI editor + Salem domain bridge + tour mode app feature. Three coherent phases, but ~9-12 sessions instead of ~4-5.
- **The labor in 9Q.3 (manually seeding 50-80 building↔POI mappings) is real.** Auto-suggest by name + geo proximity helps but doesn't eliminate the need for human review.
- **13.5 hours of newspaper TTS content is too much to play in a single tour.** Curate hard — one article per chapter, not all 202. Future enhancement: "next article" button for users who want more.
- **Per-mode category visibility migration is sensitive** — first-launch users get new tour-mode prefs initialized to defaults; document this in 9R rollout notes.

### Master Plan Updates
- Phase 9P.4a added (per-mode category visibility schema, between 9P.4 and 9P.5)
- Phase 9P.10a added ("Linked Historical Content" tab, between 9P.10 and 9P.11)
- Phase 9P "Out of scope" updated to reference Phase 9Q and 9R
- NEW Phase 9Q section inserted (7 steps + rationale + cross-project dependency note + out-of-scope list)
- NEW Phase 9R section inserted (7 steps + rationale + pre-requisites + out-of-scope list, hybrid model spec)
- TOC updated with both new phases

### State Updates
- STATE.md — TOP PRIORITY rewritten to point at Session 98 / Phase 9P.1 with the three-phase expanded vision documented
- WickedSalemWitchCityTour_MASTER_PLAN.md — Phases 9Q + 9R added, Phase 9P expanded
- SESSION-LOG.md — this entry

### Build & Deploy
None. Planning session.

### Open Items for Session 98
1. **TOP PRIORITY** — Phase 9P.1: Add `salem_narration_points` table to `cache-proxy/salem-schema.sql`, run migration against local PostgreSQL
2. Phase 9P.2: Write `cache-proxy/scripts/import-narration-points.js`, verify row count matches bundled SQL (~814)
3. Carryover (low priority): NOTE-L013 cleanup (delete VoiceAuditionActivity), 9T.9 walk simulator verification, OMEN-008 Privacy Policy drafting (deferred)

---

## Session 96: 2026-04-08 — POI Admin Tool Planning (Phase 9P added to master plan)

### Summary
Planning session, no code. User redirected away from RadioIntelligence Privacy Policy work and toward a web-based POI admin tool as the foundation for the eventual merchant advertising business. Spawned an Explore agent for a thorough survey of the existing POI infrastructure (PostgreSQL `salem_*` tables, cache-proxy endpoints, web app, sync mechanism, audit/admin precedent). Critical data archaeology finding: the ~814 narration points the user wants to manage live **only** in the bundled `salem-content/salem_content.sql`, generated by `tools/salem-data/generate_narration_sql.py` from JSON files — PostgreSQL has no idea they exist. Surfaced four architectural decisions via AskUserQuestion dialog; all four came back as the recommended options. Wrote the Phase 9P section into the master plan with all 14 steps across three sub-phases (A: migration & backend, B: admin UI, C: publish loop). Updated STATE.md to point at Phase 9P.A as top priority. OMEN-008 Privacy Policy drafting deferred (not abandoned).

### Architectural Decisions Locked (AskUserQuestion dialog)
1. **Data foundation:** Migrate `narration_points` to PostgreSQL (`salem_narration_points` table). PostgreSQL becomes single source of truth. JSON files retire.
2. **Auth:** HTTP Basic Auth via `ADMIN_USER` / `ADMIN_PASS` env vars. Browser handles native prompt. Localnet single-admin model.
3. **Categories:** Hand-port `PoiCategories.kt` (520 lines, 22 categories, 153+ subtypes) to TypeScript at `web/src/config/poiCategories.ts`. Cross-reference both files. Unification deferred.
4. **Sync:** No live OTA. Edit → Publish → rebuild `salem_content.sql` from PostgreSQL → next APK ships fresh.

### Master Plan Updates
- New Phase 9P — POI Admin Tool (Developer Infrastructure), 14 steps across 9P.A / 9P.B / 9P.C
- Phase 9P marked PRIORITY in TOC (line 31)
- Phase 9T PRIORITY marker removed (still IN PROGRESS at 9T.9 verification, but displaced)
- Three new web stack dependencies queued: react-arborist, react-hook-form, @headlessui/react

### Out of Scope for v1 (deferred)
- Photo upload (GeoInbox integration)
- Merchant accounts and self-service editing
- Audit log and change-tracking (Phase 17 when payments enter)
- Live OTA sync from PostgreSQL to Android
- Multi-user roles
- Categories table in PostgreSQL (vs. duplicated TypeScript)
- Full duplicate-merge UI

### State Updates
- STATE.md — TOP PRIORITY rewritten to point at Phase 9P.A
- WickedSalemWitchCityTour_MASTER_PLAN.md — Phase 9P inserted, TOC updated, 9T priority marker moved
- SESSION-LOG.md — this entry

### Build & Deploy
None. Planning session.

### Open Items for Session 97
1. **TOP PRIORITY** — Phase 9P.1: Add `salem_narration_points` table to `cache-proxy/salem-schema.sql`, run migration
2. Phase 9P.2: Write narration points importer in `cache-proxy/scripts/import-narration-points.js`, verify row count matches bundled SQL
3. Coordinate with WWWatcher's parallel shell on JSON `SensorMessage` wire format (deferred from S94/S95, low priority since Privacy Policy is deferred)
4. NOTE-L013 cleanup: delete `app/src/main/java/com/example/locationmapapp/debug/VoiceAuditionActivity.kt` (low priority, can fold into a Phase 9P commit)
5. **Deferred (still on books):** OMEN-008 Privacy Policy drafting (NOTE-L014). Can be drafted asynchronously.

---

## Session 95: 2026-04-07 — Stub (no work performed)

Session opened with the standard start protocol but stalled at "Awaiting User Direction." No work performed, no commits, no decisions. Live log preserved at `docs/session-logs/session-095-2026-04-07.md` for completeness. Session 96 picks up the next-session priority from Session 94's plan.

---

## Session 94: 2026-04-07 — RadioIntelligence Collector Role Intake (Research)

### Summary
Research session, no code. Mapped the portfolio active-scanning layer across WWWatcher Phase 20, RadioLogger, and LocationMapApp Salem in response to the new OMEN-008 directive (NOTE-L014). Identified shared analyzer DNA (WWWatcher session 068 ported analyzers from RadioLogger Kotlin → Rust, 89 unit tests), the consent-model asymmetry between operator-owned vs consenting-proxy collectors, and the unified-hash-space load-bearing legal disclosure requirement. Saved deep architectural picture to memory so next session can pick up immediately. Flagged Privacy Policy drafting (OMEN-008 §8) as the top priority for next session — it is the only currently unblocked work on the RadioIntelligence track and gates everything downstream.

### Cross-Project Intake
- WWWatcher Phase 20 — foundation done session 068; live capture sessions 069–075 in progress in parallel shell
- RadioLogger — field-running on operator test fleet; source designation per NOTE-RL002
- RadioIntelligence — pre-code, constituted by OMEN-008 (2026-04-07); LocationMapApp Salem is the constrained third collector
- Wire format coordination: WWWatcher session 073 plans JSON `SensorMessage` over TCP — natural template for Salem's eventual uploader

### Memory Updates
- NEW: `project_radiointelligence_collector_role.md` — full cross-project picture, three collectors, shared analyzer DNA, OMEN-008 constraints, file map
- NEW: `project_next_session_priority.md` — Privacy Policy drafting top priority with three drafting constraints + 10-element required Privacy Policy structure
- MEMORY.md index updated with both new entries at top, next-session priority bolded

### State Updates
- STATE.md — TOP PRIORITY section added at top
- SESSION-LOG.md — this entry

### Build & Deploy
None. Research session.

### Open Items for Next Session
1. **TOP PRIORITY** — Draft layered Privacy Policy text per OMEN-008 §8 (BLOCKING all RadioIntelligence Salem ingest)
2. Coordinate JSON `SensorMessage` wire format with WWWatcher's parallel shell (sessions 069/073)
3. User visual verification of Session 93 marker icon fix on tablet HNY0CY0W
4. 9T.9 walk simulator end-to-end verification (pending since Session 91)
5. NOTE-L013 cleanup: delete `app/src/main/java/com/example/locationmapapp/debug/VoiceAuditionActivity.kt`

---

## Session 93: 2026-04-06 — Crash Recovery + Marker Icon ANR Fix

### Summary
Recovered crashed Session 92 (committed 169 files / 8,618 insertions as `40778c2`). Diagnosed root cause of POI marker icon ANR on the Salem map: `MarkerIconHelper.labeledDot` allocating 817 bitmaps synchronously on main thread during zoom-bucket transition (17→18), compounded by undersized LRU cache. Applied surgical fix preserving labeled-icon visual.

### Changes Made
- `MarkerIconHelper.kt:37` — `MAX_CACHE_SIZE` 500 → 2000 (fits 817 narration + POI dots + system markers)
- `MarkerIconHelper.kt:165` — added `"shop_retail" to "shopping/gift_shop"` (covers 122 businesses falling through to colored-dot fallback)
- `SalemMainActivity.kt:1080` — viewport filter in `refreshNarrationIcons`, only iterates markers in current `boundingBox`
- Memory: new `feedback_poi_icon_architecture.md` (POI icons are per-app, not server-side)
- Memory: updated `feedback_conversation_log.md` (strict real-time append rule, cited Session 92 incident)

### Bugs Found via ANR Trace
- Pulled `/data/anr/anr_2026-04-06-21-52-14-034` from emulator-5570 (Input dispatching timed out 5010ms)
- Stack: `Bitmap.createBitmap (4 nested) ← labeledDot ← narrationIconForZoom ← refreshNarrationIcons ← onZoom ← MapAnimatorListener.onAnimationUpdate`
- 817 cache misses on first zoom into bucket 3 (zoom ≥18) — name-specific cache key + 500-entry cap = thrash forever

### Build & Deploy
- `:app-salem:assembleDebug` BUILD SUCCESSFUL in 22s
- Installed on tablet HNY0CY0W
- Awaiting user visual verification

---

## Session 92: 2026-04-06 — GPS Narration Bugfixes, Category Voices, Witch Circle Icons

### Summary
Crashed mid-session (context overflow). Recovered and committed in Session 93 as `40778c2`. Live log captured first 3 bug fixes; remaining work reconstructed from git diff.

### Changes Made
- **3 GPS narration bug fixes (real-world Salem walk):**
  1. `SalemMainActivity.kt:1217` — 100m GPS dead zone was skipping narration system entirely; added `updateTourLocation(point)` inside dead zone block (map skips jitter, narration system gets every update)
  2. `SalemMainActivity.kt:1204` — 60s GPS interval too slow at walking speed (90m gaps); added 5s tier when narration active
  3. `SalemMainActivityNarration.kt` — TTS queue race condition: `currentNarration` never cleared on Idle → blocked all future playback. Fix: clear on Idle + retry queue after silence delay
- **12-hour per-POI repeat window** — replaced midnight reset (`narratedToday: Set`) with `narratedAt: Map<String, Long>` + `purgeExpired()`
- **Category voice switching** — new `CategoryVoiceMap.kt`, NarrationManager voice cache + per-segment voice override
- **NarrationPoint schema** — added `voice_override` and `audio_asset` columns
- **Witch-themed circle icons** — `MarkerIconHelper.kt` extended with 100+ category→PNG mappings, `loadCircleIcon()` loads/scales/caches PNGs from `assets/poi-circle-icons/`
- **150 generated icons** across 22 categories via `tools/generate-poi-circle-icons.py` (Stable Diffusion via Forge, DreamShaper XL Turbo)
- **Reach-out tuning** — real GPS reduced from 500m to 150m, distance-first with 50m banding
- **Downtown test route** — `TourRouteLoader.kt` prefers comprehensive street coverage route
- **VoiceTestActivity** — new debug activity for voice audition
- **Extensive NARR-GEO debug logging** throughout pipeline

### Commit
- `40778c2 Session 92: GPS narration bugfixes, category voices, witch circle icons` — 169 files, 8,618 insertions

---

## Session 91: 2026-04-06 — Walk Sim Fixes, Narration Density, 100% Overpass Coverage

### Summary
Fixed 3 walk simulator bugs (interaction, interpolation, session reset), added walk sim narration mode with 3x expanded geofence entry radius and proximity-first reach-out, audited Overpass POI coverage and added 3 missing narration points for 100% coverage (817 total).

### Changes Made
- Walk sim stops on tap/long-press (was overwriting user position every second)
- Route interpolation residual distance bug fixed (short segments not consuming carry-over)
- Narration session resets on walk sim start (was skipping previously-narrated POIs)
- Walk sim mode in NarrationGeofenceManager: 3x entry radius, 200m distance-first reach-out, 500ms delays
- 3 new narration points (2x Salem Fire Dept, 1x Walgreens) — 817 total, 100% Overpass match
- Reverted VoiceAuditionActivity manifest entry (NOTE-L013 cleanup)

---

## Session 90: 2026-04-06 — Dual Narration Tiers (Free/Paid)
- Ultra-short teaser narrations for all 814 POIs (avg 12 words, ~10 sec TTS) — free tier ambient audio
- Promoted 702 existing short_narrations to long_narration — preserved as paid tier content
- Smart generator: description mining, cuisine inference, name parsing, web junk filtering, 8-25 word enforcement
- 100% coverage: all 814 POIs have both short (teaser) and long (full) narrations
- Deployed to Lenovo TB305FU tablet

## Session 89: 2026-04-06 — Universal POI Narrations + Silence Reach-Out
- Wave 4: 508 new narration_points for all remaining businesses (814 total, 100% coverage)
- Smart narration templates by category (cuisine-aware restaurants, atmospheric historic sites, etc.)
- Silence reach-out: 5s quiet → auto-narrates nearest valuable POI within 500m
- Priority narration queue: merchants first (adPriority), then historical value (priority), then distance
- Database rebuilt: 2.7MB, 814 narration_points all with short_narration
- Deployed to Lenovo TB305FU tablet

## Session: 2026-04-06 — Session 87: Tour 1 rename + downtown street route
- Renamed Tour 1: "Salem Essentials" → "Walking Through Salem" (SalemTours.kt, tour_essentials.json, SQL, DB)
- Walk simulator: replaced tour-stop-hopping with 352-point OSRM street-level route through downtown Salem
- New asset: downtown_salem_route.json (PEM → Essex → Common → Liberty → Charter → Derby → back, 5.9km)
- TourRouteLoader: added loadDowntownRoute() + loadStopCoordinates() fallback
- Deployed to Lenovo tablet, pending verification next session

## Session: 2026-04-05 — Session 85: Salem Walking Tour Restructure (planning)

### Work Performed
- Planning session: total restructure of the tour system from linear stop-to-stop to ambient content layer
- Analyzed current assets (45 POIs, 49 figures, 500 facts, geofence/narration systems)
- Delivered architectural recommendations: narration point taxonomy, content queue priority, dialog UI, walking loops, street corridor geofences, GPS mitigation, user fatigue controls
- User approved approach
- Created Phase 9T in master plan (9 steps, 5-8 session estimate) — inserted before Phase 9B as highest priority
- Updated STATE.md with Phase 9T summary

### Next Session
Begin Phase 9T Step 1: geographic boundary definition + POI audit

---

## Session: 2026-04-05 — Session 84: POI defaults, WitchKitty splash, map magnify, OSRM routes, walk simulator

### Work Performed
- POI layer defaults: only Tourism/History, Civic, Entertainment ON by default; added bbox display filtering
- Splash screen: WitchKitty.png image + Creepster horror font "Wicked Salem Witch Tours"
- Map magnify button (x1-x5): visual zoom 1.0-3.0x without changing map zoom level
- Pre-computed OSRM walking routes for witch trials tour: 19 segments + loop-back, 471 points, 8.6km
- Tour route rendering: street-following polylines replace straight lines
- GPS walk simulator: in-app Walk button + /walk-tour debug endpoint, 1.4 m/s walking pace
- Continuous GPS follow: map tracks user position on every update (bypasses 100m dead zone in manual mode)
- Show All POIs debug button: temporarily bypasses layer filter for testing
- Bottom-left button stack: POI toggle, Walk toggle, Magnify toggle

### User Note
Next session: wants to redo the tour in a different way.

## Session: 2026-04-05 ��� Session 82: Tour Hardening Plan — Phase 9A+ Added to MASTER_PLAN

### Work Performed
- Planning session — no code changes
- Deep research: tour engine architecture, offline readiness audit, tablet capabilities, Frida GPS simulation
- Verified Lenovo TB305FU tablet connection (Android 15, Magisk root, Frida 17.9.1, Google TTS)
- Added Phase 9A+ (Tour Hardening & Offline Foundation) to MASTER_PLAN — highest priority, 9 steps
- Key gaps identified: no offline tiles (blank map), no offline routes (OSRM dependency), no offline general POIs
- Decision: Phase 9B deferred. Offline-first + Frida walk simulator on tablet = proven tour foundation

### Files Changed
- WickedSalemWitchCityTour_MASTER_PLAN.md (Phase 9A+ inserted between 9A and 9B)
- STATE.md (updated direction, test device, offline requirements)
- docs/session-logs/session-082-2026-04-05.md (created)

---

## Session: 2026-04-05 — Session 81: Tour Startup UX + Satellite Upgrade + Tour Files

### Work Performed
- Welcome dialog after splash: "Explore Salem" / "Take a Tour" choices
- Enhanced tour selection: themed sections, POI category toggles, async loading spinner
- Long-press teleport feeds tour engine (geofences trigger on teleport)
- Fixed missing tour data in bundled Room DB (3 tours, 58 stops)
- Satellite tiles: USGS zoom 16 → Esri World Imagery zoom 19
- Created 3 self-contained tour JSON files (assets/tours/)
- Full emulator test: welcome → tour select → start → geofence → TTS

### Files Changed
- SalemMainActivity.kt, SalemMainActivityTour.kt, TileSourceManager.kt, salem_content.db
- NEW: assets/tours/tour_essentials.json, tour_explorer.json, tour_grand.json

---

## Session: 2026-04-05 — Session 80: Phase 9A Emulator Test (PASSES)

### Context
Phase 9A code complete and build-verified from Session 79. This session focused on emulator testing.

### Work Performed
- Installed app-salem on Salem_Tour_API34 emulator (port 5570)
- Tested full Phase 9A splash flow: dark purple background + WitchKitty Lottie animation → crossfade to SalemMainActivity → cinematic zoom (US → Salem street level) → USGS satellite tiles + POI labels
- All Phase 9A features pass emulator test
- Identified Forge (AI Art Studio, port 7860) is down — deferred

### Decisions Made
1. Phase 9A passes emulator test — more testing + commit next session
2. First cold launch on emulator showed white splash background (class verification delay) — emulator-only, not a real-device concern

### Open Items
- Phase 9A: Commit pending (user wants more testing next session)
- Tile source picker: not UI-tested via tap this session
- Forge restart needed

---

## Session: 2026-04-05 — Session 79: Recovery & Live Conversation Log (OMEN-006)

### Context
Previous session 79 was killed by context overflow during Phase 9A implementation. All in-flight context lost. This session recovered state from git diffs and implemented a crash-recovery mechanism.

### Work Performed

**Recovery**
- Reconstructed Phase 9A state from uncommitted changes on disk
- Confirmed all code survived: SplashActivity, TileSourceManager, cinematic zoom, tile source picker, layouts, themes, Lottie dep
- Build verified: `assembleDebug` passes cleanly

**OMEN-006 — Live Conversation Log (NEW GLOBAL DIRECTIVE)**
- Implemented live append-only conversation log system at `docs/session-logs/`
- Updated CLAUDE.md: Session Start step 4 (create log), Session End step 4 (finalize log), new "Live Conversation Log" section
- Issued OMEN-006 global directive to `~/Development/OMEN/directives/ACTIVE.md` — applies to ALL projects, never expires
- Pushed action items to all 7 project OMEN notes files (Salem, Vampires, Qabalah, TheWitchesOfSalem, KeyPadZoom, GeoInbox, LocationMapApp)
- LocationMapApp marked as reference implementation (COMPLETE)
- Created memory entry: `feedback_conversation_log.md`

### Decisions Made
1. Live conversation logs are mandatory for all projects (OMEN-006)
2. Logs are append-only, timestamped, written to disk incrementally
3. Phase 9A confirmed code-complete — needs emulator test and commit

### Files Created
- `docs/session-logs/session-079-2026-04-05.md` — First live conversation log
- `~/Development/OMEN/directives/ACTIVE.md` — OMEN-006 added

### Files Modified
- `CLAUDE.md` — Live log protocol added to session start/end, architecture, reference table
- `SESSION-LOG.md` — This entry
- `~/Development/OMEN/notes/*.md` — Action items for OMEN-006 added to all 7 projects

### Open Items
- Phase 9A: Emulator test + commit (code complete, build verified)
- COPPA deadline: 17 days remaining (April 22)
- Credential audit (OMEN-002): Outstanding
- Testing (OMEN-004): Outstanding

### OMEN Compliance
- OMEN-006 (Live Conversation Log): **COMPLETE** — reference implementation
- NOTE-L012: Marked complete
- All other notes: Unchanged from Session 78

### Live Log
Full conversation log: `docs/session-logs/session-079-2026-04-05.md`

---

## Session: 2026-04-04 — Session 78: UX Transformation Plan (Phases 9A-9D)

### Context
Post Session 77. User directed a strategic pivot: recenter the app as a commercial Salem tour guide product before continuing with Phase 10 production readiness. Focus on branded launch experience, satellite imagery, feature tier gating, user settings, and contextual alerts.

### Work Performed

**UX Transformation Plan Created**
- Designed and documented 4 new phases (9A-9D) inserted between Phase 9 and Phase 10 in the master plan
- Phase 9A: Splash Screen & Satellite Map Entry (Lottie WitchKitty animation, USGS aerial tiles, cinematic zoom-in, tile source picker)
- Phase 9B: Feature Tier Matrix & Gating Infrastructure (FREE/EXPLORER/PREMIUM/LLM tiers, FeatureGate singleton, stub billing, onboarding dialog)
- Phase 9C: User Settings & Alert Preferences (SettingsActivity with PreferenceFragmentCompat, alert toggles, narration prefs, quiet hours)
- Phase 9D: Contextual Alert System (ContextualAlertManager replacing ambient mode, historical facts, figure connections, business promos, frequency control)
- 20 new files identified across all 4 phases
- Detailed implementation plan saved to `.claude/plans/distributed-coalescing-pearl.md`

**Tile Source Research**
- Evaluated 7 satellite tile providers for commercial use with osmdroid
- Selected USGS National Map Imagery: free, public domain, no API key, unlimited, 15cm resolution over Salem MA
- Fallback identified: Mapbox Satellite (750K free tiles/month)
- Rejected: ESRI (ToS violation for commercial), Bing (deprecated), MapTiler/Stadia (too expensive)

**Master Plan Updated**
- Inserted Phases 9A-9D into Table of Contents and body of WickedSalemWitchCityTour_MASTER_PLAN.md
- Renumbered ToC entries (9A-9D at items 12-15, Phase 10 now item 16)
- Phase numbering uses letter suffixes (9A, 9B, 9C, 9D) to avoid renumbering all subsequent phases

### Decisions Made
1. **Lottie** chosen for splash animation (over AnimatedVectorDrawable) — richer animation for WitchKitty
2. **FREE tier is minimal** — map + basic POIs + 1 tour preview + ads only. Transit, weather, events, full tours all require EXPLORER ($4.99). Strong upgrade incentive.
3. **USGS National Map** for satellite tiles — only option that is free, public domain, and commercially licensed
4. **Phases 9A-9D prioritized before Phase 10** — UX transformation must happen before production readiness work
5. **Phase dependency chain**: 9A and 9B are independent → 9C depends on both → 9D depends on 9B+9C

### Files Modified
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Inserted Phases 9A-9D (ToC + body)
- `STATE.md` — Updated current direction, added Session 78 decisions
- `SESSION-LOG.md` — This entry
- `.claude/plans/distributed-coalescing-pearl.md` — Detailed implementation plan (new)

### Open Items
- WitchKitty Lottie JSON file needs to be created/exported from AI Art Studio assets
- Tours/Events still not loading on fresh install (bundled Room DB — Phase 10 work)
- COPPA deadline April 22 (18 days) — not addressed this session
- Credential audit (OMEN-002) — not addressed this session
- Room migration needed for `min_tier` columns on Tour and TourPoi (Phase 9B)

### OMEN Compliance
- NOTE-L001 (CLAUDE.md): Previously completed
- NOTE-L002/OMEN-002 (Credentials): Not addressed
- NOTE-L003 (COPPA): Not addressed — 18 days remaining
- NOTE-L005 (Phase 10 prereqs): Acknowledged — UX transformation now prioritized before Phase 10
- NOTE-L008 (Pricing tiers): **ADDRESSED** — Feature tier matrix defined with exact feature-to-tier mapping
- NOTE-L009 (Self-contained design): **PARTIALLY ADDRESSED** — FREE tier defined as offline-capable (map + POIs + bundled data)
- OMEN-004 (Testing): Not addressed

---

## Session: 2026-04-04 — Session 77: Emulator Testing, GeoInbox Init, AI Art Studio & Splash Screen Prototyping

### Context
Post Session 76 (master plan overhaul). Session focused on app verification, infrastructure setup, and branding exploration.

### Work Performed

**GeoInbox Infrastructure**
- Initialized git repo at `~/Development/GeoInbox/` (NOTE-G001)
- Created private GitHub repo: `deanmauriceellis-cloud/GeoInbox`
- Initial commit pushed — scaffold, gmail_auth.py, gitignore, env template
- Gmail API already authenticated with `omenpicturefeed@gmail.com`

**Emulator Testing (Salem_Tour_API34)**
- Launched app on emulator, identified proxy IP mismatch (10.0.0.4 → 10.0.0.229)
- Updated proxy IP in 16 Kotlin source files + 2 web config files
- Created `locationmapapp` PostgreSQL database, applied schemas, set password auth
- Verified features: Weather (working), Grid menu (working), Dark mode (working), Find/search (UI working, empty DB), About (working), Long-press GPS override (working), Alerts menu (working), POI markers from Overpass (working)
- Issues found: Tours "No tours available", Events "No events loaded" — bundled Room DB not populated on fresh install; Chat table missing

**AI Art Studio Installation**
- Created install script: `~/Development/scripts/install-ai-art-studio.sh`
- Installed Stable Diffusion WebUI Forge + ComfyUI at `~/AI-Studio/`
- Downloaded models: DreamShaper XL Turbo, DreamShaper 8 (SD1.5), AnimateDiff v3, ControlNet, IP-Adapter, upscalers
- Resolved multiple setup issues: pgAdmin GPG key, libgl1-mesa-glx rename, setuptools/pkg_resources, numpy/scikit-image compat

**Splash Screen Prototyping**
- Generated "WitchKitty" — black cat witch with cauldron, green glow (3 variations)
- Explored AnimateDiff animation — found img2img approach produces minimal motion
- Built anchor-based SLERP morph pipeline for image transitions
- Created American Gothic → Wizard/Witch transformation (10-stage directed narrative)
- Prototyped epic transformation: storm → fear → lightning → demonic → red eyes → transformation → green wisps → pentagram
- Best approach identified: generate independent anchor keyframes, SLERP interpolate in latent space

**Key UI Decision**
- LocationMapApp map view becomes a **utility** (used when maps needed)
- Tour guide view becomes the **primary screen**

### Files Modified
- `core/src/.../repository/*.kt` (12 files) — proxy IP 10.0.0.4 → 10.0.0.229
- `app-salem/src/.../TcpLogStreamer.kt`, `SalemMainActivityFind.kt` — proxy IP update
- `app/src/.../TcpLogStreamer.kt`, `MainActivityFind.kt` — proxy IP update
- `web/.env.development`, `web/vite.config.ts` — proxy IP update
- `STATE.md` — updated with session 77 changes
- `SESSION-LOG.md` — this entry

### Files Created
- `~/Development/scripts/install-ai-art-studio.sh` — AI art studio installer
- `~/AI-Studio/` — full Stable Diffusion installation (Forge + ComfyUI)
- `~/AI-Studio/PROMPTS.md` — tuned prompts for WickedSalemWitchCityTour art
- `~/Development/GeoInbox/.git/` — initialized repository

### Decisions Made
- Tour guide view is the primary screen, not the map
- Local Stable Diffusion (RTX 3090) for all art generation — no subscription costs
- Anchor-based SLERP is the best morph technique (vs chaining, AnimateDiff, or independent frames)
- DreamShaper 8 (SD1.5) works better for animation than SDXL at 512x640 resolution

### Open Items
- Splash screen needs significant refinement
- Tours/Events not loading — bundled Room DB issue (Phase 10 work)
- COPPA deadline April 22 (18 days) — not addressed this session
- Credential audit (OMEN-002) — not addressed this session
- PostgreSQL chat_rooms table missing — non-critical

### OMEN Compliance
- NOTE-G001 (GeoInbox git init): **COMPLETED**
- NOTE-L001 (CLAUDE.md): Previously completed
- NOTE-L002/OMEN-002 (Credentials): Not addressed — proxy IP is not a credential
- NOTE-L003 (COPPA): Not addressed — 18 days remaining
- NOTE-L010 (GeoInbox): Acknowledged, repo initialized, pipeline scaffold exists

---

## Session: 2026-04-04 — Session 76: Full Re-Evaluation & Master Plan Overhaul

### Context
Post Phases 6-9 completion. User requested a total honest re-evaluation of the app from business, marketing, competitive, and feature perspectives. Goal: make the app profitable.

### Work Performed
- **Comprehensive app evaluation**: identified critical gaps (no photos, no tests, no crash reporting, no iOS, TTS quality, pricing complexity, no web presence, no social media)
- **6 parallel research agents** deployed covering:
  1. Offline map tile caching (osmdroid CacheManager, MOBAC, SQLite archives)
  2. Firebase Crashlytics + Analytics (setup, events, GDPR, alternatives)
  3. Salem tourism partnerships (Destination Salem, NPS, Chamber, Salem 400+, local government)
  4. Social media & marketing strategy (Instagram, TikTok, ASO, paid ads, influencer strategy)
  5. Photo sourcing (Wikimedia Commons per-POI audit, NPS/LOC public domain, field photography guide)
  6. iOS/cross-platform (PWA recommended first, then KMP for native iOS)
- **Master plan restructured** from 10+3 phases to 16+4 phases:
  - Phases 10-11: Production readiness + Play Store launch (CODE)
  - Phases 12-14: Social media + fieldwork + community engagement (NO CODE)
  - Phases 15-16: Virality/gamification + iOS/PWA (CODE)
  - Phases 17-20: Merchant network, custom narration, LLM, revenue features (POST-LAUNCH)
- **New sections added**: Competitive Landscape, Social Media Content Calendar, Community Engagement Contacts, Fieldwork Planning Guide
- **Critical discovery**: 2026 is Salem's quadricentennial (Salem 400+) — once-in-a-generation marketing opportunity
- **Key competitors identified**: Action Tour Guide (direct), Salem On Your Own (direct), VoiceMap, GPSmyCity
- **Old Phase 10 + Future Phases archived** to MASTER_PLAN_ARCHIVE_Phase10_FuturePhases.md

### Files Modified
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Major restructure: new ToC, expanded Phase 10, new Phases 11-16, Competitive Landscape, Social Media Calendar, Community Contacts, Fieldwork Guide, renumbered Future Phases 17-20
- `SESSION-LOG.md` — This entry

### Files Created
- `MASTER_PLAN_ARCHIVE_Phase10_FuturePhases.md` — Archive of pre-Session 76 Phase 10 and Future Phases content

### Decisions Made
- Keep Android TTS for v1.0; plan transition to pre-recorded audio in Phase 18
- Keep price tiers as-is ($0/$4.99/$9.99/$49.99-mo); adjust based on market data later
- Social media starts IMMEDIATELY (no code dependency)
- PWA is the fastest path to iOS users (2-3 weeks, leverage existing React web app)
- Firebase Crashlytics + Analytics for production monitoring (free, lowest friction)
- MOBAC for offline tile generation, bundle zoom 12-15 in APK (~60MB)
- Coil 2.7.0 for image loading; WebP format for photos
- Salem Chamber of Commerce membership ($360/yr) is the #1 business development priority
- App must be on Play Store by September 1, 2026 (before October peak)

### Open Items
- Phase 10 work begins next session (emulator verification → Firebase → offline tiles → photos)
- Social media account registration can happen today (no code needed)
- Salem Chamber membership application can happen today
- Field trip to Salem planned for late September 2026

---

## Session: 2026-04-04 — Session 75: Phases 6-9 Complete (Tour Engine, Geofence, Narration, Directions, Events)

### Context
Continuing from Phase 5 completion. Built Phases 6-9 in a single session — all four phases code-complete, all three modules build clean.

### Phase 6 — Tour Engine (Steps 6.1-6.8)

#### Files Created (5)
- `app-salem/.../tour/TourModels.kt` — TourTheme enum, TourProgress, ActiveTour, TourState sealed class, TourSummary
- `app-salem/.../tour/TourEngine.kt` — Full tour lifecycle: start/advance/skip/reorder/add/remove/pause/resume/end, SharedPreferences persistence, custom tour builder, time-budget tours, nearest-neighbor TSP route optimization, haversine distance
- `app-salem/.../ui/TourViewModel.kt` — Bridges TourEngine to UI, exposes tour list/state/actions
- `app-salem/.../ui/SalemMainActivityTour.kt` — Tour selection dialog, active tour dialog, stop detail dialog, route polyline + numbered markers, tour HUD bar, completion dialog, custom tour builder, time-budget dialog
- `app-salem/res/drawable/ic_tour.xml` — Walking tour icon

#### Files Modified (7)
- `core/.../ui/menu/MenuEventListener.kt` — Added onTourRequested()
- `app/.../ui/MainActivity.kt` — No-op onTourRequested() override
- `app-salem/.../ui/SalemMainActivity.kt` — TourViewModel, observeTourState(), GPS location feed
- `app-salem/.../ui/menu/AppBarMenuManager.kt` — Tours button in grid row 4
- `app-salem/.../ui/StatusLineManager.kt` — TOUR priority level (4)
- `app-salem/.../ui/MarkerIconHelper.kt` — createNumberedCircle() for stop markers
- `app-salem/res/layout/grid_dropdown_panel.xml` — Row 4

### Phase 7 — GPS Geofence Triggers & Narration (Steps 7.1-7.6)

#### Files Created (2)
- `app-salem/.../tour/TourGeofenceManager.kt` — Lightweight haversine proximity engine: APPROACH/ENTRY/EXIT events, 60s cooldown, SharedFlow
- `app-salem/.../tour/NarrationManager.kt` — Android TTS wrapper: queue management, speed control (0.75-1.25x), ringer mode check, segment types (short/long/quote/transition/hint)

#### Files Modified (4)
- `tour/TourEngine.kt` — Integrated geofence+narration managers, ambient mode, auto-triggers on GPS
- `ui/TourViewModel.kt` — Exposed geofence events, narration controls, ambient mode toggle
- `ui/SalemMainActivityTour.kt` — Geofence event observer, narration controls bar
- `ui/SalemMainActivity.kt` — Wired geofence+narration observers

### Phase 8 — Walking Directions (Steps 8.1-8.6)

#### Files Created (2)
- `app-salem/.../tour/WalkingDirections.kt` — OSRM routing via OSMBonusPack OSRMRoadManager, route caching, multi-stop support
- `app-salem/.../ui/SalemMainActivityDirections.kt` — Route display (bordered gold polyline), directions info bar, turn-by-turn dialog, walkTo() helper

#### Files Modified (4)
- `tour/TourEngine.kt` — Exposed lastLocation
- `ui/TourViewModel.kt` — Walking directions state, getDirectionsTo/getFullTourRoute/clearDirections
- `ui/SalemMainActivityTour.kt` — "Walk Here" + "Narrate" buttons in stop detail
- `ui/SalemMainActivity.kt` — Wired observeWalkingRoute()

### Phase 9 — Haunted Happenings & Events (Steps 9.1-9.4)

#### Files Created (4)
- `salem-content/.../data/SalemEvents.kt` — 20 curated events (Haunted Happenings, festivals, museums, ghost tours)
- `app-salem/.../ui/EventsViewModel.kt` — Events state, today/upcoming/monthly, "on this day in 1692", October detection
- `app-salem/.../ui/SalemMainActivityEvents.kt` — Events dialog, event cards, category chips, "on this date" section
- `app-salem/res/drawable/ic_events.xml` — Calendar icon

#### Files Modified (6)
- `content/dao/TimelineEventDao.kt` — findByMonthDay()
- `content/dao/EventsCalendarDao.kt` — findByType()
- `content/SalemContentRepository.kt` — getEventsByType(), getAllEvents(), getTimelineByMonthDay()
- `salem-content/pipeline/ContentPipeline.kt` — Wired SalemEvents + SQL writer for events_calendar
- `core/MenuEventListener.kt` — onEventsRequested()
- `app-salem/menu/AppBarMenuManager.kt` — Events button in grid row 4

### Decisions Made
- Tour geofence uses lightweight haversine distance (not polygon-based GeofenceEngine) — tour stops are simple circles
- Overpass POI auto-populate gated to $19.99+ tier (not free/$4.99/$9.99)
- MediaStyle notification for narration deferred to Phase 10
- Halloween map overlays and daily historical notification deferred to Phase 10

### Open Items
- Emulator verification for all four phases
- Re-run content pipeline to regenerate salem_content.db with 20 calendar events
- Phase 10 (Polish, Branding & Play Store) is next

---

## Session: 2026-04-03f — Phase 5 Complete + Tour Data + Business Model + Full Audit

### Context
Session 74. Major session — completed Phase 5 POI gaps, created 3 circular walking tour definitions with 60 stops and TTS narrations, ran end-to-end architecture audit, captured tiered business model and monetization strategy, updated master plan with audit recommendations and future phases.

### Work Performed

**1. Tour Data (SalemTours.kt — NEW)**
Created 3 circular walking tours as loop routes that start/end where the user is:
- **Salem Essentials** (14 stops, ~90 min, 2.2 km, easy) — compact downtown must-sees
- **Salem Explorer** (20 stops, ~2 hr 15 min, 3.3 km, moderate) — extended with dining/shopping areas
- **Grand Salem Tour** (26 stops, ~3.5 hr, 5.2 km, challenging) — Derby St → Proctor's Ledge → back
- Every stop has: transition narration (TTS-optimized), walking time, distance from prev
- All tours minimize backtracking, different directional routes for variety

**2. Phase 5 POI Gaps Completed (8 new POIs, 29→37 total)**
- Witch Trials: Judge Hathorne's Home, Sheriff Corwin's Home
- Maritime: Derby Wharf Light Station, Narbonne House
- Literary: Castle Dismal (Hawthorne boyhood), 14 Mall St (Scarlet Letter house)
- Landmarks: McIntire Historic District
- Visitor Services: South Harbor Garage
- Salem Wax Museum verified closed — removed from plan

**3. Content Pipeline Integration**
- Wired SalemTours into ContentPipeline (replaced emptyList() with tour data)
- Added SQL generation for tours + tour_stops tables in writeSql()
- Enhanced ContentValidator with tour-specific checks (sequential stops, count matching, distance validation)
- Pipeline output: 37 POIs + 23 businesses + 3 tours + 60 stops, 0 errors, 0 warnings

**4. Full Architecture Audit**
Three-agent parallel audit covering app-salem, content pipeline, and core/architecture:
- **Critical**: Missing DB indexes (all entities), fallbackToDestructiveMigration, cleartext traffic, minifyEnabled false, LIKE '%query%' search
- **Major**: No @Delete/@Update in DAOs, no FK constraints, no @Transaction, no app-side sync
- **Architecture**: Solid 9/10. Core is production-ready for tours. Server/API aligned. Schema match perfect.
- **Data quality**: Excellent. Historical accuracy verified. Coordinates verified. Tour routes geographically sound.

**5. Business Model & Monetization (NEW in Master Plan)**
Captured tiered pricing strategy:
- Free ($0): limited tours, Google Ads, no transit/weather
- Explorer ($4.99): moderate tours, North Shore POIs
- Premium ($9.99): full content, transit, all tours
- Salem Village LLM ($49.99/mo): AI conversations with historical figures
Revenue: ads, geofenced merchant advertising, loyalty program, business partnerships

**6. Master Plan Major Update (1,122→1,315 lines)**
New sections added:
- Business Model & Monetization (pricing tiers, revenue streams, feature gating)
- Technical Foundation — Audit Recommendations (7 priorities: indexes, FKs, JSON packages, FTS5, API keys, Socket.IO→OkHttp, network security)
- Future Phases: Phase 11 (Merchant Network), Phase 12 (Salem LLM), Phase 13 (Additional Revenue)

### Files Created (2)
- `salem-content/src/main/kotlin/.../data/SalemTours.kt` — 3 tour definitions + 60 stops
- `/home/witchdoctor/.claude/plans/glowing-gliding-starlight.md` — Phase 5 implementation plan

### Files Modified (5)
- `salem-content/src/main/kotlin/.../data/SalemPois.kt` — 8 new POIs (29→37)
- `salem-content/src/main/kotlin/.../pipeline/ContentPipeline.kt` — Tours wired in, SQL generation for tours/stops
- `salem-content/src/main/kotlin/.../pipeline/ContentValidator.kt` — Tour-specific validation
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Business model, audit recs, future phases, Phase 5 checkboxes
- `STATE.md` — Updated to Session 74

### Key Decisions
1. **JSON content packages** as source of truth, Room DB as cache. Startup checks for updates.
2. **Core must remain backward compatible** — Salem features in :app-salem only, never in :core
3. **FTS5 replaces LIKE search** — no table scans
4. **OkHttp WebSocket replaces Socket.IO** — already in dependency tree, eliminates outdated dep
5. **Tiered feature gating at app level** — core is tier-agnostic
6. **North Shore merchant expansion** — 500+ businesses needed for Explorer tier
7. **Geofenced merchant advertising** — proximity-triggered cards for paying businesses

### Open Items
- [ ] Phase 6 code: TourEngine, TourModels, tour UI (data is ready, engine is not)
- [ ] DB hardening: indexes, FK constraints, explicit migrations, CRUD ops (Tier 1 audit items)
- [ ] JSON content package system implementation
- [ ] FTS5 search implementation
- [ ] App-side /salem/sync call implementation
- [ ] API key security (BuildConfig/secrets-gradle-plugin)
- [ ] Network security config (disable cleartext, enable minify)

---

## Session: 2026-04-03e — Phase 5: POI Catalog + Provenance + Staleness + API

### Context
Session 73. Major architecture session — established offline-first + API sync dual-database architecture, added data provenance and staleness hooks across all entities (local Room + remote PostgreSQL), curated Phase 5 POI catalog, completed Phase 4 loose ends (DB asset loading), and created Salem content API endpoints.

### Key Architectural Decisions
1. **Offline-first**: App ships with bundled Room DB (salem_content.db asset, 1.7MB, 841 records). Works 100% without internet.
2. **Online-enhanced**: When connected, syncs via `/salem/sync` endpoint. Core features (transit, weather, Overpass POIs) require network.
3. **Provenance on everything**: Every entity carries data_source, confidence (0-1), verified_date, created_at, updated_at, stale_after. Built as hooks to evolve later.
4. **Staleness TTL**: Historical content = never stale. Businesses = 180 days. Events = expire at end date.
5. **Backward compatible**: All new endpoints under `/salem/*`. Existing pois table gets optional provenance columns (ALTER ADD, nullable). No breaking changes to LocationMapApp.
6. **Cloud-ready**: Salem API endpoints are stateless, horizontally scalable for future cloud deployment.

### Changes Made

**Room Entities (9 files)** — Added 6 provenance/staleness fields to all entities:
- TourPoi, SalemBusiness, HistoricalFigure, HistoricalFact, TimelineEvent, PrimarySource, Tour, TourStop, EventsCalendar
- Room DB version 1 → 2, fallbackToDestructiveMigration (no production users yet)

**DAOs (9 files)** — Added staleness-aware queries:
- findStale(now), findBySource(source), markUpdated(id, now), setStaleAfter(id, staleAfter)

**SalemContentRepository** — Added provenance methods:
- getStaleTourPois(), getStaleBusinesses(), getStaleEvents()
- getTourPoisBySource(), getBusinessesBySource()
- markTourPoiUpdated(), markBusinessUpdated()

**SalemModule (DI)** — Added createFromAsset("salem_content.db") + fallbackToDestructiveMigration()

**Content Pipeline** — Provenance throughout:
- New `Provenance` data class in PipelineOutput.kt
- All 9 Output* classes carry provenance
- SQL generation includes all provenance columns
- Pipeline now loads curated POIs (SalemPois.kt) and businesses (SalemBusinesses.kt)

**Phase 5 POI Curation** — 29 tour POIs + 23 businesses:
- Witch Trials: 8 sites (Memorial, Museum, Witch House, Proctor's Ledge, Cemetery, Jail, Court, Rebecca Nurse)
- Maritime: 3 sites (Salem Maritime NHP, Custom House, Derby Wharf)
- Museums: 5 sites (PEM, Seven Gables, Pioneer Village, Witch Dungeon, Pirate Museum)
- Literary: 3 sites (Hawthorne Birthplace, statue, hotel)
- Parks: 6 sites (Common, Winter Island, Willows, Conant statue, Ropes Mansion, Chestnut St)
- Visitor: 4 sites (NPS Center, MBTA Station, Ferry Terminal, Museum Place Garage)
- Occult shops: 5 (Crow Haven Corner, HEX, OMEN, Artemisia, Coven's Cottage)
- Restaurants: 7 (Turner's, Sea Level, Finz, Flying Saucer, Rockafellas, Ledger, Opus)
- Bars: 3 (Mercy Tavern, Notch Brewing, Bit Bar)
- Cafes: 3 (Gulu-Gulu, Jaho, Brew Box)
- Lodging: 5 (Hawthorne Hotel, Waterfront, Salem Inn, Coach House, Morning Glory)

**PostgreSQL Schema** — `cache-proxy/salem-schema.sql`:
- 9 Salem content tables mirroring Room entities (all with provenance)
- Provenance columns added to existing `pois` table (backward compatible ALTER ADD)
- `salem_sync_state` table for tracking sync status

**Node.js API** — `cache-proxy/lib/salem.js`:
- GET /salem/pois, /salem/pois/:id (filterable by category, source, stale, nearby, search)
- GET /salem/businesses (filterable by type, source, stale, search)
- GET /salem/figures, /salem/figures/:id (includes related facts/sources)
- GET /salem/timeline, /salem/sources, /salem/tours, /salem/tours/:id, /salem/events
- GET /salem/sync?since= (incremental sync for mobile app)
- GET /salem/stats (counts + stale record counts)

**Database Asset** — `app-salem/src/main/assets/salem_content.db`:
- 1.7MB SQLite, Room identity hash 1ab2eea2c8c64126e88af7a9ce8ba38f
- 841 records: 29 POIs, 23 businesses, 49 figures, 500 facts, 40 events, 200 sources

### Emulator Verification
- Salem_Tour_API34 on port 5570
- APK installs and launches successfully
- Map renders Salem downtown with correct tiles
- Purple toolbar branding (#2D1B4E) renders correctly
- "Filling POIs..." status line shows Room DB loading
- MBTA transit loaded (20 trains, 266 buses, 257 stations)
- No crashes, no Room errors, no FATAL exceptions
- ANR was system resource contention (multiple emulators running), not app bug

### Files Created (5)
- `cache-proxy/salem-schema.sql` — PostgreSQL Salem content schema
- `cache-proxy/lib/salem.js` — Node.js Salem content API
- `salem-content/src/main/kotlin/.../data/SalemPois.kt` — Curated tour POIs
- `salem-content/src/main/kotlin/.../data/SalemBusinesses.kt` — Curated businesses
- `salem-content/create_db.sql` — Room-compatible SQLite schema for asset DB

### Files Modified (23)
- 9 Room entity files (provenance fields)
- 8 DAO files (staleness queries) + TourStopDao unchanged
- `SalemContentRepository.kt` (provenance methods)
- `SalemContentDatabase.kt` (version 2)
- `SalemModule.kt` (createFromAsset + fallbackToDestructiveMigration)
- `PipelineOutput.kt` (Provenance class + fields on all outputs)
- `ContentPipeline.kt` (provenance SQL generation + curated data loading)
- `cache-proxy/server.js` (Salem module registration + startup log)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` (architecture docs + Phase 5 checkboxes)
- `STATE.md`, `SESSION-LOG.md`

### Open Items
- Phase 5 remaining POIs: ~7 uncurated (Hathorne home, Corwin home, Derby Wharf Light, Narbonne/Derby/Scale Houses, Castle Dismal, Mall St, McIntire District, South Harbor Garage)
- Step 5.5 (Overpass overlay merge) deferred to Phase 6+ UI integration
- Phase 6 (Tour Engine) is next
- Emulator performance tuning (avoid running multiple AVDs)

---

## Session: 2026-04-03d — POI Data Provenance Strategy Note

### Context
Session 72. Brief session — user raised strategic concern about POI data quality before Phase 5 begins.

### Key Decision
POIs need data provenance tracking for a paid app. Every POI must know its source (manual_curated, overpass_import, google_places, user_report, salem_project), have a verified_date, and a confidence score. This affects Phase 5 entity schema design — must add source/confidence/verified fields to TourPoi and SalemBusiness entities before populating them.

### Changes Made
- Saved project memory: `project_poi_provenance.md`
- No code changes

### Open Items
- Phase 5 must incorporate provenance fields into Room entities before curating POI data
- Need strategy for staleness detection (businesses closing, hours changing)
- Consider correction/verification workflow

---

## Session: 2026-04-03c — Phases 2-4: Salem App Shell, Content Database, Content Pipeline

### Context
Session 71. Phase 1 emulator test confirmed working. Executed Phases 2, 3, and 4 of the WickedSalemWitchCityTour master plan.

### Changes Made

**Phase 2 — Salem App Shell (`:app-salem`)**
- Created `app-salem/build.gradle` — Android application module, applicationId `com.example.wickedsalemwitchcitytour`
- Updated `settings.gradle` with `:app-salem`
- Created `AndroidManifest.xml` — `WickedSalemApp`, `SalemMainActivity`
- Copied and adapted 31 Kotlin source files from `:app`:
  - `WickedSalemApp.kt` (Application class)
  - `SalemMainActivity.kt` + 12 extension files (map centered on Salem 42.521, -70.887, zoom 15)
  - 7 ViewModels, 3 helpers, menu system, radar scheduler, debug tools
  - Package: `com.example.wickedsalemwitchcitytour` (core imports preserved as `com.example.locationmapapp.*`)
- Added missing core imports (MenuPrefs, MenuEventListener, PoiLayerId, DebugLogger) for cross-package access
- Salem branding: deep purple (#2D1B4E), antique gold (#C9A84C), Theme.WickedSalem, placeholder "W" icon
- Copied all resources (67 drawables, 4 layouts, 8 menus)

**Phase 3 — Salem Content Database**
- Created 9 Room entity classes under `app-salem/.../content/model/`:
  TourPoi, SalemBusiness, HistoricalFigure, HistoricalFact, TimelineEvent, PrimarySource, Tour, TourStop, EventsCalendar
- Created 9 DAO interfaces under `content/dao/` with proximity queries, search, joins
- Created `SalemContentDatabase.kt` (Room, version 1, 9 entities)
- Created `SalemModule.kt` (Hilt DI — database singleton + 9 DAO providers)
- Created `SalemContentRepository.kt` — unified repository with bulk insert methods

**Phase 4 — Content Pipeline (`:salem-content`)**
- Created JVM-only module `salem-content/` with Gson + application plugin
- Created 13 Gson model classes matching ~/Development/Salem JSON schemas
- Created 6 readers: Building (424), NPC (Tier 1+2: 49), Fact (3,672 filtered), Event (40), PrimarySource (top 200), Coordinate (29)
- Created `CoordinateMapper.kt` — grid→GPS (7.9m/unit at lat 42.56°)
- Created `NarrationGenerator.kt` — TTS-optimized short/long narration from historical data
- Created `ContentPipeline.kt` — orchestrator with SQL output
- Created `ContentValidator.kt` — data integrity validation
- Pipeline runs successfully: 1.5MB SQL, 789 INSERT statements

### Files Modified
- `settings.gradle` — added `:app-salem`, `:salem-content`
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — checked off Phase 1.7, Phase 2, Phase 3, Phase 4
- `STATE.md` — updated current direction and architecture

### Files Created
- `app-salem/` — 31 Kotlin source files, manifest, build.gradle, all resources (100+ files total)
- `app-salem/.../content/` — 21 Kotlin files (9 entities, 9 DAOs, database, DI module, repository)
- `salem-content/` — 12 Kotlin files (models, readers, mapper, narration, pipeline, validator, main)
- `salem-content/salem_content.sql` — 1.5MB generated SQL

### Build Status
- `:app` — builds clean
- `:app-salem` — builds clean (9.6MB APK)
- `:salem-content` — compiles and runs successfully

### Decisions Made
- Used `.gradle` (Groovy) not `.gradle.kts` for app-salem to match existing convention
- Kept same package structure convention with explicit core imports rather than shared packages
- Pipeline outputs SQL rather than pre-built .db — Room DB generation deferred to Phase 5 when POI GPS data is curated
- Capped facts at 500 (from 3,672 available) — can increase later
- Tour POIs, businesses, tours, events calendar left empty — Phase 5/6/9 will curate with real GPS coordinates

### Open Items
- Phase 2: Splash screen layout not yet created
- Phase 2: Emulator testing of app-salem not yet done
- Phase 4: Pre-built `salem_content.db` for assets deferred to Phase 5
- Next: Phase 5 — Enhanced Salem POI Catalog (curated GPS locations for tour stops, businesses)

---

## Session: 2026-04-03b — Phase 1: Core Module Extraction

### Context
Continuation of WickedSalemWitchCityTour work. Previous session (crashed due to OS reboot) completed all Phase 1 code changes but never committed. This session recovered and verified the work, then performed proper session end.

### Changes Made

#### Phase 1: Core Module Extraction (Steps 1.1–1.7)
- Created `core/` Android library module (`core/build.gradle`, `core/src/main/AndroidManifest.xml`)
- Updated `settings.gradle` to include `:core`, `app/build.gradle` to depend on `:core`
- Moved 22 files from `:app` to `:core`:
  - **Models**: `Models.kt`, `AppException.kt`
  - **12 Repositories**: Places, Mbta, Aircraft, Weather, Webcam, Tfr, Find, Geofence, GeofenceDatabase, Auth, Chat, Comment
  - **Location**: `LocationManager.kt`
  - **Geofencing**: `GeofenceEngine.kt`
  - **Utilities**: `DebugLogger.kt`, `FavoritesManager.kt`
  - **DI**: `AppModule.kt` → `CoreModule.kt` (renamed)
  - **Menu**: `MenuPrefs.kt`, `MenuEventListener.kt`
- Updated all import statements in remaining `:app` files
- `PoiLayerId.kt` not a standalone file (N/A — defined within PoiCategories.kt)

#### Build Verification
- `./gradlew :core:assembleDebug` — BUILD SUCCESSFUL (26 tasks)
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (62 tasks)
- Emulator testing deferred to next session

#### Other Changes (from crashed session)
- `CURRENT_TESTING.md` — updated test status
- `toolbar_two_row.xml` — layout updates
- `MarkerIconHelper.kt` — additions
- `AppBarMenuManager.kt` — import updates
- Cache proxy & web app — minor config/port adjustments
- Shell scripts — updated for current environment

### Files Created (1 new module)
- `core/build.gradle` — Android library module config
- `core/src/main/AndroidManifest.xml` — minimal library manifest
- `core/src/main/java/.../` — 22 Kotlin files moved from `:app`
- `app/src/main/res/drawable/badge_red.xml`, `badge_teal.xml` — badge drawables
- `bin/` — helper scripts

### Files Modified (17)
- `settings.gradle`, `app/build.gradle` — multi-module setup
- `MainActivity.kt`, `MainActivityAircraft.kt`, `MainActivityFind.kt`, `MainActivityGeofences.kt`, `MainActivityMetar.kt`, `MainActivityTransit.kt`, `MainActivityWeather.kt` — import updates
- `MainViewModel.kt`, `TransitViewModel.kt` — import updates
- `AppBarMenuManager.kt`, `MarkerIconHelper.kt` — import updates + additions
- `DebugHttpServer.kt`, `TcpLogStreamer.kt` — minor updates
- `toolbar_two_row.xml` — layout changes
- Cache proxy + web + shell scripts — config adjustments

### Files Deleted (from `:app`, moved to `:core`)
- 22 files: Models.kt, AppException.kt, LocationManager.kt, 12 repositories, GeofenceEngine.kt, DebugLogger.kt, FavoritesManager.kt, AppModule.kt, MenuPrefs.kt, MenuEventListener.kt

### Decisions Made
- `PoiLayerId` is not a standalone file — master plan step marked N/A
- Phase 1 complete pending emulator verification in next session

### Next Steps
- Emulator test to verify all features still work identically (Step 1.7 final checkbox)
- Begin Phase 2: Salem App Shell (`:app-salem` module)

---

## Session: 2026-04-03a — WickedSalemWitchCityTour Master Plan

### Context
Planning session for a new app built on the LocationMapApp platform. The user wants to create a GPS-guided tourist app for Salem, MA that leverages all existing LocationMapApp features (maps, transit, weather, geofencing, POIs, social) plus adds tour-specific features (TTS narration, walking tours, historical content from the Salem Witch Trials project).

### Decisions Made

1. **Architecture**: Multi-module monorepo — `:core` (shared library), `:app` (generic LocationMapApp), `:app-salem` (WickedSalemWitchCityTour)
2. **App name**: WickedSalemWitchCityTour, package `com.example.wickedsalemwitchcitytour`
3. **Price**: $9.99 one-time purchase, no ads, no IAP
4. **Map SDK**: Stay with osmdroid (free, offline tile caching)
5. **Narration**: Android TTS first, upgrade later
6. **Walking directions**: OSRM (free, no API key)
7. **Danvers sites**: Included in tours, flagged as requiring transportation
8. **Content source**: ~/Development/Salem project JSON (2,174 NPCs, 3,891 facts, 4,950 primary sources, 424 buildings)
9. **Session protocol**: Established start/end protocol with WickedSalemWitchCityTour_MASTER_PLAN.md as primary plan document

### Research Performed
- Full LocationMapApp architecture analysis (52 Kotlin files, 12 repositories, all dependencies mapped)
- Full Salem project content inventory (29,800 data files, 1.1M lines of JSON, scholarly sources cataloged)
- Salem tourist destination research (25+ attractions, Heritage Trail route, MBTA access, seasonal events, GPS coordinates)

### Files Created
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Comprehensive 10-phase plan with tour definitions, POI catalog, content pipeline, database schema

### Files Modified
- `STATE.md` — Added current direction section, planned multi-module architecture
- `SESSION-LOG.md` — This session entry

### Next Steps
- Begin Phase 1: Core Module Extraction (create `:core` module, move shared code)

---

## Session: 2026-03-20a — Performance Optimization + Proxy Quick-Drain

### Context
Session startup: recycled servers, fixed missing POIs (DATABASE_URL not set), configured emulator for testing, then addressed ANR/performance issues and POI import latency.

### Issues Fixed

#### 1. POIs not appearing — DATABASE_URL missing
- Cache proxy started without `DATABASE_URL`, so `/db/*` endpoints returned 503
- POIs are PostgreSQL-backed; without the env var, bbox queries return empty
- Fix: always start proxy with `DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp`
- Helper script: `bin/restart-proxy.sh` already includes this

#### 2. Emulator configuration (Pixel_8a_API_34)
- AVD config at `~/.android/avd/Pixel_8a_API_34.avd/config.ini` updated:
  - `hw.initialOrientation=landscape` — app renders landscape
  - `showDeviceFrame=no` — removes phone bezel, fixes touch coordinate mapping
  - `skin.name=1080x2400` / `skin.path=1080x2400` — no pixel_8a skin
  - `hw.ramSize=8192` — prevents OOM with all layers active
- Immersive mode via system setting: `adb shell settings put global policy_control "immersive.full=com.example.locationmapapp"`
- Resize emulator by dragging corner — do NOT use `wmctrl` (crashes emulator or breaks touch coords)

#### 3. ANR / Performance — marker rendering on main thread
- **POI cluster rendering** (`renderPoiClusters`): moved icon generation to `Dispatchers.Default`, marker creation stays on main thread
- **POI marker rendering** (`replaceAllPoiMarkers`): same pattern — icons generated off-thread
- **Station markers** (`addStationMarkersBatched`): new batched method with off-thread icon generation (257 stations at once)
- **Webcam markers** (`addWebcamMarkersBatched`): new batched method with shared icon instance
- **TransitViewModel**: all 5 fetch methods now use `withContext(Dispatchers.IO)` to prevent DNS timeout blocking main thread
- **Staggered MBTA startup**: `onStart()` MBTA restores wrapped in coroutine with 2s initial delay + 500ms gaps between layers
- Result: frame skips reduced from 239 to ~35 per burst (emulator-only residual)

#### 4. POI import latency — quick-drain
- Problem: Overpass search finds POIs → buffered for import → but import only runs every 15 minutes → bbox returns 0
- Fix: added quick-drain in `server.js` — wraps `bufferOverpassElements` to trigger `runPoiDbImport` 2 seconds after new elements are buffered
- POIs now appear on map within ~4 seconds of a long-press search instead of waiting up to 15 minutes
- `lib/import.js` now exports `runImport()` for the quick-drain hook

#### 5. "Emulator is not responding" dialog
- This is **Ubuntu GNOME's** `check-alive-timeout` detecting the emulator process is sluggish — not an Android ANR
- No app-level ANR in logcat — the emulator hardware (Quadro K1100M / Haswell) is the bottleneck
- GNOME timeout set to 20s (`gsettings set org.gnome.mutter check-alive-timeout 20000`)
- Will not occur on real devices

### Files Modified

#### Android App
- `MainActivity.kt` — batched marker rendering (POI clusters, POI markers, webcams), staggered MBTA startup
- `MainActivityTransit.kt` — added `addStationMarkersBatched()` with off-thread icon generation
- `TransitViewModel.kt` — all fetch methods use `withContext(Dispatchers.IO)`

#### Cache Proxy
- `server.js` — quick-drain wrapper on `bufferOverpassElements` triggers import 2s after new elements arrive
- `lib/import.js` — exports `runImport()` function for quick-drain hook

#### AVD Config
- `~/.android/avd/Pixel_8a_API_34.avd/config.ini` — landscape, no frame, 8GB RAM, generic skin

---

## Session: 2026-03-05f (v1.5.68 — Web App Phase 6: Favorites + URL Routing)

### Context
Phase 6 of web app: add localStorage favorites (matching Android app pattern) and shareable URLs via `window.history` + URL search params. Client-only features — no proxy or server changes needed.

### Changes Made

#### New Files (2)
- `web/src/hooks/useFavorites.ts` (~35 lines) — localStorage key `lma_favorites`, JSON array of `FavoriteEntry`, dedup by `(osm_type, osm_id)` with String coercion, newest-first prepend, API: `{ favorites, isFavorite, toggleFavorite, count }`
- `web/src/hooks/useUrlState.ts` (~60 lines) — `parseUrlState()` reads `?lat=&lon=&z=&poi=` on load with validation (lat ±90, lon ±180, z 1-19, poi format `node|way|relation/digits`); `useUrlState()` hook returns `updateMapPosition` (500ms debounced replaceState), `setPoiParam`, `clearPoiParam`

#### Modified Files (5)
- `web/src/lib/types.ts` — added `FavoriteEntry` interface (osm_type, osm_id, name, lat, lon, category, addedAt)
- `web/src/components/Find/PoiDetailPanel.tsx` — added `isFavorite`/`onToggleFavorite` props, amber star button in header between title and close button, share clipboard fallback copies `window.location.href` instead of text
- `web/src/components/Find/FindPanel.tsx` — added `favoriteCount`/`favoriteResults`/`onShowFavorites` props, gold amber Favorites cell before category grid, useEffect to switch to results view when favoriteResults populated
- `web/src/components/Map/MapView.tsx` — added optional `zoom` prop, passed to `MapContainer zoom={zoom ?? 14}`
- `web/src/App.tsx` — imported useFavorites + useUrlState + parseUrlState, URL-based initial mapCenter/zoom, on-mount POI deep linking (fetches via `find.fetchPoiDetail`), `handleToggleFavorite` builds FavoriteEntry from selectedResult, `handleShowFavorites` converts favorites to FindResult[] with haversine distances, URL updates in handleBoundsChange/handleSelectResult/handlePoiClick/handleCloseDetail/handleAircraftClick/handleVehicleClick/handleStopClick/handleToggleFind

### Verification
- `npx tsc --noEmit` — 0 errors
- `npm run build` — clean, 506KB / 149KB gzip (144 modules)

### Testing Needed
- [ ] Open POI detail → tap star → verify amber fill → close → open Find → Favorites cell shows count → tap → favorites list → tap favorite → detail opens with star filled
- [ ] Pan map → URL updates with `?lat=&lon=&z=` → copy URL → open new tab → map loads at same position
- [ ] Open POI detail → URL has `?poi=way/123` → copy → new tab → POI detail opens automatically
- [ ] POI detail → Share button → clipboard contains full URL with poi param

---

## Session: 2026-03-05e (v1.5.67 — Web App: Long-Press + Home Location)

### Context
Two UX improvements: (1) toolbar hover tooltips (already present via title attributes), (2) long-press on map to relocate + persistent home location from Profile dropdown.

### Changes Made

#### Modified Files (5)
- `web/src/hooks/useGeolocation.ts` — rewritten: Promise-based `locate()`, persistent home location via localStorage (`homeLocation` key), `setHome(lat, lon)`, `clearHome()`, `hasHome` flag, skips browser GPS on mount when home is set
- `web/src/components/Map/MapView.tsx` — added `LongPressHandler` component (700ms timer, cancel on drag/zoom/mouseup, context menu suppressed), new `onLongPress` and `hasHome` props
- `web/src/components/Map/MapControls.tsx` — added `hasHome` prop, teal dot indicator on locate button when home is set, dynamic tooltip
- `web/src/components/Social/ProfileDropdown.tsx` — added "Set Current Location as Home" button (with house icon) + "Reset to Browser GPS" button (appears when home is set), works regardless of auth state
- `web/src/App.tsx` — wired `handleLongPress` (fly to point, auto-zoom 18), `handleSetHome` (saves map center), fixed `handleLocate` to use Promise-based locate

### Build
- TypeScript: clean, no errors
- Production: 501KB / 147KB gzip

---

## Session: 2026-03-05d (v1.5.66 — Web App Phase 5: Auth + Social)

### Context
Phase 5 of web app: add social layer matching the Android app's auth, comments, and chat features. All proxy endpoints already exist (auth.js, comments.js, chat.js) — no proxy changes needed. `socket.io-client` already in package.json.

### Changes Made

#### New Files (9)
- `web/src/hooks/useAuth.ts` (83 lines) — auth state, register, login, logout, validates stored token via `/auth/me` on mount
- `web/src/hooks/useComments.ts` (63 lines) — POI comment CRUD, falls back to unauthenticated GET if no token
- `web/src/hooks/useChat.ts` (106 lines) — Socket.IO connection with JWT auth, room list (REST), real-time messaging, typing indicator
- `web/src/lib/timeFormat.ts` (10 lines) — relative time formatter ("just now" / "5m ago" / "2h ago" / "3d ago" / date)
- `web/src/components/Social/AuthDialog.tsx` (117 lines) — modal overlay with register/login toggle, client-side validation (displayName 2-50, password 8+, email format)
- `web/src/components/Social/ProfileDropdown.tsx` (60 lines) — anchored to toolbar profile button, avatar initial + name + role + sign out, click-outside-to-close
- `web/src/components/Social/CommentsSection.tsx` (136 lines) — comment list with author/time/stars/votes/delete, add comment form with star selector + char counter (1000 limit)
- `web/src/components/Social/StarRating.tsx` (28 lines) — filled/empty stars, clickable in interactive mode
- `web/src/components/Social/ChatPanel.tsx` (214 lines) — two views (room list / chat room), room create inline form, message bubbles (own=right/teal, others=left/gray), typing indicator, send bar

#### Modified Files (5)
- `web/src/lib/types.ts` — +6 social types: AuthUser, AuthResponse, PoiComment, CommentsResponse, ChatRoom, ChatMessage
- `web/src/config/api.ts` — +`authFetch<T>()` with Bearer header, proactive token refresh (2-min buffer), singleton refresh de-duplication, 401 auto-retry; +localStorage helpers (getStoredTokens/storeTokens/storeUser/getStoredUser/clearAuth)
- `web/src/components/Layout/Toolbar.tsx` — +Chat button (speech bubble SVG) + Profile button (user circle SVG / initial letter when logged in), +3 new props (chatOpen, profileOpen, userInitial)
- `web/src/components/Find/PoiDetailPanel.tsx` — +CommentsSection embedded in overflow-y-auto area below action buttons, +10 new comment/auth props
- `web/src/App.tsx` — +useAuth/useComments/useChat hooks, +authDialogOpen/profileOpen/chatOpen state, +Chat/Find/Weather mutual exclusion, +comments load on POI open, +auth dialog auto-close on login, +chat connect/disconnect on panel open/close

### Verification
- `npx tsc --noEmit` — 0 errors
- `npx vite build` — clean, 499KB / 147KB gzip (142 modules)
- No proxy changes required — all social endpoints already exist

### Testing Needed
- [ ] Register new account → verify tokens in localStorage → refresh page → auto-login via `/auth/me`
- [ ] Profile dropdown → user info → sign out → icon reverts to generic circle
- [ ] Open POI → "Comments (0)" → add comment with star rating → verify appears → vote up/down → delete
- [ ] Chat panel → see Global room → enter → send message → verify real-time (second tab)
- [ ] Token refresh: wait for expiry or set short JWT_ACCESS_EXPIRY → verify authFetch auto-refreshes
- [ ] Panel mutual exclusion: Chat ↔ Find ↔ Weather close each other
- [ ] Dark mode: all new panels render correctly

---

## Session: 2026-03-05c (Web App External Access)

### Context
Testing web app from an external system — POIs were failing. Diagnosed that the cache proxy wasn't running. Also enabled Vite dev server to listen on all interfaces for LAN access.

### Changes Made
- `web/vite.config.ts` — added `host: '0.0.0.0'` to Vite server config (allows access from other machines on the network)
- `.gitignore` — added `web/tsconfig.tsbuildinfo` (build artifact)

### Notes
- Web app requires the cache proxy to be running (`node server.js` on port 3000) — all POI/weather/aircraft/transit data comes from the proxy
- Web app currently only shows POIs that exist in PostgreSQL (previously scanned areas); no Overpass trigger mechanism yet
- Future: consider adding auto-fetch for unseen areas (Phase 5+ scope)

---

## Session: 2026-03-05b (v1.5.65 — Web App Phase 4: Aircraft + Transit)

### Context
Phase 4 of web app: add aircraft tracking (OpenSky) and MBTA transit (trains/subway/buses) as real-time map layers with detail panels, follow mode, and arrival/departure boards. Also added server-side POI clustering to handle high-density viewports. Continued from v1.5.64 (initial implementation) with extensive bug fixes and enhancements.

### Changes Made

#### New Files (12)
- `web/src/config/aircraft.ts` — altitude color mapping (ground→gray, <5kft→green, 5-20kft→blue, >20kft→purple), unit converters (m/s→mph, m→ft, heading→compass), `aircraftIconHtml()` DivIcon SVG factory
- `web/src/config/transit.ts` — MBTA route colors (Red/Orange/Blue/Green/CR/Bus), `getRouteColor()`, `routeTypeLabel()`, `vehicleStatusLabel()`
- `web/src/hooks/useAircraft.ts` — aircraft state management, 15s auto-refresh, `parseStateVector()` for OpenSky arrays, select/follow/history
- `web/src/hooks/useTransit.ts` — trains/subway/buses/stations/busStops state, per-type refresh timers (rail 15s, bus 30s), vehicle follow, trip predictions, fetchPredictionsById for bus stops
- `web/src/components/Map/AircraftMarkerLayer.tsx` — Leaflet DivIcon markers with rotated airplane SVG, altitude-colored, callsign labels
- `web/src/components/Map/FlightTrailLayer.tsx` — Polyline segments from flight path history, altitude-colored
- `web/src/components/Map/TransitMarkerLayer.tsx` — route-colored CircleMarkers for vehicles (originally DivIcon, replaced after rendering issues), station dots, bus stop dots, selected vehicle highlighting with teal ring
- `web/src/components/Aircraft/AircraftDetailPanel.tsx` — slide-in panel: altitude-colored header, info rows, follow/map buttons, sighting history
- `web/src/components/Transit/VehicleDetailPanel.tsx` — route-colored header, status, numbered next 5 stops with arrival times, follow button
- `web/src/components/Transit/ArrivalBoardPanel.tsx` — dark-themed board with DEP/ARR labels, both-times display for through-stations, "service ended" message
- `web/src/components/Layout/LayersDropdown.tsx` — dropdown with 4 toggle switches + count badges, click-outside-to-close

#### Modified Files (8 web + 2 proxy)
- `web/src/lib/types.ts` — 7 new interfaces: AircraftState, FlightPathPoint, AircraftSighting, AircraftHistory, MbtaVehicle (with tripId), MbtaStop, MbtaPrediction (with stopName/stopSequence)
- `web/src/hooks/usePois.ts` — added clusters state, PoiCluster type for server-side aggregation
- `web/src/App.tsx` — aircraft/transit hooks, layers dropdown, detailView mutual exclusion (5 states), follow effects, bus stops bbox fetch
- `web/src/components/Map/MapView.tsx` — all new layer components + props
- `web/src/components/Map/PoiMarkerLayer.tsx` — cluster rendering (translucent circles, count labels, non-interactive)
- `web/src/components/Layout/Toolbar.tsx` — Layers button with stacked-layers icon + active count badge
- `web/src/components/Layout/StatusBar.tsx` — per-layer vehicle counts
- `web/src/index.css` — aircraft-label, transit-label, cluster-label CSS
- `cache-proxy/lib/pois.js` — `/pois/bbox` server-side clustering (COUNT + SQL grid aggregation when >1000 POIs)
- `cache-proxy/lib/mbta.js` — 5 new endpoints: vehicles, stations, predictions (with stop_name resolution), trip-predictions, bus-stops/bbox

### Bug Fixes During Implementation
1. **DivIcon transit markers invisible**: `Marker` with custom HTML at 14-20px didn't render → replaced with `CircleMarker` (same proven approach as POIs/METARs)
2. **MBTA stations returning 0**: `location_type=1` filter incorrect (MBTA uses 0) → removed filter
3. **28k POIs page unresponsive**: client tried to render 28k CircleMarkers → server-side SQL grid aggregation returns ~77 clusters
4. **Selected vehicle label not showing**: react-leaflet permanent Tooltip doesn't mount on dynamic condition change → force remount via key including selection state
5. **Bus stop predictions empty**: `stop_name` resolver only searched rail stations cache → added `fetchPredictionsById` for direct stop ID queries
6. **Predictions showing null-time entries first**: sorted by arrival_time but nulls come first → filter nulls + sort by earliest available time
7. **North Station showing only one platform**: single stop ID queried → `stop_name` parameter resolves all child platforms (33 predictions across Orange/Green/CR)
8. **Bus stops not appearing on initial toggle**: fetchBusStops not called until map move → added useEffect on busesVisible + ref-based callback in handleBoundsChange

### Verification
- Vite dev server compiles cleanly
- Aircraft markers appear when layer enabled (altitude-colored, rotated)
- Click aircraft → detail panel with info + follow button
- Trains/subway appear as route-colored circles
- Click vehicle → detail with next 5 stops and times
- Click station → arrival/departure board with DEP/ARR labels
- Bus stops appear at zoom >= 15 (max 200 per viewport)
- Vehicle follow mode tracks position across refreshes
- POI clusters render as translucent circles with count labels when zoomed out
- Detail panel mutual exclusion works across all 4 panel types

---

## Session: 2026-03-05 (v1.5.63 — Web App Phase 3: Weather Overlay)

### Context
Phase 3 of web app: add weather visualization — weather panel with current/hourly/daily forecasts, METAR aviation markers, radar overlay with animation, and alert notifications. All proxy endpoints already exist.

### Changes Made

#### New Files (5)
- `web/src/hooks/useWeather.ts` — weather/METAR fetch with AbortController, radar/metar toggles, 5-min auto-refresh timer
- `web/src/config/weatherIcons.ts` — NWS icon code → inline SVG React elements (~25 codes, day/night variants using basic SVG shapes)
- `web/src/components/Weather/WeatherPanel.tsx` — 360px slide-in panel: header (city/state/station), expandable alert banners (red/orange by severity), Current/Hourly/Daily tab bar, layer controls (Radar/Animate/METAR toggles)
- `web/src/components/Map/RadarLayer.tsx` — RainViewer API radar tiles, static (latest frame at 35% opacity) + animated (7-frame loop at 800ms via Leaflet `L.tileLayer` + `setOpacity()`)
- `web/src/components/Map/MetarMarkerLayer.tsx` — flight-category colored CircleMarkers (VFR=#2E7D32, MVFR=#1565C0, IFR=#C62828, LIFR=#AD1457), monospace labels at zoom >= 10

#### Modified Files (6)
- `web/src/lib/types.ts` — added WeatherLocation, WeatherCurrent, WeatherHourly, WeatherDaily, WeatherAlert, WeatherData, MetarStation types
- `web/src/App.tsx` — weatherOpen state, mutual exclusion with Find, METAR bounds-based fetch, alert click handler, stable callback refs via individual function deps
- `web/src/components/Layout/Toolbar.tsx` — weather button (dynamic SVG icon from weatherIcons + red dot alert indicator)
- `web/src/components/Layout/StatusBar.tsx` — red alert banner (event name + count, click opens weather panel)
- `web/src/components/Map/MapView.tsx` — RadarLayer + MetarMarkerLayer integration with new props
- `web/src/index.css` — `.metar-label` styles (monospace, dark mode variant)

### Bug Fixes During Implementation
- Iowa State Mesonet animated tile URLs return 404 (format `nexrad-n0q-{timestamp}` doesn't work for national mosaic) — switched to RainViewer API which provides a JSON frame manifest
- react-leaflet `<TileLayer>` doesn't reactively update `opacity` prop — switched to direct Leaflet API (`L.tileLayer` + `setOpacity()`) for radar animation
- `handleBoundsChange` infinite re-render loop: `wx` (entire hook return object) as dependency recreated callback every render → `BoundsWatcher` useEffect re-fired → POIs never settled. Fixed by using stable individual function refs (`wx.fetchMetars`) + ref for `metarsVisible`

### Verification
- `npm run build` — clean, 0 TypeScript errors, 404KB / 121KB gzip
- Weather panel opens with current conditions, hourly, daily tabs
- Radar toggle shows RainViewer tiles at 35% opacity
- Animate toggle cycles 7 frames smoothly
- METAR markers show colored circles at airports
- Alert banner in status bar when NWS alerts active
- Find ↔ Weather mutual exclusion works
- POIs display normally (no infinite re-render)

---

## Session: 2026-03-04j (v1.5.62 — Web App Phase 2: Find + Search + POI Detail)

### Context
Phase 2 of web app: add Find dialog, fuzzy search, and POI detail panel. All proxy API endpoints already working with CORS.

### Changes Made

#### New Files (5)
- `web/src/lib/distance.ts` — haversine distance calculation + imperial formatting (ft/mi)
- `web/src/hooks/useFind.ts` — API hook: search (1s debounce, AbortController), findByCategory, loadCounts, fetchWebsite, fetchPoiDetail; aggregates tag-string counts into category-level counts
- `web/src/components/Find/FindPanel.tsx` — slide-in panel (360px, 85vw mobile): search bar, 4-col category grid with count badges, subtype drill-down, results list, "Filter and Map" button, back navigation state machine
- `web/src/components/Find/ResultsList.tsx` — shared result row component: formatted distance + category color dot + bold name + detail line + category label
- `web/src/components/Find/PoiDetailPanel.tsx` — POI detail: category color bar header, info rows (distance/type/cuisine/address/phone/hours), async website resolution ("Find Website" button), action buttons (Directions/Call/Map/Share)

#### Modified Files (7)
- `web/src/lib/types.ts` — added FindResult, WebsiteInfo, PoiDetailResponse; widened id types to `string | number`
- `web/src/config/categories.ts` — added `resolveCategory()`, `getCategoryByTag()`, `getCategoryTags()`, `getSubtypeTags()`
- `web/src/components/Layout/Toolbar.tsx` — added Find (magnifying glass) button with teal active highlight
- `web/src/components/Layout/StatusBar.tsx` — filter mode: teal bar "Showing N results for X — click to clear"
- `web/src/components/Map/PoiMarkerLayer.tsx` — click handlers on markers, filter mode (forced labels, filtered markers only)
- `web/src/components/Map/MapView.tsx` — forwards filterResults + onPoiClick to PoiMarkerLayer
- `web/src/App.tsx` — full orchestration: Find/Detail mutual exclusion, filter mode, marker click → detail, fitBounds on Filter and Map

### Bug Fixes During Implementation
- Proxy returns `elements` not `results`, `category_hint` not `hint` — fixed response field mapping in useFind
- Proxy returns tag-string categories (`"amenity=cafe"`) not category IDs (`"FOOD_DRINK"`) — added `resolveCategory()` + count aggregation
- Proxy returns string IDs — widened TypeScript types to `string | number`

### Verification
- `npm run build` — clean, 0 TypeScript errors, 385KB / 117KB gzip
- Search "restaurant" returns results with distances and categories
- Category grid shows counts, subtypes drill down works
- POI marker click opens detail panel

---

## Session: 2026-03-04i (v1.5.61 — Web App Phase 1)

### Context
Build a cross-platform web frontend to consume the existing proxy API (54+ endpoints). Zero backend rewrite — just a new React frontend at `web/` alongside `app/` and `cache-proxy/`.

### Changes Made

#### Proxy: CORS Middleware (2 lines + 1 dep)
- Added `cors ^2.8.5` to `cache-proxy/package.json`
- Added `const cors = require('cors'); app.use(cors({ origin: true, credentials: true }))` to `server.js`
- Verified: `Access-Control-Allow-Origin` header present on all responses

#### Web App: Foundation + Map + POI Markers + Dark Mode
- **20 files** created in `web/` directory
- **Tech stack**: React 19, TypeScript (strict), Vite 6, react-leaflet 5, Tailwind CSS 3, PostCSS
- **Map**: react-leaflet MapContainer with OpenStreetMap light tiles + CartoDB Dark Matter dark tiles
- **POI markers**: colored CircleMarkers using `classifyPoi()` — all 17 categories with exact Android hex colors
- **Labels**: zoom >= 16 shows `tags.name` as permanent Leaflet Tooltips (same threshold as Android)
- **Dark mode**: toggle in toolbar, persisted to localStorage, switches tile layer + UI theme
- **Geolocation**: browser Geolocation API, falls back to Boston (42.36, -71.06)
- **Data loading**: debounced 300ms `/pois/bbox` fetch on viewport change, `/pois/stats` for total count
- **Layout**: 12px toolbar (app name + dark mode) + full-height map + 8px status bar (coords + counts)
- **Controls**: zoom +/- buttons + locate button (bottom-right, z-index 1000)
- **Build**: TypeScript clean (0 errors), Vite production build passes (368KB JS / 112KB gzipped)

### Files Created (20)
```
web/package.json, tsconfig.json, vite.config.ts, tailwind.config.ts, postcss.config.js
web/index.html, .env.development, public/favicon.svg, public/manifest.json
web/src/main.tsx, App.tsx, index.css, vite-env.d.ts
web/src/config/api.ts, categories.ts
web/src/lib/types.ts
web/src/hooks/useGeolocation.ts, usePois.ts, useDarkMode.ts
web/src/components/Map/MapView.tsx, PoiMarkerLayer.tsx, MapControls.tsx
web/src/components/Layout/Toolbar.tsx, StatusBar.tsx
```

### Files Modified (2)
- `cache-proxy/server.js` — CORS middleware
- `cache-proxy/package.json` — cors dependency

### Verification
- `npx tsc --noEmit` — 0 errors
- `npx vite build` — clean, no warnings
- `npm run dev` → http://localhost:5173 — Vite dev server starts in 346ms
- `curl -I -H "Origin: http://localhost:5173" http://10.0.0.4:3000/pois/stats` — CORS headers present

---

## Session: 2026-03-04h (v1.5.60 — Proxy Heap Reduction)

### Context
Cache proxy consuming 643MB heap — dominated by two in-memory Maps: `poiCache` (268k entries, ~90MB) fully redundant with PostgreSQL, and `cache` (7,214 entries, ~320MB) mostly stale 365-day Overpass responses.

### Changes Made

#### Phase 1: Eliminate poiCache → PostgreSQL (saves ~90MB heap + 90MB disk)
- **cache.js**: Removed `poiCache` Map, `loadPoiCache()`, `savePoiCache()`, `cacheIndividualPois()`. Added lightweight `importBuffer` array + `bufferOverpassElements()` + `drainImportBuffer()`
- **overpass.js**: Wired `bufferOverpassElements` (replaces `cacheIndividualPois`), `await` on async `collectPoisInRadius`, updated stats fields (`buffered` replaces `added+updated`)
- **scan-cells.js**: `collectPoisInRadius()` now queries PostgreSQL (`SELECT ... FROM pois WHERE lat BETWEEN ... AND lon BETWEEN ...`) instead of iterating poiCache
- **pois.js**: All 4 endpoints rewritten as async PostgreSQL queries — `/pois/stats` (COUNT), `/pois/export` (with limit param), `/pois/bbox` (bbox SELECT), `/poi/:type/:id` (single lookup)
- **import.js**: `runPoiDbImport()` calls `drainImportBuffer()` to get pending elements, dedupes by `type:id`, batch upserts. Removed `lastDbImportTime` delta tracking. Status endpoint shows `pendingDelta: importBuffer.length`
- **admin.js**: Removed `poiCache` references; `/cache/stats` shows `importBufferPending`; `/cache/clear` clears buffer instead of poiCache

#### Phase 2: LRU Cap on Main Cache (saves ~250MB+ heap)
- **cache.js**: Added `MAX_CACHE_ENTRIES=2000` (env-configurable via `MAX_CACHE_ENTRIES`), `evictOldest()` sorts by timestamp and deletes oldest; called in `cacheSet()` and after `loadCache()`
- **admin.js**: Shows `maxCacheEntries` in `/cache/stats`
- **server.js**: Updated deps wiring — removed poiCache/cacheIndividualPois/POI_CACHE_FILE, added importBuffer/bufferOverpassElements/drainImportBuffer/MAX_CACHE_ENTRIES

### Results
| Metric | Before | After |
|--------|--------|-------|
| Heap | 643 MB | 208 MB |
| Cache entries | 7,214 | 2,000 (LRU cap) |
| cache-data.json | 320 MB | 57 MB |
| poi-cache.json | 90 MB | eliminated |
| poiCache Map | 268k entries | eliminated |
| POI source | in-memory Map | PostgreSQL (268k rows) |

### Verification (all passed)
- `/cache/stats` → memoryMB: 208.2, entries: 2000, maxCacheEntries: 2000, importBufferPending: 0
- `/pois/stats` → count: 268,291 (from PostgreSQL)
- `/pois/bbox?s=42.3&w=-71.1&n=42.4&e=-71.0` → 5,819 POIs
- `/poi/way/497190524` → Orient Heights Main Parking (from PostgreSQL)
- `/db/import/status` → pendingDelta: 0, enabled: true
- Server startup clean: all modules loaded, no errors

### Files Modified (7)
- `cache-proxy/lib/cache.js` — removed poiCache + added import buffer + LRU eviction
- `cache-proxy/lib/overpass.js` — wired buffer, await async collectPoisInRadius
- `cache-proxy/lib/scan-cells.js` — PostgreSQL collectPoisInRadius
- `cache-proxy/lib/pois.js` — all 4 POI endpoints → async PostgreSQL queries
- `cache-proxy/lib/import.js` — drain buffer + dedupe instead of poiCache delta
- `cache-proxy/lib/admin.js` — importBuffer + MAX_CACHE_ENTRIES stats
- `cache-proxy/server.js` — updated deps wiring

---

## Session: 2026-03-04g (v1.5.59 — Scan Cell Coverage + Queue Cancel)

### Context
App made redundant Overpass API calls in areas already well-mapped (211k+ POIs). Overpass cache keys use exact 3dp lat/lon + radius + tags, so moving ~100m creates a cache miss. Also, stopping follow/populate left already-queued proxy requests executing wastefully.

### Changes Made

#### 1. Scan Cell Coverage (`cache-proxy/lib/scan-cells.js` — NEW, ~160 lines)
- Divides world into ~1.1km grid cells (2dp lat:lon), tracks `lastScanned` timestamp + `poiCount`
- `checkCoverage()` returns FRESH/STALE/EMPTY; `markScanned()` marks all cells in a circle
- `collectPoisInRadius()` bbox-scans poiCache to serve POIs without upstream call
- Persists to `scan-cells.json` (debounced write, same pattern as radius-hints.json)
- `GET /scan-cells` debug endpoint (all cells or specific lat/lon query)

#### 2. Overpass Integration (`cache-proxy/lib/overpass.js`)
- Coverage check inserted after cache-only merge, before queue: if FRESH, returns from poiCache with `X-Cache: CELL`
- `X-Coverage` header added to all response paths (HIT, covering-cache, scan-cell, MISS)
- `markScanned()` called after successful upstream with lat/lon/radius/poiCount
- `POST /overpass/cancel` — flushes queued requests for a client ID, resolves with 499

#### 3. Server Wiring (`cache-proxy/server.js`)
- scan-cells loaded before overpass (exports checkCoverage, markScanned, collectPoisInRadius into deps)
- `GET /scan-cells` route registered; startup log updated

#### 4. Admin (`cache-proxy/lib/admin.js`)
- `scanCells` count in `/cache/stats`; scan cells cleared + file deleted in `/cache/clear`

#### 5. App: Coverage-Aware Behavior
- `PopulateSearchResult` — new `coverageStatus: String` field
- `PlacesRepository.kt` — reads `X-Coverage` header, treats `X-Cache: CELL` as cache hit
- `MainViewModel.kt` — new `cancelPendingOverpass()` method (fire-and-forget via IO dispatcher)
- `MainActivityPopulate.kt`:
  - Silent fill: 1.5s "Coverage fresh" banner for FRESH (vs 3s)
  - Idle populate spiral: FRESH cells skipped entirely (`continue` — no search, no countdown, no subdivision)
  - Manual populate spiral: same skip behavior
  - `stopIdlePopulate()`, `stopPopulatePois()`, `stopProbe10km()` all call `cancelPendingOverpass()`

### Live Testing Results
- 185 scan cells marked after first populate spiral run
- `[Scan Cells] Marked 63 cells (500 POIs, ~8/cell)` — correct cell marking
- Persistence working: `Saved 185 scan cells to disk`
- `/scan-cells` endpoint returns correct cell data with age/config
- `/cache/stats` shows `scanCells: 185`

### Files Created (1)
- `cache-proxy/lib/scan-cells.js` — scan cell coverage tracking module

### Files Modified (7)
- `cache-proxy/lib/overpass.js` — coverage check, mark scanned, X-Coverage header, cancel endpoint
- `cache-proxy/server.js` — wire scan-cells module
- `cache-proxy/lib/admin.js` — stats + clear
- `app/.../data/model/Models.kt` — coverageStatus field on PopulateSearchResult
- `app/.../data/repository/PlacesRepository.kt` — read X-Coverage, cancelPendingOverpass()
- `app/.../ui/MainViewModel.kt` — cancelPendingOverpass(), Dispatchers import
- `app/.../ui/MainActivityPopulate.kt` — FRESH skip, cancel on stop, shorter banners

---

## Session: 2026-03-04f (Overpass Retry + Zoom 16 Labels + Tap-to-Stop)

### Context
Overpass API returning intermittent HTML error pages (~13% failure rate during 10km Probe). Also UX improvements: lower zoom threshold for labels, single-tap to cancel follow/populate.

### Changes
- **Proxy retry** (`cache-proxy/lib/overpass.js`): 3 endpoint rotation (overpass-api.de, lz4, z), `detectOverpassError()` helper, 4 total attempts with 15s/30s/60s backoff
- **App retry** (`PlacesRepository.kt`): `isHtmlErrorResponse()` + `executeOverpassWithRetry()` wrapper — 3 attempts, 5s/10s delays, coroutine-cancellable; used by both `searchPois()` and `searchPoisForPopulate()`
- **Zoom 16 labels**: POI full names + train/subway/bus detail labels (route, speed, destination, status) now visible from zoom 16+ (was 18+) — changed in `addTrainMarker`, `addSubwayMarker`, `addBusMarker`, `addPoiMarker`, `refreshPoiMarkerIcons`, `refreshVehicleMarkerIcons`, and scroll handler threshold checks
- **Single tap stop**: `singleTapConfirmedHelper` now stops following (vehicle/aircraft) + all population tasks (populate, 10km probe, idle populate, silent fill)

### Files Modified (4)
- `cache-proxy/lib/overpass.js` — retry loop with endpoint rotation + error detection
- `app/.../data/repository/PlacesRepository.kt` — retry wrapper + HTML detection + constants
- `app/.../ui/MainActivityTransit.kt` — zoom threshold 18→16 for train/subway/bus markers
- `app/.../ui/MainActivity.kt` — zoom threshold 18→16 for POI labels + scroll handler + single tap handler

---

## Session: 2026-03-04e (3-Phase Code Decomposition — server.js + ViewModels + MenuPrefs)

### Context
Monolithic files had grown to unsustainable sizes: `server.js` (3,925 lines), `MainViewModel.kt` (958 lines), `AppBarMenuManager.kt` (879 lines). Pure structural refactoring — zero behavior changes, all endpoints/menus/UI work identically after.

### Phase 1: server.js → 156-line bootstrap + 18 modules in lib/
- Created `cache-proxy/lib/` directory with 18 route/utility modules
- Each module exports `function(app, deps)` receiving shared state
- Key modules: overpass.js (300 lines), db-pois.js (680 lines), auth.js (340 lines), tfr.js (330 lines), chat.js (240 lines), weather.js (220 lines)
- auth.js registered first (exports requireAuth used by comments.js + chat.js)
- Proxy restarted and verified between batches

### Phase 2: MainViewModel.kt → 215 lines + 6 domain ViewModels
- **SocialViewModel** (200 lines): auth, comments, chat — AuthRepository, CommentRepository, ChatRepository
- **TransitViewModel** (165 lines): MBTA trains, subway, buses, stations, bus stops — MbtaRepository
- **AircraftViewModel** (79 lines): aircraft tracking, flight history — AircraftRepository
- **FindViewModel** (86 lines): Find counts, nearby, search, POI website — FindRepository
- **WeatherViewModel** (108 lines): weather, METAR, webcams, radar refresh — WeatherRepository, WebcamRepository
- **GeofenceViewModel** (286 lines): TFR, cameras, schools, flood, crossings, databases, GeofenceEngine — TfrRepository, GeofenceRepository, GeofenceDatabaseRepository
- MainViewModel retained: Location + POI viewport (215 lines, 2 dependencies)
- Each extraction: update ViewModel refs in consumer files + MainActivity observers + DebugEndpoints constructor
- 6 successful incremental builds

### Phase 3: AppBarMenuManager.kt → 812 lines + MenuPrefs.kt (74 lines)
- Removed unused MainViewModel constructor parameter
- Extracted 35 preference key constants to MenuPrefs.kt object
- Updated 65 references across 7 consuming files
- Removed unused ContextCompat import, unused AppBarMenuManager import in MainActivityFind.kt

### Files Created (26)
- `cache-proxy/lib/*.js` — 18 route modules
- `app/.../ui/SocialViewModel.kt` — auth, comments, chat
- `app/.../ui/TransitViewModel.kt` — MBTA transit
- `app/.../ui/AircraftViewModel.kt` — aircraft tracking
- `app/.../ui/FindViewModel.kt` — Find dialog queries
- `app/.../ui/WeatherViewModel.kt` — weather, METAR, webcams
- `app/.../ui/GeofenceViewModel.kt` — geofence system
- `app/.../ui/menu/MenuPrefs.kt` — preference key constants
- `REFACTORING-REPORT.txt` — detailed refactoring report

### Files Modified (15+)
- `cache-proxy/server.js` — 3,925 → 156 lines (bootstrap only)
- `app/.../ui/MainViewModel.kt` — 958 → 215 lines (Location + POI only)
- `app/.../ui/menu/AppBarMenuManager.kt` — 879 → 812 lines (companion removed)
- `app/.../ui/MainActivity.kt` — 7 ViewModel properties, updated observers + DebugEndpoints
- `app/.../ui/MainActivityTransit.kt` — viewModel → transitViewModel
- `app/.../ui/MainActivityAircraft.kt` — viewModel → aircraftViewModel
- `app/.../ui/MainActivityFind.kt` — viewModel → findViewModel
- `app/.../ui/MainActivityWeather.kt` — viewModel → weatherViewModel
- `app/.../ui/MainActivityGeofences.kt` — viewModel → geofenceViewModel
- `app/.../ui/MainActivityDebug.kt` — multiple ViewModel refs
- `app/.../ui/MainActivityRadar.kt` — weatherViewModel + MenuPrefs
- `app/.../ui/MainActivityPopulate.kt` — MenuPrefs
- `app/.../ui/MainActivitySocial.kt` — socialViewModel
- `app/.../util/DebugEndpoints.kt` — 6 ViewModel constructor params

### Commits (3)
- `6e8fa58` Decompose server.js (3,925 → 156 lines) into 18 modules in lib/
- `6762cd5` Decompose MainViewModel.kt (958 → 215 lines) into 6 domain-specific ViewModels
- `494c112` Refactor AppBarMenuManager.kt: extract MenuPrefs.kt, remove unused viewModel param

---

## Session: 2026-03-04d (COMMERCIALIZATION.md v2.0 — Lawyer-Ready Enhancement)

### Context
Rewrote COMMERCIALIZATION.md from a 15-section technical reference (1,019 lines) into a 27-section, 3-part lawyer-ready document (1,897 lines). Goal: hand the document to an attorney who has never seen the app and have them understand the product, business model, legal risks, and give actionable advice.

### Changes Made

#### 1. COMMERCIALIZATION.md — Complete Restructure (1,019 → 1,897 lines)

**Part A — What the Lawyer Needs to Know (4 new/enhanced sections)**
- §1 Product Description: plain-English app overview, features, tech stack, audience, dev status
- §2 Revenue Model & Freemium Design: free+ads / paid tier ($2.99–$4.99/mo), Google's cut, ad revenue estimates, legal implications
- §3 Data Flow Description: what's collected, ASCII data flow diagram, what users see about each other, what third parties receive, encryption status
- §4 Executive Summary: enhanced with Likelihood column and reading guide

**Part B — Legal Analysis (6 new sections + expanded privacy)**
- §5 Finding the Right Attorney: type needed, where to find (MA Bar, SCORE, meetups), what to bring, budget, red flags, timing
- §9.7–9.10 International Privacy: GDPR, UK GDPR, 5 other jurisdictions, phased expansion roadmap
- §11 Dependency Inventory: 22 Android + 12 Node.js libraries with license + risk (flagged: osmbonuspack LGPL, JTS EPL-2.0, duck-duck-scrape ToS risk)
- §12 Ad Network Compliance: AdMob requirements, UMP consent, COPPA+ads, ad content filtering, revenue estimates
- §13 In-App Purchase & Google Play Billing: Billing Library, 15%/30% commission, post-Epic v. Google, subscription legal requirements
- §14 Tax Considerations: sales tax (Google collects), income tax, S-Corp election, quarterly estimates, deductible expenses, CPA timing
- §15 Social Media Integration: current status (none), future options, social login legal requirements
- §17 Competitor Analysis: 8 comparable apps (Google Maps, Yelp, Flightradar24, Waze, Transit, AllTrails, RadarScope, Aloft) with legal approach

**Part C — Implementation & Execution (3 new sections + expanded checklist)**
- §24 Cost Summary: updated with Google Play dev ($25), CPA ($200–500), revenue projections vs costs, breakeven analysis (~1,500–2,500 MAU)
- §25 Risk Matrix: 14 risks scored by Probability × Impact (1–5 scale), priority actions ranked by score
- §26 Specific Questions for Attorney: 17 questions in 3 tiers (Must/Should/Can defer), serves as meeting agenda
- §27 Master Checklist: expanded from 8 → 10 phases, added Monetization Setup + Future Growth phases, ~70 items

All 15 original sections preserved (renumbered, Cloud Deployment moved to Part C as §23).

### Files Modified (4)
- `COMMERCIALIZATION.md` — complete rewrite (1,019 → 1,897 lines)
- `STATE.md` — updated Commercialization section + Next Steps
- `SESSION-LOG.md` — added this session entry
- `memory/MEMORY.md` — updated commercialization references

---

## Session: 2026-03-04c (v1.5.57 — POI Coverage Expansion + Cuisine Search)

### Context
Analyzed 211K POIs — found 1,324 boat ramps, 165 massage shops, 132 tattoo shops, 131 cannabis dispensaries, and 12,800+ cuisine-tagged restaurants not reachable through Find grid or search. Also missing airports, barber shops, skateparks from Overpass scans.

### Changes Made

#### 1. 15 New Subtypes in PoiCategories.kt (138 → 153)
- Food & Drink: +Wine Shops, +Butcher Shops, +Seafood Markets
- Transit: +Airports (`aeroway=aerodrome`), +Taxi Stands
- Parks & Rec: +Boat Ramps (`leisure=slipway`), +Skateparks
- Shopping: +Barber Shops, +Massage, +Tattoo Shops, +Thrift Stores, +Vape Shops, +Cannabis
- Entertainment: +Disc Golf

#### 2. Cuisine-Aware Fuzzy Search (server.js)
- Added CUISINE_KEYWORDS set (30+ entries) + CUISINE_ALIASES for OSM spelling variants
- Search query now adds `OR tags->>'cuisine' ILIKE '%keyword%'` when cuisine keyword detected
- "pizza" finds 1,483 pizza places, "burger" finds 1,693, "mexican" finds 1,103, etc.

#### 3. ~30 New Search Keywords (server.js)
- Shopping: tattoo, barber, thrift, second hand, vape, cannabis, dispensary, massage, spa
- Food: butcher, seafood, wine shop, bbq, burger, steak, ramen, noodle, taco, donut, bagel, chicken, wings, sandwich, japanese, korean, vietnamese, greek, french, mediterranean
- Transit: airport, taxi
- Parks: boat ramp, boat launch, skatepark, skateboard
- Entertainment: disc golf, frisbee golf

#### 4. Expanded Overpass Search (PlacesRepository.kt)
- Default keys: added `craft`, `aeroway`, `healthcare`
- Category extraction: handles new keys in both parseOverpassJson instances
- POI_CATEGORY_KEYS in server.js: added `aeroway`, `healthcare`

### Files Modified (3)
- `app/.../ui/menu/PoiCategories.kt` — 15 new subtypes, new tags
- `cache-proxy/server.js` — cuisine search, 30+ keywords, CATEGORY_LABEL_TAGS, POI_CATEGORY_KEYS
- `app/.../data/repository/PlacesRepository.kt` — 3 new Overpass keys, category extraction

### Testing Needed
- [ ] Proxy restart + verify cuisine search ("pizza", "bbq", "sushi" near Boston)
- [ ] App reinstall + verify new subtypes appear in Find grid
- [ ] Verify "Boat Ramps" shows 1,324 results
- [ ] Verify "Filter and Map" works with new subtypes
- [ ] Trigger a scan to verify airports/barbers/skateparks get imported

---

## Session: 2026-03-04b (Commercialization Roadmap)

### Context
User requested a comprehensive "pathway to making this an application I can sell" — covering cloud deployment, legal structure, IP protection, user content liability, privacy compliance, content moderation, and Google Play requirements.

### Changes Made

#### 1. COMMERCIALIZATION.md (new document, ~1,019 lines)
- Researched cloud hosting (Railway, DigitalOcean, Neon, Cloudflare R2), pricing at 100/1K/10K user scales
- Legal structure: Wyoming LLC recommended ($100 formation, $60/yr)
- Insurance: Tech E&O + Cyber Liability ($1,300–$3,000/yr) — non-optional given safety data
- IP protection: copyright ($130), provisional patents ($60 each micro-entity), trademark ($350), trade secrets ($0), R8 obfuscation ($0)
- User content liability: Section 230 protections, DMCA safe harbor ($6 agent registration), Take It Down Act (May 2026 deadline)
- Privacy: CCPA/CPRA, 21+ state laws treating GPS as sensitive PI, COPPA age gate (13+), data retention schedule
- Third-party APIs: **OpenSky requires commercial license (BLOCKING)**, OSM ODbL share-alike on POI database, MBTA license review needed
- Content moderation: OpenAI Moderation API (free) + Perspective API (free), tiered auto-block/queue/approve system
- Safety data disclaimers for TFR, geofences, weather, flood zones
- Google Play: Data Safety section, prominent location disclosure, R8 release builds
- Master checklist: 8 phases, ~50 action items
- Year 1 estimated cost: $4,578–$10,955

### Files Created (1)
- `COMMERCIALIZATION.md` — full commercialization roadmap

### Files Modified (3)
- `STATE.md` — added Commercialization section + updated Next Steps
- `SESSION-LOG.md` — added this session entry
- `memory/MEMORY.md` — added commercialization reference

---

## Session: 2026-03-04 (v1.5.56 — Search Distance Sort + Filter and Map UX)

### Context
User noticed fuzzy search results were sorted by relevance (fuzzy match score) instead of distance, and the "Filter and Map" button was buried at the bottom of 200+ results. Also, tapping "Filter and Map" teleported to results centroid instead of keeping current position.

### Changes Made

#### 1. Fuzzy Search Distance Sort (server.js)
- Changed `ORDER BY (score) DESC, distance ASC` to `ORDER BY distance ASC`
- All search results now sorted nearest-first regardless of fuzzy match quality

#### 2. "Filter and Map" Button Moved to Top (MainActivity.kt)
- Moved teal button from bottom of `searchResultsList` to top (before result rows)
- Removed duplicate button from bottom of results

#### 3. Filter and Map — Keep Current Position + Adaptive Zoom (MainActivity.kt)
- Removed centroid calculation and `setCenter()` call
- Starts at zoom 18, steps back one level until at least one result is visible (min zoom 3)

### Files Modified (2)
- `cache-proxy/server.js` — ORDER BY change (1 line)
- `app/.../ui/MainActivity.kt` — button move, centroid removal, adaptive zoom (~30 lines)

---

## Session: 2026-03-03g (v1.5.55 — Module IDs + Home + About)

### Changes Made
- MODULE_ID constants in 131 source files
- Home toolbar icon: GPS center at zoom 18
- About toolbar icon: version/copyright/contact dialog

---

## Session: 2026-03-03f (v1.5.53 — Filter and Map Mode)

### Changes Made
- Teal "Filter and Map" button in Find results → exclusive map view
- enterFilterAndMapMode/exitFilterAndMapMode with scroll/zoom guards
- FIND_FILTER status line priority, radar save/restore, auto-exit on Find reopen

---

## Session: 2026-03-03e (v1.5.52 — Fuzzy Search Testing & Fixes)

### Changes Made
- Fixed gridScroll/searchScroll layout weight bugs (search results invisible)
- Header hint bar moved to title, search limit 50→200, distance expansion tuning

---

## Session: 2026-03-03d (v1.5.51 — Smart Fuzzy Search)

### Changes Made
- pg_trgm extension + GIN trigram index on pois.name
- ~80 keyword→category mappings, composite scoring, distance expansion
- SearchResponse model, rewritten /db/pois/search endpoint
- Rich 3-line result rows, 1000ms debounce, keyword hint chips
