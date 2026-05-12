/*
 * S231 — splash-tree admin endpoint.
 *
 * Routes (Basic Auth):
 *   GET  /admin/splash/tree    — current tree as JSON
 *   PUT  /admin/splash/tree    — save tree (validated)
 *   POST /admin/splash/test    — { lat, lon, speed_mps?, bearing_deg? } →
 *                                 { context, bucket_id, eligible_variants[] }
 *
 * Storage: cache-proxy/data/splash-tree-v1.json (single file). Atomic writes
 * via temp+rename. Concurrent edits are rare in this single-operator tool;
 * if collisions become a thing we add an If-Match etag.
 *
 * The /test endpoint is what powers the live preview pane in the editor:
 * operator types coords + speed + bearing, server resolves which bucket
 * fires + lists every variant whose template would speak (slot-fill simulated
 * at the polygon-resolver level).
 */
const MODULE_ID = '(C) Destructive AI Gurus, LLC, 2026 - Module admin-splash-tree.js';
void MODULE_ID;

const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');
const { validateTree, SLOT_NAMES, MOVEMENT_VALUES, PLACE_KIND_VALUES } = require('./splash-tree-types');

const DATA_PATH = path.resolve(__dirname, '..', 'data', 'splash-tree-v1.json');

// Salem common (downtown anchor).
const SALEM_LAT = 42.5224;
const SALEM_LON = -70.8961;

// Tiger DB pool (peer auth via Unix socket). Lazy: only allocated if the
// /admin/splash/test endpoint is actually called. Used for accurate
// ST_Contains lookups in the editor preview — bbox-only matching produced
// wrong results in places where adjacent town bboxes overlap (Salem common
// matched Beverly's bbox before Salem's).
let tigerPool = null;
function getTigerPool() {
  if (tigerPool === null) {
    tigerPool = new Pool({
      database: 'tiger',
      host: '/var/run/postgresql',
      max: 4,
    });
    tigerPool.on('error', (err) => console.error('[admin-splash-tree] tiger pool error:', err));
  }
  return tigerPool;
}

// Names from bake-place-polygons.js — keep in sync.
const SALEM_ADJACENT_NAMES = [
  'Salem',
  'Beverly', 'Danvers', 'Lynn', 'Marblehead', 'Peabody', 'Swampscott',
  'Manchester-by-the-Sea', 'Wenham', 'Topsfield', 'Saugus',
];

const TOP_100_LIMIT = 102; // matches bake-script row count after name fix-ups

function readTree() {
  if (!fs.existsSync(DATA_PATH)) {
    throw new Error('splash-tree-v1.json not found — re-seed from cache-proxy/data/');
  }
  const raw = fs.readFileSync(DATA_PATH, 'utf8');
  return JSON.parse(raw);
}

function writeTreeAtomic(tree) {
  const tmp = DATA_PATH + '.tmp.' + process.pid;
  fs.writeFileSync(tmp, JSON.stringify(tree, null, 2) + '\n', 'utf8');
  fs.renameSync(tmp, DATA_PATH);
}

// ─── Geo helpers ──────────────────────────────────────────────────────────

function greatCircleMiles(lat1, lon1, lat2, lon2) {
  const R_MI = 3958.7613;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R_MI * Math.asin(Math.sqrt(a));
}

function bearingDeg(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const toDeg = (r) => (r * 180) / Math.PI;
  const φ1 = toRad(lat1), φ2 = toRad(lat2);
  const λ1 = toRad(lon1), λ2 = toRad(lon2);
  const y = Math.sin(λ2 - λ1) * Math.cos(φ2);
  const x = Math.cos(φ1) * Math.sin(φ2) - Math.sin(φ1) * Math.cos(φ2) * Math.cos(λ2 - λ1);
  const θ = Math.atan2(y, x);
  return (toDeg(θ) + 360) % 360;
}

