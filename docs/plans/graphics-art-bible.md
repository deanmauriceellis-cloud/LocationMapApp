# LocationMapApp / Katrina's Mystic Visitors Guide — Graphics Art Bible

**Status:** v0.2 (S299, 2026-05-25). Core visual decisions LOCKED + sample-validated (S298). Hero scope tiered to the merchant model + verified against live PG (S299, §4.1) — bespoke run cut from ~2,000 to **124** (107 HIST_BLDG + 17 WORSHIP).
**Purpose:** Single source of truth for the full graphics redo. Every asset class regenerates against this so the app reads as one coherent set. Supersedes the incoherent mix audited in S298 (photoreal splash + painterly heroes + ink-comic ghosts + occult-sigil icons).

---

## 1. Core visual language — LOCKED

**INK-COMIC WOODCUT.** Adopted from the Katrina's Collection ghost portraits, now the house style for *everything*.

- Heavy, confident **black ink outlines**.
- **Flat cel shading** — limited tonal steps, minimal gradients, no photoreal rendering.
- **1692 broadsheet / woodcut** sensibility: hand-printed, slightly rough, period-graphic.
- 2D illustration. **Never** photorealistic, never 3D render, never soft-airbrush painterly.
- Anchor reference: `app-salem/src/main/assets/ghosts/ghost_abbott_street_cemetery_a.webp`.

## 2. Palette — LOCKED

**Muted period base + ONE signature accent.**

- **Base (dominant):** parchment / cream, weathered greys, earthy browns, charcoal/black ink. All desaturated, aged, period-true.
- **Accent (sparingly):** **GHOST-TEAL / CYAN** — cool spectral glow. The *only* saturated color. Reserved for the supernatural: spell glow, ghost light, magical highlights, key UI focal points. If it's not magic or a deliberate focal point, it stays muted.
- No purple-dominant, no full-Halloween orange/green, no bright/garish fields.

**Palette card v1 — LOCKED S298** (hex derived from approved S298 renders; card image `tools/art-bible-samples/out_palette/palette_card.png`). Usage % = max share of any single composition.

| Role | Swatch | Hex | Usage | Notes |
|---|---|---|---|---|
| Ground | Parchment | `#EFE6D3` | ~45% | paper ground / dominant field |
| Ground | Aged Cream | `#F6EFE0` | ~5% | lightest highlight on paper |
| Structure | Ink Black | `#0E100F` | ~20% | outlines, the carved line |
| Structure | Charcoal | `#2B2720` | ~8% | soft line / deep shadow fill |
| Earth | Weathered Grey | `#7C766A` | ~8% | stone, aged wood, neutral mid |
| Earth | Aged Wood Tan | `#B39A72` | ~6% | warm timber / soil mid |
| Earth | Umber | `#4A4136` | ~4% | deep brown shadow |
| **Accent** | **Spectral Teal** | `#3BBBB0` | **<5%** | ghost glow, eyes, key highlight (supernatural/focal ONLY) |
| **Accent** | **Deep Teal** | `#1E6E68` | **<5%** | teal shadow / muted teal field |

Discipline: the two teals **combined** stay under ~8% of any image and appear only on the supernatural or a deliberate focal point. Mortal/daytime subjects use the muted base only.

## 3. Katrina the cat — LOCKED

**Branding / splash only. The cat is CUT from POI graphics.**

- Katrina (grey-white tuxedo cat) appears ONLY on: splash sequence, welcome screens, and Katrina's Collection UI. Redrawn in the woodcut style.
- POI hero scenes, POI icons, ghost portraits, sprites: **no Katrina**. Heroes focus on the place or the historical figure itself.
- _Note: legacy hero triptychs tucked a cat into every panel — that conceit is retired._

---

## 4.1 Hero tiering & the merchant model — LOCKED S299

**A bespoke per-POI hero is paid content.** Non-paying commercial POIs do not get one — they share their category/subcategory emblem, which serves as *both* the map marker and the detail-sheet hero. This is already exactly how `PoiHeroResolver` tiers (T0/T1 bespoke → T2 category emblem); the merchant model just decides which POIs are *allowed* a T0/T1 file. A merchant upgrade later swaps a single Tier-2 emblem for a bespoke hero with zero architecture change.

**Bespoke per-POI woodcut heroes — 124, operator-scoped + verified S299** (list: `tools/art-bible-samples/bespoke-hero-pois.tsv`):

| Bucket | Source | Count |
|---|---|---|
| Historical Buildings | `category='HISTORICAL_BUILDINGS'` (already includes all 13 historic cemeteries — Charter St / Howard St / The Burying Point etc.) | 107 |
| Worship | `category='WORSHIP'` | 17 |
| **Total bespoke** | | **124** |

_Verified-out S299:_ the 4 cemetery-named `HISTORICAL_LANDMARKS` rows are Greenlawn Cemetery **facility buildings** (Garage, Greenhouse Garage, Office, Dickson Chapel), not burying grounds — dropped to shared emblem (operator). The 1 `ENTERTAINMENT` "cemetery" hit was **"Burying Point Productions"** (a business) — excluded. All real historic cemeteries are inside the 107.

