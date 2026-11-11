import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { InstructorOnly } from './App'
import { api, ApiError, tokenStore, type Fragment } from './api'
import { AuthProvider, useAuth } from './auth'
import { StateBadge, VerdictBadge } from './components/ui'
import { DECISION_LABEL, STATE_LABEL, STATE_TONE, VERDICT_LABEL, pct } from './labels'
import { filesWithMatches, lineMarks } from './lineMarks'
import { LoginPage } from './pages/LoginPage'

const frag = (o: Partial<Fragment>): Fragment => ({ fileA: 'a.py', startLineA: 1, endLineA: 3, fileB: 'b.py', startLineB: 10, endLineB: 12, tokenCount: 20, rarityWeight: 1, distinctive: true, ...o })

describe('similarity wording and colours (spec section 7 / 32)', () => {
  const forbidden = /guilty|cheat|plagiari[sz]ed|plagiarism confirmed|copied|proof/i
  it('no state, decision or verdict label accuses anyone', () => {
    for (const l of [...Object.values(STATE_LABEL), ...Object.values(DECISION_LABEL), ...Object.values(VERDICT_LABEL)]) expect(l).not.toMatch(forbidden)
  })
  it('investigation states never use the red "bad" tone and are visually distinct from verdict tones', () => {
    for (const s of Object.keys(STATE_LABEL)) expect(['review', 'priority', 'quiet']).toContain(STATE_TONE[s])
    const { container } = render(<StateBadge state="HIGH_PRIORITY_REVIEW" />)
    expect(container.querySelector('.t-bad')).toBeNull()
    expect(container.firstChild).toHaveClass('s-priority')
    expect(screen.getByText('High-priority review').closest('.badge')).toHaveAttribute('title', expect.stringContaining('not a verdict'))
  })
  it('verdict badge renders neutral pending state and labelled verdicts', () => {
    const { rerender } = render(<VerdictBadge verdict={null} />)
    expect(screen.getByText('Pending')).toBeInTheDocument()
    rerender(<VerdictBadge verdict="WA" />)
    expect(screen.getByText('Wrong answer')).toBeInTheDocument()
  })
  it('formats percentages', () => expect(pct(0.7834)).toBe('78.3%'))
})

describe('side-by-side line marks', () => {
  it('marks lines per side and file, and respects overlaps', () => {
    const fr = [frag({}), frag({ startLineA: 3, endLineA: 4, startLineB: 20, endLineB: 21 }), frag({ fileA: 'other.py', startLineA: 50, endLineA: 50 })]
    const a = lineMarks(fr, 'A', 'a.py')
    expect([...a.keys()]).toEqual([1, 2, 3, 4])
    expect(a.get(3)).toEqual([0, 1])
    expect([...lineMarks(fr, 'B', 'b.py').keys()]).toEqual([10, 11, 12, 20, 21])
    expect(lineMarks(fr, 'A', 'missing.py').size).toBe(0)
    expect(filesWithMatches(fr, 'A')).toEqual(['a.py', 'other.py'])
  })
})

describe('api client', () => {
  beforeEach(() => { tokenStore.clear(); vi.restoreAllMocks() })
  it('sends the bearer token and surfaces the backend error body', async () => {
    tokenStore.set('tok')
    const f = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ error: 'Not found' }), { status: 404 }))
    await expect(api.get('/api/x')).rejects.toMatchObject({ status: 404, message: 'Not found' })
    expect((f.mock.calls[0][1] as RequestInit).headers).toMatchObject({ Authorization: 'Bearer tok' })
    expect(new ApiError(1, 'm').message).toBe('m')
  })
})

describe('login page', () => {
  beforeEach(() => { tokenStore.clear(); vi.restoreAllMocks() })
  it('shows the server error for bad credentials and stays on the form', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ error: 'Invalid email or password' }), { status: 401 }))
    render(<AuthProvider><LoginPage /></AuthProvider>)
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.test')
    await userEvent.type(screen.getByLabelText('Password'), 'nope')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(tokenStore.get()).toBeNull()
  })
  it('student sign-up asks for an enrollment code; there is no instructor sign-up', async () => {
    render(<AuthProvider><LoginPage /></AuthProvider>)
    await userEvent.click(screen.getByRole('tab', { name: 'Student sign-up' }))
    expect(screen.getByLabelText('Course enrollment code')).toBeRequired()
    expect(screen.queryByText(/instructor sign-?up/i)).toBeNull()
  })
})

function WaitForAuth({ children }: { children: JSX.Element }) { return useAuth().loading ? null : children } // mirrors App's loading gate

describe('instructor-only routes', () => {
  const setup = async (role: 'STUDENT' | 'INSTRUCTOR') => {
    tokenStore.set('t')
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ id: 1, email: 'x@y.test', displayName: 'X', role }), { status: 200 }))
    render(<AuthProvider><WaitForAuth><MemoryRouter initialEntries={['/secret']}><Routes><Route path="/" element={<p>home</p>} /><Route path="/secret" element={<InstructorOnly><p>pairs</p></InstructorOnly>} /></Routes></MemoryRouter></WaitForAuth></AuthProvider>)
  }
  it('redirects students away from instructor pages (the backend also enforces this)', async () => {
    await setup('STUDENT')
    await waitFor(() => expect(screen.getByText('home')).toBeInTheDocument())
    expect(screen.queryByText('pairs')).toBeNull()
  })
  it('shows instructor pages to instructors', async () => {
    await setup('INSTRUCTOR')
    await waitFor(() => expect(screen.getByText('pairs')).toBeInTheDocument())
  })
})

describe('role-aware navigation & Caliper branding', () => {
  it('renders Caliper brand name and logo on login page', () => {
    render(<AuthProvider><LoginPage /></AuthProvider>)
    expect(screen.getByText(/Cal/)).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'About Caliper' })).toBeInTheDocument()
  })
})
