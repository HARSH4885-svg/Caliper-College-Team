import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, type Note } from '../api'
import { when } from '../labels'
import { Icon, type IconName } from './brand'

export const NOTE_ICON: Record<string, IconName> = { ASSIGNMENT: 'task', DEADLINE: 'clock', ANNOUNCEMENT: 'megaphone', GRADE: 'check', DISCUSSION: 'chat', ENROLMENT: 'people' }

/** Top-bar bell: unread count (refreshed every minute and on focus), the latest items, and "mark all read". */
export function NotificationBell() {
  const [open, setOpen] = useState(false)
  const [data, setData] = useState<{ unread: number; items: Note[] } | null>(null)
  const box = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const load = useCallback(() => api.get<{ unread: number; items: Note[] }>('/api/notifications?limit=8').then(setData).catch(() => {}), [])
  useEffect(() => {
    void load()
    const t = setInterval(load, 60_000)
    window.addEventListener('focus', load)
    return () => { clearInterval(t); window.removeEventListener('focus', load) }
  }, [load])
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent) => { if (box.current && !box.current.contains(e.target as Node)) setOpen(false) }
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') setOpen(false) }
    document.addEventListener('mousedown', close); document.addEventListener('keydown', esc)
    return () => { document.removeEventListener('mousedown', close); document.removeEventListener('keydown', esc) }
  }, [open])
  const go = async (n: Note) => {
    setOpen(false)
    if (!n.read) { await api.post(`/api/notifications/${n.id}/read`).catch(() => {}); void load() }
    if (n.link) navigate(n.link)
  }
  const unread = data?.unread ?? 0
  return (
    <div className="bellwrap" ref={box}>
      <button className="ghost on-dark iconbtn bell" onClick={() => { setOpen(!open); if (!open) void load() }} aria-expanded={open} aria-haspopup="true" aria-label={`Notifications${unread ? `, ${unread} unread` : ''}`}>
        <Icon name="bell" />{unread > 0 && <span className="bellcount" aria-hidden="true">{unread > 99 ? '99+' : unread}</span>}
      </button>
      {open && (
        <div className="bellmenu" role="dialog" aria-label="Notifications">
          <div className="row space"><strong>Notifications</strong>{unread > 0 && <button className="quiet" onClick={async () => { await api.post('/api/notifications/read-all'); void load() }}>Mark all read</button>}</div>
          {!data ? <p className="muted small">Loading…</p> : data.items.length === 0 ? <p className="muted small">You are all caught up.</p> : (
            <ul>{data.items.map(n => (
              <li key={n.id}><button className={`noteitem${n.read ? '' : ' unread'}`} onClick={() => go(n)}>
                <Icon name={NOTE_ICON[n.kind] ?? 'info'} size={16} /><span><b>{n.title}</b>{n.body && <span className="small muted nb">{n.body}</span>}<span className="small muted">{when(n.createdAt)}</span></span></button></li>))}</ul>)}
          <Link to="/notifications" className="small" onClick={() => setOpen(false)}>All notifications and settings</Link>
        </div>)}
    </div>
  )
}
