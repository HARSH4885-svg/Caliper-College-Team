import { useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { api, tokenStore, type Announcement, type Assignment, type Course, type CourseAnalytics, type Gradebook, type GradeCell, type Overview, type Staff, type StudentRow } from '../api'
import { useAuth } from '../auth'
import { Avatar, FlagBanner, Icon } from '../components/brand'
import { Histogram, LineChart, WeeklyBars } from '../components/charts'
import { ClassCard } from '../components/ClassCard'
import { dueIn, Empty, ErrorBox, Field, Loading, PageHead, Synthetic, Tabs, VerdictBadge } from '../components/ui'
import { fmtMarks, KIND_LABEL, LANGUAGE_LABEL, STATUS_LABEL, when } from '../labels'
import { useFetch } from '../useFetch'
import { DiscussionsPanel, MaterialsPanel } from './CommunityPanels'

async function download(url: string, name: string) {
  const res = await fetch(url, { headers: { Authorization: `Bearer ${tokenStore.get() ?? ''}` } })
  if (!res.ok) throw new Error('Download failed')
  const a = document.createElement('a'); a.href = URL.createObjectURL(await res.blob()); a.download = name; a.click(); URL.revokeObjectURL(a.href)
}

export function CoursesPage() {
  const { me } = useAuth()
  const navigate = useNavigate()
  const inst = me?.role === 'INSTRUCTOR'
  const [showArchived, setShowArchived] = useState(false)
  const { data, error, loading } = useFetch(() => api.get<Course[]>(`/api/courses?includeArchived=${showArchived}`), [showArchived])
  const [f, setF] = useState({ code: '', title: '', subject: '', category: '', description: '', startsOn: '', endsOn: '' })
  const [formErr, setFormErr] = useState<string | null>(null)
  const [enrollCode, setEnrollCode] = useState('')
  const [q, setQ] = useState('')
  const [cat, setCat] = useState('')
  const [creating, setCreating] = useState(false)

  async function create(e: FormEvent) {
    e.preventDefault(); setFormErr(null)
    try {
      const c = await api.post<Course>('/api/courses', { ...f, startsOn: f.startsOn || null, endsOn: f.endsOn || null })
      navigate(`/courses/${c.id}?tab=manage`)
    } catch (err) { setFormErr((err as Error).message) }
  }
  async function enroll(e: FormEvent) {
    e.preventDefault(); setFormErr(null)
    try { const c = await api.post<Course>('/api/courses/join', { enrollmentCode: enrollCode }); setEnrollCode(''); navigate(`/courses/${c.id}`) } catch (err) { setFormErr((err as Error).message) }
  }
  const cats = [...new Set((data ?? []).map(c => c.category).filter(Boolean))].sort()
  const shown = (data ?? []).filter(c => (!cat || c.category === cat) && (!q.trim() || `${c.code} ${c.title} ${c.instructorName} ${c.subject}`.toLowerCase().includes(q.trim().toLowerCase())))

  return (
    <>
      <PageHead title={inst ? 'Courses' : 'My classes'} sub={inst ? 'Everything you teach or help teach.' : 'The classes you are enrolled in.'}
        actions={inst ? <><label className="row small check"><input type="checkbox" checked={showArchived} onChange={e => setShowArchived(e.target.checked)} /> Show archived</label>
          <button className="primary" onClick={() => setCreating(!creating)} aria-expanded={creating}><Icon name="plus" />New course</button></> : undefined} />
      <ErrorBox message={error ?? formErr} />
      {inst && creating && (
        <form className="card" onSubmit={create}><h2>Create a course</h2>
          <div className="grid g3"><Field id="ccode" label="Course code"><input id="ccode" value={f.code} onChange={e => setF({ ...f, code: e.target.value })} required maxLength={30} placeholder="CS201" /></Field>
            <Field id="ctitle" label="Title"><input id="ctitle" value={f.title} onChange={e => setF({ ...f, title: e.target.value })} required maxLength={150} placeholder="Data Structures" /></Field>
            <Field id="csub" label="Subject"><input id="csub" value={f.subject} onChange={e => setF({ ...f, subject: e.target.value })} maxLength={80} placeholder="Computer Science" /></Field>
            <Field id="ccat" label="Category"><input id="ccat" value={f.category} onChange={e => setF({ ...f, category: e.target.value })} maxLength={60} placeholder="Core, Elective…" list="catlist" /><datalist id="catlist">{cats.map(c => <option key={c} value={c} />)}</datalist></Field>
            <Field id="cstart" label="Starts"><input id="cstart" type="date" value={f.startsOn} onChange={e => setF({ ...f, startsOn: e.target.value })} /></Field>
            <Field id="cend" label="Ends"><input id="cend" type="date" value={f.endsOn} onChange={e => setF({ ...f, endsOn: e.target.value })} /></Field></div>
          <Field id="cdesc" label="Description" hint="Shown on the course overview."><textarea id="cdesc" value={f.description} onChange={e => setF({ ...f, description: e.target.value })} maxLength={5000} /></Field>
          <div className="row"><button className="primary" type="submit"><Icon name="plus" />Create course</button><button type="button" className="quiet" onClick={() => setCreating(false)}>Cancel</button></div></form>)}
      {(data?.length ?? 0) > 3 && (
        <div className="toolbar">
          <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Filter courses" placeholder="Filter by code, title or instructor" value={q} onChange={e => setQ(e.target.value)} /></div>
          {cats.length > 1 && <select aria-label="Category" value={cat} onChange={e => setCat(e.target.value)}><option value="">All categories</option>{cats.map(c => <option key={c}>{c}</option>)}</select>}
          <span className="small muted">{shown.length} of {data?.length}</span>
        </div>)}
      {loading ? <Loading /> : !data || data.length === 0
        ? <Empty icon="classes">{inst ? 'No courses yet. Use "New course" to create your first one.' : 'You are not enrolled in a course yet. Join one below with the code from your instructor.'}</Empty>
        : shown.length === 0 ? <Empty icon="search">No course matches the filter.</Empty>
        : <div className="classes">{shown.map(c => <ClassCard key={c.id} c={c} instructor={!!inst} />)}</div>}
      {!inst && (
        <form className="card" onSubmit={enroll} style={{ marginTop: 32 }}><h2>Join a class</h2>
          <Field id="ecode" label="Class code" hint="Your instructor gives you this code, for example CS-AB12CD34EF."><input id="ecode" value={enrollCode} onChange={e => setEnrollCode(e.target.value)} required autoComplete="off" /></Field>
          <button className="primary" type="submit">Join class</button></form>)}
    </>
  )
}

const STATUS_TONE: Record<string, string> = { GRADED: 't-ok', PENDING: 't-review', IN_PROGRESS: 't-muted', RETURNED: 't-warn', MISSING: 't-bad', NOT_STARTED: 't-muted' }
export const StatusChip = ({ c }: { c: GradeCell }) => <span className={`badge ${STATUS_TONE[c.status] ?? 't-muted'}`}>{c.status === 'GRADED' && c.score != null ? `${fmtMarks(c.score)} / ${fmtMarks(c.maxMarks)}` : STATUS_LABEL[c.status]}{c.late && ' (late)'}</span>

export function CourseDetailPage() {
  const { id } = useParams()
  const { me } = useAuth()
  const inst = me?.role === 'INSTRUCTOR'
  const course = useFetch(() => api.get<Course>(`/api/courses/${id}`), [id])
  const assignments = useFetch(() => api.get<Assignment[]>(`/api/courses/${id}/assignments`), [id])
  const mine = useFetch(() => (inst ? Promise.resolve(null) : api.get<Gradebook>(`/api/courses/${id}/my-progress`)), [id, inst])
  const ann = useFetch(() => api.get<Announcement[]>(`/api/courses/${id}/announcements`), [id])
  const [params, setParams] = useSearchParams()
  const rawTab = params.get('tab') ?? 'overview'
  
  // Resolve primary cluster from raw query param
  const activeCluster = useMemo(() => {
    if (rawTab === 'materials') return 'work'
    if (rawTab === 'news' || rawTab === 'discuss') return 'community'
    if (rawTab === 'people' || rawTab === 'analytics') return 'grades'
    if (rawTab === 'manage' || rawTab === 'settings') return 'manage'
    if (['overview', 'work', 'community', 'grades'].includes(rawTab)) return rawTab
    return 'overview'
  }, [rawTab])

  // Sub-navigation state within each cluster
  const [workSub, setWorkSub] = useState<'assignments' | 'materials'>(rawTab === 'materials' ? 'materials' : 'assignments')
  const [commSub, setCommSub] = useState<'news' | 'discuss'>(rawTab === 'discuss' ? 'discuss' : 'news')
  const [gradeSub, setGradeSub] = useState<'grades' | 'people' | 'analytics'>(
    rawTab === 'people' ? 'people' : rawTab === 'analytics' ? 'analytics' : 'grades'
  )

  const setTab = (t: string) => {
    if (t === 'work') setWorkSub('assignments')
    else if (t === 'community') setCommSub('news')
    else if (t === 'grades') setGradeSub('grades')
    setParams(t === 'overview' ? {} : { tab: t }, { replace: true })
  }

  const [copied, setCopied] = useState(false)
  const [q, setQ] = useState('')
  const [kind, setKind] = useState('')

  if (course.loading) return <Loading />
  if (!course.data) return <ErrorBox message={course.error} />
  const c = course.data
  const cellOf = (assignmentId: number) => mine.data?.rows[0]?.cells.find(x => x.assignmentId === assignmentId) ?? null
  
  const tabs = [
    { id: 'overview', label: 'Overview' },
    { id: 'work', label: 'Coursework', count: assignments.data?.length },
    { id: 'community', label: 'Community', count: ann.data?.length || undefined },
    { id: 'grades', label: inst ? 'Grades & Roster' : 'My Grades' },
    ...(inst ? [{ id: 'manage', label: 'Settings' }] : []),
  ]
  const work = (assignments.data ?? []).filter(a => (!kind || a.kind === kind) && a.title.toLowerCase().includes(q.trim().toLowerCase()))
  return (
    <>
      <div className="coursehead">
        <FlagBanner seed={c.id} />
        <Link className="back" to="/courses"><Icon name="left" size={16} />{inst ? 'Courses' : 'My classes'}</Link>
        <div className="in">
          <span className="code">{c.code}{c.category && ` / ${c.category}`}</span>
          <h1>{c.title}</h1>
          <div className="row" style={{ marginTop: 12 }}>
            <span className="small" style={{ color: '#d4def5' }}>{c.instructorName}{c.staff.length > 0 && ` with ${c.staff.join(', ')}`}</span>
            {c.synthetic && <Synthetic />}{c.archived && <span className="badge t-muted">Archived</span>}
            {inst && c.enrollmentCode && <span className="codechip">Join code <b>{c.enrollmentCode}</b>
              <button onClick={() => { void navigator.clipboard?.writeText(c.enrollmentCode ?? ''); setCopied(true); setTimeout(() => setCopied(false), 1500) }}>{copied ? 'Copied' : 'Copy'}</button></span>}
          </div>
        </div>
      </div>
      <Tabs tabs={tabs} value={activeCluster} onChange={setTab} label="Course sections" />

      {activeCluster === 'overview' && <OverviewPanel c={c} inst={!!inst} announcements={ann.data ?? []} go={setTab} />}

      {activeCluster === 'work' && (
        <div id="panel-work" role="tabpanel" aria-labelledby="tab-work">
          <div className="segmented" role="tablist" aria-label="Coursework sections" style={{ maxWidth: 300, marginBottom: 20 }}>
            <button type="button" role="tab" aria-selected={workSub === 'assignments'} className={workSub === 'assignments' ? 'on' : ''} onClick={() => setWorkSub('assignments')}>
              <Icon name="task" size={15} /> Assignments ({assignments.data?.length ?? 0})
            </button>
            <button type="button" role="tab" aria-selected={workSub === 'materials'} className={workSub === 'materials' ? 'on' : ''} onClick={() => setWorkSub('materials')}>
              <Icon name="file" size={15} /> Materials
            </button>
          </div>

          {workSub === 'materials' ? (
            <MaterialsPanel courseId={c.id} inst={!!inst} />
          ) : (
            <>
              {!inst && mine.data && mine.data.assignments.length > 0 && (() => {
                const cells = mine.data.rows[0]?.cells ?? []
                const graded = cells.filter(x => x.status === 'GRADED').length
                return (
                  <div className="myprogress" role="group" aria-label="Your progress in this class">
                    <div><b>{graded}</b> of {cells.length} graded<span className="muted small"> / {cells.filter(x => x.status === 'NOT_STARTED').length} not started{cells.some(x => x.status === 'MISSING') && ` / ${cells.filter(x => x.status === 'MISSING').length} missed`}{mine.data.rows[0]?.percent != null && ` / ${mine.data.rows[0].percent}% overall`}</span></div>
                    <div className="meter" aria-hidden="true"><i style={{ width: `${Math.round((graded / Math.max(1, cells.length)) * 100)}%` }} /></div>
                  </div>)
              })()}
              <div className="toolbar">
                <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Filter assignments" placeholder="Filter classwork" value={q} onChange={e => setQ(e.target.value)} /></div>
                <div className="segmented" role="tablist" aria-label="Kind" style={{ width: 260 }}>
                  {[['', 'All'], ['CODING', 'Coding'], ['QUIZ', 'Quizzes']].map(([k, l]) => <button key={k} type="button" role="tab" aria-selected={kind === k} className={kind === k ? 'on' : ''} onClick={() => setKind(k)}>{l}</button>)}
                </div>
                {inst && <><span className="grow" /><button className="primary" onClick={() => setTab('manage')}><Icon name="plus" />New assignment</button></>}
              </div>
              {assignments.loading ? <Loading /> : work.length === 0
                ? <Empty icon="task">{(assignments.data ?? []).length ? 'Nothing matches the filter.' : `No assignments${inst ? ' yet. Use "New assignment" to create one.' : ' published yet.'}`}</Empty>
                : <div className="timeline">{work.map(a => { const d = dueIn(a.deadline); const cell = cellOf(a.id); const scheduled = a.availableFrom && new Date(a.availableFrom).getTime() > Date.now(); return (
                  <Link key={a.id} to={`/assignments/${a.id}`} className={`titem${inst && (!a.published || scheduled) ? ' draft' : ''}`}>
                    <span className={`ico k-${a.kind.toLowerCase()}`}><Icon name={a.kind === 'QUIZ' ? 'task' : 'code'} /></span>
                    <div><strong>{a.title}</strong><div className="meta"><span>{KIND_LABEL[a.kind]}{a.kind === 'CODING' && ` (${LANGUAGE_LABEL[a.language] ?? a.language})`}{a.kind === 'QUIZ' && ` (${a.questionCount} questions)`}</span><span>{fmtMarks(a.maxMarks)} marks</span><span>Due {when(a.deadline)}</span>
                      {inst && !a.published && <span>Draft</span>}{inst && a.published && scheduled && <span>Opens {when(a.availableFrom!)}</span>}{a.archived && <span>Archived</span>}</div></div>
                    <span className="trail">{!inst && cell && <StatusChip c={cell} />}<span className={`due${d.soon ? ' soon' : ''}`}>{d.text}</span></span>
                  </Link>) })}</div>}
            </>
          )}
        </div>
      )}

      {activeCluster === 'community' && (
        <div id="panel-community" role="tabpanel" aria-labelledby="tab-community">
          <div className="segmented" role="tablist" aria-label="Community sections" style={{ maxWidth: 300, marginBottom: 20 }}>
            <button type="button" role="tab" aria-selected={commSub === 'news'} className={commSub === 'news' ? 'on' : ''} onClick={() => setCommSub('news')}>
              <Icon name="megaphone" size={15} /> Announcements ({ann.data?.length ?? 0})
            </button>
            <button type="button" role="tab" aria-selected={commSub === 'discuss'} className={commSub === 'discuss' ? 'on' : ''} onClick={() => setCommSub('discuss')}>
              <Icon name="chat" size={15} /> Discussions
            </button>
          </div>
          {commSub === 'news' ? (
            <AnnouncementsPanel courseId={c.id} inst={!!inst} items={ann.data ?? []} loading={ann.loading} error={ann.error} reload={() => ann.reload(true)} />
          ) : (
            <DiscussionsPanel courseId={c.id} inst={!!inst} />
          )}
        </div>
      )}

      {activeCluster === 'grades' && (
        <div id="panel-grades" role="tabpanel" aria-labelledby="tab-grades">
          <div className="segmented" role="tablist" aria-label="Grades and roster sections" style={{ maxWidth: inst ? 400 : 260, marginBottom: 20 }}>
            <button type="button" role="tab" aria-selected={gradeSub === 'grades'} className={gradeSub === 'grades' ? 'on' : ''} onClick={() => setGradeSub('grades')}>
              <Icon name="task" size={15} /> {inst ? 'Gradebook' : 'My Grades'}
            </button>
            <button type="button" role="tab" aria-selected={gradeSub === 'people'} className={gradeSub === 'people' ? 'on' : ''} onClick={() => setGradeSub('people')}>
              <Icon name="people" size={15} /> {inst ? 'People & Staff' : 'Classmates'}
            </button>
            {inst && (
              <button type="button" role="tab" aria-selected={gradeSub === 'analytics'} className={gradeSub === 'analytics' ? 'on' : ''} onClick={() => setGradeSub('analytics')}>
                <Icon name="chart" size={15} /> Analytics
              </button>
            )}
          </div>
          {gradeSub === 'grades' && (inst ? <GradebookPanel courseId={c.id} /> : <MyGrades book={mine.data} loading={mine.loading} />)}
          {gradeSub === 'people' && <PeoplePanel c={c} inst={!!inst} />}
          {gradeSub === 'analytics' && inst && <AnalyticsPanel courseId={c.id} />}
        </div>
      )}

      {activeCluster === 'manage' && inst && <ManagePanel c={c} reload={() => course.reload(true)} reloadWork={() => assignments.reload(true)} />}
    </>
  )
}

function OverviewPanel({ c, inst, announcements, go }: { c: Course; inst: boolean; announcements: Announcement[]; go: (t: string) => void }) {
  const o = useFetch(() => api.get<Overview>(`/api/courses/${c.id}/overview`), [c.id])
  if (o.loading) return <Loading />
  const d = o.data
  if (!d) return <ErrorBox message={o.error} />
  const pinned = announcements.filter(a => a.pinned).slice(0, 1)
  const latest = announcements.filter(a => !a.pinned).slice(0, 2)
  return (
    <div id="panel-overview" role="tabpanel" className="overview">
      <div className="ov-main">
        {(c.description || c.startsOn) && <div className="card"><h2>About this course</h2>
          {c.description && <p style={{ whiteSpace: 'pre-wrap' }}>{c.description}</p>}
          <p className="small muted" style={{ margin: 0 }}>{[c.subject, c.startsOn && c.endsOn ? `${new Date(c.startsOn).toLocaleDateString()} – ${new Date(c.endsOn).toLocaleDateString()}` : null].filter(Boolean).join(' — ')}</p></div>}
        <div className="statrow">
          {inst ? <>
            <div><b>{d.students}</b><span>students</span></div>
            <div><b>{d.published}</b><span>published{d.drafts > 0 && `, ${d.drafts} draft${d.drafts === 1 ? '' : 's'}`}</span></div>
            <div><b>{d.averagePct == null ? '–' : `${d.averagePct}%`}</b><span>class average</span></div>
            <div><b>{d.completionPct == null ? '–' : `${d.completionPct}%`}</b><span>of due work handed in</span></div>
            <div><b>{d.needsMarking}</b><span>attempts to mark</span></div>
          </> : <>
            <div><b>{d.myPercent == null ? '–' : `${d.myPercent}%`}</b><span>your overall score</span></div>
            <div><b>{d.myCompleted}</b><span>graded pieces of work</span></div>
            <div><b>{d.myMissing}</b><span>missed deadlines</span></div>
            <div><b>{d.students}</b><span>classmates</span></div>
          </>}
        </div>
        <div className="card"><div className="row space"><h2 style={{ margin: 0 }}>Coming up</h2><button className="quiet" onClick={() => go('work')}>All classwork<Icon name="right" size={15} /></button></div>
          {d.upcoming.length === 0 ? <p className="muted small" style={{ marginTop: 12 }}>Nothing due soon.</p> : (
            <ul className="duelist">{d.upcoming.map(u => { const due = dueIn(u.deadline); return (
              <li key={u.assignmentId}><Link to={`/assignments/${u.assignmentId}`}><strong>{u.title}</strong></Link><span className="small muted">{KIND_LABEL[u.kind]} / {fmtMarks(u.maxMarks)} marks</span>
                <span className="grow" />{u.handedIn != null && <span className="small muted">{u.handedIn}/{d.students} handed in</span>}
                {u.myStatus && <span className={`badge ${STATUS_TONE[u.myStatus] ?? 't-muted'}`}>{STATUS_LABEL[u.myStatus]}</span>}
                <span className={`due${due.soon ? ' soon' : ''}`}>{due.text}</span></li>) })}</ul>)}
        </div>
        {inst && d.atRisk.length > 0 && (
          <div className="card"><h2>Students who may need help</h2><p className="hint" style={{ marginTop: 0 }}>From marks and missed deadlines only. A prompt to check in, not a judgement.</p>
            <ul className="risklist">{d.atRisk.map(r => <li key={r.studentId}><Avatar name={r.name} size={28} /><strong>{r.name}</strong><span className="small muted">{r.reason}</span><span className="grow" />{r.percent != null && <span className="small">{r.percent}%</span>}</li>)}</ul></div>)}
      </div>
      <aside className="ov-side">
        {(pinned.length > 0 || latest.length > 0) && <div className="panel"><h2><Icon name="megaphone" />Announcements</h2>
          {[...pinned, ...latest].map(a => <div key={a.id} className="ov-ann">{a.pinned && <span className="badge t-warn"><Icon name="pin" size={12} />Pinned</span>}<strong>{a.title}</strong><p className="small">{a.body.length > 160 ? `${a.body.slice(0, 160)}…` : a.body}</p><span className="small muted">{a.author} / {when(a.createdAt)}</span></div>)}
          <button className="quiet" onClick={() => go('news')}>All announcements</button></div>}
        <div className="panel"><h2><Icon name="clock" />Recent activity</h2>
          {d.activity.length === 0 ? <p className="muted small">Nothing yet.</p> : <ul className="activity">{d.activity.map((e, i) => (
            <li key={i}><span className={`adot a-${e.kind}`} aria-hidden="true" /><div>
              <span className="small">{e.kind === 'announcement' ? <><b>{e.who}</b> posted “{e.what}”</> : e.kind === 'enrolment' ? <><b>{e.who}</b> joined the class</> : <><b>{inst ? e.who : 'You'}</b> {e.kind === 'quiz' ? 'submitted' : 'submitted code for'} {e.assignmentId ? <Link to={`/assignments/${e.assignmentId}`}>{e.what}</Link> : e.what}{e.kind === 'submission' && e.detail.length <= 3 && <> <VerdictBadge verdict={e.detail} /></>}</>}</span>
              <div className="small muted">{when(e.at)}</div></div></li>))}</ul>}
        </div>
      </aside>
    </div>
  )
}

function MyGrades({ book, loading }: { book: Gradebook | null; loading: boolean }) {
  if (loading) return <Loading />
  const row = book?.rows[0]
  if (!book || !row || book.assignments.length === 0) return <Empty icon="grid">Grades appear here once work is published.</Empty>
  return (
    <div id="panel-grades" role="tabpanel">
      <div className="statrow">
        <div><b>{row.percent == null ? '–' : `${row.percent}%`}</b><span>overall ({fmtMarks(row.totalScore)} of {fmtMarks(row.totalMax)} counted marks)</span></div>
        <div><b>{row.completed}</b><span>graded</span></div><div><b>{row.missing}</b><span>missed</span></div><div><b>{row.late}</b><span>late</span></div>
      </div>
      <div className="card" style={{ padding: 0 }}><div className="tablewrap"><table>
        <thead><tr><th>Assignment</th><th>Due</th><th>Status</th><th>Attempts</th></tr></thead>
        <tbody>{book.assignments.map((a, i) => { const c = row.cells[i]; return (
          <tr key={a.id}><td><Link to={`/assignments/${a.id}`}>{a.title}</Link><div className="small muted">{KIND_LABEL[a.kind]} / {fmtMarks(a.maxMarks)} marks</div></td><td>{when(a.deadline)}</td><td><StatusChip c={c} /></td><td>{c.attempts || '–'}</td></tr>) })}</tbody></table></div></div>
      <p className="hint">The overall score counts graded work and missed deadlines (as zero). Work not yet due or still being marked is left out. Quiz marks appear once your instructor releases them.</p>
    </div>
  )
}

function GradebookPanel({ courseId }: { courseId: number }) {
  const q = useFetch(() => api.get<Gradebook>(`/api/courses/${courseId}/gradebook`), [courseId])
  const [err, setErr] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [show, setShow] = useState('all')
  const [sort, setSort] = useState('name')
  const g = q.data
  const rows = useMemo(() => {
    let r = g?.rows ?? []
    const t = search.trim().toLowerCase()
    if (t) r = r.filter(x => `${x.name} ${x.email}`.toLowerCase().includes(t))
    if (show === 'missing') r = r.filter(x => x.missing > 0)
    if (show === 'late') r = r.filter(x => x.late > 0)
    if (show === 'low') r = r.filter(x => x.percent != null && x.percent < 40)
    const by: Record<string, (a: typeof r[0], b: typeof r[0]) => number> = { name: (a, b) => a.name.localeCompare(b.name), high: (a, b) => (b.percent ?? -1) - (a.percent ?? -1), low: (a, b) => (a.percent ?? 101) - (b.percent ?? 101), missing: (a, b) => b.missing - a.missing }
    return [...r].sort(by[sort])
  }, [g, search, show, sort])
  if (q.loading) return <Loading />
  if (!g) return <ErrorBox message={q.error} />
  if (g.assignments.length === 0 || g.rows.length === 0) return <Empty icon="grid">The gradebook fills in once the class has students and a published assignment.</Empty>
  const colAvg = g.assignments.map((_, i) => { const s = g.rows.map(r => r.cells[i].score).filter((x): x is number => x != null); return s.length ? s.reduce((x, y) => x + y, 0) / s.length : null })
  return (
    <div id="panel-grades" role="tabpanel">
      <div className="toolbar">
        <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Search students" placeholder="Search students" value={search} onChange={e => setSearch(e.target.value)} /></div>
        <select aria-label="Show" value={show} onChange={e => setShow(e.target.value)}><option value="all">All students</option><option value="missing">With missed work</option><option value="late">With late work</option><option value="low">Below 40%</option></select>
        <select aria-label="Sort" value={sort} onChange={e => setSort(e.target.value)}><option value="name">Name A to Z</option><option value="high">Highest overall</option><option value="low">Lowest overall</option><option value="missing">Most missed</option></select>
        <span className="grow" /><span className="small muted">{rows.length} of {g.rows.length}</span>
        <button onClick={() => download(`/api/courses/${courseId}/gradebook.csv`, `gradebook-course-${courseId}.csv`).catch(e => setErr((e as Error).message))}><Icon name="download" size={16} />Export CSV</button>
      </div>
      <ErrorBox message={err} />
      <div className="tablewrap card gbwrap" style={{ padding: 0 }}>
        <table className="gradebook">
          <thead><tr><th>Student</th><th className="num">Overall</th>{g.assignments.map(a => <th key={a.id}><Link to={`/assignments/${a.id}`}>{a.title}</Link><div className="small muted">{KIND_LABEL[a.kind]} / {fmtMarks(a.maxMarks)}</div></th>)}</tr></thead>
          <tbody>{rows.map(r => (
            <tr key={r.studentId}>
              <th scope="row"><div className="row" style={{ flexWrap: 'nowrap' }}><Avatar name={r.name} size={26} /><span>{r.name}</span></div></th>
              <td className="num"><b>{r.percent == null ? '–' : `${r.percent}%`}</b>{r.missing > 0 && <div className="small bad">{r.missing} missed</div>}</td>
              {r.cells.map((c, i) => <td key={i} className={`gc s-${c.status.toLowerCase()}`} title={`${STATUS_LABEL[c.status]}${c.late ? ', late' : ''}${c.attempts ? `, ${c.attempts} attempt${c.attempts === 1 ? '' : 's'}` : ''}`}>
                {c.score != null ? <span className={`gscore${c.score / c.maxMarks >= 0.8 ? ' hi' : c.score / c.maxMarks < 0.4 ? ' lo' : ''}`}>{fmtMarks(c.score)}</span> : <span className="small muted">{c.status === 'MISSING' ? 'missing' : c.status === 'PENDING' ? 'to mark' : c.status === 'IN_PROGRESS' ? 'working' : '–'}</span>}
                {c.late && <em className="late">late</em>}</td>)}
            </tr>))}</tbody>
          <tfoot><tr><th scope="row">Class average</th><td /> {colAvg.map((x, i) => <td key={i} className="small muted">{x == null ? '–' : fmtMarks(Math.round(x * 10) / 10)}</td>)}</tr></tfoot>
        </table>
      </div>
      <p className="hint">Best attempt per student. Coding work scores the share of tests passed (or your override); quizzes score the best submitted attempt. Late penalties are already applied. Missed deadlines count as zero in "Overall".</p>
    </div>
  )
}

function PeoplePanel({ c, inst }: { c: Course; inst: boolean }) {
  const staff = useFetch(() => api.get<Staff[]>(`/api/courses/${c.id}/staff`), [c.id])
  const students = useFetch(() => (inst ? api.get<StudentRow[]>(`/api/courses/${c.id}/students`) : Promise.resolve([] as StudentRow[])), [c.id, inst])
  const [q, setQ] = useState('')
  const [confirm, setConfirm] = useState<number | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const list = (students.data ?? []).filter(s => `${s.displayName} ${s.email}`.toLowerCase().includes(q.trim().toLowerCase()))
  const roleName = { LEAD: 'Lead instructor', CO_INSTRUCTOR: 'Co-instructor', TA: 'Teaching assistant' }
  async function remove(sid: number) { setErr(null); try { await api.delete(`/api/courses/${c.id}/students/${sid}`); setConfirm(null); await students.reload(true) } catch (e) { setErr((e as Error).message) } }
  return (
    <div id="panel-people" role="tabpanel">
      <div className="card"><h2>Teaching team</h2>
        {staff.loading ? <Loading /> : (staff.data ?? []).map(s => <div className="attempt" key={s.userId}><div className="row"><Avatar name={s.name} /><div><strong>{s.name}</strong>{s.email && <div className="small muted">{s.email}</div>}</div></div><span className="badge t-muted">{roleName[s.role]}</span></div>)}</div>
      {inst ? (
        <div className="card"><div className="row space"><h2 style={{ margin: 0 }}>Students <span className="muted small">({students.data?.length ?? 0})</span></h2>
          <div className="searchfield" style={{ maxWidth: 280 }}><Icon name="search" size={16} /><input type="search" aria-label="Search students" placeholder="Search" value={q} onChange={e => setQ(e.target.value)} /></div></div>
          <ErrorBox message={err} />
          {!students.data || students.data.length === 0 ? <Empty icon="people">No students yet. Share the join code, or add existing accounts from Manage.</Empty> : (
            <div className="roster">{list.map(s => <div className="attempt" key={s.id}><div className="row"><Avatar name={s.displayName} /><div><strong>{s.displayName}</strong><div className="small muted">{s.email}</div></div></div>
              <div className="row"><span className="small muted">Joined {when(s.enrolledAt)}</span>
                {confirm === s.id ? <><button className="quiet danger" onClick={() => remove(s.id)}>Remove from class</button><button className="quiet" onClick={() => setConfirm(null)}>Keep</button></>
                  : <button className="quiet" onClick={() => setConfirm(s.id)} aria-label={`Remove ${s.displayName}`}>Remove</button>}</div></div>)}</div>)}
          <p className="hint">Removing a student keeps their submitted work on record; they lose access to the class.</p></div>
      ) : <div className="card"><p style={{ margin: 0 }}><b>{c.students}</b> students are enrolled in this class.</p></div>}
    </div>
  )
}

function AnalyticsPanel({ courseId }: { courseId: number }) {
  const q = useFetch(() => api.get<CourseAnalytics>(`/api/courses/${courseId}/analytics`), [courseId])
  if (q.loading) return <Loading />
  const d = q.data
  if (!d) return <ErrorBox message={q.error} />
  return (
    <div id="panel-analytics" role="tabpanel">
      <div className="grid g2">
        <div className="card"><h2>Enrolment</h2><p className="hint" style={{ marginTop: 0 }}>Students enrolled, week by week.</p><LineChart points={d.enrollment.map(e => ({ x: e.date, y: e.students }))} label="Students enrolled" /></div>
        <div className="card"><h2>Weekly activity</h2><p className="hint" style={{ marginTop: 0 }}>Submissions over the last ten weeks.</p><WeeklyBars weeks={d.weekly} /></div>
      </div>
      <div className="card"><h2>Assignments</h2>
        <div className="tablewrap"><table>
          <thead><tr><th>Assignment</th><th>Handed in</th><th className="num">Average</th><th className="num">Median</th><th className="num">Range</th><th>Late</th><th>To mark</th></tr></thead>
          <tbody>{d.assignments.map(a => (
            <tr key={a.id}><td><Link to={`/assignments/${a.id}`}>{a.title}</Link><div className="small muted">{KIND_LABEL[a.kind]} / due {when(a.deadline)}</div></td>
              <td><div className="minibar" title={`${a.handedIn} of ${a.enrolled}`}><i style={{ width: `${(a.handedIn / Math.max(1, a.enrolled)) * 100}%` }} /></div><span className="small">{a.handedIn}/{a.enrolled}</span></td>
              <td className="num">{a.averagePct == null ? '–' : `${a.averagePct}%`}</td><td className="num">{a.medianPct == null ? '–' : `${a.medianPct}%`}</td>
              <td className="num small">{a.lowestPct == null ? '–' : `${a.lowestPct}–${a.highestPct}%`}</td><td>{a.late || '–'}</td><td>{a.needsMarking || '–'}</td></tr>))}</tbody></table></div></div>
      <div className="grid g2">
        <div className="card"><h2>Overall score spread</h2><p className="hint" style={{ marginTop: 0 }}>Students by overall percentage.</p><Histogram buckets={d.distribution} /></div>
        <div className="card"><h2>Hardest quiz questions</h2><p className="hint" style={{ marginTop: 0 }}>Lowest average marks, from at least three answers.</p>
          {d.hardestQuestions.length === 0 ? <p className="muted small">Not enough answers yet.</p> : <ol className="hardest">{d.hardestQuestions.map(h => <li key={h.questionId}><div className="row space" style={{ flexWrap: 'nowrap' }}><span className="small">{h.prompt}</span><b>{h.averagePct}%</b></div><div className="small muted">{h.assignment} / {h.answers} answers</div></li>)}</ol>}</div>
      </div>
      {d.languages.length > 0 && <div className="card"><h2>Coding by language</h2><div className="tablewrap"><table><thead><tr><th>Language</th><th className="num">Submissions</th><th className="num">Accepted</th><th className="num">Average run time</th></tr></thead>
        <tbody>{d.languages.map(l => <tr key={l.language}><td>{LANGUAGE_LABEL[l.language] ?? l.language}</td><td className="num">{l.submissions}</td><td className="num">{Math.round((l.accepted / Math.max(1, l.submissions)) * 100)}%</td><td className="num">{l.avgMillis} ms</td></tr>)}</tbody></table></div></div>}
    </div>
  )
}

function ManagePanel({ c, reload, reloadWork }: { c: Course; reload: () => Promise<unknown>; reloadWork: () => Promise<unknown> }) {
  const navigate = useNavigate()
  const [f, setF] = useState({ kind: 'CODING', title: '', description: '', language: 'python', deadline: '', timeLimitMs: 2000, memoryLimitMb: 256, maxMarks: 20, maxAttempts: 10, latePolicy: 'BLOCK', quizMinutes: '' as number | '' })
  const [cf, setCf] = useState({ code: c.code, title: c.title, subject: c.subject, category: c.category, description: c.description, startsOn: c.startsOn ?? '', endsOn: c.endsOn ?? '' })
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [emails, setEmails] = useState('')
  const [staffEmail, setStaffEmail] = useState(''), [staffRole, setStaffRole] = useState('CO_INSTRUCTOR')
  const [danger, setDanger] = useState(false)
  const staff = useFetch(() => api.get<Staff[]>(`/api/courses/${c.id}/staff`), [c.id])
  const run = async (fn: () => Promise<unknown>, ok?: string) => { setErr(null); setMsg(null); try { await fn(); if (ok) setMsg(ok) } catch (e) { setErr((e as Error).message) } }

  const create = (e: FormEvent) => { e.preventDefault(); void run(async () => {
    const options: Record<string, unknown> = { maxAttempts: f.maxAttempts, latePolicy: f.latePolicy }
    if (f.kind === 'CODING') options.maxMarks = f.maxMarks
    if (f.kind === 'QUIZ' && f.quizMinutes !== '') options.quizMinutes = f.quizMinutes
    const a = await api.post<Assignment>(`/api/courses/${c.id}/assignments`, { title: f.title, description: f.description, kind: f.kind, language: f.kind === 'QUIZ' ? 'none' : f.language, deadline: new Date(f.deadline).toISOString(), timeLimitMs: f.timeLimitMs, memoryLimitMb: f.memoryLimitMb, options })
    await reloadWork(); navigate(`/assignments/${a.id}${f.kind === 'QUIZ' ? '?tab=questions' : ''}`)
  }) }
  const saveCourse = (e: FormEvent) => { e.preventDefault(); void run(async () => { await api.patch(`/api/courses/${c.id}`, { ...cf, startsOn: cf.startsOn || null, endsOn: cf.endsOn || null }); await reload() }, 'Course details saved.') }
  const bulk = () => run(async () => {
    const list = emails.split(/[\s,;]+/).map(x => x.trim()).filter(Boolean)
    const r = await api.post<{ added: string[]; alreadyEnrolled: string[]; notFound: string[] }>(`/api/courses/${c.id}/enrollments`, { emails: list })
    setEmails(r.notFound.join('\n')); await reload()
    setMsg(`${r.added.length} added, ${r.alreadyEnrolled.length} already enrolled${r.notFound.length ? `, ${r.notFound.length} without a student account (left in the box)` : ''}.`)
  })

  return (
    <div id="panel-manage" role="tabpanel">
      <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
      <form className="card" onSubmit={create}><h2>New assignment</h2>
        <div className="segmented" role="radiogroup" aria-label="Kind of assignment" style={{ maxWidth: 420, marginBottom: 16 }}>
          <button type="button" role="radio" aria-checked={f.kind === 'CODING'} className={f.kind === 'CODING' ? 'on' : ''} onClick={() => setF({ ...f, kind: 'CODING' })}><Icon name="code" size={15} />Coding problem</button>
          <button type="button" role="radio" aria-checked={f.kind === 'QUIZ'} className={f.kind === 'QUIZ' ? 'on' : ''} onClick={() => setF({ ...f, kind: 'QUIZ' })}><Icon name="task" size={15} />Quiz / question set</button>
        </div>
        <p className="hint" style={{ marginTop: -8 }}>{f.kind === 'CODING' ? 'Students submit a program; it is judged against your sample and hidden tests in a sandbox.' : 'Several questions of different types (multiple choice, short answer, output prediction, essays, file uploads). You add the questions next.'}</p>
        <Field id="atitle" label="Title"><input id="atitle" value={f.title} onChange={e => setF({ ...f, title: e.target.value })} required maxLength={150} /></Field>
        <Field id="adesc" label={f.kind === 'CODING' ? 'Problem statement' : 'Instructions'}><textarea id="adesc" value={f.description} onChange={e => setF({ ...f, description: e.target.value })} required /></Field>
        <div className="grid g3">
          {f.kind === 'CODING' && <Field id="alang" label="Language"><select id="alang" value={f.language} onChange={e => setF({ ...f, language: e.target.value })}>{Object.entries(LANGUAGE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>}
          <Field id="adead" label="Deadline"><input id="adead" type="datetime-local" value={f.deadline} onChange={e => setF({ ...f, deadline: e.target.value })} required /></Field>
          {f.kind === 'CODING' && <Field id="amarks" label="Maximum marks"><input id="amarks" type="number" min={1} value={f.maxMarks} onChange={e => setF({ ...f, maxMarks: Number(e.target.value) })} /></Field>}
          {f.kind === 'QUIZ' && <Field id="amin" label="Time limit (minutes)" hint="Empty for untimed."><input id="amin" type="number" min={1} max={600} value={f.quizMinutes} onChange={e => setF({ ...f, quizMinutes: e.target.value === '' ? '' : Number(e.target.value) })} /></Field>}
          <Field id="aatt" label="Attempts allowed"><input id="aatt" type="number" min={1} max={50} value={f.maxAttempts} onChange={e => setF({ ...f, maxAttempts: Number(e.target.value) })} /></Field>
          <Field id="alate" label="Late work"><select id="alate" value={f.latePolicy} onChange={e => setF({ ...f, latePolicy: e.target.value })}><option value="BLOCK">Not accepted</option><option value="ALLOW">Accepted, marked late</option><option value="PENALTY">Accepted with 0% penalty (set later)</option></select></Field>
          {f.kind === 'CODING' && <><Field id="atime" label="Time limit per test (ms)"><input id="atime" type="number" min={100} max={20000} value={f.timeLimitMs} onChange={e => setF({ ...f, timeLimitMs: Number(e.target.value) })} required /></Field>
            <Field id="amem" label="Memory limit (MB)"><input id="amem" type="number" min={16} max={1024} value={f.memoryLimitMb} onChange={e => setF({ ...f, memoryLimitMb: Number(e.target.value) })} required /></Field></>}
        </div>
        <button className="primary" type="submit"><Icon name="plus" />Create draft</button>
        <p className="hint">Drafts are invisible to students. More options (release mode, shuffling, scheduling, penalties) are on the assignment's settings.</p></form>

      <form className="card" onSubmit={saveCourse}><h2>Course details</h2>
        <div className="grid g3"><Field id="mcode" label="Code"><input id="mcode" value={cf.code} onChange={e => setCf({ ...cf, code: e.target.value })} maxLength={30} required /></Field>
          <Field id="mtitle" label="Title"><input id="mtitle" value={cf.title} onChange={e => setCf({ ...cf, title: e.target.value })} maxLength={150} required /></Field>
          <Field id="msub" label="Subject"><input id="msub" value={cf.subject} onChange={e => setCf({ ...cf, subject: e.target.value })} maxLength={80} /></Field>
          <Field id="mcat" label="Category"><input id="mcat" value={cf.category} onChange={e => setCf({ ...cf, category: e.target.value })} maxLength={60} /></Field>
          <Field id="mstart" label="Starts"><input id="mstart" type="date" value={cf.startsOn} onChange={e => setCf({ ...cf, startsOn: e.target.value })} /></Field>
          <Field id="mend" label="Ends"><input id="mend" type="date" value={cf.endsOn} onChange={e => setCf({ ...cf, endsOn: e.target.value })} /></Field></div>
        <Field id="mdesc" label="Description"><textarea id="mdesc" value={cf.description} onChange={e => setCf({ ...cf, description: e.target.value })} maxLength={5000} /></Field>
        <button className="primary" type="submit">Save details</button></form>

      <div className="grid g2" style={{ alignItems: 'start' }}>
        <div className="card"><h2>Add students</h2>
          <Field id="bulk" label="Email addresses" hint="Paste a column from a spreadsheet: one per line, or separated by commas. Only existing student accounts are added; anyone else can join with the code."><textarea id="bulk" value={emails} onChange={e => setEmails(e.target.value)} /></Field>
          <button onClick={bulk} disabled={!emails.trim()}><Icon name="people" size={16} />Enrol</button></div>
        <div className="card"><h2>Teaching team</h2>
          {(staff.data ?? []).map(s => <div className="attempt" key={s.userId}><div><strong>{s.name}</strong><div className="small muted">{s.role === 'LEAD' ? 'Lead instructor' : s.role === 'TA' ? 'Teaching assistant' : 'Co-instructor'}</div></div>
            {c.lead && s.role !== 'LEAD' && <button className="quiet" onClick={() => run(async () => { await api.delete(`/api/courses/${c.id}/staff/${s.userId}`); await staff.reload(true); await reload() }, `${s.name} removed.`)}>Remove</button>}</div>)}
          {c.lead ? <div className="row" style={{ marginTop: 12 }}>
            <input aria-label="Instructor email" placeholder="colleague@university.edu" value={staffEmail} onChange={e => setStaffEmail(e.target.value)} style={{ flex: 1, minWidth: 180 }} />
            <select aria-label="Role" value={staffRole} onChange={e => setStaffRole(e.target.value)} style={{ width: 'auto' }}><option value="CO_INSTRUCTOR">Co-instructor</option><option value="TA">TA</option></select>
            <button disabled={!staffEmail.trim()} onClick={() => run(async () => { await api.post(`/api/courses/${c.id}/staff`, { email: staffEmail, role: staffRole }); setStaffEmail(''); await staff.reload(true); await reload() }, 'Added to the teaching team.')}>Add</button></div>
            : <p className="hint">Only the lead instructor changes the team.</p>}
          <p className="hint">Co-instructors and TAs can do everything you can in this course except change the team or delete it.</p></div>
      </div>

      <div className="card"><h2>Course actions</h2>
        <div className="actionlist">
          <div><div><strong>New join code</strong><p className="small muted">The old code stops working immediately.</p></div><button onClick={() => run(async () => { await api.patch(`/api/courses/${c.id}`, { rotateEnrollmentCode: true }); await reload() }, 'New join code created.')}><Icon name="refresh" size={16} />Replace code</button></div>
          <div><div><strong>Copy for a new term</strong><p className="small muted">Copies the details, assignments (as drafts), tests and questions. No students or work.</p></div><button onClick={() => run(async () => { const n = await api.post<Course>(`/api/courses/${c.id}/clone`); navigate(`/courses/${n.id}?tab=manage`) })}><Icon name="copyplus" size={16} />Copy course</button></div>
          <div><div><strong>{c.archived ? 'Restore course' : 'Archive course'}</strong><p className="small muted">Archiving hides the class from students and stops submissions. Nothing is deleted.</p></div><button onClick={() => run(async () => { await api.post(`/api/courses/${c.id}/archive?archived=${!c.archived}`); await reload() }, c.archived ? 'Course restored.' : 'Course archived.')}><Icon name="archive" size={16} />{c.archived ? 'Restore' : 'Archive'}</button></div>
          {c.lead && <div><div><strong>Delete course</strong><p className="small muted">Only possible while no student has handed in work. Otherwise archive it.</p></div>
            {danger ? <div className="row"><button className="quiet danger" onClick={() => run(async () => { await api.delete(`/api/courses/${c.id}`); navigate('/courses') })}>Delete permanently</button><button className="quiet" onClick={() => setDanger(false)}>Cancel</button></div>
              : <button className="quiet danger" onClick={() => setDanger(true)}><Icon name="trash" size={16} />Delete</button>}</div>}
        </div>
      </div>
    </div>
  )
}

function AnnouncementsPanel({ courseId, inst, items, loading, error, reload }: { courseId: number; inst: boolean; items: Announcement[]; loading: boolean; error: string | null; reload: () => Promise<unknown> }) {
  const [title, setTitle] = useState(''), [body, setBody] = useState(''), [pinned, setPinned] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const guard = async (f: () => Promise<unknown>) => { setErr(null); try { await f(); await reload() } catch (e) { setErr((e as Error).message) } }
  async function post(e: FormEvent) {
    e.preventDefault(); setBusy(true)
    await guard(async () => { await api.post(`/api/courses/${courseId}/announcements`, { title: title.trim(), body: body.trim(), pinned }); setTitle(''); setBody(''); setPinned(false) })
    setBusy(false)
  }
  return (
    <div id="panel-news" role="tabpanel" aria-labelledby="tab-news">
      <ErrorBox message={err ?? error} />
      {inst && (
        <form className="card" onSubmit={post}><h2>Post an announcement</h2>
          <fieldset disabled={busy} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>
          <Field id="antitle" label="Title"><input id="antitle" value={title} onChange={e => setTitle(e.target.value)} required maxLength={150} placeholder="Lab moved to Room 12" /></Field>
          <Field id="anbody" label="Message" hint="Plain text. Line breaks are kept. Enrolled students see this on the class page."><textarea id="anbody" value={body} onChange={e => setBody(e.target.value)} required maxLength={5000} /></Field>
          <div className="row space"><label className="row small" style={{ fontWeight: 500, margin: 0 }}><input type="checkbox" checked={pinned} onChange={e => setPinned(e.target.checked)} /> Pin to the top</label>
            <button className="primary" type="submit" disabled={busy || !title.trim() || !body.trim()}><Icon name="megaphone" />Post</button></div>
          </fieldset>
        </form>)}
      {loading ? <Loading /> : items.length === 0
        ? <Empty icon="megaphone">{inst ? 'Nothing posted yet. Announcements you post here appear for every enrolled student.' : 'No announcements yet.'}</Empty>
        : items.map(a => (
          <article className={`card post${a.pinned ? ' pinned' : ''}`} key={a.id}>
            <header className="row space"><div className="row"><Avatar name={a.author} size={34} /><div><strong>{a.title}</strong><div className="small muted">{a.author} / {when(a.createdAt)}</div></div></div>
              {a.pinned && <span className="badge t-warn"><Icon name="pin" size={13} />Pinned</span>}</header>
            <p className="postbody">{a.body}</p>
            {inst && <div className="row">
              <button className="quiet" onClick={() => guard(() => api.post(`/api/announcements/${a.id}/pin?pinned=${!a.pinned}`))}><Icon name="pin" size={15} />{a.pinned ? 'Unpin' : 'Pin'}</button>
              {confirm === a.id
                ? <><button className="quiet danger" onClick={() => { setConfirm(null); void guard(() => api.delete(`/api/announcements/${a.id}`)) }}>Delete for everyone</button><button className="quiet" onClick={() => setConfirm(null)}>Keep</button></>
                : <button className="quiet" onClick={() => setConfirm(a.id)}><Icon name="trash" size={15} />Delete</button>}
            </div>}
          </article>))}
    </div>
  )
}
