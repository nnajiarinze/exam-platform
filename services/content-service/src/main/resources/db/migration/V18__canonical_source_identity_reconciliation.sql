CREATE UNIQUE INDEX IF NOT EXISTS uq_source_revision_one_active
  ON source_revision(source_reference_id) WHERE status='ACTIVE';

CREATE TABLE source_identity_reconciliation (
  id uuid PRIMARY KEY,
  entity_type varchar(40) NOT NULL,
  entity_id uuid NOT NULL,
  entity_version_id uuid NOT NULL,
  corpus_id varchar(120) NOT NULL,
  logical_section_id uuid NOT NULL,
  historical_source_revision_id varchar(120) NOT NULL REFERENCES source_revision(id),
  historical_source_section_id uuid NOT NULL REFERENCES source_section(id),
  historical_checksum char(64) NOT NULL,
  canonical_source_revision_id varchar(120) NOT NULL REFERENCES source_revision(id),
  canonical_source_section_id uuid NOT NULL REFERENCES source_section(id),
  canonical_checksum char(64) NOT NULL,
  evidence_match_result varchar(40) NOT NULL,
  page_offset_comparison jsonb NOT NULL,
  classification varchar(50) NOT NULL,
  reconciled_by varchar(200) NOT NULL,
  human_verified boolean NOT NULL DEFAULT false,
  reconciled_at timestamptz NOT NULL,
  CONSTRAINT uq_source_identity_reconciliation UNIQUE(entity_type,entity_version_id,canonical_source_section_id),
  CONSTRAINT ck_source_identity_distinct CHECK(historical_source_section_id<>canonical_source_section_id),
  CONSTRAINT ck_source_identity_evidence CHECK(evidence_match_result IN ('EXACT_NORMALIZED_MATCH','REVALIDATED_EVIDENCE_MATCH','EVIDENCE_ABSENT')),
  CONSTRAINT ck_source_identity_classification CHECK(classification IN ('SAME_LOGICAL_SECTION_DIFFERENT_VERSION','STALE_FACT_PROVENANCE','INVALID_MAPPING'))
);
CREATE INDEX idx_source_identity_historical ON source_identity_reconciliation(historical_source_section_id);
CREATE INDEX idx_source_identity_canonical ON source_identity_reconciliation(canonical_source_section_id);

CREATE TABLE learning_objective_source_mapping_revision (
  id uuid PRIMARY KEY,
  learning_objective_id uuid NOT NULL REFERENCES learning_objective(id),
  revision_number integer NOT NULL,
  previous_revision_id uuid REFERENCES learning_objective_source_mapping_revision(id),
  source_section_id uuid NOT NULL REFERENCES source_section(id),
  source_revision_id varchar(120) NOT NULL REFERENCES source_revision(id),
  logical_section_id uuid NOT NULL,
  reason varchar(120) NOT NULL,
  status varchar(20) NOT NULL,
  created_by varchar(200) NOT NULL,
  created_at timestamptz NOT NULL,
  CONSTRAINT uq_objective_source_mapping_revision UNIQUE(learning_objective_id,revision_number),
  CONSTRAINT ck_objective_source_mapping_status CHECK(status IN ('ACTIVE','SUPERSEDED'))
);
CREATE UNIQUE INDEX uq_objective_source_mapping_one_active
  ON learning_objective_source_mapping_revision(learning_objective_id) WHERE status='ACTIVE';

INSERT INTO learning_objective_source_mapping_revision(
  id,learning_objective_id,revision_number,source_section_id,source_revision_id,
  logical_section_id,reason,status,created_by,created_at)
SELECT gen_random_uuid(),m.learning_objective_id,1,ss.id,ss.source_revision_id,
       ss.logical_section_id,'CANONICAL_SOURCE_V2_MAPPING_BASELINE','ACTIVE',
       'source-identity-reconciliation-v1',clock_timestamp()
FROM learning_objective_source_section m
JOIN source_section ss ON ss.id=m.source_section_id
JOIN source_revision sr ON sr.id=ss.source_revision_id AND sr.status='ACTIVE'
ON CONFLICT(learning_objective_id,revision_number) DO NOTHING;

INSERT INTO source_identity_reconciliation(
  id,entity_type,entity_id,entity_version_id,corpus_id,logical_section_id,
  historical_source_revision_id,historical_source_section_id,historical_checksum,
  canonical_source_revision_id,canonical_source_section_id,canonical_checksum,
  evidence_match_result,page_offset_comparison,classification,reconciled_by,
  human_verified,reconciled_at)
