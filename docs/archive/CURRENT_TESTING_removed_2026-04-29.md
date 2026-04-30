# ARCHIVED 2026-04-29 — Pre-pivot platform vision

> **This document has been archived.** It describes the pre-pivot generic-LocationMapApp platform: freemium with ads, social Socket.IO chat/comments, Overpass POI scanner, R-tree TFR/military/aircraft geofence engine, BlueStacks emulator testing. **None of this ships in V1.**
>
> The live V1 product is **Katrina's Mystic Visitors Guide** — a $19.99 flat-paid, fully-offline Salem walking-tour app. V1 truth lives in `STATE.md`, `CLAUDE.md`, and `docs/session-logs/`.
>
> Preserved here for historical context only. Do not treat as a V1 spec or hand to counsel as live documentation.
>
> **Archived during S201 (V1 launch triage), 2026-04-29.**

---

*Original document follows.*

---

# Social Layer Testing — Status

## Completed (all passed)
- DDL & proxy setup (8 tables, JWT_SECRET, all routes)
- Proxy API: register, login, refresh, me, comments CRUD, votes, chat rooms, duplicate rejection
- On-device UI: grid dropdown 3×4, register, profile, chat rooms, real-time messages, Socket.IO
- POI marker click-through, comments with ratings, upvote, delete (soft), add comment dialog
- Device-bonded auth (register-only, no logout), room create/navigate, message persistence
- Security hardening: sanitization, rate limiting, login lockout, validation, debug endpoint protection
- Client-side input limits, comment char counter
- BlueStacks cross-platform: multi-user confirmed
- v1.5.48 tunings: zoom 18, radar 35%, idle density guard
- v1.5.49: automated POI import (full + delta)
- v1.5.50: Find dialog ScrollView, unlimited distance, 16 new subtypes
- v1.5.51-52: Smart fuzzy search (proxy API + on-device), header hints, distance expansion
- v1.5.53: Filter and Map mode (all tests passed)
- v1.5.55: MODULE_ID, toolbar layout (7 icons)
- v1.5.56: Distance-sorted search, Filter and Map button at top, adaptive zoom
- Refactoring: server.js decomposition (18 modules), 6 ViewModels, MenuPrefs.kt — build passes, pure structural
- v1.5.58: Overpass retry (proxy + app), zoom 16 labels, single-tap stop — build passes
- v1.5.59: Scan cell coverage + queue cancel — build passes, partial live testing (cells marking correctly, persistence working)

## Performance Optimization (2026-03-20)
- [x] Moved POI cluster icon generation off main thread (`renderPoiClusters`)
- [x] Moved POI marker icon generation off main thread (`replaceAllPoiMarkers`)
- [x] Batched station marker creation off main thread (`addStationMarkersBatched` — 257 stations)
- [x] Batched webcam marker creation off main thread (`addWebcamMarkersBatched` — 50 webcams)
- [x] TransitViewModel: all fetch methods use `Dispatchers.IO` (prevents DNS timeout on main thread)
- [x] Staggered MBTA restore in `onStart()` — 2s delay + 500ms gaps
- [x] Proxy quick-drain: Overpass results auto-imported to PostgreSQL within 2s
- [x] Frame skips reduced from 239 to ~35 per burst
- [ ] Remaining "Emulator not responding" dialog is Ubuntu GNOME `check-alive-timeout` (not app ANR)

## NOT YET TESTED — Resume Here

### v1.5.60 (proxy restarted — no app changes needed)
- [ ] Overpass search → verify POIs buffered and imported to DB (check proxy logs for `[Import Buffer]` + `[DB Import]`)
- [ ] Navigate to previously-scanned area → verify CELL hits still return POIs (from PostgreSQL now, check logs for `from DB`)
- [ ] `/cache/stats` → verify `maxCacheEntries: 2000`, `importBufferPending`, no `pois` field
- [ ] `/pois/bbox?s=42.3&w=-71.1&n=42.4&e=-71.0` → POIs returned from PostgreSQL
- [ ] `/pois/stats` → count from PostgreSQL (should be ~268k)
- [ ] `/poi/way/497190524` → single POI from PostgreSQL
- [ ] `/db/import/status` → shows `pendingDelta` from buffer
- [ ] Trigger manual import `POST /db/import` after some Overpass searches → verify buffer drains
- [ ] `poi-cache.json` can be safely deleted (90MB, no longer used)
- [ ] Lower `MAX_CACHE_ENTRIES=500` → restart → verify cache pruned to 500, heap drops further

