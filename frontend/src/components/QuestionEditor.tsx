import { useState } from 'react'
import type { Question, QType } from '../api'
import { QTYPE_HINT, QTYPE_LABEL } from '../labels'
import { Icon } from './brand'
import { CodeMirrorEditor } from './LazyCodeMirror'

export const QTYPES: QType[] = ['MCQ', 'MULTI', 'TRUE_FALSE', 'SHORT', 'LONG', 'FILL_BLANK', 'OUTPUT', 'CODE', 'FILE']

/** A sensible empty question of each type, so switching type never leaves an invalid shape behind. */
export function blankQuestion(type: QType): Question {
  const base = { id: null, type, marks: 2, difficulty: 'MEDIUM', required: true, explanation: '', bankId: null, settings: {} as Record<string, unknown>, options: [] as string[], answer: {} as Record<string, unknown> }
  switch (type) {
    case 'MCQ': return { ...base, prompt: '', options: ['', '', '', ''], answer: { correct: [0] } }
    case 'MULTI': return { ...base, marks: 3, prompt: '', options: ['', '', '', ''], answer: { correct: [] }, settings: { partial: true } }
    case 'TRUE_FALSE': return { ...base, marks: 1, prompt: '', answer: { value: true } }
    case 'SHORT': return { ...base, prompt: '', answer: { accepted: [''] } }
    case 'LONG': return { ...base, marks: 10, prompt: '' }
    case 'FILL_BLANK': return { ...base, prompt: 'The ___ of a binary tree is ...', answer: { blanks: [['']] } }
    case 'OUTPUT': return { ...base, marks: 3, prompt: 'What does this program print?', answer: { accepted: [''] }, settings: { code: '', codeLanguage: 'python' } }
    case 'FILE': return { ...base, marks: 10, prompt: '' }
    case 'CODE': return { ...base, marks: 10, prompt: '', settings: { language: 'python', starter: '', timeLimitMs: 2000 }, answer: { tests: [{ name: 'Sample 1', input: '', expected: '', sample: true }] } }
  }
}

export const blanksIn = (prompt: string) => prompt.split('___').length - 1

/** Client-side mirror of the server's validation, so problems show next to the field instead of after saving. */
export function problems(q: Question): string[] {
  const out: string[] = []
  if (!q.prompt.trim()) out.push('Write the question.')
  if (!(q.marks >= 0)) out.push('Marks must be zero or more.')
  if (q.type === 'MCQ' || q.type === 'MULTI') {
    if (q.options.filter(o => o.trim()).length < q.options.length) out.push('Fill in or remove empty options.')
    if (q.options.length < 2) out.push('Give at least two options.')
    if (!(q.answer.correct?.length > 0)) out.push('Mark the correct option.')
  }
  if (q.type === 'FILL_BLANK') {
    const n = blanksIn(q.prompt)
    if (n === 0) out.push('Write ___ where each blank goes.')
    if ((q.answer.blanks ?? []).some((b: string[]) => !b[0]?.trim())) out.push('Give an accepted answer for every blank.')
  }
  if (q.type === 'CODE') {
    if (!(q.answer.tests?.length > 0)) out.push('Add at least one test.')
    if ((q.answer.tests ?? []).some((t: { expected: string }) => !t.expected?.trim())) out.push('Every test needs an expected output.')
  }
  if (q.type === 'OUTPUT') {
    if (!q.settings.code?.trim()) out.push('Add the code.')
    if (!q.answer.accepted?.[0]?.trim()) out.push('Give the expected output.')
  }
  return out
}

function Accepted({ values, onChange, label, multiline }: { values: string[]; onChange: (v: string[]) => void; label: string; multiline?: boolean }) {
  return (
    <div className="field"><label>{label}</label>
      {values.map((v, i) => (
        <div className="row optrow" key={i}>
          {multiline ? <textarea aria-label={`${label} ${i + 1}`} value={v} onChange={e => onChange(values.map((x, j) => j === i ? e.target.value : x))} className="mono" />
            : <input aria-label={`${label} ${i + 1}`} value={v} onChange={e => onChange(values.map((x, j) => j === i ? e.target.value : x))} />}
          {values.length > 1 && <button type="button" className="quiet" aria-label={`Remove accepted answer ${i + 1}`} onClick={() => onChange(values.filter((_, j) => j !== i))}><Icon name="trash" size={15} /></button>}
        </div>))}
      <button type="button" className="quiet" onClick={() => onChange([...values, ''])}><Icon name="plus" size={15} />Another accepted answer</button>
    </div>
  )
}

