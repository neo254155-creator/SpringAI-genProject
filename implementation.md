# Implementation log

Record each completed milestone here with changes, verification, and limitations.
Current user instruction: keep changes uncommitted; do not create new commits or
push automatically. Earlier commit entries below describe the historical workflow.

## Earlier milestone — Select a focus and request an interview question

Implemented on 2026-10-23.

The first flow lets the user select **Java → Spring Boot → intermediate** and
request an interview question.

### Changes, step by step

1. Added `InterviewSelection` with supported choices: Java; Core Java or Spring
   Boot; beginner, intermediate, or advanced. Unsupported or missing choices are
   rejected before calling the AI provider.
2. Added `GET /interview/options` to supply those choices to the frontend.
3. Added `POST /interview/questions`, accepting `track`, `topic`, and `level` as
   JSON. Its response includes those fields and the generated `question`.
4. Added `InterviewService.generateQuestion()` and the dynamic
   `src/main/resources/Prompt/InterviewQuestion` template. The system prompt asks
   for exactly one question without answers or hints. Question calls use
   temperature 0.5 and a 500-token output budget.
5. Added a homepage at `src/main/resources/static/index.html` and updated
   `coach.js` with selection fields, a question button, loading state, cancellation,
   timeout handling, and error messages. Generated content is displayed as text.
6. Added validation and provider-error handling: invalid selections or malformed
   JSON return 400; provider failures and empty questions return 502.
7. Added backend tests for options, prompt rendering, validation, empty output,
   and provider failures. Updated the stale `/chat` controller test and removed
   the live provider call from the application context test.
8. Updated `frontend/README.md` with startup instructions and an API example.

### Verification

- `./mvnw -o clean test -q` passed: 14 tests, no failures or errors.
- JavaScript syntax check passed using `node --input-type=module --check`.
- Node-based frontend request checks passed for successful output, provider
  errors, and empty responses using a stubbed `fetch`.
- `git diff --check` passed before committing.
- Live AI output and browser rendering have not been tested. The one-question
  requirement is a prompt instruction, not a programmatically enforced guarantee.

### Try it

Configure the existing AI provider, run `./mvnw spring-boot:run`, and open
`http://localhost:8080/`. Choose a topic and level, then click **Get interview
question**.

### Remaining work — not implemented

- Answer submission with correct points, missing points, explanation, and a score
  against a defined rubric.
- Next-question selection based on weaknesses.
- Stored sessions and a topic-progress dashboard.
- Résumé skill extraction and relevant interview questions.
- Uploaded notes with passage-backed explanations.
- Conversation memory within an interview.
- Controlled Java methods to retrieve scores and save study tasks.

Suggested next milestone: answer submission and rubric-based feedback.

Commit message for this milestone: `Add interview question flow`


## Revised build order — Step 1: Fix the foundation

Implemented on 2026-10-23. This follows the revised build order requested after the
question-selection milestone above.

### Changes, step by step

1. Replaced the hard-coded `ChatTemplate()` service contract with `chat(message)`.
   `GET /chat?q=...` returns text; new `POST /chat` accepts a JSON `message` and
   returns a JSON `content` field. Calling GET without `q` now returns 400.
2. Fixed classpath resource casing and loaded both prompt files from `Prompt/`.
   The user template now accepts the actual message instead of fixed Spring values.
   Chat calls use a 1000-token output budget.
3. Added service-level validation for blank messages and messages longer than
   8000 Java string characters. Tests cover the length boundary and Java code
   containing braces in prompt input.
4. Added explicit invalid-input and AI-provider exceptions. Centralized HTTP
   error handling into problem responses with safe descriptions. Application
   errors use 400, 502, or 500 as appropriate; Spring MVC retains protocol errors
   such as 405 and 415. Empty AI responses count as provider failures.
5. Applied the same provider boundary and error contract to the existing interview
   endpoint. Updated the frontend to read the problem response's `detail` field.
6. Aligned controller tests with GET and POST chat input. Added offline service
   tests using an in-memory ChatModel stub; no provider requests occur in the
   default suite.
7. Added `GroqLiveTest`, tagged `live`, excluded from normal tests. The
   `live-groq` Maven profile selects live tests only. The test requires a nonblank
   `GROQ_API_KEY` and otherwise skips before creating its Spring context.
8. Added root README instructions for input, error responses, environment setup,
   and separate offline/live test commands.

### Verification

