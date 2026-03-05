import { useEffect, useRef } from 'react'
import type { AuthUser } from '@/lib/types'

interface Props {
  open: boolean
  user: AuthUser | null
  onClose: () => void
  onLogout: () => void
  onSignIn: () => void
  hasHome?: boolean
  onSetHome?: () => void
  onClearHome?: () => void
}

export function ProfileDropdown({ open, user, onClose, onLogout, onSignIn, hasHome, onSetHome, onClearHome }: Props) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose()
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [open, onClose])

  if (!open) return null

  return (
    <div
      ref={ref}
      className="absolute top-14 right-12 z-[1100] w-56 bg-white dark:bg-gray-900 rounded-xl shadow-xl border border-gray-200 dark:border-gray-700 overflow-hidden"
    >
      {user ? (
        <div className="p-4 space-y-3">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-teal-600 flex items-center justify-center text-white font-bold text-lg">
              {user.displayName.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <div className="text-sm font-semibold text-gray-900 dark:text-gray-100 truncate">{user.displayName}</div>
              <div className="text-xs text-gray-500 dark:text-gray-400 capitalize">{user.role}</div>
            </div>
          </div>
          <button
            onClick={() => { onLogout(); onClose() }}
            className="w-full py-2 rounded-lg bg-gray-100 dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
          >
            Sign Out
          </button>
        </div>
      ) : (
        <div className="p-4">
          <p className="text-sm text-gray-500 dark:text-gray-400 mb-3">Sign in to comment, chat, and more.</p>
          <button
            onClick={() => { onSignIn(); onClose() }}
            className="w-full py-2 rounded-lg bg-teal-600 text-white text-sm font-medium hover:bg-teal-700 transition-colors"
          >
            Sign In
          </button>
        </div>
      )}

      {/* Home location */}
      {onSetHome && (
        <div className="border-t border-gray-200 dark:border-gray-700 px-4 py-3 space-y-2">
          <button
            onClick={() => { onSetHome(); onClose() }}
            className="w-full py-2 rounded-lg bg-gray-100 dark:bg-gray-800 text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors flex items-center justify-center gap-2"
          >
            <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            Set Current Location as Home
          </button>
          {hasHome && onClearHome && (
            <button
              onClick={() => { onClearHome(); onClose() }}
              className="w-full py-2 rounded-lg text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
            >
              Reset to Browser GPS
            </button>
          )}
        </div>
      )}
    </div>
  )
}
