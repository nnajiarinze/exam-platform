package se.medbo.examplatform.ai.generation;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import se.medbo.examplatform.ai.provider.AiProviderClient.GenerationResult;
import se.medbo.examplatform.ai.provider.AiProviderException;

@Component
public final class StructuredOutputValidator {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern PDF_LINE_HYPHEN = Pattern.compile("-\\s+");
  private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.!?]\\s+[\\p{Lu}\\d]");

  private final int maxLength;
  public record Rejection(int proposalIndex, String code, String message) {}
  public record ValidationOutcome(GenerationResult result, List<Rejection> rejections) {}

  public StructuredOutputValidator(
      @Value("${ai.editorial.max-fact-characters:500}") int maxLength) {
    this.maxLength = maxLength;
  }

  public void validate(GenerationResult result, String source, int requested) {
    if (result == null || result.proposals() == null || result.proposals().isEmpty()) {
      fail("AI_NO_SUPPORTED_PROPOSALS", "No supported proposals were generated");
    }
    if (result.proposals().size() > requested) {
      fail("AI_TOO_MANY_PROPOSALS", "The provider returned too many proposals");
    }

    var normalizedFacts = new HashSet<String>();
    for (int proposalIndex = 0; proposalIndex < result.proposals().size(); proposalIndex++) {
      var proposal = result.proposals().get(proposalIndex);
      int displayIndex = proposalIndex + 1;
      if (proposal.text() == null || proposal.text().isBlank()) {
        fail("AI_EMPTY_PROPOSAL", "Proposal " + displayIndex + " was empty");
      }
      if (proposal.text().length() > maxLength) {
        fail("AI_PROPOSAL_TOO_LONG", "Proposal " + displayIndex + " exceeded the maximum fact length");
      }
      if (proposal.text().contains("<") || proposal.text().contains(">")) {
        fail("AI_PROPOSAL_HTML", "HTML is not allowed in proposal " + displayIndex);
      }
      if (isCompound(proposal.text())) {
        fail("AI_COMPOUND_PROPOSAL", "Proposal " + displayIndex + " contains more than one sentence or a semicolon");
      }
      if (!normalizedFacts.add(normalize(proposal.text()))) {
        fail("AI_DUPLICATE_PROPOSAL", "Proposal " + displayIndex + " duplicates another candidate in the response");
      }
      if (proposal.sourceEvidence() == null || proposal.sourceEvidence().isEmpty()) {
        fail("AI_EVIDENCE_REQUIRED", "Proposal " + displayIndex + " requires source evidence");
      }
      for (int evidenceIndex = 0; evidenceIndex < proposal.sourceEvidence().size(); evidenceIndex++) {
        var evidence = proposal.sourceEvidence().get(evidenceIndex);
        if (evidence.quote() == null || evidence.quote().isBlank()) {
          fail("AI_EVIDENCE_REQUIRED", "Proposal " + displayIndex + " contains empty evidence");
        }
        if (!evidenceOccursInSource(source, evidence.quote())) {
          fail(
              "AI_EVIDENCE_NOT_IN_SOURCE",
              "Proposal " + displayIndex + " evidence " + (evidenceIndex + 1)
                  + " was not found in the supplied Source Section");
        }
      }
    }
  }

  /**
   * Keeps independently valid candidates when another candidate in the same provider response is
   * invalid. Rejected candidates are never persisted as proposals and remain visible as job
   * diagnostics.
   */
  public ValidationOutcome filterSupported(GenerationResult result, String source, int requested) {
    if (result == null || result.proposals() == null || result.proposals().isEmpty()) {
      fail("AI_NO_SUPPORTED_PROPOSALS", "No supported proposals were generated");
    }
    if (result.proposals().size() > requested) {
      fail("AI_TOO_MANY_PROPOSALS", "The provider returned too many proposals");
    }
    var valid = new ArrayList<se.medbo.examplatform.ai.provider.AiProviderClient.Proposal>();
    var rejections = new ArrayList<Rejection>();
    var seen = new HashSet<String>();
    for (int index = 0; index < result.proposals().size(); index++) {
      var proposal = result.proposals().get(index);
      String key = proposal.text() == null ? "" : normalize(proposal.text());
      if (!key.isEmpty() && !seen.add(key)) {
        rejections.add(new Rejection(index + 1, "AI_DUPLICATE_PROPOSAL",
            "Candidate " + (index + 1) + " duplicated another candidate"));
        continue;
      }
      try {
        validate(new GenerationResult(List.of(proposal), List.of(), result.usage()), source, 1);
        valid.add(proposal);
      } catch (AiProviderException failure) {
        rejections.add(new Rejection(index + 1, failure.code(), failure.getMessage()));
      }
    }
    if (valid.isEmpty()) {
      var first = rejections.getFirst();
      fail(first.code(), first.message());
    }
    return new ValidationOutcome(
        new GenerationResult(List.copyOf(valid), result.warnings(), result.usage()),
        List.copyOf(rejections));
  }

  /**
   * Accepts only the same Unicode and punctuation sequence found in the source. The layout form
   * tolerates PDF-only whitespace and line-break hyphenation, but never substitutes, removes, or
   * reorders lexical characters.
   */
  static boolean evidenceOccursInSource(String source, String evidence) {
    if (source == null || evidence == null) {
      return false;
    }
    String normalizedSource = normalizeEvidence(source);
    String normalizedEvidence = normalizeEvidence(evidence);
    if (normalizedSource.contains(normalizedEvidence)) {
      return true;
    }
    return layoutNormalize(normalizedSource).contains(layoutNormalize(normalizedEvidence));
  }

  static boolean isCompound(String fact) {
    return fact != null && (fact.indexOf(';') >= 0 || SENTENCE_BOUNDARY.matcher(fact.trim()).find());
  }

  public static String normalize(String value) {
    return normalizeEvidence(value).toLowerCase(Locale.ROOT);
  }

  private static String normalizeEvidence(String value) {
    return WHITESPACE.matcher(
        Normalizer.normalize(value, Normalizer.Form.NFC)
            .replace('\u00a0', ' ')
            .replace("\u00ad", ""))
        .replaceAll(" ")
        .trim();
  }

  private static String layoutNormalize(String value) {
    return WHITESPACE.matcher(PDF_LINE_HYPHEN.matcher(value).replaceAll("")).replaceAll("");
  }

  private void fail(String code, String message) {
    throw new AiProviderException(code, false, message);
  }
}
