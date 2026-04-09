# LocationMapApp v1.5 — Claude Code Context

> **LocationMapApp v1.5** — A multi-module Android/Web mapping platform. The primary product
> is **WickedSalemWitchCityTour**, a GPS-guided Salem, MA tourist app with historical content,
> geofence-triggered narration, and walking directions.

---

## Session Start Protocol

Execute in order before ANY work. Reads should be PARALLEL and HEAD-ONLY where noted — keep the read phase under 1,000 lines of context whenever possible.

0. **Sync with remote** — `git fetch origin`, compare HEAD vs `origin/master`. Pull if behind. Alert if diverged. Never work on stale state.
1. **Parallel read phase** (one tool call, all in parallel):
   - `~/Development/OMEN/notes/locationmapapp.md` — full file, action items addressed to this project
   - `~/Development/OMEN/directives/ACTIVE.md` — **head 200 lines only** (newest directives are at the top; deeper history is reference-only)
   - `STATE.md` — full file (it is now ~150 lines, current-state-only)
   - Most recent session log in `docs/session-logs/` (only the most recent — older logs only on demand)
   - `MEMORY.md` is already loaded automatically; do not re-read unless a specific memory file is needed
   - **DO NOT read `SESSION-LOG.md` at session start.** It is a backwards-looking changelog. The current state lives in `STATE.md` and the most recent live log; SESSION-LOG.md is only consulted on demand for historical context.
   - **DO NOT re-read `CLAUDE.md`** unless specifically investigating a protocol or structural question — it is loaded automatically by the harness.
2. **Open live conversation log** — Create `docs/session-logs/session-NNN-YYYY-MM-DD.md` with session header. **This is the canonical record of the session.** All significant actions, decisions, code changes, and reasoning MUST be appended here as they happen. This is the crash-recovery mechanism — if the session dies, this log survives on disk.
3. **Status report** — One concise message to the user summarizing: TOP PRIORITY (from STATE.md), current phase/step, last session summary (from the most recent live log), completed steps, next steps, blockers, applicable OMEN items.
4. **Decision checkpoint** — Surface ALL pending questions in three categories:
   - Open decisions from the plan
   - OMEN notes/directives requiring action this session
   - Execution confirmation: "Next step is [X], will produce [Y]. Proceed?"
5. **Await direction** — Never execute until the user confirms.

**Read full files only on demand.** If a specific question requires the full `SESSION-LOG.md`, the full `ACTIVE.md`, or any of the C-priority reference docs (`GOVERNANCE.md`, `IP.md`, `COMMERCIALIZATION.md`, `DESIGN-REVIEW.md`, `CURRENT_TESTING.md`), read them when the question arises — not at session start.

**Recovery from a killed session:** Read the most recent `docs/session-logs/session-NNN-YYYY-MM-DD.md` first to reconstruct in-flight context, then proceed with the parallel read phase as normal.

## Live Conversation Log

**Every session maintains a live log at `docs/session-logs/session-NNN-YYYY-MM-DD.md`.**

This is the crash-recovery mechanism. If a session is killed (context overflow, disconnection, etc.), this file survives on disk and the next session can reconstruct full context from it.

**What to log (append as it happens):**
- Session start context (status, phase, blockers)
- Every significant decision and reasoning
- Every file created, modified, or deleted (with summary of changes)
- Build/test results
- User direction and confirmations
- Errors encountered and how they were resolved
- Open questions and their resolutions

**Format:** Timestamped entries, append-only. Never rewrite earlier entries.

**On session start:** Read the most recent log in `docs/session-logs/` if recovering from a killed session.

## Session End Protocol

**The session story is written ONCE — in the live log at `docs/session-logs/session-NNN-YYYY-MM-DD.md`. Everywhere else gets a thin pointer.** This is the load-bearing rule of the optimized protocol. Do not duplicate the story across STATE.md, SESSION-LOG.md, and the OMEN report — that wastes context budget and creates four places to keep in sync.

Execute in order:

1. **Finalize the live log** — Append a closing session summary and final status to `docs/session-logs/session-NNN-YYYY-MM-DD.md`. This is the canonical record. Everything else in this protocol references it.
2. **Append to SESSION-LOG.md** — Add ONE entry with the session header line + a 2-3 sentence summary + a pointer to the live log. **Do not duplicate decisions, file lists, or detailed reasoning** — those live in the live log and `git log -p`. Example:
   ```
   ## Session NNN: YYYY-MM-DD — One-line title

   Two-or-three sentence summary of what shipped.

   Full session detail: `docs/session-logs/session-NNN-YYYY-MM-DD.md`. Commit: `<sha>`.
   ```
