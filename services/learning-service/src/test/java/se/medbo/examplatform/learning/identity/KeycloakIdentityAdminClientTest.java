package se.medbo.examplatform.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeycloakIdentityAdminClientTest {
    private HttpServer server;
    private final AtomicInteger tokenRequests = new AtomicInteger();

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/exam-platform/protocol/openid-connect/token", exchange -> {
            tokenRequests.incrementAndGet(); respond(exchange, 200, "{\"access_token\":\"service-token\",\"expires_in\":300}");
        });
        server.createContext("/admin/realms/exam-platform/users/user-123/federated-identity", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer service-token");
            respond(exchange, 200, "[{\"identityProvider\":\"google\"}]");
        });
        server.createContext("/admin/realms/exam-platform/users/user-123/credentials", exchange -> respond(exchange, 200, "[{\"type\":\"password\"}]"));
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void listsOnlyLoginMethodTypesAndCachesServiceToken() {
        var client = client(true, "protected-secret");
        var first = client.methods("user-123");
        var second = client.methods("user-123");
        assertThat(first.password()).isTrue();
        assertThat(first.providers()).containsExactly("google");
        assertThat(second).isEqualTo(first);
        assertThat(tokenRequests).hasValue(1);
    }

    @Test void failsClosedWithoutProtectedClientSecret() {
        var client = client(true, "");
        assertThat(client.ready()).isFalse();
        assertThatThrownBy(() -> client.methods("user-123"))
                .isInstanceOf(KeycloakIdentityAdminClient.IdentityAdminException.class)
                .hasMessage("IDENTITY_MANAGEMENT_NOT_CONFIGURED");
    }

    private KeycloakIdentityAdminClient client(boolean enabled, String secret) {
        return new KeycloakIdentityAdminClient(new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(),
                "exam-platform", "identity-management-bff", secret, enabled);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}

