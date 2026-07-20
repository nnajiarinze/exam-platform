package se.medbo.examplatform.content.reporting;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reports")
public class OperationalReportingController {
    private final OperationalReportingService service;
    private final LearnerHealthClient learnerHealth;
    public OperationalReportingController(OperationalReportingService service,LearnerHealthClient learnerHealth) { this.service = service;this.learnerHealth=learnerHealth; }
    @GetMapping("/content-health") public Map<String,Object> contentHealth() { return service.contentHealth(); }
    @GetMapping("/review-health") public Map<String,Object> reviewHealth(Authentication auth) { return service.reviewHealth(auth.getName()); }
    @GetMapping("/source-health") public Map<String,Object> sourceHealth() { return service.sourceHealth(); }
    @GetMapping("/release-health") public Map<String,Object> releaseHealth() { return service.releaseHealth(); }
    @GetMapping("/learner-health") public Map<String,Object> learnerHealth() { return learnerHealth.get(); }
}
