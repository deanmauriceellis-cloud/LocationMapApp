const API_BASE = import.meta.env.VITE_API_URL || 'http://10.0.0.4:4300'

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!res.ok) {
    throw new Error(`API ${res.status}: ${res.statusText}`)
  }
  return res.json()
}

// ── Auth token helpers ──────────────────────────────────────────────────────

const TOKEN_KEY = 'lma_tokens'
const USER_KEY = 'lma_user'

export function getStoredTokens(): { accessToken: string; refreshToken: string; expiresAt: string } | null {
  try {
    const raw = localStorage.getItem(TOKEN_KEY)
    return raw ? JSON.parse(raw) : null
  } catch { return null }
}

export function storeTokens(accessToken: string, refreshToken: string, expiresAt: string) {
  localStorage.setItem(TOKEN_KEY, JSON.stringify({ accessToken, refreshToken, expiresAt }))
}

export function getStoredUser(): { id: number; displayName: string; role: string; createdAt: string } | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    return raw ? JSON.parse(raw) : null
  } catch { return null }
}

export function storeUser(user: { id: number; displayName: string; role: string; createdAt: string }) {
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

// Singleton promise to de-duplicate concurrent refresh calls
let refreshPromise: Promise<string> | null = null

async function refreshAccessToken(): Promise<string> {
  if (refreshPromise) return refreshPromise

  refreshPromise = (async () => {
    try {
      const tokens = getStoredTokens()
      if (!tokens?.refreshToken) throw new Error('No refresh token')

      const res = await fetch(`${API_BASE}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: tokens.refreshToken }),
      })
      if (!res.ok) {
        clearAuth()
        throw new Error('Refresh failed')
      }
      const data = await res.json()
      storeTokens(data.accessToken, data.refreshToken, data.expiresAt)
      return data.accessToken as string
    } finally {
      refreshPromise = null
    }
  })()

  return refreshPromise
}

/**
 * Authenticated fetch — injects Bearer header, auto-refreshes if token
 * is expiring within 2 minutes, retries once on 401.
 */
export async function authFetch<T>(path: string, init?: RequestInit): Promise<T> {
  let tokens = getStoredTokens()
  if (!tokens?.accessToken) throw new Error('Not authenticated')

  // Proactive refresh if expiring within 2 minutes
  const expiresMs = new Date(tokens.expiresAt).getTime() - Date.now()
  if (expiresMs < 2 * 60 * 1000) {
    try {
      const newToken = await refreshAccessToken()
      tokens = { ...tokens, accessToken: newToken }
    } catch {
      // continue with existing token; may still be valid
    }
  }

  const doFetch = async (token: string) => {
    const res = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...init?.headers,
        Authorization: `Bearer ${token}`,
      },
    })
    if (!res.ok) {
      const body = await res.json().catch(() => ({}))
      const err = new Error((body as Record<string, string>).error || `API ${res.status}`)
      ;(err as any).status = res.status
      throw err
    }
    return res.json() as Promise<T>
  }

  try {
    return await doFetch(tokens.accessToken)
  } catch (err: any) {
    if (err.status === 401) {
      // One retry after refresh
      try {
        const newToken = await refreshAccessToken()
        return await doFetch(newToken)
      } catch {
        clearAuth()
        throw err
      }
    }
    throw err
  }
}

export { API_BASE }
