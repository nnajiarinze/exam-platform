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

  @Test void questionPromptCarriesTheBoundedRecoveryTarget()throws Exception{
    var seen=new AtomicReference<StructuredAiProvider.Request>();var router=mock(FreeOnlyProviderRouter.class);
    when(router.execute(any())).thenAnswer(invocation->{seen.set(invocation.getArgument(0));return new StructuredAiProvider.Response(
        mapper.readTree("{\"resultType\":\"INSUFFICIENT_GROUNDED_INFORMATION\",\"proposals\":[],\"reason\":\"unsafe\"}"),
        "GROQ","test-free","test-free","request-45",100,20,8L,"stop",Map.of(),Map.of(),null,"HTTP_200",true);});
    UUID fact=UUID.randomUUID(),version=UUID.randomUUID(),sourceId=UUID.randomUUID(),sectionId=UUID.randomUUID();
    String statement="De största fackliga centralorganisationerna är LO, TCO och SACO.";
    var target=new QuestionGenerationProviderClient.Target(fact,version,1,statement,"a".repeat(64),"sv");
    var source=new QuestionGenerationProviderClient.Source(sourceId,sectionId,"Källa","Kapitel","Avsnitt",28,29,
        "b".repeat(64),"c".repeat(64),statement,List.of(statement));
    var context=new QuestionGenerationProviderClient.Context(UUID.randomUUID(),"Parter",null,UUID.randomUUID(),
        "Arbetsmarknadens parter",UUID.randomUUID(),"Arbetsmarknad",UUID.randomUUID(),UUID.randomUUID(),List.of(source),null,null);
    var narrow=new QuestionGenerationProviderClient.NarrowTarget(statement,"DIRECT_RECOGNITION",statement,
        "Exakt LO, TCO och SACO",List.of("Saco som separat svar","externa organisationer"),
        List.of("varje distraktor måste vara tydligt falsk","inga stavningsvarianter"),"Återge endast den exakta listan.");
    var request=new QuestionGenerationProviderClient.Request(target,context,1,"SINGLE_CHOICE",
        GeminiAiProviderClient.QUESTION_PROMPT_VERSION,null,"reviewer",0,null,"EASY","REMEMBER",narrow);

    new GeminiAiProviderClient(mapper,router).generate(request);

    assertThat(seen.get().prompt()).contains("narrowTarget","DIRECT_RECOGNITION","inga stavningsvarianter",
        "Exakt LO, TCO och SACO");
    assertThat(seen.get().systemInstruction()).contains("When NARROW_TARGET is supplied","follow it exactly");
  }

  @Test void lessonRepairPromptCarriesExactRejectedClaimsAndStrictSentenceContract()throws Exception{
    var seen=new AtomicReference<StructuredAiProvider.Request>();var router=mock(FreeOnlyProviderRouter.class);
    UUID section=UUID.randomUUID(),fact=UUID.randomUUID(),version=UUID.randomUUID(),job=UUID.randomUUID();
    when(router.execute(any())).thenAnswer(invocation->{seen.set(invocation.getArgument(0));return new StructuredAiProvider.Response(
        mapper.readTree("{\"status\":\"REPAIRED\",\"body\":\"Sverige är indelat i 21 regioner.\",\"evidenceQuotes\":[\"Sverige är indelat i 21 regioner.\"],\"keyTerms\":[]}"),
        "GROQ","test-free","test-free","request-44",300,80,8L,"stop",Map.of(),Map.of(),null,"HTTP_200",true);});
    var request=new LessonGenerationProviderClient.PageRepairRequest("Nivåer","Förstå nivåerna",section,
        "a".repeat(64),"Sverige är indelat i 21 regioner.",
        List.of(new LessonGenerationProviderClient.Fact(fact,version,"Sverige är indelat i 21 regioner.",section)),
        new LessonGenerationProviderClient.Page("INTRO","Rubrik","Utöver den nationella nivån är Sverige indelat i 21 regioner.",List.of(version),List.of(),List.of()),
        "Vad innebär regioner?","Nästa sida förklarar: Kommuner.",List.of("Rubrik","Sammanfattning"),List.of("LEARNER_USABILITY_MIN_40_WORDS"),List.of(new LessonGenerationProviderClient.FailedClaim(
            "Utöver den nationella nivån är Sverige indelat i 21 regioner.","UNSUPPORTED_CLAIM","Insufficient direct lexical support")),job,"reviewer",2);
    var result=new GeminiAiProviderClient(mapper,router).repairPage(request);
    assertThat(result.content().body()).isEqualTo("Sverige är indelat i 21 regioner.");
    assertThat(seen.get().prompt()).contains("repairReasons","LEARNER_USABILITY_MIN_40_WORDS","failedClaims","Utöver den nationella nivån","UNSUPPORTED_CLAIM","Insufficient direct lexical support","Vad innebär regioner?","Nästa sida förklarar: Kommuner.");
    assertThat(seen.get().systemInstruction()).contains("copied as a complete sentence from SOURCE","one or two concise learner instructions","Never repeat a transition","Never output ellipses or placeholders");
    assertThat(mapper.writeValueAsString(seen.get().jsonSchema()))
        .contains("REPAIRED","INSUFFICIENT_GROUNDED_INFORMATION")
        .doesNotContain("pageType","title","knowledgeFactVersionIds","topicId","learningObjectiveId","sourceSectionId");
  }

  @Test void lessonGenerationRestoresImmutablePlanMetadataInsteadOfRoundTrippingIt()throws Exception{
    var seen=new AtomicReference<StructuredAiProvider.Request>();var router=mock(FreeOnlyProviderRouter.class);
    UUID section=UUID.randomUUID(),fact=UUID.randomUUID(),version=UUID.randomUUID(),job=UUID.randomUUID();
    when(router.execute(any())).thenAnswer(invocation->{seen.set(invocation.getArgument(0));return new StructuredAiProvider.Response(
        mapper.readTree("{\"title\":\"Sveriges säkerhet\",\"introduction\":\"En introduktion.\",\"summary\":\"En sammanfattning.\",\"importantPoints\":[\"En punkt\"],\"pages\":[{\"body\":\"Sverige blev medlem i Nato år 2024.\",\"evidenceQuotes\":[\"Sverige blev medlem i Nato år 2024.\"],\"keyTerms\":[\"Nato\"]}]}"),
        "OPENROUTER_PAID","test-paid","test-paid","request-46",120,60,8L,"stop",Map.of(),Map.of(),null,"HTTP_200",false);});
    var plan=List.of(new LessonGenerationProviderClient.PlannedPage("SUMMARY","Att komma ihåg",List.of(version)));
    var request=new LessonGenerationProviderClient.Request(UUID.randomUUID(),"Säkerhet",UUID.randomUUID(),
        "Förstå säkerhet",section,"Källa","a".repeat(64),"Sverige blev medlem i Nato år 2024.",
        List.of(new LessonGenerationProviderClient.Fact(fact,version,"Sverige blev medlem i Nato år 2024.",section)),
        plan,"sv",job,"reviewer",0);

    var result=new GeminiAiProviderClient(mapper,router).generateLesson(request);

    assertThat(result.proposal().pages()).singleElement().satisfies(page->{
      assertThat(page.pageType()).isEqualTo("SUMMARY");
      assertThat(page.title()).isEqualTo("Att komma ihåg");
      assertThat(page.knowledgeFactVersionIds()).containsExactly(version);
    });
    assertThat(mapper.writeValueAsString(seen.get().jsonSchema()))
        .contains("body","evidenceQuotes","keyTerms")
        .doesNotContain("pageType","knowledgeFactVersionIds");
    assertThat(seen.get().systemInstruction()).contains("read-only","must not be returned");
  }
}
