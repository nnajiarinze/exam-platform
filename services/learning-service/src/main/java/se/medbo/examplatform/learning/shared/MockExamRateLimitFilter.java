package se.medbo.examplatform.learning.shared;

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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class MockExamRateLimitFilter extends OncePerRequestFilter {
    private final Map<String,Window> windows=new ConcurrentHashMap<>();private final int limit;private final long seconds;private final ObjectMapper mapper;
    public MockExamRateLimitFilter(ObjectMapper mapper,@Value("${learning.rate-limit.mock-exam-starts-per-window:10}")int limit,@Value("${learning.rate-limit.window-seconds:60}")long seconds){this.mapper=mapper;this.limit=limit;this.seconds=seconds;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{if(!"POST".equals(request.getMethod())||!"/api/v1/mock-exams".equals(request.getRequestURI())){chain.doFilter(request,response);return;}String learner=request.getHeader("X-Learner-Identity");if(learner==null||learner.isBlank())learner=request.getRemoteAddr();long bucket=Instant.now().getEpochSecond()/seconds;Window window=windows.compute(learner,(key,current)->current==null||current.bucket()!=bucket?new Window(bucket,1):new Window(bucket,current.count()+1));if(window.count()>limit){response.setStatus(429);response.setContentType("application/json");response.setHeader("Retry-After",Long.toString(seconds));mapper.writeValue(response.getOutputStream(),Map.of("code","RATE_LIMIT_EXCEEDED","message","Too many mock exam start requests","timestamp",Instant.now(),"errors",java.util.List.of()));return;}chain.doFilter(request,response);}
    private record Window(long bucket,int count){}
}
