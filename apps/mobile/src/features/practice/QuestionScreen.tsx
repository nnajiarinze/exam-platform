import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import type { AnswerResult } from '../../api/generated/types.gen';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { AppHeader } from '../../components/AppHeader';
import { Screen } from '../../components/Screen';
import { Button, ErrorState, Loading } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme';
import { QuestionCard } from './QuestionCard';

export function QuestionScreen({ navigation, route }: NativeStackScreenProps<RootStackParamList, 'Question'>) {
  const identity = useAppStore((s) => s.learnerIdentity); const recordAnswer = useAppStore((s) => s.recordAnswer); const category = useAppStore((s) => s.currentPracticeLabel);
  const [selectedIds, setSelectedIds] = useState<string[]>([]); const [result, setResult] = useState<AnswerResult>();
  const query = useQuery({ queryKey: ['next-question', route.params.sessionId], queryFn: () => learningApi.nextQuestion(identity, route.params.sessionId), retry: 1 });
  const mutation = useMutation({ mutationFn: (optionIds: string[]) => learningApi.submitAnswer(identity, route.params.sessionId, { sessionQuestionId: query.data!.sessionQuestionId, selectedOptionIds: optionIds }), onSuccess: (answer) => { setResult(answer); recordAnswer(answer.correct); } });
  const toggle = (id: string) => setSelectedIds((current) => query.data?.questionType === 'MULTIPLE_CHOICE' ? (current.includes(id) ? current.filter((value) => value !== id) : [...current, id]) : [id]);
  const next = async () => { if (!result) return; if (result.sessionProgress.answered >= result.sessionProgress.total) { navigation.replace('SessionComplete', { total: result.sessionProgress.total }); return; } setSelectedIds([]); setResult(undefined); await query.refetch(); };
  if (query.isPending) return <Screen scroll={false}><Loading label="Loading question…" /></Screen>;
  if (query.isError) return <Screen><AppHeader onBack={() => navigation.goBack()} action="help" /><ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} /></Screen>;
  return <View style={styles.page}><Screen><AppHeader onBack={() => navigation.goBack()} action="help" />
    <QuestionCard question={query.data} category={category} selectedIds={selectedIds} result={result} submitting={mutation.isPending} onSelect={toggle} />
    {mutation.isPending && <Loading label="Checking answer…" />}{mutation.isError && <ErrorState message={friendlyError(mutation.error)} retry={selectedIds.length ? () => mutation.mutate(selectedIds) : undefined} />}
    {!result ? <Button label={mutation.isPending ? 'Checking answer…' : 'Submit answer'} disabled={!selectedIds.length || mutation.isPending} onPress={() => selectedIds.length && mutation.mutate(selectedIds)} /> : <Button label={result.sessionProgress.answered >= result.sessionProgress.total ? 'View results' : 'Continue'} onPress={next} />}
  </Screen></View>;
}
const styles = StyleSheet.create({ page: { backgroundColor: theme.colors.background, flex: 1 } });
