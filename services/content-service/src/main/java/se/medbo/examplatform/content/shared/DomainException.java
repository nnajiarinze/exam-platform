package se.medbo.examplatform.content.shared;

import org.springframework.http.HttpStatus;

public final class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public DomainException(HttpStatus status, String code, String message) { super(message); this.status = status; this.code = code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public static DomainException notFound(String type) { return new DomainException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", type + " was not found"); }
    public static DomainException conflict(String message) { return new DomainException(HttpStatus.CONFLICT, "CONFLICT", message); }
}