**Shared category/subcategory emblems (~90) — marker == hero for everyone else (~1,911 POIs):**
- The other **460** `HISTORICAL_LANDMARKS` (zero subcategory — undifferentiated) → **one** shared "landmark" emblem for now. _Expand later when subcategorized; operator: "we can always expand later."_
- All commercial categories → ~80 emblems keyed on `category × subcategory` (commercial bucket has ~65 distinct subcategories; category-level fallback where subcategory is null).
- CIVIC / PARKS_REC / EDUCATION → category emblems.

**POI markers for the bespoke set — LOCKED S299:**
- **107 HIST_BLDG → reuse Katrina's 107 ghost badges** (already shipped) as their *map markers* — Collection↔map continuity. Bespoke woodcut hero in the detail sheet; badge marks the POI on the map.
- **17 WORSHIP → woodcut category circle marker** (the planned `poi-circle-icon` class, §4 — a church glyph). No Collection/badge expansion; worship POIs have no ghost badge.

**Net production run:** 124 bespoke heroes + ~90 shared emblems ≈ **214 images** (was ~2,000 in the S298 first cut — ~9× reduction). See updated §6.

## 4. Per-class application — PROPOSED (operator to confirm/adjust)

Dimensions & path contracts below are pulled from the S298 runtime-consumer map; the **style/composition** rows are proposals.

| Class | Asset path / naming | Dimensions / format | Composition proposal |
|---|---|---|---|
| **POI hero** | `heroes/<poi.id>.webp` | **master 2.25:1 (~1152×512) WebP** | **LOCKED S298:** single woodcut establishing scene of the place/figure, no cat, no triptych. **Subject horizontally centered in a central "safe band"** so it survives the landscape crop. Displayed as a 20%-of-screen-height banner; **`scaleType` must change `fitXY`→`centerCrop`** in `poi_detail_sheet.xml` (one-line) so portrait (2.25:1 box) shows ~full image and landscape (up to 11:1 box) shows the central band — no stretch. Verified fit on Pixel 8 + Lenovo boxes via `tools/art-bible-samples/render_hero_fit.py`. **Both hero paths unified to 20%** (S298): the standard path AND the stripped commercial path (`PoiDetailSheet.kt:252`, was 55%) → drop the 55% to 20%+centerCrop. |
| **Ghost portrait** | `ghosts/ghost_<poi_id>_<a\|b>.webp` | 384×384 WebP | Already on-style; re-gen to conform to locked palette (teal not green accent). A = neutral, B = fourth-wall smirk (2% swap). |
| **Ghost frame** | `frames/frame_<style>.webp` | RGBA cutout | Woodcut border motifs; muted with teal filigree. 8 styles. |
| **POI category icon** (hero Tier-2 + dock) | `poi-icons/<cat>/<type>.webp` | ~512 src → runtime | Woodcut object emblem per subtype, parchment ground, teal magic accent. Replaces photoreal crystals. |
| **POI circle marker** | `poi-circle-icons/<cat>/<type>.webp` | 512 src → ~48dp | Bold woodcut glyph in a circle, max legibility at 48dp. Replaces abstract sigil. |
| **Splash** | `res/drawable-nodpi/splash_katrina_NN.jpg` | full-screen | Katrina woodcut hero pose(s), teal magic glow, Salem night. Replaces photoreal cat. (12 variants today — decide count.) |
| **Welcome / find tiles** | `res/drawable-nodpi/welcome_*.jpg`, `find_tile_*.jpg` | tile | Woodcut category vignettes, consistent framing. 16 find tiles + 3 welcome cards. |
| **Witch-trials portraits** | `portraits/<figure>.jpg` | grid/detail | Woodcut historical-figure portraits (period-accurate, PG-13). Needs a NEW generator pipeline (none exists; npc-portraits.py abandoned). |
| **Sprites** (ambient) | `sprites/<char>/NN.webp` | 16-frame loops | Woodcut character animation frames (cat/owl/rat/witch/etc.). Needs a NEW pipeline (none exists). |
| **Historical-map thumbs** | `historical-thumbs/thumb_<year>.webp` | 140×96 | Leave as-is (map crops); out of redo scope unless re-styled. |
| **UI vector icons** | `res/drawable/ic_*.xml` | vector | Hand-authored vectors; likely keep. Audit for woodcut-weight consistency. |
| **Launcher icon** | `res/mipmap-*` | adaptive | Decide: re-do in woodcut for store identity. |

## 5. Generator backend & prompt scaffolding

