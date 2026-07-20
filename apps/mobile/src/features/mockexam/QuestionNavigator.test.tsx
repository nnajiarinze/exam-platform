import { fireEvent, render } from '@testing-library/react-native';
import { QuestionNavigator } from './QuestionNavigator';
it('supports jump navigation and exposes answer state', async () => { const onSelect = jest.fn(); const view = await render(<QuestionNavigator current={1} onSelect={onSelect} questions={[{ attemptQuestionId: 'q1', sequenceNumber: 1, answered: false, flagged: true }, { attemptQuestionId: 'q2', sequenceNumber: 2, answered: true, flagged: false }]} />); fireEvent.press(view.getByLabelText('Question 2, answered')); expect(onSelect).toHaveBeenCalledWith(2); });
