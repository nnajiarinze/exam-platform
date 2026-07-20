package se.medbo.examplatform.content.release;

import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import jakarta.validation.constraints.NotNull;import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;import java.util.Map;import java.util.UUID;
import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/releases")
final class ReleaseController {
    private final ReleaseService service;private final ReleaseDeliveryService delivery;
    ReleaseController(ReleaseService service,ReleaseDeliveryService delivery){this.service=service;this.delivery=delivery;}
    record ReleaseRequest(@NotNull UUID examVersionId,@NotBlank String releaseNumber,@NotBlank String displayName,String description,Long version){ReleaseService.ReleaseInput input(){return new ReleaseService.ReleaseInput(examVersionId,releaseNumber,displayName,description,version);}}
    record SelectionRequest(List<UUID> questionIds,List<UUID> factIds,@NotNull @PositiveOrZero Long version){ReleaseService.SelectionInput input(){return new ReleaseService.SelectionInput(questionIds,factIds,version);}}
    record VersionRequest(@NotNull @PositiveOrZero Long version){}
    @GetMapping Map<String,Object> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)UUID examId,@RequestParam(required=false)UUID examVersionId,@RequestParam(required=false)String status,@RequestParam(required=false)Boolean activeOnly){return service.list(page,size,search,examId,examVersionId,status,activeOnly);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Map<String,Object> create(@Valid @RequestBody ReleaseRequest r){return service.create(r.input());}
    @GetMapping("/{id}") Map<String,Object> get(@PathVariable UUID id){return service.get(id);}
    @PutMapping("/{id}") Map<String,Object> update(@PathVariable UUID id,@Valid @RequestBody ReleaseRequest r){return service.update(id,r.input());}
    @PutMapping("/{id}/selection") Map<String,Object> selection(@PathVariable UUID id,@Valid @RequestBody SelectionRequest r){return service.selection(id,r.input());}
    @GetMapping("/{id}/eligible-questions") Map<String,Object> eligibleQuestions(@PathVariable UUID id,@RequestParam(required=false)String search,@RequestParam(required=false)UUID subjectId,@RequestParam(required=false)UUID topicId,@RequestParam(required=false)UUID learningObjectiveId){return service.eligible(id,"QUESTION",search,subjectId,topicId,learningObjectiveId);}
    @GetMapping("/{id}/eligible-facts") Map<String,Object> eligibleFacts(@PathVariable UUID id,@RequestParam(required=false)String search,@RequestParam(required=false)UUID subjectId,@RequestParam(required=false)UUID topicId,@RequestParam(required=false)UUID learningObjectiveId){return service.eligible(id,"KNOWLEDGE_FACT",search,subjectId,topicId,learningObjectiveId);}
    @PostMapping("/{id}/validate") Map<String,Object> validate(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.validate(id,r.version());}
    @GetMapping("/{id}/preview") Map<String,Object> preview(@PathVariable UUID id){return service.preview(id);}
    @GetMapping("/{id}/snapshot") Map<String,Object> snapshot(@PathVariable UUID id){return service.snapshot(id);}
    @GetMapping("/{id}/diff") Map<String,Object> diff(@PathVariable UUID id){return service.diff(id);}
    @GetMapping("/{id}/delivery-attempts") List<Map<String,Object>> attempts(@PathVariable UUID id){return service.deliveryAttempts(id);}
    @PostMapping("/{id}/publish") Map<String,Object> publish(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.publish(id,r.version());}
    @PostMapping("/{id}/deliver") Map<String,Object> deliver(@PathVariable UUID id){return delivery.deliver(id);}
    @PostMapping("/{id}/retry-delivery") Map<String,Object> retry(@PathVariable UUID id){return delivery.deliver(id);}
    @PostMapping("/{id}/activate") Map<String,Object> activate(@PathVariable UUID id){return delivery.activate(id);}
    @PostMapping("/{id}/retire") Map<String,Object> retire(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.retire(id,r.version());}
}
