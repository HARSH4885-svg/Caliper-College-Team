import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, tokenStore, type Assignment, type AttemptRow, type AttemptView, type Json, type ViewQuestion } from '../api'
import { useAuth } from '../auth'
import { Icon } from '../components/brand'
import { answered, QuizRunner } from '../components/QuizRunner'
import { ErrorBox, Loading, PageHead } from '../components/ui'
import { fmtMarks, STATUS_LABEL, when } from '../labels'

async function downloadAnswerFile(attemptId: number, qid: number, name: string) {
  const res = await fetch(`/api/attempts/${attemptId}/files/${qid}`, { headers: { Authorization: `Bearer ${tokenStore.get() ?? ''}` } })
  if (!res.ok) throw new Error('Download failed')
  const a = document.createElement('a'); a.href = URL.createObjectURL(await res.blob()); a.download = name; a.click(); URL.revokeObjectURL(a.href)
}

export function AttemptPage() {
  const { id } = useParams()
  const { me } = useAuth()
  const [view, setView] = useState<AttemptView | null>(null)
  const [asg, setAsg] = useState<Assignment | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const load = useCallback(async () => {
    try {
      const v = await api.get<AttemptView>(`/api/attempts/${id}`)
      setView(v)
      setAsg(await api.get<Assignment>(`/api/assignments/${v.assignmentId}`))
    } catch (e) { setErr((e as Error).message) }
  }, [id])
  useEffect(() => { setView(null); void load() }, [load])
  if (err && !view) return <ErrorBox message={err} />
  if (!view || !asg) return <Loading />
  if (me?.role === 'INSTRUCTOR') return <Marking view={view} asg={asg} reload={load} />
  return view.status === 'IN_PROGRESS' ? <Taking view={view} asg={asg} onDone={setView} /> : <Result view={view} asg={asg} />
}

