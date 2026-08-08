package se.medbo.examplatform.learning.identity;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.learning.shared.ApiException;

@Service
class IdentityManagementService {
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("google", "apple");
    private final JdbcClient jdbc;
    private final KeycloakIdentityAdminClient keycloak;
    private final IdentityAuditService audit;
    private final CurrentIdentity currentIdentity;
    private final Clock clock;
    private final boolean googleEnabled;
    private final boolean appleEnabled;

    @Autowired
    IdentityManagementService(JdbcClient jdbc, KeycloakIdentityAdminClient keycloak, IdentityAuditService audit,
            CurrentIdentity currentIdentity,
            @Value("${learning.identity.management.google-enabled:false}") boolean googleEnabled,
            @Value("${learning.identity.management.apple-enabled:false}") boolean appleEnabled) {
        this(jdbc, keycloak, audit, currentIdentity, Clock.systemUTC(), googleEnabled, appleEnabled);
    }

    IdentityManagementService(JdbcClient jdbc, KeycloakIdentityAdminClient keycloak, IdentityAuditService audit,
            CurrentIdentity currentIdentity, Clock clock, boolean googleEnabled, boolean appleEnabled) {
        this.jdbc = jdbc; this.keycloak = keycloak; this.audit = audit; this.currentIdentity = currentIdentity;
        this.clock = clock; this.googleEnabled = googleEnabled; this.appleEnabled = appleEnabled;
    }

    LinkedMethods methods(UUID learnerId) {
        var claims = currentIdentity.claims();
        var methods = admin(() -> keycloak.methods(claims.subject()));
        var values = new java.util.ArrayList<LoginMethod>();
        values.add(new LoginMethod("password", "Email and password", methods.password(), true));
        values.add(new LoginMethod("google", "Google", methods.providers().contains("google"), googleEnabled));
        values.add(new LoginMethod("apple", "Apple", methods.providers().contains("apple"), appleEnabled));
        return new LinkedMethods(keycloak.ready(), List.copyOf(values), usableCount(methods));
    }

    LinkInitiation initiateLink(UUID learnerId, String provider) {
        requireProvider(provider);
        var claims = currentIdentity.claims();
        requireProviderEnabled(provider);
        UUID correlation = UUID.randomUUID();
        audit.record(learnerId, claims.subject(), "LINK_PROVIDER", provider, "INITIATED", correlation,
                Map.of("protocol", "KEYCLOAK_AIA"));
        return new LinkInitiation(provider, "idp_link:" + provider, correlation);
    }

    LinkedMethods unlink(UUID learnerId, String provider) {
        requireProvider(provider);
        var claims = currentIdentity.requireRecent();
        UUID correlation = UUID.randomUUID();
        var methods = admin(() -> keycloak.methods(claims.subject()));
        if (!methods.providers().contains(provider)) {
            audit.record(learnerId, claims.subject(), "UNLINK_PROVIDER", provider, "REJECTED", correlation, Map.of("reason", "NOT_LINKED"));
            throw new ApiException(HttpStatus.CONFLICT, "LOGIN_METHOD_NOT_LINKED", "The login method is not linked");
        }
        if (usableCount(methods) <= 1) {
            audit.record(learnerId, claims.subject(), "UNLINK_PROVIDER", provider, "REJECTED", correlation, Map.of("reason", "FINAL_LOGIN_METHOD"));
            throw new ApiException(HttpStatus.CONFLICT, "FINAL_LOGIN_METHOD_REQUIRED", "Add another login method before removing this one");
        }
        admin(() -> { keycloak.unlink(claims.subject(), provider); return null; });
        audit.record(learnerId, claims.subject(), "UNLINK_PROVIDER", provider, "SUCCEEDED", correlation, Map.of());
        return methods(learnerId);
    }

