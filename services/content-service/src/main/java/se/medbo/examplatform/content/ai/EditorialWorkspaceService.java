package se.medbo.examplatform.content.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Types;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.medbo.examplatform.content.shared.DomainException;
import se.medbo.examplatform.content.knowledge.KnowledgeFactTextQualityValidator;

@Service
public class EditorialWorkspaceService {
  private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(EditorialWorkspaceService.class);
  public enum Operation {
    REWRITE_FOR_CLARITY, SIMPLIFY_LANGUAGE, MAKE_ATOMIC, SPLIT_FACT,
    CHECK_SOURCE_SUPPORT, DETECT_AMBIGUITY, EDITORIAL_REVIEW_NOTES
  }
  public enum SplitAcceptanceMode { CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL }
  public record Create(Operation operation, UUID targetKnowledgeFactId, String language,
                       String readingPreference, String instruction, int requestedCount,
                       String idempotencyKey) {}

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(java.time.Duration.ofSeconds(5)).build();
  private final String baseUrl;
  private final String apiKey;
  private final EditorialGroundingAuditService groundingAudit;
  private final MeterRegistry metrics;
  private final KnowledgeFactTextQualityValidator textQuality;

  public EditorialWorkspaceService(
      JdbcClient jdbc, ObjectMapper mapper, EditorialGroundingAuditService groundingAudit, MeterRegistry metrics,
      KnowledgeFactTextQualityValidator textQuality,
      @Value("${content.ai-service.base-url:http://localhost:8083}") String baseUrl,
      @Value("${content.ai-service.internal-api-key:}") String apiKey) {
    this.jdbc = jdbc;
    this.mapper = mapper;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.groundingAudit = groundingAudit;
    this.metrics = metrics;
    this.textQuality = textQuality;
  }

