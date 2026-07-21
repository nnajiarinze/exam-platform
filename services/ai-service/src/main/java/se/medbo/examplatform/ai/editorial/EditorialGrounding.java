package se.medbo.examplatform.ai.editorial;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class EditorialGrounding {
  private static final Set<String> STOP = Set.of(
      "och","eller","att","av","i","på","för","med","som","en","ett","den","det","de","är","har","kan",
      "om","till","från","samt","the","and","or","of","in","to","for","a","an","is","are","can");

  private EditorialGrounding() {}

  static String normalizeMeaning(String value) {
    String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC)
        .toLowerCase(Locale.ROOT).replaceAll("[\\p{Z}\\s]+", " ").trim();
    return normalized.replaceAll("[.!?;,.:]+$", "").trim();
  }

  static boolean noMeaningfulChange(String original, String proposed) {
    return normalizeMeaning(original).equals(normalizeMeaning(proposed));
  }

  static boolean plausiblySupports(String claim, String evidence) {
    Set<String> claimTerms = terms(claim), evidenceTerms = terms(evidence);
    if (claimTerms.isEmpty() || evidenceTerms.isEmpty()) return false;
    var overlap = new HashSet<>(claimTerms);
    overlap.retainAll(evidenceTerms);
    int required = claimTerms.size() <= 3 ? 1 : Math.max(2, (int)Math.ceil(claimTerms.size() * 0.30));
    return overlap.size() >= required;
  }

  static int overlapCount(String claim, String evidence) {
    var overlap = new HashSet<>(terms(claim));
    overlap.retainAll(terms(evidence));
    return overlap.size();
  }

  private static Set<String> terms(String value) {
    var result = new HashSet<String>();
    Arrays.stream(normalizeMeaning(value).replaceAll("[^\\p{L}\\p{N}]+", " ").split(" "))
        .map(String::trim).filter(token -> token.length() >= 3 && !STOP.contains(token)).forEach(result::add);
    return result;
  }
}
