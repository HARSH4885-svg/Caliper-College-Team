import { Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth'
import { Layout, Loading } from './components/ui'
import { AssignmentPage } from './pages/AssignmentPage'
import { CourseDetailPage, CoursesPage } from './pages/CoursesPages'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { PairPage, PlagiarismListPage } from './pages/PlagiarismPages'
import { AccountPage } from './pages/AccountPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { AttemptPage } from './pages/AttemptPage'
import { BankPage } from './pages/BankPage'
import { WorkspacePage } from './pages/WorkspacePage'
import { CalendarPage } from './pages/CalendarPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { ThreadPage } from './pages/CommunityPanels'
import { SubmissionPage } from './pages/SubmissionPage'
import { SimilarityHubPage } from './pages/SimilarityHubPage'
import { WorkspaceHubPage } from './pages/WorkspaceHubPage'

/** Role gate for the UI only. The backend independently enforces every permission. */
export function InstructorOnly({ children }: { children: JSX.Element }) {
  const { me } = useAuth()
  return me?.role === 'INSTRUCTOR' ? children : <Navigate to="/" replace />
}

export function App() {
  const { me, loading } = useAuth()
  if (loading) return <div className="auth"><Loading /></div>
  if (!me) return <LoginPage />
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="courses" element={<CoursesPage />} />
        <Route path="courses/:id" element={<CourseDetailPage />} />
        <Route path="reviews" element={<InstructorOnly><SimilarityHubPage /></InstructorOnly>} />
        <Route path="similarity" element={<InstructorOnly><SimilarityHubPage /></InstructorOnly>} />
        <Route path="workspace" element={<WorkspaceHubPage />} />
        <Route path="assignments/:id" element={<AssignmentPage />} />
        <Route path="assignments/:id/code" element={<WorkspacePage />} />
        <Route path="assignments/:id/plagiarism" element={<InstructorOnly><PlagiarismListPage /></InstructorOnly>} />
        <Route path="pairs/:id" element={<InstructorOnly><PairPage /></InstructorOnly>} />
        <Route path="submissions/:id" element={<SubmissionPage />} />
        <Route path="account" element={<AccountPage />} />
        <Route path="attempts/:id" element={<AttemptPage />} />
        <Route path="calendar" element={<CalendarPage />} />
        <Route path="notifications" element={<NotificationsPage />} />
        <Route path="threads/:id" element={<ThreadPage />} />
        <Route path="bank" element={<InstructorOnly><BankPage /></InstructorOnly>} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
