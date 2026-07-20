package se.medbo.examplatform.content.knowledge;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Types;
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
import se.medbo.examplatform.content.review.ReviewWorkflowService;

@Service
public class KnowledgeBaseService {
    private static final Logger LOG = LoggerFactory.getLogger(KnowledgeBaseService.class);
    private static final String OBJECTIVE_FIELDS = "lo.id,lo.topic_id AS \"topicId\",lo.code,lo.title,lo.description,lo.status,lo.created_at AS \"createdAt\",lo.updated_at AS \"updatedAt\",lo.version,t.name AS \"topicName\",s.id AS \"subjectId\",s.name AS \"subjectName\"";
    private static final String FACT_FIELDS = "f.id,f.learning_objective_id AS \"learningObjectiveId\",f.current_version_id AS \"currentVersionId\",f.canonical_statement AS \"canonicalStatement\",f.review_status AS \"reviewStatus\",f.status,f.valid_from AS \"validFrom\",f.valid_to AS \"validTo\",f.created_at AS \"createdAt\",f.updated_at AS \"updatedAt\",f.version,lo.title AS \"learningObjectiveTitle\",t.id AS \"topicId\",t.name AS \"topicName\",s.id AS \"subjectId\",s.name AS \"subjectName\",(SELECT count(*) FROM knowledge_fact_source kfs WHERE kfs.knowledge_fact_version_id=f.current_version_id) AS \"sourceCount\"";
    private static final String FACT_JOIN = " FROM knowledge_fact f JOIN learning_objective lo ON lo.id=f.learning_objective_id JOIN topic t ON t.id=lo.topic_id JOIN subject s ON s.id=t.subject_id ";
    private final JdbcClient jdbc;
    private final ReviewWorkflowService reviews;

    public KnowledgeBaseService(JdbcClient jdbc,ReviewWorkflowService reviews) { this.jdbc = jdbc; this.reviews=reviews; }

    public record ObjectiveInput(UUID topicId, String code, String title, String description, String status, Long version) {}
    public record FactInput(UUID learningObjectiveId, String canonicalStatement, LocalDate validFrom, LocalDate validTo, List<UUID> sourceIds, Long version) {}
    public record ActionInput(Long version, String reason, String reasonCode) {}

    public Map<String,Object> objectives(int page,int size,String search,UUID topicId,String status) {
        page=Math.max(page,0); size=Math.min(Math.max(size,1),100);
        var where=" WHERE (CAST(:search AS text) IS NULL OR lower(lo.title) LIKE lower('%'||:search||'%') OR lower(lo.code) LIKE lower('%'||:search||'%')) AND (CAST(:topic AS uuid) IS NULL OR lo.topic_id=:topic) AND (CAST(:status AS text) IS NULL OR lo.status=:status) ";
        var count=jdbc.sql("SELECT count(*) FROM learning_objective lo"+where).param("search",blank(search)).param("topic",topicId).param("status",blank(status)).query(Long.class).single();
        var items=jdbc.sql("SELECT "+OBJECTIVE_FIELDS+" FROM learning_objective lo JOIN topic t ON t.id=lo.topic_id JOIN subject s ON s.id=t.subject_id"+where+" ORDER BY lo.updated_at DESC,lo.id LIMIT :size OFFSET :offset").param("search",blank(search)).param("topic",topicId).param("status",blank(status)).param("size",size).param("offset",page*size).query().listOfRows();
        return page(items,page,size,count);
    }

    public Map<String,Object> objective(UUID id) { return one("SELECT "+OBJECTIVE_FIELDS+" FROM learning_objective lo JOIN topic t ON t.id=lo.topic_id JOIN subject s ON s.id=t.subject_id WHERE lo.id=:id",id,"Learning objective"); }

    @Transactional public Map<String,Object> createObjective(ObjectiveInput in) {
        validateObjective(in); exists("topic",in.topicId(),"Topic"); var id=UUID.randomUUID(); var now=now();
        jdbc.sql("INSERT INTO learning_objective(id,topic_id,code,title,description,status,created_at,updated_at) VALUES(:id,:topic,:code,:title,:description,:status,:now,:now)").param("id",id).param("topic",in.topicId()).param("code",in.code().trim()).param("title",in.title().trim()).param("description",blank(in.description())).param("status",in.status()).param("now",now).update();
        log("learning_objective.created",id); return objective(id);
    }

