package se.medbo.examplatform.content.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EditorialGroundingAuditService {
  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  EditorialGroundingAuditService(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc=jdbc; this.mapper=mapper; }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  void rejected(UUID factId, String code, UUID proposalId) {
    var authentication=SecurityContextHolder.getContext().getAuthentication();
    String actor=authentication==null?"system":authentication.getName();
    String roles=authentication==null?"":authentication.getAuthorities().stream().map(Object::toString).sorted().reduce((a,b)->a+","+b).orElse("");
    jdbc.sql("INSERT INTO audit_event(id,occurred_at,actor_id,actor_name,actor_role,action,entity_type,entity_id,reason,metadata,request_id) VALUES(:id,:now,:actor,:actor,:roles,'REJECT','KnowledgeFact',:fact,:reason,CAST(:metadata AS jsonb),:request)")
        .param("id",UUID.randomUUID()).param("now",OffsetDateTime.now(ZoneOffset.UTC)).param("actor",actor).param("roles",roles)
        .param("fact",factId).param("reason",code).param("metadata",json(Map.of("validationCode",code,"proposalId",proposalId==null?"":proposalId.toString())))
        .param("request",UUID.randomUUID().toString()).update();
  }

  private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
