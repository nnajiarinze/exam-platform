package se.medbo.examplatform.ai.question;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionGenerationPromptVersionTest {
  @Test void upgradesAQueuedGenerationPromptWithoutChangingItsImmutableSnapshot() {
    var target=new QuestionGenerationProviderClient.Target(UUID.randomUUID(),UUID.randomUUID(),1,
        "Riksdagen stiftar lagar.","a".repeat(64),"sv");
    var source=new QuestionGenerationProviderClient.Source(UUID.randomUUID(),UUID.randomUUID(),"Källa",
        "Kapitel","Avsnitt",1,1,"b".repeat(64),"c".repeat(64),"Riksdagen stiftar lagar.",
        List.of("Riksdagen stiftar lagar."));
    var context=new QuestionGenerationProviderClient.Context(UUID.randomUUID(),"Lagar",null,
        UUID.randomUUID(),"Demokrati",UUID.randomUUID(),"Samhälle",UUID.randomUUID(),UUID.randomUUID(),
        List.of(source),"sverige-i-fokus","QUESTION_GENERATION");
    var narrow=new QuestionGenerationProviderClient.NarrowTarget("Riksdagen stiftar lagar.","DIRECT_RECOGNITION",
        "Riksdagen stiftar lagar.","Riksdagen",List.of("andra aktörer"),List.of("entydigt falska"),
        "Återge endast faktan.");
    var stored=new QuestionGenerationProviderClient.Request(target,context,1,"SINGLE_CHOICE",
        "question-generation-intelligence-v1",null,null,0,null,null,null,narrow);
    UUID jobId=UUID.randomUUID();

    var execution=QuestionGenerationJobService.currentExecution(stored,jobId,"worker",2);

    assertThat(execution.promptVersion()).isEqualTo(QuestionGenerationProviderClient.CURRENT_PROMPT_VERSION);
    assertThat(execution.target()).isSameAs(target);
    assertThat(execution.context()).isSameAs(context);
    assertThat(execution.jobId()).isEqualTo(jobId);
    assertThat(execution.retryAttempt()).isEqualTo(2);
    assertThat(execution.narrowTarget()).isSameAs(narrow);
  }
}
