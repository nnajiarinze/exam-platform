package se.medbo.examplatform.learning.contentprojection;

import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.ApiException;

@RestController
@RequestMapping("/internal/v1/content-releases")
public class ContentImportController {
    private final ContentImportService service;
    private final String internalApiKey;

    public ContentImportController(ContentImportService service,
            @Value("${learning.internal-api-key:}") String internalApiKey) {
        this.service = service;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/import")
    public ImportResponse importSnapshot(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
            @Valid @RequestBody ContentSnapshot snapshot) {
        if (internalApiKey.isBlank() || key == null || !MessageDigest.isEqual(
                internalApiKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INTERNAL_AUTHENTICATION_REQUIRED",
                    "Valid service authentication is required");
        }
        var result = service.importSnapshot(snapshot);
        return new ImportResponse(result.releaseId(), result.imported(), result.status());
    }

    public record ImportResponse(UUID releaseId, boolean imported, String status) {}
}
