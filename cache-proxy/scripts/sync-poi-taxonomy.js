#!/usr/bin/env node
/*
 * LocationMapApp v1.5 — Phase 9P.B+ (Session 114)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * POI Taxonomy Sync — parses web/src/config/poiCategories.ts (the canonical
 * TS mirror of PoiCategories.kt) and upserts its contents into the two
 * PostgreSQL lookup tables:
 *
 *   salem_poi_categories      (22 rows)
 *   salem_poi_subcategories   (~176 rows; exact count printed on stdout)
 *
 * Idempotent: re-running upserts existing rows and prunes rows that have
 * disappeared from the source file. Safe to run whenever PoiCategories.kt
 * (or its TS mirror) changes.
 *
 * Usage:
 *   DATABASE_URL=postgres://... node cache-proxy/scripts/sync-poi-taxonomy.js
 *
 * Exit codes:
 *   0  — sync complete
 *   1  — parse or DB error
 */

const path = require('path');
const fs = require('fs');
const vm = require('vm');
const { Client } = require('pg');

const PROJECT_ROOT = path.resolve(__dirname, '..', '..');
const TS_SOURCE = path.join(PROJECT_ROOT, 'web', 'src', 'config', 'poiCategories.ts');

// ── Parsing ───────────────────────────────────────────────────────────────

/**
 * Read poiCategories.ts and extract the POI_CATEGORIES array literal.
 * The TS file's POI_CATEGORIES export is a plain JS object-literal array
 * with `PoiLayerId.XXX` identifiers as category ids — we rewrite those
 * to string literals, then eval the result in a sandboxed vm context.
 */
function loadPoiCategories() {
  const raw = fs.readFileSync(TS_SOURCE, 'utf8');

  // Grab everything from `export const POI_CATEGORIES: PoiCategory[] = [`
  // up to the matching `];` on its own line. The TS file is hand-maintained
  // and the closing bracket is always on its own line (verified in the
  // source), so this anchor is reliable.
  const start = raw.indexOf('export const POI_CATEGORIES');
  if (start === -1) {
    throw new Error(`POI_CATEGORIES export not found in ${TS_SOURCE}`);
  }
  // Find the `=` of the assignment, then the first `[` AFTER it.
  // Searching for `[` directly from `start` would hit the `[]` in
  // `PoiCategory[]` in the type annotation, which is not the array.
  const equalsIdx = raw.indexOf('=', start);
  if (equalsIdx === -1) {
    throw new Error('= of POI_CATEGORIES assignment not found');
  }
  const arrayStart = raw.indexOf('[', equalsIdx);
  if (arrayStart === -1) {
    throw new Error('Opening [ of POI_CATEGORIES array not found');
  }
  // Walk forward matching brackets to find the balanced close.
  let depth = 0;
  let arrayEnd = -1;
  for (let i = arrayStart; i < raw.length; i++) {
    const ch = raw[i];
    if (ch === '[') depth++;
    else if (ch === ']') {
      depth--;
      if (depth === 0) { arrayEnd = i; break; }
    }
  }
  if (arrayEnd === -1) {
    throw new Error('Closing ] of POI_CATEGORIES array not found');
  }

  let literal = raw.slice(arrayStart, arrayEnd + 1);

  // Rewrite PoiLayerId.FOO -> 'FOO'
  literal = literal.replace(/PoiLayerId\.(\w+)/g, "'$1'");

  // Run inside an isolated VM context.
  const sandbox = {};
  vm.createContext(sandbox);
  vm.runInContext(`categories = ${literal};`, sandbox);

  const categories = sandbox.categories;
  if (!Array.isArray(categories)) {
    throw new Error('Parsed POI_CATEGORIES is not an array');
  }
  return categories;
}

/**
 * Turn a human-readable label ("Fast Food", "Wine Shops") into a POI
 * subcategory slug: lowercase, non-alphanumerics to underscores,
 * collapse runs, trim leading/trailing underscores.
 */
