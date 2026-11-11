import { Link } from 'react-router-dom'
import { api, type Course, type InstructorDashboard, type StudentDashboard } from '../api'
import { useAuth } from '../auth'
import { ClassCard } from '../components/ClassCard'
import { Bunting, Icon } from '../components/brand'
import { dueIn, Empty, ErrorBox, Loading, VerdictBadge } from '../components/ui'
import { when } from '../labels'
import { useFetch } from '../useFetch'

const greeting = () => {
  const h = new Date().getHours()
  return h < 5 ? 'Late night' : h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening'
}

const friendlyName = (full: string) => {
  const parts = full.trim().split(/\s+/)
  if (parts.length > 1 && /^(dr|prof|mr|ms|mrs)\.?$/i.test(parts[0])) {
    return parts[1]
  }
  return parts[0].replace(/\.+$/, '')
}

export function DashboardPage() {
  const { me } = useAuth()
  return me?.role === 'INSTRUCTOR' ? <InstructorHome name={me.displayName} /> : <StudentHome name={me?.displayName ?? ''} />
}

function Classes({ courses, instructor }: { courses: Course[]; instructor: boolean }) {
  return courses.length === 0
    ? <Empty icon="classes">{instructor ? 'No classes yet. Create your first one from the Courses page.' : 'You are not in a class yet. Ask your instructor for the course number and enrollment code.'}</Empty>
    : <div className="classes">{courses.map(c => <ClassCard key={c.id} c={c} instructor={instructor} />)}</div>
}

