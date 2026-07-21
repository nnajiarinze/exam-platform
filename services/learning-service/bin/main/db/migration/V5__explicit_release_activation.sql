ALTER TABLE imported_content_release DROP CONSTRAINT imported_content_release_status_check;
UPDATE imported_content_release SET status='IMPORTED' WHERE status='SUPERSEDED';
ALTER TABLE imported_content_release ADD CONSTRAINT imported_content_release_status_check
  CHECK(status IN ('IMPORTED','ACTIVE','RETIRED','FAILED'));
DROP INDEX uq_active_release_per_exam_version;
CREATE UNIQUE INDEX uq_active_release_per_exam
  ON imported_content_release(exam_id) WHERE status='ACTIVE';
ALTER TABLE imported_content_release ADD COLUMN activated_at TIMESTAMPTZ;
ALTER TABLE imported_content_release ADD COLUMN retired_at TIMESTAMPTZ;
ALTER TABLE imported_content_release ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE imported_release_activation_history (
  id UUID PRIMARY KEY,
  imported_release_id UUID NOT NULL REFERENCES imported_content_release(id),
  previous_active_release_id UUID REFERENCES imported_content_release(id),
  activated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_imported_release_activation ON imported_release_activation_history(imported_release_id,activated_at DESC);
