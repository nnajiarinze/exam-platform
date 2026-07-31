package se.medbo.examplatform.ai.generation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
