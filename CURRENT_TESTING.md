# Social Layer Testing — In Progress

## Session 46 (2026-03-03)

### DDL & Proxy
- [x] 8 social tables created via `create-social-tables.sh` (`sudo -u postgres`)
- [x] Proxy restarted with `JWT_SECRET` env var — all social routes registered
- [x] Proxy confirmed: Auth, Chat, Comments, Socket.IO all listed at startup

### Proxy API Tests (curl)
- [x] POST /auth/register — user created, tokens returned
- [x] POST /auth/login — tokens returned, correct displayName
- [x] GET /auth/me — profile returned
- [x] POST /comments — comment created with rating
- [x] GET /comments/:type/:id — comment with authorName + content
- [x] POST /comments/:id/vote — upvote counted
- [x] GET /chat/rooms — Global + Boston Area listed
- [x] POST /chat/rooms — "Boston Area" created
- [x] Duplicate register — rejected correctly
- [x] POST /auth/refresh — new tokens returned
- [x] GET /auth/debug/users — all users listed

### On-Device UI Tests (Session 46)
- [x] Grid dropdown — 3×4 grid (12 buttons), Row 3: Social, Chat, Profile, Legend
- [x] Social → Login dialog — email + password fields, Login button, Register link
- [x] Register dialog — Display Name, Email, Password fields, Register button
- [x] Register user "Dean" — success, dialog dismissed, tokens stored in auth_prefs
- [x] Profile dialog — blue avatar "D", name "Dean", role "USER", UUID, red Logout button
- [x] Chat Rooms dialog — "Boston Area" (1 member), "Global" (0 members), "+ New" button
- [x] "1 member" grammar fix — confirmed working
- [x] Global chat room — back arrow, title, close, empty messages, send bar
- [x] Send bar visibility — `fitsSystemWindows` fix, input fully visible above nav bar
- [x] Message history — "Hello from the Global chat" shown as blue bubble with "8m" timestamp
- [x] Real-time message delivery — "Testing real-time delivery" appeared as "just now" bubble
- [x] Socket.IO connect-before-join fix — messages now appear immediately

### On-Device UI Tests (Session 47)
- [x] **POI marker tap → detail dialog** — tapping POI marker now opens detail dialog directly (was info-window-only before)
- [x] **POI Comments** — comments section visible in detail dialog with "Comments" header + "+ Add" button
- [x] **Add comment with rating** — 4-star rating + text "Great college with beautiful campus" posted successfully
- [x] **Comment display** — author "Dean" (cyan), "just now" timestamp, star rating, comment text
- [x] **Upvote** — ▲ count incremented to 1 (green), ▼ stays 0
- [x] **Delete comment** — soft delete shows "[deleted]" text, author/rating preserved
- [x] **Delete button hidden on deleted comments** — fixed (was showing before)
- [x] **Profile dialog** — no Logout button (device-bonded auth model)
- [x] **Auth dialog** — Register-only (no login/logout toggle), "bonded to device" info text
- [x] **Create room on device** — "North Shore Spotters" created with description, toast "Room created"
- [x] **Room list updated** — 3 rooms: North Shore Spotters, Global, Boston Area
- [x] **Send message in new room** — blue bubble "First message in North Shore Spotters!" with "just now"
- [x] **Back arrow** — returns to room list from chat room
- [x] **Room navigation** — switch between rooms, each shows correct message history
- [x] **Message persistence** — Global room shows previous session messages (1h, 51m, 33m ago)
- [x] **Message verified in DB** — proxy API confirms message stored with correct author/content

### Bugs Fixed Session 46
1. **Chat send bar obscured by nav bar** — added `fitsSystemWindows = true` to chat root layout
2. **"1 members" grammar** — pluralization logic for member count
3. **Socket.IO race condition** — `joinRoom` fired before socket connected; fixed with `suspendCancellableCoroutine` in `connect()` and sequential connect→join in ViewModel
4. **Idle auto-populate too aggressive** — threshold 5min → 10min; any UI activity now resets timer

### Changes Made Session 47
1. **POI marker click-through** — added `setOnMarkerClickListener` + `openPoiDetailFromPlace()` to open detail dialog from map marker tap
2. **PlaceResult.id format** — now stores `"type:id"` (e.g., "way:292110841") matching FindResult convention; both parsers updated
3. **Device-bonded auth** — removed Login toggle, removed Logout button, register-only flow
4. **Delete button on deleted comments** — hidden when `isDeleted` is true
5. **Refresh token lifetime** — 30 days → 365 days (device-bonded accounts)
6. **Chat toast** — "Log in first" → "Register first"

