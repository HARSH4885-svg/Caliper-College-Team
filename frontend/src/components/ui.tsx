import { useState, type ReactNode } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth'
import { DECISION_LABEL, STATE_LABEL, STATE_TONE, VERDICT_LABEL, VERDICT_TONE } from '../labels'
import { useTheme } from '../theme'
import { CommandPalette, ShortcutsDialog, useShortcuts } from './CommandPalette'
import { NotificationBell } from './NotificationBell'
import { Avatar, Icon, Level, Pennant, Wordmark, type IconName } from './brand'

export function Layout() {
  const { me, logout } = useAuth()
  const { theme, toggle: toggleTheme } = useTheme()
  const wide = /^\/assignments\/\d+\/code/.test(useLocation().pathname)
  const [palette, setPalette] = useState(false)
  const [help, setHelp] = useState(false)
  useShortcuts({ palette: () => setPalette(true), help: () => setHelp(true) })
  if (!me) return null
  const inst = me.role === 'INSTRUCTOR'
  const items: { to: string; label: string; icon: IconName; end?: boolean }[] = inst
    ? [
        { to: '/', label: 'Dashboard', icon: 'home', end: true },
        { to: '/courses', label: 'Courses', icon: 'classes' },
        { to: '/reviews', label: 'Similarity Review', icon: 'eye' as IconName },
        { to: '/bank', label: 'Question bank', icon: 'archive' as IconName },
        { to: '/calendar', label: 'Calendar', icon: 'calendar' },
      ]
    : [
        { to: '/', label: 'Dashboard', icon: 'home', end: true },
        { to: '/workspace', label: 'Coding Workspace', icon: 'code' as IconName },
        { to: '/courses', label: 'My classes', icon: 'classes' },
        { to: '/calendar', label: 'Calendar', icon: 'calendar' },
      ]
  return (
    <div className="shell">
      <a className="skip" href="#main">Skip to content</a>
      <header className="topbar">
        <Link to="/" className="brandlink" aria-label="Caliper home"><Wordmark light /></Link>
        <nav className="topnav" aria-label="Main">
          {items.map(i => <NavLink key={i.to} to={i.to} end={i.end}>{i.label}</NavLink>)}
        </nav>
        <div className="account">
          <button className="ghost on-dark searchbtn" onClick={() => setPalette(true)} aria-label="Search and commands (Ctrl K)"><Icon name="search" /><span className="sr-hide">Search</span><kbd>Ctrl K</kbd></button>
          <Link to="/account" className="who" aria-label="Account and preferences"><span className="who-name">{me.displayName}</span><span className="who-role">{inst ? 'Instructor' : 'Student'}</span></Link>
          <NotificationBell />
          <button className="ghost on-dark iconbtn" onClick={toggleTheme} aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}><Icon name={theme === 'dark' ? 'sun' : 'moon'} /></button>
          <Link to="/account" className="avatarlink" tabIndex={-1} aria-hidden="true"><Avatar name={me.displayName} size={34} /></Link>
          <button className="ghost on-dark" onClick={logout} aria-label="Sign out"><Icon name="logout" /><span className="sr-hide">Sign out</span></button>
        </div>
      </header>
      <main id="main" className={`main${wide ? ' wide' : ''}`}><Outlet /></main>
      <CommandPalette open={palette} onClose={() => setPalette(false)} onHelp={() => setHelp(true)} />
      <ShortcutsDialog open={help} onClose={() => setHelp(false)} />
      <nav className="tabbar" aria-label="Main (mobile)">
        {items.map(i => <NavLink key={i.to} to={i.to} end={i.end}><Icon name={i.icon} size={22} /><span>{i.label}</span></NavLink>)}
        <button onClick={() => setPalette(true)}><Icon name="search" size={22} /><span>Search</span></button>
        <NavLink to="/account"><Icon name="user" size={22} /><span>Account</span></NavLink>
        <button onClick={logout}><Icon name="logout" size={22} /><span>Sign out</span></button>
      </nav>
    </div>
  )
}

