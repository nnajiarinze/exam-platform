ALTER TABLE imported_content_release
  ADD COLUMN release_type VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
  ADD COLUMN approval_strategy VARCHAR(30) NOT NULL DEFAULT 'MANUAL_REVIEW',
  ADD COLUMN disclaimer TEXT,
  ADD COLUMN attribution TEXT;

ALTER TABLE imported_content_release
  ADD CONSTRAINT ck_imported_release_type CHECK(release_type IN ('PUBLIC','INTERNAL')),
  ADD CONSTRAINT ck_imported_release_approval CHECK(approval_strategy IN ('MANUAL_REVIEW','AUTO_APPROVED'));
