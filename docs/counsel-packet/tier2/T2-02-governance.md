# LocationMapApp — Social Layer Governance

> Reference document for legal, privacy, technical, and architectural decisions governing the social/community features of LocationMapApp.

## Last Updated: 2026-03-03

---

## Table of Contents

1. [Core Privacy Principles](#1-core-privacy-principles)
2. [Identity Model](#2-identity-model)
3. [Access Hierarchy](#3-access-hierarchy)
4. [Legal & Regulatory Requirements](#4-legal--regulatory-requirements)
5. [Technical Security Requirements](#5-technical-security-requirements)
6. [Architecture Constraints](#6-architecture-constraints)
7. [Content Moderation Policy](#7-content-moderation-policy)
8. [Data Retention & Deletion](#8-data-retention--deletion)
9. [Location Data Governance](#9-location-data-governance)
10. [Required Legal Documents](#10-required-legal-documents)
11. [Phase Plan](#11-phase-plan)
12. [Database Schema Reference](#12-database-schema-reference)
13. [Risk Register](#13-risk-register)

---

## 1. Core Privacy Principles

These are non-negotiable architectural constraints. They are enforced by design (no API endpoint exists to violate them), not by policy.

### 1.1 Server-Mediated Everything
- All communication flows: `User A → Server → Server → User B`
- No direct client-to-client connections (no peer-to-peer WebSockets, no direct IP exchange)
- The server is always the intermediary for every message, file share, and data exchange

### 1.2 No User Information Exposure
- No user's IP address is ever stored, logged, forwarded, or accessible
- No user's device identifier is stored (device UUID used once during registration handshake, then discarded)
- No network metadata is passed between users
- No user enumeration — no endpoint lists users by anything traceable
- Message metadata is server-generated (timestamps, IDs) — no client fingerprint data attached
- No "last seen," "online now," or presence indicators unless a user explicitly opts in

### 1.3 A User IS Their Chosen Name — Nothing More
- Display names are the only identity visible to any other user
- No other user can obtain information about another user — not even moderators
- Moderators act on content and display names only — zero visibility into who the person is
- Account recovery data (email, OAuth ID) is a private channel between the user and the system exclusively
- Even application logs reference users by opaque server-generated UUID only, never PII

### 1.4 Privilege by Design, Not Permission
- No API endpoint returns PII columns — excluded at the query level, not filtered in middleware
- Moderator views are served by dedicated queries that JOIN only `display_name`, never `email`/`device_id`
- The only path to PII is direct `psql` access on the server — restricted to root/god (system owner)
- The application literally cannot leak what it never queries

### 1.5 Terminology
- **Never use the word "anonymous"** in user-facing materials, marketing, or legal documents
- The FTC classifies any system with persistent identifiers as identifiable — "anonymous" claims are legally actionable
- **Use "pseudonymous"** — users pick a name, we don't require real identity, but we don't claim they're invisible
- Acceptable phrasing: "We minimize data collection," "We do not link your activity to a named identity," "We do not sell your personal information"

---

## 2. Identity Model

### 2.1 Tiered Identity

| Tier | Name | What's Stored | What's NOT Stored | Capabilities |
|------|------|--------------|-------------------|-------------|
| 0 | Pseudonymous | Server-generated UUID, display name, created_at | Device UUID, IP, email, location history | Read, comment, join public rooms |
| 1 | Recoverable | + encrypted email (AES-256-GCM) | Device UUID, IP, location history | + create rooms, build reputation |
| 2 | OAuth-linked | + OAuth provider/ID hash | Raw OAuth token, device UUID, IP | + account recovery across devices |

### 2.2 Identity Rules
- Server generates a fresh UUID for each user — identity is NEVER derived from device
- Device UUID touches the wire once during initial registration, is never stored server-side
- Display names are user-chosen, not derived from email or OAuth profile
- Email is encrypted at rest (AES-256-GCM), stored only for account recovery
- OAuth IDs are stored as one-way hashes, never raw
- No index exists on encrypted email column — it cannot be queried from the application layer
- A separate `auth_lookup` table maps `email_hash → user_id` for login purposes only

### 2.3 Account Lifecycle
- **Creation:** App requests `POST /auth/register` — server generates UUID, returns JWT + refresh token
- **Upgrade (Tier 0→1):** User provides email via `POST /auth/link` — encrypts and stores, preserves all existing data
- **Upgrade (Tier 1→2):** User links Google/Apple via `POST /auth/link-oauth` — stores hashed provider ID
- **Deletion:** User requests `POST /auth/delete` — cascading hard delete within 45 days (see Section 8)

---

## 3. Access Hierarchy

The system has two distinct power domains: **platform-level** (controls the entire application) and **room-level** (controls a single chat room branch). Platform-level roles have superuser access to all information. Room-level roles exist only within their room and have zero visibility into the platform or other rooms.

### 3.1 Platform-Level Roles (Superuser Domain)

```
OWNER (system owner):
  ├── Access: EVERYTHING — full database, encryption keys, server infrastructure
  ├── Method: direct PostgreSQL CLI, server SSH, application admin panel
  ├── Can: decrypt emails, view all user data, all platform operations
  ├── Can: create/remove Developer/Product Support accounts
  ├── Can: override any room-level decision
  ├── Can: respond to law enforcement requests
  └── Ultimate authority over the entire platform

DEVELOPER / PRODUCT SUPPORT (platform staff):
  ├── Access: ALL information needed to control the application
  ├── Method: application admin panel + internal API endpoints
  ├── Can see: all user accounts, all rooms, all messages, all content
  ├── Can see: user account details (email, registration date, activity history)
  ├── Can: ban any user from the ENTIRE PLATFORM (not just a room)
  ├── Can: block IPs from accessing the service
  ├── Can: terminate any account permanently
  ├── Can: access any chat room, read any message
  ├── Can: override room-level admin/moderator decisions
  ├── Can: respond to user reports escalated beyond room level
  ├── Can: review flagged content from automated moderation
  ├── Must: document ALL contact with any individual user
  │         (logged to support_interactions table with timestamp,
  │          staff member ID, user ID, action taken, reason)
  ├── Cannot: access server infrastructure or encryption keys (Owner only)
  └── Cannot: create other Dev/Product Support accounts (Owner only)
```

**Platform-level access is privileged.** These roles exist to run and protect the application. They can see everything because they need to — for abuse response, legal compliance, and platform safety. This access is audited and logged.

### 3.2 Room-Level Roles (Scoped to One Room Only)

```
ROOM ADMINISTRATOR (user who creates a chat room):
  ├── Scope: ONLY the room they created — zero access elsewhere
  ├── Can see: display names and content within their room
  ├── Can: set room name, description, public/private status
  ├── Can: promote any room member to Room Moderator
  ├── Can: demote Room Moderators back to member
  ├── Can: mute any user for X hours (configurable duration)
  ├── Can: kick any user from the room (immediate removal)
  ├── Can: ban any user from the room (permanent, cannot rejoin)
  ├── Can: delete any message in their room
  ├── Can: set room rules / pinned messages
  ├── Can: close/archive the room
  ├── Cannot see: any user information beyond display_name
  ├── Cannot see: user emails, IPs, device info, or account details
  ├── Cannot: affect users outside their room
  ├── Cannot: ban users from the platform (must flag to Product Support)
  └── Cannot: access other rooms' content or membership

ROOM MODERATOR (promoted by Room Administrator):
  ├── Scope: ONLY the room they were promoted in
  ├── Can see: display names and content within their room
  ├── Can: mute any user for X hours (configurable: 1h, 6h, 24h, 72h)
  ├── Can: kick any user from the room (immediate removal)
  ├── Can: ban any user from the room (permanent, cannot rejoin)
  ├── Can: delete messages in their room
  ├── Can: flag users/content for Product Support review (escalation)
  ├── Cannot see: any user information beyond display_name
  ├── Cannot: promote other moderators (Room Admin only)
  ├── Cannot: demote other moderators (Room Admin only)
  ├── Cannot: close/archive the room (Room Admin only)
  ├── Cannot: affect users outside their room
  └── Cannot: override Room Administrator decisions

MEMBER (regular room participant):
  ├── Can: send messages, share POIs, share databases
  ├── Can: report messages/users (flagged for Room Mod → Product Support)
  ├── Can see: display names and content within the room
  └── Cannot see: anything about other users beyond display_name
```

### 3.3 Room Ban Enforcement

When a Room Admin or Moderator bans a user from a room:
- User is immediately disconnected from the room's Socket.IO channel
- `room_bans` table records: room_id, banned_user_id, banned_by, reason, timestamp
- Server checks `room_bans` on every room join attempt — **banned users cannot rejoin**
- Ban is permanent unless the Room Admin explicitly lifts it
- Banned user can still use other rooms and platform features normally
- If a user is problematic across multiple rooms, Room Admins flag to Product Support for platform-level action

### 3.4 Platform Ban vs Room Ban

| Action | Scope | Who Can Do It | Effect |
|--------|-------|--------------|--------|
| Room mute | Single room | Room Admin, Room Moderator | Cannot send messages for X hours, can still read |
| Room kick | Single room | Room Admin, Room Moderator | Removed from room, can rejoin |
| Room ban | Single room | Room Admin, Room Moderator | Removed from room, **cannot rejoin** |
| Platform ban | Entire application | Dev/Product Support, Owner | All social features disabled, all rooms disconnected |
| IP block | Entire application | Dev/Product Support, Owner | Connection rejected at server level |
| Account termination | Entire application | Dev/Product Support, Owner | Account permanently deleted, all content orphaned |

### 3.5 Escalation Path

```
User reports content
  → Room Moderator reviews (room-level action: delete/mute/kick/ban)
  → If beyond room scope → Room Mod flags to Product Support
  → Product Support reviews (platform-level action: platform ban/IP block/terminate)
  → If legal/infrastructure issue → Escalate to Owner
```

### 3.6 Support Interaction Logging

ALL Dev/Product Support contact with individual users is documented:

```sql
CREATE TABLE support_interactions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    staff_user_id   UUID NOT NULL REFERENCES users(id),
    target_user_id  UUID NOT NULL REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,  -- 'review', 'warn', 'ban', 'unban', 'ip_block', 'terminate', 'contact'
    reason          TEXT NOT NULL,
    details         TEXT,                  -- full description of the interaction
    room_id         UUID REFERENCES chat_rooms(id),  -- NULL if platform-level
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

This table is append-only — records are never deleted or modified. It serves as the audit trail for all platform-level moderation activity.

### 3.7 Privacy Boundary Summary

| Role | Can see display names | Can see content | Can see PII (email, etc.) | Can see IPs | Can see all rooms |
|------|----------------------|-----------------|--------------------------|-------------|-------------------|
| Owner | Yes | Yes (all) | Yes (decrypted) | Yes (if logged) | Yes |
| Dev/Product Support | Yes | Yes (all) | Yes (via admin panel) | Yes (for blocking) | Yes |
| Room Administrator | Yes (own room) | Yes (own room) | **NO** | **NO** | **NO** |
| Room Moderator | Yes (own room) | Yes (own room) | **NO** | **NO** | **NO** |
| Member | Yes (joined rooms) | Yes (joined rooms) | **NO** | **NO** | **NO** |

The line is absolute: **platform staff see everything to control the app. Room-level roles see display names and content in their room only — nothing else, ever.**

---

## 4. Legal & Regulatory Requirements

### 4.1 COPPA (Children's Online Privacy Protection Act)

**Status:** 2025 amendments effective June 23, 2025. Compliance deadline: **April 22, 2026**.

**Requirements:**
- Age gate REQUIRED before any social feature access (chat, comments, account creation)
- Users indicating age < 13 are blocked from all social features entirely
- Expanded definition of personal information now includes: device IDs, IP addresses, precise geolocation
- Data retention: delete when no longer necessary, provide clear retention policies
- FTC February 2026 safe harbor: age verification data collection allowed solely for age determination

**Implementation:**
- Present age verification dialog before first social feature interaction
- Store age-verified flag (not date of birth) in user record
- Block social features for unverified users
- No personal data collection before age verification completes

### 4.2 Location Data as Sensitive PII

Location data receives heightened protection under every modern privacy framework:

| Law/Agency | Classification | Key Requirement |
|-----------|---------------|----------------|
| CCPA/CPRA | Sensitive Personal Information | Purpose limitation, risk assessments, opt-out rights |
| Oregon | Protected (1,750ft precision ban) | Cannot sell/share precise location data |
| Maryland | Protected (sale banned) | Cannot monetize location data at all |
| COPPA 2025 | Personal Information | Parental consent required for children |
| FTC enforcement | Essentially non-anonymizable | 4 timestamped points identify 95% of individuals |
| GDPR | Sensitive data | Heightened consent, fines up to 4% global revenue |

**Our obligations:**
- Geo-room membership is ephemeral (in-memory only, never persisted to database)
- No location history stored server-side for any user
- Geo-room auto-join requires explicit user consent (not default-on)
- Location data used only for room assignment, then discarded

### 4.3 FTC "Anonymous" / "No Tracking" Claims

**FTC position (reaffirmed 2024-2026):**
- Hashing does not make data anonymous — hashed/pseudonymized data is still identifiable
- Device UUIDs are personal data (explicitly under 2025 COPPA amendments)
- Location data is never truly anonymous (4 points = 95% identification)
- NGL Labs fined $4.5M in 2026 for false anonymity claims

**Our compliance:**
- Never claim "anonymous" or "no tracking" in any user-facing material
- Use "pseudonymous" terminology throughout
- Disclose in privacy policy exactly what we collect and retain
- Minimize collection: no device IDs stored, no IP logging, no location history

### 4.4 Section 230 (Communications Decency Act)

**Current status:** In effect but under legislative threat. Provides platform immunity for user-generated content.

**Our protections:**
- We are not liable for user comments/messages (platform, not publisher)
- Good-faith moderation decisions are protected
- Section 230 does NOT protect against: federal crimes, IP violations, ECPA violations

**Preparing for post-230 world:**
- Document all moderation policies clearly
- Apply moderation consistently across all users
- Do not algorithmically amplify or endorse user content
- Maintain audit trail of all moderation actions

### 4.5 Business Defamation on POI Comments

- Protected opinion ("food was terrible") cannot be sued
- False factual claims ("has cockroaches" when it doesn't) = potentially defamatory
- Section 230 protects us as platform, but businesses can **subpoena us to identify commenters**
- If commenter is Tier 0 (pseudonymous, no email), we have nothing to produce — legal strength
- If commenter is Tier 1-2 (has email), email may be disclosed under court order
- Terms of Service MUST disclose this possibility to users
- Consumer Review Fairness Act: we CANNOT suppress legitimate negative reviews

### 4.6 State Privacy Law Landscape (2026)

Twenty states now have comprehensive privacy laws. Key considerations:
- Indiana, Kentucky, Rhode Island laws effective January 1, 2026
- Connecticut, Arkansas, Utah new provisions effective July 1, 2026
- 2026 is described as "the most aggressive enforcement climate in U.S. privacy history"
- State AG multi-jurisdictional collaborations are now commonplace
- Compliance strategy must satisfy the strictest requirements (Maryland/Oregon for location data)

### 4.7 KOSA (Kids Online Safety Act)

**Status:** NOT YET LAW. Reintroduced May 2025 (S.1748), pending in Congress.

**If enacted:** duty of care for platforms regarding minors (under 17), FTC + state AG enforcement power, focus on platform design choices.

**Our posture:** Age gate already handles this. Building child-safety features now positions ahead of likely requirements.

### 4.8 CAN-SPAM / Notifications

- CAN-SPAM applies to email only — if we send emails, must include opt-out, physical address, honest subject
- Push notifications governed by platform policies (Google Play), not CAN-SPAM — require opt-in
- If we ever add SMS (2FA), TCPA requires prior express consent

---

## 5. Technical Security Requirements

### 5.1 Authentication

| Component | Standard | Specification |
|-----------|----------|--------------|
| Access tokens | JWT | 15-minute expiry, EdDSA or ES256 signing |
| Refresh tokens | Opaque random | 30-day expiry, stored as hash in DB |
| Refresh rotation | Mandatory | Each use issues new token, reuse = revoke entire family |
| Password hashing | Argon2id | 19 MiB memory, 2 iterations, 1 parallelism (OWASP first choice) |
| Google Sign-In | Credential Manager API | Legacy GoogleSignInClient is deprecated |
| Android token storage | DataStore + Tink + Keystore | EncryptedSharedPreferences is deprecated |

### 5.2 Transport Security

- **TLS 1.2 minimum, TLS 1.3 preferred** on all connections
- **WebSocket connections must use `wss://`** — never `ws://`
- Forward secrecy (ECDHE key exchange) on all connections
- Android Network Security Config: disallow cleartext traffic
- Consider certificate pinning for proxy server connection (with backup pin and rotation plan)
- Validate `Origin` header on WebSocket upgrade to prevent Cross-Site WebSocket Hijacking

### 5.3 Encryption at Rest

| Data | Method | Key Management |
|------|--------|---------------|
| User emails | AES-256-GCM field encryption | Application-managed key, stored outside DB |
| OAuth IDs | One-way SHA-256 hash | N/A (not reversible) |
| Passwords | Argon2id hash | N/A (not reversible) |
| Database backups | Encrypted volumes (LUKS) | System-level key management |
| Shared database files | AES-256 at rest | Application-managed key |

### 5.4 Input Validation & Injection Prevention

- **SQL injection:** Parameterized queries exclusively (`$1, $2` placeholders in `pg` library). Never interpolate user input into SQL strings. Non-negotiable.
- **XSS:** Strip or encode all HTML tags from chat messages and POI comments before storage and display. Use `sanitize-html` or equivalent server-side.
- **WebSocket payloads:** Validate and sanitize all incoming Socket.IO messages before processing or broadcasting.
- **Content length limits:** 2,000 chars for comments, configurable for chat messages, 1MB max Socket.IO message.

### 5.5 JWT Security Checklist

- [ ] Strict algorithm whitelisting (never trust `alg` header from token)
- [ ] Reject `alg: none` tokens
- [ ] Always verify signatures (no skip path)
- [ ] Use 256+ bit random secret for HMAC or asymmetric keys (EdDSA/ES256)
- [ ] Short access token expiry (15 minutes)
- [ ] Refresh token rotation with family-based revocation
- [ ] Token blacklist for logout/revocation

### 5.6 OAuth 2.0 Security

- PKCE mandatory for all mobile OAuth flows (`S256` code challenge method)
- Short-lived authorization codes (one-time use, 60-second expiry)
- Use Android App Links (verified deep links) instead of custom URI schemes
- Use AppAuth library for Android — do not build custom OAuth implementation
- Whitelist exact redirect URIs (no wildcards)

### 5.7 Rate Limiting

| Layer | Strategy | Limits |
|-------|----------|--------|
| Connection | Max connections per user | Configurable, start at 3 |
| Chat messages | Token bucket per user | 20 messages/minute |
| POI comments | Sliding window per user | 5 comments/5 minutes |
| Reports | Sliding window per user | 3 reports/hour |
| API endpoints | Sliding window per endpoint | Configurable |
| New accounts | Escalating limits | 10 comments in first 24 hours |
| Duplicate content | Content hash within time window | Block identical messages within 60s |

### 5.8 File/Database Sharing Security

- Schema validation: strict validation against expected table/column structure before import
- Dangerous object check: reject SQLite files containing triggers or views
- Integrity check: `PRAGMA integrity_check` and `PRAGMA quick_check`
- Open read-only with `trusted_schema = OFF` and `mmap_size = 0`
- Row count limit: 1M rows per table maximum
- File size limit: 50MB per file, 100MB total quota per user
- Content-addressable storage: SHA-256 hash for deduplication
- Virus scanning: ClamAV for uploaded files (optional, recommended for production)
- Content moderation: text fields in shared databases pass through same moderation pipeline as chat

---

## 6. Architecture Constraints

### 6.1 Communication Model

```
┌──────────────┐     HTTPS + wss://      ┌───────────────────────┐
│  Android App │ ◄──────────────────────► │  Cache Proxy (Node.js)│
│              │     TLS 1.3             │  Port 3000            │
│  - No P2P    │                          │                       │
│  - No IPs    │                          │  ┌─────────────────┐  │
│  - TLS only  │                          │  │ Content Filter  │  │
│              │                          │  │ (every message) │  │
└──────────────┘                          │  └────────┬────────┘  │
                                          │           │           │
                                          │  ┌────────▼────────┐  │
                                          │  │ PostgreSQL      │  │
                                          │  │ (encrypted PII) │  │
                                          │  └─────────────────┘  │
                                          └───────────────────────┘
```

### 6.2 Message Flow (every message)

1. User A sends message via Socket.IO over TLS
2. Server receives — strips ALL client metadata (IP, headers, device info)
3. Rate limit check (per-user token bucket)
4. Content moderation: instant blocklist regex check
5. If passes: store in DB with server-generated timestamp, broadcast to room
6. If blocked: reject with generic error, log for review
7. Async: Perspective API toxicity analysis on delivered messages (flag, don't block)
8. User B receives: display_name + content + server_timestamp ONLY

### 6.3 Real-Time Technology

- **Socket.IO v4** on existing Express server (shares HTTP server)
- Android client: `socket.io-client-java` with WebSocket transport only (skip HTTP long-polling)
- Heartbeat: 25s interval, 20s timeout (Socket.IO defaults)
- Reconnection: automatic with exponential backoff (1s → 30s max)
- Auth: JWT passed in handshake `auth` object, validated on connection
- Future scaling: Redis pub/sub via `@socket.io/redis-adapter` (zero code changes, add only when needed)

### 6.4 Message Persistence

Async batch writes to PostgreSQL — real-time delivery first, DB write eventual:

- Buffer: 50 messages OR 2 seconds (whichever first) → bulk INSERT
- Monthly range partitioning on `sent_at` column
- Partition benefits: recent queries only scan current partition; old partitions can be dropped instantly
- Materialized views for aggregate counts (comment counts per POI, room member counts), refreshed every 5 minutes

---

## 7. Content Moderation Policy

Content moderation operates at two levels: **automated** (every message, before delivery) and **human** (room-level moderators + platform-level Product Support). All chat content is censored for bad content before anyone sees it.

### 7.1 Automated Content Censorship (Every Message, Pre-Delivery)

Every message passes through the automated pipeline before it reaches any user. No exceptions.

| Stage | Method | Timing | Action on Trigger |
|-------|--------|--------|-------------------|
| 1 | **Rate limiting** | Synchronous | Reject message, notify sender "slow down" |
| 2 | **Duplicate detection** | Synchronous | Block if identical content within 60s |
| 3 | **Regex/keyword blocklist** | Synchronous (pre-delivery) | Block message, sender sees "message not sent — content violation" |
| 4 | **Content length / format** | Synchronous | Reject oversized or malformed messages |
| 5 | **Perspective API toxicity** | Asynchronous (post-delivery) | If score > 0.85: auto-flag for Room Mod review queue |
| 6 | **Pattern escalation** | Asynchronous | If user triggers 3+ blocks in 1 hour: auto-flag to Product Support |

The blocklist covers: slurs, hate speech, explicit sexual content, doxxing patterns (phone/SSN/address regex), spam patterns, known abuse phrases. Maintained by Dev/Product Support, updated regularly.

### 7.2 Human Moderation Chain

```
AUTOMATED FILTER (catches obvious bad content before delivery)
       │
       ▼
ROOM MODERATOR (first human line — handles room-level issues)
  ├── Delete offensive messages
  ├── Mute disruptive users (1h, 6h, 24h, 72h)
  ├── Kick users (removed, can rejoin)
  ├── Ban users (removed, CANNOT rejoin room)
  └── Flag to Product Support if issue is beyond room scope
       │
       ▼
PRODUCT SUPPORT (platform-level — handles escalations + cross-room issues)
  ├── Review flagged content and escalated reports
  ├── Platform-ban users across ALL rooms
  ├── Block IPs from the service
  ├── Terminate accounts permanently
  ├── Document ALL interactions with users
  └── Escalate to Owner for legal/infrastructure issues
       │
       ▼
OWNER (final authority — legal, infrastructure, critical decisions)
```

### 7.3 Room-Level Moderation Actions

These actions are available to **Room Administrators** and **Room Moderators** within their room only.

| Action | Who Can Do It | Effect | Duration | Reversible |
|--------|--------------|--------|----------|------------|
| Delete message | Room Admin, Room Mod | Message replaced with "[message removed]" | Permanent | No |
| Mute user | Room Admin, Room Mod | User cannot send messages in this room | 1h, 6h, 24h, or 72h (configurable) | Auto-expires |
| Kick user | Room Admin, Room Mod | User removed from room immediately | Instant (user can rejoin) | N/A |
| Ban user | Room Admin, Room Mod | User removed, **cannot rejoin this room** | Permanent until Admin lifts | Room Admin can unban |
| Promote to Moderator | Room Admin only | Member gains moderation powers | Until demoted | Room Admin can demote |
| Demote Moderator | Room Admin only | Moderator becomes regular member | Immediate | Can re-promote |
| Flag to Support | Room Admin, Room Mod | Escalates user/content to Product Support | N/A | N/A |

### 7.4 Platform-Level Moderation Actions

These actions are available to **Dev/Product Support** and **Owner** only. They affect the user across the entire platform.

| Action | Effect | Scope | Documentation Required |
|--------|--------|-------|----------------------|
| Platform ban | All social features disabled, all rooms disconnected | Entire platform | Yes — `support_interactions` entry required |
| IP block | Connection rejected at server level | Entire platform | Yes — `support_interactions` entry required |
| Account termination | Account permanently deleted, all content orphaned | Entire platform | Yes — `support_interactions` entry required |
| Shadow ban | User sees own content, nobody else does (across all rooms) | Entire platform | Yes — `support_interactions` entry required |
| Content purge | Remove all content from a specific user across all rooms | Entire platform | Yes — `support_interactions` entry required |
| Override room decision | Reverse a Room Admin/Mod action (unban, unmute, etc.) | Specific room | Yes — `support_interactions` entry required |

**Every platform-level action MUST be documented** in the `support_interactions` table with staff ID, target user, action, reason, and full details. This is non-negotiable — it's the audit trail.

### 7.5 Room Ban Enforcement (Cannot Rejoin)

When a user is banned from a room:

1. Immediate disconnect from room's Socket.IO channel
2. Record written to `room_bans` table: room_id, user_id, banned_by, reason, timestamp
3. On every future `join_room` request, server checks `room_bans` → **denied if banned**
4. Banned user sees: "You have been banned from this room"
5. Ban is permanent unless Room Administrator explicitly lifts it via unban action
6. Banned user can still use all other rooms and platform features normally

```sql
CREATE TABLE room_bans (
    room_id     UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    banned_by   UUID NOT NULL REFERENCES users(id),
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lifted_at   TIMESTAMPTZ,            -- NULL = still active
    lifted_by   UUID REFERENCES users(id),
    PRIMARY KEY (room_id, user_id)
);
```

### 7.6 Mute Enforcement

When a user is muted in a room:

1. Record written to `room_memberships.muted_until` with expiry timestamp
2. Server rejects messages from muted users: "You are muted for X hours"
3. Muted user can still read messages, they just cannot send
4. Mute auto-expires at the set time — no manual action needed
5. Room Admin/Mod can unmute early if desired
6. Mute durations: 1 hour, 6 hours, 24 hours, 72 hours (Room Admin/Mod picks)

### 7.7 Moderation Audit Trail

**Every moderation action at every level is logged.** Two separate tables for the two domains:

**Room-level actions** → `moderation_log` table:
```sql
CREATE TABLE moderation_log (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id         UUID NOT NULL REFERENCES chat_rooms(id),
    moderator_id    UUID NOT NULL REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,   -- 'delete_message', 'mute', 'kick', 'ban', 'unban', 'promote_mod', 'demote_mod'
    target_user_id  UUID REFERENCES users(id),
    target_message_id BIGINT,               -- for message deletions
    reason          TEXT,
    duration_hours  INTEGER,                -- for mutes
    content_snapshot TEXT,                   -- preserved copy of deleted content
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Platform-level actions** → `support_interactions` table (see Section 3.6)

Both tables are **append-only — records are never deleted or modified**.

### 7.8 Perspective API Integration

- Google Perspective API (free tier) for toxicity scoring
- Scores: TOXICITY, SEVERE_TOXICITY, INSULT, THREAT
- Threshold: flag for Room Mod review at > 0.85 (do NOT auto-block — reduces false positives)
- Non-blocking: analysis runs async after message delivery
- Auto-escalation: if 3+ flags in 1 hour for same user → auto-flag to Product Support
- Env var: `PERSPECTIVE_API_KEY` (optional — system works without it using stages 1-4 only)

### 7.9 Content Censorship Scope

Content censorship applies to ALL user-generated content across the platform:

| Content Type | Automated Filter | Room-Level Moderation | Platform-Level Moderation |
|-------------|-----------------|----------------------|--------------------------|
| Chat messages | Yes (pre-delivery) | Yes (delete, mute, kick, ban) | Yes (platform ban, purge) |
| POI comments | Yes (pre-save) | N/A (not room-scoped) | Yes (delete, platform ban) |
| Room names/descriptions | Yes (on creation) | Room Admin can edit | Product Support can edit/remove |
| Display names | Yes (on registration) | N/A | Product Support can force rename |
| Shared database metadata | Yes (on upload) | N/A | Product Support can remove |

No user-generated text enters the system without passing through the automated content filter.

---

## 8. Data Retention & Deletion

### 8.1 Retention Periods

| Data Type | Retention | Justification |
|-----------|-----------|--------------|
| Chat messages | Indefinite (until user deletes or room archived) | User expectation of persistent chat history |
| Deleted messages | 90 days soft-delete, then hard-delete | Appeals window |
| POI comments | Indefinite (until user deletes) | Ongoing community value |
| User accounts | Until user requests deletion | User-controlled |
| Room-level moderation logs | 2 years | Audit compliance |
| Support interactions | **Indefinite (never deleted)** | Legal audit trail — all staff-user contact |
| Room bans | Indefinite (until lifted by Room Admin) | Enforcement — banned users cannot rejoin |
| IP blocks | Until expiry or manual removal | Platform security |
| User reports | 2 years after resolution | Audit compliance + pattern detection |
| Moderation blocklist | Indefinite | Operational necessity |
| Geo-room membership | Ephemeral (in-memory only) | Privacy — never persisted |

### 8.2 User Deletion Process

When a user requests account deletion (`POST /auth/delete`):

| Step | Action | Timeline |
|------|--------|----------|
| 1 | Confirm deletion request (email if Tier 1+) | Immediate |
| 2 | Soft-delete user record | Within 24 hours |
| 3 | Replace display_name with "[deleted user]" on all content | Within 24 hours |
| 4 | Delete encrypted email, OAuth hash | Within 24 hours |
| 5 | Hard-delete user record | Within 45 days (CCPA) / 30 days (GDPR) |
| 6 | Orphaned content remains with "[deleted user]" attribution | Permanent |
| 7 | Confirmation sent (if email was on file) | After completion |

### 8.3 Content Deletion

- Chat messages: hard delete from DB, other users see "[message removed]"
- POI comments: hard delete, show "[deleted]" placeholder with rating preserved (anonymized)
- Shared databases: remove contributor attribution or anonymize; if sole contributor, remove database
- Deletion cascades must complete within 45 days of request

### 8.4 Law Enforcement Obligations

- **Preservation requests:** Must retain specified user data pending formal legal process. Failure = spoliation claims.
- **Subpoenas** (lowest standard): non-content data — account info, timestamps
- **Court orders** (intermediate): some metadata
- **Warrants** (highest): content of communications
- **What we can produce for Tier 0 users:** display_name, server-generated UUID, timestamps. No email, no device info, no IP.
- **What we can produce for Tier 1-2:** above + decrypted email (requires our encryption key)
- Internal procedure: all law enforcement requests go through root/god only

---

## 9. Location Data Governance

### 9.1 Principles

- Location data is the most sensitive data we handle — treated as toxic by default
- Minimize collection, minimize retention, minimize sharing
- No location history stored server-side for any user tier
- Geo-room assignment is the ONLY social feature that processes location

### 9.2 Geo-Room Privacy Controls

| Control | Implementation |
|---------|---------------|
| Opt-in only | Geo-rooms not joined by default; user must explicitly enable community features |
| Coarse precision | Geohash precision 4 (~39km x 20km) for city rooms — reveals only metro area |
| Ephemeral membership | Room assignment stored in-memory only, never written to database |
| Delayed updates | Room membership updated every 5 minutes, not on every GPS fix |
| No presence broadcast | Other users never see that a specific person is in a geo-room |
| No location forwarding | User's coordinates never sent to other users, only used for room hash calculation |
| Room names only | UI shows "Beverly" or "Boston" — never coordinates or precise location |

### 9.3 Geohash Room Strategy

| Precision | Cell Size | Room Type |
|-----------|-----------|-----------|
| 4 chars | ~39km x 20km | City / metro area |
| 6 chars | ~1.2km x 0.6km | Neighborhood (future) |

- Users join center cell + 8 neighbors (9 rooms total) to handle boundary cases
- UI displays only primary room name
- Room creation is on-demand (first user to enter a geohash creates the room)
- Rooms auto-archive after 90 days of no messages

---

## 10. Required Legal Documents

These documents MUST be completed before any social feature ships. COPPA compliance deadline is **April 22, 2026**.

### 10.1 Documents Needed

| Document | Purpose | Priority |
|----------|---------|----------|
| **Privacy Policy** | What we collect, retention, deletion rights, CCPA/GDPR disclosures | CRITICAL — before any social feature |
| **Terms of Service** | User conduct, content licensing, moderation rights, dispute resolution | CRITICAL — before any social feature |
| **Age Gate** | COPPA compliance — verify 13+ before social feature access | CRITICAL — deadline April 22, 2026 |
| **Content Policy** | What's allowed in chat/comments, consequences for violations | HIGH — before chat launches |
| **DMCA Takedown Policy** | Copyright infringement procedure for user-generated content | MEDIUM — before public launch |
| **Law Enforcement Response Policy** | Internal procedures for subpoenas and preservation requests | MEDIUM — before significant user base |

### 10.2 Privacy Policy Must Disclose

- We collect: server-generated user ID, display name, message content, comment content
- We collect (Tier 1+): encrypted email for account recovery only
- We do NOT collect: IP addresses, device identifiers, location history, real names
- We do NOT sell personal information
- We do NOT share personal information with third parties (except under court order)
- Approximate location is used for geo-room assignment only (ephemeral, not stored)
- Users can request deletion of all personal data within 45 days
- Users can request export of their data

### 10.3 Terms of Service Must Include

- User conduct rules (no harassment, hate speech, illegal content, doxxing)
- Content licensing (users retain ownership, grant us license to display/store/moderate)
- Moderation rights (our right to remove content, suspend accounts)
- Disclaimer of liability for user-generated content
- Age restriction (13+, COPPA compliance)
- Limitation of liability
- Dispute resolution / arbitration clause
- DMCA takedown procedure
- Consumer Review Fairness Act compliance (cannot suppress legitimate reviews)
- Disclosure: court orders may compel us to produce account recovery information (email) for Tier 1-2 users
- Pseudonymity is not guaranteed against legal process

---

## 11. Phase Plan

| Phase | Feature | Legal Prerequisites | Technical Dependencies | Effort |
|-------|---------|--------------------|-----------------------|--------|
| **0** | Age gate + Privacy Policy + ToS | COPPA deadline April 22, 2026 | None | Low |
| **1** | User identity (pseudonymous accounts, JWT auth) | Privacy Policy must be live | Phase 0 | Medium |
| **2** | POI comments + ratings | Content Policy, Section 230 | Phase 1 | Medium |
| **3** | Chat rooms (global + geo + custom) | Full ToS, moderation system | Phase 1 + Phase 4 concurrent | High |
| **4** | Content moderation (blocklist + reporting + Perspective API) | Moderation audit trail | Phase 1 | Medium |
| **5** | Shared databases (community upload/download) | File validation, quota mgmt | Phase 1 | Medium |
| **6** | Google Sign-In + account linking (Tier 0→1→2) | OAuth security review | Phase 1 | Low-Medium |

### 11.1 Phase 0 — Legal Foundation (URGENT: deadline April 22, 2026)

- Draft and publish Privacy Policy
- Draft and publish Terms of Service
- Implement age gate dialog (before any social feature access)
- Store age-verified flag (not date of birth) in user record
- Block social features for unverified users

### 11.2 Phase 1 — User Identity

- `users` table with tiered identity (Section 12)
- JWT access token (15min) + opaque refresh token (30d) with rotation
- `POST /auth/register` — create pseudonymous account
- `POST /auth/login` — email/password login (Tier 1+)
- `POST /auth/refresh` — token rotation
- `POST /auth/delete` — account deletion cascade
- Argon2id password hashing
- Android: DataStore + Tink token storage

### 11.3 Phase 2 — POI Comments

- `poi_comments` table + `comment_votes` table
- `GET /comments/:osm_type/:osm_id` — fetch comments for a POI
- `POST /comments/:osm_type/:osm_id` — add comment (rate limited)
- `POST /comments/:id/vote` — upvote/downvote
- `DELETE /comments/:id` — delete own comment
- Community section in POI detail dialog
- Word filter on submission

### 11.4 Phase 3 — Chat Rooms

- `chat_rooms` + `room_memberships` + `messages` tables (monthly partitioned)
- Socket.IO v4 integration on existing Express server
- Global room (always exists, all users)
- Geo rooms (auto-created by geohash, opt-in, ephemeral membership)
- Custom rooms (created by Tier 1+ users, public or invite-only)
- Room admin/moderator role hierarchy
- Message delivery: server-mediated, metadata-stripped, content-filtered

### 11.5 Phase 4 — Content Moderation

- `moderation_blocklist` table (regex + literal patterns)
- `reports` table (user flagging)
- `moderation_log` table (audit trail)
- Synchronous blocklist filter on all messages/comments
- Async Perspective API toxicity scoring
- Shadow ban implementation
- Rate limiting per-user per-action

### 11.6 Phase 5 — Shared Databases

- `shared_databases` table
- Upload validation pipeline (schema check, integrity check, danger check, size limit)
- Content-addressable storage (SHA-256 dedup)
- Per-user upload quotas
- Community browse/download interface
- ClamAV scanning (optional)

### 11.7 Phase 6 — OAuth Account Linking

- Google Sign-In via Credential Manager API
- `POST /auth/link-oauth` — upgrade Tier 0/1 to Tier 2
- Account recovery flow
- Token migration (preserve all existing data)

---

## 12. Database Schema Reference

### 12.1 Users

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identity
    identity_tier   VARCHAR(20) NOT NULL DEFAULT 'pseudonymous',  -- pseudonymous, recoverable, oauth_linked
    platform_role   VARCHAR(20) NOT NULL DEFAULT 'user',          -- user, support, owner
    display_name    VARCHAR(50) NOT NULL,

    -- PII (encrypted/hashed, platform-level access only)
    email_encrypted BYTEA,                  -- AES-256-GCM, Tier 1+ only
    password_hash   TEXT,                   -- Argon2id
    oauth_provider  VARCHAR(20),
    oauth_id_hash   VARCHAR(128),           -- SHA-256 hashed

    -- Platform-level moderation (Dev/Product Support + Owner only)
    is_banned       BOOLEAN NOT NULL DEFAULT FALSE,
    is_shadow_banned BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason      TEXT,
    ban_expires_at  TIMESTAMPTZ,
    banned_by       UUID REFERENCES users(id),

    -- COPPA
    age_verified    BOOLEAN NOT NULL DEFAULT FALSE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- platform_role values:
--   'user'    = regular user (default) — no platform-level privileges
--   'support' = Dev/Product Support — superuser access via admin panel, must log all interactions
--   'owner'   = System owner — full access including encryption keys and infrastructure
```

### 12.2 Auth Lookup (for login by email)

```sql
CREATE TABLE auth_lookup (
    email_hash  VARCHAR(64) PRIMARY KEY,    -- SHA-256 of lowercase email
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
);
```

### 12.3 Refresh Tokens

```sql
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    family_id   UUID NOT NULL,              -- for rotation-based revocation
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 12.4 Chat Rooms

```sql
CREATE TABLE chat_rooms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_type       VARCHAR(20) NOT NULL,   -- global, geo_city, geo_neighborhood, custom
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    created_by      UUID REFERENCES users(id),
    is_archived     BOOLEAN NOT NULL DEFAULT FALSE,
    member_count    INTEGER NOT NULL DEFAULT 0,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 12.5 Room Memberships

```sql
CREATE TABLE room_memberships (
    room_id     UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role        VARCHAR(20) NOT NULL DEFAULT 'member',  -- member, moderator, admin, owner
    joined_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    muted_until TIMESTAMPTZ,
    PRIMARY KEY (room_id, user_id)
);
```

### 12.6 Messages (Monthly Partitioned)

```sql
CREATE TABLE messages (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    room_id     UUID NOT NULL,
    user_id     UUID NOT NULL,
    content     TEXT NOT NULL,
    reply_to_id BIGINT,
    is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_by  UUID,
    deleted_at  TIMESTAMPTZ,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
) PARTITION BY RANGE (sent_at);
```

### 12.7 POI Comments

```sql
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
```

### 12.8 Comment Votes

```sql
CREATE TABLE comment_votes (
    comment_id  BIGINT NOT NULL REFERENCES poi_comments(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id),
    vote        SMALLINT NOT NULL CHECK (vote IN (-1, 1)),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (comment_id, user_id)
);
```

### 12.9 Room Bans

```sql
CREATE TABLE room_bans (
    room_id     UUID NOT NULL REFERENCES chat_rooms(id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    banned_by   UUID NOT NULL REFERENCES users(id),
    reason      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lifted_at   TIMESTAMPTZ,            -- NULL = still active, set when Admin unbans
    lifted_by   UUID REFERENCES users(id),
    PRIMARY KEY (room_id, user_id)
);

CREATE INDEX idx_room_bans_active ON room_bans (room_id) WHERE lifted_at IS NULL;
```

### 12.10 IP Blocks (Platform-Level)

```sql
CREATE TABLE ip_blocks (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ip_address  INET NOT NULL,
    blocked_by  UUID NOT NULL REFERENCES users(id),  -- must be support/owner
    reason      TEXT NOT NULL,
    expires_at  TIMESTAMPTZ,            -- NULL = permanent
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ip_blocks_active ON ip_blocks (ip_address) WHERE expires_at IS NULL OR expires_at > NOW();
```

### 12.11 Moderation — Content Filtering

```sql
CREATE TABLE moderation_blocklist (
    id          SERIAL PRIMARY KEY,
    pattern     TEXT NOT NULL,
    is_regex    BOOLEAN NOT NULL DEFAULT FALSE,
    severity    VARCHAR(20) NOT NULL DEFAULT 'block',   -- 'block', 'flag', 'warn'
    category    VARCHAR(50),            -- 'profanity', 'slur', 'spam', 'pii', 'threat'
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    added_by    UUID REFERENCES users(id),  -- support/owner who added it
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### 12.12 Moderation — User Reports

```sql
CREATE TABLE reports (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id     UUID NOT NULL REFERENCES users(id),
    target_type     VARCHAR(20) NOT NULL,   -- 'message', 'comment', 'user', 'room', 'database'
    target_id       TEXT NOT NULL,
    room_id         UUID REFERENCES chat_rooms(id),  -- room context if applicable
    reason          VARCHAR(50) NOT NULL,   -- 'spam', 'harassment', 'hate_speech', 'nsfw', 'doxxing', 'illegal'
    details         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending, reviewing, resolved, dismissed
    -- Resolution (by Room Mod, Product Support, or Owner)
    resolved_by     UUID REFERENCES users(id),
    action_taken    VARCHAR(50),            -- 'none', 'delete_content', 'mute', 'room_ban', 'platform_ban', 'ip_block', 'terminate'
    resolution_note TEXT,
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reports_pending ON reports (status, created_at) WHERE status = 'pending';
CREATE INDEX idx_reports_room ON reports (room_id) WHERE room_id IS NOT NULL;
```

### 12.13 Moderation — Room-Level Audit Log (append-only)

```sql
CREATE TABLE moderation_log (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    room_id           UUID NOT NULL REFERENCES chat_rooms(id),
    moderator_id      UUID NOT NULL REFERENCES users(id),
    action            VARCHAR(50) NOT NULL,   -- 'delete_message', 'mute', 'unmute', 'kick', 'ban', 'unban', 'promote_mod', 'demote_mod'
    target_user_id    UUID REFERENCES users(id),
    target_message_id BIGINT,                 -- for message deletions
    reason            TEXT,
    duration_hours    INTEGER,                -- for mutes (1, 6, 24, 72)
    content_snapshot  TEXT,                   -- preserved copy of deleted content
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mod_log_room ON moderation_log (room_id, created_at DESC);
```

### 12.14 Moderation — Platform-Level Support Interactions (append-only)

```sql
CREATE TABLE support_interactions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    staff_user_id   UUID NOT NULL REFERENCES users(id),  -- must be support/owner
    target_user_id  UUID NOT NULL REFERENCES users(id),
    action          VARCHAR(50) NOT NULL,   -- 'review', 'warn', 'platform_ban', 'unban', 'ip_block', 'shadow_ban',
                                            -- 'terminate', 'force_rename', 'content_purge', 'override_room_action', 'contact'
    reason          TEXT NOT NULL,
    details         TEXT,                   -- full description of the interaction
    room_id         UUID REFERENCES chat_rooms(id),  -- if action relates to specific room
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- APPEND-ONLY: no UPDATE or DELETE ever. This is the legal audit trail.
CREATE INDEX idx_support_target ON support_interactions (target_user_id, created_at DESC);
CREATE INDEX idx_support_staff ON support_interactions (staff_user_id, created_at DESC);
```

### 12.15 Shared Databases

```sql
CREATE TABLE shared_databases (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uploaded_by     UUID NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    category        VARCHAR(50) NOT NULL,
    file_hash       VARCHAR(64) NOT NULL UNIQUE,
    file_size_bytes BIGINT NOT NULL,
    file_path       TEXT NOT NULL,
    is_approved     BOOLEAN NOT NULL DEFAULT FALSE,
    download_count  INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## 13. Risk Register

| # | Risk | Severity | Likelihood | Mitigation |
|---|------|----------|-----------|------------|
| 1 | FTC enforcement for false "anonymous" claims | **CRITICAL** | High if we use the word | Never claim anonymous; use "pseudonymous" |
| 2 | COPPA violation (no age gate) | **CRITICAL** | High without age gate | Age gate before social features; deadline April 22, 2026 |
| 3 | Location data mishandling (20 state laws) | **HIGH** | Medium | Ephemeral geo-room membership, no location history, opt-in only |
| 4 | Subpoena for user identity (defamation case) | **MEDIUM** | Medium at scale | Tier 0 users have no email to produce; ToS discloses court order risk |
| 5 | Section 230 repeal | **MEDIUM** | Uncertain | Document moderation policies, consistent enforcement, audit trail |
| 6 | JWT token theft | **MEDIUM** | Low with proper impl | 15-min expiry, refresh rotation, family-based revocation |
| 7 | Content moderation failure (harassment, CSAM) | **HIGH** | Medium at scale | Multi-tier moderation, user reporting, audit trail, blocklists |
| 8 | Data deletion request cascade failure | **MEDIUM** | Medium | Automated deletion pipeline, 45-day SLA tracking |
| 9 | SQL injection via chat/comments | **HIGH** | Low with parameterized queries | Parameterized queries only, no string interpolation, input validation |
| 10 | WebSocket DoS | **MEDIUM** | Medium | Per-user connection limits, message rate limits, idle timeouts |
| 11 | Shared database malware | **LOW** | Low | Schema validation, read-only open, trusted_schema OFF, ClamAV |
| 12 | KOSA enactment | **LOW** | Uncertain | Age gate already covers this; monitor legislation |

---

## Appendix A: Technology Stack for Social Layer

| Component | Technology | Reason |
|-----------|-----------|--------|
| Real-time transport | Socket.IO v4 | Built-in rooms, reconnection, Express integration |
| Auth tokens | JWT (EdDSA/ES256) + opaque refresh | Stateless access, revocable refresh |
| Password hashing | Argon2id | OWASP first choice, memory-hard |
| Google Sign-In | Credential Manager API | Current standard (legacy deprecated) |
| Android token storage | DataStore + Tink + Keystore | Current standard (ESP deprecated) |
| Message partitioning | PostgreSQL monthly range | Efficient queries, easy cleanup |
| Toxicity detection | Google Perspective API (free) | Industry standard, async flagging |
| File scanning | ClamAV | Free, self-hosted |
| Deduplication | SHA-256 content-addressable storage | Exact dedup, simple |
| Future scaling | Redis pub/sub + Socket.IO adapter | Zero code changes when needed |

## Appendix B: npm Dependencies (New)

```
socket.io          — WebSocket server with rooms
argon2             — Password hashing (Argon2id)
jsonwebtoken       — JWT creation and verification
google-auth-library — Google ID token verification
sanitize-html      — HTML/XSS sanitization for user content
ngeohash           — Geohash encoding for geo-rooms
```

## Appendix C: Android Dependencies (New)

```
io.socket:socket.io-client:2.x       — Socket.IO client
androidx.credentials:credentials       — Credential Manager (Google Sign-In)
com.google.crypto.tink:tink-android   — Encryption for token storage
androidx.datastore:datastore-preferences — Token storage
```
