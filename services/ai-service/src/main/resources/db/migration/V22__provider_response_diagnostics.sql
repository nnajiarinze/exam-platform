ALTER TABLE ai_provider_attempt
  ADD COLUMN response_diagnostics jsonb,
  ADD COLUMN raw_response text;
