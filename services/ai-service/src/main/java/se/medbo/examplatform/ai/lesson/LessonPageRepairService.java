package se.medbo.examplatform.ai.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.ai.generation.AiApiException;

@Service
class LessonPageRepairService {
  static final String PROMPT_VERSION="lesson-page-repair-v2-exact-claim-contract";
  private final JdbcClient jdbc;private final ObjectMapper mapper;private final LessonGenerationProviderClient provider;
  private final LessonPageClaimValidator validator;
  LessonPageRepairService(JdbcClient jdbc,ObjectMapper mapper,LessonGenerationProviderClient provider,LessonPageClaimValidator validator){this.jdbc=jdbc;this.mapper=mapper;this.provider=provider;this.validator=validator;}

  Map<String,Object> inspect(UUID proposal){proposalRow(proposal);var revisions=jdbc.sql("""
      SELECT id,page_index AS "pageIndex",revision_number AS "revisionNumber",replaces_revision_id AS "replacesRevisionId",
        status,page::text,diagnostics::text,validator_version AS "validatorVersion",provider,model,prompt_version AS "promptVersion",
        provider_request_id AS "providerRequestId",input_tokens AS "inputTokens",output_tokens AS "outputTokens",
        created_by AS "createdBy",created_at AS "createdAt",updated_at AS "updatedAt"
      FROM ai_lesson_page_revision WHERE lesson_proposal_id=:id ORDER BY page_index,revision_number
      """).param("id",proposal).query().listOfRows();
    revisions.forEach(row->{parse(row,"page",Object.class);parse(row,"diagnostics",Object.class);row.put("claims",claims((UUID)row.get("id")));});
    return Map.of("proposalId",proposal,"revisions",revisions);
  }

  @Transactional Map<String,Object> validate(UUID proposal,int index,String actor){
    var context=context(proposal,index);var existing=latest(proposal,index);
    if(existing!=null)return inspect(proposal);
    persistRevision(proposal,index,1,null,"PENDING",context.page(),List.of(),actor);
    UUID revision=latestId(proposal,index);var result=validator.validate(context.page(),context.input().exactSourceText(),context.factTexts());
    persistClaims(revision,result);jdbc.sql("UPDATE ai_lesson_page_revision SET status=:status,diagnostics=CAST(:diagnostics AS jsonb),updated_at=:now WHERE id=:id")
        .param("status",result.supported()?"VALIDATED":"REJECTED").param("diagnostics",json(result.failureCodes())).param("now",now()).param("id",revision).update();
    audit(actor,"AI_LESSON_PAGE_VALIDATED",revision,Map.of("proposalId",proposal,"pageIndex",index,"supported",result.supported(),"validatorVersion",LessonPageClaimValidator.VERSION));
    return inspect(proposal);
  }

  @Transactional Map<String,Object> reject(UUID proposal,int index,String actor,String reason){
    if(latest(proposal,index)==null)validate(proposal,index,actor);var revision=latest(proposal,index);
    jdbc.sql("UPDATE ai_lesson_page_revision SET status='REJECTED',diagnostics=diagnostics||CAST(:reason AS jsonb),updated_at=:now WHERE id=:id")
        .param("reason",json(List.of(reason==null?"Rejected after claim validation":reason))).param("now",now()).param("id",revision.get("id")).update();
    audit(actor,"AI_LESSON_PAGE_REJECTED",(UUID)revision.get("id"),Map.of("proposalId",proposal,"pageIndex",index,"reason",reason==null?"claim validation":reason));return inspect(proposal);
  }

  Map<String,Object> repair(UUID proposal,int index,String actor,String idempotencyKey){
    var context=context(proposal,index);var previous=latest(proposal,index);
    if(previous==null)throw error(HttpStatus.CONFLICT,"AI_LESSON_PAGE_NOT_REJECTED","Validate and reject the page before repair");
    if(idempotencyKey!=null&&idempotencyKey.equals(previous.get("idempotencyKey")))return inspect(proposal);
    if(!"REJECTED".equals(previous.get("status")))throw error(HttpStatus.CONFLICT,"AI_LESSON_PAGE_NOT_REJECTED","Validate and reject the page before repair");
    var input=context.input();var request=new LessonGenerationProviderClient.PageRepairRequest(input.topicTitle(),input.learningObjectiveTitle(),
        input.sourceSectionId(),input.sourceSectionChecksum(),input.exactSourceText(),input.facts(),context.page(),context.surroundingTitles(),
        diagnostics(previous),failedClaims((UUID)previous.get("id")),context.jobId(),actor,(Integer)previous.get("revisionNumber"));var generated=provider.repairPage(request);var page=generated.page();
    if(!page.pageType().equals(context.page().pageType())||!page.title().equals(context.page().title())
        ||!new java.util.LinkedHashSet<>(page.knowledgeFactVersionIds()).equals(new java.util.LinkedHashSet<>(context.page().knowledgeFactVersionIds())))
      throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AI_LESSON_PAGE_REPAIR_PLAN_MISMATCH","Replacement changed immutable page plan fields");
    var result=validator.validate(page,input.exactSourceText(),context.factTexts());UUID revision=UUID.randomUUID();var usage=generated.usage();
    int revisionNumber=(Integer)previous.get("revisionNumber")+1;
    persistRevision(proposal,index,revisionNumber,(UUID)previous.get("id"),result.supported()?"VALIDATED":"REJECTED",page,result.failureCodes(),actor,idempotencyKey,usage);
    persistClaims(revisionId(proposal,index,revisionNumber),result);audit(actor,"AI_LESSON_PAGE_REPLACEMENT_CREATED",revisionId(proposal,index,revisionNumber),
        Map.of("proposalId",proposal,"pageIndex",index,"replacesRevisionId",previous.get("id"),"supported",result.supported(),"idempotencyKey",idempotencyKey==null?"":idempotencyKey));
    if(result.supported())applyReplacement(proposal,index,page);
    return inspect(proposal);
  }

