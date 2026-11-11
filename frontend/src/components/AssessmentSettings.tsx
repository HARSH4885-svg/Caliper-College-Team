import { useEffect, useState } from 'react'
import { api, type Assignment } from '../api'
import { Icon } from './brand'
import { ErrorBox, Field } from './ui'

const toLocal = (iso: string | null) => { if (!iso) return ''; const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }
const fromLocal = (v: string) => (v ? new Date(v).toISOString() : null)

/** Assessment rules: marks, attempts, schedule, late work, timing, shuffling and result release. Shared by quizzes and coding work. */
export function AssessmentSettings({ a, reload }: { a: Assignment; reload: () => Promise<unknown> }) {
  const init = () => ({
    category: a.category, difficulty: a.difficulty, maxMarks: a.maxMarks, passingMarks: a.passingMarks ?? '', maxAttempts: a.maxAttempts, availableFrom: toLocal(a.availableFrom),
    latePolicy: a.latePolicy, latePenaltyPct: a.latePenaltyPct, lateUntil: toLocal(a.lateUntil), quizMinutes: a.quizMinutes ?? '', shuffleQuestions: a.shuffleQuestions,
    shuffleOptions: a.shuffleOptions, releaseMode: a.releaseMode, showAnswers: a.showAnswers,
  })
  const [f, setF] = useState(init)
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => setF(init()), [a]) // eslint-disable-line react-hooks/exhaustive-deps
  const quiz = a.kind === 'QUIZ'
  const dirty = JSON.stringify(f) !== JSON.stringify(init())

  async function save(e: React.FormEvent) {
    e.preventDefault(); setBusy(true); setErr(null); setMsg(null)
    const options: Record<string, unknown> = {
      category: f.category, difficulty: f.difficulty, maxAttempts: Number(f.maxAttempts), latePolicy: f.latePolicy, latePenaltyPct: Number(f.latePenaltyPct),
      shuffleQuestions: f.shuffleQuestions, shuffleOptions: f.shuffleOptions, releaseMode: f.releaseMode, showAnswers: f.showAnswers,
    }
    if (!quiz) options.maxMarks = Number(f.maxMarks)
    if (f.passingMarks !== '') options.passingMarks = Number(f.passingMarks)
    if (f.availableFrom) options.availableFrom = fromLocal(f.availableFrom); else options.clearAvailableFrom = true
    if (f.lateUntil && f.latePolicy !== 'BLOCK') options.lateUntil = fromLocal(f.lateUntil); else options.clearLateUntil = true
    if (quiz) { if (f.quizMinutes !== '') options.quizMinutes = Number(f.quizMinutes); else options.clearQuizMinutes = true }
    try { await api.patch(`/api/assignments/${a.id}`, { options }); await reload(); setMsg('Settings saved.') } catch (x) { setErr((x as Error).message) } finally { setBusy(false) }
  }

  return (
    <form className="card" onSubmit={save}>
      <h2>Assessment settings</h2>
      <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
      <div className="grid g3">
        <Field id="s-cat" label="Category"><select id="s-cat" value={f.category} onChange={e => setF({ ...f, category: e.target.value })}>
          {['ASSIGNMENT', 'QUIZ', 'LAB', 'EXAM', 'PRACTICE'].map(c => <option key={c} value={c}>{c[0] + c.slice(1).toLowerCase()}</option>)}</select></Field>
        <Field id="s-dif" label="Difficulty"><select id="s-dif" value={f.difficulty} onChange={e => setF({ ...f, difficulty: e.target.value })}><option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select></Field>
        <Field id="s-max" label="Maximum marks" hint={quiz ? 'The sum of the question marks.' : 'Scaled by the share of tests passed.'}><input id="s-max" type="number" min={0.5} step={0.5} value={f.maxMarks} disabled={quiz} onChange={e => setF({ ...f, maxMarks: Number(e.target.value) })} /></Field>
        <Field id="s-pass" label="Pass mark" hint="Optional."><input id="s-pass" type="number" min={0} step={0.5} value={f.passingMarks} onChange={e => setF({ ...f, passingMarks: e.target.value === '' ? '' : Number(e.target.value) })} /></Field>
        <Field id="s-att" label="Attempts allowed"><input id="s-att" type="number" min={1} max={50} value={f.maxAttempts} onChange={e => setF({ ...f, maxAttempts: Number(e.target.value) })} /></Field>
        <Field id="s-from" label="Visible to students from" hint="Empty: as soon as it is published."><input id="s-from" type="datetime-local" value={f.availableFrom} onChange={e => setF({ ...f, availableFrom: e.target.value })} /></Field>
      </div>
      <h3>Late work</h3>
      <div className="grid g3">
        <Field id="s-late" label="After the deadline"><select id="s-late" value={f.latePolicy} onChange={e => setF({ ...f, latePolicy: e.target.value as Assignment['latePolicy'] })}>
          <option value="BLOCK">Do not accept</option><option value="ALLOW">Accept, marked late</option><option value="PENALTY">Accept with a penalty</option></select></Field>
        {f.latePolicy === 'PENALTY' && <Field id="s-pen" label="Penalty (%)"><input id="s-pen" type="number" min={0} max={100} value={f.latePenaltyPct} onChange={e => setF({ ...f, latePenaltyPct: Number(e.target.value) })} /></Field>}
        {f.latePolicy !== 'BLOCK' && <Field id="s-until" label="Accept late work until" hint="Empty: no cut-off."><input id="s-until" type="datetime-local" value={f.lateUntil} onChange={e => setF({ ...f, lateUntil: e.target.value })} /></Field>}
      </div>
      {quiz && <>
        <h3>Taking the quiz</h3>
        <div className="grid g3">
          <Field id="s-min" label="Time limit (minutes)" hint="Empty: untimed. The attempt submits itself when time runs out."><input id="s-min" type="number" min={1} max={600} value={f.quizMinutes} onChange={e => setF({ ...f, quizMinutes: e.target.value === '' ? '' : Number(e.target.value) })} /></Field>
          <Field id="s-rel" label="Release results"><select id="s-rel" value={f.releaseMode} onChange={e => setF({ ...f, releaseMode: e.target.value as Assignment['releaseMode'] })}>
            <option value="IMMEDIATE">As soon as fully marked</option><option value="AFTER_DUE">After the deadline</option><option value="MANUAL">When I release them</option></select></Field>
        </div>
        <div className="row">
          <label className="row small check"><input type="checkbox" checked={f.shuffleQuestions} onChange={e => setF({ ...f, shuffleQuestions: e.target.checked })} /> Shuffle question order per student</label>
          <label className="row small check"><input type="checkbox" checked={f.shuffleOptions} onChange={e => setF({ ...f, shuffleOptions: e.target.checked })} /> Shuffle answer options</label>
          <label className="row small check"><input type="checkbox" checked={f.showAnswers} onChange={e => setF({ ...f, showAnswers: e.target.checked })} /> Show the answer key and explanations with released results</label>
        </div>
      </>}
      <div className="row" style={{ marginTop: 16 }}><button className="primary" type="submit" disabled={busy || !dirty}>{busy ? 'Saving…' : 'Save settings'}</button>
        {dirty && <button type="button" className="quiet" onClick={() => setF(init())}>Discard</button>}</div>
    </form>
  )
}
