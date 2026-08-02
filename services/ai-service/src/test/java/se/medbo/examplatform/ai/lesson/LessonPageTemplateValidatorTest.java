package se.medbo.examplatform.ai.lesson;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class LessonPageTemplateValidatorTest {
  private final LessonPageTemplateValidator validator=new LessonPageTemplateValidator();

  @Test void acceptsOneQuestionRememberBulletsAndExactTransition(){
    String body="""
        Fråga: Vad behöver du förstå om demokrati?
        Demokrati betyder folkstyre. Medborgarna har möjlighet att påverka beslut. I en demokrati ska medborgarna kunna välja mellan olika politiska alternativ. De som får makten ska kunna bytas ut. Valen ska vara hemliga så att ingen behöver avslöja hur de röstar. Alla människor, grupper och partier har rätt att försöka övertyga andra människor om sina politiska idéer. En viktig förutsättning för demokrati är också att lagarna gäller för alla i Sverige.
        Kom ihåg:
        • Demokrati betyder folkstyre.
        • De som får makten ska kunna bytas ut.
        Nästa sida förklarar: Fria val.
        """;
    assertThat(validator.validate(body,"Vad behöver du förstå om demokrati?","Nästa sida förklarar: Fria val.").passed()).isTrue();
  }

  @Test void rejectsRepeatedQuestionAndMissingTransition(){
    var result=validator.validate("Fråga: Vad är demokrati? Varför? Kom ihåg:\n• Demokrati betyder folkstyre.","Vad är demokrati?","Nästa sida förklarar: Val.");
    assertThat(result.failureCodes()).contains("LEARNER_QUESTION_COUNT_INVALID","TRANSITION_MISMATCH","PAGE_WORD_COUNT_INVALID");
  }

  @Test void leavesPreV2PlansEligibleForClaimOnlyValidation(){
    assertThat(validator.validate("Legacy grounded lesson text.",null,null).passed()).isTrue();
  }

  @Test void summaryTitleInsideTransitionIsNotASecondRememberMarker(){
    String body="Fråga: Vad behöver du förstå om demokrati?\nPå den här sidan läser du faktameningarna i ordning och använder orden i frågan när du sammanfattar innehållet. Läs meningarna en gång till och jämför sedan sammanfattningen med punkterna under Kom ihåg. Demokrati betyder folkstyre. Medborgarna har möjlighet att påverka beslut. Valen ska vara hemliga så att ingen behöver avslöja hur de röstar. De som får makten ska kunna bytas ut.\nKom ihåg:\n• Demokrati betyder folkstyre.\nNästa sida förklarar: Kom ihåg: Demokrati.";
    assertThat(validator.validate(body,"Vad behöver du förstå om demokrati?","Nästa sida förklarar: Kom ihåg: Demokrati.").passed()).isTrue();
  }

  @Test void restoresOnlyImmutableQuestionTransitionAndRememberLayout(){
    String generated="Fråga: Normaliserad fråga?\nEn grundad mening. Kom ihåg:\n• En grundad mening.";
    assertThat(validator.restoreImmutableEnvelope(generated,"Fråga med 1 600 km?","Du har nu gått igenom ämnet.",java.util.List.of("En grundad mening.")))
        .startsWith("Fråga: Fråga med 1 600 km?\n").contains("\nKom ihåg:\n• En grundad mening.\n").endsWith("Du har nu gått igenom ämnet.");
  }
  @Test void restoresMissingRecallBlockFromAssignedImmutableFact(){
    assertThat(validator.restoreImmutableEnvelope("Fråga: Kort?\nEn grundad mening.","Kort?","Slut.",java.util.List.of("En grundad mening.")))
        .startsWith("Fråga: Kort?\n").contains("\nKom ihåg:\n• En grundad mening.\n").endsWith("Slut.");
  }
  @Test void removesTransitionGluedToPriorLineBeforeRestoringItOnce(){
    String body="Fråga: Kort?\nEn grundad mening. Nästa sida förklarar: Kom ihåg: Ämnet.";
    assertThat(validator.restoreImmutableEnvelope(body,"Kort?","Nästa sida förklarar: Kom ihåg: Ämnet.",java.util.List.of("En grundad mening.")))
        .startsWith("Fråga: Kort?\n").contains("\nKom ihåg:\n• En grundad mening.\n").endsWith("Nästa sida förklarar: Kom ihåg: Ämnet.");
  }
  @Test void restoresQuestionWhenProviderOmitsIt(){
    assertThat(validator.restoreImmutableEnvelope("En grundad mening.","Vad gäller?","Slut.",java.util.List.of("En grundad mening.")))
        .startsWith("Fråga: Vad gäller?\n");
  }
  @Test void restoresUsefulLearnerDirectionsUntilMinimumLength(){
    String restored=validator.restoreImmutableEnvelope("En kort grundad mening.","Vad gäller?","Slut.",java.util.List.of("En kort grundad mening."));
    assertThat(restored.split("\\s+").length).isGreaterThanOrEqualTo(70);
    assertThat(restored).contains("På den här sidan","Läs meningarna","Använd faktans centrala ord","Jämför formuleringen");
  }
}
