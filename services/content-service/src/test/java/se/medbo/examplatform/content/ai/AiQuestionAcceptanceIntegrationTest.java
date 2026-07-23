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
class AiQuestionAcceptanceIntegrationTest {
  @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
  static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
  static HttpServer ai;static volatile Fixture fixture;static volatile String revalidationError;
  @Autowired AiQuestionGenerationService service;@Autowired JdbcClient jdbc;

  @BeforeAll static void start()throws Exception{ai=HttpServer.create(new InetSocketAddress(0),0);ai.createContext("/",AiQuestionAcceptanceIntegrationTest::respond);ai.start();}
  @AfterAll static void stop(){ai.stop(0);}
  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("content.ai-service.base-url",()->"http://localhost:"+ai.getAddress().getPort());registry.add("content.ai-service.internal-api-key",()->"test-key");}
  @BeforeEach void setup(){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("question-author","n/a",List.of(new SimpleGrantedAuthority("ROLE_CONTENT_AUTHOR"))));fixture=seed();revalidationError=null;}

  @Test void humanAcceptanceCreatesOneUnreviewedDraftWithImmutableProvenanceAndAudit(){
    UUID proposal=UUID.randomUUID();var result=service.accept(proposal,0);UUID question=(UUID)result.get("questionId");
    assertThat(result).containsEntry("status","DRAFT").containsEntry("reviewStatus","UNREVIEWED").containsEntry("location","/questions/"+question);
    assertThat(jdbc.sql("SELECT count(*) FROM question WHERE id=:id AND status='DRAFT' AND review_status='UNREVIEWED'").param("id",question).query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM question_ai_provenance WHERE question_id=:question AND proposal_id=:proposal AND accepted_by='question-author'").param("question",question).param("proposal",proposal).query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM question_source_reference WHERE question_version_id=(SELECT current_version_id FROM question WHERE id=:question)").param("question",question).query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("SELECT language FROM question_version WHERE id=(SELECT current_version_id FROM question WHERE id=:question)").param("question",question).query(String.class).single()).isEqualTo("sv");
    assertThat(jdbc.sql("SELECT count(*) FROM audit_event WHERE entity_id=:proposal AND entity_type='AIQuestionAcceptance'").param("proposal",proposal).query(Long.class).single()).isEqualTo(5);
    assertThat(service.accept(proposal,0).get("questionId")).isEqualTo(question);
    assertThat(jdbc.sql("SELECT count(*) FROM question_ai_provenance WHERE proposal_id=:proposal").param("proposal",proposal).query(Long.class).single()).isEqualTo(1);
  }

  @Test void exactDuplicateAndReviewerAcceptanceAreRejected(){
    service.accept(UUID.randomUUID(),0);
    assertThatThrownBy(()->service.accept(UUID.randomUUID(),0)).isInstanceOf(DomainException.class).extracting(e->((DomainException)e).code()).isEqualTo("AI_QUESTION_DUPLICATE");
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reviewer","n/a",List.of(new SimpleGrantedAuthority("ROLE_CONTENT_REVIEWER"))));
    assertThatThrownBy(()->service.accept(UUID.randomUUID(),0)).isInstanceOf(DomainException.class).extracting(e->((DomainException)e).code()).isEqualTo("FORBIDDEN");
  }

  @Test void staleProposalChangedFactAndChangedSourceAreRejected(){
    assertThatThrownBy(()->service.accept(UUID.randomUUID(),9)).isInstanceOf(DomainException.class).extracting(e->((DomainException)e).code()).isEqualTo("AI_QUESTION_PROPOSAL_STALE");
    jdbc.sql("UPDATE knowledge_fact_version SET canonical_statement='Riksdagen antar lagar och beslutar om statens budget.' WHERE id=:id").param("id",fixture.factVersion).update();
    assertThatThrownBy(()->service.accept(UUID.randomUUID(),0)).isInstanceOf(DomainException.class).extracting(e->((DomainException)e).code()).isEqualTo("AI_QUESTION_GENERATION_INPUT_STALE");
    fixture=seed();String changed="Riksdagen beslutar om lagar men källan har ändrats.";
    jdbc.sql("UPDATE source_reference SET content_text=:text,content_checksum=:checksum WHERE id=:id").param("text",changed).param("checksum",checksum(changed)).param("id",fixture.source).update();
    assertThatThrownBy(()->service.accept(UUID.randomUUID(),0)).isInstanceOf(DomainException.class).extracting(e->((DomainException)e).code()).isEqualTo("AI_QUESTION_GENERATION_SOURCE_CHANGED");
  }

  @Test void freshGroundingAndQualityFailuresBlockCanonicalCreation(){
    long before=jdbc.sql("SELECT count(*) FROM question").query(Long.class).single();revalidationError="AI_QUESTION_GENERATION_EVIDENCE_NOT_GROUNDED";
    assertThatThrownBy(()->service.accept(UUID.randomUUID(),0)).isInstanceOf(DomainException.class).extracting(e->((DomainException)e).code()).isEqualTo(revalidationError);
    revalidationError="AI_QUESTION_INTELLIGENCE_REJECTED";
    assertThatThrownBy(()->service.accept(UUID.randomUUID(),0)).isInstanceOf(DomainException.class).extracting(e->((DomainException)e).code()).isEqualTo(revalidationError);
    assertThat(jdbc.sql("SELECT count(*) FROM question").query(Long.class).single()).isEqualTo(before);
  }

  private Fixture seed(){
    UUID exam=UUID.randomUUID(),examVersion=UUID.randomUUID(),subject=UUID.randomUUID(),topic=UUID.randomUUID(),objective=UUID.randomUUID(),source=UUID.randomUUID(),fact=UUID.randomUUID(),factVersion=UUID.randomUUID();var now=OffsetDateTime.now(ZoneOffset.UTC);
    String factText="Riksdagen beslutar om Sveriges lagar.";String sourceText="Riksdagen beslutar om Sveriges lagar och statens budget.";
    jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Test','SE','DRAFT',:now,:now)").param("id",exam).param("code","AIQ_"+exam).param("now",now).update();
    jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'V1','V1','DRAFT',:now,:now)").param("id",examVersion).param("exam",exam).param("now",now).update();
    jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:version,'SUB','Subject',0,'DRAFT',:now,:now)").param("id",subject).param("version",examVersion).param("now",now).update();
    jdbc.sql("INSERT INTO topic(id,subject_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:subject,'TOP','Topic',0,'DRAFT',:now,:now)").param("id",topic).param("subject",subject).param("now",now).update();
    jdbc.sql("INSERT INTO learning_objective(id,topic_id,code,title,status,created_at,updated_at) VALUES(:id,:topic,'OBJ','Objective','DRAFT',:now,:now)").param("id",objective).param("topic",topic).param("now",now).update();
    jdbc.sql("INSERT INTO source_reference(id,publisher,title,source_type,accessed_at,content_text,content_checksum,review_status,status,created_at,updated_at) VALUES(:id,'Authority','Source','GOVERNMENT_WEBPAGE',CURRENT_DATE,:text,:checksum,'REVIEWED','ACTIVE',:now,:now)").param("id",source).param("text",sourceText).param("checksum",checksum(sourceText)).param("now",now).update();
    jdbc.sql("INSERT INTO knowledge_fact(id,learning_objective_id,canonical_statement,review_status,status,created_at,updated_at) VALUES(:id,:objective,:text,'APPROVED','ACTIVE',:now,:now)").param("id",fact).param("objective",objective).param("text",factText).param("now",now).update();
    jdbc.sql("INSERT INTO knowledge_fact_version(id,knowledge_fact_id,version_number,canonical_statement,review_status,author_id,created_at,updated_at) VALUES(:id,:fact,1,:text,'APPROVED','fact-author',:now,:now)").param("id",factVersion).param("fact",fact).param("text",factText).param("now",now).update();
    jdbc.sql("UPDATE knowledge_fact SET current_version_id=:version WHERE id=:fact").param("version",factVersion).param("fact",fact).update();
    jdbc.sql("INSERT INTO knowledge_fact_source(knowledge_fact_version_id,source_reference_id) VALUES(:version,:source)").param("version",factVersion).param("source",source).update();
    return new Fixture(UUID.randomUUID(),fact,factVersion,objective,source,factText,sourceText,now);
  }

  private static void respond(HttpExchange exchange){
    try{String path=exchange.getRequestURI().getPath();Object body;
      if(path.endsWith("/revalidate")&&revalidationError!=null){body=Map.of("code",revalidationError,"message","Fresh validation failed","timestamp",OffsetDateTime.now().toString(),"errors",List.of());byte[] bytes=JSON.writeValueAsBytes(body);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(409,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();return;}
      else if(path.endsWith("/revalidate"))body=Map.of("valid",true,"proposalChecksum","b".repeat(64),"validationChecksum","a".repeat(64),"intelligenceEngineVersion","question-intelligence-v1","evaluatedAt",OffsetDateTime.now().toString(),"sourceChecksums",List.of(Map.of("sourceId",fixture.source,"checksum",checksum(fixture.sourceText))));
      else if(path.endsWith("/accept"))body=Map.of("status","ACCEPTED");
      else {UUID proposal=UUID.fromString(path.substring(path.lastIndexOf('/')+1));body=proposal(proposal);}
      byte[] bytes=JSON.writeValueAsBytes(body);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
    }catch(Exception e){throw new RuntimeException(e);}
  }
  private static Map<String,Object> proposal(UUID id){return Map.ofEntries(Map.entry("id",id),Map.entry("generationJobId",fixture.job),Map.entry("questionType","SINGLE_CHOICE"),Map.entry("questionText","Vilken institution beslutar om Sveriges lagar?"),Map.entry("language","sv"),Map.entry("answerOptions",List.of(Map.of("displayOrder",0,"text","Riksdagen","correct",true),Map.of("displayOrder",1,"text","Polisen","correct",false))),Map.entry("explanation",fixture.factText),Map.entry("rationale","Direct recall"),Map.entry("targetKnowledgeFactId",fixture.fact),Map.entry("targetKnowledgeFactVersionId",fixture.factVersion),Map.entry("targetVersion",1),Map.entry("targetChecksum",checksum(fixture.factText)),Map.entry("learningObjectiveId",fixture.objective),Map.entry("status","PROPOSED"),Map.entry("provider","FAKE"),Map.entry("model","deterministic-v1"),Map.entry("promptVersion","question-generation-intelligence-v1"),Map.entry("createdAt",fixture.created.toString()),Map.entry("version",0),Map.entry("intelligenceAssessment",Map.of("evaluationStatus","EVALUATED","passedValidation",true,"difficulty","EASY","bloomsLevel","REMEMBER","complexity","LOW","generationIntent","PRACTICE","estimatedReadingSeconds",8)));}
  private static String checksum(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
  record Fixture(UUID job,UUID fact,UUID factVersion,UUID objective,UUID source,String factText,String sourceText,OffsetDateTime created){}
}
