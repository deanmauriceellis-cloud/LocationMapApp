/**
 * Module: admin-suggest-categorization.js
 * (C) Dean Maurice Ellis, 2026
 *
 * S205 — Auto-categorize commercial POIs by mapping SalemIntelligence
 * (port 8089) `primary_category` + secondary/specialty/name patterns onto
 * LMA `(category, subcategory)` pairs that match the on-disk
 * `assets/poi-icons/<category>/` icon variants.
 *
 * Confidence tiers:
 *   1.00 — direct SI primary → LMA (cat, sub) AND current category matches
 *          AND row has intel_entity_id. Auto-apply eligible.
 *   0.90 — refined via secondary/specialty/name AND current category matches
 *          AND row has intel_entity_id. Auto-apply eligible.
 *   0.70 — propose category change (current ≠ proposed). Review only.
 *   0.60 — fuzzy SI match (no intel_entity_id) on (name + lat/lng).
 *   0.40 — fuzzy match with weak signal. Review only.
 *
 * Auto-apply rule (server-side):
 *   confidence ≥ 0.90 AND proposed_category === current_category
 *   (only fills missing subcategory; never silently changes a category).
 */

const SCOPE_CATEGORIES = [
  'FOOD_DRINK','SHOPPING','ENTERTAINMENT','LODGING','PARKS_REC',
  'AUTO_SERVICES','HEALTHCARE','OFFICES','EDUCATION',
  'WITCH_SHOP','PSYCHIC','FINANCE','TOUR_COMPANIES',
  // WORSHIP intentionally omitted — registry has no WORSHIP subcategories
  // so any subcategory we'd propose would violate salem_pois_subcategory_fkey.
];

// S205 — the salem_pois.subcategory column has a FK to salem_poi_subcategories(id).
// Refiner outputs that don't exist in the registry must be remapped or dropped.
// This map keys (LMA category, refiner-emitted token) → registry-valid token.
// Used as a last-mile alias resolver in proposeFromSi.
const SUB_TOKEN_ALIASES = {
  TOUR_COMPANIES: {
    haunted_tours:    'ghost_tours',
    historical_tours: 'walking_tours',
    night_tours:      'ghost_tours',
  },
  // Add more aliases here when refiners emit tokens that diverge from registry.
};

// Cache of registry-valid (cat, sub) pairs. Refreshed lazily.
const REGISTRY_TTL_MS = 5 * 60_000;
let _registryCache = { ts: 0, byCat: new Map() };

async function loadRegistry(pgPool) {
  if (Date.now() - _registryCache.ts < REGISTRY_TTL_MS && _registryCache.byCat.size > 0) {
    return _registryCache;
  }
  const { rows } = await pgPool.query(`
    SELECT category_id, id FROM salem_poi_subcategories
  `);
  const byCat = new Map();
  for (const r of rows) {
    if (!byCat.has(r.category_id)) byCat.set(r.category_id, new Set());
    byCat.get(r.category_id).add(r.id);
  }
  _registryCache = { ts: Date.now(), byCat };
  return _registryCache;
}

// SI primary_category → ({lma_cat, sub} | refiner-fn). Refiner returns
// {sub, conf_bonus} or null. The base mapping yields conf=1.0 when sub is a
// direct value, conf=0.9 when refined by a function.
const SI_PRIMARY_MAP = {
  restaurant:        { cat: 'FOOD_DRINK',     sub: 'restaurants' },
  cafe:              { cat: 'FOOD_DRINK',     sub: 'cafes' },
  bar:               { cat: 'FOOD_DRINK',     sub: 'bars' },
  spa_beauty:        { cat: 'SHOPPING',       sub: 'beauty_spa', refiner: refineSpaBeauty },
  shop_bookstore:    { cat: 'SHOPPING',       sub: 'bookstores' },
  shop_retail:       { cat: 'SHOPPING',       refiner: refineShopRetail },
  shop_occult:       { cat: 'WITCH_SHOP',     refiner: refineShopOccult },
  fitness:           { cat: 'ENTERTAINMENT', sub: 'fitness' },
  hotel_lodging:     { cat: 'LODGING',        refiner: refineLodging },
  tour_operator:     { cat: 'TOUR_COMPANIES', refiner: refineTour },
  museum:            { cat: 'HISTORICAL_BUILDINGS', sub: 'museums' }, // out of scope per operator
  service_professional: { cat: 'OFFICES',     refiner: refineProfessional },
  service_health:    { cat: 'HEALTHCARE',     refiner: refineHealth },
  education:         { cat: 'EDUCATION',      refiner: refineEducation },
  religious:         { cat: 'WORSHIP',        sub: 'places_of_worship' },
  gallery_art:       { cat: 'ENTERTAINMENT', sub: 'arts_centres' },
  performance_venue: { cat: 'ENTERTAINMENT', refiner: refinePerformance },
  shop_antiques:     { cat: 'SHOPPING',       sub: null }, // no icon variant on disk; skip
  // 'other' and null primary fall through to no proposal.
};