### Security Hardening (Session 48 — v1.5.47)
- Server-side: sanitizeText/sanitizeMultiline, rate limiting (express-rate-limit), login lockout, tightened validation
- Client-side: InputFilter.LengthFilter on all social EditTexts, min-length/email validation, comment char counter
- `/auth/debug/users` now requires auth + owner/support role

### Completed — Security Hardening Verification

#### Security Hardening Verification — ALL PASS (Session 49, 2026-03-03)
- [x] **Sanitization**: `<script>alert(1)</script>Nice place!` → `alert(1)Nice place!`; HTML tags fully stripped
- [x] **Rate limiting**: 429 after 9th request (1 login + 8 registers = 10 total = limit hit) — PASS
- [x] **Login lockout**: 5 wrong passwords → 401 each, 6th → 429 locked, correct password also blocked — PASS
- [x] **Validation**: empty name (rejected), 1-char name (rejected), HTML in name (sanitized to "Bad"), 2001-char comment (truncated to 1000), rating=6 (rejected), invalid osmType (rejected), 200-char password (rejected), bad email (rejected) — ALL PASS
- [x] **Debug endpoint**: no auth → 401, regular user → 403 "Insufficient privileges" — PASS
- [x] **Android input limits**: InputFilter.LengthFilter confirmed on all 7 social EditTexts (50/255/128/100/255/1000/1000)
- [x] **Comment char counter**: "0 / 1000" visible, updates live to "32 / 1000" after typing — PASS
- **Note**: proxy must be restarted to pick up server.js changes (Node caches at startup); app must be reinstalled for client-side changes

### Completed — BlueStacks Cross-Platform (Session 49)
- [x] **BlueStacks install** — APK installs and runs on BlueStacks Windows emulator
  - Needed `adb uninstall` first (signature mismatch from prior debug key)
  - BlueStacks reaches proxy at 10.0.0.4:3000 over local network
  - Both devices (Android emulator + BlueStacks) traffic visible in proxy logs
  - "Mr. Nobody" comment posted from BlueStacks confirmed in proxy logs
- [x] **Multi-user confirmed** — two separate device registrations hitting proxy simultaneously

### Completed — Startup/Behavior Tuning (Session 50)
- [x] **Default zoom 14 → 18** — first GPS fix, goToLocation, long-press all zoom to 18
- [x] **Radar alpha 70% → 35%** — default radar overlay now 50% more translucent
- [x] **Idle auto-populate POI density guard** — queries `/db/pois/counts` 10km radius; skips if ≥100 POIs nearby
- [x] **Build passes** — assembleDebug succeeds

### Completed — Automated POI DB Import (Session 51)
- [x] **Full import** — 178,395 POIs upserted in 217s on manual trigger
- [x] **Delta import** — 1,996 new POIs in 9s (only recent arrivals)
- [x] **GET /db/import/status** — returns lastImportTime, pendingDelta, running, stats
- [x] **POST /db/import** — manual trigger returns `{ upserted, skipped, totalInDb, elapsed }`
- [x] **GET /cache/stats** — `dbImport` object included with import stats
- [x] **DB verified** — `SELECT count(*) FROM pois` = 180,059 after imports
- [x] **pendingDelta** — drops to 0 after successful import
- [x] **15-min timer** — setInterval registered at startup (guarded by pgPool)

### Completed — Find Dialog Overhaul (Session 52 — v1.5.50)
- [x] **ScrollView main grid** — all 18 cells visible, scrolls to bottom row (Entertainment, Offices)
- [x] **ScrollView subtype grid** — Shopping (26 subtypes), Entertainment (18 subtypes) all scrollable
- [x] **Cell height cap** — cells properly sized with 120dp max, no oversized cells
- [x] **Count badges** — all categories show counts after back-navigation (Food 271, Parks 1.1k, Shopping 274, etc.)
- [x] **New Shopping subtypes** — Pet Stores (6), Electronics (2), Bicycle Shops (3), Garden Centers (9) visible
- [x] **New Entertainment subtypes** — Water Parks, Mini Golf (1), Escape Rooms (1) visible
- [x] **Unlimited distance** — zoo search returned 5 results at 694–1413km (`scope_m: 0` global fallback)
- [x] **`craft` category key** — added to server.js POI_CATEGORY_KEYS (breweries etc. will appear after next import)

