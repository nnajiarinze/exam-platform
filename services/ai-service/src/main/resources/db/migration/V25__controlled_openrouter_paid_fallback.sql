ALTER TABLE ai_provider_routing_decision DROP CONSTRAINT ck_ai_routing_policy;
ALTER TABLE ai_provider_routing_decision ADD CONSTRAINT ck_ai_routing_policy
  CHECK (billing_policy IN ('FREE_ONLY','FREE_FIRST_CAPPED_PAID'));

ALTER TABLE ai_provider_capacity_snapshot DROP CONSTRAINT ck_ai_capacity_policy;
ALTER TABLE ai_provider_capacity_snapshot ADD CONSTRAINT ck_ai_capacity_policy
  CHECK (billing_policy IN ('FREE_ONLY','FREE_FIRST_CAPPED_PAID'));

CREATE TABLE ai_openrouter_paid_model (
  singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
  model VARCHAR(200) NOT NULL,
  prompt_usd_per_token NUMERIC(20,12) NOT NULL,
  completion_usd_per_token NUMERIC(20,12) NOT NULL,
  reasoning_usd_per_token NUMERIC(20,12) NOT NULL DEFAULT 0,
  request_usd NUMERIC(20,12) NOT NULL DEFAULT 0,
  context_length BIGINT NOT NULL,
  supported_parameters JSONB NOT NULL,
  catalog_fingerprint VARCHAR(64) NOT NULL,
  discovered_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_ai_openrouter_paid_prices CHECK (
    prompt_usd_per_token > 0 AND completion_usd_per_token > 0
    AND reasoning_usd_per_token >= 0 AND request_usd >= 0
  ),
  CONSTRAINT ck_ai_openrouter_paid_context CHECK (context_length >= 32768)
);

CREATE TABLE ai_paid_budget (
  singleton BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
  configured_budget_usd NUMERIC(16,8) NOT NULL,
  spent_usd NUMERIC(16,8) NOT NULL DEFAULT 0,
  reserved_usd NUMERIC(16,8) NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_ai_paid_budget_non_negative CHECK (
    configured_budget_usd >= 0 AND spent_usd >= 0 AND reserved_usd >= 0
  ),
  CONSTRAINT ck_ai_paid_budget_never_exceeded CHECK (
    spent_usd + reserved_usd <= configured_budget_usd
  )
);

CREATE TABLE ai_paid_request_accounting (
  id UUID PRIMARY KEY,
  job_id UUID,
  operation VARCHAR(100) NOT NULL,
  provider VARCHAR(40) NOT NULL,
  model VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL,
  prompt_tokens INTEGER,
  completion_tokens INTEGER,
  reasoning_tokens INTEGER,
  latency_ms BIGINT,
  estimated_cost_usd NUMERIC(16,8) NOT NULL,
  actual_cost_usd NUMERIC(16,8),
  budget_before_usd NUMERIC(16,8) NOT NULL,
  budget_after_usd NUMERIC(16,8),
  routing_reason VARCHAR(120) NOT NULL,
  provider_request_id VARCHAR(300),
  error_code VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL,
  reconciled_at TIMESTAMPTZ,
  CONSTRAINT ck_ai_paid_accounting_status CHECK(status IN ('RESERVED','SUCCEEDED','FAILED')),
  CONSTRAINT ck_ai_paid_accounting_cost CHECK(
    estimated_cost_usd >= 0 AND (actual_cost_usd IS NULL OR actual_cost_usd >= 0)
  )
);

CREATE INDEX idx_ai_paid_accounting_recent
  ON ai_paid_request_accounting(created_at DESC);
