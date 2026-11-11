import { useEffect, useMemo, useRef, useState } from 'react'
import { api, type BankQuestion, type Json, type Question, type QType, type ViewQuestion } from '../api'
import { fmtMarks, QTYPE_LABEL } from '../labels'
import { Icon } from './brand'
import { blankQuestion, problems, QTYPES, QuestionEditor } from './QuestionEditor'
import { QuizRunner } from './QuizRunner'
import { Empty, ErrorBox, Loading } from './ui'

const toView = (q: Question, i: number): ViewQuestion => ({
  id: q.id ?? -(i + 1), type: q.type, prompt: q.prompt, marks: q.marks, difficulty: q.difficulty, required: q.required,
  options: q.options.map((text, index) => ({ index, text })), response: {},
  settings: q.type === 'CODE' ? { ...q.settings, samples: (q.answer.tests ?? []).filter((t: { sample: boolean }) => t.sample), hidden: (q.answer.tests ?? []).filter((t: { sample: boolean }) => !t.sample).length } : q.settings,
})

const strip = (q: Question) => ({ id: q.id, type: q.type, prompt: q.prompt, marks: q.marks, difficulty: q.difficulty, required: q.required, options: q.options, answer: q.answer, settings: q.settings, explanation: q.explanation, bankId: q.bankId })

/**
 * The multi-question authoring surface: add, edit, reorder (drag or arrow buttons), duplicate and delete questions, pull from
 * the question bank, and preview exactly what a student sees. Nothing is saved until "Save questions", and leaving with unsaved
 * changes is guarded.
 */
