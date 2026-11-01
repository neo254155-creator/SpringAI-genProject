# Implementation summary

Intervie is a resume-based interview coach with saved progress and study-note answers.
Main flow: upload résumé → confirm extracted skills → choose difficulty → answer questions → receive scored feedback → practise weaknesses → review saved progress.

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
- 2026-11-01: synced local Git history with GitHub, preserved existing edits/credentials, and restored local environment overrides.
- 2026-11-01: added optional Study Notes UI, bounded per-visit caching, and a resume-upload browser fix; main resume interview flow preserved.

## Verification

- Latest feature: 27 focused backend tests passed; all 78 local Java files compiled.
- Frontend: 38 client tests and fixture browser checks passed, including mobile layouts and resume upload. The full backend suite is not verified.
- Groq and Gemini direct API checks passed; application startup connected to Neon.
- Local HTTP checks timed out. Full workflows and answer/citation quality still need verification.

## Next

Verify the complete resume interview journey with fixtures, then a minimal approved live check.
Later: user ownership, retry protection, conversation memory, AI study tools, and deployment.

## Working agreement

Keep task notes brief. Commit and push after every two completed tasks.
