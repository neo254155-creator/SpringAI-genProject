## Full-flow review — 2026-11-06

Checked login/ownership/CSRF, résumé reading and extraction, confirmed skills/difficulty, questions, rubric feedback, weakness practice, history/progress, notes/indexing/search, retries, production sessions, configuration and deployment.

| Priority | Finding | Status / next action |
| --- | --- | --- |
| High | Extraction rejects an entire result when AI paraphrases evidence for a skill actually present in the résumé. All failures previously shared one message. | Implemented: recover the literal whole skill from source text, reject unsupported names, classify errors and log only a safe category. Exact cause of the original hosted failure remains unconfirmed. |
| High | Authenticated AI endpoints have no shared per-account usage budget; login throttling does not protect Groq/Gemini quota. | Implemented shared daily request/character budgets and per-account concurrency (V9); configurable defaults in VERCEL.md. |
| Medium | Live homepage took 13.01 seconds; browser configuration/CSRF/account reads use 10-second timeouts. | Prepared public-file cache headers/preloads and 45-second connection timeouts; targeted tests passed. Deployment/cache-hit timing remains pending. Private requests and cache misses still incur cold starts. |
| Medium | RUNNING/UNKNOWN retry records can remain blocked indefinitely after interruption; stored response copies have no cleanup policy. | Add safe reconciliation/retention without blindly repeating paid AI calls. |
| Low | Question Bank remains a placeholder; settings and résumé pages still contain some template/internal copy. | Hide the unused entry and finish essential-page cleanup. |

Verification: 176 backend tests passed and JAR packaged; 56 client tests and résumé/notes fixture browser checks passed. Real browser → packaged app → file-backed database passed the production JDBC profile, account isolation, quota recovery and process restart (six local AI fixture calls). No new live AI requests in this review.
Live checks: homepage/assets/configuration returned 200; anonymous /auth/me returned 401. UI commit 400893f is deployed and its updated landing script is served. Extraction/quota fixes are included in the current two-task release.
Earlier diagnostic: one synthetic direct Groq request returned 200 and paraphrased evidence; it used a simplified diagnostic prompt and local saved credentials, not the exact deployed request. It does not establish the original incident's cause. Gemini and hosted generation were not re-tested. H2 checks do not prove every PostgreSQL or provider behavior. This is a scoped review, not a guarantee of no remaining bugs.

## Earlier review (historical)

# Project review — 2026-10-24

This describes the checked-in source and the foundation follow-up in this change.
It does not claim that a deployed application or a real Groq response was tested.

## Current structure (updated for persistence)

The project is a single Maven application using Java 21, Spring Boot 4.1.1, and
Spring AI 2.0.1, as declared in `pom.xml`. The static frontend is served by the
same Spring Boot application. Spring Data JPA stores interview history in
PostgreSQL, with Flyway migrations and a test-only H2 database.

| Area | Current code | What it does |
| --- | --- | --- |
| Application | `GenProjectApplication` | Starts Spring Boot |
| AI configuration | `ChatOptionsConfig` | Builds a shared ChatClient using the configured model, temperature 0.2, and a 1000-token budget |
| Chat API | `AIController`, `AiService`, `AIServiceImpl` | Accepts user messages, validates them, renders system/user prompts, and requests a response |
| Interview API | `InterviewController`, `InterviewService`, `InterviewSelection`, `InterviewQuestion` | Lists supported selections and generates a question for a chosen track, topic, and level |
| Feedback API | `InterviewFeedbackController`, `InterviewFeedbackService`, feedback DTO records | Converts model JSON to validated rubric feedback and computes the total score |
| Persistence | `InterviewSessionService`, `InterviewHistoryStore`, JPA entities/repositories | Creates sessions, stores questions/answers/feedback, exposes history and topic progress |
| Schema | `db/migration/V1__create_interview_history.sql` | Versioned tables, foreign keys, indexes, and score constraints |
| Errors | `ApiExceptionHandler`, `InvalidInputException`, `AiProviderException` | Returns problem responses and avoids exposing provider exception text |
| Prompt resources | `Prompt/SystemMessage`, `Prompt/UserMessage`, `Prompt/InterviewSystem`, `Prompt/InterviewQuestion`, `Prompt/InterviewFeedbackSystem`, `Prompt/InterviewFeedbackUser` | Supplies chat instructions and dynamically substitutes user input or interview selections |
| Frontend | `static/index.html`, `static/coach.js`, `static/interview-client.js` | Creates saved sessions; shows questions, accepts answers, displays rubric feedback, requests the next question, and completes sessions |
| Documentation | `README.md`, `frontend/README.md`, `implementation.md` | Describes use, test commands, and the implementation history |

