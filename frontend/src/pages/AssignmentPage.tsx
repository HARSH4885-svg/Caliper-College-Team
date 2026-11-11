import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, tokenStore, type Analytics, type Assignment, type Submission, type TestCaseDto } from '../api'
import { useAuth } from '../auth'
import { Icon } from '../components/brand'
import { Histogram, TestBars, VerdictBar } from '../components/charts'
import { ENTRY_FILE, STARTER } from '../components/CodeEditor'
import { dueIn, Empty, ErrorBox, Field, Loading, PageHead, Tabs, VerdictBadge } from '../components/ui'
import { LANGUAGE_LABEL, VERDICT_LABEL, when } from '../labels'
import { AssessmentSettings } from '../components/AssessmentSettings'
import { QuizFacts, QuizInstructorView, QuizStudentView } from './QuizPages'
import { useFetch } from '../useFetch'

const pending = (s: Submission[]) => s.some(x => x.status === 'QUEUED' || x.status === 'RUNNING')

async function download(url: string, name: string) {
  const res = await fetch(url, { headers: { Authorization: `Bearer ${tokenStore.get() ?? ''}` } })
  if (!res.ok) throw new Error('Download failed')
  const a = document.createElement('a'); a.href = URL.createObjectURL(await res.blob()); a.download = name; a.click(); URL.revokeObjectURL(a.href)
}

export function AssignmentPage() {
  const { id } = useParams()
  const { me } = useAuth()
  const a = useFetch(() => api.get<Assignment>(`/api/assignments/${id}`), [id])
  if (a.loading) return <Loading />
  if (!a.data) return <ErrorBox message={a.error} />
  if (me?.role !== 'INSTRUCTOR') return a.data.kind === 'QUIZ' ? <QuizStudentView a={a.data} /> : <StudentView a={a.data} />
  return <InstructorView a={a.data} reload={() => a.reload(true)} />
}

function Facts({ a }: { a: Assignment }) {
  const d = dueIn(a.deadline)
  return (
    <div className="facts">
      <span className="fact"><Icon name="code" size={14} />{LANGUAGE_LABEL[a.language]}</span>
      <span className="fact"><Icon name="clock" size={14} />{d.text} / {when(a.deadline)}</span>
      <span className="fact">{a.timeLimitMs} ms</span>
      <span className="fact">{a.memoryLimitMb} MB</span>
    </div>
  )
}

function Progress({ status }: { status: string }) {
  const step = status === 'RUNNING' ? 1 : 0
  return (
    <ol className="progress" aria-live="polite" aria-label="Evaluation progress">
      {['Received', 'Judging in a sandbox', 'Result'].map((t, i) => <li key={t} className={i < step + 1 ? 'done' : ''} aria-current={i === step ? 'step' : undefined}><span />{t}</li>)}
    </ol>
  )
}

