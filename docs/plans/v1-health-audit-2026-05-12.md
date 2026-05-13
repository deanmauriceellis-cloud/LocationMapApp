# V1 Health Audit + Execution Plan — 2026-05-12 (S253 close)

**Source session:** S253 (2026-05-12). Audit run while operator was filling historical content. Five parallel Explore agents covered build/packaging, V1 offline gate, runtime memory/rendering, Room/DB/asset health, and code-health/architecture. Findings + decisions consolidated here for S254+ review.

**Scope rule:** V1 only. Internal ship target 2026-08-01. Post-V1 items flagged but not in execution waves except as planning artifacts.

---

## Top-line verdict

The codebase is structurally healthy but has **three real V1 ship cliffs** plus several pre-ship items that are quick fixes. Security / offline-gate is the strongest dimension; packaging is the weakest.

| Dimension | Verdict |
|-----------|---------|
| V1 offline gate integrity | ✅ Exemplary — three-tier defense, R8-verified, zero leaks |
| Build infrastructure | ✅ Current (AGP 9.0.1, Kotlin 2.0.21, targetSdk 35) |
| Room / DB schema discipline | ✅ Solid (v19 identity_hash aligned, publish chain robust) |
| Module structure post-S242 | ✅ Clean (no stale imports) |
| **Packaging / Play Store readiness** | 🔴 **AAB 316 MB vs 200 MB ceiling — Asset Packs unwired** |
| **Runtime memory at high tilt** | 🔴 **No largeHeap + S253 24× expansion = OOM risk** |
| **Privacy posture for paid offline** | 🟡 **allowBackup=true contradicts the offline framing** |
| Code hygiene (leaks, scopes, logs) | 🟡 Several real but small fixes (TTS shutdown, 4 scopes) |
| Tests / lint / CI | 🟡 Minimal coverage, no enforcement |
| Architecture (monolith size) | 🟡 SalemMainActivity 18,623 LOC across 20 files — post-V1 refactor |

---

## Critical findings (V1 ship-blocker cliffs)

### Cliff 1 — AAB size vs Play Store 200 MB compressed ceiling
- **Current:** `app-salem/src/main/assets/` = 316 MB total
  - `salem_tiles.sqlite` = **262 MB** (audit-measured; STATE.md + memory entries cite 848 MB or 207 MB — both stale)
  - `salem_content.db` = 5.9 MB
  - `heroes/` = 14 MB, `routing/` = 11 MB, icons/sprites/etc = ~23 MB
- **Play Store:** 200 MB compressed download is the AAB ceiling; Asset Packs extend to 2 GB combined.
- **State:** Asset Packs NOT wired. `noCompress += ['sqlite']` in `app-salem/build.gradle:95` keeps the tile DB uncompressed in the APK (correct for `AssetFileDescriptor`).
- **Memory reference:** `project_apk_packaging_components.md` flagged this previously but the build still bundles everything.

### Cliff 2 — `android:largeHeap="true"` missing from manifest
- **Current:** `app-salem/src/main/AndroidManifest.xml` has no `largeHeap` attribute. Default heap on Android 15 = 256–384 MB.
- **S253 expansion math:** at 60° tilt, MapView extends to `6× vertical × 4× lateral = 24× containerArea`. Framebuffer alone at 60° ≈ 80 MB; triple-buffered ≈ 240 MB on a 256–384 MB heap → **deterministic OOM at high tilt + z19 + active panning on Lenovo TB305FU.**
- **State:** S253 carry was "field-validate at 45°/60°" — this is more urgent than a polish item, it's a cliff waiting on field steps.

### Cliff 3 — `android:allowBackup="true"` on a paid offline app
- **Current:** `AndroidManifest.xml:43` `android:allowBackup="true"` auto-backs up `salem_content.db` + user state + osmdroid cache to Google Cloud Backup on every install/update.
- **Contradiction:** `feedback_v1_no_external_contact.md` is an absolute V1 rule (zero outside contact). Auto-backup violates it for $19.99 paid users who expect privacy.

---

## What we're doing well (worth preserving)

