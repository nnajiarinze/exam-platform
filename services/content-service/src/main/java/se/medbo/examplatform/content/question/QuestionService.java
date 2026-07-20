package se.medbo.examplatform.content.question;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.content.shared.DomainException;

@Service
public class QuestionService {
    private static final Logger LOG = LoggerFactory.getLogger(QuestionService.class);
    private static final String FIELDS = "q.id,q.learning_objective_id AS \"learningObjectiveId\",q.current_version_id AS \"currentVersionId\",q.code,q.question_type AS \"questionType\",q.question_text AS \"questionText\",q.difficulty,q.review_status AS \"reviewStatus\",q.status,q.created_at AS \"createdAt\",q.updated_at AS \"updatedAt\",q.version,lo.title AS \"learningObjectiveTitle\",t.id AS \"topicId\",t.name AS \"topicName\",s.id AS \"subjectId\",s.name AS \"subjectName\",(SELECT count(*) FROM question_knowledge_fact qkf WHERE qkf.question_version_id=q.current_version_id) AS \"knowledgeFactCount\"";
    private static final String JOIN = " FROM question q JOIN learning_objective lo ON lo.id=q.learning_objective_id JOIN topic t ON t.id=lo.topic_id JOIN subject s ON s.id=t.subject_id ";
    private final JdbcClient jdbc;
    private final QuestionValidator validator;

    public QuestionService(JdbcClient jdbc, QuestionValidator validator) { this.jdbc = jdbc; this.validator = validator; }
    public record OptionInput(UUID id, Integer displayOrder, String text, boolean correct, String feedback) {}
    public record QuestionInput(UUID learningObjectiveId, String code, String questionType, String questionText, String difficulty, String explanation, List<UUID> factIds, List<String> tags, List<OptionInput> options, Boolean trueFalseCorrect, Long version) {}
    public record ActionInput(Long version, String reason) {}

    public Map<String,Object> questions(int page,int size,String search,UUID objectiveId,String type,String difficulty,String review,String status) {
        page=Math.max(page,0); size=Math.min(Math.max(size,1),100);
        var where=" WHERE (CAST(:search AS text) IS NULL OR lower(q.question_text) LIKE lower('%'||:search||'%') OR lower(q.code) LIKE lower('%'||:search||'%') OR EXISTS(SELECT 1 FROM question_knowledge_fact qkf JOIN knowledge_fact_version kfv ON kfv.id=qkf.knowledge_fact_version_id WHERE qkf.question_version_id=q.current_version_id AND lower(kfv.canonical_statement) LIKE lower('%'||:search||'%'))) AND (CAST(:objective AS uuid) IS NULL OR q.learning_objective_id=:objective) AND (CAST(:type AS text) IS NULL OR q.question_type=:type) AND (CAST(:difficulty AS text) IS NULL OR q.difficulty=:difficulty) AND (CAST(:review AS text) IS NULL OR q.review_status=:review) AND (CAST(:status AS text) IS NULL OR q.status=:status) ";
        var count=jdbc.sql("SELECT count(*)"+JOIN+where); params(count,search,objectiveId,type,difficulty,review,status); long total=count.query(Long.class).single();
        var query=jdbc.sql("SELECT "+FIELDS+JOIN+where+" ORDER BY q.updated_at DESC,q.id LIMIT :size OFFSET :offset"); params(query,search,objectiveId,type,difficulty,review,status);
        return Map.of("items",query.param("size",size).param("offset",page*size).query().listOfRows(),"page",page,"size",size,"totalItems",total,"totalPages",(total+size-1)/size);
    }

    public Map<String,Object> question(UUID id) {
        var value=one("SELECT "+FIELDS+JOIN+" WHERE q.id=:id",id,"Question"); UUID current=(UUID)value.get("currentVersionId");
        value.put("options",options(current)); value.put("knowledgeFacts",facts(current)); value.put("factIds",factIds(current)); value.put("tags",tags(current));
        var version=one("SELECT explanation,author_id AS \"authorId\",reviewer_id AS \"reviewerId\",review_note AS \"reviewNote\" FROM question_version WHERE id=:id",current,"Question version"); value.putAll(version); return value;
    }

