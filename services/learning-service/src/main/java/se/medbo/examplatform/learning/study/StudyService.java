package se.medbo.examplatform.learning.study;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;
import se.medbo.examplatform.learning.shared.ExternalExamIdentifier;

@Service
public class StudyService {
    private final JdbcClient jdbc;

    public StudyService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<SubjectSummary> subjects(UUID learnerId, String examId) {
        UUID releaseId = activeRelease(examId);
        return jdbc.sql("""
                SELECT s.external_subject_id,s.name,count(DISTINCT t.id) AS topics,
                  count(DISTINCT p.topic_id) FILTER (WHERE p.completed_at IS NOT NULL) AS completed
                FROM imported_subject s
                JOIN imported_topic t ON t.subject_id=s.id
                JOIN imported_lesson_section ls ON ls.topic_id=t.id
                LEFT JOIN lesson_progress p ON p.learner_id=:learner AND p.content_release_id=:release
                  AND p.topic_id=t.id
                WHERE s.content_release_id=:release
                GROUP BY s.id,s.external_subject_id,s.name,s.sort_order
                ORDER BY s.sort_order,s.external_subject_id
                """).param("learner", learnerId).param("release", releaseId)
                .query((rs, row) -> new SubjectSummary(rs.getString(1), rs.getString(2),
                        rs.getInt(3), rs.getInt(4))).list();
    }

    @Transactional(readOnly = true)
    public List<TopicSummary> topics(UUID learnerId, String examId, String subjectId) {
        UUID releaseId = activeRelease(examId);
        return jdbc.sql("""
                SELECT t.external_topic_id,t.name,t.description,count(DISTINCT ls.id) AS sections,
                  count(DISTINCT q.id) FILTER (WHERE q.active) AS questions,
                  COALESCE(p.completed_section_count,0) AS completed,p.completed_at
                FROM imported_subject s
                JOIN imported_topic t ON t.subject_id=s.id
                JOIN imported_lesson_section ls ON ls.topic_id=t.id
                LEFT JOIN imported_question q ON q.topic_id=t.id AND q.content_release_id=:release
                LEFT JOIN lesson_progress p ON p.learner_id=:learner AND p.content_release_id=:release
                  AND p.topic_id=t.id
                WHERE s.content_release_id=:release AND s.external_subject_id=:subject
                GROUP BY t.id,t.external_topic_id,t.name,t.description,t.sort_order,
                  p.completed_section_count,p.completed_at
                ORDER BY t.sort_order,t.external_topic_id
                """).param("learner", learnerId).param("release", releaseId).param("subject", subjectId)
                .query((rs, row) -> {
                    int total = rs.getInt("sections");
                    int completed = rs.getInt("completed");
                    return new TopicSummary(rs.getString("external_topic_id"), rs.getString("name"),
                            rs.getString("description"), total, readingTime(topicInternal(releaseId,
                            rs.getString("external_topic_id"))), rs.getInt("questions"), completed,
                            percentage(completed, total), rs.getObject("completed_at") != null);
                }).list();
    }

    @Transactional(readOnly = true)
    public Lesson lesson(UUID learnerId, String examId, String topicId) {
        UUID releaseId = activeRelease(examId);
        var topic = jdbc.sql("""
                SELECT t.id,t.external_topic_id,t.name,t.description,r.external_release_id,r.release_version
                FROM imported_topic t JOIN imported_content_release r ON r.id=t.content_release_id
                WHERE t.content_release_id=:release AND t.external_topic_id=:topic
                """).param("release", releaseId).param("topic", topicId).query().listOfRows();
        if (topic.isEmpty()) throw notFound("STUDY_TOPIC_NOT_FOUND", "This topic is not available for study");
        UUID internalTopicId = (UUID) topic.getFirst().get("id");
        List<Section> sections = sections(internalTopicId);
        if (sections.isEmpty()) throw notFound("STUDY_LESSON_NOT_AVAILABLE",
                "This topic is not available for study yet");
        Progress progress = progress(learnerId, releaseId, internalTopicId, sections);
        int questions = jdbc.sql("SELECT count(*) FROM imported_question WHERE topic_id=:topic AND active")
                .param("topic", internalTopicId).query(Integer.class).single();
        var row = topic.getFirst();
        return new Lesson(row.get("external_topic_id").toString(), row.get("name").toString(),
                (String) row.get("description"), readingTime(sections), questions,
                row.get("external_release_id").toString(), row.get("release_version").toString(),
                sections, progress);
    }

