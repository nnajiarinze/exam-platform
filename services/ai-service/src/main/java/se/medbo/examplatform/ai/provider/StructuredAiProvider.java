package se.medbo.examplatform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface StructuredAiProvider {
  enum FreeStatus {KNOWN,ESTIMATED,UNKNOWN}
  enum CapacityAuthority {PROVIDER_HEADER,PROVIDER_API,CONFIGURATION,LOCAL_ESTIMATE,ERROR_RESPONSE}
  record Capabilities(boolean textGeneration,boolean structuredJson,boolean jsonSchema,
      boolean deterministicTemperature,boolean systemInstruction,boolean swedish,long maxContext,
      int maxOutputTokens,boolean rateLimitHeaders,boolean knownQuotaReset,boolean modelDiscovery){}
  record Availability(boolean eligible,String reason,FreeStatus freeStatus,CapacityAuthority authority,
      String circuitState,OffsetDateTime nextRetryAt,Map<String,Object> capacity){}
  record Request(String operation,String systemInstruction,String prompt,Map<String,Object> jsonSchema,
      int maximumOutputTokens,double temperature,UUID jobId,String requester,int retryAttempt,
      String correlationId,String idempotencyKey){}
  record Response(JsonNode structuredResponse,String provider,String model,String actualModel,
      String providerRequestId,Integer inputTokens,Integer outputTokens,Long latencyMs,String finishReason,
      Map<String,Object> rateLimits,Map<String,Object> freeCapacity,OffsetDateTime retryAfter,
      String rawStatus,boolean confirmedFree){}

  String provider();
  String model();
  int priority();
  boolean enabled();
  boolean credentialsConfigured();
  Capabilities capabilities();
  Availability availability(Request request);
  Response execute(Request request);

  default boolean supports(Request request){var c=capabilities();return c.textGeneration()&&c.structuredJson()
      &&c.jsonSchema()&&c.systemInstruction()&&c.swedish()
      &&request.maximumOutputTokens()<=c.maxOutputTokens();}
  default Set<String> infrastructureFallbackCodes(){return Set.of("AI_PROVIDER_TEMPORARILY_RATE_LIMITED",
      "AI_PROVIDER_DAILY_QUOTA_EXHAUSTED","AI_PROVIDER_RESOURCE_EXHAUSTED","AI_PROVIDER_UNAVAILABLE",
      "AI_REQUEST_TIMEOUT","AI_GEMINI_MODEL_UNAVAILABLE","AI_PROVIDER_MODEL_UNAVAILABLE",
      "AI_PROVIDER_INITIALIZATION_FAILED","AI_FREE_QUOTA_PAUSED");}
}
