CREATE TABLE source_payload_revision (
  id uuid PRIMARY KEY,
  logical_source_key varchar(240) NOT NULL,
  historical_shared_id uuid NOT NULL REFERENCES source_reference(id),
  materialized_source_reference_id uuid NOT NULL UNIQUE REFERENCES source_reference(id),
  source_revision_id varchar(120) NOT NULL REFERENCES source_revision(id),
  payload_role varchar(20) NOT NULL,
  document_checksum char(64) NOT NULL,
  content_checksum char(64) NOT NULL,
  parser_version varchar(120) NOT NULL,
  extraction_version varchar(120) NOT NULL,
  page_start integer NOT NULL,
  page_end integer NOT NULL,
  extraction_start jsonb NOT NULL,
  extraction_end jsonb NOT NULL,
  normalized_length integer NOT NULL,
  origin varchar(160) NOT NULL,
  status varchar(20) NOT NULL,
  created_by varchar(200) NOT NULL,
  created_at timestamptz NOT NULL,
  CONSTRAINT uq_source_payload_immutable UNIQUE(logical_source_key,source_revision_id,parser_version,extraction_version,page_start,page_end,content_checksum),
  CONSTRAINT ck_source_payload_role CHECK(payload_role IN ('CANONICAL','HISTORICAL')),
  CONSTRAINT ck_source_payload_status CHECK(status IN ('ACTIVE','SUPERSEDED')),
  CONSTRAINT ck_source_payload_checksums CHECK(document_checksum ~ '^[a-f0-9]{64}$' AND content_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_source_payload_pages CHECK(page_start>0 AND page_end>=page_start),
  CONSTRAINT ck_source_payload_length CHECK(normalized_length>=0)
);
CREATE UNIQUE INDEX uq_source_payload_one_active_canonical
  ON source_payload_revision(logical_source_key,source_revision_id)
  WHERE payload_role='CANONICAL' AND status='ACTIVE';

CREATE TABLE source_payload_identity_reconciliation (
  id uuid PRIMARY KEY,
  conflict_id varchar(120) NOT NULL UNIQUE,
  historical_shared_id uuid NOT NULL REFERENCES source_reference(id),
  local_payload_checksum char(64) NOT NULL,
  hosted_payload_checksum char(64) NOT NULL,
  canonical_payload_id uuid NOT NULL REFERENCES source_payload_revision(id),
  historical_payload_id uuid NOT NULL REFERENCES source_payload_revision(id),
  logical_source_key varchar(240) NOT NULL,
  relationship varchar(50) NOT NULL,
  reason text NOT NULL,
  deterministic_actor varchar(200) NOT NULL,
  human_verified boolean NOT NULL DEFAULT false,
  source_evidence jsonb NOT NULL,
  compatibility_metadata jsonb NOT NULL,
  reconciled_at timestamptz NOT NULL,
  CONSTRAINT ck_source_payload_reconciliation_distinct CHECK(canonical_payload_id<>historical_payload_id),
  CONSTRAINT ck_source_payload_reconciliation_relationship CHECK(relationship IN ('SUPERSEDES','HISTORICAL_VERSION','DUPLICATE_IDENTITY_SPLIT'))
);

CREATE TABLE source_dependency_reconciliation (
  id uuid PRIMARY KEY,
  reconciliation_id uuid NOT NULL REFERENCES source_payload_identity_reconciliation(id),
  entity_type varchar(50) NOT NULL,
  entity_id uuid NOT NULL,
  entity_version_id uuid,
  original_source_reference_id uuid NOT NULL REFERENCES source_reference(id),
  resolved_source_reference_id uuid NOT NULL REFERENCES source_reference(id),
  resolved_payload_id uuid NOT NULL REFERENCES source_payload_revision(id),
  classification varchar(60) NOT NULL,
  original_provenance_preserved boolean NOT NULL DEFAULT true,
  created_by varchar(200) NOT NULL,
  created_at timestamptz NOT NULL,
  CONSTRAINT uq_source_dependency_reconciliation UNIQUE(reconciliation_id,entity_type,entity_id,entity_version_id),
  CONSTRAINT ck_source_dependency_classification CHECK(classification IN ('REFERENCES_CANONICAL_PAYLOAD','REFERENCES_HISTORICAL_PAYLOAD','STALE_REFERENCE_REQUIRES_VERSIONED_REBIND','INVALID_REFERENCE'))
);

CREATE OR REPLACE FUNCTION prevent_reconciled_source_payload_mutation() RETURNS trigger AS $$
BEGIN
  IF (OLD.content_text,OLD.content_checksum,OLD.file_checksum) IS DISTINCT FROM
     (NEW.content_text,NEW.content_checksum,NEW.file_checksum)
     AND (EXISTS(SELECT 1 FROM source_payload_revision p WHERE p.materialized_source_reference_id=OLD.id)
          OR EXISTS(SELECT 1 FROM source_payload_identity_reconciliation r WHERE r.historical_shared_id=OLD.id))
  THEN RAISE EXCEPTION 'Reconciled immutable Source payload cannot be changed in place'; END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER reconciled_source_payload_immutable
  BEFORE UPDATE OF content_text,content_checksum,file_checksum ON source_reference
  FOR EACH ROW EXECUTE FUNCTION prevent_reconciled_source_payload_mutation();

CREATE VIEW canonical_source_payload AS
SELECT p.logical_source_key,p.source_revision_id,p.id payload_id,
       p.materialized_source_reference_id source_reference_id,p.content_checksum,
       p.document_checksum,p.parser_version,p.extraction_version,p.page_start,p.page_end
FROM source_payload_revision p
WHERE p.payload_role='CANONICAL' AND p.status='ACTIVE';
