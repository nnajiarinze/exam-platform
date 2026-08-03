package se.medbo.examplatform.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import se.medbo.examplatform.learning.shared.ApiException;

class CurrentIdentityTest {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void acceptsRecentAuthenticationAndReturnsOnlyTokenIdentity() {
        authenticate(Map.of("sub", "user-123", "sid", "session-456", "azp", "mobile-app", "auth_time", NOW.minusSeconds(60).getEpochSecond()));
        var claims = new CurrentIdentity(Clock.fixed(NOW, ZoneOffset.UTC), 300).requireRecent();
        assertThat(claims.subject()).isEqualTo("user-123");
        assertThat(claims.sessionId()).isEqualTo("session-456");
        assertThat(claims.clientId()).isEqualTo("mobile-app");
    }

    @Test void rejectsStaleAuthenticationForSensitiveIdentityChanges() {
        authenticate(Map.of("sub", "user-123", "auth_time", NOW.minusSeconds(301).getEpochSecond()));
        assertThatThrownBy(() -> new CurrentIdentity(Clock.fixed(NOW, ZoneOffset.UTC), 300).requireRecent())
                .isInstanceOf(ApiException.class).hasMessageContaining("Reauthenticate");
    }

    @Test void rejectsTokenWithoutAuthenticationTimeForSensitiveIdentityChanges() {
        authenticate(Map.of("sub", "user-123"));
        assertThatThrownBy(() -> new CurrentIdentity(Clock.fixed(NOW, ZoneOffset.UTC), 300).requireRecent())
                .isInstanceOf(ApiException.class);
    }

    private static void authenticate(Map<String, Object> claims) {
        Jwt jwt = new Jwt("token", NOW.minusSeconds(30), NOW.plusSeconds(300), Map.of("alg", "none"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}