- Offline suite: 34 tests passed with no failures or errors.
- JavaScript syntax and `git diff --check` passed.
- `env -u GROQ_API_KEY ./mvnw -o -Plive-groq test -q`: only the live test
  was selected, and it was skipped because credentials were absent (1 skipped,
  no failures or errors).
- No real Groq request or browser rendering verification was performed. Stub tests
  verify API behavior and prompt construction, not model answer quality.

### Remaining revised steps — not implemented by this milestone

2. Complete topic → question → answer → structured feedback flow.
3. PostgreSQL users, sessions, questions, answers, and feedback.
4. Document search with citations and an embedding model; pgvector is an option.
5. Extend the existing basic frontend with interview, history, and progress screens;
   add Docker and deployment.
6. Fixed-answer scoring evaluation and citation-support evaluation.

Commit message for this milestone: `Fix chat foundation`

## Configuration follow-up — Use the configured model

1. Fixed an unfinished method-chain dot in `ChatOptionsConfig`.
2. Made the shared ChatClient explicitly read `spring.ai.openai.chat.model` and
   pass it to `.model(model)`. The existing property defaults to
   `openai/gpt-oss-120b` and supports the `GROQ_MODEL` environment override.
3. A regression test found that per-request option builders replaced the shared
   client options. Removed those overrides from both services so configuration is
   applied consistently. Both now use temperature 0.2 and a 1000-token output
   budget from `ChatOptionsConfig`, replacing the earlier per-service budgets.
4. Added a test that inspects the actual prompts sent to a stub model by both
   services and checks the configured model and token budget.

A model name does not supply authentication: real calls still require
`GROQ_API_KEY`. No live provider call was made for this change.

Commit message: `Use shared model configuration`

Verification: `./mvnw -o test -q` passed all 35 offline tests, including the shared
model configuration regression test. `git diff --check` passed.

## Foundation review and follow-up — 2026-10-24

Reviewed the complete source tree, Maven configuration, static frontend, prompt
resources, tests, and existing implementation history. API-key setup was deferred.
The foundation code from the earlier commits was already present.

### What was verified

1. Chat accepts GET query input and POST JSON input, validates messages in the
   service, and substitutes the message into the actual prompt resource.
2. The interview API accepts supported topic/level selections and generates a
   question. It does not yet accept answers or return scored feedback.
3. Standalone controller tests match the current request and response contracts.
4. The default Maven suite excludes live tests. Live Groq tests require an
   explicit profile and are guarded by a credential environment condition.
5. Error advice distinguishes input (400), provider (502), and unexpected
   application failures (500), and does not return raw provider exceptions.

### Gaps addressed in this follow-up

1. Replaced the startup-only context test with nine application-context MVC tests
   using a primary local StubChatModel. These exercise the configured controllers,
   services, shared model options, JSON conversion, and exception advice together.
   The tests make no provider requests and do not open a real server socket.
2. Added explicit media types to chat routes: GET produces plain text; POST consumes
   and produces JSON. A JSON-only Accept header on GET now receives HTTP 406.
3. Added coverage through the configured application for input errors, malformed
   JSON, missing query input, protocol errors, safe provider failures, and empty
   provider output.
4. Added `PROJECT_REVIEW.md` with the current project structure, completed behavior,
   limitations, and remaining work in the requested order. Updated the README's
   test and error descriptions.

### Commands and observed results

- `env -u GROQ_API_KEY ./mvnw -o clean test -q`: 43 tests passed, zero failures,
  errors, or skips. This explicitly removes the key from the test process.
- `env -u GROQ_API_KEY ./mvnw -o -Plive-groq test -q`: the live smoke test was
  selected and skipped (one skipped, zero failures/errors). No Groq call was made.
- JavaScript syntax check and `git diff --check` passed.
- Browser rendering, real server networking, real Groq connectivity, and model
  answer quality remain unverified by this work.

Part 1's requested code and offline verification are complete. The next requested
implementation step is the complete question → answer → structured feedback flow.
Database persistence, document retrieval/citations, history/progress screens,
Docker/deployment, and quality evaluation remain future work.

Commit message: `Verify foundation wiring`

## Current learning order — Step 1: Prompt templates — 2026-10-24

The requested order is now: (1) prompt templates, (2) structured Java responses
for questions/feedback/scores, (3) regular PostgreSQL + JPA persistence, and
(4) document search, embeddings, and pgvector later.

### Implemented in this step

1. Kept the existing `POST /interview/questions` contract. It accepts `track`,
   `topic`, and `level`; `level` is the API field for difficulty.
