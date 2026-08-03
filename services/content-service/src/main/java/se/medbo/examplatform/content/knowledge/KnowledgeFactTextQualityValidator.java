package se.medbo.examplatform.content.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Conservative, deterministic validation for author-entered Knowledge Fact text. */
@Component
public final class KnowledgeFactTextQualityValidator {
  public static final String VALIDATOR_VERSION = "swedish-declarative-claim-v2";

  public enum Quality { VALID, SUSPICIOUS, INVALID }
  public enum ConstructionType {
    SIMPLE_FINITE, COPULAR, EXISTENTIAL, PASSIVE_S, AUXILIARY_MODAL, PERFECT,
    NEGATED_DECLARATIVE, UNKNOWN
  }
  public record Issue(String code, String message) {}
  public record Diagnostics(
      String validatorVersion,
      boolean subjectDetected,
      String subjectSpan,
      boolean finitePredicateDetected,
      String predicateSpan,
      String predicateForm,
      ConstructionType constructionType,
      boolean fragmentDetected,
      boolean questionDetected,
      boolean imperativeDetected,
      boolean incompleteClauseDetected,
      int lexicalContentScore) {}
  public record Result(Quality quality, List<Issue> issues, Diagnostics diagnostics) {
    public boolean eligible() { return quality != Quality.INVALID; }
    public boolean blocksDraftSave() {
      return issues.stream().anyMatch(issue -> DRAFT_BLOCKING.contains(issue.code()));
    }
    private static final Set<String> DRAFT_BLOCKING = Set.of(
        "EMPTY_TEXT", "GIBBERISH_DETECTED", "PLACEHOLDER_TEXT_DETECTED", "KEYBOARD_MASH_DETECTED",
        "HTML_NOT_ALLOWED", "SCRIPT_CONTENT_NOT_ALLOWED", "PROMPT_INJECTION_PATTERN", "URL_ONLY",
        "IDENTIFIER_ONLY", "EXCESSIVE_NON_WORD_TOKENS");
  }

  private record Token(String text, int start, int end) {}
  private record Predicate(int index, ConstructionType type, String form) {}

  private static final Pattern WORD = Pattern.compile("(?iuU)[a-zåäö]+(?:-[a-zåäö]+)*");
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
  private static final Pattern VAGUE = Pattern.compile("(?iu)^(municipalities|kommuner|they|de) (do|gör|are|är|har) (many|många|things|saker|responsible|ansvariga)(?: for services| för tjänster)?[.!]?\\s*$");
  private static final Pattern VAGUE_SWEDISH = Pattern.compile("(?iu)^(detta är viktigt|det fungerar på olika sätt|sverige är bra)[.!]?$");

  private static final Set<String> COPULAS = Set.of("är", "var", "blir", "blev", "is", "are", "was", "were");
  private static final Set<String> EXISTENTIALS = Set.of("finns", "fanns", "exists", "exist");
  private static final Set<String> MODALS = Set.of("kan", "får", "ska", "skall", "måste", "bör", "kunde", "fick", "can", "may", "must", "should");
  private static final Set<String> PERFECT_AUXILIARIES = Set.of("har", "hade", "has", "have", "had");
  private static final Set<String> OTHER_FINITE = Set.of(
      "gör", "ger", "går", "står", "ser", "säger", "vet", "vill", "bor", "sker", "utgör",
      "does", "means", "decides", "provides", "receives", "protects", "elects", "governs", "includes");
  private static final Set<String> PASSIVE_EXCEPTIONS = Set.of(
      "sprids", "hålls", "styrs", "används", "ges", "görs", "ses", "fås", "tas", "nås", "slås", "slöts");
  private static final Set<String> IMPERATIVES = Set.of(
      "läs", "välj", "skriv", "ange", "förklara", "beskriv", "jämför", "markera", "klicka",
      "öppna", "stäng", "svara", "studera", "diskutera", "kontrollera", "rewrite", "choose", "read");
  private static final Set<String> LEADING_FRAGMENT_WORDS = Set.of(
      "att", "genom", "för", "från", "till", "med", "utan", "under", "över", "av", "om");
  private static final Set<String> SUBORDINATORS = Set.of(
      "eftersom", "när", "medan", "fastän", "trots", "innan", "såvida");
  private static final Set<String> NON_SUBJECT_WORDS = Set.of(
      "en", "ett", "den", "denna", "dessa", "varje", "sveriges");

