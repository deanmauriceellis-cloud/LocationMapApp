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
 * build-military.js — Download MIRTA/NTAD military base boundaries and build SQLite database.
 *
 * Source: ArcGIS NTAD Military Bases feature service (esriGeometryPolygon, ~824 features)
 * URL: https://services.arcgis.com/xOi1kZaI0eWDREZv/arcgis/rest/services/NTAD_Military_Bases/FeatureServer/0
 *
 * Usage: node build-military.js
 * Output: military-bases.db (SQLite)
 */

const Database = require('better-sqlite3');
const fs = require('fs');
const path = require('path');

const SERVICE_URL = 'https://services.arcgis.com/xOi1kZaI0eWDREZv/arcgis/rest/services/NTAD_Military_Bases/FeatureServer/0/query';
const DB_PATH = path.join(__dirname, 'military-bases.db');
const BATCH_SIZE = 200;

async function fetchFeatures() {
  console.log('Fetching military base features from ArcGIS...');
  const allFeatures = [];
  let offset = 0;
  let hasMore = true;

  while (hasMore) {
    const params = new URLSearchParams({
      where: '1=1',
      outFields: 'OBJECTID,featureName,featureDescription,siteName,siteOperationalStatus,siteReportingComponent,stateNameCode,isJointBase,countryName',
      f: 'geojson',
      resultOffset: offset.toString(),
      resultRecordCount: BATCH_SIZE.toString(),
      outSR: '4326'
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
      if (data.features.length < BATCH_SIZE) hasMore = false;
    }
  }

  console.log(`Fetched ${allFeatures.length} features total.`);
  return allFeatures;
}

function computeBbox(coordinates) {
  let latMin = 90, latMax = -90, lonMin = 180, lonMax = -180;
  // GeoJSON polygon: coordinates is [ [ring1], [ring2], ... ]
  // Each ring is [ [lon, lat], [lon, lat], ... ]
  function scan(coords) {
    if (typeof coords[0] === 'number') {
      // It's a point: [lon, lat]
      const [lon, lat] = coords;
      if (lat < latMin) latMin = lat;
      if (lat > latMax) latMax = lat;
      if (lon < lonMin) lonMin = lon;
      if (lon > lonMax) lonMax = lon;
    } else {
      for (const c of coords) scan(c);
    }
  }
  scan(coordinates);
  return { latMin, latMax, lonMin, lonMax };
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
                       geometry_type, geometry, center_lat, center_lon,
                       floor_alt_ft, ceil_alt_ft, metadata,
                       lat_min, lat_max, lon_min, lon_max)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);

  let inserted = 0;
  let skipped = 0;

  const insertMany = db.transaction((feats) => {
    for (const f of feats) {
      const props = f.properties || {};
      const geom = f.geometry;
      if (!geom || !geom.coordinates) { skipped++; continue; }

      const name = props.siteName || props.featureName || 'Military Installation';
      const zoneId = `mil_${props.OBJECTID || inserted}`;

      // Build description
      const descParts = [];
      if (props.featureDescription) descParts.push(props.featureDescription);
      if (props.siteReportingComponent) descParts.push(`Component: ${props.siteReportingComponent}`);
      if (props.stateNameCode) descParts.push(`State: ${props.stateNameCode}`);
      if (props.siteOperationalStatus) descParts.push(`Status: ${props.siteOperationalStatus}`);
      if (props.isJointBase === 'Yes') descParts.push('Joint Base');
      const description = descParts.join(' · ');

      const metadata = JSON.stringify({
        component: props.siteReportingComponent || '',
        state: props.stateNameCode || '',
        status: props.siteOperationalStatus || '',
        jointBase: props.isJointBase || '',
        country: props.countryName || ''
      });

      // Handle Polygon and MultiPolygon
      const polygons = geom.type === 'MultiPolygon' ? geom.coordinates : [geom.coordinates];

      for (let pi = 0; pi < polygons.length; pi++) {
        const polyCoords = polygons[pi];
        // Use outer ring only (first ring)
        const ring = polyCoords[0];
        if (!ring || ring.length < 3) { skipped++; continue; }

        const bbox = computeBbox(ring);
        const centerLat = (bbox.latMin + bbox.latMax) / 2;
        const centerLon = (bbox.lonMin + bbox.lonMax) / 2;

        // GeoJSON convention: [[lon, lat], ...]
        const geometryJson = JSON.stringify(ring);
        const partId = polygons.length > 1 ? `${zoneId}_p${pi}` : zoneId;

        insertZone.run(
          partId, name, description, 'MILITARY_BASE', 2, // WARNING severity
          'polygon', geometryJson, centerLat, centerLon,
          0, 99999, metadata,
          bbox.latMin, bbox.latMax, bbox.lonMin, bbox.lonMax
        );
        inserted++;
      }
    }
  });

  insertMany(features);

  // Write metadata
  const insertMeta = db.prepare('INSERT INTO db_meta (key, value) VALUES (?, ?)');
  const metaEntries = {
    id: 'military-bases',
    name: 'US Military Installations',
    description: 'US military bases, camps, forts, ranges, and installations from MIRTA/HIFLD datasets',
    version: '1',
    zone_type: 'MILITARY_BASE',
    zone_count: inserted.toString(),
    updated_at: new Date().toISOString().split('T')[0],
    source: 'HIFLD / NTAD Military Bases (ArcGIS)',
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
  console.log(`  File size: ${(stat.size / 1024).toFixed(1)} KB`);

  // Update catalog.json zone count
  const catalogPath = path.join(__dirname, 'catalog.json');
  if (fs.existsSync(catalogPath)) {
    const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf-8'));
    const entry = catalog.find(e => e.id === 'military-bases');
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
