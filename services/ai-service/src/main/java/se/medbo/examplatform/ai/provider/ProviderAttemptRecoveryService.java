package se.medbo.examplatform.ai.provider;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Converts abandoned paid leases into explicit unknown exposure; it never retries them. */
@Service
class ProviderAttemptRecoveryService {
  private final JdbcClient jdbc;
  ProviderAttemptRecoveryService(JdbcClient jdbc){this.jdbc=jdbc;}

  @EventListener(ApplicationReadyEvent.class) public void onReady(){recoverExpired();}
  @Scheduled(fixedDelayString="${ai.provider.recovery-interval-ms:30000}") public void scheduled(){recoverExpired();}

  @Transactional public void recoverExpired(){
    var ids=jdbc.sql("SELECT id FROM ai_paid_request_accounting WHERE reservation_state='ACTIVE' AND lease_expires_at<:now FOR UPDATE SKIP LOCKED")
        .param("now",now()).query(UUID.class).list();
    for(UUID id:ids){
      var row=jdbc.sql("SELECT estimated_cost_usd,attempt_id,job_id FROM ai_paid_request_accounting WHERE id=:id AND reservation_state='ACTIVE' FOR UPDATE")
          .param("id",id).query().listOfRows();
      if(row.isEmpty())continue;var value=row.getFirst();
      jdbc.sql("UPDATE ai_paid_budget SET reserved_usd=reserved_usd-:cost,unknown_exposure_usd=unknown_exposure_usd+:cost,updated_at=:now WHERE singleton=true")
          .param("cost",value.get("estimated_cost_usd")).param("now",now()).update();
      jdbc.sql("UPDATE ai_paid_request_accounting SET status='RECONCILIATION_PENDING',reservation_state='EXPIRED_UNKNOWN',reconciliation_state='UNKNOWN',outcome_classification='OUTCOME_UNKNOWN',error_code='AI_PROVIDER_LEASE_EXPIRED',heartbeat_at=:now,reconciled_at=:now WHERE id=:id AND reservation_state='ACTIVE'")
          .param("now",now()).param("id",id).update();
      Object attempt=value.get("attempt_id");if(attempt!=null)jdbc.sql("UPDATE ai_provider_attempt SET status='RECONCILIATION_PENDING',lifecycle_state='RECONCILIATION_PENDING',outcome_classification='OUTCOME_UNKNOWN',error_code='AI_PROVIDER_LEASE_EXPIRED',heartbeat_at=:now,completed_at=:now WHERE id=:id AND lifecycle_state IN ('RESERVED','DISPATCHING','IN_FLIGHT')")
          .param("now",now()).param("id",attempt).update();
      Object job=value.get("job_id");if(job!=null)jdbc.sql("UPDATE ai_generation_job SET status='RECONCILIATION_PENDING',next_attempt_at=NULL,error_code='AI_PROVIDER_LEASE_EXPIRED',error_message='Paid provider outcome is unknown and requires reconciliation',version=version+1 WHERE id=:id AND status='RUNNING'")
          .param("id",job).update();
    }
  }
  private OffsetDateTime now(){return OffsetDateTime.now(ZoneOffset.UTC);}
}
