#!/usr/bin/env node
/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

/**
 * build-dji-nofly.js — Download DJI no-fly zone database and build SQLite database.
 *
 * Source: https://raw.githubusercontent.com/MAVProxyUser/dji.nfzdb/master/dji.nfzdb.csv
 * Scale: ~7,800 zones globally (airports, restricted areas)
 *
 * Usage: node build-dji-nofly.js
 * Output: dji-nofly.db (SQLite)
 */

const Database = require('better-sqlite3');
const fs = require('fs');
const path = require('path');

const CSV_URL = 'https://raw.githubusercontent.com/MAVProxyUser/dji.nfzdb/master/dji.nfzdb.csv';
const DB_PATH = path.join(__dirname, 'dji-nofly.db');

// DJI zone type codes
const DJI_TYPES = {
  0: 'Airport',
  1: 'Restricted',
  2: 'Authorization',
  29: 'Enhanced Warning',
};

// DJI level codes
const DJI_LEVELS = {
  0: 'Warning',
  2: 'Authorization Required',
};

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

function parseCSV(text) {
  const lines = text.split('\n').filter(l => l.trim());
  if (lines.length < 2) throw new Error('CSV has no data rows');

  // Parse header
  const header = parseCSVLine(lines[0]);
  const records = [];
  let skipped = 0;

  for (let i = 1; i < lines.length; i++) {
    const fields = parseCSVLine(lines[i]);
    if (fields.length < header.length) { skipped++; continue; }

    const row = {};
    for (let j = 0; j < header.length; j++) {
      row[header[j]] = fields[j];
    }

    const lat = parseFloat(row.lat);
    const lon = parseFloat(row.lng);
    const radius = parseFloat(row.radius);

    if (isNaN(lat) || isNaN(lon) || lat === 0 || isNaN(radius) || radius <= 0) {
      skipped++;
      continue;
    }

    records.push({
      id: row.id,
      lat,
      lon,
      radius,
      name: row.name || '',
      city: row.city || '',
      country: row.country || '',
      type: parseInt(row.type) || 0,
      level: parseInt(row.level) || 0,
      warning: row.warning || '0',
      areaId: row.area_id || '',
    });
  }

  console.log(`Parsed ${records.length} zones (skipped ${skipped})`);
  return records;
}

// Simple CSV line parser that handles quoted fields
function parseCSVLine(line) {
  const fields = [];
  let current = '';
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && i + 1 < line.length && line[i + 1] === '"') {
        current += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === ',' && !inQuotes) {
      fields.push(current.trim());
      current = '';
    } else {
      current += ch;
    }
  }
  fields.push(current.trim());
  return fields;
}

async function downloadCSV() {
  console.log(`Downloading from ${CSV_URL}...`);
  const resp = await fetch(CSV_URL);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
  const text = await resp.text();
  console.log(`  Downloaded ${(text.length / 1024).toFixed(1)} KB`);
  return text;
}

function buildDatabase(records) {
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
                       metadata,
                       lat_min, lat_max, lon_min, lon_max)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  let inserted = 0;

  const insertMany = db.transaction((recs) => {
    for (const r of recs) {
      const zoneId = `dji_${r.id}`;
      const djiType = DJI_TYPES[r.type] || `Type ${r.type}`;
      const djiLevel = DJI_LEVELS[r.level] || `Level ${r.level}`;
      const name = r.name || `DJI ${djiType} Zone`;

      const descParts = [djiType, djiLevel];
      if (r.city) descParts.push(r.city);
      const description = descParts.join(' · ');

      const metadata = JSON.stringify({
        city: r.city,
        country: r.country,
        djiType,
        djiLevel,
        warning: r.warning,
        areaId: r.areaId,
      });

      const bbox = circleBbox(r.lat, r.lon, r.radius);

      insertZone.run(
        zoneId, name, description, 'NO_FLY_ZONE', 2,
        'circle', r.lat, r.lon, r.radius,
        metadata,
        bbox.latMin, bbox.latMax, bbox.lonMin, bbox.lonMax
      );
      inserted++;
    }
  });

  insertMany(records);

  // Write metadata
  const insertMeta = db.prepare('INSERT INTO db_meta (key, value) VALUES (?, ?)');
  const metaEntries = {
    id: 'dji-nofly',
    name: 'DJI No-Fly Zones',
    description: 'DJI drone no-fly zones — airports, restricted areas, and authorization zones worldwide',
    version: '1',
    zone_type: 'NO_FLY_ZONE',
    zone_count: inserted.toString(),
    updated_at: new Date().toISOString().split('T')[0],
    source: 'DJI NFZDB (GitHub/MAVProxyUser)',
    license: 'Community data (unofficial)'
  };
  for (const [k, v] of Object.entries(metaEntries)) {
    insertMeta.run(k, v);
  }

  db.close();
  console.log(`\nDatabase built: ${DB_PATH}`);
  console.log(`  Zones inserted: ${inserted}`);
  const stat = fs.statSync(DB_PATH);
  console.log(`  File size: ${(stat.size / 1024).toFixed(1)} KB`);

  // Update catalog.json zone count
  const catalogPath = path.join(__dirname, 'catalog.json');
  if (fs.existsSync(catalogPath)) {
    const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf-8'));
    const entry = catalog.find(e => e.id === 'dji-nofly');
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
    const text = await downloadCSV();
    const records = parseCSV(text);
    buildDatabase(records);
  } catch (e) {
    console.error('FATAL:', e.message);
    process.exit(1);
  }
}

main();
