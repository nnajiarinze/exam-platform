package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import se.medbo.examplatform.ai.generation.AiApiException;

class GeminiQuotaConfigurationTest {
  @Test void freeOnlyFailsClosedWhenTierExpectationIsMissing(){var quota=service(GeminiQuotaService.UsageMode.FREE_ONLY,"",false,0,"",BigDecimal.ZERO,BigDecimal.ZERO);assertThatThrownBy(()->quota.reserve(10)).isInstanceOfSatisfying(AiApiException.class,e->org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo("AI_FREE_QUOTA_CONFIGURATION_REQUIRED"));}
  @Test void paidModeRequiresExplicitFlagLimitAndVersionedPriceProfile(){var quota=service(GeminiQuotaService.UsageMode.PAID_ALLOWED,"PAID",false,0,"",BigDecimal.ZERO,BigDecimal.ZERO);assertThatThrownBy(()->quota.reserve(10)).isInstanceOfSatisfying(AiApiException.class,e->org.assertj.core.api.Assertions.assertThat(e.code()).isEqualTo("AI_PAID_USAGE_NOT_ENABLED"));}
  private GeminiQuotaService service(GeminiQuotaService.UsageMode mode,String tier,boolean paid,double limit,String version,BigDecimal input,BigDecimal output){return new GeminiQuotaService(mock(JdbcClient.class),new SimpleMeterRegistry(),"GEMINI","gemini-2.5-flash","test-project",tier,"synthetic-test-key",true,mode,paid,limit,version,input,output,4096,3,1000,20,0,0,70,85,95,"America/Los_Angeles");}
}
