interface Props {
  lat: number
  lon: number
  poiCount: number
  totalCount: number
  loading: boolean
  filterLabel?: string | null
  filterCount?: number
  onClearFilter?: () => void
  alertEvent?: string | null
  alertCount?: number
  onAlertClick?: () => void
  aircraftCount?: number
  trainCount?: number
  subwayCount?: number
  busCount?: number
}

export function StatusBar({ lat, lon, poiCount, totalCount, loading, filterLabel, filterCount, onClearFilter, alertEvent, alertCount, onAlertClick, aircraftCount, trainCount, subwayCount, busCount }: Props) {
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

  if (alertEvent && (alertCount ?? 0) > 0) {
    return (
      <button
        onClick={onAlertClick}
        className="absolute bottom-0 left-0 right-0 z-[1000] h-8 bg-red-600/90 dark:bg-red-800/90 backdrop-blur-sm border-t border-red-500 dark:border-red-700 flex items-center justify-center px-3 text-xs text-white gap-2 hover:bg-red-700/90 dark:hover:bg-red-900/90 transition-colors cursor-pointer"
      >
        <svg viewBox="0 0 24 24" className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
        <span>{alertEvent}{(alertCount ?? 0) > 1 ? ` (+${(alertCount ?? 0) - 1} more)` : ''}</span>
      </button>
    )
  }

  return (
    <div className="absolute bottom-0 left-0 right-0 z-[1000] h-8 bg-white/90 dark:bg-gray-900/90 backdrop-blur-sm border-t border-gray-200 dark:border-gray-700 flex items-center px-3 text-xs text-gray-600 dark:text-gray-400 gap-4">
      <span>{lat.toFixed(4)}, {lon.toFixed(4)}</span>
      <span className="ml-auto flex gap-3">
        <span>
          {loading ? 'Loading...' : `${poiCount} POIs`}
          {totalCount > 0 && ` / ${totalCount.toLocaleString()} total`}
        </span>
        {(aircraftCount ?? 0) > 0 && <span>{aircraftCount} aircraft</span>}
        {(trainCount ?? 0) > 0 && <span>{trainCount} trains</span>}
        {(subwayCount ?? 0) > 0 && <span>{subwayCount} subway</span>}
        {(busCount ?? 0) > 0 && <span>{busCount} buses</span>}
      </span>
    </div>
  )
}
