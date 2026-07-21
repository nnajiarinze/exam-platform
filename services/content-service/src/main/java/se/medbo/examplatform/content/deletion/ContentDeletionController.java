package se.medbo.examplatform.content.deletion;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
final class ContentDeletionController {
    private final ContentDeletionService service;
    ContentDeletionController(ContentDeletionService service){this.service=service;}

    @DeleteMapping("/topics/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void topic(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteTopic(id,reason);}
    @DeleteMapping("/subjects/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void subject(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteSubject(id,reason);}
    @DeleteMapping("/exam-versions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void examVersion(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteExamVersion(id,reason);}
    @DeleteMapping("/exams/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void exam(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteExam(id,reason);}
    @DeleteMapping("/learning-objectives/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void objective(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteObjective(id,reason);}
    @DeleteMapping("/sources/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void source(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteSource(id,reason);}
    @DeleteMapping("/knowledge-facts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void fact(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteFact(id,reason);}
    @DeleteMapping("/questions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void question(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteQuestion(id,reason);}
    @DeleteMapping("/releases/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void release(@PathVariable UUID id,@RequestParam(required=false)String reason){service.deleteRelease(id,reason);}
}
