// (C) Dean Maurice Ellis, 2026 - Module aircraft.ts

/** Altitude color mapping — matches Android app */
export function getAltitudeColor(altMeters: number | null, onGround: boolean): string {
  if (onGround || altMeters == null) return '#9E9E9E' // gray
  const altFt = altMeters * 3.28084
  if (altFt < 5000) return '#4CAF50'   // green
  if (altFt < 20000) return '#2196F3'  // blue
  return '#9C27B0'                      // purple
}

/** Heading → compass direction */
export function headingToCompass(deg: number | null): string {
  if (deg == null) return '—'
  const dirs = ['N', 'NNE', 'NE', 'ENE', 'E', 'ESE', 'SE', 'SSE', 'S', 'SSW', 'SW', 'WSW', 'W', 'WNW', 'NW', 'NNW']
  return dirs[Math.round(deg / 22.5) % 16]
}

/** m/s → mph */
export function msToMph(ms: number | null): string {
  if (ms == null) return '—'
  return `${Math.round(ms * 2.237)} mph`
}

/** meters → feet */
export function metersToFeet(m: number | null): string {
  if (m == null) return '—'
  return `${Math.round(m * 3.28084).toLocaleString()} ft`
}

/** m/s → ft/min */
export function vertRateToFpm(ms: number | null): string {
  if (ms == null) return '—'
  const fpm = Math.round(ms * 196.85)
  return `${fpm > 0 ? '+' : ''}${fpm} ft/min`
}

/** Emergency squawk codes */
export function isEmergencySquawk(squawk: string | null): boolean {
  return squawk === '7500' || squawk === '7600' || squawk === '7700'
}

/** Generate aircraft DivIcon HTML — rotated airplane SVG with optional callsign label */
export function aircraftIconHtml(
  heading: number | null,
  color: string,
  callsign: string,
  spi: boolean,
): string {
  const rotation = heading != null ? heading : 0
  const spiRing = spi
    ? `<div style="position:absolute;top:-4px;left:-4px;width:24px;height:24px;border:2px solid #EF4444;border-radius:50%;"></div>`
    : ''
  return `
    <div class="aircraft-icon" style="position:relative;width:16px;height:16px;">
      ${spiRing}
      <svg viewBox="0 0 24 24" width="16" height="16" style="transform:rotate(${rotation}deg);fill:${color};">
        <path d="M12 2L8 9H3l2 3.5L3 16h5l4 6 4-6h5l-2-3.5L21 9h-5L12 2z"/>
      </svg>
    </div>
    <div class="aircraft-label">${callsign}</div>
  `
}
