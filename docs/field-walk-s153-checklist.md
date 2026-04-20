# S153 Field Walk Checklist — Exercise S150 Fixes 1 / 2 / 3 / 7

**Device:** Lenovo TB305FU (serial `HNY0CY0W`)
**APK:** S152 debug, 820 MB, installed 2026-04-19 (`a11a9fd`)
**Cold-boot-verified on Lenovo:** Fix 4 (GPS polling adaptive), Fix 5 (bbox default ON), Fix 6 (newspaper table baked). This walk exercises the remaining four.

---

## Pre-walk setup (2 minutes, before leaving)

- [ ] Confirm Lenovo tethered by USB, `adb devices` shows `HNY0CY0W device`.
- [ ] Clear prior session log on device:
      `adb -s HNY0CY0W shell rm -f /sdcard/Android/data/com.destructiveaigurus.katrinasmysticvisitorsguide/files/logs/*.log`
- [ ] Launch the app. Tap through splash → welcome → map.
- [ ] **Open the app settings → Audio → set "Audio Detail" to DEEP.** (This is the toggle that exercises Fix 1.)
- [ ] Confirm all four AudioControl group toggles (Oracle, Meaningful, Ambient, Businesses) are ENABLED. (Meaningful being on is what lets Phillips House / Hale Farm narrate for Fix 2.)
- [ ] Optionally disable the Businesses toggle as a **cross-check** for Fix 7 — if Grace Episcopal or Golden Dawn Contracting narrate with Businesses OFF, the NARR-GATE log will reveal which gate leaked.
- [ ] Unplug USB, drop phone in a pocket, walk outside.

---

## What to exercise (fixes 1 / 2 / 3 / 7)

### Fix 1 — Detail=DEEP flows long_narration to the ambient walk path

- [ ] Walk into any **meaningful** POI geofence (HISTORICAL_BUILDINGS, CIVIC, WORSHIP, WITCH_SHOP category — e.g., Salem Witch Museum, Old Town Hall, Essex Street Pedestrian Mall, The Witch House).
- [ ] Wait for the narration to speak. It should sound noticeably longer than STANDARD.
- [ ] Expected log line in `debug-YYYYMMDD.log`:
      ```
      NARR-PLAY: DIRECT PLAY: <poi_name> detail=DEEP bodyLen=<large number>
      NARR-STATE → Speaking(… type=LONG_NARRATION …)
      ```
- [ ] If `bodyLen` is small (or `detail=BRIEF` / `detail=STANDARD` appears), Fix 1 regressed.

### Fix 2 — Phillips House / Hale Farm narrate as HISTORICAL_BUILDINGS (not SKIP'd)

- [ ] Walk into **Historic New England's Phillips House** (34 Chestnut St) OR **Hale Farm** (5 Hale St, Beverly side — only if you happen to be out that way).
- [ ] Expected log lines when entering the geofence:
      ```
      NARR-GATE: <poi> category=HISTORICAL_BUILDINGS group=MEANINGFUL enabled=true …
      ```
      followed by the narration enqueuing and playing.
- [ ] If the log says `SKIP (AudioControl group muted)` or `category=ENTERTAINMENT`, Fix 2 regressed.
- [ ] Note: there are 23 POIs recategorized in S150 (12 HISTORICAL_BUILDINGS, 1 CIVIC, 10 WORSHIP) tagged `|category-fix-s150-2026-04-18` in `data_source`. Phillips House + Hale Farm are the most accessible of the 12; Ames Memorial Hall, Charter / Abbott / Greenlawn Cemeteries, Witch Trials Memorial, Salem Maritime NHP + Central Wharf, Colonial Hall, Historic Salem Inc., and John Cabot House are the rest if those aren't on your route.

### Fix 3 — GPS trail follows live movement (not frozen)

- [ ] Walk at a normal pace (> 0.5 m/s, i.e. anything faster than a shuffle).
- [ ] Watch the magenta polyline on the map. It should grow continuously, showing every GPS fix, not jump in 25 m chunks.
- [ ] Expected log pattern:
      ```
      GPS-OBS: … speedMps=0.8 … trailGrew=true
      ```
      every 2–3 seconds during the walk. Look for **no long gaps** in the `trailGrew=true` lines.
