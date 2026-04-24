# Social Layer — Implementation Plan

## Status Summary

| Phase | Version | Status | Description |
|-------|---------|--------|-------------|
| A | v1.5.45 | DONE | Auth (register/login, JWT, profile, 3x4 grid) |
| B | v1.5.45 | DONE | POI Comments (star ratings, votes, delete) |
| C | v1.5.45 | DONE | Chat Rooms (Socket.IO real-time, global room) |
| D | v1.5.46 | NOT STARTED | Room Management (mod tools, bans, room settings) |
| Gov | — | NOT STARTED | Governance checkpoints (COPPA, encryption, moderation) |

## What's Built (Phases A-C)

### Proxy (server.js)
- `argon2`, `jsonwebtoken`, `socket.io` dependencies
- `http.createServer(app)` + `io.attach(server)` (was `app.listen`)
- JWT config: `JWT_SECRET` env var, 15min access tokens, 30d refresh tokens
- `requireAuth(req, res, next)` middleware — validates Bearer token, sets `req.user`
- `generateTokens(user)` — JWT + opaque refresh, stores hash in DB
- `sha256(str)` helper
- 5 auth endpoints: register, login, refresh, logout, /auth/me + debug/users
- 4 comment endpoints: GET comments, POST comment, POST vote, DELETE
- 3 chat REST endpoints: GET rooms, POST rooms, GET messages
- Socket.IO: auth middleware, join_room, leave_room, send_message, typing events
- `ensureGlobalRoom()` on startup

### Android
- **Models.kt**: AuthUser, AuthTokens, AuthResponse, PoiComment, CommentsResponse, ChatRoom, ChatMessage
- **AuthRepository.kt**: register, login, refreshTokens, logout, fetchProfile, getValidAccessToken, authenticatedRequest
- **CommentRepository.kt**: fetchComments, postComment, voteOnComment, deleteComment
- **ChatRepository.kt**: connect/disconnect (Socket.IO), joinRoom/leaveRoom, sendMessage, fetchRooms, createRoom, fetchMessages
- **AppModule.kt**: 3 new providers
- **MainViewModel.kt**: auth LiveData + methods, comments LiveData + methods, chat LiveData + methods
- **MenuEventListener.kt**: onSocialRequested, onChatRequested, onProfileRequested
- **AppBarMenuManager.kt**: Row 3 buttons (Social, Chat, Profile, Legend)
- **grid_dropdown_panel.xml**: gridRow3
- **MainActivity.kt**: showAuthDialog, showProfileDialog, showChatDialog, showChatRoomDialog, showAddCommentDialog
- **Icons**: ic_social.xml, ic_chat.xml, ic_profile.xml, ic_legend.xml
- **build.gradle**: io.socket:socket.io-client:2.1.0

### Database Tables (DDL — not yet run)
```sql
-- Run as: sudo -u postgres psql -d locationmapapp

-- Auth tables
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name    VARCHAR(50) NOT NULL,
    email           TEXT,
    password_hash   TEXT NOT NULL,
    platform_role   VARCHAR(20) NOT NULL DEFAULT 'user',
    is_banned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users (email) WHERE email IS NOT NULL;
GRANT SELECT, INSERT, UPDATE, DELETE ON users TO witchdoctor;

CREATE TABLE auth_lookup (
    email_hash  VARCHAR(64) PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
);
GRANT SELECT, INSERT, DELETE ON auth_lookup TO witchdoctor;

CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
GRANT SELECT, INSERT, UPDATE, DELETE ON refresh_tokens TO witchdoctor;

-- Comment tables
CREATE TABLE poi_comments (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    osm_type    VARCHAR(10) NOT NULL,
    osm_id      BIGINT NOT NULL,
    user_id     UUID NOT NULL REFERENCES users(id),
    parent_id   BIGINT REFERENCES poi_comments(id),
    content     TEXT NOT NULL CHECK (length(content) <= 2000),
    rating      SMALLINT CHECK (rating BETWEEN 1 AND 5),
    upvotes     INTEGER NOT NULL DEFAULT 0,
    downvotes   INTEGER NOT NULL DEFAULT 0,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_poi_comments_poi ON poi_comments (osm_type, osm_id, created_at DESC);
GRANT SELECT, INSERT, UPDATE, DELETE ON poi_comments TO witchdoctor;

CREATE TABLE comment_votes (
    comment_id  BIGINT NOT NULL REFERENCES poi_comments(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id),
    vote        SMALLINT NOT NULL CHECK (vote IN (-1, 1)),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON comment_votes TO witchdoctor;

-- Chat tables
CREATE TABLE chat_rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type       VARCHAR(20) NOT NULL DEFAULT 'public',
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    created_by      UUID REFERENCES users(id),
    member_count    INTEGER NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_rooms TO witchdoctor;

CREATE TABLE room_memberships (
    room_id     UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'member',
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    muted_until TIMESTAMPTZ,
    PRIMARY KEY (room_id, user_id)
);
GRANT SELECT, INSERT, UPDATE, DELETE ON room_memberships TO witchdoctor;

CREATE TABLE messages (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id     UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id),
    content     TEXT NOT NULL CHECK (length(content) <= 2000),
    reply_to_id BIGINT REFERENCES messages(id),
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_messages_room ON messages (room_id, sent_at DESC);
GRANT SELECT, INSERT, UPDATE, DELETE ON messages TO witchdoctor;
```

