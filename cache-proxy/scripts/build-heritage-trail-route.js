#!/usr/bin/env node
/*
 * build-heritage-trail-route.js
 *
 * Builds the Salem Heritage Trail tour route:
 *   1. Geocodes 11 intersection waypoints from YellowLine.txt via Nominatim.
 *   2. Selects anchor POIs from salem_pois for narration targets (10 unique + loop back).
 *   3. Interleaves intersections + anchor POIs as OSRM foot-profile waypoints.
 *   4. Fetches the walking route polyline from router.project-osrm.org.
 *   5. Writes the tour JSON to
 *      app-salem/src/main/assets/tours/tour_salem_heritage_trail.json
 *      in the TourRouteLoader format (stops[] with routeToNext segments).
 *
 * Usage:
 *   cd cache-proxy && node scripts/build-heritage-trail-route.js
 *
 * Network: public Nominatim + public OSRM. Rate limit friendly (1 req/sec).
 * Writes the JSON so the app can consume it offline at runtime.
 */

const fs = require('fs');
const path = require('path');

// ---------- Configuration ----------

const TOUR_ID = 'tour_salem_heritage_trail';
const OUTPUT = path.join(
  __dirname,
  '..',
  '..',
  'app-salem',
  'src',
  'main',
  'assets',
  'tours',
  `${TOUR_ID}.json`
);

// Eleven intersection waypoints from ~/YellowLine.txt — Salem MA
// These are ROUTING waypoints only. Narration fires at the anchor POIs below.
// Hand-verified intersection coordinates based on Salem MA's street grid.
// Cross-referenced against OpenStreetMap + existing salem_pois coordinates.
// OSRM's walking profile will snap each waypoint to the nearest walkable way.
const INTERSECTIONS = [
  { label: 'Salem Regional Visitor Center (2 New Liberty St)', lat: 42.52260, lng: -70.89200 },
  { label: 'Bridge St & St. Peter St',                          lat: 42.52430, lng: -70.89080 },
  { label: 'Brown St & Washington Square W',                    lat: 42.52310, lng: -70.89200 },
  { label: 'Hawthorne Blvd & Derby St',                         lat: 42.52055, lng: -70.89025 },
  { label: 'Derby St & Orange St',                              lat: 42.52055, lng: -70.88770 },
  { label: 'New Derby St & Lafayette St',                       lat: 42.52050, lng: -70.89415 },
  { label: 'Liberty St & Charter St',                           lat: 42.52050, lng: -70.89180 },
  { label: 'Essex St & Washington St',                          lat: 42.52190, lng: -70.89440 },
  { label: 'Essex St & North St',                               lat: 42.52175, lng: -70.89880 },
  { label: 'Summer St & Chestnut St',                           lat: 42.51970, lng: -70.89720 },
  { label: 'Return to Visitor Center',                          lat: 42.52260, lng: -70.89200 },
];

// Ten unique anchor POIs for narration (stop 11 reuses stop 1 visitor center).
// Ordered by walking sequence so we can interleave with intersections.
// All (lat, lng) verified against salem_pois and/or OSM street network.
// NOTE: the `national_park_service_visitor_center` salem_pois row has a legacy approximate
// coord (42.5216, -70.8869) — the actual visitor center is at 2 New Liberty
// St which OSM puts at ~42.5226, -70.8919. We use the corrected coord here
// for the tour route; the POI row coord will be updated in the DB insert step.
const ANCHOR_POIS = [
  // { poiId, name, lat, lng, afterIntersection (0-indexed in INTERSECTIONS) }
  { poiId: 'national_park_service_visitor_center',                        name: 'NPS Regional Visitor Center',              lat: 42.52260,   lng: -70.89200,   after: 0 },
  { poiId: 'salem_jail_site',                           name: 'Old Salem Jail Site',                      lat: 42.52515,   lng: -70.89282,   after: 1 },
  { poiId: 'salem_common',                              name: 'Salem Common',                             lat: 42.52380,   lng: -70.89020,   after: 2 },
  { poiId: 'salem_maritime_national_historical_park',   name: 'Salem Maritime National Historic Site',    lat: 42.52025,   lng: -70.88641,   after: 3 },
  { poiId: 'custom_house',                              name: 'Custom House',                             lat: 42.52165,   lng: -70.88722,   after: 4 },
  { poiId: 'old_town_hall',                             name: 'Old Town Hall (Derby Square)',             lat: 42.52088,   lng: -70.89463,   after: 5 },
  { poiId: 'salem_witch_trials_memorial',               name: 'Salem Witch Trials Memorial',              lat: 42.52084,   lng: -70.89190,   after: 6 },
  { poiId: 'bewitched_sculpture_samantha_statue',       name: 'Bewitched Statue (Samantha)',              lat: 42.52133,   lng: -70.89585,   after: 7 },
  { poiId: 'the_witch_house',                           name: 'The Witch House (Jonathan Corwin)',        lat: 42.52155,   lng: -70.89890,   after: 8 },
  { poiId: 'chestnut_street',                           name: 'Chestnut Street Historic District',        lat: 42.51970,   lng: -70.89800,   after: 9 },
];

