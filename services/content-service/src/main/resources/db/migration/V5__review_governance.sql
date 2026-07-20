CREATE TABLE review_item (
  id UUID PRIMARY KEY,
  content_type VARCHAR(30) NOT NULL,
  content_id UUID NOT NULL,
  content_version_id UUID NOT NULL,
  author_id VARCHAR(200) NOT NULL,
  review_status VARCHAR(30) NOT NULL,
  lifecycle_status VARCHAR(20) NOT NULL,
  priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  assigned_reviewer_id VARCHAR(200),
  assigned_at TIMESTAMPTZ,
  submitted_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_review_item_content_version UNIQUE(content_type, content_version_id),
  CONSTRAINT ck_review_item_type CHECK(content_type IN ('KNOWLEDGE_FACT','QUESTION')),
  CONSTRAINT ck_review_item_status CHECK(review_status IN ('UNDER_REVIEW','APPROVED','REJECTED','REQUIRES_UPDATE')),
  CONSTRAINT ck_review_item_lifecycle CHECK(lifecycle_status IN ('DRAFT','ACTIVE','RETIRED')),
  CONSTRAINT ck_review_item_priority CHECK(priority IN ('LOW','NORMAL','HIGH','URGENT')),
  CONSTRAINT ck_review_item_assignment CHECK((assigned_reviewer_id IS NULL) = (assigned_at IS NULL))
);

CREATE TABLE review_record (
  id UUID PRIMARY KEY,
  review_item_id UUID NOT NULL REFERENCES review_item(id),
  content_version_id UUID NOT NULL,
  action VARCHAR(40) NOT NULL,
  from_status VARCHAR(30),
  to_status VARCHAR(30),
  actor_id VARCHAR(200) NOT NULL,
  actor_roles VARCHAR(500),
  comment TEXT,
  reason_code VARCHAR(60),
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE review_comment (
  id UUID PRIMARY KEY,
  review_item_id UUID NOT NULL REFERENCES review_item(id),
  content_version_id UUID NOT NULL,
  author_id VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_review_comment_body CHECK(length(trim(body)) > 0)
);

CREATE INDEX idx_review_item_queue ON review_item(review_status, priority, submitted_at, id);
CREATE INDEX idx_review_item_assignment ON review_item(assigned_reviewer_id, review_status);
CREATE INDEX idx_review_item_content ON review_item(content_type, content_id, submitted_at DESC);
CREATE INDEX idx_review_item_submitted ON review_item(submitted_at);
CREATE INDEX idx_review_record_item ON review_record(review_item_id, created_at, id);
CREATE INDEX idx_review_record_version ON review_record(content_version_id, created_at);
CREATE INDEX idx_review_comment_item ON review_comment(review_item_id, created_at, id);

INSERT INTO review_item(id,content_type,content_id,content_version_id,author_id,review_status,lifecycle_status,submitted_at,updated_at)
SELECT gen_random_uuid(),'KNOWLEDGE_FACT',f.id,v.id,v.author_id,f.review_status,f.status,v.updated_at,f.updated_at
FROM knowledge_fact f JOIN knowledge_fact_version v ON v.id=f.current_version_id
WHERE f.review_status IN ('UNDER_REVIEW','REQUIRES_UPDATE','REJECTED');

INSERT INTO review_item(id,content_type,content_id,content_version_id,author_id,review_status,lifecycle_status,submitted_at,updated_at)
SELECT gen_random_uuid(),'QUESTION',q.id,v.id,v.author_id,q.review_status,q.status,v.updated_at,q.updated_at
FROM question q JOIN question_version v ON v.id=q.current_version_id
WHERE q.review_status IN ('UNDER_REVIEW','REQUIRES_UPDATE','REJECTED');
