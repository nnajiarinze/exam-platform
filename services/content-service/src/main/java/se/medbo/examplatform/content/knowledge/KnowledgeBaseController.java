package se.medbo.examplatform.content.knowledge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
final class KnowledgeBaseController {
    private final KnowledgeBaseService service;
    KnowledgeBaseController(KnowledgeBaseService service){this.service=service;}
    record ObjectiveRequest(@NotNull UUID topicId,@NotBlank String code,@NotBlank String title,String description,@NotBlank String status,Long version){KnowledgeBaseService.ObjectiveInput input(){return new KnowledgeBaseService.ObjectiveInput(topicId,code,title,description,status,version);}}
    record FactRequest(@NotNull UUID learningObjectiveId,@NotBlank String canonicalStatement,LocalDate validFrom,LocalDate validTo,@NotEmpty List<UUID> sourceIds,Long version){KnowledgeBaseService.FactInput input(){return new KnowledgeBaseService.FactInput(learningObjectiveId,canonicalStatement,validFrom,validTo,sourceIds,version);}}
    record ActionRequest(@NotNull @PositiveOrZero Long version,String reason,String reasonCode){KnowledgeBaseService.ActionInput input(){return new KnowledgeBaseService.ActionInput(version,reason,reasonCode);}}

    @GetMapping("/learning-objectives") Map<String,Object> objectives(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)UUID topicId,@RequestParam(required=false)String status){return service.objectives(page,size,search,topicId,status);}
    @PostMapping("/learning-objectives") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createObjective(@Valid @RequestBody ObjectiveRequest r){return service.createObjective(r.input());}
    @GetMapping("/learning-objectives/{id}") Map<String,Object> objective(@PathVariable UUID id){return service.objective(id);}
    @PutMapping("/learning-objectives/{id}") Map<String,Object> updateObjective(@PathVariable UUID id,@Valid @RequestBody ObjectiveRequest r){return service.updateObjective(id,r.input());}
    @PostMapping("/learning-objectives/{id}/archive") Map<String,Object> archiveObjective(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.archiveObjective(id,r.version());}

    @GetMapping("/knowledge-facts") Map<String,Object> facts(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)UUID learningObjectiveId,@RequestParam(required=false)UUID topicId,@RequestParam(required=false)UUID subjectId,@RequestParam(required=false)String sourcePublisher,@RequestParam(required=false)String reviewStatus,@RequestParam(required=false)String status,@RequestParam(required=false)LocalDate validAt){return service.facts(page,size,search,learningObjectiveId,topicId,subjectId,sourcePublisher,reviewStatus,status,validAt);}
    @PostMapping("/knowledge-facts") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createFact(@Valid @RequestBody FactRequest r){return service.createFact(r.input());}
    @GetMapping("/knowledge-facts/{id}") Map<String,Object> fact(@PathVariable UUID id){return service.fact(id);}
    @PutMapping("/knowledge-facts/{id}") Map<String,Object> updateFact(@PathVariable UUID id,@Valid @RequestBody FactRequest r){return service.updateFact(id,r.input());}
    @GetMapping("/knowledge-facts/{id}/versions") List<Map<String,Object>> versions(@PathVariable UUID id){return service.versions(id);}
    @PostMapping("/knowledge-facts/{id}/submit") Map<String,Object> submit(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.submit(id,r.input());}
    @PostMapping("/knowledge-facts/{id}/approve") Map<String,Object> approve(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.approve(id,r.input());}
    @PostMapping("/knowledge-facts/{id}/reject") Map<String,Object> reject(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.reject(id,r.input());}
    @PostMapping("/knowledge-facts/{id}/require-update") Map<String,Object> requireUpdate(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.requireUpdate(id,r.input());}
    @PostMapping("/knowledge-facts/{id}/retire") Map<String,Object> retire(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.retire(id,r.input());}
}
