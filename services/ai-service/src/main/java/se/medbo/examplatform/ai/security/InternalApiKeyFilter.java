package se.medbo.examplatform.ai.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;import jakarta.servlet.ServletException;import jakarta.servlet.http.HttpServletRequest;import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;import java.time.Instant;import java.util.List;import java.util.Map;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class InternalApiKeyFilter extends OncePerRequestFilter {
    private final String key;private final ObjectMapper mapper;
    InternalApiKeyFilter(@Value("${ai.editorial.internal-api-key:}")String key,ObjectMapper mapper){this.key=key;this.mapper=mapper;}
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
        if(!req.getRequestURI().startsWith("/internal/")){chain.doFilter(req,res);return;}
        if(key.isBlank()||!constantTime(key,req.getHeader("X-Internal-Api-Key"))){res.setStatus(401);res.setContentType("application/json");mapper.writeValue(res.getOutputStream(),Map.of("code","INTERNAL_AUTHENTICATION_REQUIRED","message","Valid service authentication is required","timestamp",Instant.now(),"errors",List.of()));return;}chain.doFilter(req,res);
    }
    private boolean constantTime(String a,String b){if(b==null)return false;return java.security.MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),b.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
