#!/usr/bin/env node
/*
 * migrate-to-unified-pois.js — Phase 9U Step 9U.2 (Session 117)
 *
 * Merges salem_narration_points (817), salem_businesses (861), and
 * salem_tour_pois (45) into the new unified salem_pois table.
 *
 * Three phases in one transaction:
 *   A. INSERT all narration points (is_narrated = true)
 *   B. For businesses matching by ID → UPDATE with business fields;
 *      for unmatched businesses → INSERT (is_narrated = false)
 *   C. For tour POIs matching by ID → UPDATE with tour fields;
 *      for unmatched → try name match within 200m → UPDATE;
 *      remaining → INSERT (is_tour_poi = true)
 *
 * Usage:
 *   node scripts/migrate-to-unified-pois.js             # live run
 *   node scripts/migrate-to-unified-pois.js --dry-run   # log only
 *
 * Requires DATABASE_URL in environment or .env.
 */

const { Pool } = require('pg');

const DRY_RUN = process.argv.includes('--dry-run');

if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL environment variable is required');
  console.error('Example: DATABASE_URL=postgres://... node scripts/migrate-to-unified-pois.js');
  process.exit(1);
}
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// ── Legacy narration category → PoiLayerId mapping ──────────────────────────
// 29 distinct legacy values → 22 PoiLayerId categories.
// 'other' and 'services' are ambiguous — mapped to best-guess defaults.
const LEGACY_CATEGORY_MAP = {
  restaurant:          'FOOD_DRINK',
  cafe:                'FOOD_DRINK',
  bar:                 'FOOD_DRINK',
  brewery:             'FOOD_DRINK',
  shop:                'SHOPPING',
  witch_shop:          'WITCH_SHOP',
  psychic:             'PSYCHIC',
  ghost_tour:          'GHOST_TOUR',
  haunted_attraction:  'HAUNTED_ATTRACTION',
  historic_site:       'TOURISM_HISTORY',
  witch_museum:        'TOURISM_HISTORY',
  museum:              'TOURISM_HISTORY',
  tour:                'TOURISM_HISTORY',
  cemetery:            'TOURISM_HISTORY',
  visitor_info:        'TOURISM_HISTORY',
  park:                'PARKS_REC',
  public_art:          'PARKS_REC',
  hotel:               'LODGING',
  lodging:             'LODGING',
  venue:               'ENTERTAINMENT',
  attraction:          'ENTERTAINMENT',
  library:             'EDUCATION',
  government:          'CIVIC',
  community_center:    'CIVIC',
  public:              'CIVIC',
  place_of_worship:    'WORSHIP',
  medical:             'HEALTHCARE',
  services:            'SHOPPING',     // default for ambiguous; mostly retail/services
  other:               'SHOPPING',     // default fallback; admin tool fixes individual rows
};

// Tour POI legacy categories → PoiLayerId (tour categories are thematic)
const TOUR_CATEGORY_MAP = {
  witch_trials:       'TOURISM_HISTORY',
  maritime:           'TOURISM_HISTORY',
  literary:           'TOURISM_HISTORY',
  landmark:           'TOURISM_HISTORY',
  museum:             'TOURISM_HISTORY',
  park:               'PARKS_REC',
  visitor_services:   'CIVIC',
};

// Business type → PoiLayerId (used for business-only rows that have no narration match)
const BUSINESS_TYPE_MAP = {
  restaurant:           'FOOD_DRINK',
  cafe:                 'FOOD_DRINK',
  bar:                  'FOOD_DRINK',
  brewery:              'FOOD_DRINK',
  bakery:               'FOOD_DRINK',
  ice_cream:            'FOOD_DRINK',
  shop_retail:          'SHOPPING',
  shop_occult:          'WITCH_SHOP',
  shop_bookstore:       'SHOPPING',
  shop_antiques:        'SHOPPING',
  gallery_art:          'TOURISM_HISTORY',
  museum:               'TOURISM_HISTORY',
  attraction:           'ENTERTAINMENT',
  performance_venue:    'ENTERTAINMENT',
  hotel_lodging:        'LODGING',
  spa_beauty:           'SHOPPING',
  education:            'EDUCATION',
  religious:            'WORSHIP',
  service_health:       'HEALTHCARE',
  fitness:              'ENTERTAINMENT',
  service_professional: 'OFFICES',
  tour_operator:        'GHOST_TOUR',
};

function mapNarrationCategory(legacyCat) {
  return LEGACY_CATEGORY_MAP[legacyCat] || 'SHOPPING';
}

