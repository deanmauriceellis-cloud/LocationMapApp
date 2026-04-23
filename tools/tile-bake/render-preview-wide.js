#!/usr/bin/env node
// Wide preview — 2048x2048 view at a given zoom, centered on downtown.
// Shows the true feel of the style, not a single 256 tile.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const sharp = require('sharp');
const mbgl = require('@maplibre/maplibre-gl-native');

const VECTOR_MBTILES = path.join(__dirname, 'salem-vector.mbtiles');
const STYLE_PATH = path.join(__dirname, 'style-salem.json');
const FONTS_DIR = path.join(__dirname, 'fonts');

const vectorDb = new Database(VECTOR_MBTILES, { readonly: true });
const getVectorTile = vectorDb.prepare(
  'SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?'
);
const PARCELS_MBTILES = path.join(__dirname, 'parcels.mbtiles');
let getParcelTile = null;
if (fs.existsSync(PARCELS_MBTILES)) {
  const pdb = new Database(PARCELS_MBTILES, { readonly: true });
  getParcelTile = pdb.prepare('SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?');
}
const BUILDINGS_MBTILES = path.join(__dirname, 'buildings.mbtiles');
let getBuildingTile = null;
if (fs.existsSync(BUILDINGS_MBTILES)) {
  const bdb = new Database(BUILDINGS_MBTILES, { readonly: true });
  getBuildingTile = bdb.prepare('SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?');
}

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
        const fontstack = decodeURIComponent(m[1]);
        const range = m[2];
        const stacks = fontstack.split(',').map(s => s.trim());
        for (const fs_ of stacks) {
          const p = path.join(FONTS_DIR, fs_, `${range}.pbf`);
          if (fs.existsSync(p)) return callback(null, { data: fs.readFileSync(p) });
        }
        return callback(null, { data: Buffer.alloc(0) });
      }
      return callback(new Error('unhandled: ' + url));
    } catch (err) {
      return callback(err);
    }
  },
  ratio: 1,
});
map.load(style);

async function renderAt(z, lon, lat, outName, size = 1536) {
  return new Promise((resolve, reject) => {
    map.render({ zoom: z, center: [lon, lat], width: size, height: size }, (err, buf) => {
      if (err) return reject(err);
      sharp(buf, { raw: { width: size, height: size, channels: 4 } })
        .png()
        .toFile(path.join(__dirname, outName))
        .then(() => { console.log(outName); resolve(); });
    });
  });
}

(async () => {
  await renderAt(14, -70.896, 42.510, 'preview-z14-full-salem.png', 1024);
  await renderAt(16, -70.893, 42.522, 'preview-z16-downtown.png', 1536);
  await renderAt(17, -70.893, 42.522, 'preview-z17-downtown.png', 1536);
  await renderAt(18, -70.893, 42.522, 'preview-z18-common.png', 1536);
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
