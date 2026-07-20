package se.medbo.examplatform.content.reporting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import se.medbo.examplatform.content.shared.DomainException;

@Service
public class LearnerHealthClient {
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper mapper; private final String baseUrl; private final String key;
    public LearnerHealthClient(ObjectMapper mapper,@Value("${content.learning-service.base-url:http://localhost:8080}")String baseUrl,@Value("${content.learning-service.internal-api-key:}")String key){this.mapper=mapper;this.baseUrl=baseUrl.replaceAll("/$","");this.key=key;}
    public Map<String,Object> get(){if(key.isBlank())throw unavailable("Learning Service authentication is not configured");try{var request=HttpRequest.newBuilder(URI.create(baseUrl+"/internal/v1/reports/learner-health")).timeout(Duration.ofSeconds(5)).header("X-Internal-Api-Key",key).GET().build();var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw unavailable("Learning Service reporting is unavailable");return mapper.readValue(response.body(),new TypeReference<>(){});}catch(DomainException e){throw e;}catch(Exception e){throw unavailable("Learning Service reporting is unavailable");}}
    private DomainException unavailable(String message){return new DomainException(HttpStatus.SERVICE_UNAVAILABLE,"LEARNER_REPORT_UNAVAILABLE",message);}
}
