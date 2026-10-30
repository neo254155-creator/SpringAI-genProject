# Implementation log

Record each completed milestone here with changes, verification, and limitations.
Keep new entries short and easy to understand: explain what was added, each file's
job, how to use it, its limits, and what was actually tested. Keep earlier details
so the implementation history is not lost.
Current user instruction: keep changes uncommitted; do not create new commits or
push automatically. Earlier commit entries below describe the historical workflow.

## Premium frontend workspace — 2026-10-26

1. Inspected the native JavaScript/custom-element frontend, existing REST DTOs, and
   absence of authentication. Kept the working InterviewFlow and history clients.
2. Added a midnight/violet/cyan design system, shared navigation/icons/cards,
   SVG charts with keyboard tooltips/data tables, and responsive desktop/mobile layouts.
3. Added the landing page with a particle canvas, reveal/counter/hover animations,
   and explicitly labeled sample statistics. The real app never falls back to samples.
4. Added overview, analytics, and score-based recommendations using real progress,
   sessions, and resume APIs. Recent charts/activity are bounded to ten sessions
   and labeled accordingly; missing details produce a partial-data notice.
5. Restyled practice/history. Preserved submission, retries, next questions, saved
   focus, completion, pagination, and resume. Added end confirmation, workflow stages,
   supported topic preselection, and a local page-visit timer. Legacy session URLs redirect.
6. Added motion settings, privacy explanation, and a clearly planned question-bank
   page linking to existing question history. Login, unsupported interview types,
   readiness, active study-time tracking, additional rubric fields, and AI recommendation
   tools are not presented as implemented.
7. Added GET /frontend-config through FrontendConfigController and ui/api-config.js.
   FRONTEND_API_BASE_URL configures a public API origin; default is same-origin.
   It exposes no provider/database settings. Cross-origin CORS remains operator setup.
8. Added dashboard/config/chart tests, a controller test, and an optional Playwright
   browser-check script. Added frontend/VERIFICATION.md and refreshed frontend/README.md
   with every new module's job, screen routes, data limits, and verification commands.
   Updated README.md and intervieAssistant.md. Added ignored browser artifact/tool paths.

Verification: Maven packaging succeeded. All 126 backend tests and 21 Node tests
passed with H2/stubs/mocked requests. JavaScript syntax, asset/import links, and
whitespace checks passed. No live provider or actual database verification occurred.

Browser verification is unfinished: Playwright/Chromium is unavailable and local
server sockets are denied. The supplied browser script exited before running any
browser checks. No screenshots or visual/motion passes are claimed. Superdesign's
CLI download failed on DNS; Active Theory's public page exposed only a JavaScript
shell to the web reader. The implementation follows the requested aesthetic, not
an independently verified exact reproduction. No commits, pushes, or deployment.
User changes to application.properties were preserved.

## Feature catalogue and remaining tasks — 2026-10-26

Added `intervieAssistant.md`: explains implemented features, small workflow details,
weakness selection, document search, all API routes, storage/reliability helpers,
the original six-stage roadmap, and remaining requested features. Separates planned
work and optional improvements from existing code and records live-verification limits.
Reviewed against source and current guides; checked route/file-link coverage and
documentation whitespace. No application code, credentials, commits, or pushes changed.

## First remaining backend task: verify live connections — 2026-10-26

Follow-up: the user supplied a literal chat API-key value in application.properties.
Reran `python3 scripts/verify-live-connections.py`. The Groq check now read the
configured value and attempted a request, but network/socket access failed before
a provider response. The key's validity remains unknown; it was not reported as
invalid or working. PostgreSQL remained blocked and embeddings remained disabled.
No key value was displayed, changed, or committed.

**Status: not completed. No live service was verified in this environment.**

1. Explained DTO records: compact immutable API input/output objects, separate
   from JPA entities that map database tables.
2. Checked configuration without displaying values. GROQ_API_KEY was absent from
   this process; embedding URL/model/key properties were blank and embeddings
   disabled. Database settings and the ignored local settings file were present.
   User edits to application.properties were preserved.
