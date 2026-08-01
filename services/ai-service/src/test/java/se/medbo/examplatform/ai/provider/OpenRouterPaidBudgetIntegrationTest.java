package se.medbo.examplatform.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OpenRouterPaidBudgetIntegrationTest {
  @Container @ServiceConnection static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
  @Autowired JdbcClient jdbc;
  @Autowired ProviderAttemptRecoveryService recovery;
  private final OpenRouterPaidModelDiscoveryService.Model model=new OpenRouterPaidModelDiscoveryService.Model(
      "openai/test-editorial",new BigDecimal("0.000001"),new BigDecimal("0.000002"),BigDecimal.ZERO,BigDecimal.ZERO,
      32768,List.of("response_format","structured_outputs","temperature","max_tokens"),OffsetDateTime.now(ZoneOffset.UTC));
  @BeforeEach void clear(){jdbc.sql("DELETE FROM ai_paid_request_accounting").update();jdbc.sql("DELETE FROM ai_paid_budget").update();}

  @Test void accountsAndPersistsBudgetAcrossApplicationRestart(){
    var firstService=new OpenRouterPaidBudgetService(jdbc,true,new BigDecimal("0.10000000"));var reservation=firstService.reserve(request(),model,"FREE_CHAIN_EXHAUSTED");
    firstService.reconcile(reservation,100,50,10,123,new BigDecimal("0.01000000"),"request-1","SUCCEEDED",null);
    var restartedService=new OpenRouterPaidBudgetService(jdbc,true,new BigDecimal("0.10000000"));var status=restartedService.status(model,4096);
    assertThat(status).containsEntry("spentUsd",new BigDecimal("0.01000000")).containsEntry("remainingUsd",new BigDecimal("0.09000000"));
    var accounting=jdbc.sql("SELECT provider,model,prompt_tokens,completion_tokens,reasoning_tokens,latency_ms,estimated_cost_usd,actual_cost_usd,budget_before_usd,budget_after_usd,routing_reason,status FROM ai_paid_request_accounting").query().singleRow();
    assertThat(accounting).containsEntry("provider","OPENROUTER_PAID").containsEntry("model","openai/test-editorial").containsEntry("prompt_tokens",100).containsEntry("completion_tokens",50).containsEntry("reasoning_tokens",10).containsEntry("latency_ms",123L).containsEntry("routing_reason","FREE_CHAIN_EXHAUSTED").containsEntry("status","SUCCEEDED");
  }

  @Test void atomicallyStopsConcurrentRequestsBeforeBudgetCanBeExceeded()throws Exception{
    var service=new OpenRouterPaidBudgetService(jdbc,true,new BigDecimal("0.04000000"));var successes=new CopyOnWriteArrayList<OpenRouterPaidBudgetService.Reservation>();var failures=new CopyOnWriteArrayList<String>();
    try(var pool=Executors.newFixedThreadPool(2)){var work=java.util.stream.IntStream.range(0,2).mapToObj(i->(java.util.concurrent.Callable<Void>)()->{try{successes.add(service.reserve(request(),model,"FREE_CHAIN_EXHAUSTED"));}catch(AiProviderException e){failures.add(e.code());}return null;}).toList();for(var result:pool.invokeAll(work))result.get();}
    assertThat(successes).hasSize(1);assertThat(failures).containsExactly("PAID_BUDGET_EXHAUSTED");var status=service.status(model,4096);assertThat((BigDecimal)status.get("spentUsd")).isEqualByComparingTo(BigDecimal.ZERO);assertThat((BigDecimal)status.get("reservedUsd")).isLessThanOrEqualTo(new BigDecimal("0.04000000"));
  }

  @Test void disabledPaidModeNeverReservesMoney(){
    var service=new OpenRouterPaidBudgetService(jdbc,false,new BigDecimal("14.00000000"));
    assertThatThrownBy(()->service.reserve(request(),model,"FREE_CHAIN_EXHAUSTED")).isInstanceOfSatisfying(AiProviderException.class,e->assertThat(e.code()).isEqualTo("PAID_BUDGET_EXHAUSTED"));
    assertThat(jdbc.sql("SELECT count(*) FROM ai_paid_request_accounting").query(Long.class).single()).isZero();
  }
  @Test void unknownOutcomeRemainsUnavailableAndSurvivesRestart(){
    var service=new OpenRouterPaidBudgetService(jdbc,true,new BigDecimal("0.10000000"));var reservation=service.reserve(request(),model,"FREE_CHAIN_EXHAUSTED");
    service.markUnknown(reservation,45_001,null,"AI_PROVIDER_HARD_TIMEOUT");
    var status=new OpenRouterPaidBudgetService(jdbc,true,new BigDecimal("0.10000000")).status(model,4096);
    assertThat(status).containsEntry("reservedUsd",new BigDecimal("0.00000000"))
        .containsEntry("unknownExposureUsd",reservation.estimatedCost())
        .containsEntry("remainingUsd",new BigDecimal("0.10000000").subtract(reservation.estimatedCost()));
    assertThat(jdbc.sql("SELECT status,reservation_state,reconciliation_state,outcome_classification FROM ai_paid_request_accounting WHERE id=:id")
        .param("id",reservation.id()).query().singleRow()).containsEntry("status","RECONCILIATION_PENDING")
        .containsEntry("reservation_state","EXPIRED_UNKNOWN").containsEntry("reconciliation_state","UNKNOWN")
        .containsEntry("outcome_classification","OUTCOME_UNKNOWN");
  }
  @Test void authoritativeProviderCostReconcilesUnknownExposure(){
    var service=new OpenRouterPaidBudgetService(jdbc,true,new BigDecimal("0.10000000"));var reservation=service.reserve(request(),model,"FREE_CHAIN_EXHAUSTED");service.markUnknown(reservation,45_001,"generation-1","AI_PROVIDER_HARD_TIMEOUT");
    service.reconcileUnknownCharged(reservation.id(),new BigDecimal("0.00400000"),100,50,5,"stop");var status=service.status(model,4096);
    assertThat(status).containsEntry("spentUsd",new BigDecimal("0.00400000")).containsEntry("unknownExposureUsd",new BigDecimal("0.00000000"));
    assertThat(jdbc.sql("SELECT status,reservation_state,reconciliation_state,outcome_classification FROM ai_paid_request_accounting WHERE id=:id").param("id",reservation.id()).query().singleRow())
        .containsEntry("status","RECONCILED_SUCCESS").containsEntry("reservation_state","RECONCILED_CHARGED").containsEntry("reconciliation_state","SUCCEEDED").containsEntry("outcome_classification","PROVIDER_COMPLETED_SUCCESS");
  }
  @Test void startupRecoveryMovesAnExpiredPaidLeaseToUnknownWithoutRetry(){
    var service=new OpenRouterPaidBudgetService(jdbc,true,new BigDecimal("0.10000000"));var reservation=service.reserve(request(),model,"FREE_CHAIN_EXHAUSTED");jdbc.sql("UPDATE ai_paid_request_accounting SET lease_expires_at=now()-interval '1 second' WHERE id=:id").param("id",reservation.id()).update();recovery.recoverExpired();var row=jdbc.sql("SELECT status,reservation_state,reconciliation_state FROM ai_paid_request_accounting WHERE id=:id").param("id",reservation.id()).query().singleRow();assertThat(row).containsEntry("status","RECONCILIATION_PENDING").containsEntry("reservation_state","EXPIRED_UNKNOWN").containsEntry("reconciliation_state","UNKNOWN");assertThat(service.status(model,4096)).containsEntry("unknownExposureUsd",reservation.estimatedCost());
  }
  private StructuredAiProvider.Request request(){return new StructuredAiProvider.Request("LESSON","system","prompt",Map.of("type","object"),4096,0,UUID.randomUUID(),"test",0,"correlation","checkpoint-key");}
}