function slugify(label) {
  return label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

// ── DB ops ────────────────────────────────────────────────────────────────

async function upsertCategories(client, categories) {
  const ids = [];
  for (let i = 0; i < categories.length; i++) {
    const cat = categories[i];
    ids.push(cat.id);
    await client.query(
      `
        INSERT INTO salem_poi_categories
          (id, label, pref_key, tags, color, default_enabled, historic_tour_default, display_order, source, updated_at)
        VALUES ($1, $2, $3, $4::jsonb, $5, $6, $7, $8, 'poiCategories.ts', NOW())
        ON CONFLICT (id) DO UPDATE SET
          label                 = EXCLUDED.label,
          pref_key              = EXCLUDED.pref_key,
          tags                  = EXCLUDED.tags,
          color                 = EXCLUDED.color,
          default_enabled       = EXCLUDED.default_enabled,
          historic_tour_default = EXCLUDED.historic_tour_default,
          display_order         = EXCLUDED.display_order,
          updated_at            = NOW();
      `,
      [
        cat.id,
        cat.label,
        cat.prefKey,
        JSON.stringify(cat.tags || []),
        cat.color,
        !!cat.defaultEnabled,
        !!cat.historicTourDefault,
        i + 1,
      ],
    );
  }
  return ids;
}

async function upsertSubcategories(client, categories) {
  const ids = [];
  for (const cat of categories) {
    const subtypes = Array.isArray(cat.subtypes) ? cat.subtypes : [];
    for (let i = 0; i < subtypes.length; i++) {
      const sub = subtypes[i];
      const slug = slugify(sub.label);
      const id = `${cat.id}__${slug}`;
      ids.push(id);
      await client.query(
        `
          INSERT INTO salem_poi_subcategories
            (id, category_id, label, slug, tags, display_order, source, updated_at)
          VALUES ($1, $2, $3, $4, $5::jsonb, $6, 'poiCategories.ts', NOW())
          ON CONFLICT (id) DO UPDATE SET
            category_id   = EXCLUDED.category_id,
            label         = EXCLUDED.label,
            slug          = EXCLUDED.slug,
            tags          = EXCLUDED.tags,
            display_order = EXCLUDED.display_order,
            updated_at    = NOW();
        `,
        [
          id,
          cat.id,
          sub.label,
          slug,
          JSON.stringify(sub.tags || []),
          i + 1,
        ],
      );
    }
  }
  return ids;
}

async function pruneSubcategories(client, keepIds) {
  const del = await client.query(
    `DELETE FROM salem_poi_subcategories WHERE id != ALL($1::text[]) RETURNING id`,
    [keepIds],
  );
  if (del.rowCount > 0) {
    console.log(`  pruned ${del.rowCount} stale subcategor${del.rowCount === 1 ? 'y' : 'ies'}:`);
    for (const row of del.rows) console.log(`    - ${row.id}`);
  }
}

async function pruneCategories(client, keepIds) {
  const del = await client.query(
    `DELETE FROM salem_poi_categories WHERE id != ALL($1::text[]) RETURNING id`,
    [keepIds],
  );
  if (del.rowCount > 0) {
    console.log(`  pruned ${del.rowCount} stale categor${del.rowCount === 1 ? 'y' : 'ies'}:`);
    for (const row of del.rows) console.log(`    - ${row.id}`);
  }
}

// ── Main ──────────────────────────────────────────────────────────────────

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error('ERROR: DATABASE_URL env var not set.');
    console.error('Source cache-proxy/.env first:  source cache-proxy/.env && node ...');
    process.exit(1);
  }

  console.log(`Parsing ${path.relative(PROJECT_ROOT, TS_SOURCE)}...`);
  const categories = loadPoiCategories();
  console.log(`  found ${categories.length} categories`);

  const client = new Client({ connectionString: process.env.DATABASE_URL });
  await client.connect();

  try {
    await client.query('BEGIN');

    // Order matters:
    //   1. upsert categories first (subcategories FK them)
    //   2. upsert subcategories (now the FK targets exist)
    //   3. prune stale subcategories (FK-safe because we haven't touched categories yet)
    //   4. prune stale categories (subcategories that referenced them are gone)
    console.log('Upserting categories...');
    const catIds = await upsertCategories(client, categories);
    console.log(`  upserted ${catIds.length} categories`);

    console.log('Upserting subcategories...');
    const subcatIds = await upsertSubcategories(client, categories);
    console.log(`  upserted ${subcatIds.length} subcategories`);

    console.log('Pruning stale subcategories...');
    await pruneSubcategories(client, subcatIds);

    console.log('Pruning stale categories...');
    await pruneCategories(client, catIds);

    await client.query('COMMIT');

    // Sanity check: count rows we just wrote.
    const catRes  = await client.query('SELECT COUNT(*)::int AS n FROM salem_poi_categories');
    const subRes  = await client.query('SELECT COUNT(*)::int AS n FROM salem_poi_subcategories');
    console.log('');
    console.log(`✓ salem_poi_categories:    ${catRes.rows[0].n} rows`);
    console.log(`✓ salem_poi_subcategories: ${subRes.rows[0].n} rows`);
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    await client.end();
  }
}

main().catch((err) => {
  console.error('sync-poi-taxonomy: FAILED');
  console.error(err);
  process.exit(1);
});