    void logoutCurrent(UUID learnerId) {
        var claims = currentIdentity.claims();
        if (claims.sessionId() == null || claims.sessionId().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "SESSION_IDENTIFIER_MISSING", "The current session cannot be revoked individually");
        }
        UUID correlation = UUID.randomUUID();
        admin(() -> { keycloak.logoutSession(claims.sessionId()); return null; });
        audit.record(learnerId, claims.subject(), "LOGOUT_CURRENT", null, "SUCCEEDED", correlation, Map.of());
    }

    void logoutAll(UUID learnerId) {
        var claims = currentIdentity.requireRecent();
        UUID correlation = UUID.randomUUID();
        admin(() -> { keycloak.logoutAll(claims.subject()); return null; });
        audit.record(learnerId, claims.subject(), "LOGOUT_ALL", null, "SUCCEEDED", correlation, Map.of());
    }

    @Transactional
    DeletionStatus beginDeletion(UUID learnerId) {
        var claims = currentIdentity.requireRecent();
        UUID id = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.sql("DELETE FROM identity_deletion_request WHERE learner_id=:learner AND status IN ('PENDING_CONFIRMATION','FAILED')")
                .param("learner", learnerId).update();
        jdbc.sql("INSERT INTO identity_deletion_request(id,learner_id,status,correlation_id,requested_at,expires_at) VALUES(:id,:learner,'PENDING_CONFIRMATION',:correlation,:now,:expires)")
                .param("id", id).param("learner", learnerId).param("correlation", correlation).param("now", now)
                .param("expires", now.plusMinutes(15)).update();
        audit.record(learnerId, claims.subject(), "DELETE_ACCOUNT", null, "INITIATED", correlation,
                Map.of("retentionPolicy", "IDENTITY_DELETED_PROFILE_ANONYMISED_HISTORY_PSEUDONYMOUS"));
        return deletionStatus(learnerId);
    }

    DeletionStatus deletionStatus(UUID learnerId) { return deletionStatusInternal(learnerId); }

    DeletionStatus confirmDeletion(UUID learnerId, UUID requestId, String confirmation) {
        var claims = currentIdentity.requireRecent();
        if (!"DELETE".equals(confirmation)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "DELETION_CONFIRMATION_INVALID", "Type DELETE to confirm account deletion");
        var request = jdbc.sql("SELECT id,status,expires_at,correlation_id FROM identity_deletion_request WHERE id=:id AND learner_id=:learner")
                .param("id", requestId).param("learner", learnerId)
                .query((rs, n) -> new DeletionRequest(rs.getObject("id", UUID.class), rs.getString("status"), rs.getObject("expires_at", OffsetDateTime.class), rs.getObject("correlation_id", UUID.class)))
                .optional().orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DELETION_REQUEST_NOT_FOUND", "The deletion request does not exist"));
        if (!"PENDING_CONFIRMATION".equals(request.status())) throw new ApiException(HttpStatus.CONFLICT, "DELETION_REQUEST_NOT_PENDING", "The deletion request cannot be confirmed");
        if (request.expiresAt().toInstant().isBefore(clock.instant())) throw new ApiException(HttpStatus.CONFLICT, "DELETION_REQUEST_EXPIRED", "Start account deletion again");
        int claimed = jdbc.sql("UPDATE identity_deletion_request SET status='PROCESSING',processing_started_at=now(),failure_code=NULL WHERE id=:id AND status='PENDING_CONFIRMATION'")
                .param("id", requestId).update();
        if (claimed != 1) throw new ApiException(HttpStatus.CONFLICT, "DELETION_REQUEST_NOT_PENDING", "The deletion request cannot be confirmed");
        try {
            keycloak.deleteUser(claims.subject());
            anonymiseLearner(learnerId);
            jdbc.sql("UPDATE identity_deletion_request SET status='COMPLETED',completed_at=now() WHERE id=:id")
                    .param("id", requestId).update();
            audit.record(learnerId, claims.subject(), "DELETE_ACCOUNT", null, "SUCCEEDED", request.correlationId(), Map.of());
            return deletionStatusInternal(learnerId);
        } catch (KeycloakIdentityAdminClient.IdentityAdminException exception) {
            jdbc.sql("UPDATE identity_deletion_request SET status='FAILED',failure_code=:code WHERE id=:id")
                    .param("code", exception.code()).param("id", requestId).update();
            audit.record(learnerId, claims.subject(), "DELETE_ACCOUNT", null, "FAILED", request.correlationId(), Map.of("code", exception.code()));
            throw unavailable(exception.code());
        }
    }

    Readiness readiness() {
        return new Readiness(keycloak.ready(), googleEnabled, appleEnabled, "KEYCLOAK_AIA", true, true);
    }

    @Scheduled(fixedDelayString = "${learning.identity.management.deletion-recovery-ms:60000}")
    void recoverInterruptedDeletions() {
        if (!keycloak.ready()) return;
        var recoverable = jdbc.sql("SELECT d.id,d.learner_id,d.correlation_id,p.external_identity_id FROM identity_deletion_request d JOIN learner_profile p ON p.id=d.learner_id WHERE (d.status='PROCESSING' AND d.processing_started_at < now()-interval '60 seconds') OR d.status='FAILED' ORDER BY d.requested_at LIMIT 10")
                .query((rs, n) -> new RecoverableDeletion(rs.getObject("id", UUID.class), rs.getObject("learner_id", UUID.class),
                        rs.getObject("correlation_id", UUID.class), rs.getString("external_identity_id"))).list();
        for (RecoverableDeletion deletion : recoverable) {
            int claimed = jdbc.sql("UPDATE identity_deletion_request SET status='PROCESSING',processing_started_at=now(),failure_code=NULL WHERE id=:id AND status IN ('PROCESSING','FAILED')")
                    .param("id", deletion.id()).update();
            if (claimed != 1) continue;
            try {
                keycloak.deleteUser(deletion.subject());
                anonymiseLearner(deletion.learnerId());
                jdbc.sql("UPDATE identity_deletion_request SET status='COMPLETED',completed_at=COALESCE(completed_at,now()) WHERE id=:id")
                        .param("id", deletion.id()).update();
                audit.record(deletion.learnerId(), deletion.subject(), "DELETE_ACCOUNT_RECOVERY", null, "SUCCEEDED", deletion.correlationId(), Map.of());
            } catch (KeycloakIdentityAdminClient.IdentityAdminException exception) {
                jdbc.sql("UPDATE identity_deletion_request SET status='FAILED',failure_code=:code WHERE id=:id")
                        .param("code", exception.code()).param("id", deletion.id()).update();
            }
        }
    }

    private DeletionStatus deletionStatusInternal(UUID learnerId) {
        return jdbc.sql("SELECT id,status,requested_at,expires_at,completed_at,failure_code FROM identity_deletion_request WHERE learner_id=:learner")
                .param("learner", learnerId).query((rs, n) -> new DeletionStatus(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getObject("requested_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class), rs.getString("failure_code")))
                .optional().orElse(new DeletionStatus(null, "NOT_REQUESTED", null, null, null, null));
    }

    private void anonymiseLearner(UUID learnerId) {
        jdbc.sql("UPDATE learner_profile SET external_identity_id=:anonymous,email=NULL,display_name='Deleted learner',email_verified=false,account_status='DELETED',onboarding_completed=false,deleted_at=COALESCE(deleted_at,now()),updated_at=now() WHERE id=:learner")
                .param("anonymous", "deleted:" + learnerId).param("learner", learnerId).update();
    }

    private void requireProvider(String provider) {
        if (!SUPPORTED_PROVIDERS.contains(provider)) throw new ApiException(HttpStatus.NOT_FOUND, "IDENTITY_PROVIDER_NOT_SUPPORTED", "The identity provider is not supported");
    }

    private void requireProviderEnabled(String provider) {
        if (("google".equals(provider) && !googleEnabled) || ("apple".equals(provider) && !appleEnabled))
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_PROVIDER_NOT_CONFIGURED", "The identity provider is not configured");
    }

    private int usableCount(KeycloakIdentityAdminClient.Methods methods) {
        return countUsableMethods(methods);
    }

    static int countUsableMethods(KeycloakIdentityAdminClient.Methods methods) {
        Set<String> unique = new LinkedHashSet<>(methods.providers());
        return unique.size() + (methods.password() ? 1 : 0);
    }

    private <T> T admin(AdminCall<T> call) {
        try { return call.invoke(); }
        catch (KeycloakIdentityAdminClient.IdentityAdminException exception) { throw unavailable(exception.code()); }
    }

    private static ApiException unavailable(String code) { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, "Identity management is temporarily unavailable"); }

    @FunctionalInterface private interface AdminCall<T> { T invoke(); }
    private record DeletionRequest(UUID id, String status, OffsetDateTime expiresAt, UUID correlationId) {}
    private record RecoverableDeletion(UUID id, UUID learnerId, UUID correlationId, String subject) {}
    record LoginMethod(String id, String displayName, boolean linked, boolean available) {}
    record LinkedMethods(boolean ready, List<LoginMethod> methods, int usableMethodCount) {}
    record LinkInitiation(String provider, String keycloakAction, UUID correlationId) {}
    record DeletionStatus(UUID requestId, String status, OffsetDateTime requestedAt, OffsetDateTime expiresAt, OffsetDateTime completedAt, String failureCode) {}
    record Readiness(boolean bffReady, boolean googleEnabled, boolean appleEnabled, String linkingProtocol, boolean finalMethodProtection, boolean deletionEnabled) {}
}
