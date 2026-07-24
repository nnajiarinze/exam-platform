package se.medbo.examplatform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.List;
import java.util.Map;
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
        .query(String.class).list()).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");
    assertThat(jdbc.sql("SELECT to_regclass('public.ai_editorial_target') IS NOT NULL AND to_regclass('public.ai_editorial_proposal') IS NOT NULL AND to_regclass('public.ai_editorial_finding') IS NOT NULL AND to_regclass('public.ai_editorial_validation_metric') IS NOT NULL AND to_regclass('public.ai_quota_profile') IS NOT NULL AND to_regclass('public.ai_quota_reservation') IS NOT NULL AND to_regclass('public.ai_provider_circuit') IS NOT NULL AND to_regclass('public.ai_provider_alert') IS NOT NULL AND to_regclass('public.ai_question_proposal') IS NOT NULL AND to_regclass('public.ai_question_proposal_option') IS NOT NULL")
        .query(Boolean.class).single()).isTrue();
  }

  @Test
  void approvedSnapshotCanProducePersistentQuestionProposalsWithoutCanonicalWrites() throws Exception {
    var response=mvc.perform(post("/internal/v1/question-generation/jobs")
        .header("X-Internal-Api-Key","test-internal-key").contentType(MediaType.APPLICATION_JSON).content("""
        {"target":{"knowledgeFactId":"11111111-1111-1111-1111-111111111111","knowledgeFactVersionId":"22222222-2222-2222-2222-222222222222","version":1,"text":"Riksdagen beslutar om lagar.","checksum":"f46784f3035aa325c8f3463bb2d34f0ebaf7861181506fb98a85fa90fc544a25","language":"sv"},
         "context":{"learningObjectiveId":"33333333-3333-3333-3333-333333333333","learningObjectiveTitle":"Demokrati","learningObjectiveDescription":null,"topicId":"44444444-4444-4444-4444-444444444444","topicTitle":"Styrelseskick","subjectId":"55555555-5555-5555-5555-555555555555","subjectTitle":"Samhälle","examId":"66666666-6666-6666-6666-666666666666","examVersionId":"77777777-7777-7777-7777-777777777777","sources":[{"sourceId":"88888888-8888-8888-8888-888888888888","title":"Riksdagen","checksum":"25467c5617b6223429b5763106eebd724b90a9237c37466a86b4caa9f2262670","contentExcerpt":"Riksdagen beslutar om lagar och om statens budget."}]},
         "proposalCount":3,"questionType":"SINGLE_CHOICE","requestedBy":"author-questions","idempotencyKey":"question-generation-integration-1"}
        """)) .andExpect(status().isAccepted()).andExpect(jsonPath("$.operationType").value("GENERATE_QUESTIONS_FROM_FACT")).andReturn().getResponse().getContentAsString();
    UUID job=UUID.fromString(mapper.readTree(response).get("id").asText());
    for(int attempt=0;attempt<30;attempt++){String state=jdbc.sql("SELECT status FROM ai_generation_job WHERE id=:id").param("id",job).query(String.class).single();if(List.of("COMPLETED","PARTIALLY_COMPLETED","FAILED").contains(state))break;Thread.sleep(100);}
    assertThat(jdbc.sql("SELECT status FROM ai_generation_job WHERE id=:id").param("id",job).query(String.class).single()).isEqualTo("COMPLETED");
    assertThat(jdbc.sql("SELECT count(*) FROM ai_question_proposal WHERE generation_job_id=:id").param("id",job).query(Long.class).single()).isEqualTo(3);
    assertThat(jdbc.sql("SELECT count(*) FROM ai_question_proposal_option o JOIN ai_question_proposal p ON p.id=o.proposal_id WHERE p.generation_job_id=:id").param("id",job).query(Long.class).single()).isEqualTo(9);
    assertThat(jdbc.sql("SELECT count(*) FROM ai_audit_event WHERE entity_type='AI_QUESTION_PROPOSAL'").query(Long.class).single()).isGreaterThanOrEqualTo(3);
    assertThat(jdbc.sql("SELECT count(*) FROM ai_question_proposal WHERE generation_job_id=:id AND intelligence_evaluation_status='EVALUATED' AND overall_quality_score BETWEEN 0 AND 100").param("id",job).query(Long.class).single()).isEqualTo(3);
    assertThat(jdbc.sql("SELECT count(*) FROM ai_question_intelligence_component_score s JOIN ai_question_proposal p ON p.id=s.proposal_id WHERE p.generation_job_id=:id").param("id",job).query(Long.class).single()).isEqualTo(15);
    mvc.perform(get("/internal/v1/question-generation/jobs/{id}/proposals",job).header("X-Internal-Api-Key","test-internal-key"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].intelligenceAssessment.evaluationStatus").value("EVALUATED"))
        .andExpect(jsonPath("$[0].intelligenceAssessment.overallQualityScore").isNumber())
        .andExpect(jsonPath("$[0].intelligenceAssessment.componentScores.STRUCTURE").isNumber());
    UUID proposal=jdbc.sql("SELECT id FROM ai_question_proposal WHERE generation_job_id=:id ORDER BY proposal_order LIMIT 1").param("id",job).query(UUID.class).single();
    String validation=mvc.perform(post("/internal/v1/question-generation/proposals/{id}/revalidate",proposal)
            .header("X-Internal-Api-Key","test-internal-key"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andReturn().getResponse().getContentAsString();
    String validationChecksum=mapper.readTree(validation).get("validationChecksum").asText();UUID question=UUID.randomUUID();
    mvc.perform(post("/internal/v1/question-generation/proposals/{id}/accept",proposal).header("X-Internal-Api-Key","test-internal-key").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("questionId",question,"actor","author-questions","version",0,"validationChecksum",validationChecksum))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED")).andExpect(jsonPath("$.acceptedQuestionId").value(question.toString()));
    mvc.perform(post("/internal/v1/question-generation/proposals/{id}/reject",proposal).header("X-Internal-Api-Key","test-internal-key").contentType(MediaType.APPLICATION_JSON)
            .content("{\"reasonCode\":\"FACTUALLY_INCORRECT\",\"comment\":\"late rejection\",\"actor\":\"author-questions\",\"version\":1}"))
        .andExpect(status().isConflict());
    assertThat(jdbc.sql("SELECT count(*) FROM ai_question_proposal WHERE id=:id AND status='ACCEPTED' AND accepted_question_id=:question").param("id",proposal).param("question",question).query(Long.class).single()).isEqualTo(1);
    mvc.perform(get("/internal/v1/question-generation/jobs")
            .header("X-Internal-Api-Key","test-internal-key")
            .queryParam("knowledgeFactId","11111111-1111-1111-1111-111111111111")
            .queryParam("limit","10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(job.toString()))
        .andExpect(jsonPath("$[0].targetKnowledgeFactId").value("11111111-1111-1111-1111-111111111111"));
    mvc.perform(get("/internal/v1/question-generation/jobs")
            .header("X-Internal-Api-Key","test-internal-key")
            .queryParam("knowledgeFactId","99999999-9999-9999-9999-999999999999"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void rejectedProposalCanBeRegeneratedOnceWithImmutableLineage() throws Exception {
    var created = mvc.perform(post("/internal/v1/question-generation/jobs")
            .header("X-Internal-Api-Key", "test-internal-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content(questionGenerationRequest("question-regeneration-integration")))
        .andExpect(status().isAccepted())
        .andReturn().getResponse().getContentAsString();
    UUID originalJob = UUID.fromString(mapper.readTree(created).get("id").asText());
    awaitTerminal(originalJob);
    UUID original = jdbc.sql("""
        SELECT id FROM ai_question_proposal
        WHERE generation_job_id=:job ORDER BY proposal_order LIMIT 1
        """).param("job", originalJob).query(UUID.class).single();

    mvc.perform(post("/internal/v1/question-generation/proposals/{id}/reject", original)
            .header("X-Internal-Api-Key", "test-internal-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"reasonCode":"AMBIGUOUS","comment":"Ask directly which institution makes laws.","actor":"reviewer-1","version":0}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REJECTED"))
        .andExpect(jsonPath("$.rejectionReasonCode").value("AMBIGUOUS"));

    var regenerated = mvc.perform(post("/internal/v1/question-generation/proposals/{id}/regenerate", original)
            .header("X-Internal-Api-Key", "test-internal-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"reviewerFeedback":"Ask directly which institution makes laws and avoid vague wording.","actor":"reviewer-1","version":1,
                 "idempotencyKey":"question-regeneration-job-integration"}
                """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.parentProposalId").value(original.toString()))
        .andReturn().getResponse().getContentAsString();
    UUID regenerationJob = UUID.fromString(mapper.readTree(regenerated).get("jobId").asText());
    awaitTerminal(regenerationJob);

    mvc.perform(get("/internal/v1/question-generation/proposals/{id}/lineage", original)
            .header("X-Internal-Api-Key", "test-internal-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].status").value("SUPERSEDED"))
        .andExpect(jsonPath("$[1].status").value("PROPOSED"))
        .andExpect(jsonPath("$[1].parentProposalId").value(original.toString()))
        .andExpect(jsonPath("$[1].generationAttempt").value(2));

    mvc.perform(post("/internal/v1/question-generation/proposals/{id}/regenerate", original)
            .header("X-Internal-Api-Key", "test-internal-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"reviewerFeedback":"Try a different replacement.","actor":"reviewer-1","version":2,
                 "idempotencyKey":"second-successor-not-allowed"}
                """))
        .andExpect(status().isConflict());
    assertThat(jdbc.sql("SELECT count(*) FROM ai_question_proposal WHERE parent_proposal_id=:id")
        .param("id", original).query(Long.class).single()).isEqualTo(1);
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

  private String questionGenerationRequest(String idempotencyKey) {
    return """
        {"target":{"knowledgeFactId":"91111111-1111-1111-1111-111111111111","knowledgeFactVersionId":"92222222-2222-2222-2222-222222222222","version":1,"text":"Riksdagen beslutar om Sveriges lagar.","checksum":"8f1f9d82ab18db197cb64809a17d6116356339211632a8e5a9f41b66e5281002","language":"sv"},
         "context":{"learningObjectiveId":"93333333-3333-3333-3333-333333333333","learningObjectiveTitle":"Demokrati","learningObjectiveDescription":null,"topicId":"94444444-4444-4444-4444-444444444444","topicTitle":"Styrelseskick","subjectId":"95555555-5555-5555-5555-555555555555","subjectTitle":"Samhälle","examId":"96666666-6666-6666-6666-666666666666","examVersionId":"97777777-7777-7777-7777-777777777777","sources":[{"sourceId":"98888888-8888-8888-8888-888888888888","title":"Riksdagen","checksum":"a13141b3b135b5f6d6458189911ae00ac55c03e901c1bd17e587ab6eb1da5f5a","contentExcerpt":"Riksdagen beslutar om Sveriges lagar och om statens budget."}]},
         "proposalCount":1,"questionType":"SINGLE_CHOICE","requestedBy":"reviewer-1","idempotencyKey":"%s"}
        """.formatted(idempotencyKey);
  }

  private void awaitTerminal(UUID jobId) throws InterruptedException {
    for (int attempt = 0; attempt < 30; attempt++) {
      String state = jdbc.sql("SELECT status FROM ai_generation_job WHERE id=:id")
          .param("id", jobId).query(String.class).single();
      if (List.of("COMPLETED", "PARTIALLY_COMPLETED", "FAILED").contains(state)) return;
      Thread.sleep(100);
    }
  }
}
