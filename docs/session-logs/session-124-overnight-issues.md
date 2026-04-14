# Session 124 — Heritage Trail overnight test — Issues to address

**Test window:** 2026-04-13 ~22:00 → 2026-04-14 ~05:30 EDT
**Walker:** Lenovo TB305FU tablet (serial HNY0CY0W), continuous /walk-tour loops
**Raw data:** `docs/session-logs/overnight-2026-04-14/logcat.log` (~100 MB), `metrics.log`, `auto-restart.log`, `supervisor.log`
**APK under test:** v1.5 + Phase 9R.0 Heritage Trail + hand-authored 41 POIs + 202 1692 newspaper dispatches

---

## Priority-ordered issue list for next session

### P0 — Critical architectural gaps

#### 1. Newspaper silence starvation
**Symptom:** User reported hearing only ~35 unique dispatches all night despite the 202-article corpus. Newspaper queue pointer parked at indices 35-63 for hours at a time.

**Root cause:** `SalemMainActivityNarration`'s silence branch (inside `tourViewModel.narrationState.collectLatest`) fires ONLY on state transitions (Speaking→Idle→Speaking). When POIs constantly interrupt each other via `cancelSegmentsWithTag("poi_narration")`, TTS state never rests at `Idle` for long enough → silence branch never fires → `newspaperSilenceSlotCounter` never ticks → no newspaper.

**Fix:** add a timer-based newspaper heartbeat. Coroutine that fires every N minutes (~5 min) regardless of narration state:
```kotlin
// In initNarrationSystem():
lifecycleScope.launch {
  while (isActive) {
    delay(5 * 60_000L)
    if (narrationGeofenceManager.isHistoricalMode()) {
      val h = historicalHeadlineQueue.pollNext()
      if (h != null) {
        tourViewModel.cancelSegmentsWithTag("poi_narration")  // yield to no POI for the newspaper
        tourViewModel.speakTaggedNarration("newspaper_1692", h.text, "Salem 1692 — ${h.date}", "en-au-x-auc-local")
        historicalHeadlineQueue.advance()
      }
    }
  }
}
```

#### 2. "Queue empty after filter" dead-end
**Symptom:** ~5-minute silence windows at 02:01-02:06 and similar. Single POI in `narrationQueue` got filtered out as "behind-user" → `playNextNarration()` returned silently → silence branch never re-triggered.

**Root cause:** Observer routes based on `narrationQueue.isEmpty()`. If queue has 1 item, it calls `playNextNarration()` which dequeues. But `pickNextFromQueue()` may filter out the one item, return null, log "queue empty after filter" and return. Silence branch (reach-out + newspaper) only fires when `narrationQueue.isEmpty()` is true at observer-transition time.

**Fix:** when `pickNextFromQueue()` returns null, fall through to the silence-fill branch instead of returning silently. Extract the silence-fill body into a function callable from both the observer's else-branch and the "queue empty after filter" path.

---

### P1 — Dual-walk bug (intermittent, partial fix shipped tonight)

#### 3. Concurrent Activity walkSimJob + DebugEndpoints walkJob
**Symptom:** Map flips between two positions, ~500ms alternation, seen multiple times tonight.

**Root causes compounded:**
- Phantom touch on Walk button (screen event deviceId=6 at coords 63×464) triggered `startWalkSim()` while DebugEndpoints `walkJob` already running via `/walk-tour`.
- Rapid `/walk-tour` calls (keeper + manual) created walkJob overlap in the cancel-window.
- Kotlin coroutine cancellation is **cooperative** — old walkJob continued emitting `viewModel.setManualLocation(point)` for up to 1-2s after `cancel()` while suspended in `withContext(Dispatchers.Main)`.

**Fix deployed tonight (partial):**
- Added `DebugEndpoints.cancelAnyWalk()` public method, called from `Activity.startWalkSim()`.
- Added `Activity.stopWalkSimExternal()` public method, called from `DebugEndpoints.handleWalkTour()`.
- Both paths now cross-cancel before starting.

**Still needed:**
- Cancel must be **synchronous** — wait for old coroutine to fully exit before starting new one. Use `walkJob?.cancelAndJoin()` inside a coroutine instead of fire-and-forget `cancel()`.
- Guard with a mutex around walk start/stop transitions so only one start is in flight at a time.

---

### P2 — Filter integrity + content quality

#### 4. Modern businesses leaking via SI medium_narration sync
**Symptom:** Tour companies, witch shops, halloween museums narrating in Historical Mode: Spellbound Tours, Salem Tales, Salem Arts Association, 1692 Before and After LLC, World of Wizardry, Witch History Museum, Salem Historical Tours, Vampfangs, Salem Ghosts Tours, etc.