    public List<Map<String,Object>> versions(UUID questionId) {
        exists("question",questionId,"Question"); var rows=jdbc.sql("SELECT id,question_id AS \"questionId\",version_number AS \"versionNumber\",learning_objective_id AS \"learningObjectiveId\",question_type AS \"questionType\",question_text AS \"questionText\",difficulty,explanation,review_status AS \"reviewStatus\",author_id AS \"authorId\",reviewer_id AS \"reviewerId\",review_note AS \"reviewNote\",created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM question_version WHERE question_id=:id ORDER BY version_number DESC").param("id",questionId).query().listOfRows();
        rows.forEach(row->{UUID versionId=(UUID)row.get("id");row.put("options",options(versionId));row.put("knowledgeFacts",facts(versionId));row.put("tags",tags(versionId));}); return rows;
    }

    @Transactional public Map<String,Object> create(QuestionInput input) {
        validator.validate(input); validateReferences(input); var id=UUID.randomUUID(); var versionId=UUID.randomUUID(); var now=now();
        try { jdbc.sql("INSERT INTO question(id,learning_objective_id,code,question_type,question_text,difficulty,review_status,status,created_at,updated_at) VALUES(:id,:objective,:code,:type,:text,:difficulty,'UNREVIEWED','DRAFT',:now,:now)").param("id",id).param("objective",input.learningObjectiveId()).param("code",input.code().trim()).param("type",input.questionType()).param("text",input.questionText().trim()).param("difficulty",input.difficulty()).param("now",now).update(); }
        catch (org.springframework.dao.DuplicateKeyException e) { throw conflict("Question code already exists"); }
        insertVersion(versionId,id,1,input,now); jdbc.sql("UPDATE question SET current_version_id=:version WHERE id=:id").param("version",versionId).param("id",id).update(); insertChildren(versionId,input); log("question.created",id); return question(id);
    }

    @Transactional public Map<String,Object> update(UUID id,QuestionInput input) {
        validator.validate(input); expect(input.version()); validateReferences(input); var current=question(id); if("UNDER_REVIEW".equals(current.get("reviewStatus"))) throw conflict("A question under review cannot be edited"); var now=now();
        UUID versionId=(UUID)current.get("currentVersionId");
        if("APPROVED".equals(current.get("reviewStatus"))) { int number=jdbc.sql("SELECT coalesce(max(version_number),0)+1 FROM question_version WHERE question_id=:id").param("id",id).query(Integer.class).single(); versionId=UUID.randomUUID(); updateAggregate(id,input,now); insertVersion(versionId,id,number,input,now); jdbc.sql("UPDATE question SET current_version_id=:current WHERE id=:id").param("current",versionId).param("id",id).update(); }
        else { updateAggregate(id,input,now); jdbc.sql("UPDATE question_version SET learning_objective_id=:objective,question_type=:type,question_text=:text,difficulty=:difficulty,explanation=:explanation,review_status='UNREVIEWED',updated_at=:now WHERE id=:id AND review_status<>'APPROVED'").param("objective",input.learningObjectiveId()).param("type",input.questionType()).param("text",input.questionText().trim()).param("difficulty",input.difficulty()).param("explanation",blank(input.explanation()),Types.VARCHAR).param("now",now).param("id",versionId).update(); deleteChildren(versionId); }
        insertChildren(versionId,input); log("question.updated",id); return question(id);
    }

    @Transactional public Map<String,Object> submit(UUID id,ActionInput in) { expect(in.version()); var q=question(id); if(!List.of("UNREVIEWED","REJECTED","REQUIRES_UPDATE").contains(q.get("reviewStatus"))||"RETIRED".equals(q.get("status"))) throw conflict("Only an editable draft can be submitted"); validateCurrent(q); return transition(id,in.version(),"UNDER_REVIEW",null,null,"question.submitted"); }
    @Transactional public Map<String,Object> approve(UUID id,ActionInput in) { expect(in.version()); var q=question(id); requireReview(q,"UNDER_REVIEW"); if(actor().equals(q.get("authorId"))) throw new DomainException(HttpStatus.FORBIDDEN,"SEPARATION_OF_DUTIES_REQUIRED","An author cannot approve their own question"); validateCurrent(q); return transition(id,in.version(),"APPROVED","ACTIVE",in.reason(),"question.approved"); }
    @Transactional public Map<String,Object> reject(UUID id,ActionInput in) { expect(in.version()); reason(in.reason()); requireReview(question(id),"UNDER_REVIEW"); return transition(id,in.version(),"REJECTED","DRAFT",in.reason(),"question.rejected"); }
    @Transactional public Map<String,Object> requireUpdate(UUID id,ActionInput in) { expect(in.version()); reason(in.reason()); requireReview(question(id),"UNDER_REVIEW"); return transition(id,in.version(),"REQUIRES_UPDATE","DRAFT",in.reason(),"question.requires_update"); }
    @Transactional public Map<String,Object> retire(UUID id,ActionInput in) { expect(in.version()); var q=question(id); if("RETIRED".equals(q.get("status"))) throw conflict("The question is already retired"); int changed=jdbc.sql("UPDATE question SET status='RETIRED',updated_at=:now,version=version+1 WHERE id=:id AND version=:version").param("now",now()).param("id",id).param("version",in.version()).update(); changed(changed,id); log("question.retired",id); return question(id); }

