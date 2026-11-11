import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, setUnauthorizedHandler, tokenStore, type AuthResponse, type Me } from './api'

// Inspect URL hash for single-use token from external launches (e.g. LTI 1.3 Advantage)
function initLtiToken() {
  if (typeof window !== 'undefined' && window.location.hash) {
    const hash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : window.location.hash
    if (hash.includes('token=')) {
      const params = new URLSearchParams(hash)
      const token = params.get('token')
      const redirect = params.get('redirect')
      if (token) {
        tokenStore.set(token)
        if (redirect && redirect.startsWith('/')) {
          window.history.replaceState(null, '', redirect)
        } else {
          window.history.replaceState(null, '', window.location.pathname)
        }
      }
    }
  }
}
initLtiToken()

interface Ctx { me: Me | null; loading: boolean; login: (email: string, password: string) => Promise<void>; register: (b: { email: string; displayName: string; password: string; enrollmentCode: string }) => Promise<void>; logout: () => void; setProfile: (m: Me) => void }
const AuthContext = createContext<Ctx | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [me, setMe] = useState<Me | null>(null)
  const [loading, setLoading] = useState<boolean>(!!tokenStore.get())

  const logout = useCallback(() => {
    if (tokenStore.get()) api.post('/api/auth/logout').catch(() => {})
    tokenStore.clear()
    setMe(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => { tokenStore.clear(); setMe(null) })
    if (tokenStore.get()) {
      api.get<Me>('/api/auth/me').then(setMe).catch(() => tokenStore.clear()).finally(() => setLoading(false))
    }
  }, [])

  const value = useMemo<Ctx>(() => ({
    me, loading, logout, setProfile: setMe,
    login: async (email, password) => {
      const r = await api.post<AuthResponse>('/api/auth/login', { email, password })
      tokenStore.set(r.token); setMe(r.user)
    },
    register: async b => {
      const r = await api.post<AuthResponse>('/api/auth/register', b)
      tokenStore.set(r.token); setMe(r.user)
    },
  }), [me, loading, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): Ctx {
  const c = useContext(AuthContext)
  if (!c) throw new Error('AuthProvider missing')
  return c
}