    @Transactional
    public Progress update(UUID learnerId, String examId, String topicId, ProgressUpdate update) {
        Lesson lesson = lesson(learnerId, examId, topicId);
        UUID releaseId = activeRelease(examId);
        UUID internalTopicId = topicInternal(releaseId, topicId);
        var selected = jdbc.sql("""
                SELECT id,display_order FROM imported_lesson_section
                WHERE topic_id=:topic AND external_section_id=:section
                """).param("topic", internalTopicId).param("section", update.sectionId())
                .query((rs, row) -> new Selected(rs.getObject(1, UUID.class), rs.getInt(2))).optional()
                .orElseThrow(() -> notFound("STUDY_SECTION_NOT_FOUND", "Lesson section was not found"));
        int index = 0;
        for (int i = 0; i < lesson.sections().size(); i++) {
            if (lesson.sections().get(i).sectionId().equals(update.sectionId())) index = i;
        }
        int completed = update.completed() ? index + 1 : lesson.progress().completedSectionCount();
        completed = Math.max(completed, lesson.progress().completedSectionCount());
        boolean complete = update.completed() && index == lesson.sections().size() - 1;
        jdbc.sql("""
                INSERT INTO lesson_progress(id,learner_id,content_release_id,topic_id,last_section_id,
                  completed_section_count,started_at,last_accessed_at,completed_at)
                VALUES(:id,:learner,:release,:topic,:section,:completed,now(),now(),
                  CASE WHEN :complete THEN now() ELSE NULL END)
                ON CONFLICT(learner_id,content_release_id,topic_id) DO UPDATE SET
                  last_section_id=EXCLUDED.last_section_id,
                  completed_section_count=GREATEST(lesson_progress.completed_section_count,
                    EXCLUDED.completed_section_count),
                  last_accessed_at=now(),
                  completed_at=CASE WHEN :complete THEN COALESCE(lesson_progress.completed_at,now())
                    ELSE lesson_progress.completed_at END
                """).param("id", UUID.randomUUID()).param("learner", learnerId).param("release", releaseId)
                .param("topic", internalTopicId).param("section", selected.id()).param("completed", completed)
                .param("complete", complete).update();
        return progress(learnerId, releaseId, internalTopicId, lesson.sections());
    }

    @Transactional(readOnly = true)
    public ContinueLearning continueLearning(UUID learnerId, String examId) {
        UUID releaseId = activeRelease(examId);
        return jdbc.sql("""
                SELECT t.external_topic_id,t.name,s.name AS subject_name,ls.external_section_id,
                  p.completed_section_count,(SELECT count(*) FROM imported_lesson_section x
                    WHERE x.topic_id=t.id) AS total
                FROM lesson_progress p JOIN imported_topic t ON t.id=p.topic_id
                JOIN imported_subject s ON s.id=t.subject_id
                JOIN imported_lesson_section ls ON ls.id=p.last_section_id
                WHERE p.learner_id=:learner AND p.content_release_id=:release
                  AND p.completed_at IS NULL
                ORDER BY p.last_accessed_at DESC LIMIT 1
                """).param("learner", learnerId).param("release", releaseId)
                .query((rs, row) -> new ContinueLearning(rs.getString("external_topic_id"),
                        rs.getString("name"), rs.getString("subject_name"),
                        rs.getString("external_section_id"), rs.getInt("completed_section_count"),
                        rs.getInt("total"))).optional().orElse(null);
    }

