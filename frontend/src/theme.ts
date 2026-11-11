import { useCallback, useSyncExternalStore } from 'react'

export type Theme = 'light' | 'dark'
/** What the person chose. "system" follows the operating system and keeps following it while the page is open. */
export type ThemePreference = 'system' | Theme
const KEY = 'cs-theme'

const systemTheme = (): Theme => (typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')

function readPreference(): ThemePreference {
  try {
    const saved = localStorage.getItem(KEY)
    if (saved === 'light' || saved === 'dark') return saved
  } catch { /* storage can be unavailable; fall through */ }
  return 'system'
}

let preference: ThemePreference = readPreference()
const listeners = new Set<() => void>()
const effective = (): Theme => (preference === 'system' ? systemTheme() : preference)

function apply() { document.documentElement.dataset.theme = effective() }

function setPreference(next: ThemePreference) {
  preference = next
  try { if (next === 'system') localStorage.removeItem(KEY); else localStorage.setItem(KEY, next) } catch { /* ignore */ }
  apply()
  listeners.forEach(l => l())
}

if (typeof matchMedia === 'function') {
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => { if (preference === 'system') { apply(); listeners.forEach(l => l()) } })
}

const subscribe = (l: () => void) => { listeners.add(l); return () => { listeners.delete(l) } }

/** One shared store, so the top-bar button, the account page and the command palette always agree. */
export function useTheme() {
  const pref = useSyncExternalStore(subscribe, () => preference, () => 'system' as ThemePreference)
  const theme: Theme = pref === 'system' ? systemTheme() : pref
  const toggle = useCallback(() => setPreference(effective() === 'dark' ? 'light' : 'dark'), [])
  return { theme, preference: pref, toggle, setPreference }
}

/** Applied before first paint so the login page and a reload do not flash the wrong theme. */
export function applyInitialTheme() { preference = readPreference(); apply() }
