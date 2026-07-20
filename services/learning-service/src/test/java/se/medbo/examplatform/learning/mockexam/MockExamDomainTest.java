package se.medbo.examplatform.learning.mockexam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.learning.shared.ApiException;

class MockExamDomainTest {
    private final MockExamGenerator generator = new MockExamGenerator(new MockExamGenerator.Randomizer() {
        @Override public <T> void shuffle(List<T> values) { /* deterministic order */ }
    });

    @Test
    void generatesExactTopicAllocationWithoutDuplicateKnowledgeFacts() {
        UUID topicA = UUID.randomUUID(); UUID topicB = UUID.randomUUID();
        var questions = List.of(candidate(topicA, "fact-1"), candidate(topicA, "fact-2"),
                candidate(topicB, "fact-3"), candidate(topicB, "fact-4"));
        var selected = generator.generate(questions,
                List.of(new MockExamGenerator.TopicAllocation(topicA, "a", 2),
                        new MockExamGenerator.TopicAllocation(topicB, "b", 1)), 3);
        assertThat(selected).hasSize(3);
        assertThat(selected.stream().map(MockExamGenerator.QuestionCandidate::knowledgeFactId)).doesNotHaveDuplicates();
    }

    @Test
    void rejectsBlueprintWhenUniqueFactsAreInsufficient() {
        UUID topic = UUID.randomUUID();
        assertThatThrownBy(() -> generator.generate(
                List.of(candidate(topic, "same"), candidate(topic, "same")),
                List.of(new MockExamGenerator.TopicAllocation(topic, "topic", 2)), 2))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INSUFFICIENT_MOCK_QUESTIONS"));
    }

    @Test
    void scoresUsingConfiguredThreshold() {
        assertThat(MockExamScoring.calculate(3, 4, new BigDecimal("75.00")).passed()).isTrue();
        assertThat(MockExamScoring.calculate(2, 4, new BigDecimal("75.00")).passed()).isFalse();
        assertThat(MockExamScoring.calculate(2, 3, BigDecimal.ZERO).percentage()).isEqualByComparingTo("66.67");
    }

    @Test
    void calculatesExpiryFromServerTimestamps() {
        Instant start = Instant.parse("2026-01-01T10:00:00Z");
        assertThat(MockExamTimer.state(start, 30, start.plusSeconds(1799)).expired()).isFalse();
        var expired = MockExamTimer.state(start, 30, start.plusSeconds(1800));
        assertThat(expired.expired()).isTrue();
        assertThat(expired.remainingSeconds()).isZero();
        assertThat(expired.elapsedSeconds()).isEqualTo(1800);
    }

    private static MockExamGenerator.QuestionCandidate candidate(UUID topic, String fact) {
        return new MockExamGenerator.QuestionCandidate(UUID.randomUUID(), topic, fact);
    }
}
