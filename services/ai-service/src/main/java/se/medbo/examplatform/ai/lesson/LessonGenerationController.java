package se.medbo.examplatform.ai.lesson;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/lesson-generation")
final class LessonGenerationController {
  private final LessonGenerationService service;
  LessonGenerationController(LessonGenerationService service){this.service=service;}
  record Fact(@NotNull UUID id,@NotNull UUID versionId,@NotBlank String text,@NotNull UUID sourceSectionId){
    LessonGenerationProviderClient.Fact input(){return new LessonGenerationProviderClient.Fact(id,versionId,text,sourceSectionId);}
  }
  record PlannedPage(@NotBlank String pageType,@NotBlank String title,@NotEmpty List<@NotNull UUID> knowledgeFactVersionIds,
      String learnerQuestion,String pagePurpose,List<String> exactSupportingEvidence,List<String> allowedConcepts,
      List<String> forbiddenConcepts,List<String> neighbouringPageTitles,String expectedTransition){
    LessonGenerationProviderClient.PlannedPage input(){return new LessonGenerationProviderClient.PlannedPage(pageType,title,
        knowledgeFactVersionIds,learnerQuestion,pagePurpose,exactSupportingEvidence,allowedConcepts,forbiddenConcepts,neighbouringPageTitles,expectedTransition);}
  }
  record Create(@NotNull UUID topicId,@NotBlank String topicTitle,@NotNull UUID learningObjectiveId,
      @NotBlank String learningObjectiveTitle,@NotNull UUID sourceSectionId,@NotBlank String sourceSectionTitle,
      @NotBlank String sourceSectionChecksum,@NotBlank String exactSourceText,@NotEmpty List<@Valid Fact> facts,
      @NotEmpty List<@Valid PlannedPage> plan,
      @NotBlank String language,@NotBlank String requestedBy,@NotBlank String idempotencyKey,
      String generationMode,UUID depthTopicPlanId){
    LessonGenerationService.Create input(){return new LessonGenerationService.Create(topicId,topicTitle,
        learningObjectiveId,learningObjectiveTitle,sourceSectionId,sourceSectionTitle,sourceSectionChecksum,
        exactSourceText,facts.stream().map(Fact::input).toList(),plan.stream().map(PlannedPage::input).toList(),
        language,requestedBy,idempotencyKey,generationMode,depthTopicPlanId);}
  }
  record Accept(@NotNull UUID lessonDraftId,@NotBlank String actor,long version){}
  @PostMapping("/jobs")@ResponseStatus(HttpStatus.ACCEPTED)Map<String,Object>create(@Valid@RequestBody Create request){return service.create(request.input());}
  @GetMapping("/jobs/{id}")Map<String,Object>get(@PathVariable UUID id){return service.get(id);}
  @GetMapping("/jobs/{id}/proposals")List<Map<String,Object>>proposals(@PathVariable UUID id){return service.proposals(id);}
  @PostMapping("/proposals/{id}/revalidate")Map<String,Object>revalidate(@PathVariable UUID id){return service.revalidate(id);}
  @PostMapping("/proposals/{id}/accepted")Map<String,Object>accept(@PathVariable UUID id,@Valid@RequestBody Accept request){return service.markAccepted(id,request.lessonDraftId(),request.actor(),request.version());}
}
