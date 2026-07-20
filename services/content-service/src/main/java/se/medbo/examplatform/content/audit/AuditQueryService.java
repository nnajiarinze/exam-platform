package se.medbo.examplatform.content.audit;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    public AuditQueryService(JdbcClient jdbc,ObjectMapper mapper) { this.jdbc = jdbc;this.mapper=mapper; }

    @Transactional(readOnly = true)
    public Map<String,Object> search(String actor, String entityType, String action, String requestId,
                                     OffsetDateTime from, OffsetDateTime to, int page, int size) {
        var predicates = new ArrayList<String>();
        var params = new LinkedHashMap<String,Object>();
        if (actor != null && !actor.isBlank()) { predicates.add("lower(actor_id) LIKE :actor"); params.put("actor", "%" + actor.trim().toLowerCase() + "%"); }
        if (entityType != null && !entityType.isBlank()) { predicates.add("entity_type=:entityType"); params.put("entityType", entityType); }
        if (action != null && !action.isBlank()) { predicates.add("action=:action"); params.put("action", action); }
        if (requestId != null && !requestId.isBlank()) { predicates.add("request_id=:requestId"); params.put("requestId", requestId); }
        if (from != null) { predicates.add("occurred_at>=:from"); params.put("from", from); }
        if (to != null) { predicates.add("occurred_at<=:to"); params.put("to", to); }
        String where = predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
        var countQuery = jdbc.sql("SELECT count(*) FROM audit_event" + where);
        var itemQuery = jdbc.sql("""
                SELECT id,occurred_at AS "timestamp",actor_id AS "actorId",actor_name AS "actorName",
                       actor_role AS "actorRole",action,entity_type AS "entityType",entity_id AS "entityId",
                       entity_version AS "entityVersion",previous_state AS "previousState",new_state AS "newState",
                       reason,metadata,host(ip_address) AS "ipAddress",request_id AS "requestId"
                FROM audit_event %s ORDER BY occurred_at DESC,id DESC LIMIT :size OFFSET :offset
                """.formatted(where));
        for (var entry : params.entrySet()) { countQuery = countQuery.param(entry.getKey(), entry.getValue()); itemQuery = itemQuery.param(entry.getKey(), entry.getValue()); }
        long total = countQuery.query(Long.class).single();
        var items = itemQuery.param("size", size).param("offset", page * size).query((rs,row)->{
            var event=new LinkedHashMap<String,Object>();
            event.put("id",rs.getObject("id"));event.put("timestamp",rs.getObject("timestamp"));
            event.put("actorId",rs.getString("actorId"));event.put("actorName",rs.getString("actorName"));
            event.put("actorRole",rs.getString("actorRole"));event.put("action",rs.getString("action"));
            event.put("entityType",rs.getString("entityType"));event.put("entityId",rs.getObject("entityId"));
            event.put("entityVersion",rs.getObject("entityVersion"));event.put("previousState",json(rs.getString("previousState")));
            event.put("newState",json(rs.getString("newState")));event.put("reason",rs.getString("reason"));
            event.put("metadata",json(rs.getString("metadata")));event.put("ipAddress",rs.getString("ipAddress"));
            event.put("requestId",rs.getString("requestId"));return event;
        }).list();
        return Map.of("items", items, "page", page, "size", size, "totalItems", total,
                "totalPages", total == 0 ? 0 : (total + size - 1) / size);
    }
    private Object json(String value){if(value==null)return null;try{return mapper.readTree(value);}catch(Exception e){return Map.of();}}
}
