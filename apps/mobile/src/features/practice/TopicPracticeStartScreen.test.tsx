import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import { learningApi } from "../../api/learningApi";
import { resetStoreForTests, useAppStore } from "../../app/store";
import { TopicPracticeStartScreen } from "./TopicPracticeStartScreen";

const lesson = (relatedQuestionCount: number) => ({
  topicId: "topic-a", title: "Chapter topic", summary: null, readingTimeSeconds: 60,
  relatedQuestionCount, contentReleaseId: "release-a", version: "1", sections: [],
  progress: { lastSectionId: "section-a", completedSectionCount: 0, totalSectionCount: 1,
    completionPercentage: 0, completed: false, startedAt: null, lastAccessedAt: null, completedAt: null },
});

async function setup(client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })) {
  const navigation = { replace: jest.fn(), goBack: jest.fn() };
  const view = await render(<QueryClientProvider client={client}><TopicPracticeStartScreen
    navigation={navigation as never}
    route={{ key: "practice", name: "TopicPracticeStart", params: { topicId: "topic-a", topicName: "Chapter topic" } } as never}
  /></QueryClientProvider>);
  return { client, navigation, view };
}

beforeEach(() => { resetStoreForTests(); jest.restoreAllMocks(); });

it("automatically starts topic practice with every eligible question", async () => {
  jest.spyOn(learningApi, "lesson").mockResolvedValue(lesson(7));
  const create = jest.spyOn(learningApi, "createSession").mockResolvedValue({ sessionId: "session-a", mode: "TOPIC", topicId: "topic-a", status: "ACTIVE", answered: 0, total: 7, nextQuestion: null });
  const { navigation, view } = await setup();
  await waitFor(() => expect(create).toHaveBeenCalledWith("test-learner", expect.objectContaining({ topicId: "topic-a", mode: "TOPIC", questionCount: 7 })));
  expect(view.queryByText("Select the number of questions.")).toBeNull();
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("Question", { sessionId: "session-a" }));
});

it("supports one question and caps at the existing API limit", async () => {
  const create = jest.spyOn(learningApi, "createSession").mockResolvedValue({ sessionId: "session-a", mode: "TOPIC", status: "ACTIVE", answered: 0, total: 1, nextQuestion: null });
  jest.spyOn(learningApi, "lesson").mockResolvedValueOnce(lesson(1));
  const first = await setup();
  await waitFor(() => expect(create).toHaveBeenLastCalledWith("test-learner", expect.objectContaining({ questionCount: 1 })));
  await first.view.unmount(); first.client.clear();
  jest.spyOn(learningApi, "lesson").mockResolvedValueOnce(lesson(72));
  await setup();
  await waitFor(() => expect(create).toHaveBeenLastCalledWith("test-learner", expect.objectContaining({ questionCount: 50 })));
});

it("shows the unavailable state without creating a session and preserves back navigation", async () => {
  jest.spyOn(learningApi, "lesson").mockResolvedValue(lesson(0));
  const create = jest.spyOn(learningApi, "createSession");
  const { navigation, view } = await setup();
  expect(await view.findByText("No practice questions are available for this topic yet.")).toBeTruthy();
  expect(create).not.toHaveBeenCalled();
  fireEvent.press(view.getByLabelText("Go back"));
  expect(navigation.goBack).toHaveBeenCalled();
});

it("waits for a fresh topic response instead of using a stale cached count", async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  client.setQueryData(["practice-topic", "test-learner", "topic-a"], lesson(9));
  jest.spyOn(learningApi, "lesson").mockResolvedValue(lesson(2));
  const create = jest.spyOn(learningApi, "createSession").mockResolvedValue({ sessionId: "session-a", mode: "TOPIC", status: "ACTIVE", answered: 0, total: 2, nextQuestion: null });
  await setup(client);
  await waitFor(() => expect(create).toHaveBeenCalledWith("test-learner", expect.objectContaining({ questionCount: 2 })));
  expect(create).not.toHaveBeenCalledWith("test-learner", expect.objectContaining({ questionCount: 9 }));
});

it("does not navigate or persist a session after the learner backs out", async () => {
  let resolveSession!: (value: Awaited<ReturnType<typeof learningApi.createSession>>) => void;
  jest.spyOn(learningApi, "lesson").mockResolvedValue(lesson(3));
  jest.spyOn(learningApi, "createSession").mockReturnValue(new Promise((resolve) => { resolveSession = resolve; }));
  const { navigation, view } = await setup();
  await waitFor(() => expect(learningApi.createSession).toHaveBeenCalled());
  fireEvent.press(view.getByLabelText("Go back"));
  resolveSession({ sessionId: "late-session", mode: "TOPIC", status: "ACTIVE", answered: 0, total: 3, nextQuestion: null });
  await waitFor(() => expect(navigation.goBack).toHaveBeenCalled());
  expect(navigation.replace).not.toHaveBeenCalled();
  expect(useAppStore.getState().currentSessionId).toBeUndefined();
});

it("refreshes the eligible count before retrying a failed creation", async () => {
  const fetchLesson = jest.spyOn(learningApi, "lesson")
    .mockResolvedValueOnce(lesson(3))
    .mockResolvedValueOnce(lesson(2));
  const create = jest.spyOn(learningApi, "createSession")
    .mockRejectedValueOnce(new Error("insufficient questions"))
    .mockResolvedValueOnce({ sessionId: "session-a", mode: "TOPIC", status: "ACTIVE", answered: 0, total: 2, nextQuestion: null });
  const { navigation, view } = await setup();
  expect(await view.findByText("insufficient questions")).toBeTruthy();
  fireEvent.press(view.getByText("Try again"));
  await waitFor(() => expect(fetchLesson).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(create).toHaveBeenLastCalledWith("test-learner", expect.objectContaining({ questionCount: 2 })));
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("Question", { sessionId: "session-a" }));
});
