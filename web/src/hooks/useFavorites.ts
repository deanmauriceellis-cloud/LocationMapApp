import { useState, useCallback, useEffect } from 'react'
import type { FavoriteEntry } from '@/lib/types'

const STORAGE_KEY = 'lma_favorites'

function load(): FavoriteEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export function useFavorites() {
  const [favorites, setFavorites] = useState<FavoriteEntry[]>(load)

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(favorites))
  }, [favorites])

  const isFavorite = useCallback((type: string, id: number | string) => {
    return favorites.some(f => f.osm_type === type && String(f.osm_id) === String(id))
  }, [favorites])

  const toggleFavorite = useCallback((entry: FavoriteEntry) => {
    setFavorites(prev => {
      const exists = prev.some(f => f.osm_type === entry.osm_type && String(f.osm_id) === String(entry.osm_id))
      if (exists) {
        return prev.filter(f => !(f.osm_type === entry.osm_type && String(f.osm_id) === String(entry.osm_id)))
      }
      return [{ ...entry, addedAt: Date.now() }, ...prev]
    })
  }, [])

  return { favorites, isFavorite, toggleFavorite, count: favorites.length }
}
