# LocationMapApp v1.5 — Claude Code Context

> **LocationMapApp v1.5** — A multi-module Android/Web mapping platform. The primary product
> is **WickedSalemWitchCityTour**, a GPS-guided Salem, MA tourist app with historical content,
> geofence-triggered narration, and walking directions.

---

## Session Start Protocol

Execute in order before ANY work:

0. **Sync with remote** — `git fetch origin`, compare HEAD vs origin/main. Pull if behind. Alert if diverged. Never work on stale state.
1. **Read OMEN notes** — Read `~/Development/OMEN/notes/locationmapapp.md` for action items from project management. These are operational: action items, reminders, follow-ups, questions. Address applicable items this session.
2. **Read OMEN directives** — Read `~/Development/OMEN/directives/ACTIVE.md` for formal policy. Comply with all GLOBAL directives. Comply with TARGETED directives addressed to this project. OMEN directives take precedence over project-specific rules if there is a conflict.
3. **Read context** — CLAUDE.md, WickedSalemWitchCityTour_MASTER_PLAN.md, STATE.md, most recent session log, relevant design docs.
4. **Open live conversation log** — Create `docs/session-logs/session-NNN-YYYY-MM-DD.md` with session header. All significant actions, decisions, code changes, and reasoning during the session MUST be appended to this file as they happen. This is the crash-recovery mechanism — if the session dies, this log survives on disk.
5. **Status report** — Current phase/step from MASTER_PLAN, last session summary, completed steps, next steps, blockers. Include OMEN note and directive compliance status.
6. **Decision checkpoint** — Surface ALL pending questions in three categories:
   - Open decisions from the plan
   - OMEN notes/directives requiring action this session
   - Execution confirmation: "Next step is [X], will produce [Y]. Proceed?"
7. **Await direction** — Never execute until user confirms.

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

Execute all steps in order:

1. **Update living context docs** — Update CLAUDE.md, STATE.md, and WickedSalemWitchCityTour_MASTER_PLAN.md as needed.
2. **Archive removed content** — Anything removed from any .md doc goes to `docs/archive/` with format `FILENAME_removed_YYYY-MM-DD.md`. Never silently delete.
3. **Optimize context docs for loading** — Keep CLAUDE.md and STATE.md concise and current. Details in linked docs.
4. **Finalize conversation log** — Add session summary and final status to the live log in `docs/session-logs/`. Append summary to SESSION-LOG.md.
5. **Commit and push** — Stage all changes, commit with descriptive message, push to remote. Local and remote must never drift apart.
6. **Report to OMEN** — Write a session report to `~/Development/OMEN/reports/locationmapapp/session-NNN-YYYY-MM-DD.md` using the format at `~/Development/OMEN/templates/session-report.md`. This report must include: OMEN note acknowledgments, directive compliance, shared engine impact, security notes, test results, backward compatibility, conflicts with OMEN directives. A session without a report is an incomplete session.

---

## Project Status

**Version:** v1.5.71 (110 sessions completed as of 2026-04-09). Latest session: see `STATE.md` for current state and `SESSION-LOG.md` for the most recent session entry. Older session details archived to `SESSION-LOG.md` and `docs/session-logs/`.
**Phases 1-9 + 9A+ + 9T (8/9):** COMPLETE — core development, offline foundation, ambient narration done
**Phase 9P (POI Admin Tool) IN PROGRESS:** 9P.A (backend) DONE; 9P.B (admin UI) 6/8 done — 9P.6 taxonomy, 9P.7 AdminLayout shell, 9P.8 POI tree, 9P.9 admin map view, 9P.10 edit dialog, 9P.10b Salem Oracle integration all landed; **next: 9P.11 Highlight Duplicates wiring**
**Phase 9Q (Salem Domain Content Bridge):** not started — building→POI translation + 425 buildings + 202 newspapers
**Phase 9R (Historic Tour Mode):** not started — opt-in chapter-based 1692 tour
**Phase 10 (Production readiness):** DEFERRED behind 9P + 9Q + 9R — Firebase, photos, DB hardening, emulator verification
**Phase 11:** Branding, ASO & Play Store launch — target September 1, 2026
**Critical:** Salem 400+ quadricentennial is 2026. App must be in Play Store by September to capture October (1M+ visitors).

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

| Priority | File | Purpose |
|----------|------|---------|
| A (always) | `CLAUDE.md` | This file |
| A (always) | `STATE.md` | Current project state |
| A (always) | `WickedSalemWitchCityTour_MASTER_PLAN.md` | Full build plan |
| A (recovery) | `docs/session-logs/` | Live conversation logs (crash-recovery) |
| B (session start) | `SESSION-LOG.md` | Session history (summaries) |
| C (reference) | `GOVERNANCE.md` | Legal/compliance (56KB) |
| C (reference) | `IP.md` | Patent/copyright register |
| C (reference) | `COMMERCIALIZATION.md` | Business model |
| C (reference) | `DESIGN-REVIEW.md` | Architecture decisions |
| C (reference) | `CURRENT_TESTING.md` | Test checklist |

_Document Version: 0.1.0 — Created by OMEN Session 003 (2026-04-04)_
