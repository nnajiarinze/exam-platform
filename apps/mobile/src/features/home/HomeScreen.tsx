import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import { StyleSheet, Text, View } from "react-native";
import { learningApi } from "../../api/learningApi";
import { useAppStore } from "../../app/store";
import { AppHeader } from "../../components/AppHeader";
import { BottomTabBar, type Tab } from "../../components/BottomTabBar";
import {
  ActionCard,
  Eyebrow,
  ReadinessRing,
  SectionHeader,
  StatTile,
} from "../../components/design";
import { Screen } from "../../components/Screen";
import { Button, Card, ProgressBar } from "../../components/ui";
import type { RootStackParamList } from "../../navigation/types";
import { theme } from "../../theme";
import { learningReadinessScore, rankedTopics } from "../progress/analytics";

function greeting(name?: string) {
  const hour = new Date().getHours();
  const salutation =
    hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
  return `${salutation}${name ? `, ${name.split(/\s+/)[0]}` : ""} 👋`;
}

export function HomeScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, "Home">) {
  const sessionId = useAppStore((state) => state.currentSessionId);
  const identity = useAppStore((state) => state.learnerIdentity);
  const progress = useQuery({
    queryKey: ["progress"],
    queryFn: () => learningApi.progress(identity),
    enabled: Boolean(identity),
  });
  const learning = useQuery({
    queryKey: ["continue-learning", identity],
    queryFn: () => learningApi.continueLearning(identity),
    enabled: Boolean(identity),
    retry: 1,
  });
  const profile = useQuery({
    queryKey: ["learner-profile", identity],
    queryFn: () => learningApi.profile(identity),
    enabled: Boolean(identity),
  });
  const settings = useQuery({
    queryKey: ["learner-settings", identity],
    queryFn: () => learningApi.settings(identity),
    enabled: Boolean(identity),
  });
  const subjects = useQuery({
    queryKey: ["study-subjects", identity],
    queryFn: () => learningApi.studySubjects(identity),
    enabled: Boolean(identity),
  });
  const taxonomy = useQuery({
    queryKey: ["subjects"],
    queryFn: () => learningApi.subjects(identity),
    enabled: Boolean(identity),
  });
  const history = useQuery({
    queryKey: ["mock-history"],
    queryFn: () => learningApi.mockHistory(identity),
    enabled: Boolean(identity),
  });
  const answered =
    progress.data?.reduce((sum, item) => sum + item.questionsAnswered, 0) ?? 0;
  const correct =
    progress.data?.reduce((sum, item) => sum + item.correctAnswers, 0) ?? 0;
  const accuracy = answered
    ? Math.round((correct * 100) / answered)
    : undefined;
  const readiness = learningReadinessScore({
    subjects: subjects.data ?? [],
    topicProgress: progress.data ?? [],
    mockHistory: history.data ?? [],
  });
  const weakest = rankedTopics(progress.data ?? []).at(-1);
  const topicNames = Object.fromEntries(
    (taxonomy.data ?? []).flatMap((subject) =>
      subject.topics.map((topic) => [topic.id, topic.name]),
    ),
  );
  const dailyGoal = settings.data?.dailyQuestionGoal;
  const today = settings.data?.questionsAnsweredToday ?? 0;
  const dailyPercent = dailyGoal
    ? Math.min(100, Math.round((today * 100) / dailyGoal))
    : 0;
  const refresh = () =>
    void Promise.all([
      progress.refetch(),
      learning.refetch(),
      profile.refetch(),
      settings.refetch(),
      subjects.refetch(),
      taxonomy.refetch(),
      history.refetch(),
    ]);
  const navigateTab = (tab: Tab) => {
    if (tab === "topics") navigation.navigate("StudySubjects");
    else if (tab === "exam") navigation.navigate("MockExam");
    else if (tab === "progress") navigation.navigate("Progress");
    else if (tab === "settings") navigation.navigate("Settings");
  };
  const continueLearning = () =>
    learning.data
      ? navigation.navigate("TopicLesson", {
          topicId: learning.data.topicId,
          topicTitle: learning.data.topicTitle,
          sectionId: learning.data.lastSectionId,
        })
      : navigation.navigate("StudySubjects");

  return (
    <View style={styles.page}>
      <Screen
        bottomInset
        refreshing={[
          progress,
          learning,
          profile,
          settings,
          subjects,
          taxonomy,
          history,
        ].some((query) => query.isRefetching)}
        onRefresh={refresh}
      >
        <AppHeader
          action="profile"
          onAction={() => navigation.navigate("Profile")}
        />
        <View style={styles.greeting}>
          <Text accessibilityRole="header" style={styles.greetingTitle}>
            {greeting(profile.data?.displayName)}
          </Text>
          <Text style={styles.greetingBody}>
            {answered
              ? `You have answered ${answered} questions with ${accuracy}% accuracy.`
              : "Build confidence one focused lesson at a time."}
          </Text>
        </View>
        <Card style={styles.readinessCard}>
          <View style={styles.readinessCopy}>
            <Eyebrow>LEARNING READINESS</Eyebrow>
            <Text style={styles.readinessTitle}>
              {readiness == null
                ? "Start building your baseline"
                : readiness >= 80
                  ? "Strong momentum"
                  : readiness >= 60
                    ? "Solid progress"
                    : "Keep building"}
            </Text>
            <Text style={styles.muted}>
              {readiness == null
                ? "Complete lessons and answer at least five practice questions to unlock your score."
                : "Based on curriculum completion, sampled practice accuracy, and recent mock results."}
            </Text>
            <Button
              label={learning.data ? "Continue learning" : "Start learning"}
              onPress={continueLearning}
            />
          </View>
          <ReadinessRing value={readiness} size={118} />
        </Card>
        {dailyGoal ? (
          <Card>
            <View style={styles.goalHeader}>
              <View>
                <Text style={styles.cardTitle}>Today&apos;s goal</Text>
                <Text style={styles.muted}>Daily question challenge</Text>
              </View>
              <Text style={styles.goalValue}>
                {today}/{dailyGoal}
              </Text>
            </View>
            <ProgressBar
              value={dailyPercent}
              accessibilityLabel={`Daily goal, ${today} of ${dailyGoal} questions`}
            />
            <Text style={styles.goalHint}>
              {today >= dailyGoal
                ? "Daily goal complete."
                : `${dailyGoal - today} ${dailyGoal - today === 1 ? "question" : "questions"} to reach today’s goal.`}
            </Text>
          </Card>
        ) : null}
        {sessionId ? (
          <ActionCard
            icon="play"
            title="Continue practice"
            description="Your active practice session is ready."
            onPress={() => navigation.navigate("Question", { sessionId })}
          />
        ) : null}
        <View style={styles.actionGrid}>
          <ActionCard
            icon="progress"
            title="Practice weak topics"
            description={
              weakest
                ? `${topicNames[weakest.topicId] ?? "Your lowest sampled topic"} · ${Math.round(weakest.accuracyPercentage)}% accuracy`
                : "Build enough practice history to identify focus areas"
            }
            onPress={() =>
              weakest
                ? navigation.navigate("PracticeSetup", {
                    mode: "TOPIC",
                    topicId: weakest.topicId,
                    topicName: topicNames[weakest.topicId],
                  })
                : navigation.navigate("Topics")
            }
          />
          <ActionCard
            icon="exam"
            title="Mock exam"
            description={history.data?.length
              ? `Latest ${history.data[0].percentage}% · Best ${Math.max(...history.data.map(item => item.percentage))}% · ${history.data.length} attempts`
              : "Take a complete exam using the active curriculum"}
            accent
            onPress={() => navigation.navigate("MockExam")}
          />
        </View>
        <SectionHeader title="Recommended for you" />
        {learning.data ? (
          <Card>
            <Eyebrow>{learning.data.subjectTitle.toUpperCase()}</Eyebrow>
            <Text style={styles.recommendationTitle}>
              {learning.data.topicTitle}
            </Text>
            <Text style={styles.muted}>
              {learning.data.completedSectionCount} of{" "}
              {learning.data.totalSectionCount} key facts complete
            </Text>
            <ProgressBar
              value={
                learning.data.totalSectionCount
                  ? (learning.data.completedSectionCount * 100) /
                    learning.data.totalSectionCount
                  : 0
              }
              accessibilityLabel={`${learning.data.completedSectionCount} of ${learning.data.totalSectionCount} key facts complete`}
            />
            <Button
              label="Continue lesson"
              variant="text"
              onPress={continueLearning}
            />
          </Card>
        ) : (
          <Card tone="soft">
            <Text style={styles.recommendationTitle}>
              Choose your first lesson
            </Text>
            <Text style={styles.muted}>
              Browse the published curriculum and start with any available
              topic.
            </Text>
            <Button
              label="Browse study topics"
              variant="text"
              onPress={() => navigation.navigate("StudySubjects")}
            />
          </Card>
        )}
        <SectionHeader title="Your progress" />
        <View style={styles.stats}>
          <View style={styles.stat}>
            <StatTile icon="progress" label="QUESTIONS" value={`${answered}`} />
          </View>
          <View style={styles.stat}>
            <StatTile
              icon="check"
              label="ACCURACY"
              value={accuracy == null ? "—" : `${accuracy}%`}
              tone="green"
            />
          </View>
        </View>
      </Screen>
      <BottomTabBar active="home" onNavigate={navigateTab} />
    </View>
  );
}

