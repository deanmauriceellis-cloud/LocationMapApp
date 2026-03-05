import { useState, useEffect, useCallback } from 'react'
import { apiFetch, authFetch, storeTokens, storeUser, clearAuth, getStoredTokens, getStoredUser } from '@/config/api'
import type { AuthUser, AuthResponse } from '@/lib/types'

export function useAuth() {
  const [user, setUser] = useState<AuthUser | null>(getStoredUser)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Validate stored token on mount
  useEffect(() => {
    const tokens = getStoredTokens()
    if (!tokens?.accessToken) {
      setUser(null)
      return
    }
    let cancelled = false
    authFetch<AuthUser>('/auth/me')
      .then(u => { if (!cancelled) { setUser(u); storeUser(u) } })
      .catch(() => { if (!cancelled) { clearAuth(); setUser(null) } })
    return () => { cancelled = true }
  }, [])

  const register = useCallback(async (displayName: string, email: string, password: string) => {
    setLoading(true)
    setError(null)
    try {
      const body: Record<string, string> = { displayName, password }
      if (email) body.email = email
      const data = await apiFetch<AuthResponse>('/auth/register', {
        method: 'POST',
        body: JSON.stringify(body),
      })
      storeTokens(data.accessToken, data.refreshToken, data.expiresAt)
      storeUser(data.user)
      setUser(data.user)
    } catch (err: any) {
      setError(err.message || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    setLoading(true)
    setError(null)
    try {
      const data = await apiFetch<AuthResponse>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password }),
      })
      storeTokens(data.accessToken, data.refreshToken, data.expiresAt)
      storeUser(data.user)
      setUser(data.user)
    } catch (err: any) {
      setError(err.message || 'Login failed')
    } finally {
      setLoading(false)
    }
  }, [])

  const logout = useCallback(async () => {
    try {
      const tokens = getStoredTokens()
      await authFetch('/auth/logout', {
        method: 'POST',
        body: JSON.stringify({ refreshToken: tokens?.refreshToken }),
      })
    } catch {
      // logout best-effort
    }
    clearAuth()
    setUser(null)
  }, [])

  const clearError = useCallback(() => setError(null), [])

  return {
    user,
    loading,
    error,
    isLoggedIn: !!user,
    register,
    login,
    logout,
    clearError,
  }
}
