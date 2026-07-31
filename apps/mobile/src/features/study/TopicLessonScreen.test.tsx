import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react-native";
import { learningApi } from "../../api/learningApi";
import { resetStoreForTests } from "../../app/store";
import { TopicLessonScreen } from "./TopicLessonScreen";

const completedProgress = {
  lastSectionId: "last",
  completedSectionCount: 3,
  totalSectionCount: 3,
  completionPercentage: 100,
  completed: true,
  startedAt: "2026-01-01T10:00:00Z",
  lastAccessedAt: "2026-01-01T10:05:00Z",
  completedAt: "2026-01-01T10:05:00Z",
};

const lesson = {
  topicId: "topic-a",
  title: "Democracy",
  summary: "How democracy works.",
  readingTimeSeconds: 180,
  relatedQuestionCount: 3,
  contentReleaseId: "release-a",
  version: "1",
  sections: [
    {
      sectionId: "first",
      title: "First section",
      explanation: "First explanation.",
      displayOrder: 0,
      sourceLinks: [],
    },
    {
      sectionId: "middle",
      title: "Middle section",
      explanation: "Middle explanation.",
      displayOrder: 1,
      sourceLinks: [],
    },
    {
      sectionId: "last",
      title: "Last section",
      explanation: "Last explanation.",
      displayOrder: 2,
      sourceLinks: [],
    },
  ],
  progress: completedProgress,
};

it("opens a completed lesson at section one without hiding its content", async () => {
  resetStoreForTests();
  jest.spyOn(learningApi, "lesson").mockResolvedValue(lesson);
  const update = jest
    .spyOn(learningApi, "updateLessonProgress")
    .mockResolvedValue(completedProgress);
  const navigation = {
    goBack: jest.fn(),
    navigate: jest.fn(),
  };
  const client = new QueryClient({
    defaultOptions: {
      queries: { gcTime: Infinity, retry: false },
      mutations: { gcTime: Infinity, retry: false },
    },
  });
  const view = await render(
    <QueryClientProvider client={client}>
      <TopicLessonScreen
        navigation={navigation as never}
        route={
          {
            key: "lesson",
            name: "TopicLesson",
            params: {
              topicId: "topic-a",
              topicTitle: "Democracy",
              reviewMode: true,
              startAtBeginning: true,
            },
          } as never
        }
      />
    </QueryClientProvider>,
  );

  expect(await view.findByText("First section")).toBeTruthy();
  expect(view.getByText("COMPLETED · REVIEWING")).toBeTruthy();
  expect(view.getByText(/3 of 3 key facts completed/)).toBeTruthy();
  expect(view.getByText("Next")).toBeTruthy();
  expect(view.getByText("Practice again")).toBeTruthy();

  await waitFor(() =>
    expect(update).toHaveBeenCalledWith(
      "test-learner",
      "topic-a",
      "first",
      false,
    ),
  );
  await waitFor(() => expect(view.getByText("Next")).toBeEnabled());
  await view.unmount();
  client.clear();
  jest.restoreAllMocks();
});
