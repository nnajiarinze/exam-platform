import type {
  MockExamHistoryItem,
  StudySubject,
  TopicProgress,
} from "../../api/generated/types.gen";

export type LearningReadinessInput = {
  subjects: StudySubject[];
  topicProgress: TopicProgress[];
  mockHistory: MockExamHistoryItem[];
};

export function curriculumCompletion(subjects: StudySubject[]) {
  const total = subjects.reduce((sum, item) => sum + item.topicCount, 0);
  const completed = subjects.reduce(
    (sum, item) => sum + item.completedTopicCount,
    0,
  );
  return total ? Math.round((completed / total) * 100) : undefined;
}

export function practiceAccuracy(
  progress: TopicProgress[],
  minimumAnswers = 5,
) {
  const answered = progress.reduce(
    (sum, item) => sum + item.questionsAnswered,
    0,
  );
  const correct = progress.reduce((sum, item) => sum + item.correctAnswers, 0);
  return answered >= minimumAnswers
    ? Math.round((correct / answered) * 100)
    : undefined;
}

export function learningReadinessScore({
  subjects,
  topicProgress,
  mockHistory,
}: LearningReadinessInput) {
  const completion = curriculumCompletion(subjects);
  const accuracy = practiceAccuracy(topicProgress);
  const recentMocks = mockHistory
    .filter((item) => item.status === "SUBMITTED")
    .slice(0, 3);
  const mockScore = recentMocks.length
    ? Math.round(
        recentMocks.reduce((sum, item) => sum + item.percentage, 0) /
          recentMocks.length,
      )
    : undefined;
  const parts = [
    completion == null ? undefined : { value: completion, weight: 0.35 },
    accuracy == null ? undefined : { value: accuracy, weight: 0.35 },
    mockScore == null ? undefined : { value: mockScore, weight: 0.3 },
  ].filter((part): part is { value: number; weight: number } => Boolean(part));
  if (parts.length < 2) return undefined;
  const weight = parts.reduce((sum, part) => sum + part.weight, 0);
  return Math.round(
    parts.reduce((sum, part) => sum + part.value * part.weight, 0) / weight,
  );
}

export function rankedTopics(progress: TopicProgress[]) {
  return [...progress]
    .filter((item) => item.questionsAnswered >= 3)
    .sort((a, b) => b.accuracyPercentage - a.accuracyPercentage);
}