3. Added `scripts/VerifyLiveConnections.java`. It loads current Spring settings
   without starting the server/JPA/Flyway, checks a real PostgreSQL connection,
   table read access, V1–V4 history, and focus columns using read-only SQL. When
   configured, it also makes one small chat request and one embedding request.
4. Added `scripts/verify-live-connections.py`. This cross-platform launcher builds
   the current app/classpath and runs the Java helper. Missing/disabled settings
   are not counted as passes, and any incomplete check gives a nonzero exit code.
5. Added `LIVE_VERIFICATION.md` with the command, settings, checks, and limits;
   linked it from README.md. Keys, passwords, provider text, and raw exception
   messages are not printed. No credentials were added or changed.

Observed execution: PostgreSQL failed because socket/network access was blocked;
Groq was NOT_CONFIGURED because its key was missing; embeddings were NOT_CONFIGURED
because they were disabled. Result: **0/3 verified**, exit code 1. The helper
compiled and executed; no migrations, database writes, or AI requests ran.
Its database check does not validate every constraint/index or migration checksum.
This is a smoke check, not full API or model-quality validation.

Next action: configure the services in the environment that runs the application
and run `python3 scripts/verify-live-connections.py` in a terminal with network
access. Agent environment variables may differ from the user's terminal or host.
No commits, pushes, or deployments were made.

## Weakness-based next questions — 2026-10-26

### What it does

1. Read the latest saved feedback from the current interview session.
2. Compare correctness /5, completeness /3, and clarity /2 as proportions.
3. Choose the weakest criterion. For example, 4/5, 2/3, 1/2 targets clarity.
   Ties prefer correctness, then completeness, then clarity.
4. Build the next-question prompt using that focus, the feedback reason, up to
   three missing points, and the five most recent questions. Keep topic and level.
5. Ask Groq for one self-contained question. Reject a recent duplicate after
   lowercasing and whitespace normalization; do not automatically retry.
6. Save the question with its chosen focus and show it in practice/history/resume.

### Files and their jobs

Java paths below are relative to `src/main/java/com/first/genProject/`.

| File | Job |
| --- | --- |
| `service/AdaptiveQuestionPolicy.java` (new) | Choose the weakest proportional score in Java; bound evidence lengths and use a stable tie order. |
| `dto/QuestionFocus.java` (new) | Hold the selected criterion and a short reason. |
| `Prompt/InterviewFollowUp` (new resource) | Template the targeted question request using selection and saved evidence. |
| `Prompt/InterviewSystem` (updated resource) | Tell the model to treat feedback/questions as untrusted data, keep scope, and ask one question. |
| `service/InterviewService.java` (updated) | Render follow-up prompts with JSON evidence and call the existing shared ChatClient. |
| `service/InterviewSessionService.java` (updated) | Read context, select a plan, request/check the question, and save its focus. |
| `service/InterviewHistoryStore.java` (updated) | Read recent questions/latest feedback in a read-only transaction; save focus with the question. |
| `persistence/InterviewFeedbackRepository.java` (updated) | Retrieve the latest feedback within one session. |
| `persistence/InterviewQuestionEntity.java` (updated) | Map nullable focus criterion/reason columns. |
| `dto/StoredQuestionResponse.java` (updated) | Include optional focus in question/history/resume responses. |
| `db/migration/V4__add_question_practice_focus.sql` (new resource) | Add the two nullable columns and validate allowed criterion/reason pairs. |
| `static/coach.js`, `static/history.js` (updated resources) | Display the persisted practice-focus reason as text. |
| `AdaptiveQuestionPolicyTest.java` (new test) | Check proportional selection, ties, perfect/no feedback, evidence bounds, and Unicode handling. |
| `InterviewServiceTest.java` (updated test) | Check rendered selection, focus, JSON evidence, and literal braces in saved text. |
| `InterviewPersistenceContract.java` (updated test) | Check latest/session-specific evidence, saved focus, bounded history, duplicate rejection, and completion during generation. |
| `ADAPTIVE_INTERVIEWS.md` (new) | Explain the exact rule, API behavior, migration, and limits. |
| `README.md`, `PERSISTENCE.md`, `PROJECT_REVIEW.md`, `frontend/README.md` | Update feature status and usage. |
| `implementation.md` | Record this milestone. |

