package se.medbo.examplatform.learning.mockexam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@RestController
@RequestMapping("/api/v1/mock-exams")
public class MockExamController {
    private final LearnerIdentityResolver identityResolver;
    private final MockExamService service;

    public MockExamController(LearnerIdentityResolver identityResolver, MockExamService service) {
        this.identityResolver = identityResolver;
        this.service = service;
    }

    @PostMapping
    public MockExamService.AttemptView create(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @Valid @RequestBody CreateMockExamRequest request) {
        return service.create(identityResolver.resolve(identity), request.examId());
    }

    @GetMapping("/configuration")
    public MockExamService.ConfigurationView configuration(@RequestParam String examId) {
        return service.configuration(examId);
    }

    @GetMapping("/{attemptId}")
    public MockExamService.AttemptView get(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID attemptId) {
        return service.get(identityResolver.resolve(identity), attemptId);
    }

    @GetMapping("/{attemptId}/next")
    public MockExamService.QuestionView question(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID attemptId,
            @RequestParam(required = false) @Min(1) Integer sequenceNumber) {
        return service.question(identityResolver.resolve(identity), attemptId, sequenceNumber);
    }

    @PostMapping("/{attemptId}/responses")
    public MockExamService.AttemptProgress answer(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID attemptId, @Valid @RequestBody SubmitMockAnswerRequest request) {
        return service.answer(identityResolver.resolve(identity), attemptId, request.attemptQuestionId(),
                request.selectedAnswerOptionId(), request.version());
    }

    @PostMapping("/{attemptId}/questions/{attemptQuestionId}/flag")
    public MockExamService.AttemptProgress flag(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID attemptId, @PathVariable UUID attemptQuestionId,
            @Valid @RequestBody FlagMockQuestionRequest request) {
        return service.flag(identityResolver.resolve(identity), attemptId, attemptQuestionId, request.flagged(),
                request.version());
    }

    @PostMapping("/{attemptId}/submit")
    public MockExamService.ResultView submit(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID attemptId) {
        return service.submit(identityResolver.resolve(identity), attemptId);
    }

    @GetMapping("/history")
    public List<MockExamService.HistoryView> history(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity) {
        return service.history(identityResolver.resolve(identity));
    }

    @GetMapping("/{attemptId}/results")
    public MockExamService.ResultView results(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID attemptId) {
        return service.results(identityResolver.resolve(identity), attemptId);
    }

    public record CreateMockExamRequest(@NotBlank @Size(max = 200) String examId) {}
    public record SubmitMockAnswerRequest(@NotNull UUID attemptQuestionId,
                                          @NotBlank @Size(max = 200) String selectedAnswerOptionId,
                                          @Min(0) Long version) {}
    public record FlagMockQuestionRequest(@NotNull Boolean flagged, @Min(0) Long version) {}
}
