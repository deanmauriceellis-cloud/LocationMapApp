#!/usr/bin/env node
/*
 * Merge a per-year historical MBTiles into salem_tiles.sqlite as a new
 * provider ("Historical-YYYY"). Parametrized clone of
 * tools/tile-bake/merge-into-bundle.js (S171 modern-basemap merger) — reuses
 * the same osmdroidKey() encoding + TMS→XYZ flip semantics so the runtime
 * TileArchive can read these rows by year-keyed provider name.
 *
 * Usage:
 *   node tools/historical-maps/merge-historical-into-bundle.js \
 *        --year 1851 \
 *        --source-mbtiles /mnt/sdb-images/HistoricalMapsSample/poc_1851/mcintyre_1851.mbtiles \
 *        [--bundle tools/tile-bake/dist/salem_tiles.sqlite]
 *
 * Idempotent: existing rows with provider='Historical-YYYY' are deleted
 * before insert, so re-runs replace cleanly.
 */

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

function parseArgs(argv) {
  const out = {};
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--year') out.year = argv[++i];
    else if (a === '--source-mbtiles') out.source = argv[++i];
    else if (a === '--bundle') out.bundle = argv[++i];
    else if (a === '--help' || a === '-h') {
      console.log('Usage: --year YYYY --source-mbtiles <path> [--bundle <path>]');
      process.exit(0);
    } else {
      console.error('unknown arg: ' + a);
      process.exit(2);
    }
  }
  return out;
}

const args = parseArgs(process.argv);
if (!args.year || !/^[0-9]{4}$/.test(args.year)) {
  console.error('missing/invalid --year YYYY'); process.exit(2);
}
if (!args.source) {
  console.error('missing --source-mbtiles <path>'); process.exit(2);
}

const SOURCE = path.resolve(args.source);
const BUNDLE = path.resolve(
  args.bundle || path.join(__dirname, '../tile-bake/dist/salem_tiles.sqlite')
);
const PROVIDER = `Historical-${args.year}`;

if (!fs.existsSync(SOURCE)) { console.error('missing ' + SOURCE); process.exit(1); }
if (!fs.existsSync(BUNDLE)) { console.error('missing ' + BUNDLE); process.exit(1); }

function osmdroidKey(z, x, y) {
  // Matches MercatorMath.osmdroidKey() — Java long arithmetic.
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

const deleted = dst.prepare('DELETE FROM tiles WHERE provider = ?').run(PROVIDER);
if (deleted.changes > 0) console.log(`\nRemoved ${deleted.changes} existing ${PROVIDER} rows.`);

const insert = dst.prepare('INSERT INTO tiles (key, provider, tile) VALUES (?, ?, ?)');
const srcTiles = src.prepare(
  'SELECT zoom_level AS z, tile_column AS x, tile_row AS tmsY, tile_data FROM tiles'
);

const tx = dst.transaction(() => {
  let n = 0;
  let bytes = 0;
  const zoomCounts = {};
  for (const row of srcTiles.iterate()) {
    const z = row.z, x = row.x, tmsY = row.tmsY;
    // MBTiles TMS → osmdroid/XYZ
    const y = (1 << z) - 1 - tmsY;
    const key = osmdroidKey(z, x, y);
    insert.run(key, PROVIDER, row.tile_data);
    n++;
    bytes += row.tile_data.length;
    zoomCounts[z] = (zoomCounts[z] || 0) + 1;
  }
  return { n, bytes, zoomCounts };
});

const t0 = Date.now();
const result = tx();
const elapsed = (Date.now() - t0) / 1000;

console.log(
  `\nInserted ${result.n} tiles (${(result.bytes / 1024 / 1024).toFixed(1)} MB raw) ` +
  `as provider=${PROVIDER} in ${elapsed.toFixed(1)}s`
);
console.log('Zoom-level breakdown:');
Object.keys(result.zoomCounts).sort((a, b) => +a - +b).forEach(z => {
  console.log(`  z${z}: ${result.zoomCounts[z]} tiles`);
});

dst.pragma('journal_mode = DELETE');
dst.exec('VACUUM');

const afterCounts = dst.prepare('SELECT provider, COUNT(*) AS n FROM tiles GROUP BY provider').all();
console.log('\nAfter merge:');
afterCounts.forEach(r => console.log(`  ${r.provider}: ${r.n} tiles`));

dst.close();
src.close();

const afterSize = fs.statSync(BUNDLE).size;
console.log(
  `\nFile size: ${(beforeSize / 1024 / 1024).toFixed(1)} MB → ` +
  `${(afterSize / 1024 / 1024).toFixed(1)} MB ` +
  `(+${((afterSize - beforeSize) / 1024 / 1024).toFixed(1)} MB)`
);
