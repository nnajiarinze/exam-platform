package se.medbo.examplatform.ai.provider;

import java.sql.Types;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import se.medbo.examplatform.ai.generation.AiApiException;

@Service
public class GeminiQuotaService {
  public enum UsageMode { FREE_ONLY, PAID_ALLOWED, DISABLED }
  public record Reservation(UUID id, UUID profileId) {}
  private final JdbcClient jdbc;
  private final MeterRegistry metrics;
  private final String provider, model, projectLabel, expectedTier;
  private final String apiKey;
  private final boolean featureEnabled;
  private final UsageMode mode;
  private final boolean paidEnabled;
  private final double spendLimit;
  private final String priceVersion;
  private final BigDecimal inputPrice,outputPrice;
  private final int maxOutputTokens;
  private final int rpm,tpm,rpd,warning,critical,stop;
  private final Long inputDay,outputDay;
  private final ZoneId quotaZone;

  GeminiQuotaService(JdbcClient jdbc,MeterRegistry metrics,
      @Value("${ai.editorial.provider:GEMINI}") String provider,
      @Value("${ai.editorial.model:gemini-3.1-flash-lite}") String model,
      @Value("${ai.gemini.project-label:}") String projectLabel,
      @Value("${ai.gemini.expected-billing-tier:}") String expectedTier,
      @Value("${ai.gemini.api-key:}") String apiKey,
      @Value("${ai.editorial.enabled:false}") boolean featureEnabled,
      @Value("${ai.usage-mode:FREE_ONLY}") UsageMode mode,
      @Value("${ai.paid-usage-enabled:false}") boolean paidEnabled,
      @Value("${ai.monthly-spend-limit-usd:0}") double spendLimit,
      @Value("${ai.gemini.price-profile-version:}") String priceVersion,
      @Value("${ai.gemini.input-price-per-million-usd:0}") BigDecimal inputPrice,
      @Value("${ai.gemini.output-price-per-million-usd:0}") BigDecimal outputPrice,
      @Value("${ai.gemini.max-output-tokens-per-request:4096}") int maxOutputTokens,
      @Value("${ai.gemini.internal-rpm-limit:0}") int rpm,
      @Value("${ai.gemini.internal-tpm-limit:0}") int tpm,
      @Value("${ai.gemini.internal-rpd-limit:0}") int rpd,
      @Value("${ai.gemini.internal-input-tokens-day-limit:0}") long inputDay,
      @Value("${ai.gemini.internal-output-tokens-day-limit:0}") long outputDay,
      @Value("${ai.gemini.warning-threshold-percent:70}") int warning,
      @Value("${ai.gemini.critical-threshold-percent:85}") int critical,
      @Value("${ai.gemini.stop-threshold-percent:95}") int stop,
      @Value("${ai.gemini.quota-timezone:America/Los_Angeles}") String quotaZone) {
    this.jdbc=jdbc;this.metrics=metrics;this.provider=provider.toUpperCase();this.model=model;this.projectLabel=projectLabel;
    this.expectedTier=expectedTier;this.apiKey=apiKey;this.featureEnabled=featureEnabled;this.mode=mode;this.paidEnabled=paidEnabled;this.spendLimit=spendLimit;
    this.priceVersion=priceVersion;this.inputPrice=inputPrice;this.outputPrice=outputPrice;this.maxOutputTokens=maxOutputTokens;
    this.rpm=rpm;this.tpm=tpm;this.rpd=rpd;this.inputDay=inputDay>0?inputDay:null;this.outputDay=outputDay>0?outputDay:null;
    this.warning=warning;this.critical=critical;this.stop=stop;this.quotaZone=ZoneId.of(quotaZone);
  }

