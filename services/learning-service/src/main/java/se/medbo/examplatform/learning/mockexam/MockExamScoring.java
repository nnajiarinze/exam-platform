package se.medbo.examplatform.learning.mockexam;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MockExamScoring {
    private MockExamScoring() {}

    public static Score calculate(int correct, int total, BigDecimal passingPercentage) {
        if (correct < 0 || total < 1 || correct > total || passingPercentage.signum() < 0
                || passingPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Invalid mock exam score input");
        }
        var percentage = BigDecimal.valueOf(correct).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new Score(correct, total - correct, percentage, percentage.compareTo(passingPercentage) >= 0);
    }

    public record Score(int correct, int incorrect, BigDecimal percentage, boolean passed) {}
}
