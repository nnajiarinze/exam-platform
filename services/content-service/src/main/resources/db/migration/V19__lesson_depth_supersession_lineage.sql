ALTER TABLE lesson_draft
  ADD COLUMN supersedes_lesson_draft_id UUID REFERENCES lesson_draft(id),
  ADD COLUMN generation_plan_id UUID,
  ADD COLUMN revision_reason VARCHAR(80),
  ADD COLUMN human_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE lesson_draft
  ADD CONSTRAINT uq_lesson_draft_successor UNIQUE(supersedes_lesson_draft_id),
  ADD CONSTRAINT ck_lesson_revision_reason CHECK(revision_reason IS NULL OR revision_reason IN ('LESSON_DEPTH_EXPANSION')),
  ADD CONSTRAINT ck_lesson_supersession_metadata CHECK(
    (supersedes_lesson_draft_id IS NULL AND generation_plan_id IS NULL AND revision_reason IS NULL)
    OR (supersedes_lesson_draft_id IS NOT NULL AND generation_plan_id IS NOT NULL AND revision_reason='LESSON_DEPTH_EXPANSION'));

CREATE TABLE lesson_draft_supersession_mapping (
  id UUID PRIMARY KEY,
  predecessor_lesson_id UUID NOT NULL REFERENCES lesson_draft(id),
  successor_lesson_id UUID NOT NULL REFERENCES lesson_draft(id),
  predecessor_section_id UUID REFERENCES lesson_draft_section(id),
  successor_section_id UUID REFERENCES lesson_draft_section(id),
  mapping_type VARCHAR(30) NOT NULL,
  audit_snapshot JSONB NOT NULL,
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_section_supersession UNIQUE(successor_lesson_id,predecessor_section_id,successor_section_id),
  CONSTRAINT ck_lesson_section_mapping_type CHECK(mapping_type IN ('RETAINED','REORDERED','SUPERSEDED','SPLIT','MERGED','ADDED'))
);

CREATE OR REPLACE FUNCTION reject_lesson_supersession_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'lesson supersession mappings are immutable';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lesson_supersession_immutable
  BEFORE UPDATE OR DELETE ON lesson_draft_supersession_mapping
  FOR EACH ROW EXECUTE FUNCTION reject_lesson_supersession_mutation();

CREATE INDEX idx_lesson_draft_lineage ON lesson_draft(topic_id,version_number,supersedes_lesson_draft_id);
