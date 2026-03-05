import { useState, useRef, useCallback } from 'react'
import { apiFetch } from '@/config/api'
import type { WeatherData, MetarStation, BboxParams } from '@/lib/types'

const REFRESH_INTERVAL = 5 * 60 * 1000 // 5 minutes

export function useWeather() {
  const [weather, setWeather] = useState<WeatherData | null>(null)
  const [weatherLoading, setWeatherLoading] = useState(false)
  const [metars, setMetars] = useState<MetarStation[]>([])
  const [metarsVisible, setMetarsVisible] = useState(false)
  const [radarOn, setRadarOn] = useState(false)
  const [radarAnimating, setRadarAnimating] = useState(false)

  const weatherAbort = useRef<AbortController | null>(null)
  const metarAbort = useRef<AbortController | null>(null)
  const refreshTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchWeather = useCallback(async (lat: number, lon: number) => {
    weatherAbort.current?.abort()
    const controller = new AbortController()
    weatherAbort.current = controller
    setWeatherLoading(true)
    try {
      const params = new URLSearchParams({ lat: lat.toFixed(4), lon: lon.toFixed(4) })
      const data = await apiFetch<WeatherData>(`/weather?${params}`, { signal: controller.signal })
      if (!controller.signal.aborted) {
        setWeather(data)
        setWeatherLoading(false)
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      console.error('Weather fetch error:', e)
      if (!controller.signal.aborted) setWeatherLoading(false)
    }
  }, [])

  const startAutoRefresh = useCallback((lat: number, lon: number) => {
    if (refreshTimer.current) clearInterval(refreshTimer.current)
    refreshTimer.current = setInterval(() => fetchWeather(lat, lon), REFRESH_INTERVAL)
  }, [fetchWeather])

  const stopAutoRefresh = useCallback(() => {
    if (refreshTimer.current) {
      clearInterval(refreshTimer.current)
      refreshTimer.current = null
    }
  }, [])

  const fetchMetars = useCallback(async (bbox: BboxParams) => {
    metarAbort.current?.abort()
    const controller = new AbortController()
    metarAbort.current = controller
    try {
      const bboxStr = `${bbox.s.toFixed(2)},${bbox.w.toFixed(2)},${bbox.n.toFixed(2)},${bbox.e.toFixed(2)}`
      const data = await apiFetch<MetarStation[]>(`/metar?bbox=${bboxStr}`, { signal: controller.signal })
      if (!controller.signal.aborted) {
        setMetars(Array.isArray(data) ? data : [])
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      console.error('METAR fetch error:', e)
    }
  }, [])

  const toggleMetars = useCallback(() => setMetarsVisible(v => !v), [])
  const toggleRadar = useCallback(() => setRadarOn(v => !v), [])
  const toggleRadarAnimate = useCallback(() => setRadarAnimating(v => !v), [])

  return {
    weather, weatherLoading,
    metars, metarsVisible,
    radarOn, radarAnimating,
    fetchWeather, fetchMetars,
    startAutoRefresh, stopAutoRefresh,
    toggleMetars, toggleRadar, toggleRadarAnimate,
    setMetarsVisible, setRadarOn, setRadarAnimating,
  }
}
