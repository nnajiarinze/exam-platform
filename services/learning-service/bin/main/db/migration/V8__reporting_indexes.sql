CREATE INDEX idx_practice_session_reporting ON practice_session(status,started_at DESC);
CREATE INDEX idx_mock_attempt_reporting ON mock_exam_attempt(status,completed_at DESC) INCLUDE (percentage,passed,duration_seconds);