### Details and limits

- Reuses `POST /interview/sessions/{id}/questions` and the existing Next question
  button. No new endpoint, AI model, API key, or embedding dependency is needed.
- No feedback or perfect scores means normal practice with no invented weakness.
  Original stateless question generation stays selection-based.
- First/older questions have null focus. Later responses may include
  `focus: {criterion, reason}`; saved metadata survives reload and resume.
- Feedback is a snapshot taken when generation starts. Only that session's latest
  attempt is used, even when a question has multiple attempts. Other sessions do
  not influence the focus. Later feedback affects subsequent requests.
- Raw learner answers are not added to the next-question prompt. The weakest
  criterion reason is capped at 300 UTF-16 code units and each of three missing
  points at 200, without splitting surrogate pairs. Recent history is capped at
  five questions. Evidence is serialized as JSON and labelled as data.
- Provider calls run outside transactions. Saving still locks/checks the session
  so completing it during generation prevents a late question from being saved.
- V1–V3 were not edited. V4 was exercised in H2; it has not been applied to the
  user's actual PostgreSQL here. Flyway applies pending migrations at app startup.
- Exact recent duplicates are rejected; paraphrased repetition is not detected.
  Prompt instructions do not guarantee question relevance, quality, or resistance
  to injected instructions. Live-model evaluation remains necessary.
- Full conversation memory, multi-session weakness analysis, and automatic
  difficulty changes remain future work. This supersedes earlier notes that
  weakness-based question selection was missing.

**Verification:** `env -u GROQ_API_KEY ./mvnw -o test -q` passed 125 backend tests
with zero failures/errors/skips. The two Node test files passed 13 tests. JavaScript
syntax checks and `git diff --check` passed. Tests used H2, AI stubs, and mocked
fetch; real Groq output, PostgreSQL migration, and browser visuals remain
unverified. No commits, pushes, or deployments were performed.

## Interview history, progress, and session resume — 2026-10-26

### What you can do now

1. Open `/history.html` from the practice screen's History & progress link.
2. Browse previous sessions, their questions, and saved answer attempts.
3. Read each answer's feedback, score, correct/missing points, and rubric reasons.
4. See average/best scores by topic and difficulty, attempts, distinct answered
   questions, and last-practised dates. Overall averages are weighted by attempts.
5. Resume an active session or open a completed session for read-only review.
6. Refresh `/?session=ID` to restore the latest saved question and answer/feedback.

### Files and their jobs

Java paths are relative to `src/main/java/com/first/genProject/`; frontend files
are in `src/main/resources/static/` unless a different path is shown.

| File | Added or changed behavior |
| --- | --- |
| `history.html` (new) | Entry page for the history and progress dashboard. |
| `history.js` (new) | Render real API data, topic scores, session/question/attempt lists, rubric details, pagination, empty states, errors, and retries. |
| `history-client.js` (new) | Read paginated history and progress APIs; validate responses and calculate weighted totals. |
| `coach.js` (updated) | Add dashboard navigation, open saved session URLs, restore saved answer text, keep the session ID in the URL, and reload options for a new interview. |
| `interview-client.js` (updated) | Restore session state without generating another question; validate saved feedback and retain the distinct answered-question count. |
| `dto/SessionResumeResponse.java` (new) | Return session details, latest question, latest answer/feedback, and answered-question count. |
| `Controller/InterviewSessionController.java` (updated) | Expose read-only `GET /interview/sessions/{id}/resume`. |
| `service/InterviewHistoryStore.java` (updated) | Read and map resume data inside a short read-only transaction. |
| `persistence/InterviewFeedbackRepository.java` (updated) | Count distinct answered questions in one session, so retries are not counted as extra questions. |
| `frontend/history-client.test.mjs` (new) | Check history routes, pagination, cancellation signals, weighted averages, empty results, and error handling. |
| `frontend/interview-client.test.mjs` (updated) | Check empty/unanswered/answered/completed resume states, restored text, no extra writes, and failed-resume retry. |
| `InterviewPersistenceContract.java` (updated test) | Check saved resume state, latest attempts, distinct counts, completed sessions, missing sessions, and zero extra AI calls. |
| `frontend/README.md`, `PERSISTENCE.md`, `README.md`, `PROJECT_REVIEW.md` | Explain the screens, new endpoint, URL behavior, and remaining work. |
| `implementation.md` | Record this milestone and verification. |

