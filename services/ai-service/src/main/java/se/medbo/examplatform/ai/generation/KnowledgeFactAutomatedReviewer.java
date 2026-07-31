package se.medbo.examplatform.ai.generation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class KnowledgeFactAutomatedReviewer {
  private static final Pattern LEADING_PRONOUN = Pattern.compile(
      "^(detta|denna|dessa|han|hon|hen|man)\\b",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern FINITE_VERB = Pattern.compile(
      "\\b(är|har|finns|består|omfattar|använder|producerar|orsakar|leder|bidrar|"
          + "smälter|höjs|blir|bor|täcks|delas|transporterar|innebär|ansvarar|gäller|"
          + "utgör|sker|kan|ska|måste|får)\\b",
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
  private static final Set<String> STOP_WORDS = Set.of(
      "och", "att", "det", "den", "de", "ett", "en", "som", "i", "på", "av", "för",
      "till", "med", "från", "är", "har", "kan", "ska", "samt", "eller", "där");

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  KnowledgeFactAutomatedReviewer(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  void reviewJob(java.util.UUID jobId) {
    var proposals = jdbc.sql("""
        SELECT p.id,p.proposed_text,p.normalized_text,p.source_evidence::text AS evidence,
               p.source_section_id,p.learning_objective_id,j.source_content
        FROM ai_knowledge_fact_proposal p
        JOIN ai_generation_job j ON j.id=p.generation_job_id
        WHERE p.generation_job_id=:job AND p.status<>'ACCEPTED'
        ORDER BY p.created_at,p.id
        """).param("job", jobId).query().listOfRows();
    for (var proposal : proposals) {
      review(proposal);
    }
  }

  private void review(Map<String, Object> proposal) {
    var id = (java.util.UUID) proposal.get("id");
    String fact = String.valueOf(proposal.get("proposed_text"));
    String source = String.valueOf(proposal.get("source_content"));
    List<Map<String, Object>> evidence = readEvidence(String.valueOf(proposal.get("evidence")));

    boolean exactEvidence = !evidence.isEmpty() && evidence.stream().allMatch(item ->
        StructuredOutputValidator.evidenceOccursInSource(source, String.valueOf(item.get("quote"))));
    boolean grounding = exactEvidence && evidence.stream().anyMatch(item ->
        plausiblySupports(fact, String.valueOf(item.get("quote"))));
    boolean atomic = isAtomic(fact);
    boolean unambiguous = !LEADING_PRONOUN.matcher(fact.strip()).find();
    boolean topicMapped = proposal.get("source_section_id") != null;
    boolean objectiveMapped = proposal.get("learning_objective_id") != null;
    boolean duplicateFree = !jdbc.sql("""
        SELECT EXISTS(
          SELECT 1 FROM ai_knowledge_fact_proposal other
          WHERE other.id<>:id AND other.normalized_text=:normalized
            AND other.status<>'REJECTED'
            AND other.created_at<=(SELECT created_at FROM ai_knowledge_fact_proposal WHERE id=:id)
        )
        """).param("id", id).param("normalized", proposal.get("normalized_text"))
        .query(Boolean.class).single();
    boolean schemaValid = !fact.isBlank() && fact.length() <= 500 && evidence.size() == 1;

    String classification;
    if (!exactEvidence || !grounding) classification = "UNSUPPORTED";
    else if (!duplicateFree) classification = "DUPLICATE";
    else if (!schemaValid || !topicMapped || !objectiveMapped) classification = "NEEDS_REWRITE";
    else if (!unambiguous) classification = "AMBIGUOUS";
    else if (!atomic) classification = "NEEDS_SPLIT";
    else if (fact.length() > 220) classification = "TOO_BROAD";
    else classification = "GOOD";

    Map<String, Boolean> gates = Map.of(
        "exactEvidenceValidated", exactEvidence,
        "groundingPassed", grounding,
        "atomic", atomic,
        "duplicateDetectionPassed", duplicateFree,
        "ambiguityChecksPassed", unambiguous,
        "topicMappingPassed", topicMapped,
        "learningObjectiveMappingPassed", objectiveMapped,
        "schemaValidationPassed", schemaValid);
    boolean reject = classification.equals("DUPLICATE") || classification.equals("UNSUPPORTED");
    jdbc.sql("""
        UPDATE ai_knowledge_fact_proposal
        SET automated_classification=:classification,
            validation_gates=CAST(:gates AS jsonb),
            automated_reviewed_at=:now,
            status=CASE WHEN :reject THEN 'REJECTED' ELSE status END,
            rejection_reason=CASE WHEN :reject THEN :reason ELSE rejection_reason END,
            updated_at=:now,
            version=version+CASE WHEN :reject THEN 1 ELSE 0 END
        WHERE id=:id
        """).param("classification", classification)
        .param("gates", json(gates))
        .param("now", OffsetDateTime.now(ZoneOffset.UTC))
        .param("reject", reject)
        .param("reason", reject ? "Automated validation: " + classification : null,
            java.sql.Types.VARCHAR)
        .param("id", id)
        .update();
  }

  static boolean isAtomic(String fact) {
    if (StructuredOutputValidator.isCompound(fact)) return false;
    long finiteVerbs = FINITE_VERB.matcher(fact).results().count();
    if (finiteVerbs > 1) return false;
    String lower = fact.toLowerCase(Locale.ROOT);
    return !lower.contains(" samt ") && !lower.contains(" dessutom ")
        && !lower.matches(".*\\b(både|dels)\\b.*");
  }

  static boolean plausiblySupports(String fact, String evidence) {
    Set<String> factTerms = terms(fact);
    Set<String> evidenceTerms = terms(evidence);
    if (factTerms.isEmpty() || evidenceTerms.isEmpty()) return false;
    var overlap = new HashSet<>(factTerms);
    overlap.retainAll(evidenceTerms);
    int required = Math.min(3, Math.max(1, (int) Math.ceil(factTerms.size() * 0.3)));
    return overlap.size() >= required;
  }

  private static Set<String> terms(String text) {
    String normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ");
    var terms = new HashSet<>(Arrays.asList(normalized.strip().split("\\s+")));
    terms.removeAll(STOP_WORDS);
    terms.removeIf(term -> term.length() < 3);
    return terms;
  }

  private List<Map<String, Object>> readEvidence(String value) {
    try {
      return mapper.readValue(value, new TypeReference<>() {});
    } catch (Exception error) {
      return List.of();
    }
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }
}
