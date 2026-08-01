package se.medbo.examplatform.ai.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
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
import se.medbo.examplatform.ai.provider.AiProviderException;

@Service
class LessonPageRepairService {
  static final String PROMPT_VERSION="lesson-page-repair-v3-content-only";
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
    var attempts=jdbc.sql("""
      SELECT id,page_index AS "pageIndex",replaces_revision_id AS "replacesRevisionId",
        contract_version AS "contractVersion",status,failure_code AS "failureCode",provider,model,
        provider_request_id AS "providerRequestId",input_tokens AS "inputTokens",output_tokens AS "outputTokens",
        reasoning_tokens AS "reasoningTokens",latency_millis AS "latencyMillis",free_only AS "freeOnly",
        idempotency_key AS "idempotencyKey",mutable_response::text AS "mutableResponse",
        created_by AS "createdBy",created_at AS "createdAt"
      FROM ai_lesson_page_repair_attempt WHERE lesson_proposal_id=:id ORDER BY created_at
      """).param("id",proposal).query().listOfRows();
    attempts.forEach(row->{if(row.get("mutableResponse")!=null)parse(row,"mutableResponse",Object.class);});
    return Map.of("proposalId",proposal,"revisions",revisions,"attempts",attempts);
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
    validatePlanSnapshot(context,index);
    var input=context.input();var rejectedClaims=failedClaimsForPage(proposal,index);
    var request=new LessonGenerationProviderClient.PageRepairRequest(input.topicTitle(),input.learningObjectiveTitle(),
        input.sourceSectionId(),input.sourceSectionChecksum(),input.exactSourceText(),input.facts(),context.page(),context.surroundingTitles(),
        diagnostics(previous),rejectedClaims,context.jobId(),actor,(Integer)previous.get("revisionNumber"));
    long started=System.nanoTime();LessonGenerationProviderClient.PageRepairResult generated;
    try{generated=provider.repairPage(request);}catch(AiProviderException e){
      persistAttempt(proposal,index,(UUID)previous.get("id"),"PROVIDER_REJECTED",e.code(),null,
          elapsed(started),idempotencyKey,actor);throw e;
    }
    var content=generated.content();
    if(!"REPAIRED".equals(content.status())){
      persistAttempt(proposal,index,(UUID)previous.get("id"),"INSUFFICIENT_INFORMATION",
          "AI_LESSON_PAGE_REPAIR_INSUFFICIENT_GROUNDED_INFORMATION",generated,elapsed(started),idempotencyKey,actor);
      throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AI_LESSON_PAGE_REPAIR_INSUFFICIENT_GROUNDED_INFORMATION","Provider found insufficient grounded information for this page");
    }
    if(content.body()==null||content.body().isBlank()){
      persistAttempt(proposal,index,(UUID)previous.get("id"),"PROVIDER_REJECTED",
          "AI_PROVIDER_RESPONSE_INVALID",generated,elapsed(started),idempotencyKey,actor);
      throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AI_PROVIDER_RESPONSE_INVALID","Repair response omitted mutable page content");
    }
    var immutable=context.page();
    String guardedBody=stripExactFailedClaims(content.body(),rejectedClaims);
    if(guardedBody.isBlank()){
      persistAttempt(proposal,index,(UUID)previous.get("id"),"INSUFFICIENT_INFORMATION",
          "AI_LESSON_PAGE_REPAIR_INSUFFICIENT_GROUNDED_INFORMATION",generated,elapsed(started),idempotencyKey,actor);
      throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AI_LESSON_PAGE_REPAIR_INSUFFICIENT_GROUNDED_INFORMATION","Repair contained only previously rejected claims");
    }
    var page=new LessonGenerationProviderClient.Page(immutable.pageType(),immutable.title(),guardedBody,
        List.copyOf(immutable.knowledgeFactVersionIds()),List.copyOf(content.evidenceQuotes()),List.copyOf(content.keyTerms()));
    var result=validator.validate(page,input.exactSourceText(),context.factTexts());UUID revision=UUID.randomUUID();var usage=generated.usage();
    int revisionNumber=(Integer)previous.get("revisionNumber")+1;
    persistRevision(proposal,index,revisionNumber,(UUID)previous.get("id"),result.supported()?"VALIDATED":"REJECTED",page,result.failureCodes(),actor,idempotencyKey,usage);
    persistClaims(revisionId(proposal,index,revisionNumber),result);audit(actor,"AI_LESSON_PAGE_REPLACEMENT_CREATED",revisionId(proposal,index,revisionNumber),
        Map.of("proposalId",proposal,"pageIndex",index,"replacesRevisionId",previous.get("id"),"supported",result.supported(),"idempotencyKey",idempotencyKey==null?"":idempotencyKey));
    persistAttempt(proposal,index,(UUID)previous.get("id"),result.supported()?"SUCCEEDED":"CLAIM_REJECTED",
        result.supported()?null:String.join(",",result.failureCodes()),generated,elapsed(started),idempotencyKey,actor);
    if(result.supported())applyReplacement(proposal,index,page);
    return inspect(proposal);
  }

  private void validatePlanSnapshot(Context context,int index){
    var input=context.input();if(index>=input.plan().size())throw error(HttpStatus.CONFLICT,"PAGE_PLAN_NOT_FOUND","Page is absent from the deterministic plan snapshot");
    var planned=input.plan().get(index);var page=context.page();
    if(!planned.pageType().equals(page.pageType())||!planned.title().equals(page.title())
        ||!planned.knowledgeFactVersionIds().equals(page.knowledgeFactVersionIds()))
      throw error(HttpStatus.CONFLICT,"AI_LESSON_PAGE_REPAIR_PLAN_MISMATCH","Persisted page no longer matches its deterministic plan snapshot");
    var assigned=new java.util.LinkedHashSet<>(page.knowledgeFactVersionIds());
    var available=input.facts().stream().filter(f->f.sourceSectionId().equals(input.sourceSectionId()))
        .map(LessonGenerationProviderClient.Fact::versionId).collect(java.util.stream.Collectors.toSet());
    if(!available.containsAll(assigned))throw error(HttpStatus.CONFLICT,"SOURCE_CONTEXT_MISMATCH","Assigned Facts do not belong to the bounded Source context");
  }

  private long elapsed(long started){return Math.max(0,(System.nanoTime()-started)/1_000_000);}
  private void persistAttempt(UUID proposal,int index,UUID replaces,String status,String failure,
      LessonGenerationProviderClient.PageRepairResult generated,long latency,String key,String actor){
    var usage=generated==null?null:generated.usage();var content=generated==null?null:generated.content();
    jdbc.sql("""
      INSERT INTO ai_lesson_page_repair_attempt(id,lesson_proposal_id,page_index,replaces_revision_id,
        contract_version,status,failure_code,provider,model,provider_request_id,input_tokens,output_tokens,
        reasoning_tokens,latency_millis,free_only,idempotency_key,mutable_response,created_by,created_at)
      VALUES(:id,:proposal,:page,:replaces,:contract,:status,:failure,:provider,:model,:request,:input,:output,
        NULL,:latency,true,:key,CAST(:response AS jsonb),:actor,:now)
      ON CONFLICT(lesson_proposal_id,page_index,idempotency_key) DO NOTHING
      """).param("id",UUID.randomUUID()).param("proposal",proposal).param("page",index).param("replaces",replaces)
        .param("contract",PROMPT_VERSION).param("status",status).param("failure",failure,java.sql.Types.VARCHAR)
        .param("provider",usage==null?null:usage.provider(),java.sql.Types.VARCHAR).param("model",usage==null?null:usage.model(),java.sql.Types.VARCHAR)
        .param("request",usage==null?null:usage.requestId(),java.sql.Types.VARCHAR).param("input",usage==null?null:usage.inputTokens(),java.sql.Types.INTEGER)
        .param("output",usage==null?null:usage.outputTokens(),java.sql.Types.INTEGER).param("latency",latency).param("key",key,java.sql.Types.VARCHAR)
        .param("response",content==null?null:json(content),java.sql.Types.VARCHAR).param("actor",actor).param("now",now()).update();
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
  private List<LessonGenerationProviderClient.FailedClaim> failedClaimsForPage(UUID proposal,int pageIndex){return jdbc.sql("""
      SELECT DISTINCT c.claim_text,c.failure_code,c.diagnostic
      FROM ai_lesson_page_claim c JOIN ai_lesson_page_revision r ON r.id=c.page_revision_id
      WHERE r.lesson_proposal_id=:proposal AND r.page_index=:page AND c.status='REJECTED'
      ORDER BY c.claim_text,c.failure_code,c.diagnostic
      """).param("proposal",proposal).param("page",pageIndex)
      .query((rs,row)->new LessonGenerationProviderClient.FailedClaim(rs.getString(1),rs.getString(2),rs.getString(3))).list();}
  static String stripExactFailedClaims(String body,List<LessonGenerationProviderClient.FailedClaim> failedClaims){
    var rejected=failedClaims.stream().map(LessonGenerationProviderClient.FailedClaim::text)
        .map(LessonPageRepairService::normalizeClaim).collect(java.util.stream.Collectors.toSet());
    return java.util.Arrays.stream(body.trim().split("(?<=[.!?])\\s+"))
        .map(String::trim).filter(sentence->!sentence.isBlank())
        .filter(sentence->!rejected.contains(normalizeClaim(sentence)))
        .collect(java.util.stream.Collectors.joining(" "));
  }
  private static String normalizeClaim(String value){return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFKC)
      .toLowerCase(java.util.Locale.ROOT).replace('\u00a0',' ').replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();}
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
