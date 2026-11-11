import { useEffect, useMemo, useRef, useState } from 'react'
import { api, type Fragment, type PairDetail } from '../api'
import { filesWithMatches, lineMarks, type Side } from '../lineMarks'
import { ErrorBox } from './ui'

function CodePane({ title, sid, files, fragments, side, file, onFile, focusLine, scrollRef, onScroll }: {
  title: string; sid: number; files: { id: number; path: string }[]; fragments: Fragment[]; side: Side; file: string; onFile: (f: string) => void
  focusLine: number | null; scrollRef: React.RefObject<HTMLDivElement>; onScroll: () => void
}) {
  const [text, setText] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const marks = useMemo(() => lineMarks(fragments, side, file), [fragments, side, file])
  useEffect(() => {
    const f = files.find(x => x.path === file)
    if (!f) return
    setText(null); setErr(null)
    api.text(`/api/submissions/${sid}/files/${f.id}`).then(setText).catch(e => setErr(e.message))
  }, [sid, files, file])
  useEffect(() => {
    if (focusLine == null || !scrollRef.current) return
    const el = scrollRef.current.querySelector<HTMLElement>(`[data-line="${focusLine}"]`)
    if (el) scrollRef.current.scrollTop = Math.max(0, el.offsetTop - 60)
  }, [focusLine, text, scrollRef])
  const lines = text?.split('\n') ?? []
  return (
    <section className="pane" aria-label={title}>
      <header><strong>{title}</strong><label className="small muted" htmlFor={`f-${side}`} style={{ margin: 0 }}>File</label>
        <select id={`f-${side}`} value={file} onChange={e => onFile(e.target.value)}>{files.map(f => <option key={f.id} value={f.path}>{f.path}</option>)}</select></header>
      <ErrorBox message={err} />
      <div className="code" ref={scrollRef} onScroll={onScroll} tabIndex={0} aria-label={`${title} source`}>
        {text === null && !err ? <p className="muted" style={{ padding: 12 }}>Loading…</p> : lines.map((l, i) => {
          const n = i + 1, hit = marks.get(n)
          const dist = hit?.some(ix => fragments[ix]?.distinctive)
          return <div key={n} data-line={n} className={`ln${hit ? ' hit' : ''}${dist ? ' dist' : ''}${focusLine === n ? ' focus' : ''}`}><i>{n}</i><span>{l || ' '}</span></div>
        })}
      </div>
    </section>
  )
}

