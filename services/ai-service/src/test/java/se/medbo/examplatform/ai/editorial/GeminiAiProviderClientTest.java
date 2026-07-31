package se.medbo.examplatform.ai.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.ai.generation.PromptTemplateRegistry;
import se.medbo.examplatform.ai.provider.AiProviderClient;
import se.medbo.examplatform.ai.provider.FreeOnlyProviderRouter;
import se.medbo.examplatform.ai.provider.StructuredAiProvider;

class GeminiAiProviderClientTest {
  private final ObjectMapper mapper=new ObjectMapper();
  @Test void rendersExistingPromptAndParsesProviderNeutralStructuredResponse()throws Exception{
    var seen=new AtomicReference<StructuredAiProvider.Request>();var router=mock(FreeOnlyProviderRouter.class);
    when(router.execute(any())).thenAnswer(invocation->{seen.set(invocation.getArgument(0));return new StructuredAiProvider.Response(
        mapper.readTree("{\"proposals\":[{\"text\":\"Riksdagen stiftar lagar.\",\"sourceEvidence\":[{\"quote\":\"Riksdagen stiftar lagar.\"}]}],\"warnings\":[]}"),
        "GROQ","test-free","test-free","request-42",12,7,8L,"stop",Map.of(),Map.of(),null,"HTTP_200",true);});
    var result=new GeminiAiProviderClient(mapper,router).generate(new AiProviderClient.GenerationRequest(
        "Riksdagen stiftar lagar.","Riksdagen","Förstå lagstiftning","sv",3,"",
        PromptTemplateRegistry.KNOWLEDGE_FACT_V3,null,"test",0));
    assertThat(result.proposals()).hasSize(1);assertThat(result.usage().inputTokens()).isEqualTo(12);
    assertThat(result.usage().requestId()).isEqualTo("request-42");assertThat(seen.get().prompt()).contains("<SOURCE_CONTENT>","<OBJECTIVE>Förstå lagstiftning</OBJECTIVE>");
    assertThat(seen.get().systemInstruction()).contains("exactly one independently testable proposition");
    assertThat(seen.get().jsonSchema()).isNotEmpty();
  }
}
