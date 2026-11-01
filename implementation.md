# Implementation summary

Intervie is a resume-based interview coach with saved progress and study-note answers.

## Completed

- Chat and interviews: validated input, prompt templates, rubric feedback, and safe API errors.
- Persistence: PostgreSQL/JPA, Flyway V1–V5, saved history, progress, and session resume.
- Adaptive practice: the latest feedback guides the next question.
- Resume interviews: extract supported skills, confirm them, and practise at a chosen level.
- Frontend: landing, dashboard, practice, history, analytics, settings, and recommendations.
- Documents: upload text notes, save passages, generate Gemini embeddings, and search them.
- Configuration cleanup: environment bindings, exposed-password cleanup, and unused-file removal.
- 2026-11-01: added `POST /documents/{id}/answer` with retrieved source excerpts, citation-ID validation, and insufficient-evidence handling. See [DOCUMENTS.md](DOCUMENTS.md).
- 2026-11-01: shortened this log; future task entries will use 1–3 lines.

## Verification

- Latest feature: 27 focused backend tests passed; all 78 local Java files compiled.
- Earlier cleanup: 26 frontend tests passed. The full backend suite is not verified.
- Groq and Gemini direct API checks passed; application startup connected to Neon.
- Local HTTP checks timed out. Full workflows and answer/citation quality still need verification.

## Next

Build the document upload, indexing, question, and cited-answer frontend. Then verify the full workflow.
Later: user ownership, retry protection, conversation memory, AI study tools, and deployment.

## Working agreement

Keep task notes brief. Commit and push after every two completed tasks.