### Completed — Smart Fuzzy Search DDL (Session 53 — v1.5.51)
- [x] **pg_trgm extension** — created via `sudo -u postgres`
- [x] **GIN trigram index** — `idx_pois_name_trgm` on pois.name
- [x] **Build passes** — assembleDebug succeeds

### NOT YET TESTED — Resume Here

#### v1.5.51 Smart Fuzzy Search
- [ ] **Proxy restart** — restart proxy to load new `/db/pois/search` handler
- [ ] **Fuzzy typo search** — `curl "localhost:3000/db/pois/search?q=starbcks&lat=42.36&lon=-71.06&limit=5"` returns Starbucks results with similarity scores
- [ ] **Keyword category hint** — `curl "localhost:3000/db/pois/search?q=historic&lat=42.36&lon=-71.06&limit=10"` returns `category_hint: "Tourism & History"` with tourism POIs
- [ ] **Combined keyword+name** — `curl "localhost:3000/db/pois/search?q=historic+church&lat=42.36&lon=-71.06"` returns Tourism & History + fuzzy "church"
- [ ] **Keyword-only browse** — `curl "localhost:3000/db/pois/search?q=gas&lat=42.36&lon=-71.06"` returns Fuel & Charging POIs by distance
- [ ] **Distance expansion** — rare query expands beyond 50km scope
- [ ] **On-device: keyword search** — type "gas" in Find search bar → shows "Showing Fuel & Charging" hint + nearby gas stations
- [ ] **On-device: fuzzy search** — type "Dunkin" → fuzzy-matches Dunkin' Donuts by distance
- [ ] **On-device: rich result rows** — verify 3-line layout (bold name, gray detail, colored category)
- [ ] **On-device: result footer** — verify count + scope label at bottom
- [ ] **On-device: category hint chip** — verify cyan "Showing X" label on keyword searches
- [ ] **On-device: debounce** — verify 1s delay before search fires (not 500ms)

#### v1.5.48 Tunings
- [x] **Zoom 18 on first GPS fix** — verified by user, street-level view on fresh app start
- [x] **Zoom 18 on Go To** — verified by user, zooms to 18
- [x] **Radar transparency** — verified by user, noticeably more transparent
- [ ] **Idle populate skip** — in a dense area (DC), verify idle populate does NOT trigger after 10 min

#### v1.5.50 Find Dialog (remaining)
- [ ] **Food & Drink subtypes** — verify Breweries/Wineries/Distilleries visible (15 subtypes)
- [ ] **Civic & Gov subtypes** — verify Recycling/Embassies visible (9 subtypes)
- [ ] **Tourism & History subtypes** — verify Zoos/Aquariums/Theme Parks visible (15 subtypes)
- [ ] **Parks & Rec subtypes** — verify Beaches visible (15 subtypes)
- [ ] **Find results tap** — tap a subtype, verify distance-sorted results load + tap opens POI detail

#### Social Layer (from Session 47)
- [x] **Multi-user chat** — BlueStacks as 2nd device confirmed traffic; verify gray bubbles visually
- [ ] **Token refresh** — wait 15min or simulate expiry, verify auto-refresh works
- [ ] **Error states** — duplicate registration, short password, empty fields, network down
- [ ] **Dark mode** — verify social dialogs render correctly in dark mode
- [ ] **Comment on POI with existing comments** — verify comments load and display correctly
- [ ] **Downvote** — test downvote on a comment

### Test Accounts
| User | Email | Password | Created |
|------|-------|----------|---------|
| TestUser | test@example.com | TestPass123 | curl (proxy API test) |
| Dean | dean@test.com | password123 | on-device registration |
| SecurityTester | sectest@example.com | SecurePass123 | curl (session 49 security tests) |
| RateUser1-8 | rate1-8@test.com | TestPass123 | curl (rate limit test, session 49) |
| Mr. Nobody | (BlueStacks) | — | BlueStacks registration (session 49) |

### Proxy Startup (for next session)
```bash
cd cache-proxy && DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
  JWT_SECRET=$(openssl rand -hex 32) \
  OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
  OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR node server.js
```
Note: Random JWT_SECRET means test accounts' existing tokens won't work — users will need to re-register (device-bonded, no login flow).