function compassFromBearing(bearingFromSalem) {
  const dirs = ['N','NE','E','SE','S','SW','W','NW'];
  const idx = Math.round(bearingFromSalem / 45) % 8;
  return dirs[idx];
}

const COMPASS_LONG = {
  N: 'north', NE: 'northeast', E: 'east', SE: 'southeast',
  S: 'south', SW: 'southwest', W: 'west', NW: 'northwest',
};

function classifyMovement(bearingToSalem, headingDeg, speedMps) {
  if (speedMps == null || speedMps < 0.5) return 'STATIONARY';
  if (headingDeg == null) return 'UNKNOWN';
  const delta = Math.abs(((headingDeg - bearingToSalem + 540) % 360) - 180);
  // delta = 0 means heading exactly toward Salem; 180 = exactly away
  const towardness = 180 - delta;   // 180 = toward, 0 = away
  if (towardness >= 120) return 'APPROACHING';     // within ±60° of toward
  if (towardness <= 60) return 'DEPARTING';        // within ±60° of away
  return 'LATERAL';
}

const STATEFP_TO_POSTAL = {
  '01':'AL','02':'AK','04':'AZ','05':'AR','06':'CA','08':'CO','09':'CT','10':'DE',
  '11':'DC','12':'FL','13':'GA','15':'HI','16':'ID','17':'IL','18':'IN','19':'IA',
  '20':'KS','21':'KY','22':'LA','23':'ME','24':'MD','25':'MA','26':'MI','27':'MN',
  '28':'MS','29':'MO','30':'MT','31':'NE','32':'NV','33':'NH','34':'NJ','35':'NM',
  '36':'NY','37':'NC','38':'ND','39':'OH','40':'OK','41':'OR','42':'PA','44':'RI',
  '45':'SC','46':'SD','47':'TN','48':'TX','49':'UT','50':'VT','51':'VA','53':'WA',
  '54':'WV','55':'WI','56':'WY','60':'AS','66':'GU','69':'MP','72':'PR','78':'VI',
};