### Details and limits

- Reused existing session, question, answer, and progress APIs. No database schema
  migration, new model, or extra API key is needed for this milestone.
- Lists load ten records per page, newest first. Questions and answer attempts
  are fetched when selected rather than loading an entire history at once.
- Progress and history load independently. Read requests time out after 30 seconds;
  a newer request cancels an older one. All server/model strings render as text.
- Resume only reads saved records. Empty active sessions can request their first
  question; answered questions offer Next question; completed sessions stay closed.
- Unsubmitted drafts are not saved or restored. Resume opens the latest question,
  not every older unanswered question. Data is shared across the existing
  single-learner app; there is no per-user separation yet.
- Averages include all saved attempts. Scores are not a verified mastery measure.
  Adaptive questions, conversation memory, and AI-quality evaluation remain pending.
- This milestone supersedes earlier notes saying history/progress screens and
  saved-session resume were missing.

**Verification:** `env -u GROQ_API_KEY ./mvnw -o test -q` passed 114 backend tests
with zero failures/errors/skips. The two Node test files passed 13 tests (10 + 3).
JavaScript syntax checks and `git diff --check` passed. Backend tests used H2 and
AI stubs; JavaScript tests mocked fetch. Browser visual testing, real PostgreSQL,
live providers, and deployment were not verified. No commits or pushes were made.

## Hosted embedding settings + complete interview screen — 2026-10-26

### 1. Make the embedding provider configurable

- `application.properties`: added provider, URL, model, and API-key settings.
  Defaults are `openai-compatible`, blank URL/model/key, and disabled embeddings.
  Set `DOCUMENT_EMBEDDINGS_URL`, `DOCUMENT_EMBEDDINGS_MODEL`, and
  `DOCUMENT_EMBEDDINGS_API_KEY`, then `DOCUMENT_EMBEDDINGS_ENABLED=true`.
  These are environment references; no real credentials were entered or set.
- `DocumentEmbeddingConfig.java`: selects the hosted or local adapter. Disabled
  embeddings allow startup without embedding credentials. Enabling with missing
  configuration fails clearly at startup. These custom properties are separate
  from the existing `spring.ai.openai.*` properties used for Groq.
- `HostedPassageEmbeddingClient.java`: sends authenticated HTTPS requests to an
  OpenAI-compatible `/embeddings` API. Validates response indexes, puts vectors
  back in passage order, and uses existing vector checks and safe error responses.
- `OllamaPassageEmbeddingClient.java`: remains available when provider is
  explicitly set to `ollama`; its creation is now controlled by the configuration.
- Added `HostedPassageEmbeddingClientTest.java` and `DocumentEmbeddingConfigTest.java`
  for authentication, batching/order, invalid responses, provider selection,
  disabled operation, missing credentials, and private error handling.

### 2. Complete answer → feedback → next question in the frontend

- `static/interview-client.js`: added a session workflow that creates a saved
  session, gets questions, submits answers, receives feedback, and completes it.
  Retries keep the session already created. Invalid or duplicate submissions are
  blocked in the screen; previous state remains available after request failure.
- `static/coach.js`: now shows an answer box, character counter, correct/missing
  points, explanation, total score, and all three rubric scores/reasons. Added
  Next question, Finish interview, and Start another interview buttons.
- The UI uses persisted session APIs. It preserves typed answers on failures,
  disables controls while busy, renders model output as text, supports stopping
  a browser request, and times out after 120 seconds.
- Added `frontend/interview-client.test.mjs`: tests the full workflow, correct
  endpoint IDs, retries, invalid input, duplicate prevention, cancellation signal,
  and malformed responses with mocked fetch.
- Updated `frontend/README.md`, `README.md`, `DOCUMENTS.md`, `VERCEL.md`,
  `PROJECT_REVIEW.md`, and this log with current setup and behavior.

