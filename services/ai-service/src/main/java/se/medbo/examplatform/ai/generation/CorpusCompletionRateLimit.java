package se.medbo.examplatform.ai.generation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Owner-authorized, explicitly scoped override for one corpus-completion operator. */
@Component
public class CorpusCompletionRateLimit {
  private final boolean enabled;
  private final String allowedCorpus;
  private final String allowedActor;
  private final int maximumJobsPerHour;

  public CorpusCompletionRateLimit(
      @Value("${ai.corpus-completion.enabled:false}") boolean enabled,
      @Value("${ai.corpus-completion.allowed-corpus:}") String allowedCorpus,
      @Value("${ai.corpus-completion.allowed-actor:corpus-automation}") String allowedActor,
      @Value("${ai.corpus-completion.max-jobs-per-hour:200}") int maximumJobsPerHour) {
    this.enabled = enabled;
    this.allowedCorpus = allowedCorpus;
    this.allowedActor = allowedActor;
    this.maximumJobsPerHour = maximumJobsPerHour;
  }

  public void enforce(JdbcClient jdbc, String actor, int defaultLimit) {
    int limit = completionActor(actor) ? maximumJobsPerHour : defaultLimit;
    long recent = jdbc.sql("SELECT count(*) FROM ai_generation_job WHERE requested_by=:actor AND created_at>now()-interval '1 hour'")
        .param("actor", actor).query(Long.class).single();
    if (recent >= limit) {
      throw new AiApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMIT_EXCEEDED",
          "The hourly AI generation limit was reached");
    }
  }

  boolean completionActor(String actor) {
    return enabled
        && "sverige-i-fokus-v1".equals(allowedCorpus)
        && allowedActor.equals(actor)
        && maximumJobsPerHour > 0;
  }
}