// Resolve (lat, lon) against the layered polygon set, using PostGIS ST_Contains
// for accuracy. Layer preference: CITY > TOWN > BUFFER > COUNTY.
//
// The Salem-adjacent town layer beats generic city buffers — operator's intent
// is "if you're in Beverly, say Beverly" even though Boston's 30-mile buffer
// also covers Beverly. CITY (you're literally inside the named place's
// polygon) beats everything. Counties are the always-last fallback.
async function resolvePlace(lat, lon) {
  const pool = getTigerPool();
  const point = `ST_SetSRID(ST_MakePoint($2, $1), 4269)`;  // (lon, lat)

  // ── Layer A (highest): SALEM-ADJACENT TOWN ───────────────────────────────
  // Checked FIRST so Salem / Beverly / Danvers / etc. resolve as adjacency
  // hits rather than as generic IN_CITY (Tiger lists them in both place and
  // cousub tables). The DANVERS bucket override depends on this firing.
  {
    const r = await pool.query({
      text: `
        SELECT name
        FROM tiger.s231_ma_cousub
        WHERE statefp = '25'
          AND name = ANY($3)
          AND ST_Contains(geom, ${point})
        LIMIT 1
      `,
      values: [lat, lon, SALEM_ADJACENT_NAMES],
    });
    if (r.rows.length) {
      return mkHit('IN_TOWN_ADJACENT_TO_SALEM', { name: r.rows[0].name, admin_name: 'MA' });
    }
  }

  // ── Layer B: CITY — top-100 city polygons ────────────────────────────────
  {
    const r = await pool.query({
      text: `
        SELECT p.name, st.stusps AS state_postal
        FROM tiger.s231_us_place p
        JOIN tiger.state st ON st.statefp = p.statefp
        WHERE ST_Contains(p.geom, ${point})
        LIMIT 1
      `,
      values: [lat, lon],
    });
    if (r.rows.length) {
      return mkHit('IN_CITY', { name: r.rows[0].name, admin_name: r.rows[0].state_postal });
    }
  }

  // ── Layer 2: NEAR_CITY (within 30-mile buffer of a top-100 city) ─────────
  // Buffer computed on-the-fly via ST_DWithin with geography typecast for
  // meter-accurate distance. Limited to the top-100 city universe by ranking
  // top-N most populous (fallback when no pop column: just filter by Place
  // FUNCSTAT='A' = active and rely on ST_DWithin).
  // 30 miles = 48280.32 meters.
  {
    const r = await pool.query({
      text: `
        SELECT p.name, st.stusps AS state_postal,
               ST_Distance(p.geom::geography, ${point}::geography) AS dist_m
        FROM tiger.s231_us_place p
        JOIN tiger.state st ON st.statefp = p.statefp
        WHERE p.funcstat = 'A'
          AND ST_DWithin(p.geom::geography, ${point}::geography, 48280.32)
        ORDER BY dist_m ASC
        LIMIT 1
      `,
      values: [lat, lon],
    });
    if (r.rows.length) {
      return mkHit('NEAR_CITY', { name: r.rows[0].name, admin_name: r.rows[0].state_postal });
    }
  }

  // ── Layer 4: COUNTY fallback ─────────────────────────────────────────────
  {
    const r = await pool.query({
      text: `
        SELECT c.name, st.stusps AS state_postal
        FROM tiger.s231_us_county c
        JOIN tiger.state st ON st.statefp = c.statefp
        WHERE ST_Contains(c.geom, ${point})
        LIMIT 1
      `,
      values: [lat, lon],
    });
    if (r.rows.length) {
      return mkHit('IN_COUNTY', { name: r.rows[0].name, admin_name: r.rows[0].state_postal });
    }
  }

  return { kind: 'OFFGRID' };
}

function mkHit(kind, row) {
  return { kind, name: row.name, admin_name: row.admin_name };
}

// Build the runtime LocationContext for a given test input.
async function buildContext({ lat, lon, speed_mps, bearing_deg }) {
  if (lat == null || lon == null) return null;

  const milesToSalem = greatCircleMiles(lat, lon, SALEM_LAT, SALEM_LON);
  const userToSalemBearing = bearingDeg(lat, lon, SALEM_LAT, SALEM_LON);
  const salemToUserBearing = bearingDeg(SALEM_LAT, SALEM_LON, lat, lon);
  const compass_short = compassFromBearing(salemToUserBearing);
  const movement = classifyMovement(userToSalemBearing, bearing_deg, speed_mps);
  const place = await resolvePlace(lat, lon);

  // {place} — best-resolved descriptor. "Beverly, MA" / "Boston, MA" /
  // "Wayne County, MI" / "out at sea". Used by templates that want to be
  // robust to whichever layer fired.
  let placeSlot = '';
  if (place && place.kind !== 'OFFGRID' && place.name) {
    if (place.kind === 'IN_COUNTY') {
      placeSlot = `${place.name} County, ${place.admin_name}`;
    } else {
      placeSlot = place.admin_name ? `${place.name}, ${place.admin_name}` : place.name;
    }
  }

  return {
    lat, lon,
    miles: Number(milesToSalem.toFixed(1)),
    miles_int: Math.round(milesToSalem),
    bearing_user_to_salem: Math.round(userToSalemBearing),
    compass: COMPASS_LONG[compass_short],
    compass_short,
    movement,
    place_kind: place ? place.kind : 'OFFGRID',
    place_name: place && place.name ? place.name : null,
    place_admin: place && place.admin_name ? place.admin_name : null,
    // Slot-named conveniences (only set when meaningful).
    city:        place && place.kind === 'IN_CITY'                  ? place.name : null,
    near_city:   place && place.kind === 'NEAR_CITY'                ? place.name : null,
    town:        place && place.kind === 'IN_TOWN_ADJACENT_TO_SALEM' ? place.name : null,
    county:      place && place.kind === 'IN_COUNTY'                ? `${place.name} County` : null,
    state:       place ? place.admin_name : null,
    state_long:  null,  // runtime fills via STATE_LONG map; preview leaves null
    place:       placeSlot || null,
  };
}

