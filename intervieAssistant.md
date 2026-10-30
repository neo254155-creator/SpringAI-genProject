# Interview Assistant: features and remaining tasks

Source review date: 2026-10-26.

This project is a Java interview learning coach built with Java 21, Spring Boot,
Spring AI, Spring Data JPA, PostgreSQL, and a static JavaScript frontend.
Its main journey is: select a topic → receive a question → submit an answer →
read scored feedback → practise a weakness → review saved progress.

**Status meanings:** implemented means present in the source code. It does not
mean verified against live services or a deployed application. Planned means
unfinished. Optional improvements are distinguished from the agreed task list.
Older entries in implementation.md describe historical states; newer implemented
features below supersede those older pending-work statements.

## 1. Interview practice — implemented

| Feature | What it does and how it works |
| --- | --- |
| Track selection | Supports Java. Other language tracks are not implemented. |
| Topic selection | Supports Core Java and Spring Boot. The options API supplies the frontend choices without an AI call. |
| Difficulty selection | Supports beginner, intermediate, and advanced. Unsupported selections return a validation error. |
| Dynamic prompt templates | Separate system/user resource files receive the selected track, topic, difficulty, question, answer, or follow-up evidence. Java braces inside substituted input remain data. |
| Shared chat configuration | Chat, interview questions, and feedback reuse the configured Groq ChatClient. The configured default model is openai/gpt-oss-120b, with temperature 0.2 and a 1,000-token output budget. These settings do not prove live model availability. |
| Question generation | Requests one question for the selection, without an answer or hint. Code rejects empty responses; it does not fully verify that the model followed every instruction. Saved-session questions also have a 2,000-character limit. |
| Saved sessions | Creates a session ID and stores selection, ACTIVE/COMPLETED status, creation time, and completion time. |
| Answer submission | Validates a nonblank answer of at most 8,000 Java string characters and sends it with the saved question and selection for evaluation. |
| Correct points | Returns a list of what the learner explained correctly. |
| Missing points | Returns a list of concepts or details the answer missed. |
| Explanation | Returns the model's explanation. Uploaded documents do not currently support this explanation. |
| Defined rubric | Correctness is scored 0–5, completeness 0–3, and clarity 0–2. Each component includes a reason. |
| Validated score | Java rejects missing/invalid feedback fields, fractional component scores, and out-of-range scores, then calculates the total out of 10. Valid structure does not establish scoring accuracy. |
| Structured Java responses | DTO records represent questions, selections, feedback, scores, sessions, and document results and serialize to JSON. They are separate from JPA table entities. |
| Next question | Generates another question within the existing session, preserving its topic and difficulty and using available recent feedback. |
| Finish interview | Records completion. Repeating completion preserves the completion value. Completed sessions reject new question/answer work and remain readable. |
| Start another interview | The frontend resets a completed interview so a new selection/session can begin. |
| Standalone question and feedback APIs | Generate a question or evaluate a supplied question/answer without storing history. The standalone feedback API does not verify that its supplied question was previously generated. |
| General coding chat | GET /chat accepts a q parameter and returns text; POST /chat accepts a JSON message and returns JSON content. Messages must be nonblank and at most 8,000 Java string characters. |

Evidence: InterviewSelection, InterviewService, InterviewFeedbackService,
InterviewSessionService, InterviewHistoryStore, ChatOptionsConfig, DTOs,
AIServiceImpl, controller classes, and src/main/resources/Prompt/.

## 2. Weakness-based questions — implemented

This connects feedback directly to the next practice question.

1. Read the latest saved feedback from the current session.
2. Compare correctness /5, completeness /3, and clarity /2 as proportions.
3. Pick the lowest proportion. For example, 4/5, 2/3, and 1/2 targets clarity.
4. Give the model the chosen criterion, its feedback reason, up to three missing
   points, and the five most recent questions, while keeping topic and difficulty.
5. Save the generated question with its practice-focus criterion and reason.
6. Display that saved focus in practice, history, and restored sessions.

Small details:

