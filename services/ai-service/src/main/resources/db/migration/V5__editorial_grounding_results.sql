ALTER TABLE ai_generation_job ADD COLUMN result_type VARCHAR(50);
ALTER TABLE ai_generation_job ADD CONSTRAINT ck_ai_editorial_result_type CHECK (
  result_type IS NULL OR result_type IN (
    'PROPOSALS_AVAILABLE','ALREADY_CLEAR','NO_MEANINGFUL_CHANGE','INSUFFICIENT_GROUNDED_EVIDENCE','FINDINGS_AVAILABLE'
  )
);

CREATE TABLE ai_editorial_validation_metric (
  metric_name VARCHAR(80) NOT NULL,
  operation_type VARCHAR(60) NOT NULL,
  prompt_version VARCHAR(100) NOT NULL,
  metric_count BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(metric_name,operation_type,prompt_version)
);
