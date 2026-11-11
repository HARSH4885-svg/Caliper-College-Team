// Thin typed client. The bearer token lives in sessionStorage (cleared when the tab closes); the backend re-checks every request.
const KEY = 'cs.token'

export const tokenStore = {
  get: () => {
    try { return sessionStorage.getItem(KEY) } catch { return null }
  },
  set: (t: string) => { try { sessionStorage.setItem(KEY, t) } catch { /* storage unavailable */ } },
  clear: () => { try { sessionStorage.removeItem(KEY) } catch { /* storage unavailable */ } },
}

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message) }
}

let onUnauthorized: () => void = () => {}
export const setUnauthorizedHandler = (f: () => void) => { onUnauthorized = f }

async function request<T>(method: string, path: string, body?: unknown, form?: FormData): Promise<T> {
  const headers: Record<string, string> = {}
  const t = tokenStore.get()
  if (t) headers.Authorization = `Bearer ${t}`
  let payload: BodyInit | undefined
  if (form) payload = form
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body) }
  const res = await fetch(path, { method, headers, body: payload })
  if (res.status === 401 && t && !path.startsWith('/api/auth/login')) onUnauthorized()
  const text = await res.text()
  let data: unknown = null
  if (text) { try { data = JSON.parse(text) } catch { data = text } }
  if (!res.ok) {
    const msg = (data && typeof data === 'object' && 'error' in data ? String((data as { error: unknown }).error) : null) ?? `Request failed (${res.status})`
    throw new ApiError(res.status, msg)
  }
  return data as T
}

export const api = {
  get: <T,>(p: string) => request<T>('GET', p),
  post: <T,>(p: string, b?: unknown) => request<T>('POST', p, b ?? {}),
  patch: <T,>(p: string, b: unknown) => request<T>('PATCH', p, b),
  delete: <T,>(p: string) => request<T>('DELETE', p),
  put: <T,>(p: string, b: unknown) => request<T>('PUT', p, b),
  upload: <T,>(p: string, f: FormData) => request<T>('POST', p, undefined, f),
  text: async (p: string): Promise<string> => {
    const t = tokenStore.get()
    const res = await fetch(p, { headers: t ? { Authorization: `Bearer ${t}` } : {} })
    if (!res.ok) throw new ApiError(res.status, `Request failed (${res.status})`)
    return res.text()
  },
  /** Authenticated download (the bearer token cannot be sent by a plain link). */
  download: async (p: string, filename: string): Promise<void> => {
    const t = tokenStore.get()
    const res = await fetch(p, { headers: t ? { Authorization: `Bearer ${t}` } : {} })
    if (!res.ok) throw new ApiError(res.status, `Download failed (${res.status})`)
    const url = URL.createObjectURL(await res.blob())
    const a = document.createElement('a')
    a.href = url; a.download = filename; a.click()
    URL.revokeObjectURL(url)
  },
}

