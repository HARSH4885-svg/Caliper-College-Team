import { useMemo, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, type Assignment, type AttemptRow, type AttemptView, type Course, type MyAttempt } from '../api'
import { AssessmentSettings } from '../components/AssessmentSettings'
import { Avatar, Icon } from '../components/brand'
import { QuizBuilder } from '../components/QuizBuilder'
import { dueIn, Empty, ErrorBox, Loading, PageHead, Tabs } from '../components/ui'
import { fmtMarks, STATUS_LABEL, when } from '../labels'
import { useFetch } from '../useFetch'

export function QuizFacts({ a }: { a: Assignment }) {
  const d = dueIn(a.deadline)
  return (
    <div className="facts">
      <span className="fact"><Icon name="task" size={14} />{a.questionCount} question{a.questionCount === 1 ? '' : 's'} / {fmtMarks(a.maxMarks)} marks</span>
      <span className="fact"><Icon name="clock" size={14} />{d.text} / {when(a.deadline)}</span>
      <span className="fact">{a.quizMinutes ? `${a.quizMinutes} min time limit` : 'Untimed'}</span>
      <span className="fact">{a.maxAttempts} attempt{a.maxAttempts === 1 ? '' : 's'}</span>
      {a.passingMarks != null && <span className="fact">Pass mark {fmtMarks(a.passingMarks)}</span>}
    </div>
  )
}

const statusTone: Record<string, string> = { GRADED: 't-ok', RETURNED: 't-warn', SUBMITTED: 't-review', IN_PROGRESS: 't-muted' }

