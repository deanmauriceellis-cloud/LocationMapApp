#!/usr/bin/env node
// Stitch a 6x6 patch of tiles from salem-custom.mbtiles at z17 around downtown.
const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');
const sharp = require('sharp');

const db = new Database(path.join(__dirname, 'salem-custom.mbtiles'), { readonly: true });
const get = db.prepare('SELECT tile_data FROM tiles WHERE zoom_level=? AND tile_column=? AND tile_row=?');

function lonLatToTile(lon, lat, z) {
  const n = 2 ** z;
  const x = Math.floor(((lon + 180) / 360) * n);
  const lr = (lat * Math.PI) / 180;
  const y = Math.floor(((1 - Math.log(Math.tan(lr) + 1 / Math.cos(lr)) / Math.PI) / 2) * n);
  return [x, y];
}

(async () => {
  const z = 18;
  const [cx, cy] = lonLatToTile(-70.893, 42.522, z);
  const SPAN = 6;
  const composite = [];
  for (let dy = 0; dy < SPAN; dy++) {
    for (let dx = 0; dx < SPAN; dx++) {
      const x = cx + dx - SPAN / 2;
      const y = cy + dy - SPAN / 2;
      const tmsY = (1 << z) - 1 - y;
      const row = get.get(z, x, tmsY);
      if (!row) { console.log(`MISS z${z} ${x}/${y}`); continue; }
      composite.push({ input: row.tile_data, left: dx * 256, top: dy * 256 });
    }
  }
  await sharp({ create: { width: SPAN * 256, height: SPAN * 256, channels: 4, background: { r: 255, g: 0, b: 255, alpha: 1 } } })
    .composite(composite)
    .png()
    .toFile(path.join(__dirname, `stitch-z${z}-${SPAN}x${SPAN}.png`));
  console.log(`stitch-z${z}-${SPAN}x${SPAN}.png written`);
})();
