package se.medbo.examplatform.learning.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record TopicProgressCalculation(int questionsAnswered, int correctAnswers, BigDecimal accuracyPercentage) {
    public static TopicProgressCalculation initial(boolean correct) {
        return new TopicProgressCalculation(1, correct ? 1 : 0,
                correct ? new BigDecimal("100.00") : new BigDecimal("0.00"));
    }

    public TopicProgressCalculation answer(boolean correct) {
        int answered = questionsAnswered + 1;
        int correctTotal = correctAnswers + (correct ? 1 : 0);
        BigDecimal accuracy = BigDecimal.valueOf(correctTotal)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(answered), 2, RoundingMode.HALF_UP);
        return new TopicProgressCalculation(answered, correctTotal, accuracy);
    }
}

