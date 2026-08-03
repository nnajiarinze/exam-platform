package se.medbo.examplatform.learning.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
final class IdentityAuditService {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    IdentityAuditService(JdbcClient jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    void record(UUID learnerId, String subject, String action, String provider, String outcome, UUID correlationId, Map<String, ?> details) {
        jdbc.sql("INSERT INTO identity_management_audit(id,learner_id,subject_hash,action,provider,outcome,correlation_id,details) VALUES(:id,:learner,:subject,:action,:provider,:outcome,:correlation,CAST(:details AS jsonb))")
                .param("id", UUID.randomUUID()).param("learner", learnerId).param("subject", hash(subject))
                .param("action", action).param("provider", provider).param("outcome", outcome)
                .param("correlation", correlationId).param("details", json(details)).update();
    }

    private String json(Map<String, ?> details) {
        try { return mapper.writeValueAsString(details); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Identity audit details are not serializable", exception); }
    }

    static String hash(String subject) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
}

