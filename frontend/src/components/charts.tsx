import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import type { Analytics, PairSummary } from '../api'
import { VERDICT_LABEL } from '../labels'

const VERDICT_COLOR: Record<string, string> = { AC: '#0b7a4d', WA: '#e0a800', CE: '#b42318', RE: '#c2410c', TLE: '#7c5cff', MLE: '#7c5cff', IE: '#8793ab' }

/** One stacked bar for the latest attempt of every student, with a legend that doubles as the numbers. */
export function VerdictBar({ verdicts, pending }: { verdicts: Analytics['verdicts']; pending: number }) {
  const entries = Object.entries(verdicts).filter(([, n]) => n > 0)
  const total = entries.reduce((a, [, n]) => a + n, 0) + pending
  if (total === 0) return <p className="muted">No results yet.</p>
  return (
    <div>
      <div className="vbar" role="img" aria-label={entries.map(([k, n]) => `${n} ${VERDICT_LABEL[k] ?? k}`).join(', ')}>
        {entries.map(([k, n]) => <i key={k} style={{ flexGrow: n, background: VERDICT_COLOR[k] }} title={`${VERDICT_LABEL[k] ?? k}: ${n}`} />)}
        {pending > 0 && <i style={{ flexGrow: pending, background: 'repeating-linear-gradient(45deg,#c9d2e0 0 6px,#e8ecf3 6px 12px)' }} title={`Waiting: ${pending}`} />}
      </div>
      <ul className="legend">
        {entries.map(([k, n]) => <li key={k}><span className="dot" style={{ background: VERDICT_COLOR[k] }} />{VERDICT_LABEL[k] ?? k} <b>{n}</b></li>)}
        {pending > 0 && <li><span className="dot" style={{ background: '#c9d2e0' }} />Waiting <b>{pending}</b></li>}
      </ul>
    </div>
  )
}

export function Histogram({ buckets }: { buckets: { label: string; count: number }[] }) {
  const max = Math.max(1, ...buckets.map(b => b.count))
  return (
    <div className="hist" role="img" aria-label={buckets.map(b => `${b.label}: ${b.count}`).join(', ')}>
      {buckets.map(b => (
        <div key={b.label} className="hcol">
          <span className="hn">{b.count}</span>
          <i style={{ height: `${Math.max(3, (b.count / max) * 100)}%` }} className={b.label === '100%' ? 'full' : ''} />
          <span className="hl">{b.label}</span>
        </div>))}
    </div>
  )
}

/** Pass rate per test over every student's latest attempt: the hardest tests float to the top. */
export function TestBars({ tests }: { tests: Analytics['tests'] }) {
  const sorted = [...tests].sort((a, b) => a.passed / Math.max(1, a.total) - b.passed / Math.max(1, b.total))
  return (
    <div className="tbars">
      {sorted.map(t => {
        const r = t.total ? t.passed / t.total : 0
        return (
          <div key={t.name} className="tbar">
            <span className="tn">{t.name}{!t.sample && <em> hidden</em>}</span>
            <div className="track" role="img" aria-label={`${Math.round(r * 100)}% of students pass ${t.name}`}><i style={{ width: `${r * 100}%` }} className={r < 0.5 ? 'low' : ''} /></div>
            <b>{Math.round(r * 100)}%</b>
          </div>)
      })}
    </div>
  )
}

