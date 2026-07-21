package se.medbo.examplatform.content;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "content.identity.development-header-enabled=true")
class ContentServiceIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    @Test
    void browserPreflightAllowsAdminWritesFromTheDevelopmentPortal() throws Exception {
        mvc.perform(options("/api/v1/admin/exams")
                        .header("Origin", "http://localhost:5177")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-admin-identity,x-admin-roles"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5177"))
                .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void subjectReorderingPersistsWithoutViolatingTheNonNegativeOrderConstraint() throws Exception {
        var examId = java.util.UUID.randomUUID();
        var examVersionId = java.util.UUID.randomUUID();
        var firstId = java.util.UUID.randomUUID();
        var secondId = java.util.UUID.randomUUID();
        var now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Test','SE','DRAFT',:now,:now)")
                .param("id", examId).param("code", "REORDER_" + examId).param("now", now).update();
        jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'V1','V1','DRAFT',:now,:now)")
                .param("id", examVersionId).param("exam", examId).param("now", now).update();
        jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:version,:code,:name,:sort,'DRAFT',:now,:now)")
                .param("id", firstId).param("version", examVersionId).param("code", "FIRST").param("name", "First").param("sort", 0).param("now", now).update();
        jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:version,:code,:name,:sort,'DRAFT',:now,:now)")
                .param("id", secondId).param("version", examVersionId).param("code", "SECOND").param("name", "Second").param("sort", 1).param("now", now).update();

        mvc.perform(put("/api/v1/admin/exam-versions/" + examVersionId + "/subjects/order")
                        .headers(author()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + secondId + "\",\"" + firstId + "\"]}"))
                .andExpect(status().isNoContent());

        var ordered = jdbc.sql("SELECT id FROM subject WHERE exam_version_id=:id ORDER BY sort_order")
                .param("id", examVersionId).query(java.util.UUID.class).list();
        org.assertj.core.api.Assertions.assertThat(ordered).containsExactly(secondId, firstId);
    }

    @Test
    void authenticatedAdminCanReadReadyStatus() throws Exception {
        mvc.perform(get("/api/v1/status")
                        .header("X-Admin-Identity", "author-1")
                        .header("X-Admin-Roles", "CONTENT_AUTHOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("content-service"))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void missingDevelopmentIdentityFailsClosed() throws Exception {
        mvc.perform(get("/api/v1/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void identityWithoutRecognizedRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/status")
                        .header("X-Admin-Identity", "restricted-1")
                        .header("X-Admin-Roles", "SUPPORT"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void actuatorHealthStartsSuccessfullyWithoutAdminHeaders() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void flywayAppliesTheOwnedFoundationMigration() {
        var versions = jdbc.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                .query(String.class).list();
        org.assertj.core.api.Assertions.assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
    }

    @Test
    void reportsAreAuthorizedAndAuditEventsAreAppendOnly() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/content-health").headers(author()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.questionsMissingFacts").isNumber());
        mvc.perform(get("/api/v1/admin/reports/review-health").headers(reviewer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").isNumber());
        mvc.perform(get("/api/v1/admin/reports/source-health").headers(author()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unused").isNumber());
        mvc.perform(get("/api/v1/admin/reports/release-health").headers(publisher()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").isNumber());
        var code="AUDIT_"+java.util.UUID.randomUUID();
        mvc.perform(post("/api/v1/admin/exams").headers(author()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\""+code+"\",\"name\":\"Audit test\",\"countryCode\":\"SE\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/audit-events").headers(author()).queryParam("entityType","Exam").queryParam("action","CREATE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].newState.code").value(code));
        var auditId=jdbc.sql("SELECT id FROM audit_event WHERE new_state->>'code'=:code").param("code",code).query(java.util.UUID.class).single();
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.sql("DELETE FROM audit_event WHERE id=:id").param("id",auditId).update())
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void authorCanCreateListAndUpdateExamWithOptimisticLocking() throws Exception {
        var code="STRUCTURE_"+java.util.UUID.randomUUID();
        var body = "{\"code\":\""+code+"\",\"name\":\"Swedish Citizenship\",\"countryCode\":\"SE\",\"status\":\"DRAFT\"}";
        var created=mvc.perform(post("/api/v1/admin/exams").headers(author()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asText();
        mvc.perform(get("/api/v1/admin/exams").queryParam("search", code).headers(author())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        var update = "{\"code\":\""+code+"\",\"name\":\"Updated\",\"countryCode\":\"SE\",\"status\":\"DRAFT\",\"version\":0}";
        mvc.perform(put("/api/v1/admin/exams/"+id).headers(author()).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/v1/admin/exams/"+id).headers(author()).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/exams").headers(author()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }

    @Test
    void sourceValidationWorkflowAndAuthorizationAreEnforced() throws Exception {
        var invalid = """
                {"publisher":"Authority","title":"Source","url":"http://authority.se/x","sourceType":"GOVERNMENT_WEBPAGE","accessedAt":"2026-07-20"}
                """;
        mvc.perform(post("/api/v1/admin/sources").headers(author()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isUnprocessableEntity());
        var valid=invalid.replace("http://","https://");
        var created=mvc.perform(post("/api/v1/admin/sources").headers(author()).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asText();
        mvc.perform(post("/api/v1/admin/sources/"+id+"/review").headers(author()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/sources/"+id+"/review").headers(reviewer()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("REVIEWED"));
    }

    @Test
    void knowledgeFactCanBeCreatedSubmittedApprovedAndVersioned() throws Exception {
        var now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);
        var exam = java.util.UUID.randomUUID(); var examVersion = java.util.UUID.randomUUID();
        var subject = java.util.UUID.randomUUID(); var topic = java.util.UUID.randomUUID(); var source = java.util.UUID.randomUUID();
        jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Knowledge','SE','DRAFT',:now,:now)").param("id",exam).param("code","KNOWLEDGE_"+exam).param("now",now).update();
        jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'V1','V1','DRAFT',:now,:now)").param("id",examVersion).param("exam",exam).param("now",now).update();
        jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:parent,'S','Subject',0,'DRAFT',:now,:now)").param("id",subject).param("parent",examVersion).param("now",now).update();
        jdbc.sql("INSERT INTO topic(id,subject_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:parent,'T','Topic',0,'DRAFT',:now,:now)").param("id",topic).param("parent",subject).param("now",now).update();
        jdbc.sql("INSERT INTO source_reference(id,publisher,title,source_type,accessed_at,review_status,status,created_at,updated_at) VALUES(:id,'Authority','Source','GOVERNMENT_DOCUMENT',CURRENT_DATE,'REVIEWED','ACTIVE',:now,:now)").param("id",source).param("now",now).update();

        var objectiveBody="{\"topicId\":\""+topic+"\",\"code\":\"ROLE\",\"title\":\"Understand the role\",\"status\":\"DRAFT\"}";
        var objectiveJson=mvc.perform(post("/api/v1/admin/learning-objectives").headers(author()).contentType(MediaType.APPLICATION_JSON).content(objectiveBody)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var objectiveId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(objectiveJson).get("id").asText();
        var factBody="{\"learningObjectiveId\":\""+objectiveId+"\",\"canonicalStatement\":\"Sweden has municipalities.\",\"sourceIds\":[\""+source+"\"]}";
        var factJson=mvc.perform(post("/api/v1/admin/knowledge-facts").headers(author()).contentType(MediaType.APPLICATION_JSON).content(factBody)).andExpect(status().isCreated()).andExpect(jsonPath("$.reviewStatus").value("UNREVIEWED")).andReturn().getResponse().getContentAsString();
        var factId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(factJson).get("id").asText();
        mvc.perform(post("/api/v1/admin/knowledge-facts/"+factId+"/submit").headers(author()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("UNDER_REVIEW"));
        mvc.perform(post("/api/v1/admin/knowledge-facts/"+factId+"/approve").headers(author()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/knowledge-facts/"+factId+"/approve").headers(reviewer()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("APPROVED")).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/admin/knowledge-facts").queryParam("search","municipalities").headers(author())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        var update=factBody.substring(0,factBody.length()-1)+",\"version\":2}";
        mvc.perform(put("/api/v1/admin/knowledge-facts/"+factId).headers(author()).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("UNREVIEWED")).andExpect(jsonPath("$.status").value("DRAFT"));
        mvc.perform(get("/api/v1/admin/knowledge-facts/"+factId+"/versions").headers(author())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].reviewStatus").value("APPROVED"));
    }

    @Test
    void questionEndpointCreatesValidatesReviewsSearchesAndVersionsApprovedQuestions() throws Exception {
        var now=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);var exam=java.util.UUID.randomUUID();var examVersion=java.util.UUID.randomUUID();var subject=java.util.UUID.randomUUID();var topic=java.util.UUID.randomUUID();var objective=java.util.UUID.randomUUID();var fact=java.util.UUID.randomUUID();var factVersion=java.util.UUID.randomUUID();var source=java.util.UUID.randomUUID();
        jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Questions','SE','DRAFT',:now,:now)").param("id",exam).param("code","Swedish Citizenship").param("now",now).update();
        jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'V1','V1','DRAFT',:now,:now)").param("id",examVersion).param("exam",exam).param("now",now).update();
        jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:parent,'S','Subject',0,'ACTIVE',:now,:now)").param("id",subject).param("parent",examVersion).param("now",now).update();
        jdbc.sql("INSERT INTO topic(id,subject_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:parent,'T','Topic',0,'ACTIVE',:now,:now)").param("id",topic).param("parent",subject).param("now",now).update();
        jdbc.sql("INSERT INTO learning_objective(id,topic_id,code,title,status,created_at,updated_at) VALUES(:id,:topic,'Q','Question objective','ACTIVE',:now,:now)").param("id",objective).param("topic",topic).param("now",now).update();
        jdbc.sql("INSERT INTO knowledge_fact(id,learning_objective_id,canonical_statement,review_status,status,created_at,updated_at) VALUES(:id,:objective,'Sweden has 290 municipalities.','APPROVED','ACTIVE',:now,:now)").param("id",fact).param("objective",objective).param("now",now).update();
        jdbc.sql("INSERT INTO knowledge_fact_version(id,knowledge_fact_id,version_number,canonical_statement,review_status,author_id,reviewer_id,created_at,updated_at) VALUES(:id,:fact,1,'Sweden has 290 municipalities.','APPROVED','fact-author','fact-reviewer',:now,:now)").param("id",factVersion).param("fact",fact).param("now",now).update();
        jdbc.sql("UPDATE knowledge_fact SET current_version_id=:version WHERE id=:id").param("version",factVersion).param("id",fact).update();
        jdbc.sql("INSERT INTO source_reference(id,publisher,title,source_type,accessed_at,review_status,status,created_at,updated_at) VALUES(:id,'Authority','Release source','GOVERNMENT_DOCUMENT',CURRENT_DATE,'REVIEWED','ACTIVE',:now,:now)").param("id",source).param("now",now).update();
        jdbc.sql("INSERT INTO knowledge_fact_source(knowledge_fact_version_id,source_reference_id) VALUES(:fact,:source)").param("fact",factVersion).param("source",source).update();
        var body="{\"learningObjectiveId\":\""+objective+"\",\"code\":\"MUNICIPALITIES_"+fact+"\",\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"How many municipalities?\",\"difficulty\":\"EASY\",\"explanation\":\"The approved fact provides the answer.\",\"factIds\":[\""+fact+"\"],\"tags\":[\"municipalities\"],\"options\":[{\"displayOrder\":0,\"text\":\"290\",\"correct\":true},{\"displayOrder\":1,\"text\":\"300\",\"correct\":false}]}";
        var json=mvc.perform(post("/api/v1/admin/questions").headers(author()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.options.length()").value(2)).andExpect(jsonPath("$.knowledgeFactCount").value(1)).andReturn().getResponse().getContentAsString();
        var id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("id").asText();
        mvc.perform(get("/api/v1/admin/questions/search").queryParam("search","municipalities").headers(author())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(post("/api/v1/admin/questions/"+id+"/submit").headers(author()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("UNDER_REVIEW"));
        var queueJson=mvc.perform(get("/api/v1/admin/reviews").headers(reviewer()).queryParam("contentType","QUESTION")).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1)).andReturn().getResponse().getContentAsString();
        var reviewNode=new com.fasterxml.jackson.databind.ObjectMapper().readTree(queueJson).get("items").get(0);var reviewId=reviewNode.get("id").asText();
        mvc.perform(post("/api/v1/admin/reviews/"+reviewId+"/claim").headers(reviewer()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.assignedReviewerId").value("reviewer"));
        mvc.perform(post("/api/v1/admin/reviews/"+reviewId+"/claim").headers(reviewer()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIEW_ITEM_ALREADY_CLAIMED"));
        mvc.perform(post("/api/v1/admin/reviews/"+reviewId+"/comments").headers(reviewer()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"body\":\"The supporting context has been reviewed.\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.comments.length()").value(1));
        mvc.perform(get("/api/v1/admin/reviews/"+reviewId+"/history").headers(reviewer())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(post("/api/v1/admin/questions/"+id+"/approve").headers(author()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/questions/"+id+"/approve").headers(reviewer()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        var releaseBody="{\"examVersionId\":\""+examVersion+"\",\"releaseNumber\":\"2026.1\",\"displayName\":\"MVP release\"}";
        var releaseJson=mvc.perform(post("/api/v1/admin/releases").headers(publisher()).contentType(MediaType.APPLICATION_JSON).content(releaseBody)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString();
        var releaseId=new com.fasterxml.jackson.databind.ObjectMapper().readTree(releaseJson).get("id").asText();
        mvc.perform(put("/api/v1/admin/releases/"+releaseId+"/selection").headers(publisher()).contentType(MediaType.APPLICATION_JSON).content("{\"questionIds\":[\""+id+"\"],\"factIds\":[],\"version\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.questionCount").value(1)).andExpect(jsonPath("$.knowledgeFactCount").value(1));
        mvc.perform(post("/api/v1/admin/releases/"+releaseId+"/validate").headers(publisher()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/v1/admin/releases/"+releaseId+"/publish").headers(publisher()).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED")).andExpect(jsonPath("$.checksum").isNotEmpty());
        mvc.perform(get("/api/v1/admin/releases/"+releaseId+"/snapshot").headers(publisher())).andExpect(status().isOk()).andExpect(jsonPath("$.snapshot.externalReleaseId").value(releaseId)).andExpect(jsonPath("$.snapshot.examId").value("swedish-citizenship")).andExpect(jsonPath("$.snapshot.subjects[0].topics[0].questions[0].id").value(id));
        mvc.perform(delete("/api/v1/admin/questions/"+id).headers(author())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTITY_ALREADY_HISTORICAL"));
        var update=body.substring(0,body.length()-1)+",\"version\":2}";
        mvc.perform(put("/api/v1/admin/questions/"+id).headers(author()).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk()).andExpect(jsonPath("$.reviewStatus").value("UNREVIEWED"));
        mvc.perform(get("/api/v1/admin/questions/"+id+"/versions").headers(author())).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[1].reviewStatus").value("APPROVED"));
    }

    @Test
    void safelyDeletesEmptyDraftHierarchyAndCreatesAttributedAuditEvents() throws Exception {
        var now=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);var exam=java.util.UUID.randomUUID();var version=java.util.UUID.randomUUID();var subject=java.util.UUID.randomUUID();var topic=java.util.UUID.randomUUID();
        jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Delete','SE','DRAFT',:now,:now)").param("id",exam).param("code","DELETE_"+exam).param("now",now).update();
        jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'D1','Draft','DRAFT',:now,:now)").param("id",version).param("exam",exam).param("now",now).update();
        jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:version,'S','Subject',0,'DRAFT',:now,:now)").param("id",subject).param("version",version).param("now",now).update();
        jdbc.sql("INSERT INTO topic(id,subject_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:subject,'T','Topic',0,'DRAFT',:now,:now)").param("id",topic).param("subject",subject).param("now",now).update();
        mvc.perform(delete("/api/v1/admin/topics/"+topic).headers(admin()).queryParam("reason","Created accidentally")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/subjects/"+subject).headers(admin())).andExpect(status().isNoContent());
        var release=java.util.UUID.randomUUID();jdbc.sql("INSERT INTO content_release(id,exam_id,exam_version_id,release_number,display_name,status,created_by,created_at,updated_at) VALUES(:id,:exam,:version,:number,'Draft','DRAFT','publisher',:now,:now)").param("id",release).param("exam",exam).param("version",version).param("number","D-"+release).param("now",now).update();
        mvc.perform(delete("/api/v1/admin/releases/"+release).headers(publisher())).andExpect(status().isNoContent());
        var source=java.util.UUID.randomUUID();jdbc.sql("INSERT INTO source_reference(id,publisher,title,source_type,accessed_at,review_status,status,created_at,updated_at) VALUES(:id,'Publisher','Unused','OTHER',CURRENT_DATE,'UNREVIEWED','ACTIVE',:now,:now)").param("id",source).param("now",now).update();
        mvc.perform(delete("/api/v1/admin/sources/"+source).headers(admin())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/exam-versions/"+version).headers(admin())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/admin/exams/"+exam).headers(admin())).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbc.sql("SELECT actor_id FROM audit_event WHERE entity_id=:id AND action='DELETE'").param("id",topic).query(String.class).single()).isEqualTo("admin");
        org.assertj.core.api.Assertions.assertThat(jdbc.sql("SELECT previous_state->>'name' FROM audit_event WHERE entity_id=:id AND action='DELETE'").param("id",topic).query(String.class).single()).isEqualTo("Topic");
    }

    @Test
    void rejectsDependenciesHistoricalContentAndUnauthorizedDeletionWithoutRawConstraintErrors() throws Exception {
        var now=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);var exam=java.util.UUID.randomUUID();var version=java.util.UUID.randomUUID();var subject=java.util.UUID.randomUUID();var topic=java.util.UUID.randomUUID();var objective=java.util.UUID.randomUUID();
        jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Blocked','SE','DRAFT',:now,:now)").param("id",exam).param("code","BLOCK_"+exam).param("now",now).update();
        jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'B1','Blocked','DRAFT',:now,:now)").param("id",version).param("exam",exam).param("now",now).update();
        jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:version,'S','Subject',0,'DRAFT',:now,:now)").param("id",subject).param("version",version).param("now",now).update();
        jdbc.sql("INSERT INTO topic(id,subject_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:subject,'T','Topic',0,'DRAFT',:now,:now)").param("id",topic).param("subject",subject).param("now",now).update();
        jdbc.sql("INSERT INTO learning_objective(id,topic_id,code,title,status,created_at,updated_at) VALUES(:id,:topic,'O','Objective','DRAFT',:now,:now)").param("id",objective).param("topic",topic).param("now",now).update();
        mvc.perform(delete("/api/v1/admin/topics/"+topic).headers(admin())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTITY_HAS_DEPENDENCIES")).andExpect(jsonPath("$.errors[0].type").value("LEARNING_OBJECTIVE"));
        mvc.perform(delete("/api/v1/admin/topics/"+topic).headers(author())).andExpect(status().isForbidden());
        var release=java.util.UUID.randomUUID();jdbc.sql("INSERT INTO content_release(id,exam_id,exam_version_id,release_number,display_name,status,created_by,created_at,updated_at) VALUES(:id,:exam,:version,:number,'Published','PUBLISHED','publisher',:now,:now)").param("id",release).param("exam",exam).param("version",version).param("number","P-"+release).param("now",now).update();
        mvc.perform(delete("/api/v1/admin/releases/"+release).headers(admin())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ENTITY_ALREADY_HISTORICAL"));
    }

    @Test
    void authorsDeleteOnlyTheirOwnUnusedUnreviewedDraftFactsAndQuestions() throws Exception {
        var now=java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC);var exam=java.util.UUID.randomUUID();var version=java.util.UUID.randomUUID();var subject=java.util.UUID.randomUUID();var topic=java.util.UUID.randomUUID();var objective=java.util.UUID.randomUUID();
        jdbc.sql("INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at) VALUES(:id,:code,'Owned','SE','DRAFT',:now,:now)").param("id",exam).param("code","OWN_"+exam).param("now",now).update();jdbc.sql("INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at) VALUES(:id,:exam,'O1','Owned','DRAFT',:now,:now)").param("id",version).param("exam",exam).param("now",now).update();jdbc.sql("INSERT INTO subject(id,exam_version_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:version,'S','S',0,'DRAFT',:now,:now)").param("id",subject).param("version",version).param("now",now).update();jdbc.sql("INSERT INTO topic(id,subject_id,code,name,sort_order,status,created_at,updated_at) VALUES(:id,:subject,'T','T',0,'DRAFT',:now,:now)").param("id",topic).param("subject",subject).param("now",now).update();jdbc.sql("INSERT INTO learning_objective(id,topic_id,code,title,status,created_at,updated_at) VALUES(:id,:topic,'O','O','DRAFT',:now,:now)").param("id",objective).param("topic",topic).param("now",now).update();
        var fact=java.util.UUID.randomUUID();var factVersion=java.util.UUID.randomUUID();jdbc.sql("INSERT INTO knowledge_fact(id,learning_objective_id,canonical_statement,review_status,status,created_at,updated_at) VALUES(:id,:objective,'Draft fact','UNREVIEWED','DRAFT',:now,:now)").param("id",fact).param("objective",objective).param("now",now).update();jdbc.sql("INSERT INTO knowledge_fact_version(id,knowledge_fact_id,version_number,canonical_statement,review_status,author_id,created_at,updated_at) VALUES(:id,:fact,1,'Draft fact','UNREVIEWED','author',:now,:now)").param("id",factVersion).param("fact",fact).param("now",now).update();jdbc.sql("UPDATE knowledge_fact SET current_version_id=:version WHERE id=:id").param("version",factVersion).param("id",fact).update();
        mvc.perform(delete("/api/v1/admin/knowledge-facts/"+fact).headers(reviewer())).andExpect(status().isForbidden());mvc.perform(delete("/api/v1/admin/knowledge-facts/"+fact).headers(author())).andExpect(status().isNoContent());
        var question=java.util.UUID.randomUUID();var questionVersion=java.util.UUID.randomUUID();jdbc.sql("INSERT INTO question(id,learning_objective_id,code,question_type,question_text,difficulty,review_status,status,created_at,updated_at) VALUES(:id,:objective,:code,'SINGLE_CHOICE','Draft?','EASY','UNREVIEWED','DRAFT',:now,:now)").param("id",question).param("objective",objective).param("code","Q"+question).param("now",now).update();jdbc.sql("INSERT INTO question_version(id,question_id,version_number,learning_objective_id,question_type,question_text,difficulty,review_status,author_id,created_at,updated_at) VALUES(:id,:question,1,:objective,'SINGLE_CHOICE','Draft?','EASY','UNREVIEWED','author',:now,:now)").param("id",questionVersion).param("question",question).param("objective",objective).param("now",now).update();jdbc.sql("UPDATE question SET current_version_id=:version WHERE id=:id").param("version",questionVersion).param("id",question).update();
        mvc.perform(delete("/api/v1/admin/questions/"+question).headers(author())).andExpect(status().isNoContent());
    }

    private static org.springframework.http.HttpHeaders author(){var h=new org.springframework.http.HttpHeaders();h.set("X-Admin-Identity","author");h.set("X-Admin-Roles","CONTENT_AUTHOR");return h;}
    private static org.springframework.http.HttpHeaders reviewer(){var h=new org.springframework.http.HttpHeaders();h.set("X-Admin-Identity","reviewer");h.set("X-Admin-Roles","CONTENT_REVIEWER");return h;}
    private static org.springframework.http.HttpHeaders publisher(){var h=new org.springframework.http.HttpHeaders();h.set("X-Admin-Identity","publisher");h.set("X-Admin-Roles","CONTENT_PUBLISHER");return h;}
    private static org.springframework.http.HttpHeaders admin(){var h=new org.springframework.http.HttpHeaders();h.set("X-Admin-Identity","admin");h.set("X-Admin-Roles","ADMIN");return h;}
}