SELECT gen_random_uuid(),'KNOWLEDGE_FACT',k.id,v.id,e.code,old.logical_section_id,
       old.source_revision_id,old.id,old.section_checksum,new.source_revision_id,new.id,new.section_checksum,
       CASE WHEN old.normalized_text=new.normalized_text THEN 'EXACT_NORMALIZED_MATCH'
            WHEN EXISTS(SELECT 1 FROM source_revision_revalidation r
                        WHERE r.entity_type='KNOWLEDGE_FACT' AND r.entity_id=k.id
                          AND r.old_source_section_id=old.id AND r.new_source_section_id=new.id
                          AND r.classification='SOURCE_REVISION_UPDATED')
              THEN 'REVALIDATED_EVIDENCE_MATCH' ELSE 'EVIDENCE_ABSENT' END,
       jsonb_build_object('historicalPages',jsonb_build_array(old.page_start,old.page_end),
                          'canonicalPages',jsonb_build_array(new.page_start,new.page_end),
                          'historicalStart',old.extraction_start,'historicalEnd',old.extraction_end,
                          'canonicalStart',new.extraction_start,'canonicalEnd',new.extraction_end),
       CASE WHEN old.normalized_text=new.normalized_text OR EXISTS(
              SELECT 1 FROM source_revision_revalidation r WHERE r.entity_type='KNOWLEDGE_FACT'
                AND r.entity_id=k.id AND r.old_source_section_id=old.id
                AND r.new_source_section_id=new.id AND r.classification='SOURCE_REVISION_UPDATED')
            THEN 'SAME_LOGICAL_SECTION_DIFFERENT_VERSION' ELSE 'INVALID_MAPPING' END,
       'source-identity-reconciliation-v1',false,clock_timestamp()
FROM knowledge_fact k
JOIN knowledge_fact_version v ON v.id=k.current_version_id
JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=v.id
JOIN learning_objective lo ON lo.id=k.learning_objective_id
JOIN topic t ON t.id=lo.topic_id
JOIN subject sub ON sub.id=t.subject_id
JOIN exam_version ev ON ev.id=sub.exam_version_id
JOIN exam e ON e.id=ev.exam_id
JOIN source_section old ON old.id=p.source_section_id
JOIN source_revision old_revision ON old_revision.id=old.source_revision_id AND old_revision.status='SUPERSEDED'
JOIN source_section new ON new.logical_section_id=old.logical_section_id
JOIN source_revision new_revision ON new_revision.id=new.source_revision_id AND new_revision.status='ACTIVE'
WHERE k.status='ACTIVE' AND k.review_status='APPROVED' AND v.review_status='APPROVED'
ON CONFLICT(entity_type,entity_version_id,canonical_source_section_id) DO NOTHING;

CREATE OR REPLACE VIEW canonical_knowledge_fact_source AS
SELECT p.knowledge_fact_version_id,p.source_reference_id,
       p.source_section_id historical_source_section_id,
       coalesce(r.canonical_source_section_id,p.source_section_id) canonical_source_section_id,
       coalesce(r.canonical_source_revision_id,ss.source_revision_id) canonical_source_revision_id,
       coalesce(r.logical_section_id,ss.logical_section_id) logical_section_id,
       r.id reconciliation_id,r.evidence_match_result,r.classification
FROM knowledge_fact_ai_provenance p
JOIN source_section ss ON ss.id=p.source_section_id
LEFT JOIN source_identity_reconciliation r
  ON r.entity_type='KNOWLEDGE_FACT' AND r.entity_version_id=p.knowledge_fact_version_id
 AND r.classification<>'INVALID_MAPPING';

CREATE OR REPLACE FUNCTION enforce_canonical_objective_source_mapping() RETURNS trigger AS $$
BEGIN
  IF EXISTS (SELECT 1 FROM source_section candidate JOIN source_revision any_revision
             ON any_revision.source_reference_id=candidate.source_reference_id
             WHERE candidate.id=NEW.source_section_id)
     AND NOT EXISTS (
       SELECT 1 FROM source_section ss JOIN source_revision sr ON sr.id=ss.source_revision_id
       WHERE ss.id=NEW.source_section_id AND sr.status='ACTIVE'
     ) THEN RAISE EXCEPTION 'Objective mappings must reference the canonical active Source Section'; END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER canonical_objective_source_mapping
  BEFORE INSERT OR UPDATE ON learning_objective_source_section
  FOR EACH ROW EXECUTE FUNCTION enforce_canonical_objective_source_mapping();
