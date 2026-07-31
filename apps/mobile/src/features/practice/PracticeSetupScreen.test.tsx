import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, waitFor } from "@testing-library/react-native";
import { learningApi } from "../../api/learningApi";
import { resetStoreForTests } from "../../app/store";
import { PracticeSetupScreen } from "./PracticeSetupScreen";

it("caps topic practice at the number of eligible questions", async () => {
  resetStoreForTests();
  jest.spyOn(learningApi, "lesson").mockResolvedValue({
    topicId: "topic-a",
    title: "Chapter topic",
    summary: null,
    readingTimeSeconds: 60,
    relatedQuestionCount: 2,
    contentReleaseId: "release-a",
    version: "1",
    sections: [],
    progress: {
      lastSectionId: "section-a",
      completedSectionCount: 0,
      totalSectionCount: 1,
      completionPercentage: 0,
      completed: false,
      startedAt: null,
      lastAccessedAt: null,
      completedAt: null,
    },
  });
  const create = jest.spyOn(learningApi, "createSession").mockResolvedValue({
    sessionId: "session-a",
    mode: "TOPIC",
    topicId: "topic-a",
    status: "ACTIVE",
    answered: 0,
    total: 2,
    nextQuestion: null,
  });
  const navigation = { replace: jest.fn() };
  const client = new QueryClient({
    defaultOptions: {
      queries: { gcTime: Infinity, retry: false },
      mutations: { gcTime: Infinity, retry: false },
    },
  });
  const view = await render(
    <QueryClientProvider client={client}>
      <PracticeSetupScreen
        navigation={navigation as never}
        route={{ key: "practice", name: "PracticeSetup", params: { mode: "TOPIC", topicId: "topic-a", topicName: "Chapter topic" } } as never}
      />
    </QueryClientProvider>,
  );

  expect(await view.findByRole("radio", { name: "2" })).toBeTruthy();
  expect(view.queryByRole("radio", { name: "3" })).toBeNull();
  await act(async () => fireEvent.press(view.getByText("Start session")));
  await waitFor(() => expect(create).toHaveBeenCalledWith("test-learner", expect.objectContaining({ questionCount: 2 })));
  await waitFor(() => expect(navigation.replace).toHaveBeenCalled());

  await view.unmount();
  client.clear();
  jest.restoreAllMocks();
});
