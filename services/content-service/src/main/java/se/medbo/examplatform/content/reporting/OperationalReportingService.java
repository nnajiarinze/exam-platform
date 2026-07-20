package se.medbo.examplatform.content.reporting;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalReportingService {
    private final JdbcClient jdbc;

    public OperationalReportingService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Map<String,Object> contentHealth() {
        var result = new LinkedHashMap<String,Object>();
        result.put("questions", counts("question"));
        result.put("knowledgeFacts", counts("knowledge_fact"));
        result.put("learningObjectives", statusCounts("learning_objective"));
        result.put("sources", sourceCounts());
        result.put("questionsMissingFacts", count("SELECT count(*) FROM question q WHERE NOT EXISTS (SELECT 1 FROM question_knowledge_fact qf WHERE qf.question_version_id=q.current_version_id)"));
        result.put("factsWithoutQuestions", count("SELECT count(*) FROM knowledge_fact f WHERE NOT EXISTS (SELECT 1 FROM question_knowledge_fact qf JOIN knowledge_fact_version fv ON fv.id=qf.knowledge_fact_version_id WHERE fv.knowledge_fact_id=f.id)"));
        result.put("objectivesWithoutQuestions", count("SELECT count(*) FROM learning_objective o WHERE NOT EXISTS (SELECT 1 FROM question q WHERE q.learning_objective_id=o.id AND q.status<>'RETIRED')"));
        result.put("topicsWithoutQuestions", count("SELECT count(*) FROM topic t WHERE NOT EXISTS (SELECT 1 FROM learning_objective o JOIN question q ON q.learning_objective_id=o.id WHERE o.topic_id=t.id AND q.status<>'RETIRED')"));
        result.put("subjectsWithoutQuestions", count("SELECT count(*) FROM subject s WHERE NOT EXISTS (SELECT 1 FROM topic t JOIN learning_objective o ON o.topic_id=t.id JOIN question q ON q.learning_objective_id=o.id WHERE t.subject_id=s.id AND q.status<>'RETIRED')"));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> reviewHealth(String actor) {
        var result = new LinkedHashMap<String,Object>();
        result.put("pending", count("SELECT count(*) FROM review_item WHERE review_status='UNDER_REVIEW'"));
        result.put("assignedToMe", jdbc.sql("SELECT count(*) FROM review_item WHERE review_status='UNDER_REVIEW' AND assigned_reviewer_id=:actor").param("actor", actor).query(Long.class).single());
        result.put("oldestSubmittedAt", jdbc.sql("SELECT min(submitted_at) FROM review_item WHERE review_status='UNDER_REVIEW'").query(OffsetDateTime.class).optional().orElse(null));
        result.put("averageAgeHours", jdbc.sql("SELECT COALESCE(round(extract(epoch from avg(now()-submitted_at))/3600,1),0) FROM review_item WHERE review_status='UNDER_REVIEW'").query(java.math.BigDecimal.class).single());
        result.put("rejectedToday", count("SELECT count(*) FROM review_record WHERE action='REJECTED' AND created_at>=date_trunc('day',now())"));
        result.put("requiresUpdateToday", count("SELECT count(*) FROM review_record WHERE action='REQUIRES_UPDATE' AND created_at>=date_trunc('day',now())"));
        result.put("submittedLast7Days", count("SELECT count(*) FROM review_record WHERE action='SUBMITTED' AND created_at>=now()-interval '7 days'"));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> sourceHealth() {
        var result = new LinkedHashMap<String,Object>();
        result.put("retired", count("SELECT count(*) FROM source_reference WHERE status='RETIRED'"));
        result.put("requiresUpdate", count("SELECT count(*) FROM source_reference WHERE review_status='REQUIRES_UPDATE'"));
        result.put("unused", count("SELECT count(*) FROM source_reference s WHERE NOT EXISTS (SELECT 1 FROM knowledge_fact_source fs WHERE fs.source_reference_id=s.id)"));
        result.put("factsReferencingRetired", count("SELECT count(DISTINCT fv.knowledge_fact_id) FROM knowledge_fact_version fv JOIN knowledge_fact_source fs ON fs.knowledge_fact_version_id=fv.id JOIN source_reference s ON s.id=fs.source_reference_id WHERE s.status='RETIRED'"));
        result.put("factsReferencingOutdated", count("SELECT count(DISTINCT fv.knowledge_fact_id) FROM knowledge_fact_version fv JOIN knowledge_fact_source fs ON fs.knowledge_fact_version_id=fv.id JOIN source_reference s ON s.id=fs.source_reference_id WHERE s.review_status='REQUIRES_UPDATE'"));
        result.put("oldest", jdbc.sql("SELECT id,publisher,title,url,status,review_status AS \"reviewStatus\",accessed_at AS \"accessedAt\" FROM source_reference ORDER BY accessed_at,id LIMIT 25").query().listOfRows());
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String,Object> releaseHealth() {
        var result = new LinkedHashMap<String,Object>();
        result.put("latest", first(jdbc.sql("SELECT id,display_name AS \"displayName\",status,release_number AS \"releaseNumber\",question_count AS \"questionCount\",knowledge_fact_count AS \"factCount\",checksum,published_at AS \"publishedAt\" FROM content_release ORDER BY created_at DESC LIMIT 1").query().listOfRows()));
        result.put("active", first(jdbc.sql("SELECT id,display_name AS \"displayName\",release_number AS \"releaseNumber\",question_count AS \"questionCount\",knowledge_fact_count AS \"factCount\",checksum,activated_at AS \"activatedAt\" FROM content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1").query().listOfRows()));
        result.put("published", count("SELECT count(*) FROM content_release WHERE status IN ('PUBLISHED','DELIVERED','ACTIVE','RETIRED')"));
        result.put("failedDeliveries", count("SELECT count(*) FROM release_delivery_attempt WHERE status='FAILED'"));
        result.put("deliveryRetries", count("SELECT count(*) FROM release_delivery_attempt WHERE attempt_number>1"));
        result.put("validationFailures", count("SELECT count(*) FROM release_validation_run WHERE NOT valid"));
        result.put("recentActivations", jdbc.sql("SELECT h.release_id AS \"releaseId\",h.previous_active_release_id AS \"previousReleaseId\",h.activated_by AS \"activatedBy\",h.activated_at AS \"activatedAt\" FROM release_activation_history h ORDER BY h.activated_at DESC LIMIT 20").query().listOfRows());
        result.put("recentDeliveries", jdbc.sql("SELECT release_id AS \"releaseId\",attempt_number AS \"attemptNumber\",status,started_at AS \"startedAt\",completed_at AS \"completedAt\",error_code AS \"errorCode\" FROM release_delivery_attempt ORDER BY started_at DESC LIMIT 20").query().listOfRows());
        return result;
    }

    private Map<String,Long> counts(String table) {
        return jdbc.sql("SELECT count(*) total,count(*) FILTER(WHERE review_status='APPROVED') approved,count(*) FILTER(WHERE status='DRAFT') draft,count(*) FILTER(WHERE review_status='REJECTED') rejected,count(*) FILTER(WHERE review_status='REQUIRES_UPDATE') requires_update,count(*) FILTER(WHERE status='RETIRED') retired FROM " + table)
                .query((rs,row)->Map.of("total",rs.getLong("total"),"approved",rs.getLong("approved"),"draft",rs.getLong("draft"),"rejected",rs.getLong("rejected"),"requiresUpdate",rs.getLong("requires_update"),"retired",rs.getLong("retired"))).single();
    }
    private Map<String,Long> statusCounts(String table) { return jdbc.sql("SELECT count(*) total,count(*) FILTER(WHERE status='DRAFT') draft,count(*) FILTER(WHERE status='ACTIVE') active,count(*) FILTER(WHERE status='ARCHIVED') archived FROM " + table).query((rs,row)->Map.of("total",rs.getLong("total"),"draft",rs.getLong("draft"),"active",rs.getLong("active"),"archived",rs.getLong("archived"))).single(); }
    private Map<String,Long> sourceCounts() { return jdbc.sql("SELECT count(*) total,count(*) FILTER(WHERE review_status='REVIEWED') approved,count(*) FILTER(WHERE review_status='UNREVIEWED') draft,count(*) FILTER(WHERE review_status='REQUIRES_UPDATE') requires_update,count(*) FILTER(WHERE status='RETIRED') retired FROM source_reference").query((rs,row)->Map.of("total",rs.getLong("total"),"approved",rs.getLong("approved"),"draft",rs.getLong("draft"),"requiresUpdate",rs.getLong("requires_update"),"retired",rs.getLong("retired"))).single(); }
    private long count(String sql) { return jdbc.sql(sql).query(Long.class).single(); }
    private static Map<String,Object> first(List<Map<String,Object>> rows) { return rows.isEmpty() ? null : rows.getFirst(); }
}
