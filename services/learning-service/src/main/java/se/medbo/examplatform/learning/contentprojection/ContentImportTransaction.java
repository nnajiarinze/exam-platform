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
                   published_at, imported_at, release_type, approval_strategy, disclaimer, attribution)
                VALUES (:id, :externalId, :examId, :examVersionId, :version, :checksum, :status,
                        :publishedAt, :importedAt, :releaseType, :approvalStrategy, :disclaimer, :attribution)
                """)
                .params(releaseParameters(snapshot, releaseId, canonicalExamId, importedAt))
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
                if (topic.lessonSections() != null) {
                    for (var section : topic.lessonSections()) {
                        UUID sectionId = UUID.randomUUID();
                        jdbc.sql("""
                                INSERT INTO imported_lesson_section
                                  (id, external_section_id, external_section_version_id, content_release_id,
                                   topic_id, title, explanation, display_order)
                                VALUES (:id, :externalId, :versionId, :releaseId, :topicId, :title,
                                        :explanation, :displayOrder)
                                """).params(Map.of("id", sectionId, "externalId", section.id(),
                                "versionId", section.versionId(), "releaseId", releaseId, "topicId", topicId,
                                "title", section.title(), "explanation", section.explanation(),
                                "displayOrder", section.displayOrder())).update();
                        if (section.sourceLinks() != null) {
                            for (var source : section.sourceLinks()) {
                                jdbc.sql("""
                                        INSERT INTO imported_lesson_source
                                          (id, lesson_section_id, title, url)
                                        VALUES (:id, :sectionId, :title, :url)
                                        """).params(Map.of("id", UUID.randomUUID(), "sectionId", sectionId,
                                        "title", source.title(), "url", source.url())).update();
                            }
                        }
                    }
                }
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
                                   text, correct, feedback, sort_order)
                                VALUES (:id, :externalId, :questionId, :releaseId, :text, :correct, :feedback, :sortOrder)
                                """).params(new java.util.HashMap<>(Map.of("id", UUID.randomUUID(), "externalId", option.id(),
                                "questionId", questionId, "releaseId", releaseId,
                                "text", option.text(), "correct", option.correct(),
                                "sortOrder", option.sortOrder())))
                                .param("feedback", option.feedback(), java.sql.Types.VARCHAR).update();
                    }
                }
            }
        }
        carryLessonProgress(releaseId, canonicalExamId);
        return new ImportResult(releaseId, true, "IMPORTED");
    }

    private void carryLessonProgress(UUID releaseId,String examId){
        jdbc.sql("""
            INSERT INTO lesson_progress(id,learner_id,content_release_id,topic_id,last_section_id,
              completed_section_count,started_at,last_accessed_at,completed_at,carried_completion_at)
            SELECT gen_random_uuid(),progress.learner_id,:release,new_topic.id,
              CASE WHEN progress.completed_at IS NOT NULL THEN first_section.id ELSE resume_section.id END,
              CASE WHEN progress.completed_at IS NOT NULL THEN 0
                   ELSE LEAST(progress.completed_section_count,resume_section.display_order) END,
              progress.started_at,progress.last_accessed_at,NULL,progress.completed_at
            FROM imported_content_release previous
            JOIN imported_topic old_topic ON old_topic.content_release_id=previous.id
            JOIN lesson_progress progress ON progress.topic_id=old_topic.id
            JOIN imported_lesson_section old_last ON old_last.id=progress.last_section_id
            JOIN imported_topic new_topic ON new_topic.content_release_id=:release
              AND new_topic.external_topic_id=old_topic.external_topic_id
            JOIN LATERAL (
              SELECT section.id FROM imported_lesson_section section WHERE section.topic_id=new_topic.id
              ORDER BY section.display_order,section.id LIMIT 1
            ) first_section ON true
            JOIN LATERAL (
              SELECT section.id,section.display_order FROM imported_lesson_section section
              WHERE section.topic_id=new_topic.id
              ORDER BY CASE WHEN section.external_section_id=old_last.external_section_id THEN 0
                            WHEN section.display_order>=progress.completed_section_count THEN 1 ELSE 2 END,
                       CASE WHEN section.display_order>=progress.completed_section_count
                            THEN section.display_order ELSE 2147483647-section.display_order END,
                       section.id LIMIT 1
            ) resume_section ON true
            WHERE previous.exam_id=:exam AND previous.status='ACTIVE'
            ON CONFLICT(learner_id,content_release_id,topic_id) DO NOTHING
            """).param("release",releaseId).param("exam",examId).update();
    }

    private Map<String,Object> releaseParameters(ContentSnapshot snapshot,UUID releaseId,String canonicalExamId,Instant importedAt){var values=new java.util.HashMap<String,Object>();values.put("id",releaseId);values.put("externalId",snapshot.externalReleaseId());values.put("examId",canonicalExamId);values.put("examVersionId",snapshot.examVersionId());values.put("version",snapshot.releaseVersion());values.put("checksum",snapshot.checksum());values.put("status","IMPORTED");values.put("publishedAt",OffsetDateTime.ofInstant(snapshot.publishedAt(),ZoneOffset.UTC));values.put("importedAt",OffsetDateTime.ofInstant(importedAt,ZoneOffset.UTC));values.put("releaseType",snapshot.releaseType()==null?"PUBLIC":snapshot.releaseType());values.put("approvalStrategy",snapshot.approvalStrategy()==null?"MANUAL_REVIEW":snapshot.approvalStrategy());values.put("disclaimer",snapshot.disclaimer());values.put("attribution",snapshot.attribution());return values;}

    public record ImportResult(UUID releaseId, boolean imported, String status) {}
    private record ExistingRelease(UUID id, String checksum, String status) {}
}
