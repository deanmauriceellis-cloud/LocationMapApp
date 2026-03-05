// (C) Dean Maurice Ellis, 2026 - Module LayersDropdown.tsx
import { useEffect, useRef } from 'react'

interface LayerToggle {
  label: string
  active: boolean
  count?: number
  onToggle: () => void
}

interface Props {
  open: boolean
  layers: LayerToggle[]
  onClose: () => void
}

export function LayersDropdown({ open, layers, onClose }: Props) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        onClose()
      }
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      ref={ref}
      className="absolute top-12 right-24 z-[1002] w-48 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-xl py-1"
    >
      {layers.map(l => (
        <button
          key={l.label}
          onClick={l.onToggle}
          className="w-full flex items-center gap-3 px-3 py-2 text-sm text-left hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
        >
          <div className={`w-4 h-4 rounded border-2 flex items-center justify-center transition-colors ${
            l.active
              ? 'bg-teal-600 border-teal-600'
              : 'border-gray-300 dark:border-gray-500'
          }`}>
            {l.active && (
              <svg viewBox="0 0 24 24" className="w-3 h-3 text-white" fill="none" stroke="currentColor" strokeWidth="3">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            )}
          </div>
          <span className="flex-1 text-gray-700 dark:text-gray-200">{l.label}</span>
          {l.active && l.count != null && l.count > 0 && (
            <span className="text-xs text-gray-400 dark:text-gray-500">{l.count}</span>
          )}
        </button>
      ))}
    </div>
  )
}
