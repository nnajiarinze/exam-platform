package se.medbo.examplatform.learning.practice;

import java.util.Collection;
import se.medbo.examplatform.learning.shared.ExactSetScoring;

public final class AnswerEvaluator {
    private AnswerEvaluator() {}

    public static boolean isCorrect(boolean selectedOptionIsCorrect) {
        return selectedOptionIsCorrect;
    }

    public static boolean isExactMatch(Collection<String> selectedOptionIds, Collection<String> correctOptionIds) {
        return ExactSetScoring.matches(selectedOptionIds, correctOptionIds);
    }
}