    @Transactional public Map<String,Object> updateObjective(UUID id,ObjectiveInput in) {
        validateObjective(in); expect(in.version()); exists("topic",in.topicId(),"Topic");
        int changed=jdbc.sql("UPDATE learning_objective SET topic_id=:topic,code=:code,title=:title,description=:description,status=:status,updated_at=:now,version=version+1 WHERE id=:id AND version=:version AND status<>'ARCHIVED'").param("topic",in.topicId()).param("code",in.code().trim()).param("title",in.title().trim()).param("description",blank(in.description())).param("status",in.status()).param("now",now()).param("id",id).param("version",in.version()).update();
        changed(changed,"learning_objective",id); log("learning_objective.updated",id); return objective(id);
    }

    @Transactional public Map<String,Object> archiveObjective(UUID id,long version) {
        int changed=jdbc.sql("UPDATE learning_objective SET status='ARCHIVED',updated_at=:now,version=version+1 WHERE id=:id AND version=:version AND status<>'ARCHIVED'").param("now",now()).param("id",id).param("version",version).update();
        changed(changed,"learning_objective",id); log("learning_objective.archived",id); return objective(id);
    }

    public Map<String,Object> facts(int page,int size,String search,UUID objectiveId,UUID topicId,UUID subjectId,String publisher,String review,String status,LocalDate validAt) {
        page=Math.max(page,0); size=Math.min(Math.max(size,1),100);
        var where=" WHERE (CAST(:search AS text) IS NULL OR lower(f.canonical_statement) LIKE lower('%'||:search||'%') OR lower(lo.title) LIKE lower('%'||:search||'%') OR lower(t.name) LIKE lower('%'||:search||'%') OR lower(s.name) LIKE lower('%'||:search||'%')) AND (CAST(:objective AS uuid) IS NULL OR f.learning_objective_id=:objective) AND (CAST(:topic AS uuid) IS NULL OR t.id=:topic) AND (CAST(:subject AS uuid) IS NULL OR s.id=:subject) AND (CAST(:publisher AS text) IS NULL OR EXISTS(SELECT 1 FROM knowledge_fact_source kfs JOIN source_reference sr ON sr.id=kfs.source_reference_id WHERE kfs.knowledge_fact_version_id=f.current_version_id AND lower(sr.publisher) LIKE lower('%'||:publisher||'%'))) AND (CAST(:review AS text) IS NULL OR f.review_status=:review) AND (CAST(:status AS text) IS NULL OR f.status=:status) AND (CAST(:validAt AS date) IS NULL OR (f.valid_from IS NULL OR f.valid_from<=:validAt) AND (f.valid_to IS NULL OR f.valid_to>=:validAt)) ";
        var count=jdbc.sql("SELECT count(*)"+FACT_JOIN+where); factParams(count,search,objectiveId,topicId,subjectId,publisher,review,status,validAt); long total=count.query(Long.class).single();
        var query=jdbc.sql("SELECT "+FACT_FIELDS+FACT_JOIN+where+" ORDER BY f.updated_at DESC,f.id LIMIT :size OFFSET :offset"); factParams(query,search,objectiveId,topicId,subjectId,publisher,review,status,validAt); var items=query.param("size",size).param("offset",page*size).query().listOfRows();
        return page(items,page,size,total);
    }

    public Map<String,Object> fact(UUID id) {
        var result=one("SELECT "+FACT_FIELDS+FACT_JOIN+" WHERE f.id=:id",id,"Knowledge fact");
        result.put("sourceIds",sourceIds((UUID)result.get("currentVersionId"))); return result;
    }

    public List<Map<String,Object>> versions(UUID factId) {
        exists("knowledge_fact",factId,"Knowledge fact");
        var rows=jdbc.sql("SELECT id,knowledge_fact_id AS \"knowledgeFactId\",version_number AS \"versionNumber\",canonical_statement AS \"canonicalStatement\",review_status AS \"reviewStatus\",valid_from AS \"validFrom\",valid_to AS \"validTo\",author_id AS \"authorId\",reviewer_id AS \"reviewerId\",review_note AS \"reviewNote\",created_at AS \"createdAt\",updated_at AS \"updatedAt\" FROM knowledge_fact_version WHERE knowledge_fact_id=:id ORDER BY version_number DESC").param("id",factId).query().listOfRows();
        rows.forEach(row->row.put("sourceIds",sourceIds((UUID)row.get("id")))); return rows;
    }

