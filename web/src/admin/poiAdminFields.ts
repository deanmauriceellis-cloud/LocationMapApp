// poiAdminFields — Phase 9U (unified salem_pois table)
//
// TypeScript mirror of the field whitelist in cache-proxy/lib/admin-pois.js.
// The cache-proxy PUT /admin/salem/pois/:id endpoint refuses any field
// not in the whitelist; this file lets the React form constrain its
// payload to the same set so the two stay aligned.
//
// ⚠️  When you add a column to the whitelist on the server, add it here too.

// ─── Unified whitelist (mirror of cache-proxy/lib/admin-pois.js) ────────────

export const UPDATABLE_FIELDS = [
  // Core identity
  'name', 'lat', 'lng', 'address', 'status',
  // Taxonomy
  'category', 'subcategory',
  // Narration
  'short_narration', 'long_narration',
  'geofence_radius_m', 'geofence_shape', 'corridor_points',
  'priority', 'wave',
  'voice_clip_asset', 'custom_voice_asset',
  // Business
  'cuisine_type', 'price_range', 'rating',
  'merchant_tier', 'ad_priority',
  // Historical/tour
  'historical_period', 'admission_info',
  'requires_transportation', 'wheelchair_accessible', 'seasonal',
  // Contact/hours
  'phone', 'email', 'website',
  'hours', 'hours_text',
  'menu_url', 'reservations_url', 'order_url',
  // Content
  'description', 'short_description', 'custom_description', 'origin_story',
  'image_asset', 'custom_icon_asset',
  'action_buttons',
  // SalemIntelligence enrichment
  'intel_entity_id', 'secondary_categories', 'specialties',
  'owners', 'year_established', 'amenities', 'district',
  // Relations
  'related_figure_ids', 'related_fact_ids', 'related_source_ids',
  'source_id', 'source_categories', 'tags',
  // Provenance
  'data_source', 'confidence', 'verified_date', 'stale_after',
  // Flags
  'is_tour_poi', 'is_civic_poi', 'is_narrated', 'default_visible',
  // Phase 9Y — MassGIS / MHC Inventory / L3 parcel enrichment
  'building_footprint_geojson',
  'mhc_id', 'mhc_year_built', 'mhc_style', 'mhc_nr_status', 'mhc_narrative',
  'canonical_address_point_id', 'local_historic_district', 'parcel_owner_class',
  // S162 — POI location verification (TigerLine/MassGIS workflow). Verifier
  // owns lat_proposed/lng_proposed and the provenance columns; only the two
  // human-editable flags are in the PUT whitelist. Use the
  // POST /admin/salem/pois/:id/accept-proposed-location endpoint to commit
  // a proposal — it's not a PUT.
  'location_truth_of_record', 'location_status',
] as const

// Columns that hold JSONB and need JSON.parse on form submit / JSON.stringify
// when populating defaults. Mirror of admin-pois.js JSONB_FIELDS.
export const JSONB_FIELDS: ReadonlySet<string> = new Set([
  'hours', 'action_buttons',
  'secondary_categories', 'specialties', 'owners', 'amenities',
  'related_figure_ids', 'related_fact_ids', 'related_source_ids',
  'source_categories', 'tags',
])

// Boolean columns (rendered as checkboxes).
export const BOOLEAN_FIELDS: ReadonlySet<string> = new Set([
  'requires_transportation', 'wheelchair_accessible', 'seasonal',
  'is_tour_poi', 'is_civic_poi', 'is_narrated', 'default_visible',
  'location_truth_of_record',
])

// Numeric columns (rendered as <input type="number">).
export const NUMERIC_FIELDS: ReadonlySet<string> = new Set([
  'lat', 'lng', 'geofence_radius_m', 'priority', 'wave', 'rating',
  'confidence', 'ad_priority', 'merchant_tier', 'year_established',
  'mhc_year_built',
])

// Date columns (rendered as <input type="date">). PG returns ISO strings;
// the date input wants YYYY-MM-DD which is the leading 10 chars of the ISO.
export const DATE_FIELDS: ReadonlySet<string> = new Set([
  'verified_date', 'stale_after',
])