2. Moved the previously inline interviewer instructions into the new
   `Prompt/InterviewSystem` resource and loaded them with SystemPromptTemplate.
3. Refined `Prompt/InterviewQuestion` to clearly render the selected track,
   topic, and difficulty. Beginner targets fundamentals, intermediate targets
   practical scenarios, and advanced targets design tradeoffs/production behavior.
4. The service combines the rendered system and user messages and requests one
   question using the shared ChatClient configuration.
5. Added a parameterized service test covering both supported topics (Core Java,
   Spring Boot) at all three levels (beginner, intermediate, advanced). It verifies
   rendered selections, message roles, resolved placeholders, and returned data.
6. Updated the project review to reflect the latest requested order.

Example request body:

```json
{"track":"Java","topic":"Spring Boot","level":"intermediate"}
```

The question response already uses the `InterviewQuestion` Java record. Typed
feedback and scores are the next step; they are not implemented here. PostgreSQL,
JPA, document search, embeddings, and pgvector remain pending.

The prompt instructs the model to return one relevant question. Offline tests
verify prompt construction, not real question quality. API-key setup and live
Groq calls remain deferred.

Commit message: `Refine interview prompt templates`

Verification: `env -u GROQ_API_KEY ./mvnw -o test -q` passed all 49 offline tests
with zero failures, errors, or skips. `git diff --check` passed.


## Step 2 — Structured feedback and scores — 2026-10-24

### Local commit cleanup

The local Git record showed `origin/main` at `15da022`, with only `e47007a` and
`42c6def` ahead of it. Reset the local branch/index to `15da022` using a mixed reset.
The changes from those two local-only commits remain in the working tree. Earlier
commits recorded on origin/main were retained. No remote history was rewritten,
and no new commit or push is being made for this milestone.

### Implementation

1. Retained `InterviewQuestion` as the typed Java response for generated questions.
2. Added `POST /interview/feedback` and `InterviewFeedbackService.evaluateAnswer`.
   Input includes track, topic, level, question, and answer. Required values and
   lengths are validated before any model call.
3. Added system/user feedback templates with a fixed rubric: correctness 0–5,
   completeness 0–3, clarity 0–2, each with scoring anchors and a required reason.
4. Added `FeedbackEvaluation`, `RubricScores`, and `CriterionScore` Java records.
   Spring AI BeanOutputConverter adds format/schema instructions to the prompt
   and converts the model JSON into the typed evaluation.
5. Validate parsed output: required arrays/text/component scores must exist,
   list entries and score reasons cannot be blank, and scores must be integers
   within the rubric ranges. Invalid provider output returns a safe 502.
6. Java calculates the total from the validated component scores. The API returns
   `InterviewFeedback` with correctPoints, missingPoints, explanation, rubric,
   score, and maxScore (10). The model does not control the total calculation.
7. Added service, controller, and full application-context tests with local AI
   stubs. Coverage includes structured conversion, prompt input containing Java
   braces, score totals, invalid JSON/fields/ranges, empty output, and validation.
8. Updated documentation with the API contract and a clearly labelled fixture.

This is a stateless API. Caller-supplied questions are not checked against stored
history. The question-only UI does not yet submit answers. PostgreSQL + JPA
sessions, answers, and progress are the next step. Document search stays deferred.
No real Groq call or scoring-quality evaluation was performed.

Verification: `env -u GROQ_API_KEY ./mvnw -o test -q` passed all 82 offline tests
with zero failures/errors/skips. JavaScript syntax and `git diff --check` passed.
All current changes remain uncommitted, as requested.

## Step 3 — PostgreSQL + JPA persistence — 2026-10-24

### Implementation

1. Added Spring Data JPA, the PostgreSQL JDBC driver, Flyway, and PostgreSQL
   migration support. Normal runtime uses PostgreSQL; Hibernate validates the
   schema and Open EntityManager in View is disabled.
2. Added migration `V1__create_interview_history.sql` for sessions, questions,
   answers, feedback, and ordered correct/missing-point lists. It includes foreign
   keys, indexes, a unique feedback-to-answer relationship, and rubric score checks.
3. Added JPA entities and repositories. Sessions store track/topic/difficulty,
   ACTIVE/COMPLETED state, version, and timestamps. Feedback preserves the full
   explanation, component scores/reasons, total, maximum, and rubric version.
4. Added session APIs to create/read/list/complete sessions, generate/store
   questions, submit answers against stored questions, and read saved attempts.
   List endpoints are paginated with a maximum page size of 100.