### v1.5.59 (proxy restarted + app reinstalled)
- [ ] Scan cell CELL hits — navigate to previously-scanned area, check proxy logs for `X-Cache: CELL`
- [ ] Idle populate skip — start idle populate in well-scanned area, verify FRESH cells are skipped (no countdown, instant advance)
- [ ] Queue cancel — start populate, tap to stop, check proxy logs for `cancelled N queued requests`
- [ ] Silent fill FRESH — trigger silent fill in scanned area, verify "Coverage fresh" banner (1.5s)
- [ ] `/scan-cells` endpoint — verify `curl localhost:3000/scan-cells` returns cells
- [ ] `/scan-cells?lat=42.55&lon=-70.89` — verify specific cell query works
- [ ] `/cache/stats` — verify `scanCells` count present
- [ ] `/cache/clear` — verify scan cells cleared + file deleted
- [ ] Freshness expiry — set `SCAN_FRESHNESS_MS=1000`, verify cells go STALE and upstream resumes

### v1.5.58 (proxy restarted + app reinstalled)
- [ ] Overpass retry — run 10km Probe scan, check proxy logs for `[Overpass retry]` messages
- [ ] Overpass retry — verify error rate drops from ~13% to near zero
- [ ] Zoom 16 labels — POI full names visible at zoom 16 (was 18)
- [ ] Zoom 16 labels — train/subway/bus route, speed, destination visible at zoom 16 (was 18)
- [ ] Single tap stop — tap empty map while following a vehicle, verify follow stops
- [ ] Single tap stop — tap empty map while populate scan running, verify scan stops
- [ ] Single tap stop — tap on a marker still opens detail (not consumed by tap handler)

### v1.5.57 (needs proxy restart + app reinstall)
- [ ] Cuisine search — "pizza" returns pizza places (not just nearest Food & Drink)
- [ ] Cuisine search — "bbq" returns barbecue restaurants
- [ ] Cuisine search — "sushi" returns sushi restaurants
- [ ] New subtypes in Find grid — Boat Ramps visible in Parks & Rec
- [ ] New subtypes — Tattoo Shops, Cannabis, Barber Shops in Shopping
- [ ] New subtypes — Airports, Taxi Stands in Transit
- [ ] New subtypes — Disc Golf in Entertainment
- [ ] Filter and Map works with new subtypes
- [ ] Trigger scan near airport to verify `aeroway=aerodrome` imports
- [ ] Keyword search — "tattoo", "dispensary", "boat ramp", "disc golf" work

### v1.5.55 (needs user testing)
- [ ] Home button — tap centers map on GPS at zoom 18
- [ ] Home button no GPS — toast "No GPS fix yet"
- [ ] About button — dialog with v1.5.55, copyright, DestructiveAIGurus.com, email

### Social Layer (from Session 47)
- [ ] Token refresh — wait 15min or simulate expiry, verify auto-refresh
- [ ] Error states — duplicate registration, short password, empty fields, network down
- [ ] Dark mode — verify social dialogs render correctly
- [ ] Comment on POI with existing comments — verify comments load and display
- [ ] Downvote — test downvote on a comment

### Test Accounts
| User | Email | Created |
|------|-------|---------|
| TestUser | test@example.com | curl (proxy API test) |
| Dean | dean@test.com | on-device registration |
| SecurityTester | sectest@example.com | curl (security tests) |
| Mr. Nobody | (BlueStacks) | BlueStacks registration |

### Proxy Startup
```bash
# Use the helper script (recommended):
bin/restart-proxy.sh

# Or manually:
cd cache-proxy && DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
  JWT_SECRET=$(openssl rand -hex 32) \
  OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
  OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR node server.js
```
Note: Random JWT_SECRET means existing tokens won't work — users need to re-register.
**IMPORTANT:** Without `DATABASE_URL`, POIs will NOT appear on the map (`/db/*` endpoints return 503).

### Emulator Setup
- Use **Pixel_8a_API_34** AVD (API 34, not 36)
- AVD config (`~/.android/avd/Pixel_8a_API_34.avd/config.ini`) must have:
  - `hw.initialOrientation=landscape`
  - `showDeviceFrame=no`
  - `skin.name=1080x2400` / `skin.path=1080x2400` (not `pixel_8a`)
- Start: `emulator -avd Pixel_8a_API_34 -no-boot-anim -no-snapshot`
- After boot: `adb shell settings put global policy_control "immersive.full=com.example.locationmapapp"`
- Resize by dragging corner — do NOT use `wmctrl` fullscreen (crashes emulator)
- Install: `./gradlew installDebug`
- See STATE.md "Development Environment" section for full details.