- **Backend:** local Forge HTTP API `:7860`, model `DreamShaperXL_Turbo_v2_1` (DPM++ SDE, 8 Turbo steps, cfg ~2.5). All existing generators already target this. **GPU: RTX 3090 — must confirm free before any batch (AudioCraft/SD/SalemIntelligence contention).**
- **Shared positive base:** `ink-comic woodcut illustration, heavy black ink outlines, flat cel shading, 1692 Salem broadsheet woodcut style, muted period palette parchment cream grey earthy brown charcoal, 2D illustration`
- **Shared negative base:** `photorealistic, photograph, 3d render, soft airbrush, gradient, painterly, text, typography, letters, watermark, signature, modern objects, bright saturated colors, neon, purple dominant, gore, nude, deformed, low quality`
- **Per-class scaffold rules (LEARNED S298 — see `tools/art-bible-samples/`):**
  - **Heroes — HAUNTED MOOD, LOCKED S299 (variant "h3").** Operator feedback on the first daytime pilot: too lifelike, no Salem Witch-City flavor. Heroes are **moonlit-night, spooky-Halloween storybook** scenes: `bold stylized ink-comic woodcut, thick black ink outlines, flat graphic cel shading, high contrast, moonlit night, full moon, drifting fog, gnarled twisted bare trees in dark silhouette, circling crows and bats, long dramatic shadows`. **The building stays brightly moonlit + legible** (`light cream walls, warm glowing candlelit windows standing out against the dark sky`) — do NOT let it go to black silhouette. **Ghost-teal is a night-sky accent, not a wash** (`ghost-teal cyan moonlight accents in the sky, mostly muted`). Render at **cfg ~4.0, 9 steps**. NEG adds `daytime, bright sunny, cheerful, mundane, realistic, lifelike` + `dark building, black silhouette building, teal building, building in shadow`. DROP `hand-printed broadsheet texture` (it induced gibberish caption text). Keep `subject centered in clear central band` for the centerCrop. Reference generator: `tools/art-bible-samples/render_hero_haunted.py` (STYLE_H3). _Tradeoff accepted: teal exceeds the §2 "<8%" base rule in the night sky — heroes are the sanctioned exception; all other classes keep the restrained palette._
  - **Icons / markers:** **DO NOT** add `broadsheet texture` / `hand-printed` — it induces gibberish typography (food-icon sample grew fake text). Add `single centered, one emblem, simple bold, parchment ground`. Markers: keep detail minimal to read at 48–96 px (validated).
  - **Portraits / mortal figures:** NO teal (correct — sample read as period sepia). Reserve teal for ghosts/supernatural.
  - **Splash / Katrina:** teal accent shines (eyes/hat/wisps) — sample validated.
  - **Frames:** generate on a **chroma-green flat background with a green hollow center**, then chroma-key to alpha (use existing `cache-proxy/scripts/generate-frame-overlays.py` pipeline). Plain txt2img fills the center (gothic-frame sample put a raven where the portrait goes).
- **QC:** OCR text-rejection (already in hero-triptych full run) carried to every batch — doubly important now that the texture cue is dropped on non-hero classes.

### Per-class validation status (S298 samples)
All classes rendered and visually confirmed on-style: heroes ✅ (fit-tested both orientations), parks/food icons ✅ (drop texture cue), markers ✅ (legible at 96px), splash-Katrina ✅✅, accused portrait ✅✅, black-cat sprite ✅, gothic frame ✅ style / needs cutout pipeline. Samples in `tools/art-bible-samples/out*/`.

## 6. Coverage targets (the real gap — from S298 audit)

| Class | Today | Target |
|---|---|---|
| POI heroes (bespoke) | 280 / 2039 working (13%) | **124** — 107 HIST_BLDG + 17 WORSHIP (§4.1) |
| Category/subcategory emblems (marker == hero) | photoreal, partial coverage | **~90** — cover all ~1,911 non-bespoke POIs (§4.1) |
| Ghosts | 107 (HIST_BLDG only) | decide: expand beyond HIST_BLDG? |
| POI icons / circle | full category coverage | re-style all |
| Splash / welcome / find | exists, photoreal/mixed | re-style all |
| Portraits | 49, no pipeline | new pipeline + re-style |
| Sprites | 112, no pipeline | new pipeline + re-style |

## 7. Retire (the "nonsense" — from S298 audit)

- Legacy `assets/hero/` UUID dir (386 files) + `tools/hero-image-gen/` generator + **803 dangling `image_asset` refs** in PG.
- `salem_pois.custom_icon_asset` column (0 rows, never used).
- Orphan files: 295 `hero/` + 35 `heroes/` with no live POI.
- Abandoned `cache-proxy/scripts/generate-npc-portraits.py`.
- `tools/poi-icons/` 1.9 GB source library (8 horror variants/subtype) — only 1 ever shipped; re-evaluate before regen.

## 8. Open decisions (need operator)

1. ~~Hero format~~ — LOCKED S298: 2.25:1 master, safe-band centered, centerCrop, 20% top banner, both paths unified to 20%.
2. ~~Exact palette hex card~~ — LOCKED S298 (see §2 palette card v1).
3. Expand ghosts beyond HIST_BLDG?
4. Re-do launcher icon + UI vectors, or leave?
5. Splash variant count.

---

_v0.1 drafted S298. Next: GPU-gated sample renders to validate Section 1–3 before locking per-class specs._
