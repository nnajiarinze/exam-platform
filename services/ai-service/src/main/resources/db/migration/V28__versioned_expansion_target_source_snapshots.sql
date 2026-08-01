CREATE TABLE ai_question_target_source_snapshot_revision (
  id uuid PRIMARY KEY,
  target_id uuid NOT NULL REFERENCES ai_question_target_plan(id),
  revision_number integer NOT NULL,
  previous_revision_id uuid REFERENCES ai_question_target_source_snapshot_revision(id),
  historical_source_section_id uuid NOT NULL,
  historical_source_section_checksum char(64) NOT NULL,
  canonical_source_section_id uuid NOT NULL,
  canonical_source_section_checksum char(64) NOT NULL,
  canonical_source_revision_id varchar(120) NOT NULL,
  logical_section_id uuid NOT NULL,
  content_reconciliation_id uuid NOT NULL,
  classification varchar(40) NOT NULL,
  status varchar(20) NOT NULL,
  created_by varchar(200) NOT NULL,
  created_at timestamptz NOT NULL,
  CONSTRAINT uq_target_source_snapshot_revision UNIQUE(target_id,revision_number),
  CONSTRAINT uq_target_source_content_reconciliation UNIQUE(target_id,content_reconciliation_id),
  CONSTRAINT ck_target_source_snapshot_distinct CHECK(historical_source_section_id<>canonical_source_section_id),
  CONSTRAINT ck_target_source_snapshot_revision CHECK(canonical_source_revision_id='sverige-i-fokus-source-v2'),
  CONSTRAINT ck_target_source_snapshot_classification CHECK(classification='TARGET_REBIND_REQUIRED'),
  CONSTRAINT ck_target_source_snapshot_status CHECK(status IN ('ACTIVE','SUPERSEDED'))
);
CREATE UNIQUE INDEX uq_target_source_snapshot_one_active
  ON ai_question_target_source_snapshot_revision(target_id) WHERE status='ACTIVE';
CREATE INDEX ix_target_source_snapshot_canonical
  ON ai_question_target_source_snapshot_revision(canonical_source_section_id);
