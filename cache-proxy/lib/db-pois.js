/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module db-pois.js';

const DDG = require('duck-duck-scrape');

// ── PostgreSQL-backed /db/pois/* endpoints ────────────────────────────────────

const HAVERSINE_SQL = `
  6371000 * 2 * ASIN(SQRT(
    POWER(SIN(RADIANS(lat - $LAT) / 2), 2) +
    COS(RADIANS($LAT)) * COS(RADIANS(lat)) *
    POWER(SIN(RADIANS(lon - $LON) / 2), 2)
  ))`;

function haversine(latParam, lonParam) {
  return HAVERSINE_SQL.replace(/\$LAT/g, latParam).replace(/\$LON/g, lonParam);
}

function toOverpassElement(row) {
  const el = { type: row.osm_type, id: row.osm_id, lat: row.lat, lon: row.lon, tags: row.tags || {} };
  if (row.distance_m != null) el.distance_m = Math.round(row.distance_m);
  return el;
}

// ── Keyword → category mapping for smart search ──
const KEYWORD_CATEGORIES = {
  // Food & Drink
  'food': 'Food & Drink', 'restaurant': 'Food & Drink', 'restaurants': 'Food & Drink',
  'eat': 'Food & Drink', 'eating': 'Food & Drink', 'dining': 'Food & Drink',
  'fast food': 'Food & Drink', 'cafe': 'Food & Drink', 'coffee': 'Food & Drink',
  'bar': 'Food & Drink', 'pub': 'Food & Drink', 'pizza': 'Food & Drink',
  'bakery': 'Food & Drink', 'deli': 'Food & Drink', 'ice cream': 'Food & Drink',
  'brewery': 'Food & Drink', 'winery': 'Food & Drink', 'liquor': 'Food & Drink',
  'wine shop': 'Food & Drink', 'butcher': 'Food & Drink', 'seafood': 'Food & Drink',
  'italian': 'Food & Drink', 'chinese': 'Food & Drink', 'mexican': 'Food & Drink',
  'sushi': 'Food & Drink', 'thai': 'Food & Drink', 'indian': 'Food & Drink',
  'japanese': 'Food & Drink', 'korean': 'Food & Drink', 'vietnamese': 'Food & Drink',
  'greek': 'Food & Drink', 'french': 'Food & Drink', 'mediterranean': 'Food & Drink',
  'bbq': 'Food & Drink', 'burger': 'Food & Drink', 'burgers': 'Food & Drink',
  'steak': 'Food & Drink', 'steakhouse': 'Food & Drink', 'ramen': 'Food & Drink',
  'noodle': 'Food & Drink', 'noodles': 'Food & Drink', 'taco': 'Food & Drink',
  'tacos': 'Food & Drink', 'donut': 'Food & Drink', 'donuts': 'Food & Drink',
  'bagel': 'Food & Drink', 'chicken': 'Food & Drink', 'wings': 'Food & Drink',
  'sub': 'Food & Drink', 'sandwich': 'Food & Drink', 'sandwiches': 'Food & Drink',
  // Fuel & Charging
  'gas': 'Fuel & Charging', 'fuel': 'Fuel & Charging', 'gas station': 'Fuel & Charging',
  'charging': 'Fuel & Charging', 'ev': 'Fuel & Charging', 'ev charging': 'Fuel & Charging',
  // Transit
  'transit': 'Transit', 'train': 'Transit', 'bus': 'Transit',
  'ferry': 'Transit', 'bike rental': 'Transit', 'station': 'Transit',
  'airport': 'Transit', 'taxi': 'Transit',
  // Civic & Gov
  'civic': 'Civic & Gov', 'government': 'Civic & Gov', 'post office': 'Civic & Gov',
  'courthouse': 'Civic & Gov', 'town hall': 'Civic & Gov', 'embassy': 'Civic & Gov',
  // Parks & Rec
  'park': 'Parks & Rec', 'parks': 'Parks & Rec', 'playground': 'Parks & Rec',
  'garden': 'Parks & Rec', 'nature': 'Parks & Rec', 'pool': 'Parks & Rec',
  'dog park': 'Parks & Rec', 'beach': 'Parks & Rec', 'picnic': 'Parks & Rec',
  'recreation': 'Parks & Rec', 'boat ramp': 'Parks & Rec', 'boat launch': 'Parks & Rec',
  'skatepark': 'Parks & Rec', 'skateboard': 'Parks & Rec',
  // Shopping
  'shop': 'Shopping', 'shopping': 'Shopping', 'store': 'Shopping',
  'supermarket': 'Shopping', 'grocery': 'Shopping', 'mall': 'Shopping',
  'clothes': 'Shopping', 'clothing': 'Shopping', 'shoes': 'Shopping',
  'electronics': 'Shopping', 'hardware': 'Shopping', 'books': 'Shopping',
  'florist': 'Shopping', 'pet store': 'Shopping', 'furniture': 'Shopping',
  'tattoo': 'Shopping', 'barber': 'Shopping', 'thrift': 'Shopping',
  'second hand': 'Shopping', 'vape': 'Shopping', 'cannabis': 'Shopping',
  'dispensary': 'Shopping', 'massage': 'Shopping', 'spa': 'Shopping',
  // Healthcare
  'health': 'Healthcare', 'healthcare': 'Healthcare', 'hospital': 'Healthcare',
  'pharmacy': 'Healthcare', 'clinic': 'Healthcare', 'doctor': 'Healthcare',
  'dentist': 'Healthcare', 'vet': 'Healthcare', 'veterinary': 'Healthcare',
  'medical': 'Healthcare', 'urgent care': 'Healthcare',
  // Education
  'school': 'Education', 'education': 'Education', 'library': 'Education',
  'college': 'Education', 'university': 'Education', 'childcare': 'Education',
  // Lodging
  'hotel': 'Lodging', 'lodging': 'Lodging', 'motel': 'Lodging',
  'hostel': 'Lodging', 'camping': 'Lodging', 'guest house': 'Lodging',
  // Parking
  'parking': 'Parking',
  // Finance
  'bank': 'Finance', 'atm': 'Finance', 'finance': 'Finance',
  // Places of Worship
  'church': 'Places of Worship', 'mosque': 'Places of Worship',
  'temple': 'Places of Worship', 'synagogue': 'Places of Worship',
  'worship': 'Places of Worship',
  // Tourism & History
  'tourism': 'Tourism & History', 'tourist': 'Tourism & History',
  'museum': 'Tourism & History', 'historic': 'Tourism & History',
  'history': 'Tourism & History', 'monument': 'Tourism & History',
  'memorial': 'Tourism & History', 'gallery': 'Tourism & History',
  'zoo': 'Tourism & History', 'aquarium': 'Tourism & History',
  'attraction': 'Tourism & History', 'viewpoint': 'Tourism & History',
  // Emergency Svc
  'police': 'Emergency Svc', 'fire station': 'Emergency Svc', 'emergency': 'Emergency Svc',
  // Auto Services
  'car repair': 'Auto Services', 'car wash': 'Auto Services', 'auto': 'Auto Services',
  'mechanic': 'Auto Services', 'tires': 'Auto Services', 'car rental': 'Auto Services',
  // Entertainment
  'gym': 'Entertainment', 'fitness': 'Entertainment', 'cinema': 'Entertainment',
  'movie': 'Entertainment', 'movies': 'Entertainment', 'theatre': 'Entertainment',
  'theater': 'Entertainment', 'nightclub': 'Entertainment', 'bowling': 'Entertainment',
  'golf': 'Entertainment', 'disc golf': 'Entertainment', 'frisbee golf': 'Entertainment',
  'marina': 'Entertainment', 'sports': 'Entertainment',
  'entertainment': 'Entertainment', 'arcade': 'Entertainment',
  // Offices & Services
  'office': 'Offices & Services', 'lawyer': 'Offices & Services',
  'insurance': 'Offices & Services', 'real estate': 'Offices & Services',
};

