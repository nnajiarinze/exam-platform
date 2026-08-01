package se.medbo.examplatform.ai.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CorpusCompletionRateLimitTest {
  @Test
  void enablesOnlyTheExplicitCorpusOperator() {
    var policy = new CorpusCompletionRateLimit(true, "sverige-i-fokus-v1", "corpus-automation", 200);

    assertThat(policy.completionActor("corpus-automation")).isTrue();
    assertThat(policy.completionActor("ordinary-admin")).isFalse();
  }

  @Test
  void remainsOffByDefaultOrForAnotherCorpus() {
    assertThat(new CorpusCompletionRateLimit(false, "sverige-i-fokus-v1", "corpus-automation", 200)
        .completionActor("corpus-automation")).isFalse();
    assertThat(new CorpusCompletionRateLimit(true, "another-corpus", "corpus-automation", 200)
        .completionActor("corpus-automation")).isFalse();
  }
}
