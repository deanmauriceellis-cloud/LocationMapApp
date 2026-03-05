import { useState, useEffect } from 'react'

interface Props {
  open: boolean
  onClose: () => void
  onRegister: (displayName: string, email: string, password: string) => void
  onLogin: (email: string, password: string) => void
  loading: boolean
  error: string | null
  onClearError: () => void
}

export function AuthDialog({ open, onClose, onRegister, onLogin, loading, error, onClearError }: Props) {
  const [mode, setMode] = useState<'register' | 'login'>('register')
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [localError, setLocalError] = useState('')

  useEffect(() => {
    if (open) {
      setLocalError('')
      onClearError()
    }
  }, [open, onClearError])

  if (!open) return null

  const toggleMode = () => {
    setMode(m => m === 'register' ? 'login' : 'register')
    setLocalError('')
    onClearError()
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setLocalError('')

    if (mode === 'register') {
      if (!displayName.trim() || displayName.trim().length < 2) {
        setLocalError('Display name must be 2-50 characters')
        return
      }
      if (password.length < 8) {
        setLocalError('Password must be at least 8 characters')
        return
      }
      onRegister(displayName.trim(), email.trim(), password)
    } else {
      if (!email.trim()) {
        setLocalError('Email is required')
        return
      }
      if (!password) {
        setLocalError('Password is required')
        return
      }
      onLogin(email.trim(), password)
    }
  }

  const displayError = localError || error

  return (
    <div className="fixed inset-0 z-[2000] flex items-center justify-center bg-black/50" onClick={onClose}>
      <div
        className="w-[360px] max-w-[90vw] bg-white dark:bg-gray-900 rounded-xl shadow-2xl overflow-hidden"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-200 dark:border-gray-700">
          <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100">
            {mode === 'register' ? 'Create Account' : 'Sign In'}
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300">
            <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M18 6 6 18M6 6l12 12" />
            </svg>
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-5 space-y-4">
          {mode === 'register' && (
            <Field label="Display Name" value={displayName} onChange={setDisplayName} placeholder="2-50 characters" autoFocus />
          )}
          <Field label="Email" type="email" value={email} onChange={setEmail} placeholder={mode === 'register' ? 'Optional' : 'Required'} autoFocus={mode === 'login'} />
          <Field label="Password" type="password" value={password} onChange={setPassword} placeholder={mode === 'register' ? '8+ characters' : ''} />

          {displayError && (
            <p className="text-sm text-red-500 dark:text-red-400">{displayError}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-2.5 rounded-lg bg-teal-600 text-white font-medium text-sm hover:bg-teal-700 disabled:opacity-50 transition-colors"
          >
            {loading ? 'Please wait...' : mode === 'register' ? 'Create Account' : 'Sign In'}
          </button>

          <p className="text-center text-sm text-gray-500 dark:text-gray-400">
            {mode === 'register' ? 'Already have an account?' : "Don't have an account?"}{' '}
            <button type="button" onClick={toggleMode} className="text-teal-600 dark:text-teal-400 hover:underline">
              {mode === 'register' ? 'Sign In' : 'Create Account'}
            </button>
          </p>
        </form>
      </div>
    </div>
  )
}

function Field({ label, value, onChange, type = 'text', placeholder, autoFocus }: {
  label: string; value: string; onChange: (v: string) => void; type?: string; placeholder?: string; autoFocus?: boolean
}) {
  return (
    <div>
      <label className="block text-xs font-medium text-gray-500 dark:text-gray-400 mb-1">{label}</label>
      <input
        type={type}
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder}
        autoFocus={autoFocus}
        className="w-full px-3 py-2 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-teal-500 focus:border-teal-500 outline-none"
      />
    </div>
  )
}
