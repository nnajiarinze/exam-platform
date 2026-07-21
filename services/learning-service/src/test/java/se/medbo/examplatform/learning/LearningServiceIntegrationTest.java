package se.medbo.examplatform.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import se.medbo.examplatform.learning.contentprojection.ContentImportTransaction;
import se.medbo.examplatform.learning.contentprojection.ContentReleaseActivationService;
import se.medbo.examplatform.learning.contentprojection.ContentSnapshot;
import se.medbo.examplatform.learning.contentprojection.SnapshotValidator;
import se.medbo.examplatform.learning.practice.PracticeMode;
import se.medbo.examplatform.learning.practice.PracticeService;
import se.medbo.examplatform.learning.mockexam.MockExamService;
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
    @Autowired ContentReleaseActivationService activationService;
    @Autowired SnapshotValidator snapshotValidator;
    @Autowired PracticeService practiceService;
    @Autowired MockExamService mockExamService;
    @Autowired ProgressService progressService;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private UUID learnerId;

    @BeforeEach
    void cleanDatabase() {
        jdbc.sql("""
                TRUNCATE mock_exam_response, mock_exam_question, mock_exam_attempt,
                         mock_exam_topic_allocation, mock_exam_blueprint,
                         bookmark, topic_progress, practice_response, practice_session_question,
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
    void authenticatedLearnerGetsDeterministicSettingsDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/me/settings").header("X-Learner-Identity", "developer-learner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyQuestionGoal").value(20))
                .andExpect(jsonPath("$.weeklyStudyDaysGoal").value(5))
                .andExpect(jsonPath("$.studyReminderEnabled").value(false))
                .andExpect(jsonPath("$.timezone").value("Europe/Stockholm"))
                .andExpect(jsonPath("$.questionsAnsweredToday").value(0))
                .andExpect(jsonPath("$.version").value(0));
        assertThat(jdbc.sql("SELECT count(*) FROM learner_settings WHERE learner_id=:id")
                .param("id", learnerId).query(Integer.class).single()).isOne();
    }

    @Test
    void authenticatedLearnerUpdatesOwnSettingsWithOptimisticLocking() throws Exception {
        mockMvc.perform(put("/api/v1/me/settings").header("X-Learner-Identity", "developer-learner")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                          {"dailyQuestionGoal":30,"weeklyStudyDaysGoal":4,"studyReminderEnabled":true,
                           "preferredReminderTime":"18:30:00","timezone":"Europe/Stockholm",
                           "progressSummaryEnabled":true,"achievementNotificationsEnabled":false,"version":0}
                          """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyQuestionGoal").value(30))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(put("/api/v1/me/settings").header("X-Learner-Identity", "developer-learner")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                          {"dailyQuestionGoal":40,"weeklyStudyDaysGoal":4,"studyReminderEnabled":false,
                           "preferredReminderTime":"18:30:00","timezone":"Europe/Stockholm",
                           "progressSummaryEnabled":false,"achievementNotificationsEnabled":false,"version":0}
                          """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEARNER_SETTINGS_CONFLICT"));
    }

    @Test
    void settingsRejectInvalidGoalAndTimezone() throws Exception {
        mockMvc.perform(put("/api/v1/me/settings").header("X-Learner-Identity", "developer-learner")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                          {"dailyQuestionGoal":2,"weeklyStudyDaysGoal":8,"studyReminderEnabled":false,
                           "preferredReminderTime":"18:30:00","timezone":"Not/AZone",
                           "progressSummaryEnabled":false,"achievementNotificationsEnabled":false,"version":0}
                          """))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/v1/me/settings").header("X-Learner-Identity", "developer-learner")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                          {"dailyQuestionGoal":20,"weeklyStudyDaysGoal":5,"studyReminderEnabled":false,
                           "preferredReminderTime":"18:30:00","timezone":"Not/AZone",
                           "progressSummaryEnabled":false,"achievementNotificationsEnabled":false,"version":0}
                          """))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));
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
                .andExpect(jsonPath("$.status").value("IMPORTED"));

        mockMvc.perform(post("/internal/v1/content-releases/http-release/activate")
                        .header("X-Internal-Api-Key", "test-internal-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activated").value(true))
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
        importAndActivate(snapshot("content-list-release", "1"));

        mockMvc.perform(get("/api/v1/content/subjects")
                        .header("X-Learner-Identity", "developer-learner")
                        .queryParam("examId", "swedish-citizenship"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("subject-a"))
                .andExpect(jsonPath("$[0].topics[0].id").value("topic-a"))
                .andExpect(jsonPath("$[1].topics[0].id").value("topic-b"));
    }

    @Test
    void importsAndScoresMultipleChoiceUsingTheExactOptionSet() {
        var multiple = new ContentSnapshot.Question("multi-1", "multi-1-v1", "fact-multi",
                "MULTIPLE_CHOICE", "Select both correct answers", "Both are required", "sv", null, true,
                List.of("multi-a", "multi-c"), List.of(
                        new ContentSnapshot.AnswerOption("multi-a", "A", true, "A feedback", 0),
                        new ContentSnapshot.AnswerOption("multi-b", "B", false, "B feedback", 1),
                        new ContentSnapshot.AnswerOption("multi-c", "C", true, "C feedback", 2)));
        var candidate = new ContentSnapshot("1.1", "multi-release", "swedish-citizenship", "exam-v1",
                "10", "PUBLISHED", Instant.parse("2026-03-01T00:00:00Z"), "pending",
                List.of(new ContentSnapshot.Subject("subject-multi", "Multiple", 0,
                        List.of(new ContentSnapshot.Topic("topic-multi", "Multiple", null, 0, List.of(multiple))))));
        importAndActivate(withChecksum(candidate));

        var exactSession = practiceService.create(learnerId, new PracticeService.CreateSession(
                "swedish-citizenship", "topic-multi", PracticeMode.TOPIC, 1));
        var exact = practiceService.submit(learnerId, exactSession.sessionId(), new PracticeService.SubmitAnswer(
                exactSession.nextQuestion().orElseThrow().sessionQuestionId(), List.of("multi-c", "multi-a"), null));
        assertThat(exact.correct()).isTrue();
        assertThat(exact.correctOptionIds()).containsExactly("multi-a", "multi-c");
        assertThat(exact.optionFeedback()).hasSize(3);

        var partialSession = practiceService.create(learnerId, new PracticeService.CreateSession(
                "swedish-citizenship", "topic-multi", PracticeMode.TOPIC, 1));
        var partial = practiceService.submit(learnerId, partialSession.sessionId(), new PracticeService.SubmitAnswer(
                partialSession.nextQuestion().orElseThrow().sessionQuestionId(), List.of("multi-a"), null));
        assertThat(partial.correct()).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM practice_response_selection")
                .query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    void uppercaseImportAndRequestsResolveTheCanonicalActiveExam() throws Exception {
        var canonical = snapshot("canonical-release", "1");
        var uppercase = withChecksum(new ContentSnapshot(canonical.schemaVersion(), canonical.externalReleaseId(),
                "SWEDISH_CITIZENSHIP", canonical.examVersionId(), canonical.releaseVersion(),
                canonical.releaseStatus(), canonical.publishedAt(), "pending", canonical.subjects()));
        importAndActivate(uppercase);

        assertThat(jdbc.sql("SELECT exam_id FROM imported_content_release WHERE external_release_id='canonical-release'")
                .query(String.class).single()).isEqualTo("swedish-citizenship");
        assertThat(jdbc.sql("SELECT count(*) FROM imported_content_release WHERE exam_id='swedish-citizenship' AND status='ACTIVE'")
                .query(Integer.class).single()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/content/subjects").header("X-Learner-Identity", "developer-learner")
                        .queryParam("examId", "SWEDISH_CITIZENSHIP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].topics[0].id").value("topic-a"));
        var session=practiceService.create(learnerId,
                new PracticeService.CreateSession("Swedish Citizenship", "topic-a", PracticeMode.TOPIC, 1));
        assertThat(jdbc.sql("SELECT exam_id FROM practice_session WHERE id=:id").param("id",session.sessionId())
                .query(String.class).single()).isEqualTo("swedish-citizenship");
    }

    @Test
    void importsIdempotentlyAndSupersedesWithoutBreakingHistoricalSession() {
        var releaseOne = snapshot("release-1", "1");
        var first = importAndActivate(releaseOne);
        assertThat(importService.importSnapshot(releaseOne).imported()).isFalse();

        var session = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-a", PracticeMode.TOPIC, 2));
        var releaseTwo = snapshot("release-2", "2");
        importAndActivate(releaseTwo);

        assertThat(jdbc.sql("SELECT status FROM imported_content_release WHERE id = :id")
                .param("id", first.releaseId()).query(String.class).single()).isEqualTo("IMPORTED");
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
        var newer = importAndActivate(snapshot("newer-release", "2",
                Instant.parse("2026-02-02T00:00:00Z")));
        var delayedOlder = importService.importSnapshot(snapshot("delayed-older-release", "1",
                Instant.parse("2026-02-01T00:00:00Z")));

        assertThat(delayedOlder.status()).isEqualTo("IMPORTED");
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
        importAndActivate(snapshot("release-1", "1"));
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
        importAndActivate(snapshot("release-1", "1"));
        var mixed = practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", null, PracticeMode.MIXED, 4));
        assertThat(mixed.total()).isEqualTo(4);

        assertThatThrownBy(() -> practiceService.create(learnerId,
                new PracticeService.CreateSession("swedish-citizenship", "topic-b", PracticeMode.TOPIC, 2)))
                .isInstanceOf(ApiException.class).hasMessageContaining("only 1");
    }

    @Test
    void databaseConstraintProtectsConcurrentDuplicateResponses() throws Exception {
        importAndActivate(snapshot("release-1", "1"));
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
        var oldRelease = importAndActivate(snapshot("old-release", "1"));
        var activeRelease = importAndActivate(snapshot("active-release", "2"));
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
        importAndActivate(snapshot("ownership-release", "1"));
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

    @Test
    void createsNavigatesSubmitsAndStoresMockExamResultsAndHistory() {
        importAndActivate(snapshot("mock-release", "1"));
        insertMockBlueprint(3, 30, "67.00");

        var attempt = mockExamService.create(learnerId, "swedish-citizenship");
        assertThat(attempt.status()).isEqualTo("ACTIVE");
        assertThat(attempt.expiresAt()).isAfter(attempt.startedAt());
        assertThat(attempt.releaseId()).isNotNull();
        assertThat(mockExamService.create(learnerId, "swedish-citizenship").attemptId())
                .isEqualTo(attempt.attemptId());
        assertThat(attempt.questions()).hasSize(3);
        assertThat(jdbc.sql("""
                SELECT count(DISTINCT imported_question_id) FROM mock_exam_question WHERE attempt_id = :id
                """).param("id", attempt.attemptId()).query(Integer.class).single()).isEqualTo(3);

        var first = mockExamService.question(learnerId, attempt.attemptId(), 1);
        assertThat(mockExamService.question(learnerId, attempt.attemptId(), 1).answerOptions())
                .containsExactlyElementsOf(first.answerOptions());
        assertThat(jdbc.sql("SELECT option_order IS NOT NULL FROM mock_exam_question WHERE id = :id")
                .param("id", first.attemptQuestionId()).query(Boolean.class).single()).isTrue();
        mockExamService.flag(learnerId, attempt.attemptId(), first.attemptQuestionId(), true);
        mockExamService.answer(learnerId, attempt.attemptId(), first.attemptQuestionId(),
                correctMockOption(first.attemptQuestionId()), 0L);
        assertThatThrownBy(() -> mockExamService.answer(learnerId, attempt.attemptId(),
                first.attemptQuestionId(), wrongMockOption(first.attemptQuestionId()), 0L))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("STALE_ANSWER_VERSION"));
        var second = mockExamService.question(learnerId, attempt.attemptId(), 2);
        mockExamService.answer(learnerId, attempt.attemptId(), second.attemptQuestionId(),
                wrongMockOption(second.attemptQuestionId()));

        var result = mockExamService.submit(learnerId, attempt.attemptId());
        assertThat(result.status()).isEqualTo("SUBMITTED");
        assertThat(result.correctAnswers()).isEqualTo(1);
        assertThat(result.incorrectAnswers()).isEqualTo(2);
        assertThat(result.percentage()).isEqualByComparingTo("33.33");
        assertThat(result.passed()).isFalse();
        assertThat(result.topics()).hasSize(2);
        assertThat(result.incorrectQuestions()).hasSize(2);
        assertThat(result.incorrectQuestions().getFirst().correctAnswerOptionId()).isNotBlank();
        assertThat(mockExamService.history(learnerId)).singleElement()
                .satisfies(item -> assertThat(item.attemptId()).isEqualTo(attempt.attemptId()));

        assertThat(mockExamService.submit(learnerId, attempt.attemptId())).isEqualTo(result);
        assertThatThrownBy(() -> mockExamService.answer(learnerId, attempt.attemptId(),
                first.attemptQuestionId(), correctMockOption(first.attemptQuestionId())))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MOCK_EXAM_NOT_ACTIVE"));
    }

    @Test
    void mockExamHttpEndpointsAreRegisteredAndHideCorrectnessBeforeSubmission() throws Exception {
        importAndActivate(snapshot("mock-http-release", "1"));
        insertMockBlueprint(3, 30, "50.00");
        String response = mockMvc.perform(post("/api/v1/mock-exams")
                        .header("X-Learner-Identity", "developer-learner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"examId\":\"swedish-citizenship\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").exists())
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        UUID attemptId = UUID.fromString(objectMapper.readTree(response).get("attemptId").asText());

        mockMvc.perform(get("/api/v1/mock-exams/{id}/next", attemptId)
                        .header("X-Learner-Identity", "developer-learner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerOptions[0].correct").doesNotExist())
                .andExpect(jsonPath("$.explanation").doesNotExist());
        mockMvc.perform(get("/api/v1/mock-exams/history")
                        .header("X-Learner-Identity", "developer-learner"))
                .andExpect(status().isOk());
    }

    @Test
    void expiresMockExamUsingServerTimeAndRejectsFurtherAnswers() {
        importAndActivate(snapshot("expired-mock-release", "1"));
        insertMockBlueprint(3, 1, "50.00");
        var attempt = mockExamService.create(learnerId, "swedish-citizenship");
        jdbc.sql("UPDATE mock_exam_attempt SET started_at = now() - interval '2 minutes' WHERE id = :id")
                .param("id", attempt.attemptId()).update();

        mockExamService.expireDueAttempts();
        assertThat(mockExamService.get(learnerId, attempt.attemptId()).status()).isEqualTo("EXPIRED");
        assertThat(mockExamService.results(learnerId, attempt.attemptId()).percentage())
                .isEqualByComparingTo("0.00");
        var questionId = attempt.questions().getFirst().attemptQuestionId();
        assertThatThrownBy(() -> mockExamService.answer(learnerId, attempt.attemptId(), questionId, "anything"))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MOCK_EXAM_NOT_ACTIVE"));
    }

    @Test
    void learnerCannotAccessAnotherLearnersMockExam() {
        importAndActivate(snapshot("mock-owner-release", "1"));
        insertMockBlueprint(3, 30, "50.00");
        var attempt = mockExamService.create(learnerId, "swedish-citizenship");
        UUID other = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO learner_profile
                  (id, external_identity_id, interface_language, explanation_language, created_at, updated_at)
                VALUES (:id, 'mock-other', 'sv', 'sv', now(), now())
                """).param("id", other).update();
        assertThatThrownBy(() -> mockExamService.get(other, attempt.attemptId()))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("MOCK_EXAM_NOT_FOUND"));
    }

    private void insertMockBlueprint(int total, int durationMinutes, String passingPercentage) {
        UUID blueprintId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO mock_exam_blueprint
                  (id, exam_id, name, total_questions, duration_minutes, passing_percentage,
                   active, created_at, updated_at)
                VALUES (:id, 'swedish-citizenship', 'Demonstration mock', :total, :duration,
                        :passing, TRUE, now(), now())
                """).param("id", blueprintId).param("total", total).param("duration", durationMinutes)
                .param("passing", new java.math.BigDecimal(passingPercentage)).update();
        jdbc.sql("""
                INSERT INTO mock_exam_topic_allocation (id, blueprint_id, external_topic_id, question_count)
                VALUES (:id, :blueprintId, 'topic-a', 2)
                """).param("id", UUID.randomUUID()).param("blueprintId", blueprintId).update();
        jdbc.sql("""
                INSERT INTO mock_exam_topic_allocation (id, blueprint_id, external_topic_id, question_count)
                VALUES (:id, :blueprintId, 'topic-b', 1)
                """).param("id", UUID.randomUUID()).param("blueprintId", blueprintId).update();
    }

    private String correctMockOption(UUID attemptQuestionId) { return mockOption(attemptQuestionId, true); }
    private String wrongMockOption(UUID attemptQuestionId) { return mockOption(attemptQuestionId, false); }
    private String mockOption(UUID attemptQuestionId, boolean correct) {
        return jdbc.sql("""
                SELECT option.external_answer_option_id FROM mock_exam_question question
                JOIN imported_answer_option option ON option.question_id = question.imported_question_id
                WHERE question.id = :id AND option.correct = :correct
                """).param("id", attemptQuestionId).param("correct", correct).query(String.class).single();
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

    private ContentImportTransaction.ImportResult importAndActivate(ContentSnapshot snapshot) {
        var result = importService.importSnapshot(snapshot);
        activationService.activate(snapshot.externalReleaseId());
        return result;
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
