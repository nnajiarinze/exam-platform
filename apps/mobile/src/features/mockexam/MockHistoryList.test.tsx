import { fireEvent, render } from '@testing-library/react-native';
import { MockHistoryList } from './MockHistoryList';
it('renders and opens historical results', async () => { const onSelect = jest.fn(); const view = await render(<MockHistoryList onSelect={onSelect} attempts={[{ attemptId: 'a1', name: 'Mock', status: 'SUBMITTED', startedAt: '2026-01-01T00:00:00Z', durationSeconds: 120, score: 8, percentage: 80, passed: true, totalQuestions: 10 }]} />); fireEvent.press(view.getByText('Mock')); expect(onSelect).toHaveBeenCalledWith('a1'); });
