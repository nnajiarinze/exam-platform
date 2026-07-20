package se.medbo.examplatform.content.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import se.medbo.examplatform.content.shared.ExternalExamIdentifier;

@Service
public class ReleaseSnapshotService {
    private final JdbcClient jdbc;private final ObjectMapper mapper;
    public ReleaseSnapshotService(JdbcClient jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public SnapshotResult generate(UUID releaseId,Instant publishedAt){
        var release=jdbc.sql("SELECT r.id,e.code AS exam_code,ev.version_code FROM content_release r JOIN exam e ON e.id=r.exam_id JOIN exam_version ev ON ev.id=r.exam_version_id WHERE r.id=:id").param("id",releaseId).query().singleRow();
        var subjects=jdbc.sql("SELECT DISTINCT s.id,s.name,s.sort_order FROM content_release_item ri JOIN question_version qv ON ri.content_type='QUESTION' AND qv.id=ri.content_version_id JOIN learning_objective lo ON lo.id=qv.learning_objective_id JOIN topic t ON t.id=lo.topic_id JOIN subject s ON s.id=t.subject_id WHERE ri.release_id=:id ORDER BY s.sort_order,s.id").param("id",releaseId).query().listOfRows();var subjectSnapshots=new ArrayList<Subject>();
        for(var subject:subjects){UUID subjectId=(UUID)subject.get("id");var topics=jdbc.sql("SELECT DISTINCT t.id,t.name,t.description,t.sort_order FROM content_release_item ri JOIN question_version qv ON ri.content_type='QUESTION' AND qv.id=ri.content_version_id JOIN learning_objective lo ON lo.id=qv.learning_objective_id JOIN topic t ON t.id=lo.topic_id WHERE ri.release_id=:release AND t.subject_id=:subject ORDER BY t.sort_order,t.id").param("release",releaseId).param("subject",subjectId).query().listOfRows();var topicSnapshots=new ArrayList<Topic>();
            for(var topic:topics){UUID topicId=(UUID)topic.get("id");var questions=jdbc.sql("SELECT q.id,qv.id AS version_id,q.question_type,qv.question_text,qv.explanation,qv.difficulty,ri.display_order FROM content_release_item ri JOIN question q ON q.id=ri.content_id JOIN question_version qv ON qv.id=ri.content_version_id JOIN learning_objective lo ON lo.id=qv.learning_objective_id WHERE ri.release_id=:release AND ri.content_type='QUESTION' AND lo.topic_id=:topic ORDER BY ri.display_order,q.id").param("release",releaseId).param("topic",topicId).query().listOfRows();var questionSnapshots=new ArrayList<Question>();
                for(var q:questions){UUID version=(UUID)q.get("version_id");String factId=jdbc.sql("SELECT kfv.knowledge_fact_id::text FROM question_knowledge_fact qkf JOIN knowledge_fact_version kfv ON kfv.id=qkf.knowledge_fact_version_id WHERE qkf.question_version_id=:id ORDER BY kfv.knowledge_fact_id LIMIT 1").param("id",version).query(String.class).single();var options=jdbc.sql("SELECT id,text,correct,display_order FROM question_option WHERE question_version_id=:id ORDER BY display_order,id").param("id",version).query().listOfRows().stream().map(o->new AnswerOption(o.get("id").toString(),(String)o.get("text"),(Boolean)o.get("correct"),((Number)o.get("display_order")).intValue())).toList();questionSnapshots.add(new Question(q.get("id").toString(),version.toString(),factId,(String)q.get("question_type"),(String)q.get("question_text"),(String)q.get("explanation"),"sv",(String)q.get("difficulty"),true,options));}
                topicSnapshots.add(new Topic(topicId.toString(),(String)topic.get("name"),(String)topic.get("description"),((Number)topic.get("sort_order")).intValue(),questionSnapshots));}
            subjectSnapshots.add(new Subject(subjectId.toString(),(String)subject.get("name"),((Number)subject.get("sort_order")).intValue(),topicSnapshots));}
        var material=new SnapshotWithoutChecksum("1.0",release.get("id").toString(),ExternalExamIdentifier.normalize((String)release.get("exam_code")),(String)release.get("version_code"),releaseNumber(releaseId),"PUBLISHED",publishedAt,subjectSnapshots);String checksum=checksum(material);var snapshot=new Snapshot(material.schemaVersion(),material.externalReleaseId(),material.examId(),material.examVersionId(),material.releaseVersion(),material.releaseStatus(),material.publishedAt(),checksum,material.subjects());try{String json=mapper.writeValueAsString(snapshot);return new SnapshotResult(snapshot,json,checksum,json.getBytes(StandardCharsets.UTF_8).length);}catch(JsonProcessingException e){throw new IllegalStateException("Cannot serialize release snapshot",e);}
    }
    public Map<String,Object> preview(UUID releaseId){var result=generate(releaseId,Instant.EPOCH);return Map.of("snapshot",result.snapshot(),"coverage",coverage(releaseId),"checksumPreview",result.checksum());}
    public Map<String,Object> coverage(UUID releaseId){return jdbc.sql("SELECT count(DISTINCT s.id) AS \"subjectCount\",count(DISTINCT t.id) AS \"topicCount\",count(DISTINCT lo.id) AS \"learningObjectiveCount\",count(DISTINCT ri.content_version_id) FILTER(WHERE ri.content_type='QUESTION') AS \"questionCount\",count(DISTINCT ri.content_version_id) FILTER(WHERE ri.content_type='KNOWLEDGE_FACT') AS \"knowledgeFactCount\",count(DISTINCT ri.content_version_id) FILTER(WHERE ri.content_type='QUESTION' AND qv.difficulty='EASY') AS \"easyCount\",count(DISTINCT ri.content_version_id) FILTER(WHERE ri.content_type='QUESTION' AND qv.difficulty='MEDIUM') AS \"mediumCount\",count(DISTINCT ri.content_version_id) FILTER(WHERE ri.content_type='QUESTION' AND qv.difficulty='HARD') AS \"hardCount\" FROM content_release_item ri LEFT JOIN question_version qv ON ri.content_type='QUESTION' AND qv.id=ri.content_version_id LEFT JOIN knowledge_fact_version kfv ON ri.content_type='KNOWLEDGE_FACT' AND kfv.id=ri.content_version_id JOIN learning_objective lo ON lo.id=COALESCE(qv.learning_objective_id,(SELECT f.learning_objective_id FROM knowledge_fact f WHERE f.id=kfv.knowledge_fact_id)) JOIN topic t ON t.id=lo.topic_id JOIN subject s ON s.id=t.subject_id WHERE ri.release_id=:id").param("id",releaseId).query().singleRow();}
    public String checksum(SnapshotWithoutChecksum material){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(material)));}catch(Exception e){throw new IllegalStateException("Cannot calculate release checksum",e);}}
    private String releaseNumber(UUID id){return jdbc.sql("SELECT release_number FROM content_release WHERE id=:id").param("id",id).query(String.class).single();}
    public record SnapshotResult(Snapshot snapshot,String json,String checksum,long sizeBytes){}
    public record Snapshot(String schemaVersion,String externalReleaseId,String examId,String examVersionId,String releaseVersion,String releaseStatus,Instant publishedAt,String checksum,List<Subject> subjects){}
    public record SnapshotWithoutChecksum(String schemaVersion,String externalReleaseId,String examId,String examVersionId,String releaseVersion,String releaseStatus,Instant publishedAt,List<Subject> subjects){}
    public record Subject(String id,String name,int sortOrder,List<Topic> topics){}
    public record Topic(String id,String name,String description,int sortOrder,List<Question> questions){}
    public record Question(String id,String versionId,String knowledgeFactId,String questionType,String prompt,String explanation,String language,String difficulty,boolean active,List<AnswerOption> answerOptions){}
    public record AnswerOption(String id,String text,boolean correct,int sortOrder){}
}
