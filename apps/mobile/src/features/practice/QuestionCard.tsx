import { Pressable, StyleSheet, Text, View } from 'react-native';
import type { AnswerResult, PracticeQuestion } from '../../api/generated/types.gen';
import { Icon, ProgressBar, StatusBadge } from '../../components/ui';
import { theme } from '../../theme';

export function QuestionCard({ question, category, selectedIds, result, submitting = false, onSelect }: { question: PracticeQuestion; category?: string; selectedIds: string[]; result?: AnswerResult; submitting?: boolean; onSelect: (id: string) => void }) {
  const percent = Math.round((question.sequenceNumber / question.totalQuestionCount) * 100);
  return <View style={styles.container}>
    <View style={styles.progressLabels}><Text style={styles.progressStrong}>QUESTION {question.sequenceNumber} OF {question.totalQuestionCount}</Text><Text style={styles.progressText}>{percent}% complete</Text></View>
    <ProgressBar value={percent} accessibilityLabel={`Question ${question.sequenceNumber} of ${question.totalQuestionCount}`} />
    <View style={styles.questionBlock}>{category && <StatusBadge label={category.toUpperCase()} />}<Text accessibilityRole="header" style={styles.prompt}>{question.prompt}</Text></View>
    {question.questionType === 'MULTIPLE_CHOICE' && !result && <Text style={styles.hint}>Select all answers that apply.</Text>}
    <View accessibilityRole={question.questionType === 'MULTIPLE_CHOICE' ? undefined : 'radiogroup'} style={styles.options}>{question.answerOptions.map((option, index) => {
      const selected = selectedIds.includes(option.id);
      const correct = result?.correctOptionIds.includes(option.id) ?? false;
      const incorrect = Boolean(result) && selected && !correct;
      return <Pressable accessibilityRole={question.questionType === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'} accessibilityState={{ checked: selected, disabled: Boolean(result) || submitting }} disabled={Boolean(result) || submitting} key={option.id} onPress={() => onSelect(option.id)} style={({ pressed }) => [styles.option, selected && styles.selected, correct && styles.correct, incorrect && styles.incorrect, pressed && styles.pressed]}>
        <View style={[styles.letter, selected && styles.selectedLetter, correct && styles.correctLetter, incorrect && styles.incorrectLetter]}><Text style={[styles.letterText, (selected || correct || incorrect) && styles.activeLetterText]}>{String.fromCharCode(65 + index)}</Text></View><Text style={styles.optionText}>{option.text}</Text>{correct && <Icon name="check" color={theme.colors.success} />}{incorrect && <Icon name="close" color={theme.colors.error} />}
      </Pressable>;
    })}</View>
    {result && <View accessibilityRole="alert" style={[styles.feedback, result.correct ? styles.feedbackCorrect : styles.feedbackIncorrect]}><Text style={[styles.feedbackTitle, { color: result.correct ? theme.colors.success : theme.colors.error }]}>{result.correct ? 'Correct' : 'Not quite'}</Text><Text style={styles.explanation}>{result.explanation}</Text></View>}
  </View>;
}
const styles = StyleSheet.create({
  container: { gap: theme.spacing.sm }, progressLabels: { alignItems: 'center', flexDirection: 'row', justifyContent: 'space-between' }, progressStrong: { color: theme.colors.primary, ...theme.typography.label }, progressText: { color: theme.colors.muted, ...theme.typography.label },
  questionBlock: { gap: theme.spacing.sm, marginBottom: theme.spacing.md, marginTop: theme.spacing.xl }, prompt: { color: theme.colors.text, ...theme.typography.display },
  options: { gap: theme.spacing.sm }, option: { alignItems: 'center', backgroundColor: theme.colors.surface, borderColor: theme.colors.border, borderRadius: theme.radii.xl, borderWidth: 1.5, flexDirection: 'row', gap: theme.spacing.sm, minHeight: 96, padding: theme.spacing.md },
  pressed: { opacity: 0.82 }, selected: { backgroundColor: theme.colors.surfaceLow, borderColor: theme.colors.primary, borderWidth: 2 }, correct: { backgroundColor: theme.colors.successBackground, borderColor: theme.colors.success }, incorrect: { backgroundColor: theme.colors.errorBackground, borderColor: theme.colors.error },
  letter: { alignItems: 'center', borderColor: theme.colors.border, borderRadius: theme.radii.full, borderWidth: 1.5, height: 48, justifyContent: 'center', width: 48 }, selectedLetter: { backgroundColor: theme.colors.primary, borderColor: theme.colors.primary }, correctLetter: { backgroundColor: theme.colors.success, borderColor: theme.colors.success }, incorrectLetter: { backgroundColor: theme.colors.error, borderColor: theme.colors.error }, letterText: { color: theme.colors.text, fontSize: 18, fontWeight: '700' }, activeLetterText: { color: theme.colors.onPrimary }, optionText: { color: theme.colors.text, flex: 1, ...theme.typography.bodyLarge },
  hint: { color: theme.colors.muted, ...theme.typography.body }, feedback: { borderLeftWidth: 4, borderRadius: theme.radii.lg, gap: 6, padding: theme.spacing.sm }, feedbackCorrect: { backgroundColor: theme.colors.successBackground, borderLeftColor: theme.colors.success }, feedbackIncorrect: { backgroundColor: theme.colors.errorBackground, borderLeftColor: theme.colors.error }, feedbackTitle: { ...theme.typography.subheading }, explanation: { color: theme.colors.text, ...theme.typography.body },
});
