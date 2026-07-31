package se.medbo.examplatform.ai.provider;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
final class AiProviderStartupValidator implements ApplicationRunner {
  private final Environment environment;private final String provider,billingPolicy;private final boolean allowFake,allowPaid,requireZeroCost,allowUpgrade;
  @Autowired AiProviderStartupValidator(Environment environment,@Value("${ai.editorial.provider:GEMINI}")String provider,@Value("${ai.editorial.allow-fake-in-production:false}")boolean allowFake,@Value("${ai.billing-policy:FREE_ONLY}")String billingPolicy,@Value("${ai.allow-paid-fallback:false}")boolean allowPaid,@Value("${ai.require-zero-cost-provider:true}")boolean requireZeroCost,@Value("${ai.allow-automatic-billing-upgrade:false}")boolean allowUpgrade){this.environment=environment;this.provider=provider;this.allowFake=allowFake;this.billingPolicy=billingPolicy;this.allowPaid=allowPaid;this.requireZeroCost=requireZeroCost;this.allowUpgrade=allowUpgrade;}
  AiProviderStartupValidator(Environment environment,String provider,boolean allowFake){this(environment,provider,allowFake,"FREE_ONLY",false,true,false);}
  @Override public void run(ApplicationArguments args){if(!"FREE_ONLY".equals(billingPolicy)||allowPaid||!requireZeroCost||allowUpgrade)throw new IllegalStateException("Unsafe AI billing configuration: this service permits FREE_ONLY execution only");boolean production=Arrays.stream(environment.getActiveProfiles()).anyMatch(profile->profile.equalsIgnoreCase("prod")||profile.equalsIgnoreCase("production")||profile.equalsIgnoreCase("hosted"));if(production&&provider.equalsIgnoreCase("FAKE")&&!allowFake)throw new IllegalStateException("FAKE AI provider is forbidden in hosted or production environments unless AI_ALLOW_FAKE_IN_PRODUCTION is explicitly enabled");}
}
