package se.medbo.examplatform.ai.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.ai.provider.AiProviderClient;
import se.medbo.examplatform.ai.provider.AiProviderException;
import se.medbo.examplatform.ai.provider.GeminiQuotaService;

class GeminiAiProviderClientTest {
  private HttpServer server;
  private GeminiQuotaService quota;
  private final AtomicReference<String> requestBody=new AtomicReference<>();
  private final AtomicReference<String> apiKey=new AtomicReference<>();

  @BeforeEach void start() throws Exception {server=HttpServer.create(new InetSocketAddress(0),0);quota=mock(GeminiQuotaService.class);when(quota.reserve(anyInt(),nullable(UUID.class),nullable(String.class),nullable(String.class),anyInt())).thenReturn(new GeminiQuotaService.Reservation(UUID.randomUUID(),UUID.randomUUID()));}
  @AfterEach void stop(){server.stop(0);}

  @Test void extractsStructuredOutputUsageAndRequestIdWithoutEnablingTools() throws Exception {
    respond(200,"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"proposals\\\":[{\\\"text\\\":\\\"Riksdagen stiftar lagar.\\\",\\\"sourceEvidence\\\":[{\\\"quote\\\":\\\"Riksdagen stiftar lagar.\\\"}]}],\\\"warnings\\\":[]}\"}]}}],\"usageMetadata\":{\"promptTokenCount\":12,\"candidatesTokenCount\":7}}", "request-42");
    var result=client().generate(new AiProviderClient.GenerationRequest("Riksdagen stiftar lagar.","Demokrati","sv",1,"","v1"));
    assertThat(result.proposals()).hasSize(1);assertThat(result.usage().inputTokens()).isEqualTo(12);assertThat(result.usage().outputTokens()).isEqualTo(7);assertThat(result.usage().requestId()).isEqualTo("request-42");
    assertThat(requestBody.get()).contains("<SOURCE_CONTENT>","responseJsonSchema","application/json").doesNotContain("google_search","tools");assertThat(apiKey.get()).isEqualTo("test-secret");
    verify(quota).success(any(),org.mockito.ArgumentMatchers.eq(12),org.mockito.ArgumentMatchers.eq(7),org.mockito.ArgumentMatchers.eq("request-42"));
  }

  @Test void acceptsMissingUsageMetadata(){respond(200,"{\"responseId\":\"response-1\",\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"proposals\\\":[],\\\"warnings\\\":[]}\"}]}}]}",null);var result=client().generate(request());assertThat(result.usage().inputTokens()).isNull();assertThat(result.usage().requestId()).isEqualTo("response-1");}
  @Test void rejectsMalformedStructuredJson(){respond(200,"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"not-json\"}]}}]}",null);assertThatThrownBy(()->client().generate(request())).isInstanceOf(AiProviderException.class).hasMessageContaining("invalid structured JSON");}
  @Test void classifiesMinuteRateLimitAsRetryable(){respond(429,"{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\",\"message\":\"RPM per minute exceeded\"}}",null);assertThatThrownBy(()->client().generate(request())).isInstanceOfSatisfying(AiProviderException.class,e->{assertThat(e.code()).isEqualTo("AI_PROVIDER_TEMPORARILY_RATE_LIMITED");assertThat(e.transientFailure()).isTrue();});verify(quota).rateLimited(any(),org.mockito.ArgumentMatchers.eq("AI_PROVIDER_TEMPORARILY_RATE_LIMITED"),org.mockito.ArgumentMatchers.eq(false));}
  @Test void classifiesDailyAndAuthenticationFailuresWithoutRetry(){respond(429,"{\"error\":{\"message\":\"requests per day quota exceeded\"}}",null);assertThatThrownBy(()->client().generate(request())).isInstanceOfSatisfying(AiProviderException.class,e->{assertThat(e.code()).isEqualTo("AI_PROVIDER_DAILY_QUOTA_EXHAUSTED");assertThat(e.transientFailure()).isFalse();});}
  @Test void rejectsMissingKeyBeforeAnyHttpCall(){var client=new GeminiAiProviderClient(new ObjectMapper(),quota,HttpClient.newHttpClient(),"","gemini-2.5-flash",base(),"v1beta",Duration.ofSeconds(2));assertThatThrownBy(()->client.generate(request())).isInstanceOfSatisfying(AiProviderException.class,e->assertThat(e.code()).isEqualTo("AI_GEMINI_NOT_CONFIGURED"));}

  private AiProviderClient.GenerationRequest request(){return new AiProviderClient.GenerationRequest("Riksdagen stiftar lagar.","Demokrati","sv",1,"","v1");}
  private GeminiAiProviderClient client(){return new GeminiAiProviderClient(new ObjectMapper(),quota,HttpClient.newHttpClient(),"test-secret","gemini-2.5-flash",base(),"v1beta",Duration.ofSeconds(2));}
  private String base(){return "http://localhost:"+server.getAddress().getPort();}
  private void respond(int status,String body,String requestId){server.createContext("/",exchange->{requestBody.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));apiKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));if(requestId!=null)exchange.getResponseHeaders().add("x-goog-request-id",requestId);byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();}
}
