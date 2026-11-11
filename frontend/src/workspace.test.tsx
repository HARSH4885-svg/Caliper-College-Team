import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { RunResults, tidy } from './components/RunResults'

describe('coding workspace helpers', () => {
  it('tidy only touches whitespace', () => {
    expect(tidy('def f():\n\treturn 1   \n\n\n')).toBe('def f():\n    return 1\n')
    expect(tidy('x = "a\tb"')).toBe('x = "a    b"\n')
  })
  it('shows a per-test table with expected and actual output, and hides hidden test data', async () => {
    const rerun = vi.fn()
    render(<RunResults onRerun={rerun} r={{ verdict: 'WA', message: '', compileLog: '', passed: 1, total: 2, millis: 900, mode: 'tests', tests: [
      { name: 'Sample 1', sample: true, input: '1 2', expected: '3', actual: '3', stderr: '', verdict: 'AC', millis: 300, detail: null },
      { name: 'Sample 2', sample: true, input: '5 5', expected: '10', actual: '9', stderr: '', verdict: 'WA', millis: 310, detail: 'Wrong answer.' },
      { name: 'Hidden', sample: false, input: null, expected: null, actual: null, stderr: null, verdict: 'AC', millis: 290, detail: null }] }} />)
    expect(screen.getByText('1 / 2 tests passed')).toBeInTheDocument()
    expect(screen.getAllByText('hidden')).toHaveLength(2)
    await userEvent.click(screen.getByLabelText('Run Sample 2 again'))
    expect(rerun).toHaveBeenCalledWith(1)
    expect(screen.queryByLabelText('Run Hidden again')).not.toBeInTheDocument()
  })
  it('custom runs show output and errors instead of a verdict table', () => {
    render(<RunResults r={{ verdict: 'OK', message: '', compileLog: '', passed: 0, total: 0, millis: 200, mode: 'custom', tests: [{ name: 'Custom input', sample: true, input: '4', expected: null, actual: '16\n', stderr: '', verdict: 'OK', millis: 200, detail: null }] }} />)
    expect(screen.getByText('Ran successfully')).toBeInTheDocument()
    expect(screen.getByText('16')).toBeInTheDocument()
  })
  it('compile errors show the compiler output', () => {
    render(<RunResults r={{ verdict: 'CE', message: 'Compilation failed.', compileLog: 'main.c:3: error: expected ;', passed: 0, total: 1, millis: 400, mode: 'tests', tests: [] }} />)
    expect(screen.getByLabelText('Compiler output')).toHaveTextContent('expected ;')
  })
  it('shows the internal execution error message', () => {
    const message = 'Execution service temporarily unavailable.'

    render(<RunResults r={{
      verdict: 'IE',
      message,
      compileLog: '',
      passed: 0,
      total: 1,
      millis: 500,
      mode: 'tests',
      tests: []
    }} />)

    expect(screen.getByText(message)).toBeInTheDocument()
  })
})
