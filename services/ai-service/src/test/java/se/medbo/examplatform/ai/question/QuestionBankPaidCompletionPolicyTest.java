package se.medbo.examplatform.ai.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class QuestionBankPaidCompletionPolicyTest {
  private static final UUID PLAN=UUID.fromString("6fe8b388-51a5-5abf-93e9-d4c57c59348b");
  @Test void enablesOnlyThePinnedPlanAndAutomationActor(){var jdbc=mock(JdbcClient.class,RETURNS_DEEP_STUBS);when(jdbc.sql(anyString()).param("target",PLAN).param("plan",PLAN).param("corpus","sverige-i-fokus-v1").query(Boolean.class).single()).thenReturn(true);var policy=new QuestionBankPaidCompletionPolicy(jdbc,true,"sverige-i-fokus-v1",PLAN.toString(),"automation","OPENROUTER_PAID","openai/gpt-oss-120b");assertThat(policy.target(PLAN,"automation")).isTrue();assertThat(policy.target(PLAN,"another-actor")).isFalse();}
  @Test void rejectsAnUnpinnedPaidConfiguration(){assertThatThrownBy(()->new QuestionBankPaidCompletionPolicy(mock(JdbcClient.class),true,"other",PLAN.toString(),"automation","OPENROUTER_PAID","openai/gpt-oss-120b")).isInstanceOf(IllegalStateException.class);}
}
