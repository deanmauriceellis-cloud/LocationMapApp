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

## NOT YET TESTED — Resume Here

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
cd cache-proxy && DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
  JWT_SECRET=$(openssl rand -hex 32) \
  OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
  OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR node server.js
```
Note: Random JWT_SECRET means existing tokens won't work — users need to re-register.