const CATEGORY_LABEL_TAGS = {
  'Food & Drink':       ['amenity=restaurant','amenity=fast_food','amenity=cafe','amenity=bar','amenity=pub','amenity=ice_cream','shop=bakery','shop=alcohol','shop=deli','shop=pastry','shop=confectionery','amenity=marketplace','craft=brewery','craft=winery','craft=distillery','shop=wine','shop=butcher','shop=seafood'],
  'Fuel & Charging':    ['amenity=fuel','amenity=charging_station'],
  'Transit':            ['public_transport=station','railway=station','amenity=bus_station','amenity=bicycle_rental','amenity=ferry_terminal','aeroway=aerodrome','amenity=taxi'],
  'Civic & Gov':        ['amenity=townhall','amenity=courthouse','amenity=post_office','office=government','amenity=community_centre','amenity=social_facility','amenity=post_box','amenity=recycling','office=diplomatic'],
  'Parks & Rec':        ['leisure=park','leisure=nature_reserve','leisure=playground','leisure=pitch','leisure=swimming_pool','leisure=garden','tourism=picnic_site','amenity=drinking_water','amenity=toilets','amenity=shelter','amenity=fountain','leisure=dog_park','leisure=track','leisure=recreation_ground','leisure=beach_resort','leisure=slipway','leisure=skatepark'],
  'Shopping':           ['shop=supermarket','shop=convenience','shop=mall','shop=department_store','shop=clothes','shop=hairdresser','shop=beauty','shop=massage','shop=gift','shop=laundry','shop=variety_store','shop=mobile_phone','shop=dry_cleaning','shop=books','shop=furniture','shop=jewelry','shop=optician','shop=florist','shop=chemist','shop=storage_rental','shop=shoes','shop=tobacco','shop=hardware','shop=pet','shop=electronics','shop=bicycle','shop=garden_centre','shop=tattoo','shop=barber','shop=second_hand','shop=e-cigarette','shop=cannabis'],
  'Healthcare':         ['amenity=hospital','amenity=pharmacy','amenity=clinic','amenity=dentist','amenity=doctors','amenity=veterinary','amenity=nursing_home'],
  'Education':          ['amenity=school','amenity=library','amenity=college','amenity=university','amenity=childcare','amenity=kindergarten'],
  'Lodging':            ['tourism=hotel','tourism=motel','tourism=hostel','tourism=camp_site','tourism=guest_house','tourism=caravan_site'],
  'Parking':            ['amenity=parking'],
  'Finance':            ['amenity=bank','amenity=atm'],
  'Places of Worship':  ['amenity=place_of_worship'],
  'Tourism & History':  ['tourism=museum','tourism=attraction','tourism=viewpoint','historic=memorial','historic=monument','tourism=artwork','tourism=gallery','tourism=information','historic=cemetery','historic=building','historic=ruins','historic=maritime','tourism=zoo','tourism=aquarium','tourism=theme_park'],
  'Emergency Svc':      ['amenity=police','amenity=fire_station'],
  'Auto Services':      ['shop=car_repair','amenity=car_wash','amenity=car_rental','shop=tyres','shop=car','shop=car_parts'],
  'Entertainment':      ['leisure=fitness_centre','leisure=sports_centre','leisure=golf_course','leisure=disc_golf_course','leisure=marina','leisure=stadium','amenity=theatre','amenity=cinema','amenity=nightclub','amenity=events_venue','amenity=arts_centre','amenity=studio','leisure=dance','leisure=amusement_arcade','leisure=ice_rink','leisure=bowling_alley','leisure=water_park','leisure=miniature_golf','leisure=escape_game'],
  'Offices & Services': ['office=company','office=estate_agent','office=lawyer','office=insurance','office=tax_advisor'],
};

