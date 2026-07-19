package se.medbo.examplatform.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.medbo.examplatform.learning.contentprojection.ContentImportService;
import se.medbo.examplatform.learning.contentprojection.ContentSnapshot;
import se.medbo.examplatform.learning.contentprojection.SnapshotValidator;
import se.medbo.examplatform.learning.practice.PracticeMode;
import se.medbo.examplatform.learning.practice.PracticeService;
import se.medbo.examplatform.learning.progress.ProgressService;
import se.medbo.examplatform.learning.shared.ApiException;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "learning.identity.development-header-enabled=true",
        "learning.internal-api-key=test-internal-key"
})
class LearningServiceIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired JdbcClient jdbc;
    @Autowired ContentImportService importService;
    @Autowired SnapshotValidator snapshotValidator;
    @Autowired PracticeService practiceService;
    @Autowired ProgressService progressService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private UUID learnerId;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("""
                TRUNCATE bookmark, topic_progress, practice_response, practice_session_question,
                         practice_session, imported_answer_option, imported_question, imported_topic,
                         imported_subject, imported_content_release, learner_profile CASCADE
                """).update();
        learnerId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO learner_profile
                  (id, external_identity_id, display_name, interface_language, explanation_language,
                   created_at, updated_at)
                VALUES (:id, 'developer-learner', 'Developer', 'sv', 'en', now(), now())
                """).param("id", learnerId).update();
    }

    @Test
    void flywayInitializesAnEmptyLearningDatabase() {
        Integer migrations = jdbc.sql("SELECT count(*) FROM flyway_schema_history WHERE success")
                .query(Integer.class).single();
        assertThat(migrations).isPositive();
        assertThat(jdbc.sql("SELECT to_regclass('public.practice_session') IS NOT NULL")
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void internalHttpEndpointIsRegisteredAndImportsPublishedSnapshot() throws Exception {
        var snapshot = snapshot("http-release", "1");

        mockMvc.perform(post("/internal/v1/content-releases/import")
                        .header("X-Internal-Api-Key", "test-internal-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(snapshot)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseId").exists())
                .andExpect(jsonPath("$.imported").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        assertThat(jdbc.sql("""
                SELECT count(*) FROM imported_content_release
                WHERE external_release_id = 'http-release' AND status = 'ACTIVE'
                """).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM imported_question")
                .query(Integer.class).single()).isEqualTo(4);
    }

    @Test
    void internalImportRejectsMissingServiceAuthentication() throws Exception {
        mockMvc.perform(post("/internal/v1/content-releases/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(snapshot("unauthorized-release", "1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_AUTHENTICATION_REQUIRED"));
        assertThat(jdbc.sql("SELECT count(*) FROM imported_content_release")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void listsSubjectsAndTopicsFromTheActiveProjection() throws Exception {
        importService.importSnapshot(snapshot("content-list-release", "1"));

        mockMvc.perform(get("/api/v1/content/subjects")
                        .header("X-Learner-Identity", "developer-learner")
                        .queryParam("examId", "swedish-citizenship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("subject-a"))
                .andExpect(jsonPath("$[0].topics[0].id").value("topic-a"))
                .andExpect(jsonPath("$[1].topics[0].id").value("topic-b"));
    }

    @Test
    void importsIdempotentlyAndSupersedesWithoutBreakingHistoricalSession() {
        var releaseOne = snapshot("release-1", "1");
        var first = importService.importSnapshot(releaseOne);
        assertThat(importService.importSnapshot(releaseOne).imported()).isFalse();

        var session = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-a", PracticeMode.TOPIC, 2));
        var releaseTwo = snapshot("release-2", "2");
        importService.importSnapshot(releaseTwo);

        assertThat(jdbc.sql("SELECT status FROM imported_content_release WHERE id = :id")
                .param("id", first.releaseId()).query(String.class).single()).isEqualTo("SUPERSEDED");
        assertThat(jdbc.sql("SELECT count(*) FROM imported_content_release WHERE exam_version_id = 'exam-v1' AND status = 'ACTIVE'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM practice_session ps
                JOIN imported_content_release r ON r.id = ps.content_release_id
                WHERE ps.id = :sessionId AND r.external_release_id = 'release-1'
                """).param("sessionId", session.sessionId()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void importsAnOlderReleaseWithoutRollingBackTheActiveProjection() {
        var newer = importService.importSnapshot(snapshot("newer-release", "2",
                Instant.parse("2026-02-02T00:00:00Z")));
        var delayedOlder = importService.importSnapshot(snapshot("delayed-older-release", "1",
                Instant.parse("2026-02-01T00:00:00Z")));

        assertThat(delayedOlder.status()).isEqualTo("SUPERSEDED");
        assertThat(jdbc.sql("SELECT status FROM imported_content_release WHERE id = :id")
                .param("id", newer.releaseId()).query(String.class).single()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("""
                SELECT external_release_id FROM imported_content_release
                WHERE exam_version_id = 'exam-v1' AND status = 'ACTIVE'
                """).query(String.class).single()).isEqualTo("newer-release");
    }

    @Test
    void failedImportRecordsFailureAndActivatesNoPartialContent() {
        var valid = snapshot("bad-release", "1");
        var brokenQuestion = new ContentSnapshot.Question("q-bad", "q-bad-v1", "fact-bad", "SINGLE_CHOICE",
                "Broken?", "Broken explanation", "sv", null, true,
                List.of(new ContentSnapshot.AnswerOption("a", "A", true, 0),
                        new ContentSnapshot.AnswerOption("b", "B", true, 1)));
        var broken = withChecksum(new ContentSnapshot(valid.schemaVersion(), valid.externalReleaseId(), valid.examId(),
                valid.examVersionId(), valid.releaseVersion(), valid.releaseStatus(), valid.publishedAt(), "pending",
                List.of(new ContentSnapshot.Subject("subject", "Subject", 0,
                        List.of(new ContentSnapshot.Topic("topic", "Topic", null, 0, List.of(brokenQuestion)))))));

        assertThatThrownBy(() -> importService.importSnapshot(broken)).isInstanceOf(ApiException.class);
        assertThat(jdbc.sql("SELECT status FROM imported_content_release WHERE external_release_id = 'bad-release'")
                .query(String.class).single()).isEqualTo("FAILED");
        assertThat(jdbc.sql("SELECT count(*) FROM imported_subject").query(Integer.class).single()).isZero();
    }

    @Test
    void checksumMismatchCannotActivateContent() {
        var valid = snapshot("checksum-mismatch-release", "1");
        var tampered = new ContentSnapshot(valid.schemaVersion(), valid.externalReleaseId(), valid.examId(),
                valid.examVersionId(), valid.releaseVersion(), valid.releaseStatus(), valid.publishedAt(),
                "0".repeat(64), valid.subjects());

        assertThatThrownBy(() -> importService.importSnapshot(tampered))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("CONTENT_CHECKSUM_MISMATCH"));
        assertThat(jdbc.sql("SELECT count(*) FROM imported_content_release WHERE status = 'ACTIVE'")
                .query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM imported_question").query(Integer.class).single()).isZero();
    }

    @Test
    void rejectsDuplicateExternalQuestionAndAnswerOptionIdentifiersBeforePersistence() {
        var duplicateQuestion = new ContentSnapshot.Question("q1", "q1-v2", "fact-2", "SINGLE_CHOICE",
                "Another prompt", "Another explanation", "sv", null, true,
                List.of(new ContentSnapshot.AnswerOption("q1-v2-a", "Correct", true, 0),
                        new ContentSnapshot.AnswerOption("q1-v2-b", "Incorrect", false, 1)));
        var duplicateOption = new ContentSnapshot.Question("q2", "q2-v1", "fact-3", "SINGLE_CHOICE",
                "Prompt", "Explanation", "sv", null, true,
                List.of(new ContentSnapshot.AnswerOption("q1-a", "Correct", true, 0),
                        new ContentSnapshot.AnswerOption("q2-b", "Incorrect", false, 1)));
        var candidate = new ContentSnapshot("1.0", "duplicate-release", "swedish-citizenship", "exam-v1",
                "1", "PUBLISHED", Instant.parse("2026-01-01T00:00:00Z"), "pending",
                List.of(new ContentSnapshot.Subject("subject", "Subject", 0,
                        List.of(new ContentSnapshot.Topic("topic", "Topic", null, 0,
                                List.of(question("q1", "fact-1"), duplicateQuestion))))));

        assertThatThrownBy(() -> importService.importSnapshot(withChecksum(candidate)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_CONTENT_SNAPSHOT"));

        var duplicateOptionCandidate = new ContentSnapshot("1.0", "duplicate-option-release",
                "swedish-citizenship", "exam-v1", "2", "PUBLISHED",
                Instant.parse("2026-01-02T00:00:00Z"), "pending",
                List.of(new ContentSnapshot.Subject("subject", "Subject", 0,
                        List.of(new ContentSnapshot.Topic("topic", "Topic", null, 0,
                                List.of(question("q1", "fact-1"), duplicateOption))))));
        assertThatThrownBy(() -> importService.importSnapshot(withChecksum(duplicateOptionCandidate)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("INVALID_CONTENT_SNAPSHOT"));
        assertThat(jdbc.sql("SELECT count(*) FROM imported_question").query(Integer.class).single()).isZero();
    }

    @Test
    void mapsMalformedParametersAndUnknownRoutesToStableClientErrors() throws Exception {
        mockMvc.perform(get("/api/v1/content/subjects")
                        .header("X-Learner-Identity", "developer-learner")
                        .queryParam("examId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/practice-sessions/not-a-uuid")
                        .header("X-Learner-Identity", "developer-learner"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/not-a-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void practiceDeliversNoCorrectFlagsAndUpdatesProgressForCorrectAndIncorrectAnswers() throws Exception {
        importService.importSnapshot(snapshot("release-1", "1"));
        var session = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-a", PracticeMode.TOPIC, 2));

        mockMvc.perform(get("/api/v1/practice-sessions/{id}/next", session.sessionId())
                        .header("X-Learner-Identity", "developer-learner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionQuestionId").exists())
                .andExpect(jsonPath("$.answerOptions[0].correct").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist());

        var first = practiceService.nextQuestion(learnerId, session.sessionId());
        String unrelatedOption = jdbc.sql("""
                SELECT iao.external_answer_option_id
                FROM practice_session_question psq
                JOIN imported_answer_option iao ON iao.question_id = psq.imported_question_id
                WHERE psq.practice_session_id = :sessionId AND psq.id <> :questionId
                ORDER BY psq.sequence_number, iao.sort_order LIMIT 1
                """).param("sessionId", session.sessionId()).param("questionId", first.sessionQuestionId())
                .query(String.class).single();
        assertThatThrownBy(() -> practiceService.submit(learnerId, session.sessionId(),
                new PracticeService.SubmitAnswer(first.sessionQuestionId(), unrelatedOption, 100L)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("ANSWER_OPTION_NOT_FOR_QUESTION"));
        String correctOption = correctOption(first.sessionQuestionId());
        var firstResult = practiceService.submit(learnerId, session.sessionId(),
                new PracticeService.SubmitAnswer(first.sessionQuestionId(), correctOption, 1000L));
        assertThat(firstResult.correct()).isTrue();
        assertThat(firstResult.explanation()).startsWith("Reviewed explanation");

        var second = practiceService.nextQuestion(learnerId, session.sessionId());
        String wrongOption = wrongOption(second.sessionQuestionId());
        var finalResult = practiceService.submit(learnerId, session.sessionId(),
                new PracticeService.SubmitAnswer(second.sessionQuestionId(), wrongOption, null));
        assertThat(finalResult.correct()).isFalse();
        assertThat(finalResult.sessionProgress().answered()).isEqualTo(2);
        assertThat(practiceService.getSession(learnerId, session.sessionId()).status()).isEqualTo("COMPLETED");
        assertThatThrownBy(() -> practiceService.submit(learnerId, session.sessionId(),
                new PracticeService.SubmitAnswer(second.sessionQuestionId(), wrongOption, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("DUPLICATE_RESPONSE"));

        var progress = progressService.topics(learnerId);
        assertThat(progress).singleElement().satisfies(item -> {
            assertThat(item.questionsAnswered()).isEqualTo(2);
            assertThat(item.correctAnswers()).isEqualTo(1);
            assertThat(item.accuracyPercentage()).isEqualByComparingTo("50.00");
        });
    }

    @Test
    void mixedSelectionUsesActiveQuestionsAcrossTopicsAndRejectsInsufficientCount() {
        importService.importSnapshot(snapshot("release-1", "1"));
        var mixed = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", null, PracticeMode.MIXED, 4));
        assertThat(mixed.total()).isEqualTo(4);

        assertThatThrownBy(() -> practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-b", PracticeMode.TOPIC, 2)))
                .isInstanceOf(ApiException.class).hasMessageContaining("only 1");
    }

    @Test
    void databaseConstraintProtectsConcurrentDuplicateResponses() throws Exception {
        importService.importSnapshot(snapshot("release-1", "1"));
        var session = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-b", PracticeMode.TOPIC, 1));
        var question = practiceService.nextQuestion(learnerId, session.sessionId());
        String correct = correctOption(question.sessionQuestionId());
        var start = new CountDownLatch(1);
        Callable<Object> answer = () -> {
            start.await();
            try {
                return practiceService.submit(learnerId, session.sessionId(),
                        new PracticeService.SubmitAnswer(question.sessionQuestionId(), correct, 10L));
            } catch (RuntimeException exception) {
                return exception;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(answer);
            var second = executor.submit(answer);
            start.countDown();
            var results = List.of(first.get(), second.get());
            assertThat(results.stream().filter(PracticeService.AnswerResult.class::isInstance)).hasSize(1);
            assertThat(results.stream().filter(ApiException.class::isInstance)).hasSize(1);
        }
        assertThat(jdbc.sql("SELECT count(*) FROM practice_response").query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void databaseConstraintsPreventMixingQuestionsAcrossContentReleases() {
        var oldRelease = importService.importSnapshot(snapshot("old-release", "1"));
        var activeRelease = importService.importSnapshot(snapshot("active-release", "2"));
        var session = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-a", PracticeMode.TOPIC, 1));
        UUID oldQuestion = jdbc.sql("""
                SELECT id FROM imported_question WHERE content_release_id = :releaseId LIMIT 1
                """).param("releaseId", oldRelease.releaseId()).query(UUID.class).single();

        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO practice_session_question
                  (id, practice_session_id, imported_question_id, content_release_id, sequence_number, answered)
                VALUES (:id, :sessionId, :questionId, :releaseId, 2, FALSE)
                """).params(Map.of("id", UUID.randomUUID(), "sessionId", session.sessionId(),
                        "questionId", oldQuestion, "releaseId", activeRelease.releaseId())).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void learnerCannotReadAnotherLearnersPracticeSession() throws Exception {
        importService.importSnapshot(snapshot("ownership-release", "1"));
        var session = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-a", PracticeMode.TOPIC, 1));
        jdbc.sql("""
                INSERT INTO learner_profile
                  (id, external_identity_id, interface_language, explanation_language, created_at, updated_at)
                VALUES (:id, 'other-learner', 'sv', 'sv', now(), now())
                """).param("id", UUID.randomUUID()).update();

        mockMvc.perform(get("/api/v1/practice-sessions/{id}", session.sessionId())
                        .header("X-Learner-Identity", "other-learner"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRACTICE_SESSION_NOT_FOUND"));
    }

    private String correctOption(UUID sessionQuestionId) {
        return option(sessionQuestionId, true);
    }

    private String wrongOption(UUID sessionQuestionId) {
        return option(sessionQuestionId, false);
    }

    private String option(UUID sessionQuestionId, boolean correct) {
        return jdbc.sql("""
                SELECT iao.external_answer_option_id FROM practice_session_question psq
                JOIN imported_answer_option iao ON iao.question_id = psq.imported_question_id
                WHERE psq.id = :id AND iao.correct = :correct LIMIT 1
                """).param("id", sessionQuestionId).param("correct", correct).query(String.class).single();
    }

    private ContentSnapshot snapshot(String releaseId, String releaseVersion) {
        return snapshot(releaseId, releaseVersion,
                Instant.parse("2026-01-%02dT00:00:00Z".formatted(Integer.parseInt(releaseVersion))));
    }

    private ContentSnapshot snapshot(String releaseId, String releaseVersion, Instant publishedAt) {
        var topicA = new ContentSnapshot.Topic("topic-a", "Topic A", "Demo topic", 0,
                List.of(question("q1", "fact-1"), question("q2", "fact-2"), question("q3", "fact-3")));
        var topicB = new ContentSnapshot.Topic("topic-b", "Topic B", null, 0,
                List.of(question("q4", "fact-4")));
        var candidate = new ContentSnapshot("1.0", releaseId, "swedish-citizenship", "exam-v1",
                releaseVersion, "PUBLISHED", publishedAt, "pending",
                List.of(new ContentSnapshot.Subject("subject-a", "Subject A", 0, List.of(topicA)),
                        new ContentSnapshot.Subject("subject-b", "Subject B", 1, List.of(topicB))));
        return withChecksum(candidate);
    }

    private ContentSnapshot.Question question(String id, String factId) {
        return new ContentSnapshot.Question(id, id + "-v1", factId, "SINGLE_CHOICE", "Prompt " + id,
                "Reviewed explanation " + id, "sv", null, true,
                List.of(new ContentSnapshot.AnswerOption(id + "-a", "Correct", true, 0),
                        new ContentSnapshot.AnswerOption(id + "-b", "Incorrect", false, 1)));
    }

    private ContentSnapshot withChecksum(ContentSnapshot value) {
        String checksum = snapshotValidator.checksum(value);
        return new ContentSnapshot(value.schemaVersion(), value.externalReleaseId(), value.examId(),
                value.examVersionId(), value.releaseVersion(), value.releaseStatus(), value.publishedAt(),
                checksum, value.subjects());
    }
}
