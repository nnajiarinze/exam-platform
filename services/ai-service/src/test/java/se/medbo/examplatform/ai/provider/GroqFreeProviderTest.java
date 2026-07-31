package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class GroqFreeProviderTest {
  private final GroqFreeProvider provider=new GroqFreeProvider(new ObjectMapper(),mock(JdbcClient.class,RETURNS_DEEP_STUBS),
      "gsk_test","openai/gpt-oss-120b","https://api.groq.com/openai/v1",true,true,5,1000,8000);

  @Test void usesBestEffortSchemaModeForExistingEditorialSchemas(){
    assertThat(provider.strictSchema()).isFalse();
  }

  @Test void exposesConfiguredFreeCapacityWithoutASecret(){
    var availability=provider.availability(null);
    assertThat(availability.eligible()).isTrue();
    assertThat(availability.capacity()).containsEntry("requestLimit",1000L).containsEntry("tokenLimit",8000L);
    assertThat(availability.toString()).doesNotContain("gsk_test");
  }
}
