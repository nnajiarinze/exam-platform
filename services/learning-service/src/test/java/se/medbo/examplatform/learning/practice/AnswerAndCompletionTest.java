package se.medbo.examplatform.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AnswerAndCompletionTest {
    @Test
    void evaluatesImportedCorrectnessFlag() {
        assertThat(AnswerEvaluator.isCorrect(true)).isTrue();
        assertThat(AnswerEvaluator.isCorrect(false)).isFalse();
    }

    @Test
    void multipleChoiceRequiresTheExactSetRegardlessOfOrder() {
        assertThat(AnswerEvaluator.isExactMatch(java.util.List.of("a", "c"), java.util.List.of("c", "a"))).isTrue();
        assertThat(AnswerEvaluator.isExactMatch(java.util.List.of("a"), java.util.List.of("a", "c"))).isFalse();
        assertThat(AnswerEvaluator.isExactMatch(java.util.List.of("a", "b", "c"), java.util.List.of("a", "c"))).isFalse();
        assertThat(AnswerEvaluator.isExactMatch(java.util.List.of("a", "a"), java.util.List.of("a"))).isFalse();
    }

    @Test
    void completesOnlyAfterFinalAnswer() {
        assertThat(SessionCompletion.isComplete(1, 2)).isFalse();
        assertThat(SessionCompletion.isComplete(2, 2)).isTrue();
        assertThatThrownBy(() -> SessionCompletion.isComplete(3, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
