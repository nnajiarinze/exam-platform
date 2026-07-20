package se.medbo.examplatform.content.shared;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;

@RestControllerAdvice
final class ApiErrorHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiErrorResponse> domain(DomainException exception) {
        return ResponseEntity.status(exception.status()).body(ApiErrorResponse.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> (Object) new FieldError(error.getField(), error.getDefaultMessage())).toList();
        return ResponseEntity.unprocessableEntity().body(new ApiErrorResponse("VALIDATION_ERROR", "Request validation failed", java.time.Instant.now(), errors));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> constraint(DataIntegrityViolationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiErrorResponse.of("CONFLICT", "The requested value conflicts with existing content"));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "The Content Service encountered an unexpected error"));
    }
    record FieldError(String field, String message) {}
}
