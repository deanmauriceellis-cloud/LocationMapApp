#!/usr/bin/env node
/**
 * Populate hero images into the LMA pipeline.
 *
 * 1. Copies generated WebP images to app-salem/src/main/assets/hero/
 * 2. Updates PG salem_pois.image_asset for each entity with a generated image
 * 3. Run publish-salem-pois.js after this to push PG → bundled SQLite
 *
 * Usage:
 *   node populate-db.js [--dry-run]
 *
 * Matches generated images (named by SalemIntelligence entity_id) to
 * LMA POIs via the intel_entity_id column in salem_pois.
 */

const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

const LMA_ROOT = path.resolve(__dirname, '../..');
const APP_ASSETS = path.join(LMA_ROOT, 'app-salem/src/main/assets/hero');
const IMAGE_SOURCE = path.join(__dirname, 'output/app');

const dryRun = process.argv.includes('--dry-run');

async function main() {
  // 1. Find all generated images
  const imageFiles = fs.readdirSync(IMAGE_SOURCE)
    .filter(f => f.endsWith('.webp'));

  console.log(`Found ${imageFiles.length} generated hero images`);

  if (imageFiles.length === 0) {
    console.log('No images to process. Run generate.py first.');
    process.exit(0);
  }

  // 2. Create assets/hero/ directory
  if (!dryRun) {
    fs.mkdirSync(APP_ASSETS, { recursive: true });
  }

  // 3. Copy images to app assets
  let copied = 0;
  for (const file of imageFiles) {
    const src = path.join(IMAGE_SOURCE, file);
    const dst = path.join(APP_ASSETS, file);
    if (!dryRun) {
      fs.copyFileSync(src, dst);
    }
    copied++;
  }
  console.log(`${dryRun ? '[DRY RUN] Would copy' : 'Copied'} ${copied} images to ${APP_ASSETS}`);

  // 4. Calculate total size
  const totalBytes = imageFiles.reduce((sum, f) => {
    return sum + fs.statSync(path.join(IMAGE_SOURCE, f)).size;
  }, 0);
  console.log(`Total hero image size: ${(totalBytes / 1024 / 1024).toFixed(1)} MB`);

  // 5. Update PG — set image_asset for POIs that have a matching intel_entity_id
  const pool = new Pool({
    connectionString: process.env.DATABASE_URL || 'postgresql://localhost:5432/locationmapapp'
  });

  try {
    const client = await pool.connect();

    // Build a map: entity_id → filename
    const entityIds = imageFiles.map(f => f.replace('.webp', ''));

    // Find matching POIs
    const { rows: matches } = await client.query(
      `SELECT id, name, intel_entity_id, image_asset
       FROM salem_pois
       WHERE intel_entity_id = ANY($1::text[])
         AND deleted_at IS NULL`,
      [entityIds]
    );

    console.log(`\nMatched ${matches.length} POIs (of ${entityIds.length} images)`);

    let updated = 0;
    let skipped = 0;

    for (const poi of matches) {
      const assetPath = `hero/${poi.intel_entity_id}.webp`;

      if (poi.image_asset === assetPath) {
        skipped++;
        continue;
      }

      if (!dryRun) {
        await client.query(
          `UPDATE salem_pois SET image_asset = $1 WHERE id = $2`,
          [assetPath, poi.id]
        );
      }
      updated++;
    }

    console.log(`${dryRun ? '[DRY RUN] Would update' : 'Updated'} ${updated} POIs with hero image paths`);
    if (skipped > 0) {
      console.log(`Skipped ${skipped} POIs (already had correct image_asset)`);
    }

    // Also check for POIs without intel_entity_id that might match by name
    const unmatchedImages = entityIds.length - matches.length;
    if (unmatchedImages > 0) {
      console.log(`\n${unmatchedImages} images have no matching intel_entity_id in salem_pois`);
      console.log('(These are SalemIntelligence entities not yet imported into LMA)');
    }

    // Summary
    const { rows: stats } = await client.query(
      `SELECT
         count(*) as total,
         count(image_asset) as with_image,
         count(*) - count(image_asset) as without_image
       FROM salem_pois WHERE deleted_at IS NULL`
    );
    console.log(`\nPOI image coverage:`);
    console.log(`  Total POIs: ${stats[0].total}`);
    console.log(`  With hero image: ${stats[0].with_image}`);
    console.log(`  Without: ${stats[0].without_image}`);

    client.release();
  } catch (err) {
    console.error('Database error:', err.message);
    console.error('Make sure DATABASE_URL is set or cache-proxy .env is loaded');
    process.exit(1);
  } finally {
    await pool.end();
  }

  console.log(`\n${'='.repeat(60)}`);
  console.log('NEXT STEPS:');
  console.log('  1. cd ../../cache-proxy && node scripts/publish-salem-pois.js');
  console.log('  2. Build APK: ./gradlew :app-salem:assembleDebug');
  console.log(`${'='.repeat(60)}`);
}

main().catch(err => {
  console.error('Fatal:', err);
  process.exit(1);
});
