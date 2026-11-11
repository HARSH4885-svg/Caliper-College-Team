import type { RunResponse } from '../api'
import { VERDICT_LABEL } from '../labels'
import { Icon } from './brand'
import { VerdictBadge } from './ui'

/** Whitespace tidy-up only (tabs to four spaces, trailing spaces, final newline): never changes what the program means. */
export const tidy = (s: string) => s.replace(/\t/g, '    ').split('\n').map(l => l.replace(/\s+$/, '')).join('\n').replace(/\n*$/, '\n')

export function RunResults({ r, onRerun, busy }: { r: RunResponse; onRerun?: (i: number) => void; busy?: boolean }) {
  const custom = r.mode === 'custom'
  return (
    <div className="runres">
      <div className={`runhead v-${r.verdict}`}>
        <b>{custom ? (r.verdict === 'OK' ? 'Ran successfully' : VERDICT_LABEL[r.verdict] ?? r.verdict) : r.verdict === 'AC' ? 'All tests passed' : VERDICT_LABEL[r.verdict] ?? r.verdict}</b>
        {!custom && r.total > 0 && <span>{r.passed} / {r.total} test{r.total === 1 ? '' : 's'} passed</span>}
        <span className="muted">{r.millis} ms total</span>
      </div>
      {r.compileLog && (r.verdict === 'CE' || r.compileLog.trim()) && r.verdict === 'CE' && <pre className="log" aria-label="Compiler output">{r.compileLog}</pre>}
      {r.verdict === 'IE' && <p className="small muted">{r.message}</p>}
      {custom ? r.tests.map((t, i) => (
        <div key={i} className="grid g2">
          <div><div className="small muted">Output</div><pre className="log">{t.actual || '(no output)'}</pre></div>
          <div><div className="small muted">Errors</div><pre className="log">{t.stderr || t.detail || '(none)'}</pre></div>
        </div>)) : r.tests.length > 0 && (
        <div className="tablewrap"><table className="runtable">
          <thead><tr><th>Test</th><th>Input</th><th>Expected</th><th>Your output</th><th>Result</th><th className="num">Time</th>{onRerun && <th />}</tr></thead>
          <tbody>{r.tests.map((t, i) => (
            <tr key={i} className={t.verdict === 'AC' ? 'pass' : 'fail'}>
              <td>{t.sample ? t.name : <><Icon name="lock" size={13} /> {t.name}</>}</td>
              <td><pre>{t.input ?? 'hidden'}</pre></td><td><pre>{t.expected ?? 'hidden'}</pre></td>
              <td><pre>{t.actual || (t.stderr ? '' : '(no output)')}{t.stderr && <span className="stderr">{t.stderr}</span>}</pre></td>
              <td><VerdictBadge verdict={t.verdict} />{t.detail && t.verdict !== 'AC' && <div className="small muted">{t.detail}</div>}</td>
              <td className="num">{t.millis} ms</td>
              {onRerun && <td>{t.sample && <button className="quiet" disabled={busy} onClick={() => onRerun(i)} aria-label={`Run ${t.name} again`}>Re-run</button>}</td>}
            </tr>))}</tbody></table></div>)}
      <p className="hint">Time is wall-clock time inside the sandbox, including start-up. Memory use is limited but not reported.</p>
    </div>
  )
}

