CREATE TABLE ai_fact_density_audit (
  id UUID PRIMARY KEY,
  corpus_id VARCHAR(120) NOT NULL,
  source_revision_id VARCHAR(120) NOT NULL,
  definition_checksum CHAR(64) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'AUDITED',
  source_section_count INTEGER NOT NULL,
  existing_fact_count INTEGER NOT NULL,
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_fact_density_audit UNIQUE(corpus_id,source_revision_id,definition_checksum),
  CONSTRAINT ck_fact_density_audit_checksum CHECK(definition_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_fact_density_audit_counts CHECK(source_section_count>0 AND existing_fact_count>=0),
  CONSTRAINT ck_fact_density_audit_status CHECK(status IN ('AUDITED','GENERATING','VALIDATED','PARTIAL','BLOCKED'))
);

CREATE TABLE ai_fact_density_section_audit (
  id UUID PRIMARY KEY,
  fact_density_audit_id UUID NOT NULL REFERENCES ai_fact_density_audit(id),
  source_section_id UUID NOT NULL,
  source_section_checksum CHAR(64) NOT NULL,
  topic_id UUID NOT NULL,
  learning_objective_id UUID NOT NULL,
  classification VARCHAR(50) NOT NULL,
  normalized_character_count INTEGER NOT NULL,
  approved_fact_count INTEGER NOT NULL,
  evidence_covered_characters INTEGER NOT NULL,
  evidence_coverage_percentage NUMERIC(7,3) NOT NULL,
  maximum_safe_additional_fact_count INTEGER NOT NULL,
  audit_snapshot JSONB NOT NULL,
  audit_checksum CHAR(64) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'AUDITED',
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_fact_density_section UNIQUE(fact_density_audit_id,source_section_id),
  CONSTRAINT ck_fact_density_section_checksum CHECK(source_section_checksum ~ '^[a-f0-9]{64}$' AND audit_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_fact_density_section_counts CHECK(normalized_character_count>0 AND approved_fact_count>=0
    AND evidence_covered_characters>=0 AND maximum_safe_additional_fact_count>=0),
  CONSTRAINT ck_fact_density_section_coverage CHECK(evidence_coverage_percentage BETWEEN 0 AND 100),
  CONSTRAINT ck_fact_density_section_class CHECK(classification IN ('FULLY_REPRESENTED','UNDERUTILIZED_ONE_FACT',
    'UNDERUTILIZED_TWO_FACTS','UNDERUTILIZED_THREE_OR_MORE_FACTS','SOURCE_TOO_THIN','SOURCE_STRUCTURALLY_COMPLEX')),
  CONSTRAINT ck_fact_density_section_status CHECK(status IN ('AUDITED','GENERATION_QUEUED','GENERATED','VALIDATED','PARTIAL','BLOCKED'))
);

CREATE TABLE ai_fact_density_teaching_concept (
  id UUID PRIMARY KEY,
  section_audit_id UUID NOT NULL REFERENCES ai_fact_density_section_audit(id),
  concept_order INTEGER NOT NULL,
  classification VARCHAR(50) NOT NULL,
  concept_text TEXT NOT NULL,
  exact_evidence TEXT,
  semantic_fingerprint CHAR(64) NOT NULL,
  generation_eligible BOOLEAN NOT NULL,
  diagnostic VARCHAR(500) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_fact_density_concept_order UNIQUE(section_audit_id,concept_order),
  CONSTRAINT uq_fact_density_concept_fingerprint UNIQUE(section_audit_id,semantic_fingerprint),
  CONSTRAINT ck_fact_density_concept_order CHECK(concept_order>=0),
  CONSTRAINT ck_fact_density_concept_fingerprint CHECK(semantic_fingerprint ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_fact_density_concept_class CHECK(classification IN ('REPRESENTED_BY_APPROVED_FACT',
    'EXPLICIT_IN_SOURCE_BUT_NOT_FACT','SUPPORTING_CONTEXT_ONLY','DUPLICATE','UNSUPPORTED','BELONGS_TO_ANOTHER_TOPIC'))
);

CREATE OR REPLACE FUNCTION reject_fact_density_audit_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'fact-density audit snapshots are immutable; create a new deterministic audit';
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_fact_density_audit_immutable BEFORE UPDATE OR DELETE ON ai_fact_density_audit
  FOR EACH ROW EXECUTE FUNCTION reject_fact_density_audit_mutation();
CREATE TRIGGER trg_fact_density_section_immutable BEFORE UPDATE OR DELETE ON ai_fact_density_section_audit
  FOR EACH ROW EXECUTE FUNCTION reject_fact_density_audit_mutation();
CREATE TRIGGER trg_fact_density_concept_immutable BEFORE UPDATE OR DELETE ON ai_fact_density_teaching_concept
  FOR EACH ROW EXECUTE FUNCTION reject_fact_density_audit_mutation();

CREATE INDEX idx_fact_density_section_classification
  ON ai_fact_density_section_audit(fact_density_audit_id,classification,source_section_id);
CREATE INDEX idx_fact_density_concept_generation
  ON ai_fact_density_teaching_concept(section_audit_id,generation_eligible,concept_order);
ALTER TABLE ai_generation_job ALTER COLUMN editorial_instruction TYPE TEXT;
