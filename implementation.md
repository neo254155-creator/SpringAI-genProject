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
- 2026-11-05: verified the packaged résumé/account flow and restart recovery; kept the local C/C++ and missing-difficulty fixes. Connection checks now skip AI by default.
- 2026-11-05: prepared Secure production cookies and JDBC sessions (V8); publishing waits until after UI work. File explanations: [FILE_GUIDE.md](FILE_GUIDE.md).
- 2026-11-05: deployed [Intervie](https://intervie-genproject.vercel.app) on Vercel. Fixed cold starts with runtime settings; hosted login/privacy/reload/logout passed. Test accounts removed; zero AI calls.

- 2026-11-05: removed decorative badges, invented preview metrics, promotional filler and internal setup/roadmap notes; kept real statuses and privacy details. No new project files or AI calls.

- 2026-11-06: reviewed the full flow; fixed grounded-excerpt recovery, safe AI error reasons and stale loading text locally. 176 backend/56 client tests and production restart journey passed; SDK quota/auth/timeout checks also passed. See PROJECT_REVIEW.md.

- 2026-11-06: added shared account/site AI budgets, V9, bounded inputs/batches and local source summaries. 185 backend/56 client tests passed; no live AI calls.

- 2026-11-06: prepared public-file edge caching, module preloads and 45s connection timeouts; 12 security/cache and 57 client tests plus a 12s-delay browser fixture passed. Live speed remains unverified until release.

## Verification

- Backend: 173 offline tests passed; JAR packaging and Neon V6–V8/cookie/CSRF checks passed. The first account owns the two existing sessions; no unowned résumés, sessions or notes remain.
- Frontend: 56 client tests passed. Real browser → packaged app → file database checks passed in local and production profiles, including restart and quota recovery.
- Live resume smoke passed with Groq and Neon; Gemini's earlier direct API check passed.
- Weakness practice and file-database restart recovery passed offline; live scoring/citation quality is not established by fixture tests. No live AI calls ran for this update.

## Next

Enhance the UI while preserving the agreed résumé-to-progress flow; test updates before redeploying.
Extra memory, AI tools, study-task management and note integration are deferred.

## Working agreement

Keep task notes brief. Commit and push after every two completed tasks.
User-requested release: loading/cache changes committed; release checks caught and corrected a welcome-page forwarding bug.
Keep [FILE_GUIDE.md](FILE_GUIDE.md) current as files change; this log stays brief.
