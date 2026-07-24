package se.medbo.examplatform.ai.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/question-generation-batches")
final class QuestionGenerationBatchController {
  record Item(@NotNull QuestionGenerationProviderClient.Target target,@NotNull QuestionGenerationProviderClient.Context context,
              @NotBlank String questionType,String targetDifficulty,String targetBloomLevel){}
  record Create(@NotBlank String scopeType,UUID scopeId,String scopeLabel,@NotBlank String language,
                @NotNull Map<String,Object> configuration,@NotBlank String requestedBy,
                @NotBlank@Size(max=200)String idempotencyKey,@NotEmpty@Size(max=200)List<@Valid Item> items){}
  record Retry(List<UUID> itemIds,@NotBlank String actor){}
  record Assignment(@NotEmpty List<UUID> proposalIds,@NotBlank String reviewerId,@NotBlank String actor,OffsetDateTime reviewDeadline){}
  record Unassignment(@NotEmpty List<UUID> proposalIds,@NotBlank String actor){}
  private final QuestionGenerationBatchService service;
  QuestionGenerationBatchController(QuestionGenerationBatchService service){this.service=service;}
  @PostMapping@ResponseStatus(HttpStatus.ACCEPTED)Map<String,Object>create(@Valid@RequestBody Create r){return service.create(new QuestionGenerationBatchService.Definition(r.scopeType(),r.scopeId(),r.scopeLabel(),r.language(),r.configuration(),r.requestedBy(),r.idempotencyKey(),r.items().stream().map(i->new QuestionGenerationBatchService.Item(i.target(),i.context(),i.questionType(),i.targetDifficulty(),i.targetBloomLevel())).toList()));}
  @GetMapping Map<String,Object>list(@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size,@RequestParam(required=false)String status,@RequestParam(required=false)String scopeType,@RequestParam(required=false)String createdBy){return service.list(page,size,status,scopeType,createdBy);}
  @GetMapping("/{id}")Map<String,Object>get(@PathVariable UUID id){return service.batch(id);}
  @GetMapping("/{id}/items")Map<String,Object>items(@PathVariable UUID id,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="50")@Min(1)@Max(100)int size,@RequestParam(required=false)String status,@RequestParam(required=false)UUID knowledgeFactId){return service.items(id,page,size,status,knowledgeFactId);}
  @GetMapping("/{id}/proposals")Map<String,Object>proposals(@PathVariable UUID id,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size,@RequestParam(required=false)String status){return service.proposals(id,page,size,status);}
  @PostMapping("/{id}/cancel")Map<String,Object>cancel(@PathVariable UUID id,@RequestBody Map<String,String> body){return service.cancel(id,body.get("actor"));}
  @PostMapping("/{id}/retry-failed")Map<String,Object>retry(@PathVariable UUID id,@Valid@RequestBody Retry r){return service.retryFailed(id,r.itemIds(),r.actor());}
  @PostMapping("/proposals/assign")Map<String,Object>assign(@Valid@RequestBody Assignment r){return service.assign(r.proposalIds(),r.reviewerId(),r.actor(),r.reviewDeadline());}
  @PostMapping("/proposals/unassign")Map<String,Object>unassign(@Valid@RequestBody Unassignment r){return service.unassign(r.proposalIds(),r.actor());}
}
