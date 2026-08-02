ALTER TABLE lesson_draft_section ADD COLUMN logical_section_id UUID;
UPDATE lesson_draft_section SET logical_section_id=id WHERE logical_section_id IS NULL;
ALTER TABLE lesson_draft_section ALTER COLUMN logical_section_id SET NOT NULL;
CREATE INDEX idx_lesson_section_logical_identity ON lesson_draft_section(logical_section_id,lesson_draft_id);
