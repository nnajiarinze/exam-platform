package se.medbo.examplatform.content.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class DevelopmentAdminAuthenticationFilter extends OncePerRequestFilter {
    static final String IDENTITY_HEADER = "X-Admin-Identity";
    static final String ROLES_HEADER = "X-Admin-Roles";
    private final boolean enabled;

    DevelopmentAdminAuthenticationFilter(@Value("${content.identity.development-header-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (enabled && SecurityContextHolder.getContext().getAuthentication() == null) {
            var identity = request.getHeader(IDENTITY_HEADER);
            if (identity != null && !identity.isBlank()) {
                List<SimpleGrantedAuthority> authorities = parseRoles(request.getHeader(ROLES_HEADER));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(identity.trim(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private static List<SimpleGrantedAuthority> parseRoles(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(role -> {
                    try { AdminRole.valueOf(role); return true; } catch (IllegalArgumentException ignored) { return false; }
                })
                .distinct()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
