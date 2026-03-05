import { useState, useRef, useCallback } from 'react'
import { apiFetch } from '@/config/api'
import { getCategoryByTag } from '@/config/categories'
import type { FindResult, WebsiteInfo, PoiDetailResponse } from '@/lib/types'

export function useFind() {
  const [searchResults, setSearchResults] = useState<FindResult[]>([])
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchHint, setSearchHint] = useState<string | null>(null)
  const [categoryCounts, setCategoryCounts] = useState<Record<string, number>>({})
  const [countsTotal, setCountsTotal] = useState(0)

  const searchTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const searchAbort = useRef<AbortController | null>(null)

  const search = useCallback((query: string, lat: number, lon: number) => {
    if (searchTimer.current) clearTimeout(searchTimer.current)

    if (query.length < 2) {
      setSearchResults([])
      setSearchHint(null)
      setSearchLoading(false)
      return
    }

    setSearchLoading(true)
    searchTimer.current = setTimeout(async () => {
      searchAbort.current?.abort()
      const controller = new AbortController()
      searchAbort.current = controller
      try {
        const params = new URLSearchParams({
          q: query,
          lat: lat.toFixed(6),
          lon: lon.toFixed(6),
          limit: '200',
        })
        const data = await apiFetch<{ elements: FindResult[]; category_hint?: string }>(
          `/db/pois/search?${params}`,
          { signal: controller.signal }
        )
        if (!controller.signal.aborted) {
          setSearchResults(data.elements || [])
          setSearchHint(data.category_hint || null)
          setSearchLoading(false)
        }
      } catch (e: unknown) {
        if (e instanceof DOMException && e.name === 'AbortError') return
        console.error('Search error:', e)
        if (!controller.signal.aborted) {
          setSearchResults([])
          setSearchLoading(false)
        }
      }
    }, 1000)
  }, [])

  const clearSearch = useCallback(() => {
    if (searchTimer.current) clearTimeout(searchTimer.current)
    searchAbort.current?.abort()
    setSearchResults([])
    setSearchHint(null)
    setSearchLoading(false)
  }, [])

  const findByCategory = useCallback(async (
    lat: number, lon: number, categories: string[], limit = 200
  ): Promise<FindResult[]> => {
    const params = new URLSearchParams({
      lat: lat.toFixed(6),
      lon: lon.toFixed(6),
      categories: categories.join(','),
      limit: String(limit),
    })
    const data = await apiFetch<{ elements: FindResult[] }>(`/db/pois/find?${params}`)
    return data.elements || []
  }, [])

  const loadCounts = useCallback(async (lat: number, lon: number) => {
    try {
      const params = new URLSearchParams({
        lat: lat.toFixed(6),
        lon: lon.toFixed(6),
        radius: '10000',
      })
      const data = await apiFetch<{ counts: Record<string, number>; total: number }>(`/db/pois/counts?${params}`)
      // Aggregate tag-level counts (e.g. "amenity=restaurant") into category-level counts (e.g. "FOOD_DRINK")
      const byCat: Record<string, number> = {}
      for (const [tag, count] of Object.entries(data.counts || {})) {
        const cat = getCategoryByTag(tag)
        if (cat) {
          byCat[cat.id] = (byCat[cat.id] || 0) + count
        }
      }
      setCategoryCounts(byCat)
      setCountsTotal(data.total || 0)
    } catch (e) {
      console.error('Counts error:', e)
    }
  }, [])

  const fetchWebsite = useCallback(async (
    type: string, id: number | string, name: string, lat: number, lon: number
  ): Promise<WebsiteInfo | null> => {
    try {
      const params = new URLSearchParams({
        osm_type: type,
        osm_id: String(id),
        name,
        lat: lat.toFixed(6),
        lon: lon.toFixed(6),
      })
      return await apiFetch<WebsiteInfo>(`/pois/website?${params}`)
    } catch {
      return null
    }
  }, [])

  const fetchPoiDetail = useCallback(async (
    type: string, id: number | string
  ): Promise<PoiDetailResponse | null> => {
    try {
      return await apiFetch<PoiDetailResponse>(`/db/poi/${type}/${id}`)
    } catch {
      return null
    }
  }, [])

  return {
    searchResults, searchLoading, searchHint,
    categoryCounts, countsTotal,
    search, clearSearch,
    findByCategory, loadCounts,
    fetchWebsite, fetchPoiDetail,
  }
}
