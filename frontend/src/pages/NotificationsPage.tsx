import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, type Note } from '../api'
import { Icon } from '../components/brand'
import { NOTE_ICON } from '../components/NotificationBell'
import { Empty, ErrorBox, Loading, PageHead } from '../components/ui'
import { when } from '../labels'
import { useFetch } from '../useFetch'

const KIND_TEXT: Record<string, [string, string]> = {
  ASSIGNMENT: ['New assignments', 'When work is published in your classes.'],
  DEADLINE: ['Deadline reminders', 'Work due in the next 48 hours that you have not handed in.'],
  ANNOUNCEMENT: ['Announcements', 'Posts from your instructors.'],
  GRADE: ['Marks and feedback', 'When marks are released or feedback is added.'],
  DISCUSSION: ['Discussions', 'Replies to threads you started or joined; new questions in classes you teach.'],
  ENROLMENT: ['Enrolment', 'Changes to your class membership.'],
}

export function NotificationsPage() {
  const [unread, setUnread] = useState(false)
  const list = useFetch(() => api.get<{ unread: number; items: Note[] }>(`/api/notifications?limit=200&unread=${unread}`), [unread])
  const prefs = useFetch(() => api.get<Record<string, boolean>>('/api/notifications/preferences'), [])
  const [err, setErr] = useState<string | null>(null)
  const navigate = useNavigate()
  async function toggle(kind: string, on: boolean) {
    setErr(null)
    try { await api.put('/api/notifications/preferences', { [kind]: on }); await prefs.reload(true) } catch (e) { setErr((e as Error).message) }
  }
  return (
    <>
      <PageHead title="Notifications" sub="Everything Caliper has told you, and what it should tell you about." actions={<button onClick={async () => { await api.post('/api/notifications/read-all'); await list.reload(true) }}>Mark all read</button>} />
      <ErrorBox message={err ?? list.error} />
      <div className="split">
        <div className="card" style={{ padding: 0 }}>
          <div className="row space" style={{ padding: '12px 20px', borderBottom: '1px solid var(--line)' }}><strong>{list.data?.unread ?? 0} unread</strong>
            <label className="row small check"><input type="checkbox" checked={unread} onChange={e => setUnread(e.target.checked)} /> Unread only</label></div>
          {list.loading ? <Loading /> : !list.data || list.data.items.length === 0 ? <Empty icon="bell">Nothing here.</Empty> : (
            <ul className="notelist">{list.data.items.map(n => (
              <li key={n.id}><button className={`noteitem${n.read ? '' : ' unread'}`} onClick={async () => { if (!n.read) await api.post(`/api/notifications/${n.id}/read`).catch(() => {}); if (n.link) navigate(n.link); else void list.reload(true) }}>
                <Icon name={NOTE_ICON[n.kind] ?? 'info'} size={18} /><span><b>{n.title}</b>{n.body && <span className="small muted nb">{n.body}</span>}<span className="small muted">{when(n.createdAt)}</span></span></button></li>))}</ul>)}
        </div>
        <aside className="card"><h2>Send me notifications about</h2>
          {prefs.data && Object.keys(KIND_TEXT).map(k => (
            <label key={k} className="prefrow"><input type="checkbox" checked={prefs.data![k] ?? true} onChange={e => toggle(k, e.target.checked)} /><span><b>{KIND_TEXT[k][0]}</b><span className="small muted">{KIND_TEXT[k][1]}</span></span></label>))}
          <p className="hint">Notifications appear here and under the bell. Email delivery is not set up on this server.</p>
        </aside>
      </div>
    </>
  )
}