/** Students as dots, candidate pairs as lines: thicker means more distinctive overlap. Layout is a small deterministic spring simulation. */
export function SimilarityNetwork({ pairs }: { pairs: PairSummary[] }) {
  const { nodes, edges, vb } = useMemo(() => {
    const W = 760, H = 420
    const ids = [...new Set(pairs.flatMap(p => [p.a.studentId, p.b.studentId]))]
    const name = new Map<number, string>()
    pairs.forEach(p => { name.set(p.a.studentId, p.a.studentName); name.set(p.b.studentId, p.b.studentName) })
    const pos = ids.map((_, i) => ({ x: W / 2 + Math.cos((i / ids.length) * 6.283) * 150, y: H / 2 + Math.sin((i / ids.length) * 6.283) * 130, vx: 0, vy: 0 }))
    const idx = new Map(ids.map((id, i) => [id, i]))
    for (let it = 0; it < 220; it++) {
      for (let i = 0; i < pos.length; i++) for (let j = i + 1; j < pos.length; j++) {
        const dx = pos[i].x - pos[j].x, dy = pos[i].y - pos[j].y, d2 = Math.max(60, dx * dx + dy * dy), f = 5200 / d2, d = Math.sqrt(d2)
        pos[i].vx += (dx / d) * f; pos[i].vy += (dy / d) * f; pos[j].vx -= (dx / d) * f; pos[j].vy -= (dy / d) * f
      }
      pairs.forEach(p => {
        const a = pos[idx.get(p.a.studentId)!], b = pos[idx.get(p.b.studentId)!]
        const dx = b.x - a.x, dy = b.y - a.y, d = Math.max(1, Math.hypot(dx, dy)), f = (d - (190 - p.score * 110)) * 0.02
        a.vx += (dx / d) * f; a.vy += (dy / d) * f; b.vx -= (dx / d) * f; b.vy -= (dy / d) * f
      })
      pos.forEach(n => { n.vx += (W / 2 - n.x) * 0.004; n.vy += (H / 2 - n.y) * 0.004; n.x = Math.min(W - 60, Math.max(60, n.x + n.vx * 0.5)); n.y = Math.min(H - 30, Math.max(30, n.y + n.vy * 0.5)); n.vx *= 0.6; n.vy *= 0.6 })
    }
    const deg = new Map<number, number>(); pairs.forEach(p => { deg.set(p.a.studentId, (deg.get(p.a.studentId) ?? 0) + 1); deg.set(p.b.studentId, (deg.get(p.b.studentId) ?? 0) + 1) })
    const xs = pos.map(n => n.x), ys = pos.map(n => n.y)
    const x0 = Math.min(...xs) - 90, x1 = Math.max(...xs) + 90, y0 = Math.min(...ys) - 50, y1 = Math.max(...ys) + 60
    const bw = Math.max(420, x1 - x0), bh = Math.max(220, y1 - y0)
    return { vb: `${(x0 + x1) / 2 - bw / 2} ${(y0 + y1) / 2 - bh / 2} ${bw} ${bh}`, nodes: ids.map((id, i) => ({ id, name: name.get(id) ?? '', x: pos[i].x, y: pos[i].y, deg: deg.get(id) ?? 1 })), edges: pairs.map(p => ({ p, a: pos[idx.get(p.a.studentId)!], b: pos[idx.get(p.b.studentId)!] })) }
  }, [pairs])
  if (pairs.length === 0) return null
  const col = (s: string) => s === 'HIGH_PRIORITY_REVIEW' ? '#1238a8' : s === 'NEEDS_REVIEW' ? '#4b3fc9' : '#9aa6bd'
  return (
    <svg className="network" viewBox={vb} role="group" aria-label="Similarity network of students">
      {edges.map(({ p, a, b }) => (
        <Link key={p.id} to={`/pairs/${p.id}`} aria-label={`${p.a.studentName} and ${p.b.studentName}, evidence score ${Math.round(p.score * 100)} percent`}>
          <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke="transparent" strokeWidth="16" />
          <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke={col(p.state)} strokeWidth={1.5 + p.score * 6} strokeLinecap="round" opacity={0.85}><title>{`${p.a.studentName} ↔ ${p.b.studentName}: ${Math.round(p.score * 100)}%`}</title></line>
        </Link>))}
      {nodes.map(n => (
        <g key={n.id} className="nnode">
          <circle cx={n.x} cy={n.y} r={9 + Math.min(6, n.deg * 2)} fill="#0a1f3c" stroke="#f2b705" strokeWidth="3" />
          <text x={n.x} y={n.y + 28} textAnchor="middle">{n.name.replace(/ \(synthetic\)/, '')}</text>
        </g>))}
    </svg>
  )
}

/** A small area/line chart for a running total over dates, with labelled first and last points. */
export function LineChart({ points, label }: { points: { x: string; y: number }[]; label: string }) {
  if (points.length === 0) return <p className="muted small">No data yet.</p>
  const W = 520, H = 160, P = 28
  const max = Math.max(1, ...points.map(p => p.y))
  const xs = (i: number) => P + (points.length === 1 ? (W - 2 * P) / 2 : (i * (W - 2 * P)) / (points.length - 1))
  const ys = (y: number) => H - P - (y / max) * (H - 2 * P)
  const d = points.map((p, i) => `${i ? 'L' : 'M'}${xs(i).toFixed(1)},${ys(p.y).toFixed(1)}`).join(' ')
  const last = points[points.length - 1]
  return (
    <svg className="linechart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`${label}: from ${points[0].y} on ${points[0].x} to ${last.y} on ${last.x}`}>
      <line x1={P} x2={W - P} y1={H - P} y2={H - P} className="axis" />
      <path d={`${d} L${xs(points.length - 1)},${H - P} L${xs(0)},${H - P} Z`} className="area" />
      <path d={d} className="line" />
      {points.map((p, i) => <circle key={i} cx={xs(i)} cy={ys(p.y)} r={3} className="dot"><title>{`${p.x}: ${p.y}`}</title></circle>)}
      <text x={xs(0)} y={H - 8} className="tick">{points[0].x.slice(5)}</text>
      <text x={xs(points.length - 1)} y={H - 8} className="tick" textAnchor="end">{last.x.slice(5)}</text>
      <text x={xs(points.length - 1)} y={ys(last.y) - 8} className="val" textAnchor="end">{last.y}</text>
    </svg>
  )
}

/** Two series per week (coding submissions and quiz submissions), side by side. */
export function WeeklyBars({ weeks }: { weeks: { week: string; code: number; quiz: number }[] }) {
  const max = Math.max(1, ...weeks.map(w => Math.max(w.code, w.quiz)))
  return (
    <div>
      <div className="weekly" role="img" aria-label={weeks.map(w => `week of ${w.week}: ${w.code} code, ${w.quiz} quiz`).join('; ')}>
        {weeks.map(w => (
          <div key={w.week} className="wk" title={`Week of ${w.week}: ${w.code} code submissions, ${w.quiz} quiz submissions`}>
            <div className="bars"><i className="c" style={{ height: `${(w.code / max) * 100}%` }} /><i className="q" style={{ height: `${(w.quiz / max) * 100}%` }} /></div>
            <span>{w.week.slice(5)}</span>
          </div>))}
      </div>
      <div className="legend"><span><i className="c" />Code submissions</span><span><i className="q" />Quiz submissions</span></div>
    </div>
  )
}
