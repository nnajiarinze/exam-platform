import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useMutation, useQuery } from "@tanstack/react-query";
import { StyleSheet, Text, View } from "react-native";
import { friendlyError } from "../../api/errors";
import { learningApi } from "../../api/learningApi";
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
import { Button, Card, ErrorState, Loading } from "../../components/ui";
import type { RootStackParamList } from "../../navigation/types";
import { theme } from "../../theme";
import { learningReadinessScore } from "../progress/analytics";
import { officialStudyMaterialAttribution } from "../../contentDisclaimer";

export function MockExamScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, "MockExam">) {
  const identity = useAppStore((state) => state.learnerIdentity);
  const current = useAppStore((state) => state.currentMockAttemptId);
  const setAttempt = useAppStore((state) => state.setMockAttempt);
  const configuration = useQuery({
    queryKey: ["mock-exam-configuration"],
    queryFn: learningApi.mockExamConfiguration,
  });
  const progress = useQuery({
    queryKey: ["progress"],
    queryFn: () => learningApi.progress(identity),
    enabled: Boolean(identity),
  });
  const subjects = useQuery({
    queryKey: ["study-subjects", identity],
    queryFn: () => learningApi.studySubjects(identity),
    enabled: Boolean(identity),
  });
  const history = useQuery({
    queryKey: ["mock-history"],
    queryFn: () => learningApi.mockHistory(identity),
    enabled: Boolean(identity),
  });
  const readiness = learningReadinessScore({
    subjects: subjects.data ?? [],
    topicProgress: progress.data ?? [],
    mockHistory: history.data ?? [],
  });
  const best = history.data?.length
    ? Math.max(...history.data.map((item) => item.percentage))
    : undefined;
  const mutation = useMutation({
    mutationFn: () => learningApi.createMockExam(identity),
    onSuccess: (attempt) => {
      setAttempt(attempt.attemptId);
      navigation.replace("MockQuestion", {
        attemptId: attempt.attemptId,
        sequenceNumber: 1,
      });
    },
  });
  const navigateTab = (tab: Tab) => {
    if (tab === "home") navigation.navigate("Home");
    else if (tab === "topics") navigation.navigate("StudySubjects");
    else if (tab === "progress") navigation.navigate("Progress");
    else if (tab === "settings") navigation.navigate("Settings");
  };
  const config = configuration.data;
  if (configuration.isPending)
    return (
      <Screen scroll={false}>
        <Loading label="Loading mock examination…" />
      </Screen>
    );
  return (
    <View style={styles.page}>
      <Screen bottomInset>
        <AppHeader
          onBack={() => navigation.goBack()}
          action="profile"
          onAction={() => navigation.navigate("Profile")}
        />
        <Eyebrow>
          {readiness != null && readiness >= 70
            ? "READY TO PRACTICE"
            : "TIMED PRACTICE"}
        </Eyebrow>
        <Text accessibilityRole="header" style={styles.title}>
          {config?.name ?? "Mock examination"}
        </Text>
        <Text style={styles.description}>
          {config?.description ??
            "Practice using the active published content under timed conditions."}
        </Text>
        <Text style={styles.notice}>{officialStudyMaterialAttribution.en}</Text>
        <Card style={styles.readiness}>
          <ReadinessRing value={readiness} size={146} />
          <Text style={styles.readinessTitle}>
            {readiness == null
              ? "Build your baseline"
              : readiness >= 80
                ? "Strong preparation"
                : readiness >= 60
                  ? "Solid progress"
                  : "More practice recommended"}
          </Text>
          <Text style={styles.muted}>
            Learning readiness is a progress summary, not a probability of
            passing.
          </Text>
        </Card>
        {config ? (
          <Card>
            <SectionHeader title="Exam requirements" />
            <View style={styles.metrics}>
              <View style={styles.metric}>
                <StatTile
                  icon="exam"
                  label="QUESTIONS"
                  value={`${config.questionCount}`}
                />
              </View>
              <View style={styles.metric}>
                <StatTile
                  icon="progress"
                  label="DURATION"
                  value={`${config.durationMinutes} min`}
                />
              </View>
            </View>
            <View style={styles.metrics}>
              <View style={styles.metric}>
                <StatTile
                  icon="check"
                  label="PASSING"
                  value={`${config.passPercentage}%`}
                  tone="green"
                />
              </View>
              <View style={styles.metric}>
                <StatTile
                  icon="trophy"
                  label="BEST SCORE"
                  value={best == null ? "—" : `${best}%`}
                  tone="gold"
                />
              </View>
            </View>
            <Text style={styles.notice}>
              Results are saved to your progress history. Unanswered questions
              count as incorrect.
            </Text>
          </Card>
        ) : null}
        <SectionHeader title="Before you begin" />
        <View style={styles.tips}>
          <Card tone="soft">
            <Text style={styles.tipTitle}>○ No distractions</Text>
            <Text style={styles.muted}>
              Find a quiet place for {config?.durationMinutes ?? "the full"}{" "}
              minutes.
            </Text>
          </Card>
          <Card tone="soft">
            <Text style={styles.tipTitle}>▤ Read carefully</Text>
            <Text style={styles.muted}>
              Questions may be single-choice or multiple-choice.
            </Text>
          </Card>
          <Card tone="soft">
            <Text style={styles.tipTitle}>⚑ Review before submitting</Text>
            <Text style={styles.muted}>
              Flag questions and change saved answers while the attempt is
              active.
            </Text>
          </Card>
        </View>
        {configuration.isError ? (
          <ErrorState
            message={friendlyError(configuration.error)}
            retry={() => configuration.refetch()}
          />
        ) : null}
        {mutation.isError ? (
          <ErrorState message={friendlyError(mutation.error)} />
        ) : null}
        {current ? (
          <Button
            label="Continue active mock"
            onPress={() =>
              navigation.navigate("MockQuestion", {
                attemptId: current,
                sequenceNumber: 1,
              })
            }
          />
        ) : (
          <Button
            label={mutation.isPending ? "Starting…" : "Start mock exam"}
            disabled={mutation.isPending}
            onPress={() => mutation.mutate()}
          />
        )}
        <Button
          label="View mock history"
          variant="text"
          onPress={() => navigation.navigate("MockHistory")}
        />
      </Screen>
      <BottomTabBar active="exam" onNavigate={navigateTab} />
    </View>
  );
}
const styles = StyleSheet.create({
  page: { backgroundColor: theme.colors.background, flex: 1 },
  title: { color: theme.colors.primary, ...theme.typography.display },
  description: { color: theme.colors.muted, ...theme.typography.bodyLarge },
  readiness: { alignItems: "center", padding: theme.spacing.md },
  readinessTitle: {
    color: theme.colors.primary,
    ...theme.typography.subheading,
    textAlign: "center",
  },
  muted: {
    color: theme.colors.muted,
    ...theme.typography.body,
    textAlign: "center",
  },
  metrics: { flexDirection: "row", gap: theme.spacing.xs },
  metric: { flex: 1 },
  notice: {
    borderTopColor: theme.colors.divider,
    borderTopWidth: 1,
    color: theme.colors.muted,
    ...theme.typography.caption,
    paddingTop: theme.spacing.sm,
  },
  tips: { gap: theme.spacing.xs },
  tipTitle: { color: theme.colors.primary, ...theme.typography.label },
});