export function QuestionEditor({ q, onChange, idPrefix }: { q: Question; onChange: (q: Question) => void; idPrefix: string }) {
  const set = (patch: Partial<Question>) => onChange({ ...q, ...patch })
  const [showMore, setShowMore] = useState(!!q.explanation)
  const id = (s: string) => `${idPrefix}-${s}`
  const correct: number[] = q.answer.correct ?? []

  const setType = (type: QType) => {
    const fresh = blankQuestion(type)
    onChange({ ...fresh, id: q.id, prompt: q.prompt || fresh.prompt, marks: q.marks, difficulty: q.difficulty, required: q.required, explanation: q.explanation, bankId: q.bankId })
  }

  return (
    <div className="qeditor">
      <div className="grid qhead">
        <div className="field"><label htmlFor={id('type')}>Type</label>
          <select id={id('type')} value={q.type} onChange={e => setType(e.target.value as QType)}>{QTYPES.map(t => <option key={t} value={t}>{QTYPE_LABEL[t]}</option>)}</select></div>
        <div className="field"><label htmlFor={id('marks')}>Marks</label><input id={id('marks')} type="number" min={0} max={1000} step={0.5} value={q.marks} onChange={e => set({ marks: Number(e.target.value) })} /></div>
        <div className="field"><label htmlFor={id('diff')}>Difficulty</label>
          <select id={id('diff')} value={q.difficulty} onChange={e => set({ difficulty: e.target.value })}><option value="EASY">Easy</option><option value="MEDIUM">Medium</option><option value="HARD">Hard</option></select></div>
      </div>
      <p className="hint" style={{ marginTop: -4 }}>{QTYPE_HINT[q.type]}</p>
      <div className="field"><label htmlFor={id('prompt')}>Question</label>
        <textarea id={id('prompt')} value={q.prompt} onChange={e => {
          const prompt = e.target.value
          if (q.type === 'FILL_BLANK') {
            const n = blanksIn(prompt), cur: string[][] = q.answer.blanks ?? []
            const blanks = Array.from({ length: n }, (_, i) => cur[i] ?? [''])
            onChange({ ...q, prompt, answer: { blanks } })
          } else set({ prompt })
        }} placeholder={q.type === 'FILL_BLANK' ? 'Use ___ (three underscores) for each blank' : 'Type the question. Line breaks are kept.'} /></div>

      {(q.type === 'MCQ' || q.type === 'MULTI') && (
        <div className="field" role="group" aria-label="Options"><label>Options {q.type === 'MCQ' ? '(select the correct one)' : '(tick every correct one)'}</label>
          {q.options.map((o, i) => (
            <div className={`row optrow${correct.includes(i) ? ' right' : ''}`} key={i}>
              <input type={q.type === 'MCQ' ? 'radio' : 'checkbox'} name={id('correct')} aria-label={`Option ${i + 1} is correct`} checked={correct.includes(i)}
                onChange={() => set({ answer: { correct: q.type === 'MCQ' ? [i] : correct.includes(i) ? correct.filter(c => c !== i) : [...correct, i].sort() } })} />
              <input aria-label={`Option ${i + 1}`} value={o} onChange={e => set({ options: q.options.map((x, j) => j === i ? e.target.value : x) })} placeholder={`Option ${String.fromCharCode(65 + i)}`} />
              {q.options.length > 2 && <button type="button" className="quiet" aria-label={`Remove option ${i + 1}`} onClick={() => set({ options: q.options.filter((_, j) => j !== i), answer: { correct: correct.filter(c => c !== i).map(c => c > i ? c - 1 : c) } })}><Icon name="trash" size={15} /></button>}
            </div>))}
          {q.options.length < 10 && <button type="button" className="quiet" onClick={() => set({ options: [...q.options, ''] })}><Icon name="plus" size={15} />Add option</button>}
          <div className="row" style={{ marginTop: 8 }}>
            {q.type === 'MULTI' && <label className="row small check"><input type="checkbox" checked={!!q.settings.partial} onChange={e => set({ settings: { ...q.settings, partial: e.target.checked } })} /> Partial credit (wrong picks cancel right ones)</label>}
            {q.type === 'MCQ' && <label className="row small check">Negative marking <select aria-label="Negative marking" value={String(q.settings.negative ?? 0)} onChange={e => set({ settings: { ...q.settings, negative: Number(e.target.value) } })} style={{ width: 'auto' }}>
              <option value="0">none</option><option value="0.25">−25% of marks</option><option value="0.33">−33%</option><option value="0.5">−50%</option></select></label>}
          </div>
        </div>)}

      {q.type === 'TRUE_FALSE' && (
        <div className="segmented" role="radiogroup" aria-label="The statement is" style={{ maxWidth: 260 }}>
          {[true, false].map(v => <button key={String(v)} type="button" role="radio" aria-checked={q.answer.value === v} className={q.answer.value === v ? 'on' : ''} onClick={() => set({ answer: { value: v } })}>{v ? 'True' : 'False'}</button>)}
        </div>)}

      {q.type === 'SHORT' && <>
        <Accepted label="Accepted answer" values={q.answer.accepted ?? ['']} onChange={v => set({ answer: { accepted: v } })} />
        <label className="row small check"><input type="checkbox" checked={!!q.settings.caseSensitive} onChange={e => set({ settings: { ...q.settings, caseSensitive: e.target.checked } })} /> Case-sensitive (spacing is always ignored)</label>
      </>}

      {q.type === 'FILL_BLANK' && (q.answer.blanks ?? []).map((b: string[], i: number) =>
        <Accepted key={i} label={`Blank ${i + 1}`} values={b} onChange={v => set({ answer: { blanks: (q.answer.blanks as string[][]).map((x, j) => j === i ? v : x) } })} />)}

      {q.type === 'OUTPUT' && <>
        <div className="grid g2">
          <div className="field"><label htmlFor={id('code')}>Code</label><textarea id={id('code')} className="mono" value={q.settings.code ?? ''} onChange={e => set({ settings: { ...q.settings, code: e.target.value } })} style={{ minHeight: 140 }} /></div>
          <div>
            <div className="field"><label htmlFor={id('clang')}>Language shown</label><select id={id('clang')} value={q.settings.codeLanguage ?? 'python'} onChange={e => set({ settings: { ...q.settings, codeLanguage: e.target.value } })}>
              {['python', 'java', 'c', 'cpp', 'javascript', 'sql'].map(l => <option key={l}>{l}</option>)}</select></div>
            <Accepted label="Expected output" multiline values={q.answer.accepted ?? ['']} onChange={v => set({ answer: { accepted: v } })} />
          </div>
        </div>
        <p className="hint">Trailing spaces and trailing blank lines are ignored; everything else must match.</p>
      </>}

      {q.type === 'CODE' && <CodeTests q={q} set={set} idPrefix={idPrefix} />}

      <div className="row" style={{ marginTop: 4 }}>
        <label className="row small check"><input type="checkbox" checked={q.required} onChange={e => set({ required: e.target.checked })} /> Required</label>
        <button type="button" className="quiet" onClick={() => setShowMore(!showMore)} aria-expanded={showMore}>{showMore ? 'Hide' : 'Add'} explanation</button>
      </div>
      {showMore && <div className="field"><label htmlFor={id('expl')}>Explanation (shown to students after results are released)</label><textarea id={id('expl')} value={q.explanation} onChange={e => set({ explanation: e.target.value })} /></div>}
    </div>
  )
}

