package se.medbo.examplatform.learning.shared;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class LearnerIdentityResolver {
    private final JdbcClient jdbc;
    private final boolean developmentHeaderEnabled;

    public LearnerIdentityResolver(JdbcClient jdbc,
            @Value("${learning.identity.development-header-enabled:false}") boolean developmentHeaderEnabled) {
        this.jdbc = jdbc;
        this.developmentHeaderEnabled = developmentHeaderEnabled;
    }

    public UUID resolve(String externalIdentityId) {
        if (!developmentHeaderEnabled) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "No production identity adapter is configured");
        }
        if (externalIdentityId == null || externalIdentityId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "A development learner identity is required");
        }
        return jdbc.sql("SELECT id FROM learner_profile WHERE external_identity_id = :identity")
                .param("identity", externalIdentityId)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEARNER_NOT_FOUND", "Learner not found"));
    }
}

