CREATE TABLE ai_lesson_page_repair_attempt (
  id uuid PRIMARY KEY,
  lesson_proposal_id uuid NOT NULL REFERENCES ai_lesson_proposal(id),
  page_index integer NOT NULL,
  replaces_revision_id uuid REFERENCES ai_lesson_page_revision(id),
  contract_version varchar(100) NOT NULL,
  status varchar(30) NOT NULL,
  failure_code varchar(100),
  provider varchar(40),
  model varchar(200),
  provider_request_id varchar(200),
  input_tokens integer,
  output_tokens integer,
  reasoning_tokens integer,
  latency_millis bigint,
  free_only boolean NOT NULL,
  idempotency_key varchar(200),
  mutable_response jsonb,
  created_by varchar(200) NOT NULL,
  created_at timestamptz NOT NULL,
  CONSTRAINT ck_lesson_page_repair_attempt_status CHECK (status IN ('SUCCEEDED','CLAIM_REJECTED','PROVIDER_REJECTED','INSUFFICIENT_INFORMATION')),
  CONSTRAINT uq_lesson_page_repair_attempt_idempotency UNIQUE (lesson_proposal_id,page_index,idempotency_key)
);

CREATE INDEX idx_lesson_page_repair_attempt_page
  ON ai_lesson_page_repair_attempt(lesson_proposal_id,page_index,created_at);
