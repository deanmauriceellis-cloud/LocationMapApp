# Performance & Stability Review — LocationMapApp v1.5 (Salem)

**Session 305 · 2026-05-29 · multi-agent review (`wf_eaa9cdcd-b4b`).** 12 subsystem reviewers fanned out in parallel → every finding adversarially verified (real? V1-retail-reachable? actually on a hot path?) → completeness critic. 98 agents, ~5.5M tokens.

**Outcome:** 85 raw findings → **63 confirmed real** (22 false positives killed by the verify pass) → **61 reachable by a retail paid user**. Severity after verification: **1 critical · 14 high · 18 medium · 28 low**, plus **8 completeness-critic gaps** (3 of them high). The adversarial pass corrected several reviewer over-claims (e.g. "FAB scale multiplies tilt memory" — refuted: it's a composite-time RenderNode transform, no backing buffer; "DebugLogger is R8-stripped in release" — refuted: it is NOT, the field sink is live in retail; several "leaks"/"scans" that turned out to be dead code or bounded).

> **Scope note:** PARKED V1-disabled features (transit/aircraft/webcam/metar/weather/radar/social + the recon scanners + DebugHttpServer + SuperAdmin) were de-prioritised — issues there are marked PARKED and don't gate the retail ship. Everything below tagged **V1** is reachable by a paying user.

---

## The headline: the #1 ship risk is NOT the 3D tilt — it's a cold-start ANR every user hits

The single **CRITICAL** finding (#1) is unrelated to tilt and affects **100% of users on first launch and after every app update**:

> **`WickedSalemApp.onCreate` copies the 370 MB tile archive synchronously on the main thread** (`OfflineTileManager.extractArchiveIfNeeded`). On install/update this is a multi-second main-thread freeze before the app is usable — an ANR-class hang on the min-spec Lenovo. (The code comment still says "30MB"; it's 370MB.)

It sits inside a **cold-start cluster** of main-thread work that compounds it: `verifyArchive` runs a full-table `GROUP BY` over the 370 MB tiles DB **on every launch** (#30); `PlaceResolver` copies + opens a 2.1 MB DB and runs point-in-polygon on the splash callback thread (#31); `PolygonLibrary.load` parses a 600 KB JSON on the main thread in `onCreate` (#32); the splash JPEG decodes full-size (~3.2 MB ARGB) on the main thread (#57). **First-launch experience is the biggest stability liability and it's fixable without touching the map engine.**

## The 3D-tilt OOM (your parked "it worked before" regression) — now fully root-caused

It is **not one bug** — it's a confluence the review pulled apart. When 3D tilt is engaged the `TiltContainer` extends the osmdroid MapView to **~6× height × ~4× width (~24× tile area)** (#33, #24), with **no zoom cap** so it overzoom-fetches native-res tiles to z20 across that giant surface (#14), while the **in-memory osmdroid tile cache is never sized for the extended viewport** (critic #2). The bitmaps that fill it are never recycled and the LRUs are undersized (#21). Then the kill blow: when Android fires `TRIM_MEMORY_RUNNING_CRITICAL`, **`onTrimMemory` sheds only a 600 KB JSON index** and frees none of the tens of MB of bitmaps (critic #1) — so the app gets killed instead of recovering.

**Why it regressed ("worked before"):** the prime suspect is the **S299/S301 graphics redo**. It introduced 384×384 ghost portraits and 1152×512 heroes that are decoded **full-resolution on the main thread into undersized/absent caches** (#6, #21, #28, #16, #19, #5) — new Large-Object-Space pressure (the 59 MB LOS churn seen on-device) that didn't exist before. The tilt geometry was always heavy; the new bitmap floor is what now tips it into the OOM. Overlay-count growth to ~622 (ghost badges + worship glyphs + narration markers + tour polylines) adds the per-frame multiplier (#4, #25, #11, #12).

**Tilt fix recipe (for when you unpark it):** cap `targetTop`/`EXTRA_SIDE_CAP` so peak area is single-digit-× not 24× (#33, #24) + clamp max zoom while tilted (#14) + size the osmdroid tile cache (critic #2) + make `onTrimMemory` actually evict bitmaps — MarkerIconHelper cache + GhostResolver/PoiHeroResolver LRUs + `mapView.tileProvider.clearTileCache()` (critic #1, #29) + downsample ghost/hero decodes to marker/strip size (#28, #6, #21). That set fixes the live regression *and* lowers the whole-app OOM floor.

## Steady-state cost (every user, every session — not just tilt)

- **The WickedMap engine force-invalidates the entire MapView at 30 fps unconditionally** even when nothing is animating (#11, #12) — the single highest-leverage frame-drop/battery fix; gate it on actual visible work.
- `CameraState.project()` allocates a `FloatArray` and recomputes `Math.pow`+trig **per call, thousands of times per frame** across the ambient overlays (#9); `FireflyOverlay` rebuilds its clip-path from polygon rings every frame (#10).
- **Per-GPS-fix churn (the walking hot path):** the GPS marker is removed+re-added to the 622-overlay list every fix (#3); **3 "debug" trigger rings are rebuilt every fix in retail, ungated** (~180 GeoPoint allocs/fix — these should be `BuildConfig.DEBUG`-only) (#17, #18); the proximity dock re-inflates views + decodes full-res icons every fix (#2, #5); 3+ separate `invalidate()` calls per fix (#35).
- **Cold-start jank:** the ambient water/parks overlays run a synchronous point-in-polygon **grid scan on the main thread in `onCreate`** (#13) — already once-ANR'd-and-mitigated per a code comment; move it off-thread.

---

## Recommended fix order (ship-impact × reach × effort)

**Tier 0 — ship-critical, do for Aug 1 (hits 100% of users): ✅ DONE + on-device-validated (S305).**
- **#1** move the 370 MB archive copy off the main thread (gate map behind it / stream during splash). Plus the cold-start cluster: **#30** (first-launch-only background verify), **#31** (PlaceResolver off-main), **#32** (PolygonLibrary off-main), **#57** (splash decode).
- **Shipped** `9ccdca0` (#1+#30: background extraction + splash `awaitReady()` gate + cheap repeat-launch verify) and `086361a` (#32+#13 ambient overlays off-main, #31 PlaceResolver off-main, #57 splash decode off-main). Fresh-install Pixel 8 pass: 370 MB copy 294 ms off-main (no ANR), 2nd-launch verify a 3 ms cheap probe (no scan), ambient overlays async-attach, operator-confirmed splash. (#13 — the ambient-overlay grid scan — folded in with #32 since they share the `setupMap` path. #31 not yet exercised on-device: needs LOCATION granted.)

**Tier 1 — the 3D-tilt OOM regression (your parked item, now scoped):**
- Geometry/zoom caps **#33 #24 #14**; `onTrimMemory` real eviction **critic-1 #29**; tile-cache sizing **critic-2**; bitmap downsampling **#28 #6 #21 #16 #19 #5**.

**Tier 2 — steady-state perf (battery/jank/GC for every user):**
- 30 fps unconditional invalidate **#11 #12**; `project()` allocations + culling **#9 #23 #10**; per-fix churn **#3 #17 #18 #35**. _(#13 off-main cold-start scan — done in Tier 0.)_

**Tier 3 — memory hygiene & correctness (lowers the OOM floor):**
- Bitmap recycle/LRU sizing **#21 #29 #52 #59**; TTS engine shutdown dead-code **critic-3**; per-fix DB batching **#43**; the remaining lows.

**Note on the deferred `!!` triage:** the active-path `!!` audit folds into Tier 2/3 — but the error-hygiene reviewer found the bigger active-path crash/jank risks are the full-res main-thread bitmap decodes and ungated debug overlays above, not raw `!!`s (most of which are guarded).

---

# Full findings catalog

_Severity is the post-verification adjusted value. Each is confirmed real by an independent adversarial check against the actual code._

## CRITICAL severity (1)

### 1. 370MB tile archive copied synchronously on the main thread in Application.onCreate (first launch / any APK update)
- **Subsystem:** startup-coldlaunch · **Category:** anr · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/WickedSalemApp.kt:63 (calls OfflineTileManager.extractArchiveIfNeeded → util/OfflineTileManager.kt:60-95)`
- **Fix:** Move extractArchiveIfNeeded off the main thread: run it on a background dispatcher/Thread (or WorkManager) and gate map-tile availability behind its completion, or stream the copy during the splash (which already waits multiple seconds for TTS). At minimum log + measure the copy and never run it on the Application/main thread. Update the stale 30MB comment to 370MB.


## HIGH severity (14)

### 2. ProximityDock decodes full-res 1152x512 poi-icon WebPs (~2.36MB ARGB_8888 each) on the main thread, uncached, up to 10x per GPS fix
- **Subsystem:** error-hygiene · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/ProximityDock.kt:203-213 (loadPoiIcon); driven by updateFromQueue 138-177`
- **Fix:** Add a downsampled+LRU bitmap cache keyed on path+targetPx (the dock icon is ~48dp, so decode with inSampleSize via a bounds probe — the on-disk 1152x512 should sample down to ~64px). Cache decoded bitmaps so a queue refresh re-binds from cache instead of re-decoding. Move the initial decode off the main thread (Dispatchers.IO) and set into the ImageView on Main. Debounce refreshProximityDockFromQueue() so it does not fire on every GPS fix.

### 3. updateGpsMarker recreates+re-adds the GPS marker to the ~622-element overlay list on every accepted fix (O(n) COW copy + invalidate per fix)
- **Subsystem:** gps-location · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:4200-4210`
- **Fix:** Create gpsMarker once; on each fix mutate gpsMarker.position = point and call invalidate() (or mapView.postInvalidateOnAnimation). Never remove/re-add to the overlay list per fix. Keep the cached icon.

### 4. TiltContainer.dispatchDraw allocates a fresh Marker list and re-projects all ~580 markers every frame while tilted
- **Subsystem:** markers-overlays · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:331-334 (in dispatchDraw); loop in drawBillboardedMarkers 382-447`
- **Fix:** Cache the filtered Marker list once per overlay-set change (the narration job already maintains narrationMarkersOnOverlay) instead of filterIsInstance every frame; reuse a preallocated MutableList. Add a viewport-bbox pre-cull against mv.boundingBox before the per-marker projection so off-screen markers (the vast majority of 580 at z17+) are skipped before toPixels. Consider only drawing markers whose GeoPoint is inside the extended MapView bbox.

### 5. ProximityDock decodes full-res 1152x512/512x512 poi-icon webp on the MAIN THREAD, uncached + un-downsampled, per dock refresh
- **Subsystem:** memory-bitmaps · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/ProximityDock.kt:203-209 (loadPoiIcon); called from update()/updateFromQueue() 111-131 / 152-178`
- **Fix:** Move decode off the main thread (Dispatchers.IO) and add an inSampleSize two-pass decode targeting the dock-circle px size; add a small bitmap LRU keyed by (assetPath|targetPx) shared with the other resolvers; reuse views instead of removeAllViews + re-decode every refresh.

### 6. refreshNarrationIcons re-decodes 384x384 ghost portraits on the MAIN THREAD on every zoom-bucket change (stale 'no allocation' assumption) — contributes to tilt LOS blowout
- **Subsystem:** memory-bitmaps · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:2911-2925 (refreshNarrationIcons) + 2883-2908 (narrationIconForZoom)`
- **Fix:** Either pre-render ghost badges for all 4 zoom buckets once on Dispatchers.Default at build time (so refresh is a pure assignment as the comment claims), or do the refresh loop off-thread; and enlarge/uncap the ghost-badge small-bitmap cache so the 384x384 source decode is needed at most once per ghost. Drop the source portrait reference after the badge is built so the big bitmap is GC-eligible immediately.

### 7. Narration highlight pulse animation issues a full-surface MapView.postInvalidate() every 300ms while narrating — a direct overdraw multiplier under 3D tilt
- **Subsystem:** narration-tts · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityNarration.kt:1902-1919`
- **Fix:** Invalidate only the highlight's dirty rect (the small ring bounds) instead of the whole MapView, or drive the alpha pulse via the existing per-frame WickedMap animation tick rather than a standalone 300ms full-invalidate loop. At minimum, pause the pulse when tilt is engaged or when memory pressure is detected.

### 8. Tilt draw loop allocates a fresh filterIsInstance<Marker> list (~580 elems) every frame
- **Subsystem:** poi-loading-cache · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:331-334`
- **Fix:** Cache the Marker sublist and rebuild it only when mv.overlays membership changes (e.g. invalidate the cached list from the marker add/remove sites: loadNarrationPointMarkers, replaceAllPoiMarkers, clearAllPoiMarkers, detachAllNarrationMarkers), not per frame. Or iterate mv.overlays in place with an `is Marker` check and skip the list allocation entirely.

### 9. CameraState.project() allocates a FloatArray AND recomputes pxPerTile/centerTileX/centerTileY (Math.pow + trig) on every call — invoked thousands of times per frame across the 3 ambient overlays
- **Subsystem:** rendering-wickedmap · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/MapCamera.kt:117-129`
- **Fix:** Hoist the per-frame invariants (pxPerTile, centerTileX, centerTileY, intZoom) into locals once at the top of each overlay's draw() (CameraState already exposes them — compute once, reuse). Replace project()'s FloatArray return with an out-param variant project(lat, lon, out: FloatArray) writing into a single reused 2-element array per overlay, eliminating the per-anchor allocation. Optionally precompute a flat affine (scale + offset) for lon→x once per frame since x is linear in lon at fixed zoom.

### 10. FireflyOverlay.draw iterates all ~4,461 flies and rebuilds the clipPath from polygon rings every frame at 30fps
- **Subsystem:** rendering-wickedmap · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/FireflyOverlay.kt:191-247`
- **Fix:** Cache the projected clipPath and only rebuild it when zoom/center/orientation actually changes (compare against last CameraState). Pre-bucket flies by polygon so hidden-cemetery flies are skipped without per-fly index lookups. Hoist project invariants per finding #1. Consider drawing halo+core as a single radial-gradient circle, or pre-render the orb to a small bitmap and stamp it, to halve drawCircle count.

### 11. WickedAnimationOverlay force-invalidates the entire osmdroid MapView at 30fps unconditionally, even when no overlay drew anything
- **Subsystem:** rendering-wickedmap · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/WickedAnimationOverlay.kt:103-108`
- **Fix:** Track per-frame whether anything is in-view/animating (the overlays already compute drewThisFrame / anyVisible). Only reschedule the invalidate when at least one inner overlay has visible work, and stop the animation loop (skip the postInvalidateDelayed) when idle; resume it on the next map move/zoom or sprite fire. Also pause while the activity is not RESUMED.

### 12. WickedAnimationOverlay self-invalidates the MapView at 30fps unconditionally, forcing the entire giant tilted display list to re-record every frame even when idle
- **Subsystem:** tilt-memory · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/WickedAnimationOverlay.kt:108`
- **Fix:** Gate the self-invalidate on actual work: skip postInvalidateDelayed when no overlay reported activity this frame (e.g. SpriteOverlay.activeSwoops empty AND water/firefly amplitude below a threshold). Under tilt specifically, drop the animation cadence (suppress or halve frameIntervalMs) since the redraw cost is multiplied by the extended viewport. This is the single highest-leverage fix for the frame drops.

### 13. Ambient-overlay grid-scan constructors (water 193k cells, parks 45k cells, each × pointInRing over up to 352/427 polygons) run synchronously on the MAIN thread inside onCreate→setupMap
- **Subsystem:** rendering-wickedmap · **Category:** anr · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/AnimatedWaterOverlay.kt:56-155 (init); wired at SalemMainActivity.kt:1497-1504 from onCreate:860→setupMap:1396`
- **Fix:** Build the anchor arrays off the main thread (lifecycleScope + Dispatchers.Default), then add the overlays to mapView.overlays on the main thread once built. Alternatively precompute the anchor packed-FloatArrays at build time and ship them as an asset, or coarsen the grid step / restrict the scan to the per-polygon bbox rather than the union bbox (the union bbox over Salem-wide water spans ~491×394 cells, most empty).

### 14. No zoom cap while tilted — overzoom to z20 over the 6x-tall viewport multiplies native-resolution tile bitmaps
- **Subsystem:** tilt-memory · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:1430, 1687-1691`
- **Fix:** When tiltDeg>0, clamp the user-reachable max zoom (e.g. to z18) so the extended viewport fetches fewer/upsampled tiles, and/or restore prior zoom on tilt-off. Tie the clamp into setTiltDegrees so it is applied/relaxed on transition.

### 15. applyMapExtension forces a synchronous requestLayout/measure of a 12600px MapView on the tilt-toggle main-thread path
- **Subsystem:** tilt-memory · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:280-293`
- **Fix:** Acceptable as a one-shot, but pair it with the zoom/LOD clamp so the burst of tile requests it triggers is bounded; consider deferring the heavy extension to the next frame and showing the unextended trapezoid for one frame to avoid the synchronous measure stall.


## MEDIUM severity (18)

### 16. GhostResolver 8MB LRU undersized vs full ghost set with no downsample → re-decode thrash during marker rebuild on tilt zoom changes
- **Subsystem:** error-hygiene · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/GhostResolver.kt:40-74 (cache + load); caller SalemMainActivity.kt:2889 narrationIconForZoom`
- **Fix:** Downsample ghost decodes to the actual marker badge size (max ~40dp) via inSampleSize, which shrinks each bitmap ~10-100x and lets the full set live in cache; size the LRU against the real decoded footprint; and avoid re-decoding on every zoom bucket by caching the badge BitmapDrawable per (poiId,sizeDp).

### 17. User trigger rings ('debug overlay') are ungated and rebuilt on EVERY fix in retail — 3 pointsAsCircle allocations (~180 GeoPoints) + invalidate per fix
- **Subsystem:** gps-location · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:3750-3751, 2472-2508`
- **Fix:** Gate creation AND update behind BuildConfig.DEBUG or the SuperAdmin toggle (these are debug bands, not a retail feature). If kept, recompute circle points only when the center moves beyond a threshold, and avoid the standalone invalidate (fold into the single per-fix invalidate).

### 18. updateUserTriggerRings() runs every GPS fix in retail: 3 circle-polygon rebuilds (~180 GeoPoint allocs/fix) + forced full mapView.invalidate() + permanent debug overlays
- **Subsystem:** lifecycle-coroutines-leaks · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:3751 (call site) / 2472-2507 (impl)`
- **Fix:** Gate the call site at line 3751 behind BuildConfig.DEBUG (or BuildDefaults.GPS_TRACK_VISIBLE / a SuperAdmin toggle) the same way other recon overlays are gated, and remove the 3 ring overlays when disabled. If kept, build the circle point-lists once and translate the existing Polygon rather than reallocating pointsAsCircle every fix, and avoid the unconditional full invalidate.

### 19. PoiHeroResolver.applyTo does a synchronous main-thread decode with NO bitmap cache and NO downsample — 2.25MB ARGB per POI tap (x2)
- **Subsystem:** memory-bitmaps · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/PoiHeroResolver.kt:178-202 (applyTo); callers PoiDetailSheet.kt:263/359/374 from onViewCreated`
- **Fix:** Decode off the main thread and add a shared LRU keyed by (assetPath|targetPx) with two-pass inSampleSize sized to the ImageView/hero strip (20% screen height). The hero strip needs ~screen-width px, not 1152x512 raw.

### 20. CollectionSheet RecyclerView binds decode 384x384 ghost portraits + frames per-bind, churning the 8MB LRU during scroll
- **Subsystem:** memory-bitmaps · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/CollectionSheet.kt:390-397 (PoiVh.bind), 511-519 (TileVh.bind)`
- **Fix:** Decode into a tile-sized bitmap via inSampleSize (64dp target ≈ inSampleSize 2 on 384x384) and decode on a background dispatcher with a placeholder; consider an image-loading lib pattern (off-thread + view-tag guard) so fast flings don't pile up decodes.

### 21. Decoded hero/ghost/dock/marker bitmaps are never recycled; LRU eviction leaves large LOS bitmaps to GC — the 59MB LOS pressure source
- **Subsystem:** memory-bitmaps · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/GhostResolver.kt:60-74 (load), HeroAssetLoader 36-51, PoiHeroResolver 188-191, ProximityDock 204-209`
- **Fix:** The real fix is decoding smaller (inSampleSize to actual target) so the per-bitmap cost drops by 4-9x; additionally consider an entryRemoved() hook on the LRUs to recycle evicted bitmaps once no view holds them (safe only if not shared with a live ImageView), and downscale-then-recycle-source in createScaledBitmap paths.

### 22. Per-marker copyBounds() Rect allocation inside the per-frame tilt marker loop
- **Subsystem:** poi-loading-cache · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:442-445`
- **Fix:** Hoist a single reusable Rect field (e.g. `private val savedBoundsTmp = Rect()`) and use `icon.copyBounds(savedBoundsTmp)` / restore from it, instead of allocating per marker per frame.

### 23. Water and Grass overlays project every anchor BEFORE the viewport cull, paying the full project() cost for off-screen anchors
- **Subsystem:** rendering-wickedmap · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/AnimatedWaterOverlay.kt:202-205 (mirror in RollingGrassOverlay.kt:164-167)`
- **Fix:** Cull in geographic space before projecting: compute the visible lat/lon bbox once per frame (FireflyOverlay already does this with tileXToLon/tileYToLat) and skip anchors whose lat/lon fall outside it before calling project(). Combined with the out-param project() from finding #1 this removes nearly all wasted projection work.

### 24. TiltContainer extends the MapView to up to ~24× tile area (6× vertical × ~4× lateral) at high tilt, then rebuilds pass-2 billboards on every scroll/zoom event
- **Subsystem:** rendering-wickedmap · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:280-286, 519-530, 331-334`
- **Fix:** Cap EXTRA_TOP_FRACTION / EXTRA_SIDE_CAP much lower at the ladder tilts actually shipped (0/30/36/42/48° — not 75°), so peak tile area is bounded to a few× not 24×. Hoist the `filterIsInstance<Marker>()` allocation out of dispatchDraw (cache the marker sublist, refresh only when overlays change). Verify tiles for the extended bbox are evicted promptly when tilt returns to 0.

### 25. Per-frame allocations in tilt pass-2: filterIsInstance over ~622 overlays + Rect copyBounds per marker, ~30x/sec
- **Subsystem:** tilt-memory · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:331, 442`
- **Fix:** Maintain a cached List<Marker> (or List<BillboardMarker>) rebuilt only when overlays change, not per frame. Reuse a single preallocated Rect for save/restore of icon bounds instead of copyBounds() per marker (mirror the existing billboardXY/billboardPt reuse pattern already in the class).

### 26. Active tour route polylines stay registered as osmdroid overlays and are re-projected every frame at the ~1798x12600 tilt extent
- **Subsystem:** tour-engine · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityTour.kt:956-968 (overlays.add per leg) + clearTourOverlays:1035`
- **Fix:** Halve the overlay count by drawing one Polyline per leg with a single thick paint (or use osmdroid's built-in outline support) instead of separate border+line overlays. Consider simplifying (Douglas-Peucker) the leg geometry once at decode time before setPoints so far fewer vertices are re-projected per frame. Cap antialias on the border layer.

### 27. PoiHeroResolver.applyTo decodes 1152x512 hero/icon WebPs with plain decodeStream (no downsample, no cache) on the main thread, bypassing HeroAssetLoader's defensive two-pass downsampler — twice per PoiDetailSheet open
- **Subsystem:** error-hygiene · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/PoiHeroResolver.kt:187-195 (applyTo); callers PoiDetailSheet.kt:359 bindHero + 374 bindOverview`
- **Fix:** Route applyTo through a downsampling+LRU path (reuse/fix HeroAssetLoader.decodeAsset and update its TARGET_W_MAX/H_MAX to the current 1152x512 hero dimensions so calcSampleSize actually engages), decode off the main thread, and cache by asset path. The dead loadTriptych* code should either be wired in or removed so the protective downsampler is the real load path.

### 28. GhostResolver decodes 384x384 ARGB_8888 portraits (~590KB each) with no inSampleSize, for markers rendered at 18-40dp
- **Subsystem:** markers-overlays · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/GhostResolver.kt:60-74 (load), consumed by SalemMainActivity.narrationIconForZoom 2887-2899`
- **Fix:** Decode with inSampleSize computed from the largest needed marker size (~110px) — decodeStream twice (inJustDecodeBounds then sampled) or store/decode the portrait at a map-marker-appropriate resolution. Since ghostBadge only ever needs <=40dp, a 128px decode cuts each bitmap ~9x. Keep the full-res decode only for the Collection detail view, not the map marker path.

### 29. Static marker-icon bitmap cache (up to 4096 BitmapDrawables) is never released on memory pressure or destroy
- **Subsystem:** poi-loading-cache · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/MarkerIconHelper.kt:41-48, 632-633`
- **Fix:** Add MarkerIconHelper.clearCache() (and GhostResolver.clearCache(), circleIconCache clear) to onTrimMemory at TRIM_MEMORY_RUNNING_LOW alongside PolygonLibrary.unload(); consider a byte-budget LinkedHashMap (sizeOf bitmaps) instead of a fixed 4096-entry count so 580 large labeled bitmaps can't pin ~30MB.

### 30. verifyArchive runs a full-table GROUP BY scan over the 370MB tiles DB on the main thread on EVERY launch
- **Subsystem:** startup-coldlaunch · **Category:** anr · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/util/OfflineTileManager.kt:94 (verifyArchive), 101-129`
- **Fix:** Make verification first-launch-only (or debug-only), run it on a background thread, and replace the full-table COUNT GROUP BY with a cheap existence check (e.g. `SELECT 1 FROM tiles LIMIT 1` or a pragma). Drop the unused MIN/MAX query.

### 31. PlaceResolver copies a 2.1MB asset DB, opens it, and runs PIP queries on the main thread during the splash (FusedLocation callback)
- **Subsystem:** startup-coldlaunch · **Category:** anr · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SplashActivity.kt:219-240 (callback) → splash/PlaceResolver.kt:52-120`
- **Fix:** Pass a background Executor to addOnSuccessListener (e.g. `addOnSuccessListener(backgroundExecutor) { ... }`) or move the PlaceResolver.resolve + LocationContextBuilder.build work to a coroutine on Dispatchers.IO, marshalling only the final pick text back to the main thread. ensureOpen is documented 'safe from any thread' — use it off-main.

### 32. PolygonLibrary.load() reads and JSON-parses a 600KB asset synchronously on the main thread inside setupMap()/onCreate
- **Subsystem:** startup-coldlaunch · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:1497 (PolygonLibrary.load(this)) → wickedmap/PolygonLibrary.kt:39-98`
- **Fix:** Load + parse polygons.json on a background dispatcher and add the WickedAnimationOverlay once parsing completes (animations are background atmosphere per design and can appear a frame late). Consider a more compact in-memory layout (parallel DoubleArrays) instead of List<Pair<Double,Double>>.

### 33. Tilt extends MapView to ~6x height + ~4x width, blowing the osmdroid tile-bitmap pool (root cause of the OOM/LMK anchor issue)
- **Subsystem:** tilt-memory · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:280-287, 519, 530`
- **Fix:** Cap targetTop in absolute px (e.g. min(containerH*EXTRA_TOP_FRACTION, ~2.5*containerH)) AND/OR clamp the effective tile-fetch zoom while tilted (force osmdroid to a lower LOD for the extended rows so the wedge fills with upsampled tiles instead of native z20). Drop EXTRA_TOP_FRACTION to ~2.5-3.0 and EXTRA_SIDE_CAP to ~0.6 so peak area stays in single-digit-x. Also call Configuration.getInstance().cacheMapTileCount/Overshoot explicitly so the cache is bounded independent of the inflated view size.


## LOW severity (28)

### 34. gpsTrackOverlay.actualPoints grows unbounded in memory (seeded with 24h of points) and is appended + redrawn on every fix — no in-memory cap
- **Subsystem:** gps-location · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:3743-3748, 3312-3319`
- **Fix:** Cap the in-memory actualPoints (e.g. ring-buffer / drop points older than the visible window or beyond a max count) independent of the DB retention. Only invalidate when the overlay is actually visible (already partly guarded) and consider simplifying far-zoom geometry.

### 35. Per-fix observer fires 3+ independent mapView.invalidate() calls plus an animateTo every fix (redraw amplification)
- **Subsystem:** gps-location · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:3743-3863`
- **Fix:** Collapse to a single invalidate() at the end of the observer (or postInvalidateOnAnimation). Skip the marker/ring/track invalidations individually. Consider setCenter (no animation) or a longer-throttled animateTo for continuous follow.

### 36. In-memory GPS journey polyline grows unbounded for the whole session (one GeoPoint appended per accepted fix) and is redrawn on every per-fix invalidate
- **Subsystem:** lifecycle-coroutines-leaks · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:3743-3748 (append) / 3312-3319 (seed)`
- **Fix:** Cap actualPoints to a rolling window (e.g. last N points / last X meters) in the per-fix append, or decimate by distance before adding. Avoid calling mapView.invalidate() on every fix when the journey overlay is hidden (the contains() check already short-circuits draw, but invalidate is still issued).

### 37. walkSimJob still hand-managed (not migrated to JobCoordinator); long-lived run loop drives full per-fix observer at 1Hz and uses isCancelled polling instead of structured cancellation
- **Subsystem:** lifecycle-coroutines-leaks · **Category:** stability · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:2159 (launch) / 2286,2346 (isCancelled polling) / 478 (field)`
- **Fix:** Migrate walkSim into JobCoordinator.launch("walkSim") (gives cancel-before-relaunch + central cancelAll) and replace the walkSimJob?.isCancelled polling with coroutineContext.isActive / ensureActive(). Throttle the per-step work that does not need 1Hz (animateTo, ring/journey redraw) to reduce per-fix cost during walk-sim.

### 38. refreshNarrationIcons walks and reassigns icons for all ~580 markers on every map scroll/zoom that crosses a zoom bucket
- **Subsystem:** markers-overlays · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:2910-2926 (refreshNarrationIcons), called unconditionally from MapListener.onScroll 1750 and onZoom 1761`
- **Fix:** Refresh only markers currently within the viewport bbox on bucket change (the prior visible-only behavior, but fix the stale-edge bug by also refreshing on the next scroll-settle rather than every tick). Debounce the bucket-change icon swap to scroll-idle. narrationIconForZoom for ghosts should be hoisted out of the hot loop since the badge per (id,bucket) is stable.

### 39. normalizeForTts() compiles ~50 inline Regex objects on the main thread for every narration segment
- **Subsystem:** narration-tts · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/NarrationManager.kt:666-802 (called from speak() line 620, on Main)`
- **Fix:** Hoist all Regex literals to `private val` in the companion object (compile once). The saintPattern alternation should be a precompiled companion val, not rebuilt from the Set on every call. Optionally move normalize+chunk off the main thread before tts.speak.

### 40. checkPosition() linearly scans the entire narrated-POI set on every GPS fix — haversine + multi-branch gate + per-POI map churn per POI
- **Subsystem:** narration-tts · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/NarrationGeofenceManager.kt:589-698 (called per fix from SalemMainActivityTour.updateTourLocation:1335)`
- **Fix:** Spatial pre-filter (grid/bbox) so only POIs within NEARBY_RADIUS_M (300m) are distance-tested, instead of haversining the full table each fix. Build the upper() category once on the POI model. Guard the logQualified key-build behind the same DEBUG check that gates the log emit.

### 41. refreshProximityDockFromQueue() recomputes the full queue with multiple haversine passes on every GPS fix (and every enqueue/dequeue)
- **Subsystem:** narration-tts · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityNarration.kt:1369-1408 (called from updateNarrationUserPosition:879, per fix)`
- **Fix:** Compute each POI's distance once into a local map/list, then count/filter/sort against the cached value (avoid recomputing haversine in count passes and inside the sort comparator). Debounce dock refresh to at most once per fix rather than enqueue+dequeue+fix all triggering it.

### 42. cachePoi debounce coroutine launched on every onScroll tick during GPS-follow even though body is V1-blocked
- **Subsystem:** poi-loading-cache · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:1752-1756`
- **Fix:** Gate the cachePoi launch behind `!FeatureFlags.V1_OFFLINE_ONLY` (and !findFilterActive) at the call site so retail scroll ticks skip the launch entirely, matching the existing S234 call-site gating used for the aircraft/transit reloads two lines above.

### 43. PoiEncounterTracker upserts each in-proximity POI individually (not batched) on every GPS fix — continuous write/fsync churn while walking
- **Subsystem:** room-data · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/userdata/PoiEncounterTracker.kt:156-166`
- **Fix:** Add a `@Transaction suspend fun upsertAll(list)` (or wrap the loop in withTransaction) so the per-fix writes commit as one transaction → one fsync instead of N. Optionally only persist on encounter open/close + periodic flush rather than every fix, since the in-memory `active` map already holds live stats.

### 44. dispatchTouchEvent allocates PointerProperties/PointerCoords arrays + obtains/recycles a MotionEvent on every touch event while tilted
- **Subsystem:** tilt-memory · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt:472-503`
- **Fix:** Preallocate the props/coords arrays (resize only when pointerCount grows) and the pts FloatArray as fields, like the existing billboardXY reuse. The obtain/recycle pair is necessary but the per-event array churn is avoidable.

### 45. Per-GPS-fix O(total-vertices) leg-highlight scan re-walks every segment of every tour leg polyline on the tilt-extended MapView
- **Subsystem:** tour-engine · **Category:** performance · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityTour.kt:1092-1112 (findClosestLegIdx), called from 1362 / 1170`
- **Fix:** Cache last-fix GeoPoint and skip the scan when the user moved < a few metres (most fixes). Coarse-cull legs by bounding box before the segment loop. Only consider the current leg ±1 neighbour rather than all legs (a walker can only advance to an adjacent leg). Short-circuit before the per-segment loop, not after.

### 46. gpsTrackOverlay.actualPoints grows unbounded for the whole session and forces a full tilt-extent invalidate on every fix
- **Subsystem:** tour-engine · **Category:** memory · **V1-retail-reachable** · HOT PATH
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:3743-3748`
- **Fix:** Cap the in-session point count (ring-buffer / cap to N most-recent or simplify when it exceeds a threshold). Only invalidate when the GPS track is actually visible AND the new point is on-screen; otherwise skip the invalidate and let the next map move repaint.

### 47. MBTA train/subway/bus refresh while(true) loops launched from onStart() are not V1-gated at the call site and are never cancelled in onDestroy
- **Subsystem:** lifecycle-coroutines-leaks · **Category:** stability · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:987-1011 (onStart) / SalemMainActivityTransit.kt:112-118 (loop)`
- **Fix:** Gate the onStart MBTA restore block behind !FeatureFlags.V1_OFFLINE_ONLY at the call site (per the v1_gate_at_call_site rule) so the spinning loops never launch in retail. Optionally migrate these refresh jobs into JobCoordinator so cancelAll() covers them.

### 48. Narration markers re-added to overlays on every loadNarrationPointMarkers but synced into a fresh list with O(n) clear+rebuild plus a full invalidate
- **Subsystem:** markers-overlays · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:2849-2859`
- **Fix:** Drop the legacy narrationMarkers list and have refreshNarrationIcons iterate narrationMarkersOnOverlay + narrationMarkerCache directly, or only mutate the list by the toAdd/toRemove deltas instead of clear+rebuild. Skip bringStationMarkersToFront in V1 where stationMarkers is always empty (it already early-returns, but the call still executes the empty-check each toggle).

### 49. loadNarrationPointMarkers runs the full multi-pass filter chain + SharedPreferences read on the main thread over the 2039-POI set
- **Subsystem:** poi-loading-cache · **Category:** anr · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:2697-2775`
- **Fix:** Wrap the SharedPreferences read + the tier/histMode/layers filter passes in `withContext(Dispatchers.Default)` and only touch the overlay list (toRemove/toAdd apply) back on Main; the SalemPoi snapshot is immutable so the filtering is thread-safe off-Main.

### 50. FindViewModel.queryNearby recomputes Haversine distance 2-3x per candidate (filter + sort + toFindResult)
- **Subsystem:** poi-loading-cache · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/FindViewModel.kt:224-231, 248-252`
- **Fix:** Compute distance once per candidate into a Pair(poi, dist) (or cheap squared-degree proxy for the filter, since poiCache.findNearby already bbox-prefilters), sort on the precomputed value, and reuse it in toFindResult instead of recomputing.

### 51. replaceAllPoiMarkers does full clear-and-rebuild of every marker on each Find/scroll-in-filter-mode refresh (no diff)
- **Subsystem:** poi-loading-cache · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:4416-4452`
- **Fix:** Apply the same id-keyed diff pattern used by loadNarrationPointMarkers (a poiMarkerCache by place id; compute toAdd/toRemove against the on-overlay set) so unchanged markers are reused instead of reallocated on each filtered refresh.

### 52. SpriteOverlay decodes full-size sprite WebP frames with no inSampleSize/downsample and never recycles; up to 16 frames per sprite cached for app lifetime
- **Subsystem:** rendering-wickedmap · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/SpriteOverlay.kt:379-406`
- **Fix:** Decode with inSampleSize sized to ~TARGET_SIZE_PX*MAX_SCALE (and/or inPreferredConfig RGB_565 for opaque sprites). Optionally only decode the FRONT_FRAME_INDEX frame (the code uses a single fixed frame per fire) instead of all 16. Add an LRU cap + recycle on eviction / onTrimMemory so the sprite cache doesn't grow unbounded.

### 53. WickedAnimationOverlay's per-overlay failure suppression is permanent (HashSet never cleared) — a transient projection/draw exception silently kills an ambient layer for the whole session
- **Subsystem:** rendering-wickedmap · **Category:** correctness · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/WickedAnimationOverlay.kt:73-87`
- **Fix:** Use a bounded retry: count consecutive failures per overlay and only suppress after N in a row, resetting the counter on any successful frame, so a one-off exception doesn't kill the layer for good. Keep the first-occurrence error log to avoid log spam.

### 54. Full POI table (~2,039 rows) is materialized in memory ~3 times at startup; TourPoiDao.findAll bypasses PoiCache
- **Subsystem:** room-data · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TourViewModel.kt:94, 101`
- **Fix:** Route getAllTourPois()/_allPois through PoiCache (project from the cached SalemPoi list, or hold a single shared list). Eliminate the duplicate Room re-projection at init and collapse allPoisCache/_allPois into one source. Cuts startup DB work and steady-state heap by ~2x on the min-spec Lenovo, lowering the OOM floor that the tilt+walk-sim blowout pushes against.

### 55. CollectionSheet loads the entire poi_visit table (listAll) and re-queries findByCollection 2x per sheet open
- **Subsystem:** room-data · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/CollectionSheet.kt:220-226, 251`
- **Fix:** Replace listAll()+filter with a `@Query(... WHERE poi_id IN (:ids))` that returns full PoiVisit rows for the heard set, and fetch findByCollection(pid) once in loadAll(), passing the list to both builders. All runs in withContext(Dispatchers.IO) so it is off-main; this is allocation/scan reduction on a sheet-open event, not an ANR risk.

### 56. setContentView happens after a duplicate osmdroid Configuration.load + heavy synchronous toolbar/menu wiring, all before first frame
- **Subsystem:** startup-coldlaunch · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:759-862`
- **Fix:** Drop the duplicate Configuration.load in the Activity (already done in Application.onCreate). Defer non-first-frame wiring (sensor inventory log, FavoritesManager, haunt config, GPS track prune) to a `mapView.post{}` / lifecycle STARTED block so first frame draws sooner.

### 57. Splash JPEG decoded full-size (800x1000 nodpi → ~3.2MB ARGB_8888) on the main thread via setImageResource
- **Subsystem:** startup-coldlaunch · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SplashActivity.kt:116-117`
- **Fix:** Either move the splash images out of nodpi so they downscale to device density, or decode with inSampleSize / Glide-style async into the ImageView off the main thread, or ship the splash as a smaller bitmap matching typical screen height. Use RGB_565 if alpha isn't needed.

### 58. performCinematicZoom chains postDelayed callbacks that touch binding.mapView with no lifecycle guard (showWelcomeDialog at 2300ms)
- **Subsystem:** startup-coldlaunch · **Category:** stability · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:1637-1654 and 1455-1462`
- **Fix:** Guard the delayed bodies with an isFinishing/isDestroyed + lifecycle.currentState.isAtLeast(STARTED) check, or post via a Handler whose callbacks are removed in onPause/onDestroy.

### 59. Sprite frame bitmaps and scaled marker-icon bitmaps are cached forever with no eviction or recycle
- **Subsystem:** tilt-memory · **Category:** leak · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/wickedmap/SpriteOverlay.kt:62, 379-406`
- **Fix:** Bound the sprite-frame cache (LRU by id, evict idle sprites) and recycle frames when their last HauntConfig is removed in setHaunts; give circleIconCache an LRU bound. Optionally decode sprite frames with inSampleSize to halve their LOS footprint.

### 60. rebuildLegOverlayOrder does a full detach + repaint + re-add of every leg overlay on each leg boundary crossing
- **Subsystem:** tour-engine · **Category:** performance · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivityTour.kt:1127-1168`
- **Fix:** Avoid touching the overlay list for a highlight change: just mutate the two active/previous legs' outlinePaint color+width and invalidate. Z-order between adjacent tour legs rarely matters visually; if it does, only re-stack the prev+new active leg, not all legs. The remove/add of the whole list is the expensive part on a CopyOnWriteArrayList.

### 61. interpolateWalkRoute pre-allocates one GeoPoint per ~1.4m of the entire route up front, holding a large list for the whole walk
- **Subsystem:** tour-engine · **Category:** memory · **V1-retail-reachable**
- **Where:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt:2510-2536, called at 2170/2228/2236/2245`
- **Fix:** Interpolate lazily (advance along the polyline by distance each tick) instead of materializing the full per-metre list, or interpolate per-leg on demand. Reduces retained heap during walk-sim. Note: walk-sim is a debug/recon affordance, so retail-reachability is low.


---

# Appendix A — Refuted findings (22 false positives killed by adversarial verification)

_These were raised by a reviewer but a second independent agent disproved them against the actual code. Listed so they are not re-investigated._

- **[tilt-memory]** FAB magnify (scaleX up to 3.0) stacks multiplicatively on top of the tilt viewport extension
  - _Refuted:_ False positive: View scaleX/scaleY here has NO setLayerType(LAYER_TYPE_HARDWARE) (grep confirms none) so it allocates no backing buffer — it's a composite-time RenderNode transform, not a re-rasterized "9x pixel footprint layer". Tile/bitmap memory is driven by MapView layout size (lp.width/height in applyMapExtension), which the FAB scale never touches (scale isn't fed into applyMapExtension; mv.scaleX is read only for pass-2 icon sizing + debug logs). Magnifying actually shows FEWER ground tiles, not more, so it cannot compound the tilt extension's tile cost into an OOM. Both controls are retail-reachable, but the claimed multiplicative-memory OOM mechanism does not exist.

- **[markers-overlays]** narrationMarkerCache is never evicted — retains up to ~2039 BillboardMarker + SalemPoi + icon references for the app's lifetime
  - _Refuted:_ Cache IS unbounded-eviction-free (grep confirms only put/get), but it is keyed by POI id off a FIXED renderable pool (getRenderablePois) — so it's bounded at ~one small marker icon per renderable POI, a one-time fill to a hard ceiling, NOT "growing baseline" / a leak. This is the intentional S234 tradeoff (avoid 600+ BillboardMarker reallocs per filter toggle). Icons are small category dots, not hero/tilt bitmaps. Cache is an Activity field so it GCs with the Activity (no config-change leak). Retail-reachable but a modest bounded baseline, not the high-severity leak claimed.

- **[markers-overlays]** enterFilterAndMapMode zoom-fit loop forces up to 16 synchronous layout passes on the main thread
  - _Refuted:_ False positive on mechanism: View.setLayoutParams self-assign only calls requestLayout() (deferred, coalesced to one frame), NOT a synchronous measure/layout. Verified osmdroid 6.1.18 setZoomLevel uses requestLayout()+invalidate() at end and getProjection() is a lazy O(1) rebuild — so the loop is ~16x (cheap setZoom + Projection alloc + bb.contains scan), no overlay re-layout/redraw, sub-ms to low-ms total. Path is retail (Find > Filter and Map button) so v1Reachable=true, but no real ANR/stutter; analytical zoomToBoundingBox is a cleanliness nit, not a stability bug.

- **[poi-loading-cache]** Circle-icon WebP decoded at full resolution with no inSampleSize before scaling
  - _Refuted:_ Code/dimensions confirmed (128x128 WebP, decodeStream then createScaledBitmap, original recycled at line 238). But the decode is fully memoized via circleIconCache keyed by path|sizePx, so it runs at most ~a few dozen times over app lifetime (≈20 categories × a few sizes), not per-frame/per-marker. Transient alloc is only 64KB ARGB and freed immediately; both call sites are cached one layer up too. Real anti-pattern label but no genuine memory/perf/ANR impact even on min-spec Lenovo — below the claimed 'low'.

- **[narration-tts]** NarrationManager (process @Singleton) TTS engine is shut down on TourViewModel.onCleared(), so every Activity recreation (rotation/config change) destroys and re-binds the TTS engine
  - _Refuted:_ Code structure confirmed (TourViewModel @HiltViewModel init→initialize() L93, onCleared→shutdown() L777; NarrationManager @Singleton shutdown nulls tts L195-203), BUT premise is false: host SalemMainActivity (AndroidManifest L61-66) declares configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode|smallestScreenSize|density", so rotation/config-changes do NOT recreate the Activity and do NOT call onCleared(). The portrait lock at L77 is the unrelated ReconCaptureActivity. Even absent that, a by viewModels() VM is retained across config-change recreation; onCleared() fires only when the Activity is genuinely finishing, where releasing the process-singleton TTS binding is the correct/desired behavior. No mid-utterance teardown on rotation occurs. Minor code smell (singleton torn down by activity-scoped owner) with zero runtime stability impact.

- **[narration-tts]** Narration runtime state (highlight Polygon refs, queue, currentNarration, lastUserLat) is top-level file-scope mutable state shared across all Activity instances
  - _Refuted:_ Leak chain is false: osmdroid Polygon/PolyOverlayWithIW/Overlay hold NO MapView field (MapView is only a method param; InfoWindow never created since title is intentionally unset), so an orphaned Polygon ref cannot retain the old MapView/Activity/bitmaps. Also the manifest's broad configChanges + onConfigurationChanged (no re-inflate) means rotation/density/uiMode reuse the same mapView — no orphaning; process death resets all file-scope globals. The only Activity-capturing global, the narrationHighlightAnim coroutine, is cancelled via jobCoordinator.cancelAll() in onDestroy + lifecycleScope. narrationQueue/currentNarration hold a plain SalemPoi data class (no View/Context/Bitmap) + primitives; their file-scope nature is intentional, documented singleton-lift design. At worst two tiny geometry objects briefly survive a rare non-config recreation, overwritten on the next narration tick.

- **[narration-tts]** emitSyntheticComplete() allocates a fresh Handler(Looper.getMainLooper()) per dropped segment — one per geofence ENTRY while muted during walk-sim
  - _Refuted:_ Code quoted accurately: emitSyntheticComplete() does alloc a fresh Handler(mainLooper) per call from every muted-drop path, reachable in retail (muted/silent walk). But not a genuine perf issue: it fires once per geofence ENTRY (one per POI proximity crossing — user-paced, seconds-to-minutes apart), NOT per-frame/per-GPS-tick. "hotPath/every step" is a mischaracterization. A Handler wrapping the existing main Looper is a tiny short-lived object, negligible GC even on min-spec Lenovo; posts one Runnable then becomes garbage (no leak). Caching it is a harmless micro-cleanup with no measurable impact.

- **[narration-tts]** Broad silent/near-silent exception handling around narration load and per-ENTRY visit recording can hide DB/load failures on the active path
  - _Refuted:_ Refuted. ENTRY events are heavily gated in NarrationGeofenceManager (isNarrated 1hr REPEAT_WINDOW + 2min cooldown set the instant ENTRY fires), so each POI launches at most ONE short-lived IO coroutine per hour doing a single PK-keyed UPSERT (recordHeard) — no coroutine storm even in dense clusters; not a hot path. The two catches are intentional, well-logged design (fire-and-forget visit write that must never block narration; startup load whose only realistic failure is a corrupt/missing bundled asset that breaks the whole app anyway). Reachable by retail users but neither claimed mechanism is a genuine perf/stability defect — pure style/error-surfacing nit.

- **[gps-location]** Walk-sim loop break + completion guards test the nullable walkSimJob field, which stop paths set to null — external stop runs the 'Walk complete' UI/state reset and can emit one extra setManualLocation
  - _Refuted:_ False positive: the "Walk complete" reset (line 2371) is plain post-loop code, NOT in a finally, so a CancellationException from a cancelled delay() propagates and SKIPS it — it never runs on external cancel, so no FAB flip / resume-state clobber occurs. setManualLocation is non-suspend and the loop body is straight-line to the next delay(), which throws on cancel before iterating again, so no stray emission. All retail stop paths (UI stopWalkSim, directions stopWalkSimExternalAndJoin which cancelAndJoins) run on the same Main dispatcher as the coroutine and only null the field while it's parked at delay(), where cancellation tears down cleanly. The field-nulling fire-and-forget stopWalkSimExternal() is dead code (zero callers). The walkSimJob?.isCancelled checks are redundant/defensive but harmless; structured cancellation handles teardown.

- **[gps-location]** lastFixAtMs is a plain (non-@Volatile) Long written on Main and read by the GPS-OBS heartbeat / narration reach-out coroutines
  - _Refuted:_ No present-day defect: writer (LiveData observer, line 3584) and all readers (heartbeat coroutine 3496, runSilenceFill 572) run on Main via lifecycleScope.launch (Main.immediate); only Dispatchers.IO launch at narration:319 never touches lastFixAtMs. Single-threaded today, so no visibility hazard. Finding itself concedes "currently single-threaded and safe" — purely speculative future-proofing, not a genuine bug.

- **[room-data]** TourStopDao.findByPoi filters on poi_id (2nd column of composite PK) with no usable index — full table scan
  - _Refuted:_ Index claim is technically correct (composite PK [tour_id,poi_id] can't serve a bare WHERE poi_id=? → scan), but TourStopDao.findByPoi has ZERO callers anywhere (repo wraps only findByTour/findTourPoisByTour/insertAll; the .findByPoi calls in code are on Figure/Fact/PrimarySource DAOs). It never executes at runtime, and tour_stops is a ~tens-of-rows join table on a suspend (off-main) path. No retail perf impact — dead-code hygiene note at most.

- **[room-data]** events_calendar table has zero indices; date/type/month/stale filters are all full scans
  - _Refuted:_ Claims are factually accurate (no secondary indices; DAO filters all scan), and Events is retail-reachable (SalemMainActivity onEventsRequested -> showEventsDialog, ungated). But shipped events_calendar has exactly 20 rows and queries run on a background coroutine (viewModelScope + suspend Room DAO, off-main-thread) on tab-open, not per-frame. Scanning 20 rows is sub-ms with zero measurable perf/stability cost even on min-spec Lenovo. A consistency nit, not a genuine perf issue; finding self-rates low and admits the row count keeps it cheap.

- **[room-data]** Empty/swallow catches on userdata write+read paths log only .message, masking schema/IO failures silently in release
  - _Refuted:_ Central premise is false: DebugLogger is NOT R8-stripped in release. proguard-rules.pro has no -assumenosideeffects for DebugLogger or android.util.Log; DebugLogger.initFileSink() is called UNCONDITIONALLY (not BuildConfig.DEBUG-gated) in WickedSalemApp.onCreate:34, so all DebugLogger.e/.w calls write to the durable daily file sink at <externalFilesDir>/logs/ in release. The verbose-debug rule only strips DebugLogger.d via call-site BuildConfig.DEBUG guards; these catches use .e/.w which always fire. The exact durable-field-log telemetry the finding asks to build already exists and already receives these failures. Read paths returning emptyList() is a correct degraded sentinel; fire-and-forget writes correctly not crashing. Path is retail-reachable but there is no defect.

- **[lifecycle-coroutines-leaks]** DebugEndpoints reconstructed on every onResume in retail (new CoroutineScope + Gson + Activity ref) even though DebugHttpServer is a stub; prior instance's walkScope is not cancelled on reassign — one rooted scope leaks per foreground cycle
  - _Refuted:_ Leak claim refuted: onResume (SalemMainActivity.kt:1061) does reallocate DebugEndpoints every foreground cycle without shutting down the prior instance, but the orphaned instance is NOT rooted. Its walkScope=CoroutineScope(Dispatchers.Default+SupervisorJob()) is idle in retail (the only launch is handleWalkTour via the HTTP handle() path, and DebugHttpServer.start/stop are no-ops, so no coroutine ever runs); an idle SupervisorJob isn't held by the dispatcher or any registry, and the only escaping reference is the static DebugHttpServer.endpoints field which is overwritten on reassign. So the old DebugEndpoints (and the Activity it holds) is GC-eligible immediately — no accumulating leak, no Activity rooting across resume cycles. What's genuinely real is only a minor, GC-able per-onResume allocation (Gson pretty-printer + scope + Activity ref) for a stubbed server — retail-reachable waste, fix by gating behind BuildConfig.DEBUG — hence low, not a medium leak.

- **[lifecycle-coroutines-leaks]** loadNarrationPointMarkers relaunches narrationMarkerJob without cancel-before-relaunch; mutex-serialized callers can queue multiple coroutines, and the job is not cancelled in onDestroy
  - _Refuted:_ Both halves refuted. onDestroy "leak": narrationMarkerJob is launched via lifecycleScope (SalemMainActivity is an AppCompatActivity), which auto-cancels on destroy — JobCoordinator's own doc confirms lifecycleScope is the safety net, so no leak. "Redundant full rebuilds": S234 rewrote the body to diff against narrationMarkerCache+narrationMarkersOnOverlay and only icon-generate toAdd; on a burst of same-filter calls the diffs are empty no-ops and renderablePointsCache short-circuits the Room query, so repeats are cheap, not full reallocating rebuilds. The mutex serializes bodies (no concurrency hazard), calls are user/event-driven not per-frame, and the no-cancel choice is deliberate (cancel-before-relaunch raced cache writes / double-attached markers at cold start per the S234 comment). Path is retail-reachable but the issue is not genuine/actionable; proposed JobCoordinator fix risks reintroducing the fixed race.

- **[memory-bitmaps]** HeroAssetLoader downsample guard never fires for the S299/S301 1152x512 heroes — they decode at full 2.25MB into an 8MB LRU (~3 fit) and thrash
  - _Refuted:_ Confirmed 124 heroes are 1152x512/2.25MB and calcSampleSize returns 1, BUT the cited mechanism is dead code: loadTriptychFull/loadTriptychThumb (the only users of calcSampleSize, decodeAsset, and the 8MB LRU) have ZERO callers — the LRU is never populated, so no eviction/thrash occurs. The live retail path is PoiHeroResolver.applyTo, which does a plain BitmapFactory.decodeStream with no Options/inSampleSize and setImageBitmap into an ImageView (no cache). So the filed LRU-thrash issue cannot happen. A milder, separate truth (full-res 2.25MB transient decode on applyTo) exists but is not what was filed.

- **[memory-bitmaps]** MarkerIconHelper.loadCircleIcon decodes full-res 1152x512/512x512 then createScaledBitmap — full-res transient allocation before scaling
  - _Refuted:_ Evidence is wrong: loadCircleIcon loads ONLY from hardcoded poi-circle-icons/ (line 227), where 149/150 icons are 128x128 (64KB transient, NOT a LOS alloc) and exactly one is 512x512 (worship, ~1MB). The cited 1152x512/2.25MB dimensions belong to heroes/ and legacy poi-icons/, which this function never touches. Transient is also correctly recycled (line 238) and result cached per (category,sizePx), so first-touch-only. The 64KB transient at min-spec is negligible; inSampleSize fix not worthwhile.

- **[tour-engine]** Tour route polyline assembly (geometry decode + per-leg O(n) clip loops + setPoints) runs on the main thread at tour start / every Active|Detour transition
  - _Refuted:_ Threading is accurately described (decode/closestIdx/setPoints run on Main after Room suspend resume, no withContext), but the impact is refuted by real bundled data: largest authored tour (tour_LongWalk) has only 405 total vertices across 26 legs (max 45/leg), not "multi-hundred-vertex" — work is ~405 tiny string parses + ~2,340 float compares, sub-ms to low-single-digit-ms even on Lenovo, nowhere near ANR-class. collectLatest cancels rapid re-draws. Polyline setPoints/overlays.add must be on Main anyway. Not a "high" ANR; at most a low tidiness note. (Cited file path/line numbers also drifted: drawTourRoute is at ui/SalemMainActivityTour.kt:937.)

- **[tour-engine]** TourEngine ambient proximity scan iterates the full POI cache on every Idle-mode GPS fix
  - _Refuted:_ Loop is accurately described (full ~2039-row salem_pois scan, haversine each, hinted-skip inside loop, no spatial prune) BUT it is dead code: guarded by `ambientModeEnabled &&` which defaults false (TourEngine.kt:90) and is never assigned true anywhere in the repo (only writer is an uncalled TourViewModel setter). checkAmbientProximity never runs; real Explore ambient narration goes through SalemMainActivityNarration.runSilenceFill(), a separate path. Not retail-reachable, no runtime impact.

- **[tour-engine]** Tour-legs DB read swallows exceptions and silently degrades, masking corrupt/missing geometry during a tour
  - _Refuted:_ Code matches the finding, but the swallow-and-degrade is deliberate, documented fallback (catch→emptyList→runtime getMultiStopRoute at 389-394; size<2 guards at 416/449/488) — no crash/leak/ANR/OOM, just graceful degradation. The described failure (silent corrupt-geometry masking) requires a mangled tour_legs bundle, which a retail user cannot produce: the bundled DB is read-only, operator-baked, signed and hash-verified at launch (OMEN-025). It is a build-time/QA diagnostics observation, not a retail-reachable perf/stability defect.

- **[startup-coldlaunch]** Tilt 3D button wired during onCreate and tilt is retail-on by default, feeding the known z20/12600px overlay-blowout straight from cold launch
  - _Refuted:_ Misread: setupTilt3dButton() (SalemMainActivity.kt:1902-1929) only attaches a click listener and calls setTiltDegrees(savedTilt) where savedTilt defaults to 0f on retail; setTiltDegrees(0f) early-returns (line 133) and the 12600px MapView blowout is gated behind tiltDeg>0f in TiltContainer.applyMapExtension() (line 224). Nothing fires at cold launch — the blowout requires a user tap on the 3D FAB, so the "fed straight from cold launch / no warm-up budget" causal claim is false. Tilt feature itself is retail-reachable (TILT_3D_ENABLED=true), but this startup line is a no-op at tilt=0; the eager-overlay cost it bundles in is a separate, pre-existing concern not caused by wiring the button.

- **[error-hygiene]** MarkerIconHelper.loadCircleIcon swallows all decode/scale errors with a bare empty catch on the active marker-render path
  - _Refuted:_ Severity-driving claim is a misread: catch(Exception) at line 241 does NOT catch OutOfMemoryError (an Error, not Exception), so the "transient OOM permanently latched as null" mechanism cannot occur — OOM propagates past it. The catch only fires on IOException (asset absent) or NPE (undecodable asset), both permanent conditions where caching null is correct/intended ("cache the miss", guarded at line 231). On null return dot() falls back to dotFallback() colored dot — no crash, no leak, no main-thread/alloc cost. Path is retail-reachable and hot (SalemMainActivity:2904-2906/4227/4280/4431), but the only real residual is a trivial logging-inconsistency vs sibling get() at 437 (DebugLogger.e), an observability nit, not a perf/stability issue — and assets are bundle-verified so it realistically never fires in retail.


---

# Appendix B — Completeness critic: gaps the 12-subsystem set under-covered

Confirmed. `NarrationManager` is an `@Singleton` holding a live `TextToSpeech` engine, and nothing in the activity lifecycle calls its `shutdown()`. As an app-scoped singleton it survives activity recreation (rotation/config change), so the TTS binding persists for the process — not a per-activity leak, but the `shutdown()` method exists (line 197) and is dead code, meaning the engine is never released even on a clean exit. `orientationTracker.stop()` is also absent from onDestroy. I have everything I need.

COMPLETENESS-CRITIC FINDINGS — perf/stability problem classes the confirmed 12-subsystem set under-covered or missed. Severity-ranked, capped at 8. All paths verified by reading the files.

---

**1. [high|stability|V1, hotPath=false] onTrimMemory is a near-no-op under exactly the LMK scenario the anchor issue describes.** `SalemMainActivity.onTrimMemory` (SalemMainActivity.kt:1199-1206) only calls `PolygonLibrary.unload()`, and the comment at 1196-1197 explicitly admits "full release would also need to drop wickedAnimationOverlay." It frees NOTHING from the real bitmap-pressure sources: MarkerIconHelper's 4096-entry drawable cache, GhostResolver/PoiHeroResolver LRUs, osmdroid's in-memory MapTileCache, or the ambient overlays. So when the tilt OOM fires `TRIM_MEMORY_RUNNING_CRITICAL`, the app sheds a ~600KB JSON index and gets killed instead of dumping the tens of MB of bitmaps. NEXT LOOK: wire MarkerIconHelper cache clear + GhostResolver/PoiHeroResolver `evictAll()` + `mapView.tileProvider.clearTileCache()` into onTrimMemory at TRIM_MEMORY_RUNNING_CRITICAL.

**2. [high|memory|V1, hotPath=false] No osmdroid tile-cache sizing or DPI config — the missing root cause behind the tilt OOM.** `setupMap` (SalemMainActivity.kt:1408-1438) never calls `setTilesScaledToDpi(...)`, never sizes the in-memory `MapTileCache` (`Configuration.getInstance().cacheMapTileCount` / `tileFileSystemCacheMaxBytes`), and sets `maxZoomLevel = MAP_MAX_OVERZOOM` (z21, comment line 1427-1430). The confirmed set noted "no zoom cap while tilted" but not that the in-memory tile cache itself is left at osmdroid's screen-derived default, which is computed for a normal-height MapView, not the ~12600px tilt extent. NEXT LOOK: `tools/tile-bake` z-cap is 19; cap `maxZoomLevel` at 19-20 and explicitly size `cacheMapTileCount`/`tileFileSystemCacheMaxBytes` for the tilt-extended viewport. Verify `setTilesScaledToDpi` default isn't silently 2x-decoding tiles on the Pixel 8's high DPI.

**3. [high|leak|V1, hotPath=false] TTS engine `shutdown()` is dead code — NarrationManager (@Singleton) never releases its TextToSpeech.** `NarrationManager.shutdown()` exists (NarrationManager.kt:197 `tts?.shutdown()`) but grep shows it is called nowhere; `SalemMainActivity.onDestroy` (1145-1185) tears down audio callback, HTTP server, wakelock, encounter tracker, GPS recorder, heartbeat, jobCoordinator — but NOT the TTS engine. The engine holds a live service binding for the whole process. SplashVoice has the same shape (its `shutdown()` is documented as called by SplashActivity — verify that actually fires; if not, two TTS engines stay bound). `orientationTracker.stop()` is also absent from onDestroy (sensor unregister relies on onPause only). NEXT LOOK: confirm SplashVoice.shutdown is invoked; add narrationManager.shutdown + orientationTracker.stop to the teardown path or wire NarrationManager to a lifecycle.

**4. [high|stability|V1, hotPath=true] TTS UtteranceProgressListener.onDone/onError call playNext()→speak() on the TTS binder thread, where speak() compiles ~50 Regex (normalizeForTts) and mutates a StateFlow.** `onDone` (NarrationManager.kt:138) and `onError` (162) call `playNext()` (569) → `speak()` (580), which runs `normalizeForTts` (the confirmed ~50-regex compile, line 620) and `_state.value = NarrationState.Speaking(...)` (582) plus `NarrationHistory.add` (586). UtteranceProgressListener callbacks are delivered on the TTS service's binder thread, not Main — so the regex-compile cost lands off-main (good) but the StateFlow emission and any Main-only collectors downstream are crossing threads on every chunk boundary. The confirmed set flagged the regex cost "on the main thread for every segment" via speak() line 620 — but speak() is ALSO re-entered per inter-segment from a binder thread, so the threading assumption in that finding is half-wrong and there's a thread-safety question on currentSegment/queue mutation. NEXT LOOK: audit currentSegment/queue access — they're touched from both the GPS/main path and the binder callback with no synchronization.

**5. [high|performance|V1, hotPath=true] Heading-up map rotation calls setMapOrientation on the tilt-extended MapView every bearing change while walking — a tilt re-record amplifier the confirmed set missed.** `applyHeadingUpRotation` (SalemMainActivityTour.kt:1531) `binding.mapView.setMapOrientation(targetRotation)` fires whenever the smoothed GPS bearing crosses `HEADING_HYSTERESIS_DEG` (line 1510). Under tilt this forces a full re-record/re-raster of the ~1798×12600 display list, on top of the confirmed narration-pulse/GPS-marker/trigger-ring invalidates. The fast-path guard at 1495 only suppresses *identical* bearings; a normal walk produces a continuous stream of sub-degree-to-few-degree changes that pass the hysteresis gate. NEXT LOOK: this is the heading-up + tilt interaction; the confirmed invalidate findings are all north-up assumptions. Consider rotation rate-limiting under tilt.

**6. [medium|performance|V1, hotPath=true] ProximityDock re-inflates up to 10 `dock_icon_item` views from scratch (removeAllViews + LayoutInflater.inflate in a loop) on every dock refresh / GPS fix.** `update()` (ProximityDock.kt:102-131) and `updateFromQueue()` (144-177) call `iconRow.removeAllViews()` then `inflater.inflate(R.layout.dock_icon_item, ...)` per item every call, with `findViewById` ×4 per item and a `GradientDrawable` stroke mutation. Driven per GPS fix (confirmed dock callers). The confirmed set caught the full-res icon *decode* here but not the view-inflation + GC churn layered on top. NEXT LOOK: convert to a RecyclerView with a pool, or diff-and-reuse the existing child views instead of removeAllViews each refresh.

**7. [medium|performance|V1, hotPath=true] DeviceOrientationTracker.onUpdate() callback runs on the MAIN thread at the sensor's native ~100Hz (SENSOR_DELAY_UI is a hint the HAL ignores).** Registered with a main-looper Handler (DeviceOrientationTracker.kt:232-237); `onSensorChanged` (253) does matrix math + `getOrientation` + the static-mode delta calc every sample and calls `onUpdate()` (394) on Main. The class doc claims ~60ms but the S125 comment (line 152-153) confirms the Lenovo delivers 100Hz. While S195 removed the sensor from driving rotation, onUpdate still fires the consumer 100×/sec on Main during heading-up. NEXT LOOK: confirm what `onUpdate` is bound to today and whether it early-returns cheaply at 100Hz; if not it competes with the tilt draw for main-thread time exactly during walking.

**8. [medium|performance|V1, hotPath=false] CollectionSheet RecyclerViews use notifyDataSetChanged() with no DiffUtil/ListAdapter and no setHasFixedSize/pool sizing.** CollectionSheet.kt:208 and :261 both call `adapter.notifyDataSetChanged()`, forcing full rebinds of every visible holder (each of which the confirmed set already showed decodes a 384×384 ghost). No DiffUtil, no `setHasFixedSize`, no `setItemViewCacheSize`. Combined with the confirmed per-bind decode this means every sheet refresh re-decodes the whole visible set, not just changed rows. NEXT LOOK: migrate to ListAdapter+DiffUtil so the confirmed per-bind decode only fires for actually-changed entries.

---

**Whole-app patterns checked and found CLEAN (so the team can stop looking):** SharedPreferences are not read on hot paths (only setup/restore); BroadcastReceivers — none registered; the walk-sim wakelock IS released (onDestroy:1162 + `invokeOnCompletion` at SalemMainActivityDirections.kt:423 / SalemMainActivity.kt:2387) with a 6h auto-cap; MotionTracker/DeviceOrientationTracker sensor register/unregister is idempotent and log-throttled; the Dijkstra router and bundle load run under `withContext(Dispatchers.IO)` (WalkingDirections.kt:64,111) — not a main-thread risk; no StrictMode config exists (worth adding in debug to surface the confirmed main-thread disk/decode findings automatically). No MediaPlayer/SoundPool — audio is pure TTS. The `@Singleton` Room `salem_content.db` uses createFromAsset + fallbackToDestructiveMigration (intentional, read-only); UserDataDatabase correctly has real migrations (no destructive fallback).
