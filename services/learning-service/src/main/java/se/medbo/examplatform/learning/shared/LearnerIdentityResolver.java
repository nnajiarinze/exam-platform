package se.medbo.examplatform.learning.shared;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication instanceof JwtAuthenticationToken jwt){
            String subject=jwt.getToken().getSubject();
            String email=jwt.getToken().getClaimAsString("email");
            String name=jwt.getToken().getClaimAsString("name");
            Boolean verified=jwt.getToken().getClaim("email_verified");
            jdbc.sql("INSERT INTO learner_profile(id,external_identity_id,email,email_verified,display_name,interface_language,explanation_language,account_status,onboarding_completed,created_at,updated_at) VALUES(:id,:subject,:email,:verified,:name,'sv','sv','ACTIVE',false,now(),now()) ON CONFLICT(external_identity_id) DO NOTHING")
                    .param("id",UUID.randomUUID()).param("subject",subject).param("email",email).param("verified",Boolean.TRUE.equals(verified)).param("name",name).update();
            return active(subject);
        }
        if (!developmentHeaderEnabled) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "No production identity adapter is configured");
        }
        if (externalIdentityId == null || externalIdentityId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
                    "A development learner identity is required");
        }
        return active(externalIdentityId);
    }

    private UUID active(String externalIdentityId){
        var row=jdbc.sql("SELECT id,account_status FROM learner_profile WHERE external_identity_id = :identity")
                .param("identity", externalIdentityId)
                .query((rs,n)->new Profile(rs.getObject("id",UUID.class),rs.getString("account_status")))
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LEARNER_NOT_FOUND", "Learner not found"));
        if(!"ACTIVE".equals(row.status()))throw new ApiException(HttpStatus.FORBIDDEN,"LEARNER_ACCOUNT_DISABLED","Learner account is not active");
        return row.id();
    }
    private record Profile(UUID id,String status){}
}
