CREATE TABLE ai_quota_profile (
  id UUID PRIMARY KEY, provider VARCHAR(30) NOT NULL, model VARCHAR(200) NOT NULL,
  project_label VARCHAR(200) NOT NULL, usage_mode VARCHAR(20) NOT NULL,
  rpm_limit INTEGER NOT NULL, tpm_limit INTEGER NOT NULL, rpd_limit INTEGER NOT NULL,
  input_tokens_day_limit BIGINT, output_tokens_day_limit BIGINT,
  warning_percent INTEGER NOT NULL, critical_percent INTEGER NOT NULL, stop_percent INTEGER NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_ai_quota_mode CHECK (usage_mode IN ('FREE_ONLY','PAID_ALLOWED','DISABLED')),
  CONSTRAINT ck_ai_quota_limits CHECK (rpm_limit > 0 AND tpm_limit > 0 AND rpd_limit > 0),
  CONSTRAINT ck_ai_quota_thresholds CHECK (warning_percent > 0 AND warning_percent < critical_percent AND critical_percent < stop_percent AND stop_percent <= 100)
);
CREATE UNIQUE INDEX uq_ai_quota_active ON ai_quota_profile(provider,model) WHERE active;

CREATE TABLE ai_model_price_profile (
  id UUID PRIMARY KEY, provider VARCHAR(30) NOT NULL, model VARCHAR(200) NOT NULL,
  version VARCHAR(80) NOT NULL, input_usd_per_million NUMERIC(16,8) NOT NULL,
  output_usd_per_million NUMERIC(16,8) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
  effective_from TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_ai_price_non_negative CHECK (input_usd_per_million >= 0 AND output_usd_per_million >= 0)
);
CREATE UNIQUE INDEX uq_ai_model_price_active ON ai_model_price_profile(provider,model) WHERE active;

CREATE TABLE ai_quota_reservation (
  id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES ai_quota_profile(id), job_id UUID REFERENCES ai_generation_job(id),
  reserved_requests INTEGER NOT NULL DEFAULT 1, reserved_input_tokens INTEGER NOT NULL,
  actual_input_tokens INTEGER, actual_output_tokens INTEGER, status VARCHAR(20) NOT NULL,
  provider_request_id VARCHAR(300), error_category VARCHAR(80), created_at TIMESTAMPTZ NOT NULL,
  operation VARCHAR(80), requester VARCHAR(200), retry_attempt INTEGER NOT NULL DEFAULT 0,
  http_status INTEGER, quota_category VARCHAR(80), estimated_cost_usd NUMERIC(16,8), actual_cost_usd NUMERIC(16,8),
  reconciled_at TIMESTAMPTZ,
  CONSTRAINT ck_ai_reservation_status CHECK (status IN ('RESERVED','SUCCEEDED','FAILED','RELEASED'))
);
CREATE INDEX idx_ai_quota_reservation_window ON ai_quota_reservation(profile_id,created_at,status);
CREATE INDEX idx_ai_quota_reservation_job ON ai_quota_reservation(job_id) WHERE job_id IS NOT NULL;

CREATE TABLE ai_provider_circuit (
  profile_id UUID PRIMARY KEY REFERENCES ai_quota_profile(id), state VARCHAR(30) NOT NULL,
  reason VARCHAR(100), paused_until TIMESTAMPTZ, manually_disabled BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_ai_provider_circuit_state CHECK (state IN ('CLOSED','WARNING','CRITICAL','RATE_LIMITED','QUOTA_PAUSED','BILLING_SAFETY_PAUSED','MANUALLY_DISABLED','CONFIGURATION_INVALID'))
);

CREATE TABLE ai_provider_alert (
  id UUID PRIMARY KEY, profile_id UUID NOT NULL REFERENCES ai_quota_profile(id), alert_key VARCHAR(200) NOT NULL,
  severity VARCHAR(20) NOT NULL, code VARCHAR(100) NOT NULL, message VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, acknowledged_at TIMESTAMPTZ, acknowledged_by VARCHAR(200),
  CONSTRAINT uq_ai_provider_alert_key UNIQUE(profile_id,alert_key)
);
CREATE INDEX idx_ai_provider_alert_open ON ai_provider_alert(profile_id,created_at DESC) WHERE acknowledged_at IS NULL;
