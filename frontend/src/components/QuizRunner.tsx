import { useState } from 'react'
import type { Json, RunResponse, ViewQuestion } from '../api'
import { RunResults } from './RunResults'
import { CodeMirrorEditor } from './LazyCodeMirror'
import { VerdictBadge } from './ui'
import { fmtMarks, QTYPE_LABEL } from '../labels'
import { Icon } from './brand'

const words = (s: string) => (s.trim() ? s.trim().split(/\s+/).length : 0)

export function answered(q: ViewQuestion, r: Json): boolean {
  if (!r) return false
  switch (q.type) {
    case 'MCQ': return r.choice != null
    case 'MULTI': return (r.choices ?? []).length > 0
    case 'TRUE_FALSE': return typeof r.value === 'boolean'
    case 'FILL_BLANK': return (r.blanks ?? []).some((b: string) => b?.trim())
    case 'FILE': return !!r.fileName
    case 'CODE': return !!r.code?.trim() && r.code !== q.settings?.starter
    default: return !!r.text?.trim()
  }
}

function Score({ q }: { q: ViewQuestion }) {
  const got = q.manualScore ?? q.autoScore
  if (got == null) return <span className="qscore pending">Waiting for marks · {fmtMarks(q.marks)}</span>
  const tone = got >= q.marks ? 'full' : got > 0 ? 'part' : 'none'
  return <span className={`qscore ${tone}`}>{fmtMarks(got)} / {fmtMarks(q.marks)}</span>
}

/**
 * One renderer for taking, previewing and reviewing an assessment. In review mode it also shows marks, the key (when released)
 * and the instructor's comment. Options carry their original index so shuffled order never changes what is stored.
 */
export function QuizRunner({ questions, responses, onAnswer, readOnly, review, onUpload, downloadFile, active, marking, runCode }: {
  questions: ViewQuestion[]; responses: Record<number, Json>; onAnswer?: (id: number, r: Json) => void; readOnly?: boolean; review?: boolean
  onUpload?: (id: number, f: File) => void; downloadFile?: (id: number, name: string) => void; active?: number | null
  marking?: (q: ViewQuestion) => React.ReactNode; runCode?: (qid: number, source: string, mode: 'samples' | 'custom', input?: string) => Promise<RunResponse>
}) {
  return (
    <ol className="qlist">
      {questions.map((q, i) => {
        const r = responses[q.id] ?? {}
        const set = (v: Json) => onAnswer?.(q.id, v)
        const key = q.answer
        const isRight = (idx: number) => (key?.correct ?? []).includes(idx)
        return (
          <li key={q.id} id={`q-${q.id}`} className={`qcard${active === q.id ? ' active' : ''}`}>
            <header className="qcard-head">
              <span className="qnum">{i + 1}</span>
              <span className="small muted">{QTYPE_LABEL[q.type]}{!q.required && ' · optional'}</span>
              <span className="grow" />
              {review ? <Score q={q} /> : <span className="small muted">{fmtMarks(q.marks)} mark{q.marks === 1 ? '' : 's'}</span>}
            </header>
            {q.type !== 'FILL_BLANK' && <p className="qprompt">{q.prompt}</p>}
            {q.type === 'OUTPUT' && <pre className="log codeblock" aria-label="Code"><code>{q.settings?.code}</code></pre>}

            {(q.type === 'MCQ' || q.type === 'MULTI') && (
              <div className="choices" role={q.type === 'MCQ' ? 'radiogroup' : 'group'} aria-label={`Question ${i + 1} options`}>
                {q.options.map(o => {
                  const chosen = q.type === 'MCQ' ? r.choice === o.index : (r.choices ?? []).includes(o.index)
                  const mark = review && key ? (isRight(o.index) ? ' right' : chosen ? ' wrong' : '') : ''
                  return (
                    <label key={o.index} className={`choice${chosen ? ' on' : ''}${mark}`}>
                      <input type={q.type === 'MCQ' ? 'radio' : 'checkbox'} name={`q${q.id}`} disabled={readOnly} checked={chosen}
                        onChange={() => set(q.type === 'MCQ' ? { choice: o.index } : { choices: chosen ? (r.choices ?? []).filter((x: number) => x !== o.index) : [...(r.choices ?? []), o.index] })} />
                      <span>{o.text}</span>
                      {review && key && isRight(o.index) && <Icon name="check" size={16} className="choice-mark" />}
                    </label>)
                })}
              </div>)}

            {q.type === 'TRUE_FALSE' && (
              <div className="choices tf" role="radiogroup" aria-label={`Question ${i + 1}`}>
                {[true, false].map(v => {
                  const chosen = r.value === v
                  const mark = review && key ? (key.value === v ? ' right' : chosen ? ' wrong' : '') : ''
                  return <label key={String(v)} className={`choice${chosen ? ' on' : ''}${mark}`}><input type="radio" name={`q${q.id}`} disabled={readOnly} checked={chosen} onChange={() => set({ value: v })} /><span>{v ? 'True' : 'False'}</span></label>
                })}
              </div>)}

            {q.type === 'SHORT' && <input aria-label={`Answer to question ${i + 1}`} value={r.text ?? ''} readOnly={readOnly} onChange={e => set({ text: e.target.value })} maxLength={2000} placeholder="Your answer" />}

            {q.type === 'LONG' && <>
              <textarea aria-label={`Answer to question ${i + 1}`} value={r.text ?? ''} readOnly={readOnly} onChange={e => set({ text: e.target.value })} style={{ minHeight: 160 }} maxLength={20000} placeholder="Write your answer" />
              <div className="hint">{words(r.text ?? '')} words</div></>}

            {q.type === 'OUTPUT' && <textarea className="mono" aria-label={`Predicted output for question ${i + 1}`} value={r.text ?? ''} readOnly={readOnly} onChange={e => set({ text: e.target.value })} placeholder="Exact output" />}

            {q.type === 'FILL_BLANK' && (
              <p className="qprompt fill">{q.prompt.split('___').map((part, j, all) => (
                <span key={j}>{part}{j < all.length - 1 && <input className="blank" aria-label={`Blank ${j + 1} of question ${i + 1}`} readOnly={readOnly}
                  value={r.blanks?.[j] ?? ''} onChange={e => { const b = [...(r.blanks ?? Array(all.length - 1).fill(''))]; b[j] = e.target.value; set({ blanks: b }) }} />}</span>))}</p>)}

            {q.type === 'CODE' && <CodeQuestion q={q} code={r.code ?? q.settings?.starter ?? ''} onCode={c => set({ code: c })} readOnly={!!readOnly} review={!!review} runCode={runCode} />}

            {q.type === 'FILE' && (
              <div className="row">
                {r.fileName ? <button type="button" className="quiet" onClick={() => downloadFile?.(q.id, r.fileName)}><Icon name="file" size={16} />{r.fileName}</button> : <span className="muted small">No file yet.</span>}
                {!readOnly && <label className="btn quiet filepick"><Icon name="upload" size={15} />{r.fileName ? 'Replace file' : 'Choose file'}
                  <input type="file" className="sr-only" onChange={e => { const f = e.target.files?.[0]; if (f) onUpload?.(q.id, f); e.target.value = '' }} /></label>}
              </div>)}

            {review && key && (q.type === 'SHORT' || q.type === 'OUTPUT') && key.accepted?.length > 0 && <p className="keyline"><b>Accepted:</b> <code>{key.accepted.join('  |  ')}</code></p>}
            {review && key && q.type === 'FILL_BLANK' && <p className="keyline"><b>Accepted:</b> {(key.blanks ?? []).map((b: string[], j: number) => <code key={j}>{j + 1}: {b.join(' / ')}</code>)}</p>}
            {review && q.feedback && <p className="qfeedback"><Icon name="edit" size={15} />{q.feedback}</p>}
            {review && q.explanation && <details className="qexplain"><summary>Explanation</summary><p>{q.explanation}</p></details>}
            {marking?.(q)}
          </li>)
      })}
    </ol>
  )
}

