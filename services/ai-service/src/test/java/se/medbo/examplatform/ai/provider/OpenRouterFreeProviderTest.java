package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import org.springframework.jdbc.core.simple.JdbcClient;

class OpenRouterFreeProviderTest {
  private final ObjectMapper mapper=new ObjectMapper();
  private final OpenRouterFreeProvider provider=new OpenRouterFreeProvider(mapper,mock(JdbcClient.class,RETURNS_DEEP_STUBS),"key","vendor/model:free","https://openrouter.ai/api/v1",true,true,false,"","test",5);
  @Test void acceptsOnlyCompleteZeroPricing()throws Exception{assertThat(provider.zeroPricing(mapper.readTree("{\"prompt\":\"0\",\"completion\":\"0\",\"request\":\"0\",\"internal_reasoning\":\"0\",\"input_cache_read\":\"0\",\"input_cache_write\":\"0\"}"))).isTrue();}
  @Test void acceptsRequiredZeroPricingWhenUnusedOptionalFieldsAreOmitted()throws Exception{assertThat(provider.zeroPricing(mapper.readTree("{\"prompt\":\"0\",\"completion\":\"0\"}"))).isTrue();}
  @Test void rejectsNonZeroAndMissingRequiredPricing()throws Exception{assertThat(provider.zeroPricing(mapper.readTree("{\"prompt\":\"0.0001\",\"completion\":\"0\"}"))).isFalse();assertThat(provider.zeroPricing(mapper.readTree("{\"prompt\":\"0\"}"))).isFalse();}
}
