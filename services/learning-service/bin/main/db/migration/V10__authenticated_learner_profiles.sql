ALTER TABLE learner_profile
  ADD COLUMN email VARCHAR(320),
  ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN deleted_at TIMESTAMPTZ,
  ADD CONSTRAINT ck_learner_account_status CHECK (account_status IN ('ACTIVE','DISABLED','DELETED')),
  ADD CONSTRAINT ck_learner_deleted_state CHECK (
    (account_status = 'DELETED' AND deleted_at IS NOT NULL) OR
    (account_status <> 'DELETED' AND deleted_at IS NULL)
  );

CREATE INDEX idx_learner_profile_status ON learner_profile(account_status);
