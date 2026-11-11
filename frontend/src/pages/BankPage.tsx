import { useMemo, useState } from 'react'
import { api, type BankQuestion, type Question } from '../api'
import { Icon } from '../components/brand'
import { blankQuestion, problems, QTYPES, QuestionEditor } from '../components/QuestionEditor'
import { Empty, ErrorBox, Field, Loading, PageHead } from '../components/ui'
import { fmtMarks, QTYPE_LABEL } from '../labels'
import { useFetch } from '../useFetch'

type Draft = Question & { subject: string; topic: string }
const toDraft = (b: BankQuestion): Draft => ({ id: b.id, type: b.type, prompt: b.prompt, marks: b.marks, difficulty: b.difficulty, required: true, options: b.options, answer: b.answer, settings: b.settings, explanation: b.explanation, bankId: null, subject: b.subject, topic: b.topic })

/** The instructor's reusable question library: browse by subject and topic, search, edit, and add new questions. */
export function BankPage() {
  const q = useFetch(() => api.get<BankQuestion[]>('/api/question-bank'), [])
  const [search, setSearch] = useState(''), [type, setType] = useState(''), [difficulty, setDifficulty] = useState(''), [subject, setSubject] = useState('')
  const [editing, setEditing] = useState<Draft | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<number | null>(null)
  const items = q.data ?? []
  const subjects = [...new Set(items.map(i => i.subject).filter(Boolean))].sort()
  const shown = useMemo(() => items.filter(i => (!type || i.type === type) && (!difficulty || i.difficulty === difficulty) && (!subject || i.subject === subject)
    && (!search.trim() || `${i.prompt} ${i.topic} ${i.subject}`.toLowerCase().includes(search.trim().toLowerCase()))), [items, type, difficulty, subject, search])
  const groups = useMemo(() => {
    const m = new Map<string, BankQuestion[]>()
    for (const i of shown) { const k = `${i.subject || 'No subject'}${i.topic ? ` / ${i.topic}` : ''}`; m.set(k, [...(m.get(k) ?? []), i]) }
    return [...m.entries()]
  }, [shown])

  async function save() {
    if (!editing) return
    const p = problems(editing)
    if (p.length) { setErr(p[0]); return }
    setErr(null)
    const body = { type: editing.type, prompt: editing.prompt, marks: editing.marks, difficulty: editing.difficulty, subject: editing.subject, topic: editing.topic, options: editing.options, answer: editing.answer, settings: editing.settings, explanation: editing.explanation }
    try {
      if (editing.id) await api.put(`/api/question-bank/${editing.id}`, body); else await api.post('/api/question-bank', body)
      setMsg(editing.id ? 'Question updated. Assignments that already use it keep their own copy.' : 'Question added to your bank.'); setEditing(null); await q.reload(true)
    } catch (e) { setErr((e as Error).message) }
  }

  return (
    <>
      <PageHead title="Question bank" sub="Your reusable questions, organised by subject and topic. Import them into any quiz, or draw a random set."
        actions={<button className="primary" onClick={() => { setEditing({ ...blankQuestion('MCQ'), subject: subject, topic: '' }); setMsg(null) }}><Icon name="plus" />New question</button>} />
      <ErrorBox message={err ?? q.error} />{msg && <div className="notice" role="status"><Icon name="check" />{msg}</div>}
      {editing && (
        <div className="card">
          <h2>{editing.id ? 'Edit question' : 'New question'}</h2>
          <div className="grid g2"><Field id="b-sub" label="Subject"><input id="b-sub" value={editing.subject} onChange={e => setEditing({ ...editing, subject: e.target.value })} maxLength={80} list="b-subjects" /><datalist id="b-subjects">{subjects.map(s => <option key={s} value={s} />)}</datalist></Field>
            <Field id="b-top" label="Topic"><input id="b-top" value={editing.topic} onChange={e => setEditing({ ...editing, topic: e.target.value })} maxLength={80} /></Field></div>
          <QuestionEditor q={editing} onChange={x => setEditing({ ...editing, ...x })} idPrefix="bank" />
          <div className="row" style={{ marginTop: 12 }}><button className="primary" onClick={save}>Save</button><button className="quiet" onClick={() => setEditing(null)}>Cancel</button></div>
        </div>)}
      <div className="toolbar">
        <div className="searchfield"><Icon name="search" size={16} /><input type="search" aria-label="Search questions" placeholder="Search questions and topics" value={search} onChange={e => setSearch(e.target.value)} /></div>
        <select aria-label="Subject" value={subject} onChange={e => setSubject(e.target.value)}><option value="">All subjects</option>{subjects.map(s => <option key={s}>{s}</option>)}</select>
        <select aria-label="Type" value={type} onChange={e => setType(e.target.value)}><option value="">All types</option>{QTYPES.map(t => <option key={t} value={t}>{QTYPE_LABEL[t]}</option>)}</select>
        <select aria-label="Difficulty" value={difficulty} onChange={e => setDifficulty(e.target.value)}><option value="">Any difficulty</option><option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select>
        <span className="small muted">{shown.length} of {items.length}</span>
      </div>
      {q.loading ? <Loading /> : items.length === 0 ? <Empty icon="archive">Your bank is empty. Add a question here, or use "Save to question bank" on any quiz question.</Empty>
        : shown.length === 0 ? <Empty icon="search">No question matches.</Empty>
        : groups.map(([g, list]) => (
          <section key={g} className="bankgroup"><h3>{g} <span className="muted small">({list.length})</span></h3>
            <ul className="banklist card">{list.map(b => (
              <li key={b.id}>
                <div className="bq"><span className="small muted">{QTYPE_LABEL[b.type]} · {fmtMarks(b.marks)} mk · <span className={`diff d-${b.difficulty.toLowerCase()}`}>{b.difficulty.toLowerCase()}</span>{b.usedIn > 0 && ` · used in ${b.usedIn} assignment${b.usedIn === 1 ? '' : 's'}`}</span><p>{b.prompt}</p></div>
                <div className="row" style={{ flexWrap: 'nowrap' }}>
                  <button className="quiet" onClick={() => { setEditing(toDraft(b)); window.scrollTo({ top: 0, behavior: 'smooth' }) }}><Icon name="edit" size={15} />Edit</button>
                  {confirm === b.id ? <><button className="quiet danger" onClick={async () => { try { await api.delete(`/api/question-bank/${b.id}`); setConfirm(null); await q.reload(true) } catch (e) { setErr((e as Error).message) } }}>Delete</button><button className="quiet" onClick={() => setConfirm(null)}>Keep</button></>
                    : <button className="quiet" aria-label="Delete question" onClick={() => setConfirm(b.id)}><Icon name="trash" size={15} /></button>}
                </div>
              </li>))}</ul></section>))}
    </>
  )
}