const styles = StyleSheet.create({
  page: { backgroundColor: theme.colors.background, flex: 1 },
  greeting: { gap: 4, marginBottom: theme.spacing.xs },
  greetingTitle: { color: theme.colors.text, ...theme.typography.heading },
  greetingBody: { color: theme.colors.muted, ...theme.typography.body },
  readinessCard: {
    alignItems: "center",
    flexDirection: "row",
    padding: theme.spacing.md,
  },
  readinessCopy: { flex: 1, gap: 10 },
  readinessTitle: {
    color: theme.colors.text,
    fontSize: 22,
    fontWeight: "700",
    lineHeight: 28,
  },
  muted: { color: theme.colors.muted, ...theme.typography.body },
  goalHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  cardTitle: { color: theme.colors.text, ...theme.typography.subheading },
  goalValue: { color: theme.colors.primary, ...theme.typography.label },
  goalHint: {
    color: theme.colors.muted,
    ...theme.typography.caption,
    fontStyle: "italic",
  },
  actionGrid: { gap: theme.spacing.xs },
  recommendationTitle: {
    color: theme.colors.text,
    fontSize: 20,
    fontWeight: "700",
    lineHeight: 27,
  },
  stats: { flexDirection: "row", gap: theme.spacing.xs },
  stat: { flex: 1 },
});
