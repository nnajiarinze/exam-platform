import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery } from '@tanstack/react-query';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Button, Card, ErrorState, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';

export function MockExamScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'MockExam'>) {
  const identity = useAppStore((s) => s.learnerIdentity); const current = useAppStore((s) => s.currentMockAttemptId); const setAttempt = useAppStore((s) => s.setMockAttempt);
  const configuration = useQuery({ queryKey: ['mock-exam-configuration'], queryFn: learningApi.mockExamConfiguration });
  const mutation = useMutation({ mutationFn: () => learningApi.createMockExam(identity), onSuccess: (attempt) => { setAttempt(attempt.attemptId); navigation.replace('MockQuestion', { attemptId: attempt.attemptId, sequenceNumber: 1 }); } });
  return <Screen><Title>{configuration.data?.name ?? 'Mock examination'}</Title><Card>
    <Body>{configuration.data?.description ?? 'Timed examination using the active published content.'}</Body>
    {configuration.data && <Body>{configuration.data.questionCount} questions · {configuration.data.durationMinutes} minutes · {configuration.data.passPercentage}% to pass</Body>}
    <Body>You can change answers and flag questions before submitting. No correctness feedback is shown during the exam. The exam submits automatically when time expires, and unanswered questions count as incorrect.</Body>
  </Card>
    {configuration.isError && <ErrorState message={friendlyError(configuration.error)} retry={() => configuration.refetch()} />}
    {current && <Button label="Continue active mock" onPress={() => navigation.navigate('MockQuestion', { attemptId: current, sequenceNumber: 1 })} />}
    {mutation.isError && <ErrorState message={friendlyError(mutation.error)} />}
    <Button label={mutation.isPending ? 'Starting…' : 'Start new mock exam'} disabled={mutation.isPending} onPress={() => mutation.mutate()} />
    <Button label="View mock history" onPress={() => navigation.navigate('MockHistory')} />
  </Screen>;
}
