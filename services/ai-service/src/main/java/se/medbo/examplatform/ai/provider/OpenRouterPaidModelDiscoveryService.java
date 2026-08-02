package se.medbo.examplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenRouterPaidModelDiscoveryService {
  public record Model(String id,BigDecimal promptPrice,BigDecimal completionPrice,
      BigDecimal reasoningPrice,BigDecimal requestPrice,long contextLength,List<String> parameters,
      OffsetDateTime discoveredAt) {
    BigDecimal maximumCost(int maximumOutputTokens){
      long output=Math.max(1,maximumOutputTokens);
      long input=Math.max(1,contextLength-output);
      return promptPrice.multiply(BigDecimal.valueOf(input))
          .add(completionPrice.add(reasoningPrice).multiply(BigDecimal.valueOf(output)))
          .add(requestPrice).setScale(8,java.math.RoundingMode.CEILING);
    }
  }

  private final JdbcClient jdbc;private final ObjectMapper mapper;private final HttpClient http;
  private final String key,base,preferredModel;private final boolean allowPaid;private final Duration timeout;

  OpenRouterPaidModelDiscoveryService(JdbcClient jdbc,ObjectMapper mapper,
      @Value("${ai.openrouter.api-key:}")String key,
      @Value("${ai.openrouter.base-url:https://openrouter.ai/api/v1}")String base,
      @Value("${ai.openrouter.allow-paid:false}")boolean allowPaid,
      @Value("${ai.openrouter.paid-model:}")String preferredModel,
      @Value("${ai.openrouter.timeout-seconds:45}")long timeoutSeconds){
    URI uri=URI.create(base);if(!"https".equalsIgnoreCase(uri.getScheme())||!"openrouter.ai".equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1)throw new IllegalArgumentException("Provider base URL must be the official HTTPS endpoint");
    this.jdbc=jdbc;this.mapper=mapper;this.key=key;this.base=base.replaceAll("/+$","");
    this.allowPaid=allowPaid;this.preferredModel=preferredModel;this.timeout=Duration.ofSeconds(Math.max(1,timeoutSeconds));
    this.http=HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  public Optional<Model> current(){
    var rows=jdbc.sql("SELECT model,prompt_usd_per_token,completion_usd_per_token,reasoning_usd_per_token,request_usd,context_length,supported_parameters::text,discovered_at FROM ai_openrouter_paid_model WHERE singleton=true")
        .query((rs,row)->{try{return new Model(rs.getString("model"),rs.getBigDecimal("prompt_usd_per_token"),
            rs.getBigDecimal("completion_usd_per_token"),rs.getBigDecimal("reasoning_usd_per_token"),
            rs.getBigDecimal("request_usd"),rs.getLong("context_length"),
            mapper.readValue(rs.getString("supported_parameters"),mapper.getTypeFactory().constructCollectionType(List.class,String.class)),
            rs.getObject("discovered_at",OffsetDateTime.class));}catch(Exception e){throw new IllegalStateException("Persisted OpenRouter model metadata is invalid",e);}}).list();
    return rows.stream().findFirst();
  }

  @Transactional
  public Model discoverAndPin(){
    var existing=current();
    if(existing.isPresent()&&(preferredModel.isBlank()||preferredModel.equals(existing.get().id())))return existing.get();
    if(!allowPaid)throw new AiProviderException("AI_PAID_USAGE_NOT_ENABLED",false,"OpenRouter paid fallback is disabled");
    if(key.isBlank())throw new AiProviderException("AI_PROVIDER_AUTHENTICATION_FAILED",false,"OpenRouter credentials are not configured");
    try{
      authenticate();
      var response=http.send(HttpRequest.newBuilder(URI.create(base+"/models")).timeout(timeout)
          .header("Authorization","Bearer "+key).GET().build(),HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()/100!=2)throw new AiProviderException("AI_PROVIDER_UNAVAILABLE",true,"OpenRouter model discovery is unavailable");
      JsonNode root=mapper.readTree(response.body());
      var candidates=StreamSupport.stream(root.path("data").spliterator(),false).map(this::candidate)
          .flatMap(Optional::stream).toList();
      if(candidates.isEmpty())throw new AiProviderException("AI_PROVIDER_MODEL_UNAVAILABLE",false,"No eligible paid OpenRouter editorial model was discovered");
      Model selected=candidates.stream().filter(m->!preferredModel.isBlank()&&m.id().equals(preferredModel)).findFirst()
          .orElseGet(()->candidates.stream().min(Comparator.comparing(this::blendedPrice)
              .thenComparing(Comparator.comparingLong(Model::contextLength).reversed())
              .thenComparing(Model::id)).orElseThrow());
      String parameters=mapper.writeValueAsString(selected.parameters());String fingerprint=sha256(selected.id()+"|"+selected.promptPrice()+"|"+selected.completionPrice()+"|"+parameters);
      jdbc.sql("INSERT INTO ai_openrouter_paid_model(singleton,model,prompt_usd_per_token,completion_usd_per_token,reasoning_usd_per_token,request_usd,context_length,supported_parameters,catalog_fingerprint,discovered_at) VALUES(true,:model,:prompt,:completion,:reasoning,:request,:context,CAST(:parameters AS jsonb),:fingerprint,:now) ON CONFLICT(singleton) DO UPDATE SET model=EXCLUDED.model,prompt_usd_per_token=EXCLUDED.prompt_usd_per_token,completion_usd_per_token=EXCLUDED.completion_usd_per_token,reasoning_usd_per_token=EXCLUDED.reasoning_usd_per_token,request_usd=EXCLUDED.request_usd,context_length=EXCLUDED.context_length,supported_parameters=EXCLUDED.supported_parameters,catalog_fingerprint=EXCLUDED.catalog_fingerprint,discovered_at=EXCLUDED.discovered_at")
          .param("model",selected.id()).param("prompt",selected.promptPrice()).param("completion",selected.completionPrice())
          .param("reasoning",selected.reasoningPrice()).param("request",selected.requestPrice()).param("context",selected.contextLength())
          .param("parameters",parameters).param("fingerprint",fingerprint).param("now",selected.discoveredAt()).update();
      return current().orElseThrow();
    }catch(AiProviderException e){throw e;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AiProviderException("AI_REQUEST_CANCELLED",false,"OpenRouter discovery was cancelled");}
    catch(Exception e){throw new AiProviderException("AI_PROVIDER_INITIALIZATION_FAILED",true,"OpenRouter paid-model discovery failed");}
  }

  private void authenticate()throws Exception{
    var response=http.send(HttpRequest.newBuilder(URI.create(base+"/auth/key")).timeout(timeout)
        .header("Authorization","Bearer "+key).GET().build(),HttpResponse.BodyHandlers.discarding());
    if(response.statusCode()==401||response.statusCode()==403)throw new AiProviderException("AI_PROVIDER_AUTHENTICATION_FAILED",false,"OpenRouter authentication failed");
    if(response.statusCode()/100!=2)throw new AiProviderException("AI_PROVIDER_UNAVAILABLE",true,"OpenRouter capability authentication is unavailable");
  }

  private Optional<Model> candidate(JsonNode n){
    try{
      List<String> parameters=StreamSupport.stream(n.path("supported_parameters").spliterator(),false).map(JsonNode::asText).toList();
      if(!parameters.containsAll(List.of("response_format","structured_outputs","temperature","max_tokens")))return Optional.empty();
      long context=n.path("context_length").asLong();if(context<32768)return Optional.empty();
      BigDecimal prompt=positive(n.path("pricing").path("prompt")),completion=positive(n.path("pricing").path("completion"));
      if(prompt==null||completion==null||n.path("id").asText().endsWith(":free"))return Optional.empty();
      return Optional.of(new Model(n.path("id").asText(),prompt,completion,nonNegative(n.path("pricing").path("internal_reasoning")),nonNegative(n.path("pricing").path("request")),context,parameters,OffsetDateTime.now(ZoneOffset.UTC)));
    }catch(Exception ignored){return Optional.empty();}
  }
  private BigDecimal blendedPrice(Model m){return m.promptPrice().add(m.completionPrice().multiply(BigDecimal.valueOf(3)));}
  private BigDecimal positive(JsonNode n){BigDecimal v=decimal(n);return v.signum()>0?v:null;}
  private BigDecimal nonNegative(JsonNode n){if(n.isMissingNode()||n.isNull()||n.asText().isBlank())return BigDecimal.ZERO;BigDecimal v=decimal(n);return v.signum()<0?BigDecimal.ZERO:v;}
  private BigDecimal decimal(JsonNode n){return new BigDecimal(n.asText());}
  private String sha256(String value)throws Exception{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
}