function StudentView({ a }: { a: Assignment }) {
  const subs = useFetch(() => api.get<Submission[]>(`/api/assignments/${a.id}/submissions/me`), [a.id], pending)
  const [file, setFile] = useState<File | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState<'upload' | 'write'>('upload')
  const [code] = useState(STARTER[a.language] ?? '')
  const late = new Date(a.deadline).getTime() < Date.now()
  const last = subs.data?.[0]

  async function submit(e: FormEvent) {
    e.preventDefault()
    const toSend = mode === 'write' ? new File([code], ENTRY_FILE[a.language] ?? 'main.txt', { type: 'text/plain' }) : file
    if (!toSend) return
    setBusy(true); setErr(null)
    try { const f = new FormData(); f.append('file', toSend); await api.upload(`/api/assignments/${a.id}/submissions`, f); setFile(null); const up = document.getElementById('upload') as HTMLInputElement | null; if (up) up.value = ''; await subs.reload(true) }
    catch (e2) { setErr((e2 as Error).message) } finally { setBusy(false) }
  }

  return (
    <>
      <PageHead title={a.title} back={{ to: `/courses/${a.courseId}`, label: 'Class' }} />
      <div className="split">
        <div>
          <div className="card"><Facts a={a} /><p className="statement">{a.description}</p></div>
          {!late && <Link to={`/assignments/${a.id}/code`} className="wsbanner"><Icon name="code" size={22} /><div><strong>Solve it in the coding workspace</strong><span>Full editor, run the examples or your own input, then submit. Your draft is kept in this browser.</span></div><Icon name="right" /></Link>}
          {a.testCases.length > 0 && <div className="card"><h2>Sample tests</h2>
            {a.testCases.map(t => <div className="sample" key={t.id}><h3>{t.name}</h3><div className="grid g2"><div><div className="small muted">Input</div><pre className="log">{t.input}</pre></div><div><div className="small muted">Expected output</div><pre className="log">{t.expectedOutput}</pre></div></div></div>)}
            <p className="hidden-note"><Icon name="lock" size={16} />{a.hiddenTestCount} more hidden test{a.hiddenTestCount === 1 ? '' : 's'} will also run. Their contents are not shown.</p></div>}
          {a.files.length > 0 && <div className="card"><h2>Starter files</h2><div className="row">{a.files.map(f => <button key={f.id} onClick={() => download(`/api/assignments/${a.id}/files/${f.id}`, f.name).catch(e => setErr(e.message))}><Icon name="download" size={16} />{f.name}</button>)}</div></div>}
        </div>
        <aside className="sticky">
          <form className="panel" onSubmit={submit} style={{ marginBottom: 20 }}>
            <h2><Icon name="upload" />Your work</h2>
            <ErrorBox message={err} />
            {last && (last.status === 'QUEUED' || last.status === 'RUNNING') && <Progress status={last.status} />}
            {last && <p className="row" style={{ marginBottom: 12 }}><VerdictBadge verdict={last.verdict} /><span className="small muted">{last.total != null ? `${last.passed}/${last.total} tests` : last.status.toLowerCase()}</span></p>}
            {late ? <div className="notice"><Icon name="clock" />The deadline has passed; new submissions are not accepted.</div> : (
              <>
                <Link className="btn primary wscta" to={`/assignments/${a.id}/code`}><Icon name="code" />Open coding workspace</Link>
                <p className="hint" style={{ textAlign: 'center' }}>Write, run against the examples and submit in the browser.</p>
                <details className="uploadalt"><summary>Or upload a file or .zip</summary>
                  <div className="drop"><Icon name="file" size={26} /><div className="small muted">A source file or a .zip (max 200 files, 2 MiB each)</div>
                    <label htmlFor="upload" className="sr-only">Source file or .zip</label><input id="upload" type="file" onChange={e => { setFile(e.target.files?.[0] ?? null); setMode('upload') }} /></div>
                <button type="submit" disabled={!file || busy} style={{ width: '100%', marginTop: 12 }}>{busy ? 'Sending…' : 'Submit file'}</button></details>
              </>)}
          </form>
          <div className="panel"><h2>Attempts</h2>
            {subs.loading ? <Loading /> : !subs.data || subs.data.length === 0 ? <p className="muted small">No attempts yet.</p> : subs.data.map(s => (
              <div className="attempt" key={s.id}><div><Link to={`/submissions/${s.id}`}>Attempt #{s.attempt}</Link><div className="small muted">{when(s.createdAt)}</div></div><VerdictBadge verdict={s.verdict} /></div>))}
          </div>
        </aside>
      </div>
    </>
  )
}

function InstructorView({ a, reload }: { a: Assignment; reload: () => Promise<unknown> }) {
  const [tab, setTab] = useState('subs')
  const navigate = useNavigate()
  const [verdict, setVerdict] = useState('')
  const [search, setSearch] = useState('')
  const [term, setTerm] = useState('')
  const [sort, setSort] = useState('new')
  const [latestOnly, setLatestOnly] = useState(false)
  const [rerun, setRerun] = useState<string | null>(null)
  useEffect(() => { const t = setTimeout(() => setTerm(search.trim()), 250); return () => clearTimeout(t) }, [search])
  const query = [verdict && `verdict=${verdict}`, term && `q=${encodeURIComponent(term)}`].filter(Boolean).join('&')
  const subs = useFetch(() => api.get<Submission[]>(`/api/assignments/${a.id}/submissions${query ? `?${query}` : ''}`), [a.id, query], pending)
  const shown = useMemo(() => {
    let rows = subs.data ?? []
    if (latestOnly) { const best = new Map<number | null, number>(); rows.forEach(r => best.set(r.studentId, Math.max(best.get(r.studentId) ?? 0, r.attempt))); rows = rows.filter(r => best.get(r.studentId) === r.attempt) }
    const score = (r: Submission) => (r.total ? (r.passed ?? 0) / r.total : -1)
    const by: Record<string, (x: Submission, y: Submission) => number> = {
      new: () => 0, old: (x, y) => new Date(x.createdAt).getTime() - new Date(y.createdAt).getTime(),
      name: (x, y) => (x.studentName ?? '').localeCompare(y.studentName ?? ''), high: (x, y) => score(y) - score(x), low: (x, y) => score(x) - score(y),
    }
    return [...rows].sort(by[sort] ?? by.new)
  }, [subs.data, latestOnly, sort])
  const [tests, setTests] = useState<TestCaseDto[]>(a.testCases)
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [kind, setKind] = useState('STARTER')

  const run = async (f: () => Promise<unknown>, ok: string) => { setErr(null); setMsg(null); try { await f(); setMsg(ok) } catch (e) { setErr((e as Error).message) } }
  const saveTests = () => run(async () => { await api.post(`/api/assignments/${a.id}/test-cases`, tests.map(t => ({ name: t.name, input: t.input, expectedOutput: t.expectedOutput, sample: t.sample }))); await reload() }, 'Test cases saved.')
  const unpublish = () => run(async () => { await api.post(`/api/assignments/${a.id}/unpublish`); await reload() }, 'Assignment hidden from students.')
  const archive = () => run(async () => { await api.post(`/api/assignments/${a.id}/archive?archived=${!a.archived}`); await reload() }, a.archived ? 'Assignment restored.' : 'Assignment archived.')
  const publish = () => run(async () => { await api.post(`/api/assignments/${a.id}/publish`); await reload() }, 'Assignment published to enrolled students.')
  const upload = (e: FormEvent<HTMLFormElement>) => { e.preventDefault(); const el = (e.currentTarget.elements.namedItem('afile') as HTMLInputElement); const f = el.files?.[0]; if (!f) return
    void run(async () => { const fd = new FormData(); fd.append('kind', kind); fd.append('file', f); await api.upload(`/api/assignments/${a.id}/files`, fd); el.value = ''; await reload() }, 'File attached.') }
  const duplicate = async () => { setErr(null); try { const copy = await api.post<Assignment>(`/api/assignments/${a.id}/duplicate`); navigate(`/assignments/${copy.id}`) } catch (e) { setErr((e as Error).message) } }
  const rerunMany = async (which: string) => { setRerun(null); setErr(null); setMsg(null); try { const r = await api.post<{ queued: number }>(`/api/assignments/${a.id}/rejudge?verdict=${which}`); setMsg(r.queued === 0 ? 'Nothing to re-run: no latest attempt matches.' : `${r.queued} submission${r.queued === 1 ? '' : 's'} queued for re-evaluation.`); await subs.reload(true) } catch (e) { setErr((e as Error).message) } }
  const exportCsv = () => download(`/api/assignments/${a.id}/submissions.csv`, `submissions-assignment-${a.id}.csv`).catch(e => setErr((e as Error).message))
  const rejudge = (sid: number) => run(async () => { await api.post(`/api/submissions/${sid}/rejudge`); await subs.reload(true) }, 'Re-evaluation queued.')

  const header = <>
      <PageHead back={{ to: `/courses/${a.courseId}`, label: 'Class' }}
        title={<>{a.title} {a.published ? <span className="badge t-ok" style={{ verticalAlign: 'middle', padding: '3px 12px' }}>Published</span> : <span className="badge t-muted" style={{ verticalAlign: 'middle', padding: '3px 12px' }}>Draft</span>}{a.archived && <span className="badge t-muted" style={{ verticalAlign: 'middle', marginLeft: 8, padding: '3px 12px' }}>Archived</span>}</>}
        actions={<>
          {a.kind === 'CODING' && <Link className="btn primary" to={`/assignments/${a.id}/plagiarism`}><Icon name="eye" />Similarity review</Link>}
          {a.kind === 'CODING' && <Link className="btn" to={`/assignments/${a.id}/code`}><Icon name="code" />Try in workspace</Link>}
          {!a.published ? <button className="btn" onClick={publish}><Icon name="check" size={14} />Publish</button> : <button className="btn quiet" onClick={unpublish}>Unpublish</button>}
          <details className="action-menu">
            <summary className="btn quiet"><Icon name="edit" size={14} />Manage ▾</summary>
            <div className="menu-pop">
              <button type="button" onClick={duplicate} title="Copy this assignment, its tests and attachments into a new draft"><Icon name="copyplus" size={15} />Duplicate</button>
              <button type="button" onClick={archive}><Icon name="archive" size={15} />{a.archived ? 'Restore' : 'Archive'}</button>
            </div>
          </details>
        </>} />
      <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
    </>
  if (a.kind === 'QUIZ') return <QuizInstructorView a={a} reload={reload} header={header} details={<DetailsPanel a={a} reload={reload} />} />
  return (
    <>
      {header}
      <Tabs label="Assignment sections" value={tab} onChange={setTab} tabs={[{ id: 'subs', label: 'Submissions', count: subs.data?.length }, { id: 'insights', label: 'Insights' }, { id: 'brief', label: 'Instructions' }, { id: 'tests', label: 'Tests', count: tests.length }, { id: 'files', label: 'Attachments', count: a.files.length }]} />

      {tab === 'insights' && <Insights id={a.id} />}

      {tab === 'brief' && <DetailsPanel a={a} reload={reload} />}

      {tab === 'tests' && (
        <div id="panel-tests" role="tabpanel">
          <div className="notice"><Icon name="lock" />Sample tests are shown to students with expected output. Hidden tests are never sent to students; they only see pass or fail per test.</div>
          {tests.map((t, i) => (
            <div key={i} className="card flat">
              <div className="grid g3"><Field id={`tn${i}`} label="Name"><input id={`tn${i}`} value={t.name} onChange={e => setTests(tests.map((x, j) => j === i ? { ...x, name: e.target.value } : x))} maxLength={80} /></Field>
                <Field id={`ts${i}`} label="Visibility"><select id={`ts${i}`} value={t.sample ? 'sample' : 'hidden'} onChange={e => setTests(tests.map((x, j) => j === i ? { ...x, sample: e.target.value === 'sample' } : x))}><option value="sample">Sample (visible)</option><option value="hidden">Hidden</option></select></Field></div>
              <div className="grid g2"><Field id={`ti${i}`} label="Standard input"><textarea id={`ti${i}`} value={t.input} onChange={e => setTests(tests.map((x, j) => j === i ? { ...x, input: e.target.value } : x))} /></Field>
                <Field id={`te${i}`} label="Expected output"><textarea id={`te${i}`} value={t.expectedOutput} onChange={e => setTests(tests.map((x, j) => j === i ? { ...x, expectedOutput: e.target.value } : x))} /></Field></div>
              <button className="quiet" onClick={() => setTests(tests.filter((_, j) => j !== i))}>Remove</button>
            </div>))}
          <div className="row"><button onClick={() => setTests([...tests, { id: null, name: `test ${tests.length + 1}`, sample: tests.length === 0, input: '', expectedOutput: '' }])}><Icon name="plus" />Add test case</button>
            <button className="primary" onClick={saveTests} disabled={tests.length === 0}>Save test cases</button></div>
          <p className="hint">Comparison ignores trailing whitespace on lines, CRLF vs LF and trailing blank lines; everything else must match exactly.</p>
        </div>)}

      {tab === 'files' && (
        <div className="card" id="panel-files" role="tabpanel"><h2>Attachments</h2>
          {a.files.length === 0 ? <Empty icon="file">No attachments.</Empty> : <div>{a.files.map(f => <div className="attempt" key={f.id}><button onClick={() => download(`/api/assignments/${a.id}/files/${f.id}`, f.name).catch(e => setErr(e.message))}><Icon name="download" size={16} />{f.name}</button><span className="badge t-muted">{f.kind === 'STARTER' ? 'Starter: students see it; excluded from similarity' : 'Reference: instructor only'}</span></div>)}</div>}
          <form onSubmit={upload} className="row" style={{ marginTop: 16 }}>
            <select aria-label="Attachment kind" value={kind} onChange={e => setKind(e.target.value)} style={{ width: 'auto' }}><option value="STARTER">Starter code</option><option value="REFERENCE">Reference solution (private)</option></select>
            <input name="afile" type="file" aria-label="Attachment file" style={{ width: 'auto' }} /><button type="submit">Attach</button></form>
        </div>)}

      {tab === 'subs' && (
        <div id="panel-subs" role="tabpanel">
          <div className="toolbar">
            <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Search by student name or email" placeholder="Search students" value={search} onChange={e => setSearch(e.target.value)} /></div>
            <label className="sr-only" htmlFor="vf">Verdict</label>
            <select id="vf" value={verdict} onChange={e => setVerdict(e.target.value)}><option value="">All results</option>{['AC', 'WA', 'CE', 'RE', 'TLE', 'MLE', 'IE'].map(v => <option key={v} value={v}>{VERDICT_LABEL[v]}</option>)}</select>
            <label className="sr-only" htmlFor="sortby">Sort</label>
            <select id="sortby" value={sort} onChange={e => setSort(e.target.value)}><option value="new">Newest first</option><option value="old">Oldest first</option><option value="name">Student A to Z</option><option value="high">Highest score</option><option value="low">Lowest score</option></select>
            <label className="row small" style={{ fontWeight: 500, margin: 0 }}><input type="checkbox" checked={latestOnly} onChange={e => setLatestOnly(e.target.checked)} /> Latest attempt only</label>
            <span className="grow" />
            <button onClick={exportCsv}><Icon name="download" size={16} />Export CSV</button>
            <button onClick={() => setRerun(rerun ? null : 'IE')}><Icon name="refresh" size={16} />Re-run…</button>
          </div>
          {rerun && (
            <div className="card flat rerun" role="group" aria-label="Re-run submissions">
              <div className="row space"><div><strong>Re-run each student's latest attempt</strong><p className="hint" style={{ margin: '2px 0 0' }}>Use this after fixing a test or a Docker hiccup. Old results are replaced; students are not notified and attempts already waiting are skipped.</p></div>
                <div className="row"><label className="sr-only" htmlFor="rr">Which results</label>
                  <select id="rr" value={rerun} onChange={e => setRerun(e.target.value)}><option value="IE">Judge problems only</option><option value="WA">Wrong answers only</option><option value="CE">Compile errors only</option><option value="RE">Runtime errors only</option><option value="TLE">Time limit exceeded only</option><option value="all">Everything</option></select>
                  <button className="primary" onClick={() => rerunMany(rerun)}>Queue re-run</button><button className="quiet" onClick={() => setRerun(null)}>Cancel</button></div></div>
            </div>)}
          <div className="card">
            <div className="row space" style={{ marginBottom: 8 }}><h2 style={{ margin: 0 }}>Submissions</h2><span className="small muted" aria-live="polite">{subs.data ? `${shown.length}${shown.length !== subs.data.length ? ` of ${subs.data.length}` : ''} shown` : ''}</span></div>
            {subs.loading ? <Loading /> : shown.length === 0 ? <Empty icon="upload">{term || verdict ? 'No submissions match these filters.' : 'No submissions yet.'}</Empty> : (
              <div className="tablewrap"><table><thead><tr><th>Student</th><th>Attempt</th><th>When</th><th>Result</th><th>Tests</th><th /></tr></thead>
                <tbody>{shown.map(s => <tr key={s.id}><td><strong>{s.studentName}</strong></td><td><Link to={`/submissions/${s.id}`}>#{s.attempt}</Link></td><td>{when(s.createdAt)}</td><td><VerdictBadge verdict={s.verdict} /></td><td>{s.total != null ? `${s.passed}/${s.total}` : s.status.toLowerCase()}</td><td><button className="quiet" onClick={() => rejudge(s.id)}>Re-run</button></td></tr>)}</tbody></table></div>)}
          </div>
        </div>)}
    </>
  )
}

function Insights({ id }: { id: number }) {
  const q = useFetch(() => api.get<Analytics>(`/api/assignments/${id}/analytics`), [id])
  if (q.loading) return <Loading />
  const d = q.data
  if (!d) return <ErrorBox message={q.error} />
  if (d.submitted === 0) return <Empty icon="chart">Nothing to analyse yet. Insights appear once students submit.</Empty>
  return (
    <div id="panel-insights" role="tabpanel">
      <div className="statrow" aria-label="Assignment analytics overview">
        <div>
          <b>{d.submitted} / {d.enrolled}</b>
          <span>Students submitted</span>
        </div>
        <div>
          <b>{d.averageAttempts}</b>
          <span>Average attempts</span>
        </div>
        {d.medianMillis != null && (
          <div>
            <b>{d.medianMillis} ms</b>
            <span>Median execution</span>
          </div>
        )}
        <div>
          <b>{d.tests.length}</b>
          <span>Evaluated test cases</span>
        </div>
      </div>
      <div className="card"><h2>Where everyone stands</h2><p className="hint">Each student's latest attempt.</p><VerdictBar verdicts={d.verdicts} pending={d.pending} /></div>
      <div className="grid g2">
        <div className="card"><h2>Score spread</h2><p className="hint">Students by share of tests passed.</p><Histogram buckets={d.scoreBuckets} /></div>
        <div className="card"><h2>Hardest tests first</h2><p className="hint">Share of students passing each test.</p>{d.tests.length ? <TestBars tests={d.tests} /> : <p className="muted">No per-test results yet.</p>}</div>
      </div>
    </div>
  )
}

const toLocalInput = (iso: string) => { const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }

function DetailsPanel({ a, reload }: { a: Assignment; reload: () => Promise<unknown> }) {
  const [f, setF] = useState({ title: a.title, description: a.description, deadline: toLocalInput(a.deadline), timeLimitMs: a.timeLimitMs, memoryLimitMb: a.memoryLimitMb })
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const dirty = f.title !== a.title || f.description !== a.description || f.deadline !== toLocalInput(a.deadline) || f.timeLimitMs !== a.timeLimitMs || f.memoryLimitMb !== a.memoryLimitMb

  async function patch(body: Record<string, unknown>, ok: string) {
    setBusy(true); setErr(null); setMsg(null)
    try { await api.patch(`/api/assignments/${a.id}`, body); await reload(); setMsg(ok) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }
  const save = (e: FormEvent) => { e.preventDefault(); void patch({ title: f.title.trim(), description: f.description, deadline: new Date(f.deadline).toISOString(), timeLimitMs: f.timeLimitMs, memoryLimitMb: f.memoryLimitMb }, 'Details saved.') }
  const extend = (days: number) => {
    const base = Math.max(Date.now(), new Date(a.deadline).getTime())
    const next = new Date(base + days * 86_400_000)
    setF(x => ({ ...x, deadline: toLocalInput(next.toISOString()) }))
    void patch({ deadline: next.toISOString() }, `Deadline extended to ${when(next.toISOString())}.`)
  }
  useEffect(() => { setF({ title: a.title, description: a.description, deadline: toLocalInput(a.deadline), timeLimitMs: a.timeLimitMs, memoryLimitMb: a.memoryLimitMb }) }, [a.title, a.description, a.deadline, a.timeLimitMs, a.memoryLimitMb])

  return (
    <div id="panel-brief" role="tabpanel">
      <div className="card"><div className="row space">{a.kind === 'QUIZ' ? <QuizFacts a={a} /> : <Facts a={a} />}
        <div className="row" role="group" aria-label="Extend the deadline"><span className="small muted"><Icon name="extend" size={15} /> Extend by</span>
          {[[1, '1 day'], [3, '3 days'], [7, '1 week']].map(([d, l]) => <button key={d} className="quiet" disabled={busy} onClick={() => extend(Number(d))}>{l}</button>)}</div></div>
        <p className="hint">Extending counts from the later of now and the current deadline, and reopens submissions if the deadline had passed.</p></div>
      <form className="card" onSubmit={save}><h2>Edit details</h2>
        <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
        <Field id="et" label="Title"><input id="et" value={f.title} onChange={e => setF({ ...f, title: e.target.value })} required maxLength={150} /></Field>
        <Field id="ed" label="Problem statement" hint="Changes are visible to students immediately, including for work already submitted."><textarea id="ed" value={f.description} onChange={e => setF({ ...f, description: e.target.value })} required maxLength={20000} style={{ minHeight: 180 }} /></Field>
        <div className="grid g3">
          <Field id="edl" label="Deadline"><input id="edl" type="datetime-local" value={f.deadline} onChange={e => setF({ ...f, deadline: e.target.value })} required /></Field>
          {a.kind === 'CODING' && <><Field id="etl" label="Time limit (ms)"><input id="etl" type="number" min={100} max={20000} value={f.timeLimitMs} onChange={e => setF({ ...f, timeLimitMs: Number(e.target.value) })} required /></Field>
          <Field id="eml" label="Memory limit (MB)"><input id="eml" type="number" min={16} max={1024} value={f.memoryLimitMb} onChange={e => setF({ ...f, memoryLimitMb: Number(e.target.value) })} required /></Field></>}</div>
        {a.kind === 'CODING' && <p className="hint">The language ({LANGUAGE_LABEL[a.language]}) is fixed once an assignment exists, because tests and submissions depend on it. Duplicate the assignment to start one in another language.</p>}
        <div className="row"><button className="primary" type="submit" disabled={busy || !dirty || !f.title.trim() || !f.deadline}>{busy ? 'Saving…' : 'Save changes'}</button>
          {dirty && <button type="button" className="quiet" onClick={() => setF({ title: a.title, description: a.description, deadline: toLocalInput(a.deadline), timeLimitMs: a.timeLimitMs, memoryLimitMb: a.memoryLimitMb })}>Discard</button>}</div>
      </form>
      {a.kind === 'CODING' && <AssessmentSettings a={a} reload={reload} />}
    </div>
  )
}
