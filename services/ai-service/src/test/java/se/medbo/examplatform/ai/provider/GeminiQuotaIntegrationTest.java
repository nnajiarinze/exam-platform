package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.medbo.examplatform.ai.generation.AiApiException;

@Testcontainers
@SpringBootTest(properties={"ai.editorial.provider=GEMINI","ai.editorial.enabled=true","ai.gemini.api-key=synthetic-test-key","ai.usage-mode=FREE_ONLY","ai.gemini.expected-billing-tier=FREE","ai.gemini.project-label=test-project","ai.gemini.internal-rpm-limit=3","ai.gemini.internal-tpm-limit=1000","ai.gemini.internal-rpd-limit=20","ai.gemini.warning-threshold-percent=50","ai.gemini.critical-threshold-percent=80","ai.gemini.stop-threshold-percent=95"})
class GeminiQuotaIntegrationTest {
  @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
  @Autowired GeminiQuotaService quota;@Autowired JdbcClient jdbc;
  @BeforeEach void clear(){jdbc.sql("DELETE FROM ai_provider_alert").update();jdbc.sql("DELETE FROM ai_quota_reservation").update();jdbc.sql("DELETE FROM ai_provider_circuit").update();jdbc.sql("DELETE FROM ai_quota_profile").update();}

  @Test void reservesAtomicallyReconcilesUsageAndPersistsCircuit(){var first=quota.reserve(100);quota.success(first,80,20,"provider-request");var status=quota.status();assertThat(((java.util.Map<?,?>)status.get("usage")).get("dayInputTokens")).isEqualTo(80L);assertThat(jdbc.sql("SELECT provider_request_id FROM ai_quota_reservation WHERE id=:id").param("id",first.id()).query(String.class).single()).isEqualTo("provider-request");quota.reserve(100);assertThat(quota.status().get("state")).isEqualTo("WARNING");}

  @Test void concurrentWorkersCannotOversubscribeStopThreshold() throws Exception {var successes=new ArrayList<GeminiQuotaService.Reservation>();var failures=new ArrayList<String>();try(var pool=Executors.newFixedThreadPool(3)){List<java.util.concurrent.Callable<Void>> work=java.util.stream.IntStream.range(0,3).mapToObj(i->(java.util.concurrent.Callable<Void>)()->{try{var reservation=quota.reserve(1);synchronized(successes){successes.add(reservation);}}catch(AiApiException e){synchronized(failures){failures.add(e.code());}}return null;}).toList();for(var future:pool.invokeAll(work))future.get();}assertThat(successes).hasSize(2);assertThat(failures).containsExactly("AI_FREE_QUOTA_PAUSED");assertThat(quota.status().get("state")).isEqualTo("QUOTA_PAUSED");}
}
