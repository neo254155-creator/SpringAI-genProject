# Questions guided by saved feedback

The existing `POST /interview/sessions/{id}/questions` route now chooses a
practice focus using that session's latest saved answer feedback. The frontend's
**Next question** button already calls this route. No new API key is needed.

## Selection rule

Compare the three rubric scores as proportions of their maximum:

| Criterion | Maximum | Example |
| --- | --- | --- |
| Correctness | 5 | 4/5 = 80% |
| Completeness | 3 | 2/3 ≈ 67% |
| Clarity | 2 | 1/2 = 50% |

The example targets **clarity**. These percentages are used only to compare the
rubric components; they are not mastery or confidence estimates. Ties use the
fixed order correctness, completeness, clarity. First questions and perfect
scores have no weakness target. Perfect scores continue normal practice.

## Prompt and storage

- `AdaptiveQuestionPolicy` chooses the focus in Java; the LLM writes the question.
- The prompt keeps the selected topic and difficulty, includes the weakest
  criterion's reason (at most 300 UTF-16 code units), up to three missing points
  (200 code units each), and up to five recent questions from this session.
- Saved evidence is serialized as JSON and labelled as untrusted data. The system
  prompt instructs the model to treat it as evidence rather than commands. This
  is a prompt instruction, not proof of resistance to prompt injection.
- Previous raw answers are not included in the next-question prompt.
- Generated questions matching any of the five recent texts after whitespace
  normalization and lowercasing are rejected with the existing safe 502 error.
  Paraphrased duplicates are not detected. No automatic retry is performed.
- Question responses now include optional `focus: {criterion, reason}`. Regular
  questions and older records have null focus. Both practice and history show
  the reason, and resume restores it from storage.
- V4 adds nullable focus columns to `interview_questions` and a constraint on
  valid criterion/reason pairs. V1–V3 remain unchanged.

Feedback is read when generation starts. Newer feedback saved while the model
is running affects the next request, not the one already in progress. Database
transactions end before the AI call; saving rechecks that the session is active.

## Limits

This is a rule-based first version using the latest saved feedback. It does not
combine topic weaknesses across sessions, change difficulty automatically, or
maintain full conversation memory. Scoring accuracy and whether a real generated
question addresses the selected weakness still require live-model evaluation.

Default automated tests use AI stubs and H2, not a live Groq or PostgreSQL server.
