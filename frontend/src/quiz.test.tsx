import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type { ViewQuestion } from './api'
import { blankQuestion, blanksIn, problems } from './components/QuestionEditor'
import { answered, QuizRunner } from './components/QuizRunner'
import { fmtMarks } from './labels'

const vq = (o: Partial<ViewQuestion>): ViewQuestion => ({ id: 1, type: 'MCQ', prompt: 'Q', marks: 2, difficulty: 'EASY', required: true, options: [], settings: {}, response: {}, ...o })

describe('question authoring checks', () => {
  it('flags the problems the server would reject', () => {
    const mcq = blankQuestion('MCQ')
    expect(problems(mcq)).toEqual(expect.arrayContaining(['Write the question.', 'Fill in or remove empty options.']))
    expect(problems({ ...mcq, prompt: 'x', options: ['a', 'b'], answer: { correct: [1] } })).toEqual([])
    expect(problems({ ...blankQuestion('MULTI'), prompt: 'x', options: ['a', 'b'] })).toContain('Mark the correct option.')
    const fb = { ...blankQuestion('FILL_BLANK'), prompt: 'no holes' }
    expect(problems(fb)).toContain('Write ___ where each blank goes.')
    expect(blanksIn('a ___ b ___ c')).toBe(2)
    expect(problems({ ...blankQuestion('OUTPUT'), settings: { code: '' } })).toContain('Add the code.')
  })
  it('formats marks without noise', () => {
    expect(fmtMarks(5)).toBe('5'); expect(fmtMarks(2.5)).toBe('2.5'); expect(fmtMarks(1.25)).toBe('1.25'); expect(fmtMarks(null)).toBe('–')
  })
})

describe('quiz runner', () => {
  it('knows when a question is answered', () => {
    expect(answered(vq({ type: 'MCQ' }), { choice: 0 })).toBe(true)
    expect(answered(vq({ type: 'MULTI' }), { choices: [] })).toBe(false)
    expect(answered(vq({ type: 'TRUE_FALSE' }), { value: false })).toBe(true)
    expect(answered(vq({ type: 'FILL_BLANK' }), { blanks: ['', ' '] })).toBe(false)
    expect(answered(vq({ type: 'SHORT' }), { text: '  ' })).toBe(false)
  })
  it('stores the original option index even when options are shuffled', async () => {
    const onAnswer = vi.fn()
    render(<QuizRunner questions={[vq({ options: [{ index: 2, text: 'C' }, { index: 0, text: 'A' }] })]} responses={{}} onAnswer={onAnswer} />)
    await userEvent.click(screen.getByLabelText('A'))
    expect(onAnswer).toHaveBeenCalledWith(1, { choice: 0 })
  })
  it('renders inline blanks and keeps the other blanks when one changes', async () => {
    const onAnswer = vi.fn()
    render(<QuizRunner questions={[vq({ type: 'FILL_BLANK', prompt: 'x ___ y ___' })]} responses={{ 1: { blanks: ['a', 'b'] } }} onAnswer={onAnswer} />)
    await userEvent.type(screen.getByLabelText('Blank 2 of question 1'), 'c')
    expect(onAnswer).toHaveBeenLastCalledWith(1, { blanks: ['a', 'bc'] })
  })
  it('in review shows marks, the key and comments, and never lets the student edit', () => {
    render(<QuizRunner readOnly review questions={[vq({ options: [{ index: 0, text: 'A' }, { index: 1, text: 'B' }], response: { choice: 1 }, autoScore: 0, answer: { correct: [0] }, feedback: 'Re-read chapter 2', explanation: 'Because' })]} responses={{ 1: { choice: 1 } }} />)
    expect(screen.getByText('0 / 2')).toBeInTheDocument()
    expect(screen.getByText('Re-read chapter 2')).toBeInTheDocument()
    expect(screen.getByLabelText('A')).toBeDisabled()
    expect(screen.getByText('B').closest('label')).toHaveClass('wrong')
    expect(screen.getByText('A').closest('label')).toHaveClass('right')
  })
  it('shows "waiting for marks" for hand-marked answers', () => {
    render(<QuizRunner readOnly review questions={[vq({ type: 'LONG', marks: 10 })]} responses={{ 1: { text: 'essay' } }} />)
    expect(screen.getByText(/Waiting for marks/)).toBeInTheDocument()
  })
})