**Checks:** 112 backend tests passed with `env -u GROQ_API_KEY ./mvnw -o test -q`;
7 JavaScript workflow tests passed with `node frontend/interview-client.test.mjs`.
Both JavaScript files passed syntax checks, and `git diff --check` passed.
Backend tests use H2 and mocked providers; no real key was required.

**Still pending:** real provider/database verification, browser visual testing,
history/progress screens, refresh/resume, and weakness-based next questions.
Next question currently uses the same topic and difficulty. Stopping a browser
request does not guarantee the server stops; retrying an uncertain result can
save another attempt. Existing backend data remains saved after a page reload,
but the UI does not automatically reopen that session. Hosted API compatibility
must match the documented format; arbitrary provider formats are not supported.
No commits, pushes, remote settings, or deployments were performed.

API format checked with the OpenAI Docs skill:
https://developers.openai.com/api/reference/resources/embeddings/methods/create

## Document upload → embeddings → storage: simple guide — 2026-10-26

### The six steps

1. **Read the uploaded document.** `POST /documents` receives a `title` and `file`.
2. **Extract its text.** Read UTF-8 text from `.txt` or `.md` files. PDF and Word
   extraction are not supported yet.
3. **Divide the text into passages.** Split it into numbered sections with stable
   IDs. Save the document details and passages together in PostgreSQL.
4. **Send each passage to an embedding API.** Call `POST /documents/{id}/index`.
   It sends the saved passages to Ollama together in one batch request.
5. **Receive numerical vectors.** Each passage gets a list of numbers representing
   its meaning. Check the vectors and scale each to unit length for comparison.
6. **Save passages and vectors in PostgreSQL.** Passages were saved at upload;
   this step saves their vectors, linked by passage ID, in the document's index.

**Two separate requests:** upload saves text first; `/index` generates and saves
vectors afterward. Uploading alone does not call the embedding API.

### What each file does

Java files below are under `src/main/java/com/first/genProject/`.

| File | Job |
| --- | --- |
| `Controller/StudyDocumentController.java` | Accept uploads; list documents; return details and passages. |
| `service/StudyDocumentService.java` | Validate files, read text, split passages, and save or retrieve notes. |
| `persistence/StudyDocumentEntity.java` | Map document ID, title, filename, and creation time to a table. |
| `persistence/StudyPassageEntity.java` | Map passage ID, number, text, and document link to a table. |
| `persistence/StudyDocumentRepository.java` | Read/save documents; lock a document while replacing its index. |
| `persistence/StudyPassageRepository.java` | Read/save passages; retrieve them in number order. |
| `dto/StudyDocumentResponse.java` | Define document details returned by the API. |
| `dto/StudyPassageResponse.java` | Define a returned passage's ID, number, and text. |
| `Controller/DocumentSearchController.java` | Accept indexing and search requests. |
| `service/DocumentSearchService.java` | Request embeddings, build an index, and rank matching passages. |
| `embedding/PassageEmbeddingClient.java` | Define the embedding operations, independently of a provider. |
| `embedding/OllamaPassageEmbeddingClient.java` | Call Ollama's `/api/embed`; keep it separate from Groq chat. |
| `embedding/EmbeddingVectors.java` | Check and normalize vectors; calculate cosine similarity. |
| `service/DocumentIndexStore.java` | Read or replace a complete saved index in a database transaction. |
| `persistence/DocumentSearchIndexEntity.java` | Map vectors, model identity, dimensions, and indexing time to a table. |
| `persistence/DocumentSearchIndexRepository.java` | Read/save document indexes. |
| `dto/DocumentIndexResponse.java` | Return document ID, model key, dimensions, passage count, and indexing time. |
| `dto/DocumentSearchRequest.java` | Validate the search query, result limit, and minimum score. |
| `dto/DocumentSearchResponse.java` | Return document details and matching passages with similarity scores. |
| `exception/DocumentSearchException.java` | Identify disabled embeddings, provider failures, or an unusable index. |
| `Controller/ApiExceptionHandler.java` (updated) | Turn these failures into clear HTTP error responses. |

Other files added or updated:

