# Implementation summary

Intervie is a resume-based interview coach with saved progress and study-note answers.
Main flow: upload résumé → confirm extracted skills → choose difficulty → answer questions → receive scored feedback → practise weaknesses → review saved progress.

## Completed

- Chat and interviews: validated input, prompt templates, rubric feedback, and safe API errors.
- Persistence: PostgreSQL/JPA, Flyway V1–V7, saved history, progress, and session resume.
- Adaptive practice: the latest feedback guides the next question.
- Resume interviews: extract supported skills, confirm them, and practise at a chosen level.
- Frontend: landing, dashboard, practice, history, analytics, settings, and recommendations.
- Documents: upload text notes, save passages, generate Gemini embeddings, and search them.
- Configuration cleanup: environment bindings, exposed-password cleanup, and unused-file removal.
- 2026-11-01: added `POST /documents/{id}/answer` with retrieved source excerpts, citation-ID validation, and insufficient-evidence handling. See [DOCUMENTS.md](DOCUMENTS.md).
- 2026-11-01: shortened this log; future task entries will use 1–3 lines.
- 2026-11-01: synced local Git history with GitHub, preserved existing edits/credentials, and restored local environment overrides.
- 2026-11-01: added optional Study Notes UI, bounded per-visit caching, and a resume-upload browser fix; main resume interview flow preserved.
- 2026-11-01: verified the full resume journey with browser fixtures and API/H2 tests, including saved scores, weakness focus, refresh, and quota-error recovery; no live AI calls.
- 2026-11-01: live resume smoke passed through Groq and Neon: upload, confirmed skills, difficulty, question, score, reload, history, and progress; exactly 3 Groq calls and no Gemini calls.
- 2026-11-01: added durable retry protection, reload-safe browser keys, V6, and disabled hidden SDK retries; offline tests passed with no live AI calls.
- 2026-11-02: added username/password accounts, session/CSRF protection, private ownership, and V7; the first local account receives legacy data once, as requested.

## Verification

- Backend: full default offline suite passed (173 tests). V6/V7 passed on H2; live Neon rollout and first-account transfer are pending.
- Frontend: 56 client tests and document/resume/account fixture browser checks passed, including mobile layouts and saved progress.
- Live resume smoke passed with Groq and Neon; Gemini's earlier direct API check passed.
- Weakness practice passed offline; live follow-up quality, citation support, and database restart recovery still need evaluation.

## Next

1. Verify V6/V7 on Neon; create the first local account and confirm its existing records.
2. Add bounded interview memory, then controlled AI tools for scores and study tasks.
3. Connect study-note explanations to interview feedback.
4. Evaluate scoring, follow-ups, retrieval/citations, and restart recovery with a small live-call budget.
5. Build/test the container, deploy with HTTPS and suitable session storage, and finish the file guide.

## Working agreement

Keep task notes brief. Commit and push after every two completed tasks.
At project completion, add a file guide here: one short, simple explanation of what each project source, test, configuration, and documentation file does.