  private void applyReplacement(UUID proposal,int index,LessonGenerationProviderClient.Page page){
    var row=proposalRow(proposal);var pages=read((String)row.get("pages"),new TypeReference<List<LessonGenerationProviderClient.Page>>(){});var updated=new ArrayList<>(pages);updated.set(index,page);
    Map<String,Boolean> gates=read((String)row.get("validation_gates"),new TypeReference<>(){});gates.put("pageClaimValidationPassed",allCurrentPagesValidated(proposal,pages.size()));
    String classification=gates.values().stream().allMatch(Boolean.TRUE::equals)?"GOOD":"NEEDS_REWRITE";
    jdbc.sql("UPDATE ai_lesson_proposal SET pages=CAST(:pages AS jsonb),validation_gates=CAST(:gates AS jsonb),automated_classification=:classification,updated_at=:now,version=version+1 WHERE id=:id")
        .param("pages",json(updated)).param("gates",json(gates)).param("classification",classification).param("now",now()).param("id",proposal).update();
  }
  private boolean allCurrentPagesValidated(UUID proposal,int count){return jdbc.sql("SELECT count(DISTINCT page_index)=:count FROM ai_lesson_page_revision WHERE lesson_proposal_id=:id AND status='VALIDATED'")
      .param("count",count).param("id",proposal).query(Boolean.class).single();}
  private record Context(UUID jobId,LessonGenerationService.Create input,LessonGenerationProviderClient.Page page,List<String> factTexts,List<String> surroundingTitles){}
  private Context context(UUID proposal,int index){var row=jdbc.sql("SELECT p.pages::text,j.id job_id,j.input_snapshot::text FROM ai_lesson_proposal p JOIN ai_lesson_generation_job j ON j.id=p.generation_job_id WHERE p.id=:id AND p.status='PROPOSED'").param("id",proposal).query().listOfRows();if(row.isEmpty())throw error(HttpStatus.NOT_FOUND,"AI_LESSON_PROPOSAL_NOT_FOUND","Repairable lesson proposal was not found");var pages=read((String)row.getFirst().get("pages"),new TypeReference<List<LessonGenerationProviderClient.Page>>(){});if(index<0||index>=pages.size())throw error(HttpStatus.NOT_FOUND,"AI_LESSON_PAGE_NOT_FOUND","Lesson page was not found");var input=read((String)row.getFirst().get("input_snapshot"),LessonGenerationService.Create.class);return new Context((UUID)row.getFirst().get("job_id"),input,pages.get(index),input.facts().stream().map(LessonGenerationProviderClient.Fact::text).toList(),pages.stream().map(LessonGenerationProviderClient.Page::title).toList());}
  private Map<String,Object> proposalRow(UUID id){var rows=jdbc.sql("SELECT pages::text,validation_gates::text FROM ai_lesson_proposal WHERE id=:id").param("id",id).query().listOfRows();if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"AI_LESSON_PROPOSAL_NOT_FOUND","Lesson proposal was not found");return rows.getFirst();}
  private Map<String,Object> latest(UUID proposal,int index){var rows=jdbc.sql("SELECT id,revision_number AS \"revisionNumber\",status,diagnostics::text,idempotency_key AS \"idempotencyKey\" FROM ai_lesson_page_revision WHERE lesson_proposal_id=:proposal AND page_index=:page ORDER BY revision_number DESC LIMIT 1").param("proposal",proposal).param("page",index).query().listOfRows();return rows.isEmpty()?null:rows.getFirst();}
  private UUID latestId(UUID proposal,int index){return revisionId(proposal,index,(Integer)latest(proposal,index).get("revisionNumber"));}
  private UUID revisionId(UUID proposal,int index,int revision){return jdbc.sql("SELECT id FROM ai_lesson_page_revision WHERE lesson_proposal_id=:proposal AND page_index=:page AND revision_number=:revision").param("proposal",proposal).param("page",index).param("revision",revision).query(UUID.class).single();}
  private List<String> diagnostics(Map<String,Object> row){return read((String)row.get("diagnostics"),new TypeReference<>(){});}
  private List<LessonGenerationProviderClient.FailedClaim> failedClaims(UUID revision){return jdbc.sql("SELECT claim_text,failure_code,diagnostic FROM ai_lesson_page_claim WHERE page_revision_id=:id AND status='REJECTED' ORDER BY claim_order")
      .param("id",revision).query((rs,row)->new LessonGenerationProviderClient.FailedClaim(rs.getString(1),rs.getString(2),rs.getString(3))).list();}
  private void persistRevision(UUID proposal,int index,int number,UUID replaces,String status,LessonGenerationProviderClient.Page page,List<String> diagnostics,String actor,LessonGenerationProviderClient.Usage... usageValue){persistRevision(proposal,index,number,replaces,status,page,diagnostics,actor,null,usageValue);}
  private void persistRevision(UUID proposal,int index,int number,UUID replaces,String status,LessonGenerationProviderClient.Page page,List<String> diagnostics,String actor,String idempotencyKey,LessonGenerationProviderClient.Usage... usageValue){var usage=usageValue.length==0?null:usageValue[0];jdbc.sql("INSERT INTO ai_lesson_page_revision(id,lesson_proposal_id,page_index,revision_number,replaces_revision_id,status,page,diagnostics,validator_version,provider,model,prompt_version,provider_request_id,input_tokens,output_tokens,created_by,idempotency_key,created_at,updated_at) VALUES(:id,:proposal,:pageIndex,:number,:replaces,:status,CAST(:page AS jsonb),CAST(:diagnostics AS jsonb),:validator,:provider,:model,:prompt,:request,:input,:output,:actor,:idempotencyKey,:now,:now)").param("id",UUID.randomUUID()).param("proposal",proposal).param("pageIndex",index).param("number",number).param("replaces",replaces,java.sql.Types.OTHER).param("status",status).param("page",json(page)).param("diagnostics",json(diagnostics)).param("validator",LessonPageClaimValidator.VERSION).param("provider",usage==null?null:usage.provider(),java.sql.Types.VARCHAR).param("model",usage==null?null:usage.model(),java.sql.Types.VARCHAR).param("prompt",usage==null?null:PROMPT_VERSION,java.sql.Types.VARCHAR).param("request",usage==null?null:usage.requestId(),java.sql.Types.VARCHAR).param("input",usage==null?null:usage.inputTokens(),java.sql.Types.INTEGER).param("output",usage==null?null:usage.outputTokens(),java.sql.Types.INTEGER).param("actor",actor).param("idempotencyKey",idempotencyKey,java.sql.Types.VARCHAR).param("now",now()).update();}
  private void persistClaims(UUID revision,LessonPageClaimValidator.Result result){for(var claim:result.claims())jdbc.sql("INSERT INTO ai_lesson_page_claim(id,page_revision_id,claim_order,claim_text,status,failure_code,diagnostic,evidence,validator_version,created_at) VALUES(:id,:revision,:ordering,:text,:status,:code,:diagnostic,CAST(:evidence AS jsonb),:validator,:now)").param("id",UUID.randomUUID()).param("revision",revision).param("ordering",claim.order()).param("text",claim.text()).param("status",claim.status()).param("code",claim.failureCode(),java.sql.Types.VARCHAR).param("diagnostic",claim.diagnostic()).param("evidence",json(claim.evidence())).param("validator",LessonPageClaimValidator.VERSION).param("now",now()).update();}
  private List<Map<String,Object>> claims(UUID revision){var rows=jdbc.sql("SELECT id,claim_order AS \"claimOrder\",claim_text AS \"claimText\",status,failure_code AS \"failureCode\",diagnostic,evidence::text,validator_version AS \"validatorVersion\" FROM ai_lesson_page_claim WHERE page_revision_id=:id ORDER BY claim_order").param("id",revision).query().listOfRows();rows.forEach(row->parse(row,"evidence",Object.class));return rows;}
  private void audit(String actor,String action,UUID entity,Map<String,Object> metadata){jdbc.sql("INSERT INTO ai_audit_event(id,occurred_at,actor_id,action,entity_type,entity_id,metadata) VALUES(:id,:now,:actor,:action,'AI_LESSON_PAGE_REVISION',:entity,CAST(:metadata AS jsonb))").param("id",UUID.randomUUID()).param("now",now()).param("actor",actor).param("action",action).param("entity",entity).param("metadata",json(metadata)).update();}
  private <T>T read(String value,TypeReference<T> type){try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException(e);}}
  private <T>T read(String value,Class<T> type){try{return mapper.readValue(value,type);}catch(Exception e){throw new IllegalStateException(e);}}
  private void parse(Map<String,Object> row,String key,Class<?> type){try{row.put(key,mapper.readValue((String)row.get(key),type));}catch(Exception e){throw new IllegalStateException(e);}}
  private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
  private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
  private AiApiException error(HttpStatus status,String code,String message){return new AiApiException(status,code,message);}
}
