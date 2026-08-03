package se.medbo.examplatform.learning.identity;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
final class AuthenticationReadinessService {
    private final JdbcClient jdbc;
    private final KeycloakIdentityAdminClient keycloak;
    private final String issuer;
    private final boolean googleEnabled;
    private final boolean appleEnabled;
    private final boolean smtpConfigured;

    AuthenticationReadinessService(JdbcClient jdbc, KeycloakIdentityAdminClient keycloak,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${learning.identity.management.google-enabled:false}") boolean googleEnabled,
            @Value("${learning.identity.management.apple-enabled:false}") boolean appleEnabled,
            @Value("${learning.identity.management.smtp-configured:false}") boolean smtpConfigured) {
        this.jdbc=jdbc; this.keycloak=keycloak; this.issuer=issuer.replaceAll("/+$", "");
        this.googleEnabled=googleEnabled; this.appleEnabled=appleEnabled; this.smtpConfigured=smtpConfigured;
    }

    Readiness get() {
        long providerErrors=jdbc.sql("SELECT count(*) FROM identity_management_audit WHERE outcome='FAILED' AND created_at>=now()-interval '24 hours'").query(Long.class).single();
        long linkingConflicts=jdbc.sql("SELECT count(*) FROM identity_management_audit WHERE action='UNLINK_PROVIDER' AND outcome='REJECTED' AND created_at>=now()-interval '24 hours'").query(Long.class).single();
        return new Readiness(issuer,"sveastudy://auth/callback",keycloak.ready(),true,new EmailReadiness(true,true,smtpConfigured),
                List.of(new ProviderReadiness("google",googleEnabled,issuer+"/broker/google/endpoint",null,null),new ProviderReadiness("apple",appleEnabled,issuer+"/broker/apple/endpoint","1.17.0","GENERATED_ON_DEMAND")),providerErrors,linkingConflicts,OffsetDateTime.now());
    }

    record Readiness(String issuer,String mobileCallback,boolean bffReady,boolean accountDeletionEnabled,EmailReadiness email,List<ProviderReadiness> providers,long providerErrorCount24h,long accountLinkingConflictCount24h,OffsetDateTime checkedAt) {}
    record EmailReadiness(boolean enabled,boolean verificationRequired,boolean smtpConfigured) {}
    record ProviderReadiness(String alias,boolean enabled,String callback,String extensionVersion,String clientSecretExpiry) {}
}
