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

// Weather types — matching proxy /weather response (cache-proxy/lib/weather.js)
export interface WeatherLocation {
  city: string
  state: string
  station: string
}

export interface WeatherCurrent {
  temperature: number | null
  temperatureUnit: string
  humidity: number | null
  windSpeed: number | null
  windDirection: string | null
  windChill: number | null
  heatIndex: number | null
  dewpoint: number | null
  description: string
  iconCode: string
  isDaytime: boolean
  visibility: number | null
  barometer: number | null
}

export interface WeatherHourly {
  time: string
  temperature: number
  windSpeed: string
  windDirection: string
  precipProbability: number
  shortForecast: string
  iconCode: string
  isDaytime: boolean
}

export interface WeatherDaily {
  name: string
  isDaytime: boolean
  temperature: number
  windSpeed: string
  shortForecast: string
  detailedForecast: string
  iconCode: string
  precipProbability: number
}

export interface WeatherAlert {
  id: string
  event: string
  severity: string
  urgency: string
  headline: string
  description: string
  instruction: string
  effective: string
  expires: string
  areaDesc: string
}

export interface WeatherData {
  location: WeatherLocation
  current: WeatherCurrent | null
  hourly: WeatherHourly[]
  daily: WeatherDaily[]
  alerts: WeatherAlert[]
  fetchedAt: string
}

// METAR types — passthrough from aviationweather.gov API
export interface MetarStation {
  icaoId: string
  lat: number
  lon: number
  temp: number | null
  dewp: number | null
  wdir: number | null
  wspd: number | null
  wgst: number | null
  visib: string
  fltCat: string
  rawOb: string
  obsTime: string
  altim: number | null
}
