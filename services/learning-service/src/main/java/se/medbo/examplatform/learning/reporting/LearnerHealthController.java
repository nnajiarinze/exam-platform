package se.medbo.examplatform.learning.reporting;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.ApiException;

@RestController
@RequestMapping("/internal/v1/reports")
public class LearnerHealthController {
    private final LearnerHealthService service;
    private final String internalApiKey;
    public LearnerHealthController(LearnerHealthService service,@Value("${learning.internal-api-key:}") String key) { this.service=service;this.internalApiKey=key; }

    @GetMapping("/learner-health")
    public Map<String,Object> learnerHealth(@RequestHeader(value="X-Internal-Api-Key",required=false) String key) {
        authenticate(key);
        return service.report();
    }
    private void authenticate(String key){if(internalApiKey.isBlank()||key==null||!MessageDigest.isEqual(internalApiKey.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ApiException(HttpStatus.UNAUTHORIZED,"INTERNAL_AUTHENTICATION_REQUIRED","Valid service authentication is required");}
}