  public Result validate(String raw) {
    String text = normalize(raw);
    var issues = new ArrayList<Issue>();
    if (text.isEmpty()) return result(Quality.INVALID, List.of(issue("EMPTY_TEXT", "Enter a meaningful factual statement.")), text, null, false, false, false, false);
    if (SCRIPT.matcher(text).find()) issues.add(issue("SCRIPT_CONTENT_NOT_ALLOWED", "Script content is not allowed."));
    else if (HTML.matcher(text).find()) issues.add(issue("HTML_NOT_ALLOWED", "HTML is not allowed in a Knowledge Fact."));
    if (PROMPT_INJECTION.matcher(text).find()) issues.add(issue("PROMPT_INJECTION_PATTERN", "Instructions directed at the AI are not Knowledge Fact content."));
    if (URL_ONLY.matcher(text).matches()) issues.add(issue("URL_ONLY", "A URL by itself is not a Knowledge Fact."));
    if (UUID_ONLY.matcher(text).matches() || IDENTIFIER_ONLY.matcher(text).matches()) issues.add(issue("IDENTIFIER_ONLY", "An identifier by itself is not a Knowledge Fact."));

    String lower = text.toLowerCase(Locale.ROOT);
    String[] rawTokens = lower.split("\\s+");
    long alphabetic = text.codePoints().filter(Character::isLetter).count();
    long wordTokens = Arrays.stream(rawTokens).filter(t -> t.matches("(?iu).*[a-zåäö].*")).count();
    long mashTokens = Arrays.stream(rawTokens).filter(this::looksLikeMash).count();
    if (PLACEHOLDER.matcher(lower).find() && (rawTokens.length <= 8 || mashTokens > 0))
      issues.add(issue("PLACEHOLDER_TEXT_DETECTED", "Placeholder or test text is not valid factual content."));
    if (KEYBOARD.matcher(lower).find() || REPEATED_CHARS.matcher(lower).find() || mashTokens >= 2)
      issues.add(issue("KEYBOARD_MASH_DETECTED", "The draft contains nonsensical keyboard input."));
    if (alphabetic < 8 || wordTokens < Math.min(3, rawTokens.length))
      issues.add(issue("EXCESSIVE_NON_WORD_TOKENS", "The draft does not contain enough meaningful words."));
    if (lower.matches("(?s).*\\b(lorem|ipsum|dolor|sit amet)\\b.*"))
      issues.add(issue("PLACEHOLDER_TEXT_DETECTED", "Placeholder text is not valid factual content."));

    List<Token> tokens = tokens(lower);
    Predicate predicate = findPredicate(tokens);
    boolean question = text.endsWith("?") || lower.matches("^(what|why|when|where|who|how|vad|varför|när|var|vem|hur)\\b.*");
    boolean imperative = !tokens.isEmpty() && IMPERATIVES.contains(tokens.getFirst().text());
    boolean incomplete = !tokens.isEmpty() && SUBORDINATORS.contains(tokens.getFirst().text());
    boolean fragment = !tokens.isEmpty() && LEADING_FRAGMENT_WORDS.contains(tokens.getFirst().text())
        && predicate == null;
    if (question) issues.add(issue("QUESTION_INSTEAD_OF_FACT", "Write the Knowledge Fact as a declarative statement, not a question."));
    if (imperative || INSTRUCTION.matcher(lower).find())
      issues.add(issue("INSTRUCTION_INSTEAD_OF_FACT", "Write factual content rather than an editorial instruction."));
    if (incomplete) issues.add(issue("INCOMPLETE_SUBORDINATE_CLAUSE", "Write a complete main clause rather than an incomplete subordinate clause."));
    if (fragment) issues.add(issue("FRAGMENT_INSTEAD_OF_FACT", "Write a complete declarative clause rather than a phrase or heading."));
    if (!issues.isEmpty()) return result(Quality.INVALID, List.copyOf(issues.stream().distinct().toList()), text, null, fragment, question, imperative, incomplete);

    boolean subject = predicate != null && plausibleSubject(tokens, predicate.index());
    if (predicate == null || !subject || tokens.size() < 3)
      return suspicious("NO_PLAUSIBLE_FACTUAL_CLAIM", "The draft may not contain a clear subject and factual predicate.", text, predicate, true, false, false, false);
    if (requiresComplement(predicate.type()) && !hasLexicalComplement(tokens, predicate.index()))
      return suspicious("INCOMPLETE_PREDICATE", "The factual predicate is incomplete.", text, predicate, true, false, false, false);
    if (VAGUE.matcher(lower).matches() || VAGUE_SWEDISH.matcher(lower).matches())
      return suspicious("BROAD_OR_VAGUE_CLAIM", "The statement is meaningful but too broad or vague.", text, predicate, false, false, false, false);
    return result(Quality.VALID, List.of(), text, predicate, false, false, false, false);
  }

  private Predicate findPredicate(List<Token> tokens) {
    for (int i = 1; i < tokens.size(); i++) {
      String word = tokens.get(i).text();
      ConstructionType type = construction(word);
      if (type != ConstructionType.UNKNOWN) {
        if (type == ConstructionType.SIMPLE_FINITE && regularFinite(word) && i == tokens.size() - 1 && likelyNominalEnding(word)) continue;
        if (type == ConstructionType.PERFECT && i + 1 >= tokens.size()) continue;
        if (type == ConstructionType.AUXILIARY_MODAL && i + 1 >= tokens.size()) continue;
        if (tokens.subList(i + 1, tokens.size()).stream().anyMatch(t -> t.text().equals("inte")))
          type = ConstructionType.NEGATED_DECLARATIVE;
        return new Predicate(i, type, word);
      }
    }
    return null;
  }