  public Map<String, Object> create(Create input) {
    configured();
    boolean mutationPreparation = List.of(Operation.REWRITE_FOR_CLARITY, Operation.SIMPLIFY_LANGUAGE, Operation.SPLIT_FACT).contains(input.operation());
    var target = mutationPreparation ? editableTarget(input.targetKnowledgeFactId()) : analysisTarget(input.targetKnowledgeFactId());
    var validation = textQuality.validate(String.valueOf(target.get("canonicalStatement")));
    if (validation.quality() == KnowledgeFactTextQualityValidator.Quality.INVALID) {
      groundingAudit.rejected(input.targetKnowledgeFactId(), "AI_EDITORIAL_INPUT_INVALID", null);
      metrics.counter("ai.editorial.input.blocked", "quality", "invalid", "operation", input.operation().name()).increment();
      LOG.warn("ai_editorial_input_blocked factId={} actor={} operation={} quality={} issues={}", input.targetKnowledgeFactId(), actor(), input.operation(), validation.quality(), validation.issues().stream().map(KnowledgeFactTextQualityValidator.Issue::code).toList());
      throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_EDITORIAL_INPUT_INVALID",
          "The Knowledge Fact does not appear to contain meaningful factual content. Edit and save the fact before running AI editorial tools",
          validation.issues().stream().map(issue -> (Object) Map.of("code", issue.code(), "message", issue.message(),
              "quality", validation.quality().name(), "recommendedAction", "EDIT_FACT")).toList());
    }
    if (validation.quality() == KnowledgeFactTextQualityValidator.Quality.SUSPICIOUS) {
      metrics.counter("ai.editorial.input.detected", "quality", "suspicious", "operation", input.operation().name()).increment();
      if (!List.of(Operation.DETECT_AMBIGUITY, Operation.EDITORIAL_REVIEW_NOTES, Operation.CHECK_SOURCE_SUPPORT).contains(input.operation()))
        throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_EDITORIAL_INPUT_SUSPICIOUS",
            "The Knowledge Fact is meaningful but too vague for a transformation. Run an analysis operation or improve the draft first");
    }
    boolean sourcesRequired = input.operation() != Operation.DETECT_AMBIGUITY;
    var sources = sourceContent(uuid(target.get("currentVersionId")), sourcesRequired);
    if (List.of(Operation.REWRITE_FOR_CLARITY, Operation.SIMPLIFY_LANGUAGE).contains(input.operation())
        && sources.stream().noneMatch(source -> plausiblySupports(String.valueOf(target.get("canonicalStatement")), String.valueOf(source.get("contentText")))))
      {
        groundingAudit.rejected(input.targetKnowledgeFactId(), "AI_EDITORIAL_INSUFFICIENT_EVIDENCE", null);
        metrics.counter("ai.editorial.grounding.rejected", "reason", "pre_generation_insufficient_evidence").increment();
        throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_EDITORIAL_INSUFFICIENT_EVIDENCE",
            "The linked Sources do not plausibly support this fact. Review linked Sources or run Check Source Support first");
      }
    var payload = new LinkedHashMap<String, Object>();
    payload.put("operation", input.operation().name());
    payload.put("targets", List.of(Map.of(
        "factId", target.get("id"),
        "factVersionId", target.get("currentVersionId"),
        "version", target.get("version"),
        "text", target.get("canonicalStatement"),
        "checksum", checksum((String) target.get("canonicalStatement")))));
    payload.put("sources", sources.stream().map(source -> Map.of(
        "sourceId", source.get("id"), "title", source.get("title"), "text", source.get("contentText"),
        "checksum", source.get("contentChecksum"))).toList());
    payload.put("learningObjectiveId", target.get("learningObjectiveId"));
    payload.put("objectiveTitle", target.get("learningObjectiveTitle"));
    payload.put("language", input.language());
    payload.put("readingPreference", input.readingPreference() == null ? "" : input.readingPreference());
    payload.put("instruction", input.instruction() == null ? "" : input.instruction());
    payload.put("count", input.requestedCount());
    payload.put("requestedBy", actor());
    payload.put("idempotencyKey", input.idempotencyKey());
    return post("/internal/v1/editorial/jobs", payload);
  }

  public Map<String, Object> job(UUID id) {
    var result = get("/internal/v1/editorial/jobs/" + id);
    authorize(result);
    return result;
  }

  public Map<String,Object> providerStatus(){ return get("/internal/v1/provider/status"); }
  public List<Map<String,Object>> providerAlerts(){ return getList("/internal/v1/provider/alerts"); }
  public Map<String,Object> disableProvider(){ return post("/internal/v1/provider/disable",Map.of("actor",actor())); }
  public Map<String,Object> recheckProvider(){ return post("/internal/v1/provider/recheck",Map.of("actor",actor())); }
  public void acknowledgeProviderAlert(UUID id){ postWithoutResponse("/internal/v1/provider/alerts/"+id+"/acknowledge",Map.of("actor",actor())); }

  public List<Map<String, Object>> proposals(UUID id) {
    job(id);
    return getList("/internal/v1/editorial/jobs/" + id + "/proposals");
  }

  public List<Map<String, Object>> findings(UUID id) {
    job(id);
    return getList("/internal/v1/editorial/jobs/" + id + "/findings");
  }

  public Map<String, Object> dismissFinding(UUID id, String reason, long version) {
    requireAnalysis();
    var finding = get("/internal/v1/editorial/findings/" + id);
    authorize(get("/internal/v1/editorial/jobs/" + finding.get("generationJobId")));
    return post("/internal/v1/editorial/findings/" + id + "/dismiss",
        Map.of("actor", actor(), "reason", reason == null ? "" : reason, "version", version));
  }

  public Map<String, Object> cancel(UUID id) {
    var current = job(id);
    if (!isAdmin() && !actor().equals(current.get("requestedBy"))) throw forbidden();
    return post("/internal/v1/editorial/jobs/" + id + "/cancel", Map.of());
  }

  public Map<String, Object> edit(UUID id, String text, long version) {
    requireAuthor();
    authorizeProposal(id);
    return patch("/internal/v1/editorial/proposals/" + id,
        Map.of("text", text, "version", version));
  }

  public Map<String, Object> reject(UUID id, String reason, long version) {
    requireAuthor();
    authorizeProposal(id);
    return post("/internal/v1/editorial/proposals/" + id + "/reject",
        Map.of("reason", reason == null ? "" : reason, "version", version));
  }

  @Transactional
  public Map<String, Object> accept(UUID proposalId, long proposalVersion) {
    requireAuthor();
    var accepted = jdbc.sql("SELECT knowledge_fact_version_id FROM knowledge_fact_ai_editorial_provenance WHERE proposal_id=:id")
        .param("id", proposalId).query(UUID.class).optional();
    if (accepted.isPresent()) return factByVersion(accepted.get());

    var proposal = authorizeProposal(proposalId);
    if (!List.of("REWRITE_FOR_CLARITY", "SIMPLIFY_LANGUAGE").contains(String.valueOf(proposal.get("operation_type"))))
      throw conflict("AI_EDITORIAL_OPERATION_UNSUPPORTED", "This proposal must use split acceptance");
    if (!List.of("PROPOSED", "EDITED").contains(proposal.get("status")))
      throw conflict("AI_EDITORIAL_PROPOSAL_ALREADY_PROCESSED", "The proposal was already processed");
    UUID factId = uuid(proposal.get("target_fact_id"));
    var target = lockTarget(factId);
    validateSnapshot(target, proposal);
    String finalText = String.valueOf(proposal.get("edited_text")).trim();
    validateText(finalText);
    if (noMeaningfulChange(String.valueOf(target.get("canonical_statement")), finalText))
      throw conflict("AI_EDITORIAL_NO_MEANINGFUL_CHANGE", "The final text does not meaningfully differ from the current fact");
    validateFinalGrounding(proposalId, proposal, finalText, uuid(target.get("current_version_id")));
    long duplicate = jdbc.sql("SELECT count(*) FROM knowledge_fact WHERE learning_objective_id=:objective AND id<>:id AND status<>'RETIRED' AND lower(regexp_replace(trim(canonical_statement),'\\s+',' ','g'))=:text")
        .param("objective", target.get("learning_objective_id")).param("id", factId)
        .param("text", normalize(finalText)).query(Long.class).single();
    if (duplicate > 0) throw conflict("AI_EDITORIAL_PROPOSAL_DUPLICATE", "An equivalent Knowledge Fact already exists");

    UUID factVersionId = uuid(target.get("current_version_id"));
    var now = OffsetDateTime.now(ZoneOffset.UTC);
    int changed = jdbc.sql("UPDATE knowledge_fact SET canonical_statement=:text,updated_at=:now,version=version+1 WHERE id=:id AND version=:version AND status='DRAFT' AND review_status IN ('UNREVIEWED','REQUIRES_UPDATE')")
        .param("text", finalText).param("now", now).param("id", factId)
        .param("version", ((Number) target.get("version")).longValue()).update();
    if (changed == 0) throw stale();
    jdbc.sql("UPDATE knowledge_fact_version SET canonical_statement=:text,updated_at=:now WHERE id=:id")
        .param("text", finalText).param("now", now).param("id", factVersionId).update();

    var sourceRows = sourceContent(factVersionId, true);
    jdbc.sql("INSERT INTO knowledge_fact_ai_editorial_provenance(id,knowledge_fact_version_id,operation_type,generation_job_id,proposal_id,acceptance_action,provider,model,prompt_version,generated_at,target_fact_ids,target_fact_version_ids,target_content_checksums,source_ids,source_checksums,original_content_checksum,original_content,proposed_content,final_accepted_content,edited_before_acceptance,edit_count,edit_distance,final_text_checksum,requesting_user_id,accepting_user_id,accepted_at,source_evidence,warnings,provider_request_id,input_tokens,output_tokens) VALUES(:id,:version,:operation,:job,:proposal,'UPDATE_EXISTING_DRAFT',:provider,:model,:prompt,:generated,CAST(:targetIds AS jsonb),CAST(:targetVersions AS jsonb),CAST(:targetChecksums AS jsonb),CAST(:sourceIds AS jsonb),CAST(:sourceChecksums AS jsonb),:originalChecksum,:original,:proposed,:final,:edited,:editCount,NULL,:finalChecksum,:requester,:actor,:now,CAST(:evidence AS jsonb),CAST(:warnings AS jsonb),:providerRequest,:inputTokens,:outputTokens)")
        .param("id", UUID.randomUUID()).param("version", factVersionId)
        .param("operation", proposal.get("operation_type")).param("job", uuid(proposal.get("generation_job_id")))
        .param("proposal", proposalId).param("provider", proposal.get("provider"))
        .param("model", proposal.get("model")).param("prompt", proposal.get("prompt_version"))
        .param("generated", OffsetDateTime.parse(String.valueOf(proposal.get("generated_at"))))
        .param("targetIds", json(List.of(factId))).param("targetVersions", json(List.of(factVersionId)))
        .param("targetChecksums", json(List.of(proposal.get("original_checksum"))))
        .param("sourceIds", json(sourceRows.stream().map(r -> r.get("id")).toList()))
        .param("sourceChecksums", json(sourceRows.stream().map(r -> r.get("contentChecksum")).toList()))
        .param("originalChecksum", proposal.get("original_checksum"))
        .param("original", proposal.get("original_text")).param("proposed", proposal.get("proposed_text"))
        .param("final", finalText).param("edited", !finalText.equals(proposal.get("proposed_text")))
        .param("editCount", proposal.get("edit_count")).param("finalChecksum", checksum(finalText))
        .param("requester", proposal.get("requested_by")).param("actor", actor()).param("now", now)
        .param("evidence", proposalJson(proposal,"evidence_json","source_evidence"))
        .param("warnings", proposalJson(proposal,"warnings_json","warnings"))
        .param("providerRequest", proposal.get("provider_request_id"), Types.VARCHAR)
        .param("inputTokens", proposal.get("input_tokens"), Types.INTEGER)
        .param("outputTokens", proposal.get("output_tokens"), Types.INTEGER).update();
    postWithoutResponse("/internal/v1/editorial/proposals/" + proposalId + "/accepted",
        Map.of("factId", factId, "factVersionId", factVersionId, "actor", actor(), "version", proposalVersion));
    return fact(factId);
  }

  @Transactional
  public Map<String, Object> acceptSplit(UUID jobId, UUID targetFactId, UUID targetFactVersionId,
                                         String targetContentChecksum, List<UUID> selectedProposalIds,
                                         SplitAcceptanceMode mode, String idempotencyKey) {
    requireAuthor();
    if (selectedProposalIds == null || selectedProposalIds.isEmpty())
      throw conflict("AI_EDITORIAL_SPLIT_SELECTION_EMPTY", "Select at least one split proposal");
    var prior = jdbc.sql("SELECT resulting_fact_ids::text FROM ai_split_acceptance WHERE requested_by=:actor AND idempotency_key=:key")
        .param("actor", actor()).param("key", idempotencyKey).query(String.class).optional();
    if (prior.isPresent()) {
      List<?> storedIds = readJson(prior.get());
      return splitResult(targetFactId, mode, storedIds.stream().map(this::uuid).toList());
    }
    var job = job(jobId);
    if (!List.of("SPLIT_FACT", "MAKE_ATOMIC").contains(job.get("operationType")))
      throw conflict("AI_EDITORIAL_OPERATION_UNSUPPORTED", "Only split-style jobs support split acceptance");
    if (!List.of("COMPLETED", "PARTIALLY_COMPLETED").contains(job.get("status")))
      throw conflict("AI_EDITORIAL_SPLIT_ACCEPTANCE_FAILED", "The editorial job is not complete");
    var target = lockTarget(targetFactId);
    if (!targetFactVersionId.equals(target.get("current_version_id"))
        || !targetContentChecksum.equals(checksum((String) target.get("canonical_statement")))) throw stale();

    var proposals = new ArrayList<Map<String, Object>>();
    for (UUID proposalId : selectedProposalIds.stream().distinct().toList()) {
      var proposal = authorizeProposal(proposalId);
      if (!jobId.equals(uuid(proposal.get("generation_job_id")))
          || !targetFactId.equals(uuid(proposal.get("target_fact_id")))
          || !List.of("PROPOSED", "EDITED").contains(proposal.get("status")))
        throw conflict("AI_EDITORIAL_SPLIT_ACCEPTANCE_FAILED", "A selected proposal does not belong to this active split job");
      validateSnapshot(target, proposal);
      proposals.add(proposal);
    }
    var normalized = new java.util.HashSet<String>();
    UUID objectiveId = uuid(target.get("learning_objective_id"));
    for (var proposal : proposals) {
      String text = String.valueOf(proposal.get("edited_text")).trim();
      validateText(text);
      if (!normalized.add(normalize(text))) throw splitDuplicate();
      long duplicates = jdbc.sql("SELECT count(*) FROM knowledge_fact WHERE learning_objective_id=:objective AND status<>'RETIRED' AND lower(regexp_replace(trim(canonical_statement),'\\s+',' ','g'))=:text")
          .param("objective", objectiveId).param("text", normalize(text)).query(Long.class).single();
      if (duplicates > 0) throw splitDuplicate();
    }

    var now = OffsetDateTime.now(ZoneOffset.UTC);
    var resultingFactIds = proposals.stream().map(ignored -> UUID.randomUUID()).toList();
    var resultingVersionIds = proposals.stream().map(ignored -> UUID.randomUUID()).toList();
    for (int index = 0; index < proposals.size(); index++) {
      var proposal = proposals.get(index);
      UUID factId = resultingFactIds.get(index), versionId = resultingVersionIds.get(index);
      String text = String.valueOf(proposal.get("edited_text")).trim();
      jdbc.sql("INSERT INTO knowledge_fact(id,learning_objective_id,canonical_statement,review_status,status,created_at,updated_at) VALUES(:id,:objective,:text,'UNREVIEWED','DRAFT',:now,:now)")
          .param("id", factId).param("objective", objectiveId).param("text", text).param("now", now).update();
      jdbc.sql("INSERT INTO knowledge_fact_version(id,knowledge_fact_id,version_number,canonical_statement,review_status,author_id,created_at,updated_at) VALUES(:id,:fact,1,:text,'UNREVIEWED',:actor,:now,:now)")
          .param("id", versionId).param("fact", factId).param("text", text).param("actor", actor()).param("now", now).update();
      jdbc.sql("UPDATE knowledge_fact SET current_version_id=:version WHERE id=:fact")
          .param("version", versionId).param("fact", factId).update();
      var evidence = this.<List<Map<String, Object>>>readJson(proposalJson(proposal,"evidence_json","source_evidence"));
      var evidenceSourceIds = evidence.stream().map(item -> uuid(item.get("sourceId"))).distinct().toList();
      var currentSources = sourceContent(targetFactVersionId, true);
      if (!currentSources.stream().map(row -> uuid(row.get("id"))).toList().containsAll(evidenceSourceIds)) throw staleSource();
      for (UUID sourceId : evidenceSourceIds) jdbc.sql("INSERT INTO knowledge_fact_source(knowledge_fact_version_id,source_reference_id) VALUES(:version,:source)")
          .param("version", versionId).param("source", sourceId).update();
      insertSplitProvenance(versionId, jobId, proposal, target, currentSources, resultingFactIds, text, mode, now);
    }
    jdbc.sql("INSERT INTO ai_split_acceptance(id,generation_job_id,target_fact_id,target_fact_version_id,acceptance_mode,selected_proposal_ids,resulting_fact_ids,requested_by,idempotency_key,created_at) VALUES(:id,:job,:target,:targetVersion,:mode,CAST(:proposals AS jsonb),CAST(:results AS jsonb),:actor,:key,:now)")
        .param("id", UUID.randomUUID()).param("job", jobId).param("target", targetFactId)
        .param("targetVersion", targetFactVersionId).param("mode", mode.name())
        .param("proposals", json(selectedProposalIds)).param("results", json(resultingFactIds))
        .param("actor", actor()).param("key", idempotencyKey).param("now", now).update();
    for (int index = 0; index < proposals.size(); index++) {
      var proposal = proposals.get(index);
      postWithoutResponse("/internal/v1/editorial/proposals/" + proposal.get("id") + "/accepted",
          Map.of("factId", resultingFactIds.get(index), "factVersionId", resultingVersionIds.get(index),
              "actor", actor(), "version", ((Number) proposal.get("version")).longValue()));
    }
    return splitResult(targetFactId, mode, resultingFactIds);
  }

  private void insertSplitProvenance(UUID versionId, UUID jobId, Map<String, Object> proposal,
                                     Map<String, Object> target, List<Map<String, Object>> sources,
                                     List<UUID> siblingIds, String finalText, SplitAcceptanceMode mode,
                                     OffsetDateTime now) {
    jdbc.sql("INSERT INTO knowledge_fact_ai_editorial_provenance(id,knowledge_fact_version_id,operation_type,generation_job_id,proposal_id,acceptance_action,provider,model,prompt_version,generated_at,target_fact_ids,target_fact_version_ids,target_content_checksums,source_ids,source_checksums,original_content_checksum,original_content,proposed_content,final_accepted_content,edited_before_acceptance,edit_count,edit_distance,final_text_checksum,requesting_user_id,accepting_user_id,accepted_at,source_evidence,warnings,provider_request_id,input_tokens,output_tokens,sibling_resulting_fact_ids) VALUES(:id,:version,:operation,:job,:proposal,:action,:provider,:model,:prompt,:generated,CAST(:targetIds AS jsonb),CAST(:targetVersions AS jsonb),CAST(:targetChecksums AS jsonb),CAST(:sourceIds AS jsonb),CAST(:sourceChecksums AS jsonb),:originalChecksum,:original,:proposed,:final,:edited,:editCount,NULL,:finalChecksum,:requester,:actor,:now,CAST(:evidence AS jsonb),CAST(:warnings AS jsonb),:providerRequest,:inputTokens,:outputTokens,CAST(:siblings AS jsonb))")
        .param("id", UUID.randomUUID()).param("version", versionId).param("operation", proposal.get("operation_type"))
        .param("job", jobId).param("proposal", uuid(proposal.get("id"))).param("action", mode.name())
        .param("provider", proposal.get("provider")).param("model", proposal.get("model"))
        .param("prompt", proposal.get("prompt_version"))
        .param("generated", OffsetDateTime.parse(String.valueOf(proposal.get("generated_at"))))
        .param("targetIds", json(List.of(target.get("id")))).param("targetVersions", json(List.of(target.get("current_version_id"))))
        .param("targetChecksums", json(List.of(proposal.get("original_checksum"))))
        .param("sourceIds", json(sources.stream().map(row -> row.get("id")).toList()))
        .param("sourceChecksums", json(sources.stream().map(row -> row.get("contentChecksum")).toList()))
        .param("originalChecksum", proposal.get("original_checksum")).param("original", proposal.get("original_text"))
        .param("proposed", proposal.get("proposed_text")).param("final", finalText)
        .param("edited", !finalText.equals(proposal.get("proposed_text"))).param("editCount", proposal.get("edit_count"))
        .param("finalChecksum", checksum(finalText)).param("requester", proposal.get("requested_by"))
        .param("actor", actor()).param("now", now).param("evidence", proposalJson(proposal,"evidence_json","source_evidence"))
        .param("warnings", proposalJson(proposal,"warnings_json","warnings")).param("providerRequest", proposal.get("provider_request_id"), Types.VARCHAR)
        .param("inputTokens", proposal.get("input_tokens"), Types.INTEGER).param("outputTokens", proposal.get("output_tokens"), Types.INTEGER)
        .param("siblings", json(siblingIds)).update();
  }

  private Map<String, Object> splitResult(UUID originalId, SplitAcceptanceMode mode, List<UUID> resultingIds) {
    return Map.of("originalFactId", originalId, "originalUnchanged", true, "acceptanceMode", mode.name(),
        "resultingFactIds", resultingIds, "resultingFacts", resultingIds.stream().map(this::fact).toList());
  }

  private Map<String, Object> editableTarget(UUID id) {
    var target = one("SELECT f.id,f.learning_objective_id AS \"learningObjectiveId\",f.current_version_id AS \"currentVersionId\",f.canonical_statement AS \"canonicalStatement\",f.review_status AS \"reviewStatus\",f.status,f.version,v.author_id AS \"authorId\",lo.title AS \"learningObjectiveTitle\" FROM knowledge_fact f JOIN knowledge_fact_version v ON v.id=f.current_version_id JOIN learning_objective lo ON lo.id=f.learning_objective_id WHERE f.id=:id", id);
    if (!"DRAFT".equals(target.get("status")) || !List.of("UNREVIEWED", "REQUIRES_UPDATE").contains(target.get("reviewStatus")))
      throw new DomainException(HttpStatus.CONFLICT, "AI_EDITORIAL_TARGET_INELIGIBLE", "Only an editable draft Knowledge Fact can be revised");
    if (!isAdmin() && !actor().equals(target.get("authorId"))) throw forbidden();
    return target;
  }

  private Map<String, Object> analysisTarget(UUID id) {
    requireAnalysis();
    var target = one("SELECT f.id,f.learning_objective_id AS \"learningObjectiveId\",f.current_version_id AS \"currentVersionId\",f.canonical_statement AS \"canonicalStatement\",f.review_status AS \"reviewStatus\",f.status,f.version,v.author_id AS \"authorId\",lo.title AS \"learningObjectiveTitle\" FROM knowledge_fact f JOIN knowledge_fact_version v ON v.id=f.current_version_id JOIN learning_objective lo ON lo.id=f.learning_objective_id WHERE f.id=:id", id);
    if ("RETIRED".equals(target.get("status")))
      throw new DomainException(HttpStatus.CONFLICT, "AI_EDITORIAL_TARGET_NOT_ELIGIBLE", "Retired Knowledge Facts cannot be analysed");
    boolean reviewerOnly = has("ROLE_CONTENT_REVIEWER") && !has("ROLE_CONTENT_AUTHOR") && !isAdmin();
    if (reviewerOnly && !List.of("UNDER_REVIEW", "APPROVED").contains(target.get("reviewStatus"))) throw forbidden();
    return target;
  }

  private Map<String, Object> lockTarget(UUID id) {
    var rows = jdbc.sql("SELECT f.*,v.author_id FROM knowledge_fact f JOIN knowledge_fact_version v ON v.id=f.current_version_id WHERE f.id=:id FOR UPDATE OF f")
        .param("id", id).query().listOfRows();
    if (rows.isEmpty()) throw DomainException.notFound("Knowledge fact");
    var row = rows.getFirst();
    if (!isAdmin() && !actor().equals(row.get("author_id"))) throw forbidden();
    return row;
  }

  private void validateSnapshot(Map<String, Object> target, Map<String, Object> proposal) {
    boolean versionIdMatches = uuid(target.get("current_version_id")).equals(uuid(proposal.get("target_fact_version_id")));
    boolean aggregateVersionMatches = ((Number) target.get("version")).longValue() == ((Number) proposal.get("target_version")).longValue();
    boolean checksumMatches = checksum((String) target.get("canonical_statement")).equals(String.valueOf(proposal.get("original_checksum")));
    boolean lifecycleEditable = "DRAFT".equals(String.valueOf(target.get("status")));
    boolean reviewEditable = List.of("UNREVIEWED", "REQUIRES_UPDATE").contains(String.valueOf(target.get("review_status")));
    if (!(versionIdMatches && aggregateVersionMatches && checksumMatches && lifecycleEditable && reviewEditable)) {
      LOG.warn("ai_editorial_stale_rejected proposalId={} targetFactId={} versionIdMatches={} aggregateVersionMatches={} checksumMatches={} lifecycleEditable={} reviewEditable={}",
          proposal.get("id"), proposal.get("target_fact_id"), versionIdMatches, aggregateVersionMatches, checksumMatches, lifecycleEditable, reviewEditable);
      throw stale();
    }
    var currentSources = sourceContent(uuid(target.get("current_version_id")), true);
    Map<String, Object> context = readJson(String.valueOf(proposal.get("target_context")));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> snapshotSources = (List<Map<String, Object>>) context.get("sources");
    if (snapshotSources == null || snapshotSources.size() != currentSources.size()) {
      LOG.warn("ai_editorial_source_snapshot_rejected proposalId={} snapshotCount={} currentCount={}", proposal.get("id"), snapshotSources==null?null:snapshotSources.size(), currentSources.size());
      throw staleSource();
    }
    var currentById = new java.util.HashMap<UUID, Object>();
    currentSources.forEach(source -> currentById.put(uuid(source.get("id")), source.get("contentChecksum")));
    for (var source : snapshotSources) {
      UUID sourceId = uuid(source.get("sourceId"));
      if (!java.util.Objects.equals(String.valueOf(currentById.get(sourceId)), String.valueOf(source.get("checksum")))) {
        LOG.warn("ai_editorial_source_snapshot_rejected proposalId={} sourceId={} sourcePresent={} checksumMatches=false", proposal.get("id"), sourceId, currentById.containsKey(sourceId));
        throw staleSource();
      }
    }
  }

  private List<Map<String, Object>> sourceContent(UUID versionId, boolean required) {
    var rows = jdbc.sql("SELECT s.id,s.title,s.content_text AS \"contentText\",s.content_checksum AS \"contentChecksum\",s.status FROM knowledge_fact_source k JOIN source_reference s ON s.id=k.source_reference_id WHERE k.knowledge_fact_version_id=:id ORDER BY s.id")
        .param("id", versionId).query().listOfRows();
    boolean unavailable = rows.isEmpty() || rows.stream().anyMatch(r -> r.get("contentText") == null || r.get("contentChecksum") == null || "RETIRED".equals(r.get("status")));
    if (required && unavailable)
      throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_EDITORIAL_SOURCE_CONTENT_UNAVAILABLE", "All linked Sources must contain stored content before AI editing");
    return unavailable ? List.of() : rows;
  }

  private Map<String, Object> authorizeProposal(UUID id) {
    var proposal = get("/internal/v1/editorial/proposals/" + id);
    authorize(get("/internal/v1/editorial/jobs/" + proposal.get("generation_job_id")));
    return proposal;
  }

  private void authorize(Map<String, Object> job) {
    if (isAdmin() || actor().equals(job.get("requestedBy"))) return;
    if (has("ROLE_CONTENT_REVIEWER") || has("ROLE_CONTENT_PUBLISHER")) return;
    throw forbidden();
  }

  private Map<String, Object> fact(UUID id) {
    return one("SELECT id,learning_objective_id AS \"learningObjectiveId\",current_version_id AS \"currentVersionId\",canonical_statement AS \"canonicalStatement\",review_status AS \"reviewStatus\",status,created_at AS \"createdAt\",updated_at AS \"updatedAt\",version FROM knowledge_fact WHERE id=:id", id);
  }

  private Map<String, Object> factByVersion(UUID version) {
    UUID id = jdbc.sql("SELECT knowledge_fact_id FROM knowledge_fact_version WHERE id=:id")
        .param("id", version).query(UUID.class).single();
    return fact(id);
  }

  private Map<String, Object> one(String sql, UUID id) {
    var rows = jdbc.sql(sql).param("id", id).query().listOfRows();
    if (rows.isEmpty()) throw DomainException.notFound("Knowledge fact");
    return rows.getFirst();
  }

  private void validateText(String text) {
    if (text.isBlank() || text.length() > 500 || text.contains("<"))
      throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, "AI_EDITORIAL_OUTPUT_INVALID", "Proposal text is invalid");
  }

  private void validateFinalGrounding(UUID proposalId, Map<String, Object> proposal, String finalText, UUID factVersionId) {
    var sources = sourceContent(factVersionId, true);
    var byId = new java.util.HashMap<UUID, Map<String, Object>>();
    sources.forEach(source -> byId.put(uuid(source.get("id")), source));
    List<Map<String, Object>> evidence = readJson(proposalJson(proposal,"evidence_json","source_evidence"));
    boolean grounded = false;
    for (var item : evidence) {
      UUID sourceId = uuid(item.get("sourceId"));
      var source = byId.get(sourceId);
      String quote = String.valueOf(item.get("quote"));
      if (source == null) groundingFailure(proposal, proposalId, "AI_EDITORIAL_EVIDENCE_SOURCE_MISMATCH", "Proposal evidence is not linked to the target fact");
      if (!String.valueOf(source.get("contentText")).contains(quote)) groundingFailure(proposal, proposalId, "AI_EDITORIAL_EVIDENCE_NOT_IN_SOURCE", "Proposal evidence no longer exists in the linked Source");
      grounded |= plausiblySupports(finalText, quote);
    }
    if (!grounded) groundingFailure(proposal, proposalId, "AI_EDITORIAL_FINAL_TEXT_NOT_GROUNDED", "The final edited text is not plausibly supported by its evidence");
  }

  private void groundingFailure(Map<String,Object> proposal, UUID proposalId, String code, String message) {
    groundingAudit.rejected(uuid(proposal.get("target_fact_id")), code, proposalId);
    metrics.counter("ai.editorial.grounding.rejected", "reason", code.toLowerCase(Locale.ROOT)).increment();
    throw new DomainException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
  }

  private boolean noMeaningfulChange(String original, String proposed) {
    return normalizeMeaning(original).equals(normalizeMeaning(proposed));
  }

  private boolean plausiblySupports(String claim, String evidence) {
    var claimTerms = terms(claim); var evidenceTerms = terms(evidence);
    if (claimTerms.isEmpty() || evidenceTerms.isEmpty()) return false;
    var overlap = new java.util.HashSet<>(claimTerms); overlap.retainAll(evidenceTerms);
    int required = claimTerms.size() <= 3 ? 1 : Math.max(2, (int)Math.ceil(claimTerms.size() * 0.30));
    return overlap.size() >= required;
  }

  private java.util.Set<String> terms(String value) {
    var stop = java.util.Set.of("och","eller","att","av","i","på","för","med","som","en","ett","den","det","de","är","har","kan","om","till","från","samt");
    var result = new java.util.HashSet<String>();
    for (String token : normalizeMeaning(value).replaceAll("[^\\p{L}\\p{N}]+", " ").split(" "))
      if (token.length() >= 3 && !stop.contains(token)) result.add(token);
    return result;
  }

  private String normalizeMeaning(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{Z}\\s]+", " ").trim().replaceAll("[.!?;,.:]+$", "").trim();
  }

  private String proposalJson(Map<String,Object> proposal,String textKey,String fallbackKey) {
    Object text=proposal.get(textKey);
    if(text instanceof String value)return value;
    Object fallback=proposal.get(fallbackKey);
    if(fallback instanceof String value)return value;
    return json(fallback);
  }

  private String checksum(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception exception) { throw new IllegalStateException(exception); }
  }
  private String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
  private UUID uuid(Object value) { return value instanceof UUID id ? id : UUID.fromString(String.valueOf(value)); }
  private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }
  private <T> T readJson(String value) { try { return mapper.readValue(value, new TypeReference<T>() {}); } catch (Exception e) { throw stale(); } }

  private Map<String, Object> get(String path) { return send("GET", path, null, new TypeReference<>() {}); }
  private List<Map<String, Object>> getList(String path) { return send("GET", path, null, new TypeReference<>() {}); }
  private Map<String, Object> post(String path, Object body) { return send("POST", path, body, new TypeReference<>() {}); }
  private Map<String, Object> patch(String path, Object body) { return send("PATCH", path, body, new TypeReference<>() {}); }
  private void postWithoutResponse(String path, Object body) { sendRaw("POST", path, body); }

  private <T> T send(String method, String path, Object body, TypeReference<T> type) {
    var response = sendRaw(method, path, body);
    try { return mapper.readValue(response.body(), type); }
    catch (Exception e) { throw unavailable("AI_PROVIDER_UNAVAILABLE", "AI Service returned an unreadable response"); }
  }

  private HttpResponse<String> sendRaw(String method, String path, Object body) {
    configured();
    try {
      var builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(java.time.Duration.ofSeconds(10))
          .header("X-Internal-Api-Key", apiKey).header("Content-Type", "application/json");
      String json = body == null ? "" : mapper.writeValueAsString(body);
      builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
      var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 200 && response.statusCode() < 300) return response;
      JsonNode error = mapper.readTree(response.body());
      throw new DomainException(HttpStatus.valueOf(response.statusCode()),
          error.path("code").asText("AI_PROVIDER_UNAVAILABLE"),
          error.path("message").asText("AI Service rejected the request"));
    } catch (DomainException e) { throw e; }
    catch (Exception e) { throw unavailable("AI_PROVIDER_UNAVAILABLE", "AI Service could not be reached"); }
  }

  private void configured() { if (apiKey.isBlank()) throw unavailable("AI_PROVIDER_NOT_CONFIGURED", "AI Service authentication is not configured"); }
  private void requireAuthor() { if (!has("ROLE_CONTENT_AUTHOR") && !has("ROLE_ADMIN")) throw forbidden(); }
  private void requireAnalysis() { if (!has("ROLE_CONTENT_AUTHOR") && !has("ROLE_CONTENT_REVIEWER") && !has("ROLE_ADMIN")) throw forbidden(); }
  private boolean isAdmin() { return has("ROLE_ADMIN"); }
  private boolean has(String role) { return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority())); }
  private String actor() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
  private DomainException forbidden() { return new DomainException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Content author permission is required"); }
  private DomainException stale() { return conflict("AI_EDITORIAL_PROPOSAL_STALE", "The Knowledge Fact or its Sources changed after this proposal was generated"); }
  private DomainException staleSource() { return conflict("AI_EDITORIAL_SOURCE_CHANGED", "A linked Source changed after this output was generated"); }
  private DomainException splitDuplicate() { return conflict("AI_EDITORIAL_SPLIT_DUPLICATE", "A selected split proposal duplicates another current Knowledge Fact"); }
  private DomainException conflict(String code, String message) { return new DomainException(HttpStatus.CONFLICT, code, message); }
  private DomainException unavailable(String code, String message) { return new DomainException(HttpStatus.SERVICE_UNAVAILABLE, code, message); }
}
