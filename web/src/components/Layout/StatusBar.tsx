interface Props {
  lat: number
  lon: number
  poiCount: number
  totalCount: number
  loading: boolean
  filterLabel?: string | null
  filterCount?: number
  onClearFilter?: () => void
}

export function StatusBar({ lat, lon, poiCount, totalCount, loading, filterLabel, filterCount, onClearFilter }: Props) {
  if (filterLabel) {
    return (
      <button
        onClick={onClearFilter}
        className="absolute bottom-0 left-0 right-0 z-[1000] h-8 bg-teal-600/90 dark:bg-teal-800/90 backdrop-blur-sm border-t border-teal-500 dark:border-teal-700 flex items-center justify-center px-3 text-xs text-white gap-2 hover:bg-teal-700/90 dark:hover:bg-teal-900/90 transition-colors cursor-pointer"
      >
        <span>Showing {filterCount || 0} results for {filterLabel}</span>
        <span className="opacity-75">— click to clear</span>
      </button>
    )
  }

  return (
    <div className="absolute bottom-0 left-0 right-0 z-[1000] h-8 bg-white/90 dark:bg-gray-900/90 backdrop-blur-sm border-t border-gray-200 dark:border-gray-700 flex items-center px-3 text-xs text-gray-600 dark:text-gray-400 gap-4">
      <span>{lat.toFixed(4)}, {lon.toFixed(4)}</span>
      <span className="ml-auto">
        {loading ? 'Loading...' : `${poiCount} POIs`}
        {totalCount > 0 && ` / ${totalCount.toLocaleString()} total`}
      </span>
    </div>
  )
}
