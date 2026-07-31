CREATE TABLE ai_lesson_page_revision (
  id UUID PRIMARY KEY,
  lesson_proposal_id UUID NOT NULL REFERENCES ai_lesson_proposal(id),
  page_index INTEGER NOT NULL,
  revision_number INTEGER NOT NULL,
  replaces_revision_id UUID REFERENCES ai_lesson_page_revision(id),
  status VARCHAR(20) NOT NULL,
  page JSONB NOT NULL,
  diagnostics JSONB NOT NULL DEFAULT '[]'::jsonb,
  validator_version VARCHAR(80) NOT NULL,
  provider VARCHAR(50),
  model VARCHAR(120),
  prompt_version VARCHAR(100),
  provider_request_id VARCHAR(300),
  input_tokens INTEGER,
  output_tokens INTEGER,
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_page_revision UNIQUE(lesson_proposal_id,page_index,revision_number),
  CONSTRAINT ck_lesson_page_revision_status CHECK(status IN ('PENDING','VALIDATED','REJECTED','SUPERSEDED')),
  CONSTRAINT ck_lesson_page_revision_index CHECK(page_index>=0 AND revision_number>=1)
);

CREATE TABLE ai_lesson_page_claim (
  id UUID PRIMARY KEY,
  page_revision_id UUID NOT NULL REFERENCES ai_lesson_page_revision(id) ON DELETE CASCADE,
  claim_order INTEGER NOT NULL,
  claim_text TEXT NOT NULL,
  status VARCHAR(40) NOT NULL,
  failure_code VARCHAR(60),
  diagnostic VARCHAR(500),
  evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
  validator_version VARCHAR(80) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_page_claim_order UNIQUE(page_revision_id,claim_order),
  CONSTRAINT ck_lesson_page_claim_status CHECK(status IN ('SUPPORTED','REJECTED','NON_FACTUAL_TEXT'))
);

CREATE INDEX idx_lesson_page_revision_proposal ON ai_lesson_page_revision(lesson_proposal_id,page_index,revision_number DESC);
CREATE INDEX idx_lesson_page_claim_revision ON ai_lesson_page_claim(page_revision_id,claim_order);
