// (C) Dean Maurice Ellis, 2026 - Module transit.ts

/** MBTA route colors by route ID prefix / type */
const ROUTE_COLORS: Record<string, string> = {
  'Red': '#DA291C',
  'Orange': '#ED8B00',
  'Blue': '#003DA5',
  'Green-B': '#00843D', 'Green-C': '#00843D', 'Green-D': '#00843D', 'Green-E': '#00843D', 'Green': '#00843D',
  'Mattapan': '#DA291C',
}

const CR_COLOR = '#80276C'  // commuter rail purple
const BUS_COLOR = '#FFC72C' // bus yellow

export function getRouteColor(routeId: string, routeType: number): string {
  if (ROUTE_COLORS[routeId]) return ROUTE_COLORS[routeId]
  if (routeType === 2) return CR_COLOR         // commuter rail
  if (routeType === 3) return BUS_COLOR        // bus
  if (routeType === 0 || routeType === 1) {    // light rail / heavy rail
    return ROUTE_COLORS[routeId] || '#6B7280'
  }
  return '#6B7280'
}

export function routeTypeLabel(type: number): string {
  switch (type) {
    case 0: return 'Light Rail'
    case 1: return 'Heavy Rail'
    case 2: return 'Commuter Rail'
    case 3: return 'Bus'
    default: return 'Transit'
  }
}

export function vehicleStatusLabel(status: string): string {
  switch (status) {
    case 'INCOMING_AT': return 'Arriving at'
    case 'STOPPED_AT': return 'Stopped at'
    case 'IN_TRANSIT_TO': return 'En route to'
    default: return status || 'In transit'
  }
}

/** Train icon — colored circle with "T" */
export function trainIconHtml(color: string, _bearing: number | null, label: string): string {
  return `
    <div class="transit-icon" style="position:relative;width:20px;height:20px;">
      <svg viewBox="0 0 20 20" width="20" height="20">
        <circle cx="10" cy="10" r="9" fill="${color}" stroke="#fff" stroke-width="1.5"/>
        <text x="10" y="14.5" text-anchor="middle" fill="#fff" font-size="11" font-weight="bold" font-family="Arial,sans-serif">T</text>
      </svg>
    </div>
    <div class="transit-label">${label}</div>
  `
}

/** Bus icon — colored circle with "B" */
export function busIconHtml(color: string, _bearing: number | null, label: string): string {
  return `
    <div class="transit-icon" style="position:relative;width:18px;height:18px;">
      <svg viewBox="0 0 18 18" width="18" height="18">
        <circle cx="9" cy="9" r="8" fill="${color}" stroke="#fff" stroke-width="1.5"/>
        <text x="9" y="13" text-anchor="middle" fill="#fff" font-size="10" font-weight="bold" font-family="Arial,sans-serif">B</text>
      </svg>
    </div>
    <div class="transit-label">${label}</div>
  `
}
