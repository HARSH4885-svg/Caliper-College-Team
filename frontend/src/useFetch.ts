import { useCallback, useEffect, useRef, useState } from 'react'

export function useFetch<T>(load: () => Promise<T>, deps: unknown[], pollWhile?: (d: T) => boolean, pollMs = 2000) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const loadRef = useRef(load)
  loadRef.current = load

  const reload = useCallback(async (silent = false) => {
    if (!silent) setLoading(true)
    try { const d = await loadRef.current(); setData(d); setError(null); return d } catch (e) { setError(e instanceof Error ? e.message : 'Something went wrong'); return null } finally { setLoading(false) }
  }, [])

  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { void reload() }, deps)

  useEffect(() => {
    if (!pollWhile || !data || !pollWhile(data)) return
    const t = setTimeout(() => { void reload(true) }, pollMs)
    return () => clearTimeout(t)
  }, [data, pollWhile, pollMs, reload])

  return { data, error, loading, reload }
}
