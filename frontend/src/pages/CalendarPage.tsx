import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type CalEvent, type Course } from '../api'
import { useAuth } from '../auth'
import { Icon } from '../components/brand'
import { Empty, ErrorBox, Loading, PageHead } from '../components/ui'
import { KIND_LABEL } from '../labels'
import { useFetch } from '../useFetch'

const DAY = 86_400_000
const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate())
const startOfWeek = (d: Date) => { const s = startOfDay(d); const dow = (s.getDay() + 6) % 7; return new Date(s.getTime() - dow * DAY) } // Monday first
const sameDay = (a: Date, b: Date) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
const time = (d: Date) => d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
const KIND: Record<string, string> = { DUE: 'Due', OPENS: 'Opens', LATE_CLOSES: 'Late work closes', COURSE_START: 'Course starts', COURSE_END: 'Course ends' }
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function Chip({ e, onPick }: { e: CalEvent; onPick: (e: CalEvent) => void }) {
  return <button className={`calchip k-${e.kind.toLowerCase()}${e.done ? ' done' : ''}${e.draft ? ' draft' : ''}`} onClick={() => onPick(e)} title={`${KIND[e.kind]}: ${e.title} (${e.courseCode})`}>
    {e.kind === 'DUE' && <span className="t">{time(new Date(e.at))}</span>}{e.done && <Icon name="check" size={12} />}<span className="n">{e.title}</span></button>
}

