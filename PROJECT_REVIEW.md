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
| Frontend | `static/index.html`, `static/coach.js` | Offers selection fields and shows one generated question; handles loading, cancellation, timeout, and request errors |
| Legacy DTO | `entity/chatEntity` | Present but not referenced by the current application or tests |
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
- Session progress aggregates saved attempts and scores by topic/level. There is
  no authentication or per-user data separation yet.
- Frontend output is inserted as text. Cancelling a browser request does not
  guarantee cancellation of an already-running provider call.

## Current requested order

1. Prompt templates: generate a question by topic and difficulty (`level`). This
   step is implemented with separate system and user template resources.
2. Structured responses: question and feedback APIs now return Java records.
   Feedback contains validated component scores and a calculated total. The
   backend supports question → answer → feedback; the UI still shows questions only.
3. Regular PostgreSQL + JPA: implemented session/question/answer/feedback storage,
   history, completion, and aggregate progress APIs. H2 tests cover the contract;
   real PostgreSQL verification requires the optional Docker-backed test profile.
4. Document search later: add an embedding model, document ingestion, retrieval,
   and passage citations; consider pgvector at that stage.

The basic question screen exists. Answer/feedback/history/progress screens and
application deployment remain pending. A local PostgreSQL Compose file is provided;
no application Docker image or deployed site exists. Quality evaluation is pending.

Earlier requested extensions—weakness-targeted questions, résumé skill extraction,
interview memory, and AI-invokable Java tools for previous scores/study tasks—are
also not implemented. Previous scores can now be read through history/progress APIs. They require their own scoped milestones.

## Verification limits

See the latest dated entry in `implementation.md` for executed commands and counts.
API-key setup and real provider calls were deferred at the user's request. Real
Groq connectivity, model availability, answer quality, browser rendering, deployment,
and scoring consistency have not been established by this review.
