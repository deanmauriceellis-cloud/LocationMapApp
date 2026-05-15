# POI Passport — replaces tour-stop UI

**Status:** Proposal — for next-session exploration. No code yet.
**Drafted:** 2026-05-15 (S267 close)
**Driver:** Operator confirmed all four runtime "tour stops" UIs are leftover from a prior architecture that "isn't even working correctly" and should be killed. Replace with a persisted **POI Passport** — a per-user list of historically-narrated POIs flagged as visited/heard, regeneratable from the web admin portal under operator-defined parameters.

---

## Why

The Android app's tour runtime carries a stops-based progress UI (HUD banner, Active Tour dialog, completion stats, numbered polyline pins) inherited from when tours were stop-by-stop guided sequences. Today's authored tours are **waypoint-to-waypoint polylines** + **ambient geofence-based narration** (see memories `feedback_authored_tours_are_definitive.md`, `feedback_tour_routing_is_content_not_engineering.md`). The stop-progress surfaces no longer match how tours actually work, are user-confusing, and sit on top of a stale `currentStopIndex` machinery with at least one known bug (resume-on-relaunch doesn't validate POI id, can land on wrong POI after re-author).

The replacement concept the operator wants is a **POI Passport**: the user accumulates "stamps" by walking past POIs that have historical narration. The Passport persists across launches (so a user resuming a Salem trip the next day sees what they've already heard). The web admin portal regenerates the canonical Passport list from operator-tunable parameters (proximity threshold, categories, narration-presence, etc.) — each bake refreshes which POIs count as Passport stops.

---

## What goes away (Android side, all in `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityTour.kt`)

| Surface | Approx. lines | Notes |
|---|---|---|
| Top-center floating tour HUD banner | `updateTourHud()` ~1327, `removeTourHud()` callers | Built every location update during Active/Paused. Reads `currentStopIndex+1 / stops.size : POI name`. |
| "Active Tour" dialog (Tours FAB while in-tour) | ~680-759 | Stop-by-stop list with check/skip/number marks + ProgressBar. |
| Tour completion dialog | ~1615-1620 | "Stops completed X/Y, Skipped Z, Completion W%". The dialog itself stays — its **content** changes to Passport-based ("Stamps collected this walk: N / total in area"). |
| Numbered circle pin overlays on the polyline | `drawTourRoute()` ~1022-1145 | Currently drops a numbered pin at every stop POI coord. Polyline itself stays (authored polyline = primary tour visual); the numbered pins go. |

The runtime narration trigger (`NarrationGeofenceManager.checkPosition()` → `enqueueNarration()`) is unaffected — it never consulted `TourEngine.stops` to begin with.

## What `TourEngine` keeps vs sheds

**Keeps (still load-bearing for non-stops paths):**
- `startTour(tourId)` / `endTour()` / `pauseTour()` / `resumeTour()` lifecycle
- `tour_legs` polyline loading + `computeTourPolyline()` (this is the authored-polyline path)
- Detour state machine + return-to-tour routing
- `TourState` flow + UI observers (state transitions still drive map overlays, just not stop-based UIs)

**Sheds (dead after the four UIs go):**
- `currentStopIndex`, `completedStops`, `skippedStops`, `progress.totalStopCount`
- `advanceToNextStop()`, `skipStop()`, `maybeAdvanceOnEntry()` (auto-advance triggered by geofence ENTRY)
- `addStop()`, `removeStop()`, `reorderStops()` (already had no callers — pure dead code)
- `KEY_CURRENT_STOP_INDEX`, `KEY_COMPLETED_STOPS`, `KEY_SKIPPED_STOPS`, `KEY_CUSTOM_STOP_ORDER` SharedPrefs
- `TourSummary.completionPercent` (replaced by Passport stamps-collected)
- `TourPoi`, `currentPoi`, `nextPoi`, `remainingStops` accessors on `ActiveTour`
- `TourViewModel` wrappers: `getStopLocation(idx)`, `distanceToStop(idx)`, `isAtCurrentStop()`
- `tour_stops` Room table (assets-only — bake stops populating it)
- `publish-tour-stops.js` if it exists as a separate script (currently rolled into `publish-tour-legs.js`)

## What's new — POI Passport

### Data model

**PG (operator-side, web admin):**
- `salem_passport_filters` table — one row per active filter set. Columns roughly: `id`, `name` ("Default Salem Walking", "Tour-Specific: DensityTour", etc.), `tour_id NULLABLE` (NULL = global, non-NULL = tied to a specific tour), `min_geofence_radius_m`, `categories TEXT[]` (e.g., `{HISTORICAL_BUILDINGS, HISTORICAL_LANDMARKS, WORSHIP, CIVIC}`), `require_historical_narration BOOLEAN`, `require_image BOOLEAN`, `min_year_established INT NULL`, `max_year_established INT NULL`, `created_at`, `updated_at`. Operator edits via web admin.
- `salem_passport_pois` derived table — populated by the bake. Per-filter POI list. Columns: `filter_id`, `poi_id`, `display_order`. Computed by SQL against `salem_pois` filtered by the filter's parameters, sorted by name or geographic clustering.

**Room (Android, baked):**
- `poi_passport` table — flat list per active passport. Columns: `passport_id` (matches filter id), `poi_id`, `display_order`, `poi_name`, `poi_lat`, `poi_lng`, `poi_category`. Bake-time only; immutable at runtime.

**user_data.db (Android, runtime — already exists for poi_encounter_tracker):**
- `passport_visit` table — user's actual visit log. Columns: `passport_id`, `poi_id`, `first_heard_at_ms`, `last_heard_at_ms`, `heard_count`. Written by `NarrationGeofenceManager` on ENTRY (or by `PoiEncounterTracker` which already records observations).

### Bake pipeline

New script `cache-proxy/scripts/publish-poi-passport.js`. Reads `salem_passport_filters` from PG, runs each filter's SQL against `salem_pois`, writes results into the asset DB's new `poi_passport` table. Runs as part of the canonical publish chain, slotted between `publish-tour-legs.js` and `align-asset-schema-to-room.js`. Adds Room schema bump (v19 → v20) and asset verification entry.

Per `feedback_publish_chain_manual_after_admin_edits.md`: this becomes the 4th manual script.

### Web admin UI

New "Passport" sub-tab inside the existing Tours tab in `web/src/admin/`. Mirror of the existing Tour-edit panel: filter rows on the left, parameter form on the right, a "Preview matched POIs" button that runs the SQL and shows the count + sample list. "Save & Re-bake hint" toast tells operator to run `publish-poi-passport.js` next.

### Android UI

Replace the four killed surfaces with **one** Passport sheet:

- Entry point: a new icon in the toolbar (or repurpose the existing speaker-row "Tours" icon) → opens `PassportSheet`
- Content: scrollable list grouped by category. Each row = POI name + stamp icon (filled if heard, hollow if not) + "Heard X times" + "First heard: <date>". Tap row → opens `PoiDetailSheet` for that POI (existing infra).
- Header: "X / Y collected" overall summary
- Filter dropdown: if multiple passports exist (e.g., a Tour-Specific one is active), let user switch between the global Salem Passport and the active-tour Passport.
- During an active tour: optional small badge in the Passport icon ("3 new" since tour started)

Replaces the in-tour "Tours FAB → Active Tour dialog" with "Passport icon → Passport sheet" — works whether or not a tour is active.

### Wiring the visit flag

When `NarrationGeofenceManager` fires ENTRY for a POI, check if the POI id is in any active `poi_passport`. If yes, write/update `passport_visit(passport_id, poi_id, now)`. Existing `poi_encounter_tracker` already does observation recording; extend it or call alongside it.

Manual user tap on a POI marker that opens `PoiDetailSheet` and triggers narration also counts as "heard" (operator-confirmed: "POIs are triggered for narration by proximity OR if a user clicks on a POI to get information").

### Carry-over: resume-on-relaunch

User's Passport state persists in `user_data.db` independently of the tour state. So the existing TourEngine resume bug (saved index → wrong POI after re-author) becomes moot for the user's progress — Passport stamps are POI-id-keyed, not index-keyed. Tour resume can either be removed entirely (pause/resume becomes "stop tour / start tour again") or kept simple (just resume the polyline; no stops to track).

---

## Open questions for next session

1. **Per-tour Passport vs global Passport vs both.** Should a user see one big Salem Passport (all heard POIs over their whole trip), or per-tour Passports (Heritage Trail Passport, Density Tour Passport), or both? Operator's wording suggested per-tour-or-not flexibility ("regenerate the POI list depending on operator parameters") — likely both.
2. **What other operator parameters belong in the filter?** Suggested above: category list, geofence-radius floor, has-historical-narration, has-image, year-built range. Anything else? Tier (paid merchant)? Distance from user's start point? Walking-distance window?
3. **Tour completion dialog — keep as Passport summary, or kill entirely?** "Walk done — you collected 7 new stamps today (12/40 total Salem Passport)." Or just no dialog, the user opens the Passport themselves whenever.
4. **Stamp design.** Visual treatment — historic seal? Witch hat? Postage stamp? Operator-art call.
5. **Reset.** Should the user be able to reset their Passport (clear all stamps to start fresh)? If yes, settings entry. If no, simpler.
6. **Persistence scope.** `user_data.db` already exists. Confirm it's not wiped by `adb uninstall && install` in the field-test workflow (it should be — it's app-private storage). For real users, persists indefinitely.
7. **Sequencing with V1 ship target (2026-08-01).** This is a meaty feature (PG schema, bake script, web admin UI, Android Room v20, new sheet UI, deletion of four old surfaces). Realistic estimate: 3-5 sessions. Decide whether to ship in V1 or stage as V1.0.1.

---

## Critical files to touch (when implementation starts)

**Delete / shrink:**
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityTour.kt` — lines ~680-759 (Active Tour dialog), ~1022-1145 (numbered pins inside `drawTourRoute`), ~1327 (`updateTourHud`), ~1615 (completion-dialog content)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/TourEngine.kt` — lines ~158, 183, 206, 219, 250, 545, 717, 850 (stop-related methods + persistence)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/TourModels.kt` — `TourProgress`, `TourSummary`, stops fields on `ActiveTour`
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/content/model/TourStop.kt` — table can be dropped at v20
- `app-salem/src/main/res/layout/tour_hud_*.xml` (whatever the HUD layout file is) — delete

**Add:**
- `cache-proxy/scripts/publish-poi-passport.js`
- `cache-proxy/lib/admin-passport.js` (web admin endpoints)
- `web/src/admin/PassportTab.tsx` (new sub-tab)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/content/model/PoiPassport.kt` + DAO
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/PassportVisitDao.kt` (or extend existing PoiEncounterTracker)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/PassportSheet.kt` + layout
- `app-salem/schemas/.../20.json` (Room v20)
- PG migration adding `salem_passport_filters` + `salem_passport_pois`

**Touch:**
- `cache-proxy/scripts/verify-bundled-assets.js` — add `poi_passport` table count expectation
- `app-salem/src/main/AndroidManifest.xml` — no change
- `STATE.md` / `MASTER_SESSION_REFERENCE.md` — record the architecture transition
- `feedback_authored_tours_are_definitive.md` — extend to acknowledge Passport as the new progress model
- New memory: `feedback_passport_replaces_stops.md` — hard rule to never add a `currentStopIndex` style indicator again

---

## Verification plan

End-to-end test once implemented:
1. Web admin: create a "Default Salem Walking" passport filter (HISTORICAL_BUILDINGS + LANDMARKS + WORSHIP, requires historical_narration, geofence_radius >= 25m). Save. Preview shows ~150 POIs.
2. Run publish chain (now 5 scripts).
3. Build + install debug AAB on Lenovo via bundletool.
4. Open Passport icon — list shows ~150 POIs, all "unstamped".
5. Walk-sim past 5 historic POIs — each ENTRY narrates, Passport count updates "5 / 150".
6. Force-stop app, relaunch — Passport still shows 5/150 stamped.
7. Open one of the unstamped POIs by tap → narration fires → that one becomes stamped.
8. Reset Passport (if shipped) → all unstamped.
9. Confirm no top HUD, no Active Tour dialog, no numbered pins, no stop-completion dialog.

---

## Tone reminder

This is a feature deletion + architecture replacement, not a fix. Likely the largest deliberate UI redesign since pre-V1 pivot. Worth scoping carefully against ship date before starting. Operator decides cadence in next session.
