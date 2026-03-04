#!/usr/bin/env node
/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module build-nces.js';

/**
 * build-nces.js — Download NCES public school locations and build SQLite database.
 *
 * Source: NCES EDGE ArcGIS MapServer (101,390 features)
 * URL: https://nces.ed.gov/opengis/rest/services/K12_School_Locations/EDGE_ADMINDATA_PUBLICSCH_2223/MapServer/0/query
 *
 * Usage: node build-nces.js
 * Output: nces-schools.db (SQLite)
 */

const Database = require('better-sqlite3');
const fs = require('fs');
const path = require('path');

const SERVICE_URL = 'https://nces.ed.gov/opengis/rest/services/K12_School_Locations/EDGE_ADMINDATA_PUBLICSCH_2223/MapServer/0/query';
const DB_PATH = path.join(__dirname, 'nces-schools.db');
const BATCH_SIZE = 1000;
const RADIUS_M = 300;

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

async function fetchFeatures() {
  console.log('Fetching school features from NCES ArcGIS...');
  const allFeatures = [];
  let offset = 0;
  let hasMore = true;

  while (hasMore) {
    const params = new URLSearchParams({
      where: '1=1',
      outFields: 'OBJECTID,NCESSCH,SCH_NAME,STABR,NMCNTY,GSLO,GSHI,SCHOOL_LEVEL,CHARTER_TEXT,TOTAL,LATCOD,LONCOD',
      f: 'json',
      resultOffset: offset.toString(),
      resultRecordCount: BATCH_SIZE.toString(),
    });

    const url = `${SERVICE_URL}?${params}`;
    console.log(`  Fetching offset=${offset}...`);
    const resp = await fetch(url);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}: ${resp.statusText}`);
    const data = await resp.json();

    if (!data.features || data.features.length === 0) {
      hasMore = false;
    } else {
      allFeatures.push(...data.features);
      offset += data.features.length;
      console.log(`    Got ${data.features.length} (total: ${allFeatures.length})`);
      if (data.features.length < BATCH_SIZE) hasMore = false;
    }
  }

  console.log(`Fetched ${allFeatures.length} features total.`);
  return allFeatures;
}

function buildDatabase(features) {
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
  let skipped = 0;

  const insertMany = db.transaction((feats) => {
    for (const f of feats) {
      const a = f.attributes || {};
      const lat = a.LATCOD;
      const lon = a.LONCOD;
      if (lat == null || lon == null || lat === 0 || lon === 0) { skipped++; continue; }

      const name = a.SCH_NAME || 'Public School';
      const zoneId = `nces_${a.NCESSCH || a.OBJECTID}`;

      // Build description
      const grades = (a.GSLO && a.GSHI) ? `Grades ${a.GSLO}–${a.GSHI}` : '';
      const level = a.SCHOOL_LEVEL || '';
      const descParts = [grades, level, a.NMCNTY, a.STABR].filter(Boolean);
      const description = descParts.join(' · ');

      const enrollment = (a.TOTAL != null && a.TOTAL >= 0) ? a.TOTAL : null;
      const metadata = JSON.stringify({
        grades: grades || null,
        state: a.STABR || '',
        county: a.NMCNTY || '',
        enrollment,
        schoolLevel: level,
        charter: a.CHARTER_TEXT || '',
        ncessch: a.NCESSCH || '',
      });

      const bbox = circleBbox(lat, lon, RADIUS_M);

      insertZone.run(
        zoneId, name, description, 'SCHOOL_ZONE', 1,
        'circle', lat, lon, RADIUS_M,
        metadata,
        bbox.latMin, bbox.latMax, bbox.lonMin, bbox.lonMax
      );
      inserted++;
    }
  });

  insertMany(features);

  // Write metadata
  const insertMeta = db.prepare('INSERT INTO db_meta (key, value) VALUES (?, ?)');
  const metaEntries = {
    id: 'nces-schools',
    name: 'US Public Schools',
    description: 'US K-12 public school locations from NCES EDGE. 300m school zone radius around each school.',
    version: '1',
    zone_type: 'SCHOOL_ZONE',
    zone_count: inserted.toString(),
    updated_at: new Date().toISOString().split('T')[0],
    source: 'NCES EDGE (nces.ed.gov)',
    license: 'Public Domain (US Government)'
  };
  for (const [k, v] of Object.entries(metaEntries)) {
    insertMeta.run(k, v);
  }

  db.close();
  console.log(`\nDatabase built: ${DB_PATH}`);
  console.log(`  Zones inserted: ${inserted}`);
  console.log(`  Skipped: ${skipped}`);
  const stat = fs.statSync(DB_PATH);
  console.log(`  File size: ${(stat.size / 1024 / 1024).toFixed(1)} MB`);

  // Update catalog.json zone count
  const catalogPath = path.join(__dirname, 'catalog.json');
  if (fs.existsSync(catalogPath)) {
    const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf-8'));
    const entry = catalog.find(e => e.id === 'nces-schools');
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
    const features = await fetchFeatures();
    buildDatabase(features);
  } catch (e) {
    console.error('FATAL:', e.message);
    process.exit(1);
  }
}

main();
