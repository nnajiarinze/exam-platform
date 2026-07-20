package se.medbo.examplatform.learning.contentprojection;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;
import se.medbo.examplatform.learning.shared.ExternalExamIdentifier;

@Component
public class ContentImportTransaction {
    private final JdbcClient jdbc;
    private final Clock clock;

    @Autowired
    public ContentImportTransaction(JdbcClient jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    ContentImportTransaction(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importSnapshot(ContentSnapshot snapshot) {
        String canonicalExamId = ExternalExamIdentifier.normalize(snapshot.examId());
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:examVersionId))")
                .param("examVersionId", snapshot.examVersionId()).query((rs, row) -> true).single();

        var existing = jdbc.sql("SELECT id, checksum, status FROM imported_content_release WHERE external_release_id = :id")
                .param("id", snapshot.externalReleaseId())
                .query((rs, row) -> new ExistingRelease(rs.getObject("id", UUID.class), rs.getString("checksum"),
                        rs.getString("status")))
                .optional();
        if (existing.isPresent() && !"FAILED".equals(existing.get().status())) {
            if (existing.get().checksum().equals(snapshot.checksum())) {
                return new ImportResult(existing.get().id(), false, existing.get().status());
            }
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_RELEASE_ALREADY_IMPORTED",
                    "Content release identifier was already imported with different content");
        }
        existing.ifPresent(value -> jdbc.sql("DELETE FROM imported_content_release WHERE id = :id")
                .param("id", value.id()).update());

        UUID releaseId = UUID.randomUUID();
        Instant importedAt = clock.instant();
        jdbc.sql("""
                INSERT INTO imported_content_release
                  (id, external_release_id, exam_id, exam_version_id, release_version, checksum, status,
                   published_at, imported_at)
                VALUES (:id, :externalId, :examId, :examVersionId, :version, :checksum, :status,
                        :publishedAt, :importedAt)
                """)
                .params(Map.of("id", releaseId, "externalId", snapshot.externalReleaseId(),
                        "examId", canonicalExamId, "examVersionId", snapshot.examVersionId(),
                        "version", snapshot.releaseVersion(), "checksum", snapshot.checksum(),
                        "status", "IMPORTED",
                        "publishedAt", OffsetDateTime.ofInstant(snapshot.publishedAt(), ZoneOffset.UTC),
                        "importedAt", OffsetDateTime.ofInstant(importedAt, ZoneOffset.UTC)))
                .update();

        for (var subject : snapshot.subjects()) {
            UUID subjectId = UUID.randomUUID();
            jdbc.sql("INSERT INTO imported_subject (id, external_subject_id, content_release_id, name, sort_order) "
                            + "VALUES (:id, :externalId, :releaseId, :name, :sortOrder)")
                    .params(Map.of("id", subjectId, "externalId", subject.id(), "releaseId", releaseId,
                            "name", subject.name(), "sortOrder", subject.sortOrder())).update();
            for (var topic : subject.topics()) {
                UUID topicId = UUID.randomUUID();
                var topicParams = new java.util.HashMap<String, Object>();
                topicParams.put("id", topicId);
                topicParams.put("externalId", topic.id());
                topicParams.put("subjectId", subjectId);
                topicParams.put("releaseId", releaseId);
                topicParams.put("name", topic.name());
                topicParams.put("description", topic.description());
                topicParams.put("sortOrder", topic.sortOrder());
                jdbc.sql("""
                        INSERT INTO imported_topic
                          (id, external_topic_id, subject_id, content_release_id, name, description, sort_order)
                        VALUES (:id, :externalId, :subjectId, :releaseId, :name, :description, :sortOrder)
                        """).params(topicParams).update();
                for (var question : topic.questions()) {
                    UUID questionId = UUID.randomUUID();
                    var questionParams = new java.util.HashMap<String, Object>();
                    questionParams.put("id", questionId);
                    questionParams.put("externalId", question.id());
                    questionParams.put("versionId", question.versionId());
                    questionParams.put("releaseId", releaseId);
                    questionParams.put("topicId", topicId);
                    questionParams.put("factId", question.knowledgeFactId());
                    questionParams.put("type", question.questionType());
                    questionParams.put("prompt", question.prompt());
                    questionParams.put("explanation", question.explanation());
                    questionParams.put("language", question.language());
                    questionParams.put("difficulty", question.difficulty());
                    questionParams.put("active", question.active());
                    jdbc.sql("""
                            INSERT INTO imported_question
                              (id, external_question_id, external_question_version_id, content_release_id,
                               topic_id, knowledge_fact_id, question_type, prompt, explanation, language,
                               difficulty, active)
                            VALUES (:id, :externalId, :versionId, :releaseId, :topicId, :factId, :type,
                                    :prompt, :explanation, :language, :difficulty, :active)
                            """).params(questionParams).update();
                    for (var option : question.answerOptions()) {
                        jdbc.sql("""
                                INSERT INTO imported_answer_option
                                  (id, external_answer_option_id, question_id, content_release_id,
                                   text, correct, sort_order)
                                VALUES (:id, :externalId, :questionId, :releaseId, :text, :correct, :sortOrder)
                                """).params(Map.of("id", UUID.randomUUID(), "externalId", option.id(),
                                "questionId", questionId, "releaseId", releaseId,
                                "text", option.text(), "correct", option.correct(),
                                "sortOrder", option.sortOrder())).update();
                    }
                }
            }
        }
        return new ImportResult(releaseId, true, "IMPORTED");
    }

    public record ImportResult(UUID releaseId, boolean imported, String status) {}
    private record ExistingRelease(UUID id, String checksum, String status) {}
}
