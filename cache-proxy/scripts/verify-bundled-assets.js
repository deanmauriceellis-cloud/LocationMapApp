#!/usr/bin/env node
/*
 * verify-bundled-assets.js — S153
 *
 * Fails (exits non-zero) if any asset required by the shipped APK is missing,
 * undersized, or schema-mismatched. Run this before every APK build.
 *
 * Companion doc: app-salem/src/main/assets/ASSETS-MANIFEST.md
 *
 * Recurring failure mode this catches: a cache-proxy bundle/publish script
 * rebuilds the source DB and copies it over the APK assets DB, silently
 * wiping a table that a different script only ever wrote to assets directly.
 * Caught in prod twice already (S149 newspapers, S153 npc_bios + articles).
 *
 * Usage:
 *   node cache-proxy/scripts/verify-bundled-assets.js
 *   echo $?   # 0 = pass, 1 = fail
 */

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

const ASSETS = path.resolve(__dirname, '../../app-salem/src/main/assets');

const ROOM_IDENTITY_HASH_V9 = '4ec9ae3528d8f55529cd6875c7b0adef';

// Required Room tables with minimum row counts. A table with fewer rows than
// listed is treated as a failure (empty/partially-populated = same crash).
const REQUIRED_ROOM_TABLES = [
  { table: 'salem_pois',                    minRows: 1800 },
  { table: 'salem_businesses',              minRows: 800  },
  { table: 'narration_points',              minRows: 800  },
  { table: 'tours',                         minRows: 5    },
  { table: 'tour_stops',                    minRows: 80   },
  { table: 'tour_pois',                     minRows: 40   },
  { table: 'events_calendar',               minRows: 20   },
  { table: 'historical_facts',              minRows: 500  },
  { table: 'historical_figures',            minRows: 49   },
  { table: 'primary_sources',               minRows: 200  },
  { table: 'timeline_events',               minRows: 40   },
  { table: 'salem_witch_trials_newspapers', minRows: 200  },
  { table: 'salem_witch_trials_npc_bios',   minRows: 49   },
  { table: 'salem_witch_trials_articles',   minRows: 16   },
  { table: 'room_master_table',             minRows: 1    },
];

// Required tour-route files — one per row in the `tours` table.
const REQUIRED_TOUR_ROUTES = [
  'tour_essentials.json',
  'tour_explorer.json',
  'tour_grand.json',
  'tour_salem_heritage_trail.json',
  'tour_witch_trials.json',
];

// Required top-level asset directories that must exist and be non-empty.
const REQUIRED_NONEMPTY_DIRS = [
  'poi-icons',
  'poi-circle-icons',
  'hero',
  'heroes',
  'portraits',
];

const failures = [];
const warnings = [];
const ok = [];

function fail(msg)  { failures.push(msg); }
function warn(msg)  { warnings.push(msg); }
function pass(msg)  { ok.push(msg); }

// ── salem_content.db ──────────────────────────────────────────────────────
const CONTENT_DB = path.join(ASSETS, 'salem_content.db');
if (!fs.existsSync(CONTENT_DB)) {
  fail(`missing file: ${CONTENT_DB}`);
} else {
  let db;
  try {
    db = new Database(CONTENT_DB, { readonly: true });
    for (const { table, minRows } of REQUIRED_ROOM_TABLES) {
      const tbl = db.prepare(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
      ).get(table);
      if (!tbl) {
        fail(`salem_content.db: table missing: ${table}`);
        continue;
      }
      const n = db.prepare(`SELECT COUNT(*) AS c FROM ${table}`).get().c;
      if (n < minRows) {
        fail(`salem_content.db: ${table} has ${n} rows, expected >= ${minRows}`);
      } else {
        pass(`salem_content.db: ${table} = ${n} rows (>= ${minRows})`);
      }
    }
    // Identity hash check
    const master = db.prepare('SELECT identity_hash FROM room_master_table WHERE id=42').get();
    if (!master) {
      fail('salem_content.db: room_master_table row id=42 missing');
    } else if (master.identity_hash !== ROOM_IDENTITY_HASH_V9) {
      fail(`salem_content.db: identity_hash is ${master.identity_hash}, expected ${ROOM_IDENTITY_HASH_V9} (v9)`);
    } else {
      pass(`salem_content.db: identity_hash = ${master.identity_hash} (v9)`);
    }
  } catch (e) {
    fail(`salem_content.db: open/query failed: ${e.message}`);
  } finally {
    if (db) db.close();
  }
}

// ── salem_tiles.sqlite ────────────────────────────────────────────────────
const TILES_DB = path.join(ASSETS, 'salem_tiles.sqlite');
if (!fs.existsSync(TILES_DB)) {
  fail(`missing file: ${TILES_DB}`);
} else {
  let db;
  try {
    db = new Database(TILES_DB, { readonly: true });
    const tbl = db.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='tiles'").get();
    if (!tbl) {
      fail('salem_tiles.sqlite: table `tiles` missing');
    } else {
      const n = db.prepare('SELECT COUNT(*) AS c FROM tiles').get().c;
      if (n < 5000) {
        fail(`salem_tiles.sqlite: tiles has ${n} rows, expected >= 5000`);
      } else {
        pass(`salem_tiles.sqlite: tiles = ${n} rows (>= 5000)`);
      }
    }
  } catch (e) {
    fail(`salem_tiles.sqlite: open/query failed: ${e.message}`);
  } finally {
    if (db) db.close();
  }
}

