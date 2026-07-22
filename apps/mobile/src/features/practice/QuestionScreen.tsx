import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import type { AnswerResult, PracticeQuestion } from '../../api/generated/types.gen';
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
  const [advancing,setAdvancing]=useState(false); const [advanceError,setAdvanceError]=useState<unknown>();
  const nextQuestionRef=useRef<Promise<PracticeQuestion>|undefined>(undefined); const queryClient=useQueryClient(); const queryKey=['next-question',route.params.sessionId] as const;
  const query = useQuery({ queryKey, queryFn: () => learningApi.nextQuestion(identity, route.params.sessionId), retry: 1 });
  const mutation = useMutation({ mutationFn: (optionIds: string[]) => learningApi.submitAnswer(identity, route.params.sessionId, { sessionQuestionId: query.data!.sessionQuestionId, selectedOptionIds: optionIds }), onSuccess: (answer) => { setResult(answer); recordAnswer(answer.correct);if(answer.sessionProgress.answered<answer.sessionProgress.total){const pending=learningApi.nextQuestion(identity,route.params.sessionId);void pending.catch(()=>undefined);nextQuestionRef.current=pending;} } });
  const toggle = (id: string) => setSelectedIds((current) => query.data?.questionType === 'MULTIPLE_CHOICE' ? (current.includes(id) ? current.filter((value) => value !== id) : [...current, id]) : [id]);
  const next = async () => { if (!result) return; if (result.sessionProgress.answered >= result.sessionProgress.total) { navigation.replace('SessionComplete', { total: result.sessionProgress.total }); return; }setAdvancing(true);setAdvanceError(undefined);try{const nextQuestion=await(nextQuestionRef.current??learningApi.nextQuestion(identity,route.params.sessionId));queryClient.setQueryData(queryKey,nextQuestion);nextQuestionRef.current=undefined;setSelectedIds([]);setResult(undefined);}catch(error){nextQuestionRef.current=undefined;setAdvanceError(error);}finally{setAdvancing(false);} };
  if (query.isPending) return <Screen scroll={false}><Loading label="Loading question…" /></Screen>;
  if (query.isError) return <Screen><AppHeader onBack={() => navigation.goBack()} action="help" /><ErrorState message={friendlyError(query.error)} retry={() => query.refetch()} /></Screen>;
  if (advancing) return <Screen scroll={false}><Loading label="Loading next question…" /></Screen>;
  return <View style={styles.page}><Screen><AppHeader onBack={() => navigation.goBack()} action="help" />
    <QuestionCard question={query.data} category={category} selectedIds={selectedIds} result={result} submitting={mutation.isPending} onSelect={toggle} />
    {mutation.isPending && <Loading label="Checking answer…" />}{mutation.isError && <ErrorState message={friendlyError(mutation.error)} retry={selectedIds.length ? () => mutation.mutate(selectedIds) : undefined} />}{Boolean(advanceError)&&<ErrorState message={friendlyError(advanceError)} />}
    {!result ? <Button label={mutation.isPending ? 'Checking answer…' : 'Submit answer'} disabled={!selectedIds.length || mutation.isPending} onPress={() => selectedIds.length && mutation.mutate(selectedIds)} /> : <Button label={result.sessionProgress.answered >= result.sessionProgress.total ? 'View results' : 'Continue'} onPress={next} />}
  </Screen></View>;
}
const styles = StyleSheet.create({ page: { backgroundColor: theme.colors.background, flex: 1 } });
