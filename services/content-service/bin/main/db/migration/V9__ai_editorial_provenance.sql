ALTER TABLE source_reference ADD COLUMN content_text TEXT;
ALTER TABLE source_reference ADD COLUMN content_checksum CHAR(64);
ALTER TABLE source_reference ADD CONSTRAINT ck_source_content_checksum CHECK(content_checksum IS NULL OR content_checksum ~ '^[a-f0-9]{64}$');

CREATE TABLE knowledge_fact_ai_provenance (
  knowledge_fact_version_id UUID PRIMARY KEY REFERENCES knowledge_fact_version(id),
  generation_job_id UUID NOT NULL, proposal_id UUID NOT NULL UNIQUE, provider VARCHAR(60) NOT NULL,
  model VARCHAR(200) NOT NULL, prompt_version VARCHAR(100) NOT NULL, generated_at TIMESTAMPTZ NOT NULL,
  source_reference_id UUID NOT NULL REFERENCES source_reference(id), source_content_checksum CHAR(64) NOT NULL,
  learning_objective_id UUID NOT NULL REFERENCES learning_objective(id), requesting_user_id VARCHAR(200) NOT NULL,
  original_proposed_text VARCHAR(500) NOT NULL, final_accepted_text VARCHAR(500) NOT NULL,
  accepting_user_id VARCHAR(200) NOT NULL, accepted_at TIMESTAMPTZ NOT NULL,
  confidence VARCHAR(20), source_evidence JSONB NOT NULL, provider_request_id VARCHAR(300),
  input_tokens INTEGER, output_tokens INTEGER,
  CONSTRAINT ck_fact_ai_checksum CHECK(source_content_checksum ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_fact_ai_job ON knowledge_fact_ai_provenance(generation_job_id);
CREATE INDEX idx_fact_ai_source ON knowledge_fact_ai_provenance(source_reference_id);
