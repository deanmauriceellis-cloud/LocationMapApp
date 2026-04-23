#!/usr/bin/env node
// Extract a few sample tiles from the merged bundle at representative zooms.
const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

const BUNDLE = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_tiles.sqlite');
const db = new Database(BUNDLE, { readonly: true });

function osmdroidKey(z, x, y) {
  return (BigInt(z) << BigInt(z + z)) | (BigInt(x) << BigInt(z)) | BigInt(y);
}

function lonLatToTile(lon, lat, z) {
  const n = 2 ** z;
  const x = Math.floor(((lon + 180) / 360) * n);
  const latRad = (lat * Math.PI) / 180;
  const y = Math.floor(((1 - Math.log(Math.tan(latRad) + 1 / Math.cos(latRad)) / Math.PI) / 2) * n);
  return [x, y];
}

// Salem Common center: 42.522, -70.893
// House of Seven Gables: 42.5215, -70.8839
// Pickering Wharf: 42.5185, -70.8862

const samples = [
  { name: 'z15-wide-salem',  z: 15, lon: -70.893, lat: 42.522 },
  { name: 'z17-downtown',    z: 17, lon: -70.893, lat: 42.522 },
  { name: 'z18-seven-gables', z: 18, lon: -70.8839, lat: 42.5215 },
  { name: 'z19-common',      z: 19, lon: -70.893,  lat: 42.522 },
];

const stmt = db.prepare('SELECT tile FROM tiles WHERE key = ? AND provider = ?');

samples.forEach(s => {
  const [x, y] = lonLatToTile(s.lon, s.lat, s.z);
  const key = osmdroidKey(s.z, x, y);
  const row = stmt.get(key.toString(), 'Salem-Custom');
  if (!row) {
    console.log(`MISS ${s.name} key=${key} z=${s.z} x=${x} y=${y}`);
    return;
  }
  const out = path.join(__dirname, `sample-${s.name}.png`);
  fs.writeFileSync(out, row.tile);
  console.log(`${s.name} -> ${out} (${row.tile.length} bytes, z=${s.z} x=${x} y=${y})`);
});

db.close();
