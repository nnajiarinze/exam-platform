package se.medbo.examplatform.learning.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class LearnerIdentityResolverTest {
    @AfterEach void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test void usesStableSubjectAsCanonicalIdentityAndNeverEmailForLookup() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec insert = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec select = mock(JdbcClient.StatementSpec.class);
        @SuppressWarnings("unchecked")
        JdbcClient.MappedQuerySpec<Object> query = (JdbcClient.MappedQuerySpec<Object>) mock(JdbcClient.MappedQuerySpec.class);

        when(jdbc.sql(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.startsWith("INSERT INTO learner_profile")) return insert;
            if (sql.startsWith("SELECT id,account_status FROM learner_profile")) return select;
            throw new IllegalStateException("Unexpected SQL: " + sql);
        });

        AtomicReference<String> insertedSubject = new AtomicReference<>();
        AtomicReference<String> insertedEmail = new AtomicReference<>();
        when(insert.param(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            Object value = invocation.getArgument(1);
            if ("subject".equals(key)) insertedSubject.set((String) value);
            if ("email".equals(key)) insertedEmail.set((String) value);
            return insert;
        });
        when(insert.update()).thenReturn(1);

        AtomicReference<String> lookupIdentity = new AtomicReference<>();
        when(select.param(anyString(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            Object value = invocation.getArgument(1);
            if ("identity".equals(key)) lookupIdentity.set((String) value);
            return select;
        });
        when(select.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec) query);

        UUID existingLearnerId = UUID.randomUUID();
        when(query.optional()).thenReturn(Optional.of(activeProfile(existingLearnerId, "ACTIVE")));

        authenticate(Map.of(
                "sub", "apple-subject-001",
                "email", "a1b2c3d4@privaterelay.appleid.com",
                "email_verified", true,
                "name", "Relay Learner"
        ));

        LearnerIdentityResolver resolver = new LearnerIdentityResolver(jdbc, false);
        UUID resolved = resolver.resolve(null);

        assertThat(resolved).isEqualTo(existingLearnerId);
        assertThat(insertedSubject.get()).isEqualTo("apple-subject-001");
        assertThat(insertedEmail.get()).isEqualTo("a1b2c3d4@privaterelay.appleid.com");
        assertThat(lookupIdentity.get()).isEqualTo("apple-subject-001");
        assertThat(lookupIdentity.get()).isNotEqualTo("a1b2c3d4@privaterelay.appleid.com");
    }

    private static void authenticate(Map<String, Object> claims) {
        Instant now = Instant.parse("2026-08-04T12:00:00Z");
        Jwt jwt = new Jwt("token", now.minusSeconds(60), now.plusSeconds(300), Map.of("alg", "none"), claims);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static Object activeProfile(UUID learnerId, String status) {
        try {
            Class<?> profileType = Class.forName("se.medbo.examplatform.learning.shared.LearnerIdentityResolver$Profile");
            Constructor<?> constructor = profileType.getDeclaredConstructor(UUID.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(learnerId, status);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to instantiate learner profile test fixture", exception);
        }
    }
}
