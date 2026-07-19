import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { AnswerResult, PracticeQuestion } from '../../api/generated/types.gen';
import { Card } from '../../components/ui';
import { theme } from '../../theme/theme';

export function QuestionCard({ question, selectedId, result, submitting = false, onSelect }: { question: PracticeQuestion; selectedId?: string; result?: AnswerResult; submitting?: boolean; onSelect: (id: string) => void }) {
  return <View style={styles.container}><Text style={styles.progress}>Question {question.sequenceNumber} of {question.totalQuestionCount}</Text><Text accessibilityRole="header" style={styles.prompt}>{question.prompt}</Text>
    {question.answerOptions.map((option) => <Pressable accessibilityRole="radio" accessibilityState={{ checked: selectedId === option.id, disabled: Boolean(result) || submitting }} disabled={Boolean(result) || submitting} key={option.id} onPress={() => onSelect(option.id)} style={[styles.option, selectedId === option.id && styles.selected]}><Text style={styles.optionText}>{option.text}</Text></Pressable>)}
    {result && <Card><Text accessibilityRole="alert" style={[styles.feedback, { color: result.correct ? theme.colors.success : theme.colors.error }]}>{result.correct ? 'Correct' : 'Not quite'}</Text><Text>{result.explanation}</Text></Card>}
  </View>;
}
const styles = StyleSheet.create({ container: { gap: 14 }, progress: { color: theme.colors.muted }, prompt: { color: theme.colors.text, fontSize: 22, fontWeight: '700', lineHeight: 30 }, option: { backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: 10, borderWidth: 1, minHeight: 52, justifyContent: 'center', padding: 14 }, selected: { borderColor: theme.colors.primary, borderWidth: 2 }, optionText: { fontSize: 17 }, feedback: { fontSize: 19, fontWeight: '700' } });
