import { StyleSheet, Text, View } from "react-native";
import type {
  AnswerResult,
  PracticeQuestion,
} from "../../api/generated/types.gen";
import { Icon, ProgressBar, StatusBadge } from "../../components/ui";
import { AnswerOption } from "../../components/design";
import { theme } from "../../theme";

export function QuestionCard({
  question,
  category,
  selectedIds,
  result,
  submitting = false,
  onSelect,
}: {
  question: PracticeQuestion;
  category?: string;
  selectedIds: string[];
  result?: AnswerResult;
  submitting?: boolean;
  onSelect: (id: string) => void;
}) {
  const percent = Math.round(
    (question.sequenceNumber / question.totalQuestionCount) * 100,
  );
  return (
    <View style={styles.container}>
      <View style={styles.progressLabels}>
        <Text style={styles.progressStrong}>
          QUESTION {question.sequenceNumber} OF {question.totalQuestionCount}
        </Text>
        <Text style={styles.progressText}>{percent}% complete</Text>
      </View>
      <ProgressBar
        value={percent}
        accessibilityLabel={`Question ${question.sequenceNumber} of ${question.totalQuestionCount}`}
      />
      <View style={styles.questionBlock}>
        {category && <StatusBadge label={category.toUpperCase()} />}
        <Text accessibilityRole="header" style={styles.prompt}>
          {question.prompt}
        </Text>
      </View>
      {question.questionType === "MULTIPLE_CHOICE" && !result && (
        <Text style={styles.hint}>Select all answers that apply.</Text>
      )}
      <View
        accessibilityRole={
          question.questionType === "MULTIPLE_CHOICE" ? undefined : "radiogroup"
        }
        style={styles.options}
      >
        {question.answerOptions.map((option, index) => {
          const selected = selectedIds.includes(option.id);
          const correct = result?.correctOptionIds.includes(option.id) ?? false;
          const incorrect = Boolean(result) && selected && !correct;
          return (
            <AnswerOption
              correct={correct}
              disabled={Boolean(result) || submitting}
              incorrect={incorrect}
              index={index}
              key={option.id}
              multiple={question.questionType === "MULTIPLE_CHOICE"}
              onPress={() => onSelect(option.id)}
              selected={selected}
              text={option.text}
            />
          );
        })}
      </View>
      {result && (
        <View
          accessibilityRole="alert"
          style={[
            styles.feedback,
            result.correct ? styles.feedbackCorrect : styles.feedbackIncorrect,
          ]}
        >
          <View style={styles.feedbackHeading}>
            <Icon
              name={result.correct ? "check" : "info"}
              color={result.correct ? theme.colors.success : theme.colors.error}
            />
            <Text
              style={[
                styles.feedbackTitle,
                {
                  color: result.correct
                    ? theme.colors.success
                    : theme.colors.error,
                },
              ]}
            >
              {result.correct ? "Correct" : "Study tip"}
            </Text>
          </View>
          <Text style={styles.explanation}>{result.explanation}</Text>
        </View>
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  container: { gap: theme.spacing.sm },
  progressLabels: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  progressStrong: {
    color: theme.colors.primary,
    fontSize: 18,
    fontWeight: "700",
  },
  progressText: { color: theme.colors.muted, ...theme.typography.label },
  questionBlock: {
    gap: theme.spacing.sm,
    marginBottom: theme.spacing.sm,
    marginTop: theme.spacing.md,
  },
  prompt: {
    color: theme.colors.text,
    fontSize: 30,
    fontWeight: "800",
    lineHeight: 38,
    letterSpacing: -0.4,
  },
  options: { gap: theme.spacing.sm },
  hint: { color: theme.colors.muted, ...theme.typography.body },
  feedback: {
    borderWidth: 1,
    borderRadius: theme.radii.xl,
    gap: 8,
    padding: theme.spacing.sm,
  },
  feedbackCorrect: {
    backgroundColor: theme.colors.successBackground,
    borderColor: theme.colors.success,
  },
  feedbackIncorrect: {
    backgroundColor: theme.colors.surfaceLow,
    borderColor: theme.colors.primaryFixed,
  },
  feedbackHeading: { alignItems: "center", flexDirection: "row", gap: 8 },
  feedbackTitle: { fontSize: 18, fontWeight: "700" },
  explanation: { color: theme.colors.text, ...theme.typography.bodyLarge },
});
