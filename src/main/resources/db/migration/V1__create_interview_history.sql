CREATE TABLE interview_sessions (
    id UUID PRIMARY KEY,
    track VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    difficulty VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL,
    CHECK ((status = 'ACTIVE' AND completed_at IS NULL) OR
           (status = 'COMPLETED' AND completed_at IS NOT NULL))
);
CREATE INDEX idx_sessions_created ON interview_sessions(created_at);

CREATE TABLE interview_questions (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES interview_sessions(id),
    question_text TEXT NOT NULL CHECK (CHAR_LENGTH(question_text) BETWEEN 1 AND 2000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_questions_session ON interview_questions(session_id, created_at);

CREATE TABLE interview_answers (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES interview_questions(id),
    answer_text TEXT NOT NULL CHECK (CHAR_LENGTH(answer_text) BETWEEN 1 AND 8000),
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_answers_question ON interview_answers(question_id, submitted_at);

CREATE TABLE interview_feedback (
    id UUID PRIMARY KEY,
    answer_id UUID NOT NULL UNIQUE REFERENCES interview_answers(id),
    explanation TEXT NOT NULL,
    correctness INTEGER NOT NULL CHECK (correctness BETWEEN 0 AND 5),
    correctness_reason TEXT NOT NULL,
    completeness INTEGER NOT NULL CHECK (completeness BETWEEN 0 AND 3),
    completeness_reason TEXT NOT NULL,
    clarity INTEGER NOT NULL CHECK (clarity BETWEEN 0 AND 2),
    clarity_reason TEXT NOT NULL,
    score INTEGER NOT NULL CHECK (score BETWEEN 0 AND 10),
    max_score INTEGER NOT NULL CHECK (max_score = 10),
    rubric_version VARCHAR(40) NOT NULL,
    CHECK (score = correctness + completeness + clarity)
);
CREATE TABLE feedback_correct_points (
    feedback_id UUID NOT NULL REFERENCES interview_feedback(id),
    point_order INTEGER NOT NULL,
    point_text TEXT NOT NULL,
    PRIMARY KEY(feedback_id, point_order)
);
CREATE TABLE feedback_missing_points (
    feedback_id UUID NOT NULL REFERENCES interview_feedback(id),
    point_order INTEGER NOT NULL,
    point_text TEXT NOT NULL,
    PRIMARY KEY(feedback_id, point_order)
);
