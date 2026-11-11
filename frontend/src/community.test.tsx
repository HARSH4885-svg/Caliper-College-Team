import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { api } from './api'
import { NotificationBell } from './components/NotificationBell'

describe('notification bell', () => {
  it('shows the unread count, lists items, and marks one read when opened', async () => {
    const items = [{ id: 7, kind: 'GRADE', title: 'Marks released: Quiz 1', body: '', link: '/attempts/3', createdAt: new Date().toISOString(), read: false }]
    vi.spyOn(api, 'get').mockResolvedValue({ unread: 1, items })
    const post = vi.spyOn(api, 'post').mockResolvedValue(null)
    render(<MemoryRouter><NotificationBell /></MemoryRouter>)
    const bell = await screen.findByRole('button', { name: 'Notifications, 1 unread' })
    await userEvent.click(bell)
    await userEvent.click(await screen.findByText('Marks released: Quiz 1'))
    await waitFor(() => expect(post).toHaveBeenCalledWith('/api/notifications/7/read'))
  })
  it('says when there is nothing new', async () => {
    vi.spyOn(api, 'get').mockResolvedValue({ unread: 0, items: [] })
    render(<MemoryRouter><NotificationBell /></MemoryRouter>)
    await userEvent.click(await screen.findByRole('button', { name: 'Notifications' }))
    expect(await screen.findByText('You are all caught up.')).toBeInTheDocument()
  })
})
