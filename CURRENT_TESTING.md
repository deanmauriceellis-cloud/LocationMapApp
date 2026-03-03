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

### On-Device UI Tests
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

### Bugs Fixed This Session
1. **Chat send bar obscured by nav bar** — added `fitsSystemWindows = true` to chat root layout
2. **"1 members" grammar** — pluralization logic for member count
3. **Socket.IO race condition** — `joinRoom` fired before socket connected; fixed with `suspendCancellableCoroutine` in `connect()` and sequential connect→join in ViewModel
4. **Idle auto-populate too aggressive** — threshold 5min → 10min; any UI activity now resets timer

### NOT YET TESTED — Resume Here
- [ ] **POI Comments on device** — tap a POI, see comments section, add a comment with rating, vote
- [ ] **Logout** — tap Logout in Profile, verify tokens cleared, Social shows Login again
- [ ] **Login (existing user)** — login with test@example.com / TestPass123, verify profile
- [ ] **Create room on device** — tap "+ New" in Chat Rooms, create room, verify it appears
- [ ] **Chat with other user** — register 2nd user, send messages between users (gray vs blue bubbles)
- [ ] **Token refresh** — wait 15min or simulate expiry, verify auto-refresh works
- [ ] **Error states** — wrong password, short password, empty fields, network down
- [ ] **Dark mode** — verify social dialogs render correctly in dark mode

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
Note: Random JWT_SECRET means test accounts' existing tokens won't work — users will need to re-login.
