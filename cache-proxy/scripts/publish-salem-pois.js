#!/usr/bin/env node
/*
 * publish-salem-pois.js — Phase 9U (Session 118)
 *
 * Exports unified salem_pois from PostgreSQL into the bundled Room SQLite
 * database (salem_content.db). This is the publish loop: PG is canonical,
 * SQLite is the offline bundle shipped in the APK.
 *
 * What this script does:
 *   1. Opens the existing salem_content.db (preserves all other tables)
 *   2. Creates the salem_pois table if it doesn't exist
 *   3. Deletes all existing rows in salem_pois (full replace)
 *   4. Reads all non-deleted rows from PG salem_pois
 *   5. Inserts them into SQLite with type conversions:
 *      - JSONB → TEXT (JSON string)
 *      - BOOLEAN → INTEGER (0/1)
 *      - TIMESTAMPTZ → dropped (not needed offline)
 *      - NULL address → empty string (Room entity has non-null address in some entities)
 *   6. Copies the updated .db to app-salem/src/main/assets/
 *
 * Usage:
 *   node scripts/publish-salem-pois.js                    # live run
 *   node scripts/publish-salem-pois.js --dry-run          # count only
 *   node scripts/publish-salem-pois.js --db path/to/db    # use specific .db
 *
 * Requires: DATABASE_URL, better-sqlite3 (npm install better-sqlite3)
 */

const { Pool } = require('pg');
const path = require('path');
const fs = require('fs');

let Database;
try {
  Database = require('better-sqlite3');
} catch (_) {
  console.error('Error: better-sqlite3 not installed. Run: cd cache-proxy && npm install better-sqlite3');
  process.exit(1);
}

const DRY_RUN = process.argv.includes('--dry-run');
const dbIdx = process.argv.indexOf('--db');
const SQLITE_PATH = dbIdx !== -1
  ? process.argv[dbIdx + 1]
  : path.resolve(__dirname, '../../salem-content/salem_content.db');
const ASSETS_PATH = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');