// Cuisine keywords — when user searches these, also match tags->>'cuisine'
const CUISINE_KEYWORDS = new Set([
  'pizza', 'sushi', 'mexican', 'chinese', 'indian', 'thai', 'japanese', 'korean',
  'vietnamese', 'greek', 'french', 'mediterranean', 'italian', 'bbq', 'barbecue',
  'burger', 'burgers', 'steak', 'steakhouse', 'ramen', 'noodle', 'noodles',
  'taco', 'tacos', 'donut', 'donuts', 'bagel', 'chicken', 'wings', 'seafood',
  'sandwich', 'sandwiches', 'sub', 'vegan', 'vegetarian'
]);
// Map alternate spellings to what OSM actually uses
const CUISINE_ALIASES = { 'bbq': 'barbecue', 'burgers': 'burger', 'noodles': 'noodle', 'tacos': 'taco', 'donuts': 'donut', 'sandwiches': 'sandwich', 'steakhouse': 'steak', 'wings': 'chicken' };

function parseSearchKeywords(query) {
  const lower = query.toLowerCase().trim();
  const words = lower.split(/\s+/);
  // Try 2-word prefix first, then 1-word
  for (let len = Math.min(2, words.length); len >= 1; len--) {
    const prefix = words.slice(0, len).join(' ');
    if (KEYWORD_CATEGORIES[prefix]) {
      const label = KEYWORD_CATEGORIES[prefix];
      const remaining = words.slice(len).join(' ').trim();
      const rawCuisine = CUISINE_KEYWORDS.has(prefix) ? prefix : (CUISINE_KEYWORDS.has(remaining) ? remaining : null);
      const cuisineHint = rawCuisine ? (CUISINE_ALIASES[rawCuisine] || rawCuisine) : null;
      return { keyword: prefix, categoryLabel: label, categoryTags: CATEGORY_LABEL_TAGS[label], remainingQuery: remaining, cuisineHint };
    }
  }
  // Check if the bare query itself is a cuisine keyword
  const rawCuisine = CUISINE_KEYWORDS.has(lower) ? lower : null;
  const cuisineHint = rawCuisine ? (CUISINE_ALIASES[rawCuisine] || rawCuisine) : null;
  return { keyword: null, categoryLabel: null, categoryTags: null, remainingQuery: lower, cuisineHint };
}

