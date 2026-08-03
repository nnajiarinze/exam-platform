package se.medbo.examplatform.learning.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@RestController
@RequestMapping("/api/v1/me/identity")
final class IdentityManagementController {
    private final LearnerIdentityResolver identities;
    private final IdentityManagementService service;

    IdentityManagementController(LearnerIdentityResolver identities, IdentityManagementService service) {
        this.identities = identities; this.service = service;
    }

    @GetMapping("/methods")
    IdentityManagementService.LinkedMethods methods(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity) {
        return service.methods(identities.resolve(developmentIdentity));
    }

    @PostMapping("/links/{provider}")
    IdentityManagementService.LinkInitiation initiateLink(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity,
            @PathVariable String provider) {
        return service.initiateLink(identities.resolve(developmentIdentity), provider);
    }

    @DeleteMapping("/links/{provider}")
    IdentityManagementService.LinkedMethods unlink(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity,
            @PathVariable String provider) {
        return service.unlink(identities.resolve(developmentIdentity), provider);
    }

    @PostMapping("/sessions/current/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutCurrent(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity) {
        service.logoutCurrent(identities.resolve(developmentIdentity));
    }

    @PostMapping("/sessions/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logoutAll(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity) {
        service.logoutAll(identities.resolve(developmentIdentity));
    }

    @PostMapping("/deletion")
    IdentityManagementService.DeletionStatus beginDeletion(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity) {
        return service.beginDeletion(identities.resolve(developmentIdentity));
    }

    @PostMapping("/deletion/confirm")
    IdentityManagementService.DeletionStatus confirmDeletion(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity,
            @Valid @RequestBody ConfirmDeletion request) {
        return service.confirmDeletion(identities.resolve(developmentIdentity), request.requestId(), request.confirmation());
    }

    @GetMapping("/deletion")
    IdentityManagementService.DeletionStatus deletionStatus(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity) {
        return service.deletionStatus(identities.resolve(developmentIdentity));
    }

    @GetMapping("/readiness")
    IdentityManagementService.Readiness readiness(@RequestHeader(value="X-Learner-Identity", required=false) String developmentIdentity) {
        identities.resolve(developmentIdentity);
        return service.readiness();
    }

    record ConfirmDeletion(@NotNull UUID requestId, @NotBlank String confirmation) {}
}