- Equal proportions prefer correctness, then completeness, then clarity.
- No feedback or all-perfect scores produces general practice without inventing a weakness.
- The reason supplied as evidence is limited to 300 UTF-16 code units; each of
  three missing points is limited to 200, without splitting Unicode surrogate pairs.
- Evidence is serialized as JSON and labelled as data in the prompt. Raw learner
  answers are not included in the follow-up question prompt.
- The latest feedback is a snapshot taken before generation, including the latest
  saved attempt when a question has multiple attempts.
- A question matching one of the five recent questions after whitespace/case
  normalization is rejected. There is no automatic retry or paraphrase detection.
- Other sessions do not influence the focus. Difficulty does not change automatically.
- First/older questions can have no focus metadata.
- Prompt instructions do not guarantee relevance or prevent every injected instruction.

Evidence: AdaptiveQuestionPolicy, InterviewSessionService, InterviewHistoryStore,
InterviewService, QuestionFocus, Prompt/InterviewFollowUp, V4 migration, and
[ADAPTIVE_INTERVIEWS.md](ADAPTIVE_INTERVIEWS.md).

## 3. Frontend — implemented

Frontend refresh (2026-10-26): the existing interview flow now sits inside a shared
midnight/violet/cyan workspace. A landing page, overview, analytics, score-based
recommendations, settings, privacy, and a planned question-bank screen were added.
See [frontend/README.md](frontend/README.md) for module details and
[frontend/VERIFICATION.md](frontend/VERIFICATION.md) for executed checks and limits.

| New screen or behavior | What it does and its boundary |
| --- | --- |
| Landing page / | Hero, feature cards, process steps, interactive dashboard/statistics preview, final CTA, and footer. All preview numbers are explicitly sample data; readiness and weekly time are concepts. |
| Dashboard /dashboard.html | Actual session totals, aggregate scores, topic averages, latest saved answer scores, and real recent sessions. |
| Analytics /analytics.html | Keyboard-accessible score chart/table, weighted skill averages, strongest/weakest topics, recent weekly starts, and completion history. Charts/activity use at most ten recent sessions and disclose that scope. |
| Recommendations /recommendations.html | Ranks actual topic/difficulty averages and links to preselected practice. These are score-based suggestions; AI-generated recommendations remain planned. |
| Shared sidebar | Desktop collapse, mobile menu with Escape/focus containment, active navigation, and explicit shared-workspace status. No authentication or protected account routes are claimed. |
| Motion | Particle globe, hover tilt on fine pointers, reveal/counter effects, chart/progress animation, press states, and loading skeletons. Reduced-motion/device preference is respected. Browser smoothness remains unverified. |
| Settings /settings.html | Saves a local motion preference; marks account/profile functionality planned. |
| Question bank /questions.html | Marks the dedicated bank planned and links to real saved question history. |
| Privacy /privacy.html | Explains shared data, provider requests, browser preference, and missing user/deletion controls. |
| Public API configuration | GET /frontend-config returns only the API base URL, configured by FRONTEND_API_BASE_URL; defaults to same-origin. |
| End confirmation | Native dialog confirms completion and explains that unsubmitted text is not saved. |
| Visit timer/stages | Local page-visit elapsed time, answered count, and selection/answer/feedback stages; no invented fixed interview length or saved active-time metric. |
| Legacy resume links | /?session=ID redirects to /practice.html?session=ID; saved state is still restored without an AI call. |


