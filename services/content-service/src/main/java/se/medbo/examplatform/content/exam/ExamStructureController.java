package se.medbo.examplatform.content.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
final class ExamStructureController {
    private final ExamStructureService service;
    ExamStructureController(ExamStructureService service){this.service=service;}

    record ExamRequest(@NotBlank String code,@NotBlank String name,@NotBlank String countryCode,@NotBlank String status,Long version){ ExamStructureService.ExamInput input(){return new ExamStructureService.ExamInput(code,name,countryCode,status,version);} }
    record VersionRequest(@NotBlank String versionCode,@NotBlank String displayName,@NotBlank String status,LocalDate validFrom,LocalDate validTo,Long version){ExamStructureService.VersionInput input(){return new ExamStructureService.VersionInput(versionCode,displayName,status,validFrom,validTo,version);}}
    record NodeRequest(@NotBlank String code,@NotBlank String name,String description,@NotNull @PositiveOrZero Integer sortOrder,@NotBlank String status,Long version){ExamStructureService.NodeInput input(){return new ExamStructureService.NodeInput(code,name,description,sortOrder,status,version);}}
    record VersionOnly(@NotNull @PositiveOrZero Long version){}
    record OrderRequest(@NotNull List<UUID> ids){ExamStructureService.OrderInput input(){return new ExamStructureService.OrderInput(ids);}}

    @GetMapping("/exams") Map<String,Object> exams(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String search,@RequestParam(required=false) String status,@RequestParam(required=false) String countryCode,@RequestParam(required=false) String sort){return service.listExams(page,size,search,status,countryCode);}
    @PostMapping("/exams") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createExam(@Valid @RequestBody ExamRequest r){return service.createExam(r.input());}
    @GetMapping("/exams/{id}") Map<String,Object> exam(@PathVariable UUID id){return service.exam(id);}
    @PutMapping("/exams/{id}") Map<String,Object> updateExam(@PathVariable UUID id,@Valid @RequestBody ExamRequest r){return service.updateExam(id,r.input());}
    @PostMapping("/exams/{id}/archive") Map<String,Object> archiveExam(@PathVariable UUID id,@Valid @RequestBody VersionOnly r){return service.archiveExam(id,r.version());}

    @GetMapping("/exams/{id}/versions") List<Map<String,Object>> versions(@PathVariable UUID id,@RequestParam(required=false) String status,@RequestParam(required=false) LocalDate validAt,@RequestParam(required=false) String search){return service.versions(id,status,validAt,search);}
    @PostMapping("/exams/{id}/versions") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createVersion(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.createVersion(id,r.input());}
    @GetMapping("/exam-versions/{id}") Map<String,Object> version(@PathVariable UUID id){return service.examVersion(id);}
    @PutMapping("/exam-versions/{id}") Map<String,Object> updateVersion(@PathVariable UUID id,@Valid @RequestBody VersionRequest r){return service.updateVersion(id,r.input());}
    @PostMapping("/exam-versions/{id}/archive") Map<String,Object> archiveVersion(@PathVariable UUID id,@Valid @RequestBody VersionOnly r){return service.archiveVersion(id,r.version());}

    @GetMapping("/exam-versions/{id}/subjects") List<Map<String,Object>> subjects(@PathVariable UUID id){return service.subjects(id);}
    @PostMapping("/exam-versions/{id}/subjects") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createSubject(@PathVariable UUID id,@Valid @RequestBody NodeRequest r){return service.createSubject(id,r.input());}
    @GetMapping("/subjects/{id}") Map<String,Object> subject(@PathVariable UUID id){return service.subject(id);}
    @PutMapping("/subjects/{id}") Map<String,Object> updateSubject(@PathVariable UUID id,@Valid @RequestBody NodeRequest r){return service.updateSubject(id,r.input());}
    @PostMapping("/subjects/{id}/archive") Map<String,Object> archiveSubject(@PathVariable UUID id,@Valid @RequestBody VersionOnly r){return service.archiveSubject(id,r.version());}
    @PutMapping("/exam-versions/{id}/subjects/order") @ResponseStatus(HttpStatus.NO_CONTENT) void orderSubjects(@PathVariable UUID id,@Valid @RequestBody OrderRequest r){service.orderSubjects(id,r.input());}

    @GetMapping("/subjects/{id}/topics") List<Map<String,Object>> topics(@PathVariable UUID id){return service.topics(id);}
    @PostMapping("/subjects/{id}/topics") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> createTopic(@PathVariable UUID id,@Valid @RequestBody NodeRequest r){return service.createTopic(id,r.input());}
    @GetMapping("/topics/{id}") Map<String,Object> topic(@PathVariable UUID id){return service.topic(id);}
    @PutMapping("/topics/{id}") Map<String,Object> updateTopic(@PathVariable UUID id,@Valid @RequestBody NodeRequest r){return service.updateTopic(id,r.input());}
    @PostMapping("/topics/{id}/archive") Map<String,Object> archiveTopic(@PathVariable UUID id,@Valid @RequestBody VersionOnly r){return service.archiveTopic(id,r.version());}
    @PutMapping("/subjects/{id}/topics/order") @ResponseStatus(HttpStatus.NO_CONTENT) void orderTopics(@PathVariable UUID id,@Valid @RequestBody OrderRequest r){service.orderTopics(id,r.input());}
}
