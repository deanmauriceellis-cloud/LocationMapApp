import { formatDistance } from '@/lib/distance'
import { resolveCategory, UNCATEGORIZED_COLOR } from '@/config/categories'
import type { FindResult } from '@/lib/types'

interface Props {
  results: FindResult[]
  onSelect: (result: FindResult) => void
}

export function ResultsList({ results, onSelect }: Props) {
  if (results.length === 0) {
    return <div className="px-4 py-8 text-center text-sm text-gray-500 dark:text-gray-400">No results found</div>
  }

  return (
    <div className="flex flex-col">
      {results.map((r) => {
        const cat = resolveCategory(r.category)
        const color = cat?.color || UNCATEGORIZED_COLOR
        const detail = r.tags?.cuisine || r.tags?.brand || r.tags?.operator || r.tags?.denomination || ''
        return (
          <button
            key={`${r.type}-${r.id}`}
            onClick={() => onSelect(r)}
            className="flex items-start gap-2.5 px-4 py-2.5 text-left hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors border-b border-gray-100 dark:border-gray-800"
          >
            <span
              className="w-2.5 h-2.5 rounded-full mt-1.5 shrink-0"
              style={{ backgroundColor: color }}
            />
            <div className="flex-1 min-w-0">
              <div className="flex items-baseline gap-2">
                <span className="font-medium text-sm text-gray-900 dark:text-gray-100 truncate">
                  {r.name || 'Unnamed'}
                </span>
                <span className="text-xs text-gray-400 dark:text-gray-500 shrink-0">
                  {formatDistance(r.distance_m)}
                </span>
              </div>
              {detail && (
                <div className="text-xs text-gray-500 dark:text-gray-400 truncate">{detail}</div>
              )}
              <div className="text-xs mt-0.5" style={{ color }}>
                {cat?.label || 'Other'}
              </div>
            </div>
          </button>
        )
      })}
    </div>
  )
}