### Proxy Startup (updated)
```bash
cd cache-proxy && DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
  JWT_SECRET=$(openssl rand -hex 32) \
  OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
  OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR node server.js
```

---

## Phase D: Room Management (v1.5.46) — Next Up

### D.1 — Database Table
```sql
CREATE TABLE room_bans (
    room_id     UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id),
    banned_by   UUID NOT NULL REFERENCES users(id),
    reason      TEXT,
    expires_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (room_id, user_id)
);
GRANT SELECT, INSERT, DELETE ON room_bans TO witchdoctor;
```

### D.2 — Proxy: Room Management Endpoints (~150 lines)
- `POST /chat/rooms/:id/members/:userId/promote` (requireAuth) — promote member to moderator/admin
- `POST /chat/rooms/:id/members/:userId/demote` (requireAuth) — demote moderator to member
- `POST /chat/rooms/:id/members/:userId/mute` (requireAuth) — mute for N minutes
- `POST /chat/rooms/:id/members/:userId/kick` (requireAuth) — remove from room
- `POST /chat/rooms/:id/members/:userId/ban` (requireAuth) — ban from room
- `GET /chat/rooms/:id/members` — list room members with roles
- `PATCH /chat/rooms/:id` (requireAuth) — update room name/description
- Check role hierarchy: only admin can promote/demote, mod+ can mute/kick/ban

### D.3 — Android: Room Settings Dialog (~300 lines)
- Room settings button in chat room header (gear icon)
- Member list with role badges (admin/mod/member)
- Action buttons per member: promote, demote, mute, kick, ban (based on viewer's role)
- Edit room name/description (admin only)

### D.4 — Socket.IO: Room Events (~100 lines)
- `user_kicked`, `user_banned`, `user_muted` events
- Check ban list on join_room, reject banned users
- Check mute on send_message, reject muted users

**Estimated: ~700 lines total**

---

## Governance Checkpoints (Post-Prototype)

These are from GOVERNANCE.md and should be layered on after the prototype works:

### Priority 1 — Before Public Release
- [ ] COPPA age gate (13+ verification) — deadline April 22, 2026
- [ ] Privacy policy + terms of service
- [ ] Email encryption (AES-256-GCM) at rest
- [ ] Token storage upgrade: DataStore + Android Tink (not SharedPreferences)
- [ ] Rate limiting on auth endpoints (brute force protection)
- [ ] Input sanitization beyond length checks

### Priority 2 — Content Safety
- [ ] Content moderation pipeline: client blocklist → server blocklist → Perspective API
- [ ] Report system: users can flag comments/messages
- [ ] Shadow ban capability (user sees own posts, others don't)
- [ ] IP-based blocks for ban evasion

### Priority 3 — Scale & Polish
- [ ] OAuth/Google Sign-In (Tier 2 identity)
- [ ] Monthly message table partitioning
- [ ] Message search
- [ ] Push notifications (FCM)
- [ ] Room discovery/search
- [ ] User blocking (client-side + server-side)
- [ ] Read receipts / online indicators
- [ ] File/image attachments

---

## Testing Checklist

### Phase A-C (current — run after DDL)
- [ ] Run DDL (6 tables + grants)
- [ ] Start proxy with JWT_SECRET
- [ ] `curl` register → login → refresh → /auth/me with Bearer token
- [ ] `curl` GET /auth/debug/users — see registered user
- [ ] Open app, tap Social in grid, register new account
- [ ] See welcome toast, tap Profile to see profile dialog
- [ ] Logout, login again
- [ ] Open POI detail → see "Comments" section (empty)
- [ ] Tap "Add" → post a comment with 4-star rating
- [ ] See comment appear with author name, stars, timestamps
- [ ] Vote on comment, see count update
- [ ] Tap Chat → see room list with "Global" room
- [ ] Tap Global → see chat room, send a message
- [ ] Open second device/browser → see message arrive in real-time
- [ ] Create a new room → see it in room list

### Phase D (after implementation)
- [ ] Run DDL (room_bans table)
- [ ] Create room, promote another user to mod
- [ ] Mute a user, verify they can't send messages
- [ ] Kick a user, verify they're removed
- [ ] Ban a user, verify they can't rejoin
