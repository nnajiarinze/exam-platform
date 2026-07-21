package se.medbo.examplatform.learning.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class DevelopmentLearnerAuthenticationFilter extends OncePerRequestFilter {
    private final boolean enabled;
    public DevelopmentLearnerAuthenticationFilter(@Value("${learning.identity.development-header-enabled:false}") boolean enabled){this.enabled=enabled;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(enabled&&SecurityContextHolder.getContext().getAuthentication()==null){String identity=request.getHeader("X-Learner-Identity");if(identity!=null&&!identity.isBlank())SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(identity,null,List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))));}
        chain.doFilter(request,response);
    }
}
