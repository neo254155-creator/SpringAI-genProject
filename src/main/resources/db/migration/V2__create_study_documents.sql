CREATE TABLE study_documents (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE study_passages (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES study_documents(id),
    passage_number INTEGER NOT NULL CHECK (passage_number > 0),
    passage_text TEXT NOT NULL CHECK (CHAR_LENGTH(passage_text) BETWEEN 1 AND 1200),
    UNIQUE (document_id, passage_number)
);

CREATE INDEX idx_documents_created ON study_documents(created_at);
