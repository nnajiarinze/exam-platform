ALTER TABLE ai_generation_job ADD COLUMN operation_type VARCHAR(60);
ALTER TABLE ai_generation_job ADD COLUMN target_context JSONB NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE ai_generation_job ADD COLUMN target_content_checksum CHAR(64);
ALTER TABLE ai_generation_job ADD CONSTRAINT ck_ai_job_operation CHECK(operation_type IS NULL OR operation_type IN ('REWRITE_FOR_CLARITY','SIMPLIFY_LANGUAGE'));
CREATE INDEX idx_ai_job_operation_status ON ai_generation_job(operation_type,status,created_at DESC);

CREATE TABLE ai_editorial_target (
  generation_job_id UUID NOT NULL REFERENCES ai_generation_job(id), target_fact_id UUID NOT NULL,
  target_fact_version_id UUID NOT NULL, target_version BIGINT NOT NULL, original_text VARCHAR(500) NOT NULL,
  content_checksum CHAR(64) NOT NULL, display_order INTEGER NOT NULL,
  PRIMARY KEY(generation_job_id,target_fact_id), CONSTRAINT uq_ai_editorial_target_order UNIQUE(generation_job_id,display_order)
);

CREATE TABLE ai_editorial_proposal (
  id UUID PRIMARY KEY, generation_job_id UUID NOT NULL REFERENCES ai_generation_job(id), operation_type VARCHAR(60) NOT NULL,
  target_fact_id UUID, original_text VARCHAR(500), proposed_text VARCHAR(500) NOT NULL, edited_text VARCHAR(500) NOT NULL,
  rationale VARCHAR(1000), source_evidence JSONB NOT NULL DEFAULT '[]'::jsonb, warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
  coverage JSONB NOT NULL DEFAULT '{}'::jsonb, confidence VARCHAR(20), status VARCHAR(20) NOT NULL,
  rejection_reason VARCHAR(500), resulting_fact_id UUID, resulting_fact_version_id UUID, accepted_by VARCHAR(200), accepted_at TIMESTAMPTZ,
  edit_count INTEGER NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0, normalized_text VARCHAR(500) NOT NULL,
  CONSTRAINT uq_ai_editorial_proposal_text UNIQUE(generation_job_id,normalized_text),
  CONSTRAINT ck_ai_editorial_proposal_status CHECK(status IN ('PROPOSED','EDITED','ACCEPTED','REJECTED','DISCARDED'))
);
CREATE INDEX idx_ai_editorial_proposal_job ON ai_editorial_proposal(generation_job_id,status,created_at);

CREATE TRIGGER audit_ai_editorial_proposal AFTER INSERT OR UPDATE ON ai_editorial_proposal FOR EACH ROW EXECUTE FUNCTION append_ai_editorial_audit();
