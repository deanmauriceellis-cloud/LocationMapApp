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

### NOT YET TESTED — Resume Here

#### Security Hardening Verification
- [ ] **Sanitization**: `curl` POST comment with `<script>alert(1)</script>` — verify tags stripped
- [ ] **Rate limiting**: loop 15 register attempts — verify 429 after 10th
- [ ] **Login lockout**: 5 wrong passwords → verify 429, restart proxy → verify unlocked
- [ ] **Validation**: test empty names, 1-char names, HTML in names, 2000+ char messages — all rejected
- [ ] **Debug endpoint**: `curl /auth/debug/users` without auth → 401
- [ ] **Android input limits**: open each dialog, verify can't type beyond character limit
- [ ] **Comment char counter**: verify "0 / 1000" updates as you type

#### Social Layer (from Session 47)
- [ ] **Multi-user chat** — register 2nd user (via curl or 2nd device), verify gray bubbles for other users
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

### Proxy Startup (for next session)
```bash
cd cache-proxy && DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
  JWT_SECRET=$(openssl rand -hex 32) \
  OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
  OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR node server.js
```
Note: Random JWT_SECRET means test accounts' existing tokens won't work — users will need to re-register (device-bonded, no login flow).
