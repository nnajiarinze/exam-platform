import { fireEvent, render } from '@testing-library/react-native';
import { QuestionCard } from './QuestionCard';

const question = { sessionQuestionId: 'sq1', questionId: 'q1', prompt: 'Who makes Swedish laws?', questionType: 'SINGLE_CHOICE' as const, answerOptions: [{ id: 'a1', text: 'Riksdagen' }, { id: 'a2', text: 'Polisen' }], sequenceNumber: 1, totalQuestionCount: 3 };
it('renders and selects a question option', async () => { const onSelect = jest.fn(); const view = await render(<QuestionCard question={question} onSelect={onSelect} />); fireEvent.press(view.getByText('Riksdagen')); expect(onSelect).toHaveBeenCalledWith('a1'); });
it('shows server answer feedback', async () => { const view = await render(<QuestionCard question={question} selectedId="a1" onSelect={jest.fn()} result={{ correct: true, selectedAnswerOptionId: 'a1', correctAnswerOptionId: 'a1', explanation: 'Parliament decides.', sessionProgress: { answered: 1, total: 3 } }} />); expect(view.getByText('Correct')).toBeTruthy(); expect(view.getByText('Parliament decides.')).toBeTruthy(); });
