import { useEffect, useRef } from 'react'
import type { AuthUser } from '@/lib/types'

interface Props {
  open: boolean
  user: AuthUser | null
  onClose: () => void
  onLogout: () => void
  onSignIn: () => void
}

export function ProfileDropdown({ open, user, onClose, onLogout, onSignIn }: Props) {
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
    </div>
  )
}
