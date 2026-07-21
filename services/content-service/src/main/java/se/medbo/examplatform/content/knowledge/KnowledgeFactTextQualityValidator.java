package se.medbo.examplatform.content.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conservative, deterministic validation for author-entered Knowledge Fact text. */
@Component
public final class KnowledgeFactTextQualityValidator {
  public enum Quality { VALID, SUSPICIOUS, INVALID }
  public record Issue(String code, String message) {}
  public record Result(Quality quality, List<Issue> issues) {
    public boolean eligible() { return quality != Quality.INVALID; }
    public boolean blocksDraftSave() {
      return issues.stream().anyMatch(issue -> DRAFT_BLOCKING.contains(issue.code()));
    }
    private static final Set<String> DRAFT_BLOCKING = Set.of(
        "EMPTY_TEXT", "GIBBERISH_DETECTED", "PLACEHOLDER_TEXT_DETECTED", "KEYBOARD_MASH_DETECTED",
        "HTML_NOT_ALLOWED", "SCRIPT_CONTENT_NOT_ALLOWED", "PROMPT_INJECTION_PATTERN", "URL_ONLY",
        "IDENTIFIER_ONLY", "EXCESSIVE_NON_WORD_TOKENS");
  }

  private static final Pattern HTML = Pattern.compile("(?is)<\\s*/?\\s*[a-z][^>]*>");
  private static final Pattern SCRIPT = Pattern.compile("(?is)<\\s*script\\b|javascript\\s*:|on(?:error|load|click)\\s*=");
  private static final Pattern URL_ONLY = Pattern.compile("(?i)^https?://\\S+$");
  private static final Pattern UUID_ONLY = Pattern.compile("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
  private static final Pattern IDENTIFIER_ONLY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.:/-]{5,}$");
  private static final Pattern PROMPT_INJECTION = Pattern.compile("(?i)\\b(ignore (all |the )?(previous|prior) instructions?|reveal (the )?(system )?prompt|return json|approve this|publish this)\\b");
  private static final Pattern INSTRUCTION = Pattern.compile("(?i)^(rewrite|simplify|summarize|translate|explain|generate|approve|publish|return|tell (me|us))\\b");
  private static final Pattern PLACEHOLDER = Pattern.compile("(?i)\\b(lorem ipsum|todo|fixme|placeholder|sample text|dummy|temp(?:orary)?|test fact|random (text|shit)|abc123|xxx)\\b");
  private static final Pattern KEYBOARD = Pattern.compile("(?i)(asdf|qwerty|hjkl|zxcv|sdfg|dfgh|fghj|jkl;)");
  private static final Pattern REPEATED_CHARS = Pattern.compile("(?i)([a-zåäö])\\1{4,}");
  private static final Pattern DECLARATIVE_VERB = Pattern.compile("(?iuU)\\b(är|har|gör|får|ansvarar|beslutar|stiftar|består|innebär|gäller|finns|ska|kan|måste|ger|skyddar|betalar|väljer|utser|granskar|styr|provides?|decides?|is|are|has|have|does?|receives?|protects?|elects?|governs?|includes?|means?)\\b");
  private static final Pattern VAGUE = Pattern.compile("(?i)^(municipalities|kommuner|they|de) (do|gör|are|är|har) (many|många|things|saker|responsible|ansvariga)(?: for services| för tjänster)?[.!]?$\s*");

  public Result validate(String raw) {
    String text = normalize(raw);
    var issues = new ArrayList<Issue>();
    if (text.isEmpty()) return invalid("EMPTY_TEXT", "Enter a meaningful factual statement.");
    if (SCRIPT.matcher(text).find()) issues.add(issue("SCRIPT_CONTENT_NOT_ALLOWED", "Script content is not allowed."));
    else if (HTML.matcher(text).find()) issues.add(issue("HTML_NOT_ALLOWED", "HTML is not allowed in a Knowledge Fact."));
    if (PROMPT_INJECTION.matcher(text).find()) issues.add(issue("PROMPT_INJECTION_PATTERN", "Instructions directed at the AI are not Knowledge Fact content."));
    if (URL_ONLY.matcher(text).matches()) issues.add(issue("URL_ONLY", "A URL by itself is not a Knowledge Fact."));
    if (UUID_ONLY.matcher(text).matches() || IDENTIFIER_ONLY.matcher(text).matches()) issues.add(issue("IDENTIFIER_ONLY", "An identifier by itself is not a Knowledge Fact."));

    String lower = text.toLowerCase(Locale.ROOT);
    String[] tokens = lower.split("\\s+");
    long alphabetic = text.codePoints().filter(Character::isLetter).count();
    long wordTokens = java.util.Arrays.stream(tokens).filter(t -> t.matches("(?iu).*[a-zåäö].*")).count();
    long mashTokens = java.util.Arrays.stream(tokens).filter(this::looksLikeMash).count();
    if (PLACEHOLDER.matcher(lower).find() && (tokens.length <= 8 || mashTokens > 0))
      issues.add(issue("PLACEHOLDER_TEXT_DETECTED", "Placeholder or test text is not valid factual content."));
    if (KEYBOARD.matcher(lower).find() || REPEATED_CHARS.matcher(lower).find() || mashTokens >= 2)
      issues.add(issue("KEYBOARD_MASH_DETECTED", "The draft contains nonsensical keyboard input."));
    if (alphabetic < 8 || wordTokens < Math.min(3, tokens.length))
      issues.add(issue("EXCESSIVE_NON_WORD_TOKENS", "The draft does not contain enough meaningful words."));
    if (lower.matches("(?s).*\\b(lorem|ipsum|dolor|sit amet)\\b.*"))
      issues.add(issue("PLACEHOLDER_TEXT_DETECTED", "Placeholder text is not valid factual content."));
    if (text.endsWith("?") || lower.matches("^(what|why|when|where|who|how|vad|varför|när|var|vem|hur)\\b.*"))
      issues.add(issue("QUESTION_INSTEAD_OF_FACT", "Write the Knowledge Fact as a declarative statement, not a question."));
    if (INSTRUCTION.matcher(lower).find())
      issues.add(issue("INSTRUCTION_INSTEAD_OF_FACT", "Write factual content rather than an editorial instruction."));
    if (!issues.isEmpty()) return new Result(Quality.INVALID, List.copyOf(issues.stream().distinct().toList()));
    if (!DECLARATIVE_VERB.matcher(lower).find())
      return suspicious("NO_PLAUSIBLE_FACTUAL_CLAIM", "The draft may not contain a clear subject and factual predicate.");
    if (VAGUE.matcher(lower).matches())
      return suspicious("BROAD_OR_VAGUE_CLAIM", "The statement is meaningful but too broad or vague.");
    return new Result(Quality.VALID, List.of());
  }

  private boolean looksLikeMash(String raw) {
    String token = raw.replaceAll("(?iu)[^a-zåäö]", "");
    if (token.length() < 6) return false;
    long vowels = token.codePoints().filter(c -> "aeiouyåäö".indexOf(c) >= 0).count();
    return vowels == 0 || (vowels * 1.0 / token.length() < 0.18 && token.length() >= 8);
  }
  private Result invalid(String code, String message) { return new Result(Quality.INVALID, List.of(issue(code, message))); }
  private Result suspicious(String code, String message) { return new Result(Quality.SUSPICIOUS, List.of(issue(code, message))); }
  private Issue issue(String code, String message) { return new Issue(code, message); }
  private String normalize(String value) { return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC).trim().replaceAll("\\s+", " "); }
}
