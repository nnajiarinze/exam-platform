package se.medbo.examplatform.content.release;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReleaseOptionOrderingPolicyTest {
    private final ReleaseOptionOrderingPolicy policy = new ReleaseOptionOrderingPolicy();

    @Test
    void balancesElevenQuestionsDeterministicallyWithoutRunsOrDominance() {
        var questions = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> new ReleaseOptionOrderingPolicy.QuestionShape(
                        UUID.nameUUIDFromBytes(("question-" + index).getBytes()), index == 4 ? 3 : 4))
                .toList();

        var first = policy.assignCorrectPositions("sverige-i-fokus-v1", "internal-v2.2", questions);
        var second = policy.assignCorrectPositions("sverige-i-fokus-v1", "internal-v2.2", questions);

        assertThat(second).isEqualTo(first);
        var counts = new int[4];
        first.values().forEach(position -> counts[position]++);
        assertThat(counts).containsOnly(2, 3);
    }

    @Test
    void preservesStableIdentityAndCorrectnessAtEveryFourOptionPosition() {
        var options = List.of(new Option("correct", true), new Option("one", false),
                new Option("two", false), new Option("three", false));
        UUID questionId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        for (int position = 0; position < 4; position++) {
            var ordered = policy.order("corpus", "release", questionId, options, Option::correct, position);
            assertThat(ordered.get(position).id()).isEqualTo("correct");
            assertThat(ordered).containsExactlyInAnyOrderElementsOf(options);
            assertThat(policy.order("corpus", "release", questionId, options, Option::correct, position))
                    .isEqualTo(ordered);
        }
    }

    @Test
    void providerOrderDoesNotControlReleasedCorrectPositionForThreeOrFourOptions() {
        for (int size : List.of(3, 4)) {
            for (int providerCorrectPosition = 0; providerCorrectPosition < size; providerCorrectPosition++) {
                var options = new ArrayList<Option>();
                for (int index = 0; index < size; index++) {
                    options.add(new Option("option-" + index, index == providerCorrectPosition));
                }
                int target = size - 1 - providerCorrectPosition;
                var ordered = policy.order("corpus", "release",
                        UUID.nameUUIDFromBytes((size + "-" + providerCorrectPosition).getBytes()),
                        options, Option::correct, target);
                assertThat(ordered.get(target).correct()).isTrue();
            }
        }
    }

    private record Option(String id, boolean correct) {}
}
