package se.medbo.examplatform.ai.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class LessonPagePlanStore {
  record Plan(UUID id,UUID proposalId,int pageIndex,int revisionNumber,UUID replacesPlanRevisionId,
      String pageType,String title,List<UUID> knowledgeFactVersionIds,UUID sourceSectionId,
      String sourceSectionChecksum,UUID topicId,UUID learningObjectiveId,String checksum) {}

  private final JdbcClient jdbc;private final ObjectMapper mapper;
  LessonPagePlanStore(JdbcClient jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

  Plan resolve(UUID proposal,int pageIndex,LessonGenerationService.Create input,
      LessonGenerationProviderClient.Page page,String actor){
    var existing=plans(proposal,pageIndex).stream().filter(p->matches(p,page)).findFirst();
    if(existing.isPresent())return existing.get();
    var prior=plans(proposal,pageIndex).stream().findFirst().orElse(null);
    return insert(proposal,pageIndex,prior==null?1:prior.revisionNumber()+1,prior==null?null:prior.id(),
        page.pageType(),page.title(),page.knowledgeFactVersionIds(),input,actor);
  }

  Plan createInitial(UUID proposal,int pageIndex,LessonGenerationService.Create input,
      LessonGenerationProviderClient.PlannedPage page,String actor){
    var existing=plans(proposal,pageIndex).stream().filter(p->p.revisionNumber()==1).findFirst();
    return existing.orElseGet(()->insert(proposal,pageIndex,1,null,page.pageType(),page.title(),
        page.knowledgeFactVersionIds(),input,actor));
  }

  Plan get(UUID id){return jdbc.sql("""
      SELECT id,lesson_proposal_id,page_index,plan_revision_number,replaces_plan_revision_id,page_type,
        title,knowledge_fact_version_ids::text,source_section_id,source_section_checksum,topic_id,
        learning_objective_id,plan_checksum FROM ai_lesson_page_plan_revision WHERE id=:id
      """).param("id",id).query((rs,n)->map(rs)).single();}

  private List<Plan> plans(UUID proposal,int index){return jdbc.sql("""
      SELECT id,lesson_proposal_id,page_index,plan_revision_number,replaces_plan_revision_id,page_type,
        title,knowledge_fact_version_ids::text,source_section_id,source_section_checksum,topic_id,
        learning_objective_id,plan_checksum FROM ai_lesson_page_plan_revision
      WHERE lesson_proposal_id=:proposal AND page_index=:page ORDER BY plan_revision_number DESC
      """).param("proposal",proposal).param("page",index).query((rs,n)->map(rs)).list();}

  private Plan insert(UUID proposal,int index,int revision,UUID replaces,String type,String title,List<UUID> facts,
      LessonGenerationService.Create input,String actor){
    UUID id=UUID.randomUUID();String checksum=checksum(index,type,title,facts,input);
    jdbc.sql("""
      INSERT INTO ai_lesson_page_plan_revision(id,lesson_proposal_id,page_index,plan_revision_number,
        replaces_plan_revision_id,page_type,title,knowledge_fact_version_ids,source_section_id,
        source_section_checksum,topic_id,learning_objective_id,plan_checksum,created_by,created_at)
      VALUES(:id,:proposal,:page,:revision,:replaces,:type,:title,CAST(:facts AS jsonb),:section,
        :sourceChecksum,:topic,:objective,:checksum,:actor,:now)
      """).param("id",id).param("proposal",proposal).param("page",index).param("revision",revision)
        .param("replaces",replaces,Types.OTHER).param("type",type).param("title",title).param("facts",json(facts))
        .param("section",input.sourceSectionId()).param("sourceChecksum",input.sourceSectionChecksum())
        .param("topic",input.topicId()).param("objective",input.learningObjectiveId()).param("checksum",checksum)
        .param("actor",actor).param("now",OffsetDateTime.now(ZoneOffset.UTC)).update();
    return get(id);
  }

  static boolean matches(Plan plan,LessonGenerationProviderClient.Page page){return plan.pageType().equals(page.pageType())
      &&plan.title().equals(page.title())&&plan.knowledgeFactVersionIds().equals(page.knowledgeFactVersionIds());}

  private String checksum(int index,String type,String title,List<UUID> facts,LessonGenerationService.Create input){
    var value=new LinkedHashMap<String,Object>();value.put("pageIndex",index);value.put("pageType",type);
    value.put("title",title);value.put("knowledgeFactVersionIds",facts);value.put("sourceSectionId",input.sourceSectionId());
    value.put("sourceSectionChecksum",input.sourceSectionChecksum());value.put("topicId",input.topicId());
    value.put("learningObjectiveId",input.learningObjectiveId());
    try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));}
    catch(Exception e){throw new IllegalStateException(e);}
  }
  private Plan map(java.sql.ResultSet rs){try{return new Plan(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getInt(4),
      rs.getObject(5,UUID.class),rs.getString(6),rs.getString(7),mapper.readValue(rs.getString(8),new TypeReference<List<UUID>>(){}),
      rs.getObject(9,UUID.class),rs.getString(10),rs.getObject(11,UUID.class),rs.getObject(12,UUID.class),rs.getString(13));}
    catch(Exception e){throw new IllegalStateException(e);}}
  private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
}
