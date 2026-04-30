# Katrina's Mystic Visitors Guide — V1 Intellectual Property Register

**Author:** Dean Maurice Ellis
**Copyright:** (c) 2026 Dean Maurice Ellis. All rights reserved.
**Last updated:** 2026-04-29 (S201 V1 triage).
**Status:** Proprietary — not licensed for redistribution.

> **Pre-pivot patent register archived** at `docs/archive/IP_removed_2026-04-29.md` — 14 patentable innovations from the generic LocationMapApp platform (Adaptive Radius Cap-Retry, Probe-Calibrate-Spiral Scanner, Overpass Fair-Queue, JTS R-Tree Geofence Engine for TFR/military/aircraft, etc.). None of those algorithms ship in V1; the register is preserved for historical context. Do not treat it as a V1 spec.

---

## V1 IP posture

**V1 ships only what V1 ships.** The live product is Katrina's Mystic Visitors Guide — a fully-offline, $19.99 flat-paid Salem walking-tour app — and its IP profile is correspondingly narrow:

- **Copyright on source code, content, and assets.** All-rights-reserved via copyright headers on source files; private GitHub repository.
- **Authored content under copyright** — historical narrations (364 in v15 asset DB), Witch Trials Oracle (49 NPC bios + 16 tile articles + 202 newspapers), tour polylines, custom UI assets, splash imagery, voice clips (recorded by operator).
- **Trade-secret protection** for build-pipeline scripts, admin-tool internals, and the publish chain (cache-proxy is server-side, never ships).
- **Trademark consideration** for "Katrina's Mystic Visitors Guide" — deferred to post-launch.

## Open legal-protection items

| Item | Owner | Status |
|---|---|---|
| **Form TX copyright registration** | Counsel | **Hard deadline 2026-05-20.** Per S201 operator confirmation: lawyer is handling. Filing as Dean Maurice Ellis individually (or under newly formed C-corp if entity is registered first); preserves statutory damages eligibility for the launch window. $65 + ~10 minutes online at copyright.gov. |
| Copyright headers on source files | Done | Originally added in pre-pivot work; retained on V1 source. |
| GitHub repo private | Done | `github.com/deanmauriceellis-cloud/LocationMapApp_v1.5` is private. |
| Trademark filing | Deferred | Post-launch. USPTO TEAS Plus / Standard, IC 009 (mobile application software). $250-350 filing fee. |
| Provisional patents | Deferred | The 14 algorithms in the archived IP register are pre-pivot generic-LMA work. V1-novel patentable angles (see below) deferred to a post-launch enumeration session. |

## V1-novel patentable angles (deferred enumeration)

Several V1 architectural decisions are arguably patentable on their own merits, distinct from the archived pre-pivot list. These have not yet been formally enumerated for provisional filing — flagged here so a post-launch IP session has a starting list:

1. **Tour-mode narration gate** with mode-dependent Layers checkboxes (`NarrationGeofenceManager.setTourMode`, S186) — `is_tour_poi` baseline + mode-separate `is_civic_poi` / `is_historical_property` toggles + 1-hour dedup persistence across walk-sim restarts.
2. **Server-side auto-overzoom of bundled basemap tiles** with `sharp`-backed crop+resize and stitch (S188) — walks UP up to 8 ancestor levels or DOWN up to 2 child levels, 500-entry LRU, `X-Tile-Source` introspection.
3. **Asset-schema-aligned bridge** (`align-asset-schema-to-room.js`, S185) — canonical Room-codegen JSON drives `createSql` and `identity_hash` + `user_version` stamping for bundled SQLite assets, eliminating destructive-migration class of bugs.
4. **Per-leg authored-polyline tour model** (S183/S184/S185) — `salem_tour_legs` PG table + admin-time on-device router precomputation + anchor+clip rendering for marker continuity. Tours are content, not runtime computation.
5. **Lint + audit + revert admin infrastructure** (S187 / S196) — instant data-quality checks, on-demand deep-scan, PG row-level audit triggers, inverse-op revert with attribution.

These are filed here as a **post-launch enumeration backlog**, not a Form TX or provisional-filing claim. Patent attorney consultation deferred until post-V1 with revenue runway to support the ~$1,280-per-quad-provisional micro-entity rate.

## What this register is NOT

This document is NOT:
- The pre-pivot 14-innovation patent register (archived; do not file provisionals against the archived list — those algorithms are not in V1).
- A patent-attorney engagement letter or strategy doc.
- A trademark-search or competitive-IP audit.

For full legal analysis, see `GOVERNANCE.md`. For live commercial posture, see `COMMERCIALIZATION.md`.
