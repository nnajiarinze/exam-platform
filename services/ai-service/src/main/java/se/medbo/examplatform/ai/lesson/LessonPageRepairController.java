package se.medbo.examplatform.ai.lesson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/lesson-generation/proposals/{proposalId}/pages")
final class LessonPageRepairController {
  private final LessonPageRepairService service;
  LessonPageRepairController(LessonPageRepairService service){this.service=service;}
  record Action(@NotBlank String actor,String reason,String idempotencyKey){}
  @GetMapping Map<String,Object> pages(@PathVariable UUID proposalId){return service.inspect(proposalId);}
  @PostMapping("/{pageIndex}/validate") Map<String,Object> validate(@PathVariable UUID proposalId,@PathVariable int pageIndex,@Valid@RequestBody Action request){return service.validate(proposalId,pageIndex,request.actor());}
  @PostMapping("/{pageIndex}/reject") Map<String,Object> reject(@PathVariable UUID proposalId,@PathVariable int pageIndex,@Valid@RequestBody Action request){return service.reject(proposalId,pageIndex,request.actor(),request.reason());}
  @PostMapping("/{pageIndex}/repair") Map<String,Object> repair(@PathVariable UUID proposalId,@PathVariable int pageIndex,@Valid@RequestBody Action request){return service.repair(proposalId,pageIndex,request.actor(),request.reason(),request.idempotencyKey());}
}
