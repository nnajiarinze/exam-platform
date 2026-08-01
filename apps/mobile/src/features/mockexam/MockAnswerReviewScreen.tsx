import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { Text } from 'react-native';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Button, Card, ErrorState, Loading, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';

export function MockAnswerReviewScreen({ route, navigation }: NativeStackScreenProps<RootStackParamList, 'MockAnswerReview'>) {
  const identity = useAppStore((state) => state.learnerIdentity);
  const query = useQuery({ queryKey: ['mock-question-review', route.params.attemptId], queryFn: () => learningApi.mockQuestionReview(identity, route.params.attemptId) });
  if (query.isPending) return <Screen scroll={false}><Loading label="Loading answer review…" /></Screen>;
  if (query.isError) return <Screen><ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} /></Screen>;
  return <Screen><Title>Answer review</Title><Body>Incorrect and unanswered questions are shown below. Answers can no longer be changed.</Body>
    {query.data.length === 0 ? <Card><Body>No questions are available for review.</Body></Card> : query.data.map((question) => <Card key={question.questionId}>
      <Text accessibilityRole="header">{question.prompt}</Text><Text>{question.questionType.replaceAll('_', ' ')}</Text><Text>Status: {question.selectedOptionIds.length ? (question.selectedOptionIds.every(id => question.correctOptionIds.includes(id)) && question.correctOptionIds.every(id => question.selectedOptionIds.includes(id)) ? 'Correct' : 'Incorrect') : 'Unanswered'}</Text>
      <Body>{question.topicName} · {question.objectiveName}</Body>
      {question.options.map((option) => <Text key={option.id}>{option.selected ? 'Selected' : option.missed ? 'Missed' : option.correct ? 'Correct' : 'Not selected'}: {option.text}</Text>)}<Body>{question.explanation}</Body>
      <Button label="Review lesson" variant="text" onPress={() => navigation.navigate('TopicLesson', { topicId: question.lessonTopicId, topicTitle: question.topicName, reviewMode: true })} />
    </Card>)}
  </Screen>;
}
