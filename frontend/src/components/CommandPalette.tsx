import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type SearchHit } from '../api'
import { useAuth } from '../auth'
import { useTheme } from '../theme'
import { Icon, type IconName } from './brand'

interface Item { id: string; label: string; hint: string; icon: IconName; run: () => void }

export const isTyping = (t: EventTarget | null) => {
  const el = t as HTMLElement | null
  return !!el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable)
}

/**
 * Global keyboard shortcuts: Ctrl/Cmd+K or "/" opens the palette, "?" lists shortcuts, "g h" / "g c" jump to Home / Courses.
 * Shortcuts that are plain letters are ignored while typing in a field.
 */
export function useShortcuts(open: { palette: () => void; help: () => void }) {
  const navigate = useNavigate()
  const pending = useRef<number | null>(null)
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') { e.preventDefault(); open.palette(); return }
      if (e.ctrlKey || e.metaKey || e.altKey || isTyping(e.target)) return
      if (e.key === '/') { e.preventDefault(); open.palette(); return }
      if (e.key === '?') { e.preventDefault(); open.help(); return }
      if (pending.current != null) {
        window.clearTimeout(pending.current); pending.current = null
        if (e.key === 'h') navigate('/')
        else if (e.key === 'c') navigate('/courses')
        else if (e.key === 'a') navigate('/account')
        return
      }
      if (e.key === 'g') pending.current = window.setTimeout(() => { pending.current = null }, 900)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, navigate])
}

function useDialogFocus(active: boolean, onClose: () => void) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!active) return
    const before = document.activeElement as HTMLElement | null
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.stopPropagation(); onClose(); return }
      if (e.key !== 'Tab' || !ref.current) return
      const f = [...ref.current.querySelectorAll<HTMLElement>('button, [href], input, [tabindex]:not([tabindex="-1"])')].filter(x => !x.hasAttribute('disabled'))
      if (f.length === 0) return
      const first = f[0], last = f[f.length - 1]
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus() }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus() }
    }
    document.addEventListener('keydown', onKey, true)
    return () => { document.removeEventListener('keydown', onKey, true); before?.focus?.() }
  }, [active, onClose])
  return ref
}

