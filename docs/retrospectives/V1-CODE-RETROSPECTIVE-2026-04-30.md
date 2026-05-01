# V1 Code Retrospective — LocationMapApp_v1.5 / WickedSalemWitchCityTour

**Date:** 2026-04-30 (Session 208)
**Branch:** master @ `d8bebeb`
**Ship target:** Aug 1, 2026 (internal) → Salem 400+ Oct 2026 peak
**Author:** Claude (Opus 4.7), with operator-requested opinions baked in
**Methodology:** Three Explore subagents in parallel (Android / web+cache-proxy+PG / build pipeline), then I verified the highest-impact claims directly before writing.

---

## Operator decisions (post-review, 2026-04-30)

After the operator read the first draft, these calls were made and have been propagated into the relevant sections below:

| # | Question | Operator decision | Effect on doc |
|---|---|---|---|
| 1 | WickedMapView V1 intent | **LIVE in V1 — primary basemap** | §11 corrected; follow-up WickedMapView audit added to sprint |
| 2 | Most common real bug source | **Geofence math / dedup** | §9 test priority confirmed; geofence-math tests stay top of list |
| 3 | `.git/` 540 MB cleanup appetite | **Stop-the-bleeding + trim what's unneeded** | §5 H5 scoped to `.gitignore` + `git rm --cached` + asset audit; no history rewrite |
| 4 | V2 contributor likelihood | **Yes — 1+ helper likely** | §14 CI / pre-commit / BUILD.md bumped up; matters for V2 onboarding |
| 5 | Daily publish-chain pain | **Minimal — muscle memory** | §4 H4 demoted to LOW; dropped from sprint plan |
| 6 | `qcis/` directory | **Throwaway → `.gitignore`** | §5 M11 resolved |
| 7 | DebugHttpServer fix approach | **Option B — `src/debug/` source set** ("debug mode doesn't exist in V1 production") | §3 B1 + §13 sprint reflect Option B |

These answers reframe the sprint plan. See §13 for the updated version.

---

## How to read this document

**Severity legend:**
- 🔴 **BLOCKER** — must fix before AAB upload to Play Store
- 🟠 **HIGH** — will bite within first 1,000 users
- 🟡 **MEDIUM** — slows future development, not user-facing
- 🟢 **LOW / NIT** — cosmetic, defer

**Confidence flags:**
- **[VERIFIED]** — I personally read the file/output and confirmed
- **[AGENT-CLAIM]** — surfaced by Explore subagent, not personally verified
- **[OPINION]** — my judgment call; you may disagree

**Author's-opinion vs. fact:** Where I express a preference (e.g. "this is the right call" / "this is a smell"), it's an opinion. Facts (line numbers, sizes, counts) are facts.

**Scope:** Android app (`app/`, `app-salem/`, `core/`), web admin (`web/`), cache-proxy + publish chain (`cache-proxy/`), Postgres schema, JVM content pipeline (`salem-content/`), repo hygiene, test posture. Out of scope: external Salem JSON pipeline (separate repo), OMEN coordination, legal/compliance content (those have their own docs).

---

## Table of contents

