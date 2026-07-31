package se.medbo.examplatform.ai.editorial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import se.medbo.examplatform.ai.generation.PromptTemplateRegistry;
import se.medbo.examplatform.ai.provider.AiProviderClient;
import se.medbo.examplatform.ai.provider.FreeOnlyProviderRouter;
import se.medbo.examplatform.ai.provider.StructuredAiProvider;
import se.medbo.examplatform.ai.question.QuestionGenerationProviderClient;
import se.medbo.examplatform.ai.lesson.LessonGenerationProviderClient;

class GeminiAiProviderClientTest {
  private final ObjectMapper mapper=new ObjectMapper();
  @Test void rendersExistingPromptAndParsesProviderNeutralStructuredResponse()throws Exception{
    var seen=new AtomicReference<StructuredAiProvider.Request>();var router=mock(FreeOnlyProviderRouter.class);
    when(router.execute(any())).thenAnswer(invocation->{seen.set(invocation.getArgument(0));return new StructuredAiProvider.Response(
        mapper.readTree("{\"proposals\":[{\"text\":\"Riksdagen stiftar lagar.\",\"quote\":\"Riksdagen stiftar lagar.\"}]}"),
        "GROQ","test-free","test-free","request-42",12,7,8L,"stop",Map.of(),Map.of(),null,"HTTP_200",true);});
    var result=new GeminiAiProviderClient(mapper,router).generate(new AiProviderClient.GenerationRequest(
        "Riksdagen stiftar lagar.","Riksdagen","Förstå lagstiftning","sv",3,"",
        PromptTemplateRegistry.KNOWLEDGE_FACT_V3,null,"test",0));
    assertThat(result.proposals()).hasSize(1);assertThat(result.usage().inputTokens()).isEqualTo(12);
    assertThat(result.usage().requestId()).isEqualTo("request-42");assertThat(seen.get().prompt()).contains("\"source\":\"Riksdagen stiftar lagar.\"","\"objective\":\"Förstå lagstiftning\"");
    assertThat(seen.get().systemInstruction()).contains("one independently testable subject-predicate proposition");
    assertThat(seen.get().jsonSchema()).isNotEmpty();
  }

  @Test void questionPromptSendsOnlyRequiredContextAndRestoresImmutableEvidence()throws Exception{
    var seen=new AtomicReference<StructuredAiProvider.Request>();var router=mock(FreeOnlyProviderRouter.class);
    when(router.execute(any())).thenAnswer(invocation->{seen.set(invocation.getArgument(0));return new StructuredAiProvider.Response(
        mapper.readTree("{\"resultType\":\"QUESTIONS_PROPOSED\",\"proposals\":[{\"questionType\":\"SINGLE_CHOICE\",\"questionText\":\"Vem stiftar lagar?\",\"answerOptions\":[{\"optionKey\":\"A\",\"text\":\"Riksdagen\"},{\"optionKey\":\"B\",\"text\":\"Polisen\"}],\"correctOptionKeys\":[\"A\"],\"explanation\":\"Riksdagen stiftar lagar.\",\"rationale\":\"Prövar faktan.\",\"pedagogicalMetadata\":{\"difficulty\":\"EASY\",\"bloomsLevel\":\"REMEMBER\",\"complexity\":\"LOW\",\"intent\":\"PRACTICE\",\"estimatedReadingSeconds\":8},\"qualityRationale\":\"Entydig och grundad.\"}]}"),
        "GROQ","test-free","test-free","request-43",700,180,8L,"stop",Map.of(),Map.of(),null,"HTTP_200",true);});
    UUID fact=UUID.randomUUID(),version=UUID.randomUUID(),sourceId=UUID.randomUUID(),sectionId=UUID.randomUUID();
    String checksum="a".repeat(64),sourceChecksum="b".repeat(64),quote="Riksdagen stiftar lagar.";
    var target=new QuestionGenerationProviderClient.Target(fact,version,2,quote,checksum,"sv");
    var source=new QuestionGenerationProviderClient.Source(sourceId,sectionId,"Källa","Kapitel",null,1,2,sourceChecksum,"c".repeat(64),quote+" Orelaterad sektionskontext ska inte skickas.",List.of(quote));
    var context=new QuestionGenerationProviderClient.Context(UUID.randomUUID(),"Lagar","unused description",UUID.randomUUID(),"Demokrati",UUID.randomUUID(),"unused subject",UUID.randomUUID(),UUID.randomUUID(),List.of(source),"unused corpus","unused purpose");
    var request=new QuestionGenerationProviderClient.Request(target,context,1,"SINGLE_CHOICE",GeminiAiProviderClient.QUESTION_PROMPT_VERSION,null,"test",0,null,null,null);
    var result=new GeminiAiProviderClient(mapper,router).generate(request);var proposal=result.proposals().getFirst();
    assertThat(proposal.factEvidence()).isEqualTo(new QuestionGenerationProviderClient.FactEvidence(fact,2,checksum,quote));
    assertThat(proposal.sourceEvidence()).containsExactly(new QuestionGenerationProviderClient.SourceEvidence(sourceId,sectionId,"Källa",sourceChecksum,quote));
    assertThat(seen.get().prompt()).contains(quote,"\"topic\":\"Demokrati\"","\"objective\":\"Lagar\"")
        .doesNotContain("Orelaterad sektionskontext","unused description","unused subject","unused corpus","unused purpose","contentChecksum","examVersionId");
    assertThat(mapper.writeValueAsString(seen.get().jsonSchema())).doesNotContain("factEvidence","sourceEvidence","confidence","warnings","language");
  }

