package se.medbo.examplatform.ai.editorial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.ai.provider.AiProviderException;

@Component
@ConditionalOnProperty(name = "ai.editorial.provider", havingValue = "FAKE", matchIfMissing = true)
final class FakeAiEditorialProviderClient implements AiEditorialProviderClient {
  @Override
  public Result execute(Request request) {
    String sourceText = request.sources().stream().map(Source::text).reduce("", (a, b) -> a + " " + b);
    simulateFailures(sourceText);
    if (request.targets() == null || request.targets().isEmpty()
        || EditorialInputSafety.classify(request.targets().getFirst().text()) == EditorialInputSafety.Quality.INVALID)
      throw new AiProviderException("AI_EDITORIAL_INPUT_INVALID", false,
          "The target does not contain meaningful factual content");
    return switch (request.operation()) {
      case REWRITE_FOR_CLARITY -> rewrite(request);
      case SIMPLIFY_LANGUAGE -> simplifyResult(request);
      case MAKE_ATOMIC -> atomicity(request, sourceText);
      case SPLIT_FACT -> split(request, sourceText);
      case CHECK_SOURCE_SUPPORT -> sourceSupport(request, sourceText);
      case DETECT_AMBIGUITY -> ambiguity(request, sourceText);
      case EDITORIAL_REVIEW_NOTES -> editorialNotes(request);
    };
  }

  private Result rewrite(Request request) {
    String original = clean(target(request));
    String rewritten = original
        .replace("är ansvariga för", "ansvarar för")
        .replace("har ansvaret för", "ansvarar för")
        .replace("erforderliga", "nödvändiga")
        .replace("erforderlig", "nödvändig")
        .replace("föreligger", "finns")
        .replace("Ledamöterna beslutar", "Riksdagens ledamöter beslutar");
    return EditorialGrounding.noMeaningfulChange(original, rewritten)
        ? alreadyClear(request, "The current wording is already direct and no grounded improvement was found.")
        : revisions(request, List.of(rewritten), "Clarified the responsible actor and made the wording more direct.");
  }

  private Result simplifyResult(Request request) {
    String original = clean(target(request));
    String simplified = simplify(original);
    return EditorialGrounding.noMeaningfulChange(original, simplified)
        ? alreadyClear(request, "The current wording is already concise and no meaningful simplification was found.")
        : revisions(request, List.of(simplified), "Replaced unnecessarily complex wording while preserving the civic meaning.");
  }

  private Result alreadyClear(Request request, String rationale) {
    return findings(request, List.of(new Finding("ALREADY_CLEAR", "INFO", request.targets().getFirst().factId(),
        "Already clear", rationale, null, evidence(request, target(request)), "HIGH", "KEEP_AS_IS",
        Map.of("resultType", "ALREADY_CLEAR"))));
  }

  private Result atomicity(Request request, String sources) {
    if (sources.contains("[[ALREADY_ATOMIC]]") || !compound(target(request))) {
      return findings(request, List.of(finding(request, "ALREADY_ATOMIC", "INFO",
          "Fact is already atomic", "The fact contains one independently testable claim.", null,
          "KEEP_AS_IS", Map.of("alreadyAtomic", true))));
    }
    return revisions(request, splitText(target(request), sources), "Atomicity assessment");
  }

  private Result split(Request request, String sources) {
    return revisions(request, splitText(target(request), sources), "Split into atomic facts");
  }

  private Result sourceSupport(Request request, String sources) {
    String type;
    String severity;
    String title;
    Map<String, Object> details = new LinkedHashMap<>();
    if (sources.contains("[[UNSUPPORTED]]") || tokenOverlap(target(request), sources) == 0) {
      type = "CLAIM_NOT_FOUND_IN_SOURCE"; severity = "HIGH"; title = "Claim not found in linked Sources";
      details.put("supportStatus", "NOT_SUPPORTED"); details.put("unsupportedFragments", List.of(target(request)));
    } else if (sources.contains("[[PARTIALLY_SUPPORTED]]") || tokenOverlap(target(request), sources) < 0.7) {
      type = "CLAIM_PARTIALLY_SUPPORTED"; severity = "WARNING"; title = "Claim is only partially supported";
      details.put("supportStatus", "PARTIALLY_SUPPORTED"); details.put("unsupportedFragments", List.of("Some wording is not directly evidenced"));
    } else {
      type = "FULL_SUPPORT_CONFIRMED"; severity = "INFO"; title = "Linked Sources support this fact";
      details.put("supportStatus", "FULLY_SUPPORTED"); details.put("unsupportedFragments", List.of());
    }
    details.put("supportedFragments", List.of(target(request)));
    details.put("missingQualifiers", List.of());
    return findings(request, List.of(finding(request, type, severity, title,
        "This is an advisory comparison with the stored Source text, not a reviewer decision.", null,
        "HUMAN_REVIEW", details)));
  }

