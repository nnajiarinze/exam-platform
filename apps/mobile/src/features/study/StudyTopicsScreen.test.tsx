import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react-native";
import { learningApi } from "../../api/learningApi";
import { resetStoreForTests } from "../../app/store";
import { StudyTopicsScreen } from "./StudyTopicsScreen";

const topics = [
  "Geografi, klimat och natur",
  "Sveriges indelning",
  "Befolkning",
  "Naturresurser",
  "Klimatförändringar",
].map((title, index) => ({
  topicId: `topic-${index + 1}`,
  title,
  summary: `${title} summary`,
  keyFactCount: index + 1,
  readingTimeSeconds: 60,
  relatedQuestionCount: 1,
  completedSectionCount: index === 0 ? 1 : 0,
  completionPercentage: index === 0 ? 100 : 0,
  completed: index === 0,
}));

it("renders all five Chapter 1 topics including completed topics", async () => {
  resetStoreForTests();
  jest.spyOn(learningApi, "studyTopics").mockResolvedValue(topics);
  const client = new QueryClient({
    defaultOptions: { queries: { gcTime: Infinity, retry: false } },
  });
  const view = await render(
    <QueryClientProvider client={client}>
      <StudyTopicsScreen
        navigation={{ goBack: jest.fn(), navigate: jest.fn() } as never}
        route={{
          key: "chapter-one",
          name: "StudyTopics",
          params: { subjectId: "land-et-sverige", subjectTitle: "Landet Sverige" },
        } as never}
      />
    </QueryClientProvider>,
  );

  for (const topic of topics) expect(await view.findByText(topic.title)).toBeTruthy();
  expect(view.getByText("Completed")).toBeTruthy();
  expect(view.getByText("Study again")).toBeTruthy();
  expect(view.getByText("Practice again")).toBeTruthy();

  await view.unmount();
  client.clear();
  jest.restoreAllMocks();
});
