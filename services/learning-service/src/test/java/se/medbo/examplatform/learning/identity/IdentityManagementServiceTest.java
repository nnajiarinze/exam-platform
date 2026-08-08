package se.medbo.examplatform.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import se.medbo.examplatform.learning.shared.ApiException;

class IdentityManagementServiceTest {
    @Test void countsPasswordAndDistinctProviderLinksAsUsableLoginMethods() {
        assertThat(IdentityManagementService.countUsableMethods(new KeycloakIdentityAdminClient.Methods(true, List.of("google", "google", "apple"))))
                .isEqualTo(3);
    }

    @Test void providerOnlyAccountHasExactlyOneUsableMethod() {
        assertThat(IdentityManagementService.countUsableMethods(new KeycloakIdentityAdminClient.Methods(false, List.of("apple"))))
                .isEqualTo(1);
    }

    @Test void initiateLinkUsesAuthenticatedCanonicalSubjectWithoutRequiringRecentAuthentication() {
        var keycloak = mock(KeycloakIdentityAdminClient.class);
        var audit = mock(IdentityAuditService.class);
        var identity = mock(CurrentIdentity.class);
        var subject = "apple-subject-123";
        var learnerId = UUID.randomUUID();
        when(identity.claims()).thenReturn(new CurrentIdentity.Claims(subject, "sid-1", "mobile", java.time.Instant.EPOCH));

        var service = service(keycloak, audit, identity, true, true);
        var initiation = service.initiateLink(learnerId, "apple");

        assertThat(initiation.provider()).isEqualTo("apple");
        assertThat(initiation.keycloakAction()).isEqualTo("idp_link:apple");
        verify(identity).claims();
        verify(identity, never()).requireRecent();
        verify(audit).record(eq(learnerId), eq(subject), eq("LINK_PROVIDER"), eq("apple"), eq("INITIATED"), any(UUID.class), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
        verify(keycloak, never()).unlink(any(), any());
    }

    @Test void unlinkRejectsFinalLoginMethodAndPreservesCurrentIdentitySubject() {
        var keycloak = mock(KeycloakIdentityAdminClient.class);
        var audit = mock(IdentityAuditService.class);
        var identity = mock(CurrentIdentity.class);
        var learnerId = UUID.randomUUID();
        var claims = new CurrentIdentity.Claims("stable-keycloak-subject", "sid-1", "mobile", java.time.Instant.now());
        when(identity.requireRecent()).thenReturn(claims);
        when(keycloak.methods("stable-keycloak-subject")).thenReturn(new KeycloakIdentityAdminClient.Methods(false, List.of("apple")));

        var service = service(keycloak, audit, identity, true, true);

        assertThatThrownBy(() -> service.unlink(learnerId, "apple"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var api = (ApiException) error;
                    assertThat(api.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.code()).isEqualTo("FINAL_LOGIN_METHOD_REQUIRED");
                });

        verify(keycloak).methods("stable-keycloak-subject");
        verify(keycloak, never()).unlink(any(), any());
        verify(audit).record(eq(learnerId), eq("stable-keycloak-subject"), eq("UNLINK_PROVIDER"), eq("apple"), eq("REJECTED"), any(UUID.class), org.mockito.ArgumentMatchers.<Map<String, ?>>any());
    }

    @Test void methodsPreserveEmailPasswordAndGoogleRegressionSafety() {
        var keycloak = mock(KeycloakIdentityAdminClient.class);
        var audit = mock(IdentityAuditService.class);
        var identity = mock(CurrentIdentity.class);
        when(identity.claims()).thenReturn(new CurrentIdentity.Claims("stable-subject", "sid-1", "mobile", java.time.Instant.now()));
        when(keycloak.methods("stable-subject")).thenReturn(new KeycloakIdentityAdminClient.Methods(true, List.of("google")));
        when(keycloak.ready()).thenReturn(true);

        var service = service(keycloak, audit, identity, true, true);
        var methods = service.methods(UUID.randomUUID());

        assertThat(methods.methods()).extracting(IdentityManagementService.LoginMethod::id)
                .containsExactly("password", "google", "apple");
        assertThat(methods.methods().get(0).linked()).isTrue();
        assertThat(methods.methods().get(1).linked()).isTrue();
        assertThat(methods.methods().get(1).available()).isTrue();
        assertThat(methods.usableMethodCount()).isEqualTo(2);
    }

    @Test void googleLinkingBehaviorUnchangedWhenDisabled() {
        var keycloak = mock(KeycloakIdentityAdminClient.class);
        var audit = mock(IdentityAuditService.class);
        var identity = mock(CurrentIdentity.class);
        when(identity.claims()).thenReturn(new CurrentIdentity.Claims("stable-subject", "sid-1", "mobile", java.time.Instant.EPOCH));

        var service = service(keycloak, audit, identity, false, true);

        assertThatThrownBy(() -> service.initiateLink(UUID.randomUUID(), "google"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var api = (ApiException) error;
                    assertThat(api.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(api.code()).isEqualTo("IDENTITY_PROVIDER_NOT_CONFIGURED");
                });
    }

    @Test void staleSessionMayInitiateLinkButCannotUnlink() {
        var keycloak = mock(KeycloakIdentityAdminClient.class);
        var audit = mock(IdentityAuditService.class);
        var identity = mock(CurrentIdentity.class);
        var learnerId = UUID.randomUUID();
        when(identity.claims()).thenReturn(new CurrentIdentity.Claims("stable-subject", "sid-1", "mobile", java.time.Instant.EPOCH));
        when(identity.requireRecent()).thenThrow(new ApiException(HttpStatus.FORBIDDEN,
                "RECENT_AUTHENTICATION_REQUIRED", "Recent authentication is required"));

        var service = service(keycloak, audit, identity, true, true);

        assertThat(service.initiateLink(learnerId, "google").keycloakAction()).isEqualTo("idp_link:google");
        assertThatThrownBy(() -> service.unlink(learnerId, "google"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo("RECENT_AUTHENTICATION_REQUIRED"));
    }

    private static IdentityManagementService service(KeycloakIdentityAdminClient keycloak,
            IdentityAuditService audit,
            CurrentIdentity identity,
            boolean googleEnabled,
            boolean appleEnabled) {
        return new IdentityManagementService(mock(JdbcClient.class), keycloak, audit, identity,
                Clock.systemUTC(), googleEnabled, appleEnabled);
    }
}