    private void updateAggregate(UUID id,QuestionInput in,OffsetDateTime now) { int changed; try { changed=jdbc.sql("UPDATE question SET learning_objective_id=:objective,code=:code,question_type=:type,question_text=:text,difficulty=:difficulty,review_status='UNREVIEWED',status='DRAFT',updated_at=:now,version=version+1 WHERE id=:id AND version=:version AND status<>'RETIRED'").param("objective",in.learningObjectiveId()).param("code",in.code().trim()).param("type",in.questionType()).param("text",in.questionText().trim()).param("difficulty",in.difficulty()).param("now",now).param("id",id).param("version",in.version()).update(); } catch(org.springframework.dao.DuplicateKeyException e){throw conflict("Question code already exists");} changed(changed,id); }
    private Map<String,Object> transition(UUID id,long expected,String review,String lifecycle,String note,String action) { var q=question(id); var now=now(); var status=lifecycle==null?(String)q.get("status"):lifecycle; int c=jdbc.sql("UPDATE question SET review_status=:review,status=:status,updated_at=:now,version=version+1 WHERE id=:id AND version=:version").param("review",review).param("status",status).param("now",now).param("id",id).param("version",expected).update(); changed(c,id); jdbc.sql("UPDATE question_version SET review_status=:review,reviewer_id=:reviewer,review_note=:note,updated_at=:now WHERE id=:id").param("review",review).param("reviewer","UNDER_REVIEW".equals(review)?null:actor(),Types.VARCHAR).param("note",blank(note),Types.VARCHAR).param("now",now).param("id",q.get("currentVersionId")).update(); log(action,id); return question(id); }
    private void insertVersion(UUID versionId,UUID questionId,int number,QuestionInput in,OffsetDateTime now) { jdbc.sql("INSERT INTO question_version(id,question_id,version_number,learning_objective_id,question_type,question_text,difficulty,explanation,review_status,author_id,created_at,updated_at) VALUES(:id,:question,:number,:objective,:type,:text,:difficulty,:explanation,'UNREVIEWED',:author,:now,:now)").param("id",versionId).param("question",questionId).param("number",number).param("objective",in.learningObjectiveId()).param("type",in.questionType()).param("text",in.questionText().trim()).param("difficulty",in.difficulty()).param("explanation",blank(in.explanation()),Types.VARCHAR).param("author",actor()).param("now",now).update(); }
    private void insertChildren(UUID versionId,QuestionInput in) { var options="TRUE_FALSE".equals(in.questionType())?List.of(new OptionInput(null,0,"True",Boolean.TRUE.equals(in.trueFalseCorrect()),null),new OptionInput(null,1,"False",!Boolean.TRUE.equals(in.trueFalseCorrect()),null)):in.options(); for(var o:options) jdbc.sql("INSERT INTO question_option(id,question_version_id,display_order,text,correct,feedback) VALUES(:id,:version,:order,:text,:correct,:feedback)").param("id",o.id()==null?UUID.randomUUID():o.id()).param("version",versionId).param("order",o.displayOrder()).param("text",o.text().trim()).param("correct",o.correct()).param("feedback",blank(o.feedback()),Types.VARCHAR).update(); for(UUID factId:in.factIds()){UUID factVersion=approvedFactVersion(factId);jdbc.sql("INSERT INTO question_knowledge_fact(question_version_id,knowledge_fact_version_id) VALUES(:question,:fact)").param("question",versionId).param("fact",factVersion).update();} if(in.tags()!=null)for(String tag:in.tags().stream().map(String::trim).filter(v->!v.isEmpty()).distinct().toList())jdbc.sql("INSERT INTO question_tag(question_version_id,tag) VALUES(:version,:tag)").param("version",versionId).param("tag",tag).update(); }
    private void deleteChildren(UUID versionId) { jdbc.sql("DELETE FROM question_option WHERE question_version_id=:id").param("id",versionId).update(); jdbc.sql("DELETE FROM question_knowledge_fact WHERE question_version_id=:id").param("id",versionId).update(); jdbc.sql("DELETE FROM question_tag WHERE question_version_id=:id").param("id",versionId).update(); }
    private void validateReferences(QuestionInput in) { exists("learning_objective",in.learningObjectiveId(),"Learning objective"); for(UUID id:in.factIds()){var objective=jdbc.sql("SELECT learning_objective_id FROM knowledge_fact WHERE id=:id").param("id",id).query(UUID.class).optional().orElseThrow(()->DomainException.notFound("Knowledge fact")); approvedFactVersion(id); if(!in.learningObjectiveId().equals(objective))throw validation("Knowledge facts must belong to the question learning objective");} }
    private void validateCurrent(Map<String,Object> q) { if(factIds((UUID)q.get("currentVersionId")).isEmpty())throw validation("At least one approved knowledge fact is required"); }
    private UUID approvedFactVersion(UUID id){return jdbc.sql("SELECT current_version_id FROM knowledge_fact WHERE id=:id AND review_status='APPROVED' AND status='ACTIVE'").param("id",id).query(UUID.class).optional().orElseThrow(()->validation("Only approved active knowledge facts can be linked"));}
    private List<Map<String,Object>> options(UUID id){return jdbc.sql("SELECT id,display_order AS \"displayOrder\",text,correct,feedback FROM question_option WHERE question_version_id=:id ORDER BY display_order").param("id",id).query().listOfRows();}
    private List<Map<String,Object>> facts(UUID id){return jdbc.sql("SELECT f.id,kfv.id AS \"versionId\",kfv.canonical_statement AS \"canonicalStatement\" FROM question_knowledge_fact qkf JOIN knowledge_fact_version kfv ON kfv.id=qkf.knowledge_fact_version_id JOIN knowledge_fact f ON f.id=kfv.knowledge_fact_id WHERE qkf.question_version_id=:id ORDER BY kfv.canonical_statement").param("id",id).query().listOfRows();}
    private List<UUID> factIds(UUID id){return facts(id).stream().map(row->(UUID)row.get("id")).toList();}
    private List<String> tags(UUID id){return jdbc.sql("SELECT tag FROM question_tag WHERE question_version_id=:id ORDER BY tag").param("id",id).query(String.class).list();}
    private void params(JdbcClient.StatementSpec q,String search,UUID objective,String type,String difficulty,String review,String status){q.param("search",blank(search)).param("objective",objective).param("type",blank(type)).param("difficulty",blank(difficulty)).param("review",blank(review)).param("status",blank(status));}
    private Map<String,Object> one(String sql,UUID id,String label){var rows=jdbc.sql(sql).param("id",id).query().listOfRows();if(rows.isEmpty())throw DomainException.notFound(label);return rows.getFirst();}
    private void exists(String table,UUID id,String label){if(id==null||!jdbc.sql("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE id=:id)").param("id",id).query(Boolean.class).single())throw DomainException.notFound(label);}
    private void changed(int count,UUID id){if(count==0){exists("question",id,"Question");throw conflict("The resource was changed by another administrator");}}
    private void requireReview(Map<String,Object> q,String expected){if(!expected.equals(q.get("reviewStatus")))throw conflict("Invalid question review transition");}
    private void expect(Long version){if(version==null||version<0)throw validation("version is required");}
    private void reason(String value){if(value==null||value.isBlank())throw validation("reason is required");}
    private DomainException validation(String message){return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_ERROR",message);}
    private DomainException conflict(String message){return DomainException.conflict(message);}
    private String blank(String value){return value==null||value.isBlank()?null:value;}
    private String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private void log(String action,UUID id){LOG.info("content_action={} actor={} entity_id={}",action,actor(),id);}
}
