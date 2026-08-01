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
    var reserved=jdbc.sql("UPDATE ai_paid_budget SET reserved_usd=reserved_usd+:cost,updated_at=:now WHERE singleton=true AND configured_budget_usd-spent_usd-reserved_usd>=:cost RETURNING configured_budget_usd-spent_usd-reserved_usd+:cost")
        .param("cost",estimate).param("now",now()).query(BigDecimal.class).list();
    if(reserved.isEmpty())throw exhausted("The application paid budget cannot cover the maximum estimated request cost");BigDecimal before=reserved.getFirst();
    UUID id=UUID.randomUUID();OffsetDateTime now=now();
    jdbc.sql("INSERT INTO ai_paid_request_accounting(id,job_id,operation,provider,model,status,estimated_cost_usd,budget_before_usd,routing_reason,created_at) VALUES(:id,:job,:operation,'OPENROUTER_PAID',:model,'RESERVED',:cost,:before,:reason,:now)")
        .param("id",id).param("job",request.jobId(),Types.OTHER).param("operation",request.operation()).param("model",model.id())
        .param("cost",estimate).param("before",before).param("reason",reason).param("now",now).update();
    return new Reservation(id,estimate,before);
  }

  @Transactional
  public void reconcile(Reservation reservation,Integer promptTokens,Integer completionTokens,Integer reasoningTokens,
      long latencyMs,BigDecimal actualCost,String requestId,String status,String errorCode){
    var rows=jdbc.sql("SELECT status,estimated_cost_usd FROM ai_paid_request_accounting WHERE id=:id FOR UPDATE")
        .param("id",reservation.id()).query().listOfRows();if(rows.isEmpty()||!"RESERVED".equals(rows.getFirst().get("status")))return;
    BigDecimal estimated=decimal(rows.getFirst(),"estimated_cost_usd");BigDecimal actual=actualCost==null?BigDecimal.ZERO:actualCost.max(BigDecimal.ZERO).setScale(8,RoundingMode.CEILING);
    if(actual.compareTo(estimated)>0)throw new IllegalStateException("Provider actual cost exceeded the conservatively reserved maximum");
    var budget=lockedBudget();BigDecimal after=decimal(budget,"configured_budget_usd").subtract(decimal(budget,"spent_usd").add(actual)).subtract(decimal(budget,"reserved_usd").subtract(estimated));
    jdbc.sql("UPDATE ai_paid_budget SET reserved_usd=reserved_usd-:estimated,spent_usd=spent_usd+:actual,updated_at=:now WHERE singleton=true")
        .param("estimated",estimated).param("actual",actual).param("now",now()).update();
    jdbc.sql("UPDATE ai_paid_request_accounting SET status=:status,prompt_tokens=:prompt,completion_tokens=:completion,reasoning_tokens=:reasoning,latency_ms=:latency,actual_cost_usd=:actual,budget_after_usd=:after,provider_request_id=:request,error_code=:error,reconciled_at=:now WHERE id=:id")
        .param("status",status).param("prompt",promptTokens,Types.INTEGER).param("completion",completionTokens,Types.INTEGER)
        .param("reasoning",reasoningTokens,Types.INTEGER).param("latency",latencyMs).param("actual",actual).param("after",after)
        .param("request",requestId,Types.VARCHAR).param("error",errorCode,Types.VARCHAR).param("now",now()).param("id",reservation.id()).update();
  }

  public Map<String,Object> status(OpenRouterPaidModelDiscoveryService.Model model,int nextMaximumOutputTokens){
    ensureBudget();var rows=jdbc.sql("SELECT configured_budget_usd,spent_usd,reserved_usd,updated_at FROM ai_paid_budget WHERE singleton=true").query().listOfRows();
    var row=rows.getFirst();BigDecimal configured=decimal(row,"configured_budget_usd"),spent=decimal(row,"spent_usd"),reserved=decimal(row,"reserved_usd");
    var result=new LinkedHashMap<String,Object>();result.put("allowed",allowed);result.put("configuredBudgetUsd",configured);result.put("spentUsd",spent);
    result.put("reservedUsd",reserved);result.put("remainingUsd",configured.subtract(spent).subtract(reserved));
    result.put("estimatedNextRequestUsd",model==null?null:model.maximumCost(nextMaximumOutputTokens));result.put("updatedAt",row.get("updated_at"));return result;
  }

  public boolean allowed(){return allowed;}
  @Transactional void ensureBudget(){
    jdbc.sql("INSERT INTO ai_paid_budget(singleton,configured_budget_usd,spent_usd,reserved_usd,updated_at) VALUES(true,:budget,0,0,:now) ON CONFLICT(singleton) DO UPDATE SET configured_budget_usd=:budget,updated_at=:now WHERE ai_paid_budget.configured_budget_usd IS DISTINCT FROM :budget AND ai_paid_budget.spent_usd+ai_paid_budget.reserved_usd<=:budget")
        .param("budget",configuredBudget).param("now",now()).update();
  }
  private Map<String,Object> lockedBudget(){return jdbc.sql("SELECT configured_budget_usd,spent_usd,reserved_usd FROM ai_paid_budget WHERE singleton=true FOR UPDATE").query().singleRow();}
  private AiProviderException exhausted(String message){return new AiProviderException("PAID_BUDGET_EXHAUSTED",false,message);}
  private BigDecimal decimal(Map<String,Object> row,String key){return new BigDecimal(String.valueOf(row.get(key)));}
  private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
