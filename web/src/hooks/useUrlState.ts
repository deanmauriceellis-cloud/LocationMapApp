import { useCallback, useRef } from 'react'

interface UrlParams {
  lat?: number
  lon?: number
  z?: number
  poiType?: string
  poiId?: string
}

export function parseUrlState(): UrlParams {
  const params = new URLSearchParams(window.location.search)
  const result: UrlParams = {}

  const lat = Number(params.get('lat'))
  const lon = Number(params.get('lon'))
  const z = Number(params.get('z'))

  if (!isNaN(lat) && lat >= -90 && lat <= 90) result.lat = lat
  if (!isNaN(lon) && lon >= -180 && lon <= 180) result.lon = lon
  if (!isNaN(z) && z >= 1 && z <= 19) result.z = Math.round(z)

  const poi = params.get('poi')
  if (poi) {
    const match = poi.match(/^(node|way|relation)\/(\d+)$/)
    if (match) {
      result.poiType = match[1]
      result.poiId = match[2]
    }
  }

  return result
}

export function useUrlState() {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const hasWritten = useRef(false)

  const updateMapPosition = useCallback((lat: number, lon: number, zoom: number) => {
    // Don't write URL until we've moved away from (0,0) default — prevents poisoning on cold start
    if (!hasWritten.current && Math.abs(lat) < 1 && Math.abs(lon) < 1) return
    hasWritten.current = true
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => {
      const params = new URLSearchParams(window.location.search)
      params.set('lat', lat.toFixed(4))
      params.set('lon', lon.toFixed(4))
      params.set('z', String(Math.round(zoom)))
      window.history.replaceState(null, '', `?${params}`)
    }, 500)
  }, [])

  const setPoiParam = useCallback((type: string, id: number | string) => {
    const params = new URLSearchParams(window.location.search)
    params.set('poi', `${type}/${id}`)
    window.history.replaceState(null, '', `?${params}`)
  }, [])

  const clearPoiParam = useCallback(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.has('poi')) {
      params.delete('poi')
      const qs = params.toString()
      window.history.replaceState(null, '', qs ? `?${qs}` : window.location.pathname)
    }
  }, [])

  return { updateMapPosition, setPoiParam, clearPoiParam }
}
