package se.medbo.examplatform.learning.reporting;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearnerHealthService {
    private final JdbcClient jdbc;
    public LearnerHealthService(JdbcClient jdbc){this.jdbc=jdbc;}
    @Transactional(readOnly=true)
    public Map<String,Object> report(){var result=new LinkedHashMap<String,Object>();result.put("practiceSessions",count("SELECT count(*) FROM practice_session"));result.put("mockExams",count("SELECT count(*) FROM mock_exam_attempt"));result.put("activeMockExams",count("SELECT count(*) FROM mock_exam_attempt WHERE status='ACTIVE'"));result.put("completedMockExams",count("SELECT count(*) FROM mock_exam_attempt WHERE status='SUBMITTED'"));result.put("expiredMockExams",count("SELECT count(*) FROM mock_exam_attempt WHERE status='EXPIRED'"));result.put("averageScore",jdbc.sql("SELECT COALESCE(round(avg(percentage),2),0) FROM mock_exam_attempt WHERE status IN ('SUBMITTED','EXPIRED')").query(java.math.BigDecimal.class).single());result.put("passRate",jdbc.sql("SELECT COALESCE(round(100.0*count(*) FILTER(WHERE passed)/NULLIF(count(*),0),2),0) FROM mock_exam_attempt WHERE status IN ('SUBMITTED','EXPIRED')").query(java.math.BigDecimal.class).single());result.put("averageDurationSeconds",jdbc.sql("SELECT COALESCE(round(avg(duration_seconds)),0) FROM mock_exam_attempt WHERE status IN ('SUBMITTED','EXPIRED')").query(java.math.BigDecimal.class).single());return result;}
    private long count(String sql){return jdbc.sql(sql).query(Long.class).single();}
}
