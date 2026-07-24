package se.medbo.examplatform.learning.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@Validated
@RestController
@RequestMapping("/api/v1/learning")
public class StudyController {
    private final LearnerIdentityResolver identities;
    private final StudyService service;

    public StudyController(LearnerIdentityResolver identities, StudyService service) {
        this.identities = identities;
        this.service = service;
    }

    @GetMapping("/exams/{examId}/subjects")
    public List<StudyService.SubjectSummary> subjects(@RequestHeader(value="X-Learner-Identity",required=false) String identity,
            @PathVariable @NotBlank @Size(max=200) String examId) {
        return service.subjects(identities.resolve(identity), examId);
    }

    @GetMapping("/subjects/{subjectId}/topics")
    public List<StudyService.TopicSummary> topics(@RequestHeader(value="X-Learner-Identity",required=false) String identity,
            @PathVariable @NotBlank @Size(max=200) String subjectId,
            @RequestParam @NotBlank @Size(max=200) String examId) {
        return service.topics(identities.resolve(identity), examId, subjectId);
    }

    @GetMapping("/topics/{topicId}/lesson")
    public StudyService.Lesson lesson(@RequestHeader(value="X-Learner-Identity",required=false) String identity,
            @PathVariable @NotBlank @Size(max=200) String topicId,
            @RequestParam @NotBlank @Size(max=200) String examId) {
        return service.lesson(identities.resolve(identity), examId, topicId);
    }

    @PutMapping("/topics/{topicId}/progress")
    public StudyService.Progress progress(@RequestHeader(value="X-Learner-Identity",required=false) String identity,
            @PathVariable @NotBlank @Size(max=200) String topicId,
            @RequestParam @NotBlank @Size(max=200) String examId,
            @Valid @RequestBody StudyService.ProgressUpdate request) {
        return service.update(identities.resolve(identity), examId, topicId, request);
    }

    @GetMapping("/continue")
    public StudyService.ContinueLearning continueLearning(@RequestHeader(value="X-Learner-Identity",required=false) String identity,
            @RequestParam @NotBlank @Size(max=200) String examId) {
        return service.continueLearning(identities.resolve(identity), examId);
    }
}
