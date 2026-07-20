package se.medbo.examplatform.content.status;

import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
final class ContentServiceStatusController {
    private final JdbcClient jdbc;

    ContentServiceStatusController(JdbcClient jdbc) { this.jdbc = jdbc; }

    @GetMapping
    ContentServiceStatus status() {
        jdbc.sql("SELECT 1").query(Integer.class).single();
        return new ContentServiceStatus("content-service", "READY", Instant.now());
    }

    record ContentServiceStatus(String service, String status, Instant timestamp) {}
}
