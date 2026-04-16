#!/usr/bin/env node
/*
 * import-bcs-pois.js — Phase 9U Step 9U.10 (Session 118)
 *
 * Imports SalemIntelligence BCS (Business Current State) POIs into the unified
 * salem_pois table. Three phases in one transaction:
 *
 *   A. MATCH — Find existing salem_pois that correspond to BCS entities
 *      (by slugified name match OR proximity-based name similarity within 50m)
 *   B. ENRICH — UPDATE matched rows with intel_entity_id + BCS enrichment fields
 *   C. INSERT — Create new rows for unmatched BCS entities
 *
 * BCS category → LMA category mapping follows S116 decisions:
 *   - 19 BCS categories → 22 LMA categories (no new top-level categories)
 *   - service_professional (452) decomposed via keyword heuristics
 *   - Tourist-relevant categories get default_visible=true
 *   - Utility categories get default_visible=false
 *   - Entities without lat/lng are skipped
 *
 * Usage:
 *   node scripts/import-bcs-pois.js                           # live run
 *   node scripts/import-bcs-pois.js --dry-run                 # log only
 *   node scripts/import-bcs-pois.js --snapshot path/to/file   # use specific snapshot
 *
 * Requires DATABASE_URL in environment or .env.
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

const DRY_RUN = process.argv.includes('--dry-run');
const snapshotIdx = process.argv.indexOf('--snapshot');
const SNAPSHOT_PATH = snapshotIdx !== -1
  ? process.argv[snapshotIdx + 1]
  : path.resolve(__dirname, '../../tools/salem-data/bcs-export-2026-04-16.json');

if (!process.env.DATABASE_URL) {
  try { require('dotenv').config({ path: path.resolve(__dirname, '../.env') }); } catch (_) {}
}
if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL environment variable is required');
  process.exit(1);
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// ── BCS category → LMA category mapping (S134/S135 — 20 categories) ─────────
//
// S134: TOURISM_HISTORY split into HISTORICAL_BUILDINGS + TOUR_COMPANIES.
//       HAUNTED_ATTRACTION → ENTERTAINMENT. GHOST_TOUR → TOUR_COMPANIES.
//       HISTORIC_HOUSE merged into HISTORICAL_BUILDINGS.
// S135: ATTRACTION tier removed from narration system.

// Tourist-relevant categories → default_visible = true
const TOURIST_VISIBLE = new Set([
  'FOOD_DRINK', 'WITCH_SHOP', 'HISTORICAL_BUILDINGS', 'ENTERTAINMENT',
  'LODGING', 'SHOPPING', 'TOUR_COMPANIES', 'PSYCHIC',
]);

const BCS_CATEGORY_MAP = {
  restaurant:         { category: 'FOOD_DRINK',           subcategory: null },
  cafe:               { category: 'FOOD_DRINK',           subcategory: 'FOOD_DRINK__cafes' },
  bar:                { category: 'FOOD_DRINK',           subcategory: 'FOOD_DRINK__bars' },
  shop_occult:        { category: 'WITCH_SHOP',           subcategory: null },
  shop_retail:        { category: 'SHOPPING',              subcategory: null },
  museum:             { category: 'HISTORICAL_BUILDINGS',  subcategory: 'HISTORICAL_BUILDINGS__museums' },
  attraction:         { category: 'ENTERTAINMENT',         subcategory: null },
  tour_operator:      { category: 'TOUR_COMPANIES',        subcategory: null },
  hotel_lodging:      { category: 'LODGING',               subcategory: null },
  gallery_art:        { category: 'ENTERTAINMENT',         subcategory: 'ENTERTAINMENT__galleries' },
  performance_venue:  { category: 'ENTERTAINMENT',         subcategory: null },
  shop_bookstore:     { category: 'SHOPPING',              subcategory: 'SHOPPING__bookstores' },
  shop_antiques:      { category: 'SHOPPING',              subcategory: 'SHOPPING__antiques' },
  spa_beauty:         { category: 'SHOPPING',              subcategory: 'SHOPPING__beauty_spa' },
  fitness:            { category: 'ENTERTAINMENT',         subcategory: 'ENTERTAINMENT__fitness' },
  service_health:     { category: 'HEALTHCARE',            subcategory: null },
  education:          { category: 'EDUCATION',             subcategory: null },
  religious:          { category: 'WORSHIP',               subcategory: null },
  other:              { category: 'CIVIC',                 subcategory: null },
};

// ── service_professional keyword decomposition (S116) ───────────────────────
// Order matters: first match wins.
const SP_KEYWORDS = [
  { keywords: ['auto','car repair','car wash','tire','mechanic','oil change','body shop','collision','tow','smog','transmission'],
    category: 'AUTO_SERVICES', subcategory: null },
  { keywords: ['bank','credit union','atm'],
    category: 'FINANCE', subcategory: null },
  { keywords: ['law','attorney','legal'],
    category: 'OFFICES', subcategory: null },
  { keywords: ['insurance'],
    category: 'OFFICES', subcategory: null },
  { keywords: ['real estate','realty','property'],
    category: 'OFFICES', subcategory: null },
  { keywords: ['accounting','tax','cpa','bookkeep','payroll'],
    category: 'OFFICES', subcategory: null },
  { keywords: ['financial','invest','wealth','mortgage','loan'],
    category: 'FINANCE', subcategory: null },
  { keywords: ['plumb','electric','hvac','heating','cooling','roofing','construction','contractor','landscap','painting','flooring','remodel'],
    category: 'CIVIC', subcategory: null },
  { keywords: ['clean','janitor','laundry','dry clean','maid'],
    category: 'SHOPPING', subcategory: 'SHOPPING__laundromats' },
  { keywords: ['print','sign','copy','graphic','market','advertis','consult','media','web design'],
    category: 'OFFICES', subcategory: null },
  { keywords: ['pet','vet','animal','groom','kennel','dog','cat'],
    category: 'SHOPPING', subcategory: 'SHOPPING__pet_stores' },
  { keywords: ['storage','moving','pack','ship'],
    category: 'SHOPPING', subcategory: 'SHOPPING__storage_rentals' },
];

function classifyServiceProfessional(name, description) {
  const text = `${name} ${description}`.toLowerCase();
  for (const rule of SP_KEYWORDS) {
    if (rule.keywords.some(kw => text.includes(kw))) {
      return { category: rule.category, subcategory: rule.subcategory };
    }
  }
  // Fallback: generic office/services
  return { category: 'OFFICES', subcategory: null };
}

function mapBcsCategory(bcsCategory, name, description) {
  if (!bcsCategory || bcsCategory === 'None') {
    return { category: 'CIVIC', subcategory: null };
  }
  if (bcsCategory === 'service_professional') {
    return classifyServiceProfessional(name || '', description || '');
  }
  return BCS_CATEGORY_MAP[bcsCategory] || { category: 'CIVIC', subcategory: null };
}

// ── ID generation (slugified name, matching existing pattern) ───────────────

function slugify(name) {
  return name
    .toLowerCase()
    .replace(/['']/g, '')           // remove apostrophes
    .replace(/&/g, 'and')           // & → and
    .replace(/[^a-z0-9]+/g, '_')   // non-alphanum → underscore
    .replace(/^_|_$/g, '')          // trim leading/trailing
    .replace(/_+/g, '_');           // collapse multiple
}

function makeUniqueId(name, existingIds) {
  let base = slugify(name);
  if (!base) base = 'unnamed_poi';
  if (!existingIds.has(base)) {
    existingIds.add(base);
    return base;
  }
  // Append numeric suffix
  for (let i = 2; i < 1000; i++) {
    const candidate = `${base}_${i}`;
    if (!existingIds.has(candidate)) {
      existingIds.add(candidate);
      return candidate;
    }
  }
  throw new Error(`Cannot generate unique ID for: ${name}`);
}

// ── Haversine distance (meters) ─────────────────────────────────────────────

function haversineM(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const toRad = d => d * Math.PI / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

// ── Name normalization for matching ─────────────────────────────────────────

function normalizeName(name) {
  return (name || '')
    .toLowerCase()
    .replace(/['']/g, '')
    .replace(/&/g, 'and')
    .replace(/the\s+/g, '')
    .replace(/[^a-z0-9]/g, '')
    .trim();
}

// ── Main import ─────────────────────────────────────────────────────────────

async function main() {
  console.log(`\n=== BCS POI Import (Phase 9U Step 9U.10) ===`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log(`Snapshot: ${SNAPSHOT_PATH}\n`);

  // Load BCS snapshot
  const raw = fs.readFileSync(SNAPSHOT_PATH, 'utf8');
  const { count, pois: bcsPois } = JSON.parse(raw);
  console.log(`Loaded ${count} BCS POIs from snapshot`);

  // Filter: must have lat/lng
  const validPois = bcsPois.filter(p => p.latitude && p.longitude);
  const skippedNoCoords = bcsPois.length - validPois.length;
  console.log(`Valid (with coords): ${validPois.length}, Skipped (no coords): ${skippedNoCoords}`);

  const client = await pool.connect();
  try {
    // Load existing POIs for matching
    const { rows: existing } = await client.query(
      'SELECT id, name, lat, lng, intel_entity_id FROM salem_pois WHERE deleted_at IS NULL'
    );
    console.log(`Existing salem_pois: ${existing.length}`);

    // Build lookup structures
    // Load ALL IDs (including soft-deleted) to avoid PK collisions on insert
    const { rows: allIds } = await client.query('SELECT id FROM salem_pois');
    const existingIds = new Set(allIds.map(r => r.id));
    const existingByNorm = new Map();
    for (const row of existing) {
      const norm = normalizeName(row.name);
      if (!existingByNorm.has(norm)) existingByNorm.set(norm, []);
      existingByNorm.get(norm).push(row);
    }

    // Match BCS POIs to existing
    const matched = [];   // { bcs, existingRow }
    const unmatched = []; // { bcs }
    const alreadyLinked = new Set(); // existing IDs that have been matched

    for (const bcs of validPois) {
      const bcsNorm = normalizeName(bcs.display_name);
      const candidates = existingByNorm.get(bcsNorm) || [];

      let bestMatch = null;
      let bestDist = Infinity;

      for (const row of candidates) {
        if (alreadyLinked.has(row.id)) continue;
        const dist = haversineM(bcs.latitude, bcs.longitude, row.lat, row.lng);
        if (dist < 200 && dist < bestDist) { // 200m threshold for same-name match
          bestMatch = row;
          bestDist = dist;
        }
      }

      if (bestMatch) {
        matched.push({ bcs, existingRow: bestMatch, distance: bestDist });
        alreadyLinked.add(bestMatch.id);
      } else {
        unmatched.push(bcs);
      }
    }

    console.log(`\nMatch results:`);
    console.log(`  Matched (enrich): ${matched.length}`);
    console.log(`  New (insert): ${unmatched.length}`);

    // Category mapping stats
    const catStats = {};
    const visCounts = { visible: 0, hidden: 0 };

    if (!DRY_RUN) {
      await client.query('BEGIN');
    }

    // ── Phase B: ENRICH matched POIs ──────────────────────────────────────
    let enriched = 0;
    for (const { bcs, existingRow } of matched) {
      const updates = {};
      // Always set the intel link
      updates.intel_entity_id = bcs.entity_id;

      // S135: Always update coords from BCS — SI has validated all geocodes.
      // Only skip if BCS still has the old placeholder (42.5213339, -70.8958456).
      const isPlaceholder = Math.abs(bcs.latitude - 42.5213339) < 0.0001 &&
                            Math.abs(bcs.longitude - (-70.8958456)) < 0.0001;
      if (!isPlaceholder) {
        updates.lat = bcs.latitude;
        updates.lng = bcs.longitude;
      }
      if (bcs.street_address) updates.address = bcs.street_address;

      // Enrich with BCS data only if current value is null/empty
      if (!existingRow.phone && bcs.phone) updates.phone = bcs.phone;
      if (bcs.website) updates.website = bcs.website;
      if (bcs.email) updates.email = bcs.email;
      if (bcs.hours) updates.hours = JSON.stringify(bcs.hours);
      if (bcs.amenities) updates.amenities = JSON.stringify(bcs.amenities);
      if (bcs.price_range) updates.price_range = bcs.price_range;
      if (bcs.menu_url) updates.menu_url = bcs.menu_url;
      if (bcs.reservations_url) updates.reservations_url = bcs.reservations_url;
      if (bcs.order_url) updates.order_url = bcs.order_url;
      if (bcs.secondary_categories) updates.secondary_categories = JSON.stringify(bcs.secondary_categories);
      if (bcs.specialties) updates.specialties = JSON.stringify(bcs.specialties);
      if (bcs.owners && bcs.owners.length) updates.owners = JSON.stringify(bcs.owners);
      if (bcs.year_established) updates.year_established = bcs.year_established;
      if (bcs.district) updates.district = bcs.district;
      if (bcs.short_description && !existingRow.short_description) updates.short_description = bcs.short_description;
      if (bcs.long_description && !existingRow.description) updates.description = bcs.long_description;
      if (bcs.origin_story) updates.origin_story = bcs.origin_story;

      // Build SET clause
      const setCols = Object.keys(updates);
      if (setCols.length === 0) continue;

      const setClause = setCols.map((col, i) => {
        if (['hours', 'amenities', 'secondary_categories', 'specialties', 'owners'].includes(col)) {
          return `${col} = $${i + 1}::jsonb`;
        }
        return `${col} = $${i + 1}`;
      }).join(', ');

      const sql = `UPDATE salem_pois SET ${setClause}, updated_at = NOW() WHERE id = $${setCols.length + 1}`;
      const vals = [...setCols.map(c => updates[c]), existingRow.id];

      if (!DRY_RUN) {
        await client.query(sql, vals);
      }
      enriched++;
    }
    console.log(`  Enriched: ${enriched}`);

    // ── Phase C: INSERT new POIs ──────────────────────────────────────────
    let inserted = 0;
    let catSkipped = 0;

    for (const bcs of unmatched) {
      const mapped = mapBcsCategory(bcs.primary_category, bcs.display_name, bcs.short_description || '');
      const visible = TOURIST_VISIBLE.has(mapped.category);

      // Track category stats
      const key = `${mapped.category}${mapped.subcategory ? '/' + mapped.subcategory : ''}`;
      catStats[key] = (catStats[key] || 0) + 1;
      if (visible) visCounts.visible++;
      else visCounts.hidden++;

      const id = makeUniqueId(bcs.display_name || 'unnamed', existingIds);

      const row = {
        id,
        name: bcs.display_name || 'Unnamed Business',
        lat: bcs.latitude,
        lng: bcs.longitude,
        address: bcs.street_address || null,
        status: bcs.status || 'open',
        category: mapped.category,
        subcategory: mapped.subcategory,
        short_description: bcs.short_description || null,
        description: bcs.long_description || null,
        origin_story: bcs.origin_story || null,
        phone: bcs.phone || null,
        email: bcs.email || null,
        website: bcs.website || null,
        hours: bcs.hours ? JSON.stringify(bcs.hours) : null,
        menu_url: bcs.menu_url || null,
        reservations_url: bcs.reservations_url || null,
        order_url: bcs.order_url || null,
        price_range: bcs.price_range || null,
        intel_entity_id: bcs.entity_id,
        secondary_categories: bcs.secondary_categories ? JSON.stringify(bcs.secondary_categories) : '[]',
        specialties: bcs.specialties ? JSON.stringify(bcs.specialties) : '[]',
        owners: bcs.owners && bcs.owners.length ? JSON.stringify(bcs.owners) : '[]',
        year_established: bcs.year_established || null,
        amenities: bcs.amenities ? JSON.stringify(bcs.amenities) : '{}',
        district: bcs.district || null,
        data_source: 'salem_intelligence_bcs',
        confidence: bcs.confidence || 0.5,
        default_visible: visible,
        is_narrated: false,
        is_tour_poi: false,
      };

      const cols = Object.keys(row);
      const placeholders = cols.map((col, i) => {
        if (['hours', 'amenities', 'secondary_categories', 'specialties', 'owners'].includes(col)) {
          return `$${i + 1}::jsonb`;
        }
        return `$${i + 1}`;
      }).join(', ');

      const sql = `INSERT INTO salem_pois (${cols.join(', ')}) VALUES (${placeholders})`;

      if (!DRY_RUN) {
        await client.query(sql, cols.map(c => row[c]));
      }
      inserted++;
    }

    console.log(`  Inserted: ${inserted}`);

    // ── Phase D: ORPHAN CLEANUP ──────────────────────────────────────────
    // Soft-delete LMA rows whose intel_entity_id no longer appears in the
    // SI export. These are entities SI merged or deleted upstream.
    const exportEntityIds = new Set(validPois.map(p => p.entity_id));

    const { rows: linkedRows } = await client.query(
      `SELECT id, name, intel_entity_id FROM salem_pois
       WHERE deleted_at IS NULL AND intel_entity_id IS NOT NULL`
    );

    const orphans = linkedRows.filter(r => !exportEntityIds.has(r.intel_entity_id));
    let orphanDeleted = 0;

    for (const orphan of orphans) {
      if (!DRY_RUN) {
        await client.query(
          `UPDATE salem_pois
           SET deleted_at = NOW(),
               data_source = data_source || '|bcs-orphan-2026-04-16'
           WHERE id = $1`,
          [orphan.id]
        );
      }
      console.log(`    Orphan: ${orphan.id} (${orphan.name}) — entity ${orphan.intel_entity_id} not in export`);
      orphanDeleted++;
    }
    console.log(`  Orphans soft-deleted: ${orphanDeleted}`);

    if (!DRY_RUN) {
      await client.query('COMMIT');
    }

    // ── Summary ───────────────────────────────────────────────────────────
    console.log(`\n=== Category distribution (new inserts) ===`);
    const sorted = Object.entries(catStats).sort((a, b) => b[1] - a[1]);
    for (const [cat, count] of sorted) {
      console.log(`  ${cat}: ${count}`);
    }

    console.log(`\n=== Visibility (new inserts) ===`);
    console.log(`  default_visible=true:  ${visCounts.visible}`);
    console.log(`  default_visible=false: ${visCounts.hidden}`);

    console.log(`\n=== Final counts ===`);
    if (!DRY_RUN) {
      const { rows: [{ total, with_intel, vis, hidden }] } = await client.query(`
        SELECT
          count(*) as total,
          count(intel_entity_id) as with_intel,
          count(*) FILTER (WHERE default_visible) as vis,
          count(*) FILTER (WHERE NOT default_visible) as hidden
        FROM salem_pois WHERE deleted_at IS NULL
      `);
      console.log(`  Total POIs: ${total}`);
      console.log(`  With intel_entity_id: ${with_intel}`);
      console.log(`  default_visible=true: ${vis}`);
      console.log(`  default_visible=false: ${hidden}`);
    } else {
      console.log(`  (dry run — no DB changes)`);
    }

    console.log(`\n${DRY_RUN ? 'DRY RUN COMPLETE' : 'IMPORT COMPLETE'}`);

  } catch (err) {
    if (!DRY_RUN) {
      try { await client.query('ROLLBACK'); } catch (_) {}
    }
    console.error('Import failed:', err.message);
    console.error(err.stack);
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

main();
