// poiAdminFields — Phase 9P.B Step 9P.10
//
// TypeScript mirror of the field whitelists in cache-proxy/lib/admin-pois.js.
// The cache-proxy PUT /admin/salem/pois/:kind/:id endpoint refuses any field
// not in the per-kind whitelist; this file lets the React form constrain its
// payload to the same set so the two stay aligned.
//
// ⚠️  When you add a column to a whitelist on the server, add it here too.
//     The two files are intentionally line-aligned where possible.

export type PoiKind = 'tour' | 'business' | 'narration'

// ─── Whitelists (mirror of cache-proxy/lib/admin-pois.js lines 49-78) ────────

export const TOUR_FIELDS = [
  'name', 'lat', 'lng', 'address', 'category', 'subcategories',
  'short_narration', 'long_narration', 'description', 'historical_period',
  'admission_info', 'hours', 'phone', 'website', 'image_asset',
  'geofence_radius_m', 'requires_transportation', 'wheelchair_accessible',
  'seasonal', 'priority',
  'data_source', 'confidence', 'verified_date', 'stale_after',
] as const

export const BUSINESS_FIELDS = [
  'name', 'lat', 'lng', 'address', 'business_type', 'cuisine_type',
  'price_range', 'hours', 'phone', 'website', 'description',
  'historical_note', 'tags', 'rating', 'image_asset',
  'data_source', 'confidence', 'verified_date', 'stale_after',
] as const

export const NARRATION_FIELDS = [
  'name', 'lat', 'lng', 'address', 'category', 'subcategory',
  'short_narration', 'long_narration', 'description',
  'pass1_narration', 'pass2_narration', 'pass3_narration',
  'geofence_radius_m', 'geofence_shape', 'corridor_points',
  'priority', 'wave', 'phone', 'website', 'hours',
  'image_asset', 'voice_clip_asset',
  'related_figure_ids', 'related_fact_ids', 'related_source_ids',
  'action_buttons',
  'merchant_tier', 'ad_priority',
  'custom_icon_asset', 'custom_voice_asset', 'custom_description',
  'source_id', 'source_categories', 'tags',
  'data_source', 'confidence', 'verified_date', 'stale_after',
] as const

export const FIELDS_BY_KIND: Record<PoiKind, readonly string[]> = {
  tour: TOUR_FIELDS,
  business: BUSINESS_FIELDS,
  narration: NARRATION_FIELDS,
}

// Columns that hold JSONB and need JSON.parse on form submit / JSON.stringify
// when populating defaults. Mirror of admin-pois.js JSONB_FIELDS.
export const JSONB_FIELDS: ReadonlySet<string> = new Set([
  'subcategories', 'tags', 'related_figure_ids', 'related_fact_ids',
  'related_source_ids', 'action_buttons', 'source_categories',
])

// Boolean columns (rendered as checkboxes). Tour-only.
export const BOOLEAN_FIELDS: ReadonlySet<string> = new Set([
  'requires_transportation', 'wheelchair_accessible', 'seasonal',
])

// Numeric columns (rendered as <input type="number">).
export const NUMERIC_FIELDS: ReadonlySet<string> = new Set([
  'lat', 'lng', 'geofence_radius_m', 'priority', 'wave', 'rating',
  'confidence', 'ad_priority',
])

// Date columns (rendered as <input type="date">). PG returns ISO strings;
// the date input wants YYYY-MM-DD which is the leading 10 chars of the ISO.
export const DATE_FIELDS: ReadonlySet<string> = new Set([
  'verified_date', 'stale_after',
])
