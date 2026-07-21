CREATE TABLE knowledge_fact_ai_editorial_provenance (
  id UUID PRIMARY KEY, knowledge_fact_version_id UUID NOT NULL REFERENCES knowledge_fact_version(id),
  operation_type VARCHAR(60) NOT NULL, generation_job_id UUID NOT NULL, proposal_id UUID NOT NULL UNIQUE,
  acceptance_action VARCHAR(40) NOT NULL, provider VARCHAR(60) NOT NULL, model VARCHAR(200) NOT NULL,
  prompt_version VARCHAR(100) NOT NULL, generated_at TIMESTAMPTZ NOT NULL,
  target_fact_ids JSONB NOT NULL, target_fact_version_ids JSONB NOT NULL, target_content_checksums JSONB NOT NULL,
  source_ids JSONB NOT NULL, source_checksums JSONB NOT NULL, original_content TEXT NOT NULL,
  original_content_checksum CHAR(64) NOT NULL,
  proposed_content TEXT NOT NULL, final_accepted_content TEXT NOT NULL, edited_before_acceptance BOOLEAN NOT NULL,
  edit_count INTEGER NOT NULL DEFAULT 0, edit_distance INTEGER, final_text_checksum CHAR(64) NOT NULL,
  requesting_user_id VARCHAR(200) NOT NULL, accepting_user_id VARCHAR(200) NOT NULL, accepted_at TIMESTAMPTZ NOT NULL,
  source_evidence JSONB NOT NULL, warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
  provider_request_id VARCHAR(300), input_tokens INTEGER, output_tokens INTEGER,
  CONSTRAINT ck_ai_editorial_acceptance CHECK(acceptance_action = 'UPDATE_EXISTING_DRAFT'),
  CONSTRAINT ck_ai_editorial_checksum CHECK(final_text_checksum ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_fact_editorial_provenance_fact ON knowledge_fact_ai_editorial_provenance(knowledge_fact_version_id);
CREATE INDEX idx_fact_editorial_provenance_job ON knowledge_fact_ai_editorial_provenance(generation_job_id);