| Feature | What it does and how it works |
| --- | --- |
| Interview screen | /practice.html serves topic/difficulty controls, a question, answer input, and feedback. Spring Boot serves the frontend; no separate frontend build or browser API key is required. |
| Answer counter and limit | Displays answer length and limits input to 8,000 characters; the backend also validates input. |
| Feedback presentation | Shows total score, correct points, missing points, explanation, component scores/reasons, and optional practice focus. |
| Answered-question count | Tracks progress in the session. Resume reads a distinct answered-question count so repeated saved attempts do not count as different questions. |
| Controlled workflow | Disables busy controls, prevents simultaneous screen requests, and requires answering the current question before Next question. The screen prevents resubmitting an already answered question. |
| Failure recovery | Keeps the typed answer and previous question/feedback on request failures. If first-question generation fails, it retains the created session for retry. |
| Loading and errors | Displays request status, readable API errors, and reload/retry controls where supported. Client code checks response shape and matching IDs. |
| Stop waiting and timeout | Practice requests stop waiting after 120 seconds or when cancelled. This does not guarantee cancellation of server/provider work. |
| Session URL | Stores ?session=ID in the URL, making the saved session reopenable. |
| Safe output rendering | Inserts model/server strings as text rather than interpreting them as HTML. |
| Navigation and accessibility basics | Links practice and history, labels inputs, provides status messaging, and moves focus to relevant content. Full accessibility/browser evaluation is not complete. |

Unsubmitted drafts are not saved. An uncertain network result followed by retry
can create another saved attempt or question; backend idempotency keys are absent.

Evidence: static/index.html, coach.js, interview-client.js, and frontend/README.md.

## 4. History, progress, and resume — implemented

| Feature | What it does and how it works |
| --- | --- |
| History dashboard | /history.html lists previous sessions and their status/selection. |
| Question history | Select a session to browse its saved questions, including optional practice focus. |
| Answer-attempt history | Select a question to read saved answers, feedback, rubric reasons, scores, and timestamps. The backend permits multiple attempts. |
| Topic progress | Groups saved results by track, topic, and difficulty: attempts, distinct questions answered, average score, best score, and last practice time. |
| Weighted average | Overall average uses attempt counts, so groups with different numbers of attempts are weighted appropriately. |
| Resume active session | Restores the latest question, its latest saved answer/feedback, session details, and distinct answered count. Resume itself makes no AI calls or writes. |
| Empty-session recovery | A restored active session with no question can request its first question. |
| Completed-session review | Opens a finished session for reading; new work remains disabled. |
| Pagination | Dashboard sessions, questions, and attempts load ten per page, newest first. APIs accept validated page/size parameters, with size 1–100. |
| Independent refresh | Progress and session lists can refresh separately; questions/attempts load when selected. |
| Read recovery | History reads have a 30-second timeout and Retry controls. Superseded requests are cancelled to prevent stale results replacing newer data. |

Progress includes all saved attempts and is not a verified mastery percentage.
Resume restores the latest question rather than every older unanswered question.
There is no login, user table, ownership, or per-user separation: data is shared
across the current single-learner application.

Evidence: history.html, history.js, history-client.js, SessionResumeResponse,
InterviewHistoryStore, InterviewFeedbackRepository, and PERSISTENCE.md.

## 5. Documents and semantic search — implemented backend APIs

