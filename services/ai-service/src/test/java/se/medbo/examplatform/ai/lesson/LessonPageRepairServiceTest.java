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
  void stripsRejectedClaimsAccumulatedAcrossRepairLineage() {
    var failed=List.of(
        new LessonGenerationProviderClient.FailedClaim("Första felaktiga påståendet.","UNSUPPORTED_CLAIM","Unsupported"),
        new LessonGenerationProviderClient.FailedClaim("Andra breda påståendet.","UNSUPPORTED_GENERALIZATION","Too broad"));

    assertThat(LessonPageRepairService.stripExactFailedClaims(
        "Första felaktiga påståendet. En grundad mening. Andra breda påståendet.",failed))
        .isEqualTo("En grundad mening.");
  }
}
