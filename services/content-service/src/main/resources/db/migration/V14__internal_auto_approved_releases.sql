ALTER TABLE content_release
  ADD COLUMN release_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  ADD COLUMN approval_strategy VARCHAR(30) NOT NULL DEFAULT 'MANUAL_REVIEW',
  ADD COLUMN disclaimer TEXT,
  ADD COLUMN attribution TEXT;

ALTER TABLE content_release
  ADD CONSTRAINT ck_content_release_type CHECK(release_type IN ('PUBLIC','INTERNAL')),
  ADD CONSTRAINT ck_content_release_approval_strategy CHECK(approval_strategy IN ('MANUAL_REVIEW','AUTO_APPROVED')),
  ADD CONSTRAINT ck_internal_auto_approval CHECK(approval_strategy<>'AUTO_APPROVED' OR release_type='INTERNAL');

ALTER TABLE question_ai_provenance
  ADD COLUMN automatic_quality_gate_passed BOOLEAN NOT NULL DEFAULT TRUE,
  ADD COLUMN human_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_question_ai_internal_eligibility
  ON question_ai_provenance(question_id)
  WHERE automatic_quality_gate_passed AND NOT human_verified;
