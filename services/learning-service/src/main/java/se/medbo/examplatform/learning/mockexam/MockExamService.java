package se.medbo.examplatform.learning.mockexam;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.medbo.examplatform.learning.shared.ApiException;
import se.medbo.examplatform.learning.shared.ExternalExamIdentifier;

@Service
public class MockExamService {
    private static final Logger log = LoggerFactory.getLogger(MockExamService.class);
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

    public ConfigurationView configuration(String examId) {
        String canonicalExamId = ExternalExamIdentifier.normalize(examId);
        return jdbc.sql("""
                SELECT exam_id, name, description, total_questions, duration_minutes, passing_percentage
                FROM mock_exam_blueprint WHERE exam_id = :examId AND active
                """).param("examId", canonicalExamId).query((rs, row) -> new ConfigurationView(
                        rs.getString("exam_id"), rs.getString("name"), rs.getString("description"),
                        rs.getInt("total_questions"), rs.getObject("duration_minutes", Integer.class),
                        rs.getObject("duration_minutes", Integer.class) != null,
                        rs.getBigDecimal("passing_percentage"))).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MOCK_BLUEPRINT_NOT_FOUND",
                        "No active mock exam blueprint exists for the exam"));
    }

    @Transactional
    public AttemptView create(UUID learnerId, String examId) {
        String canonicalExamId = ExternalExamIdentifier.normalize(examId);
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", learnerId + ":" + canonicalExamId).query((rs, row) -> true).single();
        var activeAttempt = jdbc.sql("""
                SELECT id FROM mock_exam_attempt
                WHERE learner_id = :learnerId AND exam_id = :examId AND status = 'ACTIVE'
                FOR UPDATE
                """).param("learnerId", learnerId).param("examId", canonicalExamId)
                .query(UUID.class).optional();
        if (activeAttempt.isPresent()) {
            var existing = attempt(learnerId, activeAttempt.get(), false);
            if (!expireIfRequired(existing)) {
                log.info("mock_exam_resumed mockExamId={} learnerId={} examId={} releaseId={}",
                        existing.id(), learnerId, canonicalExamId, existing.releaseId());
                return attemptView(existing);
            }
        }
        var blueprint = jdbc.sql("""
                SELECT id, name, description, total_questions, duration_minutes, passing_percentage,
                       randomize_questions, randomize_options
                FROM mock_exam_blueprint WHERE exam_id = :examId AND active
                FOR SHARE
                """).param("examId", canonicalExamId).query((rs, row) -> new Blueprint(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
                        rs.getInt("total_questions"), rs.getObject("duration_minutes", Integer.class),
                        rs.getBigDecimal("passing_percentage"), rs.getBoolean("randomize_questions"),
                        rs.getBoolean("randomize_options")))
                .optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MOCK_BLUEPRINT_NOT_FOUND",
                        "No active mock exam blueprint exists for the exam"));
        var releaseId = jdbc.sql("""
                SELECT id FROM imported_content_release
                WHERE exam_id = :examId AND status = 'ACTIVE'
                ORDER BY published_at DESC, external_release_id LIMIT 1
                """).param("examId", canonicalExamId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ACTIVE_CONTENT_RELEASE",
                        "No active content release exists for the exam"));
        var allocations = new ArrayList<>(jdbc.sql("""
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
                }).list());
        if (allocations.isEmpty()) {
            allocations.addAll(deriveBalancedAllocations(releaseId, blueprint.totalQuestions()));
        }
        var eligible = jdbc.sql("""
                SELECT id, topic_id, knowledge_fact_id FROM imported_question
                WHERE content_release_id = :releaseId AND active
                  AND question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE')
                ORDER BY id
                """).param("releaseId", releaseId)
                .query((rs, row) -> new MockExamGenerator.QuestionCandidate(rs.getObject("id", UUID.class),
                        rs.getObject("topic_id", UUID.class), rs.getString("knowledge_fact_id"))).list();
        UUID attemptId = UUID.randomUUID();
        var selected = generator.generate(eligible, allocations, blueprint.totalQuestions(), attemptId);
        Instant startedAt = clock.instant();
        var attemptParams = new java.util.HashMap<String, Object>();
        attemptParams.put("id", attemptId); attemptParams.put("learnerId", learnerId);
        attemptParams.put("blueprintId", blueprint.id()); attemptParams.put("releaseId", releaseId);
        attemptParams.put("examId", canonicalExamId); attemptParams.put("startedAt", utc(startedAt));
        attemptParams.put("expiresAt", blueprint.durationMinutes() == null ? null
                : utc(startedAt.plusSeconds(blueprint.durationMinutes() * 60L)));
        attemptParams.put("name", blueprint.name()); attemptParams.put("total", blueprint.totalQuestions());
        attemptParams.put("duration", blueprint.durationMinutes());
        attemptParams.put("passing", blueprint.passingPercentage());
        jdbc.sql("""
                INSERT INTO mock_exam_attempt
                  (id, learner_id, blueprint_id, content_release_id, exam_id, status, started_at, expires_at,
                   blueprint_name, total_questions, duration_minutes, passing_percentage, created_at, updated_at)
                VALUES (:id, :learnerId, :blueprintId, :releaseId, :examId, 'ACTIVE', :startedAt, :expiresAt,
                        :name, :total, :duration, :passing, :startedAt, :startedAt)
                """).params(attemptParams).update();
        int sequence = 1;
        for (var question : selected) {
            var optionOrder = jdbc.sql("""
                    SELECT external_answer_option_id FROM imported_answer_option
                    WHERE question_id = :questionId ORDER BY sort_order
                    """).param("questionId", question.id()).query(String.class).list();
            if (blueprint.randomizeOptions()) optionOrder = deterministicOptionOrder(optionOrder, attemptId,
                    question.id());
            String optionOrderJson = optionOrder.stream().map(id -> "\"" + id.replace("\"", "\\\"") + "\"")
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
            jdbc.sql("""
                    INSERT INTO mock_exam_question
                      (id, attempt_id, imported_question_id, content_release_id, sequence_number, flagged,
                       option_order, question_external_id, prompt_snapshot, question_type_snapshot,
                       explanation_snapshot, topic_external_id, topic_name_snapshot,
                       objective_external_id, objective_name_snapshot, lesson_topic_external_id,
                       created_at, updated_at)
                    SELECT :id, :attemptId, imported.id, :releaseId, :sequence, FALSE,
                           CAST(:optionOrder AS jsonb), imported.external_question_version_id,
                           imported.prompt, imported.question_type, imported.explanation,
                           topic.external_topic_id, topic.name, imported.knowledge_fact_id, topic.name,
                           topic.external_topic_id, :createdAt, :createdAt
                    FROM imported_question imported
                    JOIN imported_topic topic ON topic.id = imported.topic_id
                    WHERE imported.id = :questionId
                    """).params(Map.of("id", UUID.randomUUID(), "attemptId", attemptId,
                            "questionId", question.id(), "releaseId", releaseId, "sequence", sequence++,
                            "optionOrder", optionOrderJson, "createdAt", utc(startedAt))).update();
            jdbc.sql("""
                    INSERT INTO mock_exam_option_snapshot
                      (attempt_question_id, option_id, option_text, correct, feedback, display_order)
                    SELECT snapshot.id, option.external_answer_option_id, option.text, option.correct,
                           option.feedback, ordering.position::integer
                    FROM mock_exam_question snapshot
                    JOIN LATERAL jsonb_array_elements_text(snapshot.option_order)
                         WITH ORDINALITY ordering(option_id, position) ON TRUE
                    JOIN imported_answer_option option
                      ON option.question_id = snapshot.imported_question_id
                     AND option.external_answer_option_id = ordering.option_id
                    WHERE snapshot.attempt_id = :attemptId AND snapshot.sequence_number = :sequence
                    """).param("attemptId", attemptId).param("sequence", sequence - 1).update();
        }
        log.info("mock_exam_started mockExamId={} learnerId={} examId={} releaseId={} questionCount={}",
                attemptId, learnerId, canonicalExamId, releaseId, selected.size());
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
                SELECT question.id, question.question_external_id AS external_question_version_id,
                       question.prompt_snapshot AS prompt,
                       question.question_type_snapshot AS question_type,
                       question.sequence_number, question.flagged,
                       question.version AS question_version, response.version AS answer_version
                FROM mock_exam_question question
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :attemptId %s
                ORDER BY question.sequence_number LIMIT 1
                """.formatted(sequenceFilter)).param("attemptId", attemptId);
        if (sequenceNumber != null) query = query.param("sequence", sequenceNumber);
        var row = query.query((rs, index) -> new QuestionRow(rs.getObject("id", UUID.class),
                rs.getString("external_question_version_id"), rs.getString("prompt"),
                rs.getString("question_type"), rs.getInt("sequence_number"), rs.getBoolean("flagged"),
                rs.getLong("question_version"), rs.getObject("answer_version", Long.class))).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MOCK_QUESTION_NOT_FOUND",
                        sequenceNumber == null ? "No unanswered question remains" : "Mock exam question not found"));
        var options = jdbc.sql("""
                SELECT option_id, option_text FROM mock_exam_option_snapshot
                WHERE attempt_question_id = :id ORDER BY display_order
                """).param("id", row.id()).query((rs, index) -> new AnswerOptionView(
                        rs.getString("option_id"), rs.getString("option_text"))).list();
        var selectedOptionIds = jdbc.sql("""
                SELECT option.external_answer_option_id FROM mock_exam_response response
                JOIN mock_exam_response_selection selection ON selection.mock_exam_response_id = response.id
                JOIN imported_answer_option option ON option.id = selection.answer_option_id
                WHERE response.attempt_question_id = :id ORDER BY option.sort_order
                """).param("id", row.id()).query(String.class).list();
        var timer = MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), clock.instant());
        return new QuestionView(row.id(), row.questionId(), row.prompt(), row.questionType(), options,
                row.sequenceNumber(), attempt.totalQuestions(), selectedOptionIds, row.flagged(),
                row.questionVersion(), row.answerVersion() == null ? 0 : row.answerVersion(), timer.remainingSeconds());
    }

    @Transactional
    public AttemptProgress answer(UUID learnerId, UUID attemptId, UUID attemptQuestionId, String optionId) {
        return answer(learnerId, attemptId, attemptQuestionId, List.of(optionId), null);
    }

    @Transactional
    public AttemptProgress answer(UUID learnerId, UUID attemptId, UUID attemptQuestionId, String optionId,
                                  Long expectedVersion) {
        return answer(learnerId, attemptId, attemptQuestionId, List.of(optionId), expectedVersion);
    }

    @Transactional
    public AttemptProgress answer(UUID learnerId, UUID attemptId, UUID attemptQuestionId, List<String> optionIds,
                                  Long expectedVersion) {
        if (optionIds == null || optionIds.isEmpty() || optionIds.size() != new java.util.HashSet<>(optionIds).size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MOCK_ANSWER_SELECTION",
                    "At least one unique answer option must be selected");
        }
        var attempt = attempt(learnerId, attemptId, true);
        if (expireIfRequired(attempt)) throw notActive("Mock examination time has expired");
        if (!"ACTIVE".equals(attempt.status())) throw notActive("Mock examination is not active");
        var answer = jdbc.sql("""
                SELECT question.imported_question_id, imported.question_type
                FROM mock_exam_question question
                JOIN imported_question imported ON imported.id = question.imported_question_id
                WHERE question.id = :questionId AND question.attempt_id = :attemptId
                """).param("questionId", attemptQuestionId)
                .param("attemptId", attemptId)
                .query((rs, row) -> new AnswerContext(rs.getObject("imported_question_id", UUID.class),
                        rs.getString("question_type"))).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "MOCK_ANSWER_OPTION_INVALID",
                        "Answer option does not belong to the mock exam question"));
        var options = jdbc.sql("SELECT id,external_answer_option_id,correct FROM imported_answer_option WHERE question_id=:id")
                .param("id", answer.importedQuestionId()).query((rs,row)->new AnswerSelection(
                        rs.getObject("id",UUID.class),rs.getString("external_answer_option_id"),rs.getBoolean("correct"))).list();
        var byId=options.stream().collect(java.util.stream.Collectors.toMap(AnswerSelection::externalId,o->o));
        if(!byId.keySet().containsAll(optionIds)) throw new ApiException(HttpStatus.BAD_REQUEST,
                "MOCK_ANSWER_OPTION_INVALID","Answer option does not belong to the mock exam question");
        if(!"MULTIPLE_CHOICE".equals(answer.questionType())&&optionIds.size()!=1) throw new ApiException(
                HttpStatus.BAD_REQUEST,"INVALID_MOCK_ANSWER_SELECTION","Single-choice questions require exactly one selected option");
        boolean correct=se.medbo.examplatform.learning.shared.ExactSetScoring.matches(optionIds,
                options.stream().filter(AnswerSelection::correct).map(AnswerSelection::externalId).toList());
        var existing=jdbc.sql("SELECT id,version FROM mock_exam_response WHERE attempt_question_id=:id")
                .param("id",attemptQuestionId).query((rs,row)->new ExistingResponse(rs.getObject("id",UUID.class),rs.getLong("version"))).optional();
        long currentVersion=existing.map(ExistingResponse::version).orElse(0L);
        if (expectedVersion != null && expectedVersion != currentVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "STALE_ANSWER_VERSION", "The answer was changed by another request");
        }
        UUID responseId=existing.map(ExistingResponse::id).orElseGet(UUID::randomUUID);
        jdbc.sql("""
                INSERT INTO mock_exam_response
                  (id, attempt_id, attempt_question_id, imported_question_id,
                   selected_answer_option_id, correct, answered_at, updated_at, version)
                VALUES (:id, :attemptId, :questionId, :importedQuestionId, :optionId, :correct, :answeredAt, :updatedAt, 1)
                ON CONFLICT (attempt_question_id) DO UPDATE SET
                  selected_answer_option_id = EXCLUDED.selected_answer_option_id,
                  correct = EXCLUDED.correct,
                  answered_at = EXCLUDED.answered_at,
                  updated_at = EXCLUDED.updated_at,
                  version = mock_exam_response.version + 1
                """).params(new java.util.HashMap<>(Map.of("id", responseId, "attemptId", attemptId,
                        "questionId", attemptQuestionId, "importedQuestionId", answer.importedQuestionId(),
                        "correct", correct, "answeredAt", utc(clock.instant()), "updatedAt", utc(clock.instant()))))
                .param("optionId",optionIds.size()==1?byId.get(optionIds.getFirst()).id():null,java.sql.Types.OTHER).update();
        jdbc.sql("DELETE FROM mock_exam_response_selection WHERE mock_exam_response_id=:id").param("id",responseId).update();
        for(String optionId:optionIds) jdbc.sql("INSERT INTO mock_exam_response_selection(mock_exam_response_id,imported_question_id,answer_option_id) VALUES(:response,:question,:option)")
                .param("response",responseId).param("question",answer.importedQuestionId())
                .param("option",byId.get(optionId).id()).update();
        log.info("mock_exam_answer_saved mockExamId={} learnerId={} questionId={}",
                attemptId, learnerId, attemptQuestionId);
        return progress(attemptId, attempt);
    }

    @Transactional
    public AttemptProgress flag(UUID learnerId, UUID attemptId, UUID attemptQuestionId, boolean flagged) {
        return flag(learnerId, attemptId, attemptQuestionId, flagged, null);
    }

    @Transactional
    public AttemptProgress flag(UUID learnerId, UUID attemptId, UUID attemptQuestionId, boolean flagged,
                                Long expectedVersion) {
        var attempt = attempt(learnerId, attemptId, true);
        if (expireIfRequired(attempt)) throw notActive("Mock examination time has expired");
        if (!"ACTIVE".equals(attempt.status())) throw notActive("Mock examination is not active");
        String versionPredicate = expectedVersion == null ? "" : "AND version = :version";
        var flagQuery = jdbc.sql("""
                UPDATE mock_exam_question SET flagged = :flagged, updated_at = :updatedAt, version = version + 1
                WHERE id = :questionId AND attempt_id = :attemptId
                  %s
                """.formatted(versionPredicate)).param("flagged", flagged).param("questionId", attemptQuestionId)
                .param("attemptId", attemptId).param("updatedAt", utc(clock.instant()));
        if (expectedVersion != null) flagQuery = flagQuery.param("version", expectedVersion);
        int updated = flagQuery.update();
        if (updated == 0) throw new ApiException(expectedVersion == null ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT,
                expectedVersion == null ? "MOCK_QUESTION_NOT_FOUND" : "STALE_MOCK_EXAM_VERSION",
                expectedVersion == null ? "Mock exam question not found" : "The question was changed by another request");
        log.info("mock_exam_question_flagged mockExamId={} learnerId={} questionId={} flagged={}",
                attemptId, learnerId, attemptQuestionId, flagged);
        return progress(attemptId, attempt);
    }

    @Transactional
    public ResultView submit(UUID learnerId, UUID attemptId) {
        var attempt = attempt(learnerId, attemptId, true);
        if (expireIfRequired(attempt)) return resultsInternal(attemptId);
        if (!"ACTIVE".equals(attempt.status())) {
            return resultsInternal(attemptId);
        }
        finalizeAttempt(attempt, "SUBMITTED", clock.instant());
        var result = resultsInternal(attemptId);
        log.info("mock_exam_submitted mockExamId={} learnerId={} examId={} releaseId={} percentage={} passed={} autoSubmitted=false",
                attemptId, learnerId, attempt.examId(), attempt.releaseId(), result.percentage(), result.passed());
        return result;
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
                  AND duration_minutes IS NOT NULL
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

    @Transactional
    public List<IncorrectQuestion> review(UUID learnerId, UUID attemptId, boolean incorrectOnly) {
        var attempt = attempt(learnerId, attemptId, true);
        expireIfRequired(attempt);
        if ("ACTIVE".equals(attempt(learnerId, attemptId, false).status())) throw new ApiException(
                HttpStatus.CONFLICT, "MOCK_EXAM_NOT_FINALIZED", "Submit the mock examination before reviewing it");
        String predicate = incorrectOnly ? "AND (response.id IS NULL OR NOT response.correct)" : "";
        var rows = jdbc.sql("""
                SELECT question.id AS attempt_question_id,
                       question.question_external_id AS question_id,
                       question.prompt_snapshot AS prompt, question.explanation_snapshot AS explanation,
                       question.question_type_snapshot AS question_type, response.id AS response_id,
                       question.topic_name_snapshot, question.objective_name_snapshot,
                       question.lesson_topic_external_id
                FROM mock_exam_question question
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :attemptId %s
                ORDER BY question.sequence_number
                """.formatted(predicate)).param("attemptId", attemptId).query((rs, row) -> new ReviewRow(
                        rs.getObject("attempt_question_id", UUID.class), rs.getString("question_id"),
                        rs.getString("prompt"), rs.getString("explanation"), rs.getString("question_type"),
                        rs.getObject("response_id", UUID.class), rs.getString("topic_name_snapshot"),
                        rs.getString("objective_name_snapshot"), rs.getString("lesson_topic_external_id"))).list();
        return rows.stream().map(row -> {
            var selected = row.responseId() == null ? List.<String>of() : jdbc.sql("""
                    SELECT option.external_answer_option_id FROM mock_exam_response_selection selection
                    JOIN imported_answer_option option ON option.id = selection.answer_option_id
                    WHERE selection.mock_exam_response_id = :id ORDER BY option.sort_order
                    """).param("id", row.responseId()).query(String.class).list();
            var options = jdbc.sql("""
                    SELECT option_id, option_text, correct, feedback FROM mock_exam_option_snapshot
                    WHERE attempt_question_id = :id ORDER BY display_order
                    """).param("id", row.attemptQuestionId()).query((rs, index) -> new ReviewOption(
                            rs.getString("option_id"), rs.getString("option_text"),
                            selected.contains(rs.getString("option_id")), rs.getBoolean("correct"),
                            rs.getBoolean("correct") && !selected.contains(rs.getString("option_id")),
                            rs.getString("feedback"))).list();
            return new IncorrectQuestion(row.questionId(), row.prompt(), row.questionType(), selected,
                    options.stream().filter(ReviewOption::correct).map(ReviewOption::id).toList(), options,
                    row.explanation(), row.topicName(), row.objectiveName(), row.lessonTopicId());
        }).toList();
    }

    @Scheduled(fixedDelayString = "${learning.mock-exam.expiry-scan-ms:30000}")
    @Transactional
    public void expireDueAttempts() {
        var due = jdbc.sql("""
                SELECT id FROM mock_exam_attempt
                WHERE status = 'ACTIVE'
                  AND duration_minutes IS NOT NULL
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

    private List<MockExamGenerator.TopicAllocation> deriveBalancedAllocations(UUID releaseId, int total) {
        var topics = jdbc.sql("""
                SELECT topic.id, topic.external_topic_id, subject.sort_order AS subject_order,
                       topic.sort_order AS topic_order, count(question.id) AS available
                FROM imported_topic topic
                JOIN imported_subject subject ON subject.id = topic.subject_id
                JOIN imported_question question ON question.topic_id = topic.id AND question.active
                WHERE topic.content_release_id = :releaseId
                GROUP BY topic.id, topic.external_topic_id, subject.sort_order, topic.sort_order
                ORDER BY subject.sort_order, topic.sort_order, topic.external_topic_id
                """).param("releaseId", releaseId).query((rs, row) -> new TopicAvailability(
                        rs.getObject("id", UUID.class), rs.getString("external_topic_id"),
                        rs.getInt("subject_order"), rs.getInt("topic_order"), rs.getInt("available"))).list();
        long subjectCount = topics.stream().map(TopicAvailability::subjectOrder).distinct().count();
        if (total < subjectCount) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "MOCK_DEFINITION_CANNOT_COVER_CURRICULUM",
                "Configured question count is smaller than the number of curriculum areas");
        var bySubject = topics.stream().collect(java.util.stream.Collectors.groupingBy(
                TopicAvailability::subjectOrder, java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()));
        var counts = new java.util.LinkedHashMap<TopicAvailability, Integer>();
        int chosen = 0;
        for (int round = 0; chosen < total; round++) {
            boolean added = false;
            for (var subjectTopics : bySubject.values()) {
                var available = subjectTopics.stream()
                        .filter(topic -> counts.getOrDefault(topic, 0) < topic.available()).toList();
                if (available.isEmpty()) continue;
                var topic = available.get(round % available.size());
                counts.merge(topic, 1, Integer::sum); chosen++; added = true;
                if (chosen == total) break;
            }
            if (!added) break;
        }
        if (counts.values().stream().mapToInt(Integer::intValue).sum() != total) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_MOCK_QUESTIONS",
                    "The active release cannot satisfy the configured mock examination size");
        }
        return counts.entrySet().stream().map(entry -> new MockExamGenerator.TopicAllocation(
                entry.getKey().id(), entry.getKey().externalId(), entry.getValue())).toList();
    }

    private void finalizeAttempt(Attempt attempt, String status, Instant completedAt) {
        int correct = jdbc.sql("SELECT count(*) FROM mock_exam_response WHERE attempt_id = :id AND correct")
                .param("id", attempt.id()).query(Integer.class).single();
        var score = MockExamScoring.calculate(correct, attempt.totalQuestions(), attempt.passingPercentage());
        int duration = MockExamTimer.state(attempt.startedAt(), attempt.durationMinutes(), completedAt).elapsedSeconds();
        jdbc.sql("""
                    UPDATE mock_exam_attempt SET status = :status, submitted_at = :submittedAt,
                    completed_at = :completedAt, duration_seconds = :duration, score = :score,
                    percentage = :percentage, passed = :passed, auto_submitted = :autoSubmitted,
                    incorrect_count = (SELECT count(*) FROM mock_exam_response r
                        WHERE r.attempt_id = :id AND NOT r.correct),
                    unanswered_count = (SELECT count(*) FROM mock_exam_question q
                        LEFT JOIN mock_exam_response r ON r.attempt_question_id = q.id
                        WHERE q.attempt_id = :id AND r.id IS NULL),
                    updated_at = :completedAt, version = version + 1
                WHERE id = :id AND status = 'ACTIVE'
                """).params(Map.of("status", status, "submittedAt", utc(completedAt),
                        "completedAt", utc(completedAt), "duration", duration, "score", score.correct(),
                        "percentage", score.percentage(), "passed", score.passed(),
                        "autoSubmitted", "EXPIRED".equals(status), "id", attempt.id())).update();
        persistBreakdowns(attempt.id());
    }

    private void persistBreakdowns(UUID attemptId) {
        jdbc.sql("""
                INSERT INTO mock_exam_subject_result
                  (attempt_id, subject_id, subject_name, question_count, correct_count,
                   incorrect_count, unanswered_count, percentage)
                SELECT :id, subject.external_subject_id, subject.name, count(*),
                       count(*) FILTER (WHERE response.correct),
                       count(*) FILTER (WHERE response.id IS NOT NULL AND NOT response.correct),
                       count(*) FILTER (WHERE response.id IS NULL),
                       round(100.0 * count(*) FILTER (WHERE response.correct) / count(*), 2)
                FROM mock_exam_question question
                JOIN imported_question imported ON imported.id = question.imported_question_id
                JOIN imported_topic topic ON topic.id = imported.topic_id
                JOIN imported_subject subject ON subject.id = topic.subject_id
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :id
                GROUP BY subject.external_subject_id, subject.name
                ON CONFLICT (attempt_id, subject_id) DO NOTHING
                """).param("id", attemptId).update();
        jdbc.sql("""
                INSERT INTO mock_exam_topic_result
                  (attempt_id, topic_id, topic_name, question_count, correct_count,
                   incorrect_count, unanswered_count, percentage)
                SELECT :id, topic.external_topic_id, topic.name, count(*),
                       count(*) FILTER (WHERE response.correct),
                       count(*) FILTER (WHERE response.id IS NOT NULL AND NOT response.correct),
                       count(*) FILTER (WHERE response.id IS NULL),
                       round(100.0 * count(*) FILTER (WHERE response.correct) / count(*), 2)
                FROM mock_exam_question question
                JOIN imported_question imported ON imported.id = question.imported_question_id
                JOIN imported_topic topic ON topic.id = imported.topic_id
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :id
                GROUP BY topic.external_topic_id, topic.name
                ON CONFLICT (attempt_id, topic_id) DO NOTHING
                """).param("id", attemptId).update();
        jdbc.sql("""
                INSERT INTO mock_exam_objective_result
                  (attempt_id, objective_id, objective_name, question_count, correct_count,
                   incorrect_count, unanswered_count, percentage)
                SELECT :id, question.objective_external_id, question.objective_name_snapshot, count(*),
                       count(*) FILTER (WHERE response.correct),
                       count(*) FILTER (WHERE response.id IS NOT NULL AND NOT response.correct),
                       count(*) FILTER (WHERE response.id IS NULL),
                       round(100.0 * count(*) FILTER (WHERE response.correct) / count(*), 2)
                FROM mock_exam_question question
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :id
                GROUP BY question.objective_external_id, question.objective_name_snapshot
                ON CONFLICT (attempt_id, objective_id) DO NOTHING
                """).param("id", attemptId).update();
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
        return new AttemptView(attempt.id(), attempt.examId(), attempt.releaseId(), attempt.blueprintName(),
                attempt.description(), attempt.status(), attempt.startedAt(), attempt.expiresAt(),
                attempt.submittedAt(), attempt.totalQuestions(), attempt.durationMinutes(),
                attempt.durationMinutes() != null, attempt.passingPercentage(), remaining,
                (int) navigation.stream().filter(NavigationItem::answered).count(), navigation);
    }

    private ResultView resultsInternal(UUID attemptId) {
        var attempt = jdbc.sql("""
                SELECT id, blueprint_name, status, started_at, completed_at, duration_seconds,
                       score, percentage, passed, total_questions, passing_percentage,
                       (SELECT count(*) FROM mock_exam_response response
                        WHERE response.attempt_id = mock_exam_attempt.id AND NOT response.correct) AS incorrect_count,
                       unanswered_count, auto_submitted
                FROM mock_exam_attempt WHERE id = :id
                """).param("id", attemptId).query((rs, row) -> new ResultRow(rs.getObject("id", UUID.class),
                        rs.getString("blueprint_name"), rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("completed_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("duration_seconds"), rs.getInt("score"), rs.getBigDecimal("percentage"),
                        rs.getBoolean("passed"), rs.getInt("total_questions"),
                        rs.getBigDecimal("passing_percentage"), rs.getInt("incorrect_count"),
                        rs.getInt("unanswered_count"), rs.getBoolean("auto_submitted"))).single();
        var subjects = jdbc.sql("""
                SELECT subject_id, subject_name, question_count, correct_count, incorrect_count,
                       unanswered_count, percentage FROM mock_exam_subject_result
                WHERE attempt_id = :attemptId ORDER BY subject_id
                """).param("attemptId", attemptId).query((rs, row) -> new SubjectResult(
                        rs.getString("subject_id"), rs.getString("subject_name"), rs.getInt("question_count"),
                        rs.getInt("correct_count"), rs.getInt("incorrect_count"), rs.getInt("unanswered_count"),
                        rs.getBigDecimal("percentage"))).list();
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
        var objectives = jdbc.sql("""
                SELECT objective_id, objective_name, question_count, correct_count,
                       question_count - unanswered_count AS answered, percentage
                FROM mock_exam_objective_result WHERE attempt_id = :attemptId ORDER BY objective_id
                """).param("attemptId", attemptId).query((rs, row) -> new ObjectiveResult(
                        rs.getString("objective_id"), rs.getString("objective_name"),
                        rs.getInt("question_count"), rs.getInt("answered"), rs.getInt("correct_count"),
                        rs.getBigDecimal("percentage"))).list();
        var incorrectRows = jdbc.sql("""
                SELECT question.imported_question_id AS id,
                       question.question_external_id AS external_question_version_id,
                       question.prompt_snapshot AS prompt,
                       question.explanation_snapshot AS explanation,
                       question.question_type_snapshot AS question_type, response.id AS response_id,
                       question.topic_external_id, question.topic_name_snapshot,
                       question.objective_external_id, question.objective_name_snapshot,
                       question.lesson_topic_external_id
                FROM mock_exam_question question
                LEFT JOIN mock_exam_response response ON response.attempt_question_id = question.id
                WHERE question.attempt_id = :attemptId AND (response.id IS NULL OR NOT response.correct)
                ORDER BY question.sequence_number
                """).param("attemptId", attemptId).query((rs, row) -> new IncorrectRow(
                        rs.getObject("id",UUID.class),rs.getString("external_question_version_id"),
                        rs.getString("prompt"),rs.getString("explanation"),
                        rs.getString("question_type"), rs.getObject("response_id",UUID.class),
                        rs.getString("topic_external_id"), rs.getString("topic_name_snapshot"),
                        rs.getString("objective_external_id"), rs.getString("objective_name_snapshot"),
                        rs.getString("lesson_topic_external_id"))).list();
        var incorrect = incorrectRows.stream().map(row -> {
            var selectedIds=row.responseId()==null?List.<String>of():jdbc.sql("""
                    SELECT option.external_answer_option_id FROM mock_exam_response_selection selection
                    JOIN imported_answer_option option ON option.id=selection.answer_option_id
                    WHERE selection.mock_exam_response_id=:id ORDER BY option.sort_order
                    """).param("id",row.responseId()).query(String.class).list();
            var options=jdbc.sql("SELECT option_id,option_text,correct,feedback FROM mock_exam_option_snapshot WHERE attempt_question_id=(SELECT id FROM mock_exam_question WHERE attempt_id=:attemptId AND question_external_id=:questionId) ORDER BY display_order")
                    .param("attemptId",attemptId).param("questionId",row.questionId()).query((rs,index)->new ReviewOption(
                            rs.getString("option_id"),rs.getString("option_text"),
                            selectedIds.contains(rs.getString("option_id")),rs.getBoolean("correct"),
                            rs.getBoolean("correct")&&!selectedIds.contains(rs.getString("option_id")),
                            rs.getString("feedback"))).list();
            return new IncorrectQuestion(row.questionId(),row.prompt(),row.questionType(),selectedIds,
                    options.stream().filter(ReviewOption::correct).map(ReviewOption::id).toList(),options,
                    row.explanation(), row.topicName(), row.objectiveName(), row.lessonTopicId());
        }).toList();
        return new ResultView(attempt.id(), attempt.blueprintName(), attempt.status(), attempt.startedAt(),
                attempt.completedAt(), attempt.durationSeconds(), attempt.score(), attempt.incorrectCount(),
                attempt.unansweredCount(), attempt.percentage(), attempt.passPercentage(), attempt.passed(),
                attempt.autoSubmitted(), subjects, topics, objectives, incorrect);
    }

    private Attempt attempt(UUID learnerId, UUID attemptId, boolean lock) {
        return jdbc.sql("""
                SELECT attempt.id, attempt.status, attempt.started_at, attempt.expires_at, attempt.submitted_at,
                       attempt.total_questions, attempt.duration_minutes, attempt.passing_percentage,
                       attempt.blueprint_name, attempt.exam_id, attempt.content_release_id, blueprint.description
                FROM mock_exam_attempt attempt JOIN mock_exam_blueprint blueprint ON blueprint.id = attempt.blueprint_id
                WHERE attempt.id = :id AND attempt.learner_id = :learnerId %s
                """.formatted(lock ? "FOR UPDATE" : ""))
                .param("id", attemptId).param("learnerId", learnerId)
                .query((rs, row) -> new Attempt(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("expires_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("submitted_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("total_questions"), rs.getObject("duration_minutes", Integer.class),
                        rs.getBigDecimal("passing_percentage"), rs.getString("blueprint_name"),
                        rs.getString("exam_id"), rs.getObject("content_release_id", UUID.class),
                        rs.getString("description")))
                .optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MOCK_EXAM_NOT_FOUND",
                        "Mock examination not found"));
    }

    private Attempt attempt(UUID attemptId) {
        return jdbc.sql("""
                SELECT attempt.id, attempt.status, attempt.started_at, attempt.expires_at, attempt.submitted_at,
                       attempt.total_questions, attempt.duration_minutes, attempt.passing_percentage,
                       attempt.blueprint_name, attempt.exam_id, attempt.content_release_id, blueprint.description
                FROM mock_exam_attempt attempt JOIN mock_exam_blueprint blueprint ON blueprint.id = attempt.blueprint_id
                WHERE attempt.id = :id
                """).param("id", attemptId).query((rs, row) -> new Attempt(rs.getObject("id", UUID.class),
                        rs.getString("status"), rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("expires_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("submitted_at", OffsetDateTime.class) == null ? null
                                : rs.getObject("submitted_at", OffsetDateTime.class).toInstant(),
                        rs.getInt("total_questions"), rs.getObject("duration_minutes", Integer.class),
                        rs.getBigDecimal("passing_percentage"), rs.getString("blueprint_name"),
                        rs.getString("exam_id"), rs.getObject("content_release_id", UUID.class),
                        rs.getString("description"))).single();
    }

    private static ApiException notActive(String message) {
        return new ApiException(HttpStatus.CONFLICT, "MOCK_EXAM_NOT_ACTIVE", message);
    }

    private static OffsetDateTime utc(Instant value) { return OffsetDateTime.ofInstant(value, ZoneOffset.UTC); }

    private static List<String> deterministicOptionOrder(List<String> optionIds, UUID attemptId, UUID questionId) {
        var ordered = new ArrayList<>(optionIds);
        ordered.sort(java.util.Comparator.comparing(optionId -> digest(attemptId + ":" + questionId + ":" + optionId)));
        return ordered;
    }

    private static String digest(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record AttemptView(UUID attemptId, String examId, UUID releaseId, String name, String description,
                              String status, Instant startedAt, Instant expiresAt, Instant submittedAt,
                              int totalQuestions, Integer durationMinutes, boolean timed, BigDecimal passPercentage,
                              int remainingSeconds, int answered,
                              List<NavigationItem> questions) {}
    public record ConfigurationView(String examId, String name, String description, int questionCount,
                                    Integer durationMinutes, boolean timed, BigDecimal passPercentage) {}
    public record NavigationItem(UUID attemptQuestionId, int sequenceNumber, boolean answered, boolean flagged) {}
    public record QuestionView(UUID attemptQuestionId, String questionId, String prompt, String questionType,
                               List<AnswerOptionView> answerOptions, int sequenceNumber, int totalQuestions,
                               List<String> selectedOptionIds, boolean flagged, long questionVersion,
                               long answerVersion, int remainingSeconds) {}
    public record AnswerOptionView(String id, String text) {}
    public record AttemptProgress(int answered, int total, int flagged, int remainingSeconds) {}
    public record ResultView(UUID attemptId, String name, String status, Instant startedAt, Instant completedAt,
                             int durationSeconds, int correctAnswers, int incorrectAnswers, int unansweredAnswers,
                             BigDecimal percentage, BigDecimal passPercentage, boolean passed, boolean autoSubmitted,
                             List<SubjectResult> subjects, List<TopicResult> topics, List<ObjectiveResult> objectives,
                             List<IncorrectQuestion> incorrectQuestions) {}
    public record SubjectResult(String subjectId, String subjectName, int total, int correct, int incorrect,
                                int unanswered, BigDecimal percentage) {}
    public record TopicResult(String topicId, String topicName, int total, int answered, int correct,
                              BigDecimal percentage) {}
    public record ObjectiveResult(String objectiveId, String objectiveName, int total, int answered, int correct,
                                  BigDecimal percentage) {}
    public record IncorrectQuestion(String questionId, String prompt, String questionType,
                                    List<String> selectedOptionIds, List<String> correctOptionIds,
                                    List<ReviewOption> options, String explanation, String topicName,
                                    String objectiveName, String lessonTopicId) {
        public IncorrectQuestion(String questionId,String prompt,String selectedId,String selectedText,
                                 String correctId,String correctText,String explanation){this(questionId,prompt,
                "SINGLE_CHOICE",selectedId==null?List.of():List.of(selectedId),List.of(correctId),
                List.of(new ReviewOption(correctId,correctText,correctId.equals(selectedId),true,!correctId.equals(selectedId),null)),explanation,null,null,null);}
        public String correctAnswerOptionId(){return correctOptionIds.isEmpty()?null:correctOptionIds.getFirst();}
    }
    public record ReviewOption(String id,String text,boolean selected,boolean correct,boolean missed,String feedback) {}
    public record HistoryView(UUID attemptId, String name, String status, Instant startedAt, int durationSeconds,
                              int score, BigDecimal percentage, boolean passed, int totalQuestions) {}
    private record Blueprint(UUID id, String name, String description, int totalQuestions, Integer durationMinutes,
                             BigDecimal passingPercentage, boolean randomizeQuestions, boolean randomizeOptions) {}
    private record Attempt(UUID id, String status, Instant startedAt, Instant expiresAt, Instant submittedAt,
                           int totalQuestions, Integer durationMinutes, BigDecimal passingPercentage,
                           String blueprintName, String examId, UUID releaseId, String description) {}
    private record QuestionRow(UUID id, String questionId, String prompt, String questionType, int sequenceNumber,
                               boolean flagged, long questionVersion, Long answerVersion) {}
    private record AnswerContext(UUID importedQuestionId, String questionType) {}
    private record AnswerSelection(UUID id,String externalId,boolean correct) {}
    private record ExistingResponse(UUID id,long version) {}
    private record IncorrectRow(UUID importedQuestionId,String questionId,String prompt,String explanation,
                                String questionType,UUID responseId,String topicId,String topicName,
                                String objectiveId,String objectiveName,String lessonTopicId) {}
    private record ResultRow(UUID id, String blueprintName, String status, Instant startedAt, Instant completedAt,
                             int durationSeconds, int score, BigDecimal percentage, boolean passed,
                             int totalQuestions, BigDecimal passPercentage, int incorrectCount,
                             int unansweredCount, boolean autoSubmitted) {}
    private record TopicAvailability(UUID id, String externalId, int subjectOrder, int topicOrder, int available) {}
    private record ReviewRow(UUID attemptQuestionId, String questionId, String prompt, String explanation,
                             String questionType, UUID responseId, String topicName, String objectiveName,
                             String lessonTopicId) {}
}
