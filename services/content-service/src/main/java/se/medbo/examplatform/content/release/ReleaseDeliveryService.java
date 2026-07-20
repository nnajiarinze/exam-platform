package se.medbo.examplatform.content.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import se.medbo.examplatform.content.shared.DomainException;

@Service
public class ReleaseDeliveryService {
    private final JdbcClient jdbc;private final ReleaseService releases;private final ObjectMapper mapper;private final HttpClient http=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();private final String baseUrl;private final String apiKey;
    public ReleaseDeliveryService(JdbcClient jdbc,ReleaseService releases,ObjectMapper mapper,@Value("${content.learning-service.base-url:http://localhost:8080}")String baseUrl,@Value("${content.learning-service.internal-api-key:}")String apiKey){this.jdbc=jdbc;this.releases=releases;this.mapper=mapper;this.baseUrl=baseUrl.replaceAll("/$","");this.apiKey=apiKey;}
    public Map<String,Object> deliver(UUID id){var release=releases.get(id);if("ACTIVE".equals(release.get("status"))||"DELIVERED".equals(release.get("status")))return release;if(!java.util.List.of("PUBLISHED","DELIVERY_FAILED").contains(release.get("status")))throw conflict("RELEASE_NOT_PUBLISHED","Only a published release can be delivered");if(apiKey.isBlank())throw unavailable("Learning Service authentication is not configured");String snapshot=jdbc.sql("SELECT snapshot_json::text FROM published_release_snapshot WHERE release_id=:id").param("id",id).query(String.class).optional().orElseThrow(()->conflict("RELEASE_NOT_PUBLISHED","Published snapshot is missing"));int number=jdbc.sql("SELECT coalesce(max(attempt_number),0)+1 FROM release_delivery_attempt WHERE release_id=:id").param("id",id).query(Integer.class).single();UUID attempt=UUID.randomUUID();var start=now();jdbc.sql("INSERT INTO release_delivery_attempt(id,release_id,attempt_number,status,started_at,target_service,checksum,requested_by) VALUES(:id,:release,:number,'PENDING',:now,'learning-service',:checksum,:actor)").param("id",attempt).param("release",id).param("number",number).param("now",start).param("checksum",release.get("checksum")).param("actor",actor()).update();try{var request=HttpRequest.newBuilder(URI.create(baseUrl+"/internal/v1/content-releases/import")).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type","application/json").header("X-Internal-Api-Key",apiKey).POST(HttpRequest.BodyPublishers.ofString(snapshot)).build();var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()>=200&&response.statusCode()<300){complete(attempt,"SUCCESS",response.statusCode(),null,null);return releases.markDelivered(id);}JsonNode error=safeJson(response.body());String code=error.path("code").asText("IMPORT_FAILED");String message=error.path("message").asText("Learning Service rejected the snapshot");complete(attempt,"FAILED",response.statusCode(),code,sanitize(message));releases.markDeliveryFailed(id);throw new DomainException(response.statusCode()==409?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE,"DELIVERY_FAILED",sanitize(message));}catch(DomainException e){throw e;}catch(Exception e){complete(attempt,"FAILED",null,"LEARNING_SERVICE_UNAVAILABLE","Learning Service could not be reached");releases.markDeliveryFailed(id);throw unavailable("Learning Service could not be reached");}}
    public Map<String,Object> activate(UUID id){var release=releases.get(id);if("ACTIVE".equals(release.get("status")))return release;if(!"DELIVERED".equals(release.get("status")))throw conflict("RELEASE_NOT_DELIVERED","Release must be delivered before activation");if(apiKey.isBlank())throw unavailable("Learning Service authentication is not configured");try{var request=HttpRequest.newBuilder(URI.create(baseUrl+"/internal/v1/content-releases/"+id+"/activate")).timeout(java.time.Duration.ofSeconds(15)).header("X-Internal-Api-Key",apiKey).POST(HttpRequest.BodyPublishers.noBody()).build();var response=http.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()>=200&&response.statusCode()<300)return releases.markActive(id);JsonNode error=safeJson(response.body());throw new DomainException(response.statusCode()==409?HttpStatus.CONFLICT:HttpStatus.SERVICE_UNAVAILABLE,"ACTIVATION_FAILED",sanitize(error.path("message").asText("Learning Service rejected activation")));}catch(DomainException e){throw e;}catch(Exception e){throw unavailable("Learning Service could not be reached");}}
    private void complete(UUID id,String status,Integer response,String code,String message){jdbc.sql("UPDATE release_delivery_attempt SET status=:status,completed_at=:now,response_code=:response,error_code=:code,error_message=:message WHERE id=:id").param("status",status).param("now",now()).param("response",response,java.sql.Types.INTEGER).param("code",code,java.sql.Types.VARCHAR).param("message",message,java.sql.Types.VARCHAR).param("id",id).update();}
    private JsonNode safeJson(String text){try{return mapper.readTree(text);}catch(Exception e){return mapper.createObjectNode();}}
    private String sanitize(String message){return message==null?"Downstream request failed":message.replaceAll("[\\r\\n]"," ").substring(0,Math.min(500,message.length()));}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    private DomainException conflict(String c,String m){return new DomainException(HttpStatus.CONFLICT,c,m);}
    private DomainException unavailable(String m){return new DomainException(HttpStatus.SERVICE_UNAVAILABLE,"LEARNING_SERVICE_UNAVAILABLE",m);}
}
