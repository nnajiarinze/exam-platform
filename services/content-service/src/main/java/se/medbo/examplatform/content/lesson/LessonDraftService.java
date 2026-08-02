package se.medbo.examplatform.content.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.content.shared.DomainException;

@Service
class LessonDraftService {
  private final JdbcClient jdbc;private final ObjectMapper mapper;
  LessonDraftService(JdbcClient jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

  @Transactional Map<String,Object> create(LessonDraftController.Create input){
    requireAuthor();
    exists("SELECT count(*) FROM topic WHERE id=:id AND status<>'ARCHIVED'",input.topicId(),"Topic");
    boolean expansion=input.supersedesLessonDraftId()!=null;
    if(expansion){
      exists("SELECT count(*) FROM lesson_draft WHERE id=:id AND topic_id='"+input.topicId()+"' AND review_status='REVIEWED'",input.supersedesLessonDraftId(),"Superseded reviewed lesson");
      if(input.generationPlanId()==null||!"LESSON_DEPTH_EXPANSION".equals(input.revisionReason()))
        throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"LESSON_SUPERSESSION_INVALID","Depth expansion requires the immutable generation plan and revision reason");
    }else if(input.generationPlanId()!=null||input.revisionReason()!=null){
      throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"LESSON_SUPERSESSION_INVALID","Supersession metadata must be complete");
    }
    var factChecksums=new ArrayList<String>();int order=0;
    for(var section:input.sections()){
      exists("SELECT count(*) FROM source_section ss JOIN learning_objective_source_section los ON los.source_section_id=ss.id JOIN learning_objective lo ON lo.id=los.learning_objective_id WHERE ss.id=:id AND lo.topic_id='"+input.topicId()+"'",section.sourceSectionId(),"Mapped source section");
      for(var factVersion:section.knowledgeFactVersionIds()){
        exists("SELECT count(*) FROM knowledge_fact_version fv JOIN knowledge_fact f ON f.current_version_id=fv.id JOIN learning_objective lo ON lo.id=f.learning_objective_id JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=fv.id WHERE fv.id=:id AND lo.topic_id='"+input.topicId()+"' AND f.review_status='APPROVED' AND fv.review_status='APPROVED' AND p.source_section_id='"+section.sourceSectionId()+"'",factVersion,"Approved grounded fact");
        factChecksums.add(String.valueOf(factVersion));
      }
    }
    var id=UUID.randomUUID();var now=OffsetDateTime.now(ZoneOffset.UTC);int versionNumber=jdbc.sql("SELECT coalesce(max(version_number),0)+1 FROM lesson_draft WHERE topic_id=:topic").param("topic",input.topicId()).query(Integer.class).single();
    String sourceChecksum=checksum(String.join("|",factChecksums));
    jdbc.sql("INSERT INTO lesson_draft(id,topic_id,version_number,title,introduction,summary,important_points,review_status,source_checksum,created_by,created_at,updated_at,supersedes_lesson_draft_id,generation_plan_id,revision_reason,human_verified) VALUES(:id,:topic,:number,:title,:introduction,:summary,CAST(:points AS jsonb),'DRAFT',:checksum,:actor,:now,:now,:predecessor,:plan,:reason,:verified)")
      .param("id",id).param("topic",input.topicId()).param("number",versionNumber).param("title",input.title().trim()).param("introduction",input.introduction().trim()).param("summary",input.summary().trim()).param("points",json(input.importantPoints()==null?List.of():input.importantPoints())).param("checksum",sourceChecksum).param("actor",actor()).param("now",now)
      .param("predecessor",input.supersedesLessonDraftId(),Types.OTHER).param("plan",input.generationPlanId(),Types.OTHER)
      .param("reason",blank(input.revisionReason()),Types.VARCHAR).param("verified",Boolean.TRUE.equals(input.humanVerified())).update();
    for(var section:input.sections()){
      var sectionId=UUID.randomUUID();String sectionChecksum=checksum(section.title().trim()+"\n"+section.explanation().trim());
      var predecessors=section.predecessorSectionIds()==null?List.<UUID>of():section.predecessorSectionIds();
      UUID logicalSectionId=predecessors.isEmpty()?sectionId:jdbc.sql("SELECT logical_section_id FROM lesson_draft_section WHERE id=:id AND lesson_draft_id=:lesson").param("id",predecessors.getFirst()).param("lesson",input.supersedesLessonDraftId()).query(UUID.class).optional().orElse(sectionId);
      jdbc.sql("INSERT INTO lesson_draft_section(id,lesson_draft_id,source_section_id,title,explanation,key_terms,supported_examples,display_order,section_checksum,logical_section_id) VALUES(:id,:lesson,:source,:title,:explanation,CAST(:terms AS jsonb),CAST(:examples AS jsonb),:display,:checksum,:logical)")
        .param("id",sectionId).param("lesson",id).param("source",section.sourceSectionId()).param("title",section.title().trim()).param("explanation",section.explanation().trim()).param("terms",json(section.keyTerms()==null?List.of():section.keyTerms())).param("examples",json(section.supportedExamples()==null?List.of():section.supportedExamples())).param("display",order++).param("checksum",sectionChecksum).param("logical",logicalSectionId).update();
      for(var fact:section.knowledgeFactVersionIds())jdbc.sql("INSERT INTO lesson_draft_section_fact VALUES(:section,:fact)").param("section",sectionId).param("fact",fact).update();
      if(expansion){
        String mapping=predecessors.isEmpty()?"ADDED":(section.mappingType()==null?"SUPERSEDED":section.mappingType());
        if(!List.of("RETAINED","REORDERED","SUPERSEDED","SPLIT","MERGED","ADDED").contains(mapping))
          throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"LESSON_SECTION_MAPPING_INVALID","Unknown section supersession mapping");
        if(predecessors.isEmpty())persistMapping(input.supersedesLessonDraftId(),id,null,sectionId,mapping,input.generationPlanId(),now);
        for(var predecessor:predecessors){
          exists("SELECT count(*) FROM lesson_draft_section WHERE id=:id AND lesson_draft_id='"+input.supersedesLessonDraftId()+"'",predecessor,"Predecessor lesson section");
          persistMapping(input.supersedesLessonDraftId(),id,predecessor,sectionId,mapping,input.generationPlanId(),now);
        }
      }
    }
    return get(id);
  }

  List<Map<String,Object>> list(String status){if(!canInspect())throw forbidden();var statement=jdbc.sql("SELECT id,topic_id AS \"topicId\",version_number AS \"versionNumber\",title,review_status AS \"reviewStatus\",source_checksum AS \"sourceChecksum\",created_by AS \"createdBy\",reviewed_by AS \"reviewedBy\",created_at AS \"createdAt\",updated_at AS \"updatedAt\",reviewed_at AS \"reviewedAt\",supersedes_lesson_draft_id AS \"supersedesLessonDraftId\",generation_plan_id AS \"generationPlanId\",revision_reason AS \"revisionReason\",human_verified AS \"humanVerified\",version FROM lesson_draft WHERE (CAST(:status AS text) IS NULL OR review_status=:status) ORDER BY updated_at DESC,id").param("status",blank(status),Types.VARCHAR);return statement.query().listOfRows();}
  Map<String,Object> get(UUID id){if(!canInspect())throw forbidden();var rows=jdbc.sql("SELECT id,topic_id AS \"topicId\",version_number AS \"versionNumber\",title,introduction,summary,important_points::text AS \"importantPoints\",review_status AS \"reviewStatus\",source_checksum AS \"sourceChecksum\",created_by AS \"createdBy\",reviewed_by AS \"reviewedBy\",review_note AS \"reviewNote\",created_at AS \"createdAt\",updated_at AS \"updatedAt\",reviewed_at AS \"reviewedAt\",supersedes_lesson_draft_id AS \"supersedesLessonDraftId\",generation_plan_id AS \"generationPlanId\",revision_reason AS \"revisionReason\",human_verified AS \"humanVerified\",version FROM lesson_draft WHERE id=:id").param("id",id).query().listOfRows();if(rows.isEmpty())throw DomainException.notFound("Lesson draft");var result=new LinkedHashMap<>(rows.getFirst());result.put("importantPoints",read(result.get("importantPoints")));result.put("sections",sections(id));result.put("supersessionMappings",supersessionMappings(id));return result;}

  @Transactional Map<String,Object> transition(UUID id,long version,String next,String note){
    if("UNDER_REVIEW".equals(next))requireAuthor();else requireReviewer();
    String allowed="UNDER_REVIEW".equals(next)?"('DRAFT','REQUIRES_UPDATE')":"('UNDER_REVIEW')";
    int changed=jdbc.sql("UPDATE lesson_draft SET review_status=:next,reviewed_by=CASE WHEN :next='UNDER_REVIEW' THEN reviewed_by ELSE :actor END,review_note=:note,reviewed_at=CASE WHEN :next='REVIEWED' THEN now() ELSE reviewed_at END,updated_at=now(),version=version+1 WHERE id=:id AND version=:version AND review_status IN "+allowed)
      .param("next",next).param("actor",actor()).param("note",blank(note),Types.VARCHAR).param("id",id).param("version",version).update();
    if(changed==0){get(id);throw new DomainException(HttpStatus.CONFLICT,"STALE_VERSION","Lesson draft changed or is not eligible for this transition");}
    return get(id);
  }

  private List<Map<String,Object>> sections(UUID lesson){var rows=jdbc.sql("SELECT s.id,s.source_section_id AS \"sourceSectionId\",s.title,s.explanation,s.key_terms::text AS \"keyTerms\",s.supported_examples::text AS \"supportedExamples\",s.display_order AS \"displayOrder\",s.section_checksum AS \"sectionChecksum\",coalesce(array_agg(f.knowledge_fact_version_id) FILTER(WHERE f.knowledge_fact_version_id IS NOT NULL),'{}') AS \"knowledgeFactVersionIds\" FROM lesson_draft_section s LEFT JOIN lesson_draft_section_fact f ON f.lesson_draft_section_id=s.id WHERE s.lesson_draft_id=:id GROUP BY s.id ORDER BY s.display_order").param("id",lesson).query().listOfRows();rows.forEach(row->{row.put("keyTerms",read(row.get("keyTerms")));row.put("supportedExamples",read(row.get("supportedExamples")));row.put("knowledgeFactVersionIds",uuidList(row.get("knowledgeFactVersionIds")));});return rows;}
  private List<Map<String,Object>> supersessionMappings(UUID lesson){var rows=jdbc.sql("SELECT id,predecessor_lesson_id AS \"predecessorLessonId\",successor_lesson_id AS \"successorLessonId\",predecessor_section_id AS \"predecessorSectionId\",successor_section_id AS \"successorSectionId\",mapping_type AS \"mappingType\",audit_snapshot::text AS \"auditSnapshot\",created_by AS \"createdBy\",created_at AS \"createdAt\" FROM lesson_draft_supersession_mapping WHERE successor_lesson_id=:id ORDER BY created_at,id").param("id",lesson).query().listOfRows();rows.forEach(row->row.put("auditSnapshot",read(row.get("auditSnapshot"))));return rows;}
  private void persistMapping(UUID predecessorLesson,UUID successorLesson,UUID predecessorSection,UUID successorSection,String type,UUID plan,OffsetDateTime now){jdbc.sql("INSERT INTO lesson_draft_supersession_mapping(id,predecessor_lesson_id,successor_lesson_id,predecessor_section_id,successor_section_id,mapping_type,audit_snapshot,created_by,created_at) VALUES(:id,:predecessor,:successor,:oldSection,:newSection,:type,CAST(:audit AS jsonb),:actor,:now)").param("id",UUID.randomUUID()).param("predecessor",predecessorLesson).param("successor",successorLesson).param("oldSection",predecessorSection,Types.OTHER).param("newSection",successorSection).param("type",type).param("audit",json(Map.of("generationPlanId",plan,"revisionReason","LESSON_DEPTH_EXPANSION"))).param("actor",actor()).param("now",now).update();}
  private List<UUID> uuidList(Object value){try{Object array=value instanceof java.sql.Array sqlArray?sqlArray.getArray():value;if(array instanceof UUID[] ids)return List.of(ids);if(array instanceof Object[] values)return java.util.Arrays.stream(values).map(v->UUID.fromString(String.valueOf(v))).toList();return List.of();}catch(java.sql.SQLException e){throw new IllegalStateException(e);}}
  private void exists(String sql,UUID id,String label){if(jdbc.sql(sql).param("id",id).query(Integer.class).single()==0)throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY,"LESSON_GROUNDING_INVALID",label+" is missing or not eligible");}
  private Object read(Object value){try{return mapper.readValue(String.valueOf(value),Object.class);}catch(Exception e){throw new IllegalStateException(e);}}
  private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
  private String checksum(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
  private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
  private String actor(){return SecurityContextHolder.getContext().getAuthentication().getName();}
  private boolean has(String role){return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a->role.equals(a.getAuthority()));}
  private boolean canInspect(){return has("ROLE_CONTENT_AUTHOR")||has("ROLE_CONTENT_REVIEWER")||has("ROLE_ADMIN");}
  private void requireAuthor(){if(!has("ROLE_CONTENT_AUTHOR")&&!has("ROLE_ADMIN"))throw forbidden();}
  private void requireReviewer(){if(!has("ROLE_CONTENT_REVIEWER")&&!has("ROLE_ADMIN"))throw forbidden();}
  private DomainException forbidden(){return new DomainException(HttpStatus.FORBIDDEN,"FORBIDDEN","Lesson editorial permission is required");}
}
