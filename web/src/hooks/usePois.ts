import { useState, useEffect, useRef, useCallback } from 'react'
import { apiFetch } from '@/config/api'
import type { POI, BboxParams } from '@/lib/types'

export interface PoiCluster {
  lat: number
  lon: number
  count: number
  tag: string
}

interface BboxResponse {
  count: number
  elements: POI[]
  clusters: PoiCluster[] | null
}

export function usePois() {
  const [pois, setPois] = useState<POI[]>([])
  const [clusters, setClusters] = useState<PoiCluster[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [totalCount, setTotalCount] = useState(0)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const fetchPois = useCallback((bbox: BboxParams) => {
    if (timerRef.current) clearTimeout(timerRef.current)
    timerRef.current = setTimeout(async () => {
      abortRef.current?.abort()
      const controller = new AbortController()
      abortRef.current = controller
      setLoading(true)
      try {
        const params = new URLSearchParams({
          s: bbox.s.toFixed(6),
          w: bbox.w.toFixed(6),
          n: bbox.n.toFixed(6),
          e: bbox.e.toFixed(6),
        })
        const data = await apiFetch<BboxResponse>(
          `/pois/bbox?${params}`,
          { signal: controller.signal }
        )
        if (!controller.signal.aborted) {
          setPois(data.elements || [])
          setClusters(data.clusters || null)
        }
      } catch (e: unknown) {
        if (e instanceof DOMException && e.name === 'AbortError') return
        console.error('POI fetch error:', e)
      } finally {
        if (!controller.signal.aborted) setLoading(false)
      }
    }, 300)
  }, [])

  useEffect(() => {
    apiFetch<{ total: number }>('/pois/stats')
      .then(d => setTotalCount(d.total))
      .catch(() => {})
  }, [])

  return { pois, clusters, loading, totalCount, fetchPois }
}