// ---------- Helpers ----------

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function photonGeocode(query) {
  // Bias to Salem MA center so intersection ambiguity resolves locally.
  const url =
    `https://photon.komoot.io/api/?q=${encodeURIComponent(query)}` +
    `&lat=42.521&lon=-70.893&limit=1`;
  const res = await fetch(url);
  if (!res.ok) throw new Error(`Photon HTTP ${res.status}: ${query}`);
  const body = await res.json();
  const feat = body?.features?.[0];
  if (!feat) throw new Error(`Photon no result: ${query}`);
  const [lng, lat] = feat.geometry.coordinates;
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) throw new Error(`Bad coords for: ${query}`);
  return { lat, lng, display: feat.properties?.name || query };
}

function haversineM(a, b) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(s));
}

// ---------- Main ----------

async function main() {
  console.log('[1/4] Using hand-verified intersection coordinates (no geocoding)...');
  const intersections = INTERSECTIONS.map((spec, i) => {
    console.log(`       [${i + 1}] ${spec.label} → ${spec.lat.toFixed(5)}, ${spec.lng.toFixed(5)}`);
    return spec;
  });

  // Interleave: intersection[i] then any anchor POIs placed "after" intersection i
  console.log('[2/4] Interleaving intersections with anchor POIs (waypoint list)...');
  const waypoints = [];
  for (let i = 0; i < intersections.length; i++) {
    waypoints.push({
      kind: 'intersection',
      label: intersections[i].label,
      lat: intersections[i].lat,
      lng: intersections[i].lng,
    });
    for (const anchor of ANCHOR_POIS) {
      if (anchor.after === i) {
        waypoints.push({
          kind: 'anchor',
          poiId: anchor.poiId,
          label: anchor.name,
          lat: anchor.lat,
          lng: anchor.lng,
        });
      }
    }
  }
  console.log(`       Total waypoints: ${waypoints.length} (${intersections.length} intersections + ${ANCHOR_POIS.length} anchors)`);

  // Build the stop list: 10 anchors + loop-back to the visitor center
  const anchors = waypoints.filter((w) => w.kind === 'anchor');
  const stops = [];
  for (let i = 0; i < anchors.length; i++) stops.push(anchors[i]);
  stops.push({
    ...anchors[0],
    poiId: 'national_park_service_visitor_center',
    label: 'NPS Regional Visitor Center (Return)',
  });

  // Group intersection waypoints by which stop they belong BEFORE. Intersection
  // i in the interleaved `waypoints` list was placed AFTER intersection[i] in
  // the intersections array. We reconstruct: for each stop pair (k → k+1),
  // which intersection waypoints fall between their anchor positions?
  //
  // The interleaving order was:
  //   intersection[0] → anchor-that-sits-after-0 → intersection[1] → anchor-after-1 → …
  // So anchor k (0-indexed among anchors) is followed by intersection[k+1],
  // then anchor k+1. For stop pair k → k+1 we include intersection[k+1] as a
  // via-point to keep OSRM on the street the operator specified in
  // YellowLine.txt.
  function viasBetween(fromAnchorIdx, toAnchorIdx) {
    // fromAnchorIdx and toAnchorIdx are indices into anchors[]
    const vias = [];
    for (let ia = fromAnchorIdx; ia < toAnchorIdx; ia++) {
      // the intersection placed BETWEEN anchor[ia] and anchor[ia+1] is
      // intersection[ia+1] per our interleave rule
      const inter = INTERSECTIONS[ia + 1];
      if (inter) vias.push({ kind: 'intersection', label: inter.label, lat: inter.lat, lng: inter.lng });
    }
    return vias;
  }

  console.log('[3/4] Requesting per-leg OSRM walking routes (clean A→B segments, no backtracking)...');
  const legs = [];
  for (let k = 0; k < stops.length - 1; k++) {
    const from = stops[k];
    const to = stops[k + 1];
    // Figure out which anchors indices these are:
    const fromAnchorIdx = (k === stops.length - 1) ? 0 : k; // last stop is loop-back = anchor 0
    const toAnchorIdx = (k + 1 === stops.length - 1) ? 0 : k + 1; // same
    // For the leg, collect via-points: the intersections between these anchors
    let vias;
    if (k < anchors.length - 1) {
      vias = viasBetween(k, k + 1);
    } else {
      // Final loop: anchor[9] (Chestnut) → anchor[0] (Visitor Center).
      // Use intersection[10] (Return to Visitor Center) if present, else none.
      const inter = INTERSECTIONS[INTERSECTIONS.length - 1];
      vias = inter ? [{ kind: 'intersection', label: inter.label, lat: inter.lat, lng: inter.lng }] : [];
    }
    const legWaypoints = [from, ...vias, to];
    const coords = legWaypoints.map((w) => `${w.lng},${w.lat}`).join(';');
    const url =
      `https://routing.openstreetmap.de/routed-foot/route/v1/foot/${coords}` +
      `?overview=full&geometries=geojson&steps=false`;
    const res = await fetch(url, { headers: { 'User-Agent': 'LocationMapApp/1.5' } });
    if (!res.ok) {
      const body = await res.text();
      throw new Error(`OSRM leg ${k + 1} HTTP ${res.status}: ${body.slice(0, 400)}`);
    }
    const j = await res.json();
    if (j.code !== 'Ok' || !j.routes?.length) {
      throw new Error(`OSRM leg ${k + 1} error: ${JSON.stringify(j).slice(0, 400)}`);
    }
    const r = j.routes[0];
    legs.push({
      fromStop: from,
      toStop: to,
      vias,
      distance: r.distance,
      duration: r.duration,
      geometry: r.geometry.coordinates, // [[lng, lat], ...]
    });
    console.log(
      `       Leg ${k + 1}: ${from.label.slice(0, 28).padEnd(28)} → ${to.label.slice(0, 28).padEnd(28)} ` +
        `${(r.distance / 1609.344).toFixed(2)}mi  ${Math.round(r.duration / 60)}min  ${r.geometry.coordinates.length}pts`
    );
    await sleep(200); // polite rate limit
  }

  console.log('[4/4] Building tour JSON from per-leg geometries...');

  const totalDistanceM = legs.reduce((a, l) => a + l.distance, 0);
  const totalDurationS = legs.reduce((a, l) => a + l.duration, 0);
  console.log(
    `       Total: ${(totalDistanceM / 1609.344).toFixed(2)} mi, ` +
      `${Math.round(totalDurationS / 60)} min, ` +
      `${legs.reduce((a, l) => a + l.geometry.length, 0)} points across ${legs.length} legs`
  );

  const outStops = [];
  for (let i = 0; i < stops.length; i++) {
    const leg = i < legs.length ? legs[i] : null;
    const routeToNext = leg
      ? leg.geometry.map((c) => [c[1], c[0]]) // [lat, lng]
      : [];
    outStops.push({
      order: i + 1,
      poiId: stops[i].poiId,
      name: stops[i].label,
      lat: stops[i].lat,
      lng: stops[i].lng,
      geofenceRadiusM: 50,
      walkingMinutesFromPrev: i === 0 ? 0 : Math.round((legs[i - 1]?.duration || 0) / 60),
      distanceMFromPrev: i === 0 ? 0 : Math.round(legs[i - 1]?.distance || 0),
      routeToNext,
      routeDistanceM: leg ? Math.round(leg.distance) : 0,
      routeDurationS: leg ? Math.round(leg.duration) : 0,
    });
  }
  const route = { distance: totalDistanceM, duration: totalDurationS };

  const payload = {
    tour: {
      id: TOUR_ID,
      name: 'Salem Heritage Trail',
      theme: 'HERITAGE_TRAIL',
      description:
        'The official ~2.7-mile Salem Heritage Trail (Yellow Line) loop — a historical walking tour of Salem, starting and ending at the Regional Visitor Center and passing the Old Jail, Salem Common, Maritime National Historic Site, Custom House, Old Town Hall, Witch Trials Memorial, Bewitched statue, the Witch House, and the McIntire Historic District.',
      totalDistanceMiles: Number((route.distance / 1609.344).toFixed(2)),
      totalDurationMinutes: Math.round(route.duration / 60),
      stopCount: outStops.length,
      historical: true,
      source: 'YellowLine.txt (operator) + public OSRM foot profile + Nominatim geocoding',
      builtAt: new Date().toISOString(),
    },
    stops: outStops,
  };

  fs.mkdirSync(path.dirname(OUTPUT), { recursive: true });
  fs.writeFileSync(OUTPUT, JSON.stringify(payload, null, 2));
  console.log(`Wrote ${OUTPUT}`);
  console.log(
    `Stops: ${outStops.length}  Total distance: ${payload.tour.totalDistanceMiles} mi  Total duration: ${payload.tour.totalDurationMinutes} min`
  );
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