export function QuizStudentView({ a }: { a: Assignment }) {
  const navigate = useNavigate()
  const mine = useFetch(() => api.get<MyAttempt[]>(`/api/assignments/${a.id}/attempts/me`), [a.id])
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const open = mine.data?.find(x => x.status === 'IN_PROGRESS')
  const used = mine.data?.length ?? 0
  const closed = new Date(a.deadline).getTime() < Date.now() && (a.latePolicy === 'BLOCK' || (a.lateUntil != null && new Date(a.lateUntil).getTime() < Date.now()))
  const best = mine.data?.filter(x => x.score != null).reduce<number | null>((m, x) => (m == null || (x.score ?? 0) > m ? x.score : m), null)

  async function start() {
    setBusy(true); setErr(null)
    try { const v = await api.post<AttemptView>(`/api/assignments/${a.id}/attempts`); navigate(`/attempts/${v.id}`) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }
  return (
    <>
      <PageHead title={a.title} back={{ to: `/courses/${a.courseId}`, label: 'Class' }} />
      <div className="split">
        <div>
          <div className="card"><QuizFacts a={a} /><p className="statement">{a.description}</p>
            {a.quizMinutes && <p className="notice"><Icon name="clock" />The timer starts when you begin and keeps running if you close the page. Answers save as you go and are submitted automatically when time runs out.</p>}</div>
        </div>
        <aside className="sticky">
          <div className="panel" style={{ marginBottom: 20 }}>
            <h2><Icon name="task" />Your attempt</h2>
            <ErrorBox message={err} />
            {best != null && <p className="row"><span className="badge t-ok">Best {fmtMarks(best)} / {fmtMarks(a.maxMarks)}</span></p>}
            {open ? <button className="primary" style={{ width: '100%' }} onClick={() => navigate(`/attempts/${open.id}`)}>Resume attempt {open.attempt}</button>
              : closed ? <div className="notice"><Icon name="clock" />This quiz is closed.</div>
              : used >= a.maxAttempts ? <div className="notice"><Icon name="check" />You have used all {a.maxAttempts} attempt{a.maxAttempts === 1 ? '' : 's'}.</div>
              : <button className="primary" style={{ width: '100%' }} disabled={busy} onClick={start}>{busy ? 'Starting…' : used === 0 ? 'Start quiz' : `Start attempt ${used + 1}`}</button>}
            <p className="hint">{a.maxAttempts - used > 0 ? `${a.maxAttempts - used} attempt${a.maxAttempts - used === 1 ? '' : 's'} left.` : ''} {a.releaseMode === 'MANUAL' ? 'Marks appear when your instructor releases them.' : a.releaseMode === 'AFTER_DUE' ? 'Marks appear after the deadline.' : 'Marks appear as soon as everything is marked.'}</p>
          </div>
          <div className="panel"><h2>Attempts</h2>
            {mine.loading ? <Loading /> : !mine.data?.length ? <p className="muted small">No attempts yet.</p> : mine.data.map(x => (
              <div className="attempt" key={x.id}><div><Link to={`/attempts/${x.id}`}>Attempt {x.attempt}</Link><div className="small muted">{x.submittedAt ? `Submitted ${when(x.submittedAt)}` : `Started ${when(x.startedAt)}`}{x.late && ' (late)'}</div></div>
                {x.score != null ? <span className="badge t-ok">{fmtMarks(x.score)} / {fmtMarks(a.maxMarks)}</span> : <span className={`badge ${statusTone[x.status] ?? 't-muted'}`}>{x.status === 'IN_PROGRESS' ? 'In progress' : 'Submitted'}</span>}</div>))}
          </div>
        </aside>
      </div>
    </>
  )
}

export function QuizInstructorView({ a, reload, header, details }: { a: Assignment; reload: () => Promise<unknown>; header: React.ReactNode; details: React.ReactNode }) {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') ?? (a.questionCount === 0 ? 'questions' : 'attempts')
  const setTab = (t: string) => setParams({ tab: t }, { replace: true })
  const course = useFetch(() => api.get<Course>(`/api/courses/${a.courseId}`), [a.courseId])
  return (
    <>
      {header}
      <Tabs label="Quiz sections" value={tab} onChange={setTab} tabs={[{ id: 'questions', label: 'Questions', count: a.questionCount }, { id: 'attempts', label: 'Attempts' }, { id: 'settings', label: 'Settings' }, { id: 'brief', label: 'Details' }]} />
      {tab === 'questions' && <div id="panel-questions" role="tabpanel"><QuizBuilder assignmentId={a.id} subject={course.data?.title ?? ''} onSaved={() => { void reload() }} /></div>}
      {tab === 'attempts' && <AttemptsPanel a={a} />}
      {tab === 'settings' && <div id="panel-settings" role="tabpanel"><AssessmentSettings a={a} reload={reload} /></div>}
      {tab === 'brief' && details}
    </>
  )
}

function AttemptsPanel({ a }: { a: Assignment }) {
  const q = useFetch(() => api.get<AttemptRow[]>(`/api/assignments/${a.id}/attempts`), [a.id])
  const [filter, setFilter] = useState('all')
  const [search, setSearch] = useState('')
  const [best, setBest] = useState(true)
  const [msg, setMsg] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const rows = useMemo(() => {
    let r = q.data ?? []
    if (best) { const top = new Map<number, AttemptRow>(); for (const x of r) { const cur = top.get(x.studentId); if (!cur || (x.score ?? -1) > (cur.score ?? -1) || (x.needsMarking > 0 && cur.needsMarking === 0)) top.set(x.studentId, x) } r = [...top.values()] }
    if (filter === 'marking') r = r.filter(x => x.needsMarking > 0)
    if (filter === 'unreleased') r = r.filter(x => !x.released && x.status !== 'IN_PROGRESS')
    if (filter === 'late') r = r.filter(x => x.late)
    if (filter === 'progress') r = r.filter(x => x.status === 'IN_PROGRESS')
    const t = search.trim().toLowerCase()
    if (t) r = r.filter(x => `${x.studentName} ${x.email}`.toLowerCase().includes(t))
    return r
  }, [q.data, filter, search, best])
  const scores = (q.data ?? []).filter(x => x.score != null).map(x => x.score as number)
  const avg = scores.length ? scores.reduce((s, x) => s + x, 0) / scores.length : null
  const toMark = (q.data ?? []).filter(x => x.needsMarking > 0).length
  const unreleased = (q.data ?? []).filter(x => !x.released && (x.status === 'GRADED' || x.status === 'RETURNED')).length

  const act = async (path: string, ok: (n: number) => string) => {
    setErr(null); setMsg(null)
    try { const r = await api.post<Record<string, number>>(path); setMsg(ok(Object.values(r)[0])); await q.reload(true) } catch (e) { setErr((e as Error).message) }
  }
  return (
    <div id="panel-attempts" role="tabpanel">
      <div className="statrow">
        <div><b>{new Set((q.data ?? []).map(x => x.studentId)).size}</b><span>students attempted</span></div>
        <div><b>{avg == null ? '–' : `${fmtMarks(Math.round(avg * 10) / 10)}`}</b><span>average of {fmtMarks(a.maxMarks)}</span></div>
        <div><b>{scores.length ? fmtMarks(Math.max(...scores)) : '–'}</b><span>highest</span></div>
        <div><b>{toMark}</b><span>waiting for marks</span></div>
        <div><b>{unreleased}</b><span>marked, not released</span></div>
      </div>
      <div className="toolbar">
        <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Search students" placeholder="Search students" value={search} onChange={e => setSearch(e.target.value)} /></div>
        <select aria-label="Show" value={filter} onChange={e => setFilter(e.target.value)}><option value="all">All attempts</option><option value="marking">Waiting for marks</option><option value="unreleased">Not released</option><option value="late">Late</option><option value="progress">In progress</option></select>
        <label className="row small check"><input type="checkbox" checked={best} onChange={e => setBest(e.target.checked)} /> Best attempt per student</label>
        <span className="grow" />
        <button onClick={() => act(`/api/assignments/${a.id}/regrade`, n => `${n} attempt${n === 1 ? '' : 's'} re-marked with the current answer key.`)}><Icon name="refresh" size={16} />Re-mark</button>
        <button className="primary" disabled={unreleased === 0} onClick={() => act(`/api/assignments/${a.id}/release`, n => `${n} result${n === 1 ? '' : 's'} released to students.`)}>Release marked results</button>
      </div>
      <ErrorBox message={err ?? q.error} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
      <div className="card" style={{ padding: 0 }}>
        {q.loading ? <Loading /> : rows.length === 0 ? <Empty icon="task">{(q.data ?? []).length ? 'No attempts match.' : 'No one has attempted this quiz yet.'}</Empty> : (
          <div className="tablewrap"><table>
            <thead><tr><th>Student</th><th>Attempt</th><th>Submitted</th><th>Status</th><th>Score</th><th /></tr></thead>
            <tbody>{rows.map(x => (
              <tr key={x.id}>
                <td><div className="row" style={{ flexWrap: 'nowrap' }}><Avatar name={x.studentName} size={26} /><strong>{x.studentName}</strong></div></td>
                <td>#{x.attempt}</td>
                <td>{x.submittedAt ? when(x.submittedAt) : <span className="muted">–</span>}{x.late && <span className="badge t-warn" style={{ marginLeft: 6 }}>late</span>}</td>
                <td><span className={`badge ${statusTone[x.status] ?? 't-muted'}`}>{x.needsMarking > 0 ? `${x.needsMarking} to mark` : STATUS_LABEL[x.status] ?? x.status}</span>{x.released && <span className="small muted"> (released)</span>}</td>
                <td>{x.score == null ? <span className="muted">–</span> : <b>{fmtMarks(x.score)}</b>}<span className="muted"> / {fmtMarks(a.maxMarks)}</span></td>
                <td><Link className="btn quiet" to={`/attempts/${x.id}`}>{x.needsMarking > 0 ? 'Mark' : 'Open'}</Link></td>
              </tr>))}</tbody></table></div>)}
      </div>
    </div>
  )
}
