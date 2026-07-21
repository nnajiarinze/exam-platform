package se.medbo.examplatform.content.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import se.medbo.examplatform.content.shared.ApiErrorResponse;

@Configuration
class SecurityConfiguration {
    private static final String[] ADMIN_ROLES = {"CONTENT_AUTHOR", "CONTENT_REVIEWER", "CONTENT_PUBLISHER", "ADMIN"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, DevelopmentAdminAuthenticationFilter filter,
            AdministrativeRateLimitFilter rateLimitFilter,
            se.medbo.examplatform.content.shared.RequestCorrelationFilter correlationFilter, ObjectMapper mapper)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/v1/status").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers("/api/v1/admin/ai/provider/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/releases/*").hasAnyRole("CONTENT_PUBLISHER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/knowledge-facts/*", "/api/v1/admin/questions/*").hasAnyRole("CONTENT_AUTHOR", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/sources/*/review", "/api/v1/admin/sources/*/require-update", "/api/v1/admin/sources/*/retire").hasAnyRole("CONTENT_REVIEWER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/knowledge-facts/*/approve", "/api/v1/admin/knowledge-facts/*/reject", "/api/v1/admin/knowledge-facts/*/require-update", "/api/v1/admin/knowledge-facts/*/retire").hasAnyRole("CONTENT_REVIEWER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/questions/*/approve", "/api/v1/admin/questions/*/reject", "/api/v1/admin/questions/*/require-update", "/api/v1/admin/questions/*/retire").hasAnyRole("CONTENT_REVIEWER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/reviews/*/assign", "/api/v1/admin/reviews/*/priority").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/reviews/*/claim", "/api/v1/admin/reviews/*/unclaim", "/api/v1/admin/reviews/*/comments").hasAnyRole("CONTENT_REVIEWER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/releases/*/validate", "/api/v1/admin/releases/*/publish", "/api/v1/admin/releases/*/deliver", "/api/v1/admin/releases/*/retry-delivery", "/api/v1/admin/releases/*/activate", "/api/v1/admin/releases/*/retire").hasAnyRole("CONTENT_PUBLISHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/releases").hasAnyRole("CONTENT_PUBLISHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/ai/editorial-jobs", "/api/v1/admin/ai/editorial-findings/*/dismiss").hasAnyRole("CONTENT_AUTHOR", "CONTENT_REVIEWER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/ai/**").hasAnyRole("CONTENT_AUTHOR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/ai/**").hasAnyRole("CONTENT_AUTHOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/releases/**").hasAnyRole("CONTENT_PUBLISHER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/admin/**").hasAnyRole("CONTENT_AUTHOR", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/**").hasAnyRole("CONTENT_AUTHOR", "ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeError(mapper, response, 401, "AUTHENTICATION_REQUIRED", "Valid admin authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> writeError(mapper, response, 403, "FORBIDDEN", "The administrator does not have a required role")))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(adminJwtConverter())))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, DevelopmentAdminAuthenticationFilter.class)
                .addFilterBefore(correlationFilter, AdministrativeRateLimitFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${content.identity.development-header-enabled:false}") boolean developmentEnabled,
            @Value("${content.identity.development-allowed-origins:http://localhost:*,http://127.0.0.1:*}") List<String> origins) {
        var source = new UrlBasedCorsConfigurationSource();
        var config = new CorsConfiguration();
            config.setAllowedOriginPatterns(origins);
            config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            config.setAllowedHeaders(List.of("Accept", "Authorization", "Content-Type", DevelopmentAdminAuthenticationFilter.IDENTITY_HEADER, DevelopmentAdminAuthenticationFilter.ROLES_HEADER));
            source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean NimbusJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")String issuer,@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")String jwks,@Value("${content.security.audience:content-api}")String audience){var decoder=NimbusJwtDecoder.withJwkSetUri(jwks).build();decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),new AudienceValidator(audience)));return decoder;}

    private static JwtAuthenticationConverter adminJwtConverter(){var converter=new JwtAuthenticationConverter();converter.setJwtGrantedAuthoritiesConverter(jwt->{Object access=jwt.getClaim("realm_access");if(!(access instanceof java.util.Map<?,?> map)||!(map.get("roles") instanceof java.util.Collection<?> roles))return List.of();return roles.stream().map(Object::toString).filter(role->{try{AdminRole.valueOf(role);return true;}catch(IllegalArgumentException ignored){return false;}}).map(role->(GrantedAuthority)new SimpleGrantedAuthority("ROLE_"+role)).toList();});converter.setPrincipalClaimName("sub");return converter;}

    private static void writeError(ObjectMapper mapper, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, message));
    }
}
