package se.medbo.examplatform.content.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestCorrelationFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{String supplied=request.getHeader("X-Request-Id");String requestId=supplied!=null&&supplied.matches("[A-Za-z0-9._-]{1,100}")?supplied:UUID.randomUUID().toString();response.setHeader("X-Request-Id",requestId);try(MDC.MDCCloseable ignored=MDC.putCloseable("requestId",requestId)){chain.doFilter(request,response);}}
}
