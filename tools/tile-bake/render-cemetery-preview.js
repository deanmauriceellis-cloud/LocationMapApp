#!/usr/bin/env node
// Close-up of Old Burying Point / Charter Street Cemetery area.
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const sharp = require('sharp');
const mbgl = require('@maplibre/maplibre-gl-native');

const vectorDb = new Database(path.join(__dirname, 'salem-vector.mbtiles'), { readonly: true });
const getVectorTile = vectorDb.prepare('SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?');
const style = JSON.parse(fs.readFileSync(path.join(__dirname, 'style-salem.json'), 'utf-8'));
style.sources.salem = { type: 'vector', tiles: ['asset://tile/{z}/{x}/{y}.pbf'], minzoom: 0, maxzoom: 16 };
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
        for (const fs_ of fontstack.split(',').map(s=>s.trim())) {
          const p = path.join(__dirname, 'fonts', fs_, `${range}.pbf`);
          if (fs.existsSync(p)) return callback(null, { data: fs.readFileSync(p) });
        }
        return callback(null, { data: Buffer.alloc(0) });
      }
    } catch (err) { return callback(err); }
  },
  ratio: 1,
});
map.load(style);

// Old Burying Point / Charter Street: 42.5207, -70.8913
// Howard Street Cemetery: 42.5237, -70.8953 (NE of downtown)
// Broad Street Cemetery: 42.5167, -70.8960
// Salem Common: 42.5232, -70.8910 (for reference)
const shots = [
  { name: 'cemetery-charter-z17', lon: -70.8920, lat: 42.5210, z: 17 },
  { name: 'cemetery-howard-z17',  lon: -70.8953, lat: 42.5237, z: 17 },
  { name: 'beach-willows-z15',    lon: -70.8650, lat: 42.5425, z: 15 },
];

(async () => {
  for (const s of shots) {
    await new Promise((resolve, reject) => {
      map.render({ zoom: s.z, center: [s.lon, s.lat], width: 1024, height: 1024 }, (err, buf) => {
        if (err) return reject(err);
        sharp(buf, { raw: { width: 1024, height: 1024, channels: 4 } })
          .png()
          .toFile(path.join(__dirname, `${s.name}.png`))
          .then(() => { console.log(`${s.name}.png`); resolve(); });
      });
    });
  }
  process.exit(0);
})().catch(e => { console.error(e); process.exit(1); });
