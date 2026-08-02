ALTER TABLE source_dependency_reconciliation
  DROP CONSTRAINT uq_source_dependency_reconciliation;
ALTER TABLE source_dependency_reconciliation
  ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE source_dependency_reconciliation
  ADD CONSTRAINT ck_source_dependency_reconciliation_status CHECK(status IN ('ACTIVE','SUPERSEDED'));

WITH ranked AS (
  SELECT id,row_number() OVER(
    PARTITION BY reconciliation_id,entity_type,entity_id,coalesce(entity_version_id,'00000000-0000-0000-0000-000000000000'::uuid)
    ORDER BY created_at,id) AS position
  FROM source_dependency_reconciliation
)
UPDATE source_dependency_reconciliation d SET status='SUPERSEDED'
FROM ranked r WHERE r.id=d.id AND r.position>1;

CREATE UNIQUE INDEX uq_source_dependency_reconciliation_active
  ON source_dependency_reconciliation(
    reconciliation_id,entity_type,entity_id,
    coalesce(entity_version_id,'00000000-0000-0000-0000-000000000000'::uuid))
  WHERE status='ACTIVE';
