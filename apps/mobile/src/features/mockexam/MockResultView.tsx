import { StyleSheet, Text, View } from "react-native";
import type {
  MockExamHistoryItem,
  MockExamResult,
} from "../../api/generated/types.gen";
import { ReadinessRing, SectionHeader } from "../../components/design";
import { Body, Card, ProgressBar } from "../../components/ui";
import { theme } from "../../theme";

export function MockResultView({
  result,
  history = [],
}: {
  result: MockExamResult;
  history?: MockExamHistoryItem[];
}) {
  const total =
    result.correctAnswers + result.incorrectAnswers + result.unansweredAnswers;
  const weakest = [...result.subjects].sort(
    (a, b) => a.percentage - b.percentage,
  )[0];
  const trend = [
    ...history
      .filter((item) => item.status === "SUBMITTED")
      .slice(0, 4)
      .reverse()
      .map((item) => item.percentage),
    result.percentage,
  ].slice(-5);
  return (
    <View style={styles.list}>
      <View style={styles.celebration}>
        <Text
          accessibilityRole="image"
          accessibilityLabel={result.passed ? "Passed" : "Completed"}
          style={styles.star}
        >
          {result.passed ? "★" : "✓"}
        </Text>
        <Text accessibilityRole="header" style={styles.title}>
          {result.passed ? "Great work! You passed." : "Mock exam complete"}
        </Text>
        <Text style={styles.subtitle}>
          {result.passed
            ? "You reached the configured pass threshold."
            : "Keep studying and try again when you are ready."}
        </Text>
      </View>
      <Card style={styles.scoreCard}>
        <ReadinessRing
          value={result.percentage}
          label="Mock examination accuracy"
        />
        <Text style={styles.score}>
          {result.correctAnswers}/{total}
        </Text>
        <Text style={styles.scoreLabel}>Correct answers</Text>
        <Body>
          {result.percentage}% correct · pass threshold {result.passPercentage}%
        </Body>
        <Body>
          {result.correctAnswers} correct · {result.incorrectAnswers} incorrect
          · {result.unansweredAnswers} unanswered
        </Body>
      </Card>
      {trend.length > 1 ? (
        <Card>
          <SectionHeader title="Progress trend" />
          <View
            accessibilityLabel={`Recent mock scores: ${trend.join(", ")}`}
            style={styles.chart}
          >
            {trend.map((score, index) => (
              <View key={`${score}-${index}`} style={styles.barSlot}>
                <View
                  style={[
                    styles.bar,
                    { height: Math.max(8, score) },
                    index === trend.length - 1 && styles.currentBar,
                  ]}
                />
                <Text style={styles.barLabel}>
                  {index === trend.length - 1 ? "Now" : `${index + 1}`}
                </Text>
              </View>
            ))}
          </View>
        </Card>
      ) : null}
      <Card tone="primary">
        <Text style={styles.improveTitle}>Room for improvement</Text>
        <Text style={styles.improveBody}>
          {weakest
            ? `${weakest.subjectName} was your lowest-scoring subject at ${weakest.percentage}%.`
            : "No subject recommendation is available for this attempt."}
        </Text>
        {weakest ? (
          <View style={styles.recommend}>
            <Text style={styles.recommendText}>
              Review: {weakest.subjectName}
            </Text>
          </View>
        ) : null}
      </Card>
      <SectionHeader title="Subject breakdown" />
      {result.subjects.length ? (
        result.subjects.map((subject) => (
          <Card key={subject.subjectId}>
            <View style={styles.breakdownHeader}>
              <Text style={styles.topic}>{subject.subjectName}</Text>
              <Text style={styles.percent}>{subject.percentage}%</Text>
            </View>
            <ProgressBar value={subject.percentage} />
            <Text style={styles.meta}>
              {subject.correct}/{subject.total} correct · {subject.unanswered}{" "}
              unanswered
            </Text>
          </Card>
        ))
      ) : (
        <Body>No subject breakdown is available.</Body>
      )}
      <SectionHeader title="Topic breakdown" />
      {result.topics.map((topic) => (
        <Card key={topic.topicId}>
          <View style={styles.breakdownHeader}>
            <Text style={styles.topic}>{topic.topicName}</Text>
            <Text style={styles.percent}>{topic.percentage}%</Text>
          </View>
          <ProgressBar value={topic.percentage} />
          <Text style={styles.meta}>
            {topic.correct}/{topic.total} correct
          </Text>
        </Card>
      ))}
      <SectionHeader title="Attempt details" />
      <Card>
        <Body>
          Time spent: {Math.floor(result.durationSeconds / 60)}m{" "}
          {result.durationSeconds % 60}s
        </Body>
        <Body>
          {result.autoSubmitted
            ? "Automatically submitted when time expired"
            : "Submitted by learner"}
        </Body>
        <Body>
          Attempt date: {new Date(result.startedAt).toLocaleDateString()}
        </Body>
      </Card>
      <SectionHeader title="Incorrect and unanswered" />
      {result.incorrectQuestions.length === 0 ? (
        <Body>No incorrect answers.</Body>
      ) : (
        result.incorrectQuestions.map((question) => (
          <Card key={question.questionId}>
            <Text style={styles.topic}>{question.prompt}</Text>
            <Text style={styles.meta}>
              {question.questionType.replaceAll("_", " ")}
            </Text>
            {question.options.map((option) => (
              <Text
                key={option.id}
                style={option.correct ? styles.correct : styles.meta}
              >
                {option.selected
                  ? "Selected"
                  : option.missed
                    ? "Missed"
                    : option.correct
                      ? "Correct"
                      : "Not selected"}
                : {option.text}
              </Text>
            ))}
            <Text>{question.explanation}</Text>
          </Card>
        ))
      )}
    </View>
  );
}
const styles = StyleSheet.create({
  list: { gap: theme.spacing.sm },
  celebration: {
    alignItems: "center",
    gap: 6,
    paddingVertical: theme.spacing.sm,
  },
  star: { color: theme.colors.accentStrong, fontSize: 56, lineHeight: 62 },
  title: {
    color: theme.colors.text,
    ...theme.typography.heading,
    textAlign: "center",
  },
  subtitle: {
    color: theme.colors.muted,
    ...theme.typography.body,
    textAlign: "center",
  },
  scoreCard: { alignItems: "center" },
  score: { color: theme.colors.primary, fontSize: 26, fontWeight: "800" },
  scoreLabel: { color: theme.colors.muted, ...theme.typography.caption },
  chart: { alignItems: "flex-end", flexDirection: "row", gap: 8, height: 140 },
  barSlot: {
    alignItems: "center",
    flex: 1,
    height: "100%",
    justifyContent: "flex-end",
  },
  bar: {
    backgroundColor: theme.colors.surfaceContainer,
    borderTopLeftRadius: 6,
    borderTopRightRadius: 6,
    width: "100%",
  },
  currentBar: { backgroundColor: theme.colors.accentStrong },
  barLabel: {
    color: theme.colors.muted,
    ...theme.typography.caption,
    marginTop: 4,
  },
  improveTitle: {
    color: theme.colors.onPrimary,
    ...theme.typography.subheading,
  },
  improveBody: { color: theme.colors.primaryFixed, ...theme.typography.body },
  recommend: {
    backgroundColor: theme.colors.accent,
    borderRadius: theme.radii.lg,
    padding: 14,
  },
  recommendText: { color: theme.colors.onAccent, ...theme.typography.label },
  breakdownHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  topic: {
    color: theme.colors.text,
    fontSize: 17,
    fontWeight: "700",
    lineHeight: 23,
  },
  percent: { color: theme.colors.primary, ...theme.typography.label },
  meta: { color: theme.colors.muted, ...theme.typography.caption },
  correct: { color: theme.colors.success, fontWeight: "600" },
});
