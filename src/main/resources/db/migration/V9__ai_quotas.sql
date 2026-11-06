CREATE TABLE ai_quotas (
    scope VARCHAR(64) PRIMARY KEY,
    usage_day DATE NOT NULL,
    requests INTEGER NOT NULL DEFAULT 0,
    input_characters BIGINT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36),
    lease_until TIMESTAMP WITH TIME ZONE
);
INSERT INTO ai_quotas(scope, usage_day) VALUES ('global', CURRENT_DATE);
