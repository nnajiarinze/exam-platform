package se.medbo.examplatform.learning.contentprojection;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;

@Service
public class ContentReleaseActivationService {
    private final JdbcClient jdbc;
    public ContentReleaseActivationService(JdbcClient jdbc){this.jdbc=jdbc;}
    @Transactional public ActivationResult activate(String externalReleaseId){
        var target=jdbc.sql("SELECT id,exam_id,status FROM imported_content_release WHERE external_release_id=:id FOR UPDATE").param("id",externalReleaseId).query((rs,n)->new Release(rs.getObject("id",UUID.class),rs.getString("exam_id"),rs.getString("status"))).optional().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"CONTENT_RELEASE_NOT_IMPORTED","Content release has not been imported"));
        if("FAILED".equals(target.status())||"RETIRED".equals(target.status()))throw new ApiException(HttpStatus.CONFLICT,"CONTENT_RELEASE_NOT_ACTIVATABLE","Content release cannot be activated");
        if("ACTIVE".equals(target.status()))return new ActivationResult(target.id(),false,"ACTIVE");
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:examId))").param("examId",target.examId()).query((rs,n)->true).single();
        var previous=jdbc.sql("SELECT id FROM imported_content_release WHERE exam_id=:exam AND status='ACTIVE' FOR UPDATE").param("exam",target.examId()).query(UUID.class).optional();
        jdbc.sql("UPDATE imported_content_release SET status='IMPORTED',version=version+1 WHERE exam_id=:exam AND status='ACTIVE'").param("exam",target.examId()).update();var now=OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.sql("UPDATE imported_content_release SET status='ACTIVE',activated_at=:now,version=version+1 WHERE id=:id").param("now",now).param("id",target.id()).update();
        var params=new java.util.HashMap<String,Object>();params.put("id",UUID.randomUUID());params.put("release",target.id());params.put("previous",previous.orElse(null));params.put("now",now);jdbc.sql("INSERT INTO imported_release_activation_history(id,imported_release_id,previous_active_release_id,activated_at) VALUES(:id,:release,:previous,:now)").params(params).update();return new ActivationResult(target.id(),true,"ACTIVE");
    }
    public record ActivationResult(UUID releaseId,boolean activated,String status){}
    private record Release(UUID id,String examId,String status){}
}