function mapTourCategory(legacyCat) {
  return TOUR_CATEGORY_MAP[legacyCat] || 'TOURISM_HISTORY';
}

function mapBusinessType(bizType) {
  return BUSINESS_TYPE_MAP[bizType] || 'SHOPPING';
}

async function run() {
  const client = await pool.connect();
  try {
    console.log(DRY_RUN ? '=== DRY RUN ===' : '=== LIVE MIGRATION ===');
    console.log();

    // Verify salem_pois exists and is empty
    const { rows: [{ count: existingCount }] } = await client.query(
      'SELECT COUNT(*) AS count FROM salem_pois'
    );
    if (parseInt(existingCount) > 0) {
      console.error(`ERROR: salem_pois already has ${existingCount} rows. Aborting.`);
      console.error('Drop and recreate the table if you want to re-run the migration.');
      process.exit(1);
    }

    await client.query('BEGIN');

    // In-memory tracking for dry-run mode (since INSERTs don't actually execute)
    const insertedIds = new Set();
    const insertedNames = new Map(); // lowercase name → { id, lat, lng }

    // ── Phase A: Narration points (817 rows) → salem_pois ─────────────────

    console.log('Phase A: Inserting narration points...');
    const { rows: narrationRows } = await client.query(
      'SELECT * FROM salem_narration_points ORDER BY id'
    );
    console.log(`  Found ${narrationRows.length} narration points`);

    let insertedNarration = 0;
    for (const n of narrationRows) {
      const mappedCategory = mapNarrationCategory(n.category);
      if (!DRY_RUN) {
        await client.query(`
          INSERT INTO salem_pois (
            id, name, lat, lng, address,
            category, subcategory,
            short_narration, long_narration, narration_pass_2, narration_pass_3,
            geofence_radius_m, geofence_shape, corridor_points,
            priority, wave,
            voice_clip_asset, custom_voice_asset,
            merchant_tier, ad_priority,
            custom_icon_asset, custom_description,
            phone, website, hours_text,
            description, image_asset,
            action_buttons,
            related_figure_ids, related_fact_ids, related_source_ids,
            source_id, source_categories, tags,
            data_source, confidence, verified_date,
            created_at, updated_at, stale_after, deleted_at,
            is_narrated, is_tour_poi, default_visible,
            legacy_narration_category
          ) VALUES (
            $1, $2, $3, $4, $5,
            $6, $7,
            $8, $9, $10, $11,
            $12, $13, $14,
            $15, $16,
            $17, $18,
            $19, $20,
            $21, $22,
            $23, $24, $25,
            $26, $27,
            $28,
            $29, $30, $31,
            $32, $33, $34,
            $35, $36, $37,
            $38, $39, $40, $41,
            true, false, true,
            $42
          )
        `, [
          n.id, n.name, n.lat, n.lng, n.address,
          mappedCategory, n.subcategory,
          n.short_narration, n.long_narration, n.narration_pass_2, n.narration_pass_3,
          n.geofence_radius_m, n.geofence_shape, n.corridor_points,
          n.priority, n.wave,
          n.voice_clip_asset, n.custom_voice_asset,
          n.merchant_tier, n.ad_priority,
          n.custom_icon_asset, n.custom_description,
          n.phone, n.website, n.hours,  // legacy hours string → hours_text
          n.description, n.image_asset,
          JSON.stringify(n.action_buttons || []),
          JSON.stringify(n.related_figure_ids || []),
          JSON.stringify(n.related_fact_ids || []),
          JSON.stringify(n.related_source_ids || []),
          n.source_id,
          JSON.stringify(n.source_categories || []),
          JSON.stringify(n.tags || []),
          n.data_source, n.confidence, n.verified_date,
          n.created_at, n.updated_at, n.stale_after, n.deleted_at,
          n.category,  // legacy_narration_category
        ]);
      }
      insertedIds.add(n.id);
      insertedNames.set(n.name.trim().toLowerCase(), { id: n.id, lat: n.lat, lng: n.lng });
      insertedNarration++;
    }
    console.log(`  Inserted: ${insertedNarration}`);

    // ── Phase B: Business enrichment ──────────────────────────────────────

    console.log('\nPhase B: Enriching from businesses...');
    const { rows: businessRows } = await client.query(
      'SELECT * FROM salem_businesses ORDER BY id'
    );
    console.log(`  Found ${businessRows.length} businesses`);

    let enriched = 0;
    let insertedBusiness = 0;
    for (const b of businessRows) {
      // Check if this business ID already exists (from Phase A)
      const exists = insertedIds.has(b.id);

      if (exists) {
        // Match found — enrich the narration point row with business data
        if (!DRY_RUN) {
          await client.query(`
            UPDATE salem_pois SET
              cuisine_type = $2,
              price_range = $3,
              rating = $4,
              historical_note = $5,
              legacy_business_type = $6,
              updated_at = NOW()
            WHERE id = $1
          `, [
            b.id,
            b.cuisine_type,
            b.price_range,
            b.rating,
            b.historical_note,
            b.business_type,
          ]);
        }
        enriched++;
      } else {
        // Business-only row — insert as new non-narrated POI
        const mappedCategory = b.category || mapBusinessType(b.business_type);
        if (!DRY_RUN) {
          await client.query(`
            INSERT INTO salem_pois (
              id, name, lat, lng, address,
              category, subcategory,
              cuisine_type, price_range, rating,
              historical_note,
              phone, website, hours_text,
              description, image_asset,
              tags,
              data_source, confidence, verified_date,
              created_at, updated_at, stale_after, deleted_at,
              is_narrated, is_tour_poi, default_visible,
              legacy_business_type
            ) VALUES (
              $1, $2, $3, $4, $5,
              $6, $7,
              $8, $9, $10,
              $11,
              $12, $13, $14,
              $15, $16,
              $17,
              $18, $19, $20,
              $21, $22, $23, $24,
              false, false, true,
              $25
            )
          `, [
            b.id, b.name, b.lat, b.lng, b.address,
            mappedCategory, b.subcategory,
            b.cuisine_type, b.price_range, b.rating,
            b.historical_note,
            b.phone, b.website, b.hours,  // legacy hours → hours_text
            b.description, b.image_asset,
            JSON.stringify(b.tags || []),
            b.data_source, b.confidence, b.verified_date,
            b.created_at, b.updated_at, b.stale_after, b.deleted_at,
            b.business_type,
          ]);
        }
        insertedIds.add(b.id);
        insertedNames.set(b.name.trim().toLowerCase(), { id: b.id, lat: b.lat, lng: b.lng });
        insertedBusiness++;
      }
    }
    console.log(`  Enriched existing rows: ${enriched}`);
    console.log(`  Inserted new business-only rows: ${insertedBusiness}`);

    // ── Phase C: Tour POIs ────────────────────────────────────────────────

    console.log('\nPhase C: Merging tour POIs...');
    const { rows: tourRows } = await client.query(
      'SELECT * FROM salem_tour_pois ORDER BY id'
    );
    console.log(`  Found ${tourRows.length} tour POIs`);

    let updatedTourById = 0;
    let updatedTourByName = 0;
    let insertedTour = 0;

    for (const t of tourRows) {
      // Try exact ID match first (using in-memory set)
      if (insertedIds.has(t.id)) {
        // ID match — update with tour-specific fields
        if (!DRY_RUN) {
          await client.query(`
            UPDATE salem_pois SET
              is_tour_poi = true,
              historical_period = $2,
              admission_info = $3,
              requires_transportation = $4,
              wheelchair_accessible = $5,
              seasonal = $6,
              legacy_tour_category = $7,
              updated_at = NOW()
            WHERE id = $1
          `, [
            t.id,
            t.historical_period,
            t.admission_info,
            t.requires_transportation,
            t.wheelchair_accessible,
            t.seasonal,
            t.category,
          ]);
        }
        updatedTourById++;
        continue;
      }

      // Try name match within 200m (in-memory lookup)
      const nameKey = t.name.trim().toLowerCase();
      const nameCandidate = insertedNames.get(nameKey);
      let nameMatchId = null;
      let nameMatchDist = Infinity;
      if (nameCandidate) {
        // Haversine distance check
        const toRad = d => d * Math.PI / 180;
        const R = 6371000;
        const dLat = toRad(nameCandidate.lat - t.lat);
        const dLng = toRad(nameCandidate.lng - t.lng);
        const a = Math.sin(dLat / 2) ** 2 +
                  Math.cos(toRad(t.lat)) * Math.cos(toRad(nameCandidate.lat)) *
                  Math.sin(dLng / 2) ** 2;
        nameMatchDist = R * 2 * Math.asin(Math.sqrt(a));
        if (nameMatchDist <= 200) {
          nameMatchId = nameCandidate.id;
        }
      }

      if (nameMatchId) {
        // Name + proximity match — update the existing row
        const matchId = nameMatchId;
        console.log(`    Name match: tour "${t.id}" → existing "${matchId}" (${Math.round(nameMatchDist)}m)`);
        if (!DRY_RUN) {
          await client.query(`
            UPDATE salem_pois SET
              is_tour_poi = true,
              historical_period = $2,
              admission_info = $3,
              requires_transportation = $4,
              wheelchair_accessible = $5,
              seasonal = $6,
              legacy_tour_category = $7,
              updated_at = NOW()
            WHERE id = $1
          `, [
            matchId,
            t.historical_period,
            t.admission_info,
            t.requires_transportation,
            t.wheelchair_accessible,
            t.seasonal,
            t.category,
          ]);
        }
        updatedTourByName++;
        continue;
      }

      // No match — insert as new tour POI
      const mappedCategory = mapTourCategory(t.category);
      if (!DRY_RUN) {
        await client.query(`
          INSERT INTO salem_pois (
            id, name, lat, lng, address,
            category,
            short_narration, long_narration, description,
            historical_period, admission_info,
            phone, website, hours_text,
            image_asset,
            geofence_radius_m,
            requires_transportation, wheelchair_accessible, seasonal,
            priority,
            data_source, confidence, verified_date,
            created_at, updated_at, stale_after, deleted_at,
            is_tour_poi, is_narrated, default_visible,
            legacy_tour_category
          ) VALUES (
            $1, $2, $3, $4, $5,
            $6,
            $7, $8, $9,
            $10, $11,
            $12, $13, $14,
            $15,
            $16,
            $17, $18, $19,
            $20,
            $21, $22, $23,
            $24, $25, $26, $27,
            true, false, true,
            $28
          )
        `, [
          t.id, t.name, t.lat, t.lng, t.address,
          mappedCategory,
          t.short_narration, t.long_narration, t.description,
          t.historical_period, t.admission_info,
          t.phone, t.website, t.hours,  // legacy hours → hours_text
          t.image_asset,
          t.geofence_radius_m,
          t.requires_transportation, t.wheelchair_accessible, t.seasonal,
          t.priority,
          t.data_source, t.confidence, t.verified_date,
          t.created_at, t.updated_at, t.stale_after, t.deleted_at,
          t.category,  // legacy_tour_category
        ]);
      }
      insertedTour++;
    }
    console.log(`  Updated by ID match: ${updatedTourById}`);
    console.log(`  Updated by name match: ${updatedTourByName}`);
    console.log(`  Inserted new tour-only rows: ${insertedTour}`);

    // ── Summary ─────────────────────────────────────────────────────────

    const totalInserted = insertedNarration + insertedBusiness + insertedTour;
    const totalTourPois = updatedTourById + updatedTourByName + insertedTour;

    console.log('\n=== MIGRATION SUMMARY ===');
    console.log(`Total rows in salem_pois: ${insertedIds.size} (${totalInserted} inserted, ${enriched} enriched, ${updatedTourById + updatedTourByName} tour-updated)`);
    console.log(`  is_narrated = true: ${insertedNarration}`);
    console.log(`  is_tour_poi = true: ${totalTourPois}`);

    if (!DRY_RUN) {
      // Verify from DB in live mode
      const { rows: [{ count: dbTotal }] } = await client.query(
        'SELECT COUNT(*) AS count FROM salem_pois'
      );
      const { rows: catCounts } = await client.query(
        'SELECT category, COUNT(*) AS c FROM salem_pois GROUP BY category ORDER BY c DESC'
      );
      console.log(`  DB count verification: ${dbTotal}`);
      console.log(`\nCategory distribution:`);
      for (const { category, c } of catCounts) {
        console.log(`  ${(category || '(null)').padEnd(22)} ${c}`);
      }

      // Sanity check
      if (parseInt(dbTotal) < narrationRows.length) {
        console.error(`\nERROR: Expected at least ${narrationRows.length} rows, got ${dbTotal}`);
        await client.query('ROLLBACK');
        process.exit(1);
      }
    }

    if (DRY_RUN) {
      console.log('\n=== DRY RUN — ROLLING BACK ===');
      await client.query('ROLLBACK');
    } else {
      await client.query('COMMIT');
      console.log('\n=== COMMITTED ===');
    }

  } catch (err) {
    console.error('Migration error:', err.message);
    console.error(err.stack);
    await client.query('ROLLBACK').catch(() => {});
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

run();
