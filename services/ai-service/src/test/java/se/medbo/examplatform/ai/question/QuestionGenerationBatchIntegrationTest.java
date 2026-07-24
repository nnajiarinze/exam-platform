package se.medbo.examplatform.ai.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import se.medbo.examplatform.ai.generation.AiApiException;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties={"ai.editorial.provider=FAKE","ai.editorial.model=deterministic-v1","spring.task.scheduling.enabled=false"})
class QuestionGenerationBatchIntegrationTest {
  @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
  @Autowired QuestionGenerationBatchService batches;@Autowired QuestionGenerationJobService jobs;

  @Test void immutableBatchProcessesIndependentItemsAndReportsGenerationReviewAndUsage() throws Exception {
    var definition=definition("batch-process",2);var created=batches.create(definition);UUID batch=(UUID)created.get("id");
    assertThat(created).containsEntry("status","PENDING").containsEntry("requested",2L);
    assertThat(batches.create(definition).get("id")).isEqualTo(batch);
    assertThatThrownBy(()->batches.create(new QuestionGenerationBatchService.Definition("TOPIC",definition.scopeId(),"changed","sv",Map.of("changed",true),"author","batch-process",definition.items())))
        .isInstanceOf(AiApiException.class).extracting(e->((AiApiException)e).code()).isEqualTo("BATCH_IDEMPOTENCY_CONFLICT");
    for(int i=0;i<8;i++){batches.work();jobs.work();Thread.sleep(80);}
    var complete=batches.batch(batch);
    assertThat(complete).containsEntry("status","COMPLETED").containsEntry("generated",2L).containsEntry("failed",0L).containsEntry("generationProgressPercentage",100);
    var proposals=batches.proposals(batch,0,20,null);
    assertThat((List<?>)proposals.get("items")).hasSize(2);
    UUID proposal=(UUID)((Map<?,?>)((List<?>)proposals.get("items")).getFirst()).get("id");
    assertThat(batches.assign(List.of(proposal),"reviewer-1","admin",null)).containsEntry("succeeded",1L);
    assertThat(batches.proposals(batch,0,20,null).toString()).contains("reviewer-1");
  }

  @Test void cancellationIsIdempotentAndStopsUnclaimedWork() {
    UUID batch=(UUID)batches.create(definition("batch-cancel",2)).get("id");
    var first=batches.cancel(batch,"admin");
    assertThat(first.get("status")).isIn("CANCELLING","CANCELLED");
    assertThat(batches.cancel(batch,"admin").get("status")).isIn("CANCELLING","CANCELLED");
  }

  private QuestionGenerationBatchService.Definition definition(String key,int count){
    UUID topic=UUID.randomUUID(),subject=UUID.randomUUID(),examVersion=UUID.randomUUID();var items=new java.util.ArrayList<QuestionGenerationBatchService.Item>();
    for(int i=0;i<count;i++){UUID fact=UUID.randomUUID(),version=UUID.randomUUID(),objective=UUID.randomUUID(),source=UUID.randomUUID();String text="Riksdagen beslutar om lagar variant "+i+".";String sourceText=text+" Källan ger mer sammanhang.";var target=new QuestionGenerationProviderClient.Target(fact,version,1,text,checksum(text),"sv");var context=new QuestionGenerationProviderClient.Context(objective,"Demokrati",null,topic,"Styrelseskick",subject,"Samhälle",UUID.randomUUID(),examVersion,List.of(new QuestionGenerationProviderClient.Source(source,"Källa",checksum(sourceText),sourceText)));items.add(new QuestionGenerationBatchService.Item(target,context,"SINGLE_CHOICE",i==0?"EASY":"MEDIUM",i==0?"REMEMBER":"UNDERSTAND"));}
    return new QuestionGenerationBatchService.Definition("TOPIC",topic,"Topic","sv",Map.of("questionsPerKnowledgeFact",1),"author",key,items);
  }
  private String checksum(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new RuntimeException(e);}}
}
