import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { learningApi } from "../../api/learningApi";
import { friendlyError } from "../../api/errors";
import { useAppStore } from "../../app/store";
import { AppHeader } from "../../components/AppHeader";
import { Screen } from "../../components/Screen";
import {
  EmptyState,
  ErrorState,
  Icon,
  Loading,
  ProgressBar,
} from "../../components/ui";
import type { RootStackParamList } from "../../navigation/types";
import { theme } from "../../theme";

export function StudyTopicsScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, "StudyTopics">) {
  const identity = useAppStore((state) => state.learnerIdentity);
  const [search, setSearch] = useState("");
  const query = useQuery({
    queryKey: ["study-topics", identity, route.params.subjectId],
    queryFn: () => learningApi.studyTopics(identity, route.params.subjectId),
    enabled: Boolean(identity),
  });
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase();
    return term
      ? (query.data ?? []).filter((topic) =>
          `${topic.title} ${topic.summary ?? ""}`
            .toLocaleLowerCase()
            .includes(term),
        )
      : (query.data ?? []);
  }, [query.data, search]);
  return (
    <Screen>
      <AppHeader
        onBack={() => navigation.goBack()}
        action="profile"
        onAction={() => navigation.navigate("Profile")}
      />
      <Text accessibilityRole="header" style={styles.title}>
        {route.params.subjectTitle}
      </Text>
      <Text style={styles.subtitle}>
        Choose a short lesson and continue at your own pace.
      </Text>
      <View style={styles.search}>
        <Icon name="search" size={20} color={theme.colors.outline} />
        <TextInput
          accessibilityLabel="Search lessons"
          onChangeText={setSearch}
          placeholder="Search lessons…"
          placeholderTextColor={theme.colors.outline}
          style={styles.searchInput}
          value={search}
        />
      </View>
      {query.isPending ? (
        <Loading label="Loading lessons…" />
      ) : query.isError ? (
        <ErrorState
          message={friendlyError(query.error)}
          retry={() => query.refetch()}
        />
      ) : filtered.length === 0 ? (
        <EmptyState
          message={
            search
              ? "No lessons match your search."
              : "This subject has no published lessons yet."
          }
        />
      ) : (
        <View style={styles.list}>
          {filtered.map((topic) => (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Open lesson ${topic.title}, ${topic.completionPercentage} percent complete`}
              key={topic.topicId}
              style={({ pressed }) => [styles.card, pressed && styles.pressed]}
              onPress={() =>
                navigation.navigate("TopicLesson", {
                  topicId: topic.topicId,
                  topicTitle: topic.title,
                })
              }
            >
              <View style={styles.row}>
                <View style={styles.icon}>
                  <Icon
                    name={topic.completed ? "check" : "topics"}
                    size={22}
                    color={
                      topic.completed
                        ? theme.colors.success
                        : theme.colors.primary
                    }
                  />
                </View>
                <Text style={styles.last}>
                  {topic.completed
                    ? "Completed"
                    : topic.completedSectionCount
                      ? "In progress"
                      : "New lesson"}
                </Text>
              </View>
              <Text accessibilityRole="header" style={styles.cardTitle}>
                {topic.title}
              </Text>
              {topic.summary ? (
                <Text numberOfLines={3} style={styles.body}>
                  {topic.summary}
                </Text>
              ) : null}
              <View style={styles.row}>
                <Text style={styles.meta}>Mastery</Text>
                <Text style={styles.percent}>
                  {Math.round(topic.completionPercentage)}%
                </Text>
              </View>
              <ProgressBar
                value={topic.completionPercentage}
                accessibilityLabel={`${topic.completionPercentage} percent complete`}
              />
              <Text style={styles.meta}>
                ◷ {Math.max(1, Math.ceil(topic.readingTimeSeconds / 60))} min ·{" "}
                {topic.keyFactCount} key facts
              </Text>
              <Text style={styles.action}>
                {topic.completedSectionCount
                  ? "Continue lesson"
                  : "Start lesson"}{" "}
                →
              </Text>
            </Pressable>
          ))}
        </View>
      )}
    </Screen>
  );
}
const styles = StyleSheet.create({
  title: { color: theme.colors.text, ...theme.typography.heading },
  subtitle: {
    color: theme.colors.muted,
    ...theme.typography.body,
    marginBottom: theme.spacing.xs,
  },
  search: {
    alignItems: "center",
    backgroundColor: theme.colors.surface,
    borderColor: theme.colors.border,
    borderRadius: theme.radii.lg,
    borderWidth: 1.5,
    flexDirection: "row",
    gap: 8,
    minHeight: 52,
    paddingHorizontal: 14,
  },
  searchInput: { color: theme.colors.text, flex: 1, ...theme.typography.body },
  list: { gap: theme.spacing.sm },
  card: {
    backgroundColor: theme.colors.surface,
    borderColor: theme.colors.divider,
    borderRadius: theme.radii.xl,
    borderWidth: 1,
    gap: 9,
    padding: theme.spacing.sm,
    ...theme.shadows.card,
  },
  pressed: { opacity: 0.8 },
  row: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  icon: {
    alignItems: "center",
    backgroundColor: theme.colors.surfaceLow,
    borderRadius: theme.radii.lg,
    height: 44,
    justifyContent: "center",
    width: 44,
  },
  last: { color: theme.colors.muted, ...theme.typography.caption },
  cardTitle: {
    color: theme.colors.text,
    fontSize: 20,
    fontWeight: "700",
    lineHeight: 27,
  },
  body: { color: theme.colors.muted, ...theme.typography.body },
  meta: { color: theme.colors.muted, ...theme.typography.caption },
  percent: { color: theme.colors.primary, ...theme.typography.label },
  action: {
    color: theme.colors.primary,
    ...theme.typography.label,
    marginTop: 4,
  },
});