3. **Roll the SESSION-LOG.md window** — If `SESSION-LOG.md` now contains more than 10 sessions, move the oldest entries to the top of `SESSION-LOG-ARCHIVE.md` (newest archived first). Target: keep the 10 most recent sessions in `SESSION-LOG.md`.
4. **Update STATE.md only if it changed** — STATE.md is a snapshot, not a changelog. Only update it if (a) the TOP PRIORITY changed, (b) a phase status changed, (c) a carry-forward item was resolved or added, or (d) a new OMEN item appeared. **Do not append session summaries to STATE.md.** Keep it under 200 lines. If the file ever drifts above 200 lines, stop and compress it on the spot.
5. **Update CLAUDE.md only if structure or protocols changed** — CLAUDE.md should be stable. Touch it ONLY when (a) project structure changes (new module, new key reference file), (b) the session start/end protocol itself changes, or (c) the tech stack changes. Do NOT update CLAUDE.md every session.
6. **Update the master plan only if a phase status changed** — `WickedSalemWitchCityTour_MASTER_PLAN.md` only gets touched when a phase or step transitions state (planned → in-progress → done → blocked).
7. **Archive removed content** — Anything removed from any tracked .md doc goes to `docs/archive/` with format `FILENAME_removed_YYYY-MM-DD.md`. Never silently delete.
8. **Commit and push** — Stage all changes, commit with a descriptive message, push to remote. Local and remote must never drift apart.
9. **Write the OMEN report** — Use the **thin template** below. **Do not duplicate Files Changed or Decisions Made** from the live log — those are already in `git log -p` and in the live log. The OMEN report carries only OMEN-specific content. Write to `~/Development/OMEN/reports/locationmapapp/session-NNN-YYYY-MM-DD.md`.

### Thin OMEN report template (~80 lines max)

```markdown
# LocationMapApp Session NNN — YYYY-MM-DD

**Branch:** master | **Commit:** <sha> | **Live log:** `~/Development/LocationMapApp_v1.5/docs/session-logs/session-NNN-YYYY-MM-DD.md`

## Summary
Two or three sentences. What shipped, what was deferred, what blocked.

## OMEN Note Acknowledgments
- NOTE-LXXX — status (acknowledged / acted on / still pending / surfaced again — Nth session)
- (only notes touched or relevant this session; skip the standing ones unless status changed)

## OMEN Directive Compliance
- OMEN-XXX — status against this session's work (compliant / no-op / conflict)
- (only directives that apply to what shipped; skip the inapplicable ones)

## Shared Engine Impact
- (any change that affects core/, the cache-proxy, or anything other projects depend on; "none" is a valid answer)

## Cross-project Notes
- (anything OMEN should relay to a sibling project — Salem, GeoInbox, RadioIntelligence, etc.; "none" is a valid answer)

## Open Items for OMEN
- (only things requiring OMEN action — operator credential rotations, directive amendments, cutovers, sign-offs)

## Reference
- Full session detail (decisions, file changes, reasoning, build results): see live log path above.
- Commit detail: `git show <sha>` in the LocationMapApp repo.
```

**Skip these sections from the old report format** (they live in the live log + git):
- Files Changed table
- Decisions Made list
- Build/test results detail
- Reasoning narrative
- Code snippets

If OMEN needs any of that, it can read the live log directly — the path is in the report header.

**A session without a live log + a SESSION-LOG.md pointer + a thin OMEN report is an incomplete session.**

---

## Project Status

> **Current state — phase, version, session count, top priority — lives in `STATE.md`.** This section is intentionally minimal so CLAUDE.md does not need to be touched every session. If you need to know what is in flight right now, read `STATE.md`.

**Critical timing:** Salem 400+ quadricentennial is 2026. App must be in Play Store by **September 1, 2026** to capture October's 1M+ visitors. This is the only date that constrains every other planning decision.

---

## Non-Negotiable Design Principles