function useCountdown(endsAt: string | null, serverNow: string) {
  const skew = useRef(new Date(serverNow).getTime() - Date.now())
  const [left, setLeft] = useState<number | null>(null)
  useEffect(() => {
    if (!endsAt) return
    const tick = () => setLeft(Math.max(0, new Date(endsAt).getTime() - (Date.now() + skew.current)))
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [endsAt])
  return left
}

const clock = (ms: number) => { const s = Math.ceil(ms / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), x = s % 60; return `${h ? `${h}:` : ''}${String(m).padStart(h ? 2 : 1, '0')}:${String(x).padStart(2, '0')}` }

function Taking({ view, asg, onDone }: { view: AttemptView; asg: Assignment; onDone: (v: AttemptView) => void }) {
  const [answers, setAnswers] = useState<Record<number, Json>>(() => Object.fromEntries(view.questions.map(q => [q.id, q.response ?? {}])))
  const [questions, setQuestions] = useState<ViewQuestion[]>(view.questions)
  const pending = useRef<Record<number, Json>>({})
  const [saveState, setSaveState] = useState<'saved' | 'saving' | 'dirty' | 'error'>('saved')
  const [savedAt, setSavedAt] = useState<Date | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [current, setCurrent] = useState<number | null>(view.questions[0]?.id ?? null)
  const left = useCountdown(view.endsAt, view.serverNow)
  const timer = useRef<number | null>(null)

  const flush = useCallback(async () => {
    const batch = pending.current
    if (Object.keys(batch).length === 0) return true
    pending.current = {}
    setSaveState('saving')
    try { await api.put(`/api/attempts/${view.id}/responses`, { answers: batch }); setSaveState(Object.keys(pending.current).length ? 'dirty' : 'saved'); setSavedAt(new Date()); return true }
    catch (e) { pending.current = { ...batch, ...pending.current }; setSaveState('error'); setErr((e as Error).message); return false }
  }, [view.id])

  const onAnswer = (qid: number, r: Json) => {
    setAnswers(a => ({ ...a, [qid]: r })); setCurrent(qid)
    pending.current[qid] = r; setSaveState('dirty')
    if (timer.current) clearTimeout(timer.current)
    timer.current = window.setTimeout(() => { void flush() }, 900)
  }
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => { if (Object.keys(pending.current).length) { e.preventDefault(); e.returnValue = '' } }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [])

  const submit = useCallback(async () => {
    setBusy(true); setErr(null)
    await flush()
    try { onDone(await api.post<AttemptView>(`/api/attempts/${view.id}/submit`)) }
    catch (e) { setErr((e as Error).message); try { onDone(await api.get<AttemptView>(`/api/attempts/${view.id}`)) } catch { /* keep the page */ } }
    finally { setBusy(false); setConfirm(false) }
  }, [flush, onDone, view.id])

  useEffect(() => { if (left === 0) void submit() }, [left, submit])

  async function upload(qid: number, f: File) {
    setErr(null)
    try { const fd = new FormData(); fd.append('file', f); const v = await api.upload<AttemptView>(`/api/attempts/${view.id}/files/${qid}`, fd); setQuestions(v.questions); setAnswers(a => ({ ...a, [qid]: v.questions.find(q => q.id === qid)?.response })) }
    catch (e) { setErr((e as Error).message) }
  }

  const done = questions.filter(q => answered(q, answers[q.id])).length
  const missingRequired = questions.filter(q => q.required && !answered(q, answers[q.id]))
  const low = left != null && left < 60_000

  return (
    <div className="taking">
      <PageHead back={{ to: `/assignments/${asg.id}`, label: 'Assignment' }} title={asg.title} sub={`Attempt ${view.attempt} of ${asg.maxAttempts} · ${fmtMarks(view.maxMarks)} marks${view.late ? ' · late' : ''}`} />
      <div className="takegrid">
        <div>
          <ErrorBox message={err} />
          <QuizRunner questions={questions} responses={answers} onAnswer={onAnswer} onUpload={upload} active={current} runCode={(qid, source, mode, input) => api.post(`/api/questions/${qid}/run?attempt=${view.id}`, { source, mode, input })} downloadFile={(q, n) => downloadAnswerFile(view.id, q, n).catch(e => setErr(e.message))} />
        </div>
        <aside className="takeside sticky" aria-label="Attempt status">
          {left != null && <div className={`timer${low ? ' low' : ''}`} role="timer" aria-live={low ? 'assertive' : 'off'}><Icon name="clock" /><b>{clock(left)}</b><span>left</span></div>}
          <div className="small muted" aria-live="polite">{done} of {questions.length} answered</div>
          <div className="meter" aria-hidden="true"><i style={{ width: `${(done / Math.max(1, questions.length)) * 100}%` }} /></div>
          <nav className="qnav" aria-label="Questions">{questions.map((q, i) => (
            <a key={q.id} href={`#q-${q.id}`} onClick={() => setCurrent(q.id)} className={`${answered(q, answers[q.id]) ? 'done' : ''}${current === q.id ? ' cur' : ''}`} aria-label={`Question ${i + 1}${answered(q, answers[q.id]) ? ', answered' : ''}`}>{i + 1}</a>))}</nav>
          <p className={`savestate ${saveState}`} role="status">{saveState === 'saving' ? 'Saving…' : saveState === 'dirty' ? 'Unsaved changes' : saveState === 'error' ? 'Not saved: check your connection' : savedAt ? `Saved ${savedAt.toLocaleTimeString()}` : 'Answers save as you type'}</p>
          <button className="primary" style={{ width: '100%' }} disabled={busy} onClick={() => setConfirm(true)}>Submit</button>
        </aside>
      </div>
      {confirm && (
        <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) setConfirm(false) }}>
          <div className="dialog" role="alertdialog" aria-modal="true" aria-labelledby="sub-t">
            <h2 id="sub-t">Submit attempt {view.attempt}?</h2>
            <p>You answered {done} of {questions.length} questions.{missingRequired.length > 0 && <> <b>{missingRequired.length} required question{missingRequired.length === 1 ? ' is' : 's are'} unanswered</b> ({missingRequired.map(q => questions.indexOf(q) + 1).join(', ')}).</>}</p>
            <p className="hint">After submitting you cannot change these answers{asg.maxAttempts > view.attempt ? `; you have ${asg.maxAttempts - view.attempt} more attempt${asg.maxAttempts - view.attempt === 1 ? '' : 's'}` : ''}.</p>
            <div className="row"><button className="primary" autoFocus disabled={busy} onClick={submit}>{busy ? 'Submitting…' : 'Submit answers'}</button><button onClick={() => setConfirm(false)}>Keep working</button></div>
          </div>
        </div>)}
    </div>
  )
}