  @Test void lessonRepairPromptCarriesExactRejectedClaimsAndStrictSentenceContract()throws Exception{
    var seen=new AtomicReference<StructuredAiProvider.Request>();var router=mock(FreeOnlyProviderRouter.class);
    UUID section=UUID.randomUUID(),fact=UUID.randomUUID(),version=UUID.randomUUID(),job=UUID.randomUUID();
    when(router.execute(any())).thenAnswer(invocation->{seen.set(invocation.getArgument(0));return new StructuredAiProvider.Response(
        mapper.readTree("{\"pageType\":\"INTRO\",\"title\":\"Rubrik\",\"body\":\"Sverige är indelat i 21 regioner.\",\"knowledgeFactVersionIds\":[\""+version+"\"],\"evidenceQuotes\":[\"Sverige är indelat i 21 regioner.\"],\"keyTerms\":[]}"),
        "GROQ","test-free","test-free","request-44",300,80,8L,"stop",Map.of(),Map.of(),null,"HTTP_200",true);});
    var request=new LessonGenerationProviderClient.PageRepairRequest("Nivåer","Förstå nivåerna",section,
        "a".repeat(64),"Sverige är indelat i 21 regioner.",
        List.of(new LessonGenerationProviderClient.Fact(fact,version,"Sverige är indelat i 21 regioner.",section)),
        new LessonGenerationProviderClient.Page("INTRO","Rubrik","Utöver den nationella nivån är Sverige indelat i 21 regioner.",List.of(version),List.of(),List.of()),
        List.of("Rubrik","Sammanfattning"),List.of(new LessonGenerationProviderClient.FailedClaim(
            "Utöver den nationella nivån är Sverige indelat i 21 regioner.","UNSUPPORTED_CLAIM","Insufficient direct lexical support")),job,"reviewer",2);
    new GeminiAiProviderClient(mapper,router).repairPage(request);
    assertThat(seen.get().prompt()).contains("failedClaims","Utöver den nationella nivån","UNSUPPORTED_CLAIM","Insufficient direct lexical support");
    assertThat(seen.get().systemInstruction()).contains("copied as a complete sentence from SOURCE","Do not pad to a word target");
  }
}
