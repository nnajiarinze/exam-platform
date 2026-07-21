package se.medbo.examplatform.ai.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/internal/v1/provider")
final class GeminiProviderOperationsController {
  record Actor(@NotBlank String actor) {}
  private final GeminiQuotaService quota;
  GeminiProviderOperationsController(GeminiQuotaService quota){this.quota=quota;}
  @GetMapping("/status") Map<String,Object> status(){return quota.status();}
  @GetMapping("/alerts") List<Map<String,Object>> alerts(){return quota.alerts();}
  @PostMapping("/disable") Map<String,Object> disable(@Valid @RequestBody Actor body){quota.disable(body.actor());return quota.status();}
  @PostMapping("/recheck") Map<String,Object> recheck(@Valid @RequestBody Actor body){quota.recheck(body.actor());return quota.status();}
  @PostMapping("/alerts/{id}/acknowledge") @ResponseStatus(HttpStatus.NO_CONTENT) void acknowledge(@PathVariable UUID id,@Valid @RequestBody Actor body){quota.acknowledge(id,body.actor());}
}
