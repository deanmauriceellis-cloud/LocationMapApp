# Post-V1 Architecture Decomposition — Wave 5 (S271)

**Status:** Plan only — no V1 code. Execute post-2026-08-01 ship, before Salem 400+ October 2026 peak.
**Drafted:** 2026-05-16 (S271) per V1 Health Audit Wave 5 obligation.

## Why now (i.e., NOT now)

S271 tech-debt survey surfaced five files over 1,500 LOC and one over 4,500. They are operator-acknowledged debt — `SalemMainActivity.kt` extraction is the canonical "we'll do it post-V1" item in this repo's history. Touching any of them now risks the 77-day ship window and the Form TX deadline. This doc captures the target architecture and rough effort so the work picks up cleanly when the runway opens.

## Target ranking (worst → best ROI on decomposition)

| File / module | LOC | Decomposition target |
|---|---:|---|
| `app-salem/.../SalemMainActivity.kt` | 4,945 | Extract 10 injected ViewModels' coordination + 30+ field collections into per-feature controllers; convert sibling fragments into ViewModel-backed Composables / Fragments with explicit state contracts |
| `app-salem/.../WitchTrialsMenuDialog.kt` | 2,586 | Lift grid layout + image cache + state into a `WitchTrialsViewModel`; dialog becomes a render-only shell |
| `app-salem/.../ui/SalemMainActivityTour.kt` | 2,006 | Move into `TourViewModel` (already exists, 779 LOC) as state-machine + side-effects; activity becomes glue only |
| `app-salem/.../ui/SalemMainActivityFind.kt` | 1,946 | Move into `FindViewModel` (already exists); split GeocodeClient out as a repository |
| `app-salem/.../ui/SalemMainActivityNarration.kt` | 1,895 | Move into `NarrationGeofenceManager` + a new `NarrationCoordinator` ViewModel |
| `web/src/admin/PoiEditDialog.tsx` | 3,037 | Split into 7 per-tab subcomponents (already tabbed UI) + extract validation hooks |
| `web/src/admin/AdminMap.tsx` | 2,676 | Decompose into layer-per-overlay components (POI / Tour / Burst-photos / Geocode-preview) |
| `app-salem/.../AppBarMenuManager.kt` | 1,287 | Extract `MenuItemGate` helper for the 6 scattered V1_OFFLINE_ONLY checks; menu state machine separate from rendering |
| `cache-proxy/lib/admin-lint.js` | 1,440 | Split schema-validation, category-mgmt, lint-rules into 3 files |
| `cache-proxy/lib/admin-pois.js` | 1,280 | Extract duplicates logic into `admin-pois-dedup.js` |

## Strategy

**Phase-gate by surface, not by module.** Each phase ships independently and is fully reversible. Order:

1. **Web admin first** (lowest risk — operator-only tool, no Play Store implications). Decompose `PoiEditDialog.tsx` and `AdminMap.tsx`. ~1 week.
2. **Cache-proxy lib splits** (no behavior change, pure organization). Run all existing route smoke tests before/after. ~3 days.
3. **Android sibling fragments → ViewModels** (`SalemMainActivityTour/Find/Narration`). Each fragment moves to its already-existing ViewModel sibling; activity glue shrinks. Smoke-test on Lenovo + Pixel 8 between each move. ~1.5 weeks.
4. **SalemMainActivity → coordination shell.** With siblings extracted, the 4,945 LOC monolith reduces to a `MainActivityCoordinator` + navigation host. ~1 week.
5. **WitchTrialsMenuDialog rebuild.** Standalone ViewModel + render shell. ~3 days.
6. **AppBarMenuManager.** MenuItemGate extraction is small but high-value defense-in-depth for the V1 ship gate. ~2 days.

**Total estimate: 3-4 weeks** of focused work post-V1 ship.

## Non-goals

- Multi-Activity architecture (current single-Activity + sibling fragments is fine after extraction).
- Compose UI rewrite (would balloon to multi-month). XML/View is staying through V1.0.x.
- DI rework (Hilt is fine; the issue is what's injected, not how).
- Test coverage parallel-track. Each phase adds tests for the extracted surface, but a separate "raise coverage" wave is out of scope here.

## Risks

- **Hidden coupling.** `SalemMainActivity` carries 30+ mutable collections (poiMarkers, trainMarkers, etc.) that are read by sibling fragments via the activity reference. Each move surfaces an implicit shared-state edge that becomes an explicit channel (StateFlow / SharedFlow). Naive extraction will produce wrong behavior; need a pre-pass to audit access patterns.
- **Narration timing.** `NarrationGeofenceManager` has per-second tick + cross-cutting state (last-fix-at-ms, 1-hour dedup, walk-sim PID guard). Extraction must preserve current timing behavior to avoid regressing the S267 latency fixes.
- **Pixel 8 portrait survey overlap.** Phase A portrait survey (STATE.md S271 docket) touches many of the same files. Sequence portrait work to land BEFORE this decomp wave kicks off so we're not chasing two architectural targets at once.

## When to revisit

Re-read this doc when the operator declares post-V1 mode. Update the LOC numbers from `wc -l` first — files may have grown or shrunk between now and the ship.
