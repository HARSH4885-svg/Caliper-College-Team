import { autocompletion, closeBrackets, closeBracketsKeymap, completionKeymap } from '@codemirror/autocomplete'
import { defaultKeymap, history, historyKeymap, indentWithTab } from '@codemirror/commands'
import { cpp } from '@codemirror/lang-cpp'
import { java } from '@codemirror/lang-java'
import { javascript } from '@codemirror/lang-javascript'
import { python } from '@codemirror/lang-python'
import { sql } from '@codemirror/lang-sql'
import { bracketMatching, defaultHighlightStyle, foldGutter, foldKeymap, HighlightStyle, indentOnInput, indentUnit, syntaxHighlighting } from '@codemirror/language'
import { highlightSelectionMatches, searchKeymap } from '@codemirror/search'
import { Compartment, EditorState, Prec } from '@codemirror/state'
import { drawSelection, EditorView, highlightActiveLine, highlightActiveLineGutter, keymap, lineNumbers, placeholder as ph } from '@codemirror/view'
import { tags as t } from '@lezer/highlight'
import { useEffect, useRef } from 'react'

const langExt = (l: string) => (l === 'python' ? python() : l === 'java' ? java() : l === 'c' || l === 'cpp' ? cpp() : l === 'javascript' ? javascript() : l === 'sql' ? sql() : [])

const darkHighlight = HighlightStyle.define([
  { tag: [t.keyword, t.controlKeyword, t.operatorKeyword, t.modifier], color: '#ffcb6b' },
  { tag: [t.string, t.special(t.string)], color: '#9ee493' },
  { tag: [t.number, t.bool, t.null], color: '#f78c6c' },
  { tag: [t.comment, t.lineComment, t.blockComment], color: '#7d8fb3', fontStyle: 'italic' },
  { tag: [t.function(t.variableName), t.function(t.propertyName)], color: '#82aaff' },
  { tag: [t.typeName, t.className], color: '#c3a6ff' },
  { tag: [t.definition(t.variableName)], color: '#e6edff' },
  { tag: [t.operator, t.punctuation], color: '#a9b8d9' },
])

const theme = (dark: boolean, fontSize: number) => EditorView.theme({
  '&': { fontSize: `${fontSize}px`, height: '100%', backgroundColor: dark ? '#0b1830' : '#ffffff', color: dark ? '#e6edff' : '#0a1f3c' },
  '.cm-scroller': { fontFamily: 'var(--mono)', lineHeight: '1.6' },
  '.cm-gutters': { backgroundColor: dark ? '#081a33' : '#f3f5f8', color: dark ? '#6a7da3' : '#8794ab', border: 'none' },
  '.cm-activeLine': { backgroundColor: dark ? '#12244a66' : '#e6ecff66' },
  '.cm-activeLineGutter': { backgroundColor: dark ? '#12244a' : '#e6ecff' },
  '.cm-cursor': { borderLeftColor: dark ? '#f2b705' : '#1b4fe0', borderLeftWidth: '2px' },
  '&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection': { backgroundColor: dark ? '#264f9a !important' : '#c9d8ff !important' },
  '.cm-tooltip': { backgroundColor: dark ? '#10203d' : '#fff', border: '1px solid #c9d2e0' },
  '.cm-tooltip-autocomplete > ul > li[aria-selected]': { backgroundColor: '#1b4fe0', color: '#fff' },
}, { dark })

/**
 * CodeMirror 6 with syntax highlighting, bracket matching, auto-closing, folding, search (Ctrl+F), completion of words in
 * the file and language keywords, and Tab to indent. Language, theme and font size change without losing undo history.
 */
export function CodeMirrorEditor({ value, onChange, language, dark = true, fontSize = 14, readOnly = false, label, onRun, onSubmit, placeholder, minHeight }: {
  value: string; onChange?: (v: string) => void; language: string; dark?: boolean; fontSize?: number; readOnly?: boolean; label: string
  onRun?: () => void; onSubmit?: () => void; placeholder?: string; minHeight?: number
}) {
  const host = useRef<HTMLDivElement>(null)
  const view = useRef<EditorView | null>(null)
  const comp = useRef({ lang: new Compartment(), theme: new Compartment(), hl: new Compartment(), ro: new Compartment() })
  const cbs = useRef({ onChange, onRun, onSubmit })
  cbs.current = { onChange, onRun, onSubmit }

  useEffect(() => {
    if (!host.current) return
    const c = comp.current
    const v = new EditorView({
      parent: host.current,
      state: EditorState.create({
        doc: value,
        extensions: [
          lineNumbers(), highlightActiveLineGutter(), foldGutter(), drawSelection(), history(), indentOnInput(), bracketMatching(), closeBrackets(), autocompletion(),
          highlightActiveLine(), highlightSelectionMatches(), indentUnit.of('    '), EditorState.tabSize.of(4),
          Prec.highest(keymap.of([
            { key: 'Mod-Enter', run: () => { cbs.current.onRun?.(); return !!cbs.current.onRun } },
            { key: 'Mod-Shift-Enter', run: () => { cbs.current.onSubmit?.(); return !!cbs.current.onSubmit } },
          ])),
          keymap.of([...closeBracketsKeymap, ...defaultKeymap, ...searchKeymap, ...historyKeymap, ...foldKeymap, ...completionKeymap, indentWithTab]),
          c.lang.of(langExt(language)), c.theme.of(theme(dark, fontSize)), c.hl.of(syntaxHighlighting(dark ? darkHighlight : defaultHighlightStyle, { fallback: true })),
          c.ro.of(EditorState.readOnly.of(readOnly)), placeholder ? ph(placeholder) : [],
          EditorView.contentAttributes.of({ 'aria-label': label }),
          EditorView.updateListener.of(u => { if (u.docChanged) cbs.current.onChange?.(u.state.doc.toString()) }),
        ],
      }),
    })
    view.current = v
    return () => { v.destroy(); view.current = null }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { view.current?.dispatch({ effects: comp.current.lang.reconfigure(langExt(language)) }) }, [language])
  useEffect(() => { view.current?.dispatch({ effects: [comp.current.theme.reconfigure(theme(dark, fontSize)), comp.current.hl.reconfigure(syntaxHighlighting(dark ? darkHighlight : defaultHighlightStyle, { fallback: true }))] }) }, [dark, fontSize])
  useEffect(() => { view.current?.dispatch({ effects: comp.current.ro.reconfigure(EditorState.readOnly.of(readOnly)) }) }, [readOnly])
  // external value changes (reset, load an old attempt) replace the document; typing does not round-trip here
  useEffect(() => {
    const v = view.current
    if (v && v.state.doc.toString() !== value) v.dispatch({ changes: { from: 0, to: v.state.doc.length, insert: value } })
  }, [value])

  return <div className="cm-host" ref={host} style={minHeight ? { minHeight } : undefined} />
}
