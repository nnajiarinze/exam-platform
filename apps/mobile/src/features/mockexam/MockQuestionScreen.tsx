import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Alert, StyleSheet, Text, View } from "react-native";
import { friendlyError } from "../../api/errors";
import { learningApi } from "../../api/learningApi";
import { useAppStore } from "../../app/store";
import { Screen } from "../../components/Screen";
import { Button, ErrorState, Loading } from "../../components/ui";
import { AnswerOption } from "../../components/design";
import type { RootStackParamList } from "../../navigation/types";
import { theme } from "../../theme/theme";
import { formatCountdown, useCountdown } from "./countdown";
import { QuestionNavigator } from "./QuestionNavigator";

export function MockQuestionScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, "MockQuestion">) {
  const { attemptId, sequenceNumber } = route.params;
  const identity = useAppStore((s) => s.learnerIdentity);
  const setAttempt = useAppStore((s) => s.setMockAttempt);
  const queryClient = useQueryClient();
  const attempt = useQuery({
    queryKey: ["mock-attempt", attemptId],
    queryFn: () => learningApi.mockExam(identity, attemptId),
  });
  const question = useQuery({
    queryKey: ["mock-question", attemptId, sequenceNumber],
    queryFn: () =>
      learningApi.mockQuestion(identity, attemptId, sequenceNumber),
  });
  const [selected, setSelected] = useState<string[]>([]);
  useEffect(
    () => setSelected(question.data?.selectedOptionIds ?? []),
    [question.data?.attemptQuestionId, question.data?.selectedOptionIds],
  );
  const remaining = useCountdown(
    question.data?.remainingSeconds ?? attempt.data?.remainingSeconds ?? -1,
  );
  const answer = useMutation({
    mutationFn: (optionIds: string[]) =>
      learningApi.answerMockQuestion(
        identity,
        attemptId,
        question.data!.attemptQuestionId,
        optionIds,
        question.data!.answerVersion,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mock-attempt", attemptId] });
      queryClient.invalidateQueries({
        queryKey: ["mock-question", attemptId, sequenceNumber],
      });
    },
    onError: async () => {
      const refreshed = await question.refetch();
      setSelected(refreshed.data?.selectedOptionIds ?? []);
    },
  });
  const flag = useMutation({
    mutationFn: (flagged: boolean) =>
      learningApi.flagMockQuestion(
        identity,
        attemptId,
        question.data!.attemptQuestionId,
        flagged,
        question.data!.questionVersion,
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["mock-attempt", attemptId] });
      queryClient.invalidateQueries({
        queryKey: ["mock-question", attemptId, sequenceNumber],
      });
    },
  });
  const submit = useMutation({
    mutationFn: () => learningApi.submitMockExam(identity, attemptId),
    onSuccess: () => {
      setAttempt(undefined);
      navigation.replace("MockResults", { attemptId });
    },
  });
  useEffect(() => {
    if (attempt.data && attempt.data.status !== "ACTIVE") {
      setAttempt(undefined);
      navigation.replace("MockResults", { attemptId });
    }
  }, [attempt.data?.status, attemptId, navigation, setAttempt]);
  useEffect(() => {
    if (question.data && remaining === 0 && !submit.isPending) submit.mutate();
  }, [remaining, question.data]);
  const go = (sequence: number) =>
    navigation.replace("MockQuestion", { attemptId, sequenceNumber: sequence });
  const confirmSubmit = () => {
    const answered = attempt.data?.answered ?? 0;
    const flagged =
      attempt.data?.questions.filter((item) => item.flagged).length ?? 0;
    const total = attempt.data?.totalQuestions ?? 0;
    Alert.alert(
      "Submit mock examination?",
      `${answered} answered · ${total - answered} unanswered · ${flagged} flagged\n${formatCountdown(Math.max(0, remaining))} remaining\n\nYou cannot change answers after submission.`,
      [
        { text: "Return to exam", style: "cancel" },
        {
          text: "Submit",
          style: "destructive",
          onPress: () => submit.mutate(),
        },
      ],
    );
  };
  if (attempt.isPending || question.isPending)
    return (
      <Screen scroll={false}>
        <Loading label="Loading mock examination…" />
      </Screen>
    );
  if (attempt.isError || question.isError)
    return (
      <Screen>
        <ErrorState
          message={friendlyError(attempt.error ?? question.error)}
          retry={() => {
            attempt.refetch();
            question.refetch();
          }}
        />
      </Screen>
    );
  const displayedRemaining = Math.max(0, remaining);
  return (
    <Screen>
      <View style={styles.header}>
        <Text style={styles.progress}>
          QUESTION {sequenceNumber} OF {question.data.totalQuestions}
        </Text>
        <View style={styles.timerPill}>
          <Text
            accessibilityLabel={`${displayedRemaining} seconds remaining`}
            style={styles.timer}
          >
            {formatCountdown(displayedRemaining)}
          </Text>
        </View>
      </View>
      <QuestionNavigator
        questions={attempt.data.questions}
        current={sequenceNumber}
        onSelect={go}
      />
      <Text accessibilityRole="header" style={styles.prompt}>
        {question.data.prompt}
      </Text>
      {question.data.questionType === "MULTIPLE_CHOICE" && (
        <Text style={styles.hint}>Select all answers that apply.</Text>
      )}
      <View style={styles.options}>
        {question.data.answerOptions.map((option, index) => (
          <AnswerOption
            disabled={answer.isPending}
            index={index}
            key={option.id}
            multiple={question.data.questionType === "MULTIPLE_CHOICE"}
            onPress={() =>
              setSelected((current) =>
                question.data.questionType === "MULTIPLE_CHOICE"
                  ? current.includes(option.id)
                    ? current.filter((id) => id !== option.id)
                    : [...current, option.id]
                  : [option.id],
              )
            }
            selected={selected.includes(option.id)}
            text={option.text}
          />
        ))}
      </View>
      <Button
        label={answer.isPending ? "Saving answer…" : "Save answer"}
        disabled={!selected.length || answer.isPending}
        onPress={() => answer.mutate(selected)}
      />
      {(answer.isError || flag.isError || submit.isError) && (
        <ErrorState
          message={friendlyError(answer.error ?? flag.error ?? submit.error)}
        />
      )}
      <Button
        label={question.data.flagged ? "Remove review flag" : "Flag for review"}
        disabled={flag.isPending}
        onPress={() => flag.mutate(!question.data.flagged)}
      />
      <View style={styles.actions}>
        <Button
          label="Previous"
          disabled={sequenceNumber <= 1}
          onPress={() => go(sequenceNumber - 1)}
        />
        <Button
          label="Next"
          disabled={sequenceNumber >= question.data.totalQuestions}
          onPress={() => go(sequenceNumber + 1)}
        />
      </View>
      <Button
        label={submit.isPending ? "Submitting…" : "Submit examination"}
        disabled={submit.isPending}
        onPress={confirmSubmit}
      />
    </Screen>
  );
}
const styles = StyleSheet.create({
  header: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  progress: { color: theme.colors.primary, ...theme.typography.label },
  timerPill: {
    backgroundColor: theme.colors.surfaceContainer,
    borderRadius: theme.radii.full,
    paddingHorizontal: 14,
    paddingVertical: 8,
  },
  timer: { color: theme.colors.accentStrong, fontSize: 18, fontWeight: "700" },
  prompt: {
    color: theme.colors.text,
    fontSize: 28,
    fontWeight: "800",
    lineHeight: 36,
    marginVertical: theme.spacing.sm,
  },
  hint: { color: theme.colors.muted, ...theme.typography.body },
  options: { gap: theme.spacing.sm },
  actions: { flexDirection: "row", gap: 8 },
});