// ---- types mirroring the backend DTOs (only fields the UI uses) ----
export type Role = 'INSTRUCTOR' | 'STUDENT'
export interface Me { id: number; email: string; displayName: string; role: Role }
export interface AuthResponse { token: string; user: Me }
export interface Course { id: number; code: string; title: string; instructorName: string; enrollmentCode: string | null; students: number; assignments: number; synthetic: boolean; archived: boolean; description: string; subject: string; category: string; startsOn: string | null; endsOn: string | null; lead: boolean; staff: string[] }
export interface StudentRow { id: number; displayName: string; email: string; enrolledAt: string }
export interface AFile { id: number; kind: string; name: string; sizeBytes: number }
export interface TestCaseDto { id: number | null; name: string; sample: boolean; input: string; expectedOutput: string }
export interface Assignment { id: number; courseId: number; title: string; description: string; language: string; deadline: string; timeLimitMs: number; memoryLimitMb: number; published: boolean; archived: boolean; files: AFile[]; testCases: TestCaseDto[]; hiddenTestCount: number; kind: 'CODING' | 'QUIZ'; category: string; difficulty: string; maxMarks: number; passingMarks: number | null; availableFrom: string | null; maxAttempts: number; latePolicy: 'BLOCK' | 'ALLOW' | 'PENALTY'; latePenaltyPct: number; lateUntil: string | null; quizMinutes: number | null; shuffleQuestions: boolean; shuffleOptions: boolean; releaseMode: 'IMMEDIATE' | 'AFTER_DUE' | 'MANUAL'; showAnswers: boolean; questionCount: number }
export interface SFile { id: number; path: string; sizeBytes: number }
export interface Submission { id: number; assignmentId: number; attempt: number; status: string; createdAt: string; studentId: number | null; studentName: string | null; verdict: string | null; passed: number | null; total: number | null; files: SFile[]; synthetic: boolean }
export interface TestResult { name: string; sample: boolean; verdict: string; millis: number; detail: string | null; stdoutExcerpt: string | null; stderrExcerpt: string | null }
export interface Evaluation { status: string; verdict: string | null; passed: number | null; total: number | null; message: string | null; compileLog: string | null; millis: number | null; tests: TestResult[] }
export interface Party { submissionId: number; studentId: number; studentName: string; attempt: number }
export interface Review { decision: string; note: string | null; reviewer: string; at: string }
export interface StructuralFragment { fileA: string; startLineA: number; endLineA: number; fileB: string; startLineB: number; endLineB: number; nodeType: string; nodeCount: number }
export interface PairSummary { id: number; state: string; score: number; fingerprintDice: number; astStructuralScore?: number; matchedTokens: number; distinctiveTokens: number; longestTile: number; a: Party; b: Party; latestReview: Review | null }
export interface RunInfo { id: number; at: string; algorithmVersion: string; config: string; submissions: number; candidatePairs: number; elapsedMs: number; notes: { submissionId: string; insufficientEvidence: boolean; reasons: string[] }[] }
export interface PairList { run: RunInfo | null; pairs: PairSummary[]; disclaimer: string }
export interface Fragment { fileA: string; startLineA: number; endLineA: number; fileB: string; startLineB: number; endLineB: number; tokenCount: number; rarityWeight: number; distinctive: boolean }
export interface PairDetail { summary: PairSummary; weightedDice: number; coverageA: number; coverageB: number; suppressedA: number; suppressedB: number; rarityActive: boolean; explanation: string[]; fragments: Fragment[]; structuralFragments?: StructuralFragment[]; filesA: { id: number; path: string }[]; filesB: { id: number; path: string }[]; reviews: Review[]; disclaimer: string }
export interface InstructorDashboard { courses: number; publishedAssignments: number; submissions: number; pendingEvaluations: number; pairsNeedingReview: number; assignments: { assignmentId: number; courseId: number; courseCode: string; title: string; deadline: string; published: boolean; submissions: number; pending: number; reviewPairs: number; toMark: number; kind: string }[] }
export interface StudentDashboard { upcoming: { assignmentId: number; courseId: number; courseCode: string; title: string; deadline: string; submitted: boolean }[]; recent: { submissionId: number; assignmentId: number; assignmentTitle: string; attempt: number; status: string; verdict: string | null; createdAt: string }[] }

export interface Analytics { enrolled: number; submitted: number; pending: number; averageAttempts: number; verdicts: Record<string, number>; scoreBuckets: { label: string; count: number }[]; tests: { name: string; sample: boolean; passed: number; total: number }[]; medianMillis: number | null }
export type CellStatus = 'GRADED' | 'PENDING' | 'IN_PROGRESS' | 'RETURNED' | 'MISSING' | 'NOT_STARTED'
export interface GradeCell { assignmentId: number; verdict: string | null; passed: number; total: number; attempts: number; score: number | null; maxMarks: number; late: boolean; status: CellStatus }
export interface GradeRow { studentId: number; name: string; email: string; cells: GradeCell[]; totalScore: number; totalMax: number; percent: number | null; completed: number; missing: number; late: number }
export interface Gradebook { assignments: { id: number; title: string; kind: 'CODING' | 'QUIZ'; maxMarks: number; deadline: string }[]; rows: GradeRow[] }

export interface Announcement { id: number; courseId: number; title: string; body: string; pinned: boolean; author: string; createdAt: string }
export interface SearchHit { type: 'course' | 'assignment' | 'student'; id: number; title: string; subtitle: string; path: string }

