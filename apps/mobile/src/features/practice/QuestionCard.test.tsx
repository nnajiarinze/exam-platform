import { fireEvent, render } from '@testing-library/react-native';
import { QuestionCard } from './QuestionCard';

const question = { sessionQuestionId: 'sq1', questionId: 'q1', prompt: 'Who makes Swedish laws?', questionType: 'SINGLE_CHOICE' as const, answerOptions: [{ id: 'a1', text: 'Riksdagen' }, { id: 'a2', text: 'Polisen' }], sequenceNumber: 1, totalQuestionCount: 3 };
it('renders and selects a question option', async () => { const onSelect = jest.fn(); const view = await render(<QuestionCard question={question} selectedIds={[]} onSelect={onSelect} />); fireEvent.press(view.getByText('Riksdagen')); expect(onSelect).toHaveBeenCalledWith('a1'); });
it('shows server answer feedback', async () => { const view = await render(<QuestionCard question={question} selectedIds={['a1']} onSelect={jest.fn()} result={{ correct: true, selectedOptionIds: ['a1'], correctOptionIds: ['a1'], explanation: 'Parliament decides.', optionFeedback: [], sessionProgress: { answered: 1, total: 3 } }} />); expect(view.getByText('Correct')).toBeTruthy(); expect(view.getByText('Parliament decides.')).toBeTruthy(); });
it('does not reveal feedback before submission', async () => {
  const initial = await render(<QuestionCard question={question} selectedIds={['a2']} onSelect={jest.fn()} />);
  expect(initial.queryByText('Not quite')).toBeNull();
});
it('marks backend-reported incorrect feedback', async () => {
  const answered = await render(<QuestionCard question={question} selectedIds={['a2']} onSelect={jest.fn()} result={{ correct: false, selectedOptionIds: ['a2'], correctOptionIds: ['a1'], explanation: 'Riksdagen makes laws.', optionFeedback: [], sessionProgress: { answered: 1, total: 3 } }} />);
  expect(answered.getByText('Not quite')).toBeTruthy();
  expect(answered.getByText('Riksdagen makes laws.')).toBeTruthy();
});
it('renders multiple-choice options as checkboxes without revealing answers', async () => {
  const multiple = { ...question, questionType: 'MULTIPLE_CHOICE' as const };
  const view = await render(<QuestionCard question={multiple} selectedIds={['a1']} onSelect={jest.fn()} />);
  expect(view.getByText('Select all answers that apply.')).toBeTruthy();
  expect(view.queryByText('Correct')).toBeNull();
});
