#!/usr/bin/env node
/*
 * S234 — parallel-bake worker. Variant of render-tiles.js that renders only
 * the (zoom, blockX, blockY) blocks where
 *
 *     (bx * 100003 + by) % NUM_WORKERS === WORKER_ID
 *
 * (100003 is prime, so the hash spreads adjacent blocks across workers
 * for an even tile-count split.)
 *
 * Each worker writes to its own mbtiles file at WORKER_OUTPUT. The parent
 * coordinator (bake-parallel.js) merges them at the end.
 *
 * Configurable via env:
 *   WORKER_ID           required, integer 0..NUM_WORKERS-1
 *   NUM_WORKERS         required, integer
 *   WORKER_OUTPUT       required, path to write mbtiles
 *   WORKER_QUALITY      optional, WebP quality (default 60). Use 'lossless'
 *                       for lossless mode (matches calibration script).
 *
 * The bbox + zoom range mirror render-tiles.js (full +10 mi at every zoom).
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const sharp = require('sharp');
const mbgl = require('@maplibre/maplibre-gl-native');

// ── Worker config from env ──────────────────────────────────────────────
const WORKER_ID = parseInt(process.env.WORKER_ID, 10);
const NUM_WORKERS = parseInt(process.env.NUM_WORKERS, 10);
const WORKER_OUTPUT = process.env.WORKER_OUTPUT;
const QUALITY_RAW = process.env.WORKER_QUALITY || '60';
const IS_LOSSLESS = QUALITY_RAW === 'lossless';
const QUALITY = IS_LOSSLESS ? 'lossless' : parseInt(QUALITY_RAW, 10);

if (!Number.isInteger(WORKER_ID) || !Number.isInteger(NUM_WORKERS) || !WORKER_OUTPUT) {
  console.error(`[worker] Missing env: WORKER_ID=${WORKER_ID} NUM_WORKERS=${NUM_WORKERS} WORKER_OUTPUT=${WORKER_OUTPUT}`);
  process.exit(1);
}

const TAG = `[w${WORKER_ID}/${NUM_WORKERS}]`;

// ── Bake config (mirrors render-tiles.js) ───────────────────────────────
const VECTOR_MBTILES = path.join(__dirname, 'salem-vector.mbtiles');
const STYLE_PATH = path.join(__dirname, 'style-salem.json');
const FONTS_DIR = path.join(__dirname, 'fonts');
const TILE_SIZE = 256;
const BLOCK_TILES = 4;
const BUFFER_TILES = 1;
const TOTAL_TILES = BLOCK_TILES + 2 * BUFFER_TILES;
const BLOCK_PX = TILE_SIZE * TOTAL_TILES;

const BBOX_PLUS_10MI = { north: 42.6899, south: 42.3301, west: -71.1547, east: -70.6383 };
const ZOOM_BBOXES = [
  { zoom: 14, bbox: BBOX_PLUS_10MI },
  { zoom: 15, bbox: BBOX_PLUS_10MI },
  { zoom: 16, bbox: BBOX_PLUS_10MI },
  { zoom: 17, bbox: BBOX_PLUS_10MI },
  { zoom: 18, bbox: BBOX_PLUS_10MI },
  { zoom: 19, bbox: BBOX_PLUS_10MI },
];

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
if (fs.existsSync(WORKER_OUTPUT)) fs.unlinkSync(WORKER_OUTPUT);
const outDb = new Database(WORKER_OUTPUT);
outDb.pragma('journal_mode = WAL');
outDb.exec(`
  CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT);
  CREATE TABLE tiles (
    zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER,
    tile_data BLOB, PRIMARY KEY (zoom_level, tile_column, tile_row)
  );
`);
const insertMeta = outDb.prepare('INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)');
insertMeta.run('name', `Salem Custom (worker ${WORKER_ID}/${NUM_WORKERS})`);
insertMeta.run('format', 'webp');
insertMeta.run('type', 'baselayer');
insertMeta.run('version', '1');
const insertTile = outDb.prepare('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)');

// ── Render loop ─────────────────────────────────────────────────────────
const webpOpts = IS_LOSSLESS ? { lossless: true, effort: 6 } : { quality: QUALITY, effort: 6 };

(async () => {
  // First pass: count this worker's assigned tiles for progress reporting.
  let myTileTarget = 0;
  for (const { zoom, bbox } of ZOOM_BBOXES) {
    const r = getTileRange(bbox, zoom);
    const bxMin = Math.floor(r.xMin / BLOCK_TILES);
    const bxMax = Math.floor(r.xMax / BLOCK_TILES);
    const byMin = Math.floor(r.yMin / BLOCK_TILES);
    const byMax = Math.floor(r.yMax / BLOCK_TILES);
    for (let bx = bxMin; bx <= bxMax; bx++) {
      for (let by = byMin; by <= byMax; by++) {
        if (((bx * 100003 + by) % NUM_WORKERS + NUM_WORKERS) % NUM_WORKERS !== WORKER_ID) continue;
        const xStart = bx * BLOCK_TILES;
        const yStart = by * BLOCK_TILES;
        for (let dy = 0; dy < BLOCK_TILES; dy++) {
          for (let dx = 0; dx < BLOCK_TILES; dx++) {
            const x = xStart + dx;
            const y = yStart + dy;
            if (x < r.xMin || x > r.xMax || y < r.yMin || y > r.yMax) continue;
            myTileTarget += 1;
          }
        }
      }
    }
  }
  console.log(`${TAG} START — assigned ${myTileTarget} tiles, output=${WORKER_OUTPUT}`);

  let done = 0;
  let bytes = 0;
  const t0 = Date.now();
  let lastProgressMs = t0;

  for (const { zoom, bbox } of ZOOM_BBOXES) {
    const r = getTileRange(bbox, zoom);
    const bxMin = Math.floor(r.xMin / BLOCK_TILES);
    const bxMax = Math.floor(r.xMax / BLOCK_TILES);
    const byMin = Math.floor(r.yMin / BLOCK_TILES);
    const byMax = Math.floor(r.yMax / BLOCK_TILES);

    let myZoomTiles = 0;
    for (let bx = bxMin; bx <= bxMax; bx++) {
      for (let by = byMin; by <= byMax; by++) {
        if (((bx * 100003 + by) % NUM_WORKERS + NUM_WORKERS) % NUM_WORKERS !== WORKER_ID) continue;
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
            done += 1;
            myZoomTiles += 1;
          }
        }
        // Progress line every ~10 seconds, or on each zoom transition.
        const now = Date.now();
        if (now - lastProgressMs >= 10_000) {
          const elapsed = (now - t0) / 1000;
          const rate = done / elapsed;
          const eta = (myTileTarget - done) / Math.max(rate, 1e-9);
          console.log(`${TAG} ${done}/${myTileTarget}  ${(bytes / 1024 / 1024).toFixed(1)} MB  ${rate.toFixed(1)} t/s  ETA ${eta.toFixed(0)}s`);
          lastProgressMs = now;
        }
      }
    }
    console.log(`${TAG} z${zoom} done: ${myZoomTiles} tiles`);
  }

  outDb.pragma('journal_mode = DELETE');
  outDb.exec('VACUUM');
  outDb.close();

  const stat = fs.statSync(WORKER_OUTPUT);
  const elapsed = (Date.now() - t0) / 1000;
  console.log(`${TAG} DONE — ${done} tiles, ${(stat.size / 1024 / 1024).toFixed(1)} MB, ${elapsed.toFixed(1)}s`);
  process.exit(0);
})().catch(err => { console.error(`${TAG} ERROR:`, err); process.exit(1); });
