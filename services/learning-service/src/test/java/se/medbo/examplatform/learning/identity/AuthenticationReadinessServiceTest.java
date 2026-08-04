package se.medbo.examplatform.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class AuthenticationReadinessServiceTest {
    private HttpServer server;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/realms/exam-platform/protocol/openid-connect/token", exchange ->
                respond(exchange, 200, "{\"access_token\":\"service-token\",\"expires_in\":300}", "application/json"));
        server.createContext("/admin/realms/exam-platform", exchange -> respond(exchange, 200, """
                {"smtpServer":{"host":"smtp.resend.com","port":"587","starttls":"true","ssl":"false",
                "auth":"true","user":"resend","password":"protected-smtp-secret",
                "from":"no-reply@tinkona.com","replyTo":"support@tinkona.com"},
                "attributes":{"resendDomainStatus":"verified","resendSpfStatus":"verified",
                "resendDkimStatus":"verified","emailDmarcStatus":"present","lastSmtpTestAt":"2026-08-03T10:00:00Z"}}
                """, "application/json"));
        server.createContext("/admin/realms/exam-platform/identity-provider/instances", exchange -> respond(exchange, 200, """
                [{"alias":"google","enabled":true,"config":{"clientId":"configured-google-client","clientSecret":"configured-google-secret"}}]
                """, "application/json"));
        server.createContext("/authentication/flows", exchange -> respond(exchange, 200, "[{\"id\":\"flow-1\",\"alias\":\"first broker login\"}]", "application/json"));
        server.createContext("/authentication/flows/flow-1/executions", exchange -> respond(exchange, 200, "[]", "application/json"));
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void reportsOperationalWhenGoogleRedirectIsReached() {
        server.createContext("/realms/exam-platform/protocol/openid-connect/auth", exchange ->
                redirect(exchange, "https://accounts.google.com/o/oauth2/v2/auth?client_id=test-client&redirect_uri=" +
                        urlEncode(issuer() + "/broker/google/endpoint")));

        var service = service(true);
        var readiness = service.get();
        var google = readiness.providers().stream().filter(provider -> provider.alias().equals("google")).findFirst().orElseThrow();

        assertThat(google.configurationState()).isEqualTo("CONFIGURED");
        assertThat(google.operationalState()).isEqualTo("OPERATIONAL");
        assertThat(google.failureClassification()).isEqualTo("GOOGLE_REDIRECT_REACHED");
    }

    @Test void reportsDegradedWhenGoogleRedirectIsNotReached() {
        server.createContext("/realms/exam-platform/protocol/openid-connect/auth", exchange ->
                respond(exchange, 200, "<html><title>Logga in till Svea Study</title></html>", "text/html"));

        var service = service(true);
        var readiness = service.get();
        var google = readiness.providers().stream().filter(provider -> provider.alias().equals("google")).findFirst().orElseThrow();

        assertThat(google.configurationState()).isEqualTo("CONFIGURED");
        assertThat(google.operationalState()).isEqualTo("DEGRADED");
        assertThat(google.failureClassification()).isEqualTo("BROKER_RETURNED_INTERACTIVE_LOGIN");
    }

    private AuthenticationReadinessService service(boolean googleEnabled) {
        var jdbc = mock(JdbcClient.class);
        var firstStatement = mock(JdbcClient.StatementSpec.class);
        var secondStatement = mock(JdbcClient.StatementSpec.class);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> firstQuery = (JdbcClient.MappedQuerySpec<Long>) mock(JdbcClient.MappedQuerySpec.class);
        @SuppressWarnings("unchecked") JdbcClient.MappedQuerySpec<Long> secondQuery = (JdbcClient.MappedQuerySpec<Long>) mock(JdbcClient.MappedQuerySpec.class);
        when(jdbc.sql("SELECT count(*) FROM identity_management_audit WHERE outcome='FAILED' AND created_at>=now()-interval '24 hours'"))
                .thenReturn(firstStatement);
        when(jdbc.sql("SELECT count(*) FROM identity_management_audit WHERE action='UNLINK_PROVIDER' AND outcome='REJECTED' AND created_at>=now()-interval '24 hours'"))
                .thenReturn(secondStatement);
        when(firstStatement.query(Long.class)).thenReturn(firstQuery);
        when(secondStatement.query(Long.class)).thenReturn(secondQuery);
        when(firstQuery.single()).thenReturn(0L);
        when(secondQuery.single()).thenReturn(0L);

        var keycloak = new KeycloakIdentityAdminClient(new ObjectMapper(), "http://127.0.0.1:" + server.getAddress().getPort(),
                "exam-platform", "identity-management-bff", "protected-secret", true);
        var probe = new GoogleBrokerOperationalProbe();
        return new AuthenticationReadinessService(jdbc, keycloak, issuer(), probe, googleEnabled, false);
    }

    private String issuer() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform";
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
