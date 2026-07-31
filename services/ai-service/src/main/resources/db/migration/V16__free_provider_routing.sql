CREATE TABLE ai_provider_attempt (
  id UUID PRIMARY KEY,
  job_id UUID,
  operation VARCHAR(100) NOT NULL,
  attempt_number INTEGER NOT NULL,
  provider VARCHAR(40) NOT NULL,
  model VARCHAR(200) NOT NULL,
  status VARCHAR(30) NOT NULL,
  confirmed_free BOOLEAN NOT NULL,
  free_verification_source VARCHAR(40) NOT NULL,
  provider_request_id VARCHAR(300),
  input_tokens INTEGER,
  output_tokens INTEGER,
  latency_ms BIGINT,
  finish_reason VARCHAR(100),
  error_code VARCHAR(100),
  error_message VARCHAR(500),
  fallback_reason VARCHAR(100),
  retry_after TIMESTAMPTZ,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  CONSTRAINT ck_ai_provider_attempt_status CHECK(status IN ('STARTED','SUCCEEDED','FAILED','SKIPPED')),
  CONSTRAINT uq_ai_provider_attempt UNIQUE(job_id,operation,attempt_number)
);

CREATE TABLE ai_provider_routing_decision (
  id UUID PRIMARY KEY,
  job_id UUID,
  operation VARCHAR(100) NOT NULL,
  billing_policy VARCHAR(30) NOT NULL,
  providers_evaluated JSONB NOT NULL,
  selected_provider VARCHAR(40),
  selected_model VARCHAR(200),
  outcome VARCHAR(30) NOT NULL,
  next_retry_at TIMESTAMPTZ,
  routed_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_ai_routing_policy CHECK(billing_policy='FREE_ONLY'),
  CONSTRAINT ck_ai_routing_outcome CHECK(outcome IN ('SELECTED','PAUSED'))
);

CREATE TABLE ai_provider_capacity_snapshot (
  id UUID PRIMARY KEY,
  provider VARCHAR(40) NOT NULL,
  model VARCHAR(200) NOT NULL,
  billing_policy VARCHAR(30) NOT NULL,
  free_status VARCHAR(20) NOT NULL,
  authority VARCHAR(30) NOT NULL,
  request_limit BIGINT,
  requests_used BIGINT,
  requests_remaining BIGINT,
  token_limit BIGINT,
  tokens_used BIGINT,
  tokens_remaining BIGINT,
  neuron_limit BIGINT,
  neurons_used BIGINT,
  neurons_remaining BIGINT,
  reset_at TIMESTAMPTZ,
  retry_after TIMESTAMPTZ,
  circuit_state VARCHAR(40) NOT NULL,
  last_error VARCHAR(200),
  refreshed_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_ai_capacity_policy CHECK(billing_policy='FREE_ONLY'),
  CONSTRAINT ck_ai_capacity_free_status CHECK(free_status IN ('KNOWN','ESTIMATED','UNKNOWN')),
  CONSTRAINT ck_ai_capacity_authority CHECK(authority IN ('PROVIDER_HEADER','PROVIDER_API','CONFIGURATION','LOCAL_ESTIMATE','ERROR_RESPONSE'))
);

CREATE INDEX idx_ai_provider_attempt_recent ON ai_provider_attempt(started_at DESC);
CREATE INDEX idx_ai_routing_recent ON ai_provider_routing_decision(routed_at DESC);
CREATE INDEX idx_ai_capacity_recent ON ai_provider_capacity_snapshot(provider,model,refreshed_at DESC);

-- Provider attempts can belong to any of the persistent generation job types.
ALTER TABLE ai_quota_reservation DROP CONSTRAINT IF EXISTS ai_quota_reservation_job_id_fkey;
