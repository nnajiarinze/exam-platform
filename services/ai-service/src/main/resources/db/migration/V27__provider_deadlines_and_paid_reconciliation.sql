ALTER TABLE ai_provider_attempt DROP CONSTRAINT ck_ai_provider_attempt_status;
ALTER TABLE ai_provider_attempt ALTER COLUMN status TYPE varchar(40);
ALTER TABLE ai_provider_attempt ADD CONSTRAINT ck_ai_provider_attempt_status
  CHECK(status IN ('STARTED','SUCCEEDED','FAILED','SKIPPED','RECONCILIATION_PENDING'));
ALTER TABLE ai_provider_attempt
  ADD COLUMN lifecycle_state varchar(40) NOT NULL DEFAULT 'RESERVED',
  ADD COLUMN heartbeat_at timestamptz,
  ADD COLUMN lease_expires_at timestamptz,
  ADD COLUMN worker_id varchar(200),
  ADD COLUMN process_instance_id varchar(200),
  ADD COLUMN request_payload_checksum char(64),
  ADD COLUMN request_idempotency_key varchar(300),
  ADD COLUMN parent_attempt_id uuid REFERENCES ai_provider_attempt(id),
  ADD COLUMN configured_deadline_ms bigint,
  ADD COLUMN cancellation_succeeded boolean,
  ADD COLUMN outcome_classification varchar(40),
  ADD COLUMN response_headers_received_at timestamptz,
  ADD COLUMN response_received_at timestamptz;
ALTER TABLE ai_provider_attempt ADD CONSTRAINT ck_ai_provider_attempt_lifecycle CHECK(lifecycle_state IN (
  'RESERVED','DISPATCHING','IN_FLIGHT','RESPONSE_RECEIVED','COMPLETED','FAILED_CONFIRMED',
  'TIMED_OUT_UNKNOWN','CANCELLED_CONFIRMED','RECONCILIATION_PENDING','RECONCILED_SUCCESS',
  'RECONCILED_FAILURE','ABANDONED_WITH_CHARGE_UNKNOWN'));

ALTER TABLE ai_paid_budget ADD COLUMN unknown_exposure_usd numeric(16,8) NOT NULL DEFAULT 0;
ALTER TABLE ai_paid_budget DROP CONSTRAINT ck_ai_paid_budget_non_negative;
ALTER TABLE ai_paid_budget DROP CONSTRAINT ck_ai_paid_budget_never_exceeded;
ALTER TABLE ai_paid_budget ADD CONSTRAINT ck_ai_paid_budget_non_negative CHECK(
  configured_budget_usd>=0 AND spent_usd>=0 AND reserved_usd>=0 AND unknown_exposure_usd>=0);
ALTER TABLE ai_paid_budget ADD CONSTRAINT ck_ai_paid_budget_never_exceeded CHECK(
  spent_usd+reserved_usd+unknown_exposure_usd<=configured_budget_usd);

ALTER TABLE ai_paid_request_accounting DROP CONSTRAINT ck_ai_paid_accounting_status;
ALTER TABLE ai_paid_request_accounting ALTER COLUMN status TYPE varchar(40);
ALTER TABLE ai_paid_request_accounting ADD CONSTRAINT ck_ai_paid_accounting_status CHECK(status IN (
  'RESERVED','SUCCEEDED','FAILED','RECONCILIATION_PENDING','RECONCILED_SUCCESS','RECONCILED_FAILURE','ABANDONED_WITH_CHARGE_UNKNOWN'));
ALTER TABLE ai_paid_request_accounting
  ADD COLUMN attempt_id uuid REFERENCES ai_provider_attempt(id),
  ADD COLUMN reservation_state varchar(40) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN reconciliation_state varchar(40) NOT NULL DEFAULT 'NOT_REQUIRED',
  ADD COLUMN lease_expires_at timestamptz,
  ADD COLUMN heartbeat_at timestamptz,
  ADD COLUMN owner_worker_id varchar(200),
  ADD COLUMN process_instance_id varchar(200),
  ADD COLUMN request_payload_checksum char(64),
  ADD COLUMN request_idempotency_key varchar(300),
  ADD COLUMN outcome_classification varchar(40);
ALTER TABLE ai_paid_request_accounting ADD CONSTRAINT ck_ai_paid_reservation_state CHECK(reservation_state IN (
  'ACTIVE','CONSUMED','RELEASED_NOT_SENT','RELEASED_CONFIRMED_FAILURE','RECONCILIATION_PENDING',
  'RECONCILED_CHARGED','RECONCILED_NOT_CHARGED','EXPIRED_UNKNOWN'));
ALTER TABLE ai_paid_request_accounting ADD CONSTRAINT ck_ai_paid_reconciliation_state CHECK(reconciliation_state IN (
  'NOT_REQUIRED','PENDING','SUCCEEDED','FAILED','UNKNOWN'));

ALTER TABLE ai_generation_job DROP CONSTRAINT ck_ai_job_status;
ALTER TABLE ai_generation_job ALTER COLUMN status TYPE varchar(40);
ALTER TABLE ai_generation_job ADD CONSTRAINT ck_ai_job_status CHECK(status IN (
  'QUEUED','RUNNING','RECONCILIATION_PENDING','COMPLETED','PARTIALLY_COMPLETED','FAILED','CANCELLED'));

CREATE INDEX ix_ai_attempt_expired_lease ON ai_provider_attempt(lifecycle_state,lease_expires_at);
CREATE INDEX ix_ai_paid_expired_lease ON ai_paid_request_accounting(reservation_state,lease_expires_at);

-- Preserve the verified unknown exposure from the pre-deadline OpenRouter request.
UPDATE ai_provider_attempt
SET status='RECONCILIATION_PENDING',lifecycle_state='RECONCILIATION_PENDING',
    heartbeat_at=started_at,lease_expires_at=started_at+interval '90 seconds',
    outcome_classification='OUTCOME_UNKNOWN',configured_deadline_ms=45000
WHERE id='50ecc531-6a13-4998-b5bb-2e006ebf505e' AND status='STARTED';

UPDATE ai_paid_request_accounting
SET status='RECONCILIATION_PENDING',reservation_state='EXPIRED_UNKNOWN',reconciliation_state='UNKNOWN',
    attempt_id='50ecc531-6a13-4998-b5bb-2e006ebf505e',heartbeat_at=created_at,
    lease_expires_at=created_at+interval '90 seconds',outcome_classification='OUTCOME_UNKNOWN'
WHERE id='0f07dc46-695b-47ce-a483-5d73e2b2ae9a' AND status='RESERVED';

UPDATE ai_paid_budget SET
  unknown_exposure_usd=unknown_exposure_usd+(SELECT estimated_cost_usd FROM ai_paid_request_accounting WHERE id='0f07dc46-695b-47ce-a483-5d73e2b2ae9a'),
  reserved_usd=reserved_usd-(SELECT estimated_cost_usd FROM ai_paid_request_accounting WHERE id='0f07dc46-695b-47ce-a483-5d73e2b2ae9a'),updated_at=now()
WHERE singleton=true AND EXISTS(SELECT 1 FROM ai_paid_request_accounting WHERE id='0f07dc46-695b-47ce-a483-5d73e2b2ae9a' AND reservation_state='EXPIRED_UNKNOWN');

UPDATE ai_generation_job SET status='RECONCILIATION_PENDING',error_code='AI_PROVIDER_HARD_TIMEOUT',
  error_message='Paid provider outcome is unknown and requires reconciliation',version=version+1
WHERE id='709f7548-7c4c-4081-a30c-5fe1b26ed99e' AND status='RUNNING';
