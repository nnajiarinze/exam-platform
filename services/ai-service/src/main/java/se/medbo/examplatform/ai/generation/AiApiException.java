package se.medbo.examplatform.ai.generation;
import org.springframework.http.HttpStatus;
public final class AiApiException extends RuntimeException {private final HttpStatus status;private final String code;public AiApiException(HttpStatus status,String code,String message){super(message);this.status=status;this.code=code;}public HttpStatus status(){return status;}public String code(){return code;}}
