package se.medbo.examplatform.learning.practice;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;
import se.medbo.examplatform.learning.shared.ExternalExamIdentifier;

@Service
public class PracticeService {
    private static final Logger log = LoggerFactory.getLogger(PracticeService.class);
    private final JdbcClient jdbc;
    private final QuestionSelector selector;
    private final int maxQuestionCount;
    private final Clock clock;

    @Autowired
    public PracticeService(JdbcClient jdbc, QuestionSelector selector,
            @Value("${learning.practice.max-question-count:50}") int maxQuestionCount) {
        this(jdbc, selector, maxQuestionCount, Clock.systemUTC());
    }

    PracticeService(JdbcClient jdbc, QuestionSelector selector, int maxQuestionCount, Clock clock) {
        this.jdbc = jdbc;
        this.selector = selector;
        this.maxQuestionCount = maxQuestionCount;
        this.clock = clock;
    }

    @Transactional
    public SessionView create(UUID learnerId, CreateSession command) {
        String canonicalExamId = ExternalExamIdentifier.normalize(command.examId());
        if (command.questionCount() < 1 || command.questionCount() > maxQuestionCount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUESTION_COUNT",
                    "Question count must be between 1 and " + maxQuestionCount);
        }
        if (command.mode() == PracticeMode.INCORRECT_REVIEW) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRACTICE_MODE",
                    "Incorrect-answer review is not implemented");
        }
        if (command.mode() == PracticeMode.TOPIC && (command.topicId() == null || command.topicId().isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOPIC_REQUIRED", "TOPIC mode requires a topic");
        }
        if (command.mode() == PracticeMode.MIXED && command.topicId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOPIC_NOT_ALLOWED", "MIXED mode does not accept a topic");
        }

        var release = jdbc.sql("""
                SELECT id, exam_id FROM imported_content_release
                WHERE exam_id = :examId AND status = 'ACTIVE'
                ORDER BY published_at DESC LIMIT 1
                """).param("examId", canonicalExamId)
                .query((rs, row) -> new ActiveRelease(rs.getObject("id", UUID.class), rs.getString("exam_id")))
                .optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ACTIVE_CONTENT_RELEASE",
                        "No active content release exists for the exam"));

        UUID topicId = null;
        if (command.mode() == PracticeMode.TOPIC) {
            topicId = jdbc.sql("""
                    SELECT id FROM imported_topic
                    WHERE content_release_id = :releaseId AND external_topic_id = :topicId
                    """).param("releaseId", release.id()).param("topicId", command.topicId())
                    .query(UUID.class).optional()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "Topic not found"));
        }

        String sql = "SELECT id, topic_id, knowledge_fact_id FROM imported_question "
                + "WHERE content_release_id = :releaseId AND active = TRUE ORDER BY id";
        var spec = jdbc.sql(sql).param("releaseId", release.id());
        List<QuestionSelector.QuestionCandidate> eligible = spec.query((rs, row) ->
                new QuestionSelector.QuestionCandidate(rs.getObject("id", UUID.class),
                        rs.getObject("topic_id", UUID.class),
                        rs.getString("knowledge_fact_id"))).list();
        var selected = selector.selectForMode(eligible, command.mode(), topicId, command.questionCount());

        UUID sessionId = UUID.randomUUID();
        var sessionParams = new HashMap<String, Object>();
        sessionParams.put("id", sessionId);
        sessionParams.put("learnerId", learnerId);
        sessionParams.put("examId", canonicalExamId);
        sessionParams.put("releaseId", release.id());
        sessionParams.put("topicId", topicId);
        sessionParams.put("mode", command.mode().name());
        sessionParams.put("startedAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        jdbc.sql("""
                INSERT INTO practice_session
                  (id, learner_id, exam_id, content_release_id, topic_id, mode, status, started_at)
                VALUES (:id, :learnerId, :examId, :releaseId, :topicId, :mode, 'ACTIVE', :startedAt)
                """).params(sessionParams).update();
        int sequence = 1;
        for (var question : selected) {
            jdbc.sql("""
                    INSERT INTO practice_session_question
                      (id, practice_session_id, imported_question_id, content_release_id,
                       sequence_number, answered)
                    VALUES (:id, :sessionId, :questionId, :releaseId, :sequence, FALSE)
                    """).params(Map.of("id", UUID.randomUUID(), "sessionId", sessionId,
                    "questionId", question.id(), "releaseId", release.id(), "sequence", sequence++)).update();
        }
        log.atInfo().addKeyValue("sessionId", sessionId).addKeyValue("learnerId", learnerId)
                .addKeyValue("questionCount", selected.size()).log("practice_session_created");
        return getSession(learnerId, sessionId);
    }

    @Transactional(readOnly = true)
    public SessionView getSession(UUID learnerId, UUID sessionId) {
        var metadata = sessionMetadata(learnerId, sessionId);
        int answered = jdbc.sql("SELECT count(*) FROM practice_session_question WHERE practice_session_id = :id AND answered")
                .param("id", sessionId).query(Integer.class).single();
        return new SessionView(metadata.id(), metadata.mode(), metadata.topicExternalId(), metadata.status(),
                answered, metadata.total(), nextQuestionInternal(sessionId));
    }

    @Transactional(readOnly = true)
    public QuestionView nextQuestion(UUID learnerId, UUID sessionId) {
        sessionMetadata(learnerId, sessionId);
        return nextQuestionInternal(sessionId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                "NO_UNANSWERED_QUESTION", "No unanswered question remains"));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AnswerResult submit(UUID learnerId, UUID sessionId, SubmitAnswer command) {
        if (command.selectedOptionIds() == null || command.selectedOptionIds().isEmpty()
                || command.selectedOptionIds().size() != new java.util.HashSet<>(command.selectedOptionIds()).size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER_SELECTION",
                    "At least one unique answer option must be selected");
        }
        var session = jdbc.sql("""
                SELECT status FROM practice_session WHERE id = :id AND learner_id = :learnerId FOR UPDATE
                """).param("id", sessionId).param("learnerId", learnerId).query(String.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRACTICE_SESSION_NOT_FOUND",
                        "Practice session not found"));
        if (!"ACTIVE".equals(session)) {
            boolean alreadyAnswered = jdbc.sql("""
                    SELECT EXISTS(
                      SELECT 1 FROM practice_response
                      WHERE practice_session_id = :sessionId
                        AND practice_session_question_id = :sessionQuestionId
                        AND learner_id = :learnerId)
                    """).param("sessionId", sessionId).param("sessionQuestionId", command.sessionQuestionId())
                    .param("learnerId", learnerId).query(Boolean.class).single();
            if (alreadyAnswered) {
                throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESPONSE", "Question was already answered");
            }
            throw new ApiException(HttpStatus.CONFLICT, "PRACTICE_SESSION_COMPLETED",
                    "Practice session is not active");
        }
        var answerContext = jdbc.sql("""
                SELECT psq.id AS session_question_id, psq.answered, iq.id AS question_id,
                       iq.topic_id, iq.explanation, iq.question_type
                FROM practice_session_question psq
                JOIN imported_question iq ON iq.id = psq.imported_question_id
                WHERE psq.id = :sessionQuestionId AND psq.practice_session_id = :sessionId
                FOR UPDATE OF psq
                """).param("sessionQuestionId", command.sessionQuestionId()).param("sessionId", sessionId)
                .query((rs, row) -> new AnswerContext(rs.getObject("session_question_id", UUID.class),
                        rs.getObject("question_id", UUID.class), rs.getBoolean("answered"),
                        rs.getObject("topic_id", UUID.class),
                        rs.getString("explanation"), rs.getString("question_type"))).optional()
                .orElseThrow(() -> answerContextError(sessionId, command));
        if (answerContext.answered()) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESPONSE", "Question was already answered");
        }
        var options = jdbc.sql("""
                SELECT id, external_answer_option_id, correct, feedback
                FROM imported_answer_option WHERE question_id = :questionId ORDER BY sort_order
                """).param("questionId", answerContext.importedQuestionId())
                .query((rs, row) -> new AnswerDetail(rs.getObject("id", UUID.class),
                        rs.getString("external_answer_option_id"), rs.getBoolean("correct"),
                        rs.getString("feedback"))).list();
        var byExternalId = options.stream().collect(java.util.stream.Collectors.toMap(AnswerDetail::externalId, o -> o));
        if (!byExternalId.keySet().containsAll(command.selectedOptionIds())) {
            throw answerContextError(sessionId, command);
        }
        if (!"MULTIPLE_CHOICE".equals(answerContext.questionType()) && command.selectedOptionIds().size() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER_SELECTION",
                    "Single-choice questions require exactly one selected option");
        }
        var correctOptionIds = options.stream().filter(AnswerDetail::correct).map(AnswerDetail::externalId).toList();
        boolean correct = AnswerEvaluator.isExactMatch(command.selectedOptionIds(), correctOptionIds);
        Instant now = clock.instant();
        UUID responseId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                INSERT INTO practice_response
                  (id, practice_session_id, practice_session_question_id, learner_id,
                   selected_answer_option_id, imported_question_id, correct, answered_at,
                   response_time_millis)
                VALUES (:id, :sessionId, :sessionQuestionId, :learnerId, :optionId, :questionId,
                        :correct, :answeredAt, :responseTime)
                ON CONFLICT (practice_session_question_id) DO NOTHING
                """).params(new HashMap<>(Map.of("id", responseId, "sessionId", sessionId,
                "sessionQuestionId", answerContext.sessionQuestionId(), "learnerId", learnerId,
                "questionId", answerContext.importedQuestionId(), "correct", correct,
                "answeredAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))))
                .param("optionId", command.selectedOptionIds().size() == 1
                        ? byExternalId.get(command.selectedOptionIds().getFirst()).id() : null, java.sql.Types.OTHER)
                .param("responseTime", command.responseTimeMillis()).update();
        if (inserted == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESPONSE", "Question was already answered");
        }
        for (String selectedId : command.selectedOptionIds()) {
            jdbc.sql("INSERT INTO practice_response_selection(practice_response_id,imported_question_id,answer_option_id) VALUES(:responseId,:questionId,:optionId)")
                    .param("responseId", responseId).param("questionId",answerContext.importedQuestionId())
                    .param("optionId", byExternalId.get(selectedId).id()).update();
        }
        jdbc.sql("UPDATE practice_session_question SET answered = TRUE WHERE id = :id")
                .param("id", answerContext.sessionQuestionId()).update();
        updateProgress(learnerId, answerContext.topicId(), correct, now);
        int total = jdbc.sql("SELECT count(*) FROM practice_session_question WHERE practice_session_id = :id")
                .param("id", sessionId).query(Integer.class).single();
        int answered = jdbc.sql("SELECT count(*) FROM practice_session_question WHERE practice_session_id = :id AND answered")
                .param("id", sessionId).query(Integer.class).single();
        if (SessionCompletion.isComplete(answered, total)) {
            jdbc.sql("UPDATE practice_session SET status = 'COMPLETED', completed_at = :completedAt WHERE id = :id")
                    .param("completedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC)).param("id", sessionId).update();
            log.atInfo().addKeyValue("sessionId", sessionId).addKeyValue("learnerId", learnerId)
                    .log("practice_session_completed");
        }
        var feedback = options.stream().map(o -> new OptionFeedback(o.externalId(),
                command.selectedOptionIds().contains(o.externalId()), o.correct(), o.feedback())).toList();
        return new AnswerResult(correct, List.copyOf(command.selectedOptionIds()), correctOptionIds,
                answerContext.explanation(), feedback, new SessionProgress(answered, total));
    }

    private ApiException answerContextError(UUID sessionId, SubmitAnswer command) {
        boolean sessionQuestionExists = jdbc.sql("""
                SELECT EXISTS(SELECT 1 FROM practice_session_question WHERE id = :id AND practice_session_id = :sessionId)
                """).param("id", command.sessionQuestionId()).param("sessionId", sessionId)
                .query(Boolean.class).single();
        return sessionQuestionExists
                ? new ApiException(HttpStatus.BAD_REQUEST, "ANSWER_OPTION_NOT_FOR_QUESTION",
                        "Answer option does not belong to the question")
                : new ApiException(HttpStatus.NOT_FOUND, "SESSION_QUESTION_NOT_FOUND", "Session question not found");
    }

    private void updateProgress(UUID learnerId, UUID topicId, boolean correct, Instant now) {
        var initial = se.medbo.examplatform.learning.progress.TopicProgressCalculation.initial(correct);
        jdbc.sql("""
                INSERT INTO topic_progress
                  (id, learner_id, topic_id, questions_answered, correct_answers, accuracy_percentage,
                   last_practised_at, updated_at)
                VALUES (:id, :learnerId, :topicId, 1, :correctCount, :accuracy, :now, :now)
                ON CONFLICT (learner_id, topic_id) DO UPDATE SET
                  questions_answered = topic_progress.questions_answered + 1,
                  correct_answers = topic_progress.correct_answers + EXCLUDED.correct_answers,
                  accuracy_percentage = ROUND(
                    ((topic_progress.correct_answers + EXCLUDED.correct_answers)::numeric * 100)
                    / (topic_progress.questions_answered + 1), 2),
                  last_practised_at = EXCLUDED.last_practised_at,
                  updated_at = EXCLUDED.updated_at
                """).params(Map.of("id", UUID.randomUUID(), "learnerId", learnerId, "topicId", topicId,
                "correctCount", initial.correctAnswers(), "accuracy", initial.accuracyPercentage(),
                "now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))).update();
    }

    private SessionMetadata sessionMetadata(UUID learnerId, UUID sessionId) {
        return jdbc.sql("""
                SELECT ps.id, ps.mode, ps.status, it.external_topic_id,
                       (SELECT count(*) FROM practice_session_question q WHERE q.practice_session_id = ps.id) AS total
                FROM practice_session ps
                LEFT JOIN imported_topic it ON it.id = ps.topic_id
                WHERE ps.id = :id AND ps.learner_id = :learnerId
                """).param("id", sessionId).param("learnerId", learnerId)
                .query((rs, row) -> new SessionMetadata(rs.getObject("id", UUID.class),
                        PracticeMode.valueOf(rs.getString("mode")), rs.getString("external_topic_id"),
                        rs.getString("status"), rs.getInt("total"))).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRACTICE_SESSION_NOT_FOUND",
                        "Practice session not found"));
    }

    private java.util.Optional<QuestionView> nextQuestionInternal(UUID sessionId) {
        var question = jdbc.sql("""
                SELECT psq.id AS session_question_id, iq.external_question_version_id, iq.prompt,
                       iq.question_type, psq.sequence_number,
                       (SELECT count(*) FROM practice_session_question x WHERE x.practice_session_id = psq.practice_session_id) AS total
                FROM practice_session_question psq
                JOIN imported_question iq ON iq.id = psq.imported_question_id
                WHERE psq.practice_session_id = :sessionId AND psq.answered = FALSE
                ORDER BY psq.sequence_number LIMIT 1
                """).param("sessionId", sessionId)
                .query((rs, row) -> new QuestionRow(rs.getObject("session_question_id", UUID.class),
                        rs.getString("external_question_version_id"), rs.getString("prompt"),
                        rs.getString("question_type"), rs.getInt("sequence_number"), rs.getInt("total")))
                .optional();
        return question.map(row -> {
            var options = jdbc.sql("""
                    SELECT iao.external_answer_option_id, iao.text
                    FROM practice_session_question psq
                    JOIN imported_answer_option iao ON iao.question_id = psq.imported_question_id
                    WHERE psq.id = :id ORDER BY iao.sort_order
                    """).param("id", row.sessionQuestionId())
                    .query((rs, index) -> new AnswerOptionView(rs.getString("external_answer_option_id"),
                            rs.getString("text"))).list();
            return new QuestionView(row.sessionQuestionId(), row.questionId(), row.prompt(), row.questionType(),
                    options, List.of(), row.sequenceNumber(), row.total());
        });
    }

    public record CreateSession(String examId, String topicId, PracticeMode mode, int questionCount) {}
    public record SubmitAnswer(UUID sessionQuestionId, List<String> selectedOptionIds, Long responseTimeMillis) {
        public SubmitAnswer(UUID sessionQuestionId,String selectedOptionId,Long responseTimeMillis){
            this(sessionQuestionId,List.of(selectedOptionId),responseTimeMillis);
        }
    }
    public record SessionView(UUID sessionId, PracticeMode mode, String topicId, String status, int answered,
                              int total, java.util.Optional<QuestionView> nextQuestion) {}
    public record QuestionView(UUID sessionQuestionId, String questionId, String prompt, String questionType,
                               List<AnswerOptionView> answerOptions, List<String> selectedOptionIds,
                               int sequenceNumber, int totalQuestionCount) {}
    public record AnswerOptionView(String id, String text) {}
    public record AnswerResult(boolean correct, List<String> selectedOptionIds, List<String> correctOptionIds,
                               String explanation, List<OptionFeedback> optionFeedback,
                               SessionProgress sessionProgress) {}
    public record OptionFeedback(String optionId, boolean selected, boolean correct, String feedback) {}
    public record SessionProgress(int answered, int total) {}
    private record ActiveRelease(UUID id, String examId) {}
    private record SessionMetadata(UUID id, PracticeMode mode, String topicExternalId, String status, int total) {}
    private record QuestionRow(UUID sessionQuestionId, String questionId, String prompt, String questionType,
                               int sequenceNumber, int total) {}
    private record AnswerContext(UUID sessionQuestionId, UUID importedQuestionId, boolean answered, UUID topicId,
                                 String explanation, String questionType) {}
    private record AnswerDetail(UUID id, String externalId, boolean correct, String feedback) {}
}
