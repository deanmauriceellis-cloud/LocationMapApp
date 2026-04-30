#!/usr/bin/env node
/*
 * prune-commercial-heroes.js — S154
 *
 * Removes per-POI hero image files for non-licensed commercial POIs from the
 * bundled assets directory. The stripped PoiDetailSheet renders category-level
 * poi-icons/ art for these POIs, so the per-business WebPs in heroes/ and
 * hero/ are unused at runtime AND carry legal risk (per-business AI art likely
 * referenced real storefronts / merchant photography).
 *
 * Scope:
 *   - salem_pois.category IN (BUSINESSES group; see AudioControl.groupForCategory)
 *   - AND salem_pois.merchant_tier = 0 (unlicensed)
 *
 * Files removed (if present on disk):
 *   - app-salem/src/main/assets/heroes/{poi.id}.webp       (S147 triptych)
 *   - app-salem/src/main/assets/hero/{basename(image_asset)}  (S119 cel-shaded)
 *
 * Historic / civic / parks / worship / public-entity POIs keep their per-POI
 * heroes — their buildings are public landmarks, not commercial merchandising.
 *
 * Usage:
 *   node cache-proxy/scripts/prune-commercial-heroes.js --dry-run
 *   node cache-proxy/scripts/prune-commercial-heroes.js
 *
 * Exit 0 on success (including when dry-run). Exit 1 on DB / IO errors.
 *
 * Restore path: `git checkout app-salem/src/main/assets/heroes/ app-salem/src/main/assets/hero/`
 */

const path = require('path');
const fs = require('fs');
const { Pool } = require('pg');

const ASSETS = path.resolve(__dirname, '../../app-salem/src/main/assets');
const HEROES_DIR = path.join(ASSETS, 'heroes');
const HERO_DIR = path.join(ASSETS, 'hero');

// Mirror of AudioControl.groupForCategory's BUSINESSES bucket (Kotlin source
// of truth: app-salem/src/main/java/.../audio/AudioControl.kt). WITCH_SHOP
// was moved to BUSINESSES in S154.
const BUSINESS_CATEGORIES = [
  'FOOD_DRINK', 'SHOPPING', 'LODGING', 'HEALTHCARE', 'ENTERTAINMENT',
  'AUTO_SERVICES', 'OFFICES', 'TOUR_COMPANIES', 'PSYCHIC', 'FINANCE',
  'FUEL_CHARGING', 'TRANSIT', 'PARKING', 'EMERGENCY', 'WITCH_SHOP',
  'SERVICES', // S200 — operator+lawyer-confirmed commercial
];

const DRY_RUN = process.argv.includes('--dry-run');

async function main() {
  const dbUrl = process.env.DATABASE_URL;
  if (!dbUrl) {
    console.error('DATABASE_URL not set — cannot query salem_pois.');
    process.exit(1);
  }
  const pool = new Pool({ connectionString: dbUrl });

  const { rows } = await pool.query(
    `SELECT id, category, image_asset, merchant_tier
       FROM salem_pois
      WHERE category = ANY($1::text[])
        AND (merchant_tier IS NULL OR merchant_tier = 0)`,
    [BUSINESS_CATEGORIES]
  );
  await pool.end();

  console.log(`Scope: ${rows.length} unlicensed commercial POIs across ${BUSINESS_CATEGORIES.length} categories.`);

  let heroesHits = 0, heroesBytes = 0;
  let heroHits = 0, heroBytes = 0;
  let misses = 0;

  for (const r of rows) {
    // Priority 0: heroes/{poi.id}.webp
    const triptych = path.join(HEROES_DIR, `${r.id}.webp`);
    if (fs.existsSync(triptych)) {
      const sz = fs.statSync(triptych).size;
      heroesHits++;
      heroesBytes += sz;
      if (!DRY_RUN) fs.unlinkSync(triptych);
    }

    // Priority 1: hero/{basename(image_asset)} — image_asset stored as full
    // "hero/<uuid>.webp" path in the DB, so strip the leading 'hero/' if
    // present and join.
    if (r.image_asset) {
      const base = r.image_asset.replace(/^hero\//, '');
      const celShaded = path.join(HERO_DIR, base);
      if (fs.existsSync(celShaded)) {
        const sz = fs.statSync(celShaded).size;
        heroHits++;
        heroBytes += sz;
        if (!DRY_RUN) fs.unlinkSync(celShaded);
      } else {
        misses++;
      }
    }
  }

  const mb = (n) => (n / 1024 / 1024).toFixed(1);
  const action = DRY_RUN ? 'Would remove' : 'Removed';
  console.log(`${action}: ${heroesHits} files from heroes/ (${mb(heroesBytes)} MB)`);
  console.log(`${action}: ${heroHits} files from hero/ (${mb(heroBytes)} MB)`);
  console.log(`Total:   ${heroesHits + heroHits} files, ${mb(heroesBytes + heroBytes)} MB`);
  if (misses > 0) {
    console.log(`(${misses} commercial POIs with image_asset set had no matching file on disk — already pruned or never generated)`);
  }
  if (DRY_RUN) console.log('\n(dry run — no files deleted; re-run without --dry-run to apply)');
}

main().catch(err => {
  console.error(err.stack || err.message || err);
  process.exit(1);
});
