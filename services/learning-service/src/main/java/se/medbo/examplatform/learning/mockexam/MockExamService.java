package se.medbo.examplatform.learning.mockexam;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;

@Service
public class MockExamService {
    private final JdbcClient jdbc;
    private final MockExamGenerator generator;
    private final Clock clock;

    @Autowired
    public MockExamService(JdbcClient jdbc, MockExamGenerator generator) {
        this(jdbc, generator, Clock.systemUTC());
    }

    MockExamService(JdbcClient jdbc, MockExamGenerator generator, Clock clock) {
        this.jdbc = jdbc;
        this.generator = generator;
        this.clock = clock;
    }

    @Transactional
    public AttemptView create(UUID learnerId, String examId) {
        var blueprint = jdbc.sql("""
                SELECT id, name, total_questions, duration_minutes, passing_percentage
                FROM mock_exam_blueprint WHERE exam_id = :examId AND active
                FOR SHARE
                """).param("examId", examId).query((rs, row) -> new Blueprint(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getInt("total_questions"),
                        rs.getInt("duration_minutes"), rs.getBigDecimal("passing_percentage")))
                .optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MOCK_BLUEPRINT_NOT_FOUND",
                        "No active mock exam blueprint exists for the exam"));
        var releaseId = jdbc.sql("""
                SELECT id FROM imported_content_release
                WHERE exam_id = :examId AND status = 'ACTIVE'
                ORDER BY published_at DESC, external_release_id LIMIT 1
                """).param("examId", examId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ACTIVE_CONTENT_RELEASE",
                        "No active content release exists for the exam"));
        var allocations = jdbc.sql("""
                SELECT allocation.external_topic_id, allocation.question_count, topic.id
                FROM mock_exam_topic_allocation allocation
                LEFT JOIN imported_topic topic
                  ON topic.external_topic_id = allocation.external_topic_id
                 AND topic.content_release_id = :releaseId
                WHERE allocation.blueprint_id = :blueprintId
                ORDER BY allocation.external_topic_id
                """).param("releaseId", releaseId).param("blueprintId", blueprint.id())
                .query((rs, row) -> {
                    UUID topicId = rs.getObject("id", UUID.class);
                    if (topicId == null) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "MOCK_BLUEPRINT_TOPIC_NOT_FOUND", "Blueprint topic is absent from the active release");
                    return new MockExamGenerator.TopicAllocation(topicId, rs.getString("external_topic_id"),
                            rs.getInt("question_count"));
                }).list();
        if (allocations.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_MOCK_BLUEPRINT",
                    "Mock exam blueprint has no topic allocations");
        }
        var eligible = jdbc.sql("""
                SELECT id, topic_id, knowledge_fact_id FROM imported_question
                WHERE content_release_id = :releaseId AND active
                ORDER BY id
                """).param("releaseId", releaseId)
                .query((rs, row) -> new MockExamGenerator.QuestionCandidate(rs.getObject("id", UUID.class),
                        rs.getObject("topic_id", UUID.class), rs.getString("knowledge_fact_id"))).list();
        var selected = generator.generate(eligible, allocations, blueprint.totalQuestions());

        UUID attemptId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        jdbc.sql("""
                INSERT INTO mock_exam_attempt
                  (id, learner_id, blueprint_id, content_release_id, status, started_at,
                   blueprint_name, total_questions, duration_minutes, passing_percentage)
                VALUES (:id, :learnerId, :blueprintId, :releaseId, 'ACTIVE', :startedAt,
                        :name, :total, :duration, :passing)
                """).params(Map.of("id", attemptId, "learnerId", learnerId, "blueprintId", blueprint.id(),
                        "releaseId", releaseId, "startedAt", utc(startedAt), "name", blueprint.name(),
                        "total", blueprint.totalQuestions(), "duration", blueprint.durationMinutes(),
                        "passing", blueprint.passingPercentage())).update();
        int sequence = 1;
        for (var question : selected) {
            jdbc.sql("""
                    INSERT INTO mock_exam_question
                      (id, attempt_id, imported_question_id, content_release_id, sequence_number, flagged)
                    VALUES (:id, :attemptId, :questionId, :releaseId, :sequence, FALSE)
                    """).params(Map.of("id", UUID.randomUUID(), "attemptId", attemptId,
                            "questionId", question.id(), "releaseId", releaseId, "sequence", sequence++)).update();
        }
        return get(learnerId, attemptId);
    }

    @Transactional
    public AttemptView get(UUID learnerId, UUID attemptId) {
        var attempt = attempt(learnerId, attemptId, true);
        expireIfRequired(attempt);
        return attemptView(attempt(learnerId, attemptId, false));
    }

    @Transactional
    public QuestionView question(UUID learnerId, UUID attemptId, Integer sequenceNumber) {
        var attempt = attempt(learnerId, attemptId, true);
        if (expireIfRequired(attempt)) throw notActive("Mock examination time has expired");
        if (!"ACTIVE".equals(attempt.status())) throw notActive("Mock examination is not active");
        String sequenceFilter = sequenceNumber == null ? "AND response.id IS NULL" : "AND question.sequence_number = :sequence";
        var query = jdbc.sql("""
                SELECT question.id, imported.external_question_version_id, imported.prompt,
                       imported.question_type, question.sequence_number, question.flagged,
                       response_option.external_answer_option_id AS selected_option_id
                FROM mock_exam_question question
                JOIN imported_question imported ON imported.id = question.imported_question_id
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                LEFT JOIN imported_answer_option response_option ON response_option.id = response.selected_answer_option_id
                WHERE question.attempt_id = :attemptId %s
                ORDER BY question.sequence_number LIMIT 1
                """.formatted(sequenceFilter)).param("attemptId", attemptId);
        if (sequenceNumber != null) query = query.param("sequence", sequenceNumber);
        var row = query.query((rs, index) -> new QuestionRow(rs.getObject("id", UUID.class),
                rs.getString("external_question_version_id"), rs.getString("prompt"),
                rs.getString("question_type"), rs.getInt("sequence_number"), rs.getBoolean("flagged"),
                rs.getString("selected_option_id"))).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MOCK_QUESTION_NOT_FOUND",
                        sequenceNumber == null ? "No unanswered question remains" : "Mock exam question not found"));
        var options = jdbc.sql("""
                SELECT option.external_answer_option_id, option.text
                FROM mock_exam_question question
                JOIN imported_answer_option option ON option.question_id = question.imported_question_id
                WHERE question.id = :id ORDER BY option.sort_order
                """).param("id", row.id()).query((rs, index) -> new AnswerOptionView(
                        rs.getString("external_answer_option_id"), rs.getString("text"))).list();
        var timer = MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), clock.instant());
        return new QuestionView(row.id(), row.questionId(), row.prompt(), row.questionType(), options,
                row.sequenceNumber(), attempt.totalQuestions(), row.selectedOptionId(), row.flagged(),
                timer.remainingSeconds());
    }

    @Transactional
    public AttemptProgress answer(UUID learnerId, UUID attemptId, UUID attemptQuestionId, String optionId) {
        var attempt = attempt(learnerId, attemptId, true);
        if (expireIfRequired(attempt)) throw notActive("Mock examination time has expired");
        if (!"ACTIVE".equals(attempt.status())) throw notActive("Mock examination is not active");
        var answer = jdbc.sql("""
                SELECT question.imported_question_id, option.id, option.correct
                FROM mock_exam_question question
                JOIN imported_answer_option option
                  ON option.question_id = question.imported_question_id
                 AND option.external_answer_option_id = :optionId
                WHERE question.id = :questionId AND question.attempt_id = :attemptId
                """).param("optionId", optionId).param("questionId", attemptQuestionId)
                .param("attemptId", attemptId)
                .query((rs, row) -> new AnswerContext(rs.getObject("imported_question_id", UUID.class),
                        rs.getObject("id", UUID.class), rs.getBoolean("correct"))).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MOCK_ANSWER_OPTION_INVALID",
                        "Answer option does not belong to the mock exam question"));
        jdbc.sql("""
                INSERT INTO mock_exam_response
                  (id, attempt_id, attempt_question_id, imported_question_id,
                   selected_answer_option_id, correct, answered_at)
                VALUES (:id, :attemptId, :questionId, :importedQuestionId, :optionId, :correct, :answeredAt)
                ON CONFLICT (attempt_question_id) DO UPDATE SET
                  selected_answer_option_id = EXCLUDED.selected_answer_option_id,
                  correct = EXCLUDED.correct,
                  answered_at = EXCLUDED.answered_at
                """).params(Map.of("id", UUID.randomUUID(), "attemptId", attemptId,
                        "questionId", attemptQuestionId, "importedQuestionId", answer.importedQuestionId(),
                        "optionId", answer.optionId(), "correct", answer.correct(),
                        "answeredAt", utc(clock.instant()))).update();
        return progress(attemptId, attempt);
    }

    @Transactional
    public AttemptProgress flag(UUID learnerId, UUID attemptId, UUID attemptQuestionId, boolean flagged) {
        var attempt = attempt(learnerId, attemptId, true);
        if (expireIfRequired(attempt)) throw notActive("Mock examination time has expired");
        if (!"ACTIVE".equals(attempt.status())) throw notActive("Mock examination is not active");
        int updated = jdbc.sql("""
                UPDATE mock_exam_question SET flagged = :flagged
                WHERE id = :questionId AND attempt_id = :attemptId
                """).param("flagged", flagged).param("questionId", attemptQuestionId)
                .param("attemptId", attemptId).update();
        if (updated == 0) throw new ApiException(HttpStatus.NOT_FOUND, "MOCK_QUESTION_NOT_FOUND",
                "Mock exam question not found");
        return progress(attemptId, attempt);
    }

    @Transactional
    public ResultView submit(UUID learnerId, UUID attemptId) {
        var attempt = attempt(learnerId, attemptId, true);
        if (expireIfRequired(attempt)) return resultsInternal(attemptId);
        if (!"ACTIVE".equals(attempt.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "MOCK_EXAM_ALREADY_FINALIZED",
                    "Mock examination has already been finalized");
        }
        finalizeAttempt(attempt, "SUBMITTED", clock.instant());
        return resultsInternal(attemptId);
    }

    @Transactional
    public ResultView results(UUID learnerId, UUID attemptId) {
        var attempt = attempt(learnerId, attemptId, true);
        expireIfRequired(attempt);
        var current = attempt(learnerId, attemptId, false);
        if ("ACTIVE".equals(current.status())) throw new ApiException(HttpStatus.CONFLICT,
                "MOCK_EXAM_NOT_FINALIZED", "Submit the mock examination before viewing results");
        return resultsInternal(attemptId);
    }

    @Transactional
    public List<HistoryView> history(UUID learnerId) {
        var expired = jdbc.sql("""
                SELECT id FROM mock_exam_attempt
                WHERE learner_id = :learnerId AND status = 'ACTIVE'
                  AND started_at + duration_minutes * interval '1 minute' <= :now
                FOR UPDATE
                """).param("learnerId", learnerId).param("now", utc(clock.instant()))
                .query(UUID.class).list();
        for (var id : expired) finalizeAttempt(attempt(learnerId, id, false), "EXPIRED",
                MockExamTimer.state(attempt(learnerId, id, false).startedAt(),
                        attempt(learnerId, id, false).durationMinutes(), clock.instant()).deadline());
        return jdbc.sql("""
                SELECT id, blueprint_name, status, started_at, duration_seconds, score,
                       percentage, passed, total_questions
                FROM mock_exam_attempt WHERE learner_id = :learnerId AND status <> 'ACTIVE'
                ORDER BY started_at DESC
                """).param("learnerId", learnerId).query((rs, row) -> new HistoryView(
                        rs.getObject("id", UUID.class), rs.getString("blueprint_name"), rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class).toInstant(), rs.getInt("duration_seconds"),
                        rs.getInt("score"), rs.getBigDecimal("percentage"), rs.getBoolean("passed"),
                        rs.getInt("total_questions"))).list();
    }

    @Scheduled(fixedDelayString = "${learning.mock-exam.expiry-scan-ms:30000}")
    @Transactional
    public void expireDueAttempts() {
        var due = jdbc.sql("""
                SELECT id FROM mock_exam_attempt
                WHERE status = 'ACTIVE'
                  AND started_at + duration_minutes * interval '1 minute' <= :now
                FOR UPDATE SKIP LOCKED
                """).param("now", utc(clock.instant())).query(UUID.class).list();
        for (var id : due) {
            var attempt = attempt(id);
            var deadline = MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), clock.instant())
                    .deadline();
            finalizeAttempt(attempt, "EXPIRED", deadline);
        }
    }

    private boolean expireIfRequired(Attempt attempt) {
        if (!"ACTIVE".equals(attempt.status())) return false;
        var timer = MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), clock.instant());
        if (!timer.expired()) return false;
        finalizeAttempt(attempt, "EXPIRED", timer.deadline());
        return true;
    }

    private void finalizeAttempt(Attempt attempt, String status, Instant completedAt) {
        int correct = jdbc.sql("SELECT count(*) FROM mock_exam_response WHERE attempt_id = :id AND correct")
                .param("id", attempt.id()).query(Integer.class).single();
        var score = MockExamScoring.calculate(correct, attempt.totalQuestions(), attempt.passingPercentage());
        int duration = MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), completedAt).elapsedSeconds();
        jdbc.sql("""
                UPDATE mock_exam_attempt SET status = :status, submitted_at = :submittedAt,
                    completed_at = :completedAt, duration_seconds = :duration, score = :score,
                    percentage = :percentage, passed = :passed
                WHERE id = :id AND status = 'ACTIVE'
                """).params(Map.of("status", status, "submittedAt", utc(completedAt),
                        "completedAt", utc(completedAt), "duration", duration, "score", score.correct(),
                        "percentage", score.percentage(), "passed", score.passed(), "id", attempt.id())).update();
    }

    private AttemptProgress progress(UUID attemptId, Attempt attempt) {
        int answered = jdbc.sql("SELECT count(*) FROM mock_exam_response WHERE attempt_id = :id")
                .param("id", attemptId).query(Integer.class).single();
        int flagged = jdbc.sql("SELECT count(*) FROM mock_exam_question WHERE attempt_id = :id AND flagged")
                .param("id", attemptId).query(Integer.class).single();
        int remaining = MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), clock.instant())
                .remainingSeconds();
        return new AttemptProgress(answered, attempt.totalQuestions(), flagged, remaining);
    }

    private AttemptView attemptView(Attempt attempt) {
        var navigation = jdbc.sql("""
                SELECT question.id, question.sequence_number, question.flagged,
                       response.id IS NOT NULL AS answered
                FROM mock_exam_question question
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :attemptId ORDER BY question.sequence_number
                """).param("attemptId", attempt.id()).query((rs, row) -> new NavigationItem(
                        rs.getObject("id", UUID.class), rs.getInt("sequence_number"),
                        rs.getBoolean("answered"), rs.getBoolean("flagged"))).list();
        int remaining = "ACTIVE".equals(attempt.status())
                ? MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), clock.instant()).remainingSeconds()
                : 0;
        return new AttemptView(attempt.id(), attempt.blueprintName(), attempt.status(), attempt.startedAt(),
                attempt.submittedAt(), attempt.totalQuestions(), attempt.durationMinutes(), remaining,
                (int) navigation.stream().filter(NavigationItem::answered).count(), navigation);
    }

    private ResultView resultsInternal(UUID attemptId) {
        var attempt = jdbc.sql("""
                SELECT id, blueprint_name, status, started_at, completed_at, duration_seconds,
                       score, percentage, passed, total_questions
                FROM mock_exam_attempt WHERE id = :id
                """).param("id", attemptId).query((rs, row) -> new ResultRow(rs.getObject("id", UUID.class),
                        rs.getString("blueprint_name"), rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("completed_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("duration_seconds"), rs.getInt("score"), rs.getBigDecimal("percentage"),
                        rs.getBoolean("passed"), rs.getInt("total_questions"))).single();
        var topics = jdbc.sql("""
                SELECT topic.external_topic_id, topic.name, count(*) AS total,
                       count(response.id) AS answered,
                       count(*) FILTER (WHERE response.correct) AS correct
                FROM mock_exam_question question
                JOIN imported_question imported ON imported.id = question.imported_question_id
                JOIN imported_topic topic ON topic.id = imported.topic_id
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :attemptId
                GROUP BY topic.external_topic_id, topic.name ORDER BY topic.external_topic_id
                """).param("attemptId", attemptId).query((rs, row) -> {
                    int total = rs.getInt("total"); int correct = rs.getInt("correct");
                    return new TopicResult(rs.getString("external_topic_id"), rs.getString("name"),
                            total, rs.getInt("answered"), correct,
                            MockExamScoring.calculate(correct, total, BigDecimal.ZERO).percentage());
                }).list();
        var incorrect = jdbc.sql("""
                SELECT imported.external_question_version_id, imported.prompt, imported.explanation,
                       selected.external_answer_option_id AS selected_id, selected.text AS selected_text,
                       correct.external_answer_option_id AS correct_id, correct.text AS correct_text
                FROM mock_exam_question question
                JOIN imported_question imported ON imported.id = question.imported_question_id
                JOIN imported_answer_option correct ON correct.question_id = imported.id AND correct.correct
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                LEFT JOIN imported_answer_option selected ON selected.id = response.selected_answer_option_id
                WHERE question.attempt_id = :attemptId AND (response.id IS NULL OR NOT response.correct)
                ORDER BY question.sequence_number
                """).param("attemptId", attemptId).query((rs, row) -> new IncorrectQuestion(
                        rs.getString("external_question_version_id"), rs.getString("prompt"),
                        rs.getString("selected_id"), rs.getString("selected_text"), rs.getString("correct_id"),
                        rs.getString("correct_text"), rs.getString("explanation"))).list();
        return new ResultView(attempt.id(), attempt.blueprintName(), attempt.status(), attempt.startedAt(),
                attempt.completedAt(), attempt.durationSeconds(), attempt.score(),
                attempt.totalQuestions() - attempt.score(), attempt.percentage(), attempt.passed(), topics, incorrect);
    }

    private Attempt attempt(UUID learnerId, UUID attemptId, boolean lock) {
        return jdbc.sql("""
                SELECT id, status, started_at, submitted_at, total_questions, duration_minutes,
                       passing_percentage, blueprint_name
                FROM mock_exam_attempt WHERE id = :id AND learner_id = :learnerId %s
                """.formatted(lock ? "FOR UPDATE" : ""))
                .param("id", attemptId).param("learnerId", learnerId)
                .query((rs, row) -> new Attempt(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("submitted_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("total_questions"), rs.getInt("duration_minutes"),
                        rs.getBigDecimal("passing_percentage"), rs.getString("blueprint_name")))
                .optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MOCK_EXAM_NOT_FOUND",
                        "Mock examination not found"));
    }

    private Attempt attempt(UUID attemptId) {
        return jdbc.sql("""
                SELECT id, status, started_at, submitted_at, total_questions, duration_minutes,
                       passing_percentage, blueprint_name
                FROM mock_exam_attempt WHERE id = :id
                """).param("id", attemptId).query((rs, row) -> new Attempt(rs.getObject("id", UUID.class),
                        rs.getString("status"), rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("submitted_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("total_questions"), rs.getInt("duration_minutes"),
                        rs.getBigDecimal("passing_percentage"), rs.getString("blueprint_name"))).single();
    }

    private static ApiException notActive(String message) {
        return new ApiException(HttpStatus.CONFLICT, "MOCK_EXAM_NOT_ACTIVE", message);
    }

    private static OffsetDateTime utc(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }

    public record AttemptView(UUID attemptId, String name, String status, Instant startedAt, Instant submittedAt,
                              int totalQuestions, int durationMinutes, int remainingSeconds, int answered,
                              List<NavigationItem> questions) {}
    public record NavigationItem(UUID attemptQuestionId, int sequenceNumber, boolean answered, boolean flagged) {}
    public record QuestionView(UUID attemptQuestionId, String questionId, String prompt, String questionType,
                               List<AnswerOptionView> answerOptions, int sequenceNumber, int totalQuestions,
                               String selectedAnswerOptionId, boolean flagged, int remainingSeconds) {}
    public record AnswerOptionView(String id, String text) {}
    public record AttemptProgress(int answered, int total, int flagged, int remainingSeconds) {}
    public record ResultView(UUID attemptId, String name, String status, Instant startedAt, Instant completedAt,
                             int durationSeconds, int correctAnswers, int incorrectAnswers, BigDecimal percentage,
                             boolean passed, List<TopicResult> topics, List<IncorrectQuestion> incorrectQuestions) {}
    public record TopicResult(String topicId, String topicName, int total, int answered, int correct,
                              BigDecimal percentage) {}
    public record IncorrectQuestion(String questionId, String prompt, String selectedAnswerOptionId,
                                    String selectedAnswerText, String correctAnswerOptionId,
                                    String correctAnswerText, String explanation) {}
    public record HistoryView(UUID attemptId, String name, String status, Instant startedAt, int durationSeconds,
                              int score, BigDecimal percentage, boolean passed, int totalQuestions) {}
    private record Blueprint(UUID id, String name, int totalQuestions, int durationMinutes,
                             BigDecimal passingPercentage) {}
    private record Attempt(UUID id, String status, Instant startedAt, Instant submittedAt, int totalQuestions,
                           int durationMinutes, BigDecimal passingPercentage, String blueprintName) {}
    private record QuestionRow(UUID id, String questionId, String prompt, String questionType, int sequenceNumber,
                               boolean flagged, String selectedOptionId) {}
    private record AnswerContext(UUID importedQuestionId, UUID optionId, boolean correct) {}
    private record ResultRow(UUID id, String blueprintName, String status, Instant startedAt, Instant completedAt,
                             int durationSeconds, int score, BigDecimal percentage, boolean passed,
                             int totalQuestions) {}
}
