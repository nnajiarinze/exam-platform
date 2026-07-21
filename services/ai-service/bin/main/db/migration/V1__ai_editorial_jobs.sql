CREATE TABLE ai_generation_job (
  id UUID PRIMARY KEY, job_type VARCHAR(50) NOT NULL, source_id UUID NOT NULL,
  source_title VARCHAR(500) NOT NULL, source_content TEXT NOT NULL, source_content_checksum CHAR(64) NOT NULL,
  learning_objective_id UUID NOT NULL, learning_objective_title VARCHAR(500) NOT NULL,
  requested_by VARCHAR(200) NOT NULL, requested_count INTEGER NOT NULL, language VARCHAR(20) NOT NULL,
  editorial_instruction VARCHAR(1000), idempotency_key VARCHAR(200) NOT NULL,
  status VARCHAR(30) NOT NULL, cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE,
  provider VARCHAR(60) NOT NULL, model VARCHAR(200) NOT NULL, prompt_version VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL, started_at TIMESTAMPTZ, completed_at TIMESTAMPTZ,
  failed_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ, next_attempt_at TIMESTAMPTZ,
  retry_count INTEGER NOT NULL DEFAULT 0, input_character_count INTEGER NOT NULL,
  input_tokens INTEGER, output_tokens INTEGER, total_tokens INTEGER, reported_cost NUMERIC(14,6),
  provider_request_id VARCHAR(300), error_code VARCHAR(100), error_message VARCHAR(500), version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_ai_job_idempotency UNIQUE(requested_by,idempotency_key),
  CONSTRAINT ck_ai_job_status CHECK(status IN ('QUEUED','RUNNING','COMPLETED','PARTIALLY_COMPLETED','FAILED','CANCELLED')),
  CONSTRAINT ck_ai_job_count CHECK(requested_count BETWEEN 1 AND 20),
  CONSTRAINT ck_ai_job_checksum CHECK(source_content_checksum ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_ai_job_status_due ON ai_generation_job(status,next_attempt_at,created_at);
CREATE INDEX idx_ai_job_requester ON ai_generation_job(requested_by,created_at DESC);

CREATE TABLE ai_knowledge_fact_proposal (
  id UUID PRIMARY KEY, generation_job_id UUID NOT NULL REFERENCES ai_generation_job(id),
  source_id UUID NOT NULL, learning_objective_id UUID NOT NULL, proposed_text VARCHAR(500) NOT NULL,
  edited_text VARCHAR(500) NOT NULL, source_evidence JSONB NOT NULL, confidence VARCHAR(20), model_note VARCHAR(500),
  status VARCHAR(20) NOT NULL, rejection_reason VARCHAR(500), resulting_knowledge_fact_id UUID,
  accepted_by VARCHAR(200), accepted_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL, version BIGINT NOT NULL DEFAULT 0,
  normalized_text VARCHAR(500) NOT NULL,
  CONSTRAINT uq_ai_proposal_job_text UNIQUE(generation_job_id,normalized_text),
  CONSTRAINT ck_ai_proposal_status CHECK(status IN ('PROPOSED','EDITED','ACCEPTED','REJECTED','DISCARDED'))
);
CREATE INDEX idx_ai_proposal_job ON ai_knowledge_fact_proposal(generation_job_id,status,created_at);
CREATE UNIQUE INDEX uq_ai_proposal_accepted_fact ON ai_knowledge_fact_proposal(resulting_knowledge_fact_id) WHERE resulting_knowledge_fact_id IS NOT NULL;
