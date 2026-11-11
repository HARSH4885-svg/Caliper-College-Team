import { act, render, renderHook, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from './api'
import { CommandPalette, useShortcuts } from './components/CommandPalette'
import { AccountPage } from './pages/AccountPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { useTheme } from './theme'

const setProfile = vi.fn()
vi.mock('./auth', () => ({ useAuth: () => ({ me: { id: 1, email: 'p@x.test', displayName: 'Prof Example', role: 'INSTRUCTOR' }, logout: vi.fn(), setProfile }) }))

const Where = () => <span data-testid="where">{useLocation().pathname}</span>

describe('command palette', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('lists commands, filters them, runs the highlighted one with Enter and closes', async () => {
    const onClose = vi.fn()
    render(<MemoryRouter><Where /><CommandPalette open onClose={onClose} onHelp={vi.fn()} /></MemoryRouter>)
    expect(screen.getByRole('dialog', { name: 'Search and commands' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: /Home/ })).toBeInTheDocument()
    await userEvent.type(screen.getByRole('combobox'), 'cours')
    expect(screen.queryByRole('option', { name: /Home/ })).not.toBeInTheDocument()
    await userEvent.keyboard('{Enter}')
    expect(onClose).toHaveBeenCalled()
    expect(screen.getByTestId('where')).toHaveTextContent('/courses')
  })

  it('searches the server after two characters and opens a result', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValue([{ type: 'assignment', id: 7, title: 'Array statistics', subtitle: 'Assignment · DEMO101', path: '/assignments/7' }])
    render(<MemoryRouter><Where /><CommandPalette open onClose={vi.fn()} onHelp={vi.fn()} /></MemoryRouter>)
    await userEvent.type(screen.getByRole('combobox'), 'a')
    expect(get).not.toHaveBeenCalled()
    await userEvent.type(screen.getByRole('combobox'), 'r')
    await waitFor(() => expect(get).toHaveBeenCalledWith('/api/search?q=ar'))
    await userEvent.click(await screen.findByRole('option', { name: /Array statistics/ }))
    expect(screen.getByTestId('where')).toHaveTextContent('/assignments/7')
  })

  it('moves with the arrow keys and closes on Escape', async () => {
    const onClose = vi.fn()
    render(<MemoryRouter><CommandPalette open onClose={onClose} onHelp={vi.fn()} /></MemoryRouter>)
    const opts = () => screen.getAllByRole('option')
    expect(opts()[0]).toHaveAttribute('aria-selected', 'true')
    await userEvent.keyboard('{ArrowDown}')
    expect(opts()[1]).toHaveAttribute('aria-selected', 'true')
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalled()
  })

  it('renders nothing while closed', () => {
    render(<MemoryRouter><CommandPalette open={false} onClose={vi.fn()} onHelp={vi.fn()} /></MemoryRouter>)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('keyboard shortcuts', () => {
  function Host({ palette, help }: { palette: () => void; help: () => void }) {
    useShortcuts({ palette, help })
    return <><input aria-label="field" /><Where /></>
  }
  const mount = (palette = vi.fn(), help = vi.fn()) => { render(<MemoryRouter><Routes><Route path="*" element={<Host palette={palette} help={help} />} /></Routes></MemoryRouter>); return { palette, help } }

  it('Ctrl+K and "/" open the palette, "?" opens help', async () => {
    const { palette, help } = mount()
    await userEvent.keyboard('{Control>}k{/Control}')
    await userEvent.keyboard('/')
    await userEvent.keyboard('?')
    expect(palette).toHaveBeenCalledTimes(2)
    expect(help).toHaveBeenCalledTimes(1)
  })

  it('"g c" navigates and letter shortcuts are ignored while typing', async () => {
    const { palette } = mount()
    await userEvent.keyboard('gc')
    expect(screen.getByTestId('where')).toHaveTextContent('/courses')
    await userEvent.click(screen.getByLabelText('field'))
    await userEvent.keyboard('/')
    expect(palette).not.toHaveBeenCalled()
    await userEvent.keyboard('{Control>}k{/Control}')
    expect(palette).toHaveBeenCalledTimes(1)
  })
})

describe('account page', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('refuses mismatched new passwords without calling the server', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue(null)
    render(<MemoryRouter><AccountPage /></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Current password'), 'old-password-1')
    await userEvent.type(screen.getByLabelText('New password'), 'new-password-1')
    await userEvent.type(screen.getByLabelText('New password again'), 'different-pass-1')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('do not match')
    expect(post).not.toHaveBeenCalled()
  })

  it('sends the current and new password, then clears the form', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue(null)
    render(<MemoryRouter><AccountPage /></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Current password'), 'old-password-1')
    await userEvent.type(screen.getByLabelText('New password'), 'new-password-1')
    await userEvent.type(screen.getByLabelText('New password again'), 'new-password-1')
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/auth/password', { currentPassword: 'old-password-1', newPassword: 'new-password-1' }))
    expect(await screen.findByText(/Password changed/)).toBeInTheDocument()
    expect(screen.getByLabelText('Current password')).toHaveValue('')
  })

  it('saves a new display name and hands it to the session', async () => {
    vi.spyOn(api, 'patch').mockResolvedValue({ id: 1, email: 'p@x.test', displayName: 'Prof Renamed', role: 'INSTRUCTOR' })
    render(<MemoryRouter><AccountPage /></MemoryRouter>)
    expect(screen.getByRole('button', { name: 'Save name' })).toBeDisabled()
    const field = screen.getByLabelText('Name')
    await userEvent.clear(field); await userEvent.type(field, 'Prof Renamed')
    await userEvent.click(screen.getByRole('button', { name: 'Save name' }))
    await waitFor(() => expect(setProfile).toHaveBeenCalledWith(expect.objectContaining({ displayName: 'Prof Renamed' })))
  })
})

describe('not found page and theme store', () => {
  it('names the missing address instead of silently redirecting', () => {
    render(<MemoryRouter initialEntries={['/nope/123']}><NotFoundPage /></MemoryRouter>)
    expect(screen.getByText('/nope/123')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Back to Home/ })).toHaveAttribute('href', '/')
  })

  it('theme preference is shared, persisted, and "system" clears the saved choice', () => {
    const a = renderHook(() => useTheme()), b = renderHook(() => useTheme())
    act(() => a.result.current.setPreference('dark'))
    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(b.result.current.theme).toBe('dark')
    expect(localStorage.getItem('cs-theme')).toBe('dark')
    act(() => b.result.current.toggle())
    expect(a.result.current.theme).toBe('light')
    act(() => a.result.current.setPreference('system'))
    expect(localStorage.getItem('cs-theme')).toBeNull()
    expect(a.result.current.preference).toBe('system')
  })
})
