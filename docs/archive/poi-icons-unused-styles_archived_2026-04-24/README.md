# POI icon style variants — archived 2026-04-24 (S167)

## What's in here

1,238 PNG files that used to live in `app-salem/src/main/assets/poi-icons/<category>/`. All `<subtype>_<style>.png` files where **`<style> ≠ witchcraft`** were moved here on 2026-04-24.

22 category folders preserved, mirroring the original `poi-icons/` structure.

## Why they were pulled from the APK

**Size.** `poi-icons/` was 544 MB — the single largest asset tree in the APK. 512×512 8-bit RGBA PNG, one per `<subtype>_<style>` pair, at ~380 KB each × 1,415 files.

The 8 style variants — `cute`, `demon`, `devil`, `evil`, `psycho`, `undead`, `witchcraft`, `zombie` — were originally generated to support per-POI tier-based icon assignment (see `project_poi_monetization.md`). A merchant picks which style their POI displays based on their payment tier; free-tier POIs get a deterministic pick via `abs(poi.id.hashCode()) % icons.size`.

V1 pivoted to a flat $19.99 offline release with no per-POI monetization (`project_business_model.md`, S138). The 8-style-picker feature is not shipping in V1, so the extra 7 variants per subtype were 470+ MB of cold inventory.

## What was kept

Only the `witchcraft` variant survived in `app-salem/src/main/assets/poi-icons/`:
- 177 `<subtype>_witchcraft.png` files
- Re-encoded as WebP q=80 at 512×512 (same dimension — they're used as hero images at 55% of screen height in `PoiDetailSheet.bindStrippedHero`)
- Total: 74 MB PNG → ~2.5 MB WebP

Choice of `witchcraft` as the single surviving style: most thematically on-brand for a Salem tour app, distinct without being gruesome, PG-13 safe per `feedback_pg13_content_rule.md`.

## Restoration

If V2 brings tier-based icon assignment back:

1. Move the style variants back into `app-salem/src/main/assets/poi-icons/<category>/`
2. Convert them to WebP at 512×512 (match the witchcraft survivors) — the filter in `ProximityDock.loadPoiIcon` and `PoiHeroResolver.loadCategoryPool` is `.webp` as of S167
3. Optionally prune the set down to only the styles a tier actually uses

The files here are still 512×512 PNG originals — no lossy encoding yet, so quality is whatever the SDXL pipeline generated.

## Related

- Canonical asset inventory: `app-salem/src/main/assets/ASSETS-MANIFEST.md`
- Session log: `docs/session-logs/session-167-2026-04-24.md`
- Monetization context: memory `project_poi_monetization.md`
- Business model pivot: memory `project_business_model.md`
