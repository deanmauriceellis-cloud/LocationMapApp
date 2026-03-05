// (C) Dean Maurice Ellis, 2026 - Module useAircraft.ts
import { useState, useRef, useCallback } from 'react'
import { apiFetch, API_BASE } from '@/config/api'
import type { AircraftState, AircraftHistory, BboxParams } from '@/lib/types'

const REFRESH_INTERVAL = 15_000 // 15s

/** Parse OpenSky state vector array into typed object */
function parseStateVector(s: unknown[]): AircraftState {
  return {
    icao24: s[0] as string,
    callsign: ((s[1] as string) || '').trim(),
    originCountry: (s[2] as string) || '',
    timePosition: s[3] as number | null,
    lastContact: s[4] as number,
    lon: s[5] as number,
    lat: s[6] as number,
    baroAlt: s[7] as number | null,
    onGround: s[8] as boolean,
    velocity: s[9] as number | null,
    track: s[10] as number | null,
    vertRate: s[11] as number | null,
    sensors: s[12] as number[] | null,
    geoAlt: s[13] as number | null,
    squawk: s[14] as string | null,
    spi: s[15] as boolean,
    posSource: s[16] as number,
    category: s[17] as number,
  }
}

export function useAircraft() {
  const [aircraft, setAircraft] = useState<AircraftState[]>([])
  const [visible, setVisible] = useState(false)
  const [selectedAircraft, setSelectedAircraft] = useState<AircraftState | null>(null)
  const [history, setHistory] = useState<AircraftHistory | null>(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [following, setFollowing] = useState(false)

  const abortRef = useRef<AbortController | null>(null)
  const refreshRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const lastBboxRef = useRef<BboxParams | null>(null)

  const fetchAircraft = useCallback(async (bbox: BboxParams) => {
    lastBboxRef.current = bbox
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    try {
      const bboxStr = `${bbox.s.toFixed(2)},${bbox.w.toFixed(2)},${bbox.n.toFixed(2)},${bbox.e.toFixed(2)}`
      const res = await fetch(`${API_BASE}/aircraft?bbox=${bboxStr}`, { signal: controller.signal })
      if (!res.ok || controller.signal.aborted) return
      const data = await res.json()
      if (!controller.signal.aborted) {
        const states: AircraftState[] = (data.states || [])
          .filter((s: unknown[]) => s[5] != null && s[6] != null)
          .map(parseStateVector)
        setAircraft(states)
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === 'AbortError') return
      console.error('Aircraft fetch error:', e)
    }
  }, [])

  const startAutoRefresh = useCallback((bbox: BboxParams) => {
    if (refreshRef.current) clearInterval(refreshRef.current)
    refreshRef.current = setInterval(() => {
      const b = lastBboxRef.current
      if (b) fetchAircraft(b)
    }, REFRESH_INTERVAL)
  }, [fetchAircraft])

  const stopAutoRefresh = useCallback(() => {
    if (refreshRef.current) {
      clearInterval(refreshRef.current)
      refreshRef.current = null
    }
  }, [])

  const toggleVisible = useCallback(() => {
    setVisible(prev => {
      if (prev) {
        // Turning off — stop refresh, clear data
        if (refreshRef.current) {
          clearInterval(refreshRef.current)
          refreshRef.current = null
        }
        setAircraft([])
        setSelectedAircraft(null)
        setHistory(null)
        setFollowing(false)
      }
      return !prev
    })
  }, [])

  const selectAircraft = useCallback(async (ac: AircraftState) => {
    setSelectedAircraft(ac)
    setHistory(null)
    setHistoryLoading(true)
    try {
      const data = await apiFetch<AircraftHistory>(`/db/aircraft/${ac.icao24}`)
      setHistory(data)
    } catch {
      // No history in DB — that's OK
    } finally {
      setHistoryLoading(false)
    }
  }, [])

  const clearSelection = useCallback(() => {
    setSelectedAircraft(null)
    setHistory(null)
    setFollowing(false)
  }, [])

  const toggleFollow = useCallback(() => {
    setFollowing(f => !f)
  }, [])

  return {
    aircraft, visible, selectedAircraft, history, historyLoading, following,
    fetchAircraft, startAutoRefresh, stopAutoRefresh,
    toggleVisible, selectAircraft, clearSelection, toggleFollow,
  }
}