| File | Job |
| --- | --- |
| `src/main/resources/db/migration/V2__create_study_documents.sql` | Create `study_documents` and `study_passages`, with constraints and an index. |
| `src/main/resources/db/migration/V3__create_document_search_indexes.sql` | Create `document_search_indexes`, linked to its document. |
| `src/main/resources/application.properties` | Add optional embedding settings; preserve existing settings. |
| `src/test/resources/application-test.properties` | Disable real embeddings in offline tests. |
| `GenProjectApplicationTests.java` (updated test) | Check upload, passage retrieval, validation, and disabled indexing. |
| `DocumentSearchTests.java` (new test) | Check indexing, ranking, source IDs, errors, model changes, and safe reindexing. |
| `OllamaPassageEmbeddingClientTest.java` (new test) | Check HTTP requests, vector validation, and provider errors using a mock server. |
| `DOCUMENTS.md` | Explain setup, API examples, and limits. |
| `PROJECT_REVIEW.md` | Update the project's feature status. |
| `implementation.md` | Record changes and this simple explanation. |

### Use and limits

- Read notes through `GET /documents`, `GET /documents/{id}`, and
  `GET /documents/{id}/passages`. Listing uses `page` and `size` (1–100).
- Files must be nonblank UTF-8 text, at most 64 KiB; titles must be 1–200
  characters. Display filenames are limited to 255 characters and never used as
  disk paths. Invalid text and disallowed control characters are rejected.
- Passages hold at most 1,200 UTF-16 code units without splitting a Unicode
  surrogate pair. No overlap is added. An initial UTF-8 byte-order mark is removed;
  joining passage text reconstructs the remaining decoded notes.
- Embeddings are off by default. Set `DOCUMENT_EMBEDDINGS_ENABLED=true`,
  `DOCUMENT_EMBEDDINGS_PROVIDER` (default `openai-compatible`),
  `DOCUMENT_EMBEDDINGS_URL`, `DOCUMENT_EMBEDDINGS_MODEL`, and
  `DOCUMENT_EMBEDDINGS_API_KEY`. URL, model, and key now default to empty.
  For local Ollama, choose provider `ollama`, URL `http://localhost:11434`, and
  model `embeddinggemma`; no embedding key is needed. Groq has a separate key.
- Ollama requests disable silent text truncation. Both adapters use 5-second
  connection and 60-second read timeouts. Invalid vector counts, dimensions, zero vectors,
  and non-finite numbers are rejected; supported dimensions are 1–8,192.
- Vectors are stored as JSON text in regular PostgreSQL. **pgvector is not used.**
  Provider calls happen outside database transactions. Reindexing replaces one
  complete index; an embedding failure preserves the previous index.
- `POST /documents/{id}/search` embeds your query and compares it with saved
  vectors for that document. It returns source text, passage IDs/numbers, and
  similarity scores, ordered by score and then passage number.
- Search accepts `query` (1–2,000 characters), `limit` (1–20; default 5), and
  `minimumScore` (-1 to 1; default 0). No matches above the threshold returns an
  empty list. Similarity is not a correctness score or a guarantee of relevance.
- Reindex after changing provider URL, model, or model weights. Model/endpoint
  identity and dimensions are checked; changed weights under the same model
  name cannot be detected automatically.
- Errors: invalid input → 400; missing document → 404; index missing/incompatible
  → 409; embedding failure → 502; embeddings disabled → 503. Oversized multipart
  requests may be rejected by the server with 413. Provider details stay private.
- Documents follow the existing shared, single-learner setup. No document upload/search
  frontend, per-user ownership, library-wide search, or generated explanations
  with citations has been added yet.

**Verified previously:** 106 offline tests passed using H2, fake vectors, and a
mock embedding server. Live Ollama access was blocked by the execution environment;
real embeddings and the new migrations on local PostgreSQL remain unverified.
This update changes documentation only; `git diff --check` was used to check it.

## Future implementation: embeddings + pgvector + AI answers

```text
User uploads a document
          ↓
Extract document text
          ↓
Split text into passages
          ↓
Generate an embedding for each passage
          ↓
Store passages and vectors in PostgreSQL + pgvector
          ↓
User asks a question
          ↓
Generate an embedding for the question
          ↓
Find the most similar passage vectors
          ↓
Return the relevant passages with IDs
          ↓
Send those passages to an LLM to generate an answer
```