| Feature | What it does and how it works |
| --- | --- |
| Note upload | Accepts nonempty UTF-8 .txt and .md files up to 65,536 bytes (64 KiB). PDF/Word extraction is absent. |
| Upload validation | Validates a nonblank title up to 200 characters, filename up to 255 characters, file type, size, encoding, and readable content. Removes an initial UTF-8 byte-order mark. |
| Filename handling | Removes path components and uses the filename only as metadata; never writes to an uploader-selected filesystem path. |
| Document metadata | Saves document ID, title, display filename, and creation time. |
| Text passages | Saves numbered passages with stable IDs in original order, at most 1,200 UTF-16 code units each, preserving surrogate pairs. Joining passages reconstructs the decoded text after BOM removal. No overlap, token-aware splitting, or semantic splitting is implemented. |
| Atomic upload | Saves document metadata and passages together in a transaction. |
| Browse documents | Paginated document listing, metadata retrieval, and ordered passage retrieval. These operations do not call AI. |
| Explicit indexing | A separate request embeds all passages and saves their vectors. Upload/startup alone does not generate embeddings. |
| Hosted embedding adapter | Supports an OpenAI-compatible /embeddings API with bearer authentication and configurable URL/model/key. |
| Optional Ollama adapter | Supports a separately running Ollama /api/embed service. Local embeddinggemma is a documented setup option, not a verified installed/configured model. |
| Optional embeddings | Disabled by default; disabled search/index requests return an explicit unavailable error. Enabling requires valid provider configuration. |
| Vector validation | Checks response/passages alignment, dimensions, finite values, and nonzero vector norms before saving normalized vectors. |
| Vector storage | Saves vectors as JSON in regular PostgreSQL with dimensions, index time, and model/endpoint identity. pgvector is not used. |
| Query embeddings | Generates a vector for the search question using the configured embedding model. |
| Semantic ranking | Java calculates cosine similarity against passages from one selected document, sorting by similarity then passage number. Search across the full library is absent. |
| Search controls | Query up to 2,000 Java string characters; result limit defaults to 5, range 1–20; minimum similarity defaults to 0, range -1 to 1. No match returns an empty list. |
| Source references | Results contain document ID/title, query, passage IDs/numbers/text, and similarity scores. Similarity is not confidence or proof of an answer. |
| Safe reindexing | Generates embeddings outside transactions, then atomically replaces the saved index under a document lock. Provider failures retain the previous index. |
| Compatibility detection | Missing/incompatible indexes or dimensions require reindexing. Changing model weights under the same identity is not detected automatically. |
| Provider timeouts | Embedding adapters use a 5-second connection timeout and 60-second read timeout. Ollama requests disable silent input truncation. Hosted provider input limits still apply. |

There is no document frontend yet. Search returns source passages; it does not
generate an explanation with citations or incorporate notes into interview feedback.
A hosted application needs a reachable embedding service; localhost points to its
own runtime, not the learner's computer.

Evidence: StudyDocumentService, DocumentSearchService, DocumentIndexStore,
DocumentEmbeddingConfig, embedding adapters, document controllers/entities/DTOs,
and [DOCUMENTS.md](DOCUMENTS.md).

## 6. Database, reliability, and project support — implemented

| Feature | What it does and how it works |
| --- | --- |
| PostgreSQL + JPA | Entities/repositories map sessions, questions, answers, feedback, feedback-point lists, documents, passages, and document indexes to database storage. |
| Local InterviewData setup | Supplies a PowerShell script and Java helper to create/configure the requested local database and run migrations. The user's report of running it is not independent verification here. |
| Local connection settings | Setup can save ignored .local/database.properties; runtime configuration supports database URL/username/password settings. Properties do not create environment variables. No secret values are recorded here. |
| Flyway migrations | V1 creates interview history; V2 document/passages tables; V3 document indexes; V4 question-focus columns. Flyway applies pending schema changes and tracks migration history; JPA maps data and validates schema. |
| Database constraints | Foreign keys, uniqueness, score constraints, and indexes protect relationships and stored score structure. |
| Consistent timestamps | Uses UTC instants with microsecond precision for stored interview timestamps. |
| Atomic answer/feedback save | A valid evaluation is saved as an answer plus feedback in one transaction. Provider/validation failure saves neither; database failure rolls back both. |
| Session state protection | Rechecks ACTIVE state under a row lock when saving, preventing late writes if the session completes during generation/evaluation. |
| Short transactions | Chat/embedding network calls run outside database transactions. |
| Standard API errors | Problem JSON describes invalid input (400), missing records (404), completed sessions/index conflicts (409), oversized multipart requests (413 where applicable), AI/embedding failures (502), unavailable database/embeddings (503), and unexpected errors (500). MVC preserves protocol errors such as 405/406/415. |
| Error privacy | Client responses do not expose raw provider exceptions, credentials, or unexpected internal exception details. |
| Offline tests | Default backend tests use AI stubs and test-only H2; frontend workflow/history tests mock requests. |
| Separate live tests | Optional live-groq profile checks a nonempty real response when its environment key exists. Optional postgres-test uses disposable PostgreSQL through Testcontainers/Docker. Skips are not passes. |
| Live connection checker | scripts/verify-live-connections.py launches a Java helper to check configured PostgreSQL, chat, and embeddings. Database checks are read-only and do not run migrations. Missing/disabled services are not passes. |
| Database inspection helper | Supplies read-only SQL for local inspection; its original detailed schema checks target V1 and do not prove all later migrations. |
| Docker database option | compose.yaml supplies PostgreSQL with persistent storage as an alternative to a locally installed server. It does not replace or delete an existing installation. |
| Application image definition | Dockerfile.vercel supplies Java build/runtime stages; .dockerignore excludes local secrets and unrelated files. Actual container build/deployment remains unverified. |
| Runtime port | Defaults to 8080 and supports PORT configuration. |
| Implementation notes | implementation.md records completed milestones, file responsibilities, observed checks, and limitations. Other focused guides explain persistence, documents, adaptive questions, frontend, deployment, and live checks. |

