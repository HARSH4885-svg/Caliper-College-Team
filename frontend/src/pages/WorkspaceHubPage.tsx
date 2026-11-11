import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { api, type StudentDashboard } from '../api'
import { useAuth } from '../auth'
import { Icon } from '../components/brand'
import { dueIn, Empty, ErrorBox, Loading, PageHead, VerdictBadge } from '../components/ui'
import { when } from '../labels'
import { useFetch } from '../useFetch'

export function WorkspaceHubPage() {
  const { me } = useAuth()
  const dash = useFetch(() => api.get<StudentDashboard>('/api/dashboard/student'), [])

  const data = dash.data

  // Inspect localStorage for any saved in-browser drafts
  const activeDraft = useMemo(() => {
    if (!me || !data) return null
    for (const item of data.upcoming) {
      try {
        const saved = localStorage.getItem(`cs-draft-${item.assignmentId}-${me.id}`)
        if (saved && saved.trim().length > 0) {
          return {
            assignmentId: item.assignmentId,
            courseCode: item.courseCode,
            title: item.title,
            deadline: item.deadline,
          }
        }
      } catch {
        /* storage read unavailable */
      }
    }
    // Fall back to the earliest pending assignment
    const firstUnsubmitted = data.upcoming.find(u => !u.submitted)
    if (firstUnsubmitted) {
      return {
        assignmentId: firstUnsubmitted.assignmentId,
        courseCode: firstUnsubmitted.courseCode,
        title: firstUnsubmitted.title,
        deadline: firstUnsubmitted.deadline,
      }
    }
    return null
  }, [me, data])

  if (dash.loading) return <Loading />
  if (!data) return <ErrorBox message={dash.error} />

  const upcomingList = data.upcoming ?? []
  const recentSubmissions = data.recent ?? []

  return (
    <>
      <PageHead
        title="Coding Workspace"
        sub="Your interactive development environment. Write, run tests in an isolated sandbox, and submit assignments."
      />

      {activeDraft && (
        <div
          className="card"
          style={{
            background: 'linear-gradient(135deg, rgba(27, 79, 224, 0.08) 0%, rgba(10, 31, 60, 0.03) 100%)',
            border: '1px solid rgba(27, 79, 224, 0.28)',
            padding: '24px 28px',
            marginBottom: 32,
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 20,
          }}
        >
          <div>
            <div className="row" style={{ gap: 8, alignItems: 'center' }}>
              <span className="badge t-ok">Active Workspace Task</span>
              <span className="code" style={{ fontWeight: 700 }}>
                {activeDraft.courseCode}
              </span>
            </div>
            <h2 style={{ margin: '8px 0 4px', fontSize: '1.35rem' }}>{activeDraft.title}</h2>
            <p className="small muted" style={{ margin: 0 }}>
              Deadline: {when(activeDraft.deadline ? activeDraft.deadline.toString() : new Date().toISOString())} / Draft saved in this browser.
            </p>
          </div>

          <Link
            to={`/assignments/${activeDraft.assignmentId}/code`}
            className="btn primary"
            style={{ padding: '10px 24px', fontSize: '0.98rem' }}
          >
            <Icon name="code" size={18} />
            Continue coding in workspace →
          </Link>
        </div>
      )}

      <div className="home" style={{ alignItems: 'start' }}>
        <div>
          <section aria-labelledby="upcoming-work">
            <h2 id="upcoming-work">Pending Coding Assignments</h2>
            {upcomingList.length === 0 ? (
              <Empty icon="task">No pending assignments at this time. All caught up!</Empty>
            ) : (
              <div className="desk">
                {upcomingList.map(u => {
                  const d = dueIn(u.deadline ? u.deadline.toString() : new Date().toISOString())
                  return (
                    <div key={u.assignmentId} className="pairrow" style={{ alignItems: 'center' }}>
                      <div>
                        <div className="row" style={{ gap: 8, alignItems: 'center' }}>
                          <span className="code" style={{ fontWeight: 700, color: 'var(--text-secondary)' }}>
                            {u.courseCode}
                          </span>
                          <Link
                            to={`/assignments/${u.assignmentId}`}
                            style={{ fontWeight: 600, fontSize: '1.02rem', color: 'inherit' }}
                          >
                            {u.title}
                          </Link>
                        </div>
                        <div className="row" style={{ marginTop: 6, gap: 10 }}>
                          <span className={`due${d.soon ? ' soon' : ''}${d.late ? ' late' : ''}`}>
                            <Icon name={u.submitted ? 'check' : 'clock'} size={14} />
                            <span>{u.submitted ? 'Submitted' : d.text}</span>
                          </span>
                        </div>
                      </div>

                      <div className="row" style={{ gap: 10, alignItems: 'center' }}>
                        <Link
                          to={`/assignments/${u.assignmentId}/code`}
                          className={`btn${u.submitted ? '' : ' primary'}`}
                        >
                          <Icon name="code" size={16} />
                          {u.submitted ? 'Open workspace' : 'Start coding'}
                        </Link>
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </section>
        </div>

        <aside className="panel" aria-label="Recent workspace runs">
          <div className="flagrule" />
          <h2><Icon name="clock" />Recent Test Results</h2>
          {recentSubmissions.length === 0 ? (
            <p className="muted small">No recent evaluations. Open an assignment to run your code against test cases.</p>
          ) : (
            <ul>
              {recentSubmissions.map(r => (
                <li key={r.submissionId}>
                  <Link to={`/submissions/${r.submissionId}`}>{r.assignmentTitle}</Link>
                  <div className="due" style={{ justifyContent: 'space-between', marginTop: 4 }}>
                    <span className="small muted">Attempt #{r.attempt} / {when(r.createdAt ? r.createdAt.toString() : new Date().toISOString())}</span>
                    <VerdictBadge verdict={r.verdict} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </aside>
      </div>
    </>
  )
}