export function QuizBuilder({ assignmentId, subject, onSaved }: { assignmentId: number; subject: string; onSaved: () => void }) {
  const [qs, setQs] = useState<Question[] | null>(null)
  const [saved, setSaved] = useState<string>('[]')
  const [open, setOpen] = useState<number | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [mode, setMode] = useState<'edit' | 'preview'>('edit')
  const [bank, setBank] = useState<'import' | 'random' | null>(null)
  const [previewAnswers, setPreviewAnswers] = useState<Record<number, Json>>({})
  const drag = useRef<number | null>(null)
  const [over, setOver] = useState<number | null>(null)

  const load = () => api.get<Question[]>(`/api/assignments/${assignmentId}/questions`).then(r => { setQs(r); setSaved(JSON.stringify(r.map(strip))) }).catch(e => setErr(e.message))
  useEffect(() => { void load() }, [assignmentId]) // eslint-disable-line react-hooks/exhaustive-deps

  const dirty = qs != null && JSON.stringify(qs.map(strip)) !== saved
  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => { e.preventDefault(); e.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  const total = useMemo(() => (qs ?? []).reduce((s, q) => s + (Number(q.marks) || 0), 0), [qs])
  const issues = useMemo(() => (qs ?? []).map(problems), [qs])
  if (!qs) return err ? <ErrorBox message={err} /> : <Loading />

  const update = (i: number, q: Question) => setQs(qs.map((x, j) => (j === i ? q : x)))
  const move = (from: number, to: number) => {
    if (to < 0 || to >= qs.length || from === to) return
    const next = [...qs]; const [m] = next.splice(from, 1); next.splice(to, 0, m); setQs(next)
    setOpen(o => (o === from ? to : o))
  }
  const add = (t: QType) => { setQs([...qs, blankQuestion(t)]); setOpen(qs.length); setMsg(null) }
  const duplicate = (i: number) => { const next = [...qs]; next.splice(i + 1, 0, { ...structuredClone(qs[i]), id: null }); setQs(next); setOpen(i + 1) }
  const remove = (i: number) => { setQs(qs.filter((_, j) => j !== i)); setOpen(null) }

  async function save() {
    const bad = issues.findIndex(p => p.length > 0)
    if (bad >= 0) { setOpen(bad); setErr(`Question ${bad + 1}: ${issues[bad][0]}`); return }
    setBusy(true); setErr(null); setMsg(null)
    try {
      const r = await api.put<Question[]>(`/api/assignments/${assignmentId}/questions`, qs!.map(strip))
      setQs(r); setSaved(JSON.stringify(r.map(strip))); setMsg(`${r.length} question${r.length === 1 ? '' : 's'} saved. Total ${fmtMarks(r.reduce((s, q) => s + q.marks, 0))} marks; any submitted attempts were re-marked.`); onSaved()
    } catch (e) { setErr((e as Error).message) } finally { setBusy(false) }
  }
  async function toBank(q: Question) {
    setErr(null)
    try { await api.post('/api/question-bank', { ...strip(q), subject, topic: '' }); setMsg('Copied to your question bank.') } catch (e) { setErr((e as Error).message) }
  }

  return (
    <div className="builder">
      <div className="builder-bar">
        <div><strong>{qs.length} question{qs.length === 1 ? '' : 's'}</strong><span className="muted"> · {fmtMarks(total)} marks</span>{dirty && <span className="badge t-warn" style={{ marginLeft: 10 }}>Unsaved changes</span>}</div>
        <div className="segmented" role="tablist" aria-label="Builder mode" style={{ width: 220 }}>
          <button type="button" role="tab" aria-selected={mode === 'edit'} className={mode === 'edit' ? 'on' : ''} onClick={() => setMode('edit')}><Icon name="edit" size={15} />Edit</button>
          <button type="button" role="tab" aria-selected={mode === 'preview'} className={mode === 'preview' ? 'on' : ''} onClick={() => setMode('preview')}><Icon name="eye" size={15} />Preview</button>
        </div>
        <span className="grow" />
        {dirty && <button className="quiet" onClick={() => { setQs(JSON.parse(saved)); setOpen(null) }}>Discard</button>}
        <button className="primary" onClick={save} disabled={busy || !dirty}>{busy ? 'Saving…' : 'Save questions'}</button>
      </div>
      <ErrorBox message={err} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}

      {mode === 'preview' ? (
        <div className="preview-frame">
          <p className="hint" style={{ marginTop: 0 }}><Icon name="eye" size={14} /> This is what a student sees. Answers here are not saved; options appear {`in ${'the order you wrote them'}`} unless shuffling is on.</p>
          {qs.length === 0 ? <Empty icon="task">Add a question to preview it.</Empty> : <QuizRunner questions={qs.map(toView)} responses={previewAnswers} onAnswer={(id, r) => setPreviewAnswers({ ...previewAnswers, [id]: r })}
            runCode={(qid, source, mode, input) => qid > 0 ? api.post(`/api/questions/${qid}/run`, { source, mode, input }) : Promise.reject(new Error('Save the question first to run code against its tests.'))} />}
        </div>
      ) : <>
        {qs.length === 0 && <Empty icon="task">No questions yet. Add one below or pull some from your question bank.</Empty>}
        <ol className="blist">
          {qs.map((q, i) => (
            <li key={i} className={`bitem${open === i ? ' open' : ''}${over === i ? ' over' : ''}${issues[i].length ? ' invalid' : ''}`}
              onDragOver={e => { if (drag.current != null) { e.preventDefault(); setOver(i) } }} onDragLeave={() => setOver(null)}
              onDrop={e => { e.preventDefault(); if (drag.current != null) move(drag.current, i); drag.current = null; setOver(null) }}>
              <div className="bsummary">
                <span className="handle" draggable onDragStart={e => { drag.current = i; e.dataTransfer.effectAllowed = 'move' }} onDragEnd={() => { drag.current = null; setOver(null) }} title="Drag to reorder" aria-hidden="true">⋮⋮</span>
                <span className="qnum">{i + 1}</span>
                <button type="button" className="bopen" onClick={() => setOpen(open === i ? null : i)} aria-expanded={open === i}>
                  <span className="btype">{QTYPE_LABEL[q.type]}</span>
                  <span className="bprompt">{q.prompt.trim() || <em className="muted">Untitled question</em>}</span>
                </button>
                {issues[i].length > 0 && <span className="badge t-warn" title={issues[i].join(' ')}>Incomplete</span>}
                <span className="small muted bmarks">{fmtMarks(q.marks)} mk</span>
                <div className="bactions">
                  <button type="button" className="quiet icononly" aria-label={`Move question ${i + 1} up`} disabled={i === 0} onClick={() => move(i, i - 1)}>↑</button>
                  <button type="button" className="quiet icononly" aria-label={`Move question ${i + 1} down`} disabled={i === qs.length - 1} onClick={() => move(i, i + 1)}>↓</button>
                  <button type="button" className="quiet icononly" aria-label={`Duplicate question ${i + 1}`} title="Duplicate" onClick={() => duplicate(i)}><Icon name="copyplus" size={15} /></button>
                  <button type="button" className="quiet icononly" aria-label={`Save question ${i + 1} to the bank`} title="Save to question bank" onClick={() => toBank(q)}><Icon name="archive" size={15} /></button>
                  <button type="button" className="quiet icononly" aria-label={`Delete question ${i + 1}`} title="Delete" onClick={() => remove(i)}><Icon name="trash" size={15} /></button>
                </div>
              </div>
              {open === i && <div className="bbody"><QuestionEditor q={q} onChange={x => update(i, x)} idPrefix={`q${i}`} />
                {issues[i].length > 0 && <ul className="issues">{issues[i].map(x => <li key={x}>{x}</li>)}</ul>}</div>}
            </li>))}
        </ol>
        <div className="addrow">
          <span className="small muted">Add</span>
          {QTYPES.map(t => <button key={t} type="button" className="quiet" onClick={() => add(t)}><Icon name="plus" size={14} />{QTYPE_LABEL[t]}</button>)}
        </div>
        <div className="row" style={{ marginTop: 12 }}>
          <button type="button" onClick={() => setBank('import')}><Icon name="archive" size={16} />Import from question bank</button>
          <button type="button" onClick={() => setBank('random')} disabled={dirty} title={dirty ? 'Save your changes first' : undefined}><Icon name="refresh" size={16} />Random set from bank</button>
        </div>
      </>}
      {bank === 'import' && <BankPicker onClose={() => setBank(null)} onPick={picked => { setQs([...qs, ...picked]); setBank(null); setMsg(`${picked.length} question${picked.length === 1 ? '' : 's'} added. Save to keep them.`) }} />}
      {bank === 'random' && <RandomDialog assignmentId={assignmentId} subject={subject} onClose={() => setBank(null)} onDone={r => { setQs(r); setSaved(JSON.stringify(r.map(strip))); setBank(null); setMsg('Random questions added and saved.'); onSaved() }} />}
    </div>
  )
}

