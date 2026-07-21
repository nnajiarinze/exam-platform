package se.medbo.examplatform.content.ai;

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
@RequestMapping("/api/v1/admin/ai")
final class EditorialWorkspaceController {
  private final EditorialWorkspaceService service;

  EditorialWorkspaceController(EditorialWorkspaceService service) { this.service = service; }

  record Create(
      @NotNull EditorialWorkspaceService.Operation operation,
      @NotNull UUID targetKnowledgeFactId,
      @NotBlank String language,
      String readingPreference,
      @Size(max = 1000) String instruction,
      @Min(1) @Max(3) int requestedCount,
      @NotBlank String idempotencyKey) {}

  record Edit(@NotBlank @Size(max = 500) String text, @PositiveOrZero long version) {}
  record Decision(@Size(max = 500) String reason, @PositiveOrZero long version) {}
  record Accept(@PositiveOrZero long version) {}
  record SplitAccept(
      @NotNull UUID jobId,
      @NotNull UUID targetKnowledgeFactId,
      @NotNull UUID targetFactVersionId,
      @NotBlank @Size(min = 64, max = 64) String targetContentChecksum,
      @NotNull @Size(min = 1, max = 5) List<UUID> selectedProposalIds,
      @NotNull EditorialWorkspaceService.SplitAcceptanceMode acceptanceMode,
      @NotBlank @Size(max = 200) String idempotencyKey) {}

  @PostMapping("/editorial-jobs")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, Object> create(@Valid @RequestBody Create request) {
    return service.create(new EditorialWorkspaceService.Create(
        request.operation(), request.targetKnowledgeFactId(), request.language(),
        request.readingPreference(), request.instruction(), request.requestedCount(),
        request.idempotencyKey()));
  }

  @GetMapping("/editorial-jobs/{id}")
  Map<String, Object> job(@PathVariable UUID id) { return service.job(id); }

  @PostMapping("/editorial-jobs/{id}/cancel")
  Map<String, Object> cancel(@PathVariable UUID id) { return service.cancel(id); }

  @GetMapping("/editorial-jobs/{id}/proposals")
  List<Map<String, Object>> proposals(@PathVariable UUID id) { return service.proposals(id); }

  @GetMapping("/editorial-jobs/{id}/findings")
  List<Map<String, Object>> findings(@PathVariable UUID id) { return service.findings(id); }

  @PatchMapping("/editorial-proposals/{id}")
  Map<String, Object> edit(@PathVariable UUID id, @Valid @RequestBody Edit request) {
    return service.edit(id, request.text(), request.version());
  }

  @PostMapping("/editorial-proposals/{id}/reject")
  Map<String, Object> reject(@PathVariable UUID id, @Valid @RequestBody Decision request) {
    return service.reject(id, request.reason(), request.version());
  }

  @PostMapping("/editorial-proposals/{id}/accept")
  Map<String, Object> accept(@PathVariable UUID id, @Valid @RequestBody Accept request) {
    return service.accept(id, request.version());
  }

  @PostMapping("/editorial-proposals/accept-split")
  Map<String, Object> acceptSplit(@Valid @RequestBody SplitAccept request) {
    return service.acceptSplit(request.jobId(), request.targetKnowledgeFactId(),
        request.targetFactVersionId(), request.targetContentChecksum(), request.selectedProposalIds(),
        request.acceptanceMode(), request.idempotencyKey());
  }

  @PostMapping("/editorial-findings/{id}/dismiss")
  Map<String, Object> dismissFinding(@PathVariable UUID id, @Valid @RequestBody Decision request) {
    return service.dismissFinding(id, request.reason(), request.version());
  }

  @GetMapping("/provider/status")
  Map<String,Object> providerStatus(){ return service.providerStatus(); }

  @GetMapping("/provider/alerts")
  List<Map<String,Object>> providerAlerts(){ return service.providerAlerts(); }

  @PostMapping("/provider/disable")
  Map<String,Object> disableProvider(){ return service.disableProvider(); }

  @PostMapping("/provider/recheck")
  Map<String,Object> recheckProvider(){ return service.recheckProvider(); }

  @PostMapping("/provider/alerts/{id}/acknowledge")
  @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  void acknowledgeProviderAlert(@PathVariable UUID id){ service.acknowledgeProviderAlert(id); }
}
