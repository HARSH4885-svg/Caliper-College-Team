import { useState, type FormEvent } from 'react'
import { api, type Me } from '../api'
import { useAuth } from '../auth'
import { Avatar, Icon } from '../components/brand'
import { ErrorBox, Field, PageHead } from '../components/ui'
import { useTheme, type ThemePreference } from '../theme'

const THEMES: { id: ThemePreference; label: string; note: string }[] = [
  { id: 'system', label: 'Match my device', note: 'Follows your operating system, including at night.' },
  { id: 'light', label: 'Light', note: 'Paper-white surfaces.' },
  { id: 'dark', label: 'Dark', note: 'Easier on the eyes in a dim room.' },
]

export function AccountPage() {
  const { me, setProfile } = useAuth()
  const { preference, setPreference } = useTheme()
  const [name, setName] = useState(me?.displayName ?? '')
  const [nameMsg, setNameMsg] = useState<string | null>(null)
  const [nameErr, setNameErr] = useState<string | null>(null)
  const [cur, setCur] = useState(''), [next, setNext] = useState(''), [again, setAgain] = useState('')
  const [pwMsg, setPwMsg] = useState<string | null>(null)
  const [pwErr, setPwErr] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  if (!me) return null

  async function saveName(e: FormEvent) {
    e.preventDefault(); setNameErr(null); setNameMsg(null)
    try { setProfile(await api.patch<Me>('/api/auth/me', { displayName: name.trim() })); setNameMsg('Name updated.') } catch (x) { setNameErr((x as Error).message) }
  }
  async function savePassword(e: FormEvent) {
    e.preventDefault(); setPwErr(null); setPwMsg(null)
    if (next !== again) { setPwErr('The new passwords do not match.'); return }
    setBusy(true)
    try { await api.post('/api/auth/password', { currentPassword: cur, newPassword: next }); setCur(''); setNext(''); setAgain(''); setPwMsg('Password changed. Use it the next time you sign in.') }
    catch (x) { setPwErr((x as Error).message) } finally { setBusy(false) }
  }

  return (
    <>
      <PageHead title="Account and preferences" sub="Your name, your password and how Caliper looks for you." />
      <div className="card"><div className="row" style={{ gap: 16 }}><Avatar name={me.displayName} size={56} />
        <div><strong style={{ fontSize: '1.1rem' }}>{me.displayName}</strong><div className="muted small">{me.email} · {me.role === 'INSTRUCTOR' ? 'Instructor' : 'Student'}</div></div></div></div>

      <div className="grid g2" style={{ alignItems: 'start' }}>
        <form className="card" onSubmit={saveName}>
          <h2>Display name</h2>
          <p className="hint" style={{ marginTop: 0 }}>This is the name your {me.role === 'INSTRUCTOR' ? 'students' : 'instructor'} see. Your email and role cannot be changed here.</p>
          <ErrorBox message={nameErr} />{nameMsg && <div className="notice" role="status"><Icon name="check" />{nameMsg}</div>}
          <Field id="dn" label="Name"><input id="dn" value={name} onChange={e => setName(e.target.value)} maxLength={100} required autoComplete="name" /></Field>
          <button className="primary" type="submit" disabled={name.trim() === '' || name.trim() === me.displayName}>Save name</button>
        </form>

        <form className="card" onSubmit={savePassword}>
          <h2>Change password</h2>
          <ErrorBox message={pwErr} />{pwMsg && <div className="notice" role="status"><Icon name="check" />{pwMsg}</div>}
          <Field id="pw0" label="Current password"><input id="pw0" type="password" value={cur} onChange={e => setCur(e.target.value)} required autoComplete="current-password" /></Field>
          <Field id="pw1" label="New password" hint="At least 8 characters."><input id="pw1" type="password" value={next} onChange={e => setNext(e.target.value)} required minLength={8} maxLength={200} autoComplete="new-password" /></Field>
          <Field id="pw2" label="New password again"><input id="pw2" type="password" value={again} onChange={e => setAgain(e.target.value)} required minLength={8} maxLength={200} autoComplete="new-password" /></Field>
          <button className="primary" type="submit" disabled={busy || !cur || next.length < 8}>{busy ? 'Saving…' : 'Change password'}</button>
          <p className="hint">Other devices stay signed in until their session ends.</p>
        </form>
      </div>

      <div className="card"><h2>Appearance</h2>
        <div className="verdictpick" role="radiogroup" aria-label="Theme">
          {THEMES.map(t => <label key={t.id}><input type="radio" name="theme" checked={preference === t.id} onChange={() => setPreference(t.id)} /><span>{t.label}<small>{t.note}</small></span></label>)}
        </div>
      </div>

      <div className="card"><h2>Keyboard</h2>
        <p style={{ marginBottom: 0 }}>Press <kbd>Ctrl</kbd> <kbd>K</kbd> (or <kbd>/</kbd>) anywhere to search courses, assignments{me.role === 'INSTRUCTOR' ? ' and students' : ''}, and press <kbd>?</kbd> for the full list.</p></div>
    </>
  )
}
