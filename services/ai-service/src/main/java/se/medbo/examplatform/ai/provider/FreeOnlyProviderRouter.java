package se.medbo.examplatform.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class FreeOnlyProviderRouter {
  private final List<StructuredAiProvider> providers;private final JdbcClient jdbc;private final ObjectMapper mapper;
  private final List<String> configuredPriority;private final int maxAttempts;private final String billingPolicy;
  private final boolean allowPaidFallback,requireZeroCost,allowBillingUpgrade;
  public FreeOnlyProviderRouter(List<StructuredAiProvider> providers,JdbcClient jdbc,ObjectMapper mapper,
      @Value("${ai.routing.priority:GEMINI,GROQ,CLOUDFLARE_WORKERS_AI,OPENROUTER_FREE}")String priority,
      @Value("${ai.routing.max-provider-attempts:4}")int maxAttempts,
      @Value("${ai.billing-policy:FREE_ONLY}")String billingPolicy,
      @Value("${ai.allow-paid-fallback:false}")boolean allowPaidFallback,
      @Value("${ai.require-zero-cost-provider:true}")boolean requireZeroCost,
      @Value("${ai.allow-automatic-billing-upgrade:false}")boolean allowBillingUpgrade){
    this.providers=providers;this.jdbc=jdbc;this.mapper=mapper;this.configuredPriority=List.of(priority.split(","));
    this.maxAttempts=Math.max(1,Math.min(maxAttempts,providers.size()));this.billingPolicy=billingPolicy;
    this.allowPaidFallback=allowPaidFallback;this.requireZeroCost=requireZeroCost;this.allowBillingUpgrade=allowBillingUpgrade;
    if(!"FREE_ONLY".equals(billingPolicy)||allowPaidFallback||!requireZeroCost||allowBillingUpgrade)
      throw new IllegalStateException("AI routing requires FREE_ONLY, zero-cost providers, no paid fallback, and no automatic billing upgrade");
  }

  public StructuredAiProvider.Response execute(StructuredAiProvider.Request request){
    var evaluated=new ArrayList<Map<String,Object>>();int attempts=0;OffsetDateTime next=null;AiProviderException last=null;
    for(var provider:ordered()){
      String rejection=null;var availability=provider.availability(request);
      if(!provider.enabled())rejection="DISABLED";else if(!provider.credentialsConfigured())rejection="MISSING_CREDENTIALS";
      else if(!provider.supports(request))rejection="CAPABILITY_UNSUPPORTED";
      else if(availability.freeStatus()!=StructuredAiProvider.FreeStatus.KNOWN)rejection="FREE_STATUS_UNVERIFIED";
      else if(!availability.eligible())rejection=availability.reason();
      var decision=new LinkedHashMap<String,Object>();decision.put("provider",provider.provider());decision.put("model",provider.model());
      decision.put("freeStatus",availability.freeStatus().name());decision.put("reason",rejection==null?"ELIGIBLE":rejection);evaluated.add(decision);
      snapshot(provider,availability);
      if(availability.nextRetryAt()!=null&&(next==null||availability.nextRetryAt().isBefore(next)))next=availability.nextRetryAt();
      if(rejection!=null)continue;if(++attempts>maxAttempts)break;
      UUID attemptId=startAttempt(request,provider);long started=System.nanoTime();
      try{var response=provider.execute(request);if(!response.confirmedFree())throw new AiProviderException("AI_PROVIDER_FREE_STATUS_UNVERIFIED",false,"Provider response was not confirmed free");
        success(attemptId,response,(System.nanoTime()-started)/1_000_000);snapshot(provider,provider.availability(request));recordSelectedProvider(request.jobId(),provider);routing(request,evaluated,provider,"SELECTED",null);return response;
      }catch(AiProviderException error){last=error;failure(attemptId,error,(System.nanoTime()-started)/1_000_000);
        if(!provider.infrastructureFallbackCodes().contains(error.code())){routing(request,evaluated,null,"PAUSED",next);throw error;}
      }
    }
    routing(request,evaluated,null,"PAUSED",next);throw new AiProviderException("AI_ALL_FREE_PROVIDERS_UNAVAILABLE",true,
        next==null?"All confirmed-free providers are unavailable":"All confirmed-free providers are unavailable until "+next);
  }

  public List<Map<String,Object>> statuses(){var result=new ArrayList<Map<String,Object>>();for(var p:ordered()){var a=p.availability(null);var row=new LinkedHashMap<String,Object>();row.put("provider",p.provider());row.put("model",p.model());row.put("enabled",p.enabled());row.put("credentialConfigured",p.credentialsConfigured());row.put("billingPolicy",billingPolicy);row.put("confirmedFree",a.freeStatus()==StructuredAiProvider.FreeStatus.KNOWN);row.put("freeStatus",a.freeStatus());row.put("status",status(p,a));row.put("reason",a.reason());row.put("circuitState",a.circuitState());row.put("nextRetryAt",a.nextRetryAt());row.put("capacity",a.capacity());row.put("capabilities",p.capabilities());row.put("priority",configuredPriority.indexOf(p.provider())+1);row.putAll(attemptSummary(p));result.add(row);}return result;}
  public Map<String,Object> operations(){return Map.of("billingPolicy",billingPolicy,"allowPaidFallback",false,"requireZeroCostProvider",true,"allowAutomaticBillingUpgrade",false,"priority",configuredPriority,"providers",statuses(),"recentAttempts",jdbc.sql("SELECT id,job_id AS \"jobId\",operation,attempt_number AS \"attemptNumber\",provider,model,status,confirmed_free AS \"confirmedFree\",provider_request_id AS \"providerRequestId\",input_tokens AS \"inputTokens\",output_tokens AS \"outputTokens\",latency_ms AS \"latencyMs\",error_code AS \"errorCode\",fallback_reason AS \"fallbackReason\",retry_after AS \"retryAfter\",started_at AS \"startedAt\",completed_at AS \"completedAt\" FROM ai_provider_attempt ORDER BY started_at DESC LIMIT 50").query().listOfRows(),"recentRouting",jdbc.sql("SELECT id,job_id AS \"jobId\",operation,billing_policy AS \"billingPolicy\",providers_evaluated::text AS \"providersEvaluated\",selected_provider AS \"selectedProvider\",selected_model AS \"selectedModel\",outcome,next_retry_at AS \"nextRetryAt\",routed_at AS \"routedAt\" FROM ai_provider_routing_decision ORDER BY routed_at DESC LIMIT 50").query().listOfRows());}
  public Map<String,Object> recheck(){boolean ready=ordered().stream().anyMatch(provider->provider.enabled()&&provider.credentialsConfigured()&&provider.availability(null).eligible());int resumed=ready?jdbc.sql("UPDATE ai_generation_job SET next_attempt_at=:now,version=version+1 WHERE status='QUEUED' AND error_code='AI_ALL_FREE_PROVIDERS_UNAVAILABLE' AND next_attempt_at>:now").param("now",now()).update():0;return Map.of("ready",ready,"resumedJobs",resumed,"providers",statuses());}
  private List<StructuredAiProvider> ordered(){return providers.stream().sorted(Comparator.comparingInt(p->{int i=configuredPriority.indexOf(p.provider());return i<0?Integer.MAX_VALUE:i;})).toList();}
  private String status(StructuredAiProvider p,StructuredAiProvider.Availability a){if(!p.enabled())return "DISABLED";if(!p.credentialsConfigured())return "NOT_CONFIGURED";if(a.freeStatus()!=StructuredAiProvider.FreeStatus.KNOWN)return "UNKNOWN";if(a.eligible())return "READY";return switch(a.reason()){case "FREE_QUOTA_EXHAUSTED"->"QUOTA_EXHAUSTED";case "RATE_LIMITED"->"RATE_LIMITED";case "CIRCUIT_OPEN"->"CIRCUIT_OPEN";default->"MISCONFIGURED";};}
  private Map<String,Object> attemptSummary(StructuredAiProvider provider){return jdbc.sql("SELECT max(completed_at) FILTER (WHERE status='SUCCEEDED') AS \"lastSuccessfulRequest\",max(completed_at) FILTER (WHERE status='FAILED') AS \"lastFailure\",round(avg(latency_ms) FILTER (WHERE status='SUCCEEDED')) AS \"averageLatencyMs\" FROM ai_provider_attempt WHERE provider=:provider AND model=:model").param("provider",provider.provider()).param("model",provider.model()).query().singleRow();}
  private UUID startAttempt(StructuredAiProvider.Request r,StructuredAiProvider p){UUID id=UUID.randomUUID();Integer value=jdbc.sql("SELECT coalesce(max(attempt_number),0)+1 FROM ai_provider_attempt WHERE job_id IS NOT DISTINCT FROM :job AND operation=:op").param("job",r.jobId(),Types.OTHER).param("op",r.operation()).query(Integer.class).single();int n=value==null?1:value;jdbc.sql("INSERT INTO ai_provider_attempt(id,job_id,operation,attempt_number,provider,model,status,confirmed_free,free_verification_source,started_at) VALUES(:id,:job,:op,:n,:provider,:model,'STARTED',true,'CONFIGURATION',:now)").param("id",id).param("job",r.jobId(),Types.OTHER).param("op",r.operation()).param("n",n).param("provider",p.provider()).param("model",p.model()).param("now",now()).update();return id;}
  private void success(UUID id,StructuredAiProvider.Response r,long latency){jdbc.sql("UPDATE ai_provider_attempt SET status='SUCCEEDED',provider_request_id=:request,input_tokens=:input,output_tokens=:output,latency_ms=:latency,finish_reason=:finish,completed_at=:now WHERE id=:id").param("request",r.providerRequestId(),Types.VARCHAR).param("input",r.inputTokens(),Types.INTEGER).param("output",r.outputTokens(),Types.INTEGER).param("latency",latency).param("finish",r.finishReason(),Types.VARCHAR).param("now",now()).param("id",id).update();}
  private void failure(UUID id,AiProviderException e,long latency){jdbc.sql("UPDATE ai_provider_attempt SET status='FAILED',error_code=:code,error_message=:message,latency_ms=:latency,fallback_reason=:reason,response_diagnostics=CAST(:diagnostics AS jsonb),raw_response=:raw,completed_at=:now WHERE id=:id").param("code",e.code()).param("message",sanitize(e.getMessage())).param("latency",latency).param("reason",e.code()).param("diagnostics",json(e.diagnostics())).param("raw",e.rawResponse(),Types.VARCHAR).param("now",now()).param("id",id).update();}
  private void routing(StructuredAiProvider.Request r,List<Map<String,Object>> evaluated,StructuredAiProvider selected,String outcome,OffsetDateTime next){jdbc.sql("INSERT INTO ai_provider_routing_decision(id,job_id,operation,billing_policy,providers_evaluated,selected_provider,selected_model,outcome,next_retry_at,routed_at) VALUES(:id,:job,:op,'FREE_ONLY',CAST(:providers AS jsonb),:provider,:model,:outcome,:next,:now)").param("id",UUID.randomUUID()).param("job",r.jobId(),Types.OTHER).param("op",r.operation()).param("providers",json(evaluated)).param("provider",selected==null?null:selected.provider(),Types.VARCHAR).param("model",selected==null?null:selected.model(),Types.VARCHAR).param("outcome",outcome).param("next",next,Types.TIMESTAMP_WITH_TIMEZONE).param("now",now()).update();}
  private void snapshot(StructuredAiProvider p,StructuredAiProvider.Availability a){var c=a.capacity();jdbc.sql("INSERT INTO ai_provider_capacity_snapshot(id,provider,model,billing_policy,free_status,authority,request_limit,requests_used,requests_remaining,token_limit,tokens_used,tokens_remaining,neuron_limit,neurons_used,neurons_remaining,reset_at,retry_after,circuit_state,last_error,refreshed_at) VALUES(:id,:provider,:model,'FREE_ONLY',:free,:authority,:rl,:ru,:rr,:tl,:tu,:tr,:nl,:nu,:nr,:reset,:retry,:circuit,:error,:now)").param("id",UUID.randomUUID()).param("provider",p.provider()).param("model",p.model()).param("free",a.freeStatus().name()).param("authority",a.authority().name()).param("rl",number(c,"requestLimit"),Types.BIGINT).param("ru",number(c,"requestsUsed"),Types.BIGINT).param("rr",number(c,"requestsRemaining"),Types.BIGINT).param("tl",number(c,"tokenLimit"),Types.BIGINT).param("tu",number(c,"tokensUsed"),Types.BIGINT).param("tr",number(c,"tokensRemaining"),Types.BIGINT).param("nl",number(c,"neuronLimit"),Types.BIGINT).param("nu",number(c,"neuronsUsed"),Types.BIGINT).param("nr",number(c,"neuronsRemaining"),Types.BIGINT).param("reset",a.nextRetryAt(),Types.TIMESTAMP_WITH_TIMEZONE).param("retry",a.nextRetryAt(),Types.TIMESTAMP_WITH_TIMEZONE).param("circuit",a.circuitState()).param("error",a.reason(),Types.VARCHAR).param("now",now()).update();}
  private void recordSelectedProvider(UUID jobId,StructuredAiProvider provider){if(jobId==null)return;updateJobProvider("ai_generation_job",jobId,provider);updateJobProvider("ai_lesson_generation_job",jobId,provider);}
  private void updateJobProvider(String table,UUID jobId,StructuredAiProvider provider){jdbc.sql("UPDATE "+table+" SET provider=:provider,model=:model WHERE id=:id").param("provider",provider.provider()).param("model",provider.model()).param("id",jobId).update();}
  private Long number(Map<String,Object> map,String key){Object v=map==null?null:map.get(key);return v instanceof Number n?n.longValue():null;}private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}private String json(Object v){try{return mapper.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}private String sanitize(String v){return v==null?null:v.replaceAll("[\\r\\n]"," ").substring(0,Math.min(v.length(),500));}
}
