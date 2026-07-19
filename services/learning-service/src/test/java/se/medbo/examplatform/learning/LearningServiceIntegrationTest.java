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
        var topicA = new ContentSnapshot.Topic("topic-a", "Topic A", "Demo topic", 0,
                List.of(question("q1", "fact-1"), question("q2", "fact-2"), question("q3", "fact-3")));
        var topicB = new ContentSnapshot.Topic("topic-b", "Topic B", null, 0,
                List.of(question("q4", "fact-4")));
        var candidate = new ContentSnapshot("1.0", releaseId, "swedish-citizenship", "exam-v1",
                releaseVersion, "PUBLISHED", Instant.parse("2026-01-01T00:00:00Z"), "pending",
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
