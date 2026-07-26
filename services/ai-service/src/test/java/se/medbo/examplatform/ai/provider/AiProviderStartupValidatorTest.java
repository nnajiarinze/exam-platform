package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiProviderStartupValidatorTest {
  @Test
  void hostedFakeProviderRequiresDeliberateOverride() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("hosted");

    assertThatThrownBy(() -> new AiProviderStartupValidator(environment, "FAKE", false).run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("explicitly enabled");
    assertThatCode(() -> new AiProviderStartupValidator(environment, "FAKE", true).run(null))
        .doesNotThrowAnyException();
  }

  @Test
  void hostedGeminiAndLocalFakeRemainValid() {
    var hosted = new MockEnvironment();
    hosted.setActiveProfiles("hosted");
    var local = new MockEnvironment();

    assertThatCode(() -> new AiProviderStartupValidator(hosted, "GEMINI", false).run(null))
        .doesNotThrowAnyException();
    assertThatCode(() -> new AiProviderStartupValidator(local, "FAKE", false).run(null))
        .doesNotThrowAnyException();
  }
}