export function CommandPalette({ open, onClose, onHelp }: { open: boolean; onClose: () => void; onHelp: () => void }) {
  const { me, logout } = useAuth()
  const { theme, toggle } = useTheme()
  const navigate = useNavigate()
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<SearchHit[]>([])
  const [active, setActive] = useState(0)
  const [searching, setSearching] = useState(false)
  const seq = useRef(0)
  const listId = useId()
  const ref = useDialogFocus(open, onClose)
  const inst = me?.role === 'INSTRUCTOR'

  useEffect(() => { if (open) { setQ(''); setHits([]); setActive(0) } }, [open])

  useEffect(() => {
    const term = q.trim()
    if (!open || term.length < 2) { setHits([]); setSearching(false); return }
    setSearching(true)
    const mine = ++seq.current
    const t = setTimeout(() => {
      api.get<SearchHit[]>(`/api/search?q=${encodeURIComponent(term)}`)
        .then(r => { if (mine === seq.current) setHits(r) })
        .catch(() => { if (mine === seq.current) setHits([]) })
        .finally(() => { if (mine === seq.current) setSearching(false) })
    }, 180)
    return () => clearTimeout(t)
  }, [q, open])

  const items = useMemo<Item[]>(() => {
    const go = (path: string) => () => { onClose(); navigate(path) }
    const commands: Item[] = [
      { id: 'c-home', label: 'Home', hint: 'G then H', icon: 'home', run: go('/') },
      ...(inst ? [{ id: 'c-sim', label: 'Similarity review desk', hint: 'G then S', icon: 'eye' as IconName, run: go('/reviews') }] : []),
      ...(!inst ? [{ id: 'c-ws', label: 'Coding workspace', hint: 'G then W', icon: 'code' as IconName, run: go('/workspace') }] : []),
      { id: 'c-courses', label: inst ? 'Courses' : 'My classes', hint: 'G then C', icon: 'classes', run: go('/courses') },
      { id: 'c-cal', label: 'Calendar', hint: 'Dates', icon: 'calendar', run: go('/calendar') },
      { id: 'c-notes', label: 'Notifications', hint: 'Inbox', icon: 'bell', run: go('/notifications') },
      ...(inst ? [{ id: 'c-bank', label: 'Question bank', hint: 'Teaching', icon: 'archive' as IconName, run: go('/bank') }] : []),
      { id: 'c-account', label: 'Account and preferences', hint: 'G then A', icon: 'user', run: go('/account') },
      { id: 'c-theme', label: theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme', hint: 'Appearance', icon: theme === 'dark' ? 'sun' : 'moon', run: () => { toggle(); onClose() } },
      { id: 'c-help', label: 'Keyboard shortcuts', hint: '?', icon: 'keyboard', run: () => { onClose(); onHelp() } },
      { id: 'c-out', label: 'Sign out', hint: 'Account', icon: 'logout', run: () => { onClose(); logout() } },
    ]
    const term = q.trim().toLowerCase()
    const shownCommands = term ? commands.filter(c => c.label.toLowerCase().includes(term)) : commands
    const found: Item[] = hits.map(h => ({ id: `${h.type}-${h.id}`, label: h.title, hint: h.subtitle, icon: h.type === 'student' ? 'people' : h.type === 'course' ? 'classes' : 'task', run: go(h.path) }))
    return [...found, ...shownCommands]
  }, [q, hits, theme, toggle, navigate, onClose, onHelp, logout, inst])

  useEffect(() => { setActive(a => Math.min(a, Math.max(0, items.length - 1))) }, [items.length])
  if (!open) return null

  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive(a => (a + 1) % Math.max(1, items.length)) }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive(a => (a - 1 + items.length) % Math.max(1, items.length)) }
    else if (e.key === 'Enter') { e.preventDefault(); items[active]?.run() }
  }

  return (
    <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="dialog palette" role="dialog" aria-modal="true" aria-label="Search and commands" ref={ref}>
        <div className="palette-input">
          <Icon name="search" />
          <input autoFocus role="combobox" aria-expanded="true" aria-controls={listId} aria-activedescendant={items[active] ? `${listId}-${active}` : undefined} aria-autocomplete="list"
            placeholder="Search or run a command" value={q} onChange={e => { setQ(e.target.value); setActive(0) }} onKeyDown={onKey} />
          <kbd>Esc</kbd>
        </div>
        <ul id={listId} role="listbox" aria-label="Results" className="palette-list">
          {items.map((it, i) => (
            <li key={it.id} id={`${listId}-${i}`} role="option" aria-selected={i === active} className={i === active ? 'on' : ''} onMouseMove={() => setActive(i)} onClick={it.run}>
              <Icon name={it.icon} size={18} /><span className="lbl">{it.label}</span><span className="hint-r">{it.hint}</span>
            </li>))}
          {items.length === 0 && <li className="none" role="presentation">{searching ? 'Searching…' : 'Nothing matches.'}</li>}
        </ul>
        <div className="palette-foot" aria-live="polite">{searching ? 'Searching…' : q.trim().length === 1 ? 'Type one more letter to search.' : <><kbd>↑</kbd><kbd>↓</kbd> to move <kbd>Enter</kbd> to open</>}</div>
      </div>
    </div>
  )
}

const SHORTCUTS: [string, string][] = [
  ['Ctrl K  or  /', 'Open search and commands'],
  ['G then H', 'Go to Home'],
  ['G then C', 'Go to courses'],
  ['G then A', 'Go to account and preferences'],
  ['?', 'Show this list'],
  ['Esc', 'Close a dialog'],
]

export function ShortcutsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const ref = useDialogFocus(open, onClose)
  if (!open) return null
  return (
    <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="sc-title" ref={ref}>
        <h2 id="sc-title">Keyboard shortcuts</h2>
        <dl className="shortcuts">{SHORTCUTS.map(([k, d]) => <div key={k}><dt>{k.split(/\s{2}or\s{2}/).map((x, i) => <span key={i}>{i > 0 && ' or '}{x.split(' ').map((c, j) => c === 'then' ? <em key={j}> then </em> : <kbd key={j}>{c}</kbd>)}</span>)}</dt><dd>{d}</dd></div>)}</dl>
        <p className="hint">Letter shortcuts are ignored while you are typing in a field.</p>
        <button className="primary" onClick={onClose}>Close</button>
      </div>
    </div>
  )
}
