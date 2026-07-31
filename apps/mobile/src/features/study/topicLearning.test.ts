import {
  finalLessonActionLabel,
  initialLessonSectionIndex,
  lessonEntryOptions,
  practiceLabel,
  primaryLearningLabel,
  topicLearningState,
} from "./topicLearning";

describe("completed-topic learning actions", () => {
  it.each([
    [false, 0, "not-started", "Start learning", "Practice"],
    [false, 1, "in-progress", "Continue learning", "Practice"],
    [true, 3, "completed", "Study again", "Practice again"],
  ] as const)(
    "derives the available actions",
    (completed, completedSectionCount, state, learning, practice) => {
      const derived = topicLearningState({ completed, completedSectionCount });
      expect(derived).toBe(state);
      expect(primaryLearningLabel(derived)).toBe(learning);
      expect(practiceLabel(derived)).toBe(practice);
    },
  );

  it("starts Study again at the first lesson section", () => {
    const sections = [
      { sectionId: "first" },
      { sectionId: "second" },
      { sectionId: "last" },
    ];

    expect(initialLessonSectionIndex(sections, "last", true)).toBe(0);
    expect(initialLessonSectionIndex(sections, "second", false)).toBe(1);
    expect(lessonEntryOptions("completed")).toEqual({
      reviewMode: true,
      startAtBeginning: true,
    });
    expect(finalLessonActionLabel(true)).toBe("Finished reviewing");
  });
});