  private Result ambiguity(Request request, String sources) {
    String text = target(request).toLowerCase(Locale.ROOT);
    if (EditorialInputSafety.classify(target(request)) == EditorialInputSafety.Quality.SUSPICIOUS)
      return findings(request, List.of(finding(request, "BROAD_GENERALISATION", "WARNING",
          "The factual claim is too broad", "The statement is meaningful but does not identify a sufficiently specific responsibility or scope.",
          target(request), "REWRITE", Map.of("ambiguityFound", true, "inputQuality", "SUSPICIOUS"))));
    if (sources.contains("[[NO_AMBIGUITY]]") || (!text.matches(".*\\b(den|det|de|alla|alltid|aldrig)\\b.*") && !compound(text))) {
      return findings(request, List.of(finding(request, "NO_AMBIGUITY_FOUND", "INFO",
          "No material ambiguity found", "No wording with multiple reasonable interpretations was detected.",
          null, "KEEP_AS_IS", Map.of("ambiguityFound", false))));
    }
    String type = text.matches(".*\\b(den|det|de)\\b.*") ? "VAGUE_PRONOUN"
        : text.matches(".*\\b(alla|alltid|aldrig)\\b.*") ? "OVERLY_ABSOLUTE_WORDING" : "COMPOUND_CLAIM";
    return findings(request, List.of(finding(request, type, "WARNING", "Potentially ambiguous wording",
        "The highlighted wording may allow more than one interpretation.", affected(text, type),
        "REWRITE", Map.of("ambiguityFound", true, "suggestedWording", clean(target(request))))));
  }

  private Result editorialNotes(Request request) {
    if (EditorialInputSafety.classify(target(request)) == EditorialInputSafety.Quality.SUSPICIOUS)
      return findings(request, List.of(finding(request, "EDITORIAL_ASSESSMENT", "WARNING",
          "The draft needs a more specific factual claim",
          "The statement is meaningful but too broad for a clean editorial assessment.", target(request),
          "REWRITE", Map.of("summary", "Input quality needs attention", "strengths", List.of("A subject is identifiable"),
              "concerns", List.of("The predicate or scope is too vague"), "recommendedAction", "REWRITE", "inputQuality", "SUSPICIOUS"))));
    return findings(request, List.of(finding(request, "EDITORIAL_ASSESSMENT", "INFO",
        "AI-assisted editorial review notes",
        "The fact is concise and should still receive normal independent human review.", null,
        compound(target(request)) ? "SPLIT" : "HUMAN_REVIEW",
        Map.of("summary", "Advisory editorial assessment",
            "strengths", List.of("Concise wording", "Linked Source context supplied"),
            "concerns", compound(target(request)) ? List.of("May contain multiple claims") : List.of(),
            "recommendedAction", compound(target(request)) ? "SPLIT" : "HUMAN_REVIEW"))));
  }

  private Result revisions(Request request, List<String> texts, String rationale) {
    var revisions = new ArrayList<Revision>();
    int order = 0;
    for (String text : texts) {
      if (text.isBlank()) continue;
      revisions.add(new Revision(request.targets().getFirst().factId(), clean(text), rationale,
          evidence(request, text), List.of(), Map.of("summary", "Evidence mapped to this proposal", "order", order++), "MEDIUM"));
    }
    return new Result(revisions, List.of(), List.of(), usage(request));
  }

  private Result findings(Request request, List<Finding> findings) {
    return new Result(List.of(), findings, List.of(), usage(request));
  }

  private Finding finding(Request request, String type, String severity, String title, String message,
                          String affected, String action, Map<String, Object> details) {
    var mappedEvidence = evidence(request, target(request));
    if (mappedEvidence.isEmpty() && request.operation() == EditorialOperationType.CHECK_SOURCE_SUPPORT && !request.sources().isEmpty()) {
      var source = request.sources().getFirst();
      String quote = source.text().replaceAll("\\[\\[[A-Z_]+]]", "").trim().split("(?<=[.!?])\\s+")[0].trim();
      if (!quote.isBlank()) mappedEvidence = List.of(new Evidence(source.sourceId(), source.title(), quote, "Stored Source text"));
    }
    return new Finding(type, severity, request.targets().getFirst().factId(), title, message, affected,
        mappedEvidence, "MEDIUM", action, details);
  }

  private List<String> splitText(String original, String sources) {
    String markerFree = clean(original.replaceAll("\\[\\[[A-Z_]+]]", ""));
    String[] pieces = markerFree.split("(?i)\\s+(?:och|samt)\\s+|;\\s*|(?<=[.!?])\\s+");
    var result = new ArrayList<String>();
    for (String piece : pieces) if (!piece.isBlank()) result.add(sentence(piece));
    if (sources.contains("[[THREE_WAY_SPLIT]]") && result.size() < 3) {
      for (String sentence : sources.replace("[[THREE_WAY_SPLIT]]", "").split("(?<=[.!?])\\s+"))
        if (!sentence.isBlank() && result.stream().noneMatch(value -> normalize(value).equals(normalize(sentence)))) result.add(sentence(sentence));
    }
    return result.stream().distinct().limit(5).toList();
  }