    @Transactional public Map<String,Object> createFact(FactInput in) {
        validateFact(in); exists("learning_objective",in.learningObjectiveId(),"Learning objective"); validateSources(in.sourceIds());
        var id=UUID.randomUUID(); var versionId=UUID.randomUUID(); var now=now(); var actor=actor();
        jdbc.sql("INSERT INTO knowledge_fact(id,learning_objective_id,canonical_statement,review_status,status,valid_from,valid_to,created_at,updated_at) VALUES(:id,:objective,:statement,'UNREVIEWED','DRAFT',:from,:to,:now,:now)").param("id",id).param("objective",in.learningObjectiveId()).param("statement",in.canonicalStatement().trim()).param("from",in.validFrom()).param("to",in.validTo()).param("now",now).update();
        insertVersion(versionId,id,1,in,"UNREVIEWED",actor,now); jdbc.sql("UPDATE knowledge_fact SET current_version_id=:version WHERE id=:id").param("version",versionId).param("id",id).update(); linkSources(versionId,in.sourceIds());
        log("knowledge_fact.created",id); return fact(id);
    }

    @Transactional public Map<String,Object> updateFact(UUID id,FactInput in) {
        validateFact(in); expect(in.version()); exists("learning_objective",in.learningObjectiveId(),"Learning objective"); validateSources(in.sourceIds()); var current=fact(id);
        if("UNDER_REVIEW".equals(current.get("reviewStatus"))) throw conflict("A fact under review cannot be edited");
        var now=now(); var actor=actor();
        if(List.of("APPROVED","REJECTED","REQUIRES_UPDATE").contains(current.get("reviewStatus"))) {
            int number=jdbc.sql("SELECT coalesce(max(version_number),0)+1 FROM knowledge_fact_version WHERE knowledge_fact_id=:id").param("id",id).query(Integer.class).single(); var versionId=UUID.randomUUID();
            int changed=jdbc.sql("UPDATE knowledge_fact SET learning_objective_id=:objective,current_version_id=NULL,canonical_statement=:statement,review_status='UNREVIEWED',status='DRAFT',valid_from=:from,valid_to=:to,updated_at=:now,version=version+1 WHERE id=:id AND version=:version").param("objective",in.learningObjectiveId()).param("statement",in.canonicalStatement().trim()).param("from",in.validFrom()).param("to",in.validTo()).param("now",now).param("id",id).param("version",in.version()).update(); changed(changed,"knowledge_fact",id);
            insertVersion(versionId,id,number,in,"UNREVIEWED",actor,now); jdbc.sql("UPDATE knowledge_fact SET current_version_id=:version WHERE id=:id").param("version",versionId).param("id",id).update(); linkSources(versionId,in.sourceIds()); log("knowledge_fact.version_created",id);
        } else {
            UUID versionId=(UUID)current.get("currentVersionId"); int changed=jdbc.sql("UPDATE knowledge_fact SET learning_objective_id=:objective,canonical_statement=:statement,review_status='UNREVIEWED',valid_from=:from,valid_to=:to,updated_at=:now,version=version+1 WHERE id=:id AND version=:version AND status<>'RETIRED'").param("objective",in.learningObjectiveId()).param("statement",in.canonicalStatement().trim()).param("from",in.validFrom()).param("to",in.validTo()).param("now",now).param("id",id).param("version",in.version()).update(); changed(changed,"knowledge_fact",id);
            jdbc.sql("UPDATE knowledge_fact_version SET canonical_statement=:statement,review_status='UNREVIEWED',valid_from=:from,valid_to=:to,updated_at=:now WHERE id=:id AND review_status<>'APPROVED'").param("statement",in.canonicalStatement().trim()).param("from",in.validFrom()).param("to",in.validTo()).param("now",now).param("id",versionId).update(); jdbc.sql("DELETE FROM knowledge_fact_source WHERE knowledge_fact_version_id=:id").param("id",versionId).update(); linkSources(versionId,in.sourceIds()); log("knowledge_fact.updated",id);
        }
        return fact(id);
    }

