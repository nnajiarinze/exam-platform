package se.medbo.examplatform.learning.contentprojection;

import jakarta.validation.Valid;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.ApiException;

@RestController
@RequestMapping("/internal/v1/content-releases")
public class ContentImportController {
    private final ContentImportService service;
    private final ContentReleaseActivationService activationService;
    private final String internalApiKey;

    public ContentImportController(ContentImportService service,ContentReleaseActivationService activationService,
            @Value("${learning.internal-api-key:}") String internalApiKey) {
        this.service = service;
        this.activationService=activationService;
        this.internalApiKey = internalApiKey;
    }

    @PostMapping("/import")
    public ImportResponse importSnapshot(@RequestHeader(value = "X-Internal-Api-Key", required = false) String key,
            @Valid @RequestBody ContentSnapshot snapshot) {
        authenticate(key);
        var result = service.importSnapshot(snapshot);
        return new ImportResponse(result.releaseId(), result.imported(), result.status());
    }

    public record ImportResponse(UUID releaseId, boolean imported, String status) {}

    @PostMapping("/{externalReleaseId}/activate")
    public ActivationResponse activate(@PathVariable String externalReleaseId,
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String key) {
        authenticate(key);var result=activationService.activate(externalReleaseId);return new ActivationResponse(result.releaseId(),result.activated(),result.status());
    }

    private void authenticate(String key){if (internalApiKey.isBlank() || key == null || !MessageDigest.isEqual(internalApiKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8))) throw new ApiException(HttpStatus.UNAUTHORIZED,"INTERNAL_AUTHENTICATION_REQUIRED","Valid service authentication is required");}
    public record ActivationResponse(UUID releaseId,boolean activated,String status){}
}
