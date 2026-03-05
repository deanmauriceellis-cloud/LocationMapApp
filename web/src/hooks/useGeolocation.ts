import { useState, useEffect, useCallback } from 'react'

interface GeoState {
  lat: number
  lon: number
  accuracy: number | null
  loading: boolean
  error: string | null
}

const BOSTON_DEFAULT = { lat: 42.3601, lon: -71.0589 }

export function useGeolocation() {
  const [state, setState] = useState<GeoState>({
    ...BOSTON_DEFAULT,
    accuracy: null,
    loading: true,
    error: null,
  })

  const locate = useCallback(() => {
    if (!navigator.geolocation) {
      setState(s => ({ ...s, loading: false, error: 'Geolocation not supported' }))
      return
    }
    setState(s => ({ ...s, loading: true, error: null }))
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setState({
          lat: pos.coords.latitude,
          lon: pos.coords.longitude,
          accuracy: pos.coords.accuracy,
          loading: false,
          error: null,
        })
      },
      (err) => {
        setState(s => ({ ...s, loading: false, error: err.message }))
      },
      { enableHighAccuracy: true, timeout: 10000 }
    )
  }, [])

  useEffect(() => { locate() }, [locate])

  return { ...state, locate }
}
