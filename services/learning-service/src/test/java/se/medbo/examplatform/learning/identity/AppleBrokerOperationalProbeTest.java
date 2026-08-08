package se.medbo.examplatform.learning.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleBrokerOperationalProbeTest {
    private HttpServer server;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void marksOperationalWhenAuthorizationFlowRedirectsToApple() {
        var callback = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform/broker/apple/endpoint";
        server.createContext("/realms/exam-platform/protocol/openid-connect/auth", exchange ->
            redirect(exchange, "https://appleid.apple.com/auth/authorize?client_id=test-services-id&redirect_uri=" + encode(callback)));

        var probe = new AppleBrokerOperationalProbe();
        var issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform";

        var result = probe.check(issuer, callback);

        assertThat(result.operational()).isTrue();
        assertThat(result.classification()).isEqualTo("APPLE_REDIRECT_REACHED");
        assertThat(result.redirectHost()).isEqualTo("appleid.apple.com");
        assertThat(result.callbackMatches()).isTrue();
        assertThat(result.clientIdFingerprint()).isNotBlank();
    }

    @Test void marksDegradedWhenBrokerReturnsBadRequest() {
        server.createContext("/realms/exam-platform/protocol/openid-connect/auth", exchange ->
                respond(exchange, 400, "{\"error\":\"invalid_request\"}", "application/json"));

        var probe = new AppleBrokerOperationalProbe();
        var issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform";

        var result = probe.check(issuer, issuer + "/broker/apple/endpoint");

        assertThat(result.operational()).isFalse();
        assertThat(result.classification()).isEqualTo("BROKER_SESSION_FAILED");
    }

    @Test void marksDegradedWhenInteractiveLoginPageIsReturned() {
        server.createContext("/realms/exam-platform/protocol/openid-connect/auth", exchange ->
                respond(exchange, 200, "<html><title>Logga in till Medbo</title></html>", "text/html"));

        var probe = new AppleBrokerOperationalProbe();
        var issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform";

        var result = probe.check(issuer, issuer + "/broker/apple/endpoint");

        assertThat(result.operational()).isFalse();
        assertThat(result.classification()).isEqualTo("BROKER_RETURNED_INTERACTIVE_LOGIN");
    }

    @Test void marksCallbackMismatchWhenRedirectUriDiffersFromExpectedCallback() {
        var callback = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform/broker/apple/endpoint";
        server.createContext("/realms/exam-platform/protocol/openid-connect/auth", exchange ->
            redirect(exchange, "https://appleid.apple.com/auth/authorize?client_id=test-services-id&redirect_uri=" + encode("https://wrong.example.com/callback")));

        var probe = new AppleBrokerOperationalProbe();
        var issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform";

        var result = probe.check(issuer, callback);

        assertThat(result.operational()).isFalse();
        assertThat(result.classification()).isEqualTo("CALLBACK_MISMATCH");
        assertThat(result.callbackMatches()).isFalse();
    }

    @Test void marksNetworkErrorWhenServerIsUnreachable() {
        int port = server.getAddress().getPort();
        server.stop(0);
        var probe = new AppleBrokerOperationalProbe();
        var issuer = "http://127.0.0.1:" + port + "/realms/exam-platform";

        var result = probe.check(issuer, issuer + "/broker/apple/endpoint");

        assertThat(result.operational()).isFalse();
        assertThat(result.classification()).isIn("NETWORK_ERROR", "APPLE_REDIRECT_NOT_REACHED");
    }

    @Test void cachesResultBrieflyToAvoidRepeatedAppleTraffic() {
        var callback = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform/broker/apple/endpoint";
        var hitCount = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/realms/exam-platform/protocol/openid-connect/auth", exchange -> {
            hitCount.incrementAndGet();
            redirect(exchange, "https://appleid.apple.com/auth/authorize?client_id=test-services-id&redirect_uri=" + encode(callback));
        });

        var probe = new AppleBrokerOperationalProbe();
        var issuer = "http://127.0.0.1:" + server.getAddress().getPort() + "/realms/exam-platform";

        probe.check(issuer, callback);
        probe.check(issuer, callback);

        assertThat(hitCount.get()).isEqualTo(1);
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

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
