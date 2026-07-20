import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Alert, Pressable, StyleSheet, Text, View } from 'react-native';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Button, ErrorState, Loading } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme/theme';
import { formatCountdown, useCountdown } from './countdown';
import { QuestionNavigator } from './QuestionNavigator';

export function MockQuestionScreen({ navigation, route }: NativeStackScreenProps<RootStackParamList, 'MockQuestion'>) {
  const { attemptId, sequenceNumber } = route.params; const identity = useAppStore((s) => s.learnerIdentity); const setAttempt = useAppStore((s) => s.setMockAttempt); const queryClient = useQueryClient();
  const attempt = useQuery({ queryKey: ['mock-attempt', attemptId], queryFn: () => learningApi.mockExam(identity, attemptId) });
  const question = useQuery({ queryKey: ['mock-question', attemptId, sequenceNumber], queryFn: () => learningApi.mockQuestion(identity, attemptId, sequenceNumber) });
  const [selected, setSelected] = useState<string>();
  useEffect(() => setSelected(question.data?.selectedAnswerOptionId ?? undefined), [question.data?.attemptQuestionId, question.data?.selectedAnswerOptionId]);
  const remaining = useCountdown(question.data?.remainingSeconds ?? attempt.data?.remainingSeconds ?? -1);
  const answer = useMutation({ mutationFn: (optionId: string) => learningApi.answerMockQuestion(identity, attemptId, question.data!.attemptQuestionId, optionId, question.data!.answerVersion), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['mock-attempt', attemptId] }); queryClient.invalidateQueries({ queryKey: ['mock-question', attemptId, sequenceNumber] }); }, onError: async () => { const refreshed = await question.refetch(); setSelected(refreshed.data?.selectedAnswerOptionId ?? undefined); } });
  const flag = useMutation({ mutationFn: (flagged: boolean) => learningApi.flagMockQuestion(identity, attemptId, question.data!.attemptQuestionId, flagged, question.data!.questionVersion), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['mock-attempt', attemptId] }); queryClient.invalidateQueries({ queryKey: ['mock-question', attemptId, sequenceNumber] }); } });
  const submit = useMutation({ mutationFn: () => learningApi.submitMockExam(identity, attemptId), onSuccess: () => { setAttempt(undefined); navigation.replace('MockResults', { attemptId }); } });
  useEffect(() => { if (attempt.data && attempt.data.status !== 'ACTIVE') { setAttempt(undefined); navigation.replace('MockResults', { attemptId }); } }, [attempt.data?.status, attemptId, navigation, setAttempt]);
  useEffect(() => { if (question.data && remaining === 0 && !submit.isPending) submit.mutate(); }, [remaining, question.data]);
  const go = (sequence: number) => navigation.replace('MockQuestion', { attemptId, sequenceNumber: sequence });
  const confirmSubmit = () => {
    const answered = attempt.data?.answered ?? 0;
    const flagged = attempt.data?.questions.filter((item) => item.flagged).length ?? 0;
    const total = attempt.data?.totalQuestions ?? 0;
    Alert.alert('Submit mock examination?', `${answered} answered · ${total - answered} unanswered · ${flagged} flagged\n${formatCountdown(Math.max(0, remaining))} remaining\n\nYou cannot change answers after submission.`, [{ text: 'Return to exam', style: 'cancel' }, { text: 'Submit', style: 'destructive', onPress: () => submit.mutate() }]);
  };
  if (attempt.isPending || question.isPending) return <Screen scroll={false}><Loading label="Loading mock examination…" /></Screen>;
  if (attempt.isError || question.isError) return <Screen><ErrorState message={friendlyError(attempt.error ?? question.error)} retry={() => { attempt.refetch(); question.refetch(); }} /></Screen>;
  const displayedRemaining = Math.max(0, remaining);
  return <Screen><View style={styles.header}><Text style={styles.progress}>Question {sequenceNumber} of {question.data.totalQuestions}</Text><Text accessibilityLabel={`${displayedRemaining} seconds remaining`} style={styles.timer}>{formatCountdown(displayedRemaining)}</Text></View>
    <QuestionNavigator questions={attempt.data.questions} current={sequenceNumber} onSelect={go} />
    <Text accessibilityRole="header" style={styles.prompt}>{question.data.prompt}</Text>
    {question.data.answerOptions.map((option) => <Pressable accessibilityRole="radio" accessibilityState={{ checked: selected === option.id, disabled: answer.isPending }} disabled={answer.isPending} key={option.id} onPress={() => { setSelected(option.id); answer.mutate(option.id); }} style={[styles.option, selected === option.id && styles.selected]}><Text style={styles.optionText}>{option.text}</Text></Pressable>)}
    {(answer.isError || flag.isError || submit.isError) && <ErrorState message={friendlyError(answer.error ?? flag.error ?? submit.error)} />}
    <Button label={question.data.flagged ? 'Remove review flag' : 'Flag for review'} disabled={flag.isPending} onPress={() => flag.mutate(!question.data.flagged)} />
    <View style={styles.actions}><Button label="Previous" disabled={sequenceNumber <= 1} onPress={() => go(sequenceNumber - 1)} /><Button label="Next" disabled={sequenceNumber >= question.data.totalQuestions} onPress={() => go(sequenceNumber + 1)} /></View>
    <Button label={submit.isPending ? 'Submitting…' : 'Submit examination'} disabled={submit.isPending} onPress={confirmSubmit} />
  </Screen>;
}
const styles = StyleSheet.create({ header: { flexDirection: 'row', justifyContent: 'space-between' }, progress: { color: theme.colors.muted }, timer: { color: theme.colors.error, fontSize: 18, fontWeight: '700' }, prompt: { fontSize: 22, fontWeight: '700', lineHeight: 30 }, option: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: 10, borderWidth: 1, padding: 14 }, selected: { borderColor: theme.colors.primary, borderWidth: 2 }, optionText: { fontSize: 17 }, actions: { flexDirection: 'row', gap: 8 } });
