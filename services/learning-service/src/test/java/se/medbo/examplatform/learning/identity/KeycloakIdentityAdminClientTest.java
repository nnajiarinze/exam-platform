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
        server.createContext("/admin/realms/exam-platform", exchange -> respond(exchange, 200, """
                {"smtpServer":{"host":"smtp.resend.com","port":"587","starttls":"true","ssl":"false",
                "auth":"true","user":"resend","password":"protected-smtp-secret",
                "from":"no-reply@tinkona.com","replyTo":"support@tinkona.com"},
                "attributes":{"resendDomainStatus":"verified","resendSpfStatus":"verified",
                "resendDkimStatus":"verified","emailDmarcStatus":"present","lastSmtpTestAt":"2026-08-03T10:00:00Z"}}
                """));
        server.createContext("/admin/realms/exam-platform/identity-provider/instances", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/google")) {
                respond(exchange, 200, """
                    {"alias":"google","enabled":true,"config":{"clientId":"configured-google-client","clientSecret":"configured-google-secret"}}
                    """);
            } else {
                respond(exchange, 404, "{\"error\":\"not found\"}");
            }
        });
        server.createContext("/admin/realms/exam-platform/authentication/flows", exchange -> respond(exchange, 200, """
            [{"id":"flow-1","alias":"first broker login"}]
            """));
        server.createContext("/admin/realms/exam-platform/authentication/flows/first broker login/executions", exchange -> respond(exchange, 200, "[]"));
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

    @Test void derivesRedactedResendReadinessWithoutReturningThePassword() {
        var configured = client(true, "protected-secret").emailConfiguration();

        assertThat(configured.configured()).isTrue();
        assertThat(configured.sender()).isEqualTo("no-reply@tinkona.com");
        assertThat(configured.replyTo()).isEqualTo("support@tinkona.com");
        assertThat(configured.domainStatus()).isEqualTo("verified");
        assertThat(configured.spfStatus()).isEqualTo("verified");
        assertThat(configured.dkimStatus()).isEqualTo("verified");
        assertThat(configured.dmarcStatus()).isEqualTo("present");
        assertThat(configured.toString()).doesNotContain("protected-smtp-secret");
    }

    @Test void returnsProviderStatusWithSafeConfigurationFlags() {
        var status = client(true, "protected-secret").providerStatus("google");

        assertThat(status.present()).isTrue();
        assertThat(status.enabled()).isTrue();
        assertThat(status.clientIdConfigured()).isTrue();
        assertThat(status.clientSecretConfigured()).isTrue();
        assertThat(status.unsafeAutoLinkPresent()).isFalse();
    }

    @Test void flagsUnsafeAutoLinkWhenFirstBrokerFlowContainsIdpAutoLink() {
        server.removeContext("/admin/realms/exam-platform/authentication/flows/first broker login/executions");
        server.createContext("/admin/realms/exam-platform/authentication/flows/first broker login/executions", exchange -> respond(exchange, 200,
                "[{\"providerId\":\"idp-auto-link\"}]"));

        var status = client(true, "protected-secret").providerStatus("google");

        assertThat(status.unsafeAutoLinkPresent()).isTrue();
    }

    @Test void mapsAppleProviderStatusWithoutUsingEmailAsIdentitySignal() {
        server.removeContext("/admin/realms/exam-platform/identity-provider/instances");
        server.createContext("/admin/realms/exam-platform/identity-provider/instances", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/apple")) {
                respond(exchange, 200, """
                    {"alias":"apple","enabled":true,"config":{"clientId":"apple-services-id","clientSecret":"apple-private-key"}}
                    """);
            } else {
                respond(exchange, 404, "{\"error\":\"not found\"}");
            }
        });

        var status = client(true, "protected-secret").providerStatus("apple");

        assertThat(status.alias()).isEqualTo("apple");
        assertThat(status.present()).isTrue();
        assertThat(status.enabled()).isTrue();
        assertThat(status.clientIdConfigured()).isTrue();
        assertThat(status.clientSecretConfigured()).isTrue();
        assertThat(status.unsafeAutoLinkPresent()).isFalse();
    }

    @Test void treatsMissingProviderAsNotPresentWhenPerAliasEndpointReturns404() {
        var status = client(true, "protected-secret").providerStatus("apple");

        assertThat(status.alias()).isEqualTo("apple");
        assertThat(status.present()).isFalse();
        assertThat(status.enabled()).isFalse();
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