function fillSlots(template, ctx) {
  return template.replace(/\{([a-z_]+)\}/g, (_match, slot) => {
    if (ctx == null) return '';
    const v = ctx[slot];
    return v == null ? '' : String(v);
  });
}

// Match a Trigger against a LocationContext (or null = no GPS).
function triggerMatches(trigger, ctx) {
  if (!trigger) return false;
  if (trigger.no_gps === true) return ctx == null;
  if (ctx == null) return false;   // GPS-required trigger but no context
  if (trigger.distance_min_mi != null && ctx.miles < trigger.distance_min_mi) return false;
  if (trigger.distance_max_mi != null && ctx.miles > trigger.distance_max_mi) return false;
  if (trigger.movement && !trigger.movement.includes(ctx.movement)) return false;
  if (trigger.place_kind && !trigger.place_kind.includes(ctx.place_kind)) return false;
  if (trigger.town_name && ctx.town !== trigger.town_name) return false;
  return true;
}

function selectBucket(tree, ctx) {
  const sorted = [...(tree.buckets || [])].sort((a, b) => (b.priority || 0) - (a.priority || 0));
  for (const b of sorted) {
    if (triggerMatches(b.trigger, ctx)) return b;
  }
  return tree.fallback || null;
}

// ─── Express wiring ───────────────────────────────────────────────────────

module.exports = function(app, _deps) {
  app.get('/admin/splash/tree', (_req, res) => {
    try {
      res.json(readTree());
    } catch (err) {
      console.error('[admin-splash-tree] read failed:', err);
      res.status(500).json({ error: String(err.message || err) });
    }
  });

  app.put('/admin/splash/tree', (req, res) => {
    const body = req.body;
    if (!body || typeof body !== 'object') {
      return res.status(400).json({ error: 'body must be a JSON object' });
    }
    const v = validateTree(body);
    if (!v.ok) {
      return res.status(400).json({ error: 'validation failed', details: v.errors });
    }
    body.updated_at = new Date().toISOString();
    try {
      writeTreeAtomic(body);
      res.json({ ok: true, updated_at: body.updated_at });
    } catch (err) {
      console.error('[admin-splash-tree] write failed:', err);
      res.status(500).json({ error: String(err.message || err) });
    }
  });

  app.post('/admin/splash/test', async (req, res) => {
    const { lat, lon, speed_mps, bearing_deg } = req.body || {};
    let tree;
    try {
      tree = readTree();
    } catch (err) {
      return res.status(500).json({ error: String(err.message || err) });
    }
    let ctx = null;
    if (lat != null && lon != null) {
      try {
        ctx = await buildContext({ lat: Number(lat), lon: Number(lon), speed_mps, bearing_deg });
      } catch (err) {
        console.error('[admin-splash-tree] buildContext failed:', err);
        return res.status(500).json({ error: String(err.message || err) });
      }
    }
    const bucket = selectBucket(tree, ctx);
    const variants = bucket
      ? bucket.variants.map(v => ({
          id: v.id,
          template: v.text,
          rendered: fillSlots(v.text, ctx),
          weight: v.weight ?? 1,
        }))
      : [];
    res.json({
      context: ctx,
      bucket_id: bucket ? bucket.id : null,
      bucket_label: bucket ? bucket.label : null,
      eligible_variants: variants,
      slot_vocabulary: SLOT_NAMES,
      movement_values: MOVEMENT_VALUES,
      place_kind_values: PLACE_KIND_VALUES,
    });
  });
};
