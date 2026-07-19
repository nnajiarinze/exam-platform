import { render } from '@testing-library/react-native';
import { ProgressList } from './ProgressList';
it('renders topic progress returned by the backend', async () => { const view = await render(<ProgressList topicNames={{ t1: 'Democracy' }} progress={[{ topicId: 't1', questionsAnswered: 3, correctAnswers: 2, accuracyPercentage: 66.67, lastPractisedAt: '2026-07-19T10:00:00Z' }]} />); expect(view.getByText('Democracy')).toBeTruthy(); expect(view.getByText('Questions answered: 3')).toBeTruthy(); });
