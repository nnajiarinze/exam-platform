package se.medbo.examplatform.content.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import se.medbo.examplatform.content.shared.ApiErrorResponse;

@Component
public class AdministrativeRateLimitFilter extends OncePerRequestFilter {
    private final Map<String,Window> windows=new ConcurrentHashMap<>(); private final int limit; private final long seconds; private final ObjectMapper mapper;
    public AdministrativeRateLimitFilter(ObjectMapper mapper,@Value("${content.rate-limit.sensitive-writes-per-window:30}")int limit,@Value("${content.rate-limit.window-seconds:60}")long seconds){this.mapper=mapper;this.limit=limit;this.seconds=seconds;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
        if(!sensitive(request)){chain.doFilter(request,response);return;}String actor=request.getHeader("X-Admin-Identity");if(actor==null||actor.isBlank())actor=request.getRemoteAddr();String key=actor+":"+request.getRequestURI();long bucket=Instant.now().getEpochSecond()/seconds;Window window=windows.compute(key,(ignored,current)->current==null||current.bucket()!=bucket?new Window(bucket,1):new Window(bucket,current.count()+1));if(window.count()>limit){response.setStatus(429);response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.setHeader("Retry-After",Long.toString(seconds));mapper.writeValue(response.getOutputStream(),ApiErrorResponse.of("RATE_LIMIT_EXCEEDED","Too many sensitive administrative requests"));return;}chain.doFilter(request,response);
    }
    private boolean sensitive(HttpServletRequest request){if(!"POST".equals(request.getMethod())&&!"PUT".equals(request.getMethod()))return false;String path=request.getRequestURI();return path.contains("/publish")||path.contains("/deliver")||path.contains("/activate")||path.contains("/approve")||path.contains("/reject")||path.contains("/require-update")||path.contains("/reviews/");}
    private record Window(long bucket,int count){}
}