5. Added `GET /interview/progress`: attempt count, distinct questions answered,
   average/best score, and latest practice time grouped by track/topic/difficulty.
   Progress is calculated from saved evaluations, not a fabricated mastery score.
6. Separated AI orchestration from database transactions. AI failure saves no
   question/attempt. Answer and feedback are saved atomically; a storage failure
   rolls both back. Session state is checked again under a row lock before saving
   so completion during an AI call blocks the late write.
7. Added safe 404 responses for missing records, 409 for completed-session writes,
   and 503 for database connection failures. Completion is idempotent. Timestamps
   are normalized to microseconds to match database round-trips.
8. Added `compose.yaml` for a local PostgreSQL 17 database with a named data volume.
   Added `DB_URL`, `DB_USER`, and `DB_PASSWORD` runtime configuration and detailed
   setup/API instructions in `PERSISTENCE.md`.
9. Added ten persistence contract tests covering complete API history reads,
   failure handling, atomic rollback, grouped progress/repeated attempts,
   completion, completion during AI work, pagination, validation, and missing IDs.
   The test model asserts that AI calls run outside database transactions.
10. Default tests run the migration and JPA validation on H2 in PostgreSQL mode.
    The same persistence contract is available through `-Ppostgres-test` on
    disposable PostgreSQL using Testcontainers. It is excluded from default tests.
    Explicit test datasource properties prevent environment settings from
    redirecting offline context tests. The live Groq test also uses H2 for storage.

### Scope and limitations

This is a single-learner workspace with no login/user ownership layer. History and
progress are database-wide. The original stateless question/feedback APIs and
question-only UI still work as before; use the new session APIs to save history.
History/progress screens and connecting the existing UI to session endpoints are
future work. Each accepted answer submission creates an attempt; retries are not
deduplicated. Failed AI evaluations do not save the submitted answer.

Real PostgreSQL execution was not verified in this workspace: Docker was
unavailable, so the provided integration-test path could not run.
The PostgreSQL profile selected ten tests and skipped all ten because Docker was
unavailable (zero failures/errors). This is a skip, not a PostgreSQL test pass.
No real Groq request was made. Database-backed history is not yet conversation
memory or document search. Embeddings/pgvector remain deferred.

All work remains uncommitted, per the current instruction.


Verification: `env -u GROQ_API_KEY ./mvnw -o clean test -q` passed 92 default tests
with zero failures/errors/skips. `env -u GROQ_API_KEY ./mvnw -o -Ppostgres-test test -q`
selected the ten PostgreSQL tests and skipped them as described above.
`git diff --check` passed. The PostgreSQL Compose setup was supplied but not run.


## Local InterviewData setup — 2026-10-24

User requested an actual local PostgreSQL database named `InterviewData`.

1. Found Windows PostgreSQL 18 binaries, including psql.exe. Attempting to execute
   them from this workspace failed with `UtilBindVsockAnyPort: socket failed 1`.
   A localhost TCP connection also failed with `Operation not permitted`. This
   execution environment cannot perform the actual database creation/connection.
2. Changed the project's fallback JDBC URL and Compose database name to
   `InterviewData`. No existing database, role, or Docker volume was changed.
3. Added `scripts/setup-local-postgres.ps1` for execution in Windows PowerShell.
   It prompts for passwords without echoing them, creates a missing app login and
   database, verifies the app login, and saves local connection settings in
   Git-ignored `.local/database.properties`. Existing role passwords are not reset
   and databases with another owner are not taken over.
4. Added `scripts/LocalDatabaseSetup.java`. The setup script builds its dependency
   classpath, runs it to verify the exact database name, applies actual Flyway
   migrations, and checks the six tables. It does not start Spring or call Groq.
5. Added optional local settings import. DB_URL/DB_USER/DB_PASSWORD overrides
   remain supported. Updated PERSISTENCE.md and README with the setup procedure
   and Windows/WSL connection distinction.

Observed verification: the Maven runtime classpath was built offline, the Java
migration helper compiled successfully, `.local/database.properties` is ignored
by Git, and `git diff --check` passed. The PowerShell setup script was not executed
here; real database creation, migrations, and connectivity remain unverified.
No new commit or push was made.

Configuration regression check: `env -u GROQ_API_KEY ./mvnw -o
-Dtest=GenProjectApplicationTests test -q` passed all 10 application-context tests
using H2 and a local AI stub. This does not verify the Windows setup script or an
actual PostgreSQL connection.
