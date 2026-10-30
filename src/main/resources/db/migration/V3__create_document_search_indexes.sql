CREATE TABLE document_search_indexes (
    document_id UUID PRIMARY KEY REFERENCES study_documents(id),
    model_key VARCHAR(512) NOT NULL,
    dimensions INTEGER NOT NULL CHECK (dimensions BETWEEN 1 AND 8192),
    vectors_json TEXT NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE NOT NULL
);