// ── Website resolution helpers ──

const DIRECTORY_DOMAINS = ['yelp.com', 'facebook.com', 'tripadvisor.com', 'yellowpages.com',
  'foursquare.com', 'bbb.org', 'mapquest.com', 'whitepages.com', 'manta.com'];

async function cacheResolvedWebsite(pgPool, osmType, osmId, url, source) {
  if (!pgPool) return;
  try {
    const patch = JSON.stringify({ _resolved_website: url || '', _resolved_source: source });
    await pgPool.query(
      `UPDATE pois SET tags = tags || $1::jsonb WHERE osm_type = $2 AND osm_id = $3`,
      [patch, osmType, parseInt(osmId)]
    );
  } catch (err) {
    console.error('[cacheResolvedWebsite]', err.message);
  }
}

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // GET /db/pois/search — smart fuzzy search with keyword category hints
  app.get('/db/pois/search', requirePg, async (req, res) => {
    try {
      const { q, lat, lon, limit } = req.query;
      if (!q || !lat || !lon) return res.status(400).json({ error: 'q, lat, and lon required' });

      const fLat = parseFloat(lat), fLon = parseFloat(lon);
      const lim = Math.min(parseInt(limit) || 50, 200);
      const { keyword, categoryLabel, categoryTags, remainingQuery, cuisineHint } = parseSearchKeywords(q);
      const hasNameQuery = remainingQuery.length >= 2;

      // Distance expansion radii — expand until ≥50 results, max 100 miles (160,934m)
      const radii = [50000, 100000, 160934];
      let finalRows = [];
      let scopeM = 0;

      for (const radiusM of radii) {
        const conditions = [];
        const params = [];
        let pi = 1;

        // Lat/lon params for distance
        const latP = `$${pi}`, lonP = `$${pi + 1}`;
        params.push(fLat, fLon);
        pi += 2;

        // Bbox pre-filter for non-global
        if (radiusM > 0) {
          const degPerKm = 0.009;
          const kmR = radiusM / 1000;
          const latD = kmR * degPerKm;
          const lonD = kmR * degPerKm / Math.cos(fLat * Math.PI / 180);
          conditions.push(`lat BETWEEN $${pi} AND $${pi + 1}`);
          params.push(fLat - latD, fLat + latD);
          pi += 2;
          conditions.push(`lon BETWEEN $${pi} AND $${pi + 1}`);
          params.push(fLon - lonD, fLon + lonD);
          pi += 2;
        }

        // Category filter from keyword
        if (categoryTags && categoryTags.length > 0) {
          conditions.push(`category = ANY($${pi})`);
          params.push(categoryTags);
          pi++;
        }

        // Name matching: fuzzy + substring + cuisine tag
        let scoreExpr;
        if (hasNameQuery) {
          conditions.push(`name IS NOT NULL`);
          if (cuisineHint) {
            // Also match cuisine tag (e.g. "italian" matches name OR cuisine)
            conditions.push(`(name ILIKE $${pi} OR similarity(name, $${pi + 1}) > 0.2 OR tags->>'cuisine' ILIKE $${pi + 2})`);
            params.push(`%${remainingQuery}%`, remainingQuery, `%${cuisineHint}%`);
            scoreExpr = `CASE WHEN name ILIKE $${pi} THEN 1.0 WHEN tags->>'cuisine' ILIKE $${pi + 2} THEN 0.8 ELSE 0.0 END + COALESCE(similarity(name, $${pi + 1}), 0)`;
            pi += 3;
          } else {
            conditions.push(`(name ILIKE $${pi} OR similarity(name, $${pi + 1}) > 0.2)`);
            params.push(`%${remainingQuery}%`, remainingQuery);
            scoreExpr = `CASE WHEN name ILIKE $${pi} THEN 1.0 ELSE 0.0 END + similarity(name, $${pi + 1})`;
            pi += 2;
          }
        } else if (cuisineHint && categoryTags) {
          // Cuisine keyword only (e.g. just "pizza") — match name OR cuisine tag
          conditions.push(`(name ILIKE $${pi} OR tags->>'cuisine' ILIKE $${pi + 1})`);
          params.push(`%${cuisineHint}%`, `%${cuisineHint}%`);
          scoreExpr = `CASE WHEN tags->>'cuisine' ILIKE $${pi + 1} THEN 1.0 WHEN name ILIKE $${pi} THEN 0.8 ELSE 0.0 END`;
          pi += 2;
        } else if (!categoryTags) {
          // No keyword, no name query — need at least a name substring
          conditions.push(`name ILIKE $${pi}`);
          params.push(`%${q}%`);
          scoreExpr = `CASE WHEN name ILIKE $${pi} THEN 1.0 ELSE 0.0 END + COALESCE(similarity(name, $${pi + 1}), 0)`;
          params.push(q);
          pi += 2;
        }
        // else: keyword-only browse — no name filter

        const dist = haversine(latP, lonP);
        const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';
        const order = `ORDER BY ${dist} ASC`;

        const sql = `SELECT osm_type, osm_id, name, category, lat, lon, tags, ${dist} AS distance_m${scoreExpr ? `, (${scoreExpr}) AS score` : ''} FROM pois ${where} ${order} LIMIT ${lim}`;
        const result = await pgPool.query(sql, params);
        finalRows = result.rows;
        scopeM = radiusM || 99999000;

        if (finalRows.length >= 50) break; // enough results
      }

      res.json({
        count: finalRows.length,
        scope_m: scopeM,
        category_hint: categoryLabel || null,
        elements: finalRows.map(r => {
          const el = toOverpassElement(r);
          if (r.name) el.name = r.name;
          if (r.category) el.category = r.category;
          if (r.score != null) el.score = parseFloat(r.score.toFixed(3));
          return el;
        })
      });
    } catch (err) {
      console.error('[/db/pois/search]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/pois/nearby — nearby POIs sorted by distance
  app.get('/db/pois/nearby', requirePg, async (req, res) => {
    try {
      const { lat, lon, radius, category, limit } = req.query;
      if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });

      const fLat = parseFloat(lat), fLon = parseFloat(lon);
      const fRadius = parseFloat(radius) || 1000;
      const lim = Math.min(parseInt(limit) || 50, 200);

      // Pre-filter with bbox approximation (~0.009° per km at mid-latitudes)
      const degPerKm = 0.009;
      const kmRadius = fRadius / 1000;
      const latDelta = kmRadius * degPerKm;
      const lonDelta = kmRadius * degPerKm / Math.cos(fLat * Math.PI / 180);

      const conditions = [
        `lat BETWEEN $1 AND $2`,
        `lon BETWEEN $3 AND $4`,
      ];
      const params = [fLat - latDelta, fLat + latDelta, fLon - lonDelta, fLon + lonDelta];
      let pi = 5;

      if (category) {
        conditions.push(`category = $${pi}`);
        params.push(category);
        pi++;
      }

      params.push(fLat, fLon, fRadius);
      const distCol = haversine(`$${pi}`, `$${pi + 1}`);
      const radiusP = `$${pi + 2}`;

      const sql = `SELECT osm_type, osm_id, lat, lon, tags, ${distCol} AS distance_m
        FROM pois WHERE ${conditions.join(' AND ')} AND ${distCol} <= ${radiusP}
        ORDER BY distance_m LIMIT ${lim}`;

      const result = await pgPool.query(sql, params);
      res.json({ count: result.rows.length, elements: result.rows.map(toOverpassElement) });
    } catch (err) {
      console.error('[/db/pois/nearby]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/poi/:type/:id — single POI lookup
  app.get('/db/poi/:type/:id', requirePg, async (req, res) => {
    try {
      const result = await pgPool.query(
        'SELECT osm_type, osm_id, lat, lon, name, category, tags, first_seen, last_seen FROM pois WHERE osm_type = $1 AND osm_id = $2',
        [req.params.type, req.params.id]
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'POI not found' });
      const row = result.rows[0];
      res.json({
        type: row.osm_type, id: row.osm_id, lat: row.lat, lon: row.lon,
        name: row.name, category: row.category, tags: row.tags,
        first_seen: row.first_seen, last_seen: row.last_seen,
      });
    } catch (err) {
      console.error('[/db/poi/:type/:id]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/pois/stats — analytics overview
  app.get('/db/pois/stats', requirePg, async (req, res) => {
    try {
      const [total, named, topCategories, bounds, timeRange] = await Promise.all([
        pgPool.query('SELECT COUNT(*) AS n FROM pois'),
        pgPool.query('SELECT COUNT(*) AS n FROM pois WHERE name IS NOT NULL'),
        pgPool.query('SELECT category, COUNT(*) AS n FROM pois WHERE category IS NOT NULL GROUP BY category ORDER BY n DESC LIMIT 20'),
        pgPool.query('SELECT MIN(lat) AS min_lat, MAX(lat) AS max_lat, MIN(lon) AS min_lon, MAX(lon) AS max_lon FROM pois'),
        pgPool.query('SELECT MIN(first_seen) AS earliest, MAX(last_seen) AS latest FROM pois'),
      ]);
      res.json({
        total: parseInt(total.rows[0].n),
        named: parseInt(named.rows[0].n),
        topCategories: topCategories.rows.map(r => ({ category: r.category, count: parseInt(r.n) })),
        bounds: bounds.rows[0],
        timeRange: timeRange.rows[0],
      });
    } catch (err) {
      console.error('[/db/pois/stats]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/pois/categories — category breakdown
  app.get('/db/pois/categories', requirePg, async (req, res) => {
    try {
      const result = await pgPool.query(
        'SELECT category, COUNT(*) AS n FROM pois WHERE category IS NOT NULL GROUP BY category ORDER BY n DESC'
      );
      const categories = result.rows.map(r => {
        const parts = r.category.split('=');
        return { category: r.category, key: parts[0], value: parts[1] || null, count: parseInt(r.n) };
      });
      res.json({ count: categories.length, categories });
    } catch (err) {
      console.error('[/db/pois/categories]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // GET /db/pois/coverage — geographic grid coverage
  app.get('/db/pois/coverage', requirePg, async (req, res) => {
    try {
      const resolution = Math.min(Math.max(parseInt(req.query.resolution) || 2, 1), 4);
      const result = await pgPool.query(
        `SELECT ROUND(lat::numeric, $1) AS grid_lat, ROUND(lon::numeric, $1) AS grid_lon, COUNT(*) AS n
         FROM pois GROUP BY grid_lat, grid_lon ORDER BY n DESC`,
        [resolution]
      );
      const cells = result.rows.map(r => ({
        lat: parseFloat(r.grid_lat), lon: parseFloat(r.grid_lon), count: parseInt(r.n),
      }));
      res.json({ resolution, cells: cells.length, grid: cells });
    } catch (err) {
      console.error('[/db/pois/coverage]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ── /db/pois/counts — category counts with 10-min server cache ───────────────

  let poiCountsCache = null;
  let poiCountsCachedAt = 0;
  const POI_COUNTS_TTL_MS = 10 * 60 * 1000; // 10 minutes

  app.get('/db/pois/counts', requirePg, async (req, res) => {
    try {
      const lat = parseFloat(req.query.lat);
      const lon = parseFloat(req.query.lon);
      const radiusM = parseInt(req.query.radius) || 10000;
      const hasLocation = !isNaN(lat) && !isNaN(lon);

      // Use cache only for global (non-location) queries
      if (!hasLocation) {
        const now = Date.now();
        if (poiCountsCache && (now - poiCountsCachedAt) < POI_COUNTS_TTL_MS) {
          return res.json(poiCountsCache);
        }
      }

      let result;
      if (hasLocation) {
        // Haversine distance filter — count only POIs within radius
        const radiusKm = radiusM / 1000;
        result = await pgPool.query(
          `SELECT category, COUNT(*)::int AS count FROM pois
           WHERE category IS NOT NULL
             AND (6371 * acos(LEAST(1.0, cos(radians($1)) * cos(radians(lat)) * cos(radians(lon) - radians($2)) + sin(radians($1)) * sin(radians(lat))))) <= $3
           GROUP BY category ORDER BY count DESC`,
          [lat, lon, radiusKm]
        );
      } else {
        result = await pgPool.query(
          `SELECT category, COUNT(*)::int AS count FROM pois WHERE category IS NOT NULL GROUP BY category ORDER BY count DESC`
        );
      }

      const counts = {};
      let total = 0;
      for (const row of result.rows) {
        counts[row.category] = row.count;
        total += row.count;
      }
      const response = { counts, total, cachedAt: new Date().toISOString() };
      if (hasLocation) response.radiusM = radiusM;

      // Cache only global queries
      if (!hasLocation) {
        poiCountsCache = response;
        poiCountsCachedAt = Date.now();
      }
      res.json(response);
    } catch (err) {
      console.error('[/db/pois/counts]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ── /db/pois/find — distance-sorted POIs by category ─────────────────────────

  app.get('/db/pois/find', requirePg, async (req, res) => {
    try {
      const { lat, lon, categories, limit: rawLimit, offset: rawOffset } = req.query;
      if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });

      const userLat = parseFloat(lat);
      const userLon = parseFloat(lon);
      const maxResults = Math.min(parseInt(rawLimit) || 50, 200);
      const skip = parseInt(rawOffset) || 0;
      const catList = categories ? categories.split(',').map(c => c.trim()).filter(Boolean) : [];
      if (catList.length === 0) return res.status(400).json({ error: 'categories required (comma-separated)' });

      // Try expanding scopes: 50km → 200km → 1000km → global (0 = no bbox)
      let scopeKm = 50;
      let rows, totalInRange;

      // Inline lat/lon in Haversine to avoid pg type inference issues
      const distExpr = haversine(String(userLat), String(userLon));

      for (const tryKm of [50, 200, 1000, 0]) {
        scopeKm = tryKm;

        if (tryKm === 0) {
          // Global fallback — no bbox filter, just closest by distance
          const catPlaceholders = catList.map((_, i) => `$${i + 1}`).join(', ');
          const params = [...catList];
          const limitIdx = params.length + 1;
          const offsetIdx = params.length + 2;
          params.push(maxResults, skip);

          const sql = `
            SELECT osm_type, osm_id, lat, lon, name, category, tags,
                   ${distExpr} AS distance_m
            FROM pois
            WHERE category IN (${catPlaceholders})
            ORDER BY distance_m
            LIMIT $${limitIdx} OFFSET $${offsetIdx}
          `;
          const countSql = `
            SELECT COUNT(*)::int AS total
            FROM pois
            WHERE category IN (${catPlaceholders})
          `;
          const countParams = catList;

          const [dataResult, countResult] = await Promise.all([
            pgPool.query(sql, params),
            pgPool.query(countSql, countParams)
          ]);
          rows = dataResult.rows;
          totalInRange = countResult.rows[0]?.total || 0;
          break;
        }

        const degLat = tryKm / 111.0;
        const degLon = degLat / Math.cos(userLat * Math.PI / 180);

        const params = [
          userLat - degLat, userLat + degLat,   // $1, $2 — lat bbox
          ...catList                             // $3.. — categories
        ];
        const catPlaceholders = catList.map((_, i) => `$${i + 3}`).join(', ');

        // lon bounds after categories
        const lonIdx1 = params.length + 1;
        const lonIdx2 = params.length + 2;
        params.push(userLon - degLon, userLon + degLon);

        const limitIdx = lonIdx2 + 1;
        const offsetIdx = lonIdx2 + 2;
        params.push(maxResults, skip);

        const sql = `
          SELECT osm_type, osm_id, lat, lon, name, category, tags,
                 ${distExpr} AS distance_m
          FROM pois
          WHERE category IN (${catPlaceholders})
            AND lat BETWEEN $1 AND $2
            AND lon BETWEEN $${lonIdx1} AND $${lonIdx2}
          ORDER BY distance_m
          LIMIT $${limitIdx} OFFSET $${offsetIdx}
        `;

        // Count query (same filters, no limit)
        const countSql = `
          SELECT COUNT(*)::int AS total
          FROM pois
          WHERE category IN (${catPlaceholders})
            AND lat BETWEEN $1 AND $2
            AND lon BETWEEN $${lonIdx1} AND $${lonIdx2}
        `;
        const countParams = params.slice(0, params.length - 2); // exclude limit/offset

        const [dataResult, countResult] = await Promise.all([
          pgPool.query(sql, params),
          pgPool.query(countSql, countParams)
        ]);
        rows = dataResult.rows;
        totalInRange = countResult.rows[0]?.total || 0;

        if (rows.length >= maxResults || tryKm === 0) break;
      }

      const elements = rows.map(row => ({
        type: row.osm_type,
        id: row.osm_id,
        lat: row.lat,
        lon: row.lon,
        name: row.name,
        category: row.category,
        distance_m: Math.round(row.distance_m),
        tags: row.tags || {}
      }));

      res.json({
        count: elements.length,
        total_in_range: totalInRange,
        scope_m: scopeKm * 1000,
        elements
      });
    } catch (err) {
      console.error('[/db/pois/find]', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ── GET /pois/website — resolve website URL for a POI (waterfall: OSM → Wikidata → DDG) ──

  app.get('/pois/website', requirePg, async (req, res) => {
    try {
      const { osm_type, osm_id, name, lat, lon } = req.query;
      if (!osm_type || !osm_id) {
        return res.status(400).json({ error: 'osm_type and osm_id required' });
      }

      // Fetch POI row
      const result = await pgPool.query(
        `SELECT tags FROM pois WHERE osm_type = $1 AND osm_id = $2`,
        [osm_type, parseInt(osm_id)]
      );
      const tags = result.rows[0]?.tags || {};

      // Extract contact info from tags regardless of website outcome
      const phone = tags['phone'] || tags['contact:phone'] || null;
      const hours = tags['opening_hours'] || null;
      const addrParts = [];
      if (tags['addr:housenumber']) addrParts.push(tags['addr:housenumber']);
      if (tags['addr:street']) addrParts.push(tags['addr:street']);
      if (tags['addr:city']) addrParts.push(tags['addr:city']);
      if (tags['addr:state']) addrParts.push(tags['addr:state']);
      const address = addrParts.length > 0 ? addrParts.join(' ') : null;

      // Check cached result first
      if (tags._resolved_website) {
        return res.json({ url: tags._resolved_website || null, source: 'cached', phone, hours, address });
      }
      if (tags._resolved_source === 'none') {
        return res.json({ url: null, source: 'cached', phone, hours, address });
      }

      // Tier 1 — OSM tags
      const osmUrl = tags['website'] || tags['contact:website'] || tags['brand:website'] || tags['url'];
      if (osmUrl) {
        cacheResolvedWebsite(pgPool, osm_type, osm_id, osmUrl, 'osm');
        return res.json({ url: osmUrl, source: 'osm', phone, hours, address });
      }

      // Tier 2 — Wikidata
      const wikidataId = tags['wikidata'] || tags['brand:wikidata'];
      if (wikidataId) {
        try {
          const wdResp = await fetch(
            `https://www.wikidata.org/wiki/Special:EntityData/${wikidataId}.json`
          );
          if (wdResp.ok) {
            const wdData = await wdResp.json();
            const entity = wdData.entities?.[wikidataId];
            const p856 = entity?.claims?.P856?.[0]?.mainsnak?.datavalue?.value;
            if (p856) {
              cacheResolvedWebsite(pgPool, osm_type, osm_id, p856, 'wikidata');
              return res.json({ url: p856, source: 'wikidata', phone, hours, address });
            }
          }
        } catch (wdErr) {
          console.error('[/pois/website] Wikidata error:', wdErr.message);
        }
      }

      // Tier 3 — DuckDuckGo search
      if (name && lat && lon) {
        try {
          const searchQuery = `${name} ${lat} ${lon}`;
          const ddgResults = await DDG.search(searchQuery, { safeSearch: DDG.SafeSearchType.OFF });
          const hits = (ddgResults?.results || []).filter(r => {
            if (!r.url) return false;
            const hostname = new URL(r.url).hostname.toLowerCase();
            return !DIRECTORY_DOMAINS.some(d => hostname.includes(d));
          });
          if (hits.length > 0) {
            const foundUrl = hits[0].url;
            cacheResolvedWebsite(pgPool, osm_type, osm_id, foundUrl, 'search');
            return res.json({ url: foundUrl, source: 'search', phone, hours, address });
          }
        } catch (ddgErr) {
          console.error('[/pois/website] DDG search error:', ddgErr.message);
        }
      }

      // No website found
      cacheResolvedWebsite(pgPool, osm_type, osm_id, '', 'none');
      res.json({ url: null, source: 'none', phone, hours, address });
    } catch (err) {
      console.error('[/pois/website]', err.message);
      res.json({ url: null, source: 'none', phone: null, hours: null, address: null });
    }
  });
};
