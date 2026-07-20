package se.medbo.examplatform.content.question;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.content.shared.DomainException;

@Component
final class QuestionValidator {
    static final Set<String> TYPES = Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    void validate(QuestionService.QuestionInput input) {
        required(input.code(), "code"); required(input.questionText(), "questionText");
        if (input.learningObjectiveId() == null) fail("learningObjectiveId is required");
        if (!TYPES.contains(input.questionType())) fail("Unsupported questionType");
        if (!DIFFICULTIES.contains(input.difficulty())) fail("Unsupported difficulty");
        if (input.factIds() == null || input.factIds().isEmpty()) fail("At least one approved knowledge fact is required");
        if (input.factIds().stream().distinct().count() != input.factIds().size()) fail("Duplicate knowledge facts are not allowed");
        if ("TRUE_FALSE".equals(input.questionType())) {
            if (input.trueFalseCorrect() == null) fail("trueFalseCorrect is required");
            return;
        }
        List<QuestionService.OptionInput> options = input.options();
        if (options == null || options.size() < 2 || options.size() > 6) fail("Questions require between two and six options");
        var texts = new HashSet<String>(); var orders = new HashSet<Integer>(); int correct = 0;
        for (var option : options) {
            required(option.text(), "Option text");
            if (option.displayOrder() == null || option.displayOrder() < 0 || !orders.add(option.displayOrder())) fail("Option displayOrder values must be unique non-negative integers");
            if (!texts.add(option.text().trim().toLowerCase())) fail("Duplicate options are not allowed");
            if (option.correct()) correct++;
        }
        if ("SINGLE_CHOICE".equals(input.questionType()) && correct != 1) fail("Single-choice questions require exactly one correct option");
        if ("MULTIPLE_CHOICE".equals(input.questionType()) && correct < 1) fail("Multiple-choice questions require at least one correct option");
    }

    private void required(String value, String name) { if (value == null || value.isBlank()) fail(name + " is required"); }
    private void fail(String message) { throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message); }
}