1. **V1 offline gate is exemplary.** Three-tier defense — `const val V1_OFFLINE_ONLY = true` in `FeatureFlags.kt` + `OfflineModeInterceptor` (transport) + INTERNET stripped from release `AndroidManifest.xml` + `network_security_config.xml` debug-only at `app-salem/src/debug/res/xml/`. S249 R8 verification (`apkanalyzer dex code --class …SuperAdminMode`) returned "class not found" against the release APK. **Audit found zero ungated network paths in release-executable code.** This is the strongest part of the codebase.

2. **Build infrastructure is current.** AGP 9.0.1, Kotlin 2.0.21, KSP aligned, Hilt 2.51, targetSdk 35 (meets Play Store's Aug 2025 requirement exactly), JVM 17, R8 minify on with sane proguard rules. No EOL approaching before Aug 2026.

3. **Module structure clean post-S242.** `:app` and `:salem-content` deletions surgical — zero stale imports. Three-module DAG (`:app-salem` / `:core` / `:routing-jvm`) properly wired in `settings.gradle:17-19`.

4. **Schema discipline.** v19 identity_hash `745afa3eb4ce04bd7873671ea297b6e0` matches asset, JSON, and `room_master_table` exactly. `align-asset-schema-to-room.js:41-50` numerically (not lexically) sorts versions — future-safe for v100+. Destructive-migration alarm in `SalemModule.onDestructiveMigration` is the right pattern. Bundled-asset Room + UserData explicit-migration split (UserData rejects destructive fallback) is correct.

5. **S243 verbose instrumentation paying off.** GPS-OBS heartbeat, `MapDebugDumper`, billboard draw counts, narration gate decisions — observability is the reason the S251–S253 diagnostics were fast. TCP collector streaming from Lenovo is a real win.

6. **Tech-debt annotation density is low.** 7 TODO/FIXME in `:app-salem`, mostly format-string false positives. One legitimate `Phase 9C or later` TODO in `PoiCategories.kt`. No "before-ship" debt callouts hiding in comments.

---

## Detailed findings by dimension (the rest)

### Build / packaging

- AGP 9.0.1, Gradle 8.9, Kotlin 2.0.21, KSP 2.0.21-1.0.28, Hilt 2.51 ✓
- `compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`, `versionCode = 10000`, `versionName = "1.0.0"` ✓
- `applicationId = "com.destructiveaigurus.katrinasmysticvisitorsguide"` (S246 LLC pivot ✓)
- Signing externalized via `~/.gradle/gradle.properties` (mode 0600) → `WICKED_SALEM_KEYSTORE_PATH` etc., debug-signing fallback at `app-salem/build.gradle:62-65`
- R8 minify + shrinkResources on release ✓; `proguard-rules.pro` (60 lines) preserves Hilt/Room/Gson reflectables
- `multiDexEnabled` absent but auto-enabled at minSdk 26 ✓
- No native libs / jniLibs ✓ (no ABI splits needed)
- `org.gradle.jvmargs=-Xmx2048m` — likely too low for AGP 9 + R8 + 316 MB shrinkResources pass
- No `lint-baseline.xml`; lint runs but doesn't enforce
- No `.github/workflows/`; zero CI
- No hardcoded secrets in `app-salem/src/main` source (verified via grep). MBTA + Windy keys live in `cache-proxy/.env`.

### V1 offline gate

- `FeatureFlags.V1_OFFLINE_ONLY: const val Boolean = true` at `core/src/main/java/com/example/locationmapapp/util/FeatureFlags.kt:31-40` ✓ (const inlines, R8 DCEs)
- `SuperAdminMode` in `core/.../util/SuperAdminMode.kt:35-88` ✓ (binary AND gate, seeded from `BuildDefaults.SUPER_ADMIN_ENABLED = BuildConfig.RECON_DEFAULTS`, no SharedPreferences persistence)
- `OfflineModeInterceptor` in `core/.../util/network/OfflineModeInterceptor.kt:34-60` throws `OfflineModeException` ✓; installed first in every OkHttpClient builder verified at MbtaRepository:57, WeatherRepository, AircraftRepository, TfrRepository, WebcamRepository, GeocodeProxy at SalemMainActivityFind:1786
- Release manifests clean (no INTERNET, no ACCESS_NETWORK_STATE) — `core/src/main/AndroidManifest.xml` S250 leak fix verified
- Debug-only `network_security_config.xml` allow-lists 10.0.0.229 / localhost / 127.0.0.1 ✓
- Radar tile call (`SalemMainActivityRadar.kt:42-46, 108-112`) is gated on `FeatureFlags.V1_OFFLINE_ONLY && !SuperAdminMode.allowNetwork` before `XYTileSource` construction ✓
- `TcpLogStreamer` BuildConfig.DEBUG-gated at `WickedSalemApp.onCreate:40` ✓
- TTS uses `android.speech.tts.TextToSpeech` — on-device only, no Bark/Piper at runtime ✓
- WebView usage: WitchTrials uses `loadDataWithBaseURL(null, html, ...)` (local HTML); Webcam player gated by `!FeatureFlags.V1_OFFLINE_ONLY` ✓
- **Minor:** MBTA API key hardcoded at `MbtaRepository.kt:51` (unreachable in release due to interceptor + INTERNET strip, but operator-clarified: "LMA should never be hitting MBTA" — delete key + reroute via cache-proxy)

### Runtime memory / rendering cliffs

- **No `android:largeHeap="true"`** in `AndroidManifest.xml` — see Cliff 2
- 8 sites allocate ARGB_8888 bitmaps without sampling (`SalemMainActivityWeather`, `SalemMainActivityDialogs`, `MarkerIconHelper`, `SalemMainActivityMetar`, `HeroAssetLoader`, `PoiHeroResolver`, `SpriteOverlay`, `TileArchive`)
- Hero LRU cache 8 MB at `HeroAssetLoader` — bounded ✓
- 2,037 POIs → ~925 visible markers at any time; per-marker base ~300 bytes ✓
- BillboardMarker pass-1 + pass-2 at tilt with `SCREEN_CULL_PRE_PX=50f` / `SCREEN_CULL_POST_PX=300f` — loose but functional; steady-state 11k draws/frame per S251 instrumentation
- Animation overlays (water/grass/firefly/sprite) all viewport-culled with anchored duty cycles; aggregate ~2 ms/frame when all active ✓
- Tile cache defaults (no `tileMaxCacheSize` override) — appropriate for offline ✓
- osmdroid tile decode WebP on-the-fly without `inSampleSize`; rapid pan at z19+tilt could backlog
- TTS queue `ArrayDeque<NarrationSegment>` in `NarrationManager.kt:80` — bounded by geofence rate ✓
- Wake lock acquired during walk-sim, released onDestroy ✓; no foreground service

### Room / DB / asset health

- `SalemContentDatabase.kt:17-49` — `@Database(version = 19)`, 12 entities, `exportSchema = true`, `.createFromAsset("salem_content.db")` + `.fallbackToDestructiveMigration()` ✓
- `SalemModule.kt:59-73` — onOpen logs schema_v + tables; onDestructiveMigration fires E-level alarm **but DEBUG-only at line 52** (silent in release if drift ships)
- Only `19.json` present in `app-salem/schemas/.../SalemContentDatabase/` ✓ (v10-v18 dropped per S242)
- 12 Room-managed tables: salem_pois (2,037), historical_figures (49), tours (4), tour_legs (73), tour_stops (**0** — Room-declared but empty), timeline_events (40), primary_sources (200), historical_facts (500), events_calendar (20), salem_witch_trials_articles (16), salem_witch_trials_newspapers (202), salem_witch_trials_npc_bios (49)
- **3 dead tables in asset:** `tour_pois` (45), `salem_businesses` (861), `narration_points` (817) — superseded by SalemPoi but never DROPped. ~1 MB waste.
- DAOs use `LIMIT` on text search, `@RewriteQueriesToDropUnusedColumns` on proximity ✓; no N+1 patterns
- No `TypeConverters` (all scalar) ✓
- `salem_content.db` 5.9 MB, `salem_tiles.sqlite` 262 MB, `salem-routing-graph.sqlite` 11 MB — all noCompress'd, journal_mode=delete (single file, no WAL sidecars)
- `OfflineTileManager.kt:60-90` copies salem_tiles.sqlite to `externalFilesDir` on first launch via size-based version check — 2–5 s with no progress UI
- `UserDataDatabase` (version 2) rejects destructive fallback → correct (user-generated state must survive updates)
- No `MigrationTest` in `androidTest/`

### Code health / architecture

- **SalemMainActivity monolith:** main file 4,889 LOC + 19 sibling `SalemMainActivity*.kt` files = **18,623 LOC = 100% of `:app-salem` UI source**
- Top 10 files by LOC: SalemMainActivity 4,889 / WitchTrialsMenuDialog 2,586 / SalemMainActivityTour 2,277 / Find 1,946 / Narration 1,824 / AppBarMenuManager 1,264 / MarkerIconHelper 1,082 / Transit 1,034 / Geofences 1,016 / DebugEndpoints 989
- 30+ Manager/Repository/Tracker classes with overlapping concerns (LocationManager vs AppLocationManager vs FusedLocationProviderClient; NarrationManager vs NarrationGeofenceManager)
- TODO/FIXME density: 7 in `:app-salem`, 1 in `:core` — mostly format strings, one legitimate Phase 9C TODO
- 92 `@Suppress` annotations; 82 are `"unused"` (MODULE_ID diagnostic constants — explained, acceptable)
- **4 unbounded CoroutineScopes without `scope.cancel()`:** `PoiEncounterTracker`, `GpsTrackRecorder`, `TcpLogStreamer` (debug), `DebugEndpoints` (debug)
- **TTS leak:** `NarrationManager.kt:~196` nulls `tts` but never calls `tts?.shutdown()` — Singleton retains TTS service binding
- 109 listener registrations vs 6 explicit unregisters — most are lifecycleScope/Flow auto-clean ✓ but ratio warrants spot-check
- 5 ungated `Log.d`/`Log.e` calls (PolygonLibrary + WickedMapView) vs 34 properly DEBUG-gated
- 29 hardcoded Toast strings (no `strings.xml` externalization)
- Test coverage: 2 unit tests in `:app-salem/src/test/`, no androidTest, `:routing-jvm:test` 4/10 failing
- No `detekt.yml`, no `.editorconfig`, no `lint.xml`

---

## Decision ledger (23 items, all answered S253)

### Manifest / build (4)
| # | Decision | Choice |
|---|----------|--------|
| 1 | salem_tiles.sqlite delivery | **Install-time Asset Pack** |
| 2 | `android:largeHeap` | **Add `true`** |
| 3 | `android:allowBackup` | **Flip to `false`** |
| 4 | Gradle JVM heap | **Bump 2 GB → 4 GB** |

### Code hygiene (4)
| # | Decision | Choice |
|---|----------|--------|
| 5 | NarrationManager TTS shutdown | **Add `tts?.shutdown()` before null** |
| 6 | 4 unbounded CoroutineScopes | **Add `scope.cancel()` to all 4** |
| 7 | onDestructiveMigration log | **Always log E-level (remove DEBUG gate)** |
| 8 | 5 ungated `Log.d`/`Log.e` calls | **Gate all 5** |

### DB / asset (4)
| # | Decision | Choice |
|---|----------|--------|
| 9 | 3 dead tables (tour_pois / salem_businesses / narration_points) | **Drop in next bake** |
| 10 | First-launch tile extraction UX | **Add progress dialog/toast** |
| 11 | Bitmap `inSampleSize` | **Heroes only (targeted fix)** |
| 12 | Tile cache size tuning | **Leave osmdroid defaults** |

### Tests / process (4)
| # | Decision | Choice |
|---|----------|--------|
| 13 | `:routing-jvm:test` 4/10 failing | **Re-bake fixtures from current bundle** |
| 14 | Lint baseline + enforcement | **Generate baseline + `abortOnError=true` on release** |
| 15 | Room MigrationTest | **Add before next schema bump** |
| 16 | CI workflow | **Minimal: lint + assembleDebug on push** |

### Ship prep (3)
| # | Decision | Choice |
|---|----------|--------|
| 17 | S253 tilt 45°/60° validation order | **Add largeHeap first, then field-validate** |
| 18 | Signing keystore verification | **Write pre-ship checklist doc** |
| 19 | Memory write (manual publish chain rule) | **Write `feedback_publish_chain_manual_after_admin_edits.md`** |

### Strategic (2 — captured for planning, not Wave 1-3 execution)
| # | Decision | Choice |
|---|----------|--------|
| 20 | SalemMainActivity monolith + Manager/Repo overlap | **Plan decomposition now (Plan-mode doc), execute post-V1** |
| 21 | MBTA hardcoded API key | **Delete from Android entirely — LMA should never hit MBTA. Cache-proxy is the only consumer.** Includes: grep-verify all MBTA paths route through `10.0.0.229:4300/mbta/*` (closes S251/S252 carry on `api-v3.mbta.com` direct leak). |

### i18n (3 — new scope addition)
| # | Decision | Choice |
|---|----------|--------|
| 22 | Language scope | **Spanish first; build infrastructure to support all languages** |
| 23a | Translation quality | **UI strings translated V1; narration stays English V1** |
| 23b | Spanish TTS | **Device-default `es-ES`/`es-MX` TTS, no Bark/Piper** |

---

## Execution waves

### Wave 1 — Quick wins (~30 min, single commit)

Risk-zero edits. Should ship before field-validating S253 at higher tilts.

1. `app-salem/src/main/AndroidManifest.xml` — add `android:largeHeap="true"`, flip `android:allowBackup="false"`
2. `gradle.properties` — `org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8`
3. `NarrationManager.kt:~196` — `tts?.shutdown()` before `tts = null`
4. `PoiEncounterTracker.kt`, `GpsTrackRecorder.kt`, `TcpLogStreamer.kt`, `DebugEndpoints.kt` — add `scope.cancel()` in lifecycle teardown (onCleared/onDestroy/equivalent)
5. `SalemModule.kt:51-63` — remove the `if (!BuildConfig.DEBUG) return` in `onDestructiveMigration`
6. Gate 5 ungated `Log.d`/`Log.e` calls (PolygonLibrary + WickedMapView) behind `if (BuildConfig.DEBUG) { ... }`
7. Write `~/.claude/projects/.../memory/feedback_publish_chain_manual_after_admin_edits.md` + MEMORY.md index entry

### Wave 2 — Pre-ship hardening (~2 sessions)

Build APK with Wave 1 + Wave 2 → field-validate S253 wedge fill at 45° + 60° on Lenovo HNY0CY0W. With largeHeap in place, expect survivability; if it still chunks, dial `EXTRA_SIDE_CAP` down from 1.5.

8. Drop 3 dead tables (`tour_pois`, `salem_businesses`, `narration_points`) in `publish-salem-pois.js` or the align step; rebake
9. Delete hardcoded MBTA API key from `MbtaRepository.kt:51`; grep-verify all MBTA paths route through `10.0.0.229:4300/mbta/*` (closes S251 `api-v3.mbta.com` direct-leak carry)
10. Re-bake `:routing-jvm:test` fixtures from current `salem-routing-graph.sqlite` bundle
11. Run `:app-salem:lintRelease` → commit `lint-baseline.xml` → set `lintOptions { abortOnError true; baseline file("lint-baseline.xml") }` on release buildType
12. Add `BitmapFactory.Options.inSampleSize` logic in `HeroAssetLoader.kt` (heroes only per decision)
13. Write `docs/SHIP-CHECKLIST.md` — keystore at `~/keys/wickedsalem-upload.jks`, `~/.gradle/gradle.properties` contents, USB backup verification step, second-medium owed item
14. Add `.github/workflows/ci.yml` — lint + `assembleDebug -PskipPublishChain` on push
15. Field-validate Wave 1+2 build on Lenovo at 30° / 45° / 60° tilt + z17 / z18 / z19 (combinatorial smoke test)

### Wave 3 — Ship implementation (~3–4 sessions)

The Play Store unlocker. Without Wave 3 the AAB cannot be uploaded.

16. **Install-time Asset Pack for `salem_tiles.sqlite`** — new module `app-salem-tiles-pack` (asset-pack), Play Asset Delivery wiring, `AssetPackManager` integration in `OfflineTileManager.kt`, build config `bundle { abi { ... }; density { ... }; language { ... } }`, manifest update
17. Tile-extraction / Asset Pack install progress UI in `OfflineTileManager.kt:75-85` (becomes Asset Pack install UI under the new model)
18. Add `app-salem/src/androidTest/.../SalemContentDatabaseMigrationTest.kt` — runs identityHash check + row-count smoke before any v20 ships

### Wave 4 — i18n infrastructure (~2–3 sessions)

The new V1 scope commitment. Architecture must support all languages even though only Spanish ships V1.

19. Externalize all 29 hardcoded Toast strings + any other survey-found user-facing literals into `app-salem/src/main/res/values/strings.xml`
20. Create `app-salem/src/main/res/values-es/strings.xml` — Spanish UI translation
21. Locale switcher in Settings (or system-locale-following) + locale-aware TTS engine selection (`TextToSpeech.setLanguage(Locale.forLanguageTag("es-ES"))` with fallback)
22. Verify narration path stays English-only V1 (per decision 23a) — narration text from PG, no `values-es` for narration tables
23. Smoke-test Spanish on Lenovo with `adb shell setprop persist.sys.locale es-ES`

### Wave 5 — Post-V1 planning artifact (no V1 code)

24. Write `docs/plans/post-v1-architecture-decomposition.md` — SalemMainActivity ViewModel extraction plan + Manager/Repository consolidation rules + estimated 3–4 week post-V1 refactor scope. Plan-mode doc only. Not executed in V1.

---

## Open items (not in execution waves)

- **TigerBase Android wiring (Task #15)** — S252 carry, S253 carry. Post-V1 lawyer-gated per memory `project_tigerbase_pipeline.md`. Explicitly **out of V1 scope** per operator directive ("I don't care about post V1").
- **Radar bypass of cache-proxy** (`SalemMainActivityRadar.kt:52,127` direct to `mesonet.agron.iastate.edu`) — V1+ service, gated, not a V1 blocker.
- **`ufw allow 4300/tcp` + `4301/tcp` persistence** (S251 carry) — dev-box rule, not V1.
- **STATE.md 10.0.0.229 footnote correction** (S251 carry) — doc hygiene.
- **`salem_tiles.sqlite` size discrepancy** — multiple sources cite 848 MB / 207 MB; actual is **262 MB**. Memory + STATE references should be reconciled when those files are next touched.

---

## Time / risk estimates

| Wave | Sessions | Risk | Blocks ship? |
|------|----------|------|--------------|
| 1 | 1 (~30 min) | None | No — but unblocks Wave 2 field validation |
| 2 | 2 | Low | No |
| 3 | 3–4 | Medium (Asset Pack first-time wiring) | **YES — required for AAB upload** |
| 4 | 2–3 | Medium (i18n surface coverage) | Scope commitment, not technical blocker |
| 5 | 0.5 (doc only) | None | No |
| **Total V1** | **~9–10 sessions** | | |

Calendar runway: 2026-05-12 → 2026-08-01 = **~80 days**. At 2 sessions/week conservative, that's 22 sessions available. Wave 1–4 fits with margin for content authoring + field-walk validation passes + operator-side legal items (Form TX, Play Console, privacy-policy hosting).

---

## Operator-side items (not Claude execution)

These remain operator-owned regardless of the technical waves:

- Form TX copyright filing — counsel, deadline **2026-05-20** (8 days from this doc); confirm filing names Destructive AI Gurus, LLC as claimant
- Google Play Developer Account registration under the LLC; multi-week ID verification clock
- Operating agreement (counsel recommendation pending)
- Privacy Policy public hosting + in-app link (waiting on counsel approval)
- Merchant / payments profile in Play Console
- Field walks for narration content QC + S253 tilt validation

---

## Next-session action

Recommend opening S254 with Wave 1 (the 30-minute commit) immediately after session-start protocol completes. Wave 1 is risk-free, unblocks Wave 2 field validation, and clears 7 of the 23 decisions on the ledger in one pass.

After Wave 1 commit, decide whether to continue into Wave 2 same session or pivot to operator's TigerBase / content / other priorities.
