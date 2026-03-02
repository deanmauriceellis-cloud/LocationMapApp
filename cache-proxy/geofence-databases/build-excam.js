#!/usr/bin/env node
/**
 * build-excam.js — Download WzSabre speed/red-light camera data and build SQLite database.
 *
 * Source: https://wzsabre.rocks/cameras.download (XZ-compressed NDJSON)
 * Scale: ~109k cameras worldwide
 *
 * Usage: node build-excam.js
 * Output: excam-cameras.db (SQLite)
 */

const Database = require('better-sqlite3');
const lzma = require('lzma-native');
const fs = require('fs');
const path = require('path');

const DOWNLOAD_URL = 'https://wzsabre.rocks/cameras.download';
const DB_PATH = path.join(__dirname, 'excam-cameras.db');
const RADIUS_M = 200;

// Flag bitmask → camera type label
const FLAG_LABELS = {
  1: 'speed_camera',
  2: 'red_light_camera',
  3: 'speed_and_red_light',
  4: 'bus_lane_camera',
  5: 'toll_camera',
  9: 'average_speed_camera',
  13: 'mobile_speed_camera',
  32: 'distance_camera',
};

function flagToType(flg) {
  return FLAG_LABELS[flg] || `camera_type_${flg}`;
}

function circleBbox(lat, lon, radiusM) {
  const latDelta = radiusM / 111320;
  const lonDelta = radiusM / (111320 * Math.cos(lat * Math.PI / 180));
  return {
    latMin: lat - latDelta,
    latMax: lat + latDelta,
    lonMin: lon - lonDelta,
    lonMax: lon + lonDelta
  };
}

async function downloadAndDecompress() {
  console.log(`Downloading from ${DOWNLOAD_URL}...`);
  const resp = await fetch(DOWNLOAD_URL);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
  const compressed = Buffer.from(await resp.arrayBuffer());
  console.log(`  Downloaded ${(compressed.length / 1024 / 1024).toFixed(1)} MB compressed`);

  console.log('Decompressing XZ...');
  const decompressed = await new Promise((resolve, reject) => {
    lzma.decompress(compressed, (result, error) => {
      if (error) reject(error);
      else resolve(result);
    });
  });
  console.log(`  Decompressed to ${(decompressed.length / 1024 / 1024).toFixed(1)} MB`);
  return decompressed.toString('utf-8');
}

function parseCameras(text) {
  const lines = text.split('\n').filter(l => l.trim());
  const cameras = [];
  let skipped = 0;

  for (const line of lines) {
    try {
      const obj = JSON.parse(line);
      // Skip metadata line
      if (obj._meta) continue;
      if (obj.lat == null || obj.lon == null) { skipped++; continue; }
      cameras.push(obj);
    } catch {
      skipped++;
    }
  }

  console.log(`Parsed ${cameras.length} cameras (skipped ${skipped})`);
  return cameras;
}

function buildDatabase(cameras) {
  if (fs.existsSync(DB_PATH)) fs.unlinkSync(DB_PATH);
  const db = new Database(DB_PATH);

  db.exec(`
    CREATE TABLE db_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    CREATE TABLE zones (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      zone_id TEXT NOT NULL UNIQUE,
      name TEXT NOT NULL,
      description TEXT DEFAULT '',
      zone_type TEXT NOT NULL,
      severity INTEGER DEFAULT 1,
      geometry_type TEXT NOT NULL,
      geometry TEXT,
      center_lat REAL, center_lon REAL, radius_m REAL,
      floor_alt_ft INTEGER DEFAULT 0,
      ceil_alt_ft INTEGER DEFAULT 99999,
      speed_limit INTEGER,
      time_restrict TEXT,
      metadata TEXT DEFAULT '{}',
      lat_min REAL, lat_max REAL, lon_min REAL, lon_max REAL
    );
    CREATE INDEX idx_zones_bbox ON zones (lat_min, lat_max, lon_min, lon_max);
    CREATE INDEX idx_zones_zone_id ON zones (zone_id);
  `);

  const insertZone = db.prepare(`
    INSERT INTO zones (zone_id, name, description, zone_type, severity,
                       geometry_type, center_lat, center_lon, radius_m,
                       speed_limit, metadata,
                       lat_min, lat_max, lon_min, lon_max)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  let inserted = 0;

  const insertMany = db.transaction((cams) => {
    for (let i = 0; i < cams.length; i++) {
      const c = cams[i];
      const cameraType = flagToType(c.flg || 0);
      const zoneId = `excam_${i}`;
      const name = c.str || cameraType;
      const speedLimit = c.spd || null;
      const direction = Array.isArray(c.dir) ? c.dir.join(',') : (c.dir || '');
      const description = speedLimit ? `${cameraType} · ${speedLimit} km/h` : cameraType;

      const metadata = JSON.stringify({
        cameraType,
        maxspeed: speedLimit,
        direction,
        street: c.str || '',
      });

      const bbox = circleBbox(c.lat, c.lon, RADIUS_M);

      insertZone.run(
        zoneId, name, description, 'SPEED_CAMERA', 1,
        'circle', c.lat, c.lon, RADIUS_M,
        speedLimit, metadata,
        bbox.latMin, bbox.latMax, bbox.lonMin, bbox.lonMax
      );
      inserted++;
    }
  });

  insertMany(cameras);

  // Write metadata
  const insertMeta = db.prepare('INSERT INTO db_meta (key, value) VALUES (?, ?)');
  const metaEntries = {
    id: 'excam-cameras',
    name: 'Speed & Red-Light Cameras',
    description: 'Worldwide speed cameras, red-light cameras, and enforcement cameras from WzSabre/ExCam dataset',
    version: '1',
    zone_type: 'SPEED_CAMERA',
    zone_count: inserted.toString(),
    updated_at: new Date().toISOString().split('T')[0],
    source: 'WzSabre (wzsabre.rocks)',
    license: 'Community-contributed data'
  };
  for (const [k, v] of Object.entries(metaEntries)) {
    insertMeta.run(k, v);
  }

  db.close();
  console.log(`\nDatabase built: ${DB_PATH}`);
  console.log(`  Zones inserted: ${inserted}`);
  const stat = fs.statSync(DB_PATH);
  console.log(`  File size: ${(stat.size / 1024 / 1024).toFixed(1)} MB`);

  // Update catalog.json zone count
  const catalogPath = path.join(__dirname, 'catalog.json');
  if (fs.existsSync(catalogPath)) {
    const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf-8'));
    const entry = catalog.find(e => e.id === 'excam-cameras');
    if (entry) {
      entry.zoneCount = inserted;
      entry.fileSize = stat.size;
      entry.updatedAt = metaEntries.updated_at;
      fs.writeFileSync(catalogPath, JSON.stringify(catalog, null, 2) + '\n');
      console.log('  Updated catalog.json');
    }
  }
}

async function main() {
  try {
    const text = await downloadAndDecompress();
    const cameras = parseCameras(text);
    buildDatabase(cameras);
  } catch (e) {
    console.error('FATAL:', e.message);
    process.exit(1);
  }
}

main();