function nameLower(p) {
  return (p?.display_name || p?.name || '').toLowerCase();
}

function refineShopRetail(p) {
  const n = nameLower(p);
  const sec = (p.secondary_categories || []).map(s => String(s).toLowerCase());
  const spec = (p.specialties || []).map(s => String(s).toLowerCase());
  const all = `${n} ${sec.join(' ')} ${spec.join(' ')}`;
  if (/\b(jewel|jewelry)\b/.test(all))                                   return { sub: 'jewelry' };
  if (/\b(florist|flower)\b/.test(all))                                  return { sub: 'florists' };
  if (/\b(thrift|consignment)\b/.test(all))                              return { sub: 'thrift_stores' };
  if (/\b(cannabis|dispensary|marijuana)\b/.test(all))                   return { sub: 'cannabis' };
  if (/\b(hardware|home depot|lowe's|true value|ace hardware)\b/.test(all)) return { sub: 'hardware_stores' };
  if (/\b(supermarket|shaw's|stop & shop|market basket|whole foods|trader joe)\b/.test(all)) return { sub: 'supermarkets' };
  if (/\b(convenience|7-eleven|cumberland farms|7 eleven)\b/.test(all))  return { sub: 'convenience_stores' };
  if (/\b(mall)\b/.test(n))                                              return { sub: 'malls' };
  if (/\b(garden center|nursery|garden centre)\b/.test(all))             return { sub: 'garden_centers' };
  if (/\b(pet|pets|petco|petsmart)\b/.test(all))                         return { sub: 'pet_stores' };
  if (/\b(tattoo)\b/.test(all))                                          return { sub: 'tattoo_shops' };
  if (/\b(vape|vaping|smoke shop)\b/.test(all))                          return { sub: 'vape_shops' };
  if (/\b(tobacco|cigar)\b/.test(all))                                   return { sub: 'tobacco_shops' };
  if (/\b(shoe|footwear|sneaker)\b/.test(all))                           return { sub: 'shoe_stores' };
  if (/\b(verizon|at&t|t-mobile|sprint|phone repair|cell phone)\b/.test(all)) return { sub: 'phone_stores' };
  if (/\b(pharmacy|drug store|cvs|rite aid|walgreens)\b/.test(all))      return { sub: 'drug_stores' };
  if (/\b(electronics|best buy|radio shack)\b/.test(all))                return { sub: 'electronics' };
  if (/\b(barber)\b/.test(all))                                          return { sub: 'barber_shops' };
  if (/\b(laundromat|laundry)\b/.test(all))                              return { sub: 'laundromats' };
  if (/\b(dry clean)\b/.test(all))                                       return { sub: 'dry_cleaners' };
  if (/\b(storage|u-?haul|public storage)\b/.test(all))                  return { sub: 'storage_rentals' };
  if (/\b(department store|macy's|kohl's|target)\b/.test(all))           return { sub: 'department_stores' };
  if (/\b(furniture)\b/.test(all))                                       return { sub: 'furniture' };
  if (/\b(bicycle|bike shop)\b/.test(all))                               return { sub: 'bicycle_shops' };
  if (/\b(gift|souvenir|memento)\b/.test(all))                           return { sub: 'gift_shops' };
  if (/\b(optician|eyewear|glasses)\b/.test(all))                        return { sub: 'opticians' };
  if (/\b(massage)\b/.test(all))                                         return { sub: 'massage' };
  if (/\b(apparel|clothing|boutique|fashion)\b/.test(all))               return { sub: 'clothing' };
  return null;
}

function refineShopOccult(p) {
  const all = `${nameLower(p)} ${(p.specialties||[]).join(' ').toLowerCase()}`;
  if (/\b(crystal)\b/.test(all))                       return { sub: 'crystal_shops' };
  if (/\b(herb|apothecary)\b/.test(all))               return { sub: 'herb_shops' };
  if (/\b(metaphysical)\b/.test(all))                  return { sub: 'metaphysical' };
  // Default to generic witchcraft shops for the remainder.
  return { sub: 'witchcraft_shops' };
}

function refineSpaBeauty(p) {
  const n = nameLower(p);
  if (/\b(barber)\b/.test(n))            return { sub: 'barber_shops' };
  if (/\b(massage)\b/.test(n))           return { sub: 'massage' };
  if (/\b(salon|hair|cuts)\b/.test(n))   return { sub: 'hair_salons' };
  // Default to beauty_spa.
  return { sub: 'beauty_spa' };
}

// Strong name overrides — applied BEFORE the SI primary mapping. Catches
// names where a single keyword definitively places the POI regardless of
// what SI labeled it. Returns { cat, sub } or null.
function nameOverride(p) {
  const n = nameLower(p);
  // Auto: match "auto", "autobody", "autozone", "automotive", "garage";
  // tires/tire shops; oil change; etc.
  if (/auto(body|motive|zone|mart)?\b|automotive|\bgarage\b|motor works|\btires?\b|\btransmission\b|\bmuffler\b|\bcollision\b|\bbody shop\b|\bcar wash\b|\boil change\b|valvoline|jiffy lube|midas/.test(n)) {
    if (/\btires?\b/.test(n))                                                                   return { cat: 'AUTO_SERVICES', sub: 'tire_shops' };
    if (/\bcar wash\b/.test(n))                                                                 return { cat: 'AUTO_SERVICES', sub: 'car_washes' };
    if (/dealership|motors\b|chevrolet|ford|toyota|honda|kia|nissan|bmw|mercedes|subaru|hyundai/.test(n)) return { cat: 'AUTO_SERVICES', sub: 'dealerships' };
    if (/autozone|auto parts|parts store|napa/.test(n))                                         return { cat: 'AUTO_SERVICES', sub: 'parts_stores' };
    if (/\b(enterprise|hertz|avis|budget)\b.*\b(rental|car)\b|car rental/.test(n))              return { cat: 'AUTO_SERVICES', sub: 'rentals' };
    return { cat: 'AUTO_SERVICES', sub: 'repair_shops' };
  }
  // Storage rental
  if (/\bself storage\b|public storage|u-?haul|extra space storage/.test(n))                   return { cat: 'SHOPPING', sub: 'storage_rentals' };
  // Bicycle
  if (/\bcycles?\b|bicycle|\bbike shop\b/.test(n))                                              return { cat: 'SHOPPING', sub: 'bicycle_shops' };
  return null;
}

function refineProfessional(p) {
  const n = nameLower(p);
  const sec = (p.secondary_categories||[]).map(s => String(s).toLowerCase()).join(' ');
  const all = `${n} ${sec}`;
  if (/\b(law|attorney|esq\.?|legal|paralegal|barrister)\b/.test(all))         return { sub: 'law_offices' };
  if (/\b(real estate|realty|realtor|sotheby|coldwell|re\/max)\b/.test(all))  return { sub: 'real_estate' };
  if (/\b(insurance|allstate|geico|state farm)\b/.test(all))                  return { sub: 'insurance' };
  if (/turbotax|\btax\b|\bcpa\b|accounting|accountant|h&r block|h ?& ?r/.test(all)) return { sub: 'tax_advisors' };
  return { sub: 'companies' };
}

function refineHealth(p) {
  const n = nameLower(p);
  if (/\b(dental|dentist|dds|orthodont|endodont|periodont)\b/.test(n)) return { sub: 'dentists' };
  if (/\b(veterinar|animal hospital|animal clinic|vet\b)/.test(n))     return { sub: 'veterinary' };
  if (/\b(pharmacy|cvs|rite aid|walgreens)\b/.test(n))                 return { sub: 'pharmacies' };
  if (/\b(hospital)\b/.test(n))                                        return { sub: 'hospitals' };
  if (/\b(nursing|assisted living|hospice|rehab)\b/.test(n))           return { sub: 'nursing_homes' };
  if (/\b(clinic|urgent care)\b/.test(n))                              return { sub: 'clinics' };
  return { sub: 'doctors' };
}

function refineEducation(p) {
  const n = nameLower(p);
  if (/\b(library)\b/.test(n))                                  return { sub: 'libraries' };
  if (/\b(preschool|daycare|child ?care)\b/.test(n))            return { sub: 'childcare' };
  if (/\b(kindergarten)\b/.test(n))                             return { sub: 'kindergartens' };
  if (/\b(college|university|institute)\b/.test(n))             return { sub: 'universities' };
  return { sub: 'schools' };
}

function refineLodging(p) {
  const n = nameLower(p);
  if (/\b(motel)\b/.test(n))                  return { sub: 'motels' };
  if (/\b(hostel)\b/.test(n))                 return { sub: 'hostels' };
  if (/\b(guest house|guesthouse|b&b|bed and breakfast|inn)\b/.test(n)) return { sub: 'guest_houses' };
  if (/\b(campground|rv park)\b/.test(n))     return { sub: 'campgrounds' };
  return { sub: 'hotels' };
}

function refineTour(p) {
  const n = nameLower(p);
  if (/\b(haunted|ghost|cemetery|paranormal)\b/.test(n))     return { sub: 'haunted_tours' };
  if (/\b(history|historical|colonial|witch trial)\b/.test(n)) return { sub: 'historical_tours' };
  if (/\b(night|lantern|after dark|after-dark)\b/.test(n))   return { sub: 'night_tours' };
  return { sub: 'walking_tours' };
}

function refinePerformance(p) {
  const n = nameLower(p);
  if (/\b(theater|theatre|opera)\b/.test(n))                 return { sub: 'theatres' };
  if (/\b(stadium)\b/.test(n))                               return { sub: 'stadiums' };
  if (/\b(arena|amphitheater)\b/.test(n))                    return { sub: 'event_venues' };
  return { sub: 'event_venues' };
}

// ─── SI poi-export cache ────────────────────────────────────────────────────

const SI_BASE = process.env.SALEM_INTELLIGENCE_URL || 'http://localhost:8089';
const SI_TTL_MS = 5 * 60_000;
let _siCache = { ts: 0, byId: new Map(), all: [] };

async function loadSiPois() {
  if (Date.now() - _siCache.ts < SI_TTL_MS && _siCache.all.length > 0) return _siCache;
  const url = `${SI_BASE}/api/intel/poi-export`;
  const res = await fetch(url, { signal: AbortSignal.timeout(15_000) });
  if (!res.ok) throw new Error(`SI poi-export: HTTP ${res.status}`);
  const body = await res.json();
  const all = Array.isArray(body?.pois) ? body.pois : [];
  const byId = new Map();
  for (const p of all) {
    if (p.entity_id) byId.set(p.entity_id, p);
  }
  _siCache = { ts: Date.now(), byId, all };
  return _siCache;
}

// ─── Proposal engine ────────────────────────────────────────────────────────

function proposeFromSi(si) {
  // Name-override layer runs first — catches definitive name keywords
  // (auto repair, self storage, etc.) regardless of how SI categorized them.
  const ov = nameOverride(si);
  if (ov) return { cat: ov.cat, sub: ov.sub, source: 'name_override', baseConf: 0.95 };

  const primary = si?.primary_category;
  if (!primary) return null;
  const map = SI_PRIMARY_MAP[primary];
  if (!map) return null;
  // direct sub
  if (map.sub) {
    return { cat: map.cat, sub: map.sub, source: `si.primary=${primary}`, baseConf: 1.0 };
  }
  // refiner — may override the parent category (e.g. service_professional
  // names like "Auto Repair" rerouted to AUTO_SERVICES).
  if (map.refiner) {
    const r = map.refiner(si);
    if (!r || !r.sub) return null;
    const cat = r.cat || map.cat;
    return { cat, sub: r.sub, source: `si.primary=${primary}+refined`, baseConf: 0.9 };
  }
  return null;
}

function haversineKm(lat1, lng1, lat2, lng2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLng = (lng2 - lng1) * Math.PI / 180;
  const a = Math.sin(dLat/2)**2
    + Math.cos(lat1*Math.PI/180) * Math.cos(lat2*Math.PI/180) * Math.sin(dLng/2)**2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function nameTokens(s) {
  return (s || '').toLowerCase()
    .replace(/[^a-z0-9 ]+/g, ' ')
    .split(/\s+/)
    .filter(t => t.length >= 3 && !STOPWORDS.has(t));
}
const STOPWORDS = new Set(['the','and','of','for','llc','inc','co','corp','co.','llp','ltd','salem','ma','massachusetts','street','st','road','rd','ave','avenue']);

function fuzzyMatchSi(lmaPoi, siAll) {
  // Prefer SI rows within 200m AND with shared name tokens.
  const tokens = new Set(nameTokens(lmaPoi.name));
  if (tokens.size === 0) return null;
  let best = null;
  for (const si of siAll) {
    if (typeof si.latitude !== 'number' || typeof si.longitude !== 'number') continue;
    const km = haversineKm(lmaPoi.lat, lmaPoi.lng, si.latitude, si.longitude);
    if (km > 0.2) continue; // 200 m gate
    const siTokens = new Set(nameTokens(si.display_name));
    let overlap = 0;
    for (const t of tokens) if (siTokens.has(t)) overlap++;
    if (overlap === 0) continue;
    const score = overlap / Math.max(tokens.size, siTokens.size);
    if (!best || score > best.score) best = { si, score, km };
  }
  return best;
}

// Resolve a (cat, sub) pair against the registry; apply aliases. Returns
// the FK-valid full id ("CATEGORY__sub") or null if not registered.
function resolveRegistryId(registry, cat, sub) {
  const valid = registry.byCat.get(cat);
  if (!valid) return null;
  const direct = `${cat}__${sub}`;
  if (valid.has(direct)) return direct;
  // alias?
  const aliasMap = SUB_TOKEN_ALIASES[cat];
  if (aliasMap && aliasMap[sub]) {
    const aliased = `${cat}__${aliasMap[sub]}`;
    if (valid.has(aliased)) return aliased;
  }
  return null;
}

let _dropCounter = {};
let _flowCounter = {};

async function proposeForPois(pgPool) {
  _dropCounter = {};
  _flowCounter = { total: 0, no_intel_no_fuzzy: 0, no_proposal: 0, hist_civic_skip: 0, registry_skip: 0, already_correct: 0, proposed: 0 };
  const cache = await loadSiPois();
  const registry = await loadRegistry(pgPool);
  const { rows: lmaRows } = await pgPool.query(`
    SELECT id, name, lat, lng, category, subcategory, intel_entity_id
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND COALESCE(is_tour_poi,false) = false
      AND category = ANY($1)
    ORDER BY category, name
  `, [SCOPE_CATEGORIES]);

  const proposals = [];
  for (const r of lmaRows) {
    _flowCounter.total++;
    let prop = null;
    let matchKind = null;
    if (r.intel_entity_id && cache.byId.has(r.intel_entity_id)) {
      const si = cache.byId.get(r.intel_entity_id);
      prop = proposeFromSi(si);
      matchKind = 'intel_id';
    } else {
      const fuzzy = fuzzyMatchSi(r, cache.all);
      if (fuzzy) {
        prop = proposeFromSi(fuzzy.si);
        if (prop) {
          // Multiply base confidence by fuzzy score; cap below intel_id path.
          prop.baseConf = Math.min(0.6, prop.baseConf * fuzzy.score);
          prop.source += `+fuzzy(km=${fuzzy.km.toFixed(2)},score=${fuzzy.score.toFixed(2)})`;
          matchKind = 'fuzzy';
        }
      } else {
        _flowCounter.no_intel_no_fuzzy++;
      }
    }
    if (!prop) { _flowCounter.no_proposal++; continue; }
    if (prop.cat === 'HISTORICAL_BUILDINGS') { _flowCounter.hist_civic_skip++; continue; }
    if (prop.cat === 'CIVIC') { _flowCounter.hist_civic_skip++; continue; }

    // Validate against the FK registry. If neither the direct nor the alias
    // form is registered, drop the proposal — applying it would otherwise
    // 500 with salem_pois_subcategory_fkey.
    const expectedSub = resolveRegistryId(registry, prop.cat, prop.sub);
    if (!expectedSub) {
      _dropCounter[`${prop.cat}__${prop.sub}`] = (_dropCounter[`${prop.cat}__${prop.sub}`] || 0) + 1;
      _flowCounter.registry_skip++;
      continue;
    }
    const currentSub = r.subcategory || null;
    if (currentSub === expectedSub && r.category === prop.cat) { _flowCounter.already_correct++; continue; }
    _flowCounter.proposed++;

    let confidence = prop.baseConf;
    if (r.category !== prop.cat) {
      // proposing a category change — drop confidence
      confidence = Math.min(confidence, 0.7);
    }

    proposals.push({
      poi_id: r.id,
      poi_name: r.name,
      lat: r.lat,
      lng: r.lng,
      current_category: r.category,
      current_subcategory: currentSub,
      proposed_category: prop.cat,
      proposed_subcategory: expectedSub,
      confidence,
      match_kind: matchKind,
      source: prop.source,
      auto_apply_eligible: confidence >= 0.9 && r.category === prop.cat,
    });
  }
  return proposals;
}

async function applyProposals(pgPool, proposals) {
  // Transactional all-or-nothing. Pre-S205 builds applied UPDATEs sequentially
  // outside a transaction; one bad sub mid-loop left the prefix updated and
  // the suffix untouched (TOUR_COMPANIES__historical_tours FK error halted
  // the apply after ~700 rows were already changed). With BEGIN/COMMIT,
  // a single FK violation rolls everything back so the operator either gets
  // a clean apply or a clean failure.
  const client = await pgPool.connect();
  try {
    await client.query('BEGIN');
    let applied = 0;
    for (const p of proposals) {
      const { rowCount } = await client.query(`
        UPDATE salem_pois
        SET category = $1, subcategory = $2, updated_at = NOW()
        WHERE id = $3
          AND deleted_at IS NULL
      `, [p.proposed_category, p.proposed_subcategory, p.poi_id]);
      applied += rowCount;
    }
    await client.query('COMMIT');
    return applied;
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    throw e;
  } finally {
    client.release();
  }
}

// ─── HTTP wiring ────────────────────────────────────────────────────────────

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // POST /admin/salem/auto-categorize
  // Body: { dry_run?: boolean }  default: false
  // Behavior:
  //   - Build proposals.
  //   - If !dry_run, auto-apply confidence ≥ 0.9 same-category proposals.
  //   - Return { auto_applied, review: [...remaining] }.
  app.post('/admin/salem/auto-categorize', requirePg, async (req, res) => {
    try {
      const dryRun = !!(req.body && req.body.dry_run);
      const proposals = await proposeForPois(pgPool);
      const eligible = proposals.filter(p => p.auto_apply_eligible);
      const review = proposals.filter(p => !p.auto_apply_eligible);

      let autoApplied = 0;
      if (!dryRun && eligible.length > 0) {
        autoApplied = await applyProposals(pgPool, eligible);
      }
      // Top dropped (cat__sub) tokens — diagnostic only, helps the operator see
      // which refiner outputs need an alias added or the registry extended.
      const dropped = Object.entries(_dropCounter)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 20)
        .map(([k, n]) => ({ id: k, count: n }));
      res.json({
        generated_at: new Date().toISOString(),
        total_proposals: proposals.length,
        auto_applied: autoApplied,
        auto_eligible_count: eligible.length,
        review_count: review.length,
        dropped_unregistered: dropped,
        flow: _flowCounter,
        review,
        dry_run: dryRun,
      });
    } catch (e) {
      console.error('[admin-suggest-categorization] auto-categorize failed:', e);
      res.status(500).json({ error: e.message });
    }
  });

  // POST /admin/salem/apply-categorization
  // Body: { changes: [{ poi_id, proposed_category, proposed_subcategory }, ...] }
  // Operator-confirmed batch apply (used by review UI).
  app.post('/admin/salem/apply-categorization', requirePg, async (req, res) => {
    try {
      const changes = Array.isArray(req.body?.changes) ? req.body.changes : [];
      if (changes.length === 0) return res.status(400).json({ error: 'no changes' });
      const applied = await applyProposals(pgPool, changes);
      res.json({ applied, requested: changes.length });
    } catch (e) {
      console.error('[admin-suggest-categorization] apply failed:', e);
      res.status(500).json({ error: e.message });
    }
  });

  console.log('AdminSuggest: POST /admin/salem/auto-categorize, /admin/salem/apply-categorization (Basic Auth)');
};
