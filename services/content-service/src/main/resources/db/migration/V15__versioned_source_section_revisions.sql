CREATE TABLE source_revision (
  id VARCHAR(120) PRIMARY KEY,
  source_reference_id UUID NOT NULL REFERENCES source_reference(id),
  revision_number INTEGER NOT NULL,
  parent_revision_id VARCHAR(120),
  pdf_checksum CHAR(64) NOT NULL,
  parser_version VARCHAR(120) NOT NULL,
  correction_reason TEXT NOT NULL,
  review_status VARCHAR(30) NOT NULL,
  reviewer_actor VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(30) NOT NULL,
  CONSTRAINT uq_source_revision_number UNIQUE(source_reference_id,revision_number),
  CONSTRAINT ck_source_revision_checksum CHECK(pdf_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_source_revision_review CHECK(review_status IN ('UNREVIEWED','REVIEWED','REJECTED')),
  CONSTRAINT ck_source_revision_status CHECK(status IN ('ACTIVE','SUPERSEDED'))
);

INSERT INTO source_revision(
  id,source_reference_id,revision_number,parent_revision_id,pdf_checksum,parser_version,
  correction_reason,review_status,reviewer_actor,created_at,status
)
SELECT 'sverige-i-fokus-source-v1',id,1,NULL,file_checksum,'page-prefix-v1',
       'Original deterministic corpus extraction retained for historical provenance.',
       'REVIEWED','sverige-i-fokus-import',coalesce(imported_at,created_at),'ACTIVE'
FROM source_reference
WHERE file_checksum='39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada'
ON CONFLICT(id) DO NOTHING;

ALTER TABLE source_section
  ADD COLUMN logical_section_id UUID,
  ADD COLUMN source_revision_id VARCHAR(120) REFERENCES source_revision(id),
  ADD COLUMN extraction_start JSONB,
  ADD COLUMN extraction_end JSONB,
  ADD COLUMN boundary_reason VARCHAR(40);

UPDATE source_section
SET logical_section_id=id,
    source_revision_id='sverige-i-fokus-source-v1',
    extraction_start=jsonb_build_object('page',page_start,'offset',0),
    extraction_end=jsonb_build_object('page',page_end,'offset',length(exact_text)),
    boundary_reason='LEGACY_EXTRACTION'
WHERE source_revision_id IS NULL
  AND source_reference_id=(SELECT id FROM source_reference WHERE file_checksum='39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada');

ALTER TABLE source_section
  ADD CONSTRAINT uq_source_section_revision_logical UNIQUE(source_revision_id,logical_section_id),
  ADD CONSTRAINT ck_source_section_extraction_start CHECK(extraction_start ? 'page' AND extraction_start ? 'offset'),
  ADD CONSTRAINT ck_source_section_extraction_end CHECK(extraction_end ? 'page' AND extraction_end ? 'offset');

ALTER TABLE source_section
  DROP CONSTRAINT uq_source_section_code,
  DROP CONSTRAINT uq_source_section_order,
  DROP CONSTRAINT uq_source_section_checksum;

ALTER TABLE source_section
  ADD CONSTRAINT uq_source_section_revision_code UNIQUE(source_revision_id,code),
  ADD CONSTRAINT uq_source_section_revision_order UNIQUE(source_revision_id,display_order),
  ADD CONSTRAINT uq_source_section_revision_checksum UNIQUE(source_revision_id,section_checksum);

CREATE INDEX idx_source_section_revision_order ON source_section(source_revision_id,display_order);

CREATE TABLE source_revision_revalidation (
  id UUID PRIMARY KEY,
  entity_type VARCHAR(40) NOT NULL,
  entity_id UUID NOT NULL,
  old_source_section_id UUID NOT NULL REFERENCES source_section(id),
  new_source_section_id UUID NOT NULL REFERENCES source_section(id),
  classification VARCHAR(40) NOT NULL,
  old_checksum CHAR(64) NOT NULL,
  new_checksum CHAR(64) NOT NULL,
  validator_version VARCHAR(80) NOT NULL,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  reviewed_by VARCHAR(200) NOT NULL,
  reviewed_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_source_revalidation_entity UNIQUE(entity_type,entity_id,new_source_section_id),
  CONSTRAINT ck_source_revalidation_classification CHECK(classification IN (
    'UNAFFECTED','SOURCE_REVISION_UPDATED','AFFECTED_REQUIRES_REPAIR','INVALID_AFTER_BOUNDARY_CORRECTION'
  ))
);
CREATE INDEX idx_source_revalidation_new_section ON source_revision_revalidation(new_source_section_id,classification);
