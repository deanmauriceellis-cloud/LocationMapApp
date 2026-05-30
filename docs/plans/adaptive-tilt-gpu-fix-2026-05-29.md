# Adaptive Tilt-Tile GPU Fix — Implementation Plan (S308, 2026-05-29)

> ⚠️ **STATUS: IMPLEMENTED, SHIPPED (`0b81e7e`), THEN REVERTED (S308 close, 2026-05-30).** On-device testing proved the software-layer approach does **not** fix the BlueStacks corruption: it is **renderer-agnostic texture-memory exhaustion on BlueStacks' virtualized GPU** under the tilt load (OpenGL fails full-screen, DirectX fails partially), and a software layer can't escape it because the HW-accelerated window still GL-composites the CPU bitmap. The premise below (corruption is the perspective composite specifically, fixable by routing tiles through CPU) was WRONG. Kept only as an investigation record. The corruption is NOT app-fixable while keeping HW-accel + the no-blank-wedge rule; real hardware is unaffected. See `docs/session-logs/session-308-2026-05-29.md`.

> Source: design workflow `wf_8de33348-09e` (15 agents, adversarially verified). Problem: on weak/virtualized GPUs (BlueStacks GLES 3.0 canary) the osmdroid basemap **tile** layer corrupts under 3D tilt (magenta #FF00FF / cyan / bare green plane flicker) because tiles are drawn through a `setPolyToPoly` perspective matrix on a hardware-accelerated canvas; Canvas-drawn vector overlays stay perfect. Real devices (Pixel 8, Lenovo) render fine. Constraints: keep tilt on, keep wedge coverage (EXTRA_TOP_FRACTION=5.0 untouched), default-safe on unknown GPUs, no real-device regression, no ANR.

## Adversarial verdicts (5 strategies)

- **Startup GL-renderer probe → software layer ONLY on flagged GPUs** → **VIABLE-WITH-CHANGES**
    - No HARD violation of the literal constraint text — but two constraints are placed at INDIRECT risk by the remedy.
    - #1 (no blank wedge): at-risk indirectly — on maxTex<=4096 WEAK GPUs the 6x-extended software-layer bitmap exceeds GL_MAX_TEXTURE_SIZE and can fail to composite, blanking the tilt surface. Not a literal shrink of EXTRA_TOP_FRACTION (which is untouched), but a new path to a blank wedge on the canary class.
    - #3 (default-safe on unknown GPU): routing satisfies the letter, but the safe path's correctness is unproven — the final perspective composite is still a non-affine GL texture-sample (the documented-failing op per TiltContainer.kt:60 comment), so corruption is possible even on the safe path on a never-seen weak GPU.
    - #5 (ANR): mitigations cited address tile fetch, not the fixed 50-106 MB layer-bitmap re-raster per invalidate under walk-sim heading-up; ANR/slideshow risk remains on the target weak class.
- **Always-software while TILTED (hardware when flat) — no probe, simplest correctness** → **REJECTED**
    - Constraint #3 (default-safe on unknown GPU): FALSE. On weak/virtualized GPUs the ~8000-14400px software layer exceeds GL_MAX_TEXTURE_SIZE (commonly 4096) and Android drops the layer, rendering through the corrupt hardware path — reproducing the magenta/cyan corruption on exactly the GPUs the constraint protects.
    - Constraint #4 (no regression on real hardware): VIOLATED. Pixel 8 / Lenovo forced to CPU-rasterize a 13-33 MP perspective surface every tilted frame plus carry a 50-133 MB layer bitmap — regression in both frame time and memory; conceded by the strategy.
    - Constraint #5 (ANR): VIOLATED/HIGH RISK. Per-GPS-fix re-raster of a multi-megapixel surface plus a 50-133 MB layer bitmap on an app already at PSS 1.9-2.9 GB reopens the deep-tilt ANR/LMK the team is actively fighting.
- **Render-and-probe: PixelCopy the tilted buffer, count magenta/cyan sentinels, demote on corruption** → **VIABLE-WITH-CHANGES**
    - Constraint #3 (default-safe on unknown GPU): VIOLATED as named — 'trust hardware by default' shows corruption on unknown weak/virtualized GPUs for the frames before the probe completes, and intermittent ~1-in-8 corruption lets a single-frame probe false-negative and leave the device permanently corrupt. The strategy's own text concedes this and says it must invert to software-until-proven-clean, which makes it the hybrid, not the pure render-probe.
    - Constraint #4 (no real-hardware regression): AT RISK — the #2D1B4E 'tilt-plane' sentinel is also the layout background (activity_main.xml:55) and the legitimate pre-tile-load wedge fill, so the green/purple check can false-positive and permanently demote a healthy Pixel 8/Lenovo to software. Also, a software-until-clean default forces a first-tilt CPU-rasterized frame on real hardware.
    - Constraint #5 (ANR): AT RISK — the fallback (LAYER_TYPE_SOFTWARE) target is undefined; on the whole TiltContainer it CPU-rasterizes the perspective-warped up-to-24x-container surface every tilted frame on weak CPUs; on the MapView child alone it may not fix the corruption. The cited mitigations (z19 clamp, onTrimMemory) reduce memory, not per-frame raster cost.
- **Hybrid capability-tier: emulator heuristics + GL denylist + GL limits, default-safe on UNKNOWN, optional render-probe confirmation** → **VIABLE-WITH-CHANGES**
- **Software-layer fallback + walk-sim invalidate throttle & downscale (ANR-hardening layer)** → **VIABLE-WITH-CHANGES**
    - #5 (ANR) only PARTIALLY satisfied: lever (1) bounds raster frequency but lever (2) — the only lever that bounds per-raster CPU cost on the weakest GPUs — is specified against a non-existent 'software-layer render density / scale hint' API, so per-frame cost on a 24x-extended surface is left unbounded on exactly the hardware that needs it most.
    - #1 (no blank wedges) AT RISK via lever (2): coverage-safety is asserted from geometry-untouched, but the downscale mechanism is unspecified; depending on implementation (clip/viewport-shrink vs true uniform scale of the full extended surface, or setTilesScaleFactor colliding with the z19 clamp) it could blank or illegibly crush the wedge. Not a proven violation, but not proven safe either — and the burden is on the strategy to prove safe.
    - Dependency gap: references GpuCapability.needsSoftwareTilt() and a software-layer base that do not exist in the tree (no GpuCapability.kt, no setLayerType/LAYER_TYPE_SOFTWARE). Standalone the strategy is inert; it cannot be evaluated or shipped without its base, which is out of scope here.

---

Confirmed: zero existing GL probe code, `largeHeap=true`, no `hardwareAccelerated` override (defaults true), TiltContainer bg `#2D1B4E`. I have full grounding. Here is the final plan.

---

# Final Recommended Implementation Plan — Adaptive, Coverage-Preserving Tilt-Tile Fix

## 1. Recommended approach

**Ship the Hybrid capability-tier detector (strategy #4) feeding a software-layer fallback that targets the `TiltContainer` itself (not the MapView child), hard-gated by a `GL_MAX_TEXTURE_SIZE` ceiling check, and paired with the mandatory invalidate-throttle from strategy #5 on the software path only.** This synthesis is the only design that survives all five adversarial verdicts. Pure strategy #2 (always-software) is **rejected** — it regresses Pixel 8/Lenovo into a 50–133 MB per-frame CPU raster and, worse, silently drops the oversized layer back to the corrupt path on the exact weak GPUs it targets. Pure #3 (render-probe) is not default-safe on the first frame and its `#2D1B4E` sentinel collides with the real TiltContainer background (`activity_main.xml:55`) and pre-tile-load fill, false-demoting healthy devices. Strategy #1's routing is sound but its verdict reviewer flagged two unproven premises that #4 already resolves: the **texture-size ceiling** (the 6×-extended software bitmap is ~8000–14400 px tall and exceeds the 4096 maxTex of the canary class → silent layer-drop → corruption returns) and the **fallback target** (concat happens on the *parent* canvas at `dispatchDraw:363`, so a child-only software layer is still HW-perspective-warped — the exact failure the code's own KDoc at `:59-61` documents). My plan promotes both from "risks to verify" to **gating preconditions baked into the code**: the software layer goes on the `TiltContainer` (the view that owns the concat), and it engages **only when the probed `GL_MAX_TEXTURE_SIZE` actually exceeds the extended-surface long edge** — otherwise it falls through to the per-frame flat-tile last-resort (`dispatchDraw` without the concat), which keeps full wedge *coverage* with flat tiles rather than ever showing magenta or a blank wedge. Detection routes; it never touches `EXTRA_TOP_FRACTION=5.0`, `EXTRA_SIDE_CAP=1.5`, `TOPY_FACTOR=0.6`, or `applyMapExtension` — so the no-blank-wedge hard rule (#1) is structurally untouchable and tilt is never disabled (#2).

## 2. Detection mechanism

**New singleton `core/src/main/java/com/example/locationmapapp/util/GpuCapability.kt`**, mirroring the `SuperAdminMode` idiom exactly (object, `@Volatile` fields, set-once-then-read, no Hilt).

**What/where/thread:**
- Invoked **once** from `WickedSalemApp.onCreate()` (after `DebugLogger.initFileSink` at `WickedSalemApp.kt:34`), **fire-and-forget on a dedicated `HandlerThread`** — never the main thread (EGL context creation can take tens of ms on a slow virtualized driver).
- **Layer 1 (no GL, microseconds):** `Build.FINGERPRINT/MODEL/MANUFACTURER/BRAND/HARDWARE/PRODUCT` against emulator literals (`generic`, `sdk_gphone`, `vbox`, `genymotion`, `goldfish`, `ranchu`, `bluestacks`); `ActivityManager.deviceConfigurationInfo.reqGlEsVersion >> 16 < 3`; `ActivityManager.isLowRamDevice()`. Any QEMU/emulator hit → tentative `WEAK` (still confirmed by Layer 2 if a context can be made).
- **Layer 2 (authoritative EGL14 1×1 pbuffer probe):** `eglGetDisplay(EGL_DEFAULT_DISPLAY)` → `eglInitialize` → `eglChooseConfig(EGL_PBUFFER_BIT, EGL_OPENGL_ES2_BIT, RGB888)` → `eglCreateContext(CLIENT_VERSION 2)` → `eglCreatePbufferSurface(1×1)` → `eglMakeCurrent` → read `GLES20.glGetString(GL_RENDERER/GL_VENDOR/GL_VERSION)` + `glGetIntegerv(GL_MAX_TEXTURE_SIZE, …)` → full teardown (`eglMakeCurrent(NONE)`, destroy surface/context, `eglTerminate`). **All EGL/GL calls on the same HandlerThread.** Wrapped in `try/catch` + a **2 s watchdog** — any throw, hang, or failed EGL setup → verdict `WEAK`.
- **Classification (default-safe):**
  - `GOOD` **only if** renderer-family allowlist (`adreno`/`mali`/`immortalis`/`powervr`/`xclipse`) **AND** `GL_MAX_TEXTURE_SIZE >= 8192` **AND** not Layer-1-flagged. (maxTex floor is **8192**, not 16384 — the verdict reviewer noted many real mid-tier Adreno/Mali report 8192 and shouldn't be needlessly demoted.)
  - `WEAK` if denylist (`swiftshader`/`llvmpipe`/`softpipe`/`angle`/`android emulator`/`goldfish`/`bluestacks`/`virgl`/`virtio`/`vmware`) **OR** EGL-fail **OR** `GL_MAX_TEXTURE_SIZE <= 4096`.
  - everything else → `UNKNOWN`.
- **`needsSoftwareTilt(): Boolean = verdict != GOOD`** (WEAK and UNKNOWN both route to the safe path).
- **`maxTextureSize: Int`** is stored on the singleton (defaults to a conservative `2048` before the probe returns or on EGL-fail — so the ceiling check below errs toward *not* using an oversized software layer).
- **Fail-safe-to-weak window:** before the probe returns, `verdict = UNKNOWN` (the field's initial value), so `needsSoftwareTilt()` returns `true`. A cold-start saved-tilt restore (`setupTilt3dButton` → `setTiltDegrees(savedTilt)` at `SalemMainActivity.kt:1956`) that races ahead of the probe gets the **safe** path, never corruption. **Caching:** verdict + maxTex persisted to `SharedPreferences` keyed `(GL_RENDERER-hash + Build.FINGERPRINT + BuildConfig.VERSION_CODE)`; subsequent launches read it synchronously in `WickedSalemApp.onCreate` before any tilt is possible, eliminating the race entirely after first run.

## 3. The rendering fallback

**Target: the `TiltContainer` view itself**, not the MapView child. The perspective matrix is concat'd on the TiltContainer's canvas (`dispatchDraw:363`); only a software layer on the *view that owns the concat* forces the whole tilted composite through Skia's CPU rasterizer (no GL texture sampling → no magenta/cyan). A child-only layer is re-warped by the parent HW canvas and fixes nothing (the documented `:59-61` failure).

**Two-tier, ceiling-gated, engaged from the single `setTiltDegrees` funnel inside the existing `wasActive != nowActive` block (`TiltContainer.kt:147-163`), right after `applyTiltZoomClamp(mv, nowActive)` at `:161`:**

```kotlin
// Inside the if (wasActive != nowActive) block, after applyTiltZoomClamp(mv, nowActive)
if (BuildDefaults.TILT_SOFTWARE_LAYER_ON_UNKNOWN_GPU && GpuCapability.needsSoftwareTilt()) {
    val longEdge = maxOf(width + 2 * extraSidePx, height + extraTopPx)
    if (nowActive && longEdge <= GpuCapability.maxTextureSize) {
        // Tier A: software-rasterize the whole tilted composite (correct on weak GPU)
        if (layerType != LAYER_TYPE_SOFTWARE) setLayerType(LAYER_TYPE_SOFTWARE, null)
        softwareTiltActive = true
    } else {
        // Tier B last-resort: surface too big for this GPU's max texture, OR tilt off.
        // Restore hardware; dispatchDraw's flat-fallback branch (see §4) keeps full
        // wedge COVERAGE with flat tiles — never magenta, never blank.
        if (layerType != LAYER_TYPE_HARDWARE) setLayerType(LAYER_TYPE_HARDWARE, null)
        softwareTiltActive = false
        flatFallbackActive = (nowActive)   // read by dispatchDraw
    }
} else if (!nowActive && softwareTiltActive) {
    // GOOD devices never entered here, so this only restores devices we flipped.
    setLayerType(LAYER_TYPE_HARDWARE, null)
    softwareTiltActive = false
    flatFallbackActive = false
}
```

**Coverage/geometry preservation:** This block reads `extraTopPx`/`extraSidePx` (already computed by `applyMapExtension`) only to *size-check* against `maxTextureSize`. It never reads or mutates `EXTRA_TOP_FRACTION`, `EXTRA_SIDE_CAP`, `TOPY_FACTOR`, the layoutParams, `clipChildren`, or the `setPolyToPoly` matrix. Tier A rasterizes the **identical** extended surface through the **identical** `tiltMatrix` — every wedge fills exactly as today, just in Skia instead of GL. Tier B (when even Skia's backing bitmap would exceed maxTex) keeps full-screen flat tiles + upright pass-2 vectors via the `dispatchDraw` flat branch — coverage preserved, look degraded but never corrupt.

**Real-device fast path (constraint #4):** `GOOD` → `needsSoftwareTilt() == false` → the whole block is skipped → `layerType` stays at the default HARDWARE → `dispatchDraw`'s existing `canvas.concat(tiltMatrix)` GL path runs **byte-for-byte unchanged**. The `else if (!nowActive && softwareTiltActive)` restore is guarded on `softwareTiltActive`, so GOOD devices (never flipped) eat **no** RenderNode rebuild on tilt-off.

## 4. Exact code changes

**(a) NEW `core/src/main/java/com/example/locationmapapp/util/GpuCapability.kt`** — object singleton per §2: `enum Verdict { GOOD, WEAK, UNKNOWN }`; `@Volatile var verdict = UNKNOWN`; `@Volatile var maxTextureSize = 2048`; `fun probe(ctx: Context)` (HandlerThread + 2s watchdog + EGL14 pbuffer + classify + SharedPreferences cache, keyed on renderer-hash+fingerprint+VERSION_CODE); `fun needsSoftwareTilt(): Boolean = available && verdict != Verdict.GOOD` (where `available` is set from the new BuildDefaults const so release can const-fold the whole thing); `setAvailable(Boolean)` mirroring SuperAdminMode. Every layer's raw signal + final verdict logged via `if (BuildConfig.DEBUG) DebugLogger.d("GpuCapability", …)` per the verbose-debug rule.

**(b) `WickedSalemApp.kt:34`** — after `DebugLogger.initFileSink(applicationContext)`, add:
```kotlin
GpuCapability.setAvailable(BuildDefaults.TILT_SOFTWARE_LAYER_ON_UNKNOWN_GPU)
GpuCapability.probe(applicationContext)   // fire-and-forget on its own HandlerThread
```
(BuildDefaults lives in the app-salem package — import it, as `OfflineTileManager` etc. are already imported here.)

**(c) `BuildDefaults.kt:58`** — add companion consts beside `TILT_3D_ENABLED`:
```kotlin
// S308 — default-safe-on-weak/unknown-GPU tilt rendering (BlueStacks tile-corruption fix).
const val TILT_SOFTWARE_LAYER_ON_UNKNOWN_GPU: Boolean = true   // ships in release; R8-foldable
const val TILT_SOFTWARE_MAX_FPS: Int = 12                       // §5 invalidate throttle (software path only)
```
`true` (not `RECON_DEFAULTS`) because the safe behavior must ship in retail — that is the entire point of constraint #3.

**(d) `TiltContainer.kt`** — three edits:
- **Fields (near `:122`):** add `private var softwareTiltActive = false`, `private var flatFallbackActive = false`, plus a throttle handle `private var softwareInvalidatePending = false`.
- **`setTiltDegrees`, inside `if (wasActive != nowActive)` after `applyTiltZoomClamp(mv, nowActive)` at `:161`:** the gated layer-flip block from §3, with `if (BuildConfig.DEBUG) DebugLogger.d(TAG, "tilt-layer: verdict=${GpuCapability.verdict} sw=$softwareTiltActive flat=$flatFallbackActive maxTex=${GpuCapability.maxTextureSize} longEdge=…")`.
- **`onMapStateChanged` (`:131`) — §5 throttle:**
```kotlin
fun onMapStateChanged() {
    if (tiltDeg <= 0f) return
    if (softwareTiltActive) {                 // weak-GPU path only
        if (softwareInvalidatePending) return
        softwareInvalidatePending = true
        postDelayed({ softwareInvalidatePending = false; invalidate() },
                    (1000L / BuildDefaults.TILT_SOFTWARE_MAX_FPS))
    } else {
        invalidate()                          // hardware path unchanged — immediate
    }
}
```
- **`dispatchDraw` (`:348`) — Tier B flat-fallback branch:** at the top of the `tiltDeg > 0f` path, before `canvas.concat(tiltMatrix)` at `:362-363`:
```kotlin
if (flatFallbackActive) {
    super.dispatchDraw(canvas)               // flat tiles, no perspective concat → no GL warp
    if (mv != null) { drawBillboardedSprites(...); drawBillboardedMarkers(canvas, mv) }
    return
}
```
This keeps the markers upright and the tiles full-coverage-flat on the rare GPU whose maxTex can't hold even the Skia layer. (Guard `flatFallbackActive` is only ever set true on a `needsSoftwareTilt()` device, so GOOD/real hardware never takes this branch.)

**(e) `SalemMainActivity.kt:1555`** — optional belt-and-braces: where `binding.tiltContainer.spriteOverlayRef = sprites` is set, no change needed; the verdict is read live from the singleton in `setTiltDegrees`, and the cached read in `WickedSalemApp.onCreate` guarantees it's ready before `setupTilt3dButton`'s restore at `:1956`. **No mandatory wiring change here** — the singleton + pre-probe `UNKNOWN`-safe default already covers the cold-start race (§2).

## 5. Perf + ANR analysis

- **Pixel 8 / Lenovo (verdict `GOOD`): zero change.** `needsSoftwareTilt()==false` → the §3 block is skipped entirely, `layerType` stays HARDWARE, the existing `canvas.concat(tiltMatrix)` GL composite runs unchanged, `onMapStateChanged` keeps its immediate `invalidate()`. The probe is a one-time 1×1 pbuffer on a background HandlerThread (no RTX-contention concern per `feedback_gpu_caution`), cached forever after first run. Constraint #4 fully satisfied. The tilt-off restore is guarded on `softwareTiltActive`, so GOOD devices never eat a needless RenderNode rebuild.
- **Weak/unknown GPU, Tier A (software layer, maxTex sufficient):** the tilted composite CPU-rasterizes once per invalidate. **Static tilted view costs nothing after the first raster** (Android caches the software-layer bitmap; it re-rasters only on `invalidate`). The ANR exposure is *walk-sim heading-up*, where `mapListener` fires per GPS fix. **Mitigation is the §5 throttle:** `onMapStateChanged` coalesces software-path invalidations to `TILT_SOFTWARE_MAX_FPS=12`, converting a per-fix raster storm into a bounded cadence. The existing `z19 applyTiltZoomClamp` (`:329`) already removed the z20 throwaway bitmaps; the 350 ms FAB debounce (`SalemMainActivity:1965`) caps re-layout storms; `onTrimMemory` CRITICAL eviction (`:1218`) backstops the larger layer bitmap. The verdict reviewer's correct caveat — *throttle bounds frequency, not per-raster cost* — is handled by Tier B: the per-raster cost is bounded by the maxTex ceiling check (a surface too big to raster cheaply is exactly a surface that exceeds maxTex → routes to Tier B's flat path).
- **Weak GPU, Tier B (flat fallback, surface exceeds maxTex):** no perspective concat, no software layer — full-screen flat z19 tiles + upright vectors. Cheapest of all paths; coverage preserved, 3D look sacrificed only on the most constrained GPUs. This is the constraint-#3 "correctness > speed on unknown GPU" floor.
- **Memory:** the software-layer bitmap is the new cost on Tier A; `largeHeap=true` is already set (`AndroidManifest:44`), and the maxTex ceiling caps its dimensions. The throttle plus `onTrimMemory` eviction keep it from becoming a new OOM source.

## 6. How to validate (BlueStacks 6015 + Pixel 8 + Lenovo)

**Build:** `./gradlew :app-salem:assembleDebug` (debug carries the same `TILT_SOFTWARE_LAYER_ON_UNKNOWN_GPU=true`). Install per the asset-pack workflow (`bundletool build-apks --connected-device` / `install-apks`) so `salem_tiles.sqlite` is present — a standalone `adb install` ships no tiles and would itself look "blank."

**BlueStacks 6015 (over the ssh/HD-Adb channel) — the corruption canary:**
1. `adb -s <bluestacks-serial> logcat -s GpuCapability:* TiltContainer:*` — confirm `verdict=WEAK` (or UNKNOWN) and the raw `GL_RENDERER` string (expect swiftshader/virgl/llvmpipe/angle) + `maxTextureSize`.
2. Cold-start → tap the 3D FAB through `30→36→42→48°`. **Before:** ~1-in-8 frames correct, magenta `#FF00FF`/cyan/green flicker. **After:** every frame shows correct tiles in the full wedge — `tilt-layer: sw=true` in logcat (Tier A) confirms the software layer engaged; if `maxTextureSize<=4096` forces `flat=true` (Tier B), confirm full-screen **flat** tiles with **no magenta and no blank wedge**.
3. Start walk-sim heading-up at 48°; watch `adb shell dumpsys gfxinfo <pkg>` — confirm no ANR, frame cadence bounded by the 12 fps throttle, no `Bitmap too large to be uploaded into a texture` in logcat (the silent-layer-drop signal — its absence proves Tier A/B routing held).

**Pixel 8 (`41231FDJH0018J`) + Lenovo (`HNY0CY0W`) — no-regression proof:**
1. Logcat → confirm `verdict=GOOD`, `needsSoftwareTilt()==false`.
2. Tap through the tilt ladder → wedge renders correctly at full HW speed exactly as today; `tilt-layer` log shows `sw=false flat=false`.
3. `dumpsys gfxinfo` at 48° walk-sim heading-up **before vs after** the patch → Janky% and Missed-Vsync **unchanged** (the GOOD path is byte-identical). On the **Lenovo (V1 min-spec floor)** this is the gating before/after — any delta means the GOOD branch was accidentally touched.

## 7. Open risks + on-device confirmations

- **[BLOCKER, confirm first] Does software-layer-on-TiltContainer actually kill the magenta on BlueStacks?** The whole synthesis rests on Skia CPU rasterization bypassing the GL texture-sample. Theory is strong (the KDoc at `:59-61` is direct precedent that software canvas behaves differently), but it is **unverified on a virtualized GPU**. Confirm step 6.2 *before* trusting the fix — if Tier A still flickers, the maxTex ceiling routes those GPUs to Tier B (flat, definitely correct), so the app is never shipped corrupt either way; but the 3D look would be lost on more devices than expected.
- **maxTex routing accuracy:** a real mid-tier phone reporting maxTex=8192 lands `GOOD` and keeps the fast path; one reporting 4096 lands Tier B (flat tilt). Confirm on a representative cheap MediaTek device if one is reachable that the flat path is acceptable, or widen the allowlist after field telemetry of real `GL_RENDERER` strings (the DEBUG log captures them).
- **`GL_RENDERER` string drift** across BlueStacks/ARC/driver releases — denylist needs occasional curation, but UNKNOWN→safe means a miss errs toward correctness (slow/flat), the right failure mode for a paid app.
- **Throttle lifecycle foot-gun:** the `softwareInvalidatePending` flag must clear on tilt-off and never post on the hardware path. It is guarded on `softwareTiltActive`, which is only true on flipped weak devices; confirm on Pixel/Lenovo that tilt-off and rapid FAB cycling never starve a needed redraw (step 6, Pixel).
- **R8 strip proof:** run `apkanalyzer dex packages` / manifest check on the **release** AAB to confirm `TILT_SOFTWARE_LAYER_ON_UNKNOWN_GPU=true` keeps the path live (it must ship in retail, unlike the RECON-gated consts) and that nothing flipped app-wide `hardwareAccelerated=false` — the swap is per-view only.

**Files:** new `/home/witchdoctor/Development/LocationMapApp_v1.5/core/src/main/java/com/example/locationmapapp/util/GpuCapability.kt`; edits to `/home/witchdoctor/Development/LocationMapApp_v1.5/app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TiltContainer.kt` (`:131`, `:147-163`, `:348`, fields near `:122`), `/home/witchdoctor/Development/LocationMapApp_v1.5/app-salem/src/main/java/com/example/wickedsalemwitchcitytour/WickedSalemApp.kt:34`, and `/home/witchdoctor/Development/LocationMapApp_v1.5/app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/BuildDefaults.kt:58`.