import type { Fragment } from './api'

export type Side = 'A' | 'B'

/** Which 1-based lines of `file` on `side` are covered by a fragment (and which fragments), for highlighting. */
export function lineMarks(fragments: Fragment[], side: Side, file: string): Map<number, number[]> {
  const marks = new Map<number, number[]>()
  fragments.forEach((f, idx) => {
    const name = side === 'A' ? f.fileA : f.fileB
    if (name !== file) return
    const from = side === 'A' ? f.startLineA : f.startLineB
    const to = side === 'A' ? f.endLineA : f.endLineB
    for (let l = from; l <= to; l++) marks.set(l, [...(marks.get(l) ?? []), idx])
  })
  return marks
}

/** Files that actually carry matches, so the viewer can open on one of them. */
export function filesWithMatches(fragments: Fragment[], side: Side): string[] {
  return [...new Set(fragments.map(f => (side === 'A' ? f.fileA : f.fileB)))]
}
