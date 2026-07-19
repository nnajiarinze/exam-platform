package se.medbo.examplatform.learning.contentprojection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@Validated
@RestController
@RequestMapping("/api/v1/content")
public class ContentProjectionController {
    private final LearnerIdentityResolver identityResolver;
    private final ContentProjectionQueryService service;

    public ContentProjectionController(LearnerIdentityResolver identityResolver,
            ContentProjectionQueryService service) {
        this.identityResolver = identityResolver;
        this.service = service;
    }

    @GetMapping("/subjects")
    public List<ContentProjectionQueryService.SubjectView> subjects(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @NotBlank @Size(max = 200) @RequestParam String examId) {
        identityResolver.resolve(identity);
        return service.subjects(examId);
    }
}
