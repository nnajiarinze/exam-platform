package se.medbo.examplatform.content.lesson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/v1/admin/lesson-drafts")
final class LessonDraftController {
  private final LessonDraftService service;
  LessonDraftController(LessonDraftService service){this.service=service;}

  record Section(@NotNull UUID sourceSectionId,@NotBlank String title,@NotBlank String explanation,
      List<String> keyTerms,List<String> supportedExamples,@NotEmpty List<UUID> knowledgeFactVersionIds,
      List<UUID> predecessorSectionIds,String mappingType){}
  record Create(@NotNull UUID topicId,@NotBlank String title,@NotBlank String introduction,@NotBlank String summary,
      List<String> importantPoints,@NotEmpty List<@Valid Section> sections,UUID supersedesLessonDraftId,
      UUID generationPlanId,String revisionReason,Boolean humanVerified){}
  record Decision(@PositiveOrZero long version,String note){}

  @PostMapping @ResponseStatus(HttpStatus.CREATED) Map<String,Object> create(@Valid @RequestBody Create request){return service.create(request);}
  @GetMapping List<Map<String,Object>> list(@RequestParam(required=false)String reviewStatus){return service.list(reviewStatus);}
  @GetMapping("/{id}") Map<String,Object> get(@PathVariable UUID id){return service.get(id);}
  @PostMapping("/{id}/submit") Map<String,Object> submit(@PathVariable UUID id,@Valid @RequestBody Decision request){return service.transition(id,request.version(),"UNDER_REVIEW",request.note());}
  @PostMapping("/{id}/review") Map<String,Object> review(@PathVariable UUID id,@Valid @RequestBody Decision request){return service.transition(id,request.version(),"REVIEWED",request.note());}
  @PostMapping("/{id}/require-update") Map<String,Object> requireUpdate(@PathVariable UUID id,@Valid @RequestBody Decision request){return service.transition(id,request.version(),"REQUIRES_UPDATE",request.note());}
  @PostMapping("/{id}/reject") Map<String,Object> reject(@PathVariable UUID id,@Valid @RequestBody Decision request){return service.transition(id,request.version(),"REJECTED",request.note());}
}
