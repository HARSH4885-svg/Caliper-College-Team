import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type PairDetail, type PairList } from '../api'
import { Avatar, Icon } from '../components/brand'
import { SimilarityNetwork } from '../components/charts'
import { CodeCompare } from '../components/CodeCompare'
import { DecisionBadge, Empty, ErrorBox, Loading, Notice, PageHead, StateBadge } from '../components/ui'
import { DECISION_LABEL, pct, STATE_LABEL, when } from '../labels'
import { useFetch } from '../useFetch'

const DECISION_HELP: Record<string, string> = {
  CONFIRMED_CONCERN: 'You looked at the evidence and want to follow up.',
  DISMISSED: 'The overlap has an ordinary explanation.',
  INCONCLUSIVE: 'Not enough to say either way yet.',
}

export function PlagiarismListPage() {
  const { id } = useParams()
  const [state, setState] = useState('')
  const list = useFetch(() => api.get<PairList>(`/api/assignments/${id}/plagiarism-pairs${state ? `?state=${state}` : ''}`), [id, state])
  const [busy, setBusy] = useState(false)
  const [view, setView] = useState<'list' | 'network'>('list')
  const [err, setErr] = useState<string | null>(null)

  async function run() {
    setBusy(true); setErr(null)
    try { await api.post(`/api/assignments/${id}/plagiarism/run`); await list.reload(true) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }
  const d = list.data
  const insufficient = d?.run?.notes.filter(n => n.insufficientEvidence) ?? []

  return (
    <>
      <PageHead title="Similarity review" back={{ to: `/assignments/${id}`, label: 'Assignment' }}
        sub="A short list of submission pairs that share more than chance would explain, with the evidence to judge them yourself."
        actions={<button className="primary" onClick={run} disabled={busy}><Icon name="refresh" />{busy ? 'Comparing…' : d?.run ? 'Run comparison again' : 'Run comparison'}</button>} />
      <Notice>{d?.disclaimer ?? 'Similarity is an investigation signal, not a finding.'} Only submissions to this assignment are compared, and only each student's latest attempt.</Notice>
      <ErrorBox message={err ?? list.error} />
      <div className="row space" style={{ marginBottom: 16 }}>
        <span className="small muted">
          {d?.run ? (
            <>
              Last run {when(d.run.at)} / {d.run.submissions} submissions / {d.run.candidatePairs} candidate pairs / {d.run.elapsedMs} ms
            </>
          ) : 'No comparison has been run for this assignment yet.'}
        </span>
        <div className="row"><label htmlFor="st" style={{ margin: 0 }}>Show</label>
          <select id="st" value={state} onChange={e => setState(e.target.value)} style={{ width: 'auto' }}><option value="">All states</option>{Object.entries(STATE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
      </div>
      {d && d.pairs.length > 0 && <div className="segmented" role="tablist" aria-label="View" style={{ maxWidth: 280, marginBottom: 16 }}>
        <button type="button" role="tab" aria-selected={view === 'list'} className={view === 'list' ? 'on' : ''} onClick={() => setView('list')}><Icon name="task" size={15} />List</button>
        <button type="button" role="tab" aria-selected={view === 'network'} className={view === 'network' ? 'on' : ''} onClick={() => setView('network')}><Icon name="net" size={15} />Network</button></div>}
      {view === 'network' && d && d.pairs.length > 0 ? <div className="card"><SimilarityNetwork pairs={d.pairs} /><p className="hint">Each dot is a student; a line is a candidate pair, thicker for more distinctive overlap. Select a line to open the evidence. Position only reflects how pairs connect; it is not a ranking.</p></div>
      : list.loading ? <Loading /> : !d || d.pairs.length === 0
        ? <Empty icon="eye">{d?.run ? 'No pair met the candidate threshold. That does not prove independence, and it is not a clearance.' : 'Run a comparison to see candidate pairs.'}</Empty>
        : <div className="desk">{d.pairs.map(p => (
          <Link key={p.id} to={`/pairs/${p.id}`} className="pairrow" aria-label={`Open ${p.a.studentName} and ${p.b.studentName}`}>
            <div>
              <div className="people2"><Avatar name={p.a.studentName} /><span>{p.a.studentName}</span><Icon name="swap" size={16} className="vs" /><Avatar name={p.b.studentName} /><span>{p.b.studentName}</span></div>
              <div className="row" style={{ marginTop: 8 }}><StateBadge state={p.state} />{p.latestReview ? <DecisionBadge decision={p.latestReview.decision} /> : <span className="small muted">Not reviewed</span>}<span className="small muted">{p.distinctiveTokens} distinctive of {p.matchedTokens} matched tokens</span>{p.astStructuralScore ? <span className="small" style={{ background: 'var(--bg-subtle, #f4f4f5)', border: '1px solid var(--border, #e4e4e7)', borderRadius: 4, padding: '1px 6px', fontWeight: 600 }}>AST: {pct(p.astStructuralScore)}</span> : null}</div>
            </div>
            <div className="meterwrap"><span>Evidence score</span><b>{pct(p.score)}</b><div className="meter" aria-hidden="true"><i style={{ width: `${Math.round(p.score * 100)}%` }} /></div></div>
            <span className="go">Review<Icon name="right" size={16} /></span>
          </Link>))}</div>}
      {insufficient.length > 0 && <div className="card" style={{ marginTop: 24 }}><h2>Not enough code to judge</h2><p className="hint">These submissions were too small for a meaningful comparison. This is not a clearance.</p>
        <ul>{insufficient.map(n => <li key={n.submissionId}>Submission {n.submissionId}: {n.reasons.join(' ')}</li>)}</ul></div>}
    </>
  )
}

export function PairPage() {
  const { id } = useParams()
  const detail = useFetch(() => api.get<PairDetail>(`/api/plagiarism-pairs/${id}`), [id])
  const [decision, setDecision] = useState('INCONCLUSIVE')
  const [note, setNote] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function review() {
    setErr(null); setSaved(false)
    try { await api.post(`/api/plagiarism-pairs/${id}/review`, { decision, note: note || null }); setNote(''); setSaved(true); await detail.reload(true) } catch (e) { setErr((e as Error).message) }
  }
  if (detail.loading) return <Loading />
  if (!detail.data) return <ErrorBox message={detail.error} />
  const d = detail.data, s = d.summary
  return (
    <>
      <PageHead back={{ to: '/', label: 'Home' }} title="Pair review"
        actions={
          <div className="row" style={{ gap: 8 }}>
            <button className="primary" onClick={() => api.download(`/api/plagiarism-pairs/${id}/report.pdf`, `caliper-audit-report-pair-${id}.pdf`).catch(e => setErr((e as Error).message))}>
              <Icon name="download" />PDF Dossier
            </button>
            <button onClick={() => api.download(`/api/plagiarism-pairs/${id}/report.html`, `similarity-pair-${id}.html`).catch(e => setErr((e as Error).message))}>
              <Icon name="download" />HTML Report
            </button>
          </div>
        } />
      <Notice>{d.disclaimer}</Notice>
      <ErrorBox message={err} />
      <div className="card">
        <div className="pairhead">
          <div className="party"><Avatar name={s.a.studentName} size={44} /><strong>{s.a.studentName}</strong></div>
          <Icon name="swap" size={22} className="swapmark" />
          <div className="party"><Avatar name={s.b.studentName} size={44} /><strong>{s.b.studentName}</strong></div>
          <StateBadge state={s.state} />
        </div>
        <div className="meterwrap" style={{ maxWidth: 420 }}><span>Evidence score, from distinctive matched tokens (starter code and very common idioms are excluded)</span><b>{pct(s.score)}</b><div className="meter" aria-hidden="true"><i style={{ width: `${Math.round(s.score * 100)}%` }} /></div></div>
        <div className="facts4">
          <div><b>{pct(s.astStructuralScore ?? s.score)}</b><span>AST structural syntax score</span></div>
          <div><b>{s.distinctiveTokens}</b><span>distinctive matched tokens (of {s.matchedTokens})</span></div>
          <div><b>{s.longestTile}</b><span>longest matching run, in tokens</span></div>
          <div><b>{pct(d.coverageA)} / {pct(d.coverageB)}</b><span>coverage of A / B</span></div>
          <div><b>{pct(s.fingerprintDice)}</b><span>fingerprint overlap (Dice)</span></div>
        </div>
        <h3>Why this pair was surfaced</h3>
        <ul className="why">{d.explanation.map((x, i) => <li key={i}>{x}</li>)}</ul>
      </div>
      <div className="card"><h2>Side by side</h2><CodeCompare detail={d} /></div>
      <div className="card"><h2>Your decision</h2>
        {saved && <div className="notice" role="status"><Icon name="check" />Decision recorded.</div>}
        <p className="hint" style={{ marginTop: 0, marginBottom: 16 }}>Recording a decision does not notify the students and never changes grades. You can record a new one at any time; the history is kept.</p>
        <div className="verdictpick" role="radiogroup" aria-label="Decision">
          {Object.entries(DECISION_LABEL).map(([k, v]) => <label key={k}><input type="radio" name="dec" value={k} checked={decision === k} onChange={() => setDecision(k)} /><span>{v}<small>{DECISION_HELP[k]}</small></span></label>)}
        </div>
        <div className="field"><label htmlFor="note">Note (optional)</label><textarea id="note" value={note} onChange={e => setNote(e.target.value)} maxLength={2000} style={{ fontFamily: 'inherit', fontSize: '1rem' }} /></div>
        <button className="primary" onClick={review}>Record decision</button>
        {d.reviews.length > 0 && <><h3 style={{ marginTop: 24 }}>History</h3><ul className="history">{d.reviews.map((r, i) => <li key={i}><strong>{DECISION_LABEL[r.decision]}</strong> by {r.reviewer}, {when(r.at)}{r.note ? ` — ${r.note}` : ''}</li>)}</ul></>}
      </div>
    </>
  )
}