- **Embeddings:** turn passages and questions into numerical vectors using the
  same embedding model, so their meanings can be compared.
- **pgvector:** store vectors in PostgreSQL vector columns and find similar
  passages inside the database.
- **Passage IDs:** identify the source text supporting the generated answer.
- **LLM:** use the retrieved passages to answer the question, include source
  citations, and say when the notes do not contain enough information.
- **Citation checks:** verify that returned citation IDs belong to the retrieved
  passages. Checking an ID alone does not prove the answer is supported; answer
  quality and source support also need evaluation.

**Current versus planned:** text upload, passage splitting, optional embeddings,
and single-document retrieval are implemented. Current vectors are JSON text in
regular PostgreSQL, and Java calculates similarity. Switching to pgvector and
generating answers with citations are future tasks. Upload and indexing currently
use separate requests; this diagram describes the intended overall workflow.

## Document search, second milestone: embeddings and retrieval — 2026-10-26

1. Added a provider-independent PassageEmbeddingClient interface and optional
   Ollama /api/embed adapter. The provider preference question was unanswered;
   used local Ollama as the stated default implementation, disabled until enabled
   through DOCUMENT_EMBEDDINGS_ENABLED. URL and model are independently configured
   with DOCUMENT_EMBEDDINGS_URL and DOCUMENT_EMBEDDINGS_MODEL. Groq chat settings
   remain separate. No provider installation or model download was performed.
2. Added POST /documents/{id}/index. It embeds the document's saved passages,
   validates vector counts/dimensions/finite nonzero values, normalizes them,
   then atomically replaces the index under a database document lock. Provider
   calls run outside transactions, and provider failures preserve the old index.
3. Added V3__create_document_search_indexes.sql and an index entity/repository.
   Vectors are serialized as JSON text in regular PostgreSQL, together with model
   identity, endpoint hash, dimensions, and timestamp. V1/V2 were left unchanged.
4. Added POST /documents/{id}/search with query, limit, and minimumScore. It ranks
   passages within one document by cosine similarity and returns actual passage
   IDs, numbers, text, and scores. Missing or incompatible indexes return 409;
   disabled embeddings return 503; provider failures return a safe 502 response.
5. Added offline HTTP adapter and full MVC/persistence tests for batch requests,
   disabled operation, malformed vectors, ranking/source identity, thresholds,
   invalid requests, model/dimension mismatches, repeated indexing, and preserving
   an existing index on provider failure. Tests assert no provider call holds a
   database transaction. Test properties explicitly disable real embeddings.
6. Updated DOCUMENTS.md and PROJECT_REVIEW.md with setup, endpoint examples,
   limitations, and remaining work. Existing user application.properties edits
   were preserved; optional embedding properties were added.

Verification: env -u GROQ_API_KEY ./mvnw -o test -q passed all 106 tests, with
zero failures/errors/skips. H2 exercised Flyway V3 and JPA mappings; the embedding
adapter used a mock HTTP server and retrieval used deterministic fake vectors.
git diff --check passed. An Ollama executable exists, but attempting to list local
models failed with socket: operation not permitted. No real embedding output,
PostgreSQL migration, or retrieval quality was verified against a live service.

Scope: bounded single-document search for the existing 64 KiB note limit. This
does not install pgvector or search across all documents. AI-generated
explanations with verified citations remain the next feature. No commits,
pushes, database switches, or deployments were performed.

Provider contract checked against:
https://github.com/ollama/ollama/blob/main/docs/api.md
https://github.com/ollama/ollama/blob/main/docs/capabilities/embeddings.mdx

## Document search, first milestone: save source notes — 2026-10-26

1. Checked the current roadmap: prompt templates, structured feedback, and regular
   JPA persistence are implemented. Document search is the next planned feature.
2. Added POST /documents for UTF-8 .txt/.md uploads (64 KiB maximum) with a title.
   Validation rejects blank files, invalid UTF-8, control characters, unsupported
   extensions, and oversized input. Filenames are stored only as display metadata.
