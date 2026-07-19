package se.medbo.examplatform.learning.progress;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressService {
    private final JdbcClient jdbc;

    public ProgressService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TopicProgressView> topics(UUID learnerId) {
        return jdbc.sql("""
                SELECT it.external_topic_id,
                       SUM(tp.questions_answered) AS answered,
                       SUM(tp.correct_answers) AS correct,
                       ROUND(SUM(tp.correct_answers)::numeric * 100 / SUM(tp.questions_answered), 2) AS accuracy,
                       MAX(tp.last_practised_at) AS last_practised_at
                FROM topic_progress tp
                JOIN imported_topic it ON it.id = tp.topic_id
                WHERE tp.learner_id = :learnerId
                GROUP BY it.external_topic_id
                ORDER BY it.external_topic_id
                """).param("learnerId", learnerId)
                .query((rs, row) -> new TopicProgressView(rs.getString("external_topic_id"),
                        rs.getInt("answered"), rs.getInt("correct"), rs.getBigDecimal("accuracy"),
                        rs.getObject("last_practised_at", OffsetDateTime.class).toInstant())).list();
    }

    public record TopicProgressView(String topicId, int questionsAnswered, int correctAnswers,
                                    BigDecimal accuracyPercentage, Instant lastPractisedAt) {}
}
