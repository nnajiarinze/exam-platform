import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRef, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import type {
  AnswerResult,
  PracticeQuestion,
} from "../../api/generated/types.gen";
import { friendlyError } from "../../api/errors";
import { learningApi } from "../../api/learningApi";
import { useAppStore } from "../../app/store";
import { Screen } from "../../components/Screen";
import { Button, ErrorState, Icon, Loading } from "../../components/ui";
import type { RootStackParamList } from "../../navigation/types";
import { theme } from "../../theme";
import { QuestionCard } from "./QuestionCard";

export function QuestionScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, "Question">) {
  const identity = useAppStore((s) => s.learnerIdentity);
  const recordAnswer = useAppStore((s) => s.recordAnswer);
  const category = useAppStore((s) => s.currentPracticeLabel);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [result, setResult] = useState<AnswerResult>();
  const [advancing, setAdvancing] = useState(false);
  const [advanceError, setAdvanceError] = useState<unknown>();
  const nextQuestionRef = useRef<Promise<PracticeQuestion> | undefined>(
    undefined,
  );
  const queryClient = useQueryClient();
  const queryKey = ["next-question", route.params.sessionId] as const;
  const query = useQuery({
    queryKey,
    queryFn: () => learningApi.nextQuestion(identity, route.params.sessionId),
    retry: 1,
  });
  const mutation = useMutation({
    mutationFn: (optionIds: string[]) =>
      learningApi.submitAnswer(identity, route.params.sessionId, {
        sessionQuestionId: query.data!.sessionQuestionId,
        selectedOptionIds: optionIds,
      }),
    onSuccess: (answer) => {
      setResult(answer);
      recordAnswer(answer.correct);
      if (answer.sessionProgress.answered < answer.sessionProgress.total) {
        const pending = learningApi.nextQuestion(
          identity,
          route.params.sessionId,
        );
        void pending.catch(() => undefined);
        nextQuestionRef.current = pending;
      }
    },
  });
  const toggle = (id: string) =>
    setSelectedIds((current) =>
      query.data?.questionType === "MULTIPLE_CHOICE"
        ? current.includes(id)
          ? current.filter((value) => value !== id)
          : [...current, id]
        : [id],
    );
  const next = async () => {
    if (!result) return;
    if (result.sessionProgress.answered >= result.sessionProgress.total) {
      navigation.replace("SessionComplete", {
        total: result.sessionProgress.total,
      });
      return;
    }
    setAdvancing(true);
    setAdvanceError(undefined);
    try {
      const nextQuestion = await (nextQuestionRef.current ??
        learningApi.nextQuestion(identity, route.params.sessionId));
      queryClient.setQueryData(queryKey, nextQuestion);
      nextQuestionRef.current = undefined;
      setSelectedIds([]);
      setResult(undefined);
    } catch (error) {
      nextQuestionRef.current = undefined;
      setAdvanceError(error);
    } finally {
      setAdvancing(false);
    }
  };
  if (query.isPending)
    return (
      <Screen scroll={false}>
        <Loading label="Loading question…" />
      </Screen>
    );
  if (query.isError)
    return (
      <Screen>
        <ErrorState
          message={friendlyError(query.error)}
          retry={() => query.refetch()}
        />
      </Screen>
    );
  const actionLabel = advancing
    ? "Loading next question…"
    : !result
      ? mutation.isPending
        ? "Checking answer…"
        : "Submit answer"
      : result.sessionProgress.answered >= result.sessionProgress.total
        ? "View results"
        : "Continue";
  return (
    <View style={styles.page}>
      <Screen bottomInset>
        <View style={styles.focusHeader}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Exit practice"
            hitSlop={8}
            onPress={() => navigation.goBack()}
            style={styles.close}
          >
            <Icon name="close" size={30} />
          </Pressable>
          <Text numberOfLines={1} style={styles.category}>
            {category?.toUpperCase() ?? "PRACTICE"}
          </Text>
          <View style={styles.headerSpacer} />
        </View>
        <QuestionCard
          question={query.data}
          category={category}
          selectedIds={selectedIds}
          result={result}
          submitting={mutation.isPending}
          onSelect={toggle}
        />
        {mutation.isPending && <Loading label="Checking answer…" />}
        {mutation.isError && (
          <ErrorState
            message={friendlyError(mutation.error)}
            retry={
              selectedIds.length
                ? () => mutation.mutate(selectedIds)
                : undefined
            }
          />
        )}
        {Boolean(advanceError) && (
          <ErrorState message={friendlyError(advanceError)} />
        )}
      </Screen>
      <SafeAreaView edges={["bottom"]} style={styles.footer}>
        <Button
          label={actionLabel}
          disabled={
            advancing ||
            (!result && (!selectedIds.length || mutation.isPending))
          }
          onPress={() =>
            result
              ? void next()
              : selectedIds.length && mutation.mutate(selectedIds)
          }
        />
      </SafeAreaView>
    </View>
  );
}
const styles = StyleSheet.create({
  page: { backgroundColor: theme.colors.background, flex: 1 },
  focusHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    minHeight: 48,
  },
  close: {
    alignItems: "center",
    height: 44,
    justifyContent: "center",
    width: 44,
  },
  category: {
    color: theme.colors.muted,
    ...theme.typography.caption,
    letterSpacing: 1.4,
    maxWidth: "70%",
  },
  headerSpacer: { width: 44 },
  footer: {
    backgroundColor: theme.colors.background,
    borderTopColor: theme.colors.divider,
    borderTopWidth: 1,
    paddingHorizontal: theme.layout.screenGutter,
    paddingTop: 12,
  },
});
