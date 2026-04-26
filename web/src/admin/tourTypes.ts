// Types for the admin tour editor (S174). Mirrors the shape returned by the
// cache-proxy admin routes in lib/admin-tours.js.

export interface TourSummary {
  id: string
  name: string
  theme: string
  description: string
  estimated_minutes: number
  distance_km: number
  stop_count: number
  difficulty: string | null
  seasonal: boolean | null
  icon_asset: string | null
  sort_order: number | null
  updated_at: string
  /** COUNT(*) over salem_tour_stops, computed live; may diverge from stop_count. */
  stops_actual: number
}

export interface TourStop {
  stop_id: number
  tour_id: string
  poi_id: string | null
  stop_order: number
  transition_narration: string | null
  walking_minutes_from_prev: number | null
  distance_m_from_prev: number | null
  /** Per-tour coordinate override (drag-to-reposition writes here). */
  override_lat: number | null
  override_lng: number | null
  override_name: string | null
  poi_name: string | null
  poi_lat: number | null
  poi_lng: number | null
  poi_category: string | null
  /** COALESCE(stop.lat, poi.lat) — what the map renders. */
  effective_lat: number | null
  effective_lng: number | null
  effective_name: string | null
  updated_at: string
}

export interface ToursListResponse {
  count: number
  tours: TourSummary[]
}

export interface TourDetailResponse {
  tour: TourSummary
  stops: TourStop[]
}

// ─── Tour legs (S183) ───────────────────────────────────────────────────────
//
// One row per consecutive (stop_order n → n+1) pair on a tour. Authored
// once via the admin "Compute Route" tool against the on-device router and
// baked into salem_content.db so the APK never has to route at runtime.
export interface TourLeg {
  tour_id: string
  leg_order: number
  from_stop_id: number
  to_stop_id: number
  /** Array of [lat, lng] pairs — the rendered walking polyline. */
  polyline_json: Array<[number, number]>
  distance_m: number
  duration_s: number
  router_version: string | null
  /** Null until vertices are hand-edited. */
  manual_edits: unknown | null
  computed_at: string
}

export interface TourLegsResponse {
  tour_id: string
  count: number
  legs: TourLeg[]
}

export interface ComputeRouteResponse {
  tour_id: string
  leg_count: number
  skipped: Array<{ leg_order: number; reason: string }>
  total_distance_m: number
  total_duration_s: number
  router_version: string | null
  force: boolean
  legs: TourLeg[]
}
