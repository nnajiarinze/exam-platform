package se.medbo.examplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class OpenRouterPaidProvider implements StructuredAiProvider {
  private final ObjectMapper mapper;private final OpenRouterPaidModelDiscoveryService discovery;private final OpenRouterPaidBudgetService budget;
  private final HttpClient http;private final String key,base,referer,title;private final Duration timeout;private final int defaultMaximumOutputTokens;
  OpenRouterPaidProvider(ObjectMapper mapper,OpenRouterPaidModelDiscoveryService discovery,OpenRouterPaidBudgetService budget,
      @Value("${ai.openrouter.api-key:}")String key,@Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}")String base,
      @Value("${ai.openrouter.http-referer:}")String referer,@Value("${ai.openrouter.app-title:Svea Study}")String title,
      @Value("${ai.openrouter.timeout-seconds:45}")long timeoutSeconds,
      @Value("${ai.openrouter.paid-default-max-output-tokens:4096}")int defaultMaximumOutputTokens){
    URI uri=URI.create(base);if(!"https".equalsIgnoreCase(uri.getScheme())||!"openrouter.ai".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1)throw new IllegalArgumentException("Provider base URL must be the official HTTPS endpoint");
    this.mapper=mapper;this.discovery=discovery;this.budget=budget;this.key=key;this.base=base.replaceAll("/+$","");this.referer=referer;this.title=title;
    this.timeout=Duration.ofSeconds(Math.max(1,timeoutSeconds));this.defaultMaximumOutputTokens=Math.max(1,defaultMaximumOutputTokens);this.http=HttpClient.newBuilder().connectTimeout(timeout).build();
  }
  public String provider(){return "OPENROUTER_PAID";}public String model(){return discovery.current().map(OpenRouterPaidModelDiscoveryService.Model::id).orElse("DISCOVERY_PENDING");}
  public int priority(){return 4;}public boolean enabled(){return budget.allowed();}public boolean credentialsConfigured(){return !key.isBlank();}
  public Capabilities capabilities(){return new Capabilities(true,true,true,true,true,true,128_000,defaultMaximumOutputTokens,true,true,true);}
  public Availability availability(Request request){
    if(!enabled())return unavailable("DISABLED");if(!credentialsConfigured())return unavailable("MISSING_CREDENTIALS");
    try{var model=discovery.discoverAndPin();var state=budget.status(model,request==null?defaultMaximumOutputTokens:request.maximumOutputTokens());
      BigDecimal remaining=(BigDecimal)state.get("remainingUsd"),estimate=(BigDecimal)state.get("estimatedNextRequestUsd");
      if(remaining.compareTo(estimate)<0)return new Availability(false,"PAID_BUDGET_EXHAUSTED",FreeStatus.UNKNOWN,CapacityAuthority.LOCAL_ESTIMATE,"BILLING_SAFETY_PAUSED",null,state);
      return new Availability(true,"READY",FreeStatus.UNKNOWN,CapacityAuthority.PROVIDER_API,"CLOSED",null,state);
    }catch(AiProviderException e){return unavailable(e.code());}catch(Exception e){return unavailable("PROVIDER_UNHEALTHY");}
  }
  public Response execute(Request request){
    var model=discovery.discoverAndPin();var reservation=budget.reserve(request,model,"ALL_FREE_PROVIDERS_UNAVAILABLE");long start=System.nanoTime();
    HttpResponse<String> response=null;JsonNode usage=null;String requestId=null;Integer prompt=null,completion=null,reasoning=null;BigDecimal actual=BigDecimal.ZERO;
    try{
      response=http.send(build(request,model.id()),HttpResponse.BodyHandlers.ofString());requestId=response.headers().firstValue("x-request-id").orElse(null);
      if(response.statusCode()/100!=2)throw classify(response.statusCode());
      JsonNode root=mapper.readTree(response.body());usage=root.path("usage");requestId=requestId==null?root.path("id").asText(null):requestId;
      prompt=integer(usage,"prompt_tokens");completion=integer(usage,"completion_tokens");reasoning=integer(usage.path("completion_tokens_details"),"reasoning_tokens");actual=actualCost(usage,model,prompt,completion,reasoning);
      String content=root.path("choices").path(0).path("message").path("content").asText();JsonNode structured;
      try{structured=OpenAiCompatibleFreeProvider.normalizeStructuredResponse(mapper,content);}catch(Exception e){throw new AiProviderException("AI_PROVIDER_RESPONSE_INVALID",false,"OpenRouter paid returned invalid structured JSON");}
      long latency=(System.nanoTime()-start)/1_000_000;budget.reconcile(reservation,prompt,completion,reasoning,latency,actual,requestId,"SUCCEEDED",null);
      return new Response(structured,provider(),model.id(),root.path("model").asText(model.id()),requestId,prompt,completion,latency,
          root.path("choices").path(0).path("finish_reason").asText(null),Map.of(),Map.of(),null,"HTTP_"+response.statusCode(),false);
    }catch(AiProviderException e){budget.reconcile(reservation,prompt,completion,reasoning,(System.nanoTime()-start)/1_000_000,actual,requestId,"FAILED",e.code());throw e;
    }catch(java.net.http.HttpTimeoutException e){var error=new AiProviderException("AI_REQUEST_TIMEOUT",true,"OpenRouter paid request timed out");budget.reconcile(reservation,prompt,completion,reasoning,(System.nanoTime()-start)/1_000_000,actual,requestId,"FAILED",error.code());throw error;
    }catch(InterruptedException e){Thread.currentThread().interrupt();var error=new AiProviderException("AI_REQUEST_CANCELLED",false,"OpenRouter paid request was cancelled");budget.reconcile(reservation,prompt,completion,reasoning,(System.nanoTime()-start)/1_000_000,actual,requestId,"FAILED",error.code());throw error;
    }catch(Exception e){var error=new AiProviderException("AI_PROVIDER_RESPONSE_INVALID",false,"OpenRouter paid returned an invalid response");budget.reconcile(reservation,prompt,completion,reasoning,(System.nanoTime()-start)/1_000_000,actual,requestId,"FAILED",error.code());throw error;}
  }
  private HttpRequest build(Request r,String model)throws Exception{
    var format=Map.of("type","json_schema","json_schema",Map.of("name","editorial_response","strict",true,"schema",r.jsonSchema()));var body=new LinkedHashMap<String,Object>();body.put("model",model);body.put("messages",List.of(Map.of("role","system","content",r.systemInstruction()),Map.of("role","user","content",r.prompt())));body.put("response_format",format);body.put("provider",Map.of("require_parameters",true));body.put("temperature",r.temperature());body.put("max_tokens",r.maximumOutputTokens());
    var builder=HttpRequest.newBuilder(URI.create(base+"/chat/completions")).timeout(timeout).header("Authorization","Bearer "+key).header("Content-Type","application/json");if(!referer.isBlank())builder.header("HTTP-Referer",referer);if(!title.isBlank())builder.header("X-Title",title);return builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
  }
  private BigDecimal actualCost(JsonNode usage,OpenRouterPaidModelDiscoveryService.Model model,Integer prompt,Integer completion,Integer reasoning){
    if(usage.has("cost")&&!usage.path("cost").isNull())try{return new BigDecimal(usage.path("cost").asText());}catch(Exception ignored){}
    return model.promptPrice().multiply(BigDecimal.valueOf(prompt==null?0:prompt)).add(model.completionPrice().multiply(BigDecimal.valueOf(completion==null?0:completion))).add(model.reasoningPrice().multiply(BigDecimal.valueOf(reasoning==null?0:reasoning))).add(model.requestPrice());
  }
  private AiProviderException classify(int status){if(status==429)return new AiProviderException("AI_PROVIDER_TEMPORARILY_RATE_LIMITED",true,"OpenRouter paid is rate limited");if(status==401||status==403)return new AiProviderException("AI_PROVIDER_AUTHENTICATION_FAILED",false,"OpenRouter paid authentication failed");if(status==404)return new AiProviderException("AI_PROVIDER_MODEL_UNAVAILABLE",false,"OpenRouter paid model is unavailable");if(status==408||status>=500)return new AiProviderException("AI_PROVIDER_UNAVAILABLE",true,"OpenRouter paid is unavailable");return new AiProviderException("AI_PROVIDER_RESPONSE_INVALID",false,"OpenRouter paid rejected the request");}
  private Integer integer(JsonNode n,String key){return n.has(key)&&n.get(key).canConvertToInt()?n.get(key).asInt():null;}
  private Availability unavailable(String reason){return new Availability(false,reason,FreeStatus.UNKNOWN,CapacityAuthority.PROVIDER_API,"UNKNOWN",null,Map.of());}
}
