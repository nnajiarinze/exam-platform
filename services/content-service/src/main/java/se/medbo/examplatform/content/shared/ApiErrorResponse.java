package se.medbo.examplatform.content.shared;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(String code, String message, Instant timestamp, List<Object> errors) {
    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, Instant.now(), List.of());
    }
}
