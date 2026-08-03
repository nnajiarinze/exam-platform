package se.medbo.examplatform.learning.identity;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.learning.shared.ApiException;

@Component
final class CurrentIdentity {
    private final Clock clock;
    private final long recentAuthenticationSeconds;

    @Autowired
    CurrentIdentity(@Value("${learning.identity.management.recent-authentication-seconds:300}") long recentAuthenticationSeconds) {
        this(Clock.systemUTC(), recentAuthenticationSeconds);
    }

    CurrentIdentity(Clock clock, long recentAuthenticationSeconds) {
        this.clock = clock;
        this.recentAuthenticationSeconds = recentAuthenticationSeconds;
    }

    Claims claims() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Valid learner authentication is required");
        }
        var jwt = token.getToken();
        String subject = jwt.getSubject();
        String sessionId = jwt.getClaimAsString("sid");
        String clientId = jwt.getClaimAsString("azp");
        Instant authenticatedAt = claimInstant(jwt.getClaims().get("auth_time"));
        return new Claims(subject, sessionId, clientId, authenticatedAt);
    }

    Claims requireRecent() {
        Claims claims = claims();
        if (claims.authenticatedAt() == null || claims.authenticatedAt().isBefore(clock.instant().minusSeconds(recentAuthenticationSeconds))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "RECENT_AUTHENTICATION_REQUIRED", "Reauthenticate before changing sign-in security");
        }
        return claims;
    }

    private static Instant claimInstant(Object value) {
        if (value instanceof Number number) return Instant.ofEpochSecond(number.longValue());
        if (value instanceof String text) try { return Instant.ofEpochSecond(Long.parseLong(text)); } catch (NumberFormatException ignored) { return null; }
        return null;
    }

    record Claims(String subject, String sessionId, String clientId, Instant authenticatedAt) {}
}
