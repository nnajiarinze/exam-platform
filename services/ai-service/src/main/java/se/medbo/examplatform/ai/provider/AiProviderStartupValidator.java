package se.medbo.examplatform.ai.provider;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
final class AiProviderStartupValidator implements ApplicationRunner {
  private final Environment environment;private final String provider;private final boolean allowFake;
  AiProviderStartupValidator(Environment environment,@Value("${ai.editorial.provider:GEMINI}") String provider,@Value("${ai.editorial.allow-fake-in-production:false}") boolean allowFake){this.environment=environment;this.provider=provider;this.allowFake=allowFake;}
  @Override public void run(ApplicationArguments args){boolean production=Arrays.stream(environment.getActiveProfiles()).anyMatch(profile->profile.equalsIgnoreCase("prod")||profile.equalsIgnoreCase("production"));if(production&&provider.equalsIgnoreCase("FAKE")&&!allowFake)throw new IllegalStateException("FAKE AI provider is forbidden in production unless AI_ALLOW_FAKE_IN_PRODUCTION is explicitly enabled");}
}
