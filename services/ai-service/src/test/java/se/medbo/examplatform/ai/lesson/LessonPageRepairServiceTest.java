package se.medbo.examplatform.ai.lesson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LessonPageRepairServiceTest {
  @Test
  void forwardsNarrowReviewerGroundingInstructionAlongsideDiagnostics() {
    assertThat(LessonPageRepairService.repairReasons(List.of("UNSUPPORTED_CLAIM"),"  SUPPORTED_CONCEPTS_ONLY  "))
        .containsExactly("UNSUPPORTED_CLAIM","SUPPORTED_CONCEPTS_ONLY");
  }
  @Test
  void stripsOnlyExactPreviouslyRejectedClaimsFromProviderContent() {
    var failed=List.of(new LessonGenerationProviderClient.FailedClaim(
        "I Sverige finns diskrimineringslagen.","UNSUPPORTED_CLAIM","Not directly supported"));

    String guarded=LessonPageRepairService.stripExactFailedClaims(
        "På den här sidan sammanfattas innehållet. I Sverige finns diskrimineringslagen. "
            + "Diskriminering av människor är ett brott mot de mänskliga rättigheterna.",failed);

    assertThat(guarded).isEqualTo("På den här sidan sammanfattas innehållet. "
        + "Diskriminering av människor är ett brott mot de mänskliga rättigheterna.");
  }

  @Test
  void preservesGroundedSentencesThatAreNotExactRejectedClaims() {
    var failed=List.of(new LessonGenerationProviderClient.FailedClaim(
        "En bredare slutsats saknar stöd.","UNSUPPORTED_GENERALIZATION","Too broad"));

    String grounded="FN presenterade den så kallade förklaringen om de mänskliga rättigheterna 1948.";

    assertThat(LessonPageRepairService.stripExactFailedClaims(grounded,failed)).isEqualTo(grounded);
  }

  @Test
  void preservesAClaimRejectedOnlyForMissingEvidenceSoTheEnvelopeCanBeRepaired() {
    String claim="Sverige fortsatte att vara neutralt och valde att stå utanför Nato efter andra världskriget.";
    var failed=List.of(new LessonGenerationProviderClient.FailedClaim(
        claim,"MISSING_EVIDENCE","No exact page evidence occurs in the bounded source"));

    assertThat(LessonPageRepairService.stripExactFailedClaims(claim,failed)).isEqualTo(claim);
  }

  @Test
  void preservesOneOccurrenceWhenDuplicateClaimCanBeRepairedByDeduplication() {
    String claim="EU-medborgare röstar i det land där de är folkbokförda.";
    var failed=List.of(new LessonGenerationProviderClient.FailedClaim(
        claim,"DUPLICATE_CLAIM","Repeated on the rejected revision"));
    assertThat(LessonPageRepairService.stripExactFailedClaims(claim,failed)).isEqualTo(claim);
  }

  @Test
  void stripsRejectedClaimsAccumulatedAcrossRepairLineage() {
    var failed=List.of(
        new LessonGenerationProviderClient.FailedClaim("Första felaktiga påståendet.","UNSUPPORTED_CLAIM","Unsupported"),
        new LessonGenerationProviderClient.FailedClaim("Andra breda påståendet.","UNSUPPORTED_GENERALIZATION","Too broad"));

    assertThat(LessonPageRepairService.stripExactFailedClaims(
        "Första felaktiga påståendet. En grundad mening. Andra breda påståendet.",failed))
        .isEqualTo("En grundad mening.");
  }

  @Test
  void preservesV2TemplateLineStructureWhileRemovingRejectedClaims() {
    var failed=List.of(new LessonGenerationProviderClient.FailedClaim(
        "Ett felaktigt påstående.","UNSUPPORTED_CLAIM","Unsupported"));
    String body="Fråga: Vad gäller?\nEtt felaktigt påstående. En grundad mening.\nKom ihåg:\n• En grundad mening.\nNästa sida förklarar: Fortsättning.";
    assertThat(LessonPageRepairService.stripExactFailedClaims(body,failed)).isEqualTo(
        "Fråga: Vad gäller?\nEn grundad mening.\nKom ihåg:\n• En grundad mening.\nNästa sida förklarar: Fortsättning.");
  }
  @Test void removesRepeatedExplanationSentenceButPreservesRecallBullet(){
    String fact="EU-medborgare röstar där de är folkbokförda.";
    assertThat(LessonPageRepairService.deduplicateSubstantiveSentences(
        "Fråga: Var röstar EU-medborgare?\n"+fact+" "+fact+"\nKom ihåg:\n• "+fact+"\nSlut."))
        .isEqualTo("Fråga: Var röstar EU-medborgare?\n"+fact+"\nKom ihåg:\n• "+fact+"\nSlut.");
  }
  @Test void trimsOnlyUnassignedSourceSentencesToTheMaximum(){
    String fact="Den tilldelade faktan ska vara kvar.";
    String body="Fråga: Vad gäller?\n"+fact+" En extra källmening med flera ord. Ytterligare en källmening med flera ord.\nKom ihåg:\n• "+fact+"\nSlut.";
    String trimmed=LessonPageRepairService.enforceMaximumWords(body,List.of(fact),24);
    assertThat(trimmed).contains(fact,"• "+fact).doesNotContain("Ytterligare en källmening");
    assertThat(trimmed.split("\\s+").length).isLessThanOrEqualTo(24);
  }
}