- [ ] If the trail visibly freezes while you're obviously moving, Fix 3 regressed.

### Fix 7 — NARR-GATE instrumentation diagnoses Grace / Golden-Dawn leak

- [ ] Walk into **any POI**. Every enqueue now emits a `NARR-GATE` line.
- [ ] Expected log format:
      ```
      NARR-GATE: <poi> category=<X> group=<Y> enabled=<bool> jumpToFront=<bool> (M=<bool> A=<bool> B=<bool>)
      ```
      where `M/A/B` = Meaningful/Ambient/Businesses toggle state, and `enabled` is the final per-POI speech gate.
- [ ] **If you turned off the Businesses toggle above**, and Grace Episcopal or Golden Dawn Contracting narrates anyway, grep `NARR-GATE:` for them after the walk:
      ```
      grep -E "NARR-GATE.*(grace_episcopal|golden_dawn)" debug-YYYYMMDD.log
      ```
      That line will reveal whether `isPoiSpeechEnabled` returned true unexpectedly or `groupForCategory` resolved to the wrong group.

---

## Post-walk log capture (5 minutes, back at desk)

- [ ] Re-tether USB.
- [ ] Pull the log:
      ```bash
      adb -s HNY0CY0W pull \
        /sdcard/Android/data/com.destructiveaigurus.katrinasmysticvisitorsguide/files/logs/ \
        ~/Development/LocationMapApp_v1.5/docs/session-logs/assets/s153/logs/
      ```
- [ ] Name the log clearly: `debug-20260420-walk.log`.
- [ ] Quick automated checks:
      ```bash
      # Fix 1 — DEEP detail + LONG_NARRATION
      grep -E "detail=DEEP.*bodyLen=[0-9]{3,}" debug-20260420-walk.log | head
      grep "type=LONG_NARRATION" debug-20260420-walk.log | head

      # Fix 2 — Phillips House / Hale Farm
      grep -iE "(phillips|hale).*category=HISTORICAL_BUILDINGS" debug-20260420-walk.log
      grep -iE "(phillips|hale).*SKIP" debug-20260420-walk.log   # should be 0

      # Fix 3 — trail growth during motion
      grep -cE "speedMps=[1-9]" debug-20260420-walk.log          # count of real-motion fixes
      grep "trailGrew=true" debug-20260420-walk.log | wc -l      # should scale with the count above

      # Fix 7 — NARR-GATE emitted + spot-check for false positives
      grep "NARR-GATE:" debug-20260420-walk.log | head
      grep -iE "NARR-GATE.*(grace_episcopal|golden_dawn)" debug-20260420-walk.log
      ```
- [ ] Record findings in the S153 live log under a "Field walk results" heading. Fix-by-fix: PASS / FAIL / INCONCLUSIVE.

---

## If something regressed

- **Fix 1 regression** (`detail=DEEP` not in log): check `NarrationGeofenceManager.getNarrationForPass` and confirm `AudioControl.detailLevel()` is reading from disk.
- **Fix 2 regression** (Phillips/Hale SKIP'd): re-verify PG and Room category — a future publish could have stomped the S150 `|category-fix-s150-2026-04-18` tag. Grep:
  ```
  sqlite3 app-salem/src/main/assets/salem_content.db \
    "SELECT id, category, data_source FROM salem_pois
     WHERE id IN ('historic_new_englands_phillips_house','hale_farm');"
  ```
- **Fix 3 regression** (trail frozen during walk): look at `MotionTracker.stationaryFrozen` + the speed-based escape hatch. Speed may be under-reporting from the GPS if the phone's in a pocket with poor sky view.
- **Fix 7 regression** (no NARR-GATE lines): instrumentation may have been stripped by R8 at some point; check `SalemMainActivityNarration.kt:905` in the current APK via `apkanalyzer`.

---

## Out-of-scope for this walk (explicit)

- APK size / icon-pack prune — separate pre-Play-Store audit (STATE.md item #10).
- 1692-victim-tribute burial-ground audit — separate scope (STATE.md item #9).
- SI phantom-coord fix — waits on SI (filed to OMEN S153).
- Unit test / OMEN-004 Phase 1 — deadline 2026-08-30, not this walk.