/** Month, week and agenda views of every date in your classes, filterable by course. All dates come from the server. */
export function CalendarPage() {
  const { me } = useAuth()
  const [view, setView] = useState<'month' | 'week' | 'agenda'>(() => (window.innerWidth < 720 ? 'agenda' : 'month'))
  const [anchor, setAnchor] = useState(() => startOfDay(new Date()))
  const [course, setCourse] = useState('')
  const [picked, setPicked] = useState<CalEvent | null>(null)
  const range = useMemo(() => {
    if (view === 'month') { const first = new Date(anchor.getFullYear(), anchor.getMonth(), 1); const s = startOfWeek(first); return { from: s, to: new Date(s.getTime() + 42 * DAY) } }
    if (view === 'week') { const s = startOfWeek(anchor); return { from: s, to: new Date(s.getTime() + 7 * DAY) } }
    return { from: startOfDay(anchor), to: new Date(startOfDay(anchor).getTime() + 60 * DAY) }
  }, [view, anchor])
  const q = useFetch(() => api.get<CalEvent[]>(`/api/calendar?from=${range.from.toISOString()}&to=${range.to.toISOString()}`), [range.from.getTime(), range.to.getTime()])
  const courses = useFetch(() => api.get<Course[]>('/api/courses'), [])
  const events = (q.data ?? []).filter(e => !course || String(e.courseId) === course)
  const today = startOfDay(new Date())
  const move = (dir: number) => setAnchor(a => view === 'month' ? new Date(a.getFullYear(), a.getMonth() + dir, 1) : new Date(a.getTime() + dir * (view === 'week' ? 7 : 30) * DAY))
  const title = view === 'month' ? anchor.toLocaleDateString([], { month: 'long', year: 'numeric' }) : view === 'week' ? `Week of ${range.from.toLocaleDateString([], { day: 'numeric', month: 'short' })}` : `From ${anchor.toLocaleDateString([], { day: 'numeric', month: 'short' })}`
  const on = (d: Date) => events.filter(e => sameDay(new Date(e.at), d))
  const dueSoon = events.filter(e => e.kind === 'DUE' && !e.done && new Date(e.at).getTime() > Date.now() && new Date(e.at).getTime() < Date.now() + 7 * DAY)

  return (
    <>
      <PageHead title="Calendar" sub={me?.role === 'INSTRUCTOR' ? 'Deadlines, scheduled releases and course dates in everything you teach.' : 'Every deadline in your classes. A tick means you have handed it in.'} />
      <div className="toolbar calbar">
        <div className="row"><button className="quiet icononly" onClick={() => move(-1)} aria-label="Previous">‹</button><button className="quiet" onClick={() => setAnchor(startOfDay(new Date()))}>Today</button><button className="quiet icononly" onClick={() => move(1)} aria-label="Next">›</button>
          <h2 style={{ margin: 0 }} aria-live="polite">{title}</h2></div>
        <span className="grow" />
        <select aria-label="Course" value={course} onChange={e => setCourse(e.target.value)}><option value="">All courses</option>{(courses.data ?? []).map(c => <option key={c.id} value={c.id}>{c.code} · {c.title}</option>)}</select>
        <div className="segmented" role="tablist" aria-label="View" style={{ width: 270, margin: 0 }}>{(['month', 'week', 'agenda'] as const).map(v => <button key={v} type="button" role="tab" aria-selected={view === v} className={view === v ? 'on' : ''} onClick={() => setView(v)}>{v[0].toUpperCase() + v.slice(1)}</button>)}</div>
      </div>
      <ErrorBox message={q.error} />
      {me?.role !== 'INSTRUCTOR' && dueSoon.length > 0 && <div className="notice"><Icon name="clock" />{dueSoon.length} piece{dueSoon.length === 1 ? '' : 's'} of work due in the next 7 days that you have not handed in.</div>}
      {q.loading ? <Loading /> : view === 'agenda' ? (
        events.length === 0 ? <Empty icon="calendar">Nothing scheduled in the next 60 days.</Empty> : (
          <div className="card agenda">{Array.from(new Set(events.map(e => startOfDay(new Date(e.at)).getTime()))).map(d => (
            <section key={d}><h3 className={sameDay(new Date(d), today) ? 'today' : ''}>{new Date(d).toLocaleDateString([], { weekday: 'long', day: 'numeric', month: 'long' })}</h3>
              <ul>{on(new Date(d)).map((e, i) => <li key={i}><span className="when">{e.kind.startsWith('COURSE') ? '' : time(new Date(e.at))}</span><span className={`kindtag k-${e.kind.toLowerCase()}`}>{KIND[e.kind]}</span>
                {e.assignmentId ? <Link to={`/assignments/${e.assignmentId}`}>{e.title}</Link> : <Link to={`/courses/${e.courseId}`}>{e.title}</Link>}
                <span className="small muted">{e.courseCode}{e.assignmentKind && ` · ${KIND_LABEL[e.assignmentKind]}`}{e.draft && ' · draft'}</span>{e.done && <span className="badge t-ok"><Icon name="check" size={12} />Handed in</span>}</li>)}</ul></section>))}</div>)
      ) : (
        <div className={`calgrid ${view}`} role="grid" aria-label={title}>
          {WEEKDAYS.map(w => <div key={w} className="calhead" role="columnheader">{w}</div>)}
          {Array.from({ length: view === 'month' ? 42 : 7 }, (_, i) => new Date(range.from.getTime() + i * DAY)).map(d => {
            const list = on(d), other = view === 'month' && d.getMonth() !== anchor.getMonth()
            return <div key={d.getTime()} role="gridcell" className={`calcell${other ? ' other' : ''}${sameDay(d, today) ? ' today' : ''}`}>
              <span className="daynum">{d.getDate()}</span>
              {list.slice(0, view === 'month' ? 3 : 20).map((e, i) => <Chip key={i} e={e} onPick={setPicked} />)}
              {view === 'month' && list.length > 3 && <button className="quiet more" onClick={() => { setAnchor(d); setView('week') }}>+{list.length - 3} more</button>}
            </div>
          })}
        </div>)}
      {picked && (
        <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) setPicked(null) }}>
          <div className="dialog" role="dialog" aria-modal="true" aria-label="Event">
            <span className={`kindtag k-${picked.kind.toLowerCase()}`}>{KIND[picked.kind]}</span>
            <h2 style={{ marginTop: 8 }}>{picked.title}</h2>
            <p className="muted">{new Date(picked.at).toLocaleString([], { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })} · {picked.courseCode}{picked.assignmentKind && ` · ${KIND_LABEL[picked.assignmentKind]}`}{picked.draft && ' · draft, not visible to students'}</p>
            {picked.done != null && <p>{picked.done ? <span className="badge t-ok"><Icon name="check" size={12} />You have handed this in</span> : <span className="badge t-warn">Not handed in yet</span>}</p>}
            <div className="row">{picked.assignmentId ? <Link className="btn primary" to={`/assignments/${picked.assignmentId}`}>Open assignment</Link> : <Link className="btn primary" to={`/courses/${picked.courseId}`}>Open course</Link>}<button onClick={() => setPicked(null)}>Close</button></div>
          </div>
        </div>)}
    </>
  )
}
