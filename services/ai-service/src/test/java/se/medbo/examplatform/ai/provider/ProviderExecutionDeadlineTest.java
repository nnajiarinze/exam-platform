package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderExecutionDeadlineTest {
  @Test void enforcesAbsoluteDeadlineAndFreesCaller(){
    var deadline=new ProviderExecutionDeadline(1,1);long started=System.nanoTime();
    try{assertThatThrownBy(()->deadline.execute(blocking(),request())).isInstanceOfSatisfying(AiProviderException.class,e->{assertThat(e.code()).isEqualTo("AI_PROVIDER_HARD_TIMEOUT");assertThat(e.diagnostics()).containsEntry("outcomeCertainty","OUTCOME_UNKNOWN");});
      assertThat((System.nanoTime()-started)/1_000_000).isLessThan(2500);
    }finally{deadline.close();}
  }
  private StructuredAiProvider blocking(){return new StructuredAiProvider(){public String provider(){return "TEST";}public String model(){return "test";}public int priority(){return 1;}public boolean enabled(){return true;}public boolean credentialsConfigured(){return true;}public Capabilities capabilities(){return new Capabilities(true,true,true,true,true,true,100,100,true,true,true);}public Availability availability(Request r){return null;}public Response execute(Request r){try{Thread.sleep(30_000);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new Response(new ObjectMapper().createObjectNode(),provider(),model(),model(),null,null,null,null,null,Map.of(),Map.of(),null,null,true);}};}
  private StructuredAiProvider.Request request(){return new StructuredAiProvider.Request("TEST","system","prompt",Map.of(),100,0,null,"test",0,"correlation","idempotency");}
}
