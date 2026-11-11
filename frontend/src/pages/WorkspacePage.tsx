import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, type Assignment, type RunResponse, type Submission } from '../api'
import { useAuth } from '../auth'
import { Icon } from '../components/brand'
import { ENTRY_FILE, STARTER } from '../components/CodeEditor'
import { CodeMirrorEditor } from '../components/LazyCodeMirror'
import { tidy, RunResults } from '../components/RunResults'
import { dueIn, ErrorBox, Loading, VerdictBadge } from '../components/ui'
import { fmtMarks, LANGUAGE_LABEL, when } from '../labels'
import { useTheme } from '../theme'

const pref = <T,>(k: string, d: T): T => { try { const v = localStorage.getItem(k); return v == null ? d : JSON.parse(v) } catch { return d } }
const save = (k: string, v: unknown) => { try { localStorage.setItem(k, JSON.stringify(v)) } catch { /* storage full or blocked */ } }
/**
 * The coding workspace: problem on the left, a full CodeMirror editor on the right, and a console below it for running
 * sample tests or custom input in the sandbox before submitting. Panels resize by dragging (or with the arrow keys on the
 * divider) and the layout, font size and editor theme are remembered; the code is kept as a draft in this browser.
 */
export function WorkspacePage() {
  const { id } = useParams()
  const { me } = useAuth()
  const { theme } = useTheme()
  const inst = me?.role === 'INSTRUCTOR'
  const [a, setA] = useState<Assignment | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const draftKey = `cs-draft-${id}-${me?.id}`
  const [code, setCode] = useState<string | null>(null)
  const [starter, setStarter] = useState('')
  const [split, setSplit] = useState<number>(() => pref('cs-ws-split', 40))
  const [consoleH, setConsoleH] = useState<number>(() => pref('cs-ws-console', 280))
  const [showProblem, setShowProblem] = useState<boolean>(() => pref('cs-ws-problem', true))
  const [font, setFont] = useState<number>(() => pref('cs-ws-font', 14))
  const [edTheme, setEdTheme] = useState<'dark' | 'light' | 'app'>(() => pref('cs-ws-theme', 'app'))
  const [leftTab, setLeftTab] = useState<'problem' | 'history'>('problem')
  const [consoleTab, setConsoleTab] = useState<'results' | 'custom'>('results')
  const [custom, setCustom] = useState('')
  const [result, setResult] = useState<RunResponse | null>(null)
  const [running, setRunning] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [submitted, setSubmitted] = useState<Submission | null>(null)
  const [history, setHistory] = useState<Submission[] | null>(null)
  const [draftAt, setDraftAt] = useState<Date | null>(null)
  const [full, setFull] = useState(false)
  const [confirmReset, setConfirmReset] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const body = useRef<HTMLDivElement>(null)
  const right = useRef<HTMLDivElement>(null)

  useEffect(() => { save('cs-ws-split', split) }, [split])
  useEffect(() => { save('cs-ws-console', consoleH) }, [consoleH])
  useEffect(() => { save('cs-ws-problem', showProblem) }, [showProblem])
  useEffect(() => { save('cs-ws-font', font) }, [font])
  useEffect(() => { save('cs-ws-theme', edTheme) }, [edTheme])

  useEffect(() => {
    api.get<Assignment>(`/api/assignments/${id}`).then(async x => {
      setA(x)
      let s = STARTER[x.language] ?? ''
      const ext = ENTRY_FILE[x.language]?.split('.').pop()
      const f = x.files.find(ff => ff.kind === 'STARTER' && ff.name.toLowerCase().endsWith(`.${ext}`))
      if (f) { try { s = await api.text(`/api/assignments/${x.id}/files/${f.id}`) } catch { /* fall back to the template */ } }
      setStarter(s)
      setCode(pref<string | null>(draftKey, null) ?? s)
    }).catch(e => setErr((e as Error).message))
  }, [id]) // eslint-disable-line react-hooks/exhaustive-deps

  const loadHistory = useCallback(() => { if (!inst) api.get<Submission[]>(`/api/assignments/${id}/submissions/me`).then(setHistory).catch(() => setHistory([])) }, [id, inst])
  useEffect(loadHistory, [loadHistory])

  // keep the draft in this browser
  useEffect(() => {
    if (code == null) return
    const t = setTimeout(() => { save(draftKey, code); setDraftAt(new Date()) }, 600)
    return () => clearTimeout(t)
  }, [code, draftKey])

  // follow a submission until the judge finishes
  useEffect(() => {
    if (!submitted || (submitted.status !== 'QUEUED' && submitted.status !== 'RUNNING')) return
    const t = setTimeout(() => { api.get<Submission>(`/api/submissions/${submitted.id}`).then(s => { setSubmitted(s); if (s.status === 'DONE' || s.status === 'FAILED') loadHistory() }).catch(() => {}) }, 1500)
    return () => clearTimeout(t)
  }, [submitted, loadHistory])

  useEffect(() => {
    const onFs = () => setFull(document.fullscreenElement === root.current)
    document.addEventListener('fullscreenchange', onFs)
    return () => document.removeEventListener('fullscreenchange', onFs)
  }, [])

  const run = useCallback(async (mode: 'samples' | 'custom' | 'all', test?: number) => {
    if (!a || code == null) return
    setRunning(true); setErr(null); setConsoleTab(mode === 'custom' ? 'custom' : 'results')
    try { setResult(await api.post<RunResponse>(`/api/assignments/${a.id}/run`, { source: code, mode, input: mode === 'custom' ? custom : undefined, test })) }
    catch (e) { setErr((e as Error).message) } finally { setRunning(false) }
  }, [a, code, custom])

  const submit = useCallback(async () => {
    if (!a || code == null || inst) return
    setSubmitting(true); setErr(null)
    try {
      const f = new FormData(); f.append('file', new File([code], ENTRY_FILE[a.language] ?? 'main.txt', { type: 'text/plain' }))
      setSubmitted(await api.upload<Submission>(`/api/assignments/${a.id}/submissions`, f)); loadHistory()
    } catch (e) { setErr((e as Error).message) } finally { setSubmitting(false) }
  }, [a, code, inst, loadHistory])

  function dragX(e: React.PointerEvent) {
    const box = body.current?.getBoundingClientRect(); if (!box) return
    const target = e.currentTarget as HTMLElement; target.setPointerCapture(e.pointerId)
    const move = (ev: PointerEvent) => setSplit(Math.min(70, Math.max(20, ((ev.clientX - box.left) / box.width) * 100)))
    const up = () => { target.removeEventListener('pointermove', move); target.removeEventListener('pointerup', up) }
    target.addEventListener('pointermove', move); target.addEventListener('pointerup', up)
  }
  function dragY(e: React.PointerEvent) {
    const box = right.current?.getBoundingClientRect(); if (!box) return
    const target = e.currentTarget as HTMLElement; target.setPointerCapture(e.pointerId)
    const move = (ev: PointerEvent) => setConsoleH(Math.min(box.height - 120, Math.max(44, box.bottom - ev.clientY)))
    const up = () => { target.removeEventListener('pointermove', move); target.removeEventListener('pointerup', up) }
    target.addEventListener('pointermove', move); target.addEventListener('pointerup', up)
  }
  async function loadAttempt(s: Submission) {
    const f = s.files.find(x => a && x.path.toLowerCase().endsWith(`.${ENTRY_FILE[a.language]?.split('.').pop()}`)) ?? s.files[0]
    if (!f) return
    try { setCode(await api.text(`/api/submissions/${s.id}/files/${f.id}`)); setLeftTab('problem') } catch (e) { setErr((e as Error).message) }
  }

  if (err && !a) return <ErrorBox message={err} />
  if (!a || code == null) return <Loading />
  if (a.kind !== 'CODING') return <ErrorBox message="This assignment is a quiz; open it from the class page." />
  const dark = edTheme === 'app' ? theme === 'dark' : edTheme === 'dark'
  const due = dueIn(a.deadline)
  const closed = due.late && (a.latePolicy === 'BLOCK' || (a.lateUntil != null && new Date(a.lateUntil).getTime() < Date.now()))
  const used = history?.length ?? 0

  return (
    <div className={`workspace${full ? ' full' : ''}`} ref={root}>
      <div className="wsbar">
        <Link className="back" to={`/assignments/${a.id}`} style={{ margin: 0 }}><Icon name="left" size={16} />Back</Link>
        <strong className="wstitle">{a.title}</strong>
        <span className="badge t-muted" title="The language is set by your instructor for this assignment">{LANGUAGE_LABEL[a.language]}</span>
        <span className={`small ${due.soon ? 'warn' : 'muted'}`}>{due.text}</span>
        <span className="grow" />
        <div className="wstools" role="toolbar" aria-label="Editor settings">
          <button className="quiet icononly" onClick={() => setShowProblem(!showProblem)} aria-pressed={showProblem} title={showProblem ? 'Hide the problem' : 'Show the problem'}><Icon name="file" size={16} /></button>
          <button className="quiet icononly" onClick={() => setFont(Math.max(11, font - 1))} aria-label="Smaller text">A−</button>
          <span className="small muted" aria-live="polite">{font}px</span>
          <button className="quiet icononly" onClick={() => setFont(Math.min(24, font + 1))} aria-label="Larger text">A+</button>
          <select aria-label="Editor theme" value={edTheme} onChange={e => setEdTheme(e.target.value as typeof edTheme)} style={{ width: 'auto', minHeight: 34, padding: '4px 10px' }}>
            <option value="app">Theme: as app</option><option value="dark">Theme: dark</option><option value="light">Theme: light</option></select>
          <button className="quiet" onClick={() => setCode(tidy(code))} title="Tabs to spaces, trailing spaces removed">Tidy</button>
          {confirmReset ? <><button className="quiet danger" onClick={() => { setCode(starter); setConfirmReset(false) }}>Replace my code</button><button className="quiet" onClick={() => setConfirmReset(false)}>Keep</button></>
            : <button className="quiet" onClick={() => setConfirmReset(true)}>Reset</button>}
          <button className="quiet icononly" onClick={() => (full ? document.exitFullscreen() : root.current?.requestFullscreen())?.catch?.(() => {})} aria-label={full ? 'Exit full screen' : 'Full screen'}><Icon name={full ? 'left' : 'grid'} size={16} /></button>
        </div>
      </div>
      <div className="wsbody" ref={body} style={{ gridTemplateColumns: showProblem ? `${split}% 8px minmax(0, 1fr)` : 'minmax(0, 1fr)' }}>
        {showProblem && <section className="wsleft" aria-label="Problem">
          <div className="wstabs" role="tablist"><button role="tab" aria-selected={leftTab === 'problem'} className={leftTab === 'problem' ? 'on' : ''} onClick={() => setLeftTab('problem')}>Problem</button>
            {!inst && <button role="tab" aria-selected={leftTab === 'history'} className={leftTab === 'history' ? 'on' : ''} onClick={() => setLeftTab('history')}>Submissions{history ? ` (${history.length})` : ''}</button>}</div>
          {leftTab === 'problem' ? <div className="wsscroll">
            <h1 className="wsh1">{a.title}</h1>
            <div className="facts"><span className="fact">{fmtMarks(a.maxMarks)} marks</span><span className="fact">{a.timeLimitMs} ms per test</span><span className="fact">{a.memoryLimitMb} MB</span><span className="fact">{a.maxAttempts} attempts</span></div>
            <p className="statement">{a.description}</p>
            {a.testCases.filter(t => t.sample).map((t, i) => (
              <div className="sample" key={i}><div className="row space"><h3 style={{ margin: 0 }}>Example {i + 1}</h3><button className="quiet" onClick={() => { setCustom(t.input); setConsoleTab('custom') }}>Use as custom input</button></div>
                <div className="grid g2"><div><div className="small muted">Input</div><pre className="log">{t.input}</pre></div><div><div className="small muted">Output</div><pre className="log">{t.expectedOutput}</pre></div></div></div>))}
            <p className="hidden-note"><Icon name="lock" size={16} />{a.hiddenTestCount} hidden test{a.hiddenTestCount === 1 ? '' : 's'} also run when you submit.</p>
            <p className="hint">Read from standard input and print to standard output. Output is compared line by line; trailing spaces and blank lines at the end are ignored.</p>
          </div> : <div className="wsscroll">
            {!history ? <Loading /> : history.length === 0 ? <p className="muted">No submissions yet.</p> : history.map(s => (
              <div className="attempt" key={s.id}><div><Link to={`/submissions/${s.id}`}>Attempt #{s.attempt}</Link><div className="small muted">{when(s.createdAt)}{s.total != null && ` · ${s.passed}/${s.total} tests`}</div></div>
                <div className="row"><VerdictBadge verdict={s.verdict} /><button className="quiet" onClick={() => loadAttempt(s)}>Load code</button></div></div>))}
          </div>}
        </section>}
        {showProblem && <div className="vsplit" role="separator" aria-orientation="vertical" aria-label="Resize panels" aria-valuenow={Math.round(split)} aria-valuemin={20} aria-valuemax={70} tabIndex={0}
          onPointerDown={dragX} onKeyDown={e => { if (e.key === 'ArrowLeft') setSplit(Math.max(20, split - 3)); if (e.key === 'ArrowRight') setSplit(Math.min(70, split + 3)) }} />}
        <section className="wsright" ref={right} aria-label="Editor and console" style={{ gridTemplateRows: `minmax(120px, 1fr) 8px ${consoleH}px` }}>
          <div className="wseditor"><CodeMirrorEditor value={code} onChange={setCode} language={a.language} dark={dark} fontSize={font} label={`Code editor, ${LANGUAGE_LABEL[a.language]}`} onRun={() => run(consoleTab === 'custom' ? 'custom' : 'samples')} onSubmit={submit} /></div>
          <div className="hsplit" role="separator" aria-orientation="horizontal" aria-label="Resize console" tabIndex={0} onPointerDown={dragY}
            onKeyDown={e => { if (e.key === 'ArrowUp') setConsoleH(consoleH + 30); if (e.key === 'ArrowDown') setConsoleH(Math.max(44, consoleH - 30)) }} />
          <div className="wsconsole">
            <div className="wsconsole-bar">
              <div className="wstabs" role="tablist"><button role="tab" aria-selected={consoleTab === 'results'} className={consoleTab === 'results' ? 'on' : ''} onClick={() => setConsoleTab('results')}>Test results</button>
                <button role="tab" aria-selected={consoleTab === 'custom'} className={consoleTab === 'custom' ? 'on' : ''} onClick={() => setConsoleTab('custom')}>Custom input</button></div>
              <span className="grow" />
              <span className="small muted savedraft">{draftAt ? `Draft kept ${draftAt.toLocaleTimeString()}` : ''}</span>
              {inst && <button onClick={() => run('all')} disabled={running}>Run all tests</button>}
              <button onClick={() => run(consoleTab === 'custom' ? 'custom' : 'samples')} disabled={running} title="Ctrl+Enter"><Icon name="right" size={15} />{running ? 'Running…' : consoleTab === 'custom' ? 'Run with input' : 'Run samples'}</button>
              {!inst && <button className="primary" onClick={submit} disabled={submitting || closed || used >= a.maxAttempts} title="Ctrl+Shift+Enter"><Icon name="upload" size={15} />{submitting ? 'Submitting…' : 'Submit'}</button>}
            </div>
            <div className="wsconsole-body">
              <ErrorBox message={err} />
              {submitted && <div className={`notice subnote v-${submitted.verdict ?? 'wait'}`} role="status">
                {submitted.status === 'QUEUED' || submitted.status === 'RUNNING' ? <><span className="spinner" />Attempt #{submitted.attempt} submitted. Judging against all tests…</>
                  : <><VerdictBadge verdict={submitted.verdict} />Attempt #{submitted.attempt}: {submitted.passed}/{submitted.total} tests passed.</>}
                <Link to={`/submissions/${submitted.id}`}>Full result</Link></div>}
              {consoleTab === 'custom' && <div className="field" style={{ marginBottom: 8 }}><label htmlFor="custom-in">Standard input</label>
                <textarea id="custom-in" className="mono" value={custom} onChange={e => setCustom(e.target.value)} placeholder="Type the input your program reads" style={{ minHeight: 80 }} /></div>}
              {running ? <p className="muted"><span className="spinner" /> Running in the sandbox…</p>
                : result && (consoleTab === 'custom') === (result.mode === 'custom') ? <RunResults r={result} busy={running} onRerun={result.mode === 'custom' || inst ? undefined : i => run('samples', i)} />
                : !submitted && <p className="muted small">{consoleTab === 'custom' ? 'Type some input and press "Run with input".' : 'Run your code against the examples before submitting. Nothing is graded until you press Submit.'}</p>}
              {!inst && <p className="hint">{closed ? 'Submissions are closed.' : `${Math.max(0, a.maxAttempts - used)} of ${a.maxAttempts} submissions left. Shortcuts: Ctrl+Enter runs, Ctrl+Shift+Enter submits.`}</p>}
            </div>
          </div>
        </section>
      </div>
    </div>
  )
}