3. Added StudyDocumentEntity/StudyPassageEntity and repositories. Uploads save
   document metadata and numbered passages in one transaction. Each passage has
   a stable UUID, with at most 1,200 UTF-16 code units and no split surrogate pair.
4. Added V2__create_study_documents.sql for the two tables, foreign key, ordered
   passage uniqueness, size constraint, and document timestamp index. V1 was not
   edited. The new migration will run against the configured database at startup;
   it was not applied to the user's local PostgreSQL from this environment.
5. Added paginated GET /documents, GET /documents/{id}, and
   GET /documents/{id}/passages. Added DOCUMENTS.md with usage and limits, and
   updated PROJECT_REVIEW.md.
6. Extended application-context tests to verify persisted uploads, exact text
   reconstruction across a Unicode boundary, stable passage IDs/order, invalid
   uploads leaving row counts unchanged, missing resources, and pagination.

Verification: env -u GROQ_API_KEY ./mvnw -o test -q passed all 95 tests with zero
failures/errors/skips. Tests use H2 and AI stubs; document endpoints made no AI
calls. Flyway V2 and entity validation ran in the test database. git diff --check
passed. Actual PostgreSQL, browser uploads, embeddings, and deployment were not
tested. The pre-existing application.properties changes were left untouched.

Embedding setup was requested from the user; no provider was selected or
configured in this milestone. Semantic retrieval, pgvector, PDF extraction, and
AI explanations with verified citations remain pending. This is the ingestion
piece of document search, not the completed search feature. No commit or push
was made.

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

## Database inspection request and Neon compatibility — 2026-10-25

The user reported running the setup script successfully. The project now has an
ignored `.local/database.properties` pointing to InterviewData with the genproject
login and a nonempty password. Password contents were not displayed.

Attempted live inspection through localhost TCP and PostgreSQL's Unix socket.
This execution environment denied both with Operation not permitted. The Linux
PostgreSQL cluster utility reports 18/main as down; this does not determine the
state of the Windows PostgreSQL server. Therefore the saved connection file and
user report are not being presented as independently verified table creation.

Added `scripts/check-local-database.sql`, a read-only inspection for execution in
the user's terminal. It requires InterviewData, the six app tables and Flyway
history, validates all V1 column names/types/nullability, checks successful V1
history, and reports actual constraints, indexes, and row counts. It inserts no
data and runs no migration. Constraint definitions are reported for review; it
does not claim to automatically verify every definition. The script has not been
executed against the real database in this environment.

Neon compatibility was checked against official documentation:
https://neon.com/docs/guides/java
https://neon.com/docs/get-started/connect-neon
The project already uses PostgreSQL JDBC/JPA and Flyway. Neon can be selected
through DB_URL (a JDBC URL with SSL), DB_USER, and DB_PASSWORD. This is a compatible
configuration path, not a tested Neon deployment; no database switch was made.
No commit or push was made.

## Vercel container build — 2026-10-25

1. Checked current official Vercel documentation: container deployments are
   supported, including Spring Boot. The detected filename is Dockerfile.vercel,
   so that spelling is used instead of the requested Dockerfile.versel.
2. Added Dockerfile.vercel with a Java 21 Maven-wrapper build stage and Java 21
   runtime stage running as a non-root user. Packaging skips tests.
3. Added server.port=${PORT:8080} so the application follows the container port
   while retaining port 8080 as its local default. The image defaults PORT to 8080.
4. Added an allowlist-based .dockerignore that excludes local credentials, Git
   history, existing build artifacts, and installers from the build context.
5. Added VERCEL.md with deployment instructions and required environment settings.
   Hosted PostgreSQL credentials and a Groq key must be configured in Vercel;
   this change does not migrate the local database or set remote secrets.

Verification: ./mvnw -o -B -DskipTests package succeeded and git diff --check
passed. Tests were skipped. Docker is not installed in this execution environment,
so the container build and deployed startup have not been tested. No deployment,
commit, or push was performed.

Sources: https://vercel.com/kb/guide/does-vercel-support-docker-deployments
and https://vercel.com/blog/dockerfile-on-vercel.
