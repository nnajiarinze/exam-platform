import type { LessonSection, StudyTopic } from "../../api/generated/types.gen";

export type TopicLearningState = "not-started" | "in-progress" | "completed";

export function topicLearningState(
  topic: Pick<StudyTopic, "completed" | "completedSectionCount">,
): TopicLearningState {
  if (topic.completed) return "completed";
  return topic.completedSectionCount > 0 ? "in-progress" : "not-started";
}

export function primaryLearningLabel(state: TopicLearningState) {
  if (state === "completed") return "Study again";
  if (state === "in-progress") return "Continue learning";
  return "Start learning";
}

export function practiceLabel(state: TopicLearningState) {
  return state === "completed" ? "Practice again" : "Practice";
}

export function lessonEntryOptions(state: TopicLearningState) {
  return {
    reviewMode: state === "completed",
    startAtBeginning: state === "completed",
  };
}

export function finalLessonActionLabel(reviewMode: boolean) {
  return reviewMode ? "Finished reviewing" : "Complete lesson";
}

export function initialLessonSectionIndex(
  sections: Pick<LessonSection, "sectionId">[],
  lastSectionId: string,
  startAtBeginning: boolean,
) {
  if (startAtBeginning) return 0;
  const index = sections.findIndex(
    (section) => section.sectionId === lastSectionId,
  );
  return Math.max(0, index);
}
