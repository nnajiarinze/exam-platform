CREATE TABLE knowledge_fact_evidence_provenance_correction (
  id UUID PRIMARY KEY,
  knowledge_fact_version_id UUID NOT NULL REFERENCES knowledge_fact_version(id),
  revision_number INTEGER NOT NULL,
  previous_correction_id UUID REFERENCES knowledge_fact_evidence_provenance_correction(id),
  source_revision_id VARCHAR(120) NOT NULL REFERENCES source_revision(id),
  source_section_id UUID NOT NULL REFERENCES source_section(id),
  source_section_checksum CHAR(64) NOT NULL,
  previous_source_evidence JSONB NOT NULL,
  corrected_source_evidence JSONB NOT NULL,
  corrected_normalized_evidence JSONB NOT NULL,
  page_start INTEGER,
  page_end INTEGER,
  raw_offsets JSONB NOT NULL,
  normalized_offsets JSONB NOT NULL,
  normalization_operations JSONB NOT NULL,
  correction_reason VARCHAR(100) NOT NULL,
  validator_version VARCHAR(80) NOT NULL,
  validation_status VARCHAR(20) NOT NULL,
  corrected_by VARCHAR(200) NOT NULL,
  human_verified BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_fact_evidence_correction_revision UNIQUE(knowledge_fact_version_id,revision_number),
  CONSTRAINT ck_fact_evidence_correction_checksum CHECK(source_section_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_fact_evidence_correction_reason CHECK(correction_reason='PDF_HYPHENATION_PROVENANCE_ALIGNMENT'),
  CONSTRAINT ck_fact_evidence_correction_validation CHECK(validation_status IN ('PASS','FAIL')),
  CONSTRAINT ck_fact_evidence_correction_not_human CHECK(NOT human_verified)
);

CREATE INDEX idx_fact_evidence_correction_current
  ON knowledge_fact_evidence_provenance_correction(knowledge_fact_version_id,revision_number DESC);
