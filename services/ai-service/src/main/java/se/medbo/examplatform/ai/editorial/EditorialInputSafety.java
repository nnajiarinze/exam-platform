package se.medbo.examplatform.ai.editorial;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class EditorialInputSafety {
  enum Quality { VALID, SUSPICIOUS, INVALID }
  private static final Pattern INVALID = Pattern.compile("(?i)(<\\s*/?script|<\\s*/?[a-z][^>]*>|ignore (all |the )?(previous|prior) instructions?|reveal (the )?(system )?prompt|lorem ipsum|\\b(todo|fixme|placeholder|test fact|random (text|shit)|asdf|qwerty|hjkl|abc123)\\b)");
  private static final Pattern URL_OR_ID = Pattern.compile("(?i)^(https?://\\S+|[0-9a-f]{8}-[0-9a-f-]{27,}|[A-Za-z_][A-Za-z0-9_.:/-]{5,})$");
  private static final Pattern VERB = Pattern.compile("(?iuU)\\b(är|har|gör|får|ansvarar|beslutar|stiftar|består|innebär|gäller|finns|ska|kan|måste|ger|skyddar|betalar|väljer|utser|granskar|styr|provides?|decides?|is|are|has|have|does?|receives?|protects?|elects?|governs?|includes?|means?)\\b");
  private static final Pattern VAGUE = Pattern.compile("(?i)^(municipalities|kommuner|they|de) (do|gör|are|är|har) (many|många|things|saker|responsible|ansvariga)(?: for services| för tjänster)?[.!]?$\s*");

  static Quality classify(String raw) {
    String text = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
    if (text.isEmpty() || INVALID.matcher(text).find() || URL_OR_ID.matcher(text).matches() || text.endsWith("?")) return Quality.INVALID;
    long letters = text.codePoints().filter(Character::isLetter).count();
    if (letters < 8 || text.matches("(?s).*([A-Za-zÅÄÖåäö])\\1{4,}.*")) return Quality.INVALID;
    String lower = text.toLowerCase(Locale.ROOT);
    long mash = java.util.Arrays.stream(lower.split("\\s+")).filter(EditorialInputSafety::mash).count();
    if (mash >= 2) return Quality.INVALID;
    return VERB.matcher(lower).find() && !VAGUE.matcher(lower).matches() ? Quality.VALID : Quality.SUSPICIOUS;
  }

  private static boolean mash(String raw) {
    String token = raw.replaceAll("(?iu)[^a-zåäö]", "");
    if (token.length() < 8) return false;
    long vowels = token.codePoints().filter(c -> "aeiouyåäö".indexOf(c) >= 0).count();
    return vowels * 1.0 / token.length() < 0.18;
  }
  private EditorialInputSafety() {}
}
