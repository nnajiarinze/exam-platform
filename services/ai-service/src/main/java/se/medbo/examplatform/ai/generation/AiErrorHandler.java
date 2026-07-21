package se.medbo.examplatform.ai.generation;
import java.time.Instant;import java.util.List;import java.util.Map;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice final class AiErrorHandler {@ExceptionHandler(AiApiException.class)ResponseEntity<?>domain(AiApiException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage(),"timestamp",Instant.now(),"errors",List.of()));}}
