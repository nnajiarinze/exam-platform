package se.medbo.examplatform.ai.question;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Owner-authorized routing override constrained to one immutable expansion plan. */
@Component
public class QuestionBankPaidCompletionPolicy {
  public static final String MODE="QUESTION_BANK_PAID_COMPLETION";
  public static final String ROUTING_REASON="OWNER_AUTHORIZED_PAID_COMPLETION";
  private final JdbcClient jdbc;private final boolean enabled;private final UUID planId;private final String corpusId,actor,provider,model;

  public QuestionBankPaidCompletionPolicy(JdbcClient jdbc,
      @Value("${ai.question-bank-paid-completion.enabled:false}")boolean enabled,
      @Value("${ai.question-bank-paid-completion.corpus-id:}")String corpusId,
      @Value("${ai.question-bank-paid-completion.plan-id:}")String planId,
      @Value("${ai.question-bank-paid-completion.actor:question-bank-paid-completion}")String actor,
      @Value("${ai.question-bank-paid-completion.provider:OPENROUTER_PAID}")String provider,
      @Value("${ai.question-bank-paid-completion.model:openai/gpt-oss-120b}")String model){
    this.jdbc=jdbc;this.enabled=enabled;this.corpusId=corpusId;this.planId=planId.isBlank()?null:UUID.fromString(planId);this.actor=actor;this.provider=provider;this.model=model;
    if(enabled&&(!"sverige-i-fokus-v1".equals(corpusId)||this.planId==null||!"OPENROUTER_PAID".equals(provider)||!"openai/gpt-oss-120b".equals(model)))throw new IllegalStateException("Paid completion mode must be pinned to the authorized corpus, plan, provider, and model");
  }
  public boolean target(UUID targetId,String requestedBy){
    if(!enabled||targetId==null||!actor.equals(requestedBy))return false;
    return jdbc.sql("SELECT count(*)=1 FROM ai_question_target_plan t JOIN ai_question_fact_density_audit a ON a.id=t.density_audit_id JOIN ai_question_bank_expansion_plan p ON p.id=a.expansion_plan_id WHERE t.id=:target AND p.id=:plan AND p.corpus_id=:corpus AND p.definition_checksum='047c40c6dd3ad2bfa6deb5b9ab9181e479512fbee83ab1ef75572d8f4bb6e4a9'")
        .param("target",targetId).param("plan",planId).param("corpus",corpusId).query(Boolean.class).single();
  }
  public boolean job(UUID jobId){
    if(!enabled||jobId==null)return false;
    return jdbc.sql("SELECT count(*)=1 FROM ai_question_target_plan t JOIN ai_question_fact_density_audit a ON a.id=t.density_audit_id JOIN ai_question_bank_expansion_plan p ON p.id=a.expansion_plan_id JOIN ai_generation_job j ON j.id=t.generation_job_id WHERE j.id=:job AND j.requested_by=:actor AND p.id=:plan AND p.corpus_id=:corpus")
        .param("job",jobId).param("actor",actor).param("plan",planId).param("corpus",corpusId).query(Boolean.class).single();
  }
  public String actor(){return actor;}public String provider(){return provider;}public String model(){return model;}
}
