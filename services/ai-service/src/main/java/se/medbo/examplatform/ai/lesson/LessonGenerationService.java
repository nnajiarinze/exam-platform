package se.medbo.examplatform.ai.lesson;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.ai.generation.AiApiException;
import se.medbo.examplatform.ai.provider.AiProviderException;

@Service
public class LessonGenerationService {
  public static final String PROMPT_VERSION="lesson-generation-v2-multi-page";
  private final JdbcClient jdbc;private final ObjectMapper mapper;private final LessonGenerationProviderClient provider;
  private final LessonPagePlanStore plans;
  private final boolean enabled;private final String providerName,model;private final int maxRetries;
  public record Create(UUID topicId,String topicTitle,UUID learningObjectiveId,
      String learningObjectiveTitle,UUID sourceSectionId,String sourceSectionTitle,
      String sourceSectionChecksum,String exactSourceText,List<LessonGenerationProviderClient.Fact> facts,
      List<LessonGenerationProviderClient.PlannedPage> plan,
      String language,String requestedBy,String idempotencyKey){}

  LessonGenerationService(JdbcClient jdbc,ObjectMapper mapper,LessonGenerationProviderClient provider,LessonPagePlanStore plans,
      @Value("${ai.editorial.enabled:false}")boolean enabled,
      @Value("${ai.editorial.provider:FAKE}")String providerName,
      @Value("${ai.editorial.model:deterministic-v1}")String model,
      @Value("${ai.editorial.max-retries:2}")int maxRetries){
    this.jdbc=jdbc;this.mapper=mapper;this.provider=provider;this.plans=plans;this.enabled=enabled;
    this.providerName=providerName;this.model=model;this.maxRetries=maxRetries;
  }

  @Transactional public Map<String,Object> create(Create input){
    if(!enabled)throw error(HttpStatus.SERVICE_UNAVAILABLE,"AI_FEATURE_DISABLED","AI-assisted generation is disabled");
    if(input.topicId()==null||input.learningObjectiveId()==null||input.sourceSectionId()==null
        ||blank(input.topicTitle())||blank(input.learningObjectiveTitle())||blank(input.sourceSectionTitle())
        ||blank(input.sourceSectionChecksum())||!input.sourceSectionChecksum().matches("[a-f0-9]{64}")
        ||blank(input.exactSourceText())
        ||input.facts()==null||input.facts().isEmpty()||input.facts().size()>10
        ||input.plan()==null||input.plan().size()<3||input.plan().size()>6
        ||blank(input.language())||blank(input.requestedBy())||blank(input.idempotencyKey()))
      throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AI_LESSON_INPUT_INVALID","Complete topic, objective, section, fact, language, requester, and idempotency data is required");
    if(input.facts().stream().anyMatch(f->f.id()==null||f.versionId()==null||blank(f.text())
        ||!input.sourceSectionId().equals(f.sourceSectionId())))
      throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AI_LESSON_GROUNDING_INVALID","Every fact must be versioned and mapped to the requested Source Section");
    var versions=input.facts().stream().map(LessonGenerationProviderClient.Fact::versionId).collect(java.util.stream.Collectors.toSet());
    if(input.plan().stream().anyMatch(p->blank(p.pageType())||blank(p.title())
        ||p.knowledgeFactVersionIds()==null||p.knowledgeFactVersionIds().isEmpty()
        ||!versions.containsAll(p.knowledgeFactVersionIds())))
      throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AI_LESSON_PLAN_INVALID","The deterministic page plan must contain 3-6 grounded pages");
    var existing=jdbc.sql("SELECT id FROM ai_lesson_generation_job WHERE requested_by=:actor AND idempotency_key=:key")
        .param("actor",input.requestedBy()).param("key",input.idempotencyKey()).query(UUID.class).optional();
    if(existing.isPresent())return get(existing.get());
    UUID id=UUID.randomUUID();var now=now();
    jdbc.sql("""
        INSERT INTO ai_lesson_generation_job(
          id,topic_id,learning_objective_id,source_section_id,input_snapshot,requested_by,
          idempotency_key,language,status,provider,model,prompt_version,next_attempt_at,created_at)
        VALUES(:id,:topic,:objective,:section,CAST(:snapshot AS jsonb),:actor,:key,:language,
          'QUEUED',:provider,:model,:prompt,:now,:now)
        """).param("id",id).param("topic",input.topicId()).param("objective",input.learningObjectiveId())
        .param("section",input.sourceSectionId()).param("snapshot",json(input))
        .param("actor",input.requestedBy()).param("key",input.idempotencyKey())
        .param("language",input.language()).param("provider",providerName).param("model",model)
        .param("prompt",PROMPT_VERSION).param("now",now).update();
    return get(id);
  }