function CodeQuestion({ q, code, onCode, readOnly, review, runCode }: { q: ViewQuestion; code: string; onCode: (c: string) => void; readOnly: boolean; review: boolean
  runCode?: (qid: number, source: string, mode: 'samples' | 'custom', input?: string) => Promise<RunResponse> }) {
  const [res, setRes] = useState<RunResponse | null>(null)
  const [busy, setBusy] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [custom, setCustom] = useState<string | null>(null)
  const samples: { input: string; expected: string }[] = q.settings?.samples ?? []
  const lang = q.settings?.language ?? 'python'
  const run = async (mode: 'samples' | 'custom') => {
    if (!runCode) return
    setBusy(true); setErr(null)
    try { setRes(await runCode(q.id, code, mode, custom ?? undefined)) } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }
  const j = q.judge
  return (
    <div className="codeq-run">
      {samples.length > 0 && <div className="grid g2">{samples.map((s, i) => <div key={i}><div className="small muted">Example {i + 1}: input → output</div><pre className="log">{s.input || '(empty)'}{'\n→ '}{s.expected}</pre></div>)}</div>}
      {q.settings?.hidden > 0 && <p className="small muted">{q.settings.hidden} hidden test{q.settings.hidden === 1 ? '' : 's'} also run after you submit.</p>}
      <div className="cm-quiz"><CodeMirrorEditor value={code} onChange={onCode} language={lang} readOnly={readOnly} label={`Code for question`} fontSize={14} onRun={() => run('samples')} /></div>
      {!readOnly && runCode && (
        <div className="row" style={{ marginTop: 8 }}>
          <button type="button" onClick={() => run('samples')} disabled={busy || !code.trim()}><Icon name="right" size={15} />{busy ? 'Running…' : 'Run examples'}</button>
          <button type="button" className="quiet" onClick={() => setCustom(custom == null ? '' : null)}>{custom == null ? 'Custom input' : 'Hide custom input'}</button>
          {custom != null && <button type="button" className="quiet" onClick={() => run('custom')} disabled={busy}>Run with input</button>}
          <span className="small muted">Ctrl+Enter runs the examples. Your answer is the code in the editor when you submit.</span>
        </div>)}
      {custom != null && !readOnly && <textarea className="mono" aria-label="Custom input" value={custom} onChange={e => setCustom(e.target.value)} style={{ minHeight: 70, marginTop: 8 }} />}
      {err && <p className="err small">{err}</p>}
      {res && !readOnly && <RunResults r={res} />}
      {(review || readOnly) && j && (
        <div className="judgebox">{j.status === 'QUEUED' || j.status === 'RUNNING' ? <p className="small muted"><span className="spinner" /> Waiting for the judge…</p>
          : j.status === 'FAILED' ? <p className="small muted">The judge could not run this answer; your instructor will mark it.</p>
          : j.result ? <><p className="small"><b>{j.result.passed} / {j.result.total}</b> tests passed</p>
            <ul className="judgetests">{(j.result.tests ?? []).map((t: { name: string; sample: boolean; verdict: string; millis: number; actual?: string }, i: number) => <li key={i}><span>{t.sample ? t.name : `${t.name} (hidden)`}</span><VerdictBadge verdict={t.verdict} /><span className="small muted">{t.millis} ms</span></li>)}</ul>
            {j.result.compileLog && j.result.verdict === 'CE' && <pre className="log">{j.result.compileLog}</pre>}</> : null}</div>)}
    </div>
  )
}
