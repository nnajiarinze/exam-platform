package se.medbo.examplatform.ai.generation;

import static org.assertj.core.api.Assertions.*;import java.util.List;import org.junit.jupiter.api.Test;import se.medbo.examplatform.ai.provider.AiProviderClient.*;import se.medbo.examplatform.ai.provider.AiProviderException;
class StructuredOutputValidatorTest {private final StructuredOutputValidator validator=new StructuredOutputValidator(100);
 @Test void acceptsEvidencePresentInExactSource(){var p=new Proposal("Riksdagen beslutar om lagar.",List.of(new Evidence("Riksdagen beslutar om lagar.","1")),"HIGH",null);assertThatCode(()->validator.validate(new GenerationResult(List.of(p),List.of(),null),p.text(),1)).doesNotThrowAnyException();}
 @Test void rejectsMissingOrInventedEvidence(){var p=new Proposal("Påstående.",List.of(new Evidence("Inte i källan.",null)),null,null);assertThatThrownBy(()->validator.validate(new GenerationResult(List.of(p),List.of(),null),"Annan källa.",1)).isInstanceOf(AiProviderException.class).hasMessageContaining("evidence");}
 @Test void rejectsDuplicateAndHtmlOutput(){var p=new Proposal("<b>Fakta</b>",List.of(new Evidence("<b>Fakta</b>",null)),null,null);assertThatThrownBy(()->validator.validate(new GenerationResult(List.of(p,p),List.of(),null),p.text(),2)).isInstanceOf(AiProviderException.class);}
}
