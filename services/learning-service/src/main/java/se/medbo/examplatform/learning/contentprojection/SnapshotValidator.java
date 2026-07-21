package se.medbo.examplatform.learning.contentprojection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.learning.shared.ApiException;
import se.medbo.examplatform.learning.shared.ExternalExamIdentifier;

@Component
public class SnapshotValidator {
    private final ObjectMapper objectMapper;

    public SnapshotValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(ContentSnapshot snapshot) {
        ExternalExamIdentifier.normalize(snapshot.examId());
        if (!java.util.Set.of("1.0", "1.1").contains(snapshot.schemaVersion()) || !"PUBLISHED".equals(snapshot.releaseStatus())) {
            invalid("Only published content snapshot schemas 1.0 and 1.1 are accepted");
        }
        var subjectIds = new HashSet<String>();
        var topicIds = new HashSet<String>();
        var questionIds = new HashSet<String>();
        var questionVersions = new HashSet<String>();
        var answerOptionIds = new HashSet<String>();
        for (var subject : snapshot.subjects()) {
            if (!subjectIds.add(subject.id())) invalid("Duplicate subject identifier");
            for (var topic : subject.topics()) {
                if (!topicIds.add(topic.id())) invalid("Duplicate topic identifier");
                for (var question : topic.questions()) {
                    if (!questionIds.add(question.id())) invalid("Duplicate question identifier");
                    if (!questionVersions.add(question.versionId())) invalid("Duplicate question version identifier");
                    if (!java.util.Set.of("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE").contains(question.questionType())) {
                        invalid("Unsupported question type");
                    }
                    long correct = question.answerOptions().stream().filter(ContentSnapshot.AnswerOption::correct).count();
                    if (question.answerOptions().size() < 2 || question.answerOptions().size() > 6
                            || ("MULTIPLE_CHOICE".equals(question.questionType()) ? correct < 1 : correct != 1)) {
                        invalid("Question answer structure is invalid for its type");
                    }
                    var optionIds = new HashSet<String>();
                    var optionSortOrders = new HashSet<Integer>();
                    for (var option : question.answerOptions()) {
                        if (!optionIds.add(option.id()) || !answerOptionIds.add(option.id())) {
                            invalid("Duplicate answer option identifier");
                        }
                        if (!optionSortOrders.add(option.sortOrder())) {
                            invalid("Duplicate answer option sort order");
                        }
                    }
                    var flaggedCorrect = question.answerOptions().stream().filter(ContentSnapshot.AnswerOption::correct)
                            .map(ContentSnapshot.AnswerOption::id).collect(java.util.stream.Collectors.toSet());
                    if ("1.1".equals(snapshot.schemaVersion())) {
                        if (question.correctOptionIds() == null || question.correctOptionIds().isEmpty()
                                || question.correctOptionIds().size() != new HashSet<>(question.correctOptionIds()).size()
                                || !flaggedCorrect.equals(new HashSet<>(question.correctOptionIds()))) {
                            invalid("Correct option identifiers must exactly match the correct option flags");
                        }
                    }
                }
            }
        }
        String actual = checksum(snapshot);
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                snapshot.checksum().toLowerCase().getBytes(StandardCharsets.US_ASCII))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONTENT_CHECKSUM_MISMATCH",
                    "Content snapshot checksum does not match");
        }
    }

    public String checksum(ContentSnapshot snapshot) {
        try {
            var material = new SnapshotWithoutChecksum(snapshot.schemaVersion(), snapshot.externalReleaseId(),
                    snapshot.examId(), snapshot.examVersionId(), snapshot.releaseVersion(), snapshot.releaseStatus(),
                    snapshot.publishedAt(), snapshot.subjects());
            var tree = objectMapper.valueToTree(material);
            if ("1.0".equals(snapshot.schemaVersion())) {
                tree.findParents("correctOptionIds").forEach(node -> ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .remove("correctOptionIds"));
                tree.findParents("feedback").forEach(node -> ((com.fasterxml.jackson.databind.node.ObjectNode) node)
                        .remove("feedback"));
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(tree)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot calculate snapshot checksum", exception);
        }
    }

    private static void invalid(String message) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CONTENT_SNAPSHOT", message);
    }

    private record SnapshotWithoutChecksum(String schemaVersion, String externalReleaseId, String examId,
            String examVersionId, String releaseVersion, String releaseStatus, java.time.Instant publishedAt,
            java.util.List<ContentSnapshot.Subject> subjects) {}
}
