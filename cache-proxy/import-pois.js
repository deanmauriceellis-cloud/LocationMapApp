#!/usr/bin/env node
// Import POIs from cache proxy into PostgreSQL
// Usage: DATABASE_URL=postgres://user:pass@localhost/locationmapapp node import-pois.js

const { Client } = require('pg');

const EXPORT_URL = 'http://localhost:3000/pois/export';

// Category tag keys in priority order
const CATEGORY_KEYS = ['amenity', 'shop', 'tourism', 'leisure', 'historic', 'office'];

function deriveCategory(tags) {
  if (!tags) return null;
  for (const key of CATEGORY_KEYS) {
    if (tags[key]) return `${key}=${tags[key]}`;
  }
  return null;
}

function extractCoords(element) {
  if (element.lat != null && element.lon != null) {
    return { lat: element.lat, lon: element.lon };
  }
  if (element.center && element.center.lat != null && element.center.lon != null) {
    return { lat: element.center.lat, lon: element.center.lon };
  }
  return { lat: null, lon: null };
}

async function main() {
  const connString = process.env.DATABASE_URL;
  if (!connString) {
    console.error('Error: DATABASE_URL environment variable is required');
    console.error('Example: DATABASE_URL=postgres://postgres:PASSWORD@localhost/locationmapapp node import-pois.js');
    process.exit(1);
  }

  // Fetch POIs from proxy
  console.log(`Fetching POIs from ${EXPORT_URL}...`);
  const res = await fetch(EXPORT_URL);
  if (!res.ok) {
    console.error(`Failed to fetch: ${res.status} ${res.statusText}`);
    process.exit(1);
  }
  const { count, pois } = await res.json();
  console.log(`Fetched ${count} POIs from cache proxy`);

  if (count === 0) {
    console.log('Nothing to import.');
    return;
  }

  // Connect to PostgreSQL
  const client = new Client({ connectionString: connString });
  await client.connect();
  console.log('Connected to PostgreSQL');

  const UPSERT_SQL = `
    INSERT INTO pois (osm_type, osm_id, lat, lon, name, category, tags, first_seen, last_seen)
    VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
    ON CONFLICT (osm_type, osm_id) DO UPDATE SET
      lat = EXCLUDED.lat,
      lon = EXCLUDED.lon,
      name = EXCLUDED.name,
      category = EXCLUDED.category,
      tags = EXCLUDED.tags,
      last_seen = EXCLUDED.last_seen
  `;

  let inserted = 0;
  let updated = 0;
  let skipped = 0;

  // Batch in a single transaction
  await client.query('BEGIN');

  for (const poi of pois) {
    const el = poi.element;
    if (!el || !el.type || !el.id) {
      skipped++;
      continue;
    }

    const { lat, lon } = extractCoords(el);
    const tags = el.tags || {};
    const name = tags.name || null;
    const category = deriveCategory(tags);
    const firstSeen = new Date(poi.firstSeen).toISOString();
    const lastSeen = new Date(poi.lastSeen).toISOString();

    const result = await client.query(UPSERT_SQL, [
      el.type,
      el.id,
      lat,
      lon,
      name,
      category,
      JSON.stringify(tags),
      firstSeen,
      lastSeen,
    ]);

    // INSERT ... ON CONFLICT: xmax=0 means inserted, xmax>0 means updated
    // But pg doesn't expose that easily; count all as upserted
    if (result.rowCount > 0) {
      inserted++;
    }
  }

  await client.query('COMMIT');

  console.log(`\nImport complete:`);
  console.log(`  Upserted: ${inserted}`);
  if (skipped > 0) console.log(`  Skipped (invalid): ${skipped}`);

  // Verify
  const { rows } = await client.query('SELECT count(*) AS total FROM pois');
  console.log(`  Total POIs in database: ${rows[0].total}`);

  await client.end();
}

main().catch(err => {
  console.error('Import failed:', err);
  process.exit(1);
});
