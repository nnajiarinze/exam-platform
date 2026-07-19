package se.medbo.examplatform.learning.progress;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TopicProgressCalculationTest {
    @Test
    void calculatesAccuracyAcrossCorrectAndIncorrectAnswers() {
        var progress = TopicProgressCalculation.initial(true).answer(false).answer(true);

        assertThat(progress.questionsAnswered()).isEqualTo(3);
        assertThat(progress.correctAnswers()).isEqualTo(2);
        assertThat(progress.accuracyPercentage()).isEqualByComparingTo("66.67");
    }
}
