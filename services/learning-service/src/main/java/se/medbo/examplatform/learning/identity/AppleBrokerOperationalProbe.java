package se.medbo.examplatform.learning.identity;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Probes the live Keycloak Apple broker by initiating a synthetic OIDC authorization
 * request and following redirects until either Apple's authorization host is reached
 * (operational) or the flow terminates in some other, non-Apple outcome (degraded).
 *
 * This never completes an Apple login and never sends credentials; it only inspects
 * HTTP redirect hops using bounded timeouts, and caches the result briefly so readiness
 * checks do not repeatedly hit Apple or Keycloak.
 */
@Component
final class AppleBrokerOperationalProbe {
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_HOPS = 6;

    private final Clock clock;
    private volatile ProbeResult cached;

    AppleBrokerOperationalProbe() { this(Clock.systemUTC()); }

    AppleBrokerOperationalProbe(Clock clock) { this.clock = clock; }

    ProbeResult check(String issuer, String expectedCallback) {
        Instant now = clock.instant();
        ProbeResult prior = cached;
        if (prior != null && Duration.between(prior.checkedAt().toInstant(), now).compareTo(CACHE_TTL) < 0) {
            return prior;
        }
        ProbeResult computed = probe(issuer, expectedCallback, now);
        cached = computed;
        return computed;
    }

    private ProbeResult probe(String issuer, String expectedCallback, Instant now) {
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(REQUEST_TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .cookieHandler(cookieManager)
                    .build();
            URI current = URI.create(issuer.replaceAll("/+$", "") + "/protocol/openid-connect/auth?" + oauthQuery());
            for (int hop = 0; hop < MAX_HOPS; hop++) {
                HttpRequest request = HttpRequest.newBuilder(current).timeout(REQUEST_TIMEOUT).GET().build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("Location").orElse("");
                    if (location.isBlank()) {
                        return ProbeResult.degraded("BROKER_REDIRECT_LOCATION_MISSING", now);
                    }
                    URI next = current.resolve(location);
                    String host = next.getHost() == null ? "" : next.getHost().toLowerCase();
                    if (isAppleAuthorizationHost(host)) {
                        String clientId = queryValue(next, "client_id");
                        String redirectUri = queryValue(next, "redirect_uri");
                        boolean callbackMatches = expectedCallback.equals(redirectUri);
                        if (!callbackMatches) {
                            return new ProbeResult(false, "CALLBACK_MISMATCH", OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                                    host, fingerprint(clientId), false);
                        }
                        return new ProbeResult(true, "APPLE_REDIRECT_REACHED", OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                                host, fingerprint(clientId), true);
                    }
                    current = next;
                    continue;
                }
                if (status == 200) {
                    return ProbeResult.degraded("BROKER_RETURNED_INTERACTIVE_LOGIN", now);
                }
                if (status == 400 || status == 401 || status == 403) {
                    return ProbeResult.degraded("BROKER_SESSION_FAILED", now);
                }
                if (status >= 500) {
                    return ProbeResult.degraded("BROKER_UPSTREAM_ERROR_" + status, now);
                }
                return ProbeResult.degraded("BROKER_UNEXPECTED_STATUS_" + status, now);
            }
            return ProbeResult.degraded("BROKER_REDIRECT_HOP_LIMIT", now);
        } catch (java.net.http.HttpTimeoutException timeout) {
            return ProbeResult.degraded("TIMEOUT", now);
        } catch (java.io.IOException networkError) {
            return ProbeResult.degraded("NETWORK_ERROR", now);
        } catch (Exception exception) {
            return ProbeResult.degraded("APPLE_REDIRECT_NOT_REACHED", now);
        }
    }

    private static String oauthQuery() {
        List<String[]> pairs = List.of(
                new String[]{"client_id", "mobile-app"},
                new String[]{"redirect_uri", "sveastudy://auth/callback"},
                new String[]{"response_type", "code"},
                new String[]{"scope", "openid profile email"},
                new String[]{"code_challenge", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
                new String[]{"code_challenge_method", "S256"},
                new String[]{"state", "readiness-state"},
                new String[]{"nonce", "readiness-nonce"},
                new String[]{"kc_idp_hint", "apple"});
        StringBuilder query = new StringBuilder();
        for (int index = 0; index < pairs.size(); index++) {
            if (index > 0) query.append('&');
            query.append(encode(pairs.get(index)[0])).append('=').append(encode(pairs.get(index)[1]));
        }
        return query.toString();
    }

    private static String queryValue(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String pair : query.split("&")) {
            String[] tokens = pair.split("=", 2);
            if (tokens.length == 2 && decode(tokens[0]).equals(key)) {
                return decode(tokens[1]);
            }
        }
        return "";
    }

    private static String fingerprint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isAppleAuthorizationHost(String host) {
        return "appleid.apple.com".equals(host) || host.endsWith(".appleid.apple.com");
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String decode(String value) { return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8); }

    record ProbeResult(boolean operational, String classification, OffsetDateTime checkedAt, String redirectHost,
                       String clientIdFingerprint, boolean callbackMatches) {
        static ProbeResult degraded(String classification, Instant now) {
            return new ProbeResult(false, classification, OffsetDateTime.ofInstant(now, ZoneOffset.UTC), null, null, false);
        }
    }
}
