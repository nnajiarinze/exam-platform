package se.medbo.examplatform.ai.editorial;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/editorial")
final class EditorialController {
  private final EditorialJobService service;
  private final String provider;
  private final String model;

  EditorialController(
      EditorialJobService service,
      @Value("${ai.editorial.provider:FAKE}") String provider,
      @Value("${ai.editorial.model:deterministic-v1}") String model) {
    this.service = service;
    this.provider = provider;
    this.model = model;
  }

  record Create(
      @NotNull EditorialOperationType operation,
      @NotNull List<AiEditorialProviderClient.Target> targets,
      @NotNull List<AiEditorialProviderClient.Source> sources,
      @NotNull UUID learningObjectiveId,
      @NotBlank String objectiveTitle,
      @NotBlank String language,
      String readingPreference,
      @Size(max = 1000) String instruction,
      @Min(1) @Max(5) int count,
      @NotBlank String requestedBy,
      @NotBlank String idempotencyKey) {}

  record Edit(@NotBlank @Size(max = 500) String text, @PositiveOrZero long version) {}

  record Decision(String reason, @PositiveOrZero long version) {}

  record Accepted(
      @NotNull UUID factId,
      @NotNull UUID factVersionId,
      @NotBlank String actor,
      @PositiveOrZero long version) {}

  @PostMapping("/jobs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, Object> create(@Valid @RequestBody Create request) {
    return service.create(
        new AiEditorialProviderClient.Request(
            request.operation(), request.targets(), request.sources(),
            request.learningObjectiveId(), request.objectiveTitle(), request.language(),
            request.readingPreference(), request.instruction(), request.count(), "server-selected"),
        request.requestedBy(), request.idempotencyKey(), provider, model);
  }

  @GetMapping("/jobs/{id}")
  Map<String, Object> get(@PathVariable UUID id) { return service.get(id); }

  @GetMapping("/jobs/{id}/proposals")
  List<Map<String, Object>> proposals(@PathVariable UUID id) { return service.proposals(id); }

  @GetMapping("/jobs/{id}/findings")
  List<Map<String, Object>> findings(@PathVariable UUID id) { return service.findings(id); }

  @PostMapping("/jobs/{id}/cancel")
  Map<String, Object> cancel(@PathVariable UUID id) { return service.cancel(id); }

  @GetMapping("/proposals/{id}")
  Map<String, Object> proposal(@PathVariable UUID id) { return service.proposal(id); }

  @PatchMapping("/proposals/{id}")
  Map<String, Object> edit(@PathVariable UUID id, @Valid @RequestBody Edit request) {
    return service.edit(id, request.text(), request.version());
  }

  @PostMapping("/proposals/{id}/reject")
  Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody Decision request) {
    return service.reject(id, request.reason(), request.version());
  }

  @PostMapping("/proposals/{id}/accepted")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void accepted(@PathVariable UUID id, @Valid @RequestBody Accepted request) {
    service.markAccepted(id, request.factId(), request.factVersionId(), request.actor(), request.version());
  }

  record Dismiss(@NotBlank String actor, @Size(max = 500) String reason, @PositiveOrZero long version) {}

  @GetMapping("/findings/{id}")
  Map<String, Object> finding(@PathVariable UUID id) { return service.finding(id); }

  @PostMapping("/findings/{id}/dismiss")
  Map<String, Object> dismiss(@PathVariable UUID id, @Valid @RequestBody Dismiss request) {
    return service.dismissFinding(id, request.actor(), request.reason(), request.version());
  }
}
