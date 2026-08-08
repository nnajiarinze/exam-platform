import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, waitFor } from "@testing-library/react-native";
import { learningApi } from "../../api/learningApi";
import { resetStoreForTests } from "../../app/store";
import { QuestionScreen } from "./QuestionScreen";

const question = (sequenceNumber: number, totalQuestionCount: number) => ({
  sessionQuestionId: `sq-${sequenceNumber}`, questionId: `q-${sequenceNumber}`,
  prompt: `Question ${sequenceNumber}?`, questionType: "SINGLE_CHOICE" as const,
  answerOptions: [{ id: "yes", text: "Yes" }, { id: "no", text: "No" }],
  selectedOptionIds: [], sequenceNumber, totalQuestionCount,
});

async function setup() {
  resetStoreForTests();
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const navigation = { goBack: jest.fn(), replace: jest.fn() };
  const view = await render(<QueryClientProvider client={client}><QuestionScreen
    navigation={navigation as never}
    route={{ key: "question", name: "Question", params: { sessionId: "session-a" } } as never}
  /></QueryClientProvider>);
  return { navigation, view };
}

beforeEach(() => jest.restoreAllMocks());

it("shows accessible current-session progress and updates for the next question", async () => {
  jest.spyOn(learningApi, "nextQuestion").mockResolvedValueOnce(question(1, 7)).mockResolvedValueOnce(question(2, 7));
  jest.spyOn(learningApi, "submitAnswer").mockResolvedValue({ correct: true, selectedOptionIds: ["yes"], correctOptionIds: ["yes"], explanation: "Correct.", optionFeedback: [], sessionProgress: { answered: 1, total: 7 } });
  const { view } = await setup();
  expect(await view.findByText("1 / 7")).toBeTruthy();
  expect(view.getByLabelText("Question 1 of 7")).toBeTruthy();
  fireEvent.press(view.getByText("Yes"));
  await waitFor(() => expect(view.getByRole("radio", { name: /Yes/ }).props.accessibilityState.checked).toBe(true));
  fireEvent.press(view.getByRole("button", { name: "Submit answer" }));
  await view.findByText("Continue");
  fireEvent.press(view.getByText("Continue"));
  expect(await view.findByText("2 / 7")).toBeTruthy();
  expect(view.getByLabelText("Question 2 of 7")).toBeTruthy();
});

it("handles a single-question session and reaches variable-total results", async () => {
  jest.spyOn(learningApi, "nextQuestion").mockResolvedValue(question(1, 1));
  jest.spyOn(learningApi, "submitAnswer").mockResolvedValue({ correct: true, selectedOptionIds: ["yes"], correctOptionIds: ["yes"], explanation: "Correct.", optionFeedback: [], sessionProgress: { answered: 1, total: 1 } });
  const { navigation, view } = await setup();
  expect(await view.findByText("1 / 1")).toBeTruthy();
  expect(view.getByLabelText("Question 1 of 1")).toHaveAccessibilityValue({ min: 0, max: 100, now: 100 });
  fireEvent.press(view.getByText("Yes"));
  await waitFor(() => expect(view.getByRole("radio", { name: /Yes/ }).props.accessibilityState.checked).toBe(true));
  fireEvent.press(view.getByRole("button", { name: "Submit answer" }));
  await view.findByText("View results");
  fireEvent.press(view.getByText("View results"));
  await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("SessionComplete", { total: 1 }));
});

it("preserves normal back navigation", async () => {
  jest.spyOn(learningApi, "nextQuestion").mockResolvedValue(question(1, 3));
  const { navigation, view } = await setup();
  fireEvent.press(await view.findByLabelText("Exit practice"));
  expect(navigation.goBack).toHaveBeenCalled();
});
