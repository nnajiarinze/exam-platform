package se.medbo.examplatform.content.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api/v1/admin")
final class AiQuestionGenerationController {
  private final AiQuestionGenerationService service;
  AiQuestionGenerationController(AiQuestionGenerationService service){this.service=service;}
  record Create(@Min(1)@Max(3)int proposalCount,String questionType,@Size(min=1,max=200)String idempotencyKey){}
  record Reject(@Size(max=500)String reason,@PositiveOrZero long version){}
  @PostMapping("/knowledge-facts/{factId}/ai-question-generation-jobs")@ResponseStatus(HttpStatus.ACCEPTED)Map<String,Object>create(@PathVariable UUID factId,@Valid@RequestBody Create request){return service.create(factId,request.proposalCount(),request.questionType(),request.idempotencyKey());}
  @GetMapping("/knowledge-facts/{factId}/ai-question-generation-eligibility")Map<String,Object>eligibility(@PathVariable UUID factId){return service.eligibility(factId);}
  @GetMapping("/knowledge-facts/{factId}/ai-question-generation-jobs")List<Map<String,Object>>history(@PathVariable UUID factId,@RequestParam(defaultValue="10")@Min(1)@Max(50)int limit){return service.history(factId,limit);}
  @GetMapping("/ai/question-generation-jobs/{jobId}")Map<String,Object>job(@PathVariable UUID jobId){return service.job(jobId);}
  @GetMapping("/ai/question-generation-jobs/{jobId}/proposals")List<Map<String,Object>>proposals(@PathVariable UUID jobId){return service.proposals(jobId);}
  @PostMapping("/ai/question-generation-jobs/{jobId}/cancel")Map<String,Object>cancel(@PathVariable UUID jobId){return service.cancel(jobId);}
  @PostMapping("/ai/question-proposals/{proposalId}/reject")Map<String,Object>reject(@PathVariable UUID proposalId,@Valid@RequestBody Reject request){return service.reject(proposalId,request.reason(),request.version());}
}
