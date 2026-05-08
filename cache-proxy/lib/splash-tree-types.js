/*
 * S231 — splash-tree schema (single source of truth).
 *
 * The tree is intentionally FLAT-WITH-PRIORITY rather than nested. Operator's
 * mental model is "given the resolved location context, which bucket fires?";
 * the engine walks `buckets` in descending `priority`, picks the first whose
 * Trigger matches, then picks a random VariantLine from its pool. The
 * `fallback` bucket is the guaranteed catch-all for UNKNOWN (no GPS, no fix).
 *
 * Slot vocabulary recognized by the runtime (templates use `{slot}` form):
 *   miles, miles_int, city, near_city, town, county, state, state_long,
 *   compass, compass_short, movement, place
 *
 * Trigger rules: all listed fields must match the resolved LocationContext.
 *   - distance_min_mi (inclusive) and distance_max_mi (inclusive) bound the
 *     great-circle distance to Salem common.
 *   - movement: any of the listed motion classes.
 *   - place_kind: any of the listed polygon-resolver hits.
 *   - town_name: exact match against IN_TOWN_ADJACENT_TO_SALEM resolver name
 *     (e.g. "Danvers"). Used by the DANVERS override pool.
 *   - no_gps=true matches when the LocationContext is empty (no last-known).
 *     Mutually exclusive with the other fields — if no_gps is true, everything
 *     else is ignored.
 */
'use strict';

const SLOT_NAMES = [
  'miles', 'miles_int',
  'city', 'near_city', 'town', 'county',
  'state', 'state_long',
  'compass', 'compass_short',
  'movement', 'place',
];

const MOVEMENT_VALUES = ['STATIONARY', 'APPROACHING', 'DEPARTING', 'LATERAL', 'UNKNOWN'];

const PLACE_KIND_VALUES = [
  'IN_CITY',
  'NEAR_CITY',
  'IN_TOWN_ADJACENT_TO_SALEM',
  'IN_COUNTY',
  'OFFGRID',
];

const SLOT_REFERENCE_RE = /\{([a-z_]+)\}/g;

/**
 * Validate a SplashTree object. Returns { ok, errors[] }. Cheap — runs on
 * every PUT to /admin/splash/tree so the operator can't save a tree that
 * the runtime would refuse to parse.
 */
function validateTree(tree) {
  const errors = [];

  if (!tree || typeof tree !== 'object') {
    errors.push('tree must be an object');
    return { ok: false, errors };
  }
  if (tree.schema_version !== 1) {
    errors.push(`schema_version must be 1 (got ${tree.schema_version})`);
  }
  if (!Array.isArray(tree.buckets)) {
    errors.push('buckets must be an array');
    return { ok: false, errors };
  }

  const bucketIds = new Set();
  for (const b of tree.buckets) {
    validateBucket(b, errors, bucketIds);
  }
  if (!tree.fallback) {
    errors.push('fallback bucket is required (the UNKNOWN catch-all)');
  } else {
    validateBucket(tree.fallback, errors, bucketIds, /*isFallback*/ true);
  }

  return { ok: errors.length === 0, errors };
}

function validateBucket(b, errors, idSet, isFallback = false) {
  if (!b || typeof b !== 'object') { errors.push('bucket must be an object'); return; }
  if (typeof b.id !== 'string' || !b.id) { errors.push('bucket.id required'); return; }
  if (idSet.has(b.id)) errors.push(`duplicate bucket id: ${b.id}`);
  idSet.add(b.id);

  if (typeof b.label !== 'string') errors.push(`bucket ${b.id}: label must be a string`);
  if (typeof b.priority !== 'number') errors.push(`bucket ${b.id}: priority must be a number`);
  if (b.trigger && typeof b.trigger === 'object') {
    validateTrigger(b.trigger, b.id, errors, isFallback);
  } else if (!isFallback) {
    errors.push(`bucket ${b.id}: trigger required (use {} for "always match")`);
  }

  if (!Array.isArray(b.variants)) {
    errors.push(`bucket ${b.id}: variants must be an array`);
    return;
  }
  if (b.variants.length === 0) {
    errors.push(`bucket ${b.id}: at least one variant required`);
  }
  const seenVariantIds = new Set();
  for (const v of b.variants) {
    if (!v || typeof v !== 'object') { errors.push(`bucket ${b.id}: variant must be an object`); continue; }
    if (typeof v.id !== 'string' || !v.id) errors.push(`bucket ${b.id}: variant.id required`);
    if (seenVariantIds.has(v.id)) errors.push(`bucket ${b.id}: duplicate variant id ${v.id}`);
    seenVariantIds.add(v.id);
    if (typeof v.text !== 'string' || !v.text.trim()) {
      errors.push(`bucket ${b.id}/${v.id}: text required`);
      continue;
    }
    // Slot-name spell check.
    let m;
    SLOT_REFERENCE_RE.lastIndex = 0;
    while ((m = SLOT_REFERENCE_RE.exec(v.text)) != null) {
      const slot = m[1];
      if (!SLOT_NAMES.includes(slot)) {
        errors.push(`bucket ${b.id}/${v.id}: unknown slot {${slot}}`);
      }
    }
    if (v.weight != null && (typeof v.weight !== 'number' || v.weight < 0)) {
      errors.push(`bucket ${b.id}/${v.id}: weight must be a non-negative number`);
    }
  }
}

function validateTrigger(t, bucketId, errors, isFallback) {
  if (t.no_gps === true) {
    // OK — short-circuits all other fields. Only fallback should use this.
    if (!isFallback) {
      errors.push(`bucket ${bucketId}: no_gps=true is reserved for the fallback bucket`);
    }
    return;
  }
  if (t.distance_min_mi != null && typeof t.distance_min_mi !== 'number') {
    errors.push(`bucket ${bucketId}: distance_min_mi must be number`);
  }
  if (t.distance_max_mi != null && typeof t.distance_max_mi !== 'number') {
    errors.push(`bucket ${bucketId}: distance_max_mi must be number`);
  }
  if (t.distance_min_mi != null && t.distance_max_mi != null && t.distance_min_mi > t.distance_max_mi) {
    errors.push(`bucket ${bucketId}: distance_min_mi > distance_max_mi`);
  }
  if (t.movement != null) {
    if (!Array.isArray(t.movement)) errors.push(`bucket ${bucketId}: movement must be an array`);
    else for (const m of t.movement) {
      if (!MOVEMENT_VALUES.includes(m)) errors.push(`bucket ${bucketId}: unknown movement "${m}"`);
    }
  }
  if (t.place_kind != null) {
    if (!Array.isArray(t.place_kind)) errors.push(`bucket ${bucketId}: place_kind must be an array`);
    else for (const k of t.place_kind) {
      if (!PLACE_KIND_VALUES.includes(k)) errors.push(`bucket ${bucketId}: unknown place_kind "${k}"`);
    }
  }
  if (t.town_name != null && typeof t.town_name !== 'string') {
    errors.push(`bucket ${bucketId}: town_name must be a string`);
  }
}

module.exports = {
  SLOT_NAMES,
  MOVEMENT_VALUES,
  PLACE_KIND_VALUES,
  validateTree,
};
