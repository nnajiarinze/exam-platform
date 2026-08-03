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

    AuthenticationReadinessService(JdbcClient jdbc, KeycloakIdentityAdminClient keycloak,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${learning.identity.management.google-enabled:false}") boolean googleEnabled,
            @Value("${learning.identity.management.apple-enabled:false}") boolean appleEnabled) {
        this.jdbc=jdbc; this.keycloak=keycloak; this.issuer=issuer.replaceAll("/+$", "");
        this.googleEnabled=googleEnabled; this.appleEnabled=appleEnabled;
    }

    Readiness get() {
        long providerErrors=jdbc.sql("SELECT count(*) FROM identity_management_audit WHERE outcome='FAILED' AND created_at>=now()-interval '24 hours'").query(Long.class).single();
        long linkingConflicts=jdbc.sql("SELECT count(*) FROM identity_management_audit WHERE action='UNLINK_PROVIDER' AND outcome='REJECTED' AND created_at>=now()-interval '24 hours'").query(Long.class).single();
        var configured=emailConfiguration();
        return new Readiness(issuer,"sveastudy://auth/callback",keycloak.ready(),true,new EmailReadiness(true,true,configured.configured(),"Resend",configured.sender(),configured.replyTo(),"tinkona.com",configured.domainStatus(),configured.spfStatus(),configured.dkimStatus(),configured.dmarcStatus(),configured.lastSmtpTestAt(),configured.lastVerificationEmailAt(),configured.lastResetEmailAt(),true),
                List.of(new ProviderReadiness("google",googleEnabled,issuer+"/broker/google/endpoint",null,null),new ProviderReadiness("apple",appleEnabled,issuer+"/broker/apple/endpoint","1.17.0","GENERATED_ON_DEMAND")),providerErrors,linkingConflicts,OffsetDateTime.now());
    }

    private KeycloakIdentityAdminClient.EmailConfiguration emailConfiguration() {
        try {
            return keycloak.emailConfiguration();
        } catch (KeycloakIdentityAdminClient.IdentityAdminException ignored) {
            return KeycloakIdentityAdminClient.EmailConfiguration.unavailable();
        }
    }

    record Readiness(String issuer,String mobileCallback,boolean bffReady,boolean accountDeletionEnabled,EmailReadiness email,List<ProviderReadiness> providers,long providerErrorCount24h,long accountLinkingConflictCount24h,OffsetDateTime checkedAt) {}
    record EmailReadiness(boolean enabled,boolean verificationRequired,boolean smtpConfigured,String provider,String sender,String replyTo,String domain,String domainStatus,String spfStatus,String dkimStatus,String dmarcStatus,String lastSmtpTestAt,String lastVerificationEmailAt,String lastResetEmailAt,boolean passwordResetEnabled) {}
    record ProviderReadiness(String alias,boolean enabled,String callback,String extensionVersion,String clientSecretExpiry) {}
}
