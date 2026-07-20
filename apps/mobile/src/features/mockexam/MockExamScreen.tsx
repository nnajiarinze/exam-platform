import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation } from '@tanstack/react-query';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Button, Card, ErrorState, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';

export function MockExamScreen({ navigation }: NativeStackScreenProps<RootStackParamList, 'MockExam'>) {
  const identity = useAppStore((s) => s.learnerIdentity); const current = useAppStore((s) => s.currentMockAttemptId); const setAttempt = useAppStore((s) => s.setMockAttempt);
  const mutation = useMutation({ mutationFn: () => learningApi.createMockExam(identity), onSuccess: (attempt) => { setAttempt(attempt.attemptId); navigation.replace('MockQuestion', { attemptId: attempt.attemptId, sequenceNumber: 1 }); } });
  return <Screen><Title>Mock examination</Title><Card><Body>This is a timed simulation. Questions are fixed when you start. Correct answers and explanations appear only after final submission.</Body></Card>
    {current && <Button label="Continue active mock" onPress={() => navigation.navigate('MockQuestion', { attemptId: current, sequenceNumber: 1 })} />}
    {mutation.isError && <ErrorState message={friendlyError(mutation.error)} />}
    <Button label={mutation.isPending ? 'Starting…' : 'Start new mock exam'} disabled={mutation.isPending} onPress={() => mutation.mutate()} />
    <Button label="View mock history" onPress={() => navigation.navigate('MockHistory')} />
  </Screen>;
}
