#!/usr/bin/env node
// Extract OSM building polygons from salem-vector.mbtiles (z16 layer)
// and emit a GeoJSON FeatureCollection of unique buildings in Salem.
// Used by the next stage to spatial-join MHC points.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const Database = require('better-sqlite3');
const VectorTile = require('@mapbox/vector-tile').VectorTile;
const Pbf = require('pbf').default;

const VECTOR_MBTILES = path.join(__dirname, 'salem-vector.mbtiles');
const OUT = path.join(__dirname, 'osm-buildings-salem.geojsonl');

const db = new Database(VECTOR_MBTILES, { readonly: true });
const ZOOM = 16;
// At z16, extent 4096, convert local tile coords to WGS84
const rows = db.prepare('SELECT tile_column AS x, tile_row AS tmsY, tile_data FROM tiles WHERE zoom_level = ?').all(ZOOM);

function tileToLonLat(z, x, y) {
  const n = 2 ** z;
  const lon = (x / n) * 360 - 180;
  const lat = (Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / n))) * 180) / Math.PI;
  return [lon, lat];
}

// Convert local tile coordinates (0..extent) to WGS84.
function tileLocalToLonLat(z, tileX, tileY, localX, localY, extent) {
  const n = 2 ** z;
  const lon = ((tileX + localX / extent) / n) * 360 - 180;
  const yRatio = (tileY + localY / extent) / n;
  const lat = (Math.atan(Math.sinh(Math.PI * (1 - 2 * yRatio))) * 180) / Math.PI;
  return [lon, lat];
}

// Hash based on ring centroid (rounded) for cross-tile dedup.
function ringKey(ring) {
  let sx = 0, sy = 0;
  for (const [lon, lat] of ring) { sx += lon; sy += lat; }
  const cx = sx / ring.length;
  const cy = sy / ring.length;
  return `${cx.toFixed(6)},${cy.toFixed(6)},${ring.length}`;
}

const seen = new Set();
const out = fs.createWriteStream(OUT);
let count = 0;

for (const row of rows) {
  const tmsY = row.tmsY;
  const tileX = row.x;
  const tileY = (1 << ZOOM) - 1 - tmsY; // XYZ
  let buf = row.tile_data;
  try { buf = zlib.gunzipSync(buf); } catch (e) {}
  const vt = new VectorTile(new Pbf(buf));
  const layer = vt.layers.building;
  if (!layer) continue;
  const extent = layer.extent;
  for (let i = 0; i < layer.length; i++) {
    const f = layer.feature(i);
    // f.loadGeometry() gives local-tile rings; f.toGeoJSON(x,y,z) gives WGS84.
    const g = f.toGeoJSON(tileX, tileY, ZOOM);
    if (!g || !g.geometry) continue;
    if (g.geometry.type !== 'Polygon' && g.geometry.type !== 'MultiPolygon') continue;
    // Use first ring's key to dedup; tiles can contain the same building as fragments.
    const ring = g.geometry.type === 'Polygon' ? g.geometry.coordinates[0]
                                               : g.geometry.coordinates[0][0];
    const k = ringKey(ring);
    if (seen.has(k)) continue;
    seen.add(k);
    g.properties = { osm_bld_id: count };
    out.write(JSON.stringify(g) + '\n');
    count++;
  }
}
out.end();
console.log(`Extracted ${count} unique OSM building polygons to ${OUT}`);