    @Transactional public Map<String,Object> submit(UUID id,ActionInput in) { expect(in.version()); var fact=fact(id); if(!List.of("UNREVIEWED","REJECTED","REQUIRES_UPDATE").contains(fact.get("reviewStatus"))||"RETIRED".equals(fact.get("status"))) throw conflict("Only an editable draft can be submitted"); if(sourceIds((UUID)fact.get("currentVersionId")).isEmpty()) throw validation("At least one source is required before submission");boolean resubmit=List.of("REJECTED","REQUIRES_UPDATE").contains(fact.get("reviewStatus"));var result=transition(id,in.version(),"UNDER_REVIEW",null,null,"knowledge_fact.submitted");reviews.submitted("KNOWLEDGE_FACT",id,(UUID)fact.get("currentVersionId"),(String)version((UUID)fact.get("currentVersionId")).get("authorId"),(String)result.get("status"),resubmit);return result; }
    @Transactional public Map<String,Object> approve(UUID id,ActionInput in) { expect(in.version()); var fact=fact(id); requireReview(fact,"UNDER_REVIEW"); var version=version((UUID)fact.get("currentVersionId")); if(actor().equals(version.get("authorId"))) throw new DomainException(HttpStatus.FORBIDDEN,"SEPARATION_OF_DUTIES_REQUIRED","An author cannot approve their own fact");validateSources(sourceIds((UUID)fact.get("currentVersionId")));var result=transition(id,in.version(),"APPROVED","ACTIVE",in.reason(),"knowledge_fact.approved");reviews.decision("KNOWLEDGE_FACT",(UUID)fact.get("currentVersionId"),"UNDER_REVIEW","APPROVED","ACTIVE",in.reason(),in.reasonCode());return result; }
    @Transactional public Map<String,Object> reject(UUID id,ActionInput in) { expect(in.version()); reviews.validateFeedback("REJECTED",in.reasonCode(),in.reason());var fact=fact(id);requireReview(fact,"UNDER_REVIEW");var result=transition(id,in.version(),"REJECTED","DRAFT",in.reason(),"knowledge_fact.rejected");reviews.decision("KNOWLEDGE_FACT",(UUID)fact.get("currentVersionId"),"UNDER_REVIEW","REJECTED","DRAFT",in.reason(),in.reasonCode());return result; }
    @Transactional public Map<String,Object> requireUpdate(UUID id,ActionInput in) { expect(in.version()); reviews.validateFeedback("REQUIRES_UPDATE",in.reasonCode(),in.reason());var fact=fact(id);requireReview(fact,"UNDER_REVIEW");var result=transition(id,in.version(),"REQUIRES_UPDATE","DRAFT",in.reason(),"knowledge_fact.requires_update");reviews.decision("KNOWLEDGE_FACT",(UUID)fact.get("currentVersionId"),"UNDER_REVIEW","REQUIRES_UPDATE","DRAFT",in.reason(),in.reasonCode());return result; }
    @Transactional public Map<String,Object> retire(UUID id,ActionInput in) { expect(in.version()); var fact=fact(id); if("RETIRED".equals(fact.get("status"))) throw conflict("The fact is already retired"); int c=jdbc.sql("UPDATE knowledge_fact SET status='RETIRED',updated_at=:now,version=version+1 WHERE id=:id AND version=:version").param("now",now()).param("id",id).param("version",in.version()).update(); changed(c,"knowledge_fact",id); log("knowledge_fact.retired",id); return fact(id); }