export function CodeCompare({ detail }: { detail: PairDetail }) {
  const { fragments, structuralFragments = [], filesA, filesB, summary } = detail
  const [evidenceMode, setEvidenceMode] = useState<'lexical' | 'ast'>('lexical')

  const activeFragments: Fragment[] = useMemo(() => {
    if (evidenceMode === 'ast' && structuralFragments.length > 0) {
      return structuralFragments.map(sf => ({
        fileA: sf.fileA,
        startLineA: sf.startLineA,
        endLineA: sf.endLineA,
        fileB: sf.fileB,
        startLineB: sf.startLineB,
        endLineB: sf.endLineB,
        tokenCount: sf.nodeCount,
        rarityWeight: 1.0,
        distinctive: true,
      }))
    }
    return fragments
  }, [evidenceMode, fragments, structuralFragments])

  const firstA = filesWithMatches(activeFragments, 'A')[0] ?? filesA[0]?.path ?? ''
  const firstB = filesWithMatches(activeFragments, 'B')[0] ?? filesB[0]?.path ?? ''
  const [fileA, setFileA] = useState(firstA)
  const [fileB, setFileB] = useState(firstB)
  const [focus, setFocus] = useState<{ a: number; b: number } | null>(null)
  const refA = useRef<HTMLDivElement>(null), refB = useRef<HTMLDivElement>(null)
  const syncing = useRef(false)

  // Synchronised scrolling: proportional, with a guard so the two panes do not trigger each other forever.
  const sync = (from: React.RefObject<HTMLDivElement>, to: React.RefObject<HTMLDivElement>) => () => {
    if (syncing.current || !from.current || !to.current) return
    syncing.current = true
    const f = from.current, t = to.current
    const ratio = f.scrollTop / Math.max(1, f.scrollHeight - f.clientHeight)
    t.scrollTop = ratio * (t.scrollHeight - t.clientHeight)
    requestAnimationFrame(() => { syncing.current = false })
  }

  const go = (f: { fileA: string; fileB: string; startLineA: number; startLineB: number }) => {
    setFileA(f.fileA); setFileB(f.fileB); setFocus({ a: f.startLineA, b: f.startLineB })
  }

  return (
    <div>
      <div className="compare">
        <CodePane title={`${summary.a.studentName}`} sid={summary.a.submissionId} files={filesA} fragments={activeFragments} side="A" file={fileA} onFile={setFileA} focusLine={focus?.a ?? null} scrollRef={refA} onScroll={sync(refA, refB)} />
        <CodePane title={`${summary.b.studentName}`} sid={summary.b.submissionId} files={filesB} fragments={activeFragments} side="B" file={fileB} onFile={setFileB} focusLine={focus?.b ?? null} scrollRef={refB} onScroll={sync(refB, refA)} />
      </div>

      <div className="row space" style={{ marginTop: 24, marginBottom: 12, alignItems: 'center' }}>
        <h3 style={{ margin: 0 }}>
          {evidenceMode === 'lexical'
            ? `Lexical Matched Fragments (${fragments.length})`
            : `AST Structural Syntax Blocks (${structuralFragments.length})`}
        </h3>
        <div className="segmented" role="tablist" style={{ maxWidth: 340 }}>
          <button
            type="button"
            role="tab"
            aria-selected={evidenceMode === 'lexical'}
            className={evidenceMode === 'lexical' ? 'on' : ''}
            onClick={() => setEvidenceMode('lexical')}
          >
            Lexical Tokens
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={evidenceMode === 'ast'}
            className={evidenceMode === 'ast' ? 'on' : ''}
            onClick={() => setEvidenceMode('ast')}
          >
            AST Structural Nodes
          </button>
        </div>
      </div>

      <p className="hint">
        {evidenceMode === 'lexical'
          ? 'Highlighted lines are token runs matching via Winnowing + RKR-GST. Darker highlight indicates distinctive content. Click a row to navigate.'
          : 'Highlighted lines are normalized AST syntax subtrees (Tree-sitter) identical in structure despite variable renaming, comments, or formatting.'}
      </p>

      {evidenceMode === 'lexical' ? (
        <div className="tablewrap"><table>
          <thead><tr><th>Left</th><th>Right</th><th>Tokens</th><th>Distinctive</th></tr></thead>
          <tbody>{fragments.map((f, i) => (
            <tr key={i}><td><button className="quiet" onClick={() => go(f)} aria-label={`Show fragment ${i + 1}`}>{f.fileA}:{f.startLineA}–{f.endLineA}</button></td><td>{f.fileB}:{f.startLineB}–{f.endLineB}</td><td>{f.tokenCount}</td><td>{f.distinctive ? 'yes' : 'common / starter-like'}</td></tr>
          ))}</tbody></table></div>
      ) : (
        <div className="tablewrap"><table>
          <thead><tr><th>Left</th><th>Right</th><th>Syntax Node Type</th><th>Subtree Size</th></tr></thead>
          <tbody>{structuralFragments.length === 0 ? (
            <tr><td colSpan={4} className="muted" style={{ padding: 12 }}>No non-trivial AST structural blocks recorded for this pair.</td></tr>
          ) : structuralFragments.map((sf, i) => (
            <tr key={i}><td><button className="quiet" onClick={() => go(sf)} aria-label={`Show AST block ${i + 1}`}>{sf.fileA}:{sf.startLineA}–{sf.endLineA}</button></td><td>{sf.fileB}:{sf.startLineB}–{sf.endLineB}</td><td><code style={{ fontSize: '0.85em', background: 'var(--bg-subtle, #f4f4f5)', padding: '2px 6px', borderRadius: 4 }}>{sf.nodeType}</code></td><td>{sf.nodeCount} nodes</td></tr>
          ))}</tbody></table></div>
      )}
    </div>
  )
}
