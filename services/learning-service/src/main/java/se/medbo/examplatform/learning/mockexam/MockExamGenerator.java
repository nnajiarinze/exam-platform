package se.medbo.examplatform.learning.mockexam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.learning.shared.ApiException;

@Component
public class MockExamGenerator {
    private final Randomizer randomizer;

    public MockExamGenerator(Randomizer randomizer) {
        this.randomizer = randomizer;
    }

    public List<QuestionCandidate> generate(List<QuestionCandidate> eligible, List<TopicAllocation> allocations,
            int totalQuestions) {
        return generate(eligible, allocations, totalQuestions, null);
    }

    public List<QuestionCandidate> generate(List<QuestionCandidate> eligible, List<TopicAllocation> allocations,
            int totalQuestions, UUID attemptSeed) {
        int allocated = allocations.stream().mapToInt(TopicAllocation::questionCount).sum();
        if (allocated != totalQuestions) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_MOCK_BLUEPRINT",
                    "Topic allocations must equal the blueprint total question count");
        }
        var selected = new ArrayList<QuestionCandidate>(totalQuestions);
        var selectedQuestionIds = new HashSet<UUID>();
        for (var allocation : allocations) {
            var candidates = new ArrayList<>(eligible.stream()
                    .filter(candidate -> candidate.topicId().equals(allocation.topicId()))
                    .filter(candidate -> !selectedQuestionIds.contains(candidate.id()))
                    .toList());
            order(candidates, attemptSeed, allocation.externalTopicId());
            if (candidates.size() < allocation.questionCount()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_MOCK_QUESTIONS",
                        "Topic %s requires %d questions but only %d are eligible"
                                .formatted(allocation.externalTopicId(), allocation.questionCount(),
                                        candidates.size()));
            }
            for (var candidate : candidates.subList(0, allocation.questionCount())) {
                selected.add(candidate);
                selectedQuestionIds.add(candidate.id());
            }
        }
        order(selected, attemptSeed, "exam");
        return List.copyOf(selected);
    }

    private void order(List<QuestionCandidate> values, UUID seed, String scope) {
        if (seed == null) {
            randomizer.shuffle(values);
            return;
        }
        values.sort(Comparator.comparing(value -> digest(seed + ":" + scope + ":" + value.id())));
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public interface Randomizer {
        <T> void shuffle(List<T> values);
    }

    public record QuestionCandidate(UUID id, UUID topicId, String knowledgeFactId) {}
    public record TopicAllocation(UUID topicId, String externalTopicId, int questionCount) {}
}
