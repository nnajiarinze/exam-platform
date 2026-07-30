import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { learningApi } from "../../api/learningApi";
import { friendlyError } from "../../api/errors";
import { useAppStore } from "../../app/store";
import { AppHeader } from "../../components/AppHeader";
import { BottomTabBar, type Tab } from "../../components/BottomTabBar";
import { Eyebrow, SectionHeader } from "../../components/design";
import { Screen } from "../../components/Screen";
import {
  Button,
  Card,
  EmptyState,
  ErrorState,
  Icon,
  Loading,
  ProgressBar,
} from "../../components/ui";
import type { RootStackParamList } from "../../navigation/types";
import { theme } from "../../theme";

export function StudySubjectsScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, "StudySubjects">) {
  const identity = useAppStore((state) => state.learnerIdentity);
  const [search, setSearch] = useState("");
  const query = useQuery({
    queryKey: ["study-subjects", identity],
    queryFn: () => learningApi.studySubjects(identity),
    enabled: Boolean(identity),
  });
  const learning = useQuery({
    queryKey: ["continue-learning", identity],
    queryFn: () => learningApi.continueLearning(identity),
    enabled: Boolean(identity),
    retry: 1,
  });
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    return term
      ? (query.data ?? []).filter((subject) =>
          subject.title.toLocaleLowerCase().includes(term),
        )
      : (query.data ?? []);
  }, [query.data, search]);
  const tabs = (tab: Tab) => {
    if (tab === "home") navigation.navigate("Home");
    else if (tab === "exam") navigation.navigate("MockExam");
    else if (tab === "progress") navigation.navigate("Progress");
    else if (tab === "settings") navigation.navigate("Settings");
  };
  return (
    <View style={styles.page}>
      <Screen
        bottomInset
        refreshing={query.isRefetching}
        onRefresh={() => void query.refetch()}
      >
        <AppHeader action="help" />
        <View style={styles.search}>
          <Icon name="search" size={20} color={theme.colors.outline} />
          <TextInput
            accessibilityLabel="Search study subjects"
            autoCapitalize="none"
            clearButtonMode="while-editing"
            onChangeText={setSearch}
            placeholder="Search topics, laws, or history…"
            placeholderTextColor={theme.colors.outline}
            style={styles.searchInput}
            value={search}
          />
        </View>
        {learning.data ? (
          <>
            <Eyebrow>RECOMMENDED NEXT LESSON</Eyebrow>
            <Card tone="primary" style={styles.recommended}>
              <Text style={styles.recommendedSubject}>
                {learning.data.subjectTitle.toUpperCase()}
              </Text>
              <Text style={styles.recommendedTitle}>
                {learning.data.topicTitle}
              </Text>
              <Text style={styles.recommendedMeta}>
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
              />
              <Button
                label="Continue lesson"
                variant="accent"
                onPress={() =>
                  navigation.navigate("TopicLesson", {
                    topicId: learning.data!.topicId,
                    topicTitle: learning.data!.topicTitle,
                    sectionId: learning.data!.lastSectionId,
                  })
                }
              />
            </Card>
          </>
        ) : null}
        <SectionHeader title="Curriculum subjects" />
        {query.isPending ? (
          <Loading label="Loading subjects…" />
        ) : query.isError ? (
          <ErrorState
            message={friendlyError(query.error)}
            retry={() => query.refetch()}
          />
        ) : filtered.length === 0 ? (
          <EmptyState
            message={
              search
                ? "No subjects match your search."
                : "No published lessons are available yet."
            }
          />
        ) : (
          <View style={styles.list}>
            {filtered.map((subject, index) => {
              const percent = subject.topicCount
                ? (subject.completedTopicCount * 100) / subject.topicCount
                : 0;
              return (
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={`Open ${subject.title}, ${Math.round(percent)} percent complete`}
                  key={subject.subjectId}
                  style={({ pressed }) => [
                    styles.card,
                    pressed && styles.pressed,
                  ]}
                  onPress={() =>
                    navigation.navigate("StudyTopics", {
                      subjectId: subject.subjectId,
                      subjectTitle: subject.title,
                    })
                  }
                >
                  <View style={styles.cardTop}>
                    <View style={styles.icon}>
                      <Icon
                        name={index % 2 ? "progress" : "topics"}
                        size={24}
                      />
                    </View>
                    <Text style={styles.meta}>
                      {subject.completedTopicCount}/{subject.topicCount} topics
                    </Text>
                  </View>
                  <Text accessibilityRole="header" style={styles.cardTitle}>
                    {subject.title}
                  </Text>
                  <View style={styles.progressLabel}>
                    <Text style={styles.meta}>Mastery</Text>
                    <Text style={styles.percent}>{Math.round(percent)}%</Text>
                  </View>
                  <ProgressBar
                    value={percent}
                    accessibilityLabel={`${Math.round(percent)} percent complete`}
                  />
                  <Text style={styles.action}>View lessons →</Text>
                </Pressable>
              );
            })}
          </View>
        )}
      </Screen>
      <BottomTabBar active="topics" onNavigate={tabs} />
    </View>
  );
}
const styles = StyleSheet.create({
  page: { backgroundColor: theme.colors.background, flex: 1 },
  search: {
    alignItems: "center",
    backgroundColor: theme.colors.surface,
    borderColor: theme.colors.border,
    borderRadius: theme.radii.lg,
    borderWidth: 1.5,
    flexDirection: "row",
    gap: 10,
    minHeight: 52,
    paddingHorizontal: 14,
  },
  searchInput: { color: theme.colors.text, flex: 1, ...theme.typography.body },
  recommended: { gap: 10, padding: theme.spacing.md },
  recommendedSubject: {
    color: theme.colors.primaryFixed,
    ...theme.typography.caption,
  },
  recommendedTitle: {
    color: theme.colors.onPrimary,
    ...theme.typography.subheading,
  },
  recommendedMeta: {
    color: theme.colors.primaryFixed,
    ...theme.typography.body,
  },
  list: { gap: theme.spacing.sm },
  card: {
    backgroundColor: theme.colors.surface,
    borderColor: theme.colors.divider,
    borderRadius: theme.radii.xl,
    borderWidth: 1,
    gap: 10,
    padding: theme.spacing.sm,
    ...theme.shadows.card,
  },
  pressed: { opacity: 0.8, transform: [{ scale: 0.99 }] },
  cardTop: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  icon: {
    alignItems: "center",
    backgroundColor: theme.colors.surfaceLow,
    borderRadius: theme.radii.lg,
    height: 48,
    justifyContent: "center",
    width: 48,
  },
  cardTitle: {
    color: theme.colors.text,
    fontSize: 19,
    fontWeight: "700",
    lineHeight: 26,
  },
  meta: { color: theme.colors.muted, ...theme.typography.caption },
  progressLabel: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  percent: { color: theme.colors.primary, ...theme.typography.label },
  action: {
    color: theme.colors.primary,
    ...theme.typography.label,
    marginTop: 4,
  },
});
