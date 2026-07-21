package se.medbo.examplatform.learning.learner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@RestController
@RequestMapping("/api/v1/me")
public class LearnerProfileController {
    private final LearnerIdentityResolver identity;private final LearnerProfileService service;
    public LearnerProfileController(LearnerIdentityResolver identity,LearnerProfileService service){this.identity=identity;this.service=service;}
    @GetMapping public LearnerProfileService.Profile get(@RequestHeader(value="X-Learner-Identity",required=false)String developmentIdentity){return service.get(identity.resolve(developmentIdentity));}
    @PutMapping public LearnerProfileService.Profile update(@RequestHeader(value="X-Learner-Identity",required=false)String developmentIdentity,@Valid @RequestBody UpdateProfile request){return service.update(identity.resolve(developmentIdentity),request.displayName(),request.interfaceLanguage(),request.explanationLanguage(),request.onboardingCompleted());}
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@RequestHeader(value="X-Learner-Identity",required=false)String developmentIdentity){service.delete(identity.resolve(developmentIdentity));}
    public record UpdateProfile(@NotBlank @Size(max=200)String displayName,@NotBlank @Size(max=35)String interfaceLanguage,@NotBlank @Size(max=35)String explanationLanguage,boolean onboardingCompleted){}
}
