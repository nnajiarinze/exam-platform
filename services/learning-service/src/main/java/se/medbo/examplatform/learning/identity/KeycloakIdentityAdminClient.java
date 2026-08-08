package se.medbo.examplatform.learning.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class KeycloakIdentityAdminClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final URI baseUri;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    private final boolean enabled;
    private volatile AccessToken accessToken;

    KeycloakIdentityAdminClient(ObjectMapper mapper,
            @Value("${learning.identity.management.keycloak-base-url:http://localhost:8090}") String baseUrl,
            @Value("${learning.identity.management.realm:exam-platform}") String realm,
            @Value("${learning.identity.management.client-id:}") String clientId,
            @Value("${learning.identity.management.client-secret:}") String clientSecret,
            @Value("${learning.identity.management.enabled:false}") boolean enabled) {
        this.mapper = mapper;
        this.baseUri = URI.create(baseUrl.replaceAll("/+$", "") + "/");
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.enabled = enabled && !clientId.isBlank() && !clientSecret.isBlank();
    }

    boolean ready() { return enabled; }

    EmailConfiguration emailConfiguration() {
        JsonNode realmConfiguration = json("GET", "admin/realms/" + encode(realm), null, Set.of(200));
        JsonNode smtp = realmConfiguration.path("smtpServer");
        JsonNode attributes = realmConfiguration.path("attributes");
        boolean configured = "smtp.resend.com".equals(smtp.path("host").asText())
                && "587".equals(smtp.path("port").asText()) && "true".equals(smtp.path("starttls").asText())
                && "true".equals(smtp.path("auth").asText()) && "resend".equals(smtp.path("user").asText())
                && !smtp.path("password").asText().isBlank();
        return new EmailConfiguration(configured, smtp.path("from").asText(), smtp.path("replyTo").asText(),
                attributes.path("resendDomainStatus").asText("UNKNOWN"), attributes.path("resendSpfStatus").asText("UNKNOWN"),
                attributes.path("resendDkimStatus").asText("UNKNOWN"), attributes.path("emailDmarcStatus").asText("UNKNOWN"),
                nullable(attributes.path("lastSmtpTestAt").asText()), nullable(attributes.path("lastVerificationEmailAt").asText()),
                nullable(attributes.path("lastResetEmailAt").asText()));
    }

    ProviderStatus providerStatus(String alias) {
        // Query the per-alias resource directly instead of enumerating the full
        // identity-provider/instances collection. Some Keycloak deployments/proxies
        // reject or 404 the collection GET even when the fine-grained per-alias
        // resource is reachable and correctly configured, so treating a 404 here
        // as "provider not present" avoids a false MISCONFIGURED reading.
        JsonNode provider = json("GET", "admin/realms/" + encode(realm) + "/identity-provider/instances/" + encode(alias),
                null, Set.of(200, 404));
        if (provider == null || provider.isNull() || provider.isMissingNode() || provider.path("alias").isMissingNode()) {
            return new ProviderStatus(alias, false, false, false, false, false);
        }
        JsonNode config = provider.path("config");
        String clientId = config.path("clientId").asText("");
        String clientSecret = config.path("clientSecret").asText("");
        boolean unsafeAutoLinkPresent = firstBrokerAutoLinkPresent();
        return new ProviderStatus(alias, true, provider.path("enabled").asBoolean(false),
                isProtectedValue(clientId), isProtectedValue(clientSecret), unsafeAutoLinkPresent);
    }

    Methods methods(String userId) {
        JsonNode identities = json("GET", adminUser(userId) + "/federated-identity", null, Set.of(200));
        JsonNode credentials = json("GET", adminUser(userId) + "/credentials", null, Set.of(200));
        var providers = new ArrayList<String>();
        identities.forEach(node -> providers.add(node.path("identityProvider").asText()));
        boolean password = false;
        for (JsonNode credential : credentials) if ("password".equals(credential.path("type").asText())) password = true;
        return new Methods(password, List.copyOf(providers));
    }

    void unlink(String userId, String provider) {
        request("DELETE", adminUser(userId) + "/federated-identity/" + encode(provider), null, Set.of(204));
    }

    void logoutAll(String userId) { request("POST", adminUser(userId) + "/logout", "", Set.of(204)); }

    void logoutSession(String sessionId) {
        request("DELETE", "admin/realms/" + encode(realm) + "/sessions/" + encode(sessionId), null, Set.of(204));
    }

    void deleteUser(String userId) { request("DELETE", adminUser(userId), null, Set.of(204, 404)); }

    private boolean firstBrokerAutoLinkPresent() {
        String authenticationFlows = "admin/realms/" + encode(realm) + "/authentication/flows";
        JsonNode flows = json("GET", authenticationFlows, null, Set.of(200));
        String flowId = null;
        for (JsonNode flow : flows) {
            if ("first broker login".equals(flow.path("alias").asText())) {
                flowId = flow.path("id").asText();
                break;
            }
        }
        if (flowId == null || flowId.isBlank()) {
            return true;
        }
        JsonNode executions = json("GET", authenticationFlows + "/" + encode(flowId) + "/executions", null, Set.of(200));
        for (JsonNode execution : executions) {
            if ("idp-auto-link".equals(execution.path("providerId").asText())) {
                return true;
            }
        }
        return false;
    }

    private String adminUser(String userId) { return "admin/realms/" + encode(realm) + "/users/" + encode(userId); }

    private JsonNode json(String method, String path, String body, Set<Integer> expected) {
        try { return mapper.readTree(request(method, path, body, expected)); }
        catch (IOException exception) { throw new IdentityAdminException("IDENTITY_RESPONSE_INVALID", exception); }
    }

    private String request(String method, String path, String body, Set<Integer> expected) {
        ensureReady();
        try {
            HttpRequest.BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body);
            HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path)).timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token()).header("Accept", "application/json")
                    .method(method, publisher).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (!expected.contains(response.statusCode())) throw new IdentityAdminException("IDENTITY_ADMIN_REJECTED_" + response.statusCode());
            return response.body();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IdentityAdminException("IDENTITY_ADMIN_INTERRUPTED", exception);
        } catch (IOException exception) { throw new IdentityAdminException("IDENTITY_ADMIN_UNAVAILABLE", exception); }
    }

    private synchronized String token() {
        if (accessToken != null && accessToken.expiresAt().isAfter(Instant.now().plusSeconds(15))) return accessToken.value();
        try {
            String form = "grant_type=client_credentials&client_id=" + encode(clientId) + "&client_secret=" + encode(clientSecret);
            URI tokenUri = baseUri.resolve("realms/" + encode(realm) + "/protocol/openid-connect/token");
            HttpRequest request = HttpRequest.newBuilder(tokenUri).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IdentityAdminException("IDENTITY_SERVICE_AUTHENTICATION_FAILED");
            JsonNode value = mapper.readTree(response.body());
            String token = value.path("access_token").asText();
            long expiresIn = value.path("expires_in").asLong(60);
            if (token.isBlank()) throw new IdentityAdminException("IDENTITY_SERVICE_TOKEN_MISSING");
            accessToken = new AccessToken(token, Instant.now().plusSeconds(expiresIn));
            return token;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt(); throw new IdentityAdminException("IDENTITY_SERVICE_AUTHENTICATION_INTERRUPTED", exception);
        } catch (IOException exception) { throw new IdentityAdminException("IDENTITY_SERVICE_AUTHENTICATION_UNAVAILABLE", exception); }
    }

    private void ensureReady() { if (!enabled) throw new IdentityAdminException("IDENTITY_MANAGEMENT_NOT_CONFIGURED"); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static boolean isProtectedValue(String value) { return value != null && !value.isBlank() && !"CHANGE_ME".equals(value); }

    record Methods(boolean password, List<String> providers) {}
    record EmailConfiguration(boolean configured, String sender, String replyTo, String domainStatus, String spfStatus,
                              String dkimStatus, String dmarcStatus, String lastSmtpTestAt,
                              String lastVerificationEmailAt, String lastResetEmailAt) {
        static EmailConfiguration unavailable() {
            return new EmailConfiguration(false, "", "", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", null, null, null);
        }
    }
    record ProviderStatus(String alias, boolean present, boolean enabled, boolean clientIdConfigured,
                          boolean clientSecretConfigured, boolean unsafeAutoLinkPresent) {}
    private static String nullable(String value) { return value == null || value.isBlank() ? null : value; }
    private record AccessToken(String value, Instant expiresAt) {}
    static final class IdentityAdminException extends RuntimeException {
        private final String code;
        IdentityAdminException(String code) { super(code); this.code = code; }
        IdentityAdminException(String code, Throwable cause) { super(code, cause); this.code = code; }
        String code() { return code; }
    }
}
