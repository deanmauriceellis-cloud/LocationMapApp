#!/usr/bin/env node
/*
 * Merge salem-custom.mbtiles into app-salem/src/main/assets/salem_tiles.sqlite
 * as a third osmdroid provider ("Salem-Custom").
 *
 * Preserves all existing Esri-WorldImagery and Mapnik rows untouched.
 * Uses osmdroid's MapTileIndex key encoding:
 *     (((long) zoom) << (zoom + zoom)) | (((long) x) << zoom) | (long) y
 *
 * salem-custom.mbtiles uses TMS y (flipped); osmdroid uses XYZ y (unflipped).
 * We unflip at merge time.
 */

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

const SOURCE = path.join(__dirname, 'salem-custom.mbtiles');
const BUNDLE = path.resolve(__dirname, 'dist/salem_tiles.sqlite');
const PROVIDER = 'Salem-Custom';

if (!fs.existsSync(SOURCE)) {
  console.error('missing ' + SOURCE); process.exit(1);
}
if (!fs.existsSync(BUNDLE)) {
  console.error('missing ' + BUNDLE); process.exit(1);
}

function osmdroidKey(z, x, y) {
  // BigInt to match Java long arithmetic; SQLite INTEGER accepts 64-bit.
  return (BigInt(z) << BigInt(z + z)) | (BigInt(x) << BigInt(z)) | BigInt(y);
}

const src = new Database(SOURCE, { readonly: true });
const dst = new Database(BUNDLE);

dst.pragma('journal_mode = WAL');

const beforeCounts = dst.prepare('SELECT provider, COUNT(*) AS n FROM tiles GROUP BY provider').all();
console.log('Before merge:');
beforeCounts.forEach(r => console.log(`  ${r.provider}: ${r.n} tiles`));
const beforeSize = fs.statSync(BUNDLE).size;
console.log(`  file size: ${(beforeSize / 1024 / 1024).toFixed(1)} MB`);

// Drop any existing Salem-Custom rows for idempotency
const deleted = dst.prepare('DELETE FROM tiles WHERE provider = ?').run(PROVIDER);
if (deleted.changes > 0) console.log(`\nRemoved ${deleted.changes} existing ${PROVIDER} rows.`);

const insert = dst.prepare('INSERT INTO tiles (key, provider, tile) VALUES (?, ?, ?)');
const srcTiles = src.prepare('SELECT zoom_level AS z, tile_column AS x, tile_row AS tmsY, tile_data FROM tiles');

const tx = dst.transaction(() => {
  let n = 0;
  let bytes = 0;
  for (const row of srcTiles.iterate()) {
    const z = row.z, x = row.x, tmsY = row.tmsY;
    // MBTiles TMS -> XYZ
    const y = (1 << z) - 1 - tmsY;
    const key = osmdroidKey(z, x, y);
    insert.run(key, PROVIDER, row.tile_data);
    n++;
    bytes += row.tile_data.length;
  }
  return { n, bytes };
});

const t0 = Date.now();
const result = tx();
const elapsed = (Date.now() - t0) / 1000;

console.log(`\nInserted ${result.n} tiles (${(result.bytes / 1024 / 1024).toFixed(1)} MB raw) as provider=${PROVIDER} in ${elapsed.toFixed(1)}s`);

dst.pragma('journal_mode = DELETE');
dst.exec('VACUUM');

const afterCounts = dst.prepare('SELECT provider, COUNT(*) AS n FROM tiles GROUP BY provider').all();
console.log('\nAfter merge:');
afterCounts.forEach(r => console.log(`  ${r.provider}: ${r.n} tiles`));

dst.close();
src.close();

const afterSize = fs.statSync(BUNDLE).size;
console.log(`\nFile size: ${(beforeSize / 1024 / 1024).toFixed(1)} MB -> ${(afterSize / 1024 / 1024).toFixed(1)} MB (+${((afterSize - beforeSize) / 1024 / 1024).toFixed(1)} MB)`);

// Sanity: check zoom coverage per provider
console.log('\nZoom coverage per provider (sampled via key bit-decode is complex — skipping);');
console.log('osmdroid requires key to decode back to (z,x,y) — trust the insert counts.');
