// Evaluation verdicts (did the program work?) and investigation states (does a pair merit a human look?) are different
// things and are labelled and coloured differently on purpose. No similarity label implies a finding of misconduct.
export const VERDICT_LABEL: Record<string, string> = {
  AC: 'Accepted', WA: 'Wrong answer', CE: 'Compile error', RE: 'Runtime error', TLE: 'Time limit exceeded', MLE: 'Memory limit exceeded', IE: 'Judge problem',
}
export const VERDICT_TONE: Record<string, 'ok' | 'warn' | 'bad' | 'muted'> = {
  AC: 'ok', WA: 'warn', CE: 'bad', RE: 'bad', TLE: 'warn', MLE: 'warn', IE: 'muted',
}

export const STATE_LABEL: Record<string, string> = {
  NO_MEANINGFUL_MATCH: 'No meaningful match',
  LOW_SIMILARITY: 'Low similarity',
  NEEDS_REVIEW: 'Needs review',
  HIGH_PRIORITY_REVIEW: 'High-priority review',
  INSUFFICIENT_EVIDENCE: 'Insufficient evidence',
}
export const STATE_TONE: Record<string, 'review' | 'priority' | 'quiet'> = {
  NO_MEANINGFUL_MATCH: 'quiet', LOW_SIMILARITY: 'quiet', INSUFFICIENT_EVIDENCE: 'quiet', NEEDS_REVIEW: 'review', HIGH_PRIORITY_REVIEW: 'priority',
}

export const DECISION_LABEL: Record<string, string> = {
  CONFIRMED_CONCERN: 'Confirmed concern', DISMISSED: 'Dismissed', INCONCLUSIVE: 'Inconclusive',
}

export const LANGUAGE_LABEL: Record<string, string> = { c: 'C', cpp: 'C++', java: 'Java', python: 'Python', javascript: 'JavaScript' }

export const pct = (x: number) => `${(x * 100).toFixed(1)}%`
export const when = (iso: string) => new Date(iso).toLocaleString()

export const QTYPE_LABEL: Record<string, string> = {
  MCQ: 'Multiple choice', MULTI: 'Multiple select', TRUE_FALSE: 'True or false', SHORT: 'Short answer', LONG: 'Long answer',
  FILL_BLANK: 'Fill in the blanks', OUTPUT: 'Predict the output', FILE: 'File upload', CODE: 'Write code',
}
export const QTYPE_HINT: Record<string, string> = {
  MCQ: 'One correct option. Marked automatically; optional negative marking.',
  MULTI: 'Several correct options. Marked automatically, with optional partial credit.',
  TRUE_FALSE: 'A statement students mark true or false. Marked automatically.',
  SHORT: 'A word or phrase. Marked automatically against accepted answers; leave them empty to mark by hand. Use it for SQL queries and definitions too.',
  LONG: 'An essay or explanation. You mark it by hand, with comments.',
  FILL_BLANK: 'Write ___ for each blank. Each blank is marked automatically.',
  OUTPUT: 'Show code; students type exactly what it prints. Marked automatically.',
  FILE: 'Students upload a file (5 MiB max), for lab reports or diagrams. You mark it by hand.',
  CODE: 'Students write and run a program in the browser. After submission it is judged in the sandbox against your tests; marks follow the share of tests passed.',
}
export const STATUS_LABEL: Record<string, string> = {
  GRADED: 'Graded', PENDING: 'Waiting for marks', IN_PROGRESS: 'In progress', RETURNED: 'Returned for revision', MISSING: 'Missing', NOT_STARTED: 'Not started', SUBMITTED: 'Submitted',
}
export const KIND_LABEL: Record<string, string> = { CODING: 'Coding', QUIZ: 'Quiz' }
export const fmtMarks = (n: number | null | undefined) => n == null ? '–' : Number.isInteger(n) ? String(n) : n.toFixed(Math.abs(n * 10 - Math.round(n * 10)) < 1e-9 ? 1 : 2)