export const Spinner = () => <span className="spinner" role="status" aria-label="Loading" />
export const ErrorBox = ({ message }: { message: string | null }) => message ? <div className="err" role="alert"><Icon name="info" />{message}</div> : null
export const Notice = ({ children, role }: { children: ReactNode; role?: 'status' }) => <div className="notice" role={role}><Icon name="shield" />{children}</div>
export const Empty = ({ children, icon = 'flag' }: { children: ReactNode; icon?: IconName }) => <div className="empty"><Icon name={icon} size={26} /><p>{children}</p></div>
export const Loading = () => <div className="loading" role="status"><span className="skel w40" /><span className="skel w70" /><span className="skel w55" /><span className="sr-only">Loading…</span></div>
export const Synthetic = () => <span className="badge synthetic" title="Generated demo data, not real students">synthetic</span>

export function PageHead({ title, sub, back, actions }: { title: ReactNode; sub?: ReactNode; back?: { to: string; label: string }; actions?: ReactNode }) {
  return (
    <div className="pagehead">
      {back && <Link className="back" to={back.to}><Icon name="left" size={16} />{back.label}</Link>}
      <div className="pagehead-row"><div><h1>{title}</h1>{sub && <p className="sub">{sub}</p>}</div>{actions && <div className="row">{actions}</div>}</div>
    </div>
  )
}

export function VerdictBadge({ verdict }: { verdict: string | null }) {
  if (!verdict) return <span className="badge t-muted">Pending</span>
  const tone = VERDICT_TONE[verdict] ?? 'muted'
  return <span className={`badge verdict t-${tone}`} title="Evaluation verdict"><Pennant code={verdict} tone={tone} /><span>{VERDICT_LABEL[verdict] ?? verdict}</span></span>
}

const LEVEL: Record<string, 0 | 1 | 2 | 3> = { NO_MEANINGFUL_MATCH: 0, LOW_SIMILARITY: 1, INSUFFICIENT_EVIDENCE: 0, NEEDS_REVIEW: 2, HIGH_PRIORITY_REVIEW: 3 }
/** Investigation state: deliberately never red, and never worded as a finding. */
export function StateBadge({ state }: { state: string }) {
  return <span className={`badge s-${STATE_TONE[state] ?? 'quiet'}`} title="Investigation state, not a verdict"><Level lit={LEVEL[state] ?? 0} dashed={state === 'INSUFFICIENT_EVIDENCE'} /><span>{STATE_LABEL[state] ?? state}</span></span>
}

export const DecisionBadge = ({ decision }: { decision: string }) => <span className="badge t-muted">Instructor: {DECISION_LABEL[decision] ?? decision}</span>

export function Field({ label, hint, children, id }: { label: string; hint?: string; children: ReactNode; id: string }) {
  return <div className="field"><label htmlFor={id}>{label}</label>{children}{hint && <div className="hint">{hint}</div>}</div>
}

/** Accessible tab strip (roving selection via buttons; panels are rendered by the caller). */
export function Tabs({ tabs, value, onChange, label }: { tabs: { id: string; label: string; count?: number }[]; value: string; onChange: (id: string) => void; label: string }) {
  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {tabs.map(t => <button key={t.id} role="tab" id={`tab-${t.id}`} aria-selected={value === t.id} aria-controls={`panel-${t.id}`} onClick={() => onChange(t.id)}>{t.label}{t.count != null && <span className="count">{t.count}</span>}</button>)}
    </div>
  )
}

export const dueIn = (iso: string) => {
  const ms = new Date(iso).getTime() - Date.now(), d = Math.floor(Math.abs(ms) / 86_400_000), h = Math.floor(Math.abs(ms) / 3_600_000)
  if (ms < 0) return { text: d >= 1 ? `Closed ${d}d ago` : 'Closed', late: true, soon: false }
  if (h < 24) return { text: h < 1 ? 'Due within the hour' : `Due in ${h}h`, late: false, soon: true }
  return { text: `Due in ${d} day${d === 1 ? '' : 's'}`, late: false, soon: d <= 2 }
}
