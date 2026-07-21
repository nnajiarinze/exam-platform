package se.medbo.examplatform.ai.question;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/question-generation")
final class QuestionGenerationController {
  private final QuestionGenerationJobService service;private final String provider,model;
  QuestionGenerationController(QuestionGenerationJobService service,@Value("${ai.editorial.provider:FAKE}")String provider,@Value("${ai.editorial.model:deterministic-v1}")String model){this.service=service;this.provider=provider;this.model=model;}
  record Create(@NotNull QuestionGenerationProviderClient.Target target,@NotNull QuestionGenerationProviderClient.Context context,
                @Min(1)@Max(3)int proposalCount,String questionType,@NotBlank String requestedBy,
                @NotBlank@Size(max=200)String idempotencyKey){}
  record Reject(@Size(max=500)String reason,@NotBlank String actor,@PositiveOrZero long version){}
  @PostMapping("/jobs")@ResponseStatus(HttpStatus.ACCEPTED)Map<String,Object>create(@Valid@RequestBody Create r){return service.create(new QuestionGenerationProviderClient.Request(r.target(),r.context(),r.proposalCount(),r.questionType(),"question-generation-foundation-v1",null,null,0),r.requestedBy(),r.idempotencyKey(),provider,model);}
  @GetMapping("/jobs/{id}")Map<String,Object>job(@PathVariable UUID id){return service.get(id);}
  @GetMapping("/jobs/{id}/proposals")List<Map<String,Object>>proposals(@PathVariable UUID id){return service.proposals(id);}
  @PostMapping("/jobs/{id}/cancel")Map<String,Object>cancel(@PathVariable UUID id){return service.cancel(id);}
  @GetMapping("/proposals/{id}")Map<String,Object>proposal(@PathVariable UUID id){return service.proposal(id);}
  @PostMapping("/proposals/{id}/reject")Map<String,Object>reject(@PathVariable UUID id,@Valid@RequestBody Reject r){return service.reject(id,r.reason(),r.actor(),r.version());}
}
