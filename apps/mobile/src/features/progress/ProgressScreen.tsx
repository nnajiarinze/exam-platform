import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import { StyleSheet, Text, View } from "react-native";
import { learningApi } from "../../api/learningApi";
import { friendlyError } from "../../api/errors";
import { useAppStore } from "../../app/store";
import { AppHeader } from "../../components/AppHeader";
import { BottomTabBar, type Tab } from "../../components/BottomTabBar";
import {
  Eyebrow,
  ReadinessRing,
  SectionHeader,
  StatTile,
} from "../../components/design";
import { Screen } from "../../components/Screen";
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Loading,
  ProgressBar,
} from "../../components/ui";
import type { RootStackParamList } from "../../navigation/types";
import { theme } from "../../theme";
import {
  curriculumCompletion,
  learningReadinessScore,
  rankedTopics,
} from "./analytics";

export function ProgressScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, "Progress">) {
  const identity = useAppStore((state) => state.learnerIdentity);
  const progress = useQuery({
    queryKey: ["progress"],
    queryFn: () => learningApi.progress(identity),
  });
  const taxonomy = useQuery({
    queryKey: ["subjects"],
    queryFn: () => learningApi.subjects(identity),
  });
  const study = useQuery({
    queryKey: ["study-subjects", identity],
    queryFn: () => learningApi.studySubjects(identity),
  });
  const history = useQuery({
    queryKey: ["mock-history"],
    queryFn: () => learningApi.mockHistory(identity),
  });
  const topicNames = Object.fromEntries(
    (taxonomy.data ?? []).flatMap((subject) =>
      subject.topics.map((topic) => [topic.id, topic.name]),
    ),
  );
  const ranked = rankedTopics(progress.data ?? []);
  const strongest = ranked.slice(0, 3);
  const weakest = [...ranked].reverse().slice(0, 3);
  const answered = (progress.data ?? []).reduce(
    (sum, item) => sum + item.questionsAnswered,
    0,
  );
  const correct = (progress.data ?? []).reduce(
    (sum, item) => sum + item.correctAnswers,
    0,
  );
  const accuracy = answered
    ? Math.round((correct * 100) / answered)
    : undefined;
  const completion = curriculumCompletion(study.data ?? []);
  const readiness = learningReadinessScore({
    subjects: study.data ?? [],
    topicProgress: progress.data ?? [],
    mockHistory: history.data ?? [],
  });
  const mocks = (history.data ?? [])
    .filter((item) => item.status === "SUBMITTED")
    .slice(0, 5)
    .reverse();
  const refresh = () =>
    void Promise.all([
      progress.refetch(),
      taxonomy.refetch(),
      study.refetch(),
      history.refetch(),
    ]);
  const navigateTab = (tab: Tab) => {
    if (tab === "home") navigation.navigate("Home");
    else if (tab === "topics") navigation.navigate("StudySubjects");
    else if (tab === "exam") navigation.navigate("MockExam");
    else if (tab === "settings") navigation.navigate("Settings");
  };
  if (progress.isPending)
    return (
      <Screen scroll={false}>
        <Loading label="Loading progress…" />
      </Screen>
    );
  if (progress.isError)
    return (
      <Screen>
        <ErrorState
          message={friendlyError(progress.error)}
          retry={() => progress.refetch()}
        />
      </Screen>
    );
  return (
    <View style={styles.page}>
      <Screen
        bottomInset
        refreshing={[progress, taxonomy, study, history].some(
          (query) => query.isRefetching,
        )}
        onRefresh={refresh}
      >
        <AppHeader
          onBack={() => navigation.goBack()}
          action="profile"
          onAction={() => navigation.navigate("Profile")}
        />
        <Eyebrow>OVERVIEW</Eyebrow>
        <Text accessibilityRole="header" style={styles.title}>
          Your learning journey
        </Text>
        <Card style={styles.readiness}>
          <Text style={styles.cardLabel}>LEARNING READINESS</Text>
          <ReadinessRing value={readiness} size={174} />
          <Text style={styles.readinessCopy}>
            {readiness == null
              ? "Complete more lessons and practice questions to unlock this summary."
              : `This score combines ${completion ?? 0}% curriculum completion, sampled practice accuracy, and recent mock performance.`}
          </Text>
          <Button
            label="Take practice exam"
            onPress={() => navigation.navigate("MockExam")}
          />
        </Card>
        <View style={styles.metrics}>
          <View style={styles.metric}>
            <StatTile icon="progress" label="QUESTIONS" value={`${answered}`} />
          </View>
          <View style={styles.metric}>
            <StatTile
              icon="check"
              label="ACCURACY"
              value={accuracy == null ? "—" : `${accuracy}%`}
              tone="green"
            />
          </View>
        </View>
        {completion != null ? (
          <Card>
            <SectionHeader title="Curriculum completion" />
            <ProgressBar
              value={completion}
              accessibilityLabel={`${completion} percent of curriculum topics complete`}
            />
            <Text style={styles.meta}>
              {completion}% of published topics completed
            </Text>
          </Card>
        ) : null}
        {mocks.length ? (
          <Card>
            <SectionHeader title="Recent mock trend" />
            <View
              accessibilityLabel={`Recent mock scores: ${mocks.map((item) => item.percentage).join(", ")}`}
              style={styles.chart}
            >
              {mocks.map((item, index) => (
                <View key={item.attemptId} style={styles.barSlot}>
                  <View
                    style={[
                      styles.bar,
                      { height: Math.max(8, item.percentage) },
                      index === mocks.length - 1 && styles.currentBar,
                    ]}
                  />
                  <Text style={styles.barLabel}>{item.percentage}%</Text>
                </View>
              ))}
            </View>
          </Card>
        ) : null}
        {ranked.length ? (
          <>
            <Card>
              <SectionHeader title="Strongest topics" />
              {strongest.map((item) => (
                <View key={item.topicId} style={styles.topicRow}>
                  <Text style={styles.topic}>
                    {topicNames[item.topicId] ?? "Published topic"}
                  </Text>
                  <Text style={styles.strong}>
                    {Math.round(item.accuracyPercentage)}%
                  </Text>
                </View>
              ))}
            </Card>
            <Card>
              <SectionHeader title="To improve" />
              {weakest.map((item) => (
                <View key={item.topicId} style={styles.topicRow}>
                  <Text style={styles.topic}>
                    {topicNames[item.topicId] ?? "Published topic"}
                  </Text>
                  <Text style={styles.weak}>
                    {Math.round(item.accuracyPercentage)}%
                  </Text>
                </View>
              ))}
            </Card>
            <Card tone="primary">
              <Text style={styles.ctaTitle}>Keep the momentum going</Text>
              <Text style={styles.ctaBody}>
                Focus your next practice session on{" "}
                {topicNames[weakest[0].topicId] ?? "your lowest sampled topic"}.
              </Text>
              <Button
                label="Practice this topic"
                variant="accent"
                onPress={() =>
                  navigation.navigate("PracticeSetup", {
                    mode: "TOPIC",
                    topicId: weakest[0].topicId,
                    topicName: topicNames[weakest[0].topicId],
                  })
                }
              />
            </Card>
          </>
        ) : (
          <EmptyState message="Answer at least three questions in a topic to unlock strongest and weakest topic insights." />
        )}
      </Screen>
      <BottomTabBar active="progress" onNavigate={navigateTab} />
    </View>
  );
}
const styles = StyleSheet.create({
  page: { backgroundColor: theme.colors.background, flex: 1 },
  title: { color: theme.colors.text, ...theme.typography.heading },
  readiness: { alignItems: "center", padding: theme.spacing.md },
  cardLabel: { color: theme.colors.muted, ...theme.typography.caption },
  readinessCopy: {
    color: theme.colors.muted,
    ...theme.typography.body,
    textAlign: "center",
  },
  metrics: { flexDirection: "row", gap: theme.spacing.xs },
  metric: { flex: 1 },
  meta: { color: theme.colors.muted, ...theme.typography.caption },
  chart: { alignItems: "flex-end", flexDirection: "row", gap: 8, height: 132 },
  barSlot: {
    alignItems: "center",
    flex: 1,
    height: "100%",
    justifyContent: "flex-end",
  },
  bar: {
    backgroundColor: theme.colors.primaryFixed,
    borderTopLeftRadius: 6,
    borderTopRightRadius: 6,
    width: "100%",
  },
  currentBar: { backgroundColor: theme.colors.accent },
  barLabel: {
    color: theme.colors.muted,
    fontSize: 10,
    fontWeight: "700",
    marginTop: 4,
  },
  topicRow: {
    alignItems: "center",
    backgroundColor: theme.colors.surfaceLow,
    borderRadius: theme.radii.lg,
    flexDirection: "row",
    justifyContent: "space-between",
    padding: 14,
  },
  topic: { color: theme.colors.text, flex: 1, ...theme.typography.label },
  strong: {
    backgroundColor: theme.colors.primaryFixed,
    borderRadius: 6,
    color: theme.colors.primary,
    ...theme.typography.caption,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  weak: {
    backgroundColor: theme.colors.errorBackground,
    borderRadius: 6,
    color: theme.colors.error,
    ...theme.typography.caption,
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  ctaTitle: { color: theme.colors.onPrimary, ...theme.typography.subheading },
  ctaBody: { color: theme.colors.primaryFixed, ...theme.typography.body },
});
