# Pixel 8 drive forensics — 2026-05-27 (S303)

Pull dir: `docs/device-pulls/pixel8-20260527-211101/`
Device: Pixel 8 (41231FDJH0018J). Build: versionName 1.0.0 / versionCode 10000, **DEBUGGABLE + LARGE_HEAP**, installed 2026-05-27 02:53 (the S302 demo / Full-Explore debug build).

## Headline
**The app never crashed, never ANR'd, was never killed.** No dropbox crash records, no ANR traces, no SIGSEGV for our package (the only SIGSEGVs in the buffers belong to an unrelated app, `om.diandian.gog`). Process **19686 started 19:15:24 and is still alive** (1h58m at pull time).

## Drive timeline (reconstructed from user_data.db + prefs + logcat)
- **19:06:17** — GPS track recording begins at the Beverly home (lat 42.557, lng −70.871). An *earlier* app process (pre-19686) is recording.
- **19:08:46 → 19:13:55** — **5-minute GPS dropout** (only a single fix at 19:13:55, then another 101s gap to 19:15:36). This is almost certainly the "issue at first start": no map movement / no POIs / no narration during this window. The earlier process appears to have stalled here.
- **19:15:24** — **Process 19686 starts** = the operator's restart. (No "Start proc"/death line survives in the ring buffer — cold-start logs aged out; only the DB preserves this window.)
- **19:15:36** — GPS resumes.
- **19:19:03** — POI encounters begin (John Cabot House, Historic Beverly — Beverly POIs exist).
- **19:21:27** — first narration fires (Beverly/Salem City Line).
- **19:24:46** — **GPS track recorder + encounter logger STOP** and never resume. NOT a location failure (see below) — matches `gps_track_visible=false`; this is the debug/recon observation logger, not core tour.
- **19:36:10** — Dr. K's tour (`tour_DrKs_001`) (re)started (`start_time` in tour_engine_prefs).
- **19:36:19** — **screen turned off** (`Blocking screen off` → `screen_off` → DreamManager dozing).
- **19:36:20** — `Activity pause timeout` + `top resumed state loss timeout` on SalemMainActivity. Main thread was busy (tour-start work; `salem-routing-graph.sqlite` 10.9 MB written at 19:36) during the screen-off pause → timeout warning. **Not a crash, no ANR dialog.**
- **19:36:24** — fresh Splash Screen (activity recreated when screen came back / relaunch). Same process.
- **19:36:42 → 19:55:35** — narration continues through Salem: **114 POIs heard after 19:24:46** incl. Salem Witch Museum, Roger Conant Statue, First Church, Abbott Street Cemetery.

## Two subsystems, two fates
| Subsystem | Data | Span | Status |
|---|---|---|---|
| Geofence → narration | `poi_visit` (125 rows) | 19:21:27 → 19:55:35 | **Worked all drive** |
| GPS-OBS recorder | `gps_track_points` (367), `poi_encounters` (59) | 19:06:17 → **19:24:46** | Stopped at 19:24:46 (matches gps_track_visible=false) |

## What was almost certainly the operator's "issue"
The **5-minute GPS dropout at 19:08–19:13**, immediately at first start, before the restart. After the 19:15 restart the tour ran correctly (125 narrations through Salem). The 19:36 splash/stall was a *separate*, benign screen-off transition mid-drive, not the start problem.

## Captured artifacts
- `logcat-{main,crash,system,events}.txt` (main reaches back to 05-26; app cold-start spew already rotated out)
- `app-19686.txt` (only 28 framework lines — app logged ~nothing to logcat this session)
- `appdata/databases/{user_data,salem_content}.db` (+ wal/shm)
- `appdata/shared_prefs/*.xml`
- `appdata/files/routing/salem-routing-graph.sqlite`, `files/geofence_databases/`
- `gps_track.csv`, `gps_track.geojson` (367-pt trail, Beverly→Salem, 19:06–19:24)
- `meminfo.txt` (idle: TOTAL PSS 419 MB / SWAP PSS 387 MB — fat demo build; Pixel-8-only per S302 Lenovo OOM caveat)

## Update — file-backed DebugLogger exists and captured the drive (S110 sink)
`DebugLogger.initFileSink()` is wired in `WickedSalemApp.kt:34` and writes to `<externalFilesDir>/logs/debug-YYYYMMDD.log` (daily rotation, 7-day retention, line-flushed, PID-stamped). The pull recovered **`debug-20260527.log` (27 MB / 207k lines)** spanning both processes. Split as `log-pid12601-procA.txt` (44,656 lines) + `log-pid19686-procB.txt` (162,649 lines).

## ROOT CAUSE — broken-motion-sensor gate, not GPS

Raw `onLocationResult` fired *continuously* in process A every ~14s (fixes #1→#77 across 19:06:17→19:15:20). The dropout in `gps_track_points` is downstream of the **GPS-OBS heartbeat**, which suppressed itself:

- **19:08:20 W/MOTION** — `armed=122s but lastMotionEventMs=0 (TB305FU-style broken significant-motion sensor? isStationary will stay true forever)` — **same symptom as the Lenovo** per `reference_lenovo_motion_sensor_broken.md`, **now confirmed on Pixel 8**.
- **19:09:35+ W/GPS-OBS** — `HEARTBEAT STALE … narration reach-out suppressed`, escalating: 49s, 79s, 109s, 139s, 169s, 199s stale. The pipeline went silent **by design**, gating on a motion sensor that wasn't ticking.
- App *looks* dead → operator force-closes at 19:15:22.

**Why process B "worked":** at **19:19:11** (≈3.5 min after restart) the Pixel's significant-motion sensor finally fired (`significant motion #1`); from there motion events kept coming, the heartbeat stayed healthy, narration ran. **But process B still went stale 21 times** (only 2 explicit recoveries logged) — the bug persisted; narration fired enough to mask it.

**This is a V1-relevant defect.** The carry-forward in `reference_lenovo_motion_sensor_broken.md` ("GPS cursor needs derived-speed escape hatch, not motion-sensor tuning") is the right fix — the heartbeat should consider derived speed from consecutive fixes when the motion sensor is quiet, not just trust `isStationary()`.

## Noise observed (not a bug, but a lot of warn-lines)
`W/TcpLogStreamer: TCP connect failed … ECONNREFUSED/ENETUNREACH` retrying every 10s the entire drive — the dev TCP log streamer trying to reach `10.0.0.194:4301` off-WiFi. Harmless but noisy; consider auto-disabling when on cellular or after N consecutive failures.
