CREATE TABLE identity_management_audit (
  id UUID PRIMARY KEY,
  learner_id UUID NOT NULL REFERENCES learner_profile(id),
  subject_hash CHAR(64) NOT NULL,
  action VARCHAR(80) NOT NULL,
  provider VARCHAR(40),
  outcome VARCHAR(30) NOT NULL,
  correlation_id UUID NOT NULL,
  details JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_identity_audit_action CHECK (action <> ''),
  CONSTRAINT ck_identity_audit_outcome CHECK (outcome IN ('INITIATED','SUCCEEDED','REJECTED','FAILED'))
);

CREATE INDEX idx_identity_audit_learner_created
  ON identity_management_audit(learner_id, created_at DESC);

CREATE TABLE identity_deletion_request (
  id UUID PRIMARY KEY,
  learner_id UUID NOT NULL REFERENCES learner_profile(id),
  status VARCHAR(30) NOT NULL,
  correlation_id UUID NOT NULL,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL,
  processing_started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  failure_code VARCHAR(80),
  CONSTRAINT uq_identity_deletion_active UNIQUE (learner_id),
  CONSTRAINT ck_identity_deletion_status CHECK (status IN ('PENDING_CONFIRMATION','PROCESSING','COMPLETED','FAILED')),
  CONSTRAINT ck_identity_deletion_expiry CHECK (expires_at > requested_at)
);

