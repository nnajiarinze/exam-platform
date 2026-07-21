package se.medbo.examplatform.learning.learner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import org.springframework.web.bind.annotation.*;
import se.medbo.examplatform.learning.shared.LearnerIdentityResolver;

@RestController
@RequestMapping("/api/v1/me/settings")
public class LearnerSettingsController {
    private final LearnerIdentityResolver identity;private final LearnerSettingsService service;
    public LearnerSettingsController(LearnerIdentityResolver identity,LearnerSettingsService service){this.identity=identity;this.service=service;}
    @GetMapping public LearnerSettingsService.Settings get(@RequestHeader(value="X-Learner-Identity",required=false)String developmentIdentity){return service.get(identity.resolve(developmentIdentity));}
    @PutMapping public LearnerSettingsService.Settings update(@RequestHeader(value="X-Learner-Identity",required=false)String developmentIdentity,@Valid @RequestBody Request request){return service.update(identity.resolve(developmentIdentity),new LearnerSettingsService.Update(request.dailyQuestionGoal(),request.weeklyStudyDaysGoal(),request.studyReminderEnabled(),request.preferredReminderTime(),request.timezone(),request.progressSummaryEnabled(),request.achievementNotificationsEnabled(),request.version()));}
    public record Request(@Min(5)@Max(100)int dailyQuestionGoal,@Min(1)@Max(7)int weeklyStudyDaysGoal,boolean studyReminderEnabled,@NotNull LocalTime preferredReminderTime,@NotBlank@Size(max=64)String timezone,boolean progressSummaryEnabled,boolean achievementNotificationsEnabled,@Min(0)long version){}
}