type T = { name: string; input: string; expected: string; sample: boolean }
function CodeTests({ q, set, idPrefix }: { q: Question; set: (p: Partial<Question>) => void; idPrefix: string }) {
  const tests: T[] = q.answer.tests ?? []
  const put = (next: T[]) => set({ answer: { tests: next } })
  return (
    <div className="codeq">
      <div className="grid g3">
        <div className="field"><label htmlFor={`${idPrefix}-lang`}>Language</label><select id={`${idPrefix}-lang`} value={q.settings.language ?? 'python'} onChange={e => set({ settings: { ...q.settings, language: e.target.value } })}>
          {[['python', 'Python'], ['java', 'Java'], ['c', 'C'], ['cpp', 'C++'], ['javascript', 'JavaScript']].map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></div>
        <div className="field"><label htmlFor={`${idPrefix}-tl`}>Time limit per test (ms)</label><input id={`${idPrefix}-tl`} type="number" min={100} max={10000} value={q.settings.timeLimitMs ?? 2000} onChange={e => set({ settings: { ...q.settings, timeLimitMs: Number(e.target.value) } })} /></div>
      </div>
      <div className="field"><label>Starter code (optional)</label><div className="cm-small"><CodeMirrorEditor value={q.settings.starter ?? ''} onChange={v => set({ settings: { ...q.settings, starter: v } })} language={q.settings.language ?? 'python'} label="Starter code" dark={false} fontSize={13} /></div></div>
      <div className="field"><label>Tests (score = share of tests passed; students only see sample tests)</label>
        {tests.map((t, i) => (
          <div className="testrow" key={i}>
            <input aria-label={`Test ${i + 1} name`} value={t.name} onChange={e => put(tests.map((x, j) => j === i ? { ...x, name: e.target.value } : x))} />
            <textarea className="mono" aria-label={`Test ${i + 1} input`} placeholder="Input" value={t.input} onChange={e => put(tests.map((x, j) => j === i ? { ...x, input: e.target.value } : x))} />
            <textarea className="mono" aria-label={`Test ${i + 1} expected output`} placeholder="Expected output" value={t.expected} onChange={e => put(tests.map((x, j) => j === i ? { ...x, expected: e.target.value } : x))} />
            <div className="testctl"><label className="row small check"><input type="checkbox" checked={t.sample} onChange={e => put(tests.map((x, j) => j === i ? { ...x, sample: e.target.checked } : x))} /> Sample</label>
              {tests.length > 1 && <button type="button" className="quiet" aria-label={`Remove test ${i + 1}`} onClick={() => put(tests.filter((_, j) => j !== i))}><Icon name="trash" size={15} /></button>}</div>
          </div>))}
        <button type="button" className="quiet" onClick={() => put([...tests, { name: `Test ${tests.length + 1}`, input: '', expected: '', sample: false }])}><Icon name="plus" size={15} />Add test</button>
      </div>
    </div>
  )
}
