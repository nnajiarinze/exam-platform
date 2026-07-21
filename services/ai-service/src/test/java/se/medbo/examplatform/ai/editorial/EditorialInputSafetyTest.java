package se.medbo.examplatform.ai.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class EditorialInputSafetyTest {
  @Test void rejectsObservedGibberishDefensively() {
    assertThat(EditorialInputSafety.classify("random shit to test asdfsdfas sffasdfsdfwwas safdsaf ds"))
        .isEqualTo(EditorialInputSafety.Quality.INVALID);
  }
  @Test void acceptsValidSwedishFact() {
    assertThat(EditorialInputSafety.classify("Kommuner ansvarar för grundskolan."))
        .isEqualTo(EditorialInputSafety.Quality.VALID);
  }
  @Test void distinguishesVagueClaim() {
    assertThat(EditorialInputSafety.classify("Municipalities do many things."))
        .isEqualTo(EditorialInputSafety.Quality.SUSPICIOUS);
  }
}
