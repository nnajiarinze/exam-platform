package se.medbo.examplatform.learning.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/readiness")
final class AuthenticationReadinessController {
    private final AuthenticationReadinessService service;
    AuthenticationReadinessController(AuthenticationReadinessService service) { this.service = service; }
    @GetMapping AuthenticationReadinessService.Readiness get() { return service.get(); }
}
