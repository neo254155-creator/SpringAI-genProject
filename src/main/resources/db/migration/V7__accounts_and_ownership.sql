CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    username VARCHAR(40) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE account_bootstrap (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    owner_id UUID REFERENCES app_users(id)
);
INSERT INTO account_bootstrap (id, owner_id) VALUES (1, NULL);
ALTER TABLE resume_profiles ADD COLUMN owner_id UUID REFERENCES app_users(id);
ALTER TABLE interview_sessions ADD COLUMN owner_id UUID REFERENCES app_users(id);
ALTER TABLE study_documents ADD COLUMN owner_id UUID REFERENCES app_users(id);
ALTER TABLE resume_profiles ADD CONSTRAINT uq_resume_owner UNIQUE (id, owner_id);
ALTER TABLE interview_sessions ADD CONSTRAINT fk_session_resume_owner
    FOREIGN KEY (resume_id, owner_id) REFERENCES resume_profiles(id, owner_id);
CREATE INDEX idx_resumes_owner ON resume_profiles(owner_id);
CREATE INDEX idx_sessions_owner ON interview_sessions(owner_id, created_at);
CREATE INDEX idx_documents_owner ON study_documents(owner_id, created_at);