function Result({ view, asg }: { view: AttemptView; asg: Assignment }) {
  const [err, setErr] = useState<string | null>(null)
  const pct = view.score != null ? Math.round((view.score / view.maxMarks) * 100) : null
  const passed = view.score != null && asg.passingMarks != null ? view.score >= asg.passingMarks : null
  return (
    <>
      <PageHead back={{ to: `/assignments/${asg.id}`, label: 'Assignment' }} title={asg.title} sub={`Attempt ${view.attempt} · submitted ${view.submittedAt ? when(view.submittedAt) : ''}${view.late ? ' (late)' : ''}`} />
      <div className="card result">
        <div className={`bigflag p-${view.visible ? (passed === false ? 'warn' : 'ok') : 'wait'}`} aria-hidden="true">{view.visible && pct != null ? `${pct}%` : '…'}</div>
        <div>
          {view.visible && view.score != null ? <>
            <h2 style={{ marginBottom: 6 }}>{fmtMarks(view.score)} of {fmtMarks(view.maxMarks)} marks</h2>
            {passed != null && <p className="muted" style={{ margin: 0 }}>{passed ? 'Passed' : 'Below the pass mark'} (pass mark {fmtMarks(asg.passingMarks)})</p>}
            {view.status === 'RETURNED' && <p className="notice" style={{ marginTop: 8 }}><Icon name="refresh" />Returned for revision. Read the comments and start a new attempt.</p>}
          </> : <>
            <h2 style={{ marginBottom: 6 }}>Submitted</h2>
            <p className="muted" style={{ margin: 0 }}>{view.status === 'SUBMITTED' ? 'Some answers are marked by your instructor. ' : ''}Your marks appear here once your instructor releases them{asg.releaseMode === 'AFTER_DUE' ? ', after the deadline' : ''}.</p>
          </>}
        </div>
      </div>
      {view.visible && view.feedback && <div className="card"><h2>Comments from your instructor</h2><p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{view.feedback}</p></div>}
      <ErrorBox message={err} />
      <div className="card"><h2>Your answers</h2>
        <QuizRunner questions={view.questions} responses={Object.fromEntries(view.questions.map(q => [q.id, q.response]))} readOnly review={view.visible}
          downloadFile={(q, n) => downloadAnswerFile(view.id, q, n).catch(e => setErr(e.message))} /></div>
    </>
  )
}