## Part 1 — Foundation status

| Requirement | Verified implementation |
| --- | --- |
| Accept user input | `GET /chat?q=...` returns plain text. `POST /chat` accepts JSON `message` and returns JSON `content`. Service validation rejects blank messages and input over 8000 Java string characters. |
| Align controller tests | Standalone MVC tests exercise current routes and DTOs. Application-context MVC tests also exercise real controller, service, configuration, serializer, and advice wiring with a local AI stub. |
| Separate offline and live tests | Default Maven tests exclude the `live` tag. AI calls in offline tests use local stubs. The `live-groq` profile selects the tagged live smoke test, whose environment guard skips it without credentials. |
| Improve errors | Validation returns 400, AI call failures or empty output return 502, and unexpected application exceptions return 500. Responses use problem JSON. MVC preserves protocol status codes including 405, 406, and 415. |

Explicit GET/POST chat media types were added during this review. GET chat
requests that accept only JSON now receive 406 rather than risking a plain string
being labelled JSON. Application-context coverage was expanded from a startup-only
test to requests through MockMvc. These tests do not open a real server socket.

The foundation already existed in earlier commits. This review verifies it and
closes the wiring-test and response-format gaps; it does not rebuild those earlier
features or claim they were missing.

## Existing interview behavior

- Supported track: Java.
- Supported topics: Core Java and Spring Boot.
- Supported levels: beginner, intermediate, advanced.
- `GET /interview/options` lists those choices without calling AI.
- `POST /interview/questions` validates a selection and returns the selection
  plus a generated question.
- The prompt asks for one question without an answer or hint. That is an AI
  instruction; code currently checks only that returned text is nonempty.
- `POST /interview/feedback` accepts the selection, question, and answer and returns
  typed feedback with a validated rubric and a Java-calculated score out of 10.
- Original `/questions` and `/feedback` routes remain stateless. New session routes
  persist questions, answer attempts, and feedback; see `PERSISTENCE.md`.
- Session progress aggregates saved attempts and scores by topic/level for the signed-in account. Username/password sessions, CSRF and ownership checks are implemented.
- Frontend output is inserted as text. Cancelling a browser request does not
  guarantee cancellation of an already-running provider call.

## Current requested order

1. Prompt templates: generate a question by topic and difficulty (`level`). This
   step is implemented with separate system and user template resources.
2. Structured responses: question and feedback APIs now return Java records.
   Feedback contains validated component scores and a calculated total. The
   backend and frontend support question → answer → feedback → next question.
3. Regular PostgreSQL + JPA: implemented session/question/answer/feedback storage,
   history, completion, and aggregate progress APIs. H2 tests cover the contract;
   real PostgreSQL verification requires the optional Docker-backed test profile.
4. Document search: text-note ingestion, numbered passages, and per-document
   semantic retrieval are implemented; see DOCUMENTS.md. Configurable hosted
   Google Gemini OpenAI-compatible adapter generate embeddings, stored as JSON in regular PostgreSQL and ranked
   by cosine similarity in Java. Live model verification, pgvector, and generated
   explanations with checked citation IDs now exist; live source-support evaluation remains pending.

The interview question/answer/feedback screen, history/progress dashboard, and
saved-session resume are implemented. Verified application deployment remains
pending. A local PostgreSQL Compose file is provided;
Dockerfile.vercel now supplies an application image build definition; an actual
container build and deployed site have not been verified. Quality evaluation is pending.

Weakness-targeted questions now use the latest session feedback and up to five
recent questions; see ADAPTIVE_INTERVIEWS.md. Résumé skill extraction and confirmation
are implemented. Full conversation memory and AI-invokable Java tools for previous
scores/study tasks remain pending. Previous scores are available through history/progress APIs.

## Verification limits

See the latest dated entry in `implementation.md` for executed commands and counts.
API-key setup and real provider calls were deferred at the user's request. Real
Groq connectivity, model availability, answer quality, browser rendering, deployment,
and scoring consistency have not been established by this review.
