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

// Aircraft types — OpenSky state vectors + DB sightings
export interface AircraftState {
  icao24: string
  callsign: string
  originCountry: string
  timePosition: number | null
  lastContact: number
  lon: number
  lat: number
  baroAlt: number | null
  onGround: boolean
  velocity: number | null
  track: number | null
  vertRate: number | null
  sensors: number[] | null
  geoAlt: number | null
  squawk: string | null
  spi: boolean
  posSource: number
  category: number
}

export interface FlightPathPoint {
  firstLat: number
  firstLon: number
  lastLat: number
  lastLon: number
  firstSeen: string
  lastSeen: string
  altitude: number | null
  heading: number | null
}

export interface AircraftSighting {
  id: number
  icao24: string
  callsign: string | null
  originCountry: string | null
  firstSeen: string
  lastSeen: string
  firstLat: number
  firstLon: number
  lastLat: number
  lastLon: number
  firstAltitude: number | null
  lastAltitude: number | null
  squawk: string | null
  onGround: boolean
}

export interface AircraftHistory {
  icao24: string
  callsigns: string[]
  originCountry: string | null
  totalSightings: number
  firstSeen: string
  lastSeen: string
  sightings: AircraftSighting[]
  path: FlightPathPoint[]
}

// MBTA transit types
export interface MbtaVehicle {
  id: string
  label: string
  routeId: string
  routeName: string
  headsign: string
  stopName: string
  tripId: string
  lat: number
  lon: number
  bearing: number | null
  speed: number | null
  status: string
  routeType: number
  updatedAt: string
}

export interface MbtaStop {
  id: string
  name: string
  lat: number
  lon: number
  routeIds: string[]
}

export interface MbtaPrediction {
  id: string
  routeId: string
  routeName: string
  headsign?: string
  stopName?: string
  stopSequence?: number
  arrivalTime: string | null
  departureTime: string | null
  status: string | null
  routeColor: string | null
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
