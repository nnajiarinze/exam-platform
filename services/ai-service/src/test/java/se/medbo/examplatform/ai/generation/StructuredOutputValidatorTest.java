package se.medbo.examplatform.ai.generation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.ai.provider.AiProviderClient;
import se.medbo.examplatform.ai.provider.AiProviderException;

class StructuredOutputValidatorTest {
  private final StructuredOutputValidator validator = new StructuredOutputValidator(100);

  @Test void acceptsEvidencePresentInExactSource(){
    var proposal=new AiProviderClient.Proposal("Riksdagen beslutar om lagar.",List.of(new AiProviderClient.Evidence("Riksdagen beslutar om lagar.","1")),"HIGH",null);
    assertThatCode(()->validator.validate(new AiProviderClient.GenerationResult(List.of(proposal),List.of(),null),proposal.text(),1)).doesNotThrowAnyException();
  }

  @Test void rejectsMissingOrInventedEvidence(){
    var proposal=new AiProviderClient.Proposal("Påstående.",List.of(new AiProviderClient.Evidence("Inte i källan.",null)),null,null);
    assertThatThrownBy(()->validator.validate(new AiProviderClient.GenerationResult(List.of(proposal),List.of(),null),"Annan källa.",1)).isInstanceOf(AiProviderException.class).hasMessageContaining("evidence");
  }

  @Test void rejectsDuplicateAndHtmlOutput(){
    var proposal=new AiProviderClient.Proposal("<b>Fakta</b>",List.of(new AiProviderClient.Evidence("<b>Fakta</b>",null)),null,null);
    assertThatThrownBy(()->validator.validate(new AiProviderClient.GenerationResult(List.of(proposal,proposal),List.of(),null),proposal.text(),2)).isInstanceOf(AiProviderException.class);
  }

  @Test void acceptsVerbatimEvidenceWhenPdfLayoutWhitespaceIsNormalized() {
    var result = new AiProviderClient.GenerationResult(
      List.of(new AiProviderClient.Proposal(
        "Sverige är indelat i län.",
        List.of(new AiProviderClient.Evidence("Sverige är\nindelat   i län.", "s. 6")),
        "HIGH",
        null
      )),
      List.of(),
      null
    );
    assertThatCode(() -> validator.validate(result, "I materialet står: Sverige är\nindelat   i län.", 1))
      .doesNotThrowAnyException();
  }

  @Test void acceptsSwedishQuotationMarksAndPunctuationWithoutRemovingThem() {
    assertAccepted(
      "Materialet säger: ”Sverige är en demokrati”, och det gäller i hela landet.",
      "”Sverige är en demokrati”,"
    );
  }

  @Test void acceptsCanonicallyEquivalentUnicode() {
    String decomposed = Normalizer.normalize("Åland nämns inte här.", Normalizer.Form.NFD);
    assertAccepted(decomposed, "Åland nämns inte här.");
  }

  @Test void acceptsMultilineAndPdfWordBreakEvidence() {
    assertAccepted(
      "Fler och mer intensiva värme perioder kan ge kraf- tiga konsekvenser.",
      "Fler och mer intensiva värmeperioder kan ge kraftiga konsekvenser."
    );
  }

  @Test void rejectsParaphrasedEvidenceEvenWhenMeaningIsSimilar() {
    assertRejected("Riksdagen beslutar om Sveriges lagar.", "Riksdagen stiftar landets lagar.", "AI_EVIDENCE_NOT_IN_SOURCE");
  }

  @Test void rejectsEvidenceFromAnotherSourceSection() {
    assertRejected("Kommunerna ansvarar för lokal service.", "Riksdagen beslutar om lagar.", "AI_EVIDENCE_NOT_IN_SOURCE");
  }

  @Test void rejectsCompoundFacts() {
    var proposal = new AiProviderClient.Proposal(
      "Sverige är en demokrati. Riksdagen beslutar om lagar.",
      List.of(new AiProviderClient.Evidence("Sverige är en demokrati. Riksdagen beslutar om lagar.", null)),
      null,
      null
    );
    assertThatThrownBy(() -> validator.validate(
      new AiProviderClient.GenerationResult(List.of(proposal), List.of(), null),
      proposal.text(),
      1
    )).isInstanceOfSatisfying(AiProviderException.class,
      error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("AI_COMPOUND_PROPOSAL"));
  }

  @Test void reportsDuplicateCandidateCode() {
    var proposal = new AiProviderClient.Proposal(
      "Sverige är en demokrati.",
      List.of(new AiProviderClient.Evidence("Sverige är en demokrati.", null)),
      null,
      null
    );
    assertThatThrownBy(() -> validator.validate(
      new AiProviderClient.GenerationResult(List.of(proposal, proposal), List.of(), null),
      proposal.text(),
      2
    )).isInstanceOfSatisfying(AiProviderException.class,
      error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo("AI_DUPLICATE_PROPOSAL"));
  }

  @Test void preservesValidCandidatesAndReportsInvalidCandidatesIndependently() {
    var valid = new AiProviderClient.Proposal(
      "Sverige är ett land.",
      List.of(new AiProviderClient.Evidence("Sverige är ett land.", null)),
      null,
      null
    );
    var invalid = new AiProviderClient.Proposal(
      "Ett påstående utan stöd.",
      List.of(new AiProviderClient.Evidence("Detta finns inte i källan.", null)),
      null,
      null
    );
    var outcome = validator.filterSupported(
      new AiProviderClient.GenerationResult(List.of(valid, invalid), List.of(), null),
      "Sverige är ett land.",
      2
    );
    org.assertj.core.api.Assertions.assertThat(outcome.result().proposals()).containsExactly(valid);
    org.assertj.core.api.Assertions.assertThat(outcome.rejections()).singleElement()
      .satisfies(rejection -> {
        org.assertj.core.api.Assertions.assertThat(rejection.proposalIndex()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(rejection.code()).isEqualTo("AI_EVIDENCE_NOT_IN_SOURCE");
      });
  }

  private void assertAccepted(String source, String quote) {
    var proposal = new AiProviderClient.Proposal(
      "Ett tydligt faktapåstående.",
      List.of(new AiProviderClient.Evidence(quote, null)),
      null,
      null
    );
    assertThatCode(() -> validator.validate(
      new AiProviderClient.GenerationResult(List.of(proposal), List.of(), null),
      source,
      1
    )).doesNotThrowAnyException();
  }

  private void assertRejected(String source, String quote, String code) {
    var proposal = new AiProviderClient.Proposal(
      "Ett tydligt faktapåstående.",
      List.of(new AiProviderClient.Evidence(quote, null)),
      null,
      null
    );
    assertThatThrownBy(() -> validator.validate(
      new AiProviderClient.GenerationResult(List.of(proposal), List.of(), null),
      source,
      1
    )).isInstanceOfSatisfying(AiProviderException.class,
      error -> org.assertj.core.api.Assertions.assertThat(error.code()).isEqualTo(code));
  }
}
