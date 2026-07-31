package se.medbo.examplatform.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeFactAutomatedReviewerTest {
  @Test
  void acceptsOneSubjectAndOnePredicateAsAtomic() {
    assertThat(KnowledgeFactAutomatedReviewer.isAtomic(
        "Norrland omfattar mer än hälften av Sveriges yta.")).isTrue();
    assertThat(KnowledgeFactAutomatedReviewer.isAtomic(
        "Ungefär 85 procent av Sveriges befolkning bor i städer.")).isTrue();
  }

  @Test
  void rejectsMultipleConsequencesAndCombinedPredicates() {
    assertThat(KnowledgeFactAutomatedReviewer.isAtomic(
        "Uppvärmningen smälter isar och havsnivån höjs.")).isFalse();
    assertThat(KnowledgeFactAutomatedReviewer.isAtomic(
        "Klimatförändringar leder till översvämningar samt torka.")).isFalse();
  }

  @Test
  void permitsOnePredicateWithAConcreteObjectList() {
    assertThat(KnowledgeFactAutomatedReviewer.isAtomic(
        "Sverige består av Götaland, Svealand och Norrland.")).isTrue();
  }

  @Test
  void groundingRequiresMeaningfulLexicalOverlap() {
    assertThat(KnowledgeFactAutomatedReviewer.plausiblySupports(
        "Norrland omfattar mer än hälften av Sveriges yta.",
        "Norrland omfattar mer än hälften av Sveriges yta.")).isTrue();
    assertThat(KnowledgeFactAutomatedReviewer.plausiblySupports(
        "Riksdagen beslutar om lagar.",
        "Kommunerna ansvarar för lokal service.")).isFalse();
  }
}