export type QType = 'MCQ' | 'MULTI' | 'TRUE_FALSE' | 'SHORT' | 'LONG' | 'FILL_BLANK' | 'OUTPUT' | 'FILE' | 'CODE'
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type Json = any
export interface Question { id: number | null; ord?: number; type: QType; prompt: string; marks: number; difficulty: string; required: boolean; options: string[]; answer: Json; settings: Json; explanation: string; bankId: number | null }
export interface BankQuestion extends Omit<Question, 'id' | 'required' | 'bankId'> { id: number; subject: string; topic: string; createdAt: string; usedIn: number }
export interface ViewQuestion { id: number; type: QType; prompt: string; marks: number; difficulty: string; required: boolean; options: { index: number; text: string }[]; settings: Json; response: Json; autoScore?: number | null; manualScore?: number | null; feedback?: string; answer?: Json; explanation?: string; judge?: { status: string; result?: Json } }
export interface AttemptView { id: number; assignmentId: number; attempt: number; status: 'IN_PROGRESS' | 'SUBMITTED' | 'GRADED' | 'RETURNED'; late: boolean; startedAt: string; submittedAt: string | null; visible: boolean; maxMarks: number; score: number | null; feedback: string; serverNow: string; endsAt: string | null; privateNote?: string; released?: boolean; studentName?: string; email?: string; questions: ViewQuestion[] }
export interface AttemptRow { id: number; studentId: number; studentName: string; email: string; attempt: number; status: string; startedAt: string; submittedAt: string | null; late: boolean; score: number | null; released: boolean; needsMarking: number }
export interface MyAttempt { id: number; attempt: number; status: string; startedAt: string; submittedAt: string | null; late: boolean; score: number | null; released: boolean }
export interface Staff { userId: number; name: string; email: string | null; role: 'LEAD' | 'CO_INSTRUCTOR' | 'TA' }
export interface Overview { students: number; published: number; drafts: number; averagePct: number | null; completionPct: number | null; needsMarking: number; lateWork: number;
  upcoming: { assignmentId: number; title: string; kind: string; deadline: string; maxMarks: number; handedIn: number | null; myStatus: CellStatus | null }[];
  activity: { kind: string; who: string; what: string; assignmentId: number; detail: string; at: string }[];
  atRisk: { studentId: number; name: string; percent: number | null; missing: number; late: number; reason: string }[]; myPercent: number | null; myCompleted: number; myMissing: number }
export interface CourseAnalytics { enrollment: { date: string; students: number }[]; weekly: { week: string; code: number; quiz: number }[]; distribution: { label: string; count: number }[];
  assignments: { id: number; title: string; kind: string; deadline: string; handedIn: number; enrolled: number; averagePct: number | null; highestPct: number | null; lowestPct: number | null; late: number; needsMarking: number; medianPct: number | null }[];
  hardestQuestions: { questionId: number; assignment: string; prompt: string; type: string; averagePct: number; answers: number }[]; languages: { language: string; submissions: number; accepted: number; avgMillis: number }[] }
export interface CodingGrade { maxMarks: number; autoScore: number | null; overrideScore: number | null; score: number | null; late: boolean; feedback: string; privateNote?: string }
export interface RunTest { name: string; sample: boolean; input: string | null; expected: string | null; actual: string | null; stderr: string | null; verdict: string; millis: number; detail: string | null }
export interface RunResponse { verdict: string; message: string; compileLog: string; passed: number; total: number; millis: number; mode: 'tests' | 'custom'; tests: RunTest[] }
export interface Material { id: number; module: string; title: string; kind: 'LINK' | 'FILE' | 'NOTE'; url: string | null; body: string; fileName: string | null; sizeBytes: number | null; ord: number; author: string; createdAt: string }
export interface ThreadRow { id: number; title: string; author: string; authorIsStaff: boolean; mine: boolean; replies: number; answered: boolean; pinned: boolean; locked: boolean; hidden: boolean; createdAt: string; lastActivity: string }
export interface Post { id: number; author: string; staff: boolean; mine: boolean; body: string; endorsed: boolean; hidden: boolean; createdAt: string }
export interface ThreadView { id: number; courseId: number; title: string; body: string; author: string; authorIsStaff: boolean; mine: boolean; pinned: boolean; locked: boolean; hidden: boolean; createdAt: string; posts: Post[]; canModerate: boolean }
export interface Note { id: number; kind: string; title: string; body: string; link: string; createdAt: string; read: boolean }
export interface CalEvent { kind: 'DUE' | 'OPENS' | 'LATE_CLOSES' | 'COURSE_START' | 'COURSE_END'; at: string; title: string; courseId: number; courseCode: string; assignmentId: number | null; assignmentKind: string | null; done: boolean | null; draft: boolean }