**Root cause:** `sync-historical-notes-from-intel.js` falls back to `medium_narration` (SI's general-purpose narration) when no dedicated `historical_note` exists. For ~1,100 SI-linked POIs this wrote business descriptions into the `historical_note` column. Many of these POIs are miscategorized as `TOURISM_HISTORY` at BCS import time, so they pass the categorical-historical gate.

**Fix paths:**
- **(a) Stop sync fallback:** remove `fallback_medium_narration` source. Only write historical_note when SI's dedicated endpoint returns content.
- **(b) Track provenance in DB:** add `historical_note_source` column (e.g., `si_historical_note`, `fallback_medium`, `hand_authored`). Filter on source in the narration gate — only trust hand-authored or dedicated SI content.
- **(c) Data cleanup:** extend `reclassify-modern-attractions.sql` with a broader sweep — any TOURISM_HISTORY POI whose name contains "Tours", "Museum" (recent era), "Experience", "Witch Shop" → reclassify.

#### 5. Tour-stop TourEngine path bypasses Historical Mode
**Symptom:** Salem Common fired `type=SHORT_NARRATION` with id=`short_salem_common`, using `point.shortNarration` (shorter legacy text), bypassing my 2:1 newspaper interleave and historical_note preference.

**Root cause:** `TourEngine.handleGeofenceEvent` calls `narrationManager.speakShortNarration(event.poi)` which uses `poi.shortNarration` directly — it doesn't go through `NarrationGeofenceManager.getNarrationForPass()` which has the Historical Mode branch.

**Fix:** in `TourEngine.handleGeofenceEvent`, when `narrationGeofenceManager.isHistoricalMode()` is true, route tour-stop ENTRY through the historical_note path instead of `speakShortNarration`. Or simply: disable the TourGeofenceManager path entirely in Historical Mode since `NarrationGeofenceManager` already covers tour stops via whitelist.

---

### P3 — Operational / lifecycle

#### 6. `narratedAt` doesn't persist across process restarts
**Symptom:** 18 app PID forks tonight (force-stops for APK installs, crashes, etc.). Each wiped in-memory `narratedAt`. POIs re-narrated within 1-hour window after any restart.

**Fix:** mirror `narratedAt` to SharedPreferences, same pattern as `HistoricalHeadlineQueue.nextIndex`. Load at init, write on every `narratedAt[id] = now`, purge on load. SharedPreferences is async, minimal I/O overhead.

#### 7. Activity-pause breaks `/walk-tour` endpoint
**Symptom:** `{"error":"Activity not active"}` responses when tablet screen locked. Keeper fires walk-tour, fails, retries 60s later, eventually succeeds when screen wakes.

**Mitigation applied tonight:** `adb shell svc power stayon true` — tablet keeps screen on while charging.

**Root fix:** hold a wake lock in the app during walk-sim, OR detect Activity paused state in DebugEndpoints.handleWalkTour and defer instead of rejecting.

#### 8. Tour progress HUD stuck at `Tour: 1/10 — Unknown`
**Symptom:** Cosmetic — walker passes all 10 stops but HUD never advances.

**Root cause:** `TourEngine.advanceToNextStop()` is only called on explicit user action (Next button), not on geofence ENTRY.

**Fix:** in `TourEngine.handleGeofenceEvent(ENTRY)`, advance the current stop index if `event.poi.id == activeTour.stops[currentStopIndex].poiId`.

---

### P4 — Minor / data issues

#### 9. Out-of-order newspaper dispatches
**Symptom:** Dispatch #44 (April 13, 1692) fired before dispatch #45 (April 8, 1692).

**Root cause:** `publish-1692-newspapers.js` uses `ORDER BY date ASC, id ASC` which should be correct. Need to verify SI's `/salem-1692/export` returns date-consistent data, or add explicit re-sort on the Kotlin side.

#### 10. `the_burying_point` duplicate content (fixed mid-night)
Mirror-copy from `charter_street_cemetery` caused identical narrations ~2 min apart. NULLed `the_burying_point.historical_note` at 01:56. Both POIs are the same physical location; longer-term, soft-delete one in the dedup sweep (already tracked in STATE.md carry-forward).

#### 11. Keeper walk_still_running has 15s detection window
**Symptom:** When an auto-walk completes naturally, up to 60s of silence before keeper notices and fires next.

**Fix options:**
- Lower keeper poll interval from 60s → 15s.
- Hook walk-completion into the DebugEndpoints and proactively notify the keeper (webhook-like), OR just poll aggressively.

---

## Summary of what shipped to the APK tonight

- 41 hand-authored tour-guide-voice `historical_note` values (4 cemeteries + 12 historic houses/monuments + 25 witch-trial sites + maritime landmarks).
- 1,143 POIs now have `historical_note` populated (up from ~180 at session start).
- Dual-walk cross-cancellation guards in `startWalkSim()` and `handleWalkTour()` (partial fix — needs `cancelAndJoin` for full).
- Newspaper queue loops via `pollNext()` wrap-around.
- 202 1692 newspaper dispatches as a bundled JSON asset (`assets/salem_1692_newspapers.json`, ~926 KB).
- 2:1 POI/newspaper silence-fill ratio via `newspaperSilenceSlotCounter`.
- POI ↔ POI interrupt with 10-second min-hold guard.
- AU female voice for newspaper dispatches (`en-au-x-auc-local`).

## Standing overnight processes (killed at session end)

- `auto-restart-walk.sh` (keeper, 60s poll) — killed
- `supervisor.sh` (5-min watchdog) — killed
- `adb logcat -T 1 -v time > logcat.log` — still running (may or may not survive shell exit)
- `metrics-collector.sh` (15-min snapshot) — still running

**Log files in `docs/session-logs/overnight-2026-04-14/`:**
- `logcat.log` — full tablet log stream, all app activity 01:04 → 05:26
- `metrics.log` — 15-min sampled metrics (POI counts, dispatch counts, errors)
- `auto-restart.log` — every keeper action
- `supervisor.log` — watchdog restarts
- `review-cheatsheet.sh` — helper to run on logcat.log for quick post-mortem

## Next session TOP PRIORITY

1. **Ship fix for P0 item #1 (newspaper heartbeat)** — this single change dramatically improves the audio experience on long walks.
2. **Ship fix for P0 item #2 (queue-filter dead-end)** — same function extraction, second biggest audibility win.
3. **P1 item #3 (dual-walk cancelAndJoin)** — prevents the map-bounce UX bug that bit multiple times tonight.
4. **P2 item #4 (sync provenance tracking)** — keeps modern businesses out of Historical Mode without requiring category cleanup of 1,100 POIs.
