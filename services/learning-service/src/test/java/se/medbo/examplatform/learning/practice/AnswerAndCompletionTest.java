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
    void completesOnlyAfterFinalAnswer() {
        assertThat(SessionCompletion.isComplete(1, 2)).isFalse();
        assertThat(SessionCompletion.isComplete(2, 2)).isTrue();
        assertThatThrownBy(() -> SessionCompletion.isComplete(3, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
