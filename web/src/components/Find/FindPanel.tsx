import { useState, useEffect, useRef } from 'react'
import { CATEGORIES } from '@/config/categories'
import { getCategoryTags, getSubtypeTags } from '@/config/categories'
import { ResultsList } from './ResultsList'
import type { FindResult, Category, Subtype } from '@/lib/types'

type View = 'categories' | 'subtypes' | 'results'

interface Props {
  open: boolean
  lat: number
  lon: number
  searchResults: FindResult[]
  searchLoading: boolean
  searchHint: string | null
  categoryCounts: Record<string, number>
  countsTotal: number
  onSearch: (query: string) => void
  onClearSearch: () => void
  onLoadCounts: () => void
  onFindByCategory: (categories: string[]) => Promise<FindResult[]>
  onSelectResult: (result: FindResult) => void
  onFilterAndMap: (results: FindResult[], label: string) => void
  onClose: () => void
  favoriteCount?: number
  favoriteResults?: FindResult[] | null
  onShowFavorites?: () => void
}

export function FindPanel({
  open, lat, lon,
  searchResults, searchLoading, searchHint,
  categoryCounts, countsTotal,
  onSearch, onClearSearch, onLoadCounts,
  onFindByCategory, onSelectResult, onFilterAndMap,
  onClose,
  favoriteCount, favoriteResults, onShowFavorites,
}: Props) {
  const [view, setView] = useState<View>('categories')
  const [query, setQuery] = useState('')
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null)
  const [browseResults, setBrowseResults] = useState<FindResult[]>([])
  const [browseLoading, setBrowseLoading] = useState(false)
  const [browseLabel, setBrowseLabel] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  // Load counts when panel opens
  useEffect(() => {
    if (open) {
      onLoadCounts()
      setView('categories')
      setQuery('')
      setSelectedCategory(null)
      setBrowseResults([])
      onClearSearch()
    }
  }, [open]) // eslint-disable-line react-hooks/exhaustive-deps

  // Switch to results view on search
  useEffect(() => {
    if (searchResults.length > 0 || searchLoading) {
      setView('results')
      setBrowseResults([])
    }
  }, [searchResults, searchLoading])

  // Switch to results view when favorites are loaded externally
  useEffect(() => {
    if (favoriteResults && favoriteResults.length > 0) {
      setBrowseResults(favoriteResults)
      setBrowseLabel('Favorites')
      setView('results')
    }
  }, [favoriteResults])

  const handleQueryChange = (val: string) => {
    setQuery(val)
    if (val.length >= 2) {
      onSearch(val)
    } else {
      onClearSearch()
      setView('categories')
    }
  }

  const handleClear = () => {
    setQuery('')
    onClearSearch()
    setView('categories')
    inputRef.current?.focus()
  }

  const handleCategoryClick = async (cat: Category) => {
    if (cat.subtypes && cat.subtypes.length > 0) {
      setSelectedCategory(cat)
      setView('subtypes')
    } else {
      setBrowseLoading(true)
      setBrowseLabel(cat.label)
      setView('results')
      const tags = getCategoryTags(cat.id)
      const results = await onFindByCategory(tags)
      setBrowseResults(results)
      setBrowseLoading(false)
    }
  }

  const handleSubtypeClick = async (subtype: Subtype) => {
    setBrowseLoading(true)
    setBrowseLabel(subtype.label)
    setView('results')
    const tags = getSubtypeTags(subtype)
    const results = await onFindByCategory(tags)
    setBrowseResults(results)
    setBrowseLoading(false)
  }

  const handleBrowseAll = async () => {
    if (!selectedCategory) return
    setBrowseLoading(true)
    setBrowseLabel(selectedCategory.label)
    setView('results')
    const tags = getCategoryTags(selectedCategory.id)
    const results = await onFindByCategory(tags)
    setBrowseResults(results)
    setBrowseLoading(false)
  }

  const handleBack = () => {
    if (view === 'results' && query.length >= 2) {
      // From search results, clear search to go back to categories
      handleClear()
    } else if (view === 'results') {
      if (selectedCategory) {
        setView('subtypes')
      } else {
        setView('categories')
      }
      setBrowseResults([])
    } else if (view === 'subtypes') {
      setSelectedCategory(null)
      setView('categories')
    }
  }

  const displayResults = query.length >= 2 ? searchResults : browseResults
  const displayLabel = query.length >= 2 ? `"${query}"` : browseLabel

  const handleFilterAndMap = () => {
    if (displayResults.length > 0) {
      onFilterAndMap(displayResults, displayLabel)
    }
  }

  return (
    <div
      className={`absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-xl transition-transform duration-200 ${open ? 'translate-x-0' : '-translate-x-full'}`}
    >
      {/* Search bar */}
      <div className="p-3 border-b border-gray-200 dark:border-gray-700">
        <div className="relative">
          <input
            ref={inputRef}
            type="text"
            value={query}
            onChange={(e) => handleQueryChange(e.target.value)}
            placeholder="Search places..."
            className="w-full h-9 pl-9 pr-8 rounded-lg bg-gray-100 dark:bg-gray-800 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 dark:placeholder-gray-500 outline-none focus:ring-2 focus:ring-teal-500"
          />
          <svg className="absolute left-2.5 top-2 w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <circle cx="11" cy="11" r="8" strokeWidth="2" />
            <path d="m21 21-4.35-4.35" strokeWidth="2" strokeLinecap="round" />
          </svg>
          {query && (
            <button onClick={handleClear} className="absolute right-2 top-1.5 w-6 h-6 flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
              <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          )}
        </div>
      </div>

      {/* Navigation */}
      {view !== 'categories' && (
        <button
          onClick={handleBack}
          className="flex items-center gap-1 px-3 py-2 text-xs text-teal-600 dark:text-teal-400 hover:bg-gray-50 dark:hover:bg-gray-800 border-b border-gray-100 dark:border-gray-800"
        >
          <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M15 18l-6-6 6-6" />
          </svg>
          Back
        </button>
      )}

      {/* Content */}
      <div className="flex-1 overflow-y-auto">
        {/* Category grid */}
        {view === 'categories' && (
          <div className="p-3">
            <div className="text-xs text-gray-500 dark:text-gray-400 mb-2">
              {countsTotal > 0 ? `${countsTotal.toLocaleString()} places within 10 km` : 'Loading...'}
            </div>
            <div className="grid grid-cols-4 gap-2">
              {onShowFavorites && (
                <button
                  onClick={onShowFavorites}
                  className="flex flex-col items-center gap-1 p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                >
                  <span className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold bg-amber-500">
                    {(favoriteCount || 0) > 99 ? '99+' : favoriteCount || '-'}
                  </span>
                  <span className="text-[10px] text-center text-gray-700 dark:text-gray-300 leading-tight">
                    Favorites
                  </span>
                </button>
              )}
              {CATEGORIES.map((cat) => {
                const count = categoryCounts[cat.id] || 0
                return (
                  <button
                    key={cat.id}
                    onClick={() => handleCategoryClick(cat)}
                    className="flex flex-col items-center gap-1 p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                  >
                    <span
                      className="w-8 h-8 rounded-full flex items-center justify-center text-white text-xs font-bold"
                      style={{ backgroundColor: cat.color }}
                    >
                      {count > 99 ? '99+' : count || '-'}
                    </span>
                    <span className="text-[10px] text-center text-gray-700 dark:text-gray-300 leading-tight">
                      {cat.label}
                    </span>
                  </button>
                )
              })}
            </div>
          </div>
        )}

        {/* Subtype grid */}
        {view === 'subtypes' && selectedCategory && (
          <div className="p-3">
            <div className="text-sm font-medium text-gray-800 dark:text-gray-200 mb-3">
              {selectedCategory.label}
            </div>
            <button
              onClick={handleBrowseAll}
              className="w-full mb-2 px-3 py-2 text-sm text-left rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-teal-600 dark:text-teal-400 font-medium"
            >
              Browse All {selectedCategory.label}
            </button>
            <div className="grid grid-cols-2 gap-1">
              {selectedCategory.subtypes!.map((sub) => (
                <button
                  key={sub.label}
                  onClick={() => handleSubtypeClick(sub)}
                  className="px-3 py-2 text-xs text-left rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-gray-700 dark:text-gray-300"
                >
                  {sub.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Results */}
        {view === 'results' && (
          <>
            {(searchLoading || browseLoading) ? (
              <div className="px-4 py-8 text-center text-sm text-gray-500 dark:text-gray-400">
                Searching...
              </div>
            ) : (
              <>
                {displayResults.length > 0 && (
                  <button
                    onClick={handleFilterAndMap}
                    className="w-full px-4 py-2.5 text-sm font-medium text-teal-700 dark:text-teal-300 bg-teal-50 dark:bg-teal-900/30 hover:bg-teal-100 dark:hover:bg-teal-900/50 transition-colors border-b border-teal-200 dark:border-teal-800"
                  >
                    Filter and Map ({displayResults.length})
                  </button>
                )}
                {searchHint && query.length >= 2 && (
                  <div className="px-4 py-2 text-xs text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-800/50 border-b border-gray-100 dark:border-gray-800">
                    {searchHint}
                  </div>
                )}
                <ResultsList results={displayResults} onSelect={onSelectResult} />
              </>
            )}
          </>
        )}
      </div>
    </div>
  )
}
