import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { appConfig } from '../../api/config';
import { friendlyError } from '../../api/errors';
import { learningApi } from '../../api/learningApi';
import { useAppStore } from '../../app/store';
import { Screen } from '../../components/Screen';
import { Body, Button, ErrorState, Title } from '../../components/ui';
import type { RootStackParamList } from '../../navigation/types';
import { theme } from '../../theme/theme';

type Values = { questionCount: number };
export function PracticeSetupScreen({ navigation, route }: NativeStackScreenProps<RootStackParamList, 'PracticeSetup'>) {
  const identity = useAppStore((s) => s.learnerIdentity); const setSession = useAppStore((s) => s.setSession);
  const { control, handleSubmit } = useForm<Values>({ defaultValues: { questionCount: 3 } });
  const mutation = useMutation({ mutationFn: (values: Values) => learningApi.createSession(identity, { examId: appConfig.examId, mode: route.params.mode, topicId: route.params.topicId, questionCount: values.questionCount }), onSuccess: (session) => { setSession(session.sessionId, route.params.topicName ?? (route.params.mode === 'MIXED' ? 'Mixed practice' : undefined)); navigation.replace('Question', { sessionId: session.sessionId }); } });
  return <Screen><Title>{route.params.mode === 'TOPIC' ? route.params.topicName ?? 'Topic practice' : 'Mixed practice'}</Title><Body>Select the number of questions.</Body>
    <Controller control={control} name="questionCount" render={({ field }) => <View style={styles.row}>{[3, 5, 10].map((count) => <Pressable accessibilityRole="radio" accessibilityState={{ checked: field.value === count }} key={count} onPress={() => field.onChange(count)} style={[styles.choice, field.value === count && styles.selected]}><Text>{count}</Text></Pressable>)}</View>} />
    {mutation.isError && <ErrorState message={friendlyError(mutation.error)} />}
    <Button label={mutation.isPending ? 'Starting…' : 'Start session'} disabled={mutation.isPending || !identity} onPress={handleSubmit((values) => mutation.mutate(values))} />
  </Screen>;
}
const styles = StyleSheet.create({ row: { flexDirection: 'row', gap: 8 }, choice: { alignItems: 'center', backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: 8, borderWidth: 1, flex: 1, padding: 16 }, selected: { borderColor: theme.colors.primary, borderWidth: 2 } });