// ── tours/ ────────────────────────────────────────────────────────────────
const TOURS_DIR = path.join(ASSETS, 'tours');
if (!fs.existsSync(TOURS_DIR)) {
  fail(`missing dir: ${TOURS_DIR}`);
} else {
  for (const name of REQUIRED_TOUR_ROUTES) {
    const f = path.join(TOURS_DIR, name);
    if (!fs.existsSync(f)) {
      fail(`tours/: missing ${name}`);
    } else {
      const size = fs.statSync(f).size;
      if (size < 100) {
        fail(`tours/${name}: suspiciously small (${size} bytes)`);
      } else {
        pass(`tours/${name}: ${size} bytes`);
      }
    }
  }
}

// ── portraits/ ────────────────────────────────────────────────────────────
const PORTRAITS = path.join(ASSETS, 'portraits');
if (!fs.existsSync(PORTRAITS)) {
  fail(`missing dir: ${PORTRAITS}`);
} else {
  const count = fs.readdirSync(PORTRAITS).filter((f) => !f.startsWith('.')).length;
  if (count < 49) {
    fail(`portraits/: ${count} files, expected >= 49 (one per historical_figures row)`);
  } else {
    pass(`portraits/: ${count} files (>= 49)`);
  }
}

// ── non-empty top-level dirs ──────────────────────────────────────────────
for (const name of REQUIRED_NONEMPTY_DIRS) {
  const d = path.join(ASSETS, name);
  if (!fs.existsSync(d)) {
    fail(`missing dir: ${d}`);
  } else {
    const entries = fs.readdirSync(d).filter((f) => !f.startsWith('.'));
    if (entries.length === 0) {
      fail(`${name}/: directory is empty`);
    } else {
      pass(`${name}/: ${entries.length} entries`);
    }
  }
}

// ── soft warnings (non-fatal but worth surfacing) ─────────────────────────
const WT_PORTRAITS = path.join(ASSETS, 'witch_trials/portraits');
if (fs.existsSync(WT_PORTRAITS)) {
  const count = fs.readdirSync(WT_PORTRAITS).filter((f) => !f.startsWith('.')).length;
  if (count === 0) {
    warn('witch_trials/portraits/: empty — npc_bio portrait_asset references will fall back to silhouette');
  }
}

// ── S154 commercial-hero leak check ──────────────────────────────────────
// Per-POI heroes for non-licensed commercial POIs must not ship. They carry
// legal risk (per-business AI art likely referenced real storefronts) and
// are bypassed at render time by the PoiDetailSheet stripped path.
// `prune-commercial-heroes.js` removes them from assets/{heroes,hero}/;
// this check fails the build if any leaked back in.
{
  // Mirror of AudioControl.groupForCategory BUSINESSES bucket (Kotlin source
  // of truth). Keep in sync with prune-commercial-heroes.js.
  const BUSINESS_CATEGORIES = new Set([
    'FOOD_DRINK', 'SHOPPING', 'LODGING', 'HEALTHCARE', 'ENTERTAINMENT',
    'AUTO_SERVICES', 'OFFICES', 'TOUR_COMPANIES', 'PSYCHIC', 'FINANCE',
    'FUEL_CHARGING', 'TRANSIT', 'PARKING', 'EMERGENCY', 'WITCH_SHOP',
  ]);

  let db;
  try {
    db = new Database(CONTENT_DB, { readonly: true });
    const rows = db.prepare(
      `SELECT id, category, image_asset, merchant_tier
         FROM salem_pois
        WHERE merchant_tier IS NULL OR merchant_tier = 0`
    ).all();

    const heroesDir = path.join(ASSETS, 'heroes');
    const heroDir = path.join(ASSETS, 'hero');
    let leaks = 0;
    for (const r of rows) {
      if (!BUSINESS_CATEGORIES.has(r.category)) continue;

      const triptych = path.join(heroesDir, `${r.id}.webp`);
      if (fs.existsSync(triptych)) {
        fail(`commercial-hero leak: heroes/${r.id}.webp (category=${r.category}, merchant_tier=${r.merchant_tier ?? 0})`);
        leaks++;
      }

      if (r.image_asset) {
        const base = r.image_asset.replace(/^hero\//, '');
        const celShaded = path.join(heroDir, base);
        if (fs.existsSync(celShaded)) {
          fail(`commercial-hero leak: hero/${base} (category=${r.category}, merchant_tier=${r.merchant_tier ?? 0})`);
          leaks++;
        }
      }
    }
    if (leaks === 0) {
      pass('commercial-hero prune: 0 leaks across all unlicensed commercial POIs');
    }
  } catch (e) {
    warn(`commercial-hero check skipped: ${e.message}`);
  } finally {
    if (db) db.close();
  }
}

// ── report ────────────────────────────────────────────────────────────────
console.log('\n=== Bundled Assets Verification ===');
for (const m of ok)       console.log(`  OK    ${m}`);
for (const m of warnings) console.log(`  WARN  ${m}`);
for (const m of failures) console.log(`  FAIL  ${m}`);
console.log(`\n${ok.length} OK, ${warnings.length} warning, ${failures.length} failure\n`);

if (failures.length > 0) {
  console.error('Asset verification FAILED. Do not ship this APK.');
  process.exit(1);
}
console.log('Asset verification PASSED.');
process.exit(0);
