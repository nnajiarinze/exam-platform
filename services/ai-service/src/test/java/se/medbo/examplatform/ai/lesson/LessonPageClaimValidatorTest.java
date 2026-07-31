package se.medbo.examplatform.ai.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LessonPageClaimValidatorTest {
  private final LessonPageClaimValidator validator=new LessonPageClaimValidator();
  private final String source="Falsk information och hat sprids ibland på sociala medier för att skapa konflikter i samhället. Ibland hotas politiker och journalister. Samhället skyddar dem som deltar i den allmänna debatten.";
  private final String fact="Falsk information och hat sprids ibland på sociala medier för att skapa konflikter i samhället.";

  @Test void extractsAndSupportsMultipleDirectClaims(){
    var result=validator.validate(page("Falsk information och hat sprids ibland på sociala medier för att skapa konflikter i samhället. Ibland hotas politiker och journalister."),source,List.of(fact));
    assertThat(result.supported()).isTrue();assertThat(result.claims()).hasSize(2).allMatch(c->c.status().equals("SUPPORTED"));
  }
  @Test void rejectsUnsupportedCausalityConsequenceAndGeneralization(){
    assertThat(validator.validate(page("Sådana inlägg leder till att människor tappar förtroendet för alla institutioner."),source,List.of(fact)).failureCodes()).contains("UNSUPPORTED_CAUSALITY");
    assertThat(validator.validate(page("Falsk information polariserar alltid hela samhället."),source,List.of(fact)).failureCodes()).contains("UNSUPPORTED_GENERALIZATION");
  }
  @Test void acceptsNormalizedPdfEvidenceAndIgnoresTransition(){
    var normalizedSource="Falsk information och hat sprids ibland på sociala\nmedier för att skapa konflikter i samhället.";
    var result=validator.validate(page("I den här lektionen läser du om demokratin. Falsk information och hat sprids ibland på sociala medier för att skapa konflikter i samhället."),normalizedSource,List.of(fact));
    assertThat(result.supported()).isTrue();assertThat(result.claims().getFirst().status()).isEqualTo("NON_FACTUAL_TEXT");
  }
  @Test void rejectsCrossSectionClaimAndMissingEvidence(){
    var result=validator.validate(page("Riksdagen beslutar om statens budget."),source,List.of(fact));
    assertThat(result.failureCodes()).contains("UNSUPPORTED_CLAIM");
  }
  @Test void reportsVersionedExplainableDiagnostics(){
    var claim=validator.validate(page("Påståendet saknar stöd i källan."),source,List.of(fact)).claims().getFirst();
    assertThat(LessonPageClaimValidator.VERSION).isEqualTo("lesson-page-claim-v1");assertThat(claim.diagnostic()).isNotBlank();
  }
  @Test void rejectsDuplicateAndContradictoryClaims(){
    assertThat(validator.validate(page("Ibland hotas politiker och journalister. Ibland hotas politiker och journalister."),source,List.of(fact)).failureCodes()).contains("DUPLICATE_CLAIM");
    assertThat(validator.validate(page("Politiker och journalister hotas inte ibland."),"Politiker och journalister hotas ibland.",List.of()).failureCodes()).contains("CONTRADICTION");
  }
  private LessonGenerationProviderClient.Page page(String body){return new LessonGenerationProviderClient.Page("CORE","Rubrik",body,List.of(UUID.randomUUID()),List.of("Falsk information och hat sprids ibland på sociala medier för att skapa konflikter i samhället."),List.of("demokrati"));}
}
