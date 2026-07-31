CREATE TABLE ai_question_batch_item_snapshot (
  id UUID PRIMARY KEY,
  batch_item_id UUID NOT NULL REFERENCES ai_question_generation_batch_item(id),
  snapshot_version INTEGER NOT NULL,
  target_snapshot JSONB NOT NULL,
  context_snapshot JSONB NOT NULL,
  generation_job_id UUID REFERENCES ai_generation_job(id),
  superseded_reason VARCHAR(120) NOT NULL,
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(batch_item_id,snapshot_version)
);
CREATE INDEX idx_ai_question_batch_snapshot_item ON ai_question_batch_item_snapshot(batch_item_id,created_at);

CREATE OR REPLACE FUNCTION populate_question_proposal_evidence_section()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.evidence_type = 'SOURCE' AND NEW.source_section_id IS NULL THEN
    SELECT s.source_section_id INTO NEW.source_section_id
    FROM ai_question_proposal p
    JOIN ai_question_generation_source s ON s.generation_job_id = p.generation_job_id
    WHERE p.id = NEW.proposal_id AND s.source_id = NEW.source_id
    ORDER BY s.display_order
    LIMIT 1;
  END IF;
  IF NEW.evidence_type = 'SOURCE' AND NEW.source_section_id IS NULL THEN
    RAISE EXCEPTION 'Source evidence requires an immutable Source Section identity';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_question_proposal_evidence_section
BEFORE INSERT ON ai_question_proposal_evidence
FOR EACH ROW EXECUTE FUNCTION populate_question_proposal_evidence_section();