- Reference `GOVERNANCE.md` for full legal/compliance framework (56KB, lawyer-ready)
- Reference `DESIGN-REVIEW.md` for UX/architecture decisions
- Reference `IP.md` for intellectual property register (14 patentable innovations)
- Reference `COMMERCIALIZATION.md` for monetization strategy
- **COPPA compliance required** before Play Store submission (age gate)
- **No hardcoded credentials** in APK or tracked files
- **Offline-first:** Free/cheap tiers must work without network connectivity
- **Data provenance** on all entities: data_source, confidence, verified_date, stale_after

---

## Tech Stack

- **Android app** — Kotlin, Hilt DI, OkHttp, osmdroid, Room SQLite, targeting API 34
- **Web app** — React 19, TypeScript, Vite, Leaflet, Tailwind CSS (`web/`)
- **Cache proxy** — Node.js/Express on port 4300 (`cache-proxy/`)
- **PostgreSQL** — `locationmapapp` DB, 9 `salem_*` tables
- **Multi-module monorepo** — `:core` (shared), `:app` (generic), `:app-salem` (WickedSalemWitchCityTour), `:salem-content` (JVM content pipeline)

---

## Architecture

```
LocationMapApp_v1.5/
├── app/                   — Generic LocationMapApp module
├── app-salem/             — WickedSalemWitchCityTour module
├── core/                  — Shared library module
├── salem-content/         — JVM content pipeline (imports from ~/Development/Salem/data/json/)
├── cache-proxy/           — Node.js caching proxy
├── web/                   — React web app
├── docs/session-logs/     — Live conversation logs (crash-recovery)
├── docs/                  — Archive, other docs
├── STATE.md               — Current project state
├── SESSION-LOG.md         — Session history
├── WickedSalemWitchCityTour_MASTER_PLAN.md — 20-phase build plan
├── GOVERNANCE.md          — Legal/compliance framework
├── IP.md                  — Intellectual property register
├── COMMERCIALIZATION.md   — Monetization strategy
└── DESIGN-REVIEW.md       — UX/architecture decisions
```

---

## Cross-Project Dependencies

- **Salem JSON data** — `salem-content/` pipeline reads from `~/Development/Salem/data/json/` (49 figures, 500 facts, 80 events, 200 sources). Salem's JSON schema is a shared engine dependency tracked in OMEN.
- **GeoInbox** — Future photo source for POI imagery (see `~/Development/OMEN/architecture/geoinbox-concept.md`)
- **Phase 19 (LLM Integration)** — Blocked by OMEN-005 (Unified Server Engine Assessment). Must use shared conversation engine, not build parallel.

---

## Infrastructure

- **Repository**: github.com/deanmauriceellis-cloud/LocationMapApp_v1.5 (private, SSH)
- **Branch**: main
- **Tiered pricing**: Free (ads) / $4.99 Explorer / $9.99 Premium / $49.99-mo (Salem LLM subscription)

---

## Key Reference Files

| Priority | File | Purpose | When to read |
|----------|------|---------|--------------|
| A (always) | `CLAUDE.md` | This file | Auto-loaded |
| A (always) | `STATE.md` | Current snapshot — TOP PRIORITY, phase status, open items | Session start (parallel read) |
| A (always) | `docs/session-logs/session-NNN-*.md` | Most recent live conversation log (canonical record) | Session start (parallel read) |
| A (reference) | `WickedSalemWitchCityTour_MASTER_PLAN.md` | Full build plan, phase specs | On demand when entering a new phase or step |
| B (on demand) | `SESSION-LOG.md` | Rolling 10-session summary index with pointers to live logs | Only when investigating recent history; do NOT read at session start |
| B (on demand) | `SESSION-LOG-ARCHIVE.md` | Older session summaries (S001-S100 + pre-v1.5.51) | Only when investigating older history |
| C (on demand) | `GOVERNANCE.md` | Legal/compliance (56KB) | When the work touches legal, privacy, or compliance |
| C (on demand) | `IP.md` | Patent/copyright register | When the work touches a patentable innovation |
| C (on demand) | `COMMERCIALIZATION.md` | Business model | When the work touches monetization or tier gating |
| C (on demand) | `DESIGN-REVIEW.md` | Architecture decisions | When the work touches a design previously reviewed |
| C (on demand) | `CURRENT_TESTING.md` | Test checklist | When entering Phase 10 or running regression |

_Document Version: 0.2.0 — Optimized session protocols by Session 111 (2026-04-09). Original by OMEN Session 003 (2026-04-04)._
