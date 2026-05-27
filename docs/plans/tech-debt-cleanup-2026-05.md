# Tech-Debt Cleanup Plan — 2026-05 (S302)

> Declared plan-of-record for the tech-debt cleanup effort. Built from the S302 survey
> (live log `docs/session-logs/session-302-2026-05-26.md`) + operator dialog decisions.
> Aug 1 ship target stands; OMEN-025 remains the ship-blocking TOP PRIORITY and is
> unaffected by this effort (gated on operator creds).

## Operator decisions (S302 dialog)

1. **Appetite:** Aggressive — include the SalemMainActivity decomposition, *but guarantee a fall-back path*.
2. **Decomp target:** Feature ViewModels + Managers; Activity becomes a thin wiring/observer layer. Each extraction = one revertable commit.
3. **Parked network code (~3,300 LOC):** Isolate into a `parked/` boundary, do NOT delete (honors `feedback_v1plus_features_parked_not_killed` + `project_v1_offline_only`).
4. **Verify cadence:** compile clean + JVM unit tests per commit; on-device bundletool install + smoke walk on Pixel 8 at each PHASE boundary.
5. **Fallback mechanism:** stay on master; tag a checkpoint after each verified phase (`cleanup-phaseN-done`); reset to last-good tag to fall back.

## Grounding facts (verified S302)

- Monolith = **18,880 LOC across 20 partial-class files** (`SalemMainActivity*.kt`); root file 5,249. Biggest partials: Tour 2012, Find 2003, Narration 1922.
- **ViewModels already exist** (10, ~2,484 LOC): Main/Tour/Find/Narration?(via NarrationManager)/Geofence/Social/Weather/Aircraft/Transit/Events/WitchTrials. **Managers already exist** (~12). → decomposition is *migration into existing classes*, not greenfield.
- Cross-cutting pain not yet owned by any class: **~122 internal mutable fields**, **17 refresh-`Job` fields** (train/subway/bus/aircraft/webcam/cachePoi/walkSim/autoDump/gpsObsHeartbeat...), **2 mutexes** (`walkMutex`, `narrationMarkerMutex`), and all map-overlay add/clear/invalidate. onDestroy cancels jobs by hand → leak risk if one is missed.
- `FeatureFlags.V1_OFFLINE_ONLY=true` lives at `core/.../util/FeatureFlags.kt`; 14 gated network clients.
- Test net is thin: app-salem has 2 JVM unit tests + 1 androidTest (migration); core has 5 activation tests; routing-jvm parity. **We add characterization tests as we go.**
- Publish chain (6 scripts) order is comment-documented, not code-enforced; `.env` parser duplicated 5×; DROP+CREATE unwrapped in 3 scripts.
- `custom_icon_asset` column never read in Kotlin; ~800 dangling `image_asset LIKE 'hero/%'` PG rows; orphaned `hero/` UUID dir. (§7 of the art-bible retirement list.)

## Standing guardrails (every phase)

- **Behavior-preserving only.** Moves and logic-changes never share a commit. A refactor commit must produce byte-identical behavior; any intentional behavior change is its own separately-reviewed commit.
- **DB discipline.** Never let the preBuild auto-bake re-stamp `salem_content.db` into a manifest mismatch: build with `-PskipPublishChain` and `git checkout` the committed DB if it drifts (S301 lesson). No Room schema bump in this effort except where a phase explicitly calls for one.
- **GPU caution.** Confirm the RTX 3090 is free before any on-device emulator/GPU work (`feedback_gpu_caution`); Pixel 8 (`41231FDJH0018J`) is primary, Lenovo (`HNY0CY0W`) is min-spec floor.
- **Install discipline.** `adb uninstall` then bundletool `build-apks`/`install-apks`; never `install -r` (`feedback_adb_install_after_db_rebake`).
- **Debug logging.** Every silent state change moved/added gets a `BuildConfig.DEBUG`-gated `DebugLogger.d` line (`feedback_verbose_debug_until_production`).
- **Tag protocol.** Before a phase: `cleanup-phaseN-pre`. After verified: `cleanup-phaseN-done`. Push tags. Fall-back = `git reset --hard cleanup-phase{N-1}-done`.

---

## Phase 0 — Safety net + release-bake correctness (LOW risk, pre-ship valuable)

The only genuinely pre-ship-critical work. Independent of the monolith.

- **0a. Publish-chain orchestrator** — `cache-proxy/scripts/publish-all.js`: runs the 6 scripts in canonical order with fail-fast (non-zero exit aborts the chain), prints the `manifestHash` at the end. Update CLAUDE.md Key Paths to point operators at it (keep the manual sequence documented as the fallback).
- **0b. Shared `.env` helper** — `cache-proxy/lib/env.js`; replace the 5 duplicated inline parsers.
- **0c. Transaction-wrap DROP+CREATE** in `publish-salem-pois.js`, `publish-tours.js`, `publish-tour-legs.js` (align + poi-collection already wrapped).
- **0d. Characterization tests for known footguns** (pure logic, cheap, high value): `MenuPrefs` S206 mirror defaults (`histLandmarkPrefDefault`/`civicPrefDefault`) and `MarkerIconHelper` category→asset resolution. These also become the regression net for later phases.
- **Verify:** `node publish-all.js` dry-run against PG produces a clean signed manifest; `:core`/`:app-salem` unit tests green; cache-proxy starts. No on-device needed (no app code changed). Tag `cleanup-phase0-done`.

## Phase 1 — Cross-cutting infra: JobCoordinator + MarkerController (MEDIUM risk, high leverage)

Tame the leak/race surface *before* moving feature state — this is what makes Phases 3–4 safe.

