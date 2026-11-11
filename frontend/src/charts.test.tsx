import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Histogram, TestBars, VerdictBar } from './components/charts'

describe('insight charts', () => {
  it('verdict bar names every verdict with its count and ignores empty ones', () => {
    render(<VerdictBar verdicts={{ AC: 5, WA: 3, CE: 0 }} pending={2} />)
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', '5 Accepted, 3 Wrong answer')
    expect(screen.getByText('Waiting')).toBeInTheDocument()
    expect(screen.queryByText('Compile error')).not.toBeInTheDocument()
  })

  it('shows an empty message instead of a blank bar', () => {
    render(<VerdictBar verdicts={{ AC: 0 }} pending={0} />)
    expect(screen.getByText('No results yet.')).toBeInTheDocument()
  })

  it('histogram exposes the numbers to assistive technology', () => {
    render(<Histogram buckets={[{ label: '0%', count: 1 }, { label: '100%', count: 4 }]} />)
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', '0%: 1, 100%: 4')
  })

  it('test bars list the hardest test first and flag hidden tests', () => {
    render(<TestBars tests={[{ name: 'easy', sample: true, passed: 9, total: 10 }, { name: 'edge', sample: false, passed: 2, total: 10 }]} />)
    const names = screen.getAllByText(/easy|edge/).map(n => n.textContent)
    expect(names[0]).toContain('edge')
    expect(screen.getByText('hidden')).toBeInTheDocument()
    expect(screen.getByText('20%')).toBeInTheDocument()
  })
})
