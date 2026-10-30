ALTER TABLE interview_questions ADD COLUMN focus_criterion VARCHAR(32);
ALTER TABLE interview_questions ADD COLUMN focus_reason VARCHAR(500);
ALTER TABLE interview_questions ADD CONSTRAINT chk_question_focus CHECK (
    (focus_criterion IS NULL AND focus_reason IS NULL) OR
    (focus_criterion IS NOT NULL AND focus_reason IS NOT NULL
        AND focus_criterion IN ('correctness', 'completeness', 'clarity')
        AND CHAR_LENGTH(focus_reason) > 0)
);
