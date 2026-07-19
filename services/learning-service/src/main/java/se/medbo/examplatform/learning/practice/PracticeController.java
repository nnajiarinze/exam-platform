package se.medbo.examplatform.learning.practice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@RestController
@RequestMapping("/api/v1/practice-sessions")
public class PracticeController {
    private final LearnerIdentityResolver identityResolver;
    private final PracticeService service;

    public PracticeController(LearnerIdentityResolver identityResolver, PracticeService service) {
        this.identityResolver = identityResolver;
        this.service = service;
    }

    @PostMapping
    public PracticeService.SessionView create(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @Valid @RequestBody CreatePracticeSessionRequest request) {
        UUID learnerId = identityResolver.resolve(identity);
        return service.create(learnerId, new PracticeService.CreateSession(request.examId(), request.topicId(),
                request.mode(), request.questionCount()));
    }

    @GetMapping("/{sessionId}")
    public PracticeService.SessionView get(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID sessionId) {
        return service.getSession(identityResolver.resolve(identity), sessionId);
    }

    @GetMapping("/{sessionId}/next")
    public PracticeService.QuestionView next(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID sessionId) {
        return service.nextQuestion(identityResolver.resolve(identity), sessionId);
    }

    @PostMapping("/{sessionId}/responses")
    public PracticeService.AnswerResult answer(
            @RequestHeader(value = "X-Learner-Identity", required = false) String identity,
            @PathVariable UUID sessionId, @Valid @RequestBody SubmitAnswerRequest request) {
        return service.submit(identityResolver.resolve(identity), sessionId,
                new PracticeService.SubmitAnswer(request.sessionQuestionId(), request.selectedAnswerOptionId(),
                        request.responseTimeMillis()));
    }

    public record CreatePracticeSessionRequest(@NotBlank @Size(max = 200) String examId,
            @Size(max = 200) String topicId,
            @NotNull PracticeMode mode, @Min(1) @Max(50) int questionCount) {}
    public record SubmitAnswerRequest(@NotNull UUID sessionQuestionId,
            @NotBlank @Size(max = 200) String selectedAnswerOptionId,
            @PositiveOrZero Long responseTimeMillis) {}
}
