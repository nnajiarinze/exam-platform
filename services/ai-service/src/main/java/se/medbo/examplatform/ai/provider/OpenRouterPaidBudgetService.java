package se.medbo.examplatform.ai.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenRouterPaidBudgetService {
  public record Reservation(UUID id,BigDecimal estimatedCost,BigDecimal budgetBefore){}
  private final JdbcClient jdbc;private final boolean allowed;private final BigDecimal configuredBudget;

  OpenRouterPaidBudgetService(JdbcClient jdbc,
      @Value("${ai.openrouter.allow-paid:false}")boolean allowed,
      @Value("${ai.openrouter.budget-usd:0}")BigDecimal configuredBudget){
    this.jdbc=jdbc;this.allowed=allowed;this.configuredBudget=configuredBudget.setScale(8,RoundingMode.HALF_UP);
    if(allowed&&this.configuredBudget.signum()<=0)throw new IllegalStateException("OPENROUTER_BUDGET_USD must be positive when paid fallback is enabled");
  }

  @Transactional
  public Reservation reserve(StructuredAiProvider.Request request,OpenRouterPaidModelDiscoveryService.Model model,String reason){
    if(!allowed)throw exhausted("OpenRouter paid fallback is disabled");
    ensureBudget();BigDecimal estimate=model.maximumCost(request.maximumOutputTokens());
    var reserved=jdbc.sql("UPDATE ai_paid_budget SET reserved_usd=reserved_usd+:cost,updated_at=:now WHERE singleton=true AND configured_budget_usd-spent_usd-reserved_usd-unknown_exposure_usd>=:cost RETURNING configured_budget_usd-spent_usd-reserved_usd-unknown_exposure_usd+:cost")
        .param("cost",estimate).param("now",now()).query(BigDecimal.class).list();
    if(reserved.isEmpty())throw exhausted("The application paid budget cannot cover the maximum estimated request cost");BigDecimal before=reserved.getFirst();
    UUID id=UUID.randomUUID();OffsetDateTime now=now();
    jdbc.sql("INSERT INTO ai_paid_request_accounting(id,job_id,operation,provider,model,status,estimated_cost_usd,budget_before_usd,routing_reason,attempt_id,reservation_state,reconciliation_state,lease_expires_at,heartbeat_at,owner_worker_id,process_instance_id,request_payload_checksum,request_idempotency_key,created_at) VALUES(:id,:job,:operation,'OPENROUTER_PAID',:model,'RESERVED',:cost,:before,:reason,:attempt,'ACTIVE','NOT_REQUIRED',:lease,:now,:worker,:process,:checksum,:key,:now)")
        .param("id",id).param("job",request.jobId(),Types.OTHER).param("operation",request.operation()).param("model",model.id())
        .param("cost",estimate).param("before",before).param("reason",reason).param("attempt",request.providerAttemptId(),Types.OTHER)
        .param("lease",now.plusSeconds(90)).param("worker",Thread.currentThread().getName()).param("process",processId())
        .param("checksum",sha(request.systemInstruction()+"\n"+request.prompt())).param("key",request.idempotencyKey(),Types.VARCHAR).param("now",now).update();
    return new Reservation(id,estimate,before);
  }

  @Transactional
  public void reconcile(Reservation reservation,Integer promptTokens,Integer completionTokens,Integer reasoningTokens,
      long latencyMs,BigDecimal actualCost,String requestId,String status,String errorCode){
    var rows=jdbc.sql("SELECT status,reservation_state,estimated_cost_usd FROM ai_paid_request_accounting WHERE id=:id FOR UPDATE")
        .param("id",reservation.id()).query().listOfRows();if(rows.isEmpty()||!"RESERVED".equals(rows.getFirst().get("status")))return;
    BigDecimal estimated=decimal(rows.getFirst(),"estimated_cost_usd");BigDecimal actual=actualCost==null?BigDecimal.ZERO:actualCost.max(BigDecimal.ZERO).setScale(8,RoundingMode.CEILING);
    if(actual.compareTo(estimated)>0)throw new IllegalStateException("Provider actual cost exceeded the conservatively reserved maximum");
    var budget=lockedBudget();BigDecimal after=decimal(budget,"configured_budget_usd").subtract(decimal(budget,"spent_usd").add(actual)).subtract(decimal(budget,"reserved_usd").subtract(estimated));
    jdbc.sql("UPDATE ai_paid_budget SET reserved_usd=reserved_usd-:estimated,spent_usd=spent_usd+:actual,updated_at=:now WHERE singleton=true")
        .param("estimated",estimated).param("actual",actual).param("now",now()).update();
    jdbc.sql("UPDATE ai_paid_request_accounting SET status=:status,reservation_state=CASE WHEN :success THEN 'CONSUMED' ELSE 'RELEASED_CONFIRMED_FAILURE' END,reconciliation_state='NOT_REQUIRED',outcome_classification=CASE WHEN :success THEN 'PROVIDER_COMPLETED_SUCCESS' ELSE 'PROVIDER_COMPLETED_FAILURE' END,prompt_tokens=:prompt,completion_tokens=:completion,reasoning_tokens=:reasoning,latency_ms=:latency,actual_cost_usd=:actual,budget_after_usd=:after,provider_request_id=:request,error_code=:error,heartbeat_at=:now,reconciled_at=:now WHERE id=:id")
        .param("status",status).param("prompt",promptTokens,Types.INTEGER).param("completion",completionTokens,Types.INTEGER)
        .param("reasoning",reasoningTokens,Types.INTEGER).param("latency",latencyMs).param("actual",actual).param("after",after)
        .param("request",requestId,Types.VARCHAR).param("error",errorCode,Types.VARCHAR).param("success","SUCCEEDED".equals(status)).param("now",now()).param("id",reservation.id()).update();
  }

  @Transactional public void markUnknown(Reservation reservation,long latencyMs,String requestId,String errorCode){
    var rows=jdbc.sql("SELECT status,estimated_cost_usd FROM ai_paid_request_accounting WHERE id=:id FOR UPDATE").param("id",reservation.id()).query().listOfRows();
    if(rows.isEmpty()||!"RESERVED".equals(rows.getFirst().get("status")))return;BigDecimal estimate=decimal(rows.getFirst(),"estimated_cost_usd");
    var budget=lockedBudget();BigDecimal after=decimal(budget,"configured_budget_usd").subtract(decimal(budget,"spent_usd")).subtract(decimal(budget,"unknown_exposure_usd").add(estimate)).subtract(decimal(budget,"reserved_usd").subtract(estimate));
    jdbc.sql("UPDATE ai_paid_budget SET reserved_usd=reserved_usd-:cost,unknown_exposure_usd=unknown_exposure_usd+:cost,updated_at=:now WHERE singleton=true").param("cost",estimate).param("now",now()).update();
    jdbc.sql("UPDATE ai_paid_request_accounting SET status='RECONCILIATION_PENDING',reservation_state='EXPIRED_UNKNOWN',reconciliation_state='UNKNOWN',outcome_classification='OUTCOME_UNKNOWN',latency_ms=:latency,budget_after_usd=:after,provider_request_id=:request,error_code=:error,heartbeat_at=:now,reconciled_at=:now WHERE id=:id")
        .param("latency",latencyMs).param("after",after).param("request",requestId,Types.VARCHAR).param("error",errorCode).param("now",now()).param("id",reservation.id()).update();
  }

  @Transactional public void recordResponseHeaders(Reservation reservation,String requestId){
    var now=now();jdbc.sql("UPDATE ai_paid_request_accounting SET provider_request_id=coalesce(:request,provider_request_id),heartbeat_at=:now WHERE id=:id AND status='RESERVED'")
        .param("request",requestId,Types.VARCHAR).param("now",now).param("id",reservation.id()).update();
    jdbc.sql("UPDATE ai_provider_attempt SET provider_request_id=coalesce(:request,provider_request_id),lifecycle_state='RESPONSE_RECEIVED',response_headers_received_at=:now,heartbeat_at=:now WHERE id=(SELECT attempt_id FROM ai_paid_request_accounting WHERE id=:reservation)")
        .param("request",requestId,Types.VARCHAR).param("now",now).param("reservation",reservation.id()).update();
  }

  @Transactional public void reconcileUnknownCharged(UUID accountingId,BigDecimal actualCost,Integer promptTokens,Integer completionTokens,Integer reasoningTokens,String finishReason){
    var rows=jdbc.sql("SELECT estimated_cost_usd,attempt_id,job_id FROM ai_paid_request_accounting WHERE id=:id AND status='RECONCILIATION_PENDING' AND reconciliation_state='UNKNOWN' FOR UPDATE")
        .param("id",accountingId).query().listOfRows();if(rows.isEmpty())return;var row=rows.getFirst();BigDecimal estimate=decimal(row,"estimated_cost_usd");BigDecimal actual=actualCost.max(BigDecimal.ZERO).setScale(8,RoundingMode.CEILING);
    if(actual.compareTo(estimate)>0)throw new IllegalStateException("Authoritative provider cost exceeded reserved maximum");var budget=lockedBudget();BigDecimal after=decimal(budget,"configured_budget_usd").subtract(decimal(budget,"spent_usd").add(actual)).subtract(decimal(budget,"reserved_usd")).subtract(decimal(budget,"unknown_exposure_usd").subtract(estimate));
    jdbc.sql("UPDATE ai_paid_budget SET unknown_exposure_usd=unknown_exposure_usd-:estimate,spent_usd=spent_usd+:actual,updated_at=:now WHERE singleton=true").param("estimate",estimate).param("actual",actual).param("now",now()).update();
    jdbc.sql("UPDATE ai_paid_request_accounting SET status='RECONCILED_SUCCESS',reservation_state='RECONCILED_CHARGED',reconciliation_state='SUCCEEDED',outcome_classification='PROVIDER_COMPLETED_SUCCESS',prompt_tokens=:prompt,completion_tokens=:completion,reasoning_tokens=:reasoning,actual_cost_usd=:actual,budget_after_usd=:after,error_code=NULL,reconciled_at=:now,heartbeat_at=:now WHERE id=:id")
        .param("prompt",promptTokens,Types.INTEGER).param("completion",completionTokens,Types.INTEGER).param("reasoning",reasoningTokens,Types.INTEGER).param("actual",actual).param("after",after).param("now",now()).param("id",accountingId).update();
    Object attempt=row.get("attempt_id");if(attempt!=null)jdbc.sql("UPDATE ai_provider_attempt SET status='SUCCEEDED',lifecycle_state='RECONCILED_SUCCESS',outcome_classification='PROVIDER_COMPLETED_SUCCESS',input_tokens=:prompt,output_tokens=:completion,finish_reason=:finish,heartbeat_at=:now,completed_at=:now WHERE id=:id")
        .param("prompt",promptTokens,Types.INTEGER).param("completion",completionTokens,Types.INTEGER).param("finish",finishReason,Types.VARCHAR).param("now",now()).param("id",attempt).update();
  }

  public Map<String,Object> status(OpenRouterPaidModelDiscoveryService.Model model,int nextMaximumOutputTokens){
    ensureBudget();var rows=jdbc.sql("SELECT configured_budget_usd,spent_usd,reserved_usd,unknown_exposure_usd,updated_at FROM ai_paid_budget WHERE singleton=true").query().listOfRows();
    var row=rows.getFirst();BigDecimal configured=decimal(row,"configured_budget_usd"),spent=decimal(row,"spent_usd"),reserved=decimal(row,"reserved_usd"),unknown=decimal(row,"unknown_exposure_usd");
    var result=new LinkedHashMap<String,Object>();result.put("allowed",allowed);result.put("configuredBudgetUsd",configured);result.put("spentUsd",spent);
    result.put("reservedUsd",reserved);result.put("unknownExposureUsd",unknown);result.put("remainingUsd",configured.subtract(spent).subtract(reserved).subtract(unknown));
    result.put("estimatedNextRequestUsd",model==null?null:model.maximumCost(nextMaximumOutputTokens));result.put("updatedAt",row.get("updated_at"));return result;
  }

  public boolean allowed(){return allowed;}
  @Transactional void ensureBudget(){
    jdbc.sql("INSERT INTO ai_paid_budget(singleton,configured_budget_usd,spent_usd,reserved_usd,unknown_exposure_usd,updated_at) VALUES(true,:budget,0,0,0,:now) ON CONFLICT(singleton) DO UPDATE SET configured_budget_usd=:budget,updated_at=:now WHERE ai_paid_budget.configured_budget_usd IS DISTINCT FROM :budget AND ai_paid_budget.spent_usd+ai_paid_budget.reserved_usd+ai_paid_budget.unknown_exposure_usd<=:budget")
        .param("budget",configuredBudget).param("now",now()).update();
  }
  private Map<String,Object> lockedBudget(){return jdbc.sql("SELECT configured_budget_usd,spent_usd,reserved_usd,unknown_exposure_usd FROM ai_paid_budget WHERE singleton=true FOR UPDATE").query().singleRow();}
  private AiProviderException exhausted(String message){return new AiProviderException("PAID_BUDGET_EXHAUSTED",false,message);}
  private BigDecimal decimal(Map<String,Object> row,String key){return new BigDecimal(String.valueOf(row.get(key)));}
  private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}private String processId(){return java.lang.management.ManagementFactory.getRuntimeMXBean().getName();}private String sha(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
