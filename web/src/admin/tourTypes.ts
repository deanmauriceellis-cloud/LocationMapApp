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
  /** S193: tours flagged historical play `salem_pois.historical_narration` instead of short/long. */
  is_historical_tour: boolean | null
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
// S190 — per-leg routing diagnostics. Returned by both compute-route
// (every leg) and recompute-leg (single leg) so the operator can spot
// suspicious detours (ratio > 1.6, big-detour-on-small-hop) without
// reading the cache-proxy log.
export interface LegDiagnostics {
  straight_m: number | null
  routed_m: number | null
  duration_s: number | null
  detour_ratio: number | null
  point_count: number
  elapsed_ms: number
  suspicious: boolean
}

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
  // S190 — present on rows returned by compute-route / recompute-leg
  // (not by the read-only /legs endpoint, since diagnostics aren't
  // persisted to salem_tour_legs).
  diagnostics?: LegDiagnostics
  from_label?: string
  to_label?: string
  preserved?: boolean
}

export interface TourLegsResponse {
  tour_id: string
  count: number
  legs: TourLeg[]
}

export interface ComputeRouteResponse {
  tour_id: string
  leg_count: number
  skipped: Array<{
    leg_order: number
    reason: string
    from?: string
    to?: string
    straight_m?: number
  }>
  total_distance_m: number
  total_duration_s: number
  router_version: string | null
  force: boolean
  /** S190 — count of legs flagged as suspicious by the router-diagnostic heuristic. */
  suspicious_count?: number
  legs: TourLeg[]
}