function Dialog({ title, onClose, children, wide }: { title: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  useEffect(() => {
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', k)
    return () => document.removeEventListener('keydown', k)
  }, [onClose])
  return (
    <div className="overlay" onMouseDown={e => { if (e.target === e.currentTarget) onClose() }}>
      <div className={`dialog${wide ? ' wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <div className="row space"><h2 style={{ margin: 0 }}>{title}</h2><button className="quiet" onClick={onClose} aria-label="Close">Close</button></div>
        {children}
      </div>
    </div>
  )
}

export function BankPicker({ onClose, onPick }: { onClose: () => void; onPick: (q: Question[]) => void }) {
  const [items, setItems] = useState<BankQuestion[] | null>(null)
  const [q, setQ] = useState(''), [type, setType] = useState(''), [subject, setSubject] = useState('')
  const [chosen, setChosen] = useState<Set<number>>(new Set())
  useEffect(() => { api.get<BankQuestion[]>('/api/question-bank').then(setItems).catch(() => setItems([])) }, [])
  const subjects = [...new Set((items ?? []).map(i => i.subject).filter(Boolean))].sort()
  const shown = (items ?? []).filter(i => (!type || i.type === type) && (!subject || i.subject === subject) && (!q.trim() || `${i.prompt} ${i.topic}`.toLowerCase().includes(q.trim().toLowerCase())))
  return (
    <Dialog title="Import from question bank" onClose={onClose} wide>
      <div className="toolbar" style={{ marginTop: 12 }}>
        <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Search the bank" placeholder="Search questions or topics" value={q} onChange={e => setQ(e.target.value)} /></div>
        <select aria-label="Type" value={type} onChange={e => setType(e.target.value)}><option value="">All types</option>{QTYPES.map(t => <option key={t} value={t}>{QTYPE_LABEL[t]}</option>)}</select>
        <select aria-label="Subject" value={subject} onChange={e => setSubject(e.target.value)}><option value="">All subjects</option>{subjects.map(s => <option key={s}>{s}</option>)}</select>
      </div>
      {!items ? <Loading /> : shown.length === 0 ? <Empty icon="archive">Nothing in your bank matches. Save questions to the bank from any quiz.</Empty> : (
        <ul className="banklist">{shown.map(b => (
          <li key={b.id}><label className="row" style={{ alignItems: 'flex-start', flexWrap: 'nowrap', margin: 0 }}>
            <input type="checkbox" checked={chosen.has(b.id)} onChange={() => { const n = new Set(chosen); if (n.has(b.id)) n.delete(b.id); else n.add(b.id); setChosen(n) }} style={{ width: 18, marginTop: 4 }} />
            <span><span className="small muted">{QTYPE_LABEL[b.type]} · {fmtMarks(b.marks)} mk · {b.difficulty.toLowerCase()}{b.subject && ` · ${b.subject}`}{b.topic && ` / ${b.topic}`}</span><br />{b.prompt}</span></label></li>))}</ul>)}
      <div className="row space" style={{ marginTop: 12 }}><span className="small muted">{chosen.size} selected</span>
        <button className="primary" disabled={chosen.size === 0} onClick={() => onPick((items ?? []).filter(b => chosen.has(b.id)).map(b => ({ id: null, type: b.type, prompt: b.prompt, marks: b.marks, difficulty: b.difficulty, required: true, options: b.options, answer: b.answer, settings: b.settings, explanation: b.explanation, bankId: b.id })))}>Add to assignment</button></div>
    </Dialog>
  )
}

function RandomDialog({ assignmentId, subject, onClose, onDone }: { assignmentId: number; subject: string; onClose: () => void; onDone: (q: Question[]) => void }) {
  const [f, setF] = useState({ subject, topic: '', difficulty: '', type: '', count: 3 })
  const [err, setErr] = useState<string | null>(null)
  async function go() {
    setErr(null)
    try { onDone(await api.post<Question[]>(`/api/assignments/${assignmentId}/questions/random`, { ...f, subject: f.subject || null, topic: f.topic || null, difficulty: f.difficulty || null, type: f.type || null })) } catch (e) { setErr((e as Error).message) }
  }
  return (
    <Dialog title="Random set from the bank" onClose={onClose}>
      <p className="hint">Draws questions you have not used in this assignment yet. Leave a filter empty to allow anything.</p>
      <ErrorBox message={err} />
      <div className="grid g2">
        <div className="field"><label htmlFor="rs-s">Subject</label><input id="rs-s" value={f.subject} onChange={e => setF({ ...f, subject: e.target.value })} /></div>
        <div className="field"><label htmlFor="rs-t">Topic</label><input id="rs-t" value={f.topic} onChange={e => setF({ ...f, topic: e.target.value })} /></div>
        <div className="field"><label htmlFor="rs-d">Difficulty</label><select id="rs-d" value={f.difficulty} onChange={e => setF({ ...f, difficulty: e.target.value })}><option value="">Any</option><option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select></div>
        <div className="field"><label htmlFor="rs-n">How many</label><input id="rs-n" type="number" min={1} max={50} value={f.count} onChange={e => setF({ ...f, count: Number(e.target.value) })} /></div>
      </div>
      <button className="primary" onClick={go}>Add questions</button>
    </Dialog>
  )
}