  @Transactional(noRollbackFor=AiApiException.class)
  public Reservation reserve(int estimatedInputTokens) {
    return reserve(estimatedInputTokens,null,null,null,0);
  }
  @Transactional(noRollbackFor=AiApiException.class)
  public Reservation reserve(int estimatedInputTokens,UUID jobId,String operation,String requester,int retryAttempt) {
    validateConfiguration();
    jdbc.sql("SELECT pg_advisory_xact_lock(hashtext(:key))").param("key",provider+":"+model).query().singleRow();
    UUID profile=ensureProfile();
    var circuit=jdbc.sql("SELECT state,paused_until,manually_disabled FROM ai_provider_circuit WHERE profile_id=:id FOR UPDATE")
        .param("id",profile).query().singleRow();
    OffsetDateTime now=now();
    if(Boolean.TRUE.equals(circuit.get("manually_disabled"))) throw unavailable("AI_PROVIDER_DISABLED","Gemini is manually disabled");
    String state=String.valueOf(circuit.get("state"));
    OffsetDateTime paused=(OffsetDateTime)circuit.get("paused_until");
    if(!"CLOSED".equals(state)&&!"WARNING".equals(state)&&!"CRITICAL".equals(state)){
      if(paused!=null&&paused.isBefore(now)&&Set.of("RATE_LIMITED","QUOTA_PAUSED").contains(state)) close(profile,"AUTOMATIC_RECHECK");
      else throw unavailable("AI_FREE_QUOTA_PAUSED","Gemini calls are paused by the application safety circuit");
    }
    var usage=usage(profile,now);
    BigDecimal estimatedCost=mode==UsageMode.PAID_ALLOWED?cost(estimatedInputTokens,maxOutputTokens):null;
    if(estimatedCost!=null&&monthlySpend(profile,now).add(estimatedCost).compareTo(BigDecimal.valueOf(spendLimit))>=0){pauseBilling(profile,"AI_PAID_SPEND_LIMIT_REACHED");alert(profile,"paid-spend-"+monthKey(now),"CRITICAL","AI_PAID_SPEND_LIMIT_REACHED","The application monthly spend guard stopped Gemini usage");throw unavailable("AI_PAID_SPEND_LIMIT_REACHED","The application monthly spend limit would be exceeded");}
    double projected=max(
        ratio(usage.minuteRequests()+1,rpm),ratio(usage.minuteInput()+estimatedInputTokens,tpm),ratio(usage.dayRequests()+1,rpd),
        ratio(usage.dayInput()+estimatedInputTokens,inputDay),ratio(usage.dayOutput(),outputDay));
    if(projected*100>=stop){metrics.counter("ai.gemini.quota.reservations","result","rejected").increment();pause(profile,"AI_FREE_QUOTA_PAUSED",nextDailyReset(now));alert(profile,"quota-stop-"+dayKey(now),"CRITICAL","AI_FREE_QUOTA_PAUSED","Application-tracked usage reached the configured stop threshold");throw unavailable("AI_FREE_QUOTA_PAUSED","Application-tracked usage reached the configured stop threshold");}
    if(projected*100>=critical){metrics.counter("ai.gemini.quota.threshold","level","critical").increment();state(profile,"CRITICAL",null);alert(profile,"quota-critical-"+dayKey(now),"CRITICAL","AI_FREE_QUOTA_CRITICAL","Application-tracked usage reached the critical threshold");}
    else if(projected*100>=warning){metrics.counter("ai.gemini.quota.threshold","level","warning").increment();state(profile,"WARNING",null);alert(profile,"quota-warning-"+dayKey(now),"WARNING","AI_FREE_QUOTA_WARNING","Application-tracked usage reached the warning threshold");}
    UUID id=UUID.randomUUID();
    jdbc.sql("INSERT INTO ai_quota_reservation(id,profile_id,job_id,reserved_input_tokens,status,operation,requester,retry_attempt,estimated_cost_usd,created_at) VALUES(:id,:profile,:job,:tokens,'RESERVED',:operation,:requester,:retry,:cost,:now)")
        .param("id",id).param("profile",profile).param("job",jobId,Types.OTHER).param("tokens",estimatedInputTokens).param("operation",operation,Types.VARCHAR).param("requester",requester,Types.VARCHAR).param("retry",retryAttempt).param("cost",estimatedCost,Types.NUMERIC).param("now",now).update();
    metrics.counter("ai.gemini.quota.reservations","result","reserved").increment();return new Reservation(id,profile);
  }

