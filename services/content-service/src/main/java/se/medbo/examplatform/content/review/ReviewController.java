package se.medbo.examplatform.content.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
final class ReviewController {
    private final ReviewQueryService service;ReviewController(ReviewQueryService service){this.service=service;}
    record VersionRequest(@NotNull @PositiveOrZero Long version){}
    record AssignRequest(@NotNull @PositiveOrZero Long version,@NotBlank String assignedReviewerId){}
    record PriorityRequest(@NotNull @PositiveOrZero Long version,@NotBlank String priority){}
    record CommentRequest(@NotNull @PositiveOrZero Long version,@NotBlank String body){}
    @GetMapping Map<String,Object> queue(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)String contentType,@RequestParam(defaultValue="UNDER_REVIEW")String reviewStatus,@RequestParam(required=false)String lifecycleStatus,@RequestParam(required=false)UUID subjectId,@RequestParam(required=false)UUID topicId,@RequestParam(required=false)UUID learningObjectiveId,@RequestParam(required=false)String authorId,@RequestParam(required=false)String assignedReviewerId,@RequestParam(required=false)Boolean unassignedOnly,@RequestParam(required=false)Boolean assignedToMe,@RequestParam(required=false)LocalDate submittedFrom,@RequestParam(required=false)LocalDate submittedTo,@RequestParam(required=false)String priority,@RequestParam(required=false)String sort,@RequestParam(required=false)String direction,@RequestParam(required=false)Boolean hasImpactWarnings){return service.queue(page,size,search,contentType,reviewStatus,lifecycleStatus,subjectId,topicId,learningObjectiveId,authorId,assignedReviewerId,unassignedOnly,assignedToMe,submittedFrom,submittedTo,priority,sort,direction,hasImpactWarnings);}
    @GetMapping("/summary") Map<String,Object> summary(){return service.summary();}
    @GetMapping("/{id}") Map<String,Object> detail(@PathVariable UUID id){return service.detail(id);}
    @GetMapping("/{id}/history") List<Map<String,Object>> history(@PathVariable UUID id){return service.history(id);}
    @GetMapping("/{id}/comments") List<Map<String,Object>> comments(@PathVariable UUID id){return service.comments(id);}
    @PostMapping("/{id}/claim") Map<String,Object> claim(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.claim(id,r.version());}
    @PostMapping("/{id}/unclaim") Map<String,Object> unclaim(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.unclaim(id,r.version());}
    @PostMapping("/{id}/assign") Map<String,Object> assign(@PathVariable UUID id,@Valid @RequestBody AssignRequest r){return service.assign(id,r.version(),r.assignedReviewerId());}
    @PostMapping("/{id}/priority") Map<String,Object> priority(@PathVariable UUID id,@Valid @RequestBody PriorityRequest r){return service.priority(id,r.version(),r.priority());}
    @PostMapping("/{id}/comments") Map<String,Object> comment(@PathVariable UUID id,@Valid @RequestBody CommentRequest r){return service.comment(id,r.version(),r.body());}
}