Neon is a PostgreSQL configuration/migration option, not a completed integration.
Moving existing data would require a separate transfer; local data does not need
to be deleted merely to use a hosted database.

## 7. Current API inventory

| Method and route | Purpose |
| --- | --- |
| GET /frontend-config | Public API base URL only. |
| GET /chat?q=... | Plain-text coding chat. |
| POST /chat | JSON coding chat. |
| GET /interview/options | Supported tracks/topics/levels. |
| POST /interview/questions | Stateless question generation. |
| POST /interview/feedback | Stateless feedback evaluation. |
| POST /interview/sessions | Create a saved session. |
| GET /interview/sessions | Page through saved sessions. |
| GET /interview/sessions/{id} | Read one session. |
| GET /interview/sessions/{id}/resume | Restore the latest saved session state. |
| POST /interview/sessions/{id}/questions | Generate/save a first or follow-up question. |
| GET /interview/sessions/{id}/questions | Page through saved questions. |
| POST /interview/questions/{id}/answers | Evaluate/save an answer with feedback. |
| GET /interview/questions/{id}/answers | Page through answer attempts and feedback. |
| POST /interview/sessions/{id}/complete | Complete a session. |
| GET /interview/progress | Aggregate topic/difficulty progress. |
| POST /documents | Upload text notes using multipart form data. |
| GET /documents | Page through document metadata. |
| GET /documents/{id} | Read document metadata. |
| GET /documents/{id}/passages | Read numbered passages. |
| POST /documents/{id}/index | Generate/store passage embeddings. |
| POST /documents/{id}/search | Find relevant passages within that document. |

## 8. Remaining task list

The original six-stage order remains the roadmap. Some stages are partly
implemented; the table lists what still needs work rather than treating completed
features as missing.

| Stage | Existing work | Remaining work |
| --- | --- | --- |
| 1. Foundation | User input, controller contracts, offline/live test separation, safe errors. | Verify actual database/chat connections in the runtime environment; configure and verify the selected embedding service before document-search use. |
| 2. Complete interview flow | Selection → question → answer → rubric feedback → weakness-based next question. | Verify the flow in a real browser against actual services and evaluate generated questions/feedback. |
| 3. PostgreSQL persistence | Sessions, questions, answers, feedback, and progress use JPA/Flyway. | Independently verify actual PostgreSQL schema/migrations and integration behavior. Add the originally requested users layer, authentication, ownership, and per-user data separation. |
| 4. Document search with citations | Upload, splitting, optional embedding adapters, JSON vector storage, and single-document retrieval. | Configure/test a real embedding provider; generate explanations from retrieved passages; return/check citations and evaluate source support. pgvector is an optional storage/search upgrade for larger collections. |
| 5. Frontend and deployment | Landing, overview, practice, history, analytics, score-based suggestions, motion settings, resume, Compose, and application Dockerfile exist. | Add document upload/index/search/cited-answer UI; verify browser presentation; build/run the image and complete a verified deployment with reachable database/chat/embedding services. |
| 6. Quality evaluation | Structural validation and deterministic offline tests exist. | Use a fixed set of answers to measure scoring consistency and explanation quality; evaluate weakness-targeted questions, real retrieval relevance, and whether cited passages support generated claims. |