function Marking({ view, asg, reload }: { view: AttemptView; asg: Assignment; reload: () => Promise<void> }) {
  const navigate = useNavigate()
  const [marks, setMarks] = useState<Record<number, { score: string; feedback: string }>>({})
  const [feedback, setFeedback] = useState(view.feedback)
  const [note, setNote] = useState(view.privateNote ?? '')
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [queue, setQueue] = useState<AttemptRow[]>([])
  useEffect(() => {
    setMarks(Object.fromEntries(view.questions.map(q => [q.id, { score: q.manualScore != null ? String(q.manualScore) : '', feedback: q.feedback ?? '' }])))
    setFeedback(view.feedback); setNote(view.privateNote ?? '')
  }, [view])
  useEffect(() => { api.get<AttemptRow[]>(`/api/assignments/${asg.id}/attempts`).then(setQueue).catch(() => setQueue([])) }, [asg.id, view.id])

  const save = async (opts: { release?: boolean; returnForRevision?: boolean }) => {
    setErr(null); setMsg(null)
    const list = view.questions.map(q => ({ questionId: q.id, score: marks[q.id]?.score === '' ? null : Number(marks[q.id]?.score), feedback: marks[q.id]?.feedback ?? '' }))
    if (list.some(m => m.score != null && Number.isNaN(m.score))) { setErr('Marks must be numbers.'); return }
    try {
      await api.post(`/api/attempts/${view.id}/grade`, { marks: list, feedback, privateNote: note, release: opts.release, returnForRevision: !!opts.returnForRevision })
      await reload()
      setMsg(opts.returnForRevision ? 'Returned to the student for revision.' : opts.release ? 'Marks saved and released to the student.' : 'Marks saved.')
    } catch (e) { setErr((e as Error).message) }
  }
  const next = queue.find(a => a.id !== view.id && a.needsMarking > 0)
  const needs = view.questions.filter(q => q.autoScore == null && q.manualScore == null).length

  return (
    <>
      <PageHead back={{ to: `/assignments/${asg.id}?tab=attempts`, label: 'Attempts' }} title={<>{view.studentName} · attempt {view.attempt}</>}
        sub={`${asg.title} · ${STATUS_LABEL[view.status] ?? view.status}${view.submittedAt ? ` · submitted ${when(view.submittedAt)}` : ''}${view.late ? ' · late' : ''}`}
        actions={next ? <button onClick={() => navigate(`/attempts/${next.id}`)}>Next to mark: {next.studentName}<Icon name="right" size={16} /></button> : undefined} />
      <div className="markbar card">
        <div><span className="small muted">Score</span><b className="bigscore">{fmtMarks(view.score)} <span className="muted">/ {fmtMarks(view.maxMarks)}</span></b></div>
        <div><span className="small muted">Waiting for marks</span><b>{needs} question{needs === 1 ? '' : 's'}</b></div>
        <div><span className="small muted">Released to student</span><b>{view.released ? 'Yes' : 'No'}</b></div>
      </div>
      <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
      {view.status === 'IN_PROGRESS' ? <div className="notice"><Icon name="clock" />The student is still working on this attempt.</div> : null}
      <div className="card">
        <QuizRunner questions={view.questions} responses={Object.fromEntries(view.questions.map(q => [q.id, q.response]))} readOnly review
          downloadFile={(q, n) => downloadAnswerFile(view.id, q, n).catch(e => setErr(e.message))}
          marking={q => (
            <div className="markq">
              <label className="small" htmlFor={`m${q.id}`}>{q.autoScore != null ? `Automatic: ${fmtMarks(q.autoScore)}. Override` : 'Marks'}</label>
              <input id={`m${q.id}`} type="number" step={0.5} min={-q.marks} max={q.marks} value={marks[q.id]?.score ?? ''} placeholder={q.autoScore != null ? 'keep' : '0'}
                onChange={e => setMarks({ ...marks, [q.id]: { ...marks[q.id], score: e.target.value } })} style={{ width: 90 }} />
              <span className="small muted">/ {fmtMarks(q.marks)}</span>
              <input aria-label={`Comment on question ${view.questions.indexOf(q) + 1}`} placeholder="Comment for the student (optional)" value={marks[q.id]?.feedback ?? ''}
                onChange={e => setMarks({ ...marks, [q.id]: { ...marks[q.id], feedback: e.target.value } })} />
            </div>)} />
      </div>
      <div className="card">
        <h2>Overall</h2>
        <div className="grid g2">
          <div className="field"><label htmlFor="ofb">Comment for the student</label><textarea id="ofb" value={feedback} onChange={e => setFeedback(e.target.value)} maxLength={5000} />
            <div className="row" style={{ marginTop: 6 }}>{['Well done.', 'Good attempt; review the explanations.', 'Please see me in office hours.'].map(t => <button key={t} type="button" className="quiet" onClick={() => setFeedback(feedback ? `${feedback} ${t}` : t)}>+ {t}</button>)}</div></div>
          <div className="field"><label htmlFor="pnote">Private note (staff only)</label><textarea id="pnote" value={note} onChange={e => setNote(e.target.value)} maxLength={5000} /></div>
        </div>
        <div className="row">
          <button className="primary" onClick={() => save({})} disabled={view.status === 'IN_PROGRESS'}>Save marks</button>
          <button onClick={() => save({ release: true })} disabled={view.status === 'IN_PROGRESS'}>Save and release</button>
          {view.released && <button className="quiet" onClick={() => save({ release: false })}>Hide from student</button>}
          <button className="quiet" onClick={() => save({ returnForRevision: true, release: true })} disabled={view.status === 'IN_PROGRESS'}>Return for revision</button>
          <Link className="btn quiet" to={`/assignments/${asg.id}?tab=attempts`}>Back to all attempts</Link>
        </div>
        <p className="hint">An empty mark box keeps the automatic mark (or leaves a hand-marked question waiting). Releasing shows the score, your comments and, if enabled, the answer key.</p>
      </div>
    </>
  )
}
