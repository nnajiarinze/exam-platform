ALTER TABLE ai_provider_circuit
  ADD COLUMN opened_at TIMESTAMPTZ,
  ADD COLUMN last_failure_at TIMESTAMPTZ,
  ADD COLUMN last_provider_error VARCHAR(100),
  ADD COLUMN last_successful_request TIMESTAMPTZ,
  ADD COLUMN opened_by VARCHAR(200),
  ADD COLUMN manually_overridden_by VARCHAR(200);

