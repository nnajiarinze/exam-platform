package se.medbo.examplatform.learning.practice;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.learning.shared.ApiException;

@Component
public class QuestionSelector {
    private final QuestionRandomizer randomizer;

    public QuestionSelector(QuestionRandomizer randomizer) {
        this.randomizer = randomizer;
    }

    public List<QuestionCandidate> select(List<QuestionCandidate> eligible, int count) {
        if (eligible.size() < count) {
            throw insufficient(count, eligible.size());
        }
        var shuffled = new ArrayList<>(eligible);
        randomizer.shuffle(shuffled);
        var selected = new ArrayList<QuestionCandidate>(count);
        var facts = new HashSet<String>();
        for (var question : shuffled) {
            if (facts.add(question.knowledgeFactId())) {
                selected.add(question);
                if (selected.size() == count) return List.copyOf(selected);
            }
        }
        for (var question : shuffled) {
            if (!selected.contains(question)) {
                selected.add(question);
                if (selected.size() == count) return List.copyOf(selected);
            }
        }
        throw insufficient(count, selected.size());
    }

    public List<QuestionCandidate> selectForMode(List<QuestionCandidate> eligible, PracticeMode mode,
            java.util.UUID topicId, int count) {
        var filtered = mode == PracticeMode.TOPIC
                ? eligible.stream().filter(candidate -> candidate.topicId().equals(topicId)).toList()
                : eligible;
        return select(filtered, count);
    }

    private static ApiException insufficient(int requested, int available) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_QUESTIONS",
                "Requested %d questions but only %d are eligible".formatted(requested, available));
    }

    public record QuestionCandidate(java.util.UUID id, java.util.UUID topicId, String knowledgeFactId) {}
}
