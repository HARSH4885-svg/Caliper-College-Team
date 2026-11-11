import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, type CodingGrade, type Evaluation, type Submission } from '../api'
import { useAuth } from '../auth'
import { Icon } from '../components/brand'
import { Empty, ErrorBox, Loading, Notice, PageHead, Synthetic, VerdictBadge } from '../components/ui'
import { fmtMarks, VERDICT_LABEL, VERDICT_TONE, when } from '../labels'
import { useFetch } from '../useFetch'

const inFlight = (e: Evaluation) => e.status === 'QUEUED' || e.status === 'RUNNING'

export function SubmissionPage() {
  const { id } = useParams()
  const sub = useFetch(() => api.get<Submission>(`/api/submissions/${id}`), [id])
  const ev = useFetch(() => api.get<Evaluation>(`/api/submissions/${id}/evaluation`), [id], inFlight)
  const [open, setOpen] = useState<{ path: string; text: string } | null>(null)
  const [err, setErr] = useState<string | null>(null)

  if (sub.loading || ev.loading) return <Loading />
  if (!sub.data || !ev.data) return <ErrorBox message={sub.error ?? ev.error} />
  const s = sub.data, e = ev.data
  const tone = e.verdict ? VERDICT_TONE[e.verdict] ?? 'muted' : 'wait'

  return (
    <>
      <PageHead back={{ to: `/assignments/${s.assignmentId}`, label: 'Assignment' }} title={<>Attempt #{s.attempt}{s.studentName ? ` — ${s.studentName}` : ''} {s.synthetic && <Synthetic />}</>} sub={`Submitted ${when(s.createdAt)}`} />
      <ErrorBox message={err} />
      <div className="card result">
        <div className={`bigflag p-${tone}`} aria-hidden="true">{e.verdict ?? '…'}</div>
        <div>
          <h2 style={{ marginBottom: 6 }}>{e.verdict ? VERDICT_LABEL[e.verdict] : 'Waiting for the judge'}</h2>
          {e.total != null && <p style={{ margin: 0, fontSize: '1.05rem' }}><strong>{e.passed}</strong> of {e.total} tests passed</p>}
          {inFlight(e) && <p className="muted" role="status" style={{ margin: 0 }}>Status: {e.status.toLowerCase()}. This page updates by itself.</p>}
          {e.verdict && e.verdict !== 'IE' && <p className="muted small" style={{ margin: '8px 0 0' }}>Grading is separate from any similarity review.</p>}
          {e.verdict === 'IE' && <Notice>The judge could not evaluate this submission ({e.message}). This is not a verdict on your code. Your instructor can re-run it.</Notice>}
        </div>
      </div>
      <GradePanel id={s.id} pending={inFlight(e)} />
      {e.compileLog && e.verdict === 'CE' && <div className="card"><h2>Compiler output</h2><pre className="log">{e.compileLog}</pre></div>}
      <div className="card"><h2>Tests</h2>
        {e.tests.length === 0 ? <Empty icon="clock">{inFlight(e) ? 'Results will appear here.' : 'No per-test results (the build did not complete).'}</Empty> : (
          <ul className="ladder">{e.tests.map((t, i) => (
            <li key={i}><span>{t.sample ? t.name : <><Icon name="lock" size={14} /> {t.name} <span className="muted small">(hidden)</span></>}</span><span className="small muted">{t.millis} ms</span><VerdictBadge verdict={t.verdict} />
              {(t.detail || t.stdoutExcerpt || t.stderrExcerpt) && <details><summary>Details</summary>{t.detail && <p className="small">{t.detail}</p>}{t.stdoutExcerpt && <><div className="small muted">Program output</div><pre className="log">{t.stdoutExcerpt}</pre></>}{t.stderrExcerpt && <><div className="small muted">Error output</div><pre className="log">{t.stderrExcerpt}</pre></>}</details>}</li>))}</ul>)}
      </div>
      <div className="card"><h2>Files</h2>
        <div className="row">{s.files.map(f => <button key={f.id} onClick={() => api.text(`/api/submissions/${s.id}/files/${f.id}`).then(text => setOpen({ path: f.path, text })).catch(x => setErr(x.message))}><Icon name="file" size={16} />{f.path}<span className="muted small">{f.sizeBytes} B</span></button>)}</div>
        {open && <div style={{ marginTop: 16 }}><h3>{open.path}</h3><pre className="log" style={{ maxHeight: 420 }}>{open.text}</pre></div>}
      </div>
    </>
  )
}

function GradePanel({ id, pending }: { id: number; pending: boolean }) {
  const { me } = useAuth()
  const inst = me?.role === 'INSTRUCTOR'
  const g = useFetch(() => api.get<CodingGrade>(`/api/submissions/${id}/grade`), [id, pending])
  const [score, setScore] = useState(''), [feedback, setFeedback] = useState(''), [note, setNote] = useState('')
  const [err, setErr] = useState<string | null>(null), [msg, setMsg] = useState<string | null>(null)
  const [loaded, setLoaded] = useState<number | null>(null)
  if (g.data && loaded !== id) { setLoaded(id); setScore(g.data.overrideScore != null ? String(g.data.overrideScore) : ''); setFeedback(g.data.feedback); setNote(g.data.privateNote ?? '') }
  if (!g.data) return null
  const d = g.data
  async function save(clear = false) {
    setErr(null); setMsg(null)
    try {
      const body: Record<string, unknown> = { feedback, privateNote: note }
      if (clear) body.clearScore = true; else if (score !== '') body.score = Number(score)
      await api.post(`/api/submissions/${id}/grade`, body); await g.reload(true); if (clear) setScore(''); setMsg('Saved.')
    } catch (e) { setErr((e as Error).message) }
  }
  if (!inst) return (d.score == null && !d.feedback) ? null : (
    <div className="card"><h2>Marks</h2>
      {d.score != null && <p style={{ fontSize: '1.1rem' }}><b>{fmtMarks(d.score)}</b> of {fmtMarks(d.maxMarks)} marks{d.late && <span className="muted small"> (late{d.overrideScore == null ? ', penalty applied' : ''})</span>}</p>}
      {d.feedback && <p className="qfeedback" style={{ whiteSpace: 'pre-wrap' }}><Icon name="edit" size={15} />{d.feedback}</p>}</div>)
  return (
    <div className="card"><h2>Marks and feedback</h2>
      <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
      <p className="small muted">Automatic score from tests: <b>{d.autoScore == null ? 'waiting for the judge' : `${fmtMarks(d.autoScore)} / ${fmtMarks(d.maxMarks)}`}</b>{d.late && ' (late; any penalty is applied)'}.</p>
      <div className="grid g3">
        <div className="field"><label htmlFor="ov">Override score</label><input id="ov" type="number" min={0} max={d.maxMarks} step={0.5} value={score} placeholder="use automatic" onChange={e => setScore(e.target.value)} /></div>
      </div>
      <div className="grid g2">
        <div className="field"><label htmlFor="fb">Feedback for the student</label><textarea id="fb" value={feedback} onChange={e => setFeedback(e.target.value)} maxLength={5000} /></div>
        <div className="field"><label htmlFor="pn">Private note (staff only)</label><textarea id="pn" value={note} onChange={e => setNote(e.target.value)} maxLength={5000} /></div>
      </div>
      <div className="row"><button className="primary" onClick={() => save()}>Save</button>{d.overrideScore != null && <button className="quiet" onClick={() => save(true)}>Use automatic score</button>}</div>
    </div>)
}