  private ConstructionType construction(String word) {
    if (COPULAS.contains(word)) return ConstructionType.COPULAR;
    if (EXISTENTIALS.contains(word)) return ConstructionType.EXISTENTIAL;
    if (MODALS.contains(word)) return ConstructionType.AUXILIARY_MODAL;
    if (PERFECT_AUXILIARIES.contains(word)) return ConstructionType.PERFECT;
    if (PASSIVE_EXCEPTIONS.contains(word) || passiveFinite(word)) return ConstructionType.PASSIVE_S;
    if (OTHER_FINITE.contains(word) || regularFinite(word)) return ConstructionType.SIMPLE_FINITE;
    return ConstructionType.UNKNOWN;
  }

  private boolean regularFinite(String word) {
    if (word.length() < 4 || word.startsWith("att")) return false;
    return word.matches("(?iu).*(?:ar|er|ade|dde|de|te)$") || (word.length() >= 5 && word.endsWith("r"));
  }

  private boolean passiveFinite(String word) {
    if (word.length() < 5) return false;
    return word.matches("(?iu).*(?:as|eras|ades|des|tes)$");
  }

  private boolean likelyNominalEnding(String word) {
    return word.matches("(?iu).*(?:heter|teter|nader|skaper|ier|iker|eller|orer)$");
  }

  private boolean plausibleSubject(List<Token> tokens, int predicateIndex) {
    if (predicateIndex < 1) return false;
    for (int i = 0; i < predicateIndex; i++) {
      String word = tokens.get(i).text();
      if (word.length() >= 2 && !LEADING_FRAGMENT_WORDS.contains(word) && !SUBORDINATORS.contains(word)
          && (!NON_SUBJECT_WORDS.contains(word) || i + 1 < predicateIndex)) return true;
    }
    return tokens.getFirst().text().equals("det") && predicateIndex == 1;
  }

  private boolean requiresComplement(ConstructionType type) {
    return type == ConstructionType.COPULAR || type == ConstructionType.AUXILIARY_MODAL || type == ConstructionType.PERFECT;
  }

  private boolean hasLexicalComplement(List<Token> tokens, int predicateIndex) {
    return tokens.subList(predicateIndex + 1, tokens.size()).stream()
        .map(Token::text).anyMatch(word -> !Set.of("inte", "ej", "icke", "att", "en", "ett", "den", "det").contains(word));
  }

  private List<Token> tokens(String text) {
    Matcher matcher = WORD.matcher(text);
    var result = new ArrayList<Token>();
    while (matcher.find()) result.add(new Token(matcher.group().toLowerCase(Locale.ROOT), matcher.start(), matcher.end()));
    return List.copyOf(result);
  }

  private boolean looksLikeMash(String raw) {
    String token = raw.replaceAll("(?iu)[^a-zåäö]", "");
    if (token.length() < 6) return false;
    long vowels = token.codePoints().filter(c -> "aeiouyåäö".indexOf(c) >= 0).count();
    return vowels == 0 || (vowels * 1.0 / token.length() < 0.18 && token.length() >= 8);
  }

  private Result suspicious(String code, String message, String text, Predicate predicate,
      boolean fragment, boolean question, boolean imperative, boolean incomplete) {
    return result(Quality.SUSPICIOUS, List.of(issue(code, message)), text, predicate, fragment, question, imperative, incomplete);
  }
  private Result result(Quality quality, List<Issue> issues, String text, Predicate predicate,
      boolean fragment, boolean question, boolean imperative, boolean incomplete) {
    List<Token> tokens = tokens(text);
    boolean subject = predicate != null && plausibleSubject(tokens, predicate.index());
    String subjectSpan = subject ? text.substring(tokens.getFirst().start(), tokens.get(predicate.index()).start()).trim() : null;
    String predicateSpan = predicate == null ? null : text.substring(tokens.get(predicate.index()).start(), tokens.get(predicate.index()).end());
    int lexicalScore = (int) tokens.stream().map(Token::text).filter(word -> word.length() > 2).count();
    return new Result(quality, issues, new Diagnostics(VALIDATOR_VERSION, subject, subjectSpan,
        predicate != null, predicateSpan, predicate == null ? null : predicate.form(),
        predicate == null ? ConstructionType.UNKNOWN : predicate.type(), fragment, question, imperative,
        incomplete, lexicalScore));
  }
  private Issue issue(String code, String message) { return new Issue(code, message); }
  private String normalize(String value) { return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFC).trim().replaceAll("\\s+", " "); }
}