  @Transactional public void success(Reservation r,Integer input,Integer output,String requestId){
    BigDecimal actual=mode==UsageMode.PAID_ALLOWED?cost(input==null?0:input,output==null?0:output):null;
    jdbc.sql("UPDATE ai_quota_reservation SET status='SUCCEEDED',actual_input_tokens=:input,actual_output_tokens=:output,actual_cost_usd=:cost,provider_request_id=:request,reconciled_at=:now WHERE id=:id AND status='RESERVED'")
        .param("input",input,Types.INTEGER).param("output",output,Types.INTEGER).param("cost",actual,Types.NUMERIC).param("request",requestId,Types.VARCHAR).param("now",now()).param("id",r.id()).update();
    jdbc.sql("UPDATE ai_provider_circuit SET last_successful_request=:now,last_provider_error=NULL,updated_at=:now,version=version+1 WHERE profile_id=:id").param("now",now()).param("id",r.profileId()).update();
  }
  @Transactional public void failure(Reservation r,String category,boolean release){
    jdbc.sql("UPDATE ai_quota_reservation SET status=:status,error_category=:category,reconciled_at=:now WHERE id=:id AND status='RESERVED'")
        .param("status",release?"RELEASED":"FAILED").param("category",category).param("now",now()).param("id",r.id()).update();
    jdbc.sql("UPDATE ai_provider_circuit SET last_failure_at=:now,last_provider_error=:category,updated_at=:now,version=version+1 WHERE profile_id=:id").param("now",now()).param("category",category).param("id",r.profileId()).update();
  }
  @Transactional public void rateLimited(Reservation r,String category,boolean daily){
    metrics.counter("ai.gemini.provider.429","category",category).increment();
    failure(r,category,false); OffsetDateTime until=daily?nextDailyReset(now()):now().plusMinutes(1);
    pause(r.profileId(),daily?"AI_PROVIDER_DAILY_QUOTA_EXHAUSTED":"AI_PROVIDER_TEMPORARILY_RATE_LIMITED",until);
    alert(r.profileId(),category+"-"+dayKey(now()),"CRITICAL",category,"Gemini returned a resource-exhausted response; provider calls were paused conservatively");
  }

  public Map<String,Object> status(){
    var result=new LinkedHashMap<String,Object>();result.put("provider",provider);result.put("model",model);result.put("usageMode",mode.name());result.put("featureEnabled",featureEnabled);
    result.put("projectLabel",projectLabel);result.put("expectedBillingTier",expectedTier);result.put("authoritativeQuota",false);result.put("usageLabel","Application-tracked usage");
    if(!"GEMINI".equals(provider)){result.put("usageMode","DISABLED");result.put("state","CLOSED");result.put("configurationValid",true);result.put("usage",Map.of());result.put("limits",Map.of());result.put("simulation",true);return result;}
    try{
      validateConfiguration();UUID profile=ensureProfile();
      var circuit=jdbc.sql("SELECT state,reason,paused_until AS \"pausedUntil\",manually_disabled AS \"manuallyDisabled\",last_successful_request AS \"lastSuccessfulRequest\",last_failure_at AS \"lastFailureAt\",last_provider_error AS \"lastProviderError\",updated_at AS \"updatedAt\" FROM ai_provider_circuit WHERE profile_id=:id").param("id",profile).query().singleRow();result.putAll(circuit);
      var u=usage(profile,now());var usageMap=new LinkedHashMap<String,Object>();usageMap.put("minuteRequests",u.minuteRequests());usageMap.put("minuteInputTokens",u.minuteInput());usageMap.put("dayRequests",u.dayRequests());usageMap.put("dayInputTokens",u.dayInput());usageMap.put("dayOutputTokens",u.dayOutput());if(mode==UsageMode.PAID_ALLOWED)usageMap.put("applicationTrackedMonthlySpendUsd",monthlySpend(profile,now()));result.put("usage",usageMap);result.put("limits",limits());
      result.put("queuedJobs",jdbc.sql("SELECT count(*) FROM ai_generation_job WHERE status='QUEUED'").query(Long.class).single());result.put("configurationValid",true);
    }
    catch(AiApiException e){result.put("state","CONFIGURATION_INVALID");result.put("configurationValid",false);result.put("configurationError",e.code());result.put("usage",Map.of());result.put("limits",limits());}
    return result;
  }
  public List<Map<String,Object>> alerts(){try{return jdbc.sql("SELECT id,severity,code,message,created_at AS \"createdAt\",acknowledged_at AS \"acknowledgedAt\",acknowledged_by AS \"acknowledgedBy\" FROM ai_provider_alert WHERE profile_id=:id ORDER BY created_at DESC").param("id",ensureProfile()).query().listOfRows();}catch(Exception e){return List.of();}}
  @Transactional public void acknowledge(UUID id,String actor){jdbc.sql("UPDATE ai_provider_alert SET acknowledged_at=:now,acknowledged_by=:actor WHERE id=:id AND acknowledged_at IS NULL").param("now",now()).param("actor",actor).param("id",id).update();}
  @Transactional public void disable(String actor){UUID p=ensureProfile();jdbc.sql("UPDATE ai_provider_circuit SET state='MANUALLY_DISABLED',reason='MANUAL_DISABLE',manually_disabled=true,paused_until=NULL,updated_at=:now,version=version+1 WHERE profile_id=:id").param("now",now()).param("id",p).update();audit(actor,"PROVIDER_MANUALLY_DISABLED",p);}
  @Transactional public void recheck(String actor){validateConfiguration();UUID p=ensureProfile();jdbc.sql("UPDATE ai_provider_circuit SET state='CLOSED',reason='MANUAL_RECHECK',manually_disabled=false,paused_until=NULL,updated_at=:now,version=version+1 WHERE profile_id=:id").param("now",now()).param("id",p).update();audit(actor,"PROVIDER_MANUALLY_RECHECKED",p);}

