export interface POI {
  osm_type: string
  osm_id: number | string
  lat: number
  lon: number
  tags: Record<string, string>
  type: string
  id: number | string
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

export interface FindResult {
  type: string
  id: number | string
  lat: number
  lon: number
  name: string
  category: string
  distance_m: number
  tags: Record<string, string>
  score?: number
}


export interface WebsiteInfo {
  url: string
  source: string
  phone?: string
  hours?: string
  address?: string
}

export interface PoiDetailResponse {
  type: string
  id: number
  lat: number
  lon: number
  name: string
  category: string
  tags: Record<string, string>
  first_seen?: string
  last_seen?: string
}
