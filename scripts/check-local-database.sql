\set ON_ERROR_STOP on
\pset pager off
BEGIN TRANSACTION READ ONLY;

DO $$
BEGIN
    IF current_database() <> 'InterviewData' THEN
        RAISE EXCEPTION 'Expected InterviewData; connected to %', current_database();
    END IF;
END $$;

SELECT current_database() AS database_name, current_user AS login,
       pg_get_userbyid(datdba) AS database_owner
FROM pg_database WHERE datname = current_database();

-- Require all six application tables plus Flyway history before reading them.
DO $$
DECLARE
    expected_table TEXT;
BEGIN
    FOREACH expected_table IN ARRAY ARRAY[
        'interview_sessions', 'interview_questions', 'interview_answers',
        'interview_feedback', 'feedback_correct_points', 'feedback_missing_points',
        'flyway_schema_history']
    LOOP
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = 'public' AND table_name = expected_table
              AND table_type = 'BASE TABLE'
        ) THEN
            RAISE EXCEPTION 'Missing required table: public.%', expected_table;
        END IF;
    END LOOP;
END $$;

-- Check every column defined by V1, including type, length, and nullability.
DO $$
DECLARE
    missing_columns TEXT;
BEGIN
    WITH expected(table_name, column_name, data_type, max_length, nullable) AS (VALUES
        ('interview_sessions', 'id', 'uuid', NULL::integer, 'NO'),
        ('interview_sessions', 'track', 'character varying', 100, 'NO'),
        ('interview_sessions', 'topic', 'character varying', 200, 'NO'),
        ('interview_sessions', 'difficulty', 'character varying', 50, 'NO'),
        ('interview_sessions', 'status', 'character varying', 20, 'NO'),
        ('interview_sessions', 'created_at', 'timestamp with time zone', NULL, 'NO'),
        ('interview_sessions', 'completed_at', 'timestamp with time zone', NULL, 'YES'),
        ('interview_sessions', 'version', 'bigint', NULL, 'NO'),
        ('interview_questions', 'id', 'uuid', NULL, 'NO'),
        ('interview_questions', 'session_id', 'uuid', NULL, 'NO'),
        ('interview_questions', 'question_text', 'text', NULL, 'NO'),
        ('interview_questions', 'created_at', 'timestamp with time zone', NULL, 'NO'),
        ('interview_answers', 'id', 'uuid', NULL, 'NO'),
        ('interview_answers', 'question_id', 'uuid', NULL, 'NO'),
        ('interview_answers', 'answer_text', 'text', NULL, 'NO'),
        ('interview_answers', 'submitted_at', 'timestamp with time zone', NULL, 'NO'),
        ('interview_feedback', 'id', 'uuid', NULL, 'NO'),
        ('interview_feedback', 'answer_id', 'uuid', NULL, 'NO'),
        ('interview_feedback', 'explanation', 'text', NULL, 'NO'),
        ('interview_feedback', 'correctness', 'integer', NULL, 'NO'),
        ('interview_feedback', 'correctness_reason', 'text', NULL, 'NO'),
        ('interview_feedback', 'completeness', 'integer', NULL, 'NO'),
        ('interview_feedback', 'completeness_reason', 'text', NULL, 'NO'),
        ('interview_feedback', 'clarity', 'integer', NULL, 'NO'),
        ('interview_feedback', 'clarity_reason', 'text', NULL, 'NO'),
        ('interview_feedback', 'score', 'integer', NULL, 'NO'),
        ('interview_feedback', 'max_score', 'integer', NULL, 'NO'),
        ('interview_feedback', 'rubric_version', 'character varying', 40, 'NO'),
        ('feedback_correct_points', 'feedback_id', 'uuid', NULL, 'NO'),
        ('feedback_correct_points', 'point_order', 'integer', NULL, 'NO'),
        ('feedback_correct_points', 'point_text', 'text', NULL, 'NO'),
        ('feedback_missing_points', 'feedback_id', 'uuid', NULL, 'NO'),
        ('feedback_missing_points', 'point_order', 'integer', NULL, 'NO'),
        ('feedback_missing_points', 'point_text', 'text', NULL, 'NO')
    )
    SELECT string_agg(e.table_name || '.' || e.column_name, ', ')
    INTO missing_columns
    FROM expected e LEFT JOIN information_schema.columns c
      ON c.table_schema = 'public' AND c.table_name = e.table_name AND c.column_name = e.column_name
    WHERE c.column_name IS NULL OR c.data_type <> e.data_type OR c.is_nullable <> e.nullable
       OR c.character_maximum_length IS DISTINCT FROM e.max_length;
    IF missing_columns IS NOT NULL THEN
        RAISE EXCEPTION 'Missing or mismatched columns: %', missing_columns;
    END IF;
END $$;

SELECT version, description, script, checksum, installed_on, success
FROM public.flyway_schema_history ORDER BY installed_rank;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.flyway_schema_history
        WHERE version = '1' AND script = 'V1__create_interview_history.sql' AND success
    ) OR EXISTS (SELECT 1 FROM public.flyway_schema_history WHERE NOT success) THEN
        RAISE EXCEPTION 'Expected successful Flyway V1 migration without failed entries';
    END IF;
END $$;

-- Report actual definitions for reviewing primary keys, foreign keys,
-- uniqueness, and score/text/status checks. Never inserts test data.
SELECT c.relname AS table_name, k.conname AS constraint_name,
       pg_get_constraintdef(k.oid) AS definition
FROM pg_constraint k JOIN pg_class c ON c.oid = k.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relname IN (
    'interview_sessions', 'interview_questions', 'interview_answers',
    'interview_feedback', 'feedback_correct_points', 'feedback_missing_points')
  AND k.contype IN ('p', 'f', 'u', 'c')
ORDER BY c.relname, k.contype, k.conname;

SELECT tablename, indexname, indexdef FROM pg_indexes
WHERE schemaname = 'public' AND tablename IN (
    'interview_sessions', 'interview_questions', 'interview_answers',
    'interview_feedback', 'feedback_correct_points', 'feedback_missing_points')
ORDER BY tablename, indexname;

SELECT 'interview_sessions' AS table_name, count(*) AS rows FROM public.interview_sessions
UNION ALL SELECT 'interview_questions', count(*) FROM public.interview_questions
UNION ALL SELECT 'interview_answers', count(*) FROM public.interview_answers
UNION ALL SELECT 'interview_feedback', count(*) FROM public.interview_feedback
UNION ALL SELECT 'feedback_correct_points', count(*) FROM public.feedback_correct_points
UNION ALL SELECT 'feedback_missing_points', count(*) FROM public.feedback_missing_points;

SELECT 'PASS: database name, seven tables, expected V1 columns/types/nullability, and successful migration history. Review constraint/index definitions above.' AS result;
ROLLBACK;
