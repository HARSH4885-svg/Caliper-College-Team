import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type Course, type InstructorDashboard } from '../api'
import { Icon, Level } from '../components/brand'
import { dueIn, Empty, ErrorBox, Loading, Notice, PageHead } from '../components/ui'
import { when } from '../labels'
import { useFetch } from '../useFetch'

export function SimilarityHubPage() {
  const dash = useFetch(() => api.get<InstructorDashboard>('/api/dashboard/instructor'), [])
  const courses = useFetch(() => api.get<Course[]>('/api/courses'), [])
  const [courseFilter, setCourseFilter] = useState<string>('')
  const [filterMode, setFilterMode] = useState<'all' | 'needs-review' | 'clear'>('all')
  const [search, setSearch] = useState('')

  const data = dash.data
  const codingAssignments = useMemo(() => {
    if (!data) return []
    return data.assignments.filter(a => a.kind === 'CODING' || a.reviewPairs > 0)
  }, [data])

  const filtered = useMemo(() => {
    return codingAssignments.filter(a => {
      if (courseFilter && String(a.courseId) !== courseFilter) return false
      if (filterMode === 'needs-review' && a.reviewPairs === 0) return false
      if (filterMode === 'clear' && a.reviewPairs > 0) return false
      if (search.trim()) {
        const q = search.toLowerCase()
        if (!a.title.toLowerCase().includes(q) && !a.courseCode.toLowerCase().includes(q)) return false
      }
      return true
    })
  }, [codingAssignments, courseFilter, filterMode, search])

  if (dash.loading || courses.loading) return <Loading />
  if (!data) return <ErrorBox message={dash.error} />

  const totalPairs = data.pairsNeedingReview
  const assignmentsNeedingReview = codingAssignments.filter(a => a.reviewPairs > 0).length

  return (
    <>
      <PageHead
        title="Similarity Review Desk"
        sub="Cross-course similarity analysis queue and evidence inspection desk. A match is a reason to look, never an automated verdict."
      />

      <div className="statrow" aria-label="Similarity review statistics">
        <div>
          <b>{totalPairs}</b>
          <span>Candidate pairs for review</span>
        </div>
        <div>
          <b>{assignmentsNeedingReview}</b>
          <span>Assignments with pairs</span>
        </div>
        <div>
          <b>{codingAssignments.length}</b>
          <span>Monitored coding assignments</span>
        </div>
        <div>
          <b>{data.submissions}</b>
          <span>Submissions analyzed</span>
        </div>
      </div>

      <Notice>
        Token-level similarity detection measures shared syntactic structures. All reviews preserve the distinction between an algorithmic comparison signal and an instructor's academic judgement.
      </Notice>

      <div className="toolbar" style={{ marginTop: 24 }}>
        <div className="searchfield">
          <Icon name="search" size={16} />
          <input
            type="search"
            aria-label="Filter assignments by title or course code"
            placeholder="Search assignments or course..."
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </div>

        {courses.data && courses.data.length > 1 && (
          <select
            aria-label="Filter by course"
            value={courseFilter}
            onChange={e => setCourseFilter(e.target.value)}
            style={{ width: 'auto' }}
          >
            <option value="">All courses</option>
            {courses.data.map(c => (
              <option key={c.id} value={c.id}>
                {c.code} — {c.title}
              </option>
            ))}
          </select>
        )}

        <div className="segmented" role="tablist" aria-label="Urgency filter" style={{ width: 320 }}>
          <button
            type="button"
            role="tab"
            aria-selected={filterMode === 'all'}
            className={filterMode === 'all' ? 'on' : ''}
            onClick={() => setFilterMode('all')}
          >
            All ({codingAssignments.length})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={filterMode === 'needs-review'}
            className={filterMode === 'needs-review' ? 'on' : ''}
            onClick={() => setFilterMode('needs-review')}
          >
            Needs Review ({assignmentsNeedingReview})
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={filterMode === 'clear'}
            className={filterMode === 'clear' ? 'on' : ''}
            onClick={() => setFilterMode('clear')}
          >
            Clear ({codingAssignments.length - assignmentsNeedingReview})
          </button>
        </div>
      </div>

      {filtered.length === 0 ? (
        <Empty icon="eye">
          {search || courseFilter || filterMode !== 'all'
            ? 'No assignments match the selected filter.'
            : 'No coding assignments found.'}
        </Empty>
      ) : (
        <div className="desk" style={{ marginTop: 16 }}>
          {filtered.map(a => {
            const due = dueIn(a.deadline ? a.deadline.toString() : new Date().toISOString())
            const hasPairs = a.reviewPairs > 0

            return (
              <div
                key={a.assignmentId}
                className="pairrow"
                style={{
                  alignItems: 'center',
                  background: hasPairs ? 'var(--bg-surface)' : 'transparent',
                }}
              >
                <div>
                  <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                    <span className="code" style={{ fontWeight: 700, color: 'var(--text-secondary)' }}>
                      {a.courseCode}
                    </span>
                    <Link
                      to={`/assignments/${a.assignmentId}/plagiarism`}
                      style={{ fontWeight: 600, fontSize: '1.05rem', color: 'inherit' }}
                    >
                      {a.title}
                    </Link>
                  </div>
                  <div className="row" style={{ marginTop: 6, gap: 12 }}>
                    <span className="small muted">
                      {a.submissions} submission{a.submissions === 1 ? '' : 's'}
                    </span>
                    <span className="small muted">/</span>
                    <span className="small muted">
                      Deadline: {when(a.deadline ? a.deadline.toString() : new Date().toISOString())} ({due.text})
                    </span>
                    {a.pending > 0 && (
                      <span className="badge t-warn">
                        {a.pending} grading in sandbox
                      </span>
                    )}
                  </div>
                </div>

                <div className="row" style={{ gap: 16, alignItems: 'center' }}>
                  {hasPairs ? (
                    <span className="badge s-review" title="Candidate pairs requiring review">
                      <Level lit={3} />
                      <b>{a.reviewPairs} pair{a.reviewPairs === 1 ? '' : 's'} to inspect</b>
                    </span>
                  ) : (
                    <span className="badge s-quiet" title="No candidate pairs meet review threshold">
                      <Level lit={0} />
                      <span>Clear</span>
                    </span>
                  )}

                  <Link
                    to={`/assignments/${a.assignmentId}/plagiarism`}
                    className={`btn${hasPairs ? ' primary' : ''}`}
                    style={{ whiteSpace: 'nowrap' }}
                  >
                    <Icon name="eye" size={16} />
                    {hasPairs ? 'Review pairs' : 'Open report'}
                  </Link>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </>
  )
}
