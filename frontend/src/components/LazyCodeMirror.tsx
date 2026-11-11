import { lazy, Suspense, type ComponentProps } from 'react'

const Inner = lazy(() => import('./CodeMirrorEditor').then(m => ({ default: m.CodeMirrorEditor })))

/** The editor and its language grammars are loaded only when a page actually shows code (about 300 kB gzipped). */
export function CodeMirrorEditor(props: ComponentProps<typeof Inner>) {
  return <Suspense fallback={<div className="cm-host cm-loading" role="status"><span className="spinner" /> Loading editor…</div>}><Inner {...props} /></Suspense>
}
