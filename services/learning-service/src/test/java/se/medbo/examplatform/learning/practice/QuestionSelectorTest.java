package se.medbo.examplatform.learning.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.learning.shared.ApiException;

class QuestionSelectorTest {
    private final QuestionSelector selector = new QuestionSelector(new QuestionRandomizer() {
        @Override
        public <T> void shuffle(List<T> values) {
            // Preserve input order for deterministic assertions.
        }
    });

    @Test
    void avoidsKnowledgeFactVariantsWhenEnoughUniqueFactsExist() {
        var first = candidate("fact-a");
        var variant = candidate("fact-a");
        var second = candidate("fact-b");

        assertThat(selector.select(List.of(first, variant, second), 2))
                .containsExactly(first, second);
    }

    @Test
    void usesVariantOnlyWhenNeededToReachRequestedCount() {
        var first = candidate("fact-a");
        var variant = candidate("fact-a");
        var second = candidate("fact-b");

        assertThat(selector.select(List.of(first, variant, second), 3))
                .containsExactly(first, second, variant);
    }

    @Test
    void reportsInsufficientEligibleQuestions() {
        assertThatThrownBy(() -> selector.select(List.of(candidate("fact-a")), 2))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only 1");
    }

    private static QuestionSelector.QuestionCandidate candidate(String fact) {
        return new QuestionSelector.QuestionCandidate(UUID.randomUUID(), UUID.randomUUID(), fact);
    }

    @Test
    void filtersTopicModeAndAllowsAllTopicsForMixedMode() {
        UUID topic = UUID.randomUUID();
        var inTopic = new QuestionSelector.QuestionCandidate(UUID.randomUUID(), topic, "fact-a");
        var elsewhere = candidate("fact-b");

        assertThat(selector.selectForMode(List.of(inTopic, elsewhere), PracticeMode.TOPIC, topic, 1))
                .containsExactly(inTopic);
        assertThat(selector.selectForMode(List.of(inTopic, elsewhere), PracticeMode.MIXED, null, 2))
                .containsExactly(inTopic, elsewhere);
    }
}
