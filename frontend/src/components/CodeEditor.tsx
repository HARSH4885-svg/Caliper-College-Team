import { useMemo, useRef, type KeyboardEvent } from 'react'

export const ENTRY_FILE: Record<string, string> = { python: 'main.py', javascript: 'main.js', c: 'main.c', cpp: 'main.cpp', java: 'Main.java' }

export const STARTER: Record<string, string> = {
  python: '# Read from standard input, print to standard output.\nimport sys\n\ndef main():\n    data = sys.stdin.read().split()\n    print(len(data))\n\nif __name__ == "__main__":\n    main()\n',
  javascript: "// Read from standard input, print to standard output.\nconst input = require('fs').readFileSync(0, 'utf8').trim().split(/\\s+/);\nconsole.log(input.length);\n",
  c: '#include <stdio.h>\n\nint main(void) {\n    int x;\n    while (scanf("%d", &x) == 1) {\n        /* ... */\n    }\n    return 0;\n}\n',
  cpp: '#include <iostream>\nusing namespace std;\n\nint main() {\n    int x;\n    while (cin >> x) {\n        // ...\n    }\n    return 0;\n}\n',
  java: 'import java.util.*;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        while (in.hasNextInt()) {\n            int x = in.nextInt();\n        }\n    }\n}\n',
}

/** A plain textarea with a line-number gutter; Tab inserts spaces instead of leaving the field. No dependencies, works offline. */
export function CodeEditor({ value, onChange, label }: { value: string; onChange: (v: string) => void; label: string }) {
  const ta = useRef<HTMLTextAreaElement>(null)
  const gutter = useRef<HTMLDivElement>(null)
  const lines = useMemo(() => Math.max(12, value.split('\n').length), [value])
  function key(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key !== 'Tab') return
    e.preventDefault()
    const t = e.currentTarget, s = t.selectionStart, en = t.selectionEnd
    onChange(value.slice(0, s) + '    ' + value.slice(en))
    requestAnimationFrame(() => { t.selectionStart = t.selectionEnd = s + 4 })
  }
  return (
    <div className="editor">
      <div className="gutter" ref={gutter} aria-hidden="true">{Array.from({ length: lines }, (_, i) => <div key={i}>{i + 1}</div>)}</div>
      <label className="sr-only" htmlFor="code-editor">{label}</label>
      <textarea id="code-editor" ref={ta} value={value} spellCheck={false} autoCapitalize="off" autoCorrect="off" wrap="off"
        onChange={e => onChange(e.target.value)} onKeyDown={key} onScroll={e => { if (gutter.current) gutter.current.scrollTop = e.currentTarget.scrollTop }} />
    </div>
  )
}