function InstructorHome({ name }: { name: string }) {
  const dash = useFetch(() => api.get<InstructorDashboard>('/api/dashboard/instructor'), [])
  const courses = useFetch(() => api.get<Course[]>('/api/courses'), [])
  if (dash.loading || courses.loading) return <Loading />
  const data = dash.data
  if (!data) return <ErrorBox message={dash.error} />
  const toReview = data.assignments.filter(a => a.reviewPairs > 0)
  const drafts = data.assignments.filter(a => !a.published)
  const toMark = data.assignments.filter(a => a.toMark > 0)

  return (
    <>
      <div className="hello">
        <Bunting />
        <h1>{greeting()}, {friendlyName(name)}.</h1>
        <p className="sub">
          {data.pairsNeedingReview > 0
            ? `${data.pairsNeedingReview} pair${data.pairsNeedingReview === 1 ? '' : 's'} of submissions need similarity review.`
            : 'Evaluation queues are clear and up to date.'}
          {data.pendingEvaluations > 0 && ` ${data.pendingEvaluations} submission${data.pendingEvaluations === 1 ? ' is' : 's are'} in sandbox grading.`}
        </p>
      </div>

      <div className="statrow" aria-label="Overview statistics">
        <div>
          <b>{data.pairsNeedingReview}</b>
          <span>Candidate pairs for review</span>
        </div>
        <div>
          <b>{data.submissions}</b>
          <span>Total student submissions</span>
        </div>
        <div>
          <b>{data.publishedAssignments}</b>
          <span>Active published assignments</span>
        </div>
        <div>
          <b>{courses.data?.length ?? 0}</b>
          <span>Active courses</span>
        </div>
      </div>

      {data.pairsNeedingReview > 0 && (
        <div
          className="card"
          style={{
            background: 'linear-gradient(135deg, rgba(242, 183, 5, 0.09) 0%, rgba(10, 31, 60, 0.04) 100%)',
            border: '1px solid rgba(242, 183, 5, 0.35)',
            padding: '20px 24px',
            marginBottom: 'var(--space-6)',
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 16,
          }}
        >
          <div>
            <div className="row" style={{ gap: 8, alignItems: 'center' }}>
              <span className="badge s-review" style={{ fontWeight: 700 }}>Action Required</span>
              <span className="small muted">Academic Integrity Desk</span>
            </div>
            <h3 style={{ margin: '8px 0 4px', fontSize: '1.2rem' }}>
              {data.pairsNeedingReview} candidate submission pair{data.pairsNeedingReview === 1 ? '' : 's'} require review
            </h3>
            <p className="small muted" style={{ margin: 0 }}>
              Across {toReview.length} active assignment{toReview.length === 1 ? '' : 's'}. Review evidence and compare side-by-side syntax tokens.
            </p>
          </div>
          <Link to="/reviews" className="btn primary" style={{ whiteSpace: 'nowrap' }}>
            <Icon name="eye" size={16} /> Open Similarity Desk →
          </Link>
        </div>
      )}

      <div className="home">
        <section aria-labelledby="yc">
          <h2 id="yc">Your classes</h2>
          <Classes courses={courses.data ?? []} instructor />
        </section>

        <aside className="panel" aria-label="Needs attention">
          <div className="flagrule" />
          <h2><Icon name="eye" />Review desk</h2>
          {toReview.length === 0 ? (
            <p className="muted small">No candidate pairs are waiting. Comparisons can be triggered from any assignment.</p>
          ) : (
            <>
              <ul>
                {toReview.map(a => (
                  <li key={a.assignmentId}>
                    <Link to={`/assignments/${a.assignmentId}/plagiarism`}>{a.title}</Link>
                    <span className="due">
                      <span className="code">{a.courseCode}</span>
                      <span>{a.reviewPairs} pair{a.reviewPairs === 1 ? '' : 's'} to inspect</span>
                    </span>
                  </li>
                ))}
              </ul>
              <Link to="/reviews" className="quiet small" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, marginTop: 10 }}>
                All assignments in Similarity Desk <Icon name="right" size={13} />
              </Link>
            </>
          )}

          {toMark.length > 0 && (
            <>
              <h2 style={{ marginTop: 24 }}><Icon name="edit" />To mark</h2>
              <ul>
                {toMark.map(a => (
                  <li key={a.assignmentId}>
                    <Link to={`/assignments/${a.assignmentId}?tab=attempts`}>{a.title}</Link>
                    <span className="due">
                      <span className="code">{a.courseCode}</span>
                      <span>{a.toMark} attempt{a.toMark === 1 ? '' : 's'} waiting</span>
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}

          {drafts.length > 0 && (
            <>
              <h2 style={{ marginTop: 24 }}><Icon name="task" />Drafts</h2>
              <ul>
                {drafts.map(a => (
                  <li key={a.assignmentId}>
                    <Link to={`/assignments/${a.assignmentId}`}>{a.title}</Link>
                    <span className="due">
                      <span className="code">{a.courseCode}</span>
                      <span>Unpublished</span>
                    </span>
                  </li>
                ))}
              </ul>
            </>
          )}

          <h2 style={{ marginTop: 24 }}><Icon name="clock" />Activity</h2>
          <p className="small muted" style={{ margin: 0 }}>
            {data.submissions} submissions evaluated across {data.publishedAssignments} assignments.
          </p>
        </aside>
      </div>
    </>
  )
}

function StudentHome({ name }: { name: string }) {
  const dash = useFetch(() => api.get<StudentDashboard>('/api/dashboard/student'), [])
  const courses = useFetch(() => api.get<Course[]>('/api/courses'), [])
  if (dash.loading || courses.loading) return <Loading />
  const data = dash.data
  if (!data) return <ErrorBox message={dash.error} />
  const todo = data.upcoming.filter(u => !u.submitted)

  return (
    <>
      <div className="hello">
        <Bunting />
        <h1>{greeting()}, {friendlyName(name)}.</h1>
        <p className="sub">
          {todo.length > 0
            ? `${todo.length} assignment${todo.length === 1 ? '' : 's'} open with upcoming deadlines.`
            : 'All current coursework is submitted.'}
        </p>
      </div>

      {todo.length > 0 && (
        <div
          className="card"
          style={{
            background: 'linear-gradient(135deg, rgba(27, 79, 224, 0.08) 0%, rgba(10, 31, 60, 0.03) 100%)',
            border: '1px solid rgba(27, 79, 224, 0.28)',
            padding: '20px 24px',
            marginBottom: 'var(--space-6)',
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 16,
          }}
        >
          <div>
            <div className="row" style={{ gap: 8, alignItems: 'center' }}>
              <span className="badge t-ok">Next Coding Task</span>
              <span className="code" style={{ fontWeight: 700 }}>{todo[0].courseCode}</span>
            </div>
            <h3 style={{ margin: '8px 0 4px', fontSize: '1.2rem' }}>{todo[0].title}</h3>
            <p className="small muted" style={{ margin: 0 }}>
              Due {dueIn(todo[0].deadline.toString()).text}. Write and run your code in the isolated sandbox.
            </p>
          </div>
          <Link to={`/assignments/${todo[0].assignmentId}/code`} className="btn primary" style={{ whiteSpace: 'nowrap' }}>
            <Icon name="code" size={16} /> Open Workspace →
          </Link>
        </div>
      )}

      <div className="home">
        <div>
          <section aria-labelledby="yc">
            <h2 id="yc">Your classes</h2>
            <Classes courses={courses.data ?? []} instructor={false} />
          </section>

          <section style={{ marginTop: 36 }} aria-labelledby="rs">
            <h2 id="rs">Recent submissions</h2>
            {data.recent.length === 0 ? (
              <Empty icon="upload">You have not submitted any assignments yet. Select a class above to get started.</Empty>
            ) : (
              <div className="card" style={{ padding: '4px 20px' }}>
                {data.recent.map(r => (
                  <div className="attempt" key={r.submissionId}>
                    <div>
                      <Link to={`/submissions/${r.submissionId}`}>{r.assignmentTitle}</Link>
                      <div className="small muted">
                        <span>Attempt #{r.attempt}</span>
                        <span style={{ margin: '0 8px' }}>/</span>
                        <span>{when(r.createdAt)}</span>
                      </div>
                    </div>
                    <VerdictBadge verdict={r.verdict} />
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="panel" aria-label="To-do">
          <div className="flagrule" />
          <h2><Icon name="task" />Deadlines & To-do</h2>
          {data.upcoming.length === 0 ? (
            <p className="muted small">No assignments currently pending.</p>
          ) : (
            <ul>
              {data.upcoming.map(u => {
                const d = dueIn(u.deadline)
                return (
                  <li key={u.assignmentId} style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <div className="row space" style={{ alignItems: 'baseline' }}>
                      <Link to={`/assignments/${u.assignmentId}`} style={{ fontWeight: 600 }}>{u.title}</Link>
                      <Link
                        to={u.submitted ? `/assignments/${u.assignmentId}` : `/assignments/${u.assignmentId}/code`}
                        className="btn quiet"
                        style={{ padding: '2px 8px', fontSize: '0.78rem' }}
                        title={u.submitted ? 'View assignment' : 'Open in Coding Workspace'}
                      >
                        <Icon name="code" size={13} /> {u.submitted ? 'Review' : 'Code'}
                      </Link>
                    </div>
                    <span className={`due${d.soon ? ' soon' : ''}${d.late ? ' late' : ''}`}>
                      <Icon name={u.submitted ? 'check' : 'clock'} size={14} />
                      <span className="code">{u.courseCode}</span>
                      <span>{u.submitted ? 'Submitted' : d.text}</span>
                    </span>
                  </li>
                )
              })}
            </ul>
          )}
        </aside>
      </div>
    </>
  )
}
