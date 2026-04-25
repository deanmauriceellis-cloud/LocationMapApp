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
