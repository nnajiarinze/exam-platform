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

@Component
public class SnapshotValidator {
    private final ObjectMapper objectMapper;

    public SnapshotValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(ContentSnapshot snapshot) {
        if (!"1.0".equals(snapshot.schemaVersion()) || !"PUBLISHED".equals(snapshot.releaseStatus())) {
            invalid("Only published content snapshot schema 1.0 is accepted");
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
                    if (!"SINGLE_CHOICE".equals(question.questionType())) invalid("Unsupported question type");
                    long correct = question.answerOptions().stream().filter(ContentSnapshot.AnswerOption::correct).count();
                    if (question.answerOptions().size() < 2 || correct != 1) {
                        invalid("A single-choice question requires at least two options and exactly one correct option");
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(material)));
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