  private void validateConfiguration(){
    if(!featureEnabled)throw unavailable("AI_PROVIDER_DISABLED","AI editorial assistance is disabled");
    if(!"GEMINI".equals(provider))throw unavailable("AI_PROVIDER_DISABLED","Gemini is not the configured provider");
    if(apiKey.isBlank()||model.isBlank())throw unavailable("AI_GEMINI_NOT_CONFIGURED","Gemini credentials and model must be configured in AI Service");
    if(mode==UsageMode.DISABLED)throw unavailable("AI_PROVIDER_DISABLED","Gemini usage is disabled");
    if(mode==UsageMode.FREE_ONLY&&(!"FREE".equalsIgnoreCase(expectedTier)||projectLabel.isBlank()||rpm<=0||tpm<=0||rpd<=0))throw unavailable("AI_FREE_QUOTA_CONFIGURATION_REQUIRED","FREE_ONLY requires expected tier FREE, a project label, and positive RPM, TPM, and RPD limits");
    if(mode==UsageMode.PAID_ALLOWED&&(!paidEnabled||spendLimit<=0||priceVersion.isBlank()||inputPrice.signum()<0||outputPrice.signum()<=0||maxOutputTokens<=0))throw unavailable("AI_PAID_USAGE_NOT_ENABLED","Paid usage requires explicit enablement, a positive spending limit, and a valid versioned model price profile");
    if(!(warning>0&&warning<critical&&critical<stop&&stop<=100))throw unavailable("AI_FREE_QUOTA_CONFIGURATION_REQUIRED","Quota thresholds are invalid");
  }
  private UUID ensureProfile(){var ids=jdbc.sql("SELECT id FROM ai_quota_profile WHERE provider=:provider AND model=:model AND active=true").param("provider",provider).param("model",model).query(UUID.class).list();if(!ids.isEmpty()){UUID id=ids.getFirst();int changed=jdbc.sql("UPDATE ai_quota_profile SET project_label=:project,usage_mode=:mode,rpm_limit=:rpm,tpm_limit=:tpm,rpd_limit=:rpd,input_tokens_day_limit=:input,output_tokens_day_limit=:output,warning_percent=:warning,critical_percent=:critical,stop_percent=:stop,updated_at=:now,version=version+1 WHERE id=:id AND (project_label,usage_mode,rpm_limit,tpm_limit,rpd_limit,input_tokens_day_limit,output_tokens_day_limit,warning_percent,critical_percent,stop_percent) IS DISTINCT FROM (:project,:mode,:rpm,:tpm,:rpd,:input,:output,:warning,:critical,:stop)").param("project",projectLabel).param("mode",mode.name()).param("rpm",rpm).param("tpm",tpm).param("rpd",rpd).param("input",inputDay,Types.BIGINT).param("output",outputDay,Types.BIGINT).param("warning",warning).param("critical",critical).param("stop",stop).param("now",now()).param("id",id).update();if(changed>0)audit("system","QUOTA_PROFILE_CHANGED",id);ensurePriceProfile();return id;}UUID id=UUID.randomUUID();OffsetDateTime now=now();jdbc.sql("INSERT INTO ai_quota_profile(id,provider,model,project_label,usage_mode,rpm_limit,tpm_limit,rpd_limit,input_tokens_day_limit,output_tokens_day_limit,warning_percent,critical_percent,stop_percent,created_at,updated_at) VALUES(:id,:provider,:model,:project,:mode,:rpm,:tpm,:rpd,:input,:output,:warning,:critical,:stop,:now,:now)").param("id",id).param("provider",provider).param("model",model).param("project",projectLabel).param("mode",mode.name()).param("rpm",Math.max(1,rpm)).param("tpm",Math.max(1,tpm)).param("rpd",Math.max(1,rpd)).param("input",inputDay,Types.BIGINT).param("output",outputDay,Types.BIGINT).param("warning",warning).param("critical",critical).param("stop",stop).param("now",now).update();jdbc.sql("INSERT INTO ai_provider_circuit(profile_id,state,updated_at) VALUES(:id,'CLOSED',:now)").param("id",id).param("now",now).update();ensurePriceProfile();audit("system","GEMINI_PROVIDER_CONFIGURED",id);return id;}
  private void ensurePriceProfile(){if(mode!=UsageMode.PAID_ALLOWED)return;var existing=jdbc.sql("SELECT version FROM ai_model_price_profile WHERE provider=:provider AND model=:model AND active=true").param("provider",provider).param("model",model).query(String.class).list();if(!existing.isEmpty()&&existing.getFirst().equals(priceVersion))return;jdbc.sql("UPDATE ai_model_price_profile SET active=false WHERE provider=:provider AND model=:model AND active=true").param("provider",provider).param("model",model).update();OffsetDateTime n=now();jdbc.sql("INSERT INTO ai_model_price_profile(id,provider,model,version,input_usd_per_million,output_usd_per_million,effective_from,created_at) VALUES(:id,:provider,:model,:version,:input,:output,:now,:now)").param("id",UUID.randomUUID()).param("provider",provider).param("model",model).param("version",priceVersion).param("input",inputPrice).param("output",outputPrice).param("now",n).update();}
  private Usage usage(UUID p,OffsetDateTime now){OffsetDateTime minute=now.minusMinutes(1),day=now.atZoneSameInstant(quotaZone).toLocalDate().atStartOfDay(quotaZone).toOffsetDateTime();var r=jdbc.sql("SELECT count(*) FILTER(WHERE created_at>=:minute AND status<>'RELEASED') AS mr,coalesce(sum(coalesce(actual_input_tokens,reserved_input_tokens)) FILTER(WHERE created_at>=:minute AND status<>'RELEASED'),0) AS mt,count(*) FILTER(WHERE created_at>=:day AND status<>'RELEASED') AS dr,coalesce(sum(coalesce(actual_input_tokens,reserved_input_tokens)) FILTER(WHERE created_at>=:day AND status<>'RELEASED'),0) AS di,coalesce(sum(coalesce(actual_output_tokens,0)) FILTER(WHERE created_at>=:day AND status<>'RELEASED'),0) AS dout FROM ai_quota_reservation WHERE profile_id=:id").param("minute",minute).param("day",day).param("id",p).query().singleRow();return new Usage(num(r,"mr"),num(r,"mt"),num(r,"dr"),num(r,"di"),num(r,"dout"));}
  private record Usage(long minuteRequests,long minuteInput,long dayRequests,long dayInput,long dayOutput){}
  private long num(Map<String,Object> r,String k){return ((Number)r.get(k)).longValue();}private double ratio(long n,Number d){return d==null||d.longValue()<=0?0:n/(double)d.longValue();}private double max(double...v){return java.util.Arrays.stream(v).max().orElse(0);}private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}private OffsetDateTime nextDailyReset(OffsetDateTime now){return now.atZoneSameInstant(quotaZone).toLocalDate().plusDays(1).atStartOfDay(quotaZone).toOffsetDateTime();}private String dayKey(OffsetDateTime n){return n.atZoneSameInstant(quotaZone).toLocalDate().toString();}
  private Map<String,Object> limits(){var m=new LinkedHashMap<String,Object>();m.put("rpm",rpm);m.put("tpm",tpm);m.put("rpd",rpd);m.put("inputTokensPerDay",inputDay);m.put("outputTokensPerDay",outputDay);m.put("warningPercent",warning);m.put("criticalPercent",critical);m.put("stopPercent",stop);return m;}
  private BigDecimal cost(long input,long output){return inputPrice.multiply(BigDecimal.valueOf(input)).add(outputPrice.multiply(BigDecimal.valueOf(output))).divide(BigDecimal.valueOf(1_000_000),8,RoundingMode.UP);}
  private BigDecimal monthlySpend(UUID profile,OffsetDateTime now){OffsetDateTime start=now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();return jdbc.sql("SELECT coalesce(sum(coalesce(actual_cost_usd,estimated_cost_usd)),0) FROM ai_quota_reservation WHERE profile_id=:id AND created_at>=:start AND status<>'RELEASED'").param("id",profile).param("start",start).query(BigDecimal.class).single();}
  private String monthKey(OffsetDateTime n){return n.getYear()+"-"+n.getMonthValue();}
  private void pauseBilling(UUID p,String reason){jdbc.sql("UPDATE ai_provider_circuit SET state='BILLING_SAFETY_PAUSED',reason=:reason,paused_until=NULL,updated_at=:now,version=version+1 WHERE profile_id=:id").param("reason",reason).param("now",now()).param("id",p).update();}
  private void close(UUID p,String reason){state(p,"CLOSED",reason);}private void state(UUID p,String state,String reason){jdbc.sql("UPDATE ai_provider_circuit SET state=:state,reason=:reason,paused_until=NULL,updated_at=:now,version=version+1 WHERE profile_id=:id").param("state",state).param("reason",reason,Types.VARCHAR).param("now",now()).param("id",p).update();}private void pause(UUID p,String reason,OffsetDateTime until){jdbc.sql("UPDATE ai_provider_circuit SET state=:state,reason=:reason,paused_until=:until,updated_at=:now,version=version+1 WHERE profile_id=:id").param("state",reason.contains("DAILY")||reason.contains("QUOTA")?"QUOTA_PAUSED":"RATE_LIMITED").param("reason",reason).param("until",until).param("now",now()).param("id",p).update();}
  private void alert(UUID p,String key,String severity,String code,String message){jdbc.sql("INSERT INTO ai_provider_alert(id,profile_id,alert_key,severity,code,message,created_at) VALUES(:id,:profile,:key,:severity,:code,:message,:now) ON CONFLICT(profile_id,alert_key) DO NOTHING").param("id",UUID.randomUUID()).param("profile",p).param("key",key).param("severity",severity).param("code",code).param("message",message).param("now",now()).update();}
  private void audit(String actor,String action,UUID p){jdbc.sql("INSERT INTO ai_audit_event(id,occurred_at,actor_id,action,entity_type,entity_id,metadata) VALUES(:id,:now,:actor,:action,'AI_PROVIDER',:entity,jsonb_build_object('provider',:provider,'model',:model,'usageMode',:mode))").param("id",UUID.randomUUID()).param("now",now()).param("actor",actor).param("action",action).param("entity",p).param("provider",provider).param("model",model).param("mode",mode.name()).update();}
  private AiApiException unavailable(String code,String message){return new AiApiException(HttpStatus.SERVICE_UNAVAILABLE,code,message);}
}