- **1a. `JobCoordinator`** — owns the 17 refresh `Job`s keyed by name; `register`/`cancel(name)`/`cancelAll()`; single `cancelAll()` call in `onDestroy`. Migrate jobs into it incrementally (one cluster per commit: transit jobs, aircraft jobs, poi/walk jobs...).
- **1b. `MarkerController`** — owns map-overlay lifecycle: `add(type, marker)`, `clear(type)`, `invalidate()`. Collapses the 5× copy-paste `clearXxxMarkers()` and 3× `addXxxMarker()` patterns. Absorbs `narrationMarkerMutex`.
- **Verify:** compile + unit; **on-device phase smoke**: GPS walk-sim on Pixel 8 confirms markers add/clear correctly, no leaked refresh loops after backgrounding (check Job logs), narration markers still render. Tag `cleanup-phase1-done`.

## Phase 2 — Dead `hero/`/icon system retirement (LOW risk, pure subtraction)

Coordinates with the art-bible §7 retirement already on the graphics docket.

- **2a.** Remove dead Kotlin refs to `custom_icon_asset` (verify zero readers first) and any dead `hero/`-path resolution branches superseded by the S299 hero system.
- **2b.** Delete the orphaned `hero/` UUID asset dir + null the ~800 dangling `image_asset LIKE 'hero/%'` PG rows (admin/script, then republish).
- **2c. DEFERRED to post-V1:** dropping the `custom_icon_asset` *column* (needs Room v23→v24 migration + identity_hash + full publish chain — schema-bump risk outweighs the benefit pre-ship). Note it in STATE.md carry-forward.
- **Verify:** publish chain clean, `verifyBundledAssets` passes, hero images still resolve on-device (no red placeholders). Tag `cleanup-phase2-done`.

## Phase 3 — Isolate parked network features (MEDIUM risk, behavior-preserving moves)

Move each V1-disabled feature out of the Activity into a quarantined `parked/` package, flag-gated, compilable. ~3,300 LOC leaves the monolith surface.

- One feature per commit + sub-tag: Radar → Metar → Webcam → Weather → Aircraft → Transit → Social. Each: move the partial-class file + its ViewModel into `.../parked/<feature>/`, keep the `FeatureFlags.V1_OFFLINE_ONLY` gate at the call site (`feedback_v1_gate_at_call_site`), confirm R8 still strips it in a release build.
- **Verify:** debug compile (flag gated off = no behavior); one release `bundleRelease` + `apkanalyzer` confirms the parked code is stripped (no new dex/permissions). On-device: V1 app behaves identically (features stay invisible). Tag `cleanup-phase3-done`.

## Phase 4 — Migrate active-feature state into existing ViewModels (HIGH risk, the core decomp)

The big one. For each *active* V1 domain, migrate state + logic still in the Activity partials into the already-existing ViewModel/Manager; Activity observes via the established pattern (`TourViewModel`/`MainViewModel` precedent). One domain per sub-phase, each its own tag so any single domain can be reset without losing the others.

Order (smallest/least-coupled first to build confidence):
- **4a. Events + Detour + Directions** (small, 253–465 LOC each) into Events/Tour VMs.
- **4b. Populate** (810) — POI load/marker population into MainViewModel + MarkerController.
- **4c. Geofences** (1016) into GeofenceViewModel + NarrationGeofenceManager.
- **4d. Find** (2003) into FindViewModel.
- **4e. Narration** (1922) into NarrationManager/VM.
- **4f. Tour** (2012) into TourViewModel — last, most coupled.
- Add a characterization test per domain before moving its state (capture current observable behavior), so the move is test-guarded.
- **Verify:** compile + unit per commit; **full on-device smoke walk per sub-phase** (tour start→narration→collection→markers→find). Tag after each: `cleanup-phase4a-done` … `cleanup-phase4f-done`.

## Phase 5 — Targeted correctness hardening (LOW–MEDIUM risk)

Only the **non-parked** paths (parked ones move untouched in Phase 3).

- Replace `!!` force-unwraps in active narration/tour/marker/GPS paths with safe handling + `DebugLogger`; convert `SimpleDateFormat.parse(...)!!` to null-safe parse with fallback.
- Replace the empty `catch {}` in active user-facing paths (phone-dial intent, etc.) with logged fallback.
- **Verify:** compile + unit; on-device smoke. Tag `cleanup-phase5-done`.

## Phase 6 — Web admin god-components (LOWEST priority, OPTIONAL / time-permitting)

Operator-only tool, never ships to users — do only if time allows before other priorities reclaim focus.

- Break `AdminMap.tsx` (2,688) / `TourTree.tsx` (1,299) into sub-components; extract a shared admin `fetch` wrapper (dedupes the per-tab error handling).
- Decide the SI/Oracle paused-service wiring: leave behind one clear "service paused" guard (recommended — the hard gate may lift) vs remove. Confirm with operator before removing.
- **Verify:** `npm run build` + lint clean in `web/`. Tag `cleanup-phase6-done`.

---

## Sequencing & fall-back summary

`Phase 0` (ship-safe, do regardless) → `Phase 1` (infra, de-risks the rest) → `Phase 2` (cheap subtraction) → `Phase 3` (isolate parked) → `Phase 4` (core decomp, the long pole) → `Phase 5` (hardening) → `Phase 6` (optional web).

Each phase ends shippable + tagged. If a phase regresses on-device and can't be fixed in-session, `git reset --hard` to the prior `cleanup-phaseN-done` tag — master returns to a known-good, ship-ready state. OMEN-025 work can interleave between phases at any tagged boundary.

**Risk-vs-ship note:** Phase 0 is the only item I'd insist on before Aug 1. Phases 1–5 are high-value but each is interruptible at a tag boundary, so they can yield to OMEN-025 the moment the operator creds arrive.
