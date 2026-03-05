export interface POI {
  osm_type: string
  osm_id: number
  lat: number
  lon: number
  tags: Record<string, string>
  type: string
  id: number
}

export interface BboxParams {
  s: number
  w: number
  n: number
  e: number
}

export interface Category {
  id: string
  label: string
  color: string
  subtypes: Subtype[] | null
  tagMatches: TagMatch[]
}

export interface Subtype {
  label: string
  tags: Record<string, string>
}

export interface TagMatch {
  key: string
  values: string[]
}

export interface PoiStats {
  total: number
  byCategory?: Record<string, number>
}
