const R = 6371000 // Earth radius in meters
const FT_PER_M = 3.28084
const MI_PER_M = 0.000621371

export function haversineM(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLon = (lon2 - lon1) * Math.PI / 180
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) ** 2
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

export function formatDistance(meters: number): string {
  const mi = meters * MI_PER_M
  if (mi >= 10) return `${Math.round(mi)} mi`
  if (mi >= 0.2) return `${mi.toFixed(1)} mi`
  const ft = meters * FT_PER_M
  return `${Math.round(ft / 10) * 10} ft`
}
