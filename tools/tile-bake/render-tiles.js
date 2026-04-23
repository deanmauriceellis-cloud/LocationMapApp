#!/usr/bin/env node
/*
 * Block-render raster PNG tiles from the Salem vector MBTiles + custom style.
 *
 * Renders BLOCK_TILES x BLOCK_TILES tile regions in a single MapLibre viewport,
 * then slices the output into individual tiles. This guarantees pixel-identical
 * seams: every tile within a block shares the same renderer context, so labels
 * are placed once and road/feature rendering is deterministic across tile
 * boundaries.
 *
 * Outputs salem-custom.mbtiles (z14-z18 full Salem + z19 downtown).
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const sharp = require('sharp');
const mbgl = require('@maplibre/maplibre-gl-native');

// ── Config ──────────────────────────────────────────────────────────────
const VECTOR_MBTILES = path.join(__dirname, 'salem-vector.mbtiles');
const STYLE_PATH = path.join(__dirname, 'style-salem.json');
const FONTS_DIR = path.join(__dirname, 'fonts');
const OUTPUT_MBTILES = path.join(__dirname, 'salem-custom.mbtiles');
const TILE_SIZE = 256;
const BLOCK_TILES = 4;                 // slice 4x4 tiles from each block
const BUFFER_TILES = 1;                // 1-tile buffer on each side for label context
const TOTAL_TILES = BLOCK_TILES + 2 * BUFFER_TILES; // render 6x6 = 1536px
const BLOCK_PX = TILE_SIZE * TOTAL_TILES;

const BBOX_FULL_SALEM = { north: 42.545, south: 42.475, west: -70.958, east: -70.835 };
const BBOX_DOWNTOWN = { north: 42.530, south: 42.508, west: -70.905, east: -70.876 };

const ZOOM_BBOXES = [
  { zoom: 14, bbox: BBOX_FULL_SALEM },
  { zoom: 15, bbox: BBOX_FULL_SALEM },
  { zoom: 16, bbox: BBOX_FULL_SALEM },
  { zoom: 17, bbox: BBOX_FULL_SALEM },
  { zoom: 18, bbox: BBOX_FULL_SALEM },
  { zoom: 19, bbox: BBOX_DOWNTOWN },
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

// ── Maplibre request handler ────────────────────────────────────────────
const style = JSON.parse(fs.readFileSync(STYLE_PATH, 'utf-8'));
style.sources.salem = {
  type: 'vector',
  tiles: ['asset://tile/{z}/{x}/{y}.pbf'],
  minzoom: 0,
  maxzoom: 16,
};
if (getParcelTile) {
  style.sources.parcels = {
    type: 'vector',
    tiles: ['asset://parcel/{z}/{x}/{y}.pbf'],
    minzoom: 0,
    maxzoom: 16,
  };
}
if (getBuildingTile) {
  style.sources.buildings = {
    type: 'vector',
    tiles: ['asset://building/{z}/{x}/{y}.pbf'],
    minzoom: 0,
    maxzoom: 16,
  };
}
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

// ── Helpers ─────────────────────────────────────────────────────────────
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

// Buffered block center = geographic midpoint of the BUFFERED region
// (i.e. the block PLUS the 1-tile buffer on each side).
// Block (bx, by) slices tiles x=[bx*N ... bx*N+N-1], y=[by*N ... by*N+N-1],
// but the render viewport covers [bx*N - B ... bx*N + N + B - 1] on each axis.
function blockCenter(z, blockX, blockY) {
  const xStart = blockX * BLOCK_TILES - BUFFER_TILES;
  const yStart = blockY * BLOCK_TILES - BUFFER_TILES;
  const [lon1] = tileToLonLat(z, xStart, yStart);
  const [lon2] = tileToLonLat(z, xStart + TOTAL_TILES, yStart);
  const [, lat1] = tileToLonLat(z, xStart, yStart);
  const [, lat2] = tileToLonLat(z, xStart, yStart + TOTAL_TILES);
  return [(lon1 + lon2) / 2, (lat1 + lat2) / 2];
}

// MapLibre's zoom <-> pixels relationship:
// at zoom z, a single OSM tile is 512 CSS pixels wide (MapLibre tile size),
// meaning a 512 viewport = 1 MapLibre tile = 1 OSM tile at z.
// Our osmdroid tiles are 256 pixels. At ratio=1, we render at MapLibre's 512
// logical size then downsample, BUT a simpler approach: render at zoom=(z-1)
// with 512*(BLOCK_TILES/2) pixels, because at zoom (z-1) a 256 OSM tile
// covers 128 logical pixels. Actually the cleanest: MapLibre's `size=N` at
// `zoom=z` fits `N/512` MapLibre-tiles. Since 1 MapLibre-tile = 1 OSM-tile at
// MapLibre's `tileSize`, and default tileSize=512, a BLOCK_PX wide viewport at
// zoom z fits BLOCK_PX/512 = BLOCK_TILES/2 OSM-tiles horizontally.
//
// The proven pattern for rendering OSM-style 256 tiles is:
//   renderZoom = z - 1   (because MapLibre internal tile=512 = 4x area of OSM 256)
//   viewportPx = BLOCK_PX   (to cover BLOCK_TILES OSM tiles)
// Then slice the output into 256-pixel squares.

function renderBlock(z, blockX, blockY) {
  return new Promise((resolve, reject) => {
    const [lon, lat] = blockCenter(z, blockX, blockY);
    // MapLibre tiles are 512 logical px, OSM tiles are 256 logical px, so
    // render at zoom (z-1): at that zoom, a 256-osm-tile covers 128 ml-px.
    // Correct approach: use MapLibre's zoom offset -- render at zoom - 1 with
    // size = (256 * BLOCK_TILES) pixels.
    map.render(
      {
        zoom: z - 1,
        center: [lon, lat],
        width: BLOCK_PX,
        height: BLOCK_PX,
      },
      (err, rgba) => {
        if (err) return reject(err);
        resolve(rgba);
      }
    );
  });
}

async function sliceBlock(rgba, z, blockX, blockY, insertTile) {
  const img = sharp(rgba, { raw: { width: BLOCK_PX, height: BLOCK_PX, channels: 4 } });
  const xStart = blockX * BLOCK_TILES;
  const yStart = blockY * BLOCK_TILES;
  let totalBytes = 0;
  let n = 0;
  for (let dy = 0; dy < BLOCK_TILES; dy++) {
    for (let dx = 0; dx < BLOCK_TILES; dx++) {
      const x = xStart + dx;
      const y = yStart + dy;
      const png = await sharp(rgba, { raw: { width: BLOCK_PX, height: BLOCK_PX, channels: 4 } })
        .extract({ left: dx * TILE_SIZE, top: dy * TILE_SIZE, width: TILE_SIZE, height: TILE_SIZE })
        .removeAlpha()
        .png({ compressionLevel: 9 })
        .toBuffer();
      const tmsY = (1 << z) - 1 - y;
      insertTile.run(z, x, tmsY, png);
      totalBytes += png.length;
      n++;
    }
  }
  return { n, bytes: totalBytes };
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
insertMeta.run('name', 'Salem Custom');
insertMeta.run('format', 'png');
insertMeta.run('type', 'baselayer');
insertMeta.run('version', '2');
insertMeta.run('description', 'Salem curated basemap (block-rendered for seamless tiles).');
insertMeta.run('attribution', '© OpenMapTiles © OpenStreetMap contributors');
insertMeta.run('bounds', `${BBOX_FULL_SALEM.west},${BBOX_FULL_SALEM.south},${BBOX_FULL_SALEM.east},${BBOX_FULL_SALEM.north}`);
insertMeta.run('minzoom', '14');
insertMeta.run('maxzoom', '19');
const insertTile = outDb.prepare('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)');

// ── Render loop ─────────────────────────────────────────────────────────
(async () => {
  let totalTiles = 0;
  for (const { zoom, bbox } of ZOOM_BBOXES) {
    const r = getTileRange(bbox, zoom);
    totalTiles += (r.xMax - r.xMin + 1) * (r.yMax - r.yMin + 1);
  }
  console.log(`Total tiles target: ${totalTiles}. Block size: ${BLOCK_TILES}x${BLOCK_TILES} = ${BLOCK_PX}x${BLOCK_PX}px.`);

  let done = 0;
  let bytes = 0;
  const t0 = Date.now();

  for (const { zoom, bbox } of ZOOM_BBOXES) {
    const r = getTileRange(bbox, zoom);
    // Block grid: round out to cover the full bbox, render excess, discard tiles outside range
    const bxMin = Math.floor(r.xMin / BLOCK_TILES);
    const bxMax = Math.floor(r.xMax / BLOCK_TILES);
    const byMin = Math.floor(r.yMin / BLOCK_TILES);
    const byMax = Math.floor(r.yMax / BLOCK_TILES);
    const blockCount = (bxMax - bxMin + 1) * (byMax - byMin + 1);
    console.log(`\nZoom ${zoom}: ${r.xMax - r.xMin + 1}x${r.yMax - r.yMin + 1} tiles, ${blockCount} blocks`);

    for (let bx = bxMin; bx <= bxMax; bx++) {
      for (let by = byMin; by <= byMax; by++) {
        const rgba = await renderBlock(zoom, bx, by);
        // Slice the inner BLOCK_TILES x BLOCK_TILES region (skip the buffer).
        const xStart = bx * BLOCK_TILES;
        const yStart = by * BLOCK_TILES;
        for (let dy = 0; dy < BLOCK_TILES; dy++) {
          for (let dx = 0; dx < BLOCK_TILES; dx++) {
            const x = xStart + dx;
            const y = yStart + dy;
            if (x < r.xMin || x > r.xMax || y < r.yMin || y > r.yMax) continue;
            const pxLeft = (dx + BUFFER_TILES) * TILE_SIZE;
            const pxTop = (dy + BUFFER_TILES) * TILE_SIZE;
            const png = await sharp(rgba, { raw: { width: BLOCK_PX, height: BLOCK_PX, channels: 4 } })
              .extract({ left: pxLeft, top: pxTop, width: TILE_SIZE, height: TILE_SIZE })
              .removeAlpha()
              .webp({ lossless: true, effort: 6 })
              .toBuffer();
            const tmsY = (1 << zoom) - 1 - y;
            insertTile.run(zoom, x, tmsY, png);
            bytes += png.length;
            done++;
          }
        }
        if (done % 200 === 0 || done === totalTiles) {
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
  console.log(`Done. ${OUTPUT_MBTILES}  ${(stat.size / 1024 / 1024).toFixed(1)} MB  (${done} tiles)`);
  process.exit(0);
})().catch(err => { console.error('\nRender error:', err); process.exit(1); });
