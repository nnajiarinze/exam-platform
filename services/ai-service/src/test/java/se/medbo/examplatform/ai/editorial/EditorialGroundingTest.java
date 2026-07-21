package se.medbo.examplatform.ai.editorial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class EditorialGroundingTest {
  @Test void preservesSwedishCharactersAndClassifiesWhitespaceAndTerminalPunctuationAsNoOp() {
    assertThat(EditorialGrounding.noMeaningfulChange("  Kommunen ansvarar för äldreomsorg. ", "Kommunen   ansvarar för äldreomsorg" )).isTrue();
    assertThat(EditorialGrounding.noMeaningfulChange("Kommunen ansvarar för äldreomsorg.", "Kommunen ansvarar för skolan.")).isFalse();
  }

  @Test void acceptsAlignedCivicEvidenceAndRejectsObviousEntityTopicMismatch() {
    assertThat(EditorialGrounding.plausiblySupports(
        "Kommuner ansvarar för skolor, äldreomsorg och bibliotek.",
        "Kommunerna ansvarar för skolan, äldreomsorgen och kommunala bibliotek.")).isTrue();
    assertThat(EditorialGrounding.plausiblySupports(
        "Kommuner ansvarar för skolor, äldreomsorg och bibliotek.",
        "Riksdagen beslutar om Sveriges lagar.")).isFalse();
    assertThat(EditorialGrounding.plausiblySupports("Riksdagen beslutar om lagar.", "Riksdagen stiftar Sveriges lagar.")).isTrue();
  }

  @Test void concurrentFakeJobsKeepSourceIdentityAndEvidenceIndependent() throws Exception {
    var provider = new FakeAiEditorialProviderClient();
    UUID municipalitySource = UUID.randomUUID(), parliamentSource = UUID.randomUUID();
    var municipality = request(UUID.randomUUID(), municipalitySource,
        "Kommunerna är ansvariga för skolor och äldreomsorg.",
        "Kommunerna ansvarar för skolor, äldreomsorg och bibliotek.", "Kommunernas ansvar");
    var parliament = request(UUID.randomUUID(), parliamentSource,
        "Ledamöterna beslutar om statens budget.",
        "Riksdagen beslutar om lagar. Ledamöterna beslutar om statens budget.", "Riksdagens uppgifter");
    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Callable<AiEditorialProviderClient.Result>> calls = List.of(
          () -> provider.execute(municipality), () -> provider.execute(parliament));
      var results = executor.invokeAll(calls);
      var first = results.get(0).get().revisions().getFirst().evidence().getFirst();
      var second = results.get(1).get().revisions().getFirst().evidence().getFirst();
      assertThat(first.sourceId()).isEqualTo(municipalitySource);
      assertThat(first.quote()).contains("Kommunerna").doesNotContain("Riksdagen");
      assertThat(second.sourceId()).isEqualTo(parliamentSource);
      assertThat(second.quote()).contains("Ledamöterna").doesNotContain("Kommunerna");
    }
  }

  @Test void unchangedClearRewriteReturnsTypedFindingAndNoProposal() {
    var result = new FakeAiEditorialProviderClient().execute(request(UUID.randomUUID(), UUID.randomUUID(),
        "Riksdagen beslutar om lagar.", "Riksdagen beslutar om lagar.", "Riksdagen"));
    assertThat(result.revisions()).isEmpty();
    assertThat(result.findings()).singleElement().satisfies(finding -> {
      assertThat(finding.type()).isEqualTo("ALREADY_CLEAR");
      assertThat(finding.details()).containsEntry("resultType", "ALREADY_CLEAR");
    });
  }

  private AiEditorialProviderClient.Request request(UUID fact, UUID source, String target, String sourceText, String title) {
    return new AiEditorialProviderClient.Request(EditorialOperationType.REWRITE_FOR_CLARITY,
        List.of(new AiEditorialProviderClient.Target(fact, UUID.randomUUID(), 0, target, "a".repeat(64))),
        List.of(new AiEditorialProviderClient.Source(source, title, sourceText, "b".repeat(64))),
        UUID.randomUUID(), "Objective", "sv", null, null, 1, "test");
  }
}
