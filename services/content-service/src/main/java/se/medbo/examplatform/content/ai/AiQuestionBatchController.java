package se.medbo.examplatform.content.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/v1/admin/ai/question-generation-batches")
final class AiQuestionBatchController {
  record Scope(@NotBlank String type,UUID id,List<UUID> knowledgeFactIds){}
  record Configuration(@NotNull@Valid Scope scope,@NotBlank String language,@Positive@Max(3)int questionsPerKnowledgeFact,
      @NotEmpty List<String> questionTypes,@NotEmpty Map<String,Integer> difficultyDistribution,
      @NotEmpty Map<String,Integer> bloomDistribution,@Size(max=200)String idempotencyKey){}
  record Retry(List<UUID> itemIds){}
  record Assignment(@NotEmpty List<UUID> proposalIds,@NotBlank String reviewerId,OffsetDateTime reviewDeadline){}
  record Selection(@NotEmpty List<UUID> proposalIds){}
  record Rejection(@NotEmpty List<UUID> proposalIds,@NotBlank String reasonCode,String reviewerComment,@NotNull Map<UUID,Long> versions){}
  record Regeneration(@NotEmpty List<UUID> proposalIds,@NotBlank String reviewerFeedback,@NotNull Map<UUID,Long> versions,@NotBlank String idempotencyKey){}
  record Acceptance(@NotEmpty List<UUID> proposalIds,@NotNull Map<UUID,Long> versions){}
  private final AiQuestionBatchService service;
  AiQuestionBatchController(AiQuestionBatchService service){this.service=service;}
  private AiQuestionBatchService.Request request(Configuration r){return new AiQuestionBatchService.Request(new AiQuestionBatchService.Scope(r.scope().type(),r.scope().id(),r.scope().knowledgeFactIds()),r.language(),r.questionsPerKnowledgeFact(),r.questionTypes(),r.difficultyDistribution(),r.bloomDistribution(),r.idempotencyKey());}
  @PostMapping("/preview")Map<String,Object>preview(@Valid@RequestBody Configuration r){return service.preview(request(r));}
  @PostMapping@ResponseStatus(HttpStatus.ACCEPTED)Map<String,Object>create(@Valid@RequestBody Configuration r){return service.create(request(r));}
  @GetMapping Map<String,Object>list(@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size,@RequestParam(required=false)String status,@RequestParam(required=false)String scopeType,@RequestParam(required=false)String createdBy){return service.list(page,size,status,scopeType,createdBy);}
  @GetMapping("/{id}")Map<String,Object>get(@PathVariable UUID id){return service.get(id);}
  @GetMapping("/{id}/items")Map<String,Object>items(@PathVariable UUID id,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="50")@Min(1)@Max(100)int size,@RequestParam(required=false)String status,@RequestParam(required=false)UUID knowledgeFactId){return service.items(id,page,size,status,knowledgeFactId);}
  @GetMapping("/{id}/proposals")Map<String,Object>proposals(@PathVariable UUID id,@RequestParam(defaultValue="0")@Min(0)int page,@RequestParam(defaultValue="20")@Min(1)@Max(100)int size,@RequestParam(required=false)String status){return service.proposals(id,page,size,status);}
  @PostMapping("/{id}/cancel")Map<String,Object>cancel(@PathVariable UUID id){return service.cancel(id);}
  @PostMapping("/{id}/retry-failed")Map<String,Object>retry(@PathVariable UUID id,@RequestBody(required=false)Retry r){return service.retry(id,r==null?List.of():r.itemIds());}
  @PostMapping("/proposals/assign")Map<String,Object>assign(@Valid@RequestBody Assignment r){return service.assign(r.proposalIds(),r.reviewerId(),r.reviewDeadline());}
  @PostMapping("/proposals/unassign")Map<String,Object>unassign(@Valid@RequestBody Selection r){return service.unassign(r.proposalIds());}
  @PostMapping("/proposals/reject")Map<String,Object>reject(@Valid@RequestBody Rejection r){return service.bulkReject(r.proposalIds(),r.reasonCode(),r.reviewerComment(),r.versions());}
  @PostMapping("/proposals/regenerate")Map<String,Object>regenerate(@Valid@RequestBody Regeneration r){return service.bulkRegenerate(r.proposalIds(),r.reviewerFeedback(),r.versions(),r.idempotencyKey());}
  @PostMapping("/proposals/accept")Map<String,Object>accept(@Valid@RequestBody Acceptance r){return service.bulkAccept(r.proposalIds(),r.versions());}
  @GetMapping("/{id}/export")Map<String,Object>export(@PathVariable UUID id,@RequestParam(defaultValue="JSON")String format){return service.export(id,format);}
}
