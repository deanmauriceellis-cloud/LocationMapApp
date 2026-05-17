# Ambient Ant Sprites — WickedMap Foot-Traffic Layer

**Status:** PARKED (S277, 2026-05-17). Revisit shortly per operator direction.
**Type:** WickedMap engine extension — single new `MapOverlay` sibling.
**Effort estimate:** one session for a working prototype; one more for polish + sprite library.
**Owner:** operator-directed, build-time + runtime, no external services.

---

## Concept

Tiny (8-16px) "ant-sized" walking-person sprites that wander the basemap as ambient foot traffic when the user is zoomed in close. Pure background atmosphere — they don't interact, aren't tappable, aren't POIs, don't appear in any list. The intent is to keep the map *visually alive* even when the user is standing still, reinforcing the "Salem is a casino of tourists" feeling.

Operator framing: *"just CGI'ish things that keep the map active … little ants on the screen … another dimension of crazy when we zoom close enough."*

---

## Why the engine already supports this

Investigation (S277 Explore) confirmed the WickedMap animation engine is **already production-wired** and architected for exactly this kind of addition. The new layer is a 5th sibling next to whitecaps / fireflies / grass / haunt-sprites — not new infrastructure.

| Piece | Where it lives | Reuse |
|---|---|---|
| 30fps heartbeat (`postInvalidateDelayed(33ms)`) | `wickedmap/WickedAnimationOverlay.kt:55-108` | Free — orchestrator already self-invalidates |
| `MapOverlay` interface (`tick + draw`) | `wickedmap/MapOverlay.kt` | Implement once for ants |
| Registration into `mapView.overlays` at z-order 0 | `SalemMainActivity.kt:1442-1477` | Add one line: `animations += AntOverlay(...)` |
| Viewport culling + Mercator projection | `wickedmap/CameraState`, `MapCamera.kt`, `MercatorMath.kt` | Free |
| Per-anchor duty cycling / phase offset pattern | `AnimatedWaterOverlay.kt`, `RollingGrassOverlay.kt` | Copy the pattern |
| Sprite billboard rendering pattern | `SpriteOverlay.kt:184` | Copy the bitmap-draw pattern |
| Sprite asset generation pipeline | `scripts/generate-ghost-portraits.py` (S275) | Reuse the AI Studio batch pattern |

**Z-order:** existing animations sit at overlay position 0 (drawn first, beneath POI markers + tour polylines). Ants would inherit this — satisfies `feedback_basemap_priority_over_animation.md` automatically.

---

## Gaps to close before build

Two missing pieces. Both small.

### 1. Zoom gate

