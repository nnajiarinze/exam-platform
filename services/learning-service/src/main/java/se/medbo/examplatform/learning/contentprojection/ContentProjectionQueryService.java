package se.medbo.examplatform.learning.contentprojection;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;
import se.medbo.examplatform.learning.shared.ExternalExamIdentifier;

@Service
public class ContentProjectionQueryService {
    private final JdbcClient jdbc;

    public ContentProjectionQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<SubjectView> subjects(String examId) {
        String canonicalExamId = ExternalExamIdentifier.normalize(examId);
        UUID releaseId = jdbc.sql("""
                SELECT id FROM imported_content_release
                WHERE exam_id = :examId AND status = 'ACTIVE'
                ORDER BY published_at DESC LIMIT 1
                """).param("examId", canonicalExamId).query(UUID.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_ACTIVE_CONTENT_RELEASE",
                        "No active content release exists for the exam"));

        return jdbc.sql("""
                SELECT id, external_subject_id, name
                FROM imported_subject
                WHERE content_release_id = :releaseId
                ORDER BY sort_order, external_subject_id
                """).param("releaseId", releaseId)
                .query((rs, row) -> new SubjectRow(rs.getObject("id", UUID.class),
                        rs.getString("external_subject_id"), rs.getString("name")))
                .list().stream()
                .map(subject -> new SubjectView(subject.externalId(), subject.name(), topics(subject.id())))
                .toList();
    }

    private List<TopicView> topics(UUID subjectId) {
        return jdbc.sql("""
                SELECT external_topic_id, name, description
                FROM imported_topic
                WHERE subject_id = :subjectId
                ORDER BY sort_order, external_topic_id
                """).param("subjectId", subjectId)
                .query((rs, row) -> new TopicView(rs.getString("external_topic_id"), rs.getString("name"),
                        rs.getString("description"))).list();
    }

    public record SubjectView(String id, String name, List<TopicView> topics) {}
    public record TopicView(String id, String name, String description) {}
    private record SubjectRow(UUID id, String externalId, String name) {}
}
