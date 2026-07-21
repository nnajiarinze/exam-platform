ALTER TABLE knowledge_fact_ai_editorial_provenance DROP CONSTRAINT ck_ai_editorial_acceptance;
ALTER TABLE knowledge_fact_ai_editorial_provenance ADD CONSTRAINT ck_ai_editorial_acceptance CHECK(
  acceptance_action IN ('UPDATE_EXISTING_DRAFT','CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL')
);
ALTER TABLE knowledge_fact_ai_editorial_provenance ADD COLUMN sibling_resulting_fact_ids JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE ai_split_acceptance (
  id UUID PRIMARY KEY,
  generation_job_id UUID NOT NULL,
  target_fact_id UUID NOT NULL REFERENCES knowledge_fact(id),
  target_fact_version_id UUID NOT NULL REFERENCES knowledge_fact_version(id),
  acceptance_mode VARCHAR(60) NOT NULL,
  selected_proposal_ids JSONB NOT NULL,
  resulting_fact_ids JSONB NOT NULL,
  requested_by VARCHAR(200) NOT NULL,
  idempotency_key VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_ai_split_acceptance_mode CHECK(acceptance_mode='CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL'),
  CONSTRAINT uq_ai_split_acceptance_idempotency UNIQUE(requested_by,idempotency_key)
);
CREATE INDEX idx_ai_split_acceptance_target ON ai_split_acceptance(target_fact_id,created_at DESC);
CREATE INDEX idx_ai_split_acceptance_job ON ai_split_acceptance(generation_job_id);