    private List<Section> sections(UUID topicId) {
        var rows = jdbc.sql("""
                SELECT id,external_section_id,title,explanation,display_order
                FROM imported_lesson_section WHERE topic_id=:topic
                ORDER BY display_order,external_section_id
                """).param("topic", topicId).query().listOfRows();
        var result = new ArrayList<Section>();
        for (var row : rows) {
            UUID id = (UUID) row.get("id");
            var sources = jdbc.sql("SELECT title,url FROM imported_lesson_source WHERE lesson_section_id=:id ORDER BY title,url")
                    .param("id", id).query((rs, n) -> new SourceLink(rs.getString(1), rs.getString(2))).list();
            result.add(new Section(row.get("external_section_id").toString(), row.get("title").toString(),
                    row.get("explanation").toString(), ((Number) row.get("display_order")).intValue(), sources));
        }
        return result;
    }

    private Progress progress(UUID learnerId, UUID releaseId, UUID topicId, List<Section> sections) {
        return jdbc.sql("""
                SELECT ls.external_section_id,p.completed_section_count,p.started_at,p.last_accessed_at,p.completed_at
                FROM lesson_progress p JOIN imported_lesson_section ls ON ls.id=p.last_section_id
                WHERE p.learner_id=:learner AND p.content_release_id=:release AND p.topic_id=:topic
                """).param("learner", learnerId).param("release", releaseId).param("topic", topicId)
                .query((rs, row) -> new Progress(rs.getString(1), rs.getInt(2), sections.size(),
                        percentage(rs.getInt(2), sections.size()), rs.getObject(5) != null,
                        rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class),
                        rs.getObject(5, OffsetDateTime.class))).optional()
                .orElse(new Progress(sections.getFirst().sectionId(), 0, sections.size(), 0,
                        false, null, null, null));
    }

    private UUID activeRelease(String examId) {
        return jdbc.sql("SELECT id FROM imported_content_release WHERE exam_id=:exam AND status='ACTIVE' ORDER BY published_at DESC LIMIT 1")
                .param("exam", ExternalExamIdentifier.normalize(examId)).query(UUID.class).optional()
                .orElseThrow(() -> notFound("NO_ACTIVE_CONTENT_RELEASE",
                        "No active content release exists for the exam"));
    }

    private UUID topicInternal(UUID release, String topic) {
        return jdbc.sql("SELECT id FROM imported_topic WHERE content_release_id=:release AND external_topic_id=:topic")
                .param("release", release).param("topic", topic).query(UUID.class).optional()
                .orElseThrow(() -> notFound("STUDY_TOPIC_NOT_FOUND", "This topic is not available for study"));
    }

    private int readingTime(UUID topicId) { return readingTime(sections(topicId)); }
    private int readingTime(List<Section> sections) {
        int words = sections.stream().mapToInt(s -> s.explanation().trim().split("\\s+").length).sum();
        return Math.max(15, (int) Math.ceil(words * 60.0 / 200.0));
    }
    private int percentage(int completed, int total) { return total == 0 ? 0 : Math.min(100, completed * 100 / total); }
    private ApiException notFound(String code, String message) { return new ApiException(HttpStatus.NOT_FOUND, code, message); }

    public record SubjectSummary(String subjectId,String title,int topicCount,int completedTopicCount){}
    public record TopicSummary(String topicId,String title,String summary,int keyFactCount,int readingTimeSeconds,
                               int relatedQuestionCount,int completedSectionCount,int completionPercentage,boolean completed){}
    public record SourceLink(String title,String url){}
    public record Section(String sectionId,String title,String explanation,int displayOrder,List<SourceLink> sourceLinks){}
    public record Progress(String lastSectionId,int completedSectionCount,int totalSectionCount,
                           int completionPercentage,boolean completed,OffsetDateTime startedAt,
                           OffsetDateTime lastAccessedAt,OffsetDateTime completedAt){}
    public record Lesson(String topicId,String title,String summary,int readingTimeSeconds,int relatedQuestionCount,
                         String contentReleaseId,String version,List<Section> sections,Progress progress){}
    public record ProgressUpdate(@NotBlank @Size(max=200) String sectionId,boolean completed){}
    public record ContinueLearning(String topicId,String topicTitle,String subjectTitle,String lastSectionId,
                                   int completedSectionCount,int totalSectionCount){}
    private record Selected(UUID id,int order){}
}
