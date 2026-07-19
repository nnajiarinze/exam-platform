package se.medbo.examplatform.learning.contentprojection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;
import java.util.List;

public record ContentSnapshot(
        @NotBlank String schemaVersion,
        @NotBlank String externalReleaseId,
        @NotBlank String examId,
        @NotBlank String examVersionId,
        @NotBlank String releaseVersion,
        @NotBlank String releaseStatus,
        @NotNull Instant publishedAt,
        @NotBlank String checksum,
        @NotEmpty List<@Valid Subject> subjects) {

    public record Subject(@NotBlank String id, @NotBlank String name, @PositiveOrZero int sortOrder,
                          @NotEmpty List<@Valid Topic> topics) {}

    public record Topic(@NotBlank String id, @NotBlank String name, String description,
                        @PositiveOrZero int sortOrder, @NotEmpty List<@Valid Question> questions) {}

    public record Question(@NotBlank String id, @NotBlank String versionId,
                           @NotBlank String knowledgeFactId, @NotBlank String questionType,
                           @NotBlank String prompt, @NotBlank String explanation,
                           @NotBlank String language, String difficulty, boolean active,
                           @NotEmpty List<@Valid AnswerOption> answerOptions) {}

    public record AnswerOption(@NotBlank String id, @NotBlank String text, boolean correct,
                               @PositiveOrZero int sortOrder) {}
}