  private List<Evidence> evidence(Request request, String claim) {
    if (request.sources().isEmpty()) return List.of();
    if (request.sources().stream().anyMatch(source -> source.text().contains("[[SIMULATE_OTHER_JOB_EVIDENCE]]"))) {
      var source=request.sources().getFirst();
      return List.of(new Evidence(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), "Another job Source", firstSentence(source.text()), "Invalid test scenario"));
    }
    if (request.sources().size()>1 && request.sources().stream().anyMatch(source -> source.text().contains("[[SIMULATE_WRONG_SOURCE]]"))) {
      var declared=request.sources().getFirst(); var quoted=request.sources().get(1);
      return List.of(new Evidence(declared.sourceId(), declared.title(), firstSentence(quoted.text()), "Invalid test scenario"));
    }
    if (request.sources().stream().anyMatch(source -> source.text().contains("[[SIMULATE_UNRELATED_EVIDENCE]]"))) {
      var source=request.sources().getFirst();
      return List.of(new Evidence(source.sourceId(), source.title(), firstSentence(source.text()), "Invalid test scenario"));
    }
    Source bestSource = null; String bestQuote = null; int best = 0;
    for (Source source : request.sources()) {
      for (String sentence : source.text().replaceAll("\\[\\[[A-Z_]+]]", "").split("(?<=[.!?])\\s+")) {
        int score = EditorialGrounding.overlapCount(claim, sentence);
        if (score > best) { best = score; bestSource = source; bestQuote = sentence.trim(); }
      }
    }
    if (bestSource == null || bestQuote == null || !EditorialGrounding.plausiblySupports(claim, bestQuote)) return List.of();
    return List.of(new Evidence(bestSource.sourceId(), bestSource.title(), bestQuote, "Stored Source text"));
  }

  private String firstSentence(String value) {
    return value.replaceAll("\\[\\[[A-Z_]+]]", "").trim().split("(?<=[.!?])\\s+")[0].trim();
  }

  private void simulateFailures(String text) {
    if (text.contains("[[SIMULATE_TIMEOUT]]")) throw new AiProviderException("AI_REQUEST_TIMEOUT", true, "The AI provider timed out");
    if (text.contains("[[SIMULATE_UNAVAILABLE]]")) throw new AiProviderException("AI_PROVIDER_UNAVAILABLE", true, "The AI provider is temporarily unavailable");
    if (text.contains("[[SIMULATE_MALFORMED]]")) throw new AiProviderException("AI_EDITORIAL_OUTPUT_INVALID", false, "The provider returned invalid structured output");
    if (text.contains("[[SIMULATE_EMPTY]]")) throw new AiProviderException("AI_EDITORIAL_OUTPUT_INVALID", false, "The provider returned empty output");
    if (text.contains("[[SIMULATE_INSUFFICIENT_EVIDENCE]]") || text.contains("[[SIMULATE_INVENTED_EVIDENCE]]"))
      throw new AiProviderException("AI_EDITORIAL_INSUFFICIENT_EVIDENCE", false, "The supplied Sources do not support the output");
  }

  private String target(Request request) { return request.targets().getFirst().text(); }
  private boolean compound(String text) { return text.matches("(?is).*(\\s+och\\s+|\\s+samt\\s+|;).*?"); }
  private String clean(String text) { return text.trim().replaceAll("\\s+", " "); }
  private String simplify(String text) { return clean(text).replace("erforderlig", "nödvändig").replace("föreligger", "finns"); }
  private String sentence(String value) { String clean = clean(value); return clean.matches(".*[.!?]$") ? clean : clean + "."; }
  private String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim(); }
  private double tokenOverlap(String fact, String sources) {
    var factTokens = new java.util.HashSet<>(List.of(normalize(fact).split(" ")));
    var sourceTokens = new java.util.HashSet<>(List.of(normalize(sources).split(" ")));
    if (factTokens.isEmpty()) return 0;
    factTokens.retainAll(sourceTokens);
    return (double) factTokens.size() / Math.max(1, normalize(fact).split(" ").length);
  }
  private String affected(String text, String type) {
    if ("VAGUE_PRONOUN".equals(type)) return text.matches(".*\\bden\\b.*") ? "den" : text.matches(".*\\bdet\\b.*") ? "det" : "de";
    if ("OVERLY_ABSOLUTE_WORDING".equals(type)) return text.matches(".*\\balltid\\b.*") ? "alltid" : text.matches(".*\\baldrig\\b.*") ? "aldrig" : "alla";
    return targetPhrase(text);
  }
  private String targetPhrase(String text) { return text.length() <= 80 ? text : text.substring(0, 80); }
  private Usage usage(Request request) {
    int input = request.targets().stream().mapToInt(target -> target.text().length()).sum()
        + request.sources().stream().mapToInt(source -> source.text().length()).sum();
    return new Usage(Math.max(1, input / 4), Math.max(1, request.count() * 20), "fake-editorial-" + UUID.randomUUID());
  }
}