if (!process.env.DATABASE_URL) {
  // Parse .env manually (no dotenv dependency)
  const envPath = path.resolve(__dirname, '../.env');
  try {
    const envContent = fs.readFileSync(envPath, 'utf8');
    for (const line of envContent.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eqIdx = trimmed.indexOf('=');
      if (eqIdx === -1) continue;
      const key = trimmed.slice(0, eqIdx).trim();
      let val = trimmed.slice(eqIdx + 1).trim();
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      if (!process.env[key]) process.env[key] = val;
    }
  } catch (_) {}
}
if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL environment variable is required');
  process.exit(1);
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// Room-compatible CREATE TABLE for salem_pois (must match SalemPoi.kt exactly)
// CRITICAL: NO DEFAULT clauses — Room validates the pre-packaged DB schema
// against @Entity definitions and crashes if any column has a DEFAULT that
// Room doesn't expect (defaultValue='undefined' in Room = no SQL DEFAULT).
const CREATE_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS salem_pois (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  lat REAL NOT NULL,
  lng REAL NOT NULL,
  address TEXT,
  status TEXT,
  category TEXT NOT NULL,
  subcategory TEXT,
  short_narration TEXT,
  long_narration TEXT,
  historical_narration TEXT,
  geofence_radius_m INTEGER NOT NULL,
  geofence_shape TEXT NOT NULL,
  corridor_points TEXT,
  priority INTEGER NOT NULL,
  wave INTEGER,
  voice_clip_asset TEXT,
  custom_voice_asset TEXT,
  cuisine_type TEXT,
  price_range TEXT,
  rating REAL,
  merchant_tier INTEGER NOT NULL,
  ad_priority INTEGER NOT NULL,
  historical_period TEXT,
  admission_info TEXT,
  requires_transportation INTEGER NOT NULL,
  wheelchair_accessible INTEGER NOT NULL,
  seasonal INTEGER NOT NULL,
  phone TEXT,
  email TEXT,
  website TEXT,
  hours TEXT,
  hours_text TEXT,
  menu_url TEXT,
  reservations_url TEXT,
  order_url TEXT,
  description TEXT,
  short_description TEXT,
  custom_description TEXT,
  origin_story TEXT,
  image_asset TEXT,
  custom_icon_asset TEXT,
  action_buttons TEXT,
  secondary_categories TEXT,
  specialties TEXT,
  owners TEXT,
  year_established INTEGER,
  amenities TEXT,
  district TEXT,
  related_figure_ids TEXT,
  related_fact_ids TEXT,
  related_source_ids TEXT,
  data_source TEXT NOT NULL,
  confidence REAL NOT NULL,
  is_tour_poi INTEGER NOT NULL,
  is_civic_poi INTEGER NOT NULL,
  is_historical_property INTEGER NOT NULL DEFAULT 0,
  is_narrated INTEGER NOT NULL,
  default_visible INTEGER NOT NULL,
  has_announce_narration INTEGER NOT NULL DEFAULT 0,
  building_footprint_geojson TEXT,
  mhc_id TEXT,
  mhc_year_built INTEGER,
  mhc_style TEXT,
  mhc_nr_status TEXT,
  mhc_narrative TEXT,
  canonical_address_point_id TEXT,
  local_historic_district TEXT,
  parcel_owner_class TEXT
)`;

// Update Room version hash table
const ROOM_MASTER_SQL = `
CREATE TABLE IF NOT EXISTS room_master_table (
  id INTEGER PRIMARY KEY,
  identity_hash TEXT
)`;

async function main() {
  console.log(`\n=== Publish Salem POIs (PG → SQLite) ===`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log(`SQLite: ${SQLITE_PATH}`);
  console.log(`Assets: ${ASSETS_PATH}\n`);

  // Read all non-deleted POIs from PG
  const pgClient = await pool.connect();
  let pgRows;
  try {
    const { rows } = await pgClient.query(`
      SELECT
        id, name, lat, lng, address, status, category, subcategory,
        short_narration, long_narration, historical_narration,
        geofence_radius_m, geofence_shape, corridor_points,
        priority, wave, voice_clip_asset, custom_voice_asset,
        cuisine_type, price_range, rating, merchant_tier, ad_priority,
        historical_period, admission_info,
        requires_transportation, wheelchair_accessible, seasonal,
        phone, email, website, hours, hours_text,
        menu_url, reservations_url, order_url,
        description, short_description, custom_description, origin_story,
        image_asset, custom_icon_asset, action_buttons,
        secondary_categories, specialties, owners,
        year_established, amenities, district,
        related_figure_ids, related_fact_ids, related_source_ids,
        data_source, confidence,
        is_tour_poi, is_civic_poi, is_historical_property,
        is_narrated, default_visible,
        has_announce_narration,
        building_footprint_geojson, mhc_id, mhc_year_built,
        mhc_style, mhc_nr_status, mhc_narrative,
        canonical_address_point_id, local_historic_district, parcel_owner_class
      FROM salem_pois
      WHERE deleted_at IS NULL
      ORDER BY category, priority, name
    `);
    pgRows = rows;
    console.log(`PG rows: ${pgRows.length}`);
  } finally {
    pgClient.release();
  }

  if (DRY_RUN) {
    const cats = {};
    for (const r of pgRows) {
      cats[r.category] = (cats[r.category] || 0) + 1;
    }
    console.log('\nCategory distribution:');
    for (const [c, n] of Object.entries(cats).sort((a, b) => b[1] - a[1])) {
      console.log(`  ${c}: ${n}`);
    }
    console.log(`\nDRY RUN COMPLETE — would publish ${pgRows.length} rows`);
    await pool.end();
    return;
  }

  // Open SQLite
  if (!fs.existsSync(SQLITE_PATH)) {
    console.error(`SQLite file not found: ${SQLITE_PATH}`);
    process.exit(1);
  }
  const db = new Database(SQLITE_PATH);

  // Drop + recreate so schema changes (e.g. S125 has_announce_narration)
  // take effect. The bundled Room DB is always full-replaced by this
  // script — no migration logic lives here.
  db.exec('DROP TABLE IF EXISTS salem_pois');
  db.exec(CREATE_TABLE_SQL);
  console.log(`Rebuilt salem_pois table with current schema`);

  // Prepare insert
  const insertStmt = db.prepare(`
    INSERT INTO salem_pois (
      id, name, lat, lng, address, status, category, subcategory,
      short_narration, long_narration, historical_narration,
      geofence_radius_m, geofence_shape, corridor_points,
      priority, wave, voice_clip_asset, custom_voice_asset,
      cuisine_type, price_range, rating, merchant_tier, ad_priority,
      historical_period, admission_info,
      requires_transportation, wheelchair_accessible, seasonal,
      phone, email, website, hours, hours_text,
      menu_url, reservations_url, order_url,
      description, short_description, custom_description, origin_story,
      image_asset, custom_icon_asset, action_buttons,
      secondary_categories, specialties, owners,
      year_established, amenities, district,
      related_figure_ids, related_fact_ids, related_source_ids,
      data_source, confidence,
      is_tour_poi, is_civic_poi, is_historical_property,
      is_narrated, default_visible,
      has_announce_narration,
      building_footprint_geojson, mhc_id, mhc_year_built,
      mhc_style, mhc_nr_status, mhc_narrative,
      canonical_address_point_id, local_historic_district, parcel_owner_class
    ) VALUES (
      @id, @name, @lat, @lng, @address, @status, @category, @subcategory,
      @short_narration, @long_narration, @historical_narration,
      @geofence_radius_m, @geofence_shape, @corridor_points,
      @priority, @wave, @voice_clip_asset, @custom_voice_asset,
      @cuisine_type, @price_range, @rating, @merchant_tier, @ad_priority,
      @historical_period, @admission_info,
      @requires_transportation, @wheelchair_accessible, @seasonal,
      @phone, @email, @website, @hours, @hours_text,
      @menu_url, @reservations_url, @order_url,
      @description, @short_description, @custom_description, @origin_story,
      @image_asset, @custom_icon_asset, @action_buttons,
      @secondary_categories, @specialties, @owners,
      @year_established, @amenities, @district,
      @related_figure_ids, @related_fact_ids, @related_source_ids,
      @data_source, @confidence,
      @is_tour_poi, @is_civic_poi, @is_historical_property,
      @is_narrated, @default_visible,
      @has_announce_narration,
      @building_footprint_geojson, @mhc_id, @mhc_year_built,
      @mhc_style, @mhc_nr_status, @mhc_narrative,
      @canonical_address_point_id, @local_historic_district, @parcel_owner_class
    )
  `);

  // Batch insert in a transaction
  const insertMany = db.transaction((rows) => {
    for (const r of rows) {
      insertStmt.run({
        id: r.id,
        name: r.name,
        lat: r.lat,
        lng: r.lng,
        address: r.address || null,
        status: r.status || 'open',
        category: r.category,
        subcategory: r.subcategory || null,
        short_narration: r.short_narration || null,
        long_narration: r.long_narration || null,
        historical_narration: r.historical_narration || null,
        geofence_radius_m: r.geofence_radius_m || 40,
        geofence_shape: r.geofence_shape || 'circle',
        corridor_points: r.corridor_points || null,
        priority: r.priority || 3,
        wave: r.wave || null,
        voice_clip_asset: r.voice_clip_asset || null,
        custom_voice_asset: r.custom_voice_asset || null,
        cuisine_type: r.cuisine_type || null,
        price_range: r.price_range || null,
        rating: r.rating || null,
        merchant_tier: r.merchant_tier || 0,
        ad_priority: r.ad_priority || 0,
        historical_period: r.historical_period || null,
        admission_info: r.admission_info || null,
        requires_transportation: r.requires_transportation ? 1 : 0,
        wheelchair_accessible: r.wheelchair_accessible !== false ? 1 : 0,
        seasonal: r.seasonal ? 1 : 0,
        phone: r.phone || null,
        email: r.email || null,
        website: r.website || null,
        hours: typeof r.hours === 'object' ? JSON.stringify(r.hours) : (r.hours || null),
        hours_text: r.hours_text || null,
        menu_url: r.menu_url || null,
        reservations_url: r.reservations_url || null,
        order_url: r.order_url || null,
        description: r.description || null,
        short_description: r.short_description || null,
        custom_description: r.custom_description || null,
        origin_story: r.origin_story || null,
        image_asset: r.image_asset || null,
        custom_icon_asset: r.custom_icon_asset || null,
        action_buttons: typeof r.action_buttons === 'object' ? JSON.stringify(r.action_buttons) : (r.action_buttons || null),
        secondary_categories: typeof r.secondary_categories === 'object' ? JSON.stringify(r.secondary_categories) : (r.secondary_categories || null),
        specialties: typeof r.specialties === 'object' ? JSON.stringify(r.specialties) : (r.specialties || null),
        owners: typeof r.owners === 'object' ? JSON.stringify(r.owners) : (r.owners || null),
        year_established: r.year_established || null,
        amenities: typeof r.amenities === 'object' ? JSON.stringify(r.amenities) : (r.amenities || null),
        district: r.district || null,
        related_figure_ids: typeof r.related_figure_ids === 'object' ? JSON.stringify(r.related_figure_ids) : (r.related_figure_ids || null),
        related_fact_ids: typeof r.related_fact_ids === 'object' ? JSON.stringify(r.related_fact_ids) : (r.related_fact_ids || null),
        related_source_ids: typeof r.related_source_ids === 'object' ? JSON.stringify(r.related_source_ids) : (r.related_source_ids || null),
        data_source: r.data_source || 'manual_curated',
        confidence: r.confidence != null ? r.confidence : 0.8,
        is_tour_poi: r.is_tour_poi ? 1 : 0,
        is_civic_poi: r.is_civic_poi ? 1 : 0,
        is_historical_property: r.is_historical_property ? 1 : 0,
        is_narrated: r.is_narrated ? 1 : 0,
        default_visible: r.default_visible !== false ? 1 : 0,
        has_announce_narration: r.has_announce_narration ? 1 : 0,
        building_footprint_geojson: r.building_footprint_geojson || null,
        mhc_id: r.mhc_id || null,
        mhc_year_built: r.mhc_year_built || null,
        mhc_style: r.mhc_style || null,
        mhc_nr_status: r.mhc_nr_status || null,
        mhc_narrative: r.mhc_narrative || null,
        canonical_address_point_id: r.canonical_address_point_id || null,
        local_historic_district: r.local_historic_district || null,
        parcel_owner_class: r.parcel_owner_class || null,
      });
    }
  });

  insertMany(pgRows);
  console.log(`Inserted ${pgRows.length} rows into SQLite salem_pois`);

  // Verify
  const countResult = db.prepare('SELECT COUNT(*) as cnt FROM salem_pois').get();
  const visibleResult = db.prepare('SELECT COUNT(*) as cnt FROM salem_pois WHERE default_visible = 1').get();
  const narratedResult = db.prepare('SELECT COUNT(*) as cnt FROM salem_pois WHERE is_narrated = 1').get();
  const silentResult = db.prepare('SELECT COUNT(*) as cnt FROM salem_pois WHERE has_announce_narration = 0').get();
  console.log(`\nSQLite verification:`);
  console.log(`  Total: ${countResult.cnt}`);
  console.log(`  Visible: ${visibleResult.cnt}`);
  console.log(`  Narrated: ${narratedResult.cnt}`);
  console.log(`  Silent (no announce narration): ${silentResult.cnt}`);

  db.close();

  // Copy to assets
  fs.copyFileSync(SQLITE_PATH, ASSETS_PATH);
  const size = fs.statSync(ASSETS_PATH).size;
  console.log(`\nCopied to assets: ${ASSETS_PATH} (${(size / 1024 / 1024).toFixed(1)} MB)`);

  console.log('\nPUBLISH COMPLETE');
  await pool.end();
}

main().catch(err => {
  console.error('Publish failed:', err.message);
  console.error(err.stack);
  process.exit(1);
});
