package se.medbo.examplatform.content.shared;

import org.springframework.http.HttpStatus;

public final class DomainException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final java.util.List<Object> errors;
    public DomainException(HttpStatus status, String code, String message) { this(status,code,message,java.util.List.of()); }
    public DomainException(HttpStatus status, String code, String message, java.util.List<Object> errors) { super(message); this.status = status; this.code = code; this.errors=java.util.List.copyOf(errors); }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public java.util.List<Object> errors() { return errors; }
    public static DomainException notFound(String type) { return new DomainException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", type + " was not found"); }
    public static DomainException conflict(String message) { return new DomainException(HttpStatus.CONFLICT, "CONFLICT", message); }
}
