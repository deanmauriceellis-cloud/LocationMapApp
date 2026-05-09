#!/usr/bin/env node
/*
 * S234 calibration bake — measure exact bytes/tile at different WebP qualities
 * before committing to the full +10mi bbox expansion.
 *
 * Differences vs render-tiles.js:
 *  - Bbox restricted to BBOX_DOWNTOWN (~2.4 km × 2.6 km), zooms restricted to
 *    z17 + z18. Total ~1,280 tiles per run — small enough to sweep multiple
 *    qualities in a few minutes.
 *  - WEBP_QUALITY env var (default 80). Set to 'lossless' to mirror the
 *    current production setting.
 *  - Output path includes the quality so multiple runs don't overwrite each
 *    other: salem-calibration-q{Q}.mbtiles.
 *  - Per-zoom byte totals printed at end so we get a clean bytes/tile per
 *    zoom (not just bundle-wide average).
 *
 * Touch nothing else in the pipeline.
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const sharp = require('sharp');
const mbgl = require('@maplibre/maplibre-gl-native');

// ── Calibration config ──────────────────────────────────────────────────
const QUALITY_RAW = process.env.WEBP_QUALITY || '80';
const IS_LOSSLESS = QUALITY_RAW === 'lossless';
const QUALITY = IS_LOSSLESS ? 'lossless' : parseInt(QUALITY_RAW, 10);
if (!IS_LOSSLESS && (!Number.isFinite(QUALITY) || QUALITY < 1 || QUALITY > 100)) {
  console.error(`Invalid WEBP_QUALITY: ${QUALITY_RAW}`);
  process.exit(1);
}
const QUALITY_TAG = IS_LOSSLESS ? 'lossless' : `q${QUALITY}`;

const VECTOR_MBTILES = path.join(__dirname, 'salem-vector.mbtiles');
const STYLE_PATH = path.join(__dirname, 'style-salem.json');
const FONTS_DIR = path.join(__dirname, 'fonts');
const OUTPUT_MBTILES = path.join(__dirname, `salem-calibration-${QUALITY_TAG}.mbtiles`);
const TILE_SIZE = 256;
const BLOCK_TILES = 4;
const BUFFER_TILES = 1;
const TOTAL_TILES = BLOCK_TILES + 2 * BUFFER_TILES;
const BLOCK_PX = TILE_SIZE * TOTAL_TILES;

const BBOX_DOWNTOWN = { north: 42.530, south: 42.508, west: -70.905, east: -70.876 };

const ZOOM_BBOXES = [
  { zoom: 17, bbox: BBOX_DOWNTOWN },
  { zoom: 18, bbox: BBOX_DOWNTOWN },
];

console.log(`[calibration] WebP setting: ${QUALITY_TAG}`);
console.log(`[calibration] Output: ${OUTPUT_MBTILES}`);

// ── Open vector sources ─────────────────────────────────────────────────
const vectorDb = new Database(VECTOR_MBTILES, { readonly: true, fileMustExist: true });
const getVectorTile = vectorDb.prepare(
  'SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?'
);
const PARCELS_MBTILES = path.join(__dirname, 'parcels.mbtiles');
let getParcelTile = null;
if (fs.existsSync(PARCELS_MBTILES)) {
  const parcelsDb = new Database(PARCELS_MBTILES, { readonly: true });
  getParcelTile = parcelsDb.prepare(
    'SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?'
  );
}
const BUILDINGS_MBTILES = path.join(__dirname, 'buildings.mbtiles');
let getBuildingTile = null;
if (fs.existsSync(BUILDINGS_MBTILES)) {
  const bdb = new Database(BUILDINGS_MBTILES, { readonly: true });
  getBuildingTile = bdb.prepare(
    'SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?'
  );
}

// ── Maplibre style + request handler ────────────────────────────────────
const style = JSON.parse(fs.readFileSync(STYLE_PATH, 'utf-8'));
style.sources.salem = { type: 'vector', tiles: ['asset://tile/{z}/{x}/{y}.pbf'], minzoom: 0, maxzoom: 16 };
if (getParcelTile) style.sources.parcels = { type: 'vector', tiles: ['asset://parcel/{z}/{x}/{y}.pbf'], minzoom: 0, maxzoom: 16 };
if (getBuildingTile) style.sources.buildings = { type: 'vector', tiles: ['asset://building/{z}/{x}/{y}.pbf'], minzoom: 0, maxzoom: 16 };
style.glyphs = 'asset://fonts/{fontstack}/{range}.pbf';

const map = new mbgl.Map({
  request: (req, callback) => {
    const url = req.url;
    try {
      if (url.startsWith('asset://tile/') || url.startsWith('asset://parcel/') || url.startsWith('asset://building/')) {
        const m = url.match(/asset:\/\/(tile|parcel|building)\/(\d+)\/(\d+)\/(\d+)\.pbf/);
        if (!m) return callback(new Error('bad asset url: ' + url));
        const source = m[1];
        const z = +m[2], x = +m[3], y = +m[4];
        const tmsY = (1 << z) - 1 - y;
        const stmt = source === 'parcel' ? getParcelTile
                   : source === 'building' ? getBuildingTile
                   : getVectorTile;
        if (!stmt) return callback(null, { data: Buffer.alloc(0) });
        const row = stmt.get(z, x, tmsY);
        if (!row) return callback(null, { data: Buffer.alloc(0) });
        let data = row.tile_data;
        try { data = zlib.gunzipSync(data); } catch (e) {}
        return callback(null, { data });
      }
      if (url.startsWith('asset://fonts/')) {
        const m = url.match(/asset:\/\/fonts\/([^/]+)\/(\d+-\d+)\.pbf/);
        if (!m) return callback(new Error('bad font url: ' + url));
        const fontstack = decodeURIComponent(m[1]);
        const range = m[2];
        const stacks = fontstack.split(',').map(s => s.trim());
        for (const fs_ of stacks) {
          const p = path.join(FONTS_DIR, fs_, `${range}.pbf`);
          if (fs.existsSync(p)) return callback(null, { data: fs.readFileSync(p) });
        }
        return callback(null, { data: Buffer.alloc(0) });
      }
      return callback(new Error('unhandled url: ' + url));
    } catch (err) { return callback(err); }
  },
  ratio: 1,
});
map.load(style);

// ── Helpers (identical to render-tiles.js) ──────────────────────────────
function tileToLonLat(z, x, y) {
  const n = 2 ** z;
  const lon = (x / n) * 360 - 180;
  const lat = (Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / n))) * 180) / Math.PI;
  return [lon, lat];
}
function lonLatToTile(lon, lat, z) {
  const n = 2 ** z;
  const x = Math.floor(((lon + 180) / 360) * n);
  const latRad = (lat * Math.PI) / 180;
  const y = Math.floor(((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * n);
  return [x, y];
}
function getTileRange(bbox, z) {
  const [xMin, yMin] = lonLatToTile(bbox.west, bbox.north, z);
  const [xMax, yMax] = lonLatToTile(bbox.east, bbox.south, z);
  return { xMin: Math.min(xMin, xMax), xMax: Math.max(xMin, xMax), yMin: Math.min(yMin, yMax), yMax: Math.max(yMin, yMax) };
}
function blockCenter(z, blockX, blockY) {
  const xStart = blockX * BLOCK_TILES - BUFFER_TILES;
  const yStart = blockY * BLOCK_TILES - BUFFER_TILES;
  const [lon1] = tileToLonLat(z, xStart, yStart);
  const [lon2] = tileToLonLat(z, xStart + TOTAL_TILES, yStart);
  const [, lat1] = tileToLonLat(z, xStart, yStart);
  const [, lat2] = tileToLonLat(z, xStart, yStart + TOTAL_TILES);
  return [(lon1 + lon2) / 2, (lat1 + lat2) / 2];
}
function renderBlock(z, blockX, blockY) {
  return new Promise((resolve, reject) => {
    const [lon, lat] = blockCenter(z, blockX, blockY);
    map.render(
      { zoom: z - 1, center: [lon, lat], width: BLOCK_PX, height: BLOCK_PX },
      (err, rgba) => { if (err) return reject(err); resolve(rgba); }
    );
  });
}

// ── Output MBTiles ──────────────────────────────────────────────────────
if (fs.existsSync(OUTPUT_MBTILES)) fs.unlinkSync(OUTPUT_MBTILES);
const outDb = new Database(OUTPUT_MBTILES);
outDb.pragma('journal_mode = WAL');
outDb.exec(`
  CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT);
  CREATE TABLE tiles (
    zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER,
    tile_data BLOB, PRIMARY KEY (zoom_level, tile_column, tile_row)
  );
`);
const insertMeta = outDb.prepare('INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)');
insertMeta.run('name', `Salem Calibration ${QUALITY_TAG}`);
insertMeta.run('format', 'webp');
insertMeta.run('type', 'baselayer');
insertMeta.run('version', '1');
insertMeta.run('description', `S234 calibration bake — WebP ${QUALITY_TAG}`);
insertMeta.run('attribution', '© OpenMapTiles © OpenStreetMap contributors');
insertMeta.run('bounds', `${BBOX_DOWNTOWN.west},${BBOX_DOWNTOWN.south},${BBOX_DOWNTOWN.east},${BBOX_DOWNTOWN.north}`);
insertMeta.run('minzoom', '17');
insertMeta.run('maxzoom', '18');
const insertTile = outDb.prepare('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)');

// ── Render loop ─────────────────────────────────────────────────────────
const webpOpts = IS_LOSSLESS
  ? { lossless: true, effort: 6 }
  : { quality: QUALITY, effort: 6 };

(async () => {
  const perZoom = new Map(); // zoom -> { tiles, bytes }

  let totalTiles = 0;
  for (const { zoom, bbox } of ZOOM_BBOXES) {
    const r = getTileRange(bbox, zoom);
    totalTiles += (r.xMax - r.xMin + 1) * (r.yMax - r.yMin + 1);
  }
  console.log(`[calibration] Total tiles target: ${totalTiles}`);

  let done = 0;
  let bytes = 0;
  const t0 = Date.now();

  for (const { zoom, bbox } of ZOOM_BBOXES) {
    const r = getTileRange(bbox, zoom);
    const bxMin = Math.floor(r.xMin / BLOCK_TILES);
    const bxMax = Math.floor(r.xMax / BLOCK_TILES);
    const byMin = Math.floor(r.yMin / BLOCK_TILES);
    const byMax = Math.floor(r.yMax / BLOCK_TILES);
    const blockCount = (bxMax - bxMin + 1) * (byMax - byMin + 1);
    console.log(`\n[calibration] Zoom ${zoom}: ${r.xMax - r.xMin + 1}x${r.yMax - r.yMin + 1} tiles, ${blockCount} blocks`);

    const zStat = { tiles: 0, bytes: 0 };
    perZoom.set(zoom, zStat);

    for (let bx = bxMin; bx <= bxMax; bx++) {
      for (let by = byMin; by <= byMax; by++) {
        const rgba = await renderBlock(zoom, bx, by);
        const xStart = bx * BLOCK_TILES;
        const yStart = by * BLOCK_TILES;
        for (let dy = 0; dy < BLOCK_TILES; dy++) {
          for (let dx = 0; dx < BLOCK_TILES; dx++) {
            const x = xStart + dx;
            const y = yStart + dy;
            if (x < r.xMin || x > r.xMax || y < r.yMin || y > r.yMax) continue;
            const pxLeft = (dx + BUFFER_TILES) * TILE_SIZE;
            const pxTop = (dy + BUFFER_TILES) * TILE_SIZE;
            const tile = await sharp(rgba, { raw: { width: BLOCK_PX, height: BLOCK_PX, channels: 4 } })
              .extract({ left: pxLeft, top: pxTop, width: TILE_SIZE, height: TILE_SIZE })
              .removeAlpha()
              .webp(webpOpts)
              .toBuffer();
            const tmsY = (1 << zoom) - 1 - y;
            insertTile.run(zoom, x, tmsY, tile);
            bytes += tile.length;
            zStat.tiles += 1;
            zStat.bytes += tile.length;
            done++;
          }
        }
        if (done % 100 === 0 || done === totalTiles) {
          const elapsed = (Date.now() - t0) / 1000;
          const rate = done / elapsed;
          const eta = (totalTiles - done) / rate;
          process.stdout.write(
            `\r  ${done}/${totalTiles}  ${(bytes / 1024 / 1024).toFixed(1)} MB  ${rate.toFixed(1)} tiles/s  ETA ${eta.toFixed(0)}s    `
          );
        }
      }
    }
  }

  console.log('\n\nFinalizing...');
  outDb.pragma('journal_mode = DELETE');
  outDb.exec('VACUUM');
  outDb.close();

  const stat = fs.statSync(OUTPUT_MBTILES);
  const elapsed = (Date.now() - t0) / 1000;

  console.log('\n=== CALIBRATION RESULTS ===');
  console.log(`WebP setting:   ${QUALITY_TAG}`);
  console.log(`Output file:    ${OUTPUT_MBTILES}`);
  console.log(`Total elapsed:  ${elapsed.toFixed(1)}s`);
  console.log(`Tiles rendered: ${done}`);
  console.log(`Total bytes:    ${(bytes / 1024 / 1024).toFixed(2)} MB`);
  console.log(`File size:      ${(stat.size / 1024 / 1024).toFixed(2)} MB`);
  console.log('\nPer-zoom breakdown:');
  for (const [z, s] of perZoom.entries()) {
    const avg = s.tiles ? s.bytes / s.tiles : 0;
    console.log(`  z${z}: ${s.tiles} tiles, ${(s.bytes / 1024).toFixed(1)} KB total, ${avg.toFixed(0)} bytes/tile avg`);
  }
  process.exit(0);
})().catch(err => { console.error('\nRender error:', err); process.exit(1); });