    private Map<String,Object> transition(UUID id,long expected,String review,String lifecycle,String note,String action) { var now=now(); var fact=fact(id); UUID versionId=(UUID)fact.get("currentVersionId"); String status=lifecycle==null?(String)fact.get("status"):lifecycle; int c=jdbc.sql("UPDATE knowledge_fact SET review_status=:review,status=:status,updated_at=:now,version=version+1 WHERE id=:id AND version=:version").param("review",review).param("status",status).param("now",now).param("id",id).param("version",expected).update(); changed(c,"knowledge_fact",id); jdbc.sql("UPDATE knowledge_fact_version SET review_status=:review,reviewer_id=:reviewer,review_note=:note,updated_at=:now WHERE id=:id").param("review",review).param("reviewer",review.equals("UNDER_REVIEW")?null:actor(),Types.VARCHAR).param("note",blank(note),Types.VARCHAR).param("now",now).param("id",versionId).update(); log(action,id); return fact(id); }
    private void insertVersion(UUID versionId,UUID factId,int number,FactInput in,String review,String author,OffsetDateTime now) { jdbc.sql("INSERT INTO knowledge_fact_version(id,knowledge_fact_id,version_number,canonical_statement,review_status,valid_from,valid_to,author_id,created_at,updated_at) VALUES(:id,:fact,:number,:statement,:review,:from,:to,:author,:now,:now)").param("id",versionId).param("fact",factId).param("number",number).param("statement",in.canonicalStatement().trim()).param("review",review).param("from",in.validFrom()).param("to",in.validTo()).param("author",author).param("now",now).update(); }
    private void linkSources(UUID versionId,List<UUID> ids) { for(UUID sourceId:ids) jdbc.sql("INSERT INTO knowledge_fact_source(knowledge_fact_version_id,source_reference_id) VALUES(:version,:source)").param("version",versionId).param("source",sourceId).update(); }
    private List<UUID> sourceIds(UUID versionId) { return jdbc.sql("SELECT source_reference_id FROM knowledge_fact_source WHERE knowledge_fact_version_id=:id ORDER BY source_reference_id").param("id",versionId).query(UUID.class).list(); }
    private Map<String,Object> version(UUID id) { return one("SELECT id,author_id AS \"authorId\" FROM knowledge_fact_version WHERE id=:id",id,"Fact version"); }
    private void validateSources(List<UUID> ids) { if(ids==null||ids.isEmpty()) throw validation("At least one source is required"); if(ids.stream().distinct().count()!=ids.size()) throw validation("Duplicate sources are not allowed"); for(UUID id:ids){var rows=jdbc.sql("SELECT status FROM source_reference WHERE id=:id").param("id",id).query(String.class).list();if(rows.isEmpty())throw DomainException.notFound("Source");if("RETIRED".equals(rows.getFirst()))throw validation("Retired sources cannot be linked");} }
    private void validateObjective(ObjectiveInput in){require(in.code(),"code");require(in.title(),"title");if(in.topicId()==null)throw validation("topicId is required");if(!List.of("DRAFT","ACTIVE","ARCHIVED").contains(in.status()))throw validation("Invalid status");}
    private void validateFact(FactInput in){if(in.learningObjectiveId()==null)throw validation("learningObjectiveId is required");require(in.canonicalStatement(),"canonicalStatement");if(in.validFrom()!=null&&in.validTo()!=null&&in.validTo().isBefore(in.validFrom()))throw validation("validTo cannot be before validFrom");}
    private void factParams(JdbcClient.StatementSpec q,String search,UUID objective,UUID topic,UUID subject,String publisher,String review,String status,LocalDate validAt){q.param("search",blank(search)).param("objective",objective).param("topic",topic).param("subject",subject).param("publisher",blank(publisher)).param("review",blank(review)).param("status",blank(status)).param("validAt",validAt);}
    private void requireReview(Map<String,Object> fact,String expected){if(!expected.equals(fact.get("reviewStatus")))throw conflict("Invalid fact review transition");}
    private void requireReason(String reason){require(reason,"reason");}
    private void require(String value,String name){if(value==null||value.isBlank())throw validation(name+" is required");}
    private void expect(Long version){if(version==null||version<0)throw validation("version is required");}
    private void changed(int count,String table,UUID id){if(count==0){exists(table,id,"Resource");throw conflict("The resource was changed by another administrator");}}
    private void exists(String table,UUID id,String label){if(!jdbc.sql("SELECT EXISTS(SELECT 1 FROM "+table+" WHERE id=:id)").param("id",id).query(Boolean.class).single())throw DomainException.notFound(label);}
    private Map<String,Object> one(String sql,UUID id,String label){var rows=jdbc.sql(sql).param("id",id).query().listOfRows();if(rows.isEmpty())throw DomainException.notFound(label);return rows.getFirst();}
    private DomainException validation(String message){return new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"VALIDATION_ERROR",message);}
    private DomainException conflict(String message){return DomainException.conflict(message);}
    private String blank(String value){return value==null||value.isBlank()?null:value;}
    private String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
    private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
    private Map<String,Object> page(List<Map<String,Object>> items,int page,int size,long total){return Map.of("items",items,"page",page,"size",size,"totalItems",total,"totalPages",(total+size-1)/size);}
    private void log(String action,UUID id){LOG.info("content_action={} actor={} entity_id={}",action,actor(),id);}
}