  public Map<String,Object> get(UUID id){
    var rows=jdbc.sql("""
        SELECT id,topic_id AS "topicId",learning_objective_id AS "learningObjectiveId",
          source_section_id AS "sourceSectionId",requested_by AS "requestedBy",language,status,
          provider,model,actual_provider AS "actualProvider",actual_model AS "actualModel",
          prompt_version AS "promptVersion",retry_count AS "retryCount",
          input_tokens AS "inputTokens",output_tokens AS "outputTokens",
          provider_request_id AS "providerRequestId",error_code AS "errorCode",
          error_message AS "errorMessage",created_at AS "createdAt",started_at AS "startedAt",
          completed_at AS "completedAt",failed_at AS "failedAt",version
        FROM ai_lesson_generation_job WHERE id=:id
        """).param("id",id).query().listOfRows();
    if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"AI_LESSON_JOB_NOT_FOUND","Lesson generation job was not found");
    var result=new LinkedHashMap<>(rows.getFirst());
    var proposals=proposals(id);result.put("proposalCount",proposals.size());return result;
  }

  public List<Map<String,Object>> proposals(UUID job){
    var rows=jdbc.sql("""
        SELECT id,generation_job_id AS "generationJobId",title,introduction,summary,
          fact_statements::text AS "factStatements",key_terms::text AS "keyTerms",
          important_points::text AS "importantPoints",pages::text AS pages,status,
          automated_classification AS "automatedClassification",
          validation_gates::text AS "validationGates",created_at AS "createdAt",
          accepted_lesson_draft_id AS "acceptedLessonDraftId",accepted_by AS "acceptedBy",
          accepted_at AS "acceptedAt",updated_at AS "updatedAt",version
        FROM ai_lesson_proposal WHERE generation_job_id=:job
        """).param("job",job).query().listOfRows();
    rows.forEach(row->{parse(row,"factStatements",List.class);parse(row,"keyTerms",List.class);
      parse(row,"importantPoints",List.class);parse(row,"pages",List.class);parse(row,"validationGates",Map.class);});
    return rows;
  }

  @Transactional public Map<String,Object> markAccepted(UUID proposal,UUID lesson,String actor,long version){
    int changed=jdbc.sql("""
        UPDATE ai_lesson_proposal SET status='ACCEPTED',accepted_lesson_draft_id=:lesson,
          accepted_by=:actor,accepted_at=:now,updated_at=:now,version=version+1
        WHERE id=:id AND version=:version AND status='PROPOSED'
        """).param("lesson",lesson).param("actor",actor).param("now",now())
        .param("id",proposal).param("version",version).update();
    if(changed==0)throw error(HttpStatus.CONFLICT,"AI_LESSON_PROPOSAL_STALE","Lesson proposal is no longer acceptable");
    jdbc.sql("""
        INSERT INTO ai_audit_event(id,occurred_at,actor_id,action,entity_type,entity_id,metadata)
        VALUES(:id,:now,:actor,'AI_LESSON_PROPOSAL_ACCEPTED','AI_LESSON_PROPOSAL',:proposal,
          CAST(:metadata AS jsonb))
        """).param("id",UUID.randomUUID()).param("now",now()).param("actor",actor)
        .param("proposal",proposal).param("metadata",json(Map.of("lessonDraftId",lesson))).update();
    return proposal(proposal);
  }

  public Map<String,Object> proposal(UUID id){
    var rows=jdbc.sql("SELECT generation_job_id FROM ai_lesson_proposal WHERE id=:id")
        .param("id",id).query(UUID.class).list();
    if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"AI_LESSON_PROPOSAL_NOT_FOUND","Lesson proposal was not found");
    return proposals(rows.getFirst()).getFirst();
  }

  @Transactional public Map<String,Object> revalidate(UUID id){
    var row=jdbc.sql("""
        SELECT p.pages::text,p.validation_gates::text,j.input_snapshot::text
        FROM ai_lesson_proposal p JOIN ai_lesson_generation_job j ON j.id=p.generation_job_id
        WHERE p.id=:id AND p.status='PROPOSED'
        """).param("id",id).query().listOfRows();
    if(row.isEmpty())throw error(HttpStatus.NOT_FOUND,"AI_LESSON_PROPOSAL_NOT_FOUND","Lesson proposal was not found");
    try{
      var pages=mapper.readValue((String)row.getFirst().get("pages"),new TypeReference<List<LessonGenerationProviderClient.Page>>(){});
      var input=mapper.readValue((String)row.getFirst().get("input_snapshot"),Create.class);
      String source=normalizeEvidence(input.exactSourceText());
      var repairedPages=pages.stream().map(p->new LessonGenerationProviderClient.Page(p.pageType(),p.title(),
          p.body(),p.knowledgeFactVersionIds(),p.evidenceQuotes()==null?List.of():p.evidenceQuotes().stream()
              .filter(q->!blank(q)&&q.trim().split("\\s+").length>=4&&source.contains(normalizeEvidence(q))).toList(),
          p.keyTerms())).toList();
      boolean evidence=repairedPages.stream().allMatch(p->!p.evidenceQuotes().isEmpty());
      Map<String,Boolean> gates=mapper.readValue((String)row.getFirst().get("validation_gates"),new TypeReference<>(){});
      gates.put("sourceEvidencePassed",evidence);
      boolean nonEmpty=repairedPages.stream().allMatch(p->!blank(p.title())&&!blank(p.body()));
      boolean terms=repairedPages.stream().allMatch(p->p.keyTerms()!=null&&p.keyTerms().stream()
          .allMatch(v->v!=null&&!v.isBlank()&&v.length()<=80));
      boolean wordBounds=repairedPages.stream().allMatch(p->{int words=p.body().trim().split("\\s+").length;return words>=40&&words<=240;});
      gates.put("learnerUsabilityPassed",nonEmpty&&terms&&wordBounds);
      gates.put("placeholderCheckPassed",nonEmpty&&!containsPlaceholder(repairedPages));
      gates.put("duplicateSectionCheckPassed",repairedPages.stream().map(p->normalize(p.body())).distinct().count()==repairedPages.size());
      String classification=gates.values().stream().allMatch(Boolean.TRUE::equals)?"GOOD":"NEEDS_REWRITE";
      jdbc.sql("UPDATE ai_lesson_proposal SET pages=CAST(:pages AS jsonb),validation_gates=CAST(:gates AS jsonb),automated_classification=:classification,updated_at=:now,version=version+1 WHERE id=:id")
          .param("pages",json(repairedPages)).param("gates",json(gates)).param("classification",classification).param("now",now()).param("id",id).update();
      return proposal(id);
    }catch(AiApiException e){throw e;}catch(Exception e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,
        "AI_LESSON_REVALIDATION_FAILED","Lesson proposal could not be revalidated");}
  }

  @Scheduled(fixedDelay=1000) public void work(){
    if(!enabled)return;
    var jobs=jdbc.sql("""
        SELECT id FROM ai_lesson_generation_job
        WHERE status='QUEUED' AND next_attempt_at<=now() ORDER BY created_at LIMIT 1
        """).query(UUID.class).list();
    if(!jobs.isEmpty())run(jobs.getFirst());
  }

  void run(UUID id){
    int claimed=jdbc.sql("""
        UPDATE ai_lesson_generation_job SET status='RUNNING',started_at=coalesce(started_at,:now),
          version=version+1 WHERE id=:id AND status='QUEUED'
        """).param("now",now()).param("id",id).update();
    if(claimed==0)return;
    var row=jdbc.sql("SELECT * FROM ai_lesson_generation_job WHERE id=:id").param("id",id).query().singleRow();
    try{
      Create input=mapper.readValue(String.valueOf(row.get("input_snapshot")),Create.class);
      var request=new LessonGenerationProviderClient.Request(input.topicId(),input.topicTitle(),
          input.learningObjectiveId(),input.learningObjectiveTitle(),input.sourceSectionId(),
          input.sourceSectionTitle(),input.sourceSectionChecksum(),input.exactSourceText(),input.facts(),
          input.plan(),input.language(),id,input.requestedBy(),(Integer)row.get("retry_count"));
      persist(id,input,provider.generateLesson(request));
    }catch(AiProviderException e){failOrRetry(id,(Integer)row.get("retry_count"),e);}
    catch(Exception e){failOrRetry(id,(Integer)row.get("retry_count"),
        new AiProviderException("AI_PROVIDER_RESPONSE_INVALID",false,"Lesson provider response was invalid"));}
  }

  @Transactional void persist(UUID job,Create input,LessonGenerationProviderClient.Result result){
    var proposal=result.proposal();
    var expectedVersions=input.facts().stream().map(LessonGenerationProviderClient.Fact::versionId).collect(java.util.stream.Collectors.toSet());
    var providerPages=proposal.pages()==null?List.<LessonGenerationProviderClient.Page>of():proposal.pages();
    var pages=providerPages.size()==input.plan().size()?java.util.stream.IntStream.range(0,providerPages.size()).mapToObj(i->{
      var generated=providerPages.get(i);var planned=input.plan().get(i);
      return new LessonGenerationProviderClient.Page(planned.pageType(),planned.title(),generated.body(),
          List.copyOf(planned.knowledgeFactVersionIds()),generated.evidenceQuotes(),generated.keyTerms());
    }).toList():providerPages;
    var actualVersions=pages.stream().flatMap(p->p.knowledgeFactVersionIds().stream()).collect(java.util.stream.Collectors.toSet());
    boolean complete=actualVersions.equals(expectedVersions);
    boolean planMatches=pages.size()==input.plan().size()&&java.util.stream.IntStream.range(0,pages.size()).allMatch(i->{
      var actual=pages.get(i);var planned=input.plan().get(i);return actual.pageType().equals(planned.pageType())
          &&actual.title().trim().equals(planned.title().trim())
          &&new LinkedHashSet<>(actual.knowledgeFactVersionIds()).equals(new LinkedHashSet<>(planned.knowledgeFactVersionIds()));});
    boolean nonEmpty=!blank(proposal.title())&&!blank(proposal.introduction())&&!blank(proposal.summary())
        &&pages.stream().allMatch(p->!blank(p.title())&&!blank(p.body()));
    String normalizedSource=normalizeEvidence(input.exactSourceText());
    boolean evidence=pages.stream().allMatch(p->p.evidenceQuotes()!=null&&!p.evidenceQuotes().isEmpty()
        &&p.evidenceQuotes().stream().allMatch(q->!blank(q)&&q.trim().split("\\s+").length>=4
            &&normalizedSource.contains(normalizeEvidence(q))));
    boolean exact=pages.stream().allMatch(p->expectedVersions.containsAll(p.knowledgeFactVersionIds()));
    boolean distinct=pages.stream().map(p->normalize(p.body())).distinct().count()==pages.size();
    boolean wordBounds=pages.stream().allMatch(p->{int words=p.body().trim().split("\\s+").length;return words>=40&&words<=240;});
    boolean noOfficialClaim=java.util.stream.Stream.concat(
        java.util.stream.Stream.of(proposal.title(),proposal.introduction(),proposal.summary()),
        pages.stream().flatMap(p->java.util.stream.Stream.of(p.title(),p.body())))
        .filter(java.util.Objects::nonNull)
        .noneMatch(value->value.toLowerCase(java.util.Locale.ROOT).matches(".*\\b(uhr|officiell(a|t)? provfrågor?)\\b.*"));
    boolean terms=pages.stream().allMatch(p->p.keyTerms()!=null&&p.keyTerms().stream().allMatch(v->v!=null&&!v.isBlank()&&v.length()<=80));
    var gates=new LinkedHashMap<String,Boolean>();
    gates.put("approvedFactCoveragePassed",complete);gates.put("unsupportedStatementDetectionPassed",exact);
    gates.put("lessonStructurePassed",nonEmpty&&pages.size()>=3&&pages.size()<=6);gates.put("sectionOrderingPassed",planMatches);
    gates.put("topicMappingPassed",input.topicId()!=null);gates.put("learningObjectiveMappingPassed",input.learningObjectiveId()!=null);
    gates.put("sourceEvidencePassed",evidence);gates.put("sourceChecksumPassed",input.sourceSectionChecksum().matches("[a-f0-9]{64}"));
    gates.put("contradictionCheckPassed",exact);gates.put("placeholderCheckPassed",nonEmpty&&!containsPlaceholder(pages));
    gates.put("duplicateSectionCheckPassed",distinct);gates.put("swedishTextValidationPassed","sv".equalsIgnoreCase(input.language()));
    gates.put("learnerUsabilityPassed",nonEmpty&&terms&&wordBounds);gates.put("officialClaimCheckPassed",noOfficialClaim);
    String classification=gates.values().stream().allMatch(Boolean.TRUE::equals)?"GOOD":"NEEDS_REWRITE";
    var now=now();UUID proposalId=UUID.randomUUID();jdbc.sql("""
        INSERT INTO ai_lesson_proposal(id,generation_job_id,title,introduction,summary,fact_statements,
          key_terms,important_points,pages,status,automated_classification,validation_gates,created_at,updated_at)
        VALUES(:id,:job,:title,:introduction,:summary,CAST(:facts AS jsonb),CAST(:terms AS jsonb),
          CAST(:points AS jsonb),CAST(:pages AS jsonb),'PROPOSED',:classification,CAST(:gates AS jsonb),:now,:now)
        """).param("id",proposalId).param("job",job).param("title",proposal.title())
        .param("introduction",proposal.introduction()).param("summary",proposal.summary())
        .param("facts",json(input.facts().stream().map(LessonGenerationProviderClient.Fact::text).toList()))
        .param("terms",json(pages.stream().flatMap(p->p.keyTerms().stream()).distinct().toList()))
        .param("points",json(proposal.importantPoints()==null?List.of():proposal.importantPoints()))
        .param("pages",json(pages))
        .param("classification",classification).param("gates",json(gates)).param("now",now).update();
    for(int i=0;i<input.plan().size();i++)plans.createInitial(proposalId,i,input,input.plan().get(i),input.requestedBy());
    var usage=result.usage();jdbc.sql("""
        UPDATE ai_lesson_generation_job SET status='COMPLETED',completed_at=:now,input_tokens=:input,
          output_tokens=:output,provider_request_id=:request,actual_provider=:actualProvider,
          actual_model=:actualModel,version=version+1 WHERE id=:id
        """).param("now",now).param("input",usage==null?null:usage.inputTokens(),Types.INTEGER)
        .param("output",usage==null?null:usage.outputTokens(),Types.INTEGER)
        .param("request",usage==null?null:usage.requestId(),Types.VARCHAR)
        .param("actualProvider",usage==null?null:usage.provider(),Types.VARCHAR)
        .param("actualModel",usage==null?null:usage.model(),Types.VARCHAR).param("id",job).update();
  }

  private boolean containsPlaceholder(List<LessonGenerationProviderClient.Page> pages){return pages.stream()
      .map(p->p.body().toLowerCase(java.util.Locale.ROOT)).anyMatch(v->v.contains("lorem ipsum")||v.contains("[placeholder]")||v.contains("todo"));}
  private String normalize(String value){return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
  private String normalizeEvidence(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC)
      .replace('\u00a0',' ').replaceAll("\\s+"," ").trim();}

  private void failOrRetry(UUID id,int retries,AiProviderException error){
    if("AI_ALL_FREE_PROVIDERS_UNAVAILABLE".equals(error.code())){pause(id,error);return;}
    if(error.transientFailure()&&retries<maxRetries)jdbc.sql("""
        UPDATE ai_lesson_generation_job SET status='QUEUED',retry_count=retry_count+1,
          next_attempt_at=:next,error_code=:code,error_message=:message,version=version+1 WHERE id=:id
        """).param("next",now().plusSeconds(1L<<(retries+1))).param("code",error.code())
        .param("message",sanitize(error.getMessage())).param("id",id).update();
    else jdbc.sql("""
        UPDATE ai_lesson_generation_job SET status='FAILED',failed_at=:now,error_code=:code,
          error_message=:message,version=version+1 WHERE id=:id
        """).param("now",now()).param("code",error.code()).param("message",sanitize(error.getMessage()))
        .param("id",id).update();
  }

  private void pause(UUID id,AiProviderException error){
    var next=jdbc.sql("SELECT coalesce(max(next_retry_at),now()+interval '1 hour') FROM ai_provider_routing_decision WHERE job_id=:id AND outcome='PAUSED'").param("id",id).query(OffsetDateTime.class).single();
    jdbc.sql("UPDATE ai_lesson_generation_job SET status='QUEUED',next_attempt_at=:next,error_code=:code,error_message=:message,version=version+1 WHERE id=:id")
        .param("next",next).param("code",error.code()).param("message",sanitize(error.getMessage())).param("id",id).update();
  }

  private void parse(Map<String,Object> row,String key,Class<?> type){try{row.put(key,mapper.readValue((String)row.get(key),type));}catch(Exception e){throw new IllegalStateException(e);}}
  private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
  private boolean blank(String value){return value==null||value.isBlank();}
  private String sanitize(String value){return value==null?null:value.replaceAll("[\\r\\n]"," ").substring(0,Math.min(500,value.length()));}
  private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
  private AiApiException error(HttpStatus status,String code,String message){return new AiApiException(status,code,message);}
}
