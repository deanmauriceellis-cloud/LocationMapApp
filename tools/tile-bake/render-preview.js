#!/usr/bin/env node
/*
 * Preview render — a single 1024x1024 snapshot of downtown Salem
 * at z16 centered on the Common, to eyeball the style before full bake.
 */
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

const style = JSON.parse(fs.readFileSync(STYLE_PATH, 'utf-8'));
style.sources.salem = {
  type: 'vector',
  tiles: ['asset://tile/{z}/{x}/{y}.pbf'],
  minzoom: 0,
  maxzoom: 16,
};
style.glyphs = 'asset://fonts/{fontstack}/{range}.pbf';

const map = new mbgl.Map({
  request: (req, callback) => {
    const url = req.url;
    try {
      if (url.startsWith('asset://tile/')) {
        const m = url.match(/asset:\/\/tile\/(\d+)\/(\d+)\/(\d+)\.pbf/);
        const z = +m[1], x = +m[2], y = +m[3];
        const tmsY = (1 << z) - 1 - y;
        const row = getVectorTile.get(z, x, tmsY);
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

// Salem Common: 42.522, -70.893
map.render(
  { zoom: 16, center: [-70.893, 42.522], width: 1024, height: 1024 },
  (err, buffer) => {
    if (err) { console.error(err); process.exit(1); }
    sharp(buffer, { raw: { width: 1024, height: 1024, channels: 4 } })
      .png()
      .toFile(path.join(__dirname, 'preview-z16-common.png'))
      .then(() => {
        console.log('preview-z16-common.png written');
        process.exit(0);
      });
  }
);
