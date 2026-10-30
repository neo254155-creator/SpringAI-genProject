CREATE TABLE resume_profiles (
    id UUID PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    skills_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
ALTER TABLE interview_sessions ADD COLUMN resume_id UUID REFERENCES resume_profiles(id);
ALTER TABLE interview_sessions ADD COLUMN resume_context_json TEXT;
ALTER TABLE interview_sessions ADD CONSTRAINT ck_resume_snapshot
    CHECK ((resume_id IS NULL AND resume_context_json IS NULL) OR
           (resume_id IS NOT NULL AND resume_context_json IS NOT NULL));
ALTER TABLE interview_questions ADD COLUMN resume_skill VARCHAR(80);
CREATE INDEX idx_sessions_resume ON interview_sessions(resume_id);