Additional requested features still unfinished:

| Requested feature | Intended behavior and current boundary |
| --- | --- |
| Résumé-based interviews | Extract skills from an uploaded résumé and generate relevant questions. There is no résumé parser or skill-extraction interview flow. Existing text-note upload is not this feature. |
| Document-backed explanations | Use uploaded notes as evidence for an answer/explanation and show the passages used. Retrieval exists, but generated cited answers and note-supported interview feedback do not. |
| Full conversation memory | Preserve useful question/answer context across the interview. Current saved history and bounded follow-up evidence are partial context, not full conversational memory. |
| Controlled Java tools for the AI | Let the model retrieve previous scores and save study tasks through explicitly controlled Java methods. Existing history/progress APIs do not expose an AI tool-calling workflow; study-task storage is absent. |

Known gaps and optional improvements, not automatically additional committed tasks:

- Backend idempotency keys to avoid duplicate writes after uncertain retries.
- Draft autosave; broader resume of older unanswered questions.
- Weakness analysis across sessions, automatic difficulty changes, and detection
  of paraphrased duplicate questions.
- PDF/Word extraction, overlapping/token-aware passages, and library-wide search.
- Optional Neon migration if selected; no switch or data migration has been done.

## 9. Intended document-answer pipeline

1. Upload a document — implemented for UTF-8 text/Markdown.
2. Extract/read text — implemented for those text formats.
3. Split into passages — implemented with fixed character sections.
4. Embed passages — adapter code implemented; live provider setup/check pending.
5. Store passages/vectors — implemented with PostgreSQL JSON vectors; pgvector optional/pending.
6. Ask a question and embed it — search API implemented; live check pending.
7. Retrieve similar passages with IDs — implemented for one document.
8. Send retrieved passages to the LLM — planned.
9. Return an answer with citations and acknowledge insufficient evidence — planned.
10. Check citation IDs against retrieved passages and evaluate actual source support — planned.

Upload and indexing are separate requests today. Citation-ID validity alone will
not prove that cited text supports a generated claim.

## 10. What distinguishes the project

- Feedback has a defined rubric and reasons, rather than only a total number.
- Java computes totals and validates the structure before saving feedback.
- Latest feedback directly guides the next question through a transparent rule.
- Saved practice focus makes the adaptation visible to the learner.
- History, attempts, resume, and grouped progress connect individual answers to ongoing practice.
- Source passage IDs provide a foundation for the planned document-citation feature.

These are implemented design strengths. There is no comparative evaluation proving
this project is the best, nor a completed evaluation proving model accuracy.

## 11. Verification and limits

This catalogue was checked against controller mappings, service logic, DTOs,
entities/repositories, prompt resources, migrations, frontend clients/screens,
scripts, and the current guides. No credentials are included.

Frontend milestone checks: 126 backend tests and 21 JavaScript tests passed
using stubs/H2/mocked requests; Maven packaging succeeded. The optional browser
check could not run here because Playwright is unavailable and local server
sockets are blocked. No visual, animation, screenshot, or live-service pass is claimed.

The last connection check in this environment verified 0/3 services: network/socket
restrictions blocked PostgreSQL/Groq, and embeddings were disabled. Groq key
validity remains unknown; zero dashboard calls does not establish an invalid key.
Actual PostgreSQL contents, live model/embedding behavior, browser visuals, Docker
execution, and deployment remain unverified here. The user reported running local
database setup, but that report is not an independent database inspection.

Source guides: [README.md](README.md), [PROJECT_REVIEW.md](PROJECT_REVIEW.md),
[implementation.md](implementation.md), [PERSISTENCE.md](PERSISTENCE.md),
[DOCUMENTS.md](DOCUMENTS.md), [ADAPTIVE_INTERVIEWS.md](ADAPTIVE_INTERVIEWS.md),
[frontend/README.md](frontend/README.md), [VERCEL.md](VERCEL.md), and
[LIVE_VERIFICATION.md](LIVE_VERIFICATION.md).
