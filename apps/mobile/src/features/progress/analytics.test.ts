import {
  curriculumCompletion,
  learningReadinessScore,
  practiceAccuracy,
  rankedTopics,
} from "./analytics";

const subjects = [
  { subjectId: "s1", title: "Society", topicCount: 4, completedTopicCount: 2 },
];
const progress = [
  {
    topicId: "t1",
    questionsAnswered: 10,
    correctAnswers: 8,
    accuracyPercentage: 80,
    lastPractisedAt: "2026-01-01T00:00:00Z",
  },
];
const history = [
  {
    attemptId: "a1",
    name: "Mock",
    status: "SUBMITTED" as const,
    startedAt: "2026-01-01T00:00:00Z",
    durationSeconds: 60,
    score: 7,
    percentage: 70,
    passed: true,
    totalQuestions: 10,
  },
];

it("derives honest progress metrics from backend data", () => {
  expect(curriculumCompletion(subjects)).toBe(50);
  expect(practiceAccuracy(progress)).toBe(80);
  expect(
    learningReadinessScore({
      subjects,
      topicProgress: progress,
      mockHistory: history,
    }),
  ).toBe(67);
});

it("requires sufficient evidence and samples for readiness and ranking", () => {
  expect(
    practiceAccuracy([
      { ...progress[0], questionsAnswered: 2, correctAnswers: 2 },
    ]),
  ).toBeUndefined();
  expect(
    learningReadinessScore({
      subjects: [],
      topicProgress: progress,
      mockHistory: [],
    }),
  ).toBeUndefined();
  expect(rankedTopics([{ ...progress[0], questionsAnswered: 2 }])).toEqual([]);
});
