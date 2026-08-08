import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { appConfig } from "../../api/config";
import { friendlyError } from "../../api/errors";
import { learningApi } from "../../api/learningApi";
import { useAppStore } from "../../app/store";
import { Screen } from "../../components/Screen";
import { AppHeader } from "../../components/AppHeader";
import { EmptyState, ErrorState, Loading } from "../../components/ui";
import type { RootStackParamList } from "../../navigation/types";

const MAX_PRACTICE_QUESTIONS = 50;

export function TopicPracticeStartScreen({ navigation, route }: NativeStackScreenProps<RootStackParamList, "TopicPracticeStart">) {
  const identity = useAppStore((state) => state.learnerIdentity);
  const setSession = useAppStore((state) => state.setSession);
  const started = useRef(false);
  const active = useRef(true);
  const topic = useQuery({
    queryKey: ["practice-topic", identity, route.params.topicId],
    queryFn: () => learningApi.lesson(identity, route.params.topicId),
    enabled: Boolean(identity),
    retry: 1,
  });
  const session = useMutation({
    mutationFn: (questionCount: number) => learningApi.createSession(identity, {
      examId: appConfig.examId,
      mode: "TOPIC",
      topicId: route.params.topicId,
      questionCount,
    }),
    onSuccess: (created) => {
      if (!active.current) return;
      setSession(created.sessionId, route.params.topicName);
      navigation.replace("Question", { sessionId: created.sessionId });
    },
    onError: () => { started.current = false; },
  });

  useEffect(() => {
    const available = topic.data?.relatedQuestionCount;
    if (!started.current && topic.isFetchedAfterMount && !topic.isError && available !== undefined && available > 0) {
      started.current = true;
      session.mutate(Math.min(available, MAX_PRACTICE_QUESTIONS));
    }
  }, [topic.data?.relatedQuestionCount, topic.isError, topic.isFetchedAfterMount, session.mutate]);

  useEffect(() => {
    active.current = true;
    return () => { active.current = false; };
  }, []);

  const retrySession = async () => {
    started.current = true;
    const refreshed = await topic.refetch();
    const available = refreshed.data?.relatedQuestionCount;
    if (available !== undefined && available > 0) {
      session.mutate(Math.min(available, MAX_PRACTICE_QUESTIONS));
    } else {
      started.current = false;
    }
  };

  const header = <AppHeader onBack={() => { active.current = false; navigation.goBack(); }} />;
  if (!identity) return <Screen>{header}<ErrorState message="No learner identity is configured." /></Screen>;
  if (topic.isError) return <Screen>{header}<ErrorState message={friendlyError(topic.error)} retry={() => topic.refetch()} /></Screen>;
  if (topic.data?.relatedQuestionCount === 0) return <Screen>{header}<EmptyState message="No practice questions are available for this topic yet." /></Screen>;
  if (session.isError) return <Screen>{header}<ErrorState message={friendlyError(session.error)} retry={() => void retrySession()} /></Screen>;
  return <Screen scroll={false}>{header}<Loading label={topic.isPending ? "Checking available questions…" : "Starting practice…"} /></Screen>;
}
