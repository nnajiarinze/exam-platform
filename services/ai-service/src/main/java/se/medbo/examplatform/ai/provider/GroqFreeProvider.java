package se.medbo.examplatform.ai.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class GroqFreeProvider extends OpenAiCompatibleFreeProvider {
  private final long requestLimit,tokenLimit;private final JdbcClient jdbc;private final java.util.concurrent.atomic.AtomicLong requestsUsed=new java.util.concurrent.atomic.AtomicLong(),tokensUsed=new java.util.concurrent.atomic.AtomicLong();private volatile boolean loaded;private volatile OffsetDateTime tokenResetAt;
  GroqFreeProvider(ObjectMapper mapper,JdbcClient jdbc,@Value("${ai.groq.api-key:}")String key,@Value("${ai.groq.model:}")String model,
      @Value("${ai.groq.base-url:https://api.groq.com/openai/v1}")String base,@Value("${ai.groq.enabled:false}")boolean enabled,
      @Value("${ai.groq.free-only:true}")boolean freeOnly,@Value("${ai.groq.timeout-seconds:45}")long timeout,
      @Value("${ai.groq.free-request-limit:0}")long requestLimit,@Value("${ai.groq.free-token-limit:0}")long tokenLimit){super(mapper,key,model,base,"api.groq.com",enabled,freeOnly,timeout);this.jdbc=jdbc;this.requestLimit=requestLimit;this.tokenLimit=tokenLimit;}
  public String provider(){return "GROQ";}public int priority(){return 2;}
  @Override boolean strictSchema(){return false;}
  public Availability availability(Request r){load();resetTokenWindow();if(!enabled)return unavailable("DISABLED",FreeStatus.UNKNOWN);if(!credentialsConfigured())return unavailable("MISSING_CREDENTIALS",FreeStatus.UNKNOWN);if(!freeOnly||requestLimit<=0||tokenLimit<=0)return unavailable("FREE_STATUS_UNVERIFIED",FreeStatus.UNKNOWN);long rr=Math.max(0,requestLimit-requestsUsed.get()),tr=Math.max(0,tokenLimit-tokensUsed.get());boolean ready=rr>0&&tr>(r==null?0:r.maximumOutputTokens());OffsetDateTime retry=rr==0?OffsetDateTime.now(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC):tokenResetAt;return new Availability(ready,ready?"READY":"FREE_QUOTA_EXHAUSTED",FreeStatus.KNOWN,CapacityAuthority.LOCAL_ESTIMATE,ready?"CLOSED":"QUOTA_PAUSED",ready?null:retry,Map.of("requestLimit",requestLimit,"requestsUsed",requestsUsed.get(),"requestsRemaining",rr,"tokenLimit",tokenLimit,"tokensUsed",tokensUsed.get(),"tokensRemaining",tr));}
  @Override void observed(Map<String,Object> limits,com.fasterxml.jackson.databind.JsonNode usage){requestsUsed.incrementAndGet();tokensUsed.addAndGet(usage.path("total_tokens").asLong(usage.path("prompt_tokens").asLong()+usage.path("completion_tokens").asLong()));Object rr=limits.get("requestsRemaining"),tr=limits.get("tokensRemaining");if(rr instanceof Number n)requestsUsed.set(Math.max(requestsUsed.get(),requestLimit-n.longValue()));if(tr instanceof Number n)tokensUsed.set(Math.max(tokensUsed.get(),tokenLimit-n.longValue()));tokenResetAt=OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(resetSeconds(limits.get("tokenReset")));}
  private synchronized void load(){if(loaded)return;loaded=true;var rows=jdbc.sql("SELECT requests_used,tokens_used,refreshed_at FROM ai_provider_capacity_snapshot WHERE provider='GROQ' AND model=:model AND refreshed_at>=date_trunc('day',now() AT TIME ZONE 'UTC') ORDER BY refreshed_at DESC LIMIT 1").param("model",model).query().listOfRows();if(!rows.isEmpty()){Object r=rows.getFirst().get("requests_used");if(r instanceof Number n)requestsUsed.set(n.longValue());OffsetDateTime refreshed=offset(rows.getFirst().get("refreshed_at"));if(refreshed!=null&&refreshed.isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))){Object t=rows.getFirst().get("tokens_used");if(t instanceof Number n)tokensUsed.set(n.longValue());tokenResetAt=refreshed.plusMinutes(1);}}}
  private synchronized void resetTokenWindow(){if(tokenResetAt!=null&&!OffsetDateTime.now(ZoneOffset.UTC).isBefore(tokenResetAt)){tokensUsed.set(0);tokenResetAt=null;}}
  private long resetSeconds(Object value){if(value==null)return 60;var matcher=java.util.regex.Pattern.compile("(?:(\\d+)m)?(?:(\\d+(?:\\.\\d+)?)s)?").matcher(String.valueOf(value));if(!matcher.matches())return 60;long minutes=matcher.group(1)==null?0:Long.parseLong(matcher.group(1));double seconds=matcher.group(2)==null?0:Double.parseDouble(matcher.group(2));return Math.max(1,minutes*60+(long)Math.ceil(seconds));}
  private OffsetDateTime offset(Object value){if(value instanceof OffsetDateTime date)return date;if(value instanceof java.sql.Timestamp timestamp)return timestamp.toInstant().atOffset(ZoneOffset.UTC);return null;}
  Map<String,Object> rateLimits(HttpResponse<?> r){var m=new LinkedHashMap<String,Object>();copy(r,m,"x-ratelimit-limit-requests","requestLimit");copy(r,m,"x-ratelimit-remaining-requests","requestsRemaining");copy(r,m,"x-ratelimit-limit-tokens","tokenLimit");copy(r,m,"x-ratelimit-remaining-tokens","tokensRemaining");copy(r,m,"retry-after","retryAfterSeconds");r.headers().firstValue("x-ratelimit-reset-requests").ifPresent(v->m.put("requestReset",v));r.headers().firstValue("x-ratelimit-reset-tokens").ifPresent(v->m.put("tokenReset",v));return m;}
  private void copy(HttpResponse<?> r,Map<String,Object> m,String h,String k){r.headers().firstValue(h).ifPresent(v->m.put(k,number(v,0)));}private Availability unavailable(String reason,FreeStatus status){return new Availability(false,reason,status,CapacityAuthority.CONFIGURATION,"UNKNOWN",null,Map.of());}
}
