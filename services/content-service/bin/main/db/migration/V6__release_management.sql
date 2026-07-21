CREATE TABLE content_release (
  id UUID PRIMARY KEY,
  exam_id UUID NOT NULL REFERENCES exam(id),
  exam_version_id UUID NOT NULL REFERENCES exam_version(id),
  release_number VARCHAR(100) NOT NULL,
  display_name VARCHAR(300) NOT NULL,
  description TEXT,
  status VARCHAR(30) NOT NULL,
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  validated_at TIMESTAMPTZ,
  validated_by VARCHAR(200),
  published_at TIMESTAMPTZ,
  published_by VARCHAR(200),
  delivered_at TIMESTAMPTZ,
  activated_at TIMESTAMPTZ,
  retired_at TIMESTAMPTZ,
  checksum VARCHAR(64),
  snapshot_schema_version VARCHAR(20) NOT NULL DEFAULT '1.0',
  knowledge_fact_count INTEGER NOT NULL DEFAULT 0,
  question_count INTEGER NOT NULL DEFAULT 0,
  last_validated_version BIGINT,
  previous_release_id UUID REFERENCES content_release(id),
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_content_release_number UNIQUE(exam_id,release_number),
  CONSTRAINT ck_content_release_status CHECK(status IN ('DRAFT','VALIDATED','PUBLISHED','DELIVERY_FAILED','DELIVERED','ACTIVE','RETIRED')),
  CONSTRAINT ck_content_release_counts CHECK(knowledge_fact_count>=0 AND question_count>=0),
  CONSTRAINT ck_content_release_checksum CHECK(checksum IS NULL OR checksum ~ '^[a-f0-9]{64}$')
);

CREATE TABLE content_release_item (
  id UUID PRIMARY KEY,
  release_id UUID NOT NULL REFERENCES content_release(id),
  content_type VARCHAR(30) NOT NULL,
  content_id UUID NOT NULL,
  content_version_id UUID NOT NULL,
  content_code VARCHAR(100),
  display_order INTEGER NOT NULL,
  automatic_dependency BOOLEAN NOT NULL DEFAULT FALSE,
  included_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_release_item_version UNIQUE(release_id,content_type,content_version_id),
  CONSTRAINT uq_release_item_order UNIQUE(release_id,content_type,display_order),
  CONSTRAINT ck_release_item_type CHECK(content_type IN ('KNOWLEDGE_FACT','QUESTION')),
  CONSTRAINT ck_release_item_order CHECK(display_order>=0)
);

CREATE TABLE release_validation_run (
  id UUID PRIMARY KEY,
  release_id UUID NOT NULL REFERENCES content_release(id),
  valid BOOLEAN NOT NULL,
  error_count INTEGER NOT NULL,
  warning_count INTEGER NOT NULL,
  validated_by VARCHAR(200) NOT NULL,
  validated_at TIMESTAMPTZ NOT NULL,
  release_version BIGINT NOT NULL,
  report_json JSONB NOT NULL,
  CONSTRAINT ck_release_validation_counts CHECK(error_count>=0 AND warning_count>=0)
);

CREATE TABLE published_release_snapshot (
  release_id UUID PRIMARY KEY REFERENCES content_release(id),
  schema_version VARCHAR(20) NOT NULL,
  checksum VARCHAR(64) NOT NULL,
  snapshot_json JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  size_bytes BIGINT NOT NULL CHECK(size_bytes>0),
  CONSTRAINT ck_published_snapshot_checksum CHECK(checksum ~ '^[a-f0-9]{64}$')
);

CREATE TABLE release_delivery_attempt (
  id UUID PRIMARY KEY,
  release_id UUID NOT NULL REFERENCES content_release(id),
  attempt_number INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  target_service VARCHAR(100) NOT NULL,
  response_code INTEGER,
  error_code VARCHAR(100),
  error_message VARCHAR(500),
  checksum VARCHAR(64) NOT NULL,
  requested_by VARCHAR(200) NOT NULL,
  CONSTRAINT uq_release_delivery_attempt UNIQUE(release_id,attempt_number),
  CONSTRAINT ck_release_delivery_status CHECK(status IN ('PENDING','SUCCESS','FAILED'))
);

CREATE TABLE release_activation_history (
  id UUID PRIMARY KEY,
  release_id UUID NOT NULL REFERENCES content_release(id),
  previous_active_release_id UUID REFERENCES content_release(id),
  activated_by VARCHAR(200) NOT NULL,
  activated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_content_release_exam ON content_release(exam_id,status,updated_at DESC);
CREATE INDEX idx_content_release_exam_version ON content_release(exam_version_id,status);
CREATE INDEX idx_content_release_published ON content_release(published_at DESC) WHERE published_at IS NOT NULL;
CREATE UNIQUE INDEX uq_active_content_release_per_exam ON content_release(exam_id) WHERE status='ACTIVE';
CREATE INDEX idx_release_item_release_type ON content_release_item(release_id,content_type,display_order);
CREATE INDEX idx_release_item_content_version ON content_release_item(content_type,content_version_id);
CREATE INDEX idx_release_validation_release ON release_validation_run(release_id,validated_at DESC);
CREATE INDEX idx_release_delivery_release ON release_delivery_attempt(release_id,attempt_number DESC);
CREATE INDEX idx_release_activation_release ON release_activation_history(release_id,activated_at DESC);