1. [TL;DR — Ship-readiness verdict](#tldr)
2. [What's going well (calibration)](#whats-going-well)
3. [BLOCKERS for V1 ship](#blockers)
4. [HIGH severity](#high)
5. [MEDIUM severity](#medium)
6. [LOW / NIT](#low)
7. [Resilience scorecard](#resilience)
8. [V1 no-network rule audit](#no-network)
9. [Test coverage reality check](#tests)
10. [Build pipeline + publish chain](#pipeline)
11. [Architecture observations](#architecture)
12. [Things I might be wrong about](#wrong)
13. [What I'd do with one focused week](#sprint)
14. [Looking forward (V2 thoughts)](#v2)
15. [Operator-side blockers (non-code)](#operator)

---

<a name="tldr"></a>
## 1. TL;DR — Ship-readiness verdict

**Honest verdict:** **Code is closer to ship-ready than the test-coverage delta suggests, but you have one real BLOCKER that will get you rejected from the Play Store as currently coded** — `DebugHttpServer` listens on port 4303 in release builds with no `BuildConfig.DEBUG` guard ([VERIFIED] `SalemMainActivity.kt:615`, `DebugHttpServer.kt` lives in `src/main/`, no `src/debug/` source set exists). Fix this before the AAB upload, full stop.

Beyond that, the codebase is in better shape than the carry-forward churn implies. The V1 no-network defense is genuinely well-engineered — three independent layers, intentional, R8-friendly. Schema versioning has survived five Room bumps without data loss. Session-log discipline is rare in solo projects. APK size (78 MB AAB) is well under the cap. The lint tab + suppressions infrastructure shipped in S187 is the kind of tool most projects never build.

The bad news: there's effectively zero automated test coverage (5 tests, 1 file, covering 1 class). The build pipeline is held together by operator memory — 4 publish scripts in strict order, no Gradle wrapper, no pre-commit guard. The `.git/` directory is 540 MB because asset binaries are tracked in history. `SalemMainActivity` is 4,410 lines and the "Activity bag" pattern across 17 files totals 17,443 LOC ([VERIFIED] via `wc -l`). One `SimpleDateFormat` race or one missed null check on a low-end phone and the first-week reviews start hurting.

**Top 5 things that matter (ranked):**
1. 🔴 Wrap `DebugHttpServer.start()` in `BuildConfig.DEBUG` or move to a `src/debug/` source set. Single biggest ship risk.
2. 🟠 Replace `!!` assertions in walk-sim hot paths (real, e.g. `activeTour!!.tour.name` at `SalemMainActivity.kt:1605/1610` [VERIFIED]).
3. 🟠 Wrap the 4-script publish chain as a Gradle `preBuild` task — closes S189 carry-forward #9, eliminates the #1 footgun on every build.
4. 🟠 Add unit tests for `NarrationGeofenceManager.checkPosition` — pure-function geofence math is the heartbeat of the app and the easiest possible test target.
5. 🟡 Audit and clean `.git/` — 540 MB is abnormal for a 4-year, 415-commit repo. Asset binaries are tracked.

**What's actually working well:** Hilt DI is clean. Coroutine usage is mostly correct (walkMutex pattern in particular is solid). `OfflineModeInterceptor` enforces V1 rule at OkHttp transport layer with `FeatureFlags.V1_OFFLINE_ONLY = const val true` ([VERIFIED] `core/.../FeatureFlags.kt:39`) so R8 inlines and dead-code-eliminates the disabled branches. INTERNET + ACCESS_NETWORK_STATE permissions are commented out in the manifest [VERIFIED]. Schema alignment scripts now read Room identity hash dynamically [VERIFIED] `cache-proxy/scripts/publish-tour-legs.js:50-70`.

---

<a name="whats-going-well"></a>
## 2. What's going well (calibration)

A retrospective that's all bad news is useless because you can't tell what to keep. Here's what to **not** change:

### V1 no-network rule — three-layer defense (genuine model code)

This is the best-engineered part of the codebase and worth bragging about:

1. **Compile-time:** `FeatureFlags.V1_OFFLINE_ONLY = const val true` ([VERIFIED] `core/src/main/java/com/example/locationmapapp/util/FeatureFlags.kt:39`). `const val` means kotlinc inlines the boolean at every call site. R8 then dead-code-eliminates the entire disabled branch in release builds. The compiled bytecode literally does not contain the network paths.

2. **Runtime:** `OfflineModeInterceptor` is installed as the first interceptor in every OkHttpClient builder. Throws `OfflineModeException` before the socket opens. Belt to the suspenders.

3. **Manifest:** INTERNET and ACCESS_NETWORK_STATE permissions removed [VERIFIED] (`AndroidManifest.xml:20`, S180 comment). Even if a leak slipped past the first two layers, the Android runtime rejects the syscall.

This is how you do feature flags right. Use this pattern for any future kill-switch.

### Schema versioning — five bumps without data loss

Room v10 → v15 over ~30 sessions. The `align-asset-schema-to-room.js` script + `verify-bundled-assets.js` preBuild gate caught real regressions (S185 DEFAULT-clause drift, S192 hardcoded-hash near-miss). The fact that you're on v15 with zero shipped wipes is non-trivial.

### Session-log discipline + crash recovery

207 live session logs (`docs/session-logs/`). Append-only. Survives context overflow. The "live log + thin OMEN report + thin SESSION-LOG.md entry" protocol is more rigorous than what most teams do. The 25K-line cumulative log is its own form of documentation.

### Lint tab infrastructure (S187/S188)

`cache-proxy/lib/admin-lint.js` + `web/src/admin/LintTab.tsx` + `salem_lint_suppressions` table. ~15 instant data-quality checks + 1 on-demand. Suppression workflow for false positives. Most solo projects never build this. It's the right tool to drive the V1 content-cleanup carry-forwards.

### Hilt DI + Singleton scoping

`@Singleton` correctly placed (`NarrationGeofenceManager` survives config changes — S110 fix), no circular dependencies, no ServiceLocator antipatterns. Coroutine cancellation via `lifecycleScope` is correct.

### APK size discipline

78 MB AAB ([AGENT-CLAIM] but plausible — total tracked assets including binaries ≈ 50 MB by my count). Well under Play's 150 MB cap. `noCompress += ['sqlite']` is a sharp move — keeps tile DB queryable without decompression.

### `walkMutex` pattern

`SalemMainActivity.kt:415-419` — `withLock` around walk-sim start/cancel prevents race between UI tap and HTTP-triggered walk start. This kind of thinking is what's missing from the rest of the activity bag, but credit for getting it right where it matters.

### Single signing key + off-machine backup discipline

USB backup at `/media/witchdoctor/writable/wickedsalem-upload-key-backup-...`, off-repo `~/.gradle/gradle.properties` mode 0600. Second-medium backup is a known carry-forward. The discipline is real.

---

<a name="blockers"></a>
## 3. 🔴 BLOCKERS for V1 ship

### B1. DebugHttpServer ships in release builds [VERIFIED]

**File:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:615`
**Server file:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/util/DebugHttpServer.kt`

```kotlin
// Line 615 — inside onCreate()
DebugHttpServer.start()
```

There is no `if (BuildConfig.DEBUG)` guard around this call. The server file lives in `src/main/`, not `src/debug/`. I verified the source-set layout — `app-salem/src/` contains only `main/` and `test/`. There is no `debug/` source set. The code ships to release.

`DebugHttpServer` listens on port 4303, accepts HTTP requests, and (per the docstring at `DebugHttpServer.kt:25-30`) routes to `DebugEndpoints` which exposes walk-sim control, GPS spoofing, state dumps, and arbitrary preference toggles. While the server only binds to localhost via `ServerSocket(PORT)` (which on Android binds to all interfaces by default — needs verification), this is exactly the kind of "exposed control surface in release" that Google Play's automated reviewers flag and that security researchers eat for breakfast.

**Why it blocks:**
1. Play Store review may auto-flag a listening TCP server as suspicious.
2. Even if it passes review, any process on the device can connect to localhost:4303 and control the app. On a rooted device or with a malicious app installed, this is full app compromise.
3. The R8/ProGuard config is unlikely to strip `DebugEndpoints` because `DebugHttpServer.start()` is called unconditionally — the entire class graph is reachable.

**Fix sketch (1 hour):**

Option A — minimal:
```kotlin
if (BuildConfig.DEBUG) {
    DebugHttpServer.start()
}
```
Plus matching guards at lines 891 (endpoints assignment) and 921-922 (stop). Verify R8 then strips the dead branch.

Option B — proper (recommended):
- Move `DebugHttpServer.kt`, `DebugEndpoints.kt`, and any `dev-only` endpoints into `app-salem/src/debug/java/...`
- Create thin no-op stubs in `app-salem/src/main/` so call sites compile without conditional logic
- This guarantees release builds don't even compile the code, eliminating reflection and class-graph risks

**Effort:** 1 hour for Option A, 2 hours for Option B. Option B is the right answer.

**Verify before action:** Confirm I'm reading the source-set layout correctly. Run `find app-salem/src -type d -maxdepth 2`. If you have a `debug/` source set I missed, recheck whether `DebugHttpServer.kt` is in `main/` or `debug/`.

---

### B2. (None other surfaced — but verify before AAB)

I looked specifically for: hardcoded API keys, embedded credentials, OAuth client secrets, Firebase config files, missing ProGuard rules, exposed `BuildConfig` constants. Found none. The V1 no-network rule audit (Section 8) is solid.

**One thing to verify before AAB:** Run `./gradlew :app-salem:bundleRelease` and `unzip -l <aab>` to inspect what actually ships. Particularly check `assets/` for any developer notes, `.env` snippets, or test fixtures that snuck in.

---

<a name="high"></a>
## 4. 🟠 HIGH severity

### H1. `!!` null assertions in walk-sim hot paths [VERIFIED at sample sites]

`SalemMainActivity.kt:1605, 1610` — `activeTour!!.tour.name` inside the `tourIsActive ->` branch. The `tourIsActive` check guards against `TourState != Active`, but `activeTour` itself is a separate variable that could be null even when `tourIsActive` is true (different observation timing). Concretely, between the `when` evaluation and the body, a `cancelAnyWalk()` could race in.

The agent counted 28 `!!` "in critical paths." Actual count in `SalemMainActivity.kt` alone is 13 [VERIFIED via `grep -c`]. Agent number was inflated. But 13 is still 13 and they're in the riskiest file in the codebase.

**Why it bites:** First time a low-end phone gets a memory pressure GC right between the state check and the body, you get an NPE in production. Crash reports show "kotlin.KotlinNullPointerException at SalemMainActivity.kt:1605" — vague stack, hard to reproduce, costs you a review.

**Fix sketch (4 hours):**
- For each `activeTour!!.X` — replace with `activeTour?.X ?: run { Log.w(...); return }` or `activeTour?.let { ... } ?: return`
- For `ContextCompat.getDrawable(...)!!` ([AGENT-CLAIM] in `MarkerIconHelper.kt:407, 662, 821, 990`) — replace with `requireNotNull(...) { "drawable missing" }` or fall back to a default marker
- For `SimpleDateFormat.parse()!!` (date parsing in weather/transit; [AGENT-CLAIM]) — wrap in try/catch with sensible fallback

[OPINION] This is the highest reliability-per-hour fix on the list. Do it second after the BLOCKER.

---

### H2. No process-death recovery for active tours / dedup state lost [AGENT-CLAIM, plausible]

`NarrationGeofenceManager.kt` — `narratedAt` and cooldown sets live in RAM. If Android kills the process (memory pressure, swipe-away, battery optimizer), all dedup is lost. User resumes, walks back into the same geofence, hears the same narration twice.

This is acknowledged as a S110 carry-forward — SharedPreferences mirror was planned and not implemented.

**Why it bites:** Real users will swipe-close the app between visits. When they reopen and walk by Hawthorne Hotel for the second time that afternoon, they'll hear the narration twice. Won't crash the app — just feels janky.

**Fix sketch (3-4 hours):** SharedPreferences-backed `Set<Pair<poiId, timestamp>>` flushed on every dedup add, loaded in `init`. Existing 1-hour expiry logic stays unchanged.

[OPINION] Lower priority than H1 because non-fatal, but you'll get one-star reviews specifically about "it talked about the same place twice" if you don't fix it.

---

### H3. LruCache tile sizing is not memory-aware [AGENT-CLAIM]

`TileArchive.kt:25-26` — `LruCache<String, Bitmap>(256)` with `sizeOf() = 1`. All bitmaps count as 1 unit regardless of memory consumption. 256 × 256x256 PNG = ~6 MB if 24-bit, more if 32-bit (alpha).

**Why it bites:** minSdk 26 = Android 8.0. Budget phones from 2017-2018 have <100 MB heap available to a single app. If the user pinches and pans aggressively, tile cache can OOM.

**Fix sketch (1 hour):** Override `sizeOf(key, bitmap) = bitmap.byteCount / 1024` (KB units), set cache to `min((Runtime.getRuntime().maxMemory() / 8).toInt(), 16 * 1024)`. Standard Android pattern.

[OPINION] You won't hear about this until a 1-star review says "crashes on my old phone." Cheap insurance.

---

### H4. ~~Publish chain has no Gradle wrapper / order enforcement~~ → **DEMOTED to LOW** per operator (post-review)

> **Operator decision (post-review):** Daily friction is minimal — muscle memory at 200+ sessions. Demoted to LOW; defer to V2. Original analysis kept below for completeness.

**Original analysis (now LOW):**

[VERIFIED — S189 carry-forward, 18+ sessions stale]

The 4-script chain (`publish-salem-pois` → `publish-tours` → `publish-tour-legs` → `align-asset-schema-to-room`) must run in order. There is no Gradle task wrapping it. There is no manifest file. Operator runs them by hand.

Failure mode: if step 3 runs before step 2, step 2's full-table-replace clobbers what step 3 just wrote. The next `assembleDebug` produces an APK with a partially-empty asset DB. `verify-bundled-assets.js` *does* catch row-count mismatches at preBuild, so the build fails — but the operator just lost 5-10 minutes finding out.

**Why it bites:** Every single build cycle. After 200+ sessions, the ergonomic cost is real — and the day you do it under stress (the night before AAB upload), one missed step turns into a multi-hour debug.

**Fix sketch (1 hour):**
```groovy
// app-salem/build.gradle
task publishContent(type: Exec) {
    workingDir '../cache-proxy'
    commandLine 'node', 'scripts/publish-all.js'  // new wrapper
    inputs.dir '../cache-proxy/scripts'
    outputs.file '../app-salem/src/main/assets/salem_content.db'
}
preBuild.dependsOn publishContent
```
Plus `publish-all.js` that runs the 4 in order with proper exit-code chaining and a stale-bake timestamp check.

[OPINION] You've called this S189 carry-forward #9 for 18 sessions. Just do it. It pays back in the first week.

---

### H5. Asset binaries tracked in git → 540 MB `.git/` [VERIFIED]

```
salem_tiles.sqlite         30,580,736 bytes
salem-routing-graph.sqlite 10,895,360 bytes
salem_content.db            6,017,024 bytes
salem_1692_newspapers.json    948,831 bytes
polygons.json                 602,799 bytes
... 215 tracked asset files total
.git/                        540 MB
415 commits over ~4 years
```

The asset DB and tiles are rebaked every session. Each rebake produces a fresh blob. Git stores all blobs forever. 540 MB is the cumulative weight of every historical bake.

**Why it bites now:** Slow clones for any new contributor (or you on a new machine). Disk waste. Backup costs.

**Why it bites later:** If you ever need to migrate hosting (GitHub LFS, GitLab, self-hosted), 540 MB is a slog. If you publish open-source even partially, you embarrass yourself.

**Fix sketch (1-2 hours, slightly destructive):**
1. Add to `.gitignore`:
   ```
   app-salem/src/main/assets/salem_content.db
   app-salem/src/main/assets/salem_tiles.sqlite
   app-salem/src/main/assets/routing/salem-routing-graph.sqlite
   ```
2. `git rm --cached` the three big binaries.
3. Document the bake-from-source workflow in `cache-proxy/README.md` so a fresh clone can regenerate.
4. (Optional, more invasive) `git filter-repo` to remove historical blobs. **Coordinate carefully** — rewriting history breaks anyone else's clones. Solo project, so probably fine, but back up first.

[OPINION] Step 4 is destructive and you should think about it before doing it. Steps 1-3 are safe and stop the bleeding.

---

<a name="medium"></a>
## 5. 🟡 MEDIUM severity

### M1. `SalemMainActivity` is 4,410 LOC + 13K LOC of extension files = 17,443 LOC effectively a god class [VERIFIED]

```
SalemMainActivity.kt           4,410
SalemMainActivityFind.kt       2,296
SalemMainActivityTour.kt       2,058
SalemMainActivityNarration.kt  1,750
SalemMainActivityTransit.kt    1,034
SalemMainActivityGeofences.kt  1,016
... 11 more files
TOTAL                         17,443
```

Splitting into `SalemMainActivity*.kt` extension files improves edit ergonomics but doesn't change the underlying coupling — they're all extension functions on the same Activity class with shared state. Testing this is functionally impossible. Refactoring it during V1 ship is too risky.

**Why MEDIUM not HIGH:** Not user-facing. Operator can ship it. But every future bug fix in this surface costs 2x what it should.

**Fix sketch (V1.1 sprint):** Extract feature areas (`Find`, `Transit`, `Aircraft`, `Weather`, `Radar`, `MBTA`, `Webcams`) into proper Fragment + ViewModel pairs. The `app/` module already has proper `*ViewModel` classes — adopt them, delete the extension files. Probably 2-week refactor with regression risk.

[OPINION] Don't touch this before V1 ship. After Aug 1, this should be V1.1 priority #1.

---

### M2. Frontend useState soup in LintTab [AGENT-CLAIM]

`web/src/admin/LintTab.tsx` has 8+ `useState` calls. No reducer, no context. Race conditions on concurrent fetches are a real risk.

**Fix sketch:** `useReducer` with a single state shape, or migrate to React Query / SWR for the data-fetching half. 4 hours.

[OPINION] You're a single user of the admin tool. The actual user-facing risk is near-zero. Defer indefinitely.

---

### M3. `SimpleDateFormat` thread safety [AGENT-CLAIM]

`SimpleDateFormat` is not thread-safe. If it's instantiated outside a function and called from multiple coroutines, results corrupt. `SalemMainActivityWeather.kt`, `Transit.kt` cited.

**Fix sketch:** Replace with `java.time.format.DateTimeFormatter` (immutable, thread-safe). 1 hour.

[OPINION] Real bug, low frequency, low impact. V1.1.

---

### M4. Hardcoded local-server IP `10.0.0.229:3000` [AGENT-CLAIM, `SalemMainActivityFind.kt:2158`]

OfflineModeInterceptor blocks the call. But the URL string sits in code as a developer artifact. Smell, not a bug.

**Fix sketch:** Either delete the call site (V1 doesn't need it) or move to a `BuildConfig` field. 15 min.

---

### M5. Soft-delete inconsistency in regen log [AGENT-CLAIM]

`salem_narration_regen_log` has no FK to `salem_pois`. Deleted POIs leave orphan log rows. Lint tab may show ghost issues.

**Fix sketch:** Add `ON DELETE CASCADE` (or filter by JOIN against active POIs). 30 min.

---

### M6. Missing index on `salem_pois(deleted_at)` [AGENT-CLAIM]

Every admin list and lint check filters `WHERE deleted_at IS NULL`. At 5K rows + 60 columns, table scans add up.

**Fix sketch:**
```sql
CREATE INDEX idx_spois_active ON salem_pois (id) WHERE deleted_at IS NULL;
```
Partial index, cheap. 5 min.

---

### M7. `sharp` native binary fragility [AGENT-CLAIM]

`cache-proxy/package.json` requires `sharp@^0.34`. If you redeploy on a different libc, sharp fails to load. Tile auto-overzoom dies.

**Fix sketch:** Document the build env in `cache-proxy/README.md`. Add a smoke-test on cache-proxy startup that verifies sharp loaded. 30 min.

[OPINION] Single-deployer (you, on this Linux box). Real risk is zero until you ever move hosts. Defer.

---

### M8. Background geocode jobs not persistent [AGENT-CLAIM]

Lint tab address-geocode deep scan tracks state via React `useRef`. Browser crash → orphan job.

**Fix sketch:** Persist job state to PG (`salem_lint_jobs` table). Resume on tab open. 2 hours.

[OPINION] Defer.

---

### M9. ODT lock files in git status [VERIFIED]

```
?? docs/.~lock.V1-LAUNCH-REVIEW-2026-04-27.odt#
?? docs/session-logs/.~lock.session-192-narrations-review-round3.odt#
?? docs/session-logs/.~lock.session-192-progress-status.odt#
```

LibreOffice temp files. Should never be in source tree.

**Fix sketch:**
```
# .gitignore
.~lock.*
*.odt#
```
+ delete the offending files. 5 min.

---

### M10. Hardcoded `~/Development/Salem` in salem-content [AGENT-CLAIM]

CLI default path. Works on your box. Won't work on CI or another machine.

**Fix sketch:** Read from env var with a fallback default. 15 min.

---

### M11. `qcis/` directory untracked [VERIFIED]

Untracked directory in working dir. Either commit it, archive it, or `.gitignore` it. Don't leave it as undecided.

---

<a name="low"></a>
## 6. 🟢 LOW / NIT

- **Unused dependencies:** Socket.IO is in BOTH `core/build.gradle:75` AND `app-salem/build.gradle:203` [VERIFIED]. Agent claimed only `core/`. Verify it's actually used somewhere — `grep -rn "import io.socket"` — and if not, drop it from both. Likely 200KB+ APK savings.
- **Retrofit dep:** Used in `app/`, not `app-salem/`. Could be excluded from `app-salem` deps if separated. Marginal.
- **Console.error without request IDs** — request correlation in cache-proxy logs is hard. Add a UUID-stamping middleware. 30 min.
- **No `.gitattributes`** — line endings could drift if Windows ever joins. Single-developer Linux-only project, so meh.
- **Minimal `gradle/libs.versions.toml`** — most deps are hardcoded in module `build.gradle` files. Centralizing is hygienic but not blocking.
- **Pre-baked tile path is read at runtime via `Runtime.getRuntime()` rather than `BuildConfig`** — works, but smells.
- **`@Volatile var endpoints: DebugEndpoints?`** in `DebugHttpServer.kt:38` — public mutable global. Functional, but a smell. Same `BuildConfig.DEBUG` guard fixes it by side effect.

---

<a name="resilience"></a>
## 7. Resilience scorecard

| Scenario | Handling | Grade | Notes |
|---|---|---|---|
| Asset DB missing at launch | OfflineTileManager catches, logs, blank map | B | App keeps running |
| GPS dead (no fix) | Walk-sim won't start; map shows last-known | B | Acceptable |
| Tile bake stale (size mismatch) | Size-based versioning re-extracts | A- | Strong |
| TTS not ready when narration triggered | SplashVoice.initEarly() warms engine; behavior if still unready unclear | C | Untested |
| Low memory (OOM) on tile pan | LruCache `sizeOf()=1` not memory-aware | D | Real risk on budget phones |
| App backgrounded mid-tour | Walk-sim cancels; no resume; dedup state lost | C | H2 above |
| Network call attempted with V1_OFFLINE_ONLY=true | OfflineModeInterceptor throws; manifest blocks | A+ | Three layers, model code |
| Permissions denied (location) | App handles, GPS dead, map stale | B | OK |
| Permissions denied (camera) | ReconCaptureActivity asks, isolated | A- | Good |
| SQLite corruption mid-run | Room's WAL recovery handles transient; no detection of permanent corruption | C | Acceptable for read-only asset DB |
| Publish-chain partial failure | Each script atomic via better-sqlite3 transactions; chain not transactional | C | H4 above |
| Lenovo motion-sensor stuck (`isStationary=true` forever) | Documented, derived-speed escape hatch shipped | B+ | S204 fix |
| Process killed mid-walk | Walk-sim gone; no resume mechanism | C | Acceptable for V1 |
| App reopen after 1+ hour | Dedup naturally clears; tour state lost | B | Per design |
| Concurrent admin edits (two browsers) | No optimistic locking; last write wins | C | Theoretical for single-operator |
| Cache-proxy crash / restart | No PID supervisor; manual restart | C | Single-host, single-operator |
| `sharp` native lib fails to load | Tile overzoom dies silently | C | M7 above |

**Overall resilience grade: B-**. Strong on the things you obsessed over (V1 no-network, tile asset versioning, signing key discipline). Weak on the things you assume "won't happen on my phone" but will happen on your users' phones (low-RAM OOM, !! crashes, narration repeats after process death).

---

<a name="no-network"></a>
## 8. V1 no-network rule audit — DETAILED

[OPINION] This is the part of the codebase I'd hold up as an example to other teams. Three independent layers, each sufficient on its own:

### Layer 1: Compile-time (the strongest)
`core/src/main/java/com/example/locationmapapp/util/FeatureFlags.kt:39` [VERIFIED]:
```kotlin
const val V1_OFFLINE_ONLY: Boolean = true
```
Because this is `const val`, kotlinc inlines `true` at every call site. R8 in release builds eliminates the dead branch entirely. Bytecode in release does not contain the network paths.

### Layer 2: Runtime (the safety net)
`OfflineModeInterceptor` is installed first in every OkHttpClient. Throws `OfflineModeException` before the socket opens. If a future developer flips a flag to runtime-settable and forgets to inline, this catches it.

### Layer 3: Manifest (the OS-level enforcement)
`AndroidManifest.xml:20` [VERIFIED]: INTERNET and ACCESS_NETWORK_STATE removed (commented with "S180" tag). Even a successful socket-call would fail the SecurityException check at the OS layer.

### Risks (small):
- **Layer 1 erodes if anyone ever changes `const val` to `var` for V2.** Document this in a `// DO NOT CHANGE WITHOUT PROPAGATING` comment on the FeatureFlags object.
- **Hardcoded URLs in code (`10.0.0.229:3000`, webcam.previewUrl) are dead paths but smell.** They're protected by Layer 2/3, but a careless V2 refactor could re-enable Layer 1 while leaving these reachable. Delete them now (M4).
- **GPS subsystem is not "no-network" in the strict sense** — it talks to satellites and (on Android) may fall back to network-assisted A-GPS. This is fine because it's an OS-managed system service, not a runtime call from your app, but if a paranoid reviewer asks, that's the answer.

### Verdict: ✓ Solid. No action needed. Just don't break it in V2.

---

<a name="tests"></a>
## 9. Test coverage reality check

[VERIFIED]: 5 `@Test` methods total across `app-salem/src/test/`. One file: `NarrationGeofenceManagerTourModeTest.kt`. Tests `setTourMode` state transitions.

Plus: `RouterParityTest.kt` in `routing-jvm` (parity between JVM and native router; not exercised in V1 since on-device routing is in use). `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt` in `app/` are placeholder scaffolding.

**Effective test count for V1 product code: 5 tests, 1 class.**

### Why this is less alarming than it sounds
- The geofence math (`NarrationGeofenceManager.checkPosition`) is a pure function over `(LatLng, List<Poi>) → Set<Trigger>`. It's the easiest possible test target. It's also the heartbeat of the app — if this breaks, narration breaks.
- Walk-sim, tour state machine, TTS queue — these are async + Android-framework-dependent. Real tests need an emulator and instrumented test infra. Cost-benefit is poor for a single-operator pre-V1 push.

### Why it's still worth doing
- Geofence math tests are 4-6 hours of work. They will catch the next regression that you don't notice on Lenovo because Lenovo's GPS is mid-Salem and you don't test edge cases.
- Hilt-based testability means you can mock the Room DAO and exercise the manager without an emulator.

### My recommended test priority (do in this order):
1. **`NarrationGeofenceManager.checkPosition()` parameterized test** — distance bucket boundaries (entry/approach/exit), dedup window, detail-level filtering, civic+commemorative override interactions. ~6 hours.
2. **Walk-sim lifecycle test** — start, pause, resume, cancel, mutex behavior. Requires more mocking but the logic is critical. ~4 hours.
3. **Tour-mode flag interaction test** (`setTourMode(active, allowHistLandmarks, allowCivic)` already has 5 tests; add edge cases for tour-mode-off plus civic-on, etc.). ~2 hours.
4. **Tile archive integrity test** — missing asset, corrupted SQLite, wrong identity hash. ~3 hours.
5. **Migration test for Room** — actually low value. You don't migrate; you destructively-recreate. Skip.

### What I would *not* test:
- The Activity bag (would require Robolectric or instrumented test infra; too expensive)
- TTS pipeline (Android system service; mock-only would be hollow)
- Cache-proxy admin endpoints (can be exercised manually; integration risk is low for single-operator workflow)

[OPINION] Six hours of geofence-math tests is the highest test ROI in the project. Do it.

---

<a name="pipeline"></a>
## 10. Build pipeline + publish chain — what to harden

### Current state (as of S207, [VERIFIED])

Operator runs, in order, by hand:
1. `node cache-proxy/scripts/publish-salem-pois.js`
2. `node cache-proxy/scripts/publish-tours.js`
3. `node cache-proxy/scripts/publish-tour-legs.js` ([VERIFIED] reads identity hash dynamically from latest schema JSON, lines 50-70)
4. `node cache-proxy/scripts/align-asset-schema-to-room.js`
5. `./gradlew :app-salem:assembleDebug`
   - `verify-bundled-assets.js` runs at `preBuild`, checks identity hash + min row counts, ~1s cost

If any one of steps 1-4 is skipped or runs out of order, the next `assembleDebug` fails (or worse, ships a partial-wipe DB that triggers `fallbackToDestructiveMigration` on the device).

### What's good
- Each individual script is atomic (better-sqlite3 transactions).
- `verify-bundled-assets.js` is the safety net at the build boundary. Catches stale identity hash, missing rows.
- Schema JSON files are git-tracked under `app-salem/schemas/`. Auditable history.
- `align-asset-schema-to-room.js` reads schema dynamically — no hardcoded hash.

### What's fragile
- The 4-script chain is coordinated by operator memory.
- No manifest file or timestamp check enforces order.
- `verify-bundled-assets.js` runs *after* Gradle starts — by the time it fails, you've already invoked the build pipeline and waited for KSP.
- No CI runs anywhere in the system. Solo operator → solo verification.

### Highest-leverage fix (1 hour)

Wrap the 4 scripts as a Gradle `preBuild`-dependent task:

```groovy
// app-salem/build.gradle (add at bottom)
task publishContent(type: Exec) {
    workingDir rootProject.file('cache-proxy')
    commandLine 'node', 'scripts/publish-all.js'
    inputs.dir file('../cache-proxy/scripts')
    inputs.dir file('../cache-proxy/lib')
    outputs.file file('src/main/assets/salem_content.db')
    outputs.file file('src/main/assets/routing/salem-routing-graph.sqlite')
}
preBuild.dependsOn publishContent
```

`publish-all.js` is a thin wrapper that runs the 4 scripts in order, fails on any non-zero exit, writes a `.publish-manifest` timestamp. Gradle's `inputs/outputs` declaration means the chain only re-runs when the source scripts change — fast incremental builds.

**Why this is the single most valuable pipeline change:**
- Eliminates skip-a-step-by-mistake risk
- Makes `./gradlew assembleDebug` self-contained from a fresh clone (after `npm install` in cache-proxy)
- Closes S189 carry-forward #9 in 1 hour after 18 sessions of being on the list

### Other pipeline thoughts (lower priority)
- **Pre-commit hook for `@Database(version = N)` bumps:** if a Kotlin file with `@Database(version = N)` is modified, ensure `app-salem/schemas/*/N.json` exists. 2 hours.
- **A real CI:** GitHub Actions workflow with `kspDebugKotlin` + `verifyBundledAssets` + Kotlin compile. ~2 hours setup, but the value is mostly when you eventually onboard another contributor. For solo, defer.
- **Asset-source-of-truth simplification:** consider keeping only `salem-content/salem_content.db` in git (or neither, if `publishContent` Gradle task generates from source) and stop committing `app-salem/src/main/assets/salem_content.db`. Reduces double-storage in `.git/`.

---

<a name="architecture"></a>
## 11. Architecture observations

### Multi-module structure: clean
`:core` / `:app` / `:app-salem` / `:salem-content` / `:routing-jvm` — sensible boundaries. `:core` is the shared base. `:app` is the generic reference. `:app-salem` is the V1 ship target. Inter-module deps look right.

### Hilt DI: clean
`@Singleton` placement is appropriate. `NarrationGeofenceManager` correctly @Singleton'd in S110 to survive config changes. No ServiceLocator antipatterns. No circular deps.

### Coroutines: mostly correct
- `lifecycleScope` for UI work — right.
- `walkMutex` — pleasantly correct.
- Coroutine cancellation in `onDestroy` is implicit via lifecycleScope, but explicit `job?.cancel()` would be clearer for the walk-sim job.
- Some `SimpleDateFormat` usage on hot paths is thread-unsafe (M3).

### Room: working
- Schema version 15.
- `exportSchema = true`.
- `fallbackToDestructiveMigration` is the strategy because the asset DB is read-only post-install. This is correct for the data model.
- No on-device migrations. No `@Migration` classes needed.

### The custom WickedMapView (S171+) — LIVE V1 basemap [CORRECTED post-review]

> **Correction:** My agents read the FAB-launch path and concluded WickedMapView was parked R&D. Operator confirmed it's the **primary V1 basemap**, not osmdroid. My agent-driven assessment of this surface is therefore incomplete and the architecture analysis below is provisional.

**What this means for the retrospective:**
- The `app-salem/.../wickedmap/` engine is production code, not parked.
- It needs the same audit treatment I gave the rest of `app-salem` — !! assertions, OOM behavior on tile load, Canvas/Bitmap lifecycle, animation thread safety, polygon-rendering performance on Lenovo, fallback behavior when tiles are missing.
- "Each unused engine is dead code" — does not apply. osmdroid may still be present for backward compat or directions overlays; if it's truly dead, *that's* the dead-code surface to strip.

**Action item (added to sprint, §13):** Run a follow-up Explore agent specifically on `wickedmap/` to surface findings I missed. Until that's done, treat the BLOCKER / HIGH list as Android-app-minus-WickedMap. There may be additional findings.

**[OPINION] My initial read of the strategy:** Replacing osmdroid with a custom engine is a brave call for V1. The payoff (no LGPL/GPL dep, full control over rendering, offline-only by construction, animation freedom for the "lively map" memory rule) is real. The risk (custom code is custom code; you own all the bugs) is also real. Once the audit is done, you'll have a better sense of how much technical debt the call actually loaded onto V1.

### The Activity-bag pattern (M1)
Already covered. Smell, not a crime. Defer.

### Witch Trials separate three-table model
`witch_trials_articles` / `witch_trials_npc_bios` / `witch_trials_newspapers` plus a multi-script bundling pipeline. Different from the unified `salem_pois` model. [OPINION] Reasonable — Witch Trials content has different cardinality and editorial workflow. The complexity is justified by the data model.

### Soft-delete pattern
`deleted_at IS NULL` filter applied throughout admin endpoints. Generally consistent. M5 is the lone gap.

### `salem_pois` schema (60+ columns)
Wide table. Includes `is_tour_poi`, `is_civic_poi`, `is_historical_property`, `is_historical_tour`, etc. Multiple boolean flags is a smell that often becomes a normalization candidate, but for a single canonical object with stable categorical attributes, it's fine.

---

<a name="wrong"></a>
## 12. Things I might be wrong about — RESOLVED post-review

Items 1, 3, 5, 6, 7, 8 below were confirmed or corrected by the operator after first read. Marked inline. Items 2 and 4 remain open.

[OPINION] Pushback welcome on any of these — they're judgment calls more than facts:

1. ~~**DebugHttpServer might be disabled some other way I missed**~~ — **RESOLVED:** Operator confirmed "debug mode doesn't exist in V1 production" and chose Option B (move to `src/debug/` source set). BLOCKER stands. Verification step (unzip + grep dex) still recommended after the fix.

2. **Geofence math test priority** — **CONFIRMED:** Operator confirmed geofence/dedup is the #1 real-world bug source. Priority stands; this is the highest test ROI.

3. ~~**WickedMapView as parked R&D**~~ — **CORRECTED:** Operator confirmed it's the LIVE V1 primary basemap. My agent reports were incomplete on this surface. See §11 correction and sprint task #2.

4. **Concurrent-edit race conditions in admin** — Still single-operator today. If a remote agent edits via admin in V2, becomes real. Treat as V2 onboarding-bundle item.

5. ~~**540 MB `.git/` is "abnormal"**~~ — **CONFIRMED:** Operator wants to stop the bleeding (`.gitignore` + `git rm --cached`) plus trim unneeded tracked binaries. No history rewrite.

6. ~~**17,443-LOC Activity bag is a smell**~~ — **DEMOTED:** Operator finds it manageable; the extension-file split is enough. Stays MEDIUM, defer to V1.1+. Don't bump.

7. ~~**Single-operator project doesn't need CI**~~ — **REVERSED:** Operator confirmed 1+ helper likely in V2. CI moves up to V2 onboarding bundle.

8. ~~**Sprint priorities are biased toward "fix things I noticed first."**~~ — **ADJUSTED:** Operator confirmed publish-chain pain is minimal (muscle memory). Demoted H4 → LOW. Sprint reordered: BLOCKER → WickedMap audit → !! cleanup → geofence tests → LRU sizing → dedup mirror.

---

<a name="sprint"></a>
## 13. What I'd do with one focused week (UPDATED post-review)

Sprint plan, reordered after operator decisions. Total ~22 hours. Doable in a focused week with operator-validation breaks:

1. **🔴 Fix DebugHttpServer in release builds — Option B (~2 hr)** — Per operator: "debug mode doesn't exist in V1 production." Move `DebugHttpServer.kt` + `DebugEndpoints.kt` to `app-salem/src/debug/java/...`. Add no-op stubs in `src/main/` so call sites compile without conditional logic. Build release AAB, `unzip -l`, grep dex for `DebugHttpServer` to confirm zero.

2. **🟠 Audit WickedMapView (~3 hr)** — NEW; surfaced post-review. Live V1 basemap, my agents underexplored it. Spawn a focused Explore agent on `app-salem/.../wickedmap/`: !! assertions, Canvas/Bitmap lifecycle, animation thread safety, OOM behavior, fallback when tiles missing. Add findings to this doc as an addendum.

3. **🟠 Replace `!!` in walk-sim hot paths (~4 hr)** — `SalemMainActivity.kt:1605, 1610` and the dozen siblings. Plus `MarkerIconHelper.kt` drawable assertions. Plus `SimpleDateFormat.parse()!!`.

4. **🟠 Add `NarrationGeofenceManager.checkPosition` unit tests (~6 hr)** — Operator confirmed geofence/dedup is the #1 real-world bug source. Pure-function test target. Distance bucketing, dedup, detail-level filter, tour-mode + civic + commemorative interactions. Mock SharedPreferences.

5. **🟠 Fix LruCache memory-aware sizing (~1 hr)** — `TileArchive.kt:25-26`. Cheap insurance against low-RAM OOM. Particularly relevant if WickedMapView audit (#2) surfaces additional cache risks.

6. **🟠 Add SharedPreferences-backed dedup mirror (~3 hr)** — Closes S110. Eliminates re-narration after process death.

7. **🟡 `.gitignore` cleanup (~30 min)** — Add `.~lock.*`, `*.odt#`, `qcis/`, plus the three big binary assets (`salem_content.db`, `salem_tiles.sqlite`, `salem-routing-graph.sqlite`). `git rm --cached` them. Audit other tracked assets for "trim what's unneeded" (operator note) — particularly the 200+ poi-circle-icons WebPs to confirm they belong in git vs. being regenerated.

8. **🟡 Drop unused Socket.IO dep from `core/` and `app-salem/` if unreferenced (~15 min).**

9. **🟡 `salem_pois (deleted_at)` partial index (~5 min).**

10. **🟡 Document the DebugHttpServer debug-source-set in CLAUDE.md so V2 doesn't regress it (~15 min).**

**Dropped from sprint per operator:**
- ~~Wrap publish chain as Gradle preBuild task~~ — minimal daily pain; defer to V2.
- ~~Full `git filter-repo` history rewrite~~ — operator picked stop-the-bleeding only.

**Buffer:** 2-3 hours for surprises and operator-validation drives.

**What I'd defer past this sprint:** Activity refactor (operator confirmed manageable), frontend useState refactor, sharp fragility hardening, optimistic locking, salem-content tests. All M+ severity, all V1.1+ candidates.

**Sprint outcome:** Clears the BLOCKER, fills in the critical map-engine knowledge gap, hardens the most failure-prone surface (geofence math), kills the easiest crashes (`!!`), reduces low-RAM risk, eliminates re-narration-after-process-death, leaves V1 in a measurably better Play-Store-ready state.

---

<a name="v2"></a>
## 14. Looking forward (V2 thoughts, not V1 blockers) — UPDATED post-review

Operator confirmed: **1+ helper likely in V2.** That bumps onboarding-quality items up the V2 list. Reordered:

**V2 onboarding bundle (do early, before any helper touches the repo):**
- **`BUILD.md` / contributor-facing setup doc** — fresh-clone-to-running-app in <30 min. Currently lives in operator's head.
- **CI / GitHub Actions workflow** — `kspDebugKotlin` + `verifyBundledAssets` + Kotlin compile + 5 existing tests. ~2 hr setup. Catches stale schema before PR merge.
- **Pre-commit hook for Room schema bumps** — if `@Database(version = N)` is modified, ensure `app-salem/schemas/*/N.json` exists. ~2 hr.
- **Wrap publish chain as Gradle preBuild task** — relegated from V1 sprint per operator's "muscle memory" comment, but a helper won't have that muscle memory. Belongs here. ~1 hr.

**V2 product items (less time-pressured):**
- **Activity refactor into Fragment + ViewModel pairs** — eliminates the 17K-LOC bag. 2-week sprint.
- **WickedMapView decision** — ship it (V2 feature) or strip it (dead code). Pick.
- **Tile delivery model** — currently APK-bundled at 30 MB. If you ever expand coverage beyond Salem downtown, Play Asset Delivery becomes attractive (lazy fetch).
- **Test coverage push beyond geofence math** — TourEngine, NarrationManager TTS queue, walk-sim race scenarios.
- **Optimistic locking on admin POI edits** — relevant if a second operator or remote agent ever edits.
- **Backup strategy for PG** — currently informal. If your laptop dies before V2, what restoration story exists?
- **Migration to typed query layer (Postgres → Drizzle / Kysely)** — frontend type safety would be tighter.
- **Process-death recovery for tours** — full-feature: persist tour state to Room, resume on app restart.
- **Reducer-based state for LintTab + admin web** — when a contractor touches it, the useState soup will frustrate them.

---

<a name="operator"></a>
## 15. Operator-side blockers (non-code, tracked here for completeness)

These aren't in code, but they block ship and you've noted them in CLAUDE.md:

- **Form TX copyright registration** — counsel handling, 2026-05-20 deadline (statutory damages window). 20 days from today (2026-04-30).
- **Google Play Developer Account + identity verification** — $25 one-time, multi-week lead. Start now if not already.
- **Privacy Policy public hosting + in-app link** — V1-minimal draft at `docs/PRIVACY-POLICY-V1.md`. Awaiting lawyer.
- **Merchant / payments profile** in Play Console.
- **Second-medium copy of upload signing key** — encrypted cloud or second USB.

[OPINION] None of these are code work. All are time-bounded. The lawyer items have the longest lead. Keep pushing.

---

## Appendix: Verified facts (so future-you can audit me)

- `.git/` size: 540 MB ([VERIFIED] `du -sh .git`)
- `SalemMainActivity.kt`: 4,410 LOC ([VERIFIED] `wc -l`)
- Total `SalemMainActivity*.kt`: 17,443 LOC across 17 files ([VERIFIED])
- Tracked asset files: 215 ([VERIFIED] `git ls-files app-salem/src/main/assets/ | wc -l`)
- Largest tracked binaries: `salem_tiles.sqlite` (30 MB), `salem-routing-graph.sqlite` (11 MB), `salem_content.db` (6 MB) ([VERIFIED])
- DebugHttpServer.start() at `SalemMainActivity.kt:615` with no `BuildConfig.DEBUG` guard ([VERIFIED] via grep + read)
- `app-salem/src/` has only `main/` and `test/` source sets — no `debug/` ([VERIFIED] `ls`)
- `FeatureFlags.V1_OFFLINE_ONLY = const val true` ([VERIFIED] `core/src/main/.../FeatureFlags.kt:39`)
- INTERNET + ACCESS_NETWORK_STATE permissions commented out in `AndroidManifest.xml:20` ([VERIFIED])
- `publish-tour-legs.js` reads identity hash dynamically from latest schema JSON ([VERIFIED] lines 50-70)
- Test count: 5 `@Test` methods in 1 file, `NarrationGeofenceManagerTourModeTest.kt` ([VERIFIED] `grep -rn "@Test" | wc -l`)
- `!!` count in `SalemMainActivity.kt`: 13 ([VERIFIED] `grep -c "!!"`)
- Socket.IO declared in BOTH `core/build.gradle:75` AND `app-salem/build.gradle:203` ([VERIFIED])
- `qcis/` untracked, `.~lock.*odt#` files untracked ([VERIFIED] `git status`)

---

---

## Addendum: WickedMapView audit (added post-review, 2026-04-30)

After operator confirmed WickedMapView is the LIVE V1 primary basemap, I ran a focused Explore agent on `app-salem/.../wickedmap/` to fill the gap. Headline findings below; promote any HIGH+ items into your sprint backlog.

### What's healthy [confirmed strong]

- **Clean separation of concerns** — `MapCamera` (pan/zoom math), `TileArchive` (SQLite tile reads), `PolygonLibrary` (asset parsing), `MapOverlay` interface (pluggable animations). Good module boundaries.
- **Synchronization discipline** — `MapCamera` uses `synchronized()` consistently on `panPixels`, `scaleAround`, `snapshot`. Render thread reads from snapshot, gestures mutate. No races apparent.
- **Render thread error handling** — `try/catch` wraps `lockCanvas` / `drawFrame` / `unlockCanvasAndPost`. A crashed overlay won't crash the render thread.
- **No allocations in `onDraw`** — `Paint` and `Path` objects pre-allocated at init in all overlays. Single reused `Rect` per `drawTiles()`. Good.
- **Polygon clipping** — Point-in-ring tests + path clipping keep particles/anchors inside polygon bounds. No outside-bounds overdraw.
- **Lat/lon-based camera state** — Avoids the "Atlantic drift bug" called out in `MapCamera.kt:11-13`. Smart choice.
- **Deterministic seeding** — `Random(0xF12EF11E)` fixed seed across overlays = reproducible particle layouts. Good for QA.
- **30 FPS frame cap with deterministic sleep** — No vsync, no Choreographer, but adequate for a map. Won't burn battery on a 60/120 Hz panel.
- **Animations are background-only** — Drawn after tiles, do not impede roads/paths/buildings. Per the `feedback_basemap_priority_over_animation` memory rule. ✓
- **Visibility culling** — `sin()` only computed for visible particles. Per-polygon bbox checks. Good discipline.
- **ANR defense** — `WickedAnimationOverlay` throttles to 33 ms `postInvalidateDelayed` when particle counts get high. Comment explicitly cites the FireflyOverlay 6M-density-overdraw rollback.

### 🟠 HIGH — promote to sprint

#### W-H1. `TileArchive` LruCache sized by count, not bytes

**File:line:** `TileArchive.kt:25-26`
This is the same finding as the original report's H3 — but the WickedMap audit confirmed it with concrete numbers. `LruCache<Long, Bitmap>(256)` with `sizeOf() = 1`. A 256×256 RGBA8 tile is 256 KB. 256 tiles = 64 MB heap pressure. On a 2 GB device with the OS + Activity + music + galleries already loaded, this OOMs during aggressive pan/zoom.

**Fix:** Change to byte-count capacity, ~1 hr:
```kotlin
LruCache<Long, Bitmap>(20 * 1024 * 1024).apply {
    override fun sizeOf(key: Long, value: Bitmap) = value.byteCount
}
```
Cap at ~20 MB. Already on the sprint as #5; this audit confirms it.

#### W-H2. PolygonLibrary stays resident forever, no `onTrimMemory` hook

**File:line:** `PolygonLibrary.kt:25-139` (loads 589 KB GeoJSON into a HashMap; `loaded = true` prevents reload but no `unload()` exists).

If the Activity is backgrounded under memory pressure, the parsed polygon data persists. There's no `onTrimMemory` handler in `SalemMainActivity` to drop it.

**Fix (~1 hr):**
1. Add `fun unload()` to `PolygonLibrary` that clears `byKind` and resets `loaded`.
2. Hook `SalemMainActivity.onTrimMemory(level)` to call `PolygonLibrary.unload()` and `archive?.cache?.evictAll()` on `TRIM_MEMORY_RUNNING_CRITICAL` / `TRIM_MEMORY_BACKGROUND`.

Combined with W-H1, this is the right memory-pressure story for low-RAM devices.

### 🟡 MEDIUM

- **No `onConfigurationChanged` handler** — rotation triggers `surfaceChanged()` which syncs `viewportW/H`. Should be safe but untested.
- **Hardcoded Salem geography in `MapCamera.kt:23-28`** — zoom [16, 19], bounds 42.475-42.600 lat, -70.960 to -70.760 lon. Not parameterized. Brittle if the engine is ever reused elsewhere. Low priority for V1 (Salem-branded app).
- **`TileArchive` cursor not protected from concurrent access** — currently single render thread, so safe. Fragile for future refactors.
- **No tests** — confirmed zero. Add to V1.1 priority list.
- **`WickedMapPrototypeActivity` extracts `salem_tiles.sqlite` from assets as fallback** (line 57-59). Suggests the integration path with `OfflineTileManager` may have an unhandled "tile DB missing" branch. Worth a smoke test on a fresh install.

### 🟢 LOW / NIT

- **Magic numbers** — duty cycles, grid step sizes, animation phases inline. Could be a `WickedMapConfig` object, but not blocking.
- **Firefly ANR archaeology** — historical comment about 6M density / 700 cap rollback. Useful record; keep.
- **No `Bitmap.recycle()`** — relies on LruCache eviction. Fine.

### What I got wrong in the original retrospective

- **§11 Architecture said "WickedMapView reachable only via FAB, not the default map."** That came from the agent reports and is incorrect. WickedMapView is the production V1 basemap.
- **My initial sprint plan didn't include any WickedMap-specific tasks.** Now sprint task #2 is the audit (done — this addendum) plus W-H2 (`onTrimMemory` hook). W-H1 was already covered as H3.
- **The retrospective's "verify intent: dead code" suggestion for WickedMapView is wrong** — don't strip it. osmdroid's role in V1 (if any) is now the actual question — see follow-up below.

### Follow-up — osmdroid migration status

**Operator answer (post-review):** "Trying to get rid of it but as long as there are hooks we need to use it, we are trapped."

So osmdroid → WickedMapView is an in-progress migration, not a clean cutover. Both deps ship in V1 because some surfaces still hook into osmdroid that haven't been ported yet. **This belongs on the V1.1 / V2 backlog as "complete the osmdroid → WickedMapView migration."**

Concrete value: **identifying the remaining osmdroid hooks** would let you scope the migration honestly. Likely candidates: directions polyline overlay rendering, marker overlay (POI dots), info-window dialogs, the gesture/zoom plumbing that was easier to bridge than rewrite. Each hook is a port cost.

[OPINION] Worth a focused 30-min investigation post-V1 ship to enumerate the remaining hooks and estimate the migration cost. Could be ~1 day or ~2 weeks depending on what's still tangled. Until then, accept the dual-engine state — it's the cost of an in-flight migration.

### Top-3 ship-blockers from this audit

1. **W-H1 LruCache byte sizing** (~1 hr) — prevents low-RAM OOM.
2. **W-H2 `onTrimMemory` + `PolygonLibrary.unload()`** (~1 hr) — completes the memory-pressure story.
3. **Smoke test the missing-tile path** on a fresh install — confirm graceful blank-tile rendering vs. crash. Manual, ~30 min.

### Verdict on WickedMapView V1 readiness

**Functionally solid. Memory management needs hardening.** The engine works. Rendering, gestures, animations, overlay system all correct and well-disciplined. But the byte-count cache and the missing trim handler will hurt on budget phones during aggressive use. Both fixable in a half-day combined. Ship-ready after that.

This is not a "scrap and rewrite" finding. It's a "two specific fixes and you're good" finding.

---

*End of retrospective. Pushback encouraged.*