No existing animation gates by zoom (`whitecaps render at every zoom` — noted in S232+ carry-forwards). Ants MUST gate, because they make no visual sense at z14 (you can't see them) and would burn frame budget for nothing.

**Approach:** add an optional `minZoom: Int` field to `MapOverlay` interface (default 0 to preserve current behavior), and have `WickedAnimationOverlay.draw()` skip overlays whose `minZoom > camera.intZoom`. Set `AntOverlay.minZoom = 18`.

Trivial — ~10 lines across `MapOverlay.kt` + `WickedAnimationOverlay.kt`.

### 2. Walking paths — where do ants walk?

Three options, in order of cheapness:

| Option | What it gives | Cost |
|---|---|---|
| **A. Random walks inside sidewalk polygons** | Ants ricochet inside a polygon set; no graph needed; reads as "people in plaza/street" | Cheapest — needs a sidewalk polygon kind added to `assets/wickedmap/polygons.json` via `PolygonLibrary`. Can be hand-traced for downtown core only at first. |
| **B. Walk along baked sidewalk edges** | Ants follow actual street/sidewalk centerlines; reads as proper foot traffic | Medium — needs a lightweight on-device sidewalk edge table (lat,lng pairs). Could derive at build-time from existing routing graph and bundle as JSON sibling to `polygons.json`. Avoid runtime querying the full routing-jvm graph — it's not built for this kind of access pattern. |
| **C. Walk along baked tour polylines** | Ants trail along the 4 baked `salem_tour_legs` polylines — every street with a tour gets foot traffic, others stay empty | Free — polylines already on-device. But coverage is sparse (only ~4 tours worth of streets). |

**Recommendation when we revisit:** start with **A** (polygon-bounded random walks) for the downtown core only. It's enough to prove the feel without building any new bake pipeline. Promote to **B** if A reads as "people pacing in circles" rather than "people walking somewhere." Skip **C** — coverage too sparse.

---

## Scope (when un-parked)

### Phase 1 — Prototype (one session)
- 1 sprite (single 4-frame walk-cycle, hand-drawn or AI Studio one-shot, ~3 directions: N/E/S — mirror E for W).
- ~50 ants in downtown core, polygon-bounded random walks (Option A).
- `minZoom = 18` gate.
- 30% opacity to confirm "background atmosphere" reading.
- One-line wire-up in `SalemMainActivity.kt` next to existing `animations += ...` block.
- Field-walk on Lenovo + Pixel 8, check (a) reads as alive not noisy, (b) zero impact on POI marker clarity, (c) zero perceptible frame drop.

### Phase 2 — Variety + modes (one session)
- 5-10 sprite variants via AI Studio batch (tourists with cameras, costumed Halloween folks, the occasional dog).
- Time-of-day density curve: low pre-9am, peak noon-5pm, tapered evening, sparse night (a few lit lanterns?).
- October bump: density 1.5x + Halloween costume sprites unlocked Oct 1 (reuses the date-gate hook from the planned Halloween seasonal layer in `STATE.md`).
- Optional: per-zone density bias (more ants on pedestrian malls, fewer on side streets).

### Phase 3 — Polish (optional, post-V1)
- Promote to walking-edge pathing (Option B) if random-walk feels wrong.
- Cohort hint: occasionally spawn a small cluster of 5-8 ants moving together (reads as "walking tour group" — Salem has 20-40 person walking tours per the operator).
- Sprite shadow blob for added depth.

---

## Decisions deferred until revisit

1. **Sprite count budget.** 50 / 100 / 200 / 500? Operator originally said "1000" — confirm tolerance after seeing 50.
2. **Sprite source.** AI Studio batch (cheapest, consistent with S275 ghost pipeline) vs hand-drawn pixel art (more soulful, slower). Default: AI Studio first pass; hand-redraw only if the AI output reads as too generic at 16px.
3. **Sound?** Currently no — but if the operator wants "lively casino" feeling, a faint distant crowd murmur at z18+ is the audio analog. Out of scope for V1; flag for post-V1.
4. **Show in walk-sim only? Always-on?** Probably always-on at z18+. Walk-sim showing them would help operator field-test without leaving Beverly.

---

## Non-goals (explicit)

- **NOT interactive.** No tap handlers. No selection. No POI association. They are pixels, not entities.
- **NOT navigation hints.** Ant direction is random; an ant walking north does NOT mean anything is north.
- **NOT historically accurate populations.** No 1692-vs-2026 mode. Salem-modern foot traffic only.
- **NOT a simulation.** No pathfinding around obstacles, no collision, no goals. Random walks within polygons.
- **NOT visible at low zoom.** Hard `minZoom = 18` gate. Above that they don't exist.
- **NOT in the basemap-priority rule's foreground.** Sit at overlay position 0 like the other ambient animations.

---

## Risks / things to watch

1. **Frame budget.** 30fps × 50-200 sprite draws is fine on Pixel 8, untested on Lenovo (V1 min-spec floor). Carry-forward S267 noted the existing draw loop has unrelated latency follow-ups (BillboardMarker viewport-cull, MapDebugDumper cadence) — none block ants but the field test should monitor.
2. **Visual interference with POI markers.** 64x64 was the original suggestion and would have competed; "ant-sized" at 8-16px should not. But validate at z18 on Lenovo before committing to denser counts.
3. **AAB size.** Sprite atlas adds <1 MB if kept tight (e.g., 16x16 × 4 frames × 4 dirs × 10 variants = ~10KB raw PNG). No size pressure.
4. **`feedback_basemap_priority_over_animation.md` compliance.** Z-order inherits correctly via existing overlay position 0; do not regress this when wiring.
5. **`feedback_visible_animation_in_app_each_session.md` compliance.** Any session that touches WickedMap must ship visible animation in the running Salem app. If we open this plan and don't reach Phase 1 wire-up, do not commit half-built code; leave the plan open.

---

## Anchors (verified S277)

- Engine root: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/`
- Heartbeat: `wickedmap/WickedAnimationOverlay.kt:55-108`
- Interface: `wickedmap/MapOverlay.kt`
- Registration: `SalemMainActivity.kt:1442-1477`
- Polygon library + asset: `wickedmap/PolygonLibrary.kt`, `assets/wickedmap/polygons.json`
- Sprite-asset precedent: `scripts/generate-ghost-portraits.py` (S275)

## Relevant memory

- `project_lively_map.md` — lively-map product direction
- `feedback_basemap_priority_over_animation.md` — animations stay below roads/markers
- `feedback_visible_animation_in_app_each_session.md` — ship animation visible in-app per session
- `feedback_lenovo_is_v1_minimum_spec.md` — Lenovo is the perf floor to test against
- `project_katrinas_collection_ghost_asset_pairing.md` — S275 AI Studio batch pattern reuse

---

_Parked S277 2026-05-17. Operator: "we will revisit it shortly."_
