CREATE TABLE request_replays (
    key_hash VARCHAR(64) PRIMARY KEY,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('RUNNING', 'COMPLETE', 'FAILED', 'UNKNOWN')),
    response_status INTEGER,
    response_body TEXT,
    content_type VARCHAR(255),
    location VARCHAR(1024),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
