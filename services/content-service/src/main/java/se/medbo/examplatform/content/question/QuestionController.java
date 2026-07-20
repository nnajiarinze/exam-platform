package se.medbo.examplatform.content.question;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/questions")
final class QuestionController {
    private final QuestionService service;
    QuestionController(QuestionService service){this.service=service;}
    record OptionRequest(UUID id,@NotNull @PositiveOrZero Integer displayOrder,@NotBlank String text,boolean correct,String feedback){QuestionService.OptionInput input(){return new QuestionService.OptionInput(id,displayOrder,text,correct,feedback);}}
    record QuestionRequest(@NotNull UUID learningObjectiveId,@NotBlank String code,@NotBlank String questionType,@NotBlank String questionText,@NotBlank String difficulty,String explanation,@NotEmpty List<UUID> factIds,List<String> tags,List<@Valid OptionRequest> options,Boolean trueFalseCorrect,Long version){QuestionService.QuestionInput input(){return new QuestionService.QuestionInput(learningObjectiveId,code,questionType,questionText,difficulty,explanation,factIds,tags,options==null?List.of():options.stream().map(OptionRequest::input).toList(),trueFalseCorrect,version);}}
    record ActionRequest(@NotNull @PositiveOrZero Long version,String reason){QuestionService.ActionInput input(){return new QuestionService.ActionInput(version,reason);}}
    @GetMapping Map<String,Object> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)UUID learningObjectiveId,@RequestParam(required=false)String questionType,@RequestParam(required=false)String difficulty,@RequestParam(required=false)String reviewStatus,@RequestParam(required=false)String status){return service.questions(page,size,search,learningObjectiveId,questionType,difficulty,reviewStatus,status);}
    @GetMapping("/search") Map<String,Object> search(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)String search,@RequestParam(required=false)UUID learningObjectiveId,@RequestParam(required=false)String questionType,@RequestParam(required=false)String difficulty,@RequestParam(required=false)String reviewStatus,@RequestParam(required=false)String status){return service.questions(page,size,search,learningObjectiveId,questionType,difficulty,reviewStatus,status);}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) Map<String,Object> create(@Valid @RequestBody QuestionRequest r){return service.create(r.input());}
    @GetMapping("/{id}") Map<String,Object> get(@PathVariable UUID id){return service.question(id);}
    @PutMapping("/{id}") Map<String,Object> update(@PathVariable UUID id,@Valid @RequestBody QuestionRequest r){return service.update(id,r.input());}
    @GetMapping("/{id}/versions") List<Map<String,Object>> versions(@PathVariable UUID id){return service.versions(id);}
    @PostMapping("/{id}/submit") Map<String,Object> submit(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.submit(id,r.input());}
    @PostMapping("/{id}/approve") Map<String,Object> approve(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.approve(id,r.input());}
    @PostMapping("/{id}/reject") Map<String,Object> reject(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.reject(id,r.input());}
    @PostMapping("/{id}/require-update") Map<String,Object> requireUpdate(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.requireUpdate(id,r.input());}
    @PostMapping("/{id}/retire") Map<String,Object> retire(@PathVariable UUID id,@Valid @RequestBody ActionRequest r){return service.retire(id,r.input());}
}
