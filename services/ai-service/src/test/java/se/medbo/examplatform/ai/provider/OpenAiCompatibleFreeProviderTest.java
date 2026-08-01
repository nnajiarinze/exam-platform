package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleFreeProviderTest {
  private final ObjectMapper mapper=new ObjectMapper();

  @Test void acceptsJsonAndWhitespace()throws Exception{
    assertThat(OpenAiCompatibleFreeProvider.normalizeStructuredResponse(mapper,"  {\"status\":\"REPAIRED\"} \n").path("status").asText()).isEqualTo("REPAIRED");
  }
  @Test void acceptsSingleFencedJsonObject()throws Exception{
    assertThat(OpenAiCompatibleFreeProvider.normalizeStructuredResponse(mapper,"```json\n{\"status\":\"REPAIRED\"}\n```").isObject()).isTrue();
  }
  @Test void acceptsOneUnambiguousWrappedObject()throws Exception{
    assertThat(OpenAiCompatibleFreeProvider.normalizeStructuredResponse(mapper,"Here is the result:\n{\"status\":\"REPAIRED\"}\nDone.").isObject()).isTrue();
  }
  @Test void rejectsMultipleObjects(){
    assertThatThrownBy(()->OpenAiCompatibleFreeProvider.normalizeStructuredResponse(mapper,"{\"a\":1} {\"b\":2}")).hasMessage("MULTIPLE_JSON_OBJECTS");
  }
  @Test void rejectsTruncatedJson(){
    assertThatThrownBy(()->OpenAiCompatibleFreeProvider.normalizeStructuredResponse(mapper,"```json\n{\"status\":\"REPAIRED\"")).hasMessage("TRUNCATED_JSON");
  }
  @Test void rejectsNonObjectJson(){
    assertThatThrownBy(()->OpenAiCompatibleFreeProvider.normalizeStructuredResponse(mapper,"[1,2,3]")).hasMessage("JSON_OBJECT_REQUIRED");
  }
}
