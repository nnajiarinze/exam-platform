package se.medbo.examplatform.content.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.medbo.examplatform.content.shared.DomainException;

@SpringBootTest
@Testcontainers
class EditorialSplitAcceptanceIntegrationTest {
  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  static HttpServer ai;
  static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  static final UUID JOB = UUID.fromString("10000000-0000-0000-0000-000000000001");
  static final UUID PROPOSAL_ONE = UUID.fromString("10000000-0000-0000-0000-000000000002");
  static final UUID PROPOSAL_TWO = UUID.fromString("10000000-0000-0000-0000-000000000003");
  static final UUID PROPOSAL_THREE = UUID.fromString("10000000-0000-0000-0000-000000000004");
  static volatile Fixture fixture;
  static volatile String jobOperation = "SPLIT_FACT";

  @Autowired EditorialWorkspaceService service;
  @Autowired JdbcClient jdbc;

  @BeforeAll static void startAiStub() throws Exception {
    ai = HttpServer.create(new InetSocketAddress(0), 0);
    ai.createContext("/", EditorialSplitAcceptanceIntegrationTest::respond);
    ai.start();
  }
  @AfterAll static void stopAiStub() { ai.stop(0); }
  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
    registry.add("content.ai-service.base-url", () -> "http://localhost:" + ai.getAddress().getPort());
    registry.add("content.ai-service.internal-api-key", () -> "test-key");
    registry.add("content.identity.development-header-enabled", () -> "true");
  }

  @BeforeEach void setUp() {
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
        "split-author", "n/a", List.of(new SimpleGrantedAuthority("ROLE_CONTENT_AUTHOR"))));
    fixture = seed();
    jobOperation = "SPLIT_FACT";
  }

  @Test void unsupportedLinkedSourceBlocksRewriteBeforeProviderCall() {
    String unrelated = "Kommunerna ansvarar för bibliotek och äldreomsorg.";
    jdbc.sql("UPDATE source_reference SET content_text=:text,content_checksum=:checksum WHERE id=:id")
        .param("text", unrelated).param("checksum", checksum(unrelated)).param("id", fixture.sourceId).update();
    assertThatThrownBy(() -> service.create(new EditorialWorkspaceService.Create(
        EditorialWorkspaceService.Operation.REWRITE_FOR_CLARITY, fixture.factId, "sv", null, null, 1, "unsupported-" + fixture.factId)))
        .isInstanceOf(DomainException.class)
        .extracting(error -> ((DomainException) error).code()).isEqualTo("AI_EDITORIAL_INSUFFICIENT_EVIDENCE");
    assertThat(jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id=:fact AND reason='AI_EDITORIAL_INSUFFICIENT_EVIDENCE'")
        .param("fact",fixture.factId).query(Long.class).single()).isEqualTo(1);
  }

  @Test void validLinkedSourceAllowsRewriteGenerationRequest() {
    var result = service.create(new EditorialWorkspaceService.Create(
        EditorialWorkspaceService.Operation.REWRITE_FOR_CLARITY, fixture.factId, "sv", null, null, 1, "grounded-" + fixture.factId));
    assertThat(result).containsEntry("accepted", true);
  }

  @Test void unsupportedHumanEditCannotBeAccepted() {
    jobOperation = "REWRITE_FOR_CLARITY";
    fixture = fixture.withFirstText("Kommunerna ansvarar för äldreomsorg och bibliotek.");
    assertThatThrownBy(() -> service.accept(PROPOSAL_THREE, 0))
        .isInstanceOf(DomainException.class)
        .extracting(error -> ((DomainException) error).code()).isEqualTo("AI_EDITORIAL_FINAL_TEXT_NOT_GROUNDED");
    assertThat(jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id=:fact AND reason='AI_EDITORIAL_FINAL_TEXT_NOT_GROUNDED'")
        .param("fact",fixture.factId).query(Long.class).single()).isEqualTo(1);
  }

  @Test void selectedProposalsAtomicallyCreateUnreviewedDraftsAndKeepOriginal() {
    var result = service.acceptSplit(JOB, fixture.factId, fixture.factVersionId, checksum(fixture.original),
        List.of(PROPOSAL_ONE, PROPOSAL_TWO), EditorialWorkspaceService.SplitAcceptanceMode.CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL,
        "split-acceptance-" + fixture.factId);
    @SuppressWarnings("unchecked") var ids = (List<UUID>) result.get("resultingFactIds");
    assertThat(ids).hasSize(2);
    assertThat(jdbc.sql("SELECT canonical_statement FROM knowledge_fact WHERE id=:id").param("id", fixture.factId).query(String.class).single()).isEqualTo(fixture.original);
    assertThat(jdbc.sql("SELECT count(*) FROM knowledge_fact WHERE id=ANY(:ids) AND status='DRAFT' AND review_status='UNREVIEWED'").param("ids", ids.toArray(UUID[]::new)).query(Long.class).single()).isEqualTo(2);
    assertThat(jdbc.sql("SELECT count(*) FROM knowledge_fact_source s JOIN knowledge_fact f ON f.current_version_id=s.knowledge_fact_version_id WHERE f.id=ANY(:ids) AND s.source_reference_id=:source").param("ids", ids.toArray(UUID[]::new)).param("source", fixture.sourceId).query(Long.class).single()).isEqualTo(2);
    assertThat(jdbc.sql("SELECT count(*) FROM knowledge_fact_ai_editorial_provenance WHERE knowledge_fact_version_id IN(SELECT current_version_id FROM knowledge_fact WHERE id=ANY(:ids)) AND acceptance_action='CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL'").param("ids", ids.toArray(UUID[]::new)).query(Long.class).single()).isEqualTo(2);
    assertThat(jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id=ANY(:ids)").param("ids", ids.toArray(UUID[]::new)).query(Long.class).single()).isGreaterThanOrEqualTo(2);

    var repeated = service.acceptSplit(JOB, fixture.factId, fixture.factVersionId, checksum(fixture.original),
        List.of(PROPOSAL_ONE, PROPOSAL_TWO), EditorialWorkspaceService.SplitAcceptanceMode.CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL,
        "split-acceptance-" + fixture.factId);
    assertThat(repeated.get("resultingFactIds")).isEqualTo(ids);
    assertThat(jdbc.sql("SELECT count(*) FROM ai_split_acceptance WHERE target_fact_id=:id").param("id", fixture.factId).query(Long.class).single()).isEqualTo(1);
  }

  @Test void exactDuplicateAndStaleSourceAreRejectedWithoutPartialFacts() {
    fixture = fixture.withSecondText(fixture.original);
    long before = jdbc.sql("SELECT count(*) FROM knowledge_fact").query(Long.class).single();
    assertThatThrownBy(() -> service.acceptSplit(JOB, fixture.factId, fixture.factVersionId, checksum(fixture.original),
        List.of(PROPOSAL_TWO), EditorialWorkspaceService.SplitAcceptanceMode.CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL, "duplicate-" + fixture.factId))
        .isInstanceOf(DomainException.class).extracting(error -> ((DomainException) error).code()).isEqualTo("AI_EDITORIAL_SPLIT_DUPLICATE");
    assertThat(jdbc.sql("SELECT count(*) FROM knowledge_fact").query(Long.class).single()).isEqualTo(before);

    fixture = fixture.withSnapshotChecksum("f".repeat(64));
    assertThatThrownBy(() -> service.acceptSplit(JOB, fixture.factId, fixture.factVersionId, checksum(fixture.original),
        List.of(PROPOSAL_ONE), EditorialWorkspaceService.SplitAcceptanceMode.CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL, "stale-source-" + fixture.factId))
        .isInstanceOf(DomainException.class).extracting(error -> ((DomainException) error).code()).isEqualTo("AI_EDITORIAL_SOURCE_CHANGED");
    assertThat(jdbc.sql("SELECT count(*) FROM knowledge_fact").query(Long.class).single()).isEqualTo(before);
  }

  private Fixture seed() {
    UUID exam = UUID.randomUUID(), examVersion = UUID.randomUUID(), subject = UUID.randomUUID(), topic = UUID.randomUUID(), objective = UUID.randomUUID();
    UUID source = UUID.randomUUID(), fact = UUID.randomUUID(), factVersion = UUID.randomUUID();
    String original = "Riksdagen beslutar om lagar och regeringen verkställer besluten.";
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Test','SE','DRAFT',:now,:now)").param("id",exam).param("code","SPLIT_"+exam).param("now",now).update();
    jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'V1','V1','DRAFT',:now,:now)").param("id",examVersion).param("exam",exam).param("now",now).update();
    jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:version,'SUB','Subject',0,'DRAFT',:now,:now)").param("id",subject).param("version",examVersion).param("now",now).update();
    jdbc.sql("INSERT INTO topic(id,subject_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:subject,'TOP','Topic',0,'DRAFT',:now,:now)").param("id",topic).param("subject",subject).param("now",now).update();
    jdbc.sql("INSERT INTO learning_objective(id,topic_id,code,title,status,created_at,updated_at) VALUES(:id,:topic,'OBJ','Objective','DRAFT',:now,:now)").param("id",objective).param("topic",topic).param("now",now).update();
    jdbc.sql("INSERT INTO source_reference(id,publisher,title,source_type,accessed_at,content_text,content_checksum,review_status,status,created_at,updated_at) VALUES(:id,'Authority','Source','GOVERNMENT_WEBPAGE',CURRENT_DATE,:text,:checksum,'REVIEWED','ACTIVE',:now,:now)").param("id",source).param("text",original).param("checksum",checksum(original)).param("now",now).update();
    jdbc.sql("INSERT INTO knowledge_fact(id,learning_objective_id,canonical_statement,review_status,status,created_at,updated_at) VALUES(:id,:objective,:text,'UNREVIEWED','DRAFT',:now,:now)").param("id",fact).param("objective",objective).param("text",original).param("now",now).update();
    jdbc.sql("INSERT INTO knowledge_fact_version(id,knowledge_fact_id,version_number,canonical_statement,review_status,author_id,created_at,updated_at) VALUES(:id,:fact,1,:text,'UNREVIEWED','split-author',:now,:now)").param("id",factVersion).param("fact",fact).param("text",original).param("now",now).update();
    jdbc.sql("UPDATE knowledge_fact SET current_version_id=:version WHERE id=:fact").param("version",factVersion).param("fact",fact).update();
    jdbc.sql("INSERT INTO knowledge_fact_source(knowledge_fact_version_id,source_reference_id) VALUES(:version,:source)").param("version",factVersion).param("source",source).update();
    return new Fixture(fact,factVersion,source,objective,original,"Riksdagen beslutar om lagar.","Regeringen verkställer besluten.",checksum(original));
  }

  private static void respond(HttpExchange exchange) {
    try {
      String path = exchange.getRequestURI().getPath(); Object body;
      if (path.equals("/internal/v1/editorial/jobs") && "POST".equals(exchange.getRequestMethod())) body = Map.of("accepted",true);
      else if (path.equals("/internal/v1/editorial/jobs/" + JOB)) body = Map.of("id",JOB,"operationType",jobOperation,"requestedBy","split-author","status","COMPLETED");
      else if (path.endsWith("/accepted")) { exchange.sendResponseHeaders(204,-1); exchange.close(); return; }
      else if (path.endsWith(PROPOSAL_ONE.toString())) body = proposal(PROPOSAL_ONE,fixture.firstText,0);
      else if (path.endsWith(PROPOSAL_TWO.toString())) body = proposal(PROPOSAL_TWO,fixture.secondText,1);
      else if (path.endsWith(PROPOSAL_THREE.toString())) body = proposal(PROPOSAL_THREE,fixture.firstText,0);
      else body = Map.of("code","AI_JOB_NOT_FOUND","message","Not found","timestamp",OffsetDateTime.now().toString(),"errors",List.of());
      byte[] bytes=JSON.writeValueAsBytes(body);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
    } catch(Exception exception){throw new RuntimeException(exception);}
  }
  private static Map<String,Object> proposal(UUID id,String text,int order){String context;try{context=JSON.writeValueAsString(Map.of("sources",List.of(Map.of("sourceId",fixture.sourceId,"text",fixture.original,"checksum",fixture.snapshotChecksum))));}catch(Exception e){throw new RuntimeException(e);}String evidence;try{evidence=JSON.writeValueAsString(List.of(Map.of("sourceId",fixture.sourceId,"quote",fixture.original,"location","Stored Source text")));}catch(Exception e){throw new RuntimeException(e);}return Map.ofEntries(Map.entry("id",id),Map.entry("generation_job_id",JOB),Map.entry("operation_type",jobOperation),Map.entry("target_fact_id",fixture.factId),Map.entry("target_fact_version_id",fixture.factVersionId),Map.entry("target_version",0),Map.entry("original_checksum",checksum(fixture.original)),Map.entry("target_context",context),Map.entry("original_text",fixture.original),Map.entry("proposed_text",text),Map.entry("edited_text",text),Map.entry("source_evidence",evidence),Map.entry("warnings","[]"),Map.entry("status","PROPOSED"),Map.entry("version",0),Map.entry("edit_count",0),Map.entry("provider","FAKE"),Map.entry("model","deterministic-v1"),Map.entry("prompt_version","knowledge-fact-split-v1"),Map.entry("generated_at",OffsetDateTime.now().toString()),Map.entry("requested_by","split-author"),Map.entry("proposal_order",order));}
  private static String checksum(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
  record Fixture(UUID factId,UUID factVersionId,UUID sourceId,UUID objectiveId,String original,String firstText,String secondText,String snapshotChecksum){Fixture withFirstText(String value){return new Fixture(factId,factVersionId,sourceId,objectiveId,original,value,secondText,snapshotChecksum);}Fixture withSecondText(String value){return new Fixture(factId,factVersionId,sourceId,objectiveId,original,firstText,value,snapshotChecksum);}Fixture withSnapshotChecksum(String value){return new Fixture(factId,factVersionId,sourceId,objectiveId,original,firstText,secondText,value);}}
}
