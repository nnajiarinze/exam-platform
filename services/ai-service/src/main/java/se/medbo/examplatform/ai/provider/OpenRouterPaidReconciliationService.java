package se.medbo.examplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Reconciles only attempts for which OpenRouter supplied an authoritative generation id. */
@Service
final class OpenRouterPaidReconciliationService {
  private final JdbcClient jdbc;private final ObjectMapper mapper;private final OpenRouterPaidBudgetService budget;
  private final HttpClient http;private final String key,base;private final Duration timeout;
  OpenRouterPaidReconciliationService(JdbcClient jdbc,ObjectMapper mapper,OpenRouterPaidBudgetService budget,
      @Value("${ai.openrouter.api-key:}")String key,@Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}")String base,
      @Value("${ai.openrouter.reconciliation-timeout-seconds:15}")long seconds){
    this.jdbc=jdbc;this.mapper=mapper;this.budget=budget;this.key=key;this.base=base.replaceAll("/+$","");this.timeout=Duration.ofSeconds(Math.max(1,seconds));this.http=HttpClient.newBuilder().connectTimeout(timeout).build();
  }
  @Scheduled(fixedDelayString="${ai.openrouter.reconciliation-interval-ms:60000}") void reconcile(){
    if(key.isBlank())return;var rows=jdbc.sql("SELECT id,provider_request_id FROM ai_paid_request_accounting WHERE status='RECONCILIATION_PENDING' AND reconciliation_state='UNKNOWN' AND provider_request_id IS NOT NULL ORDER BY created_at LIMIT 10").query().listOfRows();
    for(var row:rows)try{reconcile((UUID)row.get("id"),String.valueOf(row.get("provider_request_id")));}catch(Exception ignored){/* Unknown remains unavailable; the next bounded pass retries metadata only. */}
  }
  private void reconcile(UUID id,String generationId)throws Exception{
    URI uri=URI.create(base+"/generation?id="+URLEncoder.encode(generationId,StandardCharsets.UTF_8));var request=HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization","Bearer "+key).GET().build();
    var response=http.sendAsync(request,HttpResponse.BodyHandlers.ofString()).orTimeout(timeout.toMillis(),java.util.concurrent.TimeUnit.MILLISECONDS).join();if(response.statusCode()/100!=2)return;
    JsonNode data=mapper.readTree(response.body()).path("data");if(data.isMissingNode()||data.isNull())return;BigDecimal cost=decimal(data,"total_cost");if(cost==null)cost=decimal(data.path("usage"),"cost");if(cost==null)return;
    budget.reconcileUnknownCharged(id,cost,integer(data,"tokens_prompt"),integer(data,"tokens_completion"),integer(data,"native_tokens_reasoning"),data.path("finish_reason").asText(null));
  }
  private Integer integer(JsonNode node,String name){return node.has(name)&&node.path(name).canConvertToInt()?node.path(name).asInt():null;}
  private BigDecimal decimal(JsonNode node,String name){if(!node.has(name)||node.path(name).isNull())return null;try{return new BigDecimal(node.path(name).asText());}catch(Exception e){return null;}}
}
