import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { Text } from 'react-native';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Card, ErrorState, Loading, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';

export function MockAnswerReviewScreen({ route }: NativeStackScreenProps<RootStackParamList, 'MockAnswerReview'>) {
  const identity = useAppStore((state) => state.learnerIdentity);
  const query = useQuery({ queryKey: ['mock-results', route.params.attemptId], queryFn: () => learningApi.mockResults(identity, route.params.attemptId) });
  if (query.isPending) return <Screen scroll={false}><Loading label="Loading answer review…" /></Screen>;
  if (query.isError) return <Screen><ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} /></Screen>;
  return <Screen><Title>Answer review</Title><Body>Incorrect and unanswered questions are shown below. Answers can no longer be changed.</Body>
    {query.data.incorrectQuestions.length === 0 ? <Card><Body>Every question was answered correctly.</Body></Card> : query.data.incorrectQuestions.map((question) => <Card key={question.questionId}>
      <Text accessibilityRole="header">{question.prompt}</Text><Text>Status: {question.selectedAnswerText ? 'Incorrect' : 'Unanswered'}</Text>
      <Text>Your answer: {question.selectedAnswerText ?? 'No answer'}</Text><Text>Correct answer: {question.correctAnswerText}</Text><Body>{question.explanation}</Body>
    </Card>)}
  </Screen>;
}
