import { useState, type FormEvent } from 'react'
import { useAuth } from '../auth'
import { FlagBanner, Icon, Wordmark } from '../components/brand'
import { ErrorBox, Field } from '../components/ui'

const DEMO = import.meta.env.VITE_DEMO === 'true'

export function LoginPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState(''), [password, setPassword] = useState(''), [name, setName] = useState(''), [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault(); setBusy(true); setError(null)
    try {
      if (mode === 'login') await login(email, password)
      else await register({ email, displayName: name, password, enrollmentCode: code })
    } catch (err) { setError(err instanceof Error ? err.message : 'Failed') } finally { setBusy(false) }
  }

  return (
    <div className="landing">
      <section className="brandpanel" aria-label="About Caliper">
        <div className="mosaic" aria-hidden="true">{Array.from({ length: 16 }, (_, i) => <FlagBanner key={i} seed={i * 7 + 3} />)}</div>
        <Wordmark light />
        <div>
          <h1>Every submission, <em>read fairly.</em></h1>
          <p className="lede">Sandboxed grading for programming assignments, and a similarity desk that shows its evidence and leaves the decision to a person.</p>
          <ul className="promises">
            <li><Icon name="lock" />Hidden tests stay hidden from students.</li>
            <li><Icon name="swap" />Similarity is checked within one assignment, with the matching code side by side.</li>
            <li><Icon name="shield" />No automatic penalties. A match is a reason to look, never a verdict.</li>
          </ul>
        </div>
        <p className="small" style={{ color: '#9fb2d9', margin: 0 }}>Runs locally. Student code only ever runs inside an isolated container.</p>
      </section>
      <section className="formpanel">
        <div className="formbox">
          <h2>{mode === 'login' ? 'Welcome back' : 'Join your class'}</h2>
          <p className="muted">{mode === 'login' ? 'Sign in to your classes.' : 'Your instructor gives you the enrollment code.'}</p>
          <div className="seg" role="tablist" aria-label="Account">
            <button role="tab" aria-selected={mode === 'login'} onClick={() => setMode('login')}>Sign in</button>
            <button role="tab" aria-selected={mode === 'register'} onClick={() => setMode('register')}>Student sign-up</button>
          </div>
          <ErrorBox message={error} />
          <form onSubmit={submit}>
            {mode === 'register' && <Field id="name" label="Full name"><input id="name" value={name} onChange={e => setName(e.target.value)} required maxLength={100} autoComplete="name" /></Field>}
            <Field id="email" label="Email"><input id="email" type="email" value={email} onChange={e => setEmail(e.target.value)} required autoComplete="username" /></Field>
            <Field id="password" label="Password" hint={mode === 'register' ? 'At least 8 characters.' : undefined}>
              <input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} required minLength={mode === 'register' ? 8 : 1} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} /></Field>
            {mode === 'register' && <Field id="code" label="Course enrollment code" hint="Instructor accounts are created by the administrator."><input id="code" value={code} onChange={e => setCode(e.target.value)} required /></Field>}
            <button className="primary" type="submit" disabled={busy} style={{ width: '100%' }}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
          </form>
          {DEMO && mode === 'login' && (
            <div className="demo">
              <p className="small muted">Demo data is synthetic. Fill in a demo account:</p>
              <div className="row">
                <button type="button" className="quiet" onClick={() => { setEmail('demo-instructor@demo.invalid'); setPassword('demo-password-1') }}>Instructor</button>
                <button type="button" className="quiet" onClick={() => { setEmail('student1@demo.invalid'); setPassword('demo-password-1') }}>Student</button>
              </div>
            </div>)}
        </div>
      </section>
    </div>
  )
}
