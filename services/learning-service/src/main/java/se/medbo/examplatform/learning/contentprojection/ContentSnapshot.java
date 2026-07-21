package se.medbo.examplatform.learning.contentprojection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record ContentSnapshot(
        @NotBlank @Size(max = 20) String schemaVersion,
        @NotBlank @Size(max = 200) String externalReleaseId,
        @NotBlank @Size(max = 200) String examId,
        @NotBlank @Size(max = 200) String examVersionId,
        @NotBlank @Size(max = 100) String releaseVersion,
        @NotBlank @Size(max = 20) String releaseStatus,
        @NotNull Instant publishedAt,
        @NotBlank @Pattern(regexp = "^[a-f0-9]{64}$") String checksum,
        @NotEmpty List<@Valid Subject> subjects) {

    public record Subject(@NotBlank @Size(max = 200) String id,
                          @NotBlank @Size(max = 500) String name, @PositiveOrZero int sortOrder,
                          @NotEmpty List<@Valid Topic> topics) {}

    public record Topic(@NotBlank @Size(max = 200) String id,
                        @NotBlank @Size(max = 500) String name, String description,
                        @PositiveOrZero int sortOrder, @NotEmpty List<@Valid Question> questions) {}

    public record Question(@NotBlank @Size(max = 200) String id,
                           @NotBlank @Size(max = 200) String versionId,
                           @NotBlank @Size(max = 200) String knowledgeFactId,
                           @NotBlank @Size(max = 30) String questionType,
                           @NotBlank String prompt, @NotBlank String explanation,
                           @NotBlank @Size(min = 2, max = 35) String language,
                           @Size(max = 50) String difficulty, boolean active,
                           List<@NotBlank @Size(max = 200) String> correctOptionIds,
                           @NotEmpty List<@Valid AnswerOption> answerOptions) {
        public Question(String id, String versionId, String knowledgeFactId, String questionType, String prompt,
                        String explanation, String language, String difficulty, boolean active,
                        List<AnswerOption> answerOptions) {
            this(id, versionId, knowledgeFactId, questionType, prompt, explanation, language, difficulty, active,
                    null, answerOptions);
        }
    }

    public record AnswerOption(@NotBlank @Size(max = 200) String id, @NotBlank String text, boolean correct,
                               String feedback, @PositiveOrZero int sortOrder) {
        public AnswerOption(String id, String text, boolean correct, int sortOrder) {
            this(id, text, correct, null, sortOrder);
        }
    }
}
