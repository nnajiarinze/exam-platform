package se.medbo.examplatform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
    "ai.editorial.enabled=true",
    "ai.editorial.provider=FAKE",
    "ai.editorial.model=deterministic-v1",
    "ai.editorial.internal-api-key=test-internal-key"
})
class AiEditorialIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper mapper;

  @Test
  void migrationsCreateThePersistentEditorialWorkspace() {
    assertThat(jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
        .query(String.class).list()).containsExactly("1", "2", "3", "4", "5", "6");
    assertThat(jdbc.sql("SELECT to_regclass('public.ai_editorial_target') IS NOT NULL AND to_regclass('public.ai_editorial_proposal') IS NOT NULL AND to_regclass('public.ai_editorial_finding') IS NOT NULL AND to_regclass('public.ai_editorial_validation_metric') IS NOT NULL AND to_regclass('public.ai_quota_profile') IS NOT NULL AND to_regclass('public.ai_quota_reservation') IS NOT NULL AND to_regclass('public.ai_provider_circuit') IS NOT NULL AND to_regclass('public.ai_provider_alert') IS NOT NULL")
        .query(Boolean.class).single()).isTrue();
  }

  @Test
  void authenticatedInternalRequestQueuesTheRegisteredEditorialEndpoint() throws Exception {
    mvc.perform(post("/internal/v1/editorial/jobs")
            .header("X-Internal-Api-Key", "test-internal-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "operation":"REWRITE_FOR_CLARITY",
                  "targets":[{"factId":"11111111-1111-1111-1111-111111111111","factVersionId":"22222222-2222-2222-2222-222222222222","version":0,"text":"Riksdagen beslutar om lagar.","checksum":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],
                  "sources":[{"sourceId":"33333333-3333-3333-3333-333333333333","title":"Riksdagen","text":"Riksdagen beslutar om lagar.","checksum":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}],
                  "learningObjectiveId":"44444444-4444-4444-4444-444444444444",
                  "objectiveTitle":"Riksdagen",
                  "language":"sv",
                  "count":1,
                  "requestedBy":"author-1",
                  "idempotencyKey":"editorial-integration-1"
                }
                """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.operationType").value("REWRITE_FOR_CLARITY"))
        .andExpect(jsonPath("$.status").value("QUEUED"));
  }

  @Test
  void missingInternalAuthenticationFailsClosed() throws Exception {
    mvc.perform(post("/internal/v1/editorial/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INTERNAL_AUTHENTICATION_REQUIRED"));
  }

  @Test
  void unrelatedEvidenceIsRejectedBeforeProposalPersistence() throws Exception {
    var response=mvc.perform(post("/internal/v1/editorial/jobs")
            .header("X-Internal-Api-Key", "test-internal-key").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"operation":"REWRITE_FOR_CLARITY",
                 "targets":[{"factId":"aaaaaaaa-1111-1111-1111-111111111111","factVersionId":"aaaaaaaa-2222-2222-2222-222222222222","version":0,"text":"Ledamöterna beslutar om statens budget.","checksum":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],
                 "sources":[{"sourceId":"aaaaaaaa-3333-3333-3333-333333333333","title":"Riksdagens uppgifter","text":"Riksdagen beslutar om Sveriges lagar. [[SIMULATE_UNRELATED_EVIDENCE]] Ledamöterna beslutar om statens budget.","checksum":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}],
                 "learningObjectiveId":"aaaaaaaa-4444-4444-4444-444444444444","objectiveTitle":"Riksdagen","language":"sv","count":1,"requestedBy":"author-grounding","idempotencyKey":"invalid-grounding-integration"}
                """))
        .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
    UUID job=UUID.fromString(mapper.readTree(response).get("id").asText());
    for(int attempt=0;attempt<20;attempt++){
      String state=jdbc.sql("SELECT status FROM ai_generation_job WHERE id=:id").param("id",job).query(String.class).single();
      if("FAILED".equals(state))break;
      Thread.sleep(100);
    }
    assertThat(jdbc.sql("SELECT error_code FROM ai_generation_job WHERE id=:id").param("id",job).query(String.class).single()).isEqualTo("AI_EDITORIAL_EVIDENCE_NOT_RELEVANT");
    assertThat(jdbc.sql("SELECT count(*) FROM ai_editorial_proposal WHERE generation_job_id=:id").param("id",job).query(Long.class).single()).isZero();
    assertThat(jdbc.sql("SELECT count(*) FROM ai_editorial_validation_metric WHERE metric_name='REJECTED_AI_EDITORIAL_EVIDENCE_NOT_RELEVANT'").query(Long.class).single()).isEqualTo(1);
  }
}
