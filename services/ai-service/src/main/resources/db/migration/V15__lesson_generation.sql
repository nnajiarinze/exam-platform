CREATE TABLE ai_lesson_generation_job (
  id UUID PRIMARY KEY,
  topic_id UUID NOT NULL,
  learning_objective_id UUID NOT NULL,
  source_section_id UUID NOT NULL,
  input_snapshot JSONB NOT NULL,
  requested_by VARCHAR(200) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  language VARCHAR(10) NOT NULL,
  status VARCHAR(30) NOT NULL,
  provider VARCHAR(50) NOT NULL,
  model VARCHAR(120) NOT NULL,
  prompt_version VARCHAR(100) NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  input_tokens INTEGER,
  output_tokens INTEGER,
  provider_request_id VARCHAR(200),
  error_code VARCHAR(100),
  error_message VARCHAR(500),
  next_attempt_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  failed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_ai_lesson_job_idempotency UNIQUE(requested_by,idempotency_key),
  CONSTRAINT ck_ai_lesson_job_status CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED'))
);

CREATE TABLE ai_lesson_proposal (
  id UUID PRIMARY KEY,
  generation_job_id UUID NOT NULL UNIQUE REFERENCES ai_lesson_generation_job(id),
  title TEXT NOT NULL,
  fact_statements JSONB NOT NULL,
  key_terms JSONB NOT NULL,
  status VARCHAR(30) NOT NULL,
  automated_classification VARCHAR(40) NOT NULL,
  validation_gates JSONB NOT NULL,
  accepted_lesson_draft_id UUID,
  accepted_by VARCHAR(200),
  accepted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_ai_lesson_proposal_status CHECK(status IN ('PROPOSED','ACCEPTED','REJECTED'))
);

CREATE INDEX idx_ai_lesson_job_work
  ON ai_lesson_generation_job(status,next_attempt_at,created_at);
CREATE INDEX idx_ai_lesson_job_topic
  ON ai_lesson_generation_job(topic_id,created_at DESC);
