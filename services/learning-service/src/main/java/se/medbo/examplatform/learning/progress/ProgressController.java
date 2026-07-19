package se.medbo.examplatform.learning.progress;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@RestController
@RequestMapping("/api/v1/progress")
public class ProgressController {
    private final LearnerIdentityResolver identityResolver;
    private final ProgressService service;

    public ProgressController(LearnerIdentityResolver identityResolver, ProgressService service) {
        this.identityResolver = identityResolver;
        this.service = service;
    }

    @GetMapping("/topics")
    public List<ProgressService.TopicProgressView> topics(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity) {
        return service.topics(identityResolver.resolve(identity));
    }
}
