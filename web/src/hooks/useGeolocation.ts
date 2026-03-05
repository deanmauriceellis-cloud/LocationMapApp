import { useState, useEffect, useCallback } from 'react'

interface GeoState {
  lat: number
  lon: number
  accuracy: number | null
  loading: boolean
  error: string | null
}

const BOSTON_DEFAULT = { lat: 42.3601, lon: -71.0589 }
const HOME_KEY = 'homeLocation'

function getStoredHome(): { lat: number; lon: number } | null {
  try {
    const raw = localStorage.getItem(HOME_KEY)
    if (!raw) return null
    const { lat, lon } = JSON.parse(raw)
    if (typeof lat === 'number' && typeof lon === 'number') return { lat, lon }
  } catch { /* ignore */ }
  return null
}

export function useGeolocation() {
  const [hasHome, setHasHome] = useState(() => !!getStoredHome())
  const [state, setState] = useState<GeoState>(() => {
    const home = getStoredHome()
    return {
      lat: home?.lat ?? BOSTON_DEFAULT.lat,
      lon: home?.lon ?? BOSTON_DEFAULT.lon,
      accuracy: null,
      loading: !home,
      error: null,
    }
  })

  const locate = useCallback((): Promise<{ lat: number; lon: number }> => {
    return new Promise((resolve, reject) => {
      if (!navigator.geolocation) {
        setState(s => ({ ...s, loading: false, error: 'Geolocation not supported' }))
        reject(new Error('Geolocation not supported'))
        return
      }
      setState(s => ({ ...s, loading: true, error: null }))
      navigator.geolocation.getCurrentPosition(
        (pos) => {
          const coords = { lat: pos.coords.latitude, lon: pos.coords.longitude }
          setState({
            ...coords,
            accuracy: pos.coords.accuracy,
            loading: false,
            error: null,
          })
          resolve(coords)
        },
        (err) => {
          setState(s => ({ ...s, loading: false, error: err.message }))
          reject(err)
        },
        { enableHighAccuracy: true, timeout: 10000 }
      )
    })
  }, [])

  // Only auto-locate from browser GPS if no home location is set
  useEffect(() => {
    if (!getStoredHome()) locate().catch(() => {})
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const setHome = useCallback((lat: number, lon: number) => {
    localStorage.setItem(HOME_KEY, JSON.stringify({ lat, lon }))
    setHasHome(true)
  }, [])

  const clearHome = useCallback(() => {
    localStorage.removeItem(HOME_KEY)
    setHasHome(false)
    locate().catch(() => {})
  }, [locate])

  return { ...state, locate, setHome, clearHome, hasHome }
}
