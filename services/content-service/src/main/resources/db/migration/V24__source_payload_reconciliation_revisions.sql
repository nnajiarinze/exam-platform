CREATE TABLE source_payload_reconciliation_revision (
  id uuid PRIMARY KEY,
  reconciliation_id uuid NOT NULL REFERENCES source_payload_identity_reconciliation(id),
  revision_number integer NOT NULL,
  previous_revision_id uuid REFERENCES source_payload_reconciliation_revision(id),
  compatibility_metadata jsonb NOT NULL,
  reason text NOT NULL,
  created_by varchar(200) NOT NULL,
  created_at timestamptz NOT NULL,
  CONSTRAINT uq_source_payload_reconciliation_revision UNIQUE(reconciliation_id,revision_number)
);
